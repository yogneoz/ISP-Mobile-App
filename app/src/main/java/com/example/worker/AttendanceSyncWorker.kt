package com.example.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Legacy-named worker delegating to [SyncQueueProcessor] so any previously scheduled work
 * seamlessly processes both attendance and leave request queues.
 */
@HiltWorker
class AttendanceSyncWorker @AssistedInject constructor(
  @Assisted appContext: Context,
  @Assisted workerParams: WorkerParameters,
  private val syncQueueProcessor: SyncQueueProcessor,
) : CoroutineWorker(appContext, workerParams) {

  override suspend fun doWork(): Result {
    val batchResult = syncQueueProcessor.processAllPendingActions()
    return if (batchResult.failedCount > 0) Result.retry() else Result.success()
  }
}
