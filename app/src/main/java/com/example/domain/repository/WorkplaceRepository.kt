package com.example.domain.repository

import com.example.domain.model.ApprovalItem
import com.example.domain.model.AuditLogEntry
import com.example.domain.model.ClaimCategory
import com.example.domain.model.DelegationRule
import com.example.domain.model.EmployeeShift
import com.example.domain.model.ExpenseClaim
import com.example.domain.model.NeedsAttentionItem
import com.example.domain.model.RosterEntry
import com.example.domain.model.StockAuditRow
import com.example.domain.model.VanStockItem
import com.example.domain.model.WorkOrder
import kotlinx.coroutines.flow.Flow

interface WorkplaceRepository {
  fun observeWorkOrders(): Flow<List<WorkOrder>>

  fun observeVanStock(): Flow<List<VanStockItem>>

  fun observeClaims(): Flow<List<ExpenseClaim>>

  fun observeApprovalItems(): Flow<List<ApprovalItem>>

  fun observeDelegations(): Flow<List<DelegationRule>>

  fun observeRosterEntries(): Flow<List<RosterEntry>>

  fun observeNeedsAttention(): Flow<List<NeedsAttentionItem>>

  fun observeAuditLogs(): Flow<List<AuditLogEntry>>

  fun observeStockAuditRows(): Flow<List<StockAuditRow>>

  fun observeEmployeeShifts(): Flow<List<EmployeeShift>>

  suspend fun syncShiftsFromBootstrap(shifts: List<EmployeeShift>): RepoResult<Unit>

  suspend fun completeWorkOrder(
    workOrderId: String,
    opticalPowerDbm: Double,
    otdrLossDb: Double,
    customerSignatureName: String,
    consumedCableMeters: Int,
    serialUsed: String?,
    photoCount: Int,
    poleRouteNote: String?,
  ): RepoResult<WorkOrder>

  suspend fun addPoleRouteLog(workOrderId: String, note: String): RepoResult<Unit>

  suspend fun requestVanRestock(itemId: String, addQty: Int): RepoResult<Unit>

  suspend fun submitExpenseClaim(
    category: ClaimCategory,
    amount: Double,
    ticketCode: String?,
    description: String,
    receiptAttached: Boolean,
  ): RepoResult<ExpenseClaim>

  suspend fun actOnApprovalItem(
    approvalId: String,
    approve: Boolean,
    note: String,
  ): RepoResult<Unit>

  suspend fun createDelegation(
    delegateEmployeeId: String,
    delegateName: String,
    endDate: String,
  ): RepoResult<Unit>

  suspend fun assignDispatchTicket(
    workOrderId: String,
    assigneeEmployeeId: String,
    assigneeName: String,
    overrideLeaveWarning: Boolean,
    overrideReason: String?,
  ): RepoResult<Unit>

  suspend fun resolveNeedsAttentionItem(
    needsAttentionId: String,
    correctedSerial: String? = null,
  ): RepoResult<String>

  suspend fun logSensitiveScreenView(screenName: String, actionLabel: String)
}
