package com.example

import com.example.domain.geofence.GeofenceEvaluator
import com.example.domain.model.BranchLocation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GeofenceEvaluatorTest {

  private val evaluator = GeofenceEvaluator()

  private val hqBranch =
    BranchLocation(
      id = "br_hq",
      name = "HQ Campus",
      code = "MV-HQ",
      address = "1600 Amphitheatre Pkwy",
      latitude = 37.4219983,
      longitude = -122.0840000,
      geofenceRadiusMeters = 200.0,
    )

  @Test
  fun `allows clock action when GPS coordinates are inside branch geofence`() {
    val result =
      evaluator.evaluate(
        branch = hqBranch,
        userLatitude = 37.4224000, // ~45 meters north of branch center
        userLongitude = -122.0840000,
        accuracyMeters = 12f,
      )

    assertTrue(result.isInsideGeofence)
    assertNull(result.failureReason)
    assertNotNull(result.distanceMeters)
    assertTrue(result.distanceMeters!! < 100.0)
  }

  @Test
  fun `blocks clock action when GPS coordinates are outside branch geofence`() {
    val result =
      evaluator.evaluate(
        branch = hqBranch,
        userLatitude = 37.4300000, // ~890 meters north of branch center (> 200m)
        userLongitude = -122.0840000,
        accuracyMeters = 10f,
      )

    assertFalse(result.isInsideGeofence)
    assertNotNull(result.failureReason)
    assertTrue(result.failureReason!!.contains("Outside"))
    assertTrue(result.distanceMeters!! > 200.0)
  }

  @Test
  fun `blocks clock action when GPS coordinates are missing`() {
    val result =
      evaluator.evaluate(
        branch = hqBranch,
        userLatitude = null,
        userLongitude = null,
        accuracyMeters = null,
      )

    assertFalse(result.isInsideGeofence)
    assertNotNull(result.failureReason)
    assertEquals(200.0, result.branchRadiusMeters, 0.01)
  }
}
