package com.example.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.data.local.NeedsAttentionEntity
import com.example.data.local.PendingSyncActionEntity
import com.example.data.local.PulseHrDao
import com.example.data.remote.ApiErrorEnvelopeDto
import com.example.data.remote.AttendancePunchRequestDto
import com.example.data.remote.ExpenseClaimSubmitRequestDto
import com.example.data.remote.LeaveReviewQueuePayload
import com.example.data.remote.LeaveReviewRequestDto
import com.example.data.remote.LeaveSubmitRequestDto
import com.example.data.remote.PulseHrApiService
import com.example.data.remote.WorkOrderCompleteRequestDto
import com.squareup.moshi.Moshi
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object SyncActionTypes {
  const val CLOCK_IN = "CLOCK_IN"
  const val CLOCK_OUT = "CLOCK_OUT"
  const val LEAVE_SUBMIT = "LEAVE_SUBMIT"
  const val LEAVE_REVIEW = "LEAVE_REVIEW"
  const val LEAVE_CANCEL = "LEAVE_CANCEL"
  const val WORK_ORDER_COMPLETE = "WORK_ORDER_COMPLETE"
  const val CLAIM_SUBMIT = "CLAIM_SUBMIT"
}

data class SyncQueueBatchResult(
  val syncedCount: Int,
  val failedCount: Int,
  val conflictCount: Int = 0,
  val lastError: String? = null,
)

/**
 * Coordinates v2 offline-first persistence in Room (`pending_sync_actions`) and WorkManager
 * background synchronization with idempotency keys, device timestamps, and Needs Attention routing
 * when server conflicts (SERIAL_NOT_IN_VAN, TICKET_CLOSED) occur so captured field work is never lost.
 */
@Singleton
class SyncQueueProcessor @Inject constructor(
  private val dao: PulseHrDao,
  private val apiService: PulseHrApiService,
  private val moshi: Moshi,
  private val workManager: WorkManager,
) {

  private val _isUploading = MutableStateFlow(false)
  val isUploading: StateFlow<Boolean> = _isUploading.asStateFlow()

  suspend fun enqueueAttendanceAction(
    isClockIn: Boolean,
    dto: AttendancePunchRequestDto,
    targetRecordId: String,
    description: String,
    errorReason: String,
  ) {
    val idemp = dto.idempotencyKey.ifBlank { "idemp_att_${UUID.randomUUID().toString().take(8)}" }
    val withKey = dto.copy(idempotencyKey = idemp)
    val json = moshi.adapter(AttendancePunchRequestDto::class.java).toJson(withKey)
    val now = System.currentTimeMillis()
    dao.insertPendingSyncAction(
      PendingSyncActionEntity(
        id = "sync_att_${UUID.randomUUID().toString().take(8)}",
        actionType = if (isClockIn) SyncActionTypes.CLOCK_IN else SyncActionTypes.CLOCK_OUT,
        description = description,
        payloadJson = json,
        targetRecordId = targetRecordId,
        createdAtEpochMs = now,
        retryCount = 0,
        lastError = errorReason,
        idempotencyKey = idemp,
        baseVersion = 1,
        deviceTimeEpochMs = dto.deviceTimeEpochMs,
        stateLabel = "QUEUED",
      )
    )
    scheduleNetworkRestoredSync()
  }

  suspend fun enqueueLeaveSubmitAction(
    dto: LeaveSubmitRequestDto,
    targetRequestId: String,
    leaveTypeName: String,
    errorReason: String,
  ) {
    val json = moshi.adapter(LeaveSubmitRequestDto::class.java).toJson(dto)
    val now = System.currentTimeMillis()
    dao.insertPendingSyncAction(
      PendingSyncActionEntity(
        id = "sync_lv_${UUID.randomUUID().toString().take(8)}",
        actionType = SyncActionTypes.LEAVE_SUBMIT,
        description = "Leave Request ($leaveTypeName: ${dto.startDate} → ${dto.endDate})",
        payloadJson = json,
        targetRecordId = targetRequestId,
        createdAtEpochMs = now,
        retryCount = 0,
        lastError = errorReason,
        idempotencyKey = "idemp_lv_${UUID.randomUUID().toString().take(8)}",
        baseVersion = 1,
        deviceTimeEpochMs = now,
        stateLabel = "QUEUED",
      )
    )
    scheduleNetworkRestoredSync()
  }

  suspend fun enqueueLeaveReviewAction(
    requestId: String,
    approve: Boolean,
    reviewerNote: String,
    errorReason: String,
  ) {
    val payload = LeaveReviewQueuePayload(requestId, approve, reviewerNote)
    val json = moshi.adapter(LeaveReviewQueuePayload::class.java).toJson(payload)
    val now = System.currentTimeMillis()
    dao.insertPendingSyncAction(
      PendingSyncActionEntity(
        id = "sync_lvr_${UUID.randomUUID().toString().take(8)}",
        actionType = SyncActionTypes.LEAVE_REVIEW,
        description = "${if (approve) "Approve" else "Decline"} Leave ($requestId)",
        payloadJson = json,
        targetRecordId = requestId,
        createdAtEpochMs = now,
        retryCount = 0,
        lastError = errorReason,
        idempotencyKey = "idemp_lvr_${UUID.randomUUID().toString().take(8)}",
        baseVersion = 1,
        deviceTimeEpochMs = now,
        stateLabel = "QUEUED",
      )
    )
    scheduleNetworkRestoredSync()
  }

  suspend fun enqueueLeaveCancelAction(
    requestId: String,
    errorReason: String,
  ) {
    val now = System.currentTimeMillis()
    dao.insertPendingSyncAction(
      PendingSyncActionEntity(
        id = "sync_lvc_${UUID.randomUUID().toString().take(8)}",
        actionType = SyncActionTypes.LEAVE_CANCEL,
        description = "Cancel Leave Request ($requestId)",
        payloadJson = "{}",
        targetRecordId = requestId,
        createdAtEpochMs = now,
        retryCount = 0,
        lastError = errorReason,
        idempotencyKey = "idemp_lvc_${UUID.randomUUID().toString().take(8)}",
        baseVersion = 1,
        deviceTimeEpochMs = now,
        stateLabel = "QUEUED",
      )
    )
    scheduleNetworkRestoredSync()
  }

  suspend fun enqueueWorkOrderCompleteAction(
    dto: WorkOrderCompleteRequestDto,
    errorReason: String,
  ) {
    val json = moshi.adapter(WorkOrderCompleteRequestDto::class.java).toJson(dto)
    dao.insertPendingSyncAction(
      PendingSyncActionEntity(
        id = "sync_wo_${UUID.randomUUID().toString().take(8)}",
        actionType = SyncActionTypes.WORK_ORDER_COMPLETE,
        description = "Close Work Order ${dto.ticketCode} (${dto.consumedCableMeters}m cable)",
        payloadJson = json,
        targetRecordId = dto.workOrderId,
        createdAtEpochMs = dto.deviceTimeEpochMs,
        retryCount = 0,
        lastError = errorReason,
        idempotencyKey = dto.idempotencyKey,
        baseVersion = dto.baseVersion,
        deviceTimeEpochMs = dto.deviceTimeEpochMs,
        stateLabel = "QUEUED",
      )
    )
    scheduleNetworkRestoredSync()
  }

  suspend fun enqueueClaimSubmitAction(
    dto: ExpenseClaimSubmitRequestDto,
    errorReason: String,
  ) {
    val json = moshi.adapter(ExpenseClaimSubmitRequestDto::class.java).toJson(dto)
    val now = System.currentTimeMillis()
    dao.insertPendingSyncAction(
      PendingSyncActionEntity(
        id = "sync_clm_${UUID.randomUUID().toString().take(8)}",
        actionType = SyncActionTypes.CLAIM_SUBMIT,
        description = "${dto.category} Claim ($${dto.amount})",
        payloadJson = json,
        targetRecordId = dto.claimId,
        createdAtEpochMs = now,
        retryCount = 0,
        lastError = errorReason,
        idempotencyKey = dto.idempotencyKey,
        baseVersion = 1,
        deviceTimeEpochMs = now,
        stateLabel = "QUEUED",
      )
    )
    scheduleNetworkRestoredSync()
  }

  fun scheduleNetworkRestoredSync() {
    val constraints =
      Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    val oneTimeRequest =
      OneTimeWorkRequestBuilder<EnterpriseSyncWorker>()
        .setConstraints(constraints)
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
        .build()

    workManager.enqueueUniqueWork(
      UNIQUE_SYNC_WORK_NAME,
      ExistingWorkPolicy.REPLACE,
      oneTimeRequest,
    )
  }

  fun ensurePeriodicCatchUpSync() {
    val constraints =
      Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    val periodicRequest =
      PeriodicWorkRequestBuilder<EnterpriseSyncWorker>(15, TimeUnit.MINUTES)
        .setConstraints(constraints)
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
        .build()

    workManager.enqueueUniquePeriodicWork(
      UNIQUE_PERIODIC_SYNC_NAME,
      ExistingPeriodicWorkPolicy.KEEP,
      periodicRequest,
    )
  }

  suspend fun processAllPendingActions(): SyncQueueBatchResult {
    val pending = dao.getAllPendingSyncActions()
    if (pending.isEmpty()) return SyncQueueBatchResult(syncedCount = 0, failedCount = 0)

    _isUploading.value = true
    val punchAdapter = moshi.adapter(AttendancePunchRequestDto::class.java)
    val leaveSubmitAdapter = moshi.adapter(LeaveSubmitRequestDto::class.java)
    val leaveReviewAdapter = moshi.adapter(LeaveReviewQueuePayload::class.java)
    val workOrderAdapter = moshi.adapter(WorkOrderCompleteRequestDto::class.java)
    val claimAdapter = moshi.adapter(ExpenseClaimSubmitRequestDto::class.java)
    val errorAdapter = moshi.adapter(ApiErrorEnvelopeDto::class.java)

    var syncedCount = 0
    var failedCount = 0
    var conflictCount = 0
    var lastError: String? = null

    try {
      for (action in pending) {
        try {
          when (action.actionType) {
            SyncActionTypes.CLOCK_IN,
            SyncActionTypes.CLOCK_OUT -> {
              val dto = punchAdapter.fromJson(action.payloadJson)
              if (dto == null) {
                dao.deletePendingSyncAction(action.id)
                continue
              }
              val response = apiService.submitAttendancePunch(dto)
              if (response.isSuccessful) {
                val body = response.body()
                if (body != null) {
                  dao.updateAttendanceSyncAndIntegrity(
                    recordId = action.targetRecordId,
                    syncStatus = "SYNCED",
                    riskScore = body.riskScore,
                    integrityStatus = body.integrityStatus,
                    notes = body.integrityNotes,
                  )
                } else {
                  dao.updateAttendanceSyncStatus(action.targetRecordId, "SYNCED")
                }
                dao.deletePendingSyncAction(action.id)
                syncedCount++
              } else {
                failedCount++
                lastError = "HTTP ${response.code()}"
                dao.markPendingSyncRetry(action.id, lastError, "FAILED")
              }
            }

            SyncActionTypes.LEAVE_SUBMIT -> {
              val dto = leaveSubmitAdapter.fromJson(action.payloadJson)
              if (dto == null) {
                dao.deletePendingSyncAction(action.id)
                continue
              }
              val response = apiService.submitLeaveRequest(dto)
              if (response.isSuccessful) {
                dao.updateLeaveSyncStatus(action.targetRecordId, "SYNCED")
                dao.deletePendingSyncAction(action.id)
                syncedCount++
              } else {
                failedCount++
                lastError = "HTTP ${response.code()}"
                dao.markPendingSyncRetry(action.id, lastError, "FAILED")
              }
            }

            SyncActionTypes.LEAVE_REVIEW -> {
              val payload = leaveReviewAdapter.fromJson(action.payloadJson)
              if (payload == null) {
                dao.deletePendingSyncAction(action.id)
                continue
              }
              val response =
                apiService.reviewLeaveRequest(
                  payload.requestId,
                  LeaveReviewRequestDto(payload.approve, payload.reviewerNote),
                )
              if (response.isSuccessful) {
                dao.updateLeaveSyncStatus(action.targetRecordId, "SYNCED")
                dao.deletePendingSyncAction(action.id)
                syncedCount++
              } else {
                failedCount++
                lastError = "HTTP ${response.code()}"
                dao.markPendingSyncRetry(action.id, lastError, "FAILED")
              }
            }

            SyncActionTypes.LEAVE_CANCEL -> {
              val response = apiService.cancelLeaveRequest(action.targetRecordId)
              if (response.isSuccessful) {
                dao.updateLeaveSyncStatus(action.targetRecordId, "SYNCED")
                dao.deletePendingSyncAction(action.id)
                syncedCount++
              } else {
                failedCount++
                lastError = "HTTP ${response.code()}"
                dao.markPendingSyncRetry(action.id, lastError, "FAILED")
              }
            }

            SyncActionTypes.WORK_ORDER_COMPLETE -> {
              val dto = workOrderAdapter.fromJson(action.payloadJson)
              if (dto == null) {
                dao.deletePendingSyncAction(action.id)
                continue
              }
              val response = apiService.completeWorkOrder(dto.workOrderId, dto)
              if (response.isSuccessful) {
                val existing = dao.getWorkOrderById(dto.workOrderId)
                if (existing != null) {
                  dao.upsertWorkOrder(
                    existing.copy(status = "CLOSED", syncStatus = "SYNCED")
                  )
                }
                dao.deletePendingSyncAction(action.id)
                syncedCount++
              } else {
                val errorJson = response.errorBody()?.string()
                val envelope =
                  errorJson?.let { runCatching { errorAdapter.fromJson(it) }.getOrNull() }
                if (envelope != null && response.code() == 409) {
                  conflictCount++
                  val timeLabel = SimpleDateFormat("MMM dd, HH:mm", Locale.US).format(Date())
                  dao.upsertNeedsAttention(
                    NeedsAttentionEntity(
                      id = "na_${dto.workOrderId}",
                      errorCode = envelope.code,
                      title = "${dto.ticketCode} Sync Conflict (${envelope.code})",
                      message = envelope.message,
                      sourceType = "WORK_ORDER",
                      sourceId = dto.workOrderId,
                      capturedSummary =
                        "Optical ${dto.opticalPowerDbm} dBm • OTDR ${dto.otdrLossDb} dB • Photos ${dto.photoCount} • Sig: ${dto.customerSignatureName}",
                      retryable = envelope.retryable,
                      createdAtLabel = timeLabel,
                      resolved = false,
                    )
                  )
                  dao.markPendingSyncRetry(action.id, envelope.message, "NEEDS_ATTENTION")
                } else {
                  failedCount++
                  lastError = "HTTP ${response.code()}"
                  dao.markPendingSyncRetry(action.id, lastError, "FAILED")
                }
              }
            }

            SyncActionTypes.CLAIM_SUBMIT -> {
              val dto = claimAdapter.fromJson(action.payloadJson)
              if (dto == null) {
                dao.deletePendingSyncAction(action.id)
                continue
              }
              val response = apiService.submitExpenseClaim(dto)
              if (response.isSuccessful) {
                dao.updateClaimSyncStatus(
                  id = dto.claimId,
                  syncStatus = "SYNCED",
                  outboxStatus = "QUEUED_FOR_VERIFICATION",
                )
                dao.deletePendingSyncAction(action.id)
                syncedCount++
              } else {
                failedCount++
                lastError = "HTTP ${response.code()}"
                dao.markPendingSyncRetry(action.id, lastError, "FAILED")
              }
            }

            else -> dao.deletePendingSyncAction(action.id)
          }
        } catch (e: Exception) {
          failedCount++
          lastError = e.message ?: "Network unavailable"
          dao.markPendingSyncRetry(action.id, lastError, "FAILED")
        }
      }
    } finally {
      _isUploading.value = false
    }

    return SyncQueueBatchResult(
      syncedCount = syncedCount,
      failedCount = failedCount,
      conflictCount = conflictCount,
      lastError = lastError,
    )
  }

  companion object {
    const val UNIQUE_SYNC_WORK_NAME = "pulsehr_background_sync_queue"
    const val UNIQUE_PERIODIC_SYNC_NAME = "pulsehr_periodic_sync_catchup"
    const val QUEUE_CAP_WARNING_THRESHOLD = 20
  }
}

@HiltWorker
class EnterpriseSyncWorker @AssistedInject constructor(
  @Assisted appContext: Context,
  @Assisted workerParams: WorkerParameters,
  private val syncQueueProcessor: SyncQueueProcessor,
) : CoroutineWorker(appContext, workerParams) {

  override suspend fun doWork(): Result {
    val batchResult = syncQueueProcessor.processAllPendingActions()
    return if (batchResult.failedCount > 0) Result.retry() else Result.success()
  }
}
