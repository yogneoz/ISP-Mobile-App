package com.example

import com.example.domain.geofence.GeofenceEvaluator
import com.example.domain.model.BranchLocation
import com.example.voice.VoiceAuthVerifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceAuthVerifierTest {

  private val hqBranch =
    BranchLocation(
      id = "br_hq",
      name = "HQ Campus",
      code = "MV-HQ",
      address = "1600 Amphitheatre Pkwy",
      latitude = 37.4219983,
      longitude = -122.0840,
      geofenceRadiusMeters = 250.0,
    )

  private val geofenceEvaluator = GeofenceEvaluator()

  @Test
  fun `authenticates exact and minor punctuation variations of default phrase inside geofence`() {
    val inside = geofenceEvaluator.evaluate(hqBranch, 37.4221, -122.0841, 8f)
    assertTrue(inside.isInsideGeofence)

    val matchPunctuated =
      VoiceAuthVerifier.verifySpokenCandidates(
        candidates = listOf("Pulse, check-in!"),
        expectedPhrase = "Pulse check in",
      )
    assertTrue(matchPunctuated.isAuthenticated)
    assertEquals("Pulse, check-in!", matchPunctuated.matchedCandidate)

    val matchSecondHypothesis =
      VoiceAuthVerifier.verifySpokenCandidates(
        candidates = listOf("pause check in", "pulse checkin"),
        expectedPhrase = "Pulse check in",
      )
    assertTrue(matchSecondHypothesis.isAuthenticated)
    assertEquals("pulse checkin", matchSecondHypothesis.matchedCandidate)
  }

  @Test
  fun `rejects incomplete or unrelated spoken phrases even when candidates exist`() {
    val res1 =
      VoiceAuthVerifier.verifySpokenCandidates(
        candidates = listOf("check in", "clock in now", "pulse"),
        expectedPhrase = "Pulse check in",
      )
    assertFalse(res1.isAuthenticated)

    val empty =
      VoiceAuthVerifier.verifySpokenCandidates(
        candidates = emptyList(),
        expectedPhrase = "Pulse check in",
      )
    assertFalse(empty.isAuthenticated)
  }

  @Test
  fun `supports custom user passphrase and blocks voice check-in outside geofence`() {
    val customMatch =
      VoiceAuthVerifier.verifySpokenCandidates(
        candidates = listOf("alpha verify attendance"),
        expectedPhrase = "Alpha verify attendance",
      )
    assertTrue(customMatch.isAuthenticated)

    // 1.5 km away from HQ branch -> Geofence blocks check-in
    val outside = geofenceEvaluator.evaluate(hqBranch, 37.4355, -122.0840, 10f)
    assertFalse(outside.isInsideGeofence)
  }
}
