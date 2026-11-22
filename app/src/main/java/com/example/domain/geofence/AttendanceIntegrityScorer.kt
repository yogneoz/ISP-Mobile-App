package com.example.domain.geofence

import com.example.domain.model.PunchIntegrityStatus
import javax.inject.Inject
import javax.inject.Singleton

data class PunchIntegrityAssessment(
  val riskScore: Int,
  val status: PunchIntegrityStatus,
  val notes: String,
)

/**
 * Evaluates v2 attendance integrity proofs (GPS accuracy <= 50m, clock drift vs server time,
 * mock-location / root signals, offline late submission delay, and Wi-Fi BSSID context).
 * Never silently rejects a punch: suspicious punches are accepted as FLAGGED or HELD_FOR_REVIEW.
 */
@Singleton
class AttendanceIntegrityScorer @Inject constructor() {

  fun assessPunch(
    gpsAccuracyMeters: Float?,
    hasLocationFix: Boolean,
    clockDriftMs: Long,
    isMockLocationSuspected: Boolean = false,
    wifiBssid: String? = null,
    offlineDelayMinutes: Long = 0L,
  ): PunchIntegrityAssessment {
    var score = 0
    val reasons = mutableListOf<String>()

    if (!hasLocationFix) {
      score += 55
      reasons += "No GPS fix (held for supervisor review)"
    } else if (gpsAccuracyMeters == null || gpsAccuracyMeters > MAX_VERIFIED_ACCURACY_METERS) {
      score += 35
      val accLabel = gpsAccuracyMeters?.let { "${it.toInt()}m" } ?: "unknown"
      reasons += "GPS error $accLabel exceeds 50m threshold"
    }

    if (kotlin.math.abs(clockDriftMs) > MAX_CLOCK_DRIFT_MS) {
      score += 35
      reasons += "Device clock drift detected"
    }

    if (isMockLocationSuspected) {
      score += 55
      reasons += "Mock/spoof location signal"
    }

    if (offlineDelayMinutes > MAX_OFFLINE_DELAY_MINUTES) {
      score += 30
      reasons += "Offline punch arrived ${offlineDelayMinutes}m late"
    }

    if (wifiBssid.isNullOrBlank()) {
      score += 5
      reasons += "No branch Wi-Fi BSSID witness"
    }

    val boundedScore = score.coerceIn(0, 100)
    val status =
      when {
        !hasLocationFix ||
          isMockLocationSuspected ||
          offlineDelayMinutes > MAX_OFFLINE_DELAY_MINUTES ||
          boundedScore >= 60 -> PunchIntegrityStatus.HELD_FOR_REVIEW
        boundedScore >= 25 -> PunchIntegrityStatus.FLAGGED
        else -> PunchIntegrityStatus.VERIFIED
      }

    val summary =
      if (reasons.isEmpty()) "All location, BSSID, and monotonic clock proofs verified."
      else reasons.joinToString("; ")

    return PunchIntegrityAssessment(
      riskScore = boundedScore,
      status = status,
      notes = summary,
    )
  }

  companion object {
    const val MAX_VERIFIED_ACCURACY_METERS = 50f
    const val MAX_CLOCK_DRIFT_MS = 300_000L // 5 min
    const val MAX_OFFLINE_DELAY_MINUTES = 30L
  }
}
