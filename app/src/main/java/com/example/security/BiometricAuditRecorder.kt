package com.example.security

import android.os.Build
import com.example.data.local.AuditLogEntity
import com.example.data.local.PulseHrDao
import com.example.domain.model.AuditOutcome
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@Singleton
class BiometricAuditRecorder @Inject constructor(
  private val dao: PulseHrDao,
  private val tokenStore: SecureTokenStore,
) {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  fun recordBiometricAttempt(
    purposeLabel: String,
    outcome: AuditOutcome,
    authMethod: String,
    failureReason: String? = null,
    screenName: String = "BIOMETRIC_PROMPT",
  ) {
    scope.launch {
      recordEventSuspend(
        eventCategory = "BIOMETRIC_AUTH",
        screenName = screenName,
        actionLabel =
          when (outcome) {
            AuditOutcome.SUCCESS -> "Biometric verified • $purposeLabel"
            AuditOutcome.FAILED -> "Biometric authentication failed • $purposeLabel"
            AuditOutcome.CANCELLED -> "Biometric prompt cancelled • $purposeLabel"
            AuditOutcome.FALLBACK -> "Switched to App PIN fallback • $purposeLabel"
            AuditOutcome.INFO -> "Biometric check • $purposeLabel"
          },
        outcome = outcome,
        authMethod = authMethod,
        failureReason = failureReason,
      )
    }
  }

  fun recordPinFallbackAttempt(
    purposeLabel: String,
    success: Boolean,
  ) {
    scope.launch {
      recordEventSuspend(
        eventCategory = "PIN_FALLBACK",
        screenName = "PIN_VERIFICATION",
        actionLabel =
          if (success) "App PIN verified • $purposeLabel"
          else "Incorrect App PIN attempt • $purposeLabel",
        outcome = if (success) AuditOutcome.SUCCESS else AuditOutcome.FAILED,
        authMethod = "PIN_FALLBACK",
        failureReason = if (success) null else "Entered 4-digit security PIN did not match stored hash",
      )
    }
  }

  suspend fun recordEventSuspend(
    eventCategory: String,
    screenName: String,
    actionLabel: String,
    outcome: AuditOutcome,
    authMethod: String,
    failureReason: String? = null,
    timestampEpochMs: Long = System.currentTimeMillis(),
  ) {
    val session = tokenStore.getCurrentSession()
    val timestampLabel = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(timestampEpochMs))
    dao.insertAuditLog(
      AuditLogEntity(
        id = "aud_${UUID.randomUUID().toString().take(8)}",
        actorId = session?.employeeId ?: "unauthenticated",
        actorEmail = session?.email ?: "device_lock_screen",
        deviceId = session?.deviceId ?: tokenStore.getOrCreateDeviceId(),
        screenName = screenName,
        actionLabel = actionLabel,
        timestampLabel = timestampLabel,
        createdAtEpochMs = timestampEpochMs,
        eventCategory = eventCategory,
        outcome = outcome.name,
        authMethod = authMethod,
        deviceModel = buildDeviceInfoSummary(),
        failureReason = failureReason,
      )
    )
  }

  fun buildDeviceInfoSummary(): String {
    val manufacturer = Build.MANUFACTURER.orEmpty().replaceFirstChar { it.uppercaseChar() }
    val model = Build.MODEL.orEmpty()
    val hardwareName = listOf(manufacturer, model).filter { it.isNotBlank() }.joinToString(" ").ifBlank { "Android Device" }
    val osVersion = Build.VERSION.RELEASE.orEmpty().ifBlank { "Unknown" }
    return "$hardwareName • Android $osVersion (API ${Build.VERSION.SDK_INT})"
  }
}
