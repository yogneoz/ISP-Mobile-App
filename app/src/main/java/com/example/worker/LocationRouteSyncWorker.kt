package com.example.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.domain.model.RouteEventType
import com.example.domain.repository.RepoResult
import com.example.domain.repository.RouteTrackingRepository
import com.example.location.LocationFixResult
import com.example.location.LocationManager
import com.example.security.SecureTokenStore
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Periodic WorkManager worker (every 15 min + expedited flush when network reconnects) that:
 * 1. Captures the employee's current GPS waypoint when a Check-In shift is active & location
 *    permission is granted.
 * 2. Pushes all unsynced Room location breadcrumbs and attendance/hr queues to the database.
 */
@HiltWorker
class LocationRouteSyncWorker @AssistedInject constructor(
  @Assisted appContext: Context,
  @Assisted workerParams: WorkerParameters,
  private val locationManager: LocationManager,
  private val routeTrackingRepository: RouteTrackingRepository,
  private val syncQueueProcessor: SyncQueueProcessor,
  private val tokenStore: SecureTokenStore,
) : CoroutineWorker(appContext, workerParams) {

  override suspend fun doWork(): Result {
    // 1. If employee is Checked In and location permission is active, record current waypoint
    val activeSessionId = tokenStore.getActiveRouteSessionId()
    if (!activeSessionId.isNullOrBlank() && locationManager.hasLocationPermission()) {
      val fix = locationManager.acquireCurrentLocation()
      if (fix is LocationFixResult.Success) {
        routeTrackingRepository.pushLocationWaypoint(
          latitude = fix.latitude,
          longitude = fix.longitude,
          accuracyMeters = fix.accuracyMeters,
          eventType = RouteEventType.WAYPOINT,
        )
      }
    }

    // 2. Push any unsynced location breadcrumbs stored offline in Room
    val locResult = routeTrackingRepository.syncPendingLocationsToDatabase()

    // 3. Flush standard HR/Attendance/Field Ops sync queue
    val queueResult = syncQueueProcessor.processAllPendingActions()

    return if (locResult is RepoResult.Error || queueResult.failedCount > 0) {
      Result.retry()
    } else {
      Result.success()
    }
  }

  companion object {
    const val UNIQUE_PERIODIC_WORK_NAME = "pulsehr_periodic_location_route_sync"
    const val UNIQUE_IMMEDIATE_FLUSH_NAME = "pulsehr_immediate_location_route_flush"
  }
}
