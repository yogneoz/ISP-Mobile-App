package com.example.domain.model

import java.util.Locale

enum class DutyStatus(val label: String) {
  ON_DUTY("On Duty"),
  ON_FIELD_JOB("On Field Job"),
  BREAK("Break"),
  OFF_DUTY("Off Duty"),
}

enum class PunchIntegrityStatus(val label: String) {
  VERIFIED("Verified"),
  FLAGGED("Flagged"),
  HELD_FOR_REVIEW("Held for Review"),
  INCOMPLETE("Incomplete"),
}

enum class OfflineAccessMode(val label: String) {
  ONLINE("Online Synced"),
  OFFLINE_GRACE("Offline Grace (<24h)"),
  OFFLINE_LOCKED("Offline Lock (>24h Token Expired)"),
}

data class WorkHoursScheduleConfig(
  val enabled: Boolean = true,
  val startHour: Int = 8,
  val startMinute: Int = 30,
  val endHour: Int = 18,
  val endMinute: Int = 30,
  val weekdaysOnly: Boolean = true,
) {
  fun formattedWindow(): String {
    return String.format(
      Locale.US,
      "%02d:%02d – %02d:%02d%s",
      startHour,
      startMinute,
      endHour,
      endMinute,
      if (weekdaysOnly) " (Mon–Fri)" else " (Daily)",
    )
  }
}

enum class ShiftWorkMode(val label: String) {
  OFFICE("Office"),
  FIELD("Field Dispatch"),
  HYBRID("Hybrid"),
}

data class EmployeeShift(
  val id: String,
  val employeeId: String,
  val shiftCode: String,
  val shiftName: String,
  val shiftDateAd: String,
  val startHour: Int,
  val startMinute: Int,
  val endHour: Int,
  val endMinute: Int,
  val crossesMidnight: Boolean = (endHour * 60 + endMinute) <= (startHour * 60 + startMinute),
  val workMode: ShiftWorkMode = ShiftWorkMode.FIELD,
  val requireGeofenceCheckIn: Boolean = true,
  val requireGeofenceCheckOut: Boolean = false,
  val branchCode: String = "MV-HQ",
  val sequenceNo: Int = 1,
  val isStandby24x7: Boolean = false,
  val source: String = "BOOTSTRAP_ROSTER",
) {
  fun formattedTimeWindow(): String =
    String.format(
      Locale.US,
      "%02d:%02d – %02d:%02d%s",
      startHour,
      startMinute,
      endHour,
      endMinute,
      if (crossesMidnight) " (+1d)" else "",
    )
}

enum class WorkOrderStatus(val label: String) {
  ASSIGNED("Assigned"),
  EN_ROUTE("En Route"),
  IN_PROGRESS("In Progress"),
  COMPLETED_LOCAL("Completed (Pending Sync)"),
  CLOSED("Closed"),
  FLAGGED("Accepted (Flagged)"),
}

data class WorkOrder(
  val id: String,
  val ticketCode: String,
  val customerName: String,
  val address: String,
  val taskType: String,
  val slaDeadlineLabel: String,
  val slaMinutesRemaining: Int,
  val status: WorkOrderStatus,
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
  val syncStatus: SyncStatus = SyncStatus.SYNCED,
)

data class VanStockItem(
  val id: String,
  val sku: String,
  val itemName: String,
  val category: String,
  val serialNumber: String?,
  val quantityOnHand: Int,
  val unit: String,
  val minThreshold: Int,
  val vanCode: String,
)

enum class ClaimCategory(val label: String) {
  FUEL("Fuel"),
  TRAVEL("Travel"),
  MEAL("Meal"),
  LOCAL_PURCHASE("Local Purchase"),
  OVERTIME("Overtime / Call-Out"),
}

data class ExpenseClaim(
  val id: String,
  val employeeId: String,
  val employeeName: String,
  val category: ClaimCategory,
  val amount: Double,
  val currency: String = "USD",
  val ticketCode: String?,
  val description: String,
  val receiptAttached: Boolean,
  val status: String,
  val submittedAt: String,
  val accountingOutboxStatus: String,
  val syncStatus: SyncStatus,
)

data class ApprovalItem(
  val id: String,
  val itemType: String, // LATE_PUNCH, OVERTIME, EXPENSE_CLAIM
  val requesterId: String,
  val requesterName: String,
  val summary: String,
  val detail: String,
  val metricLabel: String,
  val submittedAt: String,
  val status: String, // PENDING, APPROVED, REJECTED
  val delegatedFrom: String? = null,
  val reviewedBy: String? = null,
) {
  fun canBeActedOnBy(currentEmployeeId: String, capabilities: Set<AppCapability>): Boolean {
    if (status != "PENDING") return false
    if (requesterId == currentEmployeeId) return false // Hard v2 rule: No self-approval
    return when (itemType) {
      "EXPENSE_CLAIM" -> AppCapability.APPROVE_EXPENSE in capabilities
      else -> AppCapability.APPROVE_ATTENDANCE_OT in capabilities
    }
  }

  fun isSelfSubmitted(currentEmployeeId: String): Boolean = requesterId == currentEmployeeId
}

data class DelegationRule(
  val id: String,
  val delegatorId: String,
  val delegatorName: String,
  val delegateEmployeeId: String,
  val delegateName: String,
  val capabilitiesLabel: String,
  val startDate: String,
  val endDate: String,
  val active: Boolean,
)

data class RosterEntry(
  val id: String,
  val employeeId: String,
  val employeeName: String,
  val roleLabel: String,
  val shiftLabel: String,
  val standby24x7: Boolean,
  val leaveStatusWarning: String?,
  val assignedBranchCode: String,
)

data class DispatchCrewMember(
  val employeeId: String,
  val fullName: String,
  val dutyStatus: DutyStatus,
  val availabilityLabel: String,
  val leaveConflictWarning: String?,
  val activeTicketsCount: Int,
  val skillsLabel: String,
  val branchCode: String,
)

data class NeedsAttentionItem(
  val id: String,
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

enum class AuditOutcome(val label: String) {
  SUCCESS("Verified"),
  FAILED("Failed"),
  CANCELLED("Cancelled"),
  FALLBACK("PIN Fallback"),
  INFO("Logged"),
}

data class AuditLogEntry(
  val id: String,
  val actorId: String,
  val actorEmail: String,
  val deviceId: String,
  val screenName: String,
  val actionLabel: String,
  val timestampLabel: String,
  val createdAtEpochMs: Long = 0L,
  val eventCategory: String = "SENSITIVE_ACCESS",
  val outcome: AuditOutcome = AuditOutcome.INFO,
  val authMethod: String = "SYSTEM",
  val deviceModel: String = "",
  val failureReason: String? = null,
)

data class StockAuditRow(
  val id: String,
  val technicianName: String,
  val itemName: String,
  val warehouseIssuedQty: Int,
  val ticketConsumedQty: Int,
  val vanBalanceQty: Int,
  val variance: Int,
)
