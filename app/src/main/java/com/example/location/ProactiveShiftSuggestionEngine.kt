package com.example.location

import com.example.domain.geofence.RouteLocationCalculator
import com.example.domain.model.AttendanceRecord
import com.example.domain.model.AttendanceStatus
import com.example.domain.model.EmployeeShift
import com.example.domain.model.LocationBreadcrumb
import com.example.domain.model.ShiftWorkMode
import com.example.domain.model.WorkHoursScheduleConfig
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

data class ProactiveShiftSuggestion(
  val shift: EmployeeShift,
  val confidenceScore: Int,
  val headline: String,
  val reasonSummary: String,
  val reasons: List<String>,
  val nearestVisitedSite: String? = null,
  val typicalCheckInLabel: String? = null,
  val distanceFromOfficeMeters: Double? = null,
  val minutesUntilShiftStart: Int = 0,
)

/**
 * Scores synced employee shifts to proactively suggest the most relevant shift when an employee
 * starts their workday outside the office perimeter, combining:
 * 1. Recent & current GPS proximity to historical client/field route locations.
 * 2. Historical attendance check-in times and prior off-site start frequency.
 * 3. Shift start window proximity (active now or starting within the pre-shift lead window).
 */
object ProactiveShiftSuggestionEngine {

  private const val PRE_SHIFT_LEAD_MINUTES = 120
  private const val NEAR_CLIENT_SITE_RADIUS_METERS = 1_500.0
  private const val MIN_SUGGESTION_CONFIDENCE = 40

  fun suggestShiftForOffsiteStart(
    calendar: Calendar,
    schedule: WorkHoursScheduleConfig,
    shifts: List<EmployeeShift>,
    attendanceHistory: List<AttendanceRecord>,
    recentBreadcrumbs: List<LocationBreadcrumb>,
    currentlyInsideGeofence: Boolean,
    currentLatitude: Double? = null,
    currentLongitude: Double? = null,
    distanceFromBranchMeters: Double? = null,
    hasCheckedInToday: Boolean = false,
    hasCheckedOutToday: Boolean = false,
    hasActiveFieldRoute: Boolean = false,
    leadWindowMinutes: Int = PRE_SHIFT_LEAD_MINUTES,
    routeCalculator: RouteLocationCalculator = RouteLocationCalculator(),
  ): ProactiveShiftSuggestion? {
    if (!schedule.enabled) return null
    if (currentlyInsideGeofence) return null
    if (hasCheckedInToday || hasCheckedOutToday || hasActiveFieldRoute) return null
    if (shifts.isEmpty()) return null

    val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    val todayAd = dateFmt.format(Date(calendar.timeInMillis))
    val nowMinutes =
      calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)

    val candidateEntries =
      shifts.mapNotNull { shift ->
        val minutesUntilStart =
          computeMinutesUntilShiftStart(
            shift = shift,
            calendar = calendar,
            todayAd = todayAd,
            nowMinutes = nowMinutes,
          )
        val isActiveNow = GeofenceReminderScheduler.isWithinShiftWindow(shift, calendar, 0)
        val isWithinLeadWindow =
          shift.shiftDateAd == todayAd && minutesUntilStart in 0..leadWindowMinutes
        if (isActiveNow || isWithinLeadWindow) {
          shift to minutesUntilStart
        } else {
          null
        }
      }
    if (candidateEntries.isEmpty()) return null

    // 1. Analyze historical attendance start times & off-site frequency
    val historicalCheckInMinutes =
      attendanceHistory
        .filter { it.status == AttendanceStatus.PRESENT || it.status == AttendanceStatus.HALF_DAY }
        .mapNotNull { parseTimeToMinutesOfDay(it.checkInTime) }
        .sorted()
    val typicalCheckInMinute =
      if (historicalCheckInMinutes.isNotEmpty()) {
        historicalCheckInMinutes[historicalCheckInMinutes.size / 2]
      } else {
        null
      }
    val typicalCheckInLabel = typicalCheckInMinute?.let(::formatMinutesAs12Hour)

    val validDistanceRecords = attendanceHistory.mapNotNull { it.distanceFromBranchMeters }
    val historicalOffsiteCheckIns = validDistanceRecords.count { it > 250.0 }
    val offSiteHistoryRatio =
      if (validDistanceRecords.isNotEmpty()) {
        historicalOffsiteCheckIns.toDouble() / validDistanceRecords.size.toDouble()
      } else {
        0.0
      }

    // 2. Analyze recent GPS / visited client route locations
    val nearestVisitedSiteMatch =
      findNearestVisitedFieldSite(
        currentLatitude = currentLatitude,
        currentLongitude = currentLongitude,
        breadcrumbs = recentBreadcrumbs,
        routeCalculator = routeCalculator,
      )
    val recentOutsideBreadcrumb =
      recentBreadcrumbs
        .sortedByDescending { it.capturedAtEpochMs }
        .firstOrNull { !it.isInsideGeofence }

    val scored =
      candidateEntries.map { (shift, minutesUntilStart) ->
        var score = 0
        val reasons = mutableListOf<String>()
        val shiftStartMinutes = shift.startHour * 60 + shift.startMinute

        // A. Temporal fit (0..32 pts)
        when {
          minutesUntilStart in -45..30 -> {
            score += 32
            val label =
              if (minutesUntilStart > 0) "Starts in ${minutesUntilStart}m"
              else "Shift window starting now"
            reasons += "$label (${shift.formattedTimeWindow()})"
          }
          minutesUntilStart in 31..leadWindowMinutes -> {
            score += 22
            reasons += "Upcoming in ${minutesUntilStart}m (${shift.formattedTimeWindow()})"
          }
          else -> {
            score += 15
            reasons += "Ongoing shift window (${shift.formattedTimeWindow()})"
          }
        }

        // B. Work mode compatibility for starting outside HQ perimeter (-8..+24 pts)
        when (shift.workMode) {
          ShiftWorkMode.FIELD -> {
            score += 24
            if (offSiteHistoryRatio >= 0.25) {
              score += 6
            }
          }
          ShiftWorkMode.HYBRID -> {
            score += 18
            if (offSiteHistoryRatio >= 0.25) {
              score += 4
            }
          }
          ShiftWorkMode.OFFICE -> {
            score += if (!shift.requireGeofenceCheckIn) 10 else -8
          }
        }

        // C. Historical attendance timing match (0..34 pts)
        if (typicalCheckInMinute != null && typicalCheckInLabel != null) {
          val diff = circularMinutesDiff(typicalCheckInMinute, shiftStartMinutes)
          val alignedHistoryCount =
            historicalCheckInMinutes.count { circularMinutesDiff(it, shiftStartMinutes) <= 45 }
          if (diff <= 35) {
            score += 26
            reasons += "Matches your usual ~$typicalCheckInLabel start"
          } else if (diff <= 75) {
            score += 14
            reasons += "Close to your usual ~$typicalCheckInLabel check-in"
          }
          if (alignedHistoryCount >= 2) {
            score += 8
          }
        } else if (shift.sequenceNo == 1) {
          score += 8
        }

        // D. Recent GPS & historical visited client site correlation (0..22 pts)
        if (nearestVisitedSiteMatch != null && shift.workMode != ShiftWorkMode.OFFICE) {
          val (siteLabel, siteDistanceMeters) = nearestVisitedSiteMatch
          score += if (shift.workMode == ShiftWorkMode.FIELD) 16 else 10
          reasons += "Near visited site: $siteLabel (${routeCalculator.formatDistance(siteDistanceMeters)})"
        } else if (recentOutsideBreadcrumb != null && shift.workMode != ShiftWorkMode.OFFICE) {
          score += 8
          reasons += "Recent route pin near ${recentOutsideBreadcrumb.siteLabel}"
        }

        if (distanceFromBranchMeters != null && distanceFromBranchMeters > 300.0) {
          if (shift.workMode != ShiftWorkMode.OFFICE) {
            score += 6
          }
          reasons += "${routeCalculator.formatDistance(distanceFromBranchMeters)} outside office perimeter"
        }

        if (shift.sequenceNo == 1) {
          score += 4
        }

        val clampedScore = score.coerceIn(0, 99)
        val summary =
          reasons
            .distinct()
            .take(3)
            .joinToString(" • ")
            .ifBlank { "${shift.workMode.label} (${shift.formattedTimeWindow()})" }

        ProactiveShiftSuggestion(
          shift = shift,
          confidenceScore = clampedScore,
          headline = "Suggested: ${shift.shiftName}",
          reasonSummary = summary,
          reasons = reasons.distinct(),
          nearestVisitedSite = nearestVisitedSiteMatch?.first ?: recentOutsideBreadcrumb?.siteLabel,
          typicalCheckInLabel = typicalCheckInLabel,
          distanceFromOfficeMeters = distanceFromBranchMeters,
          minutesUntilShiftStart = minutesUntilStart,
        )
      }

    val best =
      scored.maxWithOrNull(
        compareBy<ProactiveShiftSuggestion> { it.confidenceScore }
          .thenByDescending { if (it.shift.workMode == ShiftWorkMode.FIELD) 2 else if (it.shift.workMode == ShiftWorkMode.HYBRID) 1 else 0 }
          .thenBy { it.shift.sequenceNo }
          .thenBy { abs(it.minutesUntilShiftStart) }
      ) ?: return null

    return best.takeIf { it.confidenceScore >= MIN_SUGGESTION_CONFIDENCE }
  }

  private fun computeMinutesUntilShiftStart(
    shift: EmployeeShift,
    calendar: Calendar,
    todayAd: String,
    nowMinutes: Int,
  ): Int {
    val startMinutes = shift.startHour * 60 + shift.startMinute
    if (shift.shiftDateAd == todayAd) {
      return startMinutes - nowMinutes
    }
    // Check if shift started yesterday and crossed midnight
    val yesterdayCal = (calendar.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
    val yesterdayAd = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(yesterdayCal.timeInMillis))
    if (shift.shiftDateAd == yesterdayAd && shift.crossesMidnight) {
      return -((24 * 60 - startMinutes) + nowMinutes)
    }
    return Int.MAX_VALUE
  }

  private fun findNearestVisitedFieldSite(
    currentLatitude: Double?,
    currentLongitude: Double?,
    breadcrumbs: List<LocationBreadcrumb>,
    routeCalculator: RouteLocationCalculator,
  ): Pair<String, Double>? {
    if (currentLatitude == null || currentLongitude == null || breadcrumbs.isEmpty()) return null
    val fieldSites =
      breadcrumbs.filter { !it.isInsideGeofence || it.distanceFromBranchMeters > 250.0 }
    if (fieldSites.isEmpty()) return null
    val best =
      fieldSites.minByOrNull { point ->
        routeCalculator.haversineMeters(
          currentLatitude,
          currentLongitude,
          point.latitude,
          point.longitude,
        )
      } ?: return null
    val dist =
      routeCalculator.haversineMeters(
        currentLatitude,
        currentLongitude,
        best.latitude,
        best.longitude,
      )
    return if (dist <= NEAR_CLIENT_SITE_RADIUS_METERS) {
      best.siteLabel to dist
    } else {
      null
    }
  }

  internal fun parseTimeToMinutesOfDay(timeLabel: String?): Int? {
    if (timeLabel.isNullOrBlank()) return null
    val cleaned = timeLabel.trim().uppercase(Locale.US)
    val amPmMatch = Regex("^(\\d{1,2}):(\\d{2})\\s*(AM|PM)$").find(cleaned)
    if (amPmMatch != null) {
      var hour = amPmMatch.groupValues[1].toIntOrNull() ?: return null
      val minute = amPmMatch.groupValues[2].toIntOrNull() ?: return null
      val amPm = amPmMatch.groupValues[3]
      if (hour !in 1..12 || minute !in 0..59) return null
      if (amPm == "AM" && hour == 12) hour = 0
      if (amPm == "PM" && hour < 12) hour += 12
      return hour * 60 + minute
    }
    val twentyFourMatch = Regex("^(\\d{1,2}):(\\d{2})$").find(cleaned)
    if (twentyFourMatch != null) {
      val hour = twentyFourMatch.groupValues[1].toIntOrNull() ?: return null
      val minute = twentyFourMatch.groupValues[2].toIntOrNull() ?: return null
      if (hour !in 0..23 || minute !in 0..59) return null
      return hour * 60 + minute
    }
    return null
  }

  private fun formatMinutesAs12Hour(totalMinutes: Int): String {
    val norm = ((totalMinutes % (24 * 60)) + 24 * 60) % (24 * 60)
    val h24 = norm / 60
    val m = norm % 60
    val amPm = if (h24 >= 12) "PM" else "AM"
    val h12 = when {
      h24 == 0 -> 12
      h24 > 12 -> h24 - 12
      else -> h24
    }
    return String.format(Locale.US, "%02d:%02d %s", h12, m, amPm)
  }

  private fun circularMinutesDiff(a: Int, b: Int): Int {
    val raw = abs(a - b) % (24 * 60)
    return minOf(raw, 24 * 60 - raw)
  }
}
