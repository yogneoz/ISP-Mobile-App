package com.example.notifications

import android.content.Context
import android.util.Log
import com.example.data.remote.PulseHrApiService
import com.example.data.remote.RegisterFcmTokenRequestDto
import com.example.security.SecureTokenStore
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class FcmStatus(
  val isFirebaseConfigured: Boolean = false,
  val fcmToken: String? = null,
  val leaveAlertsEnabled: Boolean = true,
  val payrollAlertsEnabled: Boolean = true,
  val registrationState: String = "Awaiting Firebase configuration",
)

@Singleton
class FcmTokenManager @Inject constructor(
  @ApplicationContext private val context: Context,
  private val secureTokenStore: SecureTokenStore,
  private val apiService: PulseHrApiService,
) {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  private val _fcmStatus = MutableStateFlow(buildInitialStatus())
  val fcmStatus: StateFlow<FcmStatus> = _fcmStatus.asStateFlow()

  private fun buildInitialStatus(): FcmStatus {
    val configured = isFirebaseInitialized()
    val storedToken = secureTokenStore.getFcmToken()
    return FcmStatus(
      isFirebaseConfigured = configured,
      fcmToken = storedToken,
      leaveAlertsEnabled = secureTokenStore.isLeaveAlertsEnabled(),
      payrollAlertsEnabled = secureTokenStore.isPayrollAlertsEnabled(),
      registrationState =
        when {
          !configured -> "Add google-services.json from Firebase Console"
          !storedToken.isNullOrBlank() -> "Active • Registered with backend"
          else -> "Fetching FCM registration token..."
        },
    )
  }

  fun isFirebaseInitialized(): Boolean = FirebaseApp.getApps(context).isNotEmpty()

  fun initializeAndSyncToken() {
    val configured = isFirebaseInitialized()
    if (!configured) {
      _fcmStatus.value = buildInitialStatus()
      return
    }

    val messaging = FirebaseMessaging.getInstance()
    syncTopicSubscriptions(messaging)

    messaging.token.addOnCompleteListener { task ->
      if (!task.isSuccessful) {
        Log.w(TAG, "Fetching FCM token failed", task.exception)
        _fcmStatus.value =
          _fcmStatus.value.copy(
            isFirebaseConfigured = true,
            registrationState = "Token fetch failed: ${task.exception?.localizedMessage ?: "Unknown"}",
          )
        return@addOnCompleteListener
      }

      val token = task.result
      if (!token.isNullOrBlank()) {
        onTokenReceived(token)
      }
    }
  }

  fun onTokenReceived(token: String) {
    secureTokenStore.saveFcmToken(token)
    _fcmStatus.value =
      _fcmStatus.value.copy(
        isFirebaseConfigured = isFirebaseInitialized(),
        fcmToken = token,
        registrationState = "Syncing token with HR backend...",
      )

    scope.launch {
      val topics = buildActiveTopics()
      val registered =
        runCatching {
          val response =
            apiService.registerFcmToken(
              RegisterFcmTokenRequestDto(fcmToken = token, topics = topics)
            )
          response.isSuccessful
        }.getOrDefault(false)

      _fcmStatus.value =
        _fcmStatus.value.copy(
          registrationState =
            if (registered) "Active • Registered with backend"
            else "Token saved locally; backend sync pending",
        )
    }
  }

  fun setLeaveAlertsEnabled(enabled: Boolean) {
    secureTokenStore.setLeaveAlertsEnabled(enabled)
    _fcmStatus.value = _fcmStatus.value.copy(leaveAlertsEnabled = enabled)
    if (isFirebaseInitialized()) {
      val messaging = FirebaseMessaging.getInstance()
      if (enabled) messaging.subscribeToTopic(TOPIC_LEAVE_UPDATES)
      else messaging.unsubscribeFromTopic(TOPIC_LEAVE_UPDATES)
      secureTokenStore.getFcmToken()?.let { onTokenReceived(it) }
    }
  }

  fun setPayrollAlertsEnabled(enabled: Boolean) {
    secureTokenStore.setPayrollAlertsEnabled(enabled)
    _fcmStatus.value = _fcmStatus.value.copy(payrollAlertsEnabled = enabled)
    if (isFirebaseInitialized()) {
      val messaging = FirebaseMessaging.getInstance()
      if (enabled) messaging.subscribeToTopic(TOPIC_PAYROLL_ALERTS)
      else messaging.unsubscribeFromTopic(TOPIC_PAYROLL_ALERTS)
      secureTokenStore.getFcmToken()?.let { onTokenReceived(it) }
    }
  }

  private fun syncTopicSubscriptions(messaging: FirebaseMessaging) {
    if (secureTokenStore.isLeaveAlertsEnabled()) {
      messaging.subscribeToTopic(TOPIC_LEAVE_UPDATES)
    } else {
      messaging.unsubscribeFromTopic(TOPIC_LEAVE_UPDATES)
    }
    if (secureTokenStore.isPayrollAlertsEnabled()) {
      messaging.subscribeToTopic(TOPIC_PAYROLL_ALERTS)
    } else {
      messaging.unsubscribeFromTopic(TOPIC_PAYROLL_ALERTS)
    }
  }

  private fun buildActiveTopics(): List<String> {
    return buildList {
      if (secureTokenStore.isLeaveAlertsEnabled()) add(TOPIC_LEAVE_UPDATES)
      if (secureTokenStore.isPayrollAlertsEnabled()) add(TOPIC_PAYROLL_ALERTS)
    }
  }

  companion object {
    private const val TAG = "FcmTokenManager"
    const val TOPIC_LEAVE_UPDATES = "leave_updates"
    const val TOPIC_PAYROLL_ALERTS = "payroll_alerts"
  }
}
