package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PulseHrDao {

  // Branches
  @Query("SELECT * FROM branches ORDER BY name ASC")
  fun observeBranches(): Flow<List<BranchEntity>>

  @Query("SELECT * FROM branches WHERE id = :branchId LIMIT 1")
  suspend fun getBranchById(branchId: String): BranchEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertBranches(branches: List<BranchEntity>)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertBranch(branch: BranchEntity)

  // Employees
  @Query("SELECT * FROM employees WHERE userId = :userId LIMIT 1")
  fun observeEmployeeByUserId(userId: String): Flow<EmployeeEntity?>

  @Query("SELECT * FROM employees WHERE userId = :userId LIMIT 1")
  suspend fun getEmployeeByUserId(userId: String): EmployeeEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertEmployee(employee: EmployeeEntity)

  // Attendance
  @Query("SELECT * FROM hr_attendance WHERE employeeId = :employeeId ORDER BY attendanceDateAd DESC")
  fun observeAttendance(employeeId: String): Flow<List<AttendanceEntity>>

  @Query("SELECT * FROM hr_attendance WHERE employeeId = :employeeId AND attendanceDateAd = :dateAd LIMIT 1")
  suspend fun getAttendanceForDate(employeeId: String, dateAd: String): AttendanceEntity?

  @Query("SELECT * FROM hr_attendance WHERE employeeId = :employeeId ORDER BY attendanceDateAd DESC")
  suspend fun getAttendanceHistorySync(employeeId: String): List<AttendanceEntity>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertAttendance(records: List<AttendanceEntity>)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertAttendance(record: AttendanceEntity)

  @Query("UPDATE hr_attendance SET syncStatus = :syncStatus WHERE id = :recordId")
  suspend fun updateAttendanceSyncStatus(recordId: String, syncStatus: String)

  @Query("UPDATE hr_attendance SET syncStatus = :syncStatus, riskScore = :riskScore, integrityStatus = :integrityStatus, integrityNotes = :notes WHERE id = :recordId")
  suspend fun updateAttendanceSyncAndIntegrity(
    recordId: String,
    syncStatus: String,
    riskScore: Int,
    integrityStatus: String,
    notes: String,
  )

  // Leave Balances
  @Query("SELECT * FROM hr_leave_balances WHERE employeeId = :employeeId ORDER BY leaveTypeName ASC")
  fun observeLeaveBalances(employeeId: String): Flow<List<LeaveBalanceEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertLeaveBalances(balances: List<LeaveBalanceEntity>)

  @Query(
    "UPDATE hr_leave_balances SET pendingDays = pendingDays + :delta, remainingDays = remainingDays - :delta WHERE employeeId = :employeeId AND leaveTypeId = :leaveTypeId"
  )
  suspend fun adjustLeavePendingBalance(employeeId: String, leaveTypeId: String, delta: Double)

  // Leave Requests
  @Query("SELECT * FROM hr_leave_requests WHERE isTeamRequest = 0 AND employeeId = :employeeId ORDER BY submittedAt DESC")
  fun observeMyLeaveRequests(employeeId: String): Flow<List<LeaveRequestEntity>>

  @Query("SELECT * FROM hr_leave_requests WHERE isTeamRequest = 1 ORDER BY submittedAt DESC")
  fun observeTeamLeaveRequests(): Flow<List<LeaveRequestEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertLeaveRequests(requests: List<LeaveRequestEntity>)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertLeaveRequest(request: LeaveRequestEntity)

  @Query("SELECT * FROM hr_leave_requests WHERE id = :requestId LIMIT 1")
  suspend fun getLeaveRequestById(requestId: String): LeaveRequestEntity?

  @Query("UPDATE hr_leave_requests SET status = :status, reviewerNote = :reviewerNote WHERE id = :requestId")
  suspend fun updateLeaveStatus(requestId: String, status: String, reviewerNote: String?)

  @Query("UPDATE hr_leave_requests SET status = :status, reviewerNote = :reviewerNote, syncStatus = :syncStatus WHERE id = :requestId")
  suspend fun updateLeaveStatusWithSync(
    requestId: String,
    status: String,
    reviewerNote: String?,
    syncStatus: String,
  )

  @Query("UPDATE hr_leave_requests SET syncStatus = :syncStatus WHERE id = :requestId")
  suspend fun updateLeaveSyncStatus(requestId: String, syncStatus: String)

  // Payroll
  @Query("SELECT * FROM payroll_runs WHERE employeeId = :employeeId ORDER BY id DESC")
  fun observePayrollRuns(employeeId: String): Flow<List<PayrollRunEntity>>

  @Query("SELECT * FROM payroll_run_lines WHERE employeeId = :employeeId")
  fun observePayrollRunLines(employeeId: String): Flow<List<PayrollRunLineEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertPayrollRuns(runs: List<PayrollRunEntity>)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertPayrollRunLines(lines: List<PayrollRunLineEntity>)

  // Documents
  @Query("SELECT * FROM hr_employee_documents WHERE employeeId = :employeeId ORDER BY uploadedAt DESC")
  fun observeDocuments(employeeId: String): Flow<List<EmployeeDocumentEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertDocument(document: EmployeeDocumentEntity)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertDocuments(documents: List<EmployeeDocumentEntity>)

  @Query("DELETE FROM hr_employee_documents WHERE id = :documentId")
  suspend fun deleteDocument(documentId: String)

  // Announcements
  @Query("SELECT * FROM company_announcements ORDER BY isUrgent DESC, publishedAt DESC")
  fun observeAnnouncements(): Flow<List<AnnouncementEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertAnnouncements(items: List<AnnouncementEntity>)

  // Pending Sync Queue
  @Query("SELECT * FROM pending_sync_actions ORDER BY createdAtEpochMs ASC")
  fun observePendingSyncActions(): Flow<List<PendingSyncActionEntity>>

  @Query("SELECT * FROM pending_sync_actions ORDER BY createdAtEpochMs ASC")
  suspend fun getAllPendingSyncActions(): List<PendingSyncActionEntity>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertPendingSyncAction(action: PendingSyncActionEntity)

  @Query("DELETE FROM pending_sync_actions WHERE id = :actionId")
  suspend fun deletePendingSyncAction(actionId: String)

  @Query("UPDATE pending_sync_actions SET retryCount = retryCount + 1, lastError = :error, stateLabel = :stateLabel WHERE id = :actionId")
  suspend fun markPendingSyncRetry(actionId: String, error: String, stateLabel: String = "FAILED")

  @Query("DELETE FROM pending_sync_actions")
  suspend fun clearPendingSyncActions()

  // Work Orders (v2)
  @Query("SELECT * FROM work_orders ORDER BY slaMinutesRemaining ASC")
  fun observeWorkOrders(): Flow<List<WorkOrderEntity>>

  @Query("SELECT * FROM work_orders WHERE id = :id LIMIT 1")
  suspend fun getWorkOrderById(id: String): WorkOrderEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertWorkOrders(items: List<WorkOrderEntity>)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertWorkOrder(item: WorkOrderEntity)

  // Van Stock (v2)
  @Query("SELECT * FROM van_stock ORDER BY category ASC, itemName ASC")
  fun observeVanStock(): Flow<List<VanStockEntity>>

  @Query("SELECT * FROM van_stock WHERE serialNumber = :serial LIMIT 1")
  suspend fun findVanStockBySerial(serial: String): VanStockEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertVanStock(items: List<VanStockEntity>)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertVanStockItem(item: VanStockEntity)

  @Query("UPDATE van_stock SET quantityOnHand = quantityOnHand + :delta WHERE id = :id")
  suspend fun adjustVanStockQty(id: String, delta: Int)

  // Expense Claims (v2)
  @Query("SELECT * FROM expense_claims WHERE employeeId = :employeeId ORDER BY submittedAt DESC")
  fun observeClaims(employeeId: String): Flow<List<ExpenseClaimEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertClaims(items: List<ExpenseClaimEntity>)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertClaim(item: ExpenseClaimEntity)

  @Query("UPDATE expense_claims SET syncStatus = :syncStatus, accountingOutboxStatus = :outboxStatus WHERE id = :id")
  suspend fun updateClaimSyncStatus(id: String, syncStatus: String, outboxStatus: String)

  // Approvals & Delegations (v2)
  @Query("SELECT * FROM approval_items ORDER BY status ASC, submittedAt DESC")
  fun observeApprovalItems(): Flow<List<ApprovalItemEntity>>

  @Query("SELECT * FROM approval_items WHERE id = :id LIMIT 1")
  suspend fun getApprovalItemById(id: String): ApprovalItemEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertApprovalItems(items: List<ApprovalItemEntity>)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertApprovalItem(item: ApprovalItemEntity)

  @Query("SELECT * FROM delegations ORDER BY active DESC, endDate DESC")
  fun observeDelegations(): Flow<List<DelegationEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertDelegations(items: List<DelegationEntity>)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertDelegation(item: DelegationEntity)

  // Roster (v2)
  @Query("SELECT * FROM roster_entries ORDER BY standby24x7 DESC, employeeName ASC")
  fun observeRosterEntries(): Flow<List<RosterEntryEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertRosterEntries(items: List<RosterEntryEntity>)

  // Needs Attention Inbox (v2)
  @Query("SELECT * FROM needs_attention_inbox ORDER BY resolved ASC, id DESC")
  fun observeNeedsAttention(): Flow<List<NeedsAttentionEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertNeedsAttention(items: List<NeedsAttentionEntity>)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertNeedsAttention(item: NeedsAttentionEntity)

  @Query("UPDATE needs_attention_inbox SET resolved = 1 WHERE id = :id")
  suspend fun markNeedsAttentionResolved(id: String)

  // Audit Logs (v2)
  @Query("SELECT * FROM audit_logs ORDER BY createdAtEpochMs DESC")
  fun observeAuditLogs(): Flow<List<AuditLogEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertAuditLog(entry: AuditLogEntity)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertAuditLogs(entries: List<AuditLogEntity>)

  // Location Breadcrumbs & Visited Route (v4)
  @Query("SELECT * FROM location_breadcrumbs WHERE employeeId = :employeeId ORDER BY capturedAtEpochMs ASC")
  fun observeLocationBreadcrumbs(employeeId: String): Flow<List<LocationBreadcrumbEntity>>

  @Query("SELECT * FROM location_breadcrumbs WHERE employeeId = :employeeId ORDER BY capturedAtEpochMs DESC")
  suspend fun getLocationBreadcrumbsSync(employeeId: String): List<LocationBreadcrumbEntity>

  @Query("SELECT * FROM location_breadcrumbs WHERE employeeId = :employeeId AND syncStatus != 'SYNCED' ORDER BY capturedAtEpochMs ASC")
  suspend fun getUnsyncedLocationBreadcrumbs(employeeId: String): List<LocationBreadcrumbEntity>

  @Query("SELECT * FROM location_breadcrumbs WHERE syncStatus != 'SYNCED' ORDER BY capturedAtEpochMs ASC")
  suspend fun getAllUnsyncedLocationBreadcrumbs(): List<LocationBreadcrumbEntity>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertLocationBreadcrumb(point: LocationBreadcrumbEntity)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertLocationBreadcrumbs(points: List<LocationBreadcrumbEntity>)

  @Query("UPDATE location_breadcrumbs SET syncStatus = 'SYNCED', syncedAtEpochMs = :syncedAtEpochMs WHERE id IN (:ids)")
  suspend fun markLocationBreadcrumbsSynced(ids: List<String>, syncedAtEpochMs: Long)

  @Query("UPDATE location_breadcrumbs SET syncStatus = 'FAILED' WHERE id IN (:ids)")
  suspend fun markLocationBreadcrumbsFailed(ids: List<String>)

  // Multi-Shift Schedule Synced From Bootstrap / Roster (v5)
  @Query(
    "SELECT * FROM employee_shifts WHERE employeeId = :employeeId ORDER BY shiftDateAd ASC, sequenceNo ASC, startHour ASC, startMinute ASC"
  )
  fun observeEmployeeShifts(employeeId: String): Flow<List<EmployeeShiftEntity>>

  @Query(
    "SELECT * FROM employee_shifts WHERE employeeId = :employeeId ORDER BY shiftDateAd ASC, sequenceNo ASC, startHour ASC, startMinute ASC"
  )
  suspend fun getEmployeeShiftsSync(employeeId: String): List<EmployeeShiftEntity>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertEmployeeShifts(shifts: List<EmployeeShiftEntity>)

  @Query("DELETE FROM employee_shifts WHERE employeeId = :employeeId AND source = :source")
  suspend fun deleteShiftsForEmployeeBySource(employeeId: String, source: String)
}

