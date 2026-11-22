package com.example

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.data.local.BranchEntity
import com.example.data.local.PulseHrDao
import com.example.data.local.PulseHrDatabase
import com.example.data.remote.AttendancePunchRequestDto
import com.example.data.remote.AttendancePunchResponseDto
import com.example.data.remote.AuthTokenResponseDto
import com.example.data.remote.DelegationCreateRequestDto
import com.example.data.remote.DeviceRegisterRequestDto
import com.example.data.remote.DeviceRegisterResponseDto
import com.example.data.remote.DispatchAssignRequestDto
import com.example.data.remote.ExpenseClaimSubmitRequestDto
import com.example.data.remote.ExpenseClaimSubmitResponseDto
import com.example.data.remote.LeaveReviewRequestDto
import com.example.data.remote.LeaveSubmitRequestDto
import com.example.data.remote.LeaveSubmitResponseDto
import com.example.data.remote.LocationBatchPushRequestDto
import com.example.data.remote.LocationBatchPushResponseDto
import com.example.data.remote.LoginRequestDto
import com.example.data.remote.PulseHrApiService
import com.example.data.remote.RefreshTokenRequestDto
import com.example.data.remote.RegisterFcmTokenRequestDto
import com.example.data.remote.RegisterFcmTokenResponseDto
import com.example.data.remote.SyncPullResponseDto
import com.example.data.remote.UpdateContactRequestDto
import com.example.data.remote.WorkOrderCompleteRequestDto
import com.example.data.remote.WorkOrderCompleteResponseDto
import com.example.data.remote.WorkplaceBootstrapDto
import com.example.data.repository.RouteTrackingRepositoryImpl
import com.example.domain.geofence.RouteLocationCalculator
import com.example.domain.model.RouteEventType
import com.example.domain.model.SyncStatus
import com.example.domain.model.UserRole
import com.example.domain.model.UserSession
import com.example.domain.repository.RepoResult
import com.example.security.SecureTokenStore
import com.example.worker.LocationRouteSyncWorker
import java.io.IOException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Response

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
class RouteTrackingRepositoryTest {

  private lateinit var db: PulseHrDatabase
  private lateinit var dao: PulseHrDao
  private lateinit var workManager: WorkManager
  private lateinit var tokenStore: SecureTokenStore
  private lateinit var fakeApi: FakeRouteApiService
  private lateinit var repo: RouteTrackingRepositoryImpl

  @Before
  fun setUp() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    if (!WorkManager.isInitialized()) {
      WorkManager.initialize(context, Configuration.Builder().build())
    }
    workManager = WorkManager.getInstance(context)
    db = Room.inMemoryDatabaseBuilder(context, PulseHrDatabase::class.java).allowMainThreadQueries().build()
    dao = db.pulseHrDao()
    tokenStore =
      SecureTokenStore.createForTesting(
        context.getSharedPreferences("test_route_prefs", Context.MODE_PRIVATE)
      )
    tokenStore.clearAll()
    tokenStore.saveAuthSession(
      accessToken = "acc_test",
      refreshToken = "ref_test",
      expiresAtEpochMs = System.currentTimeMillis() + 3600_000L,
      session =
        UserSession(
          userId = "usr_emp_103",
          email = "maya.shrestha@pulsehr.com",
          role = UserRole.HR_SELF_SERVICE,
          employeeId = "emp_103",
          isAuthenticated = true,
        ),
    )
    dao.upsertBranch(
      BranchEntity(
        id = "br_hq",
        name = "HQ Campus — Mountain View",
        code = "MV-HQ",
        address = "1600 Amphitheatre Pkwy",
        latitude = 37.4220,
        longitude = -122.0840,
        geofenceRadiusMeters = 250.0,
      )
    )
    fakeApi = FakeRouteApiService()
    repo =
      RouteTrackingRepositoryImpl(
        dao = dao,
        apiService = fakeApi,
        tokenStore = tokenStore,
        routeCalculator = RouteLocationCalculator(),
        workManager = workManager,
      )
  }

  @After
  fun tearDown() {
    workManager.cancelAllWork()
    tokenStore.clearAll()
    db.close()
  }

  @Test
  fun `checkIn starts periodic sync, queues offline waypoints, syncs batch on reconnect, and checkOut closes route`() =
    runBlocking {
      // 1. Check In online -> records CHECK_IN, marks SYNCED, schedules periodic WorkManager job
      val checkInRes = repo.checkInShift(37.4220, -122.0840, 10f)
      assertTrue(checkInRes is RepoResult.Success)
      val activeSessionId = repo.activeRouteSessionIdFlow.value
      assertNotNull(activeSessionId)

      val periodicWork =
        workManager
          .getWorkInfosForUniqueWork(LocationRouteSyncWorker.UNIQUE_PERIODIC_WORK_NAME)
          .get()
      assertTrue(periodicWork.any { it.state == WorkInfo.State.ENQUEUED })

      // 2. Simulate offline travel -> record intermediate waypoints queued locally
      fakeApi.isOffline = true
      val wp1 = repo.pushLocationWaypoint(37.3948, -122.0785, 14f, RouteEventType.WAYPOINT)
      assertTrue(wp1 is RepoResult.Success)
      assertEquals(SyncStatus.PENDING_SYNC, (wp1 as RepoResult.Success).data.syncStatus)

      val midSession = repo.observeActiveOrLatestRouteSession().first()!!
      assertTrue(midSession.isActive)
      assertEquals(2, midSession.totalPointsCount)
      assertEquals(1, midSession.pendingPointsCount)

      // 3. Reconnect -> Check Out flushes pending waypoint + check-out point together in batch
      fakeApi.isOffline = false
      val checkOutRes = repo.checkOutShift(37.3861, -122.0839, 11f)
      assertTrue(checkOutRes is RepoResult.Success)
      assertNull(repo.activeRouteSessionIdFlow.value)

      val closedSession = repo.observeActiveOrLatestRouteSession().first()!!
      assertEquals(3, closedSession.totalPointsCount)
      assertEquals(3, closedSession.syncedPointsCount)
      assertEquals(0, closedSession.pendingPointsCount)
      assertTrue(closedSession.totalDistanceMeters > 3_500.0)
      assertEquals(
        listOf(RouteEventType.CHECK_IN, RouteEventType.WAYPOINT, RouteEventType.CHECK_OUT),
        closedSession.waypoints.map { it.eventType },
      )
      assertEquals(2, fakeApi.pushedBatches.size)
      assertEquals(2, fakeApi.pushedBatches.last().points.size)
    }

  private class FakeRouteApiService : PulseHrApiService {
    var isOffline = false
    val pushedBatches = mutableListOf<LocationBatchPushRequestDto>()

    override suspend fun pushLocationBreadcrumbs(
      request: LocationBatchPushRequestDto
    ): Response<LocationBatchPushResponseDto> {
      if (isOffline) throw IOException("Offline")
      pushedBatches += request
      return Response.success(
        LocationBatchPushResponseDto(
          acceptedCount = request.points.size,
          serverTimestampEpochMs = System.currentTimeMillis(),
          routeSessionId = request.points.firstOrNull()?.sessionId,
        )
      )
    }

    override suspend fun login(request: LoginRequestDto): Response<AuthTokenResponseDto> =
      throw UnsupportedOperationException()

    override suspend fun refreshToken(request: RefreshTokenRequestDto): Response<AuthTokenResponseDto> =
      throw UnsupportedOperationException()

    override suspend fun fetchWorkplaceBootstrap(): Response<WorkplaceBootstrapDto> =
      throw UnsupportedOperationException()

    override suspend fun pullSyncDelta(updatedSince: Long): Response<SyncPullResponseDto> =
      throw UnsupportedOperationException()

    override suspend fun registerDevice(request: DeviceRegisterRequestDto): Response<DeviceRegisterResponseDto> =
      throw UnsupportedOperationException()

    override suspend fun submitAttendancePunch(
      request: AttendancePunchRequestDto
    ): Response<AttendancePunchResponseDto> = throw UnsupportedOperationException()

    override suspend fun submitLeaveRequest(
      request: LeaveSubmitRequestDto
    ): Response<LeaveSubmitResponseDto> = throw UnsupportedOperationException()

    override suspend fun reviewLeaveRequest(
      requestId: String,
      request: LeaveReviewRequestDto,
    ): Response<Unit> = throw UnsupportedOperationException()

    override suspend fun cancelLeaveRequest(requestId: String): Response<Unit> =
      throw UnsupportedOperationException()

    override suspend fun updateContact(request: UpdateContactRequestDto): Response<Unit> =
      throw UnsupportedOperationException()

    override suspend fun registerFcmToken(
      request: RegisterFcmTokenRequestDto
    ): Response<RegisterFcmTokenResponseDto> = throw UnsupportedOperationException()

    override suspend fun completeWorkOrder(
      workOrderId: String,
      request: WorkOrderCompleteRequestDto,
    ): Response<WorkOrderCompleteResponseDto> = throw UnsupportedOperationException()

    override suspend fun submitExpenseClaim(
      request: ExpenseClaimSubmitRequestDto
    ): Response<ExpenseClaimSubmitResponseDto> = throw UnsupportedOperationException()

    override suspend fun assignDispatchWorkOrder(request: DispatchAssignRequestDto): Response<Unit> =
      throw UnsupportedOperationException()

    override suspend fun createDelegation(request: DelegationCreateRequestDto): Response<Unit> =
      throw UnsupportedOperationException()
  }
}
