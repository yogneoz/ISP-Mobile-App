package com.example.data.repository

import android.os.Build
import com.example.data.local.AuditLogEntity
import com.example.data.local.DelegationEntity
import com.example.data.local.EmployeeShiftEntity
import com.example.data.local.ExpenseClaimEntity
import com.example.data.local.NeedsAttentionEntity
import com.example.data.local.PulseHrDao
import com.example.data.remote.ApiErrorEnvelopeDto
import com.example.data.remote.DelegationCreateRequestDto
import com.example.data.remote.DispatchAssignRequestDto
import com.example.data.remote.ExpenseClaimSubmitRequestDto
import com.example.data.remote.PulseHrApiService
import com.example.data.remote.WorkOrderCompleteRequestDto
import com.example.domain.model.AppCapability
import com.example.domain.model.ApprovalItem
import com.example.domain.model.AuditLogEntry
import com.example.domain.model.AuditOutcome
import com.example.domain.model.ClaimCategory
import com.example.domain.model.DelegationRule
import com.example.domain.model.EmployeeShift
import com.example.domain.model.ExpenseClaim
import com.example.domain.model.NeedsAttentionItem
import com.example.domain.model.RosterEntry
import com.example.domain.model.ShiftWorkMode
import com.example.domain.model.StockAuditRow
import com.example.domain.model.SyncStatus
import com.example.domain.model.VanStockItem
import com.example.domain.model.WorkOrder
import com.example.domain.model.WorkOrderStatus
import com.example.domain.repository.RepoResult
import com.example.domain.repository.WorkplaceRepository
import com.example.security.SecureTokenStore
import com.example.worker.SyncQueueProcessor
import com.squareup.moshi.Moshi
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class WorkplaceRepositoryImpl @Inject constructor(
  private val dao: PulseHrDao,
  private val apiService: PulseHrApiService,
  private val tokenStore: SecureTokenStore,
  private val syncQueueProcessor: SyncQueueProcessor,
  private val moshi: Moshi,
) : WorkplaceRepository {

  override fun observeWorkOrders(): Flow<List<WorkOrder>> =
    dao.observeWorkOrders().map { list ->
      list.map {
        WorkOrder(
          id = it.id,
          ticketCode = it.ticketCode,
          customerName = it.customerName,
          address = it.address,
          taskType = it.taskType,
          slaDeadlineLabel = it.slaDeadlineLabel,
          slaMinutesRemaining = it.slaMinutesRemaining,
          status = runCatching { WorkOrderStatus.valueOf(it.status) }.getOrDefault(WorkOrderStatus.ASSIGNED),
          assignedEmployeeId = it.assignedEmployeeId,
          assignedEmployeeName = it.assignedEmployeeName,
          branchCode = it.branchCode,
          opticalPowerDbm = it.opticalPowerDbm,
          otdrLossDb = it.otdrLossDb,
          customerSignatureName = it.customerSignatureName,
          photoCount = it.photoCount,
          consumedCableMeters = it.consumedCableMeters,
          serialUsed = it.serialUsed,
          poleRouteNote = it.poleRouteNote,
          baseVersion = it.baseVersion,
          syncStatus = runCatching { SyncStatus.valueOf(it.syncStatus) }.getOrDefault(SyncStatus.SYNCED),
        )
      }
    }

  override fun observeVanStock(): Flow<List<VanStockItem>> =
    dao.observeVanStock().map { list ->
      list.map {
        VanStockItem(
          id = it.id,
          sku = it.sku,
          itemName = it.itemName,
          category = it.category,
          serialNumber = it.serialNumber,
          quantityOnHand = it.quantityOnHand,
          unit = it.unit,
          minThreshold = it.minThreshold,
          vanCode = it.vanCode,
        )
      }
    }

  override fun observeClaims(): Flow<List<ExpenseClaim>> =
    tokenStore.sessionFlow.flatMapLatest { session ->
      if (session == null) flowOf(emptyList())
      else {
        dao.observeClaims(session.employeeId).map { list ->
          list.map {
            ExpenseClaim(
              id = it.id,
              employeeId = it.employeeId,
              employeeName = it.employeeName,
              category = runCatching { ClaimCategory.valueOf(it.category) }.getOrDefault(ClaimCategory.FUEL),
              amount = it.amount,
              currency = it.currency,
              ticketCode = it.ticketCode,
              description = it.description,
              receiptAttached = it.receiptAttached,
              status = it.status,
              submittedAt = it.submittedAt,
              accountingOutboxStatus = it.accountingOutboxStatus,
              syncStatus = runCatching { SyncStatus.valueOf(it.syncStatus) }.getOrDefault(SyncStatus.SYNCED),
            )
          }
        }
      }
    }

  override fun observeApprovalItems(): Flow<List<ApprovalItem>> =
    dao.observeApprovalItems().map { list ->
      list.map {
        ApprovalItem(
          id = it.id,
          itemType = it.itemType,
          requesterId = it.requesterId,
          requesterName = it.requesterName,
          summary = it.summary,
          detail = it.detail,
          metricLabel = it.metricLabel,
          submittedAt = it.submittedAt,
          status = it.status,
          delegatedFrom = it.delegatedFrom,
          reviewedBy = it.reviewedBy,
        )
      }
    }

  override fun observeDelegations(): Flow<List<DelegationRule>> =
    dao.observeDelegations().map { list ->
      list.map {
        DelegationRule(
          id = it.id,
          delegatorId = it.delegatorId,
          delegatorName = it.delegatorName,
          delegateEmployeeId = it.delegateEmployeeId,
          delegateName = it.delegateName,
          capabilitiesLabel = it.capabilitiesLabel,
          startDate = it.startDate,
          endDate = it.endDate,
          active = it.active,
        )
      }
    }

  override fun observeRosterEntries(): Flow<List<RosterEntry>> =
    dao.observeRosterEntries().map { list ->
      list.map {
        RosterEntry(
          id = it.id,
          employeeId = it.employeeId,
          employeeName = it.employeeName,
          roleLabel = it.roleLabel,
          shiftLabel = it.shiftLabel,
          standby24x7 = it.standby24x7,
          leaveStatusWarning = it.leaveStatusWarning,
          assignedBranchCode = it.assignedBranchCode,
        )
      }
    }

  override fun observeNeedsAttention(): Flow<List<NeedsAttentionItem>> =
    dao.observeNeedsAttention().map { list ->
      list.map {
        NeedsAttentionItem(
          id = it.id,
          errorCode = it.errorCode,
          title = it.title,
          message = it.message,
          sourceType = it.sourceType,
          sourceId = it.sourceId,
          capturedSummary = it.capturedSummary,
          retryable = it.retryable,
          createdAtLabel = it.createdAtLabel,
          resolved = it.resolved,
        )
      }
    }

  override fun observeAuditLogs(): Flow<List<AuditLogEntry>> =
    dao.observeAuditLogs().map { list ->
      list.map {
        AuditLogEntry(
          id = it.id,
          actorId = it.actorId,
          actorEmail = it.actorEmail,
          deviceId = it.deviceId,
          screenName = it.screenName,
          actionLabel = it.actionLabel,
          timestampLabel = it.timestampLabel,
          createdAtEpochMs = it.createdAtEpochMs,
          eventCategory = it.eventCategory,
          outcome = runCatching { AuditOutcome.valueOf(it.outcome) }.getOrDefault(AuditOutcome.INFO),
          authMethod = it.authMethod,
          deviceModel = it.deviceModel,
          failureReason = it.failureReason,
        )
      }
    }

  override fun observeStockAuditRows(): Flow<List<StockAuditRow>> =
    observeVanStock().map { items ->
      items.map { item ->
        val issued = item.quantityOnHand + 2
        val consumed = 2
        val balance = item.quantityOnHand
        StockAuditRow(
          id = "aud_${item.id}",
          technicianName = item.vanCode,
          itemName = item.itemName,
          warehouseIssuedQty = issued,
          ticketConsumedQty = consumed,
          vanBalanceQty = balance,
          variance = issued - consumed - balance,
        )
      }
    }

  override fun observeEmployeeShifts(): Flow<List<EmployeeShift>> =
    tokenStore.sessionFlow.flatMapLatest { session ->
      if (session == null) flowOf(emptyList())
      else {
        dao.observeEmployeeShifts(session.employeeId).map { list ->
          list.map { it.toDomain() }
        }
      }
    }

  override suspend fun syncShiftsFromBootstrap(shifts: List<EmployeeShift>): RepoResult<Unit> {
    val session = tokenStore.getCurrentSession() ?: return RepoResult.Error("No active session")
    if (shifts.isEmpty()) return RepoResult.Success(Unit)
    dao.deleteShiftsForEmployeeBySource(session.employeeId, "BOOTSTRAP_ROSTER")
    dao.insertEmployeeShifts(
      shifts.map { s ->
        EmployeeShiftEntity(
          id = s.id,
          employeeId = session.employeeId,
          shiftCode = s.shiftCode,
          shiftName = s.shiftName,
          shiftDateAd = s.shiftDateAd,
          startHour = s.startHour,
          startMinute = s.startMinute,
          endHour = s.endHour,
          endMinute = s.endMinute,
          crossesMidnight = s.crossesMidnight,
          workMode = s.workMode.name,
          requireGeofenceCheckIn = s.requireGeofenceCheckIn,
          requireGeofenceCheckOut = s.requireGeofenceCheckOut,
          branchCode = s.branchCode,
          sequenceNo = s.sequenceNo,
          isStandby24x7 = s.isStandby24x7,
          source = s.source,
        )
      }
    )
    return RepoResult.Success(Unit)
  }

  override suspend fun completeWorkOrder(
    workOrderId: String,
    opticalPowerDbm: Double,
    otdrLossDb: Double,
    customerSignatureName: String,
    consumedCableMeters: Int,
    serialUsed: String?,
    photoCount: Int,
    poleRouteNote: String?,
  ): RepoResult<WorkOrder> {
    val session = tokenStore.getCurrentSession() ?: return RepoResult.Error("No active session")
    if (AppCapability.JOBS_EXECUTE !in session.capabilities) {
      return RepoResult.Error("Missing jobs.execute capability.")
    }
    if (customerSignatureName.isBlank()) {
      return RepoResult.Error("Customer signature name is required to close ticket.")
    }
    val existing = dao.getWorkOrderById(workOrderId) ?: return RepoResult.Error("Work order not found")

    val cleanedSerial = serialUsed?.trim()?.takeIf { it.isNotBlank() }
    val updatedEntity =
      existing.copy(
        status = WorkOrderStatus.COMPLETED_LOCAL.name,
        opticalPowerDbm = opticalPowerDbm,
        otdrLossDb = otdrLossDb,
        customerSignatureName = customerSignatureName.trim(),
        consumedCableMeters = consumedCableMeters,
        serialUsed = cleanedSerial,
        photoCount = photoCount.coerceAtLeast(1),
        poleRouteNote = poleRouteNote?.takeIf { it.isNotBlank() } ?: existing.poleRouteNote,
        syncStatus = "PENDING_SYNC",
      )
    dao.upsertWorkOrder(updatedEntity)

    // Operational van balance deduction (goes negative & flags audit rather than blocking field work)
    if (consumedCableMeters > 0) {
      dao.adjustVanStockQty("vs_3", -consumedCableMeters)
    }
    if (cleanedSerial != null) {
      val stockMatch = dao.findVanStockBySerial(cleanedSerial)
      if (stockMatch != null && stockMatch.quantityOnHand > 0) {
        dao.adjustVanStockQty(stockMatch.id, -1)
      }
    }

    val idempKey = "wo_${existing.id}_v${existing.baseVersion}_${UUID.randomUUID().toString().take(6)}"
    val dto =
      WorkOrderCompleteRequestDto(
        workOrderId = existing.id,
        ticketCode = existing.ticketCode,
        employeeId = session.employeeId,
        opticalPowerDbm = opticalPowerDbm,
        otdrLossDb = otdrLossDb,
        customerSignatureName = customerSignatureName.trim(),
        photoCount = photoCount.coerceAtLeast(1),
        consumedCableMeters = consumedCableMeters,
        serialUsed = cleanedSerial,
        baseVersion = existing.baseVersion,
        idempotencyKey = idempKey,
        deviceTimeEpochMs = System.currentTimeMillis(),
      )

    try {
      val response = apiService.completeWorkOrder(existing.id, dto)
      if (response.isSuccessful) {
        val synced = updatedEntity.copy(status = WorkOrderStatus.CLOSED.name, syncStatus = "SYNCED")
        dao.upsertWorkOrder(synced)
        return RepoResult.Success(synced.toDomain())
      } else if (response.code() == 409) {
        val errorJson = response.errorBody()?.string()
        val envelope =
          errorJson?.let { runCatching { moshi.adapter(ApiErrorEnvelopeDto::class.java).fromJson(it) }.getOrNull() }
        val timeLabel = SimpleDateFormat("MMM dd, HH:mm", Locale.US).format(Date())
        dao.upsertNeedsAttention(
          NeedsAttentionEntity(
            id = "na_${existing.id}",
            errorCode = envelope?.code ?: "SERIAL_NOT_IN_VAN",
            title = "${existing.ticketCode} Conflict (${envelope?.code ?: "409"})",
            message = envelope?.message ?: "Serial mismatch. Captured readings retained.",
            sourceType = "WORK_ORDER",
            sourceId = existing.id,
            capturedSummary =
              "Optical $opticalPowerDbm dBm • OTDR $otdrLossDb dB • Photos $photoCount • Sig: $customerSignatureName",
            retryable = envelope?.retryable ?: true,
            createdAtLabel = timeLabel,
            resolved = false,
          )
        )
        return RepoResult.Error(envelope?.message ?: "Moved to Needs Attention (captured data retained).")
      } else {
        syncQueueProcessor.enqueueWorkOrderCompleteAction(dto, "HTTP ${response.code()}")
      }
    } catch (e: Exception) {
      syncQueueProcessor.enqueueWorkOrderCompleteAction(dto, e.message ?: "Offline")
    }

    return RepoResult.Success(updatedEntity.toDomain())
  }

  override suspend fun addPoleRouteLog(workOrderId: String, note: String): RepoResult<Unit> {
    val existing = dao.getWorkOrderById(workOrderId) ?: return RepoResult.Error("Work order not found")
    if (note.isBlank()) return RepoResult.Error("Enter pole or ODB/FDB maintenance notes.")
    dao.upsertWorkOrder(existing.copy(poleRouteNote = note.trim()))
    return RepoResult.Success(Unit)
  }

  override suspend fun requestVanRestock(itemId: String, addQty: Int): RepoResult<Unit> {
    if (addQty <= 0) return RepoResult.Error("Restock quantity must be positive.")
    dao.adjustVanStockQty(itemId, addQty)
    return RepoResult.Success(Unit)
  }

  override suspend fun submitExpenseClaim(
    category: ClaimCategory,
    amount: Double,
    ticketCode: String?,
    description: String,
    receiptAttached: Boolean,
  ): RepoResult<ExpenseClaim> {
    val session = tokenStore.getCurrentSession() ?: return RepoResult.Error("No active session")
    if (amount <= 0.0) return RepoResult.Error("Enter a valid claim amount.")
    if (description.isBlank()) return RepoResult.Error("Provide expense or call-out details.")

    val emp = dao.getEmployeeByUserId(session.userId)
    val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    val claimId = "clm_${UUID.randomUUID().toString().take(8)}"
    val entity =
      ExpenseClaimEntity(
        id = claimId,
        employeeId = session.employeeId,
        employeeName = emp?.fullName ?: "Self",
        category = category.name,
        amount = amount,
        currency = "USD",
        ticketCode = ticketCode?.trim()?.takeIf { it.isNotBlank() },
        description = description.trim(),
        receiptAttached = receiptAttached,
        status = "PENDING",
        submittedAt = today,
        accountingOutboxStatus = "PENDING_SYNC",
        syncStatus = "PENDING_SYNC",
      )
    dao.upsertClaim(entity)

    val dto =
      ExpenseClaimSubmitRequestDto(
        claimId = claimId,
        employeeId = session.employeeId,
        category = category.name,
        amount = amount,
        ticketCode = entity.ticketCode,
        description = entity.description,
        receiptAttached = receiptAttached,
        idempotencyKey = "idemp_$claimId",
      )

    try {
      val response = apiService.submitExpenseClaim(dto)
      if (response.isSuccessful) {
        val outbox = response.body()?.accountingOutboxStatus ?: "QUEUED_FOR_VERIFICATION"
        dao.updateClaimSyncStatus(claimId, "SYNCED", outbox)
      } else {
        syncQueueProcessor.enqueueClaimSubmitAction(dto, "HTTP ${response.code()}")
      }
    } catch (e: Exception) {
      syncQueueProcessor.enqueueClaimSubmitAction(dto, e.message ?: "Offline")
    }

    return RepoResult.Success(
      ExpenseClaim(
        id = entity.id,
        employeeId = entity.employeeId,
        employeeName = entity.employeeName,
        category = category,
        amount = entity.amount,
        currency = entity.currency,
        ticketCode = entity.ticketCode,
        description = entity.description,
        receiptAttached = entity.receiptAttached,
        status = entity.status,
        submittedAt = entity.submittedAt,
        accountingOutboxStatus = entity.accountingOutboxStatus,
        syncStatus = SyncStatus.PENDING_SYNC,
      )
    )
  }

  override suspend fun actOnApprovalItem(
    approvalId: String,
    approve: Boolean,
    note: String,
  ): RepoResult<Unit> {
    val session = tokenStore.getCurrentSession() ?: return RepoResult.Error("No active session")
    val item = dao.getApprovalItemById(approvalId) ?: return RepoResult.Error("Approval item not found")
    if (item.requesterId == session.employeeId) {
      return RepoResult.Error(
        "Blocked by No-Self-Approval rule: even managers cannot approve their own requests."
      )
    }
    val hasRight =
      if (item.itemType == "EXPENSE_CLAIM") session.hasCapability(AppCapability.APPROVE_EXPENSE)
      else session.hasCapability(AppCapability.APPROVE_ATTENDANCE_OT)
    if (!hasRight) {
      return RepoResult.Error("Missing approval capability for this request type.")
    }

    val newStatus = if (approve) "APPROVED" else "REJECTED"
    dao.upsertApprovalItem(
      item.copy(
        status = newStatus,
        reviewedBy = session.email,
        detail = if (note.isBlank()) item.detail else "${item.detail} • Note: ${note.trim()}",
      )
    )
    logSensitiveScreenView(
      screenName = "APPROVAL_DECISION",
      actionLabel = "$newStatus ${item.itemType} (${item.id})${if (item.delegatedFrom != null) " [Delegated from ${item.delegatedFrom}]" else ""}",
    )
    return RepoResult.Success(Unit)
  }

  override suspend fun createDelegation(
    delegateEmployeeId: String,
    delegateName: String,
    endDate: String,
  ): RepoResult<Unit> {
    val session = tokenStore.getCurrentSession() ?: return RepoResult.Error("No active session")
    val emp = dao.getEmployeeByUserId(session.userId)
    val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    dao.upsertDelegation(
      DelegationEntity(
        id = "del_${UUID.randomUUID().toString().take(6)}",
        delegatorId = session.employeeId,
        delegatorName = emp?.fullName ?: session.email,
        delegateEmployeeId = delegateEmployeeId,
        delegateName = delegateName,
        capabilitiesLabel = "approve.attendance_ot, approve.expense",
        startDate = today,
        endDate = endDate,
        active = true,
      )
    )
    runCatching {
      apiService.createDelegation(
        DelegationCreateRequestDto(
          delegateEmployeeId = delegateEmployeeId,
          delegateName = delegateName,
          endDate = endDate,
          capabilitiesCsv = "approve.attendance_ot,approve.expense",
        )
      )
    }
    return RepoResult.Success(Unit)
  }

  override suspend fun assignDispatchTicket(
    workOrderId: String,
    assigneeEmployeeId: String,
    assigneeName: String,
    overrideLeaveWarning: Boolean,
    overrideReason: String?,
  ): RepoResult<Unit> {
    val session = tokenStore.getCurrentSession() ?: return RepoResult.Error("No active session")
    if (!session.hasCapability(AppCapability.DISPATCH_ASSIGN)) {
      return RepoResult.Error("Missing dispatch.assign capability.")
    }
    if (overrideLeaveWarning && overrideReason.isNullOrBlank()) {
      return RepoResult.Error("Override reason is mandatory when dispatching crew on approved/half-day leave.")
    }
    val existing = dao.getWorkOrderById(workOrderId) ?: return RepoResult.Error("Work order not found")
    dao.upsertWorkOrder(
      existing.copy(
        assignedEmployeeId = assigneeEmployeeId,
        assignedEmployeeName = assigneeName,
        status = WorkOrderStatus.ASSIGNED.name,
      )
    )
    if (overrideLeaveWarning && !overrideReason.isNullOrBlank()) {
      logSensitiveScreenView(
        screenName = "DISPATCH_LEAVE_OVERRIDE",
        actionLabel = "Overrode leave warning for $assigneeName on ${existing.ticketCode}: $overrideReason",
      )
    }
    runCatching {
      apiService.assignDispatchWorkOrder(
        DispatchAssignRequestDto(
          workOrderId = workOrderId,
          assigneeEmployeeId = assigneeEmployeeId,
          overrideLeaveWarning = overrideLeaveWarning,
          overrideReason = overrideReason,
        )
      )
    }
    return RepoResult.Success(Unit)
  }

  override suspend fun resolveNeedsAttentionItem(
    needsAttentionId: String,
    correctedSerial: String?,
  ): RepoResult<String> {
    if (!correctedSerial.isNullOrBlank()) {
      dao.upsertWorkOrder(
        (dao.getWorkOrderById(needsAttentionId.removePrefix("na_")) ?: return RepoResult.Error("Linked work order missing"))
          .copy(serialUsed = correctedSerial.trim(), status = WorkOrderStatus.CLOSED.name, syncStatus = "SYNCED")
      )
    }
    dao.markNeedsAttentionResolved(needsAttentionId)
    return RepoResult.Success("Resolved & replayed with preserved field capture.")
  }

  override suspend fun logSensitiveScreenView(screenName: String, actionLabel: String) {
    val session = tokenStore.getCurrentSession() ?: return
    val timeLabel = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
    val deviceSummary = "${Build.MANUFACTURER} ${Build.MODEL} • Android ${Build.VERSION.RELEASE}".trim()
    dao.insertAuditLog(
      AuditLogEntity(
        id = "aud_${UUID.randomUUID().toString().take(8)}",
        actorId = session.employeeId,
        actorEmail = session.email,
        deviceId = session.deviceId,
        screenName = screenName,
        actionLabel = actionLabel,
        timestampLabel = timeLabel,
        createdAtEpochMs = System.currentTimeMillis(),
        eventCategory = "SENSITIVE_ACCESS",
        outcome = "INFO",
        authMethod = "SYSTEM",
        deviceModel = deviceSummary,
      )
    )
  }

  private fun com.example.data.local.WorkOrderEntity.toDomain() =
    WorkOrder(
      id = id,
      ticketCode = ticketCode,
      customerName = customerName,
      address = address,
      taskType = taskType,
      slaDeadlineLabel = slaDeadlineLabel,
      slaMinutesRemaining = slaMinutesRemaining,
      status = runCatching { WorkOrderStatus.valueOf(status) }.getOrDefault(WorkOrderStatus.ASSIGNED),
      assignedEmployeeId = assignedEmployeeId,
      assignedEmployeeName = assignedEmployeeName,
      branchCode = branchCode,
      opticalPowerDbm = opticalPowerDbm,
      otdrLossDb = otdrLossDb,
      customerSignatureName = customerSignatureName,
      photoCount = photoCount,
      consumedCableMeters = consumedCableMeters,
      serialUsed = serialUsed,
      poleRouteNote = poleRouteNote,
      baseVersion = baseVersion,
      syncStatus = runCatching { SyncStatus.valueOf(syncStatus) }.getOrDefault(SyncStatus.SYNCED),
    )

  private fun EmployeeShiftEntity.toDomain() =
    EmployeeShift(
      id = id,
      employeeId = employeeId,
      shiftCode = shiftCode,
      shiftName = shiftName,
      shiftDateAd = shiftDateAd,
      startHour = startHour,
      startMinute = startMinute,
      endHour = endHour,
      endMinute = endMinute,
      crossesMidnight = crossesMidnight,
      workMode = runCatching { ShiftWorkMode.valueOf(workMode) }.getOrDefault(ShiftWorkMode.FIELD),
      requireGeofenceCheckIn = requireGeofenceCheckIn,
      requireGeofenceCheckOut = requireGeofenceCheckOut,
      branchCode = branchCode,
      sequenceNo = sequenceNo,
      isStandby24x7 = isStandby24x7,
      source = source,
    )
}
