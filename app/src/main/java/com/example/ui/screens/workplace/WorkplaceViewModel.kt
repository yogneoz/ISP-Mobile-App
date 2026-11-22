package com.example.ui.screens.workplace

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.domain.model.ApprovalItem
import com.example.domain.model.AuditLogEntry
import com.example.domain.model.ClaimCategory
import com.example.domain.model.CompanyAnnouncement
import com.example.domain.model.DelegationRule
import com.example.domain.model.ExpenseClaim
import com.example.domain.model.NeedsAttentionItem
import com.example.domain.model.RosterEntry
import com.example.domain.model.StockAuditRow
import com.example.domain.model.VanStockItem
import com.example.domain.model.WorkOrder
import com.example.domain.repository.EmployeeRepository
import com.example.domain.repository.RepoResult
import com.example.domain.repository.WorkplaceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class WorkplaceViewModel @Inject constructor(
  private val workplaceRepository: WorkplaceRepository,
  private val employeeRepository: EmployeeRepository,
) : ViewModel() {

  val workOrders: StateFlow<List<WorkOrder>> =
    workplaceRepository
      .observeWorkOrders()
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

  val vanStock: StateFlow<List<VanStockItem>> =
    workplaceRepository
      .observeVanStock()
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

  val claims: StateFlow<List<ExpenseClaim>> =
    workplaceRepository
      .observeClaims()
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

  val approvals: StateFlow<List<ApprovalItem>> =
    workplaceRepository
      .observeApprovalItems()
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

  val delegations: StateFlow<List<DelegationRule>> =
    workplaceRepository
      .observeDelegations()
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

  val rosterEntries: StateFlow<List<RosterEntry>> =
    workplaceRepository
      .observeRosterEntries()
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

  val needsAttention: StateFlow<List<NeedsAttentionItem>> =
    workplaceRepository
      .observeNeedsAttention()
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

  val auditLogs: StateFlow<List<AuditLogEntry>> =
    workplaceRepository
      .observeAuditLogs()
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

  val stockAuditRows: StateFlow<List<StockAuditRow>> =
    workplaceRepository
      .observeStockAuditRows()
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

  val announcements: StateFlow<List<CompanyAnnouncement>> =
    employeeRepository
      .observeAnnouncements()
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

  private val _feedback = MutableStateFlow<Pair<String, Boolean>?>(null)
  val feedback: StateFlow<Pair<String, Boolean>?> = _feedback.asStateFlow()

  fun completeWorkOrder(
    workOrderId: String,
    opticalPowerDbm: Double,
    otdrLossDb: Double,
    customerSignatureName: String,
    consumedCableMeters: Int,
    serialUsed: String?,
    photoCount: Int,
    poleRouteNote: String?,
  ) {
    viewModelScope.launch {
      when (
        val res =
          workplaceRepository.completeWorkOrder(
            workOrderId = workOrderId,
            opticalPowerDbm = opticalPowerDbm,
            otdrLossDb = otdrLossDb,
            customerSignatureName = customerSignatureName,
            consumedCableMeters = consumedCableMeters,
            serialUsed = serialUsed,
            photoCount = photoCount,
            poleRouteNote = poleRouteNote,
          )
      ) {
        is RepoResult.Success ->
          _feedback.value =
            "${res.data.ticketCode} ${res.data.status.label} • Accounting outbox queued." to false
        is RepoResult.Error -> _feedback.value = res.message to true
      }
    }
  }

  fun addPoleRouteLog(workOrderId: String, note: String) {
    viewModelScope.launch {
      when (val res = workplaceRepository.addPoleRouteLog(workOrderId, note)) {
        is RepoResult.Success -> _feedback.value = "Route & pole log saved offline-first." to false
        is RepoResult.Error -> _feedback.value = res.message to true
      }
    }
  }

  fun requestVanRestock(itemId: String, addQty: Int) {
    viewModelScope.launch {
      when (val res = workplaceRepository.requestVanRestock(itemId, addQty)) {
        is RepoResult.Success -> _feedback.value = "Van restock posted (+$addQty units)." to false
        is RepoResult.Error -> _feedback.value = res.message to true
      }
    }
  }

  fun submitExpenseClaim(
    category: ClaimCategory,
    amountText: String,
    ticketCode: String,
    description: String,
    receiptAttached: Boolean,
  ) {
    val amount = amountText.toDoubleOrNull() ?: 0.0
    viewModelScope.launch {
      when (
        val res =
          workplaceRepository.submitExpenseClaim(
            category = category,
            amount = amount,
            ticketCode = ticketCode,
            description = description,
            receiptAttached = receiptAttached,
          )
      ) {
        is RepoResult.Success ->
          _feedback.value =
            "${res.data.category.label} claim $${res.data.amount} submitted (${res.data.syncStatus.label})." to false
        is RepoResult.Error -> _feedback.value = res.message to true
      }
    }
  }

  fun actOnApproval(approvalId: String, approve: Boolean, note: String) {
    viewModelScope.launch {
      when (val res = workplaceRepository.actOnApprovalItem(approvalId, approve, note)) {
        is RepoResult.Success ->
          _feedback.value =
            (if (approve) "Approved" else "Declined") + " & logged to audit stream." to false
        is RepoResult.Error -> _feedback.value = res.message to true
      }
    }
  }

  fun createDelegation(delegateEmployeeId: String, delegateName: String, endDate: String) {
    viewModelScope.launch {
      when (val res = workplaceRepository.createDelegation(delegateEmployeeId, delegateName, endDate)) {
        is RepoResult.Success ->
          _feedback.value = "Approval authority delegated to $delegateName until $endDate." to false
        is RepoResult.Error -> _feedback.value = res.message to true
      }
    }
  }

  fun assignDispatchTicket(
    workOrderId: String,
    assigneeEmployeeId: String,
    assigneeName: String,
    overrideLeaveWarning: Boolean,
    overrideReason: String?,
  ) {
    viewModelScope.launch {
      when (
        val res =
          workplaceRepository.assignDispatchTicket(
            workOrderId = workOrderId,
            assigneeEmployeeId = assigneeEmployeeId,
            assigneeName = assigneeName,
            overrideLeaveWarning = overrideLeaveWarning,
            overrideReason = overrideReason,
          )
      ) {
        is RepoResult.Success ->
          _feedback.value =
            "Ticket assigned to $assigneeName" +
              (if (overrideLeaveWarning) " (leave override logged)." else ".") to false
        is RepoResult.Error -> _feedback.value = res.message to true
      }
    }
  }

  fun resolveNeedsAttention(id: String, correctedSerial: String?) {
    viewModelScope.launch {
      when (val res = workplaceRepository.resolveNeedsAttentionItem(id, correctedSerial)) {
        is RepoResult.Success -> _feedback.value = res.data to false
        is RepoResult.Error -> _feedback.value = res.message to true
      }
    }
  }

  fun clearFeedback() {
    _feedback.value = null
  }
}
