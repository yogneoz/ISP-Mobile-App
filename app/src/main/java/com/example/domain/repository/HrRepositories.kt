package com.example.domain.repository

import android.net.Uri
import com.example.domain.model.AttendanceRecord
import com.example.domain.model.BranchLocation
import com.example.domain.model.CompanyAnnouncement
import com.example.domain.model.DocumentOcrMetadata
import com.example.domain.model.DocumentType
import com.example.domain.model.EmployeeDocument
import com.example.domain.model.EmployeeProfile
import com.example.domain.model.EmployeeRouteSession
import com.example.domain.model.HolidaySchedule
import com.example.domain.model.LeaveBalance
import com.example.domain.model.LeaveRequest
import com.example.domain.model.LocationBreadcrumb
import com.example.domain.model.PayrollRun
import com.example.domain.model.PendingSyncAction
import com.example.domain.model.RoleBundle
import com.example.domain.model.RouteEventType
import com.example.domain.model.UserRole
import com.example.domain.model.UserSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

sealed interface RepoResult<out T> {
  data class Success<T>(val data: T) : RepoResult<T>

  data class Error(val message: String, val cause: Throwable? = null) : RepoResult<Nothing>
}

interface AuthRepository {
  val sessionFlow: Flow<UserSession?>

  suspend fun login(
    email: String,
    password: String,
    role: UserRole,
    bundles: List<RoleBundle> = emptyList(),
  ): RepoResult<UserSession>

  suspend fun logout()

  fun hasActiveSession(): Boolean

  fun hasConfiguredPin(): Boolean

  fun savePin(pin: String)

  fun verifyPin(pin: String): Boolean

  fun getCurrentSession(): UserSession?
}

interface EmployeeRepository {
  fun observeEmployeeProfile(): Flow<EmployeeProfile?>

  fun observeBranches(): Flow<List<BranchLocation>>

  fun observeAnnouncements(): Flow<List<CompanyAnnouncement>>

  fun observeUpcomingHolidays(): Flow<List<HolidaySchedule>>

  suspend fun refreshEmployeeData(): RepoResult<Unit>

  suspend fun ensureSessionDataSeeded()

  suspend fun switchAssignedBranch(branchId: String): RepoResult<EmployeeProfile>

  suspend fun calibrateBranchGeofence(
    branchId: String,
    latitude: Double,
    longitude: Double,
    radiusMeters: Double,
  ): RepoResult<BranchLocation>

  suspend fun updateContactDetails(
    phone: String,
    emergencyContactName: String,
    emergencyContactPhone: String,
  ): RepoResult<EmployeeProfile>

  suspend fun updateProfilePhoto(uri: Uri): RepoResult<EmployeeProfile>
}

interface AttendanceRepository {
  fun observeAttendanceRecords(): Flow<List<AttendanceRecord>>

  fun observePendingSyncActions(): Flow<List<PendingSyncAction>>

  suspend fun refreshAttendance(): RepoResult<Unit>

  suspend fun clockIn(latitude: Double?, longitude: Double?, accuracyMeters: Float?): RepoResult<AttendanceRecord>

  suspend fun clockOut(latitude: Double?, longitude: Double?, accuracyMeters: Float?): RepoResult<AttendanceRecord>

  suspend fun syncPendingActions(): RepoResult<Int>
}

interface LeaveRepository {
  fun observeLeaveBalances(): Flow<List<LeaveBalance>>

  fun observeMyLeaveRequests(): Flow<List<LeaveRequest>>

  fun observeTeamPendingRequests(): Flow<List<LeaveRequest>>

  suspend fun refreshLeaves(): RepoResult<Unit>

  suspend fun submitLeaveRequest(
    leaveTypeId: String,
    startDate: String,
    endDate: String,
    totalDays: Double,
    reason: String,
  ): RepoResult<LeaveRequest>

  suspend fun cancelLeaveRequest(requestId: String): RepoResult<Unit>

  suspend fun reviewLeaveRequest(
    requestId: String,
    approve: Boolean,
    reviewerNote: String,
  ): RepoResult<Unit>
}

interface PayrollRepository {
  fun observePayrollRuns(): Flow<List<PayrollRun>>

  suspend fun refreshPayroll(): RepoResult<Unit>
}

interface DocumentRepository {
  fun observeDocuments(): Flow<List<EmployeeDocument>>

  suspend fun uploadDocument(
    uri: Uri,
    title: String,
    documentType: DocumentType,
    ocrMetadata: DocumentOcrMetadata? = null,
  ): RepoResult<EmployeeDocument>

  suspend fun deleteDocument(documentId: String): RepoResult<Unit>
}

interface RouteTrackingRepository {
  val activeRouteSessionIdFlow: StateFlow<String?>

  val lastRouteSyncEpochMsFlow: StateFlow<Long?>

  fun observeBreadcrumbs(): Flow<List<LocationBreadcrumb>>

  fun observeActiveOrLatestRouteSession(): Flow<EmployeeRouteSession?>

  suspend fun checkInShift(
    latitude: Double,
    longitude: Double,
    accuracyMeters: Float?,
  ): RepoResult<LocationBreadcrumb>

  suspend fun checkOutShift(
    latitude: Double,
    longitude: Double,
    accuracyMeters: Float?,
  ): RepoResult<LocationBreadcrumb>

  suspend fun pushLocationWaypoint(
    latitude: Double,
    longitude: Double,
    accuracyMeters: Float?,
    eventType: RouteEventType = RouteEventType.WAYPOINT,
  ): RepoResult<LocationBreadcrumb>

  suspend fun syncPendingLocationsToDatabase(): RepoResult<Int>
}

