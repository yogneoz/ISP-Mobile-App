package com.example

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.data.local.AttendanceEntity
import com.example.data.local.LeaveRequestEntity
import com.example.data.local.PulseHrDao
import com.example.data.local.PulseHrDatabase
import com.example.data.remote.AttendancePunchRequestDto
import com.example.data.remote.AttendancePunchResponseDto
import com.example.data.remote.AuthTokenResponseDto
import com.example.data.remote.LeaveReviewRequestDto
import com.example.data.remote.LeaveSubmitRequestDto
import com.example.data.remote.LeaveSubmitResponseDto
import com.example.data.remote.LoginRequestDto
import com.example.data.remote.PulseHrApiService
import com.example.data.remote.RefreshTokenRequestDto
import com.example.data.remote.RegisterFcmTokenRequestDto
import com.example.data.remote.RegisterFcmTokenResponseDto
import com.example.data.remote.UpdateContactRequestDto
import com.example.worker.SyncActionTypes
import com.example.worker.SyncQueueProcessor
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.io.IOException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Response

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
class SyncQueueProcessorTest {

  private lateinit var db: PulseHrDatabase
  private lateinit var dao: PulseHrDao
  private lateinit var workManager: WorkManager
  private lateinit var fakeApi: FakePulseHrApiService
  private lateinit var processor: SyncQueueProcessor

  @Before
  fun setUp() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    if (!WorkManager.isInitialized()) {
      WorkManager.initialize(context, Configuration.Builder().build())
    }
    workManager = WorkManager.getInstance(context)
    db = Room.inMemoryDatabaseBuilder(context, PulseHrDatabase::class.java).allowMainThreadQueries().build()
    dao = db.pulseHrDao()
    fakeApi = FakePulseHrApiService()
    val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    processor = SyncQueueProcessor(dao = dao, apiService = fakeApi, moshi = moshi, workManager = workManager)
  }

  @After
  fun tearDown() {
    workManager.cancelAllWork()
    db.close()
  }

  @Test
  fun `queues attendance and leave actions offline then syncs all on connectivity restore`() = runBlocking {
    dao.upsertAttendance(
      AttendanceEntity(
        id = "att_2026-11-14_emp_103",
        employeeId = "emp_103",
        branchId = "br_hq",
        attendanceDateAd = "2026-11-14",
        checkInTime = "09:05 AM",
        checkOutTime = null,
        status = "PRESENT",
        source = "MOBILE",
        checkInLatitude = 37.4219983,
        checkInLongitude = -122.0840,
        distanceFromBranchMeters = 12.0,
        overtimeHours = 0.0,
        syncStatus = "PENDING_SYNC",
      )
    )
    dao.upsertLeaveRequest(
      LeaveRequestEntity(
        id = "lv_offline_01",
        employeeId = "emp_103",
        employeeName = "Maya Shrestha",
        leaveTypeId = "LT_ANNUAL",
        leaveTypeName = "Annual Earned Leave",
        startDate = "2026-11-26",
        endDate = "2026-11-27",
        totalDays = 2.0,
        reason = "Family travel",
        status = "PENDING",
        submittedAt = "2026-11-14",
        reviewerNote = null,
        isTeamRequest = false,
        syncStatus = "PENDING_SYNC",
      )
    )
    dao.upsertLeaveRequest(
      LeaveRequestEntity(
        id = "lv_team_02",
        employeeId = "emp_205",
        employeeName = "Aarav Thapa",
        leaveTypeId = "LT_SICK",
        leaveTypeName = "Medical & Sick Leave",
        startDate = "2026-11-20",
        endDate = "2026-11-20",
        totalDays = 1.0,
        reason = "Dental checkup",
        status = "APPROVED",
        submittedAt = "2026-11-14",
        reviewerNote = "Approved offline",
        isTeamRequest = true,
        syncStatus = "PENDING_SYNC",
      )
    )

    processor.enqueueAttendanceAction(
      isClockIn = true,
      dto =
        AttendancePunchRequestDto(
          employeeId = "emp_103",
          branchId = "br_hq",
          attendanceDateAd = "2026-11-14",
          action = "CLOCK_IN",
          punchTime = "09:05 AM",
          latitude = 37.4219983,
          longitude = -122.0840,
          distanceMeters = 12.0,
        ),
      targetRecordId = "att_2026-11-14_emp_103",
      description = "Clock-In at 09:05 AM",
      errorReason = "Offline",
    )
    processor.enqueueLeaveSubmitAction(
      dto =
        LeaveSubmitRequestDto(
          employeeId = "emp_103",
          leaveTypeId = "LT_ANNUAL",
          startDate = "2026-11-26",
          endDate = "2026-11-27",
          totalDays = 2.0,
          reason = "Family travel",
        ),
      targetRequestId = "lv_offline_01",
      leaveTypeName = "Annual Earned Leave",
      errorReason = "Offline",
    )
    processor.enqueueLeaveReviewAction(
      requestId = "lv_team_02",
      approve = true,
      reviewerNote = "Approved offline",
      errorReason = "Offline",
    )

    val queued = dao.getAllPendingSyncActions()
    assertEquals(3, queued.size)
    assertEquals(
      listOf(SyncActionTypes.CLOCK_IN, SyncActionTypes.LEAVE_SUBMIT, SyncActionTypes.LEAVE_REVIEW),
      queued.map { it.actionType },
    )

    val scheduledWork = workManager.getWorkInfosForUniqueWork(SyncQueueProcessor.UNIQUE_SYNC_WORK_NAME).get()
    assertTrue(scheduledWork.isNotEmpty())
    assertEquals(NetworkType.CONNECTED, scheduledWork.first().constraints.requiredNetworkType)

    // Simulate network still offline -> items remain queued with retryCount incremented
    fakeApi.isOffline = true
    val offlinePass = processor.processAllPendingActions()
    assertEquals(0, offlinePass.syncedCount)
    assertEquals(3, offlinePass.failedCount)
    assertTrue(dao.getAllPendingSyncActions().all { it.retryCount == 1 })

    // Simulate connectivity restored -> all queued attendance & leave actions sync to server
    fakeApi.isOffline = false
    val onlinePass = processor.processAllPendingActions()
    assertEquals(3, onlinePass.syncedCount)
    assertEquals(0, onlinePass.failedCount)
    assertTrue(dao.getAllPendingSyncActions().isEmpty())
    assertEquals(1, fakeApi.punchCalls)
    assertEquals(1, fakeApi.leaveSubmitCalls)
    assertEquals(1, fakeApi.leaveReviewCalls)

    assertEquals("SYNCED", dao.getAttendanceForDate("emp_103", "2026-11-14")?.syncStatus)
    assertEquals("SYNCED", dao.getLeaveRequestById("lv_offline_01")?.syncStatus)
    assertEquals("SYNCED", dao.getLeaveRequestById("lv_team_02")?.syncStatus)
  }

  @Test
  fun `enqueueLeaveCancelAction syncs leave cancellation when network returns`() = runBlocking {
    dao.upsertLeaveRequest(
      LeaveRequestEntity(
        id = "lv_cancel_01",
        employeeId = "emp_103",
        employeeName = "Maya Shrestha",
        leaveTypeId = "LT_CASUAL",
        leaveTypeName = "Casual Personal Leave",
        startDate = "2026-11-28",
        endDate = "2026-11-28",
        totalDays = 1.0,
        reason = "Errand",
        status = "CANCELLED",
        submittedAt = "2026-11-14",
        reviewerNote = "Cancelled by employee",
        isTeamRequest = false,
        syncStatus = "PENDING_SYNC",
      )
    )

    processor.enqueueLeaveCancelAction(requestId = "lv_cancel_01", errorReason = "Offline")
    processor.ensurePeriodicCatchUpSync()

    val periodicInfos =
      workManager.getWorkInfosForUniqueWork(SyncQueueProcessor.UNIQUE_PERIODIC_SYNC_NAME).get()
    assertTrue(periodicInfos.any { it.state == WorkInfo.State.ENQUEUED })

    val result = processor.processAllPendingActions()
    assertEquals(1, result.syncedCount)
    assertEquals(0, result.failedCount)
    assertEquals(1, fakeApi.leaveCancelCalls)
    assertEquals("SYNCED", dao.getLeaveRequestById("lv_cancel_01")?.syncStatus)
  }

  @Test
  fun `work order 409 SERIAL_NOT_IN_VAN routes to NeedsAttentionEntity and preserves telemetry`() = runBlocking {
    val dto =
      com.example.data.remote.WorkOrderCompleteRequestDto(
        workOrderId = "wo_conflict_01",
        ticketCode = "FTTH-9901",
        employeeId = "emp_103",
        opticalPowerDbm = -19.2,
        otdrLossDb = 0.35,
        customerSignatureName = "Ramesh K",
        photoCount = 3,
        consumedCableMeters = 60,
        serialUsed = "BAD-SERIAL-99",
        baseVersion = 1,
        idempotencyKey = "idemp_wo_99",
        deviceTimeEpochMs = 1763100000000L,
      )
    processor.enqueueWorkOrderCompleteAction(dto, errorReason = "Offline")
    val batch = processor.processAllPendingActions()
    assertEquals(0, batch.syncedCount)
    assertEquals(1, batch.conflictCount)

    val needsAttention =
      dao.observeNeedsAttention().first().firstOrNull { it.id == "na_wo_conflict_01" }
    assertEquals("SERIAL_NOT_IN_VAN", needsAttention?.errorCode)
    assertTrue(needsAttention?.capturedSummary?.contains("-19.2 dBm") == true)
    assertTrue(needsAttention?.capturedSummary?.contains("Ramesh K") == true)
  }

  private class FakePulseHrApiService : PulseHrApiService {
    var isOffline = false
    var punchCalls = 0
    var leaveSubmitCalls = 0
    var leaveReviewCalls = 0
    var leaveCancelCalls = 0

    override suspend fun login(request: LoginRequestDto): Response<AuthTokenResponseDto> =
      throw UnsupportedOperationException()

    override suspend fun refreshToken(request: RefreshTokenRequestDto): Response<AuthTokenResponseDto> =
      throw UnsupportedOperationException()

    override suspend fun fetchWorkplaceBootstrap(): Response<com.example.data.remote.WorkplaceBootstrapDto> =
      throw UnsupportedOperationException()

    override suspend fun pullSyncDelta(updatedSince: Long): Response<com.example.data.remote.SyncPullResponseDto> =
      Response.success(com.example.data.remote.SyncPullResponseDto(serverCursor = 1000L))

    override suspend fun registerDevice(
      request: com.example.data.remote.DeviceRegisterRequestDto
    ): Response<com.example.data.remote.DeviceRegisterResponseDto> =
      Response.success(com.example.data.remote.DeviceRegisterResponseDto(true, request.deviceId, "2026-11-14"))

    override suspend fun submitAttendancePunch(
      request: AttendancePunchRequestDto
    ): Response<AttendancePunchResponseDto> {
      if (isOffline) throw IOException("Simulated offline")
      punchCalls++
      return Response.success(AttendancePunchResponseDto("srv_att", "SYNCED", "2026-11-14T09:05:00Z"))
    }

    override suspend fun submitLeaveRequest(
      request: LeaveSubmitRequestDto
    ): Response<LeaveSubmitResponseDto> {
      if (isOffline) throw IOException("Simulated offline")
      leaveSubmitCalls++
      return Response.success(LeaveSubmitResponseDto("srv_lv", "PENDING", "2026-11-14"))
    }

    override suspend fun reviewLeaveRequest(
      requestId: String,
      request: LeaveReviewRequestDto,
    ): Response<Unit> {
      if (isOffline) throw IOException("Simulated offline")
      leaveReviewCalls++
      return Response.success(Unit)
    }

    override suspend fun cancelLeaveRequest(requestId: String): Response<Unit> {
      if (isOffline) throw IOException("Simulated offline")
      leaveCancelCalls++
      return Response.success(Unit)
    }

    override suspend fun updateContact(request: UpdateContactRequestDto): Response<Unit> =
      Response.success(Unit)

    override suspend fun registerFcmToken(
      request: RegisterFcmTokenRequestDto
    ): Response<RegisterFcmTokenResponseDto> =
      Response.success(RegisterFcmTokenResponseDto(true, request.topics))

    override suspend fun completeWorkOrder(
      workOrderId: String,
      request: com.example.data.remote.WorkOrderCompleteRequestDto,
    ): Response<com.example.data.remote.WorkOrderCompleteResponseDto> {
      if (isOffline) throw IOException("Simulated offline")
      if (request.serialUsed?.startsWith("BAD-") == true) {
        val bodyJson =
          """{"code":"SERIAL_NOT_IN_VAN","message":"Serial not assigned to your van","retryable":false}"""
        val mediaType = okhttp3.MediaType.Companion.run { "application/json".toMediaType() }
        val errorBody = okhttp3.ResponseBody.Companion.run { bodyJson.toResponseBody(mediaType) }
        return Response.error(409, errorBody)
      }
      return Response.success(
        com.example.data.remote.WorkOrderCompleteResponseDto(workOrderId, "CLOSED")
      )
    }

    override suspend fun submitExpenseClaim(
      request: com.example.data.remote.ExpenseClaimSubmitRequestDto
    ): Response<com.example.data.remote.ExpenseClaimSubmitResponseDto> {
      if (isOffline) throw IOException("Simulated offline")
      return Response.success(
        com.example.data.remote.ExpenseClaimSubmitResponseDto(
          request.claimId,
          "SUBMITTED",
          "QUEUED_FOR_VERIFICATION",
        )
      )
    }

    override suspend fun assignDispatchWorkOrder(
      request: com.example.data.remote.DispatchAssignRequestDto
    ): Response<Unit> = Response.success(Unit)

    override suspend fun createDelegation(
      request: com.example.data.remote.DelegationCreateRequestDto
    ): Response<Unit> = Response.success(Unit)

    override suspend fun pushLocationBreadcrumbs(
      request: com.example.data.remote.LocationBatchPushRequestDto
    ): Response<com.example.data.remote.LocationBatchPushResponseDto> {
      if (isOffline) throw IOException("Simulated offline")
      return Response.success(
        com.example.data.remote.LocationBatchPushResponseDto(
          acceptedCount = request.points.size,
          serverTimestampEpochMs = System.currentTimeMillis(),
          routeSessionId = request.points.firstOrNull()?.sessionId,
        )
      )
    }
  }
}

