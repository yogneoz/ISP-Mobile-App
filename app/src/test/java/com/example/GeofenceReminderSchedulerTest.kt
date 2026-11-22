package com.example

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.domain.model.WorkHoursScheduleConfig
import com.example.location.GeofenceReminderDecision
import com.example.location.GeofenceReminderScheduler
import com.example.notifications.PulseHrNotificationHelper
import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
class GeofenceReminderSchedulerTest {

  private val defaultSchedule =
    WorkHoursScheduleConfig(
      enabled = true,
      startHour = 8,
      startMinute = 30,
      endHour = 18,
      endMinute = 30,
      weekdaysOnly = true,
    )

  @Test
  fun `triggers clock-in reminder when entering perimeter during working hours without check-in`() {
    val decision =
      GeofenceReminderScheduler.evaluateTransition(
        hourOfDay = 9,
        minuteOfHour = 15,
        dayOfWeek = Calendar.MONDAY,
        schedule = defaultSchedule,
        currentlyInsideGeofence = true,
        wasInsideGeofence = false,
        hasCheckedInToday = false,
        hasCheckedOutToday = false,
      )
    assertEquals(GeofenceReminderDecision.CLOCK_IN_REMINDER, decision)
  }

  @Test
  fun `triggers clock-out reminder when leaving perimeter during working hours after check-in`() {
    val decision =
      GeofenceReminderScheduler.evaluateTransition(
        hourOfDay = 17,
        minuteOfHour = 45,
        dayOfWeek = Calendar.WEDNESDAY,
        schedule = defaultSchedule,
        currentlyInsideGeofence = false,
        wasInsideGeofence = true,
        hasCheckedInToday = true,
        hasCheckedOutToday = false,
      )
    assertEquals(GeofenceReminderDecision.CLOCK_OUT_REMINDER, decision)
  }

  @Test
  fun `suppresses reminders outside designated working hours or on weekends`() {
    assertFalse(
      GeofenceReminderScheduler.isWithinWorkingHours(7, 30, Calendar.TUESDAY, defaultSchedule)
    )
    assertFalse(
      GeofenceReminderScheduler.isWithinWorkingHours(10, 0, Calendar.SATURDAY, defaultSchedule)
    )

    val evening =
      GeofenceReminderScheduler.evaluateTransition(
        hourOfDay = 20,
        minuteOfHour = 0,
        dayOfWeek = Calendar.TUESDAY,
        schedule = defaultSchedule,
        currentlyInsideGeofence = true,
        wasInsideGeofence = false,
        hasCheckedInToday = false,
        hasCheckedOutToday = false,
      )
    assertEquals(GeofenceReminderDecision.NONE, evening)
  }

  @Test
  fun `suppresses exit reminder if employee already clocked out`() {
    val alreadyOut =
      GeofenceReminderScheduler.evaluateTransition(
        hourOfDay = 18,
        minuteOfHour = 0,
        dayOfWeek = Calendar.FRIDAY,
        schedule = defaultSchedule,
        currentlyInsideGeofence = false,
        wasInsideGeofence = true,
        hasCheckedInToday = true,
        hasCheckedOutToday = true,
      )
    assertEquals(GeofenceReminderDecision.NONE, alreadyOut)
  }

  @Test
  fun `notification helper dispatches clock-in and clock-out geofence reminder notifications`() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    Shadows.shadowOf(app).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
    val helper = PulseHrNotificationHelper(app)
    val nm = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val shadowNm = Shadows.shadowOf(nm)

    helper.showGeofenceAttendanceReminder(
      isEntry = true,
      branchName = "MV-HQ Engineering Campus",
      workHoursLabel = defaultSchedule.formattedWindow(),
    )
    helper.showGeofenceAttendanceReminder(
      isEntry = false,
      branchName = "MV-HQ Engineering Campus",
      workHoursLabel = defaultSchedule.formattedWindow(),
    )

    assertEquals(2, shadowNm.allNotifications.size)
    assertTrue(shadowNm.allNotifications.any { it.extras.getString("android.title")?.contains("Clock In") == true })
    assertTrue(shadowNm.allNotifications.any { it.extras.getString("android.title")?.contains("Clock Out") == true })
  }

  @Test
  fun `overnight shift stays active past midnight and triggers off-site shift-end reminder`() {
    val nightShift =
      com.example.domain.model.EmployeeShift(
        id = "shf_night_1",
        employeeId = "emp_103",
        shiftCode = "NOC-NIGHT",
        shiftName = "Overnight Backbone Maintenance",
        shiftDateAd = "2026-11-20",
        startHour = 22,
        startMinute = 0,
        endHour = 6,
        endMinute = 0,
        crossesMidnight = true,
        workMode = com.example.domain.model.ShiftWorkMode.OFFICE,
        requireGeofenceCheckIn = true,
        requireGeofenceCheckOut = true,
      )

    // 02:30 AM next day (Nov 21): inside active overnight shift from Nov 20
    val midNightCal =
      Calendar.getInstance().apply {
        set(2026, Calendar.NOVEMBER, 21, 2, 30, 0)
      }
    assertTrue(GeofenceReminderScheduler.isWithinShiftWindow(nightShift, midNightCal))

    // Entering office at 22:15 on Nov 20 without clock-in -> CLOCK_IN_REMINDER
    val arrivalCal =
      Calendar.getInstance().apply {
        set(2026, Calendar.NOVEMBER, 20, 22, 15, 0)
      }
    val entryResult =
      GeofenceReminderScheduler.evaluateMultiShiftTransition(
        calendar = arrivalCal,
        schedule = defaultSchedule,
        shifts = listOf(nightShift),
        currentlyInsideGeofence = true,
        wasInsideGeofence = false,
        hasCheckedInForShift = false,
        hasCheckedOutForShift = false,
      )
    assertEquals(GeofenceReminderDecision.CLOCK_IN_REMINDER, entryResult.decision)
    assertEquals("shf_night_1", entryResult.activeShift?.id)

    // 06:20 AM on Nov 21 (post-shift grace window): employee went straight home outside HQ -> SHIFT_END_CLOCK_OUT_REMINDER
    val shiftEndCal =
      Calendar.getInstance().apply {
        set(2026, Calendar.NOVEMBER, 21, 6, 20, 0)
      }
    val endResult =
      GeofenceReminderScheduler.evaluateMultiShiftTransition(
        calendar = shiftEndCal,
        schedule = defaultSchedule,
        shifts = listOf(nightShift),
        currentlyInsideGeofence = false,
        wasInsideGeofence = false,
        hasCheckedInForShift = true,
        hasCheckedOutForShift = false,
      )
    assertEquals(GeofenceReminderDecision.SHIFT_END_CLOCK_OUT_REMINDER, endResult.decision)
    assertEquals("2026-11-20", endResult.effectiveShiftDateAd)
  }

  @Test
  fun `field shift suppresses mid-day HQ exit alert but fires shift-end reminder when straight home`() {
    val morningFieldShift =
      com.example.domain.model.EmployeeShift(
        id = "shf_field_1",
        employeeId = "emp_103",
        shiftCode = "FTTH-DAY",
        shiftName = "Primary Field Dispatch",
        shiftDateAd = "2026-11-21",
        startHour = 8,
        startMinute = 30,
        endHour = 17,
        endMinute = 30,
        crossesMidnight = false,
        workMode = com.example.domain.model.ShiftWorkMode.FIELD,
        requireGeofenceCheckIn = true,
        requireGeofenceCheckOut = false,
        sequenceNo = 1,
      )
    val eveningSplitShift =
      com.example.domain.model.EmployeeShift(
        id = "shf_split_2",
        employeeId = "emp_103",
        shiftCode = "EVE-SURGE",
        shiftName = "Evening Split Cutover",
        shiftDateAd = "2026-11-21",
        startHour = 19,
        startMinute = 0,
        endHour = 21,
        endMinute = 0,
        crossesMidnight = false,
        workMode = com.example.domain.model.ShiftWorkMode.FIELD,
        requireGeofenceCheckIn = true,
        requireGeofenceCheckOut = false,
        sequenceNo = 2,
      )

    // 10:30 AM leaving HQ for client visits -> suppressed because it's a FIELD shift
    val midDayExitCal =
      Calendar.getInstance().apply {
        set(2026, Calendar.NOVEMBER, 21, 10, 30, 0)
      }
    val midDayResult =
      GeofenceReminderScheduler.evaluateMultiShiftTransition(
        calendar = midDayExitCal,
        schedule = defaultSchedule,
        shifts = listOf(morningFieldShift, eveningSplitShift),
        currentlyInsideGeofence = false,
        wasInsideGeofence = true,
        hasCheckedInForShift = true,
        hasCheckedOutForShift = false,
        hasActiveFieldRoute = true,
      )
    assertEquals(GeofenceReminderDecision.NONE, midDayResult.decision)

    // 17:45 PM outside HQ -> fires SHIFT_END_CLOCK_OUT_REMINDER
    val endOfDayCal =
      Calendar.getInstance().apply {
        set(2026, Calendar.NOVEMBER, 21, 17, 45, 0)
      }
    val endResult =
      GeofenceReminderScheduler.evaluateMultiShiftTransition(
        calendar = endOfDayCal,
        schedule = defaultSchedule,
        shifts = listOf(morningFieldShift, eveningSplitShift),
        currentlyInsideGeofence = false,
        wasInsideGeofence = false,
        hasCheckedInForShift = true,
        hasCheckedOutForShift = false,
      )
    assertEquals(GeofenceReminderDecision.SHIFT_END_CLOCK_OUT_REMINDER, endResult.decision)
    assertEquals("shf_field_1", endResult.activeShift?.id)

    // 19:20 PM -> second split shift resolves cleanly
    val splitCal =
      Calendar.getInstance().apply {
        set(2026, Calendar.NOVEMBER, 21, 19, 20, 0)
      }
    val splitActive =
      GeofenceReminderScheduler.findActiveOrJustEndedShift(
        listOf(morningFieldShift, eveningSplitShift),
        splitCal,
      )
    assertEquals("shf_split_2", splitActive?.id)
    assertFalse(GeofenceReminderScheduler.isUpcomingShift(morningFieldShift, splitCal))
    assertTrue(GeofenceReminderScheduler.isUpcomingShift(eveningSplitShift, splitCal))
  }

  @Test
  fun `room persists and queries EmployeeShiftEntity synced from bootstrap`() = kotlinx.coroutines.runBlocking {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val db =
      androidx.room.Room.inMemoryDatabaseBuilder(app, com.example.data.local.PulseHrDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    try {
      val dao = db.pulseHrDao()
      dao.insertEmployeeShifts(
        listOf(
          com.example.data.local.EmployeeShiftEntity(
            id = "s_1",
            employeeId = "emp_103",
            shiftCode = "DAY",
            shiftName = "Day Shift",
            shiftDateAd = "2026-11-21",
            startHour = 8,
            startMinute = 30,
            endHour = 17,
            endMinute = 30,
            crossesMidnight = false,
            workMode = "FIELD",
          ),
          com.example.data.local.EmployeeShiftEntity(
            id = "s_2",
            employeeId = "emp_103",
            shiftCode = "NIGHT",
            shiftName = "Overnight Shift",
            shiftDateAd = "2026-11-22",
            startHour = 22,
            startMinute = 0,
            endHour = 6,
            endMinute = 0,
            crossesMidnight = true,
            workMode = "OFFICE",
          ),
        )
      )
      val loaded = dao.getEmployeeShiftsSync("emp_103")
      assertEquals(2, loaded.size)
      assertEquals("s_1", loaded[0].id)
      assertTrue(loaded[1].crossesMidnight)
    } finally {
      db.close()
    }
  }
}
