package com.example.data.repository

import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.data.local.AuditLogEntity
import com.example.data.local.BranchEntity
import com.example.data.local.LocationBreadcrumbEntity
import com.example.data.local.PulseHrDao
import com.example.data.remote.LocationBatchPushRequestDto
import com.example.data.remote.LocationBreadcrumbPushDto
import com.example.data.remote.PulseHrApiService
import com.example.domain.geofence.RouteLocationCalculator
import com.example.domain.model.BranchLocation
import com.example.domain.model.EmployeeRouteSession
import com.example.domain.model.LocationBreadcrumb
import com.example.domain.model.RouteEventType
import com.example.domain.model.SyncStatus
import com.example.domain.repository.RepoResult
import com.example.domain.repository.RouteTrackingRepository
import com.example.security.SecureTokenStore
import com.example.worker.LocationRouteSyncWorker
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class RouteTrackingRepositoryImpl @Inject constructor(
  private val dao: PulseHrDao,
  private val apiService: PulseHrApiService,
  private val tokenStore: SecureTokenStore,
  private val routeCalculator: RouteLocationCalculator,
  private val workManager: WorkManager,
) : RouteTrackingRepository {

  override val activeRouteSessionIdFlow: StateFlow<String?> = tokenStore.activeRouteSessionIdFlow

  override val lastRouteSyncEpochMsFlow: StateFlow<Long?> = tokenStore.lastRouteSyncEpochMsFlow

  override fun observeBreadcrumbs(): Flow<List<LocationBreadcrumb>> {
    return tokenStore.sessionFlow.flatMapLatest { session ->
      if (session == null) {
        flowOf(emptyList())
      } else {
        dao.observeLocationBreadcrumbs(session.employeeId).map { list -> list.map { it.toDomain() } }
      }
    }
  }

  override fun observeActiveOrLatestRouteSession(): Flow<EmployeeRouteSession?> {
    return combine(
      observeBreadcrumbs(),
      activeRouteSessionIdFlow,
    ) { allPoints, activeId ->
      if (allPoints.isEmpty() && activeId == null) {
        null
      } else {
        val targetSessionId =
          activeId
            ?: allPoints.maxByOrNull { it.capturedAtEpochMs }?.sessionId
            ?: return@combine null
        val waypoints =
          allPoints
            .filter { it.sessionId == targetSessionId }
            .sortedBy { it.capturedAtEpochMs }
        val totalDistance = routeCalculator.totalRouteDistanceMeters(waypoints)
        val syncedCount = waypoints.count { it.syncStatus == SyncStatus.SYNCED }
        val pendingCount = waypoints.size - syncedCount
        EmployeeRouteSession(
          sessionId = targetSessionId,
          dateIso =
            waypoints.firstOrNull()?.dateIso
              ?: SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()),
          isActive = activeId != null && activeId == targetSessionId,
          startedAtLabel = waypoints.firstOrNull()?.capturedTimeLabel,
          endedAtLabel =
            if (activeId == targetSessionId) null
            else waypoints.lastOrNull { it.eventType == RouteEventType.CHECK_OUT }?.capturedTimeLabel,
          totalDistanceMeters = totalDistance,
          totalPointsCount = waypoints.size,
          syncedPointsCount = syncedCount,
          pendingPointsCount = pendingCount,
          waypoints = waypoints,
        )
      }
    }
  }

  override suspend fun checkInShift(
    latitude: Double,
    longitude: Double,
    accuracyMeters: Float?,
  ): RepoResult<LocationBreadcrumb> {
    val session = tokenStore.getCurrentSession() ?: return RepoResult.Error("No active user session.")
    val nowMs = System.currentTimeMillis()
    val existingActive = tokenStore.getActiveRouteSessionId()
    val routeSessionId = existingActive ?: "route_${SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date(nowMs))}_${session.employeeId}"
    tokenStore.startRouteShiftSession(routeSessionId, nowMs)

    val point =
      recordAndAttemptSync(
        employeeId = session.employeeId,
        deviceId = session.deviceId,
        sessionId = routeSessionId,
        eventType = RouteEventType.CHECK_IN,
        latitude = latitude,
        longitude = longitude,
        accuracyMeters = accuracyMeters,
        capturedAtEpochMs = nowMs,
      )

    dao.insertAuditLog(
      AuditLogEntity(
        id = "aud_chk_in_${UUID.randomUUID().toString().take(8)}",
        actorId = session.employeeId,
        actorEmail = session.email,
        deviceId = session.deviceId,
        screenName = "ROUTE_CHECK_IN",
        actionLabel = "Checked In at ${point.siteLabel} (${String.format(Locale.US, "%.5f, %.5f", latitude, longitude)})",
        timestampLabel = point.capturedTimeLabel,
        createdAtEpochMs = nowMs,
      )
    )

    ensurePeriodicLocationSyncScheduled()
    return RepoResult.Success(point)
  }

  override suspend fun checkOutShift(
    latitude: Double,
    longitude: Double,
    accuracyMeters: Float?,
  ): RepoResult<LocationBreadcrumb> {
    val session = tokenStore.getCurrentSession() ?: return RepoResult.Error("No active user session.")
    val routeSessionId =
      tokenStore.getActiveRouteSessionId()
        ?: return RepoResult.Error("No active Check-In shift to check out from.")
    val nowMs = System.currentTimeMillis()

    val point =
      recordAndAttemptSync(
        employeeId = session.employeeId,
        deviceId = session.deviceId,
        sessionId = routeSessionId,
        eventType = RouteEventType.CHECK_OUT,
        latitude = latitude,
        longitude = longitude,
        accuracyMeters = accuracyMeters,
        capturedAtEpochMs = nowMs,
      )

    tokenStore.endRouteShiftSession()

    dao.insertAuditLog(
      AuditLogEntity(
        id = "aud_chk_out_${UUID.randomUUID().toString().take(8)}",
        actorId = session.employeeId,
        actorEmail = session.email,
        deviceId = session.deviceId,
        screenName = "ROUTE_CHECK_OUT",
        actionLabel = "Checked Out at ${point.siteLabel} (${String.format(Locale.US, "%.5f, %.5f", latitude, longitude)})",
        timestampLabel = point.capturedTimeLabel,
        createdAtEpochMs = nowMs,
      )
    )

    scheduleImmediateLocationFlush()
    return RepoResult.Success(point)
  }

  override suspend fun pushLocationWaypoint(
    latitude: Double,
    longitude: Double,
    accuracyMeters: Float?,
    eventType: RouteEventType,
  ): RepoResult<LocationBreadcrumb> {
    val session = tokenStore.getCurrentSession() ?: return RepoResult.Error("No active user session.")
    val nowMs = System.currentTimeMillis()
    val routeSessionId =
      tokenStore.getActiveRouteSessionId()
        ?: "route_${SimpleDateFormat("yyyyMMdd", Locale.US).format(Date(nowMs))}_${session.employeeId}"

    val point =
      recordAndAttemptSync(
        employeeId = session.employeeId,
        deviceId = session.deviceId,
        sessionId = routeSessionId,
        eventType = eventType,
        latitude = latitude,
        longitude = longitude,
        accuracyMeters = accuracyMeters,
        capturedAtEpochMs = nowMs,
      )
    return RepoResult.Success(point)
  }

  private suspend fun recordAndAttemptSync(
    employeeId: String,
    deviceId: String,
    sessionId: String,
    eventType: RouteEventType,
    latitude: Double,
    longitude: Double,
    accuracyMeters: Float?,
    capturedAtEpochMs: Long,
  ): LocationBreadcrumb {
    val branches = dao.observeBranches().first().map { it.toDomain() }
    val (siteLabel, nearestDistMeters) =
      routeCalculator.resolveNearestSiteLabel(latitude, longitude, branches)
    val insideAnyBranch =
      branches.any { b ->
        routeCalculator.haversineMeters(latitude, longitude, b.latitude, b.longitude) <=
          b.geofenceRadiusMeters
      }

    val dateIso = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(capturedAtEpochMs))
    val timeLabel = SimpleDateFormat("hh:mm:ss a", Locale.US).format(Date(capturedAtEpochMs))
    val entity =
      LocationBreadcrumbEntity(
        id = "loc_${capturedAtEpochMs}_${UUID.randomUUID().toString().take(6)}",
        employeeId = employeeId,
        sessionId = sessionId,
        dateIso = dateIso,
        eventType = eventType.name,
        latitude = latitude,
        longitude = longitude,
        accuracyMeters = accuracyMeters,
        siteLabel = siteLabel,
        distanceFromBranchMeters = nearestDistMeters,
        isInsideGeofence = insideAnyBranch,
        capturedAtEpochMs = capturedAtEpochMs,
        capturedTimeLabel = timeLabel,
        syncStatus = "PENDING_SYNC",
        syncedAtEpochMs = null,
      )
    dao.insertLocationBreadcrumb(entity)

    val syncRes = pushUnsyncedBatchInternal(employeeId, deviceId)
    return if (syncRes is RepoResult.Success && syncRes.data > 0) {
      entity.copy(syncStatus = "SYNCED", syncedAtEpochMs = System.currentTimeMillis()).toDomain()
    } else {
      scheduleImmediateLocationFlush()
      entity.toDomain()
    }
  }

  override suspend fun syncPendingLocationsToDatabase(): RepoResult<Int> {
    val session = tokenStore.getCurrentSession()
    val unsynced =
      if (session != null) dao.getUnsyncedLocationBreadcrumbs(session.employeeId)
      else dao.getAllUnsyncedLocationBreadcrumbs()

    if (unsynced.isEmpty()) {
      return RepoResult.Success(0)
    }

    val employeeId = session?.employeeId ?: unsynced.first().employeeId
    val deviceId = session?.deviceId ?: tokenStore.getOrCreateDeviceId()
    return pushUnsyncedBatchInternal(employeeId, deviceId)
  }

  private suspend fun pushUnsyncedBatchInternal(
    employeeId: String,
    deviceId: String,
  ): RepoResult<Int> {
    val unsynced = dao.getUnsyncedLocationBreadcrumbs(employeeId)
    if (unsynced.isEmpty()) return RepoResult.Success(0)

    val ids = unsynced.map { it.id }
    val idempotencyKey = "loc_batch_${unsynced.first().id}_${unsynced.size}"
    val request =
      LocationBatchPushRequestDto(
        employeeId = employeeId,
        deviceId = deviceId,
        idempotencyKey = idempotencyKey,
        points =
          unsynced.map {
            LocationBreadcrumbPushDto(
              id = it.id,
              employeeId = it.employeeId,
              sessionId = it.sessionId,
              eventType = it.eventType,
              latitude = it.latitude,
              longitude = it.longitude,
              accuracyMeters = it.accuracyMeters,
              siteLabel = it.siteLabel,
              capturedAtEpochMs = it.capturedAtEpochMs,
            )
          },
      )

    return try {
      val response = apiService.pushLocationBreadcrumbs(request)
      if (response.isSuccessful) {
        val syncedMs = response.body()?.serverTimestampEpochMs ?: System.currentTimeMillis()
        dao.markLocationBreadcrumbsSynced(ids, syncedMs)
        tokenStore.updateLastRouteSyncEpochMs(syncedMs)
        RepoResult.Success(unsynced.size)
      } else {
        dao.markLocationBreadcrumbsFailed(ids)
        RepoResult.Error("Sync deferred (HTTP ${response.code()})")
      }
    } catch (e: Exception) {
      RepoResult.Error(e.message ?: "Offline; waypoints queued in Room")
    }
  }

  fun ensurePeriodicLocationSyncScheduled() {
    val constraints =
      Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    val periodicRequest =
      PeriodicWorkRequestBuilder<LocationRouteSyncWorker>(15, TimeUnit.MINUTES)
        .setConstraints(constraints)
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
        .build()

    workManager.enqueueUniquePeriodicWork(
      LocationRouteSyncWorker.UNIQUE_PERIODIC_WORK_NAME,
      ExistingPeriodicWorkPolicy.KEEP,
      periodicRequest,
    )
  }

  private fun scheduleImmediateLocationFlush() {
    val constraints =
      Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
    val oneTime =
      OneTimeWorkRequestBuilder<LocationRouteSyncWorker>()
        .setConstraints(constraints)
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
        .build()
    workManager.enqueueUniqueWork(
      LocationRouteSyncWorker.UNIQUE_IMMEDIATE_FLUSH_NAME,
      ExistingWorkPolicy.REPLACE,
      oneTime,
    )
  }

  private fun BranchEntity.toDomain() =
    BranchLocation(
      id = id,
      name = name,
      code = code,
      address = address,
      latitude = latitude,
      longitude = longitude,
      geofenceRadiusMeters = geofenceRadiusMeters,
    )

  private fun LocationBreadcrumbEntity.toDomain() =
    LocationBreadcrumb(
      id = id,
      employeeId = employeeId,
      sessionId = sessionId,
      dateIso = dateIso,
      eventType = runCatching { RouteEventType.valueOf(eventType) }.getOrDefault(RouteEventType.WAYPOINT),
      latitude = latitude,
      longitude = longitude,
      accuracyMeters = accuracyMeters,
      siteLabel = siteLabel,
      distanceFromBranchMeters = distanceFromBranchMeters,
      isInsideGeofence = isInsideGeofence,
      capturedAtEpochMs = capturedAtEpochMs,
      capturedTimeLabel = capturedTimeLabel,
      syncStatus = runCatching { SyncStatus.valueOf(syncStatus) }.getOrDefault(SyncStatus.PENDING_SYNC),
      syncedAtEpochMs = syncedAtEpochMs,
    )
}
