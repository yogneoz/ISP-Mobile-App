package com.example.data.remote

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class LoginRequestDto(
  val email: String,
  val password: String,
  val role: String,
  val bundles: List<String> = emptyList(),
  val deviceId: String = "dev_and_primary",
)

@JsonClass(generateAdapter = true)
data class AuthTokenResponseDto(
  val accessToken: String,
  val refreshToken: String,
  val expiresInSeconds: Long,
  val userId: String,
  val email: String,
  val role: String,
  val employeeId: String,
  val capabilities: List<String> = emptyList(),
  val branchScope: String = "MV-HQ",
)

@JsonClass(generateAdapter = true)
data class RefreshTokenRequestDto(
  val refreshToken: String,
)

@JsonClass(generateAdapter = true)
data class AttendancePunchRequestDto(
  val employeeId: String,
  val branchId: String,
  val attendanceDateAd: String,
  val action: String, // CLOCK_IN or CLOCK_OUT
  val punchTime: String,
  val latitude: Double,
  val longitude: Double,
  val distanceMeters: Double,
  val gpsAccuracyMeters: Float? = null,
  val wifiBssid: String? = null,
  val deviceTimeEpochMs: Long = System.currentTimeMillis(),
  val idempotencyKey: String = "",
  val source: String = "MOBILE",
)

@JsonClass(generateAdapter = true)
data class AttendancePunchResponseDto(
  val id: String,
  val syncStatus: String,
  val serverTimestamp: String,
  val integrityStatus: String = "VERIFIED",
  val riskScore: Int = 0,
  val integrityNotes: String = "Verified punch proof",
)

@JsonClass(generateAdapter = true)
data class LeaveSubmitRequestDto(
  val employeeId: String,
  val leaveTypeId: String,
  val startDate: String,
  val endDate: String,
  val totalDays: Double,
  val reason: String,
)

@JsonClass(generateAdapter = true)
data class LeaveSubmitResponseDto(
  val id: String,
  val status: String,
  val submittedAt: String,
)

@JsonClass(generateAdapter = true)
data class LeaveReviewRequestDto(
  val approve: Boolean,
  val reviewerNote: String,
)

@JsonClass(generateAdapter = true)
data class LeaveReviewQueuePayload(
  val requestId: String,
  val approve: Boolean,
  val reviewerNote: String,
)

@JsonClass(generateAdapter = true)
data class UpdateContactRequestDto(
  val phone: String,
  val emergencyContactName: String,
  val emergencyContactPhone: String,
)

@JsonClass(generateAdapter = true)
data class RegisterFcmTokenRequestDto(
  val fcmToken: String,
  val platform: String = "ANDROID",
  val topics: List<String>,
)

@JsonClass(generateAdapter = true)
data class RegisterFcmTokenResponseDto(
  val registered: Boolean,
  val topics: List<String>,
)

@JsonClass(generateAdapter = true)
data class DeviceRegisterRequestDto(
  val deviceId: String,
  val employeeId: String,
  val modelName: String,
  val appVersion: String = "2.0.0",
)

@JsonClass(generateAdapter = true)
data class DeviceRegisterResponseDto(
  val registered: Boolean,
  val deviceId: String,
  val boundAt: String,
)

@JsonClass(generateAdapter = true)
data class EmployeeShiftDto(
  val id: String,
  val shiftCode: String,
  val shiftName: String,
  val shiftDateAd: String,
  val startHour: Int,
  val startMinute: Int,
  val endHour: Int,
  val endMinute: Int,
  val crossesMidnight: Boolean = false,
  val workMode: String = "FIELD",
  val requireGeofenceCheckIn: Boolean = true,
  val requireGeofenceCheckOut: Boolean = false,
  val branchCode: String = "MV-HQ",
  val sequenceNo: Int = 1,
  val isStandby24x7: Boolean = false,
)

@JsonClass(generateAdapter = true)
data class WorkplaceBootstrapDto(
  val serverTimeEpochMs: Long,
  val capabilities: List<String>,
  val punchState: String,
  val shiftWindow: String,
  val unreadNotices: Int,
  val pendingApprovals: Int,
  val vanLocation: String,
  val shifts: List<EmployeeShiftDto> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class WorkOrderCompleteRequestDto(
  val workOrderId: String,
  val ticketCode: String,
  val employeeId: String,
  val opticalPowerDbm: Double,
  val otdrLossDb: Double,
  val customerSignatureName: String,
  val photoCount: Int,
  val consumedCableMeters: Int,
  val serialUsed: String?,
  val baseVersion: Int,
  val idempotencyKey: String,
  val deviceTimeEpochMs: Long,
)

@JsonClass(generateAdapter = true)
data class WorkOrderCompleteResponseDto(
  val workOrderId: String,
  val resultStatus: String, // CLOSED, FLAGGED, NEEDS_ATTENTION
  val errorCode: String? = null,
  val message: String = "Operational close committed; Accounting outbox queued.",
  val accountingOutboxId: String? = null,
)

@JsonClass(generateAdapter = true)
data class ExpenseClaimSubmitRequestDto(
  val claimId: String,
  val employeeId: String,
  val category: String,
  val amount: Double,
  val ticketCode: String?,
  val description: String,
  val receiptAttached: Boolean,
  val idempotencyKey: String,
)

@JsonClass(generateAdapter = true)
data class ExpenseClaimSubmitResponseDto(
  val claimId: String,
  val status: String,
  val accountingOutboxStatus: String,
)

@JsonClass(generateAdapter = true)
data class SyncPullResponseDto(
  val serverCursor: Long,
  val revokedDevice: Boolean = false,
  val updatedNoticesCount: Int = 0,
)

@JsonClass(generateAdapter = true)
data class DispatchAssignRequestDto(
  val workOrderId: String,
  val assigneeEmployeeId: String,
  val overrideLeaveWarning: Boolean,
  val overrideReason: String?,
)

@JsonClass(generateAdapter = true)
data class DelegationCreateRequestDto(
  val delegateEmployeeId: String,
  val delegateName: String,
  val endDate: String,
  val capabilitiesCsv: String,
)

@JsonClass(generateAdapter = true)
data class ApiErrorEnvelopeDto(
  val code: String,
  val message: String,
  val retryable: Boolean,
  val details: String? = null,
)

@JsonClass(generateAdapter = true)
data class LocationBreadcrumbPushDto(
  val id: String,
  val employeeId: String,
  val sessionId: String,
  val eventType: String,
  val latitude: Double,
  val longitude: Double,
  val accuracyMeters: Float? = null,
  val siteLabel: String,
  val capturedAtEpochMs: Long,
)

@JsonClass(generateAdapter = true)
data class LocationBatchPushRequestDto(
  val employeeId: String,
  val deviceId: String,
  val idempotencyKey: String,
  val points: List<LocationBreadcrumbPushDto>,
)

@JsonClass(generateAdapter = true)
data class LocationBatchPushResponseDto(
  val acceptedCount: Int,
  val serverTimestampEpochMs: Long,
  val routeSessionId: String? = null,
  val message: String = "Route breadcrumbs persisted to database",
)

