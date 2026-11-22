package com.example

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkManager
import com.example.data.local.AttendanceEntity
import com.example.data.local.EmployeeShiftEntity
import com.example.data.local.LocationBreadcrumbEntity
import com.example.data.local.PulseHrDatabase
import com.example.domain.geofence.GeofenceEvaluator
import com.example.domain.model.AttendanceRecord
import com.example.domain.model.AttendanceStatus
import com.example.domain.model.EmployeeShift
import com.example.domain.model.LocationBreadcrumb
import com.example.domain.model.RouteEventType
import com.example.domain.model.ShiftWorkMode
import com.example.domain.model.SyncStatus
import com.example.domain.model.WorkHoursScheduleConfig
import com.example.location.GeofenceReminderDecision
import com.example.location.GeofenceReminderScheduler
import com.example.location.LocationManager
import com.example.location.ProactiveShiftSuggestionEngine
import com.example.notifications.PulseHrNotificationHelper
import com.example.security.SecureTokenStore
import com.google.android.gms.location.LocationServices
import java.util.Calendar
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
class ProactiveShiftSuggestionTest {

  private val defaultSchedule =
    WorkHoursScheduleConfig(
      enabled = true,
      startHour = 8,
      startMinute = 30,
      endHour = 18,
      endMinute = 30,
      weekdaysOnly = true,
    )

  private fun shift(
    id: String,
    code: String,
    name: String,
    dateAd: String = "2026-11-21",
    startH: Int,
    startM: Int,
    endH: Int,
    endM: Int,
    mode: ShiftWorkMode,
    seq: Int = 1,
    requireIn: Boolean = mode == ShiftWorkMode.OFFICE,
  ) =
    EmployeeShift(
      id = id,
      employeeId = "emp_103",
      shiftCode = code,
      shiftName = name,
      shiftDateAd = dateAd,
      startHour = startH,
      startMinute = startM,
      endHour = endH,
      endMinute = endM,
      crossesMidnight = (endH * 60 + endM) <= (startH * 60 + startM),
      workMode = mode,
      requireGeofenceCheckIn = requireIn,
      requireGeofenceCheckOut = mode == ShiftWorkMode.OFFICE,
      branchCode = "MV-HQ",
      sequenceNo = seq,
    )

  @Test
  fun `suggests field shift over overlapping office shift when starting near visited client site with morning pattern`() {
    val fieldShift =
      shift(
        id = "shf_field_morning",
        code = "FTTH-DAY",
        name = "Primary Field Dispatch Shift",
        startH = 8,
        startM = 30,
        endH = 17,
        endM = 30,
        mode = ShiftWorkMode.FIELD,
        seq = 1,
      )
    val officeShift =
      shift(
        id = "shf_hq_morning",
        code = "HQ-DESK",
        name = "HQ Helpdesk Rotation",
        startH = 9,
        startM = 0,
        endH = 18,
        endM = 0,
        mode = ShiftWorkMode.OFFICE,
        seq = 2,
      )

    val history =
      listOf(
        AttendanceRecord(
          id = "a1",
          employeeId = "emp_103",
          branchId = "br_hq",
          attendanceDateAd = "2026-11-18",
          checkInTime = "08:48 AM",
          checkOutTime = "05:30 PM",
          status = AttendanceStatus.PRESENT,
          distanceFromBranchMeters = 1_450.0,
        ),
        AttendanceRecord(
          id = "a2",
          employeeId = "emp_103",
          branchId = "br_hq",
          attendanceDateAd = "2026-11-19",
          checkInTime = "08:55 AM",
          checkOutTime = "05:40 PM",
          status = AttendanceStatus.PRESENT,
          distanceFromBranchMeters = 2_100.0,
        ),
      )

    val breadcrumbs =
      listOf(
        LocationBreadcrumb(
          id = "b1",
          employeeId = "emp_103",
          sessionId = "s_prev",
          dateIso = "2026-11-19",
          eventType = RouteEventType.MANUAL_PUSH,
          latitude = 37.3948,
          longitude = -122.0785,
          accuracyMeters = 10f,
          siteLabel = "Everest Espresso Roasters • 412 Castro St",
          distanceFromBranchMeters = 3_060.0,
          isInsideGeofence = false,
          capturedAtEpochMs = 1_763_500_000_000L,
          capturedTimeLabel = "09:10:00 AM",
          syncStatus = SyncStatus.SYNCED,
        )
      )

    val cal = Calendar.getInstance().apply { set(2026, Calendar.NOVEMBER, 21, 8, 20, 0) }
    val suggestion =
      ProactiveShiftSuggestionEngine.suggestShiftForOffsiteStart(
        calendar = cal,
        schedule = defaultSchedule,
        shifts = listOf(officeShift, fieldShift),
        attendanceHistory = history,
        recentBreadcrumbs = breadcrumbs,
        currentlyInsideGeofence = false,
        currentLatitude = 37.3952,
        currentLongitude = -122.0789,
        distanceFromBranchMeters = 3_010.0,
        hasCheckedInToday = false,
        hasCheckedOutToday = false,
      )

    assertNotNull(suggestion)
    assertEquals("shf_field_morning", suggestion!!.shift.id)
    assertTrue(suggestion.confidenceScore >= 75)
    assertEquals("Everest Espresso Roasters • 412 Castro St", suggestion.nearestVisitedSite)
    assertEquals("08:55 AM", suggestion.typicalCheckInLabel)
    assertTrue(suggestion.reasonSummary.contains("Castro St"))
  }

  @Test
  fun `prefers evening shift when employee historical attendance pattern starts around 18_30`() {
    val morningShift =
      shift(
        id = "shf_am",
        code = "FTTH-AM",
        name = "Morning Route Shift",
        startH = 8,
        startM = 30,
        endH = 12,
        endM = 30,
        mode = ShiftWorkMode.FIELD,
        seq = 1,
      )
    val eveningShift =
      shift(
        id = "shf_pm",
        code = "EVE-SURGE",
        name = "Evening Surge Cutover",
        startH = 18,
        startM = 30,
        endH = 22,
        endM = 0,
        mode = ShiftWorkMode.FIELD,
        seq = 2,
      )

    val eveningHistory =
      listOf(
        AttendanceRecord(
          id = "a_pm1",
          employeeId = "emp_103",
          branchId = "br_hq",
          attendanceDateAd = "2026-11-18",
          checkInTime = "06:22 PM",
          checkOutTime = "10:15 PM",
          status = AttendanceStatus.PRESENT,
          distanceFromBranchMeters = 900.0,
        ),
        AttendanceRecord(
          id = "a_pm2",
          employeeId = "emp_103",
          branchId = "br_hq",
          attendanceDateAd = "2026-11-19",
          checkInTime = "06:35 PM",
          checkOutTime = "10:20 PM",
          status = AttendanceStatus.PRESENT,
          distanceFromBranchMeters = 1_200.0,
        ),
      )

    val cal = Calendar.getInstance().apply { set(2026, Calendar.NOVEMBER, 21, 18, 15, 0) }
    val suggestion =
      ProactiveShiftSuggestionEngine.suggestShiftForOffsiteStart(
        calendar = cal,
        schedule = defaultSchedule,
        shifts = listOf(morningShift, eveningShift),
        attendanceHistory = eveningHistory,
        recentBreadcrumbs = emptyList(),
        currentlyInsideGeofence = false,
        distanceFromBranchMeters = 1_200.0,
      )

    assertNotNull(suggestion)
    assertEquals("shf_pm", suggestion!!.shift.id)
    assertTrue(suggestion.reasonSummary.contains("06:35 PM") || suggestion.reasonSummary.contains("Evening"))
  }

  @Test
  fun `suppresses proactive shift suggestion when inside office or already checked in`() {
    val fieldShift =
      shift(
        id = "shf_field",
        code = "FTTH-DAY",
        name = "Primary Field Dispatch",
        startH = 8,
        startM = 30,
        endH = 17,
        endM = 30,
        mode = ShiftWorkMode.FIELD,
      )
    val cal = Calendar.getInstance().apply { set(2026, Calendar.NOVEMBER, 21, 8, 40, 0) }

    val insideOffice =
      ProactiveShiftSuggestionEngine.suggestShiftForOffsiteStart(
        calendar = cal,
        schedule = defaultSchedule,
        shifts = listOf(fieldShift),
        attendanceHistory = emptyList(),
        recentBreadcrumbs = emptyList(),
        currentlyInsideGeofence = true,
      )
    assertNull(insideOffice)

    val alreadyCheckedIn =
      ProactiveShiftSuggestionEngine.suggestShiftForOffsiteStart(
        calendar = cal,
        schedule = defaultSchedule,
        shifts = listOf(fieldShift),
        attendanceHistory = emptyList(),
        recentBreadcrumbs = emptyList(),
        currentlyInsideGeofence = false,
        hasCheckedInToday = true,
      )
    assertNull(alreadyCheckedIn)
  }

  @Test
  fun `scheduler dispatches proactive shift suggestion notification outside HQ and deduplicates repeat check`() =
    runBlocking {
      val app = ApplicationProvider.getApplicationContext<Application>()
      Shadows.shadowOf(app).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
      if (!WorkManager.isInitialized()) {
        WorkManager.initialize(app, Configuration.Builder().build())
      }
      val db = Room.inMemoryDatabaseBuilder(app, PulseHrDatabase::class.java).allowMainThreadQueries().build()
      try {
        val dao = db.pulseHrDao()
        val tokenStore =
          SecureTokenStore.createForTesting(
            app.getSharedPreferences("test_proactive_shift_prefs", Context.MODE_PRIVATE)
          )
        tokenStore.clearAll()
        tokenStore.saveWorkHoursSchedule(defaultSchedule)

        dao.insertEmployeeShifts(
          listOf(
            EmployeeShiftEntity(
              id = "shf_offsite_1",
              employeeId = "emp_103",
              shiftCode = "FTTH-DAY",
              shiftName = "Primary Field Dispatch",
              shiftDateAd = "2026-11-21",
              startHour = 8,
              startMinute = 30,
              endHour = 17,
              endMinute = 30,
              crossesMidnight = false,
              workMode = "FIELD",
              requireGeofenceCheckIn = true,
              requireGeofenceCheckOut = false,
              sequenceNo = 1,
            )
          )
        )
        dao.insertAttendance(
          listOf(
            AttendanceEntity(
              id = "hist_1",
              employeeId = "emp_103",
              branchId = "br_hq",
              attendanceDateAd = "2026-11-19",
              checkInTime = "08:50 AM",
              checkOutTime = "05:35 PM",
              status = "PRESENT",
              distanceFromBranchMeters = 1_800.0,
            )
          )
        )
        dao.insertLocationBreadcrumbs(
          listOf(
            LocationBreadcrumbEntity(
              id = "crumb_1",
              employeeId = "emp_103",
              sessionId = "route_prev",
              dateIso = "2026-11-19",
              eventType = "WAYPOINT",
              latitude = 37.3948,
              longitude = -122.0785,
              siteLabel = "Everest Espresso Roasters • 412 Castro St",
              distanceFromBranchMeters = 3_060.0,
              isInsideGeofence = false,
              capturedAtEpochMs = 1_763_500_000_000L,
              capturedTimeLabel = "09:10:00 AM",
              syncStatus = "SYNCED",
            )
          )
        )

        val locationManager =
          LocationManager(
            context = app,
            fusedLocationClient = LocationServices.getFusedLocationProviderClient(app),
            geofenceEvaluator = GeofenceEvaluator(),
          )
        val helper = PulseHrNotificationHelper(app)
        val scheduler =
          GeofenceReminderScheduler(
            locationManager = locationManager,
            dao = dao,
            tokenStore = tokenStore,
            notificationHelper = helper,
            workManager = WorkManager.getInstance(app),
          )

        val morningOutsideCal = Calendar.getInstance().apply { set(2026, Calendar.NOVEMBER, 21, 8, 25, 0) }
        val decision1 =
          scheduler.handleEvaluatedPerimeter(
            isInsideNow = false,
            branchName = "HQ Campus",
            employeeId = "emp_103",
            calendar = morningOutsideCal,
            userLatitude = 37.3950,
            userLongitude = -122.0786,
            distanceFromBranchMeters = 3_020.0,
          )
        assertEquals(GeofenceReminderDecision.PROACTIVE_OFFSITE_SHIFT_SUGGESTION, decision1)

        val nm = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val shadowNm = Shadows.shadowOf(nm)
        assertEquals(1, shadowNm.allNotifications.size)
        val title = shadowNm.allNotifications.first().extras.getString("android.title").orEmpty()
        assertTrue(title.contains("Primary Field Dispatch"))

        // Subsequent poll on same day shouldn't spam duplicate notification
        val decision2 =
          scheduler.handleEvaluatedPerimeter(
            isInsideNow = false,
            branchName = "HQ Campus",
            employeeId = "emp_103",
            calendar = morningOutsideCal,
            userLatitude = 37.3951,
            userLongitude = -122.0787,
            distanceFromBranchMeters = 3_010.0,
          )
        assertEquals(GeofenceReminderDecision.PROACTIVE_OFFSITE_SHIFT_SUGGESTION, decision2)
        assertEquals(1, shadowNm.allNotifications.size)
      } finally {
        db.close()
      }
    }
}
