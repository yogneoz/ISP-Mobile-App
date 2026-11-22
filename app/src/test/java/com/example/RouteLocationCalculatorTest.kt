package com.example

import com.example.domain.geofence.RouteLocationCalculator
import com.example.domain.model.BranchLocation
import com.example.domain.model.LocationBreadcrumb
import com.example.domain.model.RouteEventType
import com.example.domain.model.SyncStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteLocationCalculatorTest {

  private val calculator = RouteLocationCalculator()

  private val branches =
    listOf(
      BranchLocation(
        id = "br_hq",
        name = "HQ Campus — Mountain View",
        code = "MV-HQ",
        address = "1600 Amphitheatre Pkwy",
        latitude = 37.4220,
        longitude = -122.0840,
        geofenceRadiusMeters = 250.0,
      ),
      BranchLocation(
        id = "br_sf",
        name = "Financial District Hub — SF",
        code = "SF-FIN",
        address = "345 California St",
        latitude = 37.7929,
        longitude = -122.4005,
        geofenceRadiusMeters = 200.0,
      ),
    )

  @Test
  fun `calculates total route distance across ordered waypoints`() {
    val base = 1_763_600_000_000L
    val points =
      listOf(
        LocationBreadcrumb(
          id = "p1",
          employeeId = "emp_103",
          sessionId = "s1",
          dateIso = "2026-11-20",
          eventType = RouteEventType.CHECK_IN,
          latitude = 37.4220,
          longitude = -122.0840,
          accuracyMeters = 10f,
          siteLabel = "HQ Campus — Mountain View",
          distanceFromBranchMeters = 0.0,
          isInsideGeofence = true,
          capturedAtEpochMs = base,
          capturedTimeLabel = "09:00:00 AM",
          syncStatus = SyncStatus.SYNCED,
        ),
        LocationBreadcrumb(
          id = "p2",
          employeeId = "emp_103",
          sessionId = "s1",
          dateIso = "2026-11-20",
          eventType = RouteEventType.WAYPOINT,
          latitude = 37.3948,
          longitude = -122.0785,
          accuracyMeters = 12f,
          siteLabel = "Castro St Fiber Hub",
          distanceFromBranchMeters = 3060.0,
          isInsideGeofence = false,
          capturedAtEpochMs = base + 1_200_000L,
          capturedTimeLabel = "09:20:00 AM",
          syncStatus = SyncStatus.SYNCED,
        ),
        LocationBreadcrumb(
          id = "p3",
          employeeId = "emp_103",
          sessionId = "s1",
          dateIso = "2026-11-20",
          eventType = RouteEventType.CHECK_OUT,
          latitude = 37.4220,
          longitude = -122.0840,
          accuracyMeters = 9f,
          siteLabel = "HQ Campus — Mountain View",
          distanceFromBranchMeters = 0.0,
          isInsideGeofence = true,
          capturedAtEpochMs = base + 2_400_000L,
          capturedTimeLabel = "09:40:00 AM",
          syncStatus = SyncStatus.SYNCED,
        ),
      )

    val totalMeters = calculator.totalRouteDistanceMeters(points)
    // Out and back (~3.06 km each leg -> ~6.12 km total)
    assertTrue("Expected ~6.1 km, got $totalMeters", totalMeters in 5_800.0..6_400.0)
  }

  @Test
  fun `resolves nearest branch name when inside geofence vs field pin when remote`() {
    val (hqLabel, hqDist) = calculator.resolveNearestSiteLabel(37.4221, -122.0841, branches)
    assertEquals("HQ Campus — Mountain View", hqLabel)
    assertTrue(hqDist < 50.0)

    val (nearLabel, _) = calculator.resolveNearestSiteLabel(37.4280, -122.0840, branches)
    assertTrue(nearLabel.startsWith("Near MV-HQ"))

    val (fieldLabel, _) = calculator.resolveNearestSiteLabel(37.5000, -122.1500, branches)
    assertTrue(fieldLabel.startsWith("Field Route"))
  }

  @Test
  fun `formats distance cleanly in meters and kilometers`() {
    assertEquals("450 m", calculator.formatDistance(450.2))
    assertEquals("3.25 km", calculator.formatDistance(3_250.0))
  }
}
