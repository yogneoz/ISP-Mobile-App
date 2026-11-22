package com.example.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface PulseHrApiService {

  @POST("api/v1/auth/login")
  suspend fun login(@Body request: LoginRequestDto): Response<AuthTokenResponseDto>

  @POST("api/v1/auth/refresh")
  suspend fun refreshToken(@Body request: RefreshTokenRequestDto): Response<AuthTokenResponseDto>

  @GET("api/v1/workplace/bootstrap")
  suspend fun fetchWorkplaceBootstrap(): Response<WorkplaceBootstrapDto>

  @GET("api/v1/sync/pull")
  suspend fun pullSyncDelta(
    @Query("updated_since") updatedSince: Long
  ): Response<SyncPullResponseDto>

  @POST("api/v1/devices/register")
  suspend fun registerDevice(
    @Body request: DeviceRegisterRequestDto
  ): Response<DeviceRegisterResponseDto>

  @POST("api/v1/workplace/attendance/punch")
  suspend fun submitAttendancePunch(
    @Body request: AttendancePunchRequestDto
  ): Response<AttendancePunchResponseDto>

  @POST("api/v1/leaves/requests")
  suspend fun submitLeaveRequest(
    @Body request: LeaveSubmitRequestDto
  ): Response<LeaveSubmitResponseDto>

  @POST("api/v1/leaves/requests/{id}/review")
  suspend fun reviewLeaveRequest(
    @Path("id") requestId: String,
    @Body request: LeaveReviewRequestDto,
  ): Response<Unit>

  @POST("api/v1/leaves/requests/{id}/cancel")
  suspend fun cancelLeaveRequest(@Path("id") requestId: String): Response<Unit>

  @PUT("api/v1/employees/me/contact")
  suspend fun updateContact(@Body request: UpdateContactRequestDto): Response<Unit>

  @POST("api/v1/notifications/fcm-token")
  suspend fun registerFcmToken(
    @Body request: RegisterFcmTokenRequestDto
  ): Response<RegisterFcmTokenResponseDto>

  @POST("api/v1/workplace/jobs/{id}/complete")
  suspend fun completeWorkOrder(
    @Path("id") workOrderId: String,
    @Body request: WorkOrderCompleteRequestDto,
  ): Response<WorkOrderCompleteResponseDto>

  @POST("api/v1/workplace/claims")
  suspend fun submitExpenseClaim(
    @Body request: ExpenseClaimSubmitRequestDto
  ): Response<ExpenseClaimSubmitResponseDto>

  @POST("api/v1/workplace/dispatch/assign")
  suspend fun assignDispatchWorkOrder(
    @Body request: DispatchAssignRequestDto
  ): Response<Unit>

  @POST("api/v1/workplace/delegations")
  suspend fun createDelegation(
    @Body request: DelegationCreateRequestDto
  ): Response<Unit>

  @POST("api/v1/workplace/location/batch")
  suspend fun pushLocationBreadcrumbs(
    @Body request: LocationBatchPushRequestDto
  ): Response<LocationBatchPushResponseDto>
}

