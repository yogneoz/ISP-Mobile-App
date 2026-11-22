package com.example.location

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.data.local.AttendanceEntity
import com.example.data.local.EmployeeShiftEntity
import com.example.data.local.LocationBreadcrumbEntity
import com.example.data.local.PulseHrDao
import com.example.domain.model.AttendanceRecord
import com.example.domain.model.AttendanceStatus
import com.example.domain.model.BranchLocation
import com.example.domain.model.EmployeeShift
import com.example.domain.model.LocationBreadcrumb
import com.example.domain.model.RouteEventType
import com.example.domain.model.ShiftWorkMode
import com.example.domain.model.SyncStatus
import com.example.domain.model.WorkHoursScheduleConfig
import com.example.notifications.PulseHrNotificationHelper
import com.example.security.SecureTokenStore
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

enum class GeofenceReminderDecision {
  NONE,
  CLOCK_IN_REMINDER,
  CLOCK_OUT_REMINDER,
  SHIFT_END_CLOCK_OUT_REMINDER,
  PROACTIVE_OFFSITE_SHIFT_SUGGESTION,
}

data class MultiShiftEvaluationResult(
  val decision: GeofenceReminderDecision,
  val activeShift: EmployeeShift? = null,
  val effectiveShiftDateAd: String? = null,
)

/**
 * Local notification scheduler supporting multiple & overnight synced employee shifts as well as
 * field-worker off-site shift-end reminders.
 * - Evaluates synced `EmployeeShiftEntity` windows (same-day, split, and cross-midnight shifts),
 *   falling back to `WorkHoursScheduleConfig` when no synced shifts exist.
 * - Suppresses premature mid-day HQ-exit clock-out prompts during `FIELD` shifts or active route
 *   sessions, while firing a shift-end clock-out reminder once a shift finishes if still clocked in
 *   (even when the employee drives straight home without re-entering HQ).
 */
@Singleton
class GeofenceReminderScheduler @Inject constructor(
  private val locationManager: LocationManager,
  private val dao: PulseHrDao,
  private val tokenStore: SecureTokenStore,
  private val notificationHelper: PulseHrNotificationHelper,
  private val workManager: WorkManager,
) {

  fun updateSchedule(config: WorkHoursScheduleConfig) {
    tokenStore.saveWorkHoursSchedule(config)
    if (config.enabled) {
      ensureScheduled()
    } else {
      cancelScheduled()
    }
  }

  fun ensureScheduled() {
    val config = tokenStore.workHoursScheduleFlow.value
    if (!config.enabled) {
      cancelScheduled()
      return
    }
    val request =
      PeriodicWorkRequestBuilder<GeofenceWorkHoursWorker>(15, TimeUnit.MINUTES)
        .build()
    workManager.enqueueUniquePeriodicWork(
      UNIQUE_WORK_NAME,
      ExistingPeriodicWorkPolicy.KEEP,
      request,
    )
  }

  fun cancelScheduled() {
    workManager.cancelUniqueWork(UNIQUE_WORK_NAME)
  }

  suspend fun checkGeofenceTransitionAndNotify(
    calendar: Calendar = Calendar.getInstance(),
  ): GeofenceReminderDecision {
    val session = tokenStore.getCurrentSession() ?: return GeofenceReminderDecision.NONE
    val employee = dao.getEmployeeByUserId(session.userId) ?: return GeofenceReminderDecision.NONE
    val branchEntity = dao.getBranchById(employee.branchId) ?: return GeofenceReminderDecision.NONE
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

    if (!locationManager.hasLocationPermission()) return GeofenceReminderDecision.NONE
    val verification = locationManager.verifyOfficePerimeter(branch)
    val hasFix = verification.evaluation.userLatitude != null
    if (!hasFix) return GeofenceReminderDecision.NONE

    return handleEvaluatedPerimeter(
      isInsideNow = verification.isWithinPerimeter,
      branchName = branch.name,
      employeeId = session.employeeId,
      calendar = calendar,
      forceTriggerIfEligible = false,
      userLatitude = verification.evaluation.userLatitude,
      userLongitude = verification.evaluation.userLongitude,
      distanceFromBranchMeters = verification.evaluation.distanceMeters,
    )
  }

  suspend fun handleEvaluatedPerimeter(
    isInsideNow: Boolean,
    branchName: String,
    employeeId: String,
    calendar: Calendar = Calendar.getInstance(),
    forceTriggerIfEligible: Boolean = false,
    userLatitude: Double? = null,
    userLongitude: Double? = null,
    distanceFromBranchMeters: Double? = null,
  ): GeofenceReminderDecision {
    val schedule = tokenStore.workHoursScheduleFlow.value
    val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    val todayAd = dateFmt.format(Date(calendar.timeInMillis))
    val yesterdayCal = (calendar.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
    val yesterdayAd = dateFmt.format(Date(yesterdayCal.timeInMillis))

    val syncedShifts = dao.getEmployeeShiftsSync(employeeId).map { it.toDomain() }
    val activeWindowShift =
      findActiveOrJustEndedShift(
        shifts = syncedShifts,
        calendar = calendar,
      )
    val lookupDateAd = activeWindowShift?.shiftDateAd ?: todayAd
    val attendanceRecord =
      dao.getAttendanceForDate(employeeId, lookupDateAd)
        ?: if (lookupDateAd != todayAd) dao.getAttendanceForDate(employeeId, todayAd) else null

    val hasCheckedIn = attendanceRecord?.checkInTime != null
    val hasCheckedOut = attendanceRecord?.checkOutTime != null
    val hasActiveRouteSession = !tokenStore.getActiveRouteSessionId().isNullOrBlank()
    val wasInside = tokenStore.getLastGeofenceInsideState()

    val evalResult =
      evaluateMultiShiftTransition(
        calendar = calendar,
        schedule = schedule,
        shifts = syncedShifts,
        currentlyInsideGeofence = isInsideNow,
        wasInsideGeofence = if (forceTriggerIfEligible) !isInsideNow else wasInside,
        hasCheckedInForShift = hasCheckedIn,
        hasCheckedOutForShift = hasCheckedOut,
        hasActiveFieldRoute = hasActiveRouteSession,
      )

    tokenStore.setLastGeofenceInsideState(isInsideNow)

    val decision = evalResult.decision
    if (decision != GeofenceReminderDecision.NONE) {
      val shift = evalResult.activeShift
      val dedupKey = "${shift?.id ?: todayAd}_${todayAd}_${decision.name}"
      if (forceTriggerIfEligible || tokenStore.getLastReminderSentKey() != dedupKey) {
        tokenStore.setLastReminderSentKey(dedupKey)
        notificationHelper.showGeofenceAttendanceReminder(
          isEntry = decision == GeofenceReminderDecision.CLOCK_IN_REMINDER,
          branchName = branchName,
          workHoursLabel = shift?.formattedTimeWindow() ?: schedule.formattedWindow(),
          shiftName = shift?.shiftName,
          isShiftEndFallback = decision == GeofenceReminderDecision.SHIFT_END_CLOCK_OUT_REMINDER,
          notificationId = (shift?.id ?: yesterdayAd).hashCode().and(0x7FFF) + 4100,
        )
      }
      return decision
    }

    // Proactive off-site shift suggestion when starting workday outside the office geofence
    if (!isInsideNow && !hasCheckedIn && !hasCheckedOut && !hasActiveRouteSession && schedule.enabled) {
      val history = dao.getAttendanceHistorySync(employeeId).map { it.toDomain() }
      val breadcrumbs = dao.getLocationBreadcrumbsSync(employeeId).map { it.toDomain() }
      val suggestion =
        ProactiveShiftSuggestionEngine.suggestShiftForOffsiteStart(
          calendar = calendar,
          schedule = schedule,
          shifts = syncedShifts,
          attendanceHistory = history,
          recentBreadcrumbs = breadcrumbs,
          currentlyInsideGeofence = false,
          currentLatitude = userLatitude,
          currentLongitude = userLongitude,
          distanceFromBranchMeters = distanceFromBranchMeters,
          hasCheckedInToday = false,
          hasCheckedOutToday = false,
          hasActiveFieldRoute = false,
        )
      if (suggestion != null) {
        val suggestionDedupKey =
          "${suggestion.shift.id}_${todayAd}_${GeofenceReminderDecision.PROACTIVE_OFFSITE_SHIFT_SUGGESTION.name}"
        if (forceTriggerIfEligible || tokenStore.getLastReminderSentKey() != suggestionDedupKey) {
          tokenStore.setLastReminderSentKey(suggestionDedupKey)
          notificationHelper.showProactiveShiftSuggestionNotification(
            suggestion = suggestion,
            branchName = branchName,
          )
        }
        return GeofenceReminderDecision.PROACTIVE_OFFSITE_SHIFT_SUGGESTION
      }
    }

    return GeofenceReminderDecision.NONE
  }

  /**
   * Dispatches an immediate local notification simulating an office geofence entry or exit during
   * designated working hours so employees/admins can verify alert delivery on device.
   */
  fun triggerWorkingHoursReminderPreview(
    isEntry: Boolean,
    branchName: String,
    activeShift: EmployeeShift? = null,
  ) {
    val schedule = tokenStore.workHoursScheduleFlow.value
    notificationHelper.showGeofenceAttendanceReminder(
      isEntry = isEntry,
      branchName = branchName,
      workHoursLabel = activeShift?.formattedTimeWindow() ?: schedule.formattedWindow(),
      shiftName = activeShift?.shiftName,
      isShiftEndFallback = !isEntry && activeShift?.workMode == ShiftWorkMode.FIELD,
    )
  }

  fun triggerProactiveShiftSuggestionPreview(
    branchName: String,
    suggestion: ProactiveShiftSuggestion,
  ) {
    notificationHelper.showProactiveShiftSuggestionNotification(
      suggestion = suggestion,
      branchName = branchName,
    )
  }

  private fun EmployeeShiftEntity.toDomain() =
    EmployeeShift(
      id = id,
      employeeId = employeeId,
      shiftCode = shiftCode,
      shiftName = shiftName,
      shiftDateAd = shiftDateAd,
      startHour = startHour,
      startMinute = startMinute,
      endHour = endHour,
      endMinute = endMinute,
      crossesMidnight = crossesMidnight,
      workMode = runCatching { ShiftWorkMode.valueOf(workMode) }.getOrDefault(ShiftWorkMode.FIELD),
      requireGeofenceCheckIn = requireGeofenceCheckIn,
      requireGeofenceCheckOut = requireGeofenceCheckOut,
      branchCode = branchCode,
      sequenceNo = sequenceNo,
      isStandby24x7 = isStandby24x7,
      source = source,
    )

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
      syncStatus = runCatching { SyncStatus.valueOf(syncStatus) }.getOrDefault(SyncStatus.SYNCED),
      syncedAtEpochMs = syncedAtEpochMs,
    )

  companion object {
    const val UNIQUE_WORK_NAME = "pulsehr_geofence_work_hours_monitor"
    private const val POST_SHIFT_GRACE_MINUTES = 120

    fun isWithinWorkingHours(
      hourOfDay: Int,
      minuteOfHour: Int,
      dayOfWeek: Int,
      schedule: WorkHoursScheduleConfig,
    ): Boolean {
      if (!schedule.enabled) return false
      if (schedule.weekdaysOnly) {
        val isWeekday =
          dayOfWeek == Calendar.MONDAY ||
            dayOfWeek == Calendar.TUESDAY ||
            dayOfWeek == Calendar.WEDNESDAY ||
            dayOfWeek == Calendar.THURSDAY ||
            dayOfWeek == Calendar.FRIDAY
        if (!isWeekday) return false
      }
      val nowMinutes = hourOfDay * 60 + minuteOfHour
      val startMinutes = schedule.startHour * 60 + schedule.startMinute
      val endMinutes = schedule.endHour * 60 + schedule.endMinute
      return if (endMinutes >= startMinutes) {
        nowMinutes in startMinutes..endMinutes
      } else {
        // Overnight schedule fallback
        nowMinutes >= startMinutes || nowMinutes <= endMinutes
      }
    }

    /**
     * Checks whether [calendar] falls inside [shift]'s active window or optional post-shift
     * grace period, properly handling cross-midnight (overnight) shifts.
     */
    fun isWithinShiftWindow(
      shift: EmployeeShift,
      calendar: Calendar,
      includePostShiftGraceMinutes: Int = 0,
    ): Boolean {
      val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
      val todayAd = dateFmt.format(Date(calendar.timeInMillis))
      val yesterdayCal = (calendar.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
      val yesterdayAd = dateFmt.format(Date(yesterdayCal.timeInMillis))

      val nowMinutes =
        calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
      val startMinutes = shift.startHour * 60 + shift.startMinute
      val endMinutes = shift.endHour * 60 + shift.endMinute

      return if (!shift.crossesMidnight && endMinutes > startMinutes) {
        shift.shiftDateAd == todayAd &&
          nowMinutes in startMinutes..(endMinutes + includePostShiftGraceMinutes)
      } else {
        // Overnight shift starting at startMinutes on shiftDateAd and ending on next calendar day
        (shift.shiftDateAd == todayAd && nowMinutes >= startMinutes) ||
          (shift.shiftDateAd == yesterdayAd &&
            nowMinutes <= (endMinutes + includePostShiftGraceMinutes))
      }
    }

    /**
     * Returns whether [calendar] is inside the post-shift end window where a clocked-in employee
     * who did not return to HQ should receive a shift-end clock-out reminder.
     */
    fun isInPostShiftEndWindow(
      shift: EmployeeShift,
      calendar: Calendar,
      graceMinutes: Int = POST_SHIFT_GRACE_MINUTES,
    ): Boolean {
      val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
      val todayAd = dateFmt.format(Date(calendar.timeInMillis))
      val yesterdayCal = (calendar.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
      val yesterdayAd = dateFmt.format(Date(yesterdayCal.timeInMillis))

      val nowMinutes =
        calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
      val startMinutes = shift.startHour * 60 + shift.startMinute
      val endMinutes = shift.endHour * 60 + shift.endMinute

      return if (!shift.crossesMidnight && endMinutes > startMinutes) {
        shift.shiftDateAd == todayAd &&
          nowMinutes in endMinutes..(endMinutes + graceMinutes)
      } else {
        shift.shiftDateAd == yesterdayAd &&
          nowMinutes in endMinutes..(endMinutes + graceMinutes)
      }
    }

    /**
     * Picks the currently active shift across multi/split/overnight shifts, or a shift currently
     * in its post-shift grace window if no later shift has started yet.
     */
    fun findActiveOrJustEndedShift(
      shifts: List<EmployeeShift>,
      calendar: Calendar,
    ): EmployeeShift? {
      if (shifts.isEmpty()) return null
      val activeNow = shifts.firstOrNull { isWithinShiftWindow(it, calendar, 0) }
      if (activeNow != null) return activeNow
      return shifts.firstOrNull { isInPostShiftEndWindow(it, calendar) }
    }

    fun isUpcomingShift(
      shift: EmployeeShift,
      calendar: Calendar,
    ): Boolean {
      val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
      val todayAd = dateFmt.format(Date(calendar.timeInMillis))
      if (shift.shiftDateAd > todayAd) return true
      if (shift.shiftDateAd < todayAd) return false
      val nowMinutes =
        calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
      val startMinutes = shift.startHour * 60 + shift.startMinute
      val endMinutes = shift.endHour * 60 + shift.endMinute
      return if (shift.crossesMidnight || endMinutes <= startMinutes) {
        nowMinutes < startMinutes
      } else {
        nowMinutes < endMinutes
      }
    }

    /**
     * Multi-shift + overnight + field-aware transition evaluator.
     * - Prioritizes synced [shifts] (matching active split or overnight shift).
     * - Suppresses mid-shift HQ exit alerts during `FIELD` shifts or active field routes.
     * - Fires `SHIFT_END_CLOCK_OUT_REMINDER` once shift ends if still clocked in without clock-out,
     *   solving the "field employee drives straight home without re-entering office perimeter" case.
     */
    fun evaluateMultiShiftTransition(
      calendar: Calendar,
      schedule: WorkHoursScheduleConfig,
      shifts: List<EmployeeShift>,
      currentlyInsideGeofence: Boolean,
      wasInsideGeofence: Boolean?,
      hasCheckedInForShift: Boolean,
      hasCheckedOutForShift: Boolean,
      hasActiveFieldRoute: Boolean = false,
    ): MultiShiftEvaluationResult {
      if (!schedule.enabled) {
        return MultiShiftEvaluationResult(GeofenceReminderDecision.NONE)
      }

      val matchedShift = findActiveOrJustEndedShift(shifts, calendar)
      if (matchedShift != null) {
        val inActiveCoreWindow = isWithinShiftWindow(matchedShift, calendar, 0)
        val inShiftEndGrace = isInPostShiftEndWindow(matchedShift, calendar)
        val previouslyInside = wasInsideGeofence ?: !currentlyInsideGeofence

        // 1. Shift-end fallback (even outside HQ geofence) so field workers going straight home get reminded
        if (inShiftEndGrace && hasCheckedInForShift && !hasCheckedOutForShift) {
          return MultiShiftEvaluationResult(
            decision = GeofenceReminderDecision.SHIFT_END_CLOCK_OUT_REMINDER,
            activeShift = matchedShift,
            effectiveShiftDateAd = matchedShift.shiftDateAd,
          )
        }

        // 2. Arriving inside HQ during active shift window without clock-in
        if (
          inActiveCoreWindow &&
            matchedShift.requireGeofenceCheckIn &&
            !previouslyInside &&
            currentlyInsideGeofence &&
            !hasCheckedInForShift
        ) {
          return MultiShiftEvaluationResult(
            decision = GeofenceReminderDecision.CLOCK_IN_REMINDER,
            activeShift = matchedShift,
            effectiveShiftDateAd = matchedShift.shiftDateAd,
          )
        }

        // 3. Leaving HQ perimeter while clocked in (suppressed mid-shift for FIELD shifts/active routes)
        val allowExitAlert =
          matchedShift.requireGeofenceCheckOut &&
            matchedShift.workMode == ShiftWorkMode.OFFICE &&
            !hasActiveFieldRoute
        if (
          allowExitAlert &&
            inActiveCoreWindow &&
            previouslyInside &&
            !currentlyInsideGeofence &&
            hasCheckedInForShift &&
            !hasCheckedOutForShift
        ) {
          return MultiShiftEvaluationResult(
            decision = GeofenceReminderDecision.CLOCK_OUT_REMINDER,
            activeShift = matchedShift,
            effectiveShiftDateAd = matchedShift.shiftDateAd,
          )
        }

        return MultiShiftEvaluationResult(
          decision = GeofenceReminderDecision.NONE,
          activeShift = matchedShift,
          effectiveShiftDateAd = matchedShift.shiftDateAd,
        )
      }

      // If shifts exist in DB for this employee, don't fall back to arbitrary static window outside scheduled shifts
      if (shifts.isNotEmpty()) {
        return MultiShiftEvaluationResult(GeofenceReminderDecision.NONE)
      }

      val fallbackDecision =
        evaluateTransition(
          hourOfDay = calendar.get(Calendar.HOUR_OF_DAY),
          minuteOfHour = calendar.get(Calendar.MINUTE),
          dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK),
          schedule = schedule,
          currentlyInsideGeofence = currentlyInsideGeofence,
          wasInsideGeofence = wasInsideGeofence,
          hasCheckedInToday = hasCheckedInForShift,
          hasCheckedOutToday = hasCheckedOutForShift,
        )
      return MultiShiftEvaluationResult(decision = fallbackDecision)
    }

    /**
     * Pure deterministic geofence transition evaluator for local clock-in/out alerts.
     * - Entering perimeter during work hours without today's clock-in -> CLOCK_IN_REMINDER
     * - Leaving perimeter during work hours after clock-in and before clock-out -> CLOCK_OUT_REMINDER
     * - Outside work hours or unchanged perimeter state -> NONE
     */
    fun evaluateTransition(
      hourOfDay: Int,
      minuteOfHour: Int,
      dayOfWeek: Int,
      schedule: WorkHoursScheduleConfig,
      currentlyInsideGeofence: Boolean,
      wasInsideGeofence: Boolean?,
      hasCheckedInToday: Boolean,
      hasCheckedOutToday: Boolean,
    ): GeofenceReminderDecision {
      if (!isWithinWorkingHours(hourOfDay, minuteOfHour, dayOfWeek, schedule)) {
        return GeofenceReminderDecision.NONE
      }
      val previouslyInside = wasInsideGeofence ?: !currentlyInsideGeofence
      return when {
        !previouslyInside && currentlyInsideGeofence && !hasCheckedInToday ->
          GeofenceReminderDecision.CLOCK_IN_REMINDER
        previouslyInside && !currentlyInsideGeofence && hasCheckedInToday && !hasCheckedOutToday ->
          GeofenceReminderDecision.CLOCK_OUT_REMINDER
        else -> GeofenceReminderDecision.NONE
      }
    }
  }
}

@HiltWorker
class GeofenceWorkHoursWorker @AssistedInject constructor(
  @Assisted appContext: Context,
  @Assisted workerParams: WorkerParameters,
  private val scheduler: GeofenceReminderScheduler,
) : CoroutineWorker(appContext, workerParams) {

  override suspend fun doWork(): Result {
    scheduler.checkGeofenceTransitionAndNotify()
    return Result.success()
  }
}
