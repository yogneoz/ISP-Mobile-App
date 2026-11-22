package com.example.security

import android.content.Context
import android.os.SystemClock
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.example.domain.model.OfflineAccessMode
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class SessionManager @Inject constructor(
  @ApplicationContext private val context: Context,
  private val secureTokenStore: SecureTokenStore,
  private val biometricPrompt: BiometricPromptInterface,
) : DefaultLifecycleObserver {

  private var backgroundedAtElapsedMs: Long? = null

  private val _isAppLocked = MutableStateFlow(secureTokenStore.hasActiveSession())
  val isAppLocked: StateFlow<Boolean> = _isAppLocked.asStateFlow()

  private val _isPayrollUnlocked = MutableStateFlow(false)
  val isPayrollUnlocked: StateFlow<Boolean> = _isPayrollUnlocked.asStateFlow()

  private val _lastLockReason =
    MutableStateFlow(
      if (secureTokenStore.hasActiveSession()) {
        "Verify fingerprint or facial recognition for secure application entry."
      } else {
        null
      }
    )
  val lastLockReason: StateFlow<String?> = _lastLockReason.asStateFlow()

  override fun onStop(owner: LifecycleOwner) {
    if (secureTokenStore.hasActiveSession()) {
      backgroundedAtElapsedMs = SystemClock.elapsedRealtime()
    }
  }

  override fun onStart(owner: LifecycleOwner) {
    val stoppedAt = backgroundedAtElapsedMs ?: return
    backgroundedAtElapsedMs = null
    val elapsedMs = SystemClock.elapsedRealtime() - stoppedAt
    if (secureTokenStore.hasActiveSession()) {
      if (secureTokenStore.getOfflineAccessMode() == OfflineAccessMode.OFFLINE_LOCKED) {
        lockSession("Offline token exceeded 24h window. Reconnect and re-authenticate.")
      } else if (elapsedMs > SESSION_TIMEOUT_MS) {
        lockSession("Locked automatically after 2+ minutes in background.")
      }
    }
  }

  fun enforceOfflineTokenWindow(): Boolean {
    if (
      secureTokenStore.hasActiveSession() &&
        secureTokenStore.getOfflineAccessMode() == OfflineAccessMode.OFFLINE_LOCKED
    ) {
      lockSession("24-hour offline token expired. Connect to network to re-verify device session.")
      return false
    }
    return true
  }

  fun lockSession(reason: String = "Session locked for security.") {
    secureTokenStore.clearSensitiveInMemoryCache()
    _isPayrollUnlocked.value = false
    if (secureTokenStore.hasActiveSession()) {
      _lastLockReason.value = reason
      _isAppLocked.value = true
    }
  }

  fun unlockSession() {
    _lastLockReason.value = null
    _isAppLocked.value = false
  }

  fun onFreshLogin() {
    backgroundedAtElapsedMs = null
    _isPayrollUnlocked.value = false
    val requireBiometricGate = biometricPrompt.canAuthenticateWithBiometrics(context)
    if (requireBiometricGate) {
      _lastLockReason.value = "Verify fingerprint or facial recognition to enter PulseHR."
      _isAppLocked.value = true
    } else {
      _lastLockReason.value = null
      _isAppLocked.value = false
    }
  }

  fun onLogout() {
    backgroundedAtElapsedMs = null
    _lastLockReason.value = null
    _isAppLocked.value = false
    _isPayrollUnlocked.value = false
  }

  fun unlockPayroll() {
    _isPayrollUnlocked.value = true
  }

  fun lockPayroll() {
    _isPayrollUnlocked.value = false
  }

  companion object {
    const val SESSION_TIMEOUT_MS = 120_000L // 2 minutes
  }
}
