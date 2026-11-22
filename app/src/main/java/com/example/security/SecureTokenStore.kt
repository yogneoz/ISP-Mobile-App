package com.example.security

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.VisibleForTesting
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.domain.model.AppCapability
import com.example.domain.model.DutyStatus
import com.example.domain.model.OfflineAccessMode
import com.example.domain.model.RoleBundle
import com.example.domain.model.UserRole
import com.example.domain.model.UserSession
import com.example.domain.model.WorkHoursScheduleConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class SecureTokenStore private constructor(
  private val prefs: SharedPreferences
) {

  @Inject constructor(@ApplicationContext context: Context) : this(createEncryptedPrefs(context))

  @Volatile private var inMemoryAccessToken: String? = null

  private val _sessionFlow = MutableStateFlow<UserSession?>(loadSessionFromPrefs())
  val sessionFlow: StateFlow<UserSession?> = _sessionFlow.asStateFlow()

  private val _offlineSimulationFlow = MutableStateFlow(false)
  val offlineSimulationFlow: StateFlow<Boolean> = _offlineSimulationFlow.asStateFlow()

  private val _dutyStatusFlow = MutableStateFlow(loadDutyStatus())
  val dutyStatusFlow: StateFlow<DutyStatus> = _dutyStatusFlow.asStateFlow()

  private val _workHoursScheduleFlow = MutableStateFlow(loadWorkHoursSchedule())
  val workHoursScheduleFlow: StateFlow<WorkHoursScheduleConfig> =
    _workHoursScheduleFlow.asStateFlow()

  private val _activeRouteSessionIdFlow = MutableStateFlow(loadActiveRouteSessionId())
  val activeRouteSessionIdFlow: StateFlow<String?> = _activeRouteSessionIdFlow.asStateFlow()

  private val _lastRouteSyncEpochMsFlow = MutableStateFlow(loadLastRouteSyncEpochMs())
  val lastRouteSyncEpochMsFlow: StateFlow<Long?> = _lastRouteSyncEpochMsFlow.asStateFlow()

  fun getOrCreateDeviceId(): String {
    val existing = prefs.getString(KEY_DEVICE_ID, null)
    if (!existing.isNullOrBlank()) return existing
    val generated = "dev_and_${UUID.randomUUID().toString().take(8)}"
    prefs.edit().putString(KEY_DEVICE_ID, generated).apply()
    return generated
  }

  fun saveAuthSession(
    accessToken: String,
    refreshToken: String,
    expiresAtEpochMs: Long,
    session: UserSession,
  ) {
    inMemoryAccessToken = accessToken
    val now = System.currentTimeMillis()
    val effectiveSession =
      session.copy(
        deviceId = session.deviceId.ifBlank { getOrCreateDeviceId() },
        lastOnlineSignInEpochMs =
          if (session.lastOnlineSignInEpochMs > 0L) session.lastOnlineSignInEpochMs else now,
      )
    prefs
      .edit()
      .putString(KEY_ACCESS_TOKEN, accessToken)
      .putString(KEY_REFRESH_TOKEN, refreshToken)
      .putLong(KEY_EXPIRES_AT, expiresAtEpochMs)
      .putString(KEY_USER_ID, effectiveSession.userId)
      .putString(KEY_USER_EMAIL, effectiveSession.email)
      .putString(KEY_USER_ROLE, effectiveSession.role.name)
      .putString(KEY_EMPLOYEE_ID, effectiveSession.employeeId)
      .putString(
        KEY_CAPABILITIES,
        effectiveSession.capabilities.joinToString(",") { it.key },
      )
      .putString(
        KEY_ASSIGNED_BUNDLES,
        effectiveSession.assignedBundles.joinToString("|"),
      )
      .putString(KEY_BRANCH_SCOPE, effectiveSession.branchScope)
      .putString(KEY_DEVICE_ID, effectiveSession.deviceId)
      .putLong(KEY_LAST_ONLINE_AUTH_MS, effectiveSession.lastOnlineSignInEpochMs)
      .apply()
    _sessionFlow.value = effectiveSession
  }

  fun rotateTokens(accessToken: String, refreshToken: String, expiresAtEpochMs: Long) {
    inMemoryAccessToken = accessToken
    val now = System.currentTimeMillis()
    prefs
      .edit()
      .putString(KEY_ACCESS_TOKEN, accessToken)
      .putString(KEY_REFRESH_TOKEN, refreshToken)
      .putLong(KEY_EXPIRES_AT, expiresAtEpochMs)
      .putLong(KEY_LAST_ONLINE_AUTH_MS, now)
      .apply()
    _sessionFlow.value = _sessionFlow.value?.copy(lastOnlineSignInEpochMs = now)
  }

  fun getAccessToken(): String? {
    return inMemoryAccessToken ?: prefs.getString(KEY_ACCESS_TOKEN, null)?.also {
      inMemoryAccessToken = it
    }
  }

  fun getRefreshToken(): String? = prefs.getString(KEY_REFRESH_TOKEN, null)

  fun isTokenExpired(): Boolean {
    val expiresAt = prefs.getLong(KEY_EXPIRES_AT, 0L)
    return System.currentTimeMillis() >= expiresAt
  }

  fun getLastOnlineAuthEpochMs(): Long = prefs.getLong(KEY_LAST_ONLINE_AUTH_MS, 0L)

  fun setLastOnlineAuthEpochMsForTesting(epochMs: Long) {
    prefs.edit().putLong(KEY_LAST_ONLINE_AUTH_MS, epochMs).apply()
    _sessionFlow.value = _sessionFlow.value?.copy(lastOnlineSignInEpochMs = epochMs)
  }

  fun getOfflineAccessMode(nowEpochMs: Long = System.currentTimeMillis()): OfflineAccessMode {
    if (!_offlineSimulationFlow.value) {
      return OfflineAccessMode.ONLINE
    }
    val lastAuth = getLastOnlineAuthEpochMs()
    val ageMs = if (lastAuth > 0L) (nowEpochMs - lastAuth).coerceAtLeast(0L) else 0L
    return if (ageMs <= OFFLINE_TOKEN_WINDOW_MS) {
      OfflineAccessMode.OFFLINE_GRACE
    } else {
      OfflineAccessMode.OFFLINE_LOCKED
    }
  }

  fun clearSensitiveInMemoryCache() {
    inMemoryAccessToken = null
  }

  fun hasActiveSession(): Boolean =
    prefs.getString(KEY_ACCESS_TOKEN, null) != null && prefs.getString(KEY_USER_ID, null) != null

  fun getCurrentSession(): UserSession? = _sessionFlow.value

  fun savePin(pin: String) {
    val saltBytes = ByteArray(16).apply { SecureRandom().nextBytes(this) }
    val saltHex = saltBytes.joinToString("") { "%02x".format(it) }
    val hashHex = sha256("$saltHex:$pin")
    prefs.edit().putString(KEY_PIN_SALT, saltHex).putString(KEY_PIN_HASH, hashHex).apply()
  }

  fun hasConfiguredPin(): Boolean = !prefs.getString(KEY_PIN_HASH, null).isNullOrBlank()

  fun verifyPin(pin: String): Boolean {
    val salt = prefs.getString(KEY_PIN_SALT, null) ?: return false
    val expectedHash = prefs.getString(KEY_PIN_HASH, null) ?: return false
    return MessageDigest.isEqual(
      sha256("$salt:$pin").toByteArray(),
      expectedHash.toByteArray(),
    )
  }

  fun setOfflineSimulation(enabled: Boolean) {
    _offlineSimulationFlow.value = enabled
  }

  fun setDutyStatus(status: DutyStatus) {
    prefs.edit().putString(KEY_DUTY_STATUS, status.name).apply()
    _dutyStatusFlow.value = status
  }

  fun saveWorkHoursSchedule(config: WorkHoursScheduleConfig) {
    prefs
      .edit()
      .putBoolean(KEY_SCHED_ENABLED, config.enabled)
      .putInt(KEY_SCHED_START_H, config.startHour)
      .putInt(KEY_SCHED_START_M, config.startMinute)
      .putInt(KEY_SCHED_END_H, config.endHour)
      .putInt(KEY_SCHED_END_M, config.endMinute)
      .putBoolean(KEY_SCHED_WEEKDAYS, config.weekdaysOnly)
      .apply()
    _workHoursScheduleFlow.value = config
  }

  fun getLastGeofenceInsideState(): Boolean? {
    return if (prefs.contains(KEY_LAST_GEO_INSIDE)) {
      prefs.getBoolean(KEY_LAST_GEO_INSIDE, false)
    } else {
      null
    }
  }

  fun setLastGeofenceInsideState(isInside: Boolean) {
    prefs.edit().putBoolean(KEY_LAST_GEO_INSIDE, isInside).apply()
  }

  fun getLastReminderSentKey(): String? = prefs.getString(KEY_LAST_REMINDER_KEY, null)

  fun setLastReminderSentKey(key: String) {
    prefs.edit().putString(KEY_LAST_REMINDER_KEY, key).apply()
  }

  fun saveFcmToken(token: String) {
    prefs.edit().putString(KEY_FCM_TOKEN, token).apply()
  }

  fun getFcmToken(): String? = prefs.getString(KEY_FCM_TOKEN, null)

  fun isLeaveAlertsEnabled(): Boolean = prefs.getBoolean(KEY_TOPIC_LEAVE, true)

  fun setLeaveAlertsEnabled(enabled: Boolean) {
    prefs.edit().putBoolean(KEY_TOPIC_LEAVE, enabled).apply()
  }

  fun isPayrollAlertsEnabled(): Boolean = prefs.getBoolean(KEY_TOPIC_PAYROLL, true)

  fun setPayrollAlertsEnabled(enabled: Boolean) {
    prefs.edit().putBoolean(KEY_TOPIC_PAYROLL, enabled).apply()
  }

  fun saveVoiceAuthPhrase(phrase: String) {
    val cleaned = phrase.trim().ifBlank { DEFAULT_VOICE_AUTH_PHRASE }
    prefs.edit().putString(KEY_VOICE_AUTH_PHRASE, cleaned).apply()
  }

  fun getVoiceAuthPhrase(): String =
    prefs.getString(KEY_VOICE_AUTH_PHRASE, null)?.takeIf { it.isNotBlank() }
      ?: DEFAULT_VOICE_AUTH_PHRASE

  fun getActiveRouteSessionId(): String? = _activeRouteSessionIdFlow.value

  fun getActiveRouteStartedEpochMs(): Long = prefs.getLong(KEY_ROUTE_STARTED_AT_MS, 0L)

  fun startRouteShiftSession(sessionId: String, startedAtEpochMs: Long) {
    prefs
      .edit()
      .putString(KEY_ACTIVE_ROUTE_SESSION_ID, sessionId)
      .putLong(KEY_ROUTE_STARTED_AT_MS, startedAtEpochMs)
      .apply()
    _activeRouteSessionIdFlow.value = sessionId
  }

  fun endRouteShiftSession() {
    prefs
      .edit()
      .remove(KEY_ACTIVE_ROUTE_SESSION_ID)
      .remove(KEY_ROUTE_STARTED_AT_MS)
      .apply()
    _activeRouteSessionIdFlow.value = null
  }

  fun updateLastRouteSyncEpochMs(epochMs: Long) {
    prefs.edit().putLong(KEY_LAST_ROUTE_SYNC_MS, epochMs).apply()
    _lastRouteSyncEpochMsFlow.value = epochMs
  }

  fun clearAll() {
    inMemoryAccessToken = null
    prefs.edit().clear().apply()
    _sessionFlow.value = null
    _dutyStatusFlow.value = DutyStatus.OFF_DUTY
    _activeRouteSessionIdFlow.value = null
    _lastRouteSyncEpochMsFlow.value = null
  }

  private fun loadActiveRouteSessionId(): String? =
    prefs.getString(KEY_ACTIVE_ROUTE_SESSION_ID, null)?.takeIf { it.isNotBlank() }

  private fun loadLastRouteSyncEpochMs(): Long? {
    val v = prefs.getLong(KEY_LAST_ROUTE_SYNC_MS, 0L)
    return if (v > 0L) v else null
  }

  private fun loadDutyStatus(): DutyStatus {
    val name = prefs.getString(KEY_DUTY_STATUS, DutyStatus.ON_DUTY.name)
    return runCatching { DutyStatus.valueOf(name ?: "") }.getOrDefault(DutyStatus.ON_DUTY)
  }

  private fun loadWorkHoursSchedule(): WorkHoursScheduleConfig {
    return WorkHoursScheduleConfig(
      enabled = prefs.getBoolean(KEY_SCHED_ENABLED, true),
      startHour = prefs.getInt(KEY_SCHED_START_H, 8),
      startMinute = prefs.getInt(KEY_SCHED_START_M, 30),
      endHour = prefs.getInt(KEY_SCHED_END_H, 18),
      endMinute = prefs.getInt(KEY_SCHED_END_M, 30),
      weekdaysOnly = prefs.getBoolean(KEY_SCHED_WEEKDAYS, true),
    )
  }

  private fun loadSessionFromPrefs(): UserSession? {
    val token = prefs.getString(KEY_ACCESS_TOKEN, null) ?: return null
    val userId = prefs.getString(KEY_USER_ID, null) ?: return null
    val email = prefs.getString(KEY_USER_EMAIL, null) ?: return null
    val roleName = prefs.getString(KEY_USER_ROLE, UserRole.HR_SELF_SERVICE.name)
    val employeeId = prefs.getString(KEY_EMPLOYEE_ID, null) ?: return null
    inMemoryAccessToken = token
    val role = runCatching { UserRole.valueOf(roleName ?: "") }.getOrDefault(UserRole.HR_SELF_SERVICE)
    val capsRaw = prefs.getString(KEY_CAPABILITIES, null)
    val capabilities =
      if (capsRaw.isNullOrBlank()) {
        RoleBundle.FIELD_TECHNICIAN.capabilities
      } else {
        capsRaw.split(",").mapNotNull { AppCapability.fromKey(it.trim()) }.toSet()
          .ifEmpty { RoleBundle.FIELD_TECHNICIAN.capabilities }
      }
    val bundlesRaw = prefs.getString(KEY_ASSIGNED_BUNDLES, null)
    val assignedBundles =
      if (bundlesRaw.isNullOrBlank()) listOf(RoleBundle.FIELD_TECHNICIAN.displayName)
      else bundlesRaw.split("|").filter { it.isNotBlank() }
    val branchScope =
      prefs.getString(KEY_BRANCH_SCOPE, null) ?: RoleBundle.FIELD_TECHNICIAN.defaultBranchScope
    val deviceId = prefs.getString(KEY_DEVICE_ID, null) ?: "dev_and_primary"
    val lastAuth = prefs.getLong(KEY_LAST_ONLINE_AUTH_MS, System.currentTimeMillis())

    return UserSession(
      userId = userId,
      email = email,
      role = role,
      employeeId = employeeId,
      isAuthenticated = true,
      capabilities = capabilities,
      assignedBundles = assignedBundles,
      branchScope = branchScope,
      deviceId = deviceId,
      lastOnlineSignInEpochMs = lastAuth,
    )
  }

  private fun sha256(input: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
    return digest.joinToString("") { "%02x".format(it) }
  }

  companion object {
    const val OFFLINE_TOKEN_WINDOW_MS = 24L * 60L * 60L * 1000L // 24 hours
    private const val PREFS_FILE_NAME = "pulsehr_encrypted_prefs"
    private const val KEY_ACCESS_TOKEN = "jwt_access_token"
    private const val KEY_REFRESH_TOKEN = "jwt_refresh_token"
    private const val KEY_EXPIRES_AT = "token_expires_at"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_USER_EMAIL = "user_email"
    private const val KEY_USER_ROLE = "user_role"
    private const val KEY_EMPLOYEE_ID = "employee_id"
    private const val KEY_CAPABILITIES = "user_capabilities_csv"
    private const val KEY_ASSIGNED_BUNDLES = "user_assigned_bundles"
    private const val KEY_BRANCH_SCOPE = "user_branch_scope"
    private const val KEY_DEVICE_ID = "registered_device_id"
    private const val KEY_LAST_ONLINE_AUTH_MS = "last_online_auth_epoch_ms"
    private const val KEY_DUTY_STATUS = "duty_status"
    private const val KEY_SCHED_ENABLED = "geofence_sched_enabled"
    private const val KEY_SCHED_START_H = "geofence_sched_start_h"
    private const val KEY_SCHED_START_M = "geofence_sched_start_m"
    private const val KEY_SCHED_END_H = "geofence_sched_end_h"
    private const val KEY_SCHED_END_M = "geofence_sched_end_m"
    private const val KEY_SCHED_WEEKDAYS = "geofence_sched_weekdays"
    private const val KEY_LAST_GEO_INSIDE = "geofence_last_inside"
    private const val KEY_LAST_REMINDER_KEY = "geofence_last_reminder_key"
    private const val KEY_PIN_SALT = "pin_salt"
    private const val KEY_PIN_HASH = "pin_hash"
    private const val KEY_FCM_TOKEN = "fcm_registration_token"
    private const val KEY_TOPIC_LEAVE = "fcm_topic_leave_updates"
    private const val KEY_TOPIC_PAYROLL = "fcm_topic_payroll_alerts"
    private const val KEY_VOICE_AUTH_PHRASE = "voice_auth_phrase"
    private const val KEY_ACTIVE_ROUTE_SESSION_ID = "active_route_session_id"
    private const val KEY_ROUTE_STARTED_AT_MS = "active_route_started_at_ms"
    private const val KEY_LAST_ROUTE_SYNC_MS = "last_route_sync_epoch_ms"
    const val DEFAULT_VOICE_AUTH_PHRASE = "Pulse check in"

    @VisibleForTesting
    fun createForTesting(prefs: SharedPreferences): SecureTokenStore = SecureTokenStore(prefs)

    private fun createEncryptedPrefs(context: Context): SharedPreferences {
      val masterKey =
        MasterKey.Builder(context)
          .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
          .build()

      return EncryptedSharedPreferences.create(
        context,
        PREFS_FILE_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
      )
    }
  }
}
