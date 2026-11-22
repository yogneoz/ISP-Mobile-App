package com.example.ui

import android.content.Context
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.domain.model.AuthChallengePurpose
import com.example.domain.model.DutyStatus
import com.example.domain.model.OfflineAccessMode
import com.example.domain.model.RoleBundle
import com.example.domain.model.UserRole
import com.example.domain.model.UserSession
import com.example.domain.repository.AttendanceRepository
import com.example.domain.repository.AuthRepository
import com.example.domain.repository.EmployeeRepository
import com.example.domain.repository.RepoResult
import com.example.domain.repository.RouteTrackingRepository
import com.example.domain.repository.WorkplaceRepository
import com.example.notifications.FcmTokenManager
import com.example.security.BiometricAuditRecorder
import com.example.security.BiometricAuthOutcome
import com.example.security.BiometricAvailability
import com.example.security.BiometricPromptInterface
import com.example.security.SecureTokenStore
import com.example.security.SessionManager
import com.example.worker.SyncQueueProcessor
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PinChallengeState(
  val purpose: AuthChallengePurpose,
  val isSettingNewPin: Boolean,
  val errorMessage: String? = null,
  val onSuccess: () -> Unit,
)

data class SyncStatusSummary(
  val queuedCount: Int = 0,
  val failedCount: Int = 0,
  val isUploading: Boolean = false,
  val isOfflineSimulated: Boolean = false,
  val queueCapWarning: Boolean = false,
  val offlineAccessMode: OfflineAccessMode = OfflineAccessMode.ONLINE,
)

@HiltViewModel
class MainViewModel @Inject constructor(
  @ApplicationContext private val context: Context,
  private val authRepository: AuthRepository,
  private val employeeRepository: EmployeeRepository,
  private val attendanceRepository: AttendanceRepository,
  private val workplaceRepository: WorkplaceRepository,
  private val routeTrackingRepository: RouteTrackingRepository,
  private val sessionManager: SessionManager,
  private val tokenStore: SecureTokenStore,
  private val biometricPrompt: BiometricPromptInterface,
  private val auditRecorder: BiometricAuditRecorder,
  private val fcmTokenManager: FcmTokenManager,
  private val syncQueueProcessor: SyncQueueProcessor,
) : ViewModel() {

  init {
    viewModelScope.launch {
      employeeRepository.ensureSessionDataSeeded()
    }
  }

  val session: StateFlow<UserSession?> =
    authRepository.sessionFlow.stateIn(
      viewModelScope,
      SharingStarted.WhileSubscribed(5_000),
      authRepository.getCurrentSession(),
    )

  val isAppLocked: StateFlow<Boolean> = sessionManager.isAppLocked
  val isPayrollUnlocked: StateFlow<Boolean> = sessionManager.isPayrollUnlocked
  val lastLockReason: StateFlow<String?> = sessionManager.lastLockReason
  val isOfflineSimulated: StateFlow<Boolean> = tokenStore.offlineSimulationFlow
  val dutyStatus: StateFlow<DutyStatus> = tokenStore.dutyStatusFlow

  val pendingSyncCount: StateFlow<Int> =
    attendanceRepository
      .observePendingSyncActions()
      .map { it.size }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

  val unresolvedNeedsAttentionCount: StateFlow<Int> =
    workplaceRepository
      .observeNeedsAttention()
      .map { list -> list.count { !it.resolved } }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

  val syncStatusSummary: StateFlow<SyncStatusSummary> =
    combine(
      attendanceRepository.observePendingSyncActions(),
      syncQueueProcessor.isUploading,
      tokenStore.offlineSimulationFlow,
    ) { actions, uploading, offline ->
      val failed = actions.count { it.retryCount > 0 || it.stateLabel == "FAILED" }
      SyncStatusSummary(
        queuedCount = actions.size,
        failedCount = failed,
        isUploading = uploading,
        isOfflineSimulated = offline,
        queueCapWarning = actions.size >= SyncQueueProcessor.QUEUE_CAP_WARNING_THRESHOLD,
        offlineAccessMode = tokenStore.getOfflineAccessMode(),
      )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SyncStatusSummary())

  private val _loginLoading = MutableStateFlow(false)
  val loginLoading: StateFlow<Boolean> = _loginLoading.asStateFlow()

  private val _loginError = MutableStateFlow<String?>(null)
  val loginError: StateFlow<String?> = _loginError.asStateFlow()

  private val _pinChallenge = MutableStateFlow<PinChallengeState?>(null)
  val pinChallenge: StateFlow<PinChallengeState?> = _pinChallenge.asStateFlow()

  private val _syncMessage = MutableStateFlow<String?>(null)
  val syncMessage: StateFlow<String?> = _syncMessage.asStateFlow()

  private val _biometricEntryError = MutableStateFlow<String?>(null)
  val biometricEntryError: StateFlow<String?> = _biometricEntryError.asStateFlow()

  private val _biometricPromptActive = MutableStateFlow(false)
  val biometricPromptActive: StateFlow<Boolean> = _biometricPromptActive.asStateFlow()

  fun login(
    email: String,
    password: String,
    bundles: List<RoleBundle>,
    initialPin: String,
  ) {
    if (initialPin.length != 4 || initialPin.any { !it.isDigit() }) {
      _loginError.value = "Set a 4-digit numeric security PIN for biometric fallback."
      return
    }
    if (bundles.isEmpty()) {
      _loginError.value = "Select at least one capability bundle."
      return
    }
    val derivedRole = RoleBundle.highestRoleForBundles(bundles)
    viewModelScope.launch {
      _loginLoading.value = true
      _loginError.value = null
      _biometricEntryError.value = null
      when (val result = authRepository.login(email, password, derivedRole, bundles)) {
        is RepoResult.Success -> {
          authRepository.savePin(initialPin)
          fcmTokenManager.initializeAndSyncToken()
        }
        is RepoResult.Error -> {
          _loginError.value = result.message
        }
      }
      _loginLoading.value = false
    }
  }

  fun setDutyStatus(status: DutyStatus) {
    tokenStore.setDutyStatus(status)
    _syncMessage.value = "Duty status updated: ${status.label}"
  }

  fun logout() {
    viewModelScope.launch {
      _biometricEntryError.value = null
      authRepository.logout()
    }
  }

  fun lockAppNow() {
    _biometricEntryError.value = null
    sessionManager.lockSession("Manually locked by employee.")
  }

  fun setOfflineSimulation(enabled: Boolean) {
    tokenStore.setOfflineSimulation(enabled)
    if (!enabled) {
      syncPendingNow()
    } else {
      sessionManager.enforceOfflineTokenWindow()
    }
  }

  fun syncPendingNow() {
    viewModelScope.launch {
      val queueRes = attendanceRepository.syncPendingActions()
      val locRes = routeTrackingRepository.syncPendingLocationsToDatabase()
      val queueCount = (queueRes as? RepoResult.Success)?.data ?: 0
      val locCount = (locRes as? RepoResult.Success)?.data ?: 0
      when {
        queueCount + locCount > 0 ->
          _syncMessage.value =
            "Synced $queueCount HR action(s) and $locCount location waypoint(s) to database."
        queueRes is RepoResult.Error -> _syncMessage.value = queueRes.message
        locRes is RepoResult.Error -> _syncMessage.value = locRes.message
      }
    }
  }

  fun clearSyncMessage() {
    _syncMessage.value = null
  }

  fun logSensitiveScreenView(screenName: String, actionLabel: String) {
    viewModelScope.launch {
      workplaceRepository.logSensitiveScreenView(screenName, actionLabel)
    }
  }

  fun hasConfiguredPin(): Boolean = authRepository.hasConfiguredPin()

  fun canUseBiometrics(): Boolean = biometricPrompt.canAuthenticateWithBiometrics(context)

  fun getBiometricAvailability(): BiometricAvailability =
    biometricPrompt.getAvailabilityStatus(context)

  fun requestAppEntryBiometric(activity: FragmentActivity, employeeEmail: String) {
    if (_biometricPromptActive.value) return
    viewModelScope.launch {
      _biometricPromptActive.value = true
      _biometricEntryError.value = null
      try {
        when (val outcome = biometricPrompt.authenticateForAppEntry(activity, employeeEmail)) {
          BiometricAuthOutcome.Succeeded -> {
            _biometricEntryError.value = null
            sessionManager.unlockSession()
          }
          BiometricAuthOutcome.UsePinFallback -> {
            _biometricEntryError.value =
              "Switched to fallback PIN. Enter your 4-digit security PIN below."
          }
          BiometricAuthOutcome.Cancelled -> {
            _biometricEntryError.value =
              "Fingerprint or face verification cancelled. Tap Verify to retry or enter your App PIN."
          }
          is BiometricAuthOutcome.Error -> {
            _biometricEntryError.value = outcome.message
          }
        }
      } finally {
        _biometricPromptActive.value = false
      }
    }
  }

  fun requestAuthentication(
    activity: FragmentActivity?,
    purpose: AuthChallengePurpose,
    onAuthenticated: () -> Unit,
    onError: (String) -> Unit = {},
  ) {
    if (activity == null) {
      openPinDialog(purpose, isSettingNewPin = !hasConfiguredPin(), onSuccess = onAuthenticated)
      return
    }

    biometricPrompt.authenticate(
      activity = activity,
      purpose = purpose,
      onSuccess = {
        if (purpose == AuthChallengePurpose.APP_UNLOCK) sessionManager.unlockSession()
        if (purpose == AuthChallengePurpose.PAYSLIP_VIEW) {
          sessionManager.unlockPayroll()
          logSensitiveScreenView("PAYSLIP_VIEW", "Unlocked payslip via BiometricPrompt")
        }
        if (purpose == AuthChallengePurpose.SENSITIVE_DOC_VIEW) {
          logSensitiveScreenView("DOCUMENT_VIEW", "Unlocked compliance document via BiometricPrompt")
        }
        onAuthenticated()
      },
      onUsePinFallback = {
        openPinDialog(purpose, isSettingNewPin = !hasConfiguredPin(), onSuccess = onAuthenticated)
      },
      onError = onError,
    )
  }

  fun openPinDialog(
    purpose: AuthChallengePurpose,
    isSettingNewPin: Boolean = false,
    onSuccess: () -> Unit,
  ) {
    _pinChallenge.value =
      PinChallengeState(
        purpose = purpose,
        isSettingNewPin = isSettingNewPin,
        errorMessage = null,
        onSuccess = onSuccess,
      )
  }

  fun submitPin(pin: String) {
    val current = _pinChallenge.value ?: return
    if (current.isSettingNewPin) {
      if (pin.length == 4) {
        authRepository.savePin(pin)
        _pinChallenge.value = null
        if (current.purpose == AuthChallengePurpose.APP_UNLOCK) sessionManager.unlockSession()
        if (current.purpose == AuthChallengePurpose.PAYSLIP_VIEW) {
          sessionManager.unlockPayroll()
          logSensitiveScreenView("PAYSLIP_VIEW", "Unlocked payslip after PIN setup")
        }
        current.onSuccess()
      }
      return
    }

    if (authRepository.verifyPin(pin)) {
      auditRecorder.recordPinFallbackAttempt(current.purpose.title, success = true)
      _pinChallenge.value = null
      if (current.purpose == AuthChallengePurpose.APP_UNLOCK) sessionManager.unlockSession()
      if (current.purpose == AuthChallengePurpose.PAYSLIP_VIEW) {
        sessionManager.unlockPayroll()
        logSensitiveScreenView("PAYSLIP_VIEW", "Unlocked payslip via PIN fallback")
      }
      if (current.purpose == AuthChallengePurpose.SENSITIVE_DOC_VIEW) {
        logSensitiveScreenView("DOCUMENT_VIEW", "Unlocked compliance document via PIN fallback")
      }
      current.onSuccess()
    } else {
      auditRecorder.recordPinFallbackAttempt(current.purpose.title, success = false)
      _pinChallenge.value = current.copy(errorMessage = "Incorrect PIN. Try again.")
    }
  }

  fun verifyAppUnlockPin(pin: String): Boolean {
    val ok = authRepository.verifyPin(pin)
    auditRecorder.recordPinFallbackAttempt(AuthChallengePurpose.APP_UNLOCK.title, success = ok)
    if (ok) {
      _biometricEntryError.value = null
      sessionManager.unlockSession()
    }
    return ok
  }

  fun relockPayroll() {
    sessionManager.lockPayroll()
  }

  fun dismissPinDialog() {
    _pinChallenge.value = null
  }
}
