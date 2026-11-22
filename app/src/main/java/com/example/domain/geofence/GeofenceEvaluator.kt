package com.example.domain.geofence

import com.example.domain.model.BranchLocation
import com.example.domain.model.GeofenceEvaluation
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

@Singleton
class GeofenceEvaluator @Inject constructor() {

  fun evaluate(
    branch: BranchLocation,
    userLatitude: Double?,
    userLongitude: Double?,
    accuracyMeters: Float? = null,
  ): GeofenceEvaluation {
    if (userLatitude == null || userLongitude == null) {
      return GeofenceEvaluation(
        isInsideGeofence = false,
        distanceMeters = null,
        branchRadiusMeters = branch.geofenceRadiusMeters,
        branchName = branch.name,
        userLatitude = null,
        userLongitude = null,
        gpsAccuracyMeters = accuracyMeters,
        failureReason =
          "GPS coordinates unavailable. Enable location services and acquire a GPS fix before clocking in or out.",
      )
    }

    val distance =
      haversineDistanceMeters(
        lat1 = userLatitude,
        lon1 = userLongitude,
        lat2 = branch.latitude,
        lon2 = branch.longitude,
      )

    val inside = distance <= branch.geofenceRadiusMeters
    val roundedDistance = distance.roundToInt()
    val roundedRadius = branch.geofenceRadiusMeters.roundToInt()

    val reason =
      if (inside) {
        null
      } else {
        "Outside ${branch.name} geofence (${roundedDistance}m from branch center; allowed radius is ${roundedRadius}m). Move inside the branch perimeter to clock in or out."
      }

    return GeofenceEvaluation(
      isInsideGeofence = inside,
      distanceMeters = distance,
      branchRadiusMeters = branch.geofenceRadiusMeters,
      branchName = branch.name,
      userLatitude = userLatitude,
      userLongitude = userLongitude,
      gpsAccuracyMeters = accuracyMeters,
      failureReason = reason,
    )
  }

  /**
   * Computes great-circle distance in meters using the WGS-84 Earth radius (6,371,000 m).
   * Pure Kotlin implementation allows deterministic JVM unit testing without Android Location mocks.
   */
  fun haversineDistanceMeters(
    lat1: Double,
    lon1: Double,
    lat2: Double,
    lon2: Double,
  ): Double {
    val earthRadiusMeters = 6_371_000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a =
      sin(dLat / 2) * sin(dLat / 2) +
        cos(Math.toRadians(lat1)) *
          cos(Math.toRadians(lat2)) *
          sin(dLon / 2) *
          sin(dLon / 2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return earthRadiusMeters * c
  }
}
