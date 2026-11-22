package com.example

import com.example.domain.geofence.AttendanceIntegrityScorer
import com.example.domain.model.AppCapability
import com.example.domain.model.ApprovalItem
import com.example.domain.model.PunchIntegrityStatus
import com.example.domain.model.RoleBundle
import com.example.domain.model.UserRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkplaceV2ArchitectureTest {

  private val scorer = AttendanceIntegrityScorer()

  @Test
  fun `composable role bundles union capabilities accurately`() {
    val techCaps = RoleBundle.capabilitiesForBundles(listOf(RoleBundle.FIELD_TECHNICIAN))
    assertTrue(AppCapability.JOBS_EXECUTE in techCaps)
    assertTrue(AppCapability.STOCK_VAN in techCaps)
    assertFalse(AppCapability.DISPATCH_ASSIGN in techCaps)

    val hybridCaps =
      RoleBundle.capabilitiesForBundles(
        listOf(RoleBundle.FIELD_TECHNICIAN, RoleBundle.DISPATCH_COORDINATOR)
      )
    assertTrue(AppCapability.JOBS_EXECUTE in hybridCaps)
    assertTrue(AppCapability.DISPATCH_ASSIGN in hybridCaps)
    assertTrue(AppCapability.AUDIT_STOCK in hybridCaps)
    assertEquals(
      UserRole.HR_OFFICER,
      RoleBundle.highestRoleForBundles(
        listOf(RoleBundle.FIELD_TECHNICIAN, RoleBundle.DISPATCH_COORDINATOR)
      ),
    )
  }

  @Test
  fun `no self-approval rule blocks managers from approving own requests`() {
    val ownClaim =
      ApprovalItem(
        id = "apr_self",
        itemType = "EXPENSE_CLAIM",
        requesterId = "emp_101",
        requesterName = "Aarav Adhikari",
        summary = "Emergency Fuel",
        detail = "Zone A restoration",
        metricLabel = "$45.00",
        submittedAt = "2026-11-14",
        status = "PENDING",
      )
    val peerClaim = ownClaim.copy(id = "apr_peer", requesterId = "emp_103", requesterName = "Maya Shrestha")
    val directorCaps = RoleBundle.HR_MANAGER_BUNDLE.capabilities

    assertTrue(ownClaim.isSelfSubmitted("emp_101"))
    assertFalse(ownClaim.canBeActedOnBy("emp_101", directorCaps))
    assertTrue(peerClaim.canBeActedOnBy("emp_101", directorCaps))
  }

  @Test
  fun `attendance integrity scorer flags poor gps or clock drift without silent rejection`() {
    val clean =
      scorer.assessPunch(
        gpsAccuracyMeters = 12f,
        hasLocationFix = true,
        clockDriftMs = 1_000L,
        isMockLocationSuspected = false,
        wifiBssid = "AA:BB:CC:11:22:33",
      )
    assertEquals(PunchIntegrityStatus.VERIFIED, clean.status)
    assertEquals(0, clean.riskScore)

    val highAccuracyError =
      scorer.assessPunch(
        gpsAccuracyMeters = 95f,
        hasLocationFix = true,
        clockDriftMs = 2_000L,
        wifiBssid = "AA:BB:CC:11:22:33",
      )
    assertEquals(PunchIntegrityStatus.FLAGGED, highAccuracyError.status)
    assertTrue(highAccuracyError.riskScore >= 25)
    assertTrue(highAccuracyError.notes.contains("exceeds 50m"))

    val noFixAndDrift =
      scorer.assessPunch(
        gpsAccuracyMeters = null,
        hasLocationFix = false,
        clockDriftMs = 600_000L,
        wifiBssid = null,
      )
    assertEquals(PunchIntegrityStatus.HELD_FOR_REVIEW, noFixAndDrift.status)
    assertTrue(noFixAndDrift.riskScore >= 60)
  }
}
