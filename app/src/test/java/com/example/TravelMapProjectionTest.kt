package com.example

import com.example.domain.geofence.RouteLocationCalculator
import com.example.domain.geofence.TravelMapProjection
import com.example.domain.model.BranchLocation
import com.example.domain.model.LocationBreadcrumb
import com.example.domain.model.RouteEventType
import com.example.domain.model.SyncStatus
import com.example.domain.model.WorkOrder
import com.example.domain.model.WorkOrderStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TravelMapProjectionTest {

  private val calculator = RouteLocationCalculator()
  private val projection = TravelMapProjection(calculator)

  private val hqBranch =
    BranchLocation(
      id = "br_hq",
      name = "HQ Campus — Mountain View",
      code = "MV-HQ",
      address = "1600 Amphitheatre Pkwy",
      latitude = 37.4220,
      longitude = -122.0840,
      geofenceRadiusMeters = 250.0,
    )

  private val workOrders =
    listOf(
      WorkOrder(
        id = "wo_1",
        ticketCode = "FTTH-4821",
        customerName = "Apex Fiber Medical",
        address = "412 Castro St, Mountain View",
        taskType = "FTTH Activation",
        slaDeadlineLabel = "14:00",
        slaMinutesRemaining = 45,
        status = WorkOrderStatus.ASSIGNED,
        assignedEmployeeId = "emp_103",
        assignedEmployeeName = "Aarav Sharma",
        branchCode = "MV-HQ",
      ),
      WorkOrder(
        id = "wo_2",
        ticketCode = "LOS-4809",
        customerName = "Sierra Optical Lab",
        address = "890 El Camino Real",
        taskType = "Optical Restore",
        slaDeadlineLabel = "16:30",
        slaMinutesRemaining = 110,
        status = WorkOrderStatus.EN_ROUTE,
        assignedEmployeeId = "emp_103",
        assignedEmployeeName = "Aarav Sharma",
        branchCode = "MV-HQ",
      ),
      WorkOrder(
        id = "wo_3",
        ticketCode = "UPG-4794",
        customerName = "Bayview Logistics",
        address = "218 Shoreline Blvd",
        taskType = "ONT Swap",
        slaDeadlineLabel = "11:00",
        slaMinutesRemaining = 0,
        status = WorkOrderStatus.CLOSED,
        assignedEmployeeId = "emp_103",
        assignedEmployeeName = "Aarav Sharma",
        branchCode = "MV-HQ",
      ),
    )

  private fun breadcrumb(
    id: String,
    lat: Double,
    lon: Double,
    timeLabel: String,
    eventType: RouteEventType = RouteEventType.WAYPOINT,
  ): LocationBreadcrumb {
    return LocationBreadcrumb(
      id = id,
      employeeId = "emp_103",
      sessionId = "sess_1",
      dateIso = "2026-11-19",
      eventType = eventType,
      latitude = lat,
      longitude = lon,
      accuracyMeters = 8f,
      siteLabel = "Stop $id",
      distanceFromBranchMeters = 100.0,
      isInsideGeofence = false,
      capturedAtEpochMs = 1_763_500_000_000L,
      capturedTimeLabel = timeLabel,
      syncStatus = SyncStatus.SYNCED,
    )
  }

  @Test
  fun `buildClientVisitPins matches nearby breadcrumbs within 380m and preserves closed state`() {
    val waypoints =
      listOf(
        breadcrumb("wp1", 37.4220, -122.0840, "08:55 AM", RouteEventType.CHECK_IN),
        // Within ~100m of Castro St (37.3948, -122.0785)
        breadcrumb("wp2", 37.3951, -122.0789, "10:15 AM"),
      )

    val pins = projection.buildClientVisitPins(workOrders, waypoints)
    assertEquals(3, pins.size)

    val castroPin = pins.first { it.ticketCode == "FTTH-4821" }
    assertTrue("Expected Castro St client to be visited via nearby breadcrumb", castroPin.visited)
    assertEquals("10:15 AM", castroPin.visitedAtLabel)
    assertEquals("wp2", castroPin.matchedWaypointId)

    val elCaminoPin = pins.first { it.ticketCode == "LOS-4809" }
    assertFalse("Expected unvisited El Camino client to remain pending", elCaminoPin.visited)
    assertNull(elCaminoPin.visitedAtLabel)
    assertNull(elCaminoPin.matchedWaypointId)

    val shorelinePin = pins.first { it.ticketCode == "UPG-4794" }
    assertTrue("Closed work order should mark pin as visited even without nearby breadcrumb", shorelinePin.visited)
    assertNull(shorelinePin.matchedWaypointId)
  }

  @Test
  fun `computeBounds and projectNormalized preserve North-South and East-West ordering`() {
    val northWestPoint = breadcrumb("nw", 37.4220, -122.0840, "08:50 AM", RouteEventType.CHECK_IN)
    val southEastPoint = breadcrumb("se", 37.3861, -122.0780, "02:30 PM", RouteEventType.CHECK_OUT)
    val waypoints = listOf(northWestPoint, southEastPoint)
    val clientPins = projection.buildClientVisitPins(workOrders, waypoints)

    val bounds = projection.computeBounds(waypoints, clientPins, listOf(hqBranch))
    val (nwX, nwY) = projection.projectNormalized(northWestPoint.latitude, northWestPoint.longitude, bounds)
    val (seX, seY) = projection.projectNormalized(southEastPoint.latitude, southEastPoint.longitude, bounds)

    assertTrue("Western coordinate should have smaller normalized X ($nwX vs $seX)", nwX < seX)
    assertTrue("Northern coordinate should have smaller normalized Y ($nwY vs $seY)", nwY < seY)
    assertTrue("Normalized coords must lie strictly within padding inside [0..1]", nwX in 0.05f..0.95f)
    assertTrue("Normalized coords must lie strictly within padding inside [0..1]", seY in 0.05f..0.95f)

    val spanMeters = projection.boundsWidthMeters(bounds)
    assertNotNull(spanMeters)
    assertTrue("Expected multi-kilometer map bounds span, got $spanMeters", spanMeters in 2_000.0..15_000.0)
  }
}
