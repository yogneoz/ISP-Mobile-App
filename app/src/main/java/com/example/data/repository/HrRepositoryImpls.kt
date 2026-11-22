package com.example.data.repository

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import com.example.data.local.AttendanceEntity
import com.example.data.local.BranchEntity
import com.example.data.local.EmployeeDocumentEntity
import com.example.data.local.LeaveRequestEntity
import com.example.data.local.PulseHrDao
import com.example.data.remote.AttendancePunchRequestDto
import com.example.data.remote.DeviceRegisterRequestDto
import com.example.data.remote.LeaveReviewRequestDto
import com.example.data.remote.LeaveSubmitRequestDto
import com.example.data.remote.LoginRequestDto
import com.example.data.remote.PulseHrApiService
import com.example.data.remote.UpdateContactRequestDto
import com.example.data.seed.EnterpriseDatabaseSeeder
import com.example.domain.geofence.AttendanceIntegrityScorer
import com.example.domain.geofence.GeofenceEvaluator
import com.example.domain.model.AttendanceRecord
import com.example.domain.model.AttendanceStatus
import com.example.domain.model.BranchLocation
import com.example.domain.model.CompanyAnnouncement
import com.example.domain.model.DocumentOcrMetadata
import com.example.domain.model.DocumentType
import com.example.domain.model.EmployeeDocument
import com.example.domain.model.EmployeeProfile
import com.example.domain.model.HolidayCategory
import com.example.domain.model.HolidaySchedule
import com.example.domain.model.LeaveBalance
import com.example.domain.model.LeaveRequest
import com.example.domain.model.LeaveStatus
import com.example.domain.model.PayrollCategory
import com.example.domain.model.PayrollRun
import com.example.domain.model.PayrollRunLine
import com.example.domain.model.PendingSyncAction
import com.example.domain.model.RoleBundle
import com.example.domain.model.SyncStatus
import com.example.domain.model.UserRole
import com.example.domain.model.UserSession
import com.example.domain.repository.AttendanceRepository
import com.example.domain.repository.AuthRepository
import com.example.domain.repository.DocumentRepository
import com.example.domain.repository.EmployeeRepository
import com.example.domain.repository.LeaveRepository
import com.example.domain.repository.PayrollRepository
import com.example.domain.repository.RepoResult
import com.example.security.SecureTokenStore
import com.example.security.SessionManager
import com.example.worker.SyncQueueProcessor
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

@Singleton
class AuthRepositoryImpl @Inject constructor(
  private val apiService: PulseHrApiService,
  private val tokenStore: SecureTokenStore,
  private val sessionManager: SessionManager,
  private val seeder: EnterpriseDatabaseSeeder,
) : AuthRepository {

  override val sessionFlow: Flow<UserSession?> = tokenStore.sessionFlow

  override suspend fun login(
    email: String,
    password: String,
    role: UserRole,
    bundles: List<RoleBundle>,
  ): RepoResult<UserSession> {
    if (email.isBlank() || password.length < 4) {
      return RepoResult.Error("Enter a valid corporate email and password (min 4 characters).")
    }
    val chosenBundles =
      bundles.ifEmpty {
        listOf(
          when (role) {
            UserRole.HR_MANAGER -> RoleBundle.HR_MANAGER_BUNDLE
            UserRole.HR_OFFICER -> RoleBundle.BRANCH_LEAD
            UserRole.HR_SELF_SERVICE -> RoleBundle.FIELD_TECHNICIAN
          }
        )
      }
    val derivedRole = RoleBundle.highestRoleForBundles(chosenBundles)
    val derivedCaps = RoleBundle.capabilitiesForBundles(chosenBundles)
    val deviceId = tokenStore.getOrCreateDeviceId()

    return try {
      val response =
        apiService.login(
          LoginRequestDto(
            email = email.trim(),
            password = password,
            role = derivedRole.name,
            bundles = chosenBundles.map { it.id },
            deviceId = deviceId,
          )
        )
      if (response.isSuccessful && response.body() != null) {
        val body = response.body()!!
        val parsedRole = runCatching { UserRole.valueOf(body.role) }.getOrDefault(derivedRole)
        val session =
          UserSession(
            userId = body.userId,
            email = body.email,
            role = parsedRole,
            employeeId = body.employeeId,
            isAuthenticated = true,
            capabilities = derivedCaps,
            assignedBundles = chosenBundles.map { it.displayName },
            branchScope = chosenBundles.joinToString(" + ") { it.defaultBranchScope },
            deviceId = deviceId,
            lastOnlineSignInEpochMs = System.currentTimeMillis(),
          )
        tokenStore.saveAuthSession(
          accessToken = body.accessToken,
          refreshToken = body.refreshToken,
          expiresAtEpochMs = System.currentTimeMillis() + (body.expiresInSeconds * 1000L),
          session = session,
        )
        val bootstrapShifts =
          runCatching {
            apiService.registerDevice(
              DeviceRegisterRequestDto(
                deviceId = deviceId,
                employeeId = session.employeeId,
                modelName = "${Build.MANUFACTURER} ${Build.MODEL}",
              )
            )
            apiService.fetchWorkplaceBootstrap().body()?.shifts.orEmpty()
          }.getOrDefault(emptyList())
        seeder.seedForSession(session, bootstrapShifts)
        sessionManager.onFreshLogin()
        RepoResult.Success(session)
      } else {
        RepoResult.Error("Authentication failed (HTTP ${response.code()}).")
      }
    } catch (e: Exception) {
      RepoResult.Error(e.message ?: "Unable to connect to enterprise auth server.", e)
    }
  }

  override suspend fun logout() {
    tokenStore.clearAll()
    sessionManager.onLogout()
  }

  override fun hasActiveSession(): Boolean = tokenStore.hasActiveSession()

  override fun hasConfiguredPin(): Boolean = tokenStore.hasConfiguredPin()

  override fun savePin(pin: String) = tokenStore.savePin(pin)

  override fun verifyPin(pin: String): Boolean = tokenStore.verifyPin(pin)

  override fun getCurrentSession(): UserSession? = tokenStore.getCurrentSession()
}

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class EmployeeRepositoryImpl @Inject constructor(
  private val dao: PulseHrDao,
  private val apiService: PulseHrApiService,
  private val tokenStore: SecureTokenStore,
  private val seeder: EnterpriseDatabaseSeeder,
) : EmployeeRepository {

  override fun observeEmployeeProfile(): Flow<EmployeeProfile?> {
    return tokenStore.sessionFlow.flatMapLatest { session ->
      if (session == null) {
        flowOf(null)
      } else {
        combine(
          dao.observeEmployeeByUserId(session.userId),
          dao.observeBranches(),
        ) { emp, branches ->
          emp?.let { entity ->
            val branchEntity =
              branches.firstOrNull { it.id == entity.branchId }
                ?: branches.firstOrNull()
                ?: BranchEntity("br_hq", "HQ Campus", "MV-HQ", "1600 Amphitheatre Pkwy", 37.4219983, -122.0840, 250.0)
            EmployeeProfile(
              id = entity.id,
              userId = entity.userId,
              employeeCode = entity.employeeCode,
              fullName = entity.fullName,
              email = entity.email,
              phone = entity.phone,
              emergencyContactName = entity.emergencyContactName,
              emergencyContactPhone = entity.emergencyContactPhone,
              department = entity.department,
              designation = entity.designation,
              branch = branchEntity.toDomain(),
              bankName = entity.bankName,
              bankAccountMasked = entity.bankAccountMasked,
              joinDate = entity.joinDate,
              avatarUri = entity.avatarUri,
            )
          }
        }
      }
    }
  }

  override fun observeBranches(): Flow<List<BranchLocation>> =
    dao.observeBranches().map { list -> list.map { it.toDomain() } }

  override fun observeAnnouncements(): Flow<List<CompanyAnnouncement>> =
    dao.observeAnnouncements().map { list ->
      list.map {
        CompanyAnnouncement(
          id = it.id,
          title = it.title,
          summary = it.summary,
          category = it.category,
          publishedAt = it.publishedAt,
          isUrgent = it.isUrgent,
        )
      }
    }

  override fun observeUpcomingHolidays(): Flow<List<HolidaySchedule>> =
    observeEmployeeProfile().map { profile ->
      val branchCode = profile?.branch?.code ?: "MV-HQ"
      val branchRegionalHoliday =
        when (branchCode) {
          "KTM-NOC" ->
            HolidaySchedule(
              id = "hol_branch_ktm",
              title = "Yomari Punhi / Udhauli Parva",
              dateIso = "2026-12-04",
              dayLabel = "Fri, Dec 04",
              category = HolidayCategory.FLOATING,
              branchScope = "KTM-NOC Branch",
              daysUntil = 20,
              description = "Regional cultural holiday for Lalitpur NOC & Fiber Operations.",
            )
          "SF-FIN" ->
            HolidaySchedule(
              id = "hol_branch_sf",
              title = "Bay Area Transit Summit Day",
              dateIso = "2026-12-07",
              dayLabel = "Mon, Dec 07",
              category = HolidayCategory.FLOATING,
              branchScope = "SF-FIN Branch",
              daysUntil = 23,
              description = "Optional remote/floating wellness day for Financial District Hub.",
            )
          else ->
            HolidaySchedule(
              id = "hol_branch_hq",
              title = "Silicon Valley Innovation Day",
              dateIso = "2026-12-04",
              dayLabel = "Fri, Dec 04",
              category = HolidayCategory.FLOATING,
              branchScope = "MV-HQ Campus",
              daysUntil = 20,
              description = "Optional branch floating holiday & community volunteer day.",
            )
        }

      listOf(
        HolidaySchedule(
          id = "hol_thanksgiving",
          title = "Thanksgiving Day",
          dateIso = "2026-11-26",
          dayLabel = "Thu, Nov 26",
          category = HolidayCategory.PUBLIC,
          branchScope = "All Branches",
          daysUntil = 12,
          description = "Mandatory national public holiday; NOC skeleton SLA standby only.",
        ),
        HolidaySchedule(
          id = "hol_day_after_thanksgiving",
          title = "Company Gratitude Day",
          dateIso = "2026-11-27",
          dayLabel = "Fri, Nov 27",
          category = HolidayCategory.CORPORATE,
          branchScope = "All Branches",
          daysUntil = 13,
          description = "Enterprise-wide paid wellness shutdown following Thanksgiving.",
        ),
        branchRegionalHoliday,
        HolidaySchedule(
          id = "hol_christmas",
          title = "Christmas Day",
          dateIso = "2026-12-25",
          dayLabel = "Fri, Dec 25",
          category = HolidayCategory.PUBLIC,
          branchScope = "All Branches",
          daysUntil = 41,
          description = "Observed global corporate holiday; automated shift coverage applies.",
        ),
        HolidaySchedule(
          id = "hol_winter_reset",
          title = "Year-End Engineering Freeze Day",
          dateIso = "2026-12-31",
          dayLabel = "Thu, Dec 31",
          category = HolidayCategory.CORPORATE,
          branchScope = "All Branches",
          daysUntil = 47,
          description = "Paid corporate year-end reset day across all departments.",
        ),
        HolidaySchedule(
          id = "hol_new_year",
          title = "New Year's Day 2027",
          dateIso = "2027-01-01",
          dayLabel = "Fri, Jan 01",
          category = HolidayCategory.PUBLIC,
          branchScope = "All Branches",
          daysUntil = 48,
          description = "Official 2027 kickoff public holiday.",
        ),
      )
    }

  override suspend fun refreshEmployeeData(): RepoResult<Unit> {
    val session = tokenStore.getCurrentSession() ?: return RepoResult.Error("No active session")
    val bootstrapShifts =
      runCatching { apiService.fetchWorkplaceBootstrap().body()?.shifts.orEmpty() }
        .getOrDefault(emptyList())
    seeder.seedForSession(session, bootstrapShifts)
    return RepoResult.Success(Unit)
  }

  override suspend fun ensureSessionDataSeeded() {
    val session = tokenStore.getCurrentSession() ?: return
    val existingEmployee = dao.getEmployeeByUserId(session.userId)
    if (existingEmployee == null) {
      refreshEmployeeData()
    } else if (dao.getEmployeeShiftsSync(session.employeeId).isEmpty()) {
      val bootstrapShifts =
        runCatching { apiService.fetchWorkplaceBootstrap().body()?.shifts.orEmpty() }
          .getOrDefault(emptyList())
      seeder.seedShiftsForSession(session, bootstrapShifts)
    }
  }

  override suspend fun switchAssignedBranch(branchId: String): RepoResult<EmployeeProfile> {
    val session = tokenStore.getCurrentSession() ?: return RepoResult.Error("No active session")
    val emp = dao.getEmployeeByUserId(session.userId) ?: return RepoResult.Error("Employee not found")
    val branch = dao.getBranchById(branchId) ?: return RepoResult.Error("Branch not found")
    val updated = emp.copy(branchId = branchId)
    dao.upsertEmployee(updated)
    return RepoResult.Success(
      EmployeeProfile(
        id = updated.id,
        userId = updated.userId,
        employeeCode = updated.employeeCode,
        fullName = updated.fullName,
        email = updated.email,
        phone = updated.phone,
        emergencyContactName = updated.emergencyContactName,
        emergencyContactPhone = updated.emergencyContactPhone,
        department = updated.department,
        designation = updated.designation,
        branch = branch.toDomain(),
        bankName = updated.bankName,
        bankAccountMasked = updated.bankAccountMasked,
        joinDate = updated.joinDate,
        avatarUri = updated.avatarUri,
      )
    )
  }

  override suspend fun calibrateBranchGeofence(
    branchId: String,
    latitude: Double,
    longitude: Double,
    radiusMeters: Double,
  ): RepoResult<BranchLocation> {
    val existing = dao.getBranchById(branchId) ?: return RepoResult.Error("Branch not found")
    val updated =
      existing.copy(
        latitude = latitude,
        longitude = longitude,
        geofenceRadiusMeters = radiusMeters.coerceIn(50.0, 2000.0),
      )
    dao.upsertBranch(updated)
    return RepoResult.Success(updated.toDomain())
  }

  override suspend fun updateContactDetails(
    phone: String,
    emergencyContactName: String,
    emergencyContactPhone: String,
  ): RepoResult<EmployeeProfile> {
    val session = tokenStore.getCurrentSession() ?: return RepoResult.Error("No active session")
    val emp = dao.getEmployeeByUserId(session.userId) ?: return RepoResult.Error("Employee not found")
    val updated =
      emp.copy(
        phone = phone.trim(),
        emergencyContactName = emergencyContactName.trim(),
        emergencyContactPhone = emergencyContactPhone.trim(),
      )
    dao.upsertEmployee(updated)
    runCatching {
      apiService.updateContact(
        UpdateContactRequestDto(
          phone = updated.phone,
          emergencyContactName = updated.emergencyContactName,
          emergencyContactPhone = updated.emergencyContactPhone,
        )
      )
    }
    val branch = dao.getBranchById(updated.branchId)!!
    return RepoResult.Success(
      EmployeeProfile(
        id = updated.id,
        userId = updated.userId,
        employeeCode = updated.employeeCode,
        fullName = updated.fullName,
        email = updated.email,
        phone = updated.phone,
        emergencyContactName = updated.emergencyContactName,
        emergencyContactPhone = updated.emergencyContactPhone,
        department = updated.department,
        designation = updated.designation,
        branch = branch.toDomain(),
        bankName = updated.bankName,
        bankAccountMasked = updated.bankAccountMasked,
        joinDate = updated.joinDate,
        avatarUri = updated.avatarUri,
      )
    )
  }

  override suspend fun updateProfilePhoto(uri: Uri): RepoResult<EmployeeProfile> {
    val session = tokenStore.getCurrentSession() ?: return RepoResult.Error("No active session")
    val emp = dao.getEmployeeByUserId(session.userId) ?: return RepoResult.Error("Employee not found")
    val updated = emp.copy(avatarUri = uri.toString())
    dao.upsertEmployee(updated)
    val branch = dao.getBranchById(updated.branchId)!!
    return RepoResult.Success(
      EmployeeProfile(
        id = updated.id,
        userId = updated.userId,
        employeeCode = updated.employeeCode,
        fullName = updated.fullName,
        email = updated.email,
        phone = updated.phone,
        emergencyContactName = updated.emergencyContactName,
        emergencyContactPhone = updated.emergencyContactPhone,
        department = updated.department,
        designation = updated.designation,
        branch = branch.toDomain(),
        bankName = updated.bankName,
        bankAccountMasked = updated.bankAccountMasked,
        joinDate = updated.joinDate,
        avatarUri = updated.avatarUri,
      )
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
}

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class AttendanceRepositoryImpl @Inject constructor(
  @ApplicationContext private val context: Context,
  private val dao: PulseHrDao,
  private val apiService: PulseHrApiService,
  private val tokenStore: SecureTokenStore,
  private val geofenceEvaluator: GeofenceEvaluator,
  private val integrityScorer: AttendanceIntegrityScorer,
  private val syncQueueProcessor: SyncQueueProcessor,
) : AttendanceRepository {

  override fun observeAttendanceRecords(): Flow<List<AttendanceRecord>> {
    return tokenStore.sessionFlow.flatMapLatest { session ->
      if (session == null) {
        flowOf(emptyList())
      } else {
        dao.observeAttendance(session.employeeId).map { list -> list.map { it.toDomain() } }
      }
    }
  }

  override fun observePendingSyncActions(): Flow<List<PendingSyncAction>> =
    dao.observePendingSyncActions().map { list ->
      list.map {
        PendingSyncAction(
          id = it.id,
          actionType = it.actionType,
          description = it.description,
          createdAtEpochMs = it.createdAtEpochMs,
          retryCount = it.retryCount,
          lastError = it.lastError,
          idempotencyKey = it.idempotencyKey,
          stateLabel = it.stateLabel,
        )
      }
    }

  override suspend fun refreshAttendance(): RepoResult<Unit> = RepoResult.Success(Unit)

  override suspend fun clockIn(
    latitude: Double?,
    longitude: Double?,
    accuracyMeters: Float?,
  ): RepoResult<AttendanceRecord> {
    return performPunch(isClockIn = true, latitude = latitude, longitude = longitude, accuracyMeters = accuracyMeters)
  }

  override suspend fun clockOut(
    latitude: Double?,
    longitude: Double?,
    accuracyMeters: Float?,
  ): RepoResult<AttendanceRecord> {
    return performPunch(isClockIn = false, latitude = latitude, longitude = longitude, accuracyMeters = accuracyMeters)
  }

  private suspend fun performPunch(
    isClockIn: Boolean,
    latitude: Double?,
    longitude: Double?,
    accuracyMeters: Float?,
  ): RepoResult<AttendanceRecord> {
    val session = tokenStore.getCurrentSession() ?: return RepoResult.Error("Session expired.")
    val employee =
      dao.getEmployeeByUserId(session.userId)
        ?: return RepoResult.Error("Employee profile not loaded.")
    val branchEntity =
      dao.getBranchById(employee.branchId)
        ?: return RepoResult.Error("Assigned branch geofence not found.")

    val branch =
      BranchLocation(
        id = branchEntity.id,
        name = branchEntity.name,
        code = branchEntity.code,
        address = branchEntity.address,
        latitude = branchEntity.latitude,
        longitude = branchEntity.longitude,
        geofenceRadiusMeters = branchEntity.geofenceRadiusMeters,
      )

    // Perimeter check via FusedLocation coordinates
    val evaluation = geofenceEvaluator.evaluate(branch, latitude, longitude, accuracyMeters)
    if (!evaluation.isInsideGeofence || latitude == null || longitude == null) {
      return RepoResult.Error(
        evaluation.failureReason
          ?: "Outside assigned branch geofence. Clock-in/out action blocked."
      )
    }

    val assessment =
      integrityScorer.assessPunch(
        gpsAccuracyMeters = accuracyMeters,
        hasLocationFix = true,
        clockDriftMs = 0L,
        isMockLocationSuspected = false,
        wifiBssid = "84:1B:5E:22:10:A1",
      )

    val todayAd = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    val nowTime = SimpleDateFormat("hh:mm a", Locale.US).format(Date())
    val existingToday = dao.getAttendanceForDate(employee.id, todayAd)

    if (isClockIn && existingToday?.checkInTime != null) {
      return RepoResult.Error("Already clocked in today at ${existingToday.checkInTime}.")
    }
    if (!isClockIn && existingToday?.checkInTime == null) {
      return RepoResult.Error("Cannot clock out before clocking in for today.")
    }
    if (!isClockIn && existingToday?.checkOutTime != null) {
      return RepoResult.Error("Already clocked out today at ${existingToday.checkOutTime}.")
    }

    val recordId = existingToday?.id ?: "att_${todayAd}_${employee.id}"
    val distance = evaluation.distanceMeters ?: 0.0
    val idempKey = "punch_${recordId}_${if (isClockIn) "in" else "out"}"

    val updatedEntity =
      if (isClockIn) {
        AttendanceEntity(
          id = recordId,
          employeeId = employee.id,
          branchId = branch.id,
          attendanceDateAd = todayAd,
          checkInTime = nowTime,
          checkOutTime = null,
          status = "PRESENT",
          source = "MOBILE",
          checkInLatitude = latitude,
          checkInLongitude = longitude,
          distanceFromBranchMeters = distance,
          overtimeHours = 0.0,
          syncStatus = "PENDING_SYNC",
          riskScore = assessment.riskScore,
          integrityStatus = assessment.status.name,
          integrityNotes = assessment.notes,
          gpsAccuracyMeters = accuracyMeters,
          wifiBssid = "84:1B:5E:22:10:A1",
        )
      } else {
        existingToday!!.copy(
          checkOutTime = nowTime,
          checkOutLatitude = latitude,
          checkOutLongitude = longitude,
          distanceFromBranchMeters = distance,
          syncStatus = "PENDING_SYNC",
          riskScore = maxOf(existingToday.riskScore, assessment.riskScore),
          integrityStatus = assessment.status.name,
          integrityNotes = assessment.notes,
          gpsAccuracyMeters = accuracyMeters,
        )
      }

    dao.upsertAttendance(updatedEntity)

    val dto =
      AttendancePunchRequestDto(
        employeeId = employee.id,
        branchId = branch.id,
        attendanceDateAd = todayAd,
        action = if (isClockIn) "CLOCK_IN" else "CLOCK_OUT",
        punchTime = nowTime,
        latitude = latitude,
        longitude = longitude,
        distanceMeters = distance,
        gpsAccuracyMeters = accuracyMeters,
        wifiBssid = "84:1B:5E:22:10:A1",
        deviceTimeEpochMs = System.currentTimeMillis(),
        idempotencyKey = idempKey,
        source = "MOBILE",
      )

    return try {
      val response = apiService.submitAttendancePunch(dto)
      if (response.isSuccessful) {
        val body = response.body()
        val synced =
          updatedEntity.copy(
            syncStatus = "SYNCED",
            riskScore = body?.riskScore ?: assessment.riskScore,
            integrityStatus = body?.integrityStatus ?: assessment.status.name,
            integrityNotes = body?.integrityNotes ?: assessment.notes,
          )
        dao.upsertAttendance(synced)
        tokenStore.setLastGeofenceInsideState(true)
        RepoResult.Success(synced.toDomain())
      } else {
        enqueueOfflinePunch(dto, recordId, isClockIn, nowTime, branch.name, "HTTP ${response.code()}")
        RepoResult.Success(updatedEntity.toDomain())
      }
    } catch (e: Exception) {
      enqueueOfflinePunch(dto, recordId, isClockIn, nowTime, branch.name, e.message ?: "Offline")
      RepoResult.Success(updatedEntity.toDomain())
    }
  }

  private suspend fun enqueueOfflinePunch(
    dto: AttendancePunchRequestDto,
    recordId: String,
    isClockIn: Boolean,
    timeLabel: String,
    branchName: String,
    errorReason: String,
  ) {
    syncQueueProcessor.enqueueAttendanceAction(
      isClockIn = isClockIn,
      dto = dto,
      targetRecordId = recordId,
      description = "${if (isClockIn) "Clock-In" else "Clock-Out"} at $timeLabel ($branchName)",
      errorReason = errorReason,
    )
  }

  override suspend fun syncPendingActions(): RepoResult<Int> {
    val result = syncQueueProcessor.processAllPendingActions()
    return if (result.failedCount > 0 && result.syncedCount == 0 && result.conflictCount == 0) {
      RepoResult.Error("Sync paused: ${result.lastError ?: "Device offline"}")
    } else {
      RepoResult.Success(result.syncedCount)
    }
  }

  private fun AttendanceEntity.toDomain() =
    AttendanceRecord(
      id = id,
      employeeId = employeeId,
      branchId = branchId,
      attendanceDateAd = attendanceDateAd,
      checkInTime = checkInTime,
      checkOutTime = checkOutTime,
      status = runCatching { AttendanceStatus.valueOf(status) }.getOrDefault(AttendanceStatus.PRESENT),
      source = source,
      checkInLatitude = checkInLatitude,
      checkInLongitude = checkInLongitude,
      checkOutLatitude = checkOutLatitude,
      checkOutLongitude = checkOutLongitude,
      distanceFromBranchMeters = distanceFromBranchMeters,
      overtimeHours = overtimeHours,
      syncStatus = runCatching { SyncStatus.valueOf(syncStatus) }.getOrDefault(SyncStatus.SYNCED),
      riskScore = riskScore,
      integrityStatus = integrityStatus,
      integrityNotes = integrityNotes,
      gpsAccuracyMeters = gpsAccuracyMeters,
      wifiBssid = wifiBssid,
    )
}

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class LeaveRepositoryImpl @Inject constructor(
  private val dao: PulseHrDao,
  private val apiService: PulseHrApiService,
  private val tokenStore: SecureTokenStore,
  private val syncQueueProcessor: SyncQueueProcessor,
) : LeaveRepository {

  override fun observeLeaveBalances(): Flow<List<LeaveBalance>> =
    tokenStore.sessionFlow.flatMapLatest { session ->
      if (session == null) flowOf(emptyList())
      else {
        dao.observeLeaveBalances(session.employeeId).map { list ->
          list.map {
            LeaveBalance(
              id = it.id,
              employeeId = it.employeeId,
              leaveTypeId = it.leaveTypeId,
              leaveTypeName = it.leaveTypeName,
              leaveCode = it.leaveCode,
              allocatedDays = it.allocatedDays,
              usedDays = it.usedDays,
              pendingDays = it.pendingDays,
              remainingDays = it.remainingDays,
            )
          }
        }
      }
    }

  override fun observeMyLeaveRequests(): Flow<List<LeaveRequest>> =
    tokenStore.sessionFlow.flatMapLatest { session ->
      if (session == null) flowOf(emptyList())
      else dao.observeMyLeaveRequests(session.employeeId).map { list -> list.map { it.toDomain() } }
    }

  override fun observeTeamPendingRequests(): Flow<List<LeaveRequest>> =
    tokenStore.sessionFlow.flatMapLatest { session ->
      if (session == null || !session.canApproveAnything()) flowOf(emptyList())
      else dao.observeTeamLeaveRequests().map { list -> list.map { it.toDomain() } }
    }

  override suspend fun refreshLeaves(): RepoResult<Unit> = RepoResult.Success(Unit)

  override suspend fun submitLeaveRequest(
    leaveTypeId: String,
    startDate: String,
    endDate: String,
    totalDays: Double,
    reason: String,
  ): RepoResult<LeaveRequest> {
    val session = tokenStore.getCurrentSession() ?: return RepoResult.Error("No active session")
    if (reason.isBlank()) return RepoResult.Error("Please enter a reason for leave.")
    if (totalDays <= 0.0) return RepoResult.Error("End date must be on or after start date.")

    val emp = dao.getEmployeeByUserId(session.userId)
    val leaveTypeName =
      when (leaveTypeId) {
        "LT_ANNUAL" -> "Annual Earned Leave"
        "LT_SICK" -> "Medical & Sick Leave"
        "LT_CASUAL" -> "Casual Personal Leave"
        else -> "Floating Holiday"
      }
    val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    val requestId = "lv_${UUID.randomUUID().toString().take(8)}"
    val entity =
      LeaveRequestEntity(
        id = requestId,
        employeeId = session.employeeId,
        employeeName = emp?.fullName ?: "Self",
        leaveTypeId = leaveTypeId,
        leaveTypeName = leaveTypeName,
        startDate = startDate,
        endDate = endDate,
        totalDays = totalDays,
        reason = reason.trim(),
        status = "PENDING",
        submittedAt = today,
        reviewerNote = null,
        isTeamRequest = false,
        syncStatus = "PENDING_SYNC",
      )
    dao.upsertLeaveRequest(entity)
    dao.adjustLeavePendingBalance(session.employeeId, leaveTypeId, totalDays)

    val dto =
      LeaveSubmitRequestDto(
        employeeId = session.employeeId,
        leaveTypeId = leaveTypeId,
        startDate = startDate,
        endDate = endDate,
        totalDays = totalDays,
        reason = reason.trim(),
      )

    val finalEntity =
      try {
        val response = apiService.submitLeaveRequest(dto)
        if (response.isSuccessful) {
          dao.updateLeaveSyncStatus(requestId, "SYNCED")
          entity.copy(syncStatus = "SYNCED")
        } else {
          syncQueueProcessor.enqueueLeaveSubmitAction(
            dto = dto,
            targetRequestId = requestId,
            leaveTypeName = leaveTypeName,
            errorReason = "HTTP ${response.code()}",
          )
          entity
        }
      } catch (e: Exception) {
        syncQueueProcessor.enqueueLeaveSubmitAction(
          dto = dto,
          targetRequestId = requestId,
          leaveTypeName = leaveTypeName,
          errorReason = e.message ?: "Offline",
        )
        entity
      }

    return RepoResult.Success(finalEntity.toDomain())
  }

  override suspend fun cancelLeaveRequest(requestId: String): RepoResult<Unit> {
    val existing = dao.getLeaveRequestById(requestId) ?: return RepoResult.Error("Request not found")
    if (existing.status != "PENDING") {
      return RepoResult.Error("Only pending leave requests can be cancelled.")
    }
    dao.updateLeaveStatusWithSync(requestId, "CANCELLED", "Cancelled by employee", "PENDING_SYNC")
    dao.adjustLeavePendingBalance(existing.employeeId, existing.leaveTypeId, -existing.totalDays)

    try {
      val response = apiService.cancelLeaveRequest(requestId)
      if (response.isSuccessful) {
        dao.updateLeaveSyncStatus(requestId, "SYNCED")
      } else {
        syncQueueProcessor.enqueueLeaveCancelAction(requestId, "HTTP ${response.code()}")
      }
    } catch (e: Exception) {
      syncQueueProcessor.enqueueLeaveCancelAction(requestId, e.message ?: "Offline")
    }
    return RepoResult.Success(Unit)
  }

  override suspend fun reviewLeaveRequest(
    requestId: String,
    approve: Boolean,
    reviewerNote: String,
  ): RepoResult<Unit> {
    val session = tokenStore.getCurrentSession() ?: return RepoResult.Error("No active session")
    val existing = dao.getLeaveRequestById(requestId) ?: return RepoResult.Error("Request not found")
    if (existing.employeeId == session.employeeId) {
      return RepoResult.Error("No self-approval: requester and approver cannot be the same employee.")
    }
    if (!session.canApproveAnything()) {
      return RepoResult.Error("Unauthorized: approval capability required.")
    }
    val newStatus = if (approve) "APPROVED" else "REJECTED"
    val note =
      reviewerNote.ifBlank {
        if (approve) "Approved via MyWorkplace v2" else "Declined via MyWorkplace v2"
      }
    dao.updateLeaveStatusWithSync(requestId, newStatus, note, "PENDING_SYNC")

    try {
      val response = apiService.reviewLeaveRequest(requestId, LeaveReviewRequestDto(approve, note))
      if (response.isSuccessful) {
        dao.updateLeaveSyncStatus(requestId, "SYNCED")
      } else {
        syncQueueProcessor.enqueueLeaveReviewAction(
          requestId = requestId,
          approve = approve,
          reviewerNote = note,
          errorReason = "HTTP ${response.code()}",
        )
      }
    } catch (e: Exception) {
      syncQueueProcessor.enqueueLeaveReviewAction(
        requestId = requestId,
        approve = approve,
        reviewerNote = note,
        errorReason = e.message ?: "Offline",
      )
    }
    return RepoResult.Success(Unit)
  }

  private fun LeaveRequestEntity.toDomain() =
    LeaveRequest(
      id = id,
      employeeId = employeeId,
      employeeName = employeeName,
      leaveTypeId = leaveTypeId,
      leaveTypeName = leaveTypeName,
      startDate = startDate,
      endDate = endDate,
      totalDays = totalDays,
      reason = reason,
      status = runCatching { LeaveStatus.valueOf(status) }.getOrDefault(LeaveStatus.PENDING),
      submittedAt = submittedAt,
      reviewerNote = reviewerNote,
      syncStatus = runCatching { SyncStatus.valueOf(syncStatus) }.getOrDefault(SyncStatus.SYNCED),
    )
}

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class PayrollRepositoryImpl @Inject constructor(
  private val dao: PulseHrDao,
  private val tokenStore: SecureTokenStore,
) : PayrollRepository {

  override fun observePayrollRuns(): Flow<List<PayrollRun>> {
    return tokenStore.sessionFlow.flatMapLatest { session ->
      if (session == null) {
        flowOf(emptyList())
      } else {
        combine(
          dao.observePayrollRuns(session.employeeId),
          dao.observePayrollRunLines(session.employeeId),
        ) { runs, lines ->
          runs.map { run ->
            PayrollRun(
              id = run.id,
              employeeId = run.employeeId,
              monthLabel = run.monthLabel,
              periodStart = run.periodStart,
              periodEnd = run.periodEnd,
              payDate = run.payDate,
              status = run.status,
              currency = run.currency,
              grossPay = run.grossPay,
              totalDeductions = run.totalDeductions,
              ssfTotal = run.ssfTotal,
              tdsTax = run.tdsTax,
              netPay = run.netPay,
              lines =
                lines
                  .filter { it.payrollRunId == run.id }
                  .map { line ->
                    PayrollRunLine(
                      id = line.id,
                      payrollRunId = line.payrollRunId,
                      employeeId = line.employeeId,
                      componentName = line.componentName,
                      category =
                        runCatching { PayrollCategory.valueOf(line.category) }
                          .getOrDefault(PayrollCategory.EARNING),
                      amount = line.amount,
                      description = line.description,
                    )
                  },
            )
          }
        }
      }
    }
  }

  override suspend fun refreshPayroll(): RepoResult<Unit> = RepoResult.Success(Unit)
}

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class DocumentRepositoryImpl @Inject constructor(
  @ApplicationContext private val context: Context,
  private val dao: PulseHrDao,
  private val tokenStore: SecureTokenStore,
) : DocumentRepository {

  override fun observeDocuments(): Flow<List<EmployeeDocument>> {
    return tokenStore.sessionFlow.flatMapLatest { session ->
      if (session == null) {
        flowOf(emptyList())
      } else {
        dao.observeDocuments(session.employeeId).map { list ->
          list.map {
            EmployeeDocument(
              id = it.id,
              employeeId = it.employeeId,
              title = it.title,
              documentType =
                runCatching { DocumentType.valueOf(it.documentType) }
                  .getOrDefault(DocumentType.COMPLIANCE),
              fileName = it.fileName,
              mimeType = it.mimeType,
              fileSizeBytes = it.fileSizeBytes,
              uploadedAt = it.uploadedAt,
              localUri = it.localUri,
              verificationStatus = it.verificationStatus,
              extractedText = it.extractedText,
              detectedVendorOrIssuer = it.detectedVendorOrIssuer,
              detectedDate = it.detectedDate,
              detectedAmount = it.detectedAmount,
              detectedReferenceId = it.detectedReferenceId,
              ocrConfidenceLabel = it.ocrConfidenceLabel,
            )
          }
        }
      }
    }
  }

  override suspend fun uploadDocument(
    uri: Uri,
    title: String,
    documentType: DocumentType,
    ocrMetadata: DocumentOcrMetadata?,
  ): RepoResult<EmployeeDocument> {
    val session = tokenStore.getCurrentSession() ?: return RepoResult.Error("No active session")
    val resolver = context.contentResolver

    val mimeType = resolver.getType(uri) ?: "application/octet-stream"
    if (mimeType !in ALLOWED_MIME_TYPES) {
      return RepoResult.Error(
        "Unsupported file type ($mimeType). Only PDF, JPEG, and PNG files are accepted."
      )
    }

    var fileName = "document_${System.currentTimeMillis()}"
    var fileSizeBytes = -1L

    resolver.query(uri, null, null, null, null)?.use { cursor ->
      val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
      val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
      if (cursor.moveToFirst()) {
        if (nameIndex >= 0) fileName = cursor.getString(nameIndex) ?: fileName
        if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) fileSizeBytes = cursor.getLong(sizeIndex)
      }
    }

    if (fileSizeBytes < 0L) {
      fileSizeBytes =
        resolver.openInputStream(uri)?.use { stream ->
          var total = 0L
          val buf = ByteArray(8192)
          while (true) {
            val read = stream.read(buf)
            if (read <= 0) break
            total += read
            if (total > MAX_DOCUMENT_BYTES) break
          }
          total
        } ?: 0L
    }

    if (fileSizeBytes > MAX_DOCUMENT_BYTES) {
      val sizeMb = String.format(Locale.US, "%.2f", fileSizeBytes / (1024.0 * 1024.0))
      return RepoResult.Error(
        "File size ($sizeMb MB) exceeds the 5.00 MB limit for hr_employee_documents."
      )
    }

    val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    val entity =
      EmployeeDocumentEntity(
        id = "doc_${UUID.randomUUID().toString().take(8)}",
        employeeId = session.employeeId,
        title = title.ifBlank { fileName },
        documentType = documentType.name,
        fileName = fileName,
        mimeType = mimeType,
        fileSizeBytes = fileSizeBytes.coerceAtLeast(1L),
        uploadedAt = today,
        localUri = uri.toString(),
        verificationStatus = "PENDING_REVIEW",
        extractedText = ocrMetadata?.extractedText,
        detectedVendorOrIssuer = ocrMetadata?.detectedVendorOrIssuer,
        detectedDate = ocrMetadata?.detectedDate,
        detectedAmount = ocrMetadata?.detectedAmount,
        detectedReferenceId = ocrMetadata?.detectedReferenceId,
        ocrConfidenceLabel = ocrMetadata?.ocrConfidenceLabel,
      )
    dao.upsertDocument(entity)

    return RepoResult.Success(
      EmployeeDocument(
        id = entity.id,
        employeeId = entity.employeeId,
        title = entity.title,
        documentType = documentType,
        fileName = entity.fileName,
        mimeType = entity.mimeType,
        fileSizeBytes = entity.fileSizeBytes,
        uploadedAt = entity.uploadedAt,
        localUri = entity.localUri,
        verificationStatus = entity.verificationStatus,
        extractedText = entity.extractedText,
        detectedVendorOrIssuer = entity.detectedVendorOrIssuer,
        detectedDate = entity.detectedDate,
        detectedAmount = entity.detectedAmount,
        detectedReferenceId = entity.detectedReferenceId,
        ocrConfidenceLabel = entity.ocrConfidenceLabel,
      )
    )
  }

  override suspend fun deleteDocument(documentId: String): RepoResult<Unit> {
    dao.deleteDocument(documentId)
    return RepoResult.Success(Unit)
  }

  companion object {
    const val MAX_DOCUMENT_BYTES = 5L * 1024L * 1024L // 5 MB
    val ALLOWED_MIME_TYPES = setOf("application/pdf", "image/jpeg", "image/png")
  }
}
