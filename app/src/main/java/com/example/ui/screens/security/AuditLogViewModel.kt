package com.example.ui.screens.security

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.domain.model.AuditLogEntry
import com.example.domain.model.AuditOutcome
import com.example.domain.repository.WorkplaceRepository
import com.example.security.BiometricAuditRecorder
import com.example.security.BiometricAvailability
import com.example.security.BiometricPromptInterface
import com.example.security.SecureTokenStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

enum class AuditLogFilter(val label: String) {
  ALL("All Events"),
  BIOMETRIC_ONLY("Biometric"),
  SUCCESS("Succeeded"),
  FAILED("Failed"),
  FALLBACK("PIN / Cancelled"),
}

data class AuditLogSummary(
  val totalEvents: Int = 0,
  val biometricSuccessCount: Int = 0,
  val biometricFailedCount: Int = 0,
  val fallbackOrCancelledCount: Int = 0,
  val deviceModel: String = "",
  val deviceId: String = "",
  val biometricAvailability: BiometricAvailability = BiometricAvailability.UNSUPPORTED,
)

@HiltViewModel
class AuditLogViewModel @Inject constructor(
  @ApplicationContext private val context: Context,
  workplaceRepository: WorkplaceRepository,
  private val auditRecorder: BiometricAuditRecorder,
  private val tokenStore: SecureTokenStore,
  private val biometricPrompt: BiometricPromptInterface,
) : ViewModel() {

  private val _selectedFilter = MutableStateFlow(AuditLogFilter.ALL)
  val selectedFilter: StateFlow<AuditLogFilter> = _selectedFilter

  private val allAuditLogs: StateFlow<List<AuditLogEntry>> =
    workplaceRepository
      .observeAuditLogs()
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

  val filteredLogs: StateFlow<List<AuditLogEntry>> =
    combine(allAuditLogs, _selectedFilter) { logs, filter ->
      when (filter) {
        AuditLogFilter.ALL -> logs
        AuditLogFilter.BIOMETRIC_ONLY ->
          logs.filter { it.eventCategory == "BIOMETRIC_AUTH" || it.eventCategory == "PIN_FALLBACK" }
        AuditLogFilter.SUCCESS ->
          logs.filter { it.outcome == AuditOutcome.SUCCESS }
        AuditLogFilter.FAILED ->
          logs.filter { it.outcome == AuditOutcome.FAILED }
        AuditLogFilter.FALLBACK ->
          logs.filter {
            it.outcome == AuditOutcome.FALLBACK || it.outcome == AuditOutcome.CANCELLED
          }
      }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

  val summary: StateFlow<AuditLogSummary> =
    combine(allAuditLogs, _selectedFilter) { logs, _ ->
      val authLogs =
        logs.filter { it.eventCategory == "BIOMETRIC_AUTH" || it.eventCategory == "PIN_FALLBACK" }
      AuditLogSummary(
        totalEvents = logs.size,
        biometricSuccessCount = authLogs.count { it.outcome == AuditOutcome.SUCCESS },
        biometricFailedCount = authLogs.count { it.outcome == AuditOutcome.FAILED },
        fallbackOrCancelledCount =
          authLogs.count {
            it.outcome == AuditOutcome.FALLBACK || it.outcome == AuditOutcome.CANCELLED
          },
        deviceModel = auditRecorder.buildDeviceInfoSummary(),
        deviceId = tokenStore.getOrCreateDeviceId(),
        biometricAvailability = biometricPrompt.getAvailabilityStatus(context),
      )
    }.stateIn(
      viewModelScope,
      SharingStarted.WhileSubscribed(5_000),
      AuditLogSummary(
        deviceModel = auditRecorder.buildDeviceInfoSummary(),
        deviceId = tokenStore.getOrCreateDeviceId(),
        biometricAvailability = biometricPrompt.getAvailabilityStatus(context),
      ),
    )

  fun setFilter(filter: AuditLogFilter) {
    _selectedFilter.value = filter
  }
}
