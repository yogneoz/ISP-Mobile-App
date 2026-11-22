package com.example.domain.model

enum class UserRole(val displayName: String, val badgeLabel: String) {
  HR_SELF_SERVICE("Employee Self-Service", "SELF SERVICE"),
  HR_OFFICER("HR Officer", "HR OFFICER"),
  HR_MANAGER("HR Manager", "HR MANAGER");

  fun canApproveLeaves(): Boolean = this == HR_OFFICER || this == HR_MANAGER

  fun canManageGeofences(): Boolean = this == HR_MANAGER
}

enum class AppCapability(
  val key: String,
  val label: String,
  val scopeLabel: String,
) {
  ATTENDANCE_PUNCH("attendance.punch", "Attendance Punch", "Self"),
  ESS_VIEW("ess.view", "My HR & ESS", "Self"),
  CLAIMS_SUBMIT("claims.submit", "Claims & Overtime", "Self"),
  JOBS_EXECUTE("jobs.execute", "My Work Orders & Route Log", "Assigned tickets"),
  STOCK_VAN("stock.van", "My Van Stock & Transfers", "Own van"),
  DISPATCH_ASSIGN("dispatch.assign", "Dispatch Board", "Branch or region"),
  ROSTER_MANAGE("roster.manage", "Shift & Standby Roster", "Branch or region"),
  APPROVE_ATTENDANCE_OT("approve.attendance_ot", "Approve Late Punches & OT", "Direct reports"),
  APPROVE_EXPENSE("approve.expense", "Approve Expense Claims", "Direct reports"),
  AUDIT_STOCK("audit.stock", "Stock Reconciliation Audit", "Branch or region");

  companion object {
    fun fromKey(key: String): AppCapability? = entries.firstOrNull { it.key == key }
  }
}

enum class RoleBundle(
  val id: String,
  val displayName: String,
  val badgeLabel: String,
  val baseRole: UserRole,
  val defaultEmail: String,
  val defaultBranchScope: String,
  val capabilities: Set<AppCapability>,
) {
  FIELD_TECHNICIAN(
    id = "bundle_field_tech",
    displayName = "Field Technician",
    badgeLabel = "FIELD TECH",
    baseRole = UserRole.HR_SELF_SERVICE,
    defaultEmail = "maya.shrestha@pulsehr.com",
    defaultBranchScope = "MV-HQ • Fiber Zone A",
    capabilities =
      setOf(
        AppCapability.ATTENDANCE_PUNCH,
        AppCapability.ESS_VIEW,
        AppCapability.CLAIMS_SUBMIT,
        AppCapability.JOBS_EXECUTE,
        AppCapability.STOCK_VAN,
      ),
  ),
  BRANCH_LEAD(
    id = "bundle_branch_lead",
    displayName = "Branch / Crew Lead",
    badgeLabel = "BRANCH LEAD",
    baseRole = UserRole.HR_OFFICER,
    defaultEmail = "sita.gurung@pulsehr.com",
    defaultBranchScope = "MV-HQ & SF-FIN",
    capabilities =
      setOf(
        AppCapability.ATTENDANCE_PUNCH,
        AppCapability.ESS_VIEW,
        AppCapability.CLAIMS_SUBMIT,
        AppCapability.JOBS_EXECUTE,
        AppCapability.STOCK_VAN,
        AppCapability.ROSTER_MANAGE,
        AppCapability.APPROVE_ATTENDANCE_OT,
        AppCapability.APPROVE_EXPENSE,
      ),
  ),
  DISPATCH_COORDINATOR(
    id = "bundle_dispatch",
    displayName = "Dispatch & Audit Coordinator",
    badgeLabel = "DISPATCH",
    baseRole = UserRole.HR_OFFICER,
    defaultEmail = "sita.gurung@pulsehr.com",
    defaultBranchScope = "Region West NOC",
    capabilities =
      setOf(
        AppCapability.ATTENDANCE_PUNCH,
        AppCapability.ESS_VIEW,
        AppCapability.CLAIMS_SUBMIT,
        AppCapability.DISPATCH_ASSIGN,
        AppCapability.ROSTER_MANAGE,
        AppCapability.AUDIT_STOCK,
        AppCapability.APPROVE_EXPENSE,
      ),
  ),
  HR_MANAGER_BUNDLE(
    id = "bundle_hr_mgr",
    displayName = "HR & Operations Director",
    badgeLabel = "HR + OPS DIR",
    baseRole = UserRole.HR_MANAGER,
    defaultEmail = "aarav.adhikari@pulsehr.com",
    defaultBranchScope = "All Branches",
    capabilities = AppCapability.entries.toSet(),
  );

  companion object {
    fun capabilitiesForBundles(bundles: Collection<RoleBundle>): Set<AppCapability> =
      bundles.flatMap { it.capabilities }.toSet().ifEmpty { FIELD_TECHNICIAN.capabilities }

    fun highestRoleForBundles(bundles: Collection<RoleBundle>): UserRole {
      return when {
        bundles.any { it.baseRole == UserRole.HR_MANAGER } -> UserRole.HR_MANAGER
        bundles.any { it.baseRole == UserRole.HR_OFFICER } -> UserRole.HR_OFFICER
        else -> UserRole.HR_SELF_SERVICE
      }
    }
  }
}

data class UserSession(
  val userId: String,
  val email: String,
  val role: UserRole,
  val employeeId: String,
  val isAuthenticated: Boolean,
  val capabilities: Set<AppCapability> = RoleBundle.FIELD_TECHNICIAN.capabilities,
  val assignedBundles: List<String> = listOf(RoleBundle.FIELD_TECHNICIAN.displayName),
  val branchScope: String = RoleBundle.FIELD_TECHNICIAN.defaultBranchScope,
  val deviceId: String = "dev_android_primary",
  val lastOnlineSignInEpochMs: Long = System.currentTimeMillis(),
) {
  fun hasCapability(capability: AppCapability): Boolean = capability in capabilities

  fun canApproveAnything(): Boolean =
    hasCapability(AppCapability.APPROVE_ATTENDANCE_OT) ||
      hasCapability(AppCapability.APPROVE_EXPENSE) ||
      role.canApproveLeaves()
}

data class BranchLocation(
  val id: String,
  val name: String,
  val code: String,
  val address: String,
  val latitude: Double,
  val longitude: Double,
  val geofenceRadiusMeters: Double,
)

data class EmployeeProfile(
  val id: String,
  val userId: String,
  val employeeCode: String,
  val fullName: String,
  val email: String,
  val phone: String,
  val emergencyContactName: String,
  val emergencyContactPhone: String,
  val department: String,
  val designation: String,
  val branch: BranchLocation,
  val bankName: String,
  val bankAccountMasked: String,
  val joinDate: String,
  val avatarUri: String? = null,
)

enum class AttendanceStatus(val label: String) {
  PRESENT("Present"),
  ABSENT("Absent"),
  LEAVE("On Leave"),
  HALF_DAY("Half Day"),
  WEEKEND("Weekend"),
  HOLIDAY("Holiday"),
}

enum class SyncStatus(val label: String) {
  SYNCED("Synced"),
  PENDING_SYNC("Pending Sync"),
  FAILED("Sync Retry"),
}

data class AttendanceRecord(
  val id: String,
  val employeeId: String,
  val branchId: String,
  val attendanceDateAd: String, // YYYY-MM-DD
  val checkInTime: String?,
  val checkOutTime: String?,
  val status: AttendanceStatus,
  val source: String = "MOBILE",
  val checkInLatitude: Double? = null,
  val checkInLongitude: Double? = null,
  val checkOutLatitude: Double? = null,
  val checkOutLongitude: Double? = null,
  val distanceFromBranchMeters: Double? = null,
  val overtimeHours: Double = 0.0,
  val syncStatus: SyncStatus = SyncStatus.SYNCED,
  val riskScore: Int = 0,
  val integrityStatus: String = "VERIFIED",
  val integrityNotes: String? = null,
  val gpsAccuracyMeters: Float? = null,
  val wifiBssid: String? = null,
)

data class GeofenceEvaluation(
  val isInsideGeofence: Boolean,
  val distanceMeters: Double?,
  val branchRadiusMeters: Double,
  val branchName: String,
  val userLatitude: Double?,
  val userLongitude: Double?,
  val gpsAccuracyMeters: Float?,
  val failureReason: String? = null,
)

data class LeaveBalance(
  val id: String,
  val employeeId: String,
  val leaveTypeId: String,
  val leaveTypeName: String,
  val leaveCode: String,
  val allocatedDays: Double,
  val usedDays: Double,
  val pendingDays: Double,
  val remainingDays: Double,
)

enum class LeaveStatus(val label: String) {
  PENDING("Pending"),
  APPROVED("Approved"),
  REJECTED("Rejected"),
  CANCELLED("Cancelled"),
}

data class LeaveRequest(
  val id: String,
  val employeeId: String,
  val employeeName: String,
  val leaveTypeId: String,
  val leaveTypeName: String,
  val startDate: String,
  val endDate: String,
  val totalDays: Double,
  val reason: String,
  val status: LeaveStatus,
  val submittedAt: String,
  val reviewerNote: String? = null,
  val syncStatus: SyncStatus = SyncStatus.SYNCED,
)

enum class PayrollCategory(val label: String) {
  EARNING("Earnings"),
  DEDUCTION("Deductions"),
  SSF("SSF Contribution"),
  TDS("TDS Tax"),
}

data class PayrollRunLine(
  val id: String,
  val payrollRunId: String,
  val employeeId: String,
  val componentName: String,
  val category: PayrollCategory,
  val amount: Double,
  val description: String,
)

data class PayrollRun(
  val id: String,
  val employeeId: String,
  val monthLabel: String,
  val periodStart: String,
  val periodEnd: String,
  val payDate: String,
  val status: String,
  val currency: String,
  val grossPay: Double,
  val totalDeductions: Double,
  val ssfTotal: Double,
  val tdsTax: Double,
  val netPay: Double,
  val lines: List<PayrollRunLine>,
)

enum class DocumentType(val label: String) {
  RECEIPT("Expense Receipt"),
  CERTIFICATE("Certificate / License"),
  HR_FORM("HR / Onboarding Form"),
  ID_PROOF("National ID / Passport"),
  TAX_PAN("Tax / PAN Certificate"),
  CONTRACT("Employment Contract"),
  COMPLIANCE("Compliance Filing"),
  PROFILE_PHOTO("Profile Photo"),
}

data class DocumentOcrMetadata(
  val extractedText: String? = null,
  val detectedVendorOrIssuer: String? = null,
  val detectedDate: String? = null,
  val detectedAmount: String? = null,
  val detectedReferenceId: String? = null,
  val ocrConfidenceLabel: String? = null,
)

data class EmployeeDocument(
  val id: String,
  val employeeId: String,
  val title: String,
  val documentType: DocumentType,
  val fileName: String,
  val mimeType: String,
  val fileSizeBytes: Long,
  val uploadedAt: String,
  val localUri: String,
  val verificationStatus: String,
  val extractedText: String? = null,
  val detectedVendorOrIssuer: String? = null,
  val detectedDate: String? = null,
  val detectedAmount: String? = null,
  val detectedReferenceId: String? = null,
  val ocrConfidenceLabel: String? = null,
)

data class CompanyAnnouncement(
  val id: String,
  val title: String,
  val summary: String,
  val category: String,
  val publishedAt: String,
  val isUrgent: Boolean,
)

data class PendingSyncAction(
  val id: String,
  val actionType: String,
  val description: String,
  val createdAtEpochMs: Long,
  val retryCount: Int,
  val lastError: String?,
  val idempotencyKey: String = "",
  val stateLabel: String = "QUEUED",
)

enum class AuthChallengePurpose(val title: String, val subtitle: String) {
  APP_UNLOCK("Unlock PulseHR", "Authenticate to access enterprise HR records"),
  CLOCK_PUNCH("Verify Attendance Punch", "Confirm identity for geofenced mobile clock-in/out"),
  LEAVE_SUBMIT("Authorize Leave Request", "Verify identity before submitting leave application"),
  PAYSLIP_VIEW("Unlock Payroll & Payslips", "Authenticate to view salary, SSF, and TDS breakdowns"),
  SENSITIVE_DOC_VIEW("Unlock Sensitive Document", "Verify identity before streaming confidential HR records"),
}

enum class HolidayCategory(val label: String) {
  PUBLIC("Public Holiday"),
  CORPORATE("Company Holiday"),
  FLOATING("Floating / Branch"),
}

data class HolidaySchedule(
  val id: String,
  val title: String,
  val dateIso: String,
  val dayLabel: String,
  val category: HolidayCategory,
  val branchScope: String,
  val daysUntil: Int,
  val description: String,
)

enum class RouteEventType(val label: String) {
  CHECK_IN("Check In"),
  WAYPOINT("Waypoint"),
  MANUAL_PUSH("Manual Pin"),
  CHECK_OUT("Check Out"),
}

data class LocationBreadcrumb(
  val id: String,
  val employeeId: String,
  val sessionId: String,
  val dateIso: String,
  val eventType: RouteEventType,
  val latitude: Double,
  val longitude: Double,
  val accuracyMeters: Float?,
  val siteLabel: String,
  val distanceFromBranchMeters: Double,
  val isInsideGeofence: Boolean,
  val capturedAtEpochMs: Long,
  val capturedTimeLabel: String,
  val syncStatus: SyncStatus,
  val syncedAtEpochMs: Long? = null,
)

data class EmployeeRouteSession(
  val sessionId: String,
  val dateIso: String,
  val isActive: Boolean,
  val startedAtLabel: String?,
  val endedAtLabel: String?,
  val totalDistanceMeters: Double,
  val totalPointsCount: Int,
  val syncedPointsCount: Int,
  val pendingPointsCount: Int,
  val waypoints: List<LocationBreadcrumb>,
)

