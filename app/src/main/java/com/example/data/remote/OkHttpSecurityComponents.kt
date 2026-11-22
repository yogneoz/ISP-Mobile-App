package com.example.data.remote

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.example.security.SecureTokenStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.Authenticator
import okhttp3.CertificatePinner
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.Route

object EnterprisePinningConfig {
  const val PROD_HOST = "api.pulsehr-enterprise.com"
  const val SANDBOX_HOST = "sandbox.pulsehr-enterprise.com"
  const val SANDBOX_BASE_URL = "https://$SANDBOX_HOST/"

  // Enterprise ISP TLS SPKI SHA-256 Pins (primary leaf + backup intermediate root)
  val certificatePinner: CertificatePinner =
    CertificatePinner.Builder()
      .add(PROD_HOST, "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
      .add(PROD_HOST, "sha256/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB=")
      .build()
}

@Singleton
class AuthHeaderInterceptor @Inject constructor(
  private val secureTokenStore: SecureTokenStore,
) : Interceptor {
  override fun intercept(chain: Interceptor.Chain): Response {
    val original = chain.request()
    val path = original.url.encodedPath
    if (path.endsWith("/auth/login") || path.endsWith("/auth/refresh")) {
      return chain.proceed(original)
    }

    val accessToken = secureTokenStore.getAccessToken()
    val request =
      if (!accessToken.isNullOrBlank()) {
        original.newBuilder().header("Authorization", "Bearer $accessToken").build()
      } else {
        original
      }
    return chain.proceed(request)
  }
}

@Singleton
class TokenRefreshAuthenticator @Inject constructor(
  private val secureTokenStore: SecureTokenStore,
) : Authenticator {
  override fun authenticate(route: Route?, response: Response): Request? {
    if (responseCount(response) >= 2) return null

    synchronized(this) {
      val currentAccess = secureTokenStore.getAccessToken()
      val failedAuthHeader = response.request.header("Authorization")

      if (
        !currentAccess.isNullOrBlank() &&
          failedAuthHeader != null &&
          failedAuthHeader != "Bearer $currentAccess"
      ) {
        return response.request.newBuilder().header("Authorization", "Bearer $currentAccess").build()
      }

      secureTokenStore.getRefreshToken() ?: return null
      val newAccess = "jwt_acc_${UUID.randomUUID().toString().replace("-", "")}"
      val newRefresh = "jwt_ref_${UUID.randomUUID().toString().replace("-", "")}"
      val newExpiry = System.currentTimeMillis() + 3600_000L
      secureTokenStore.rotateTokens(newAccess, newRefresh, newExpiry)

      return response.request.newBuilder().header("Authorization", "Bearer $newAccess").build()
    }
  }

  private fun responseCount(response: Response): Int {
    var result = 1
    var prior = response.priorResponse
    while (prior != null) {
      result++
      prior = prior.priorResponse
    }
    return result
  }
}

/**
 * Connectivity & Enterprise Sandbox Gateway Interceptor.
 * Enforces real Android ConnectivityManager checks and user-controlled offline testing,
 * simulating v2 bootstrap, idempotent push/pull sync, attendance scoring, and machine-readable
 * conflict envelopes (SERIAL_NOT_IN_VAN, TICKET_CLOSED, TICKET_REASSIGNED).
 */
@Singleton
class EnterpriseGatewayInterceptor @Inject constructor(
  @ApplicationContext private val context: Context,
  private val secureTokenStore: SecureTokenStore,
) : Interceptor {

  private val seenIdempotencyKeys = mutableMapOf<String, String>()

  fun isDeviceOnline(): Boolean {
    if (secureTokenStore.offlineSimulationFlow.value) return false
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
      ?: return true
    val network = cm.activeNetwork ?: return false
    val caps = cm.getNetworkCapabilities(network) ?: return false
    return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
  }

  override fun intercept(chain: Interceptor.Chain): Response {
    val request = chain.request()
    if (!isDeviceOnline()) {
      throw IOException("Network offline: queued in Room for WorkManager sync.")
    }

    if (request.url.host != EnterprisePinningConfig.SANDBOX_HOST) {
      return chain.proceed(request)
    }

    val path = request.url.encodedPath
    val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    var httpCode = 200
    var httpMsg = "OK"

    val bodyText =
      when {
        path.endsWith("/auth/login") -> {
          val rawBody = requestBodyToString(request)
          val role =
            when {
              rawBody.contains("HR_MANAGER") -> "HR_MANAGER"
              rawBody.contains("HR_OFFICER") -> "HR_OFFICER"
              else -> "HR_SELF_SERVICE"
            }
          val email =
            Regex(""""email"\s*:\s*"([^"]+)"""")
              .find(rawBody)
              ?.groupValues
              ?.getOrNull(1) ?: "maya.shrestha@pulsehr.com"
          val userId =
            when (role) {
              "HR_MANAGER" -> "usr_mgr_101"
              "HR_OFFICER" -> "usr_off_102"
              else -> "usr_emp_103"
            }
          val empId =
            when (role) {
              "HR_MANAGER" -> "emp_101"
              "HR_OFFICER" -> "emp_102"
              else -> "emp_103"
            }
          """
          {
            "accessToken": "jwt_acc_${UUID.randomUUID().toString().replace("-", "")}",
            "refreshToken": "jwt_ref_${UUID.randomUUID().toString().replace("-", "")}",
            "expiresInSeconds": 3600,
            "userId": "$userId",
            "email": "$email",
            "role": "$role",
            "employeeId": "$empId",
            "capabilities": ["attendance.punch", "ess.view", "claims.submit", "jobs.execute", "stock.van"],
            "branchScope": "MV-HQ"
          }
          """.trimIndent()
        }

        path.endsWith("/auth/refresh") -> {
          val session = secureTokenStore.getCurrentSession()
          """
          {
            "accessToken": "jwt_acc_${UUID.randomUUID().toString().replace("-", "")}",
            "refreshToken": "jwt_ref_${UUID.randomUUID().toString().replace("-", "")}",
            "expiresInSeconds": 3600,
            "userId": "${session?.userId ?: "usr_emp_103"}",
            "email": "${session?.email ?: "maya.shrestha@pulsehr.com"}",
            "role": "${session?.role?.name ?: "HR_SELF_SERVICE"}",
            "employeeId": "${session?.employeeId ?: "emp_103"}",
            "capabilities": [],
            "branchScope": "${session?.branchScope ?: "MV-HQ"}"
          }
          """.trimIndent()
        }

        path.endsWith("/workplace/bootstrap") -> {
          val session = secureTokenStore.getCurrentSession()
          val empId = session?.employeeId ?: "emp_103"
          val capsJson =
            (session?.capabilities ?: emptySet()).joinToString(",") { "\"${it.key}\"" }
          val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
          val cal = Calendar.getInstance()
          val todayDate = fmt.format(cal.time)
          cal.add(Calendar.DAY_OF_YEAR, 1)
          val tomorrowDate = fmt.format(cal.time)
          cal.add(Calendar.DAY_OF_YEAR, 1)
          val dayAfterDate = fmt.format(cal.time)
          val branchCode = session?.branchScope?.substringBefore(" ") ?: "MV-HQ"
          """
          {
            "serverTimeEpochMs": ${System.currentTimeMillis()},
            "capabilities": [$capsJson],
            "punchState": "ACTIVE",
            "shiftWindow": "08:30 - 17:30",
            "unreadNotices": 2,
            "pendingApprovals": 2,
            "vanLocation": "VAN-MV-07",
            "shifts": [
              {
                "id": "shf_${empId}_day_1",
                "shiftCode": "FTTH-DAY",
                "shiftName": "Primary Field Dispatch Shift",
                "shiftDateAd": "$todayDate",
                "startHour": 8,
                "startMinute": 30,
                "endHour": 17,
                "endMinute": 30,
                "crossesMidnight": false,
                "workMode": "FIELD",
                "requireGeofenceCheckIn": true,
                "requireGeofenceCheckOut": false,
                "branchCode": "$branchCode",
                "sequenceNo": 1,
                "isStandby24x7": false
              },
              {
                "id": "shf_${empId}_split_2",
                "shiftCode": "EVE-SURGE",
                "shiftName": "Split Evening Cutover Window",
                "shiftDateAd": "$todayDate",
                "startHour": 18,
                "startMinute": 30,
                "endHour": 21,
                "endMinute": 0,
                "crossesMidnight": false,
                "workMode": "FIELD",
                "requireGeofenceCheckIn": false,
                "requireGeofenceCheckOut": false,
                "branchCode": "$branchCode",
                "sequenceNo": 2,
                "isStandby24x7": false
              },
              {
                "id": "shf_${empId}_night_1",
                "shiftCode": "NOC-NIGHT",
                "shiftName": "Overnight Backbone Maintenance",
                "shiftDateAd": "$tomorrowDate",
                "startHour": 22,
                "startMinute": 0,
                "endHour": 6,
                "endMinute": 0,
                "crossesMidnight": true,
                "workMode": "OFFICE",
                "requireGeofenceCheckIn": true,
                "requireGeofenceCheckOut": true,
                "branchCode": "$branchCode",
                "sequenceNo": 1,
                "isStandby24x7": true
              },
              {
                "id": "shf_${empId}_day_3",
                "shiftCode": "FTTH-DAY",
                "shiftName": "Enterprise Client Fiber Rollout",
                "shiftDateAd": "$dayAfterDate",
                "startHour": 9,
                "startMinute": 0,
                "endHour": 18,
                "endMinute": 0,
                "crossesMidnight": false,
                "workMode": "HYBRID",
                "requireGeofenceCheckIn": true,
                "requireGeofenceCheckOut": false,
                "branchCode": "$branchCode",
                "sequenceNo": 1,
                "isStandby24x7": false
              }
            ]
          }
          """.trimIndent()
        }

        path.endsWith("/sync/pull") -> {
          """
          {
            "serverCursor": ${System.currentTimeMillis()},
            "revokedDevice": false,
            "updatedNoticesCount": 0
          }
          """.trimIndent()
        }

        path.endsWith("/devices/register") -> {
          val devId = secureTokenStore.getOrCreateDeviceId()
          """
          {
            "registered": true,
            "deviceId": "$devId",
            "boundAt": "2026-11-20T16:30:00Z"
          }
          """.trimIndent()
        }

        path.endsWith("/attendance/punch") -> {
          val rawBody = requestBodyToString(request)
          val idempKey =
            Regex(""""idempotencyKey"\s*:\s*"([^"]*)"""")
              .find(rawBody)?.groupValues?.getOrNull(1).orEmpty()
          if (idempKey.isNotBlank() && seenIdempotencyKeys.containsKey(idempKey)) {
            seenIdempotencyKeys[idempKey]!!
          } else {
            val accVal =
              Regex(""""gpsAccuracyMeters"\s*:\s*([0-9.]+)""")
                .find(rawBody)?.groupValues?.getOrNull(1)?.toFloatOrNull()
            val badAcc = accVal != null && accVal > 50f
            val score = if (badAcc) 35 else 0
            val status = if (badAcc) "FLAGGED" else "VERIFIED"
            val notes =
              if (badAcc) "GPS accuracy ${accVal?.toInt()}m exceeded 50m threshold; accepted & flagged"
              else "Server timestamp & GPS verified"
            val payload =
              """
              {
                "id": "att_${UUID.randomUUID().toString().take(8)}",
                "syncStatus": "SYNCED",
                "serverTimestamp": "${System.currentTimeMillis()}",
                "integrityStatus": "$status",
                "riskScore": $score,
                "integrityNotes": "$notes"
              }
              """.trimIndent()
            if (idempKey.isNotBlank()) seenIdempotencyKeys[idempKey] = payload
            payload
          }
        }

        path.contains("/workplace/jobs/") && path.endsWith("/complete") -> {
          val rawBody = requestBodyToString(request)
          val idempKey =
            Regex(""""idempotencyKey"\s*:\s*"([^"]*)"""")
              .find(rawBody)?.groupValues?.getOrNull(1).orEmpty()
          if (idempKey.isNotBlank() && seenIdempotencyKeys.containsKey(idempKey)) {
            seenIdempotencyKeys[idempKey]!!
          } else {
            val serial =
              Regex(""""serialUsed"\s*:\s*"([^"]*)"""")
                .find(rawBody)?.groupValues?.getOrNull(1).orEmpty()
            val payload =
              when {
                serial.startsWith("BAD-", ignoreCase = true) ||
                  serial.equals("UNKNOWN", ignoreCase = true) -> {
                  httpCode = 409
                  httpMsg = "Conflict"
                  """
                  {
                    "code": "SERIAL_NOT_IN_VAN",
                    "message": "Scanned ONT serial '$serial' is not assigned to VAN-MV-07. Captured photos & optical readings preserved; coordinator can fix serial and replay.",
                    "retryable": true,
                    "details": "serial=$serial"
                  }
                  """.trimIndent()
                }
                rawBody.contains("\"CLOSED-CONFLICT\"") -> {
                  httpCode = 409
                  httpMsg = "Conflict"
                  """
                  {
                    "code": "TICKET_CLOSED",
                    "message": "Ticket was already closed by another technician while offline. Captured readings retained in Needs Attention.",
                    "retryable": false,
                    "details": "ticket_already_closed"
                  }
                  """.trimIndent()
                }
                else -> {
                  """
                  {
                    "workOrderId": "${path.substringAfter("/jobs/").substringBefore("/complete")}",
                    "resultStatus": "CLOSED",
                    "errorCode": null,
                    "message": "Operational close committed; Accounting COGS outbox queued.",
                    "accountingOutboxId": "out_${UUID.randomUUID().toString().take(8)}"
                  }
                  """.trimIndent()
                }
              }
            if (idempKey.isNotBlank() && httpCode == 200) {
              seenIdempotencyKeys[idempKey] = payload
            }
            payload
          }
        }

        path.endsWith("/workplace/claims") -> {
          val rawBody = requestBodyToString(request)
          val claimId =
            Regex(""""claimId"\s*:\s*"([^"]*)"""")
              .find(rawBody)?.groupValues?.getOrNull(1) ?: "clm_${UUID.randomUUID().toString().take(6)}"
          """
          {
            "claimId": "$claimId",
            "status": "PENDING",
            "accountingOutboxStatus": "QUEUED_FOR_VERIFICATION"
          }
          """.trimIndent()
        }

        path.endsWith("/leaves/requests") -> {
          """
          {
            "id": "lv_${UUID.randomUUID().toString().take(8)}",
            "status": "PENDING",
            "submittedAt": "2026-11-20"
          }
          """.trimIndent()
        }

        path.endsWith("/notifications/fcm-token") -> {
          """
          {
            "registered": true,
            "topics": ["leave_updates", "payroll_alerts"]
          }
          """.trimIndent()
        }

        path.endsWith("/workplace/location/batch") -> {
          val rawBody = requestBodyToString(request)
          val idempKey =
            Regex(""""idempotencyKey"\s*:\s*"([^"]*)"""")
              .find(rawBody)?.groupValues?.getOrNull(1).orEmpty()
          if (idempKey.isNotBlank() && seenIdempotencyKeys.containsKey(idempKey)) {
            seenIdempotencyKeys[idempKey]!!
          } else {
            val count = Regex(""""eventType"""").findAll(rawBody).count().coerceAtLeast(1)
            val sessionId =
              Regex(""""sessionId"\s*:\s*"([^"]*)"""")
                .find(rawBody)?.groupValues?.getOrNull(1)
            val payload =
              """
              {
                "acceptedCount": $count,
                "serverTimestampEpochMs": ${System.currentTimeMillis()},
                "routeSessionId": ${if (sessionId != null) "\"$sessionId\"" else "null"},
                "message": "Persisted $count employee route waypoint(s) to database"
              }
              """.trimIndent()
            if (idempKey.isNotBlank()) seenIdempotencyKeys[idempKey] = payload
            payload
          }
        }

        else -> "{}"
      }

    return Response.Builder()
      .request(request)
      .protocol(Protocol.HTTP_1_1)
      .code(httpCode)
      .message(httpMsg)
      .body(bodyText.toResponseBody(jsonMediaType))
      .build()
  }

  private fun requestBodyToString(request: Request): String {
    val body = request.body ?: return ""
    val buffer = okio.Buffer()
    body.writeTo(buffer)
    return buffer.readUtf8()
  }
}
