package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "branches")
data class BranchEntity(
  @PrimaryKey val id: String,
  val name: String,
  val code: String,
  val address: String,
  val latitude: Double,
  val longitude: Double,
  val geofenceRadiusMeters: Double,
)

@Entity(tableName = "employees")
data class EmployeeEntity(
  @PrimaryKey val id: String,
  val userId: String,
  val employeeCode: String,
  val fullName: String,
  val email: String,
  val phone: String,
  val emergencyContactName: String,
  val emergencyContactPhone: String,
  val department: String,
  val designation: String,
  val branchId: String,
  val bankName: String,
  val bankAccountMasked: String,
  val joinDate: String,
  val avatarUri: String? = null,
)

@Entity(tableName = "hr_attendance")
data class AttendanceEntity(
  @PrimaryKey val id: String,
  val employeeId: String,
  val branchId: String,
  val attendanceDateAd: String,
  val checkInTime: String?,
  val checkOutTime: String?,
  val status: String,
  val source: String = "MOBILE",
  val checkInLatitude: Double? = null,
  val checkInLongitude: Double? = null,
  val checkOutLatitude: Double? = null,
  val checkOutLongitude: Double? = null,
  val distanceFromBranchMeters: Double? = null,
  val overtimeHours: Double = 0.0,
  val syncStatus: String = "SYNCED",
  val riskScore: Int = 0,
  val integrityStatus: String = "VERIFIED",
  val integrityNotes: String? = null,
  val gpsAccuracyMeters: Float? = null,
  val wifiBssid: String? = null,
)

@Entity(tableName = "hr_leave_balances")
data class LeaveBalanceEntity(
  @PrimaryKey val id: String,
  val employeeId: String,
  val leaveTypeId: String,
  val leaveTypeName: String,
  val leaveCode: String,
  val allocatedDays: Double,
  val usedDays: Double,
  val pendingDays: Double,
  val remainingDays: Double,
)

@Entity(tableName = "hr_leave_requests")
data class LeaveRequestEntity(
  @PrimaryKey val id: String,
  val employeeId: String,
  val employeeName: String,
  val leaveTypeId: String,
  val leaveTypeName: String,
  val startDate: String,
  val endDate: String,
  val totalDays: Double,
  val reason: String,
  val status: String,
  val submittedAt: String,
  val reviewerNote: String? = null,
  val isTeamRequest: Boolean = false,
  val syncStatus: String = "SYNCED",
)

@Entity(tableName = "payroll_runs")
data class PayrollRunEntity(
  @PrimaryKey val id: String,
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
)

@Entity(tableName = "payroll_run_lines")
data class PayrollRunLineEntity(
  @PrimaryKey val id: String,
  val payrollRunId: String,
  val employeeId: String,
  val componentName: String,
  val category: String,
  val amount: Double,
  val description: String,
)

@Entity(tableName = "hr_employee_documents")
data class EmployeeDocumentEntity(
  @PrimaryKey val id: String,
  val employeeId: String,
  val title: String,
  val documentType: String,
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

@Entity(tableName = "company_announcements")
data class AnnouncementEntity(
  @PrimaryKey val id: String,
  val title: String,
  val summary: String,
  val category: String,
  val publishedAt: String,
  val isUrgent: Boolean,
)

@Entity(tableName = "pending_sync_actions")
data class PendingSyncActionEntity(
  @PrimaryKey val id: String,
  val actionType: String,
  val description: String,
  val payloadJson: String,
  val targetRecordId: String,
  val createdAtEpochMs: Long,
  val retryCount: Int = 0,
  val lastError: String? = null,
  val idempotencyKey: String = "",
  val baseVersion: Int = 1,
  val deviceTimeEpochMs: Long = createdAtEpochMs,
  val stateLabel: String = "QUEUED",
)

@Entity(tableName = "work_orders")
data class WorkOrderEntity(
  @PrimaryKey val id: String,
  val ticketCode: String,
  val customerName: String,
  val address: String,
  val taskType: String,
  val slaDeadlineLabel: String,
  val slaMinutesRemaining: Int,
  val status: String,
  val assignedEmployeeId: String,
  val assignedEmployeeName: String,
  val branchCode: String,
  val opticalPowerDbm: Double? = null,
  val otdrLossDb: Double? = null,
  val customerSignatureName: String? = null,
  val photoCount: Int = 0,
  val consumedCableMeters: Int = 0,
  val serialUsed: String? = null,
  val poleRouteNote: String? = null,
  val baseVersion: Int = 1,
  val syncStatus: String = "SYNCED",
)

@Entity(tableName = "van_stock")
data class VanStockEntity(
  @PrimaryKey val id: String,
  val sku: String,
  val itemName: String,
  val category: String,
  val serialNumber: String?,
  val quantityOnHand: Int,
  val unit: String,
  val minThreshold: Int,
  val vanCode: String,
)

@Entity(tableName = "expense_claims")
data class ExpenseClaimEntity(
  @PrimaryKey val id: String,
  val employeeId: String,
  val employeeName: String,
  val category: String,
  val amount: Double,
  val currency: String = "USD",
  val ticketCode: String?,
  val description: String,
  val receiptAttached: Boolean,
  val status: String,
  val submittedAt: String,
  val accountingOutboxStatus: String,
  val syncStatus: String = "SYNCED",
)

@Entity(tableName = "approval_items")
data class ApprovalItemEntity(
  @PrimaryKey val id: String,
  val itemType: String,
  val requesterId: String,
  val requesterName: String,
  val summary: String,
  val detail: String,
  val metricLabel: String,
  val submittedAt: String,
  val status: String,
  val delegatedFrom: String? = null,
  val reviewedBy: String? = null,
)

@Entity(tableName = "delegations")
data class DelegationEntity(
  @PrimaryKey val id: String,
  val delegatorId: String,
  val delegatorName: String,
  val delegateEmployeeId: String,
  val delegateName: String,
  val capabilitiesLabel: String,
  val startDate: String,
  val endDate: String,
  val active: Boolean,
)

@Entity(tableName = "roster_entries")
data class RosterEntryEntity(
  @PrimaryKey val id: String,
  val employeeId: String,
  val employeeName: String,
  val roleLabel: String,
  val shiftLabel: String,
  val standby24x7: Boolean,
  val leaveStatusWarning: String?,
  val assignedBranchCode: String,
)

@Entity(tableName = "needs_attention_inbox")
data class NeedsAttentionEntity(
  @PrimaryKey val id: String,
  val errorCode: String,
  val title: String,
  val message: String,
  val sourceType: String,
  val sourceId: String,
  val capturedSummary: String,
  val retryable: Boolean,
  val createdAtLabel: String,
  val resolved: Boolean = false,
)

@Entity(tableName = "audit_logs")
data class AuditLogEntity(
  @PrimaryKey val id: String,
  val actorId: String,
  val actorEmail: String,
  val deviceId: String,
  val screenName: String,
  val actionLabel: String,
  val timestampLabel: String,
  val createdAtEpochMs: Long,
  val eventCategory: String = "SENSITIVE_ACCESS", // BIOMETRIC_AUTH, PIN_FALLBACK, SENSITIVE_ACCESS, DEVICE_BINDING, APPROVAL, ROUTE
  val outcome: String = "INFO", // SUCCESS, FAILED, CANCELLED, FALLBACK, INFO
  val authMethod: String = "SYSTEM", // BIOMETRIC_STRONG, BIOMETRIC_WEAK_OR_FACE, DEVICE_CREDENTIAL, PIN_FALLBACK, SYSTEM
  val deviceModel: String = "",
  val failureReason: String? = null,
)

@Entity(tableName = "location_breadcrumbs")
data class LocationBreadcrumbEntity(
  @PrimaryKey val id: String,
  val employeeId: String,
  val sessionId: String,
  val dateIso: String,
  val eventType: String, // CHECK_IN, WAYPOINT, MANUAL_PUSH, CHECK_OUT
  val latitude: Double,
  val longitude: Double,
  val accuracyMeters: Float? = null,
  val siteLabel: String,
  val distanceFromBranchMeters: Double = 0.0,
  val isInsideGeofence: Boolean = false,
  val capturedAtEpochMs: Long,
  val capturedTimeLabel: String,
  val syncStatus: String = "PENDING_SYNC",
  val syncedAtEpochMs: Long? = null,
)

@Entity(tableName = "employee_shifts")
data class EmployeeShiftEntity(
  @PrimaryKey val id: String,
  val employeeId: String,
  val shiftCode: String,
  val shiftName: String,
  val shiftDateAd: String, // Nominal shift date YYYY-MM-DD
  val startHour: Int,
  val startMinute: Int,
  val endHour: Int,
  val endMinute: Int,
  val crossesMidnight: Boolean = false,
  val workMode: String = "FIELD", // OFFICE, FIELD, HYBRID
  val requireGeofenceCheckIn: Boolean = true,
  val requireGeofenceCheckOut: Boolean = false,
  val branchCode: String = "MV-HQ",
  val sequenceNo: Int = 1,
  val isStandby24x7: Boolean = false,
  val source: String = "BOOTSTRAP_ROSTER",
)

