package com.example.domain.geofence

import com.example.domain.model.BranchLocation
import com.example.domain.model.LocationBreadcrumb
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Pure geographic helper for calculating breadcrumb segment distances, total route length,
 * and nearest known enterprise site/branch label along an employee's visited route.
 */
@Singleton
class RouteLocationCalculator @Inject constructor() {

  fun haversineMeters(
    lat1: Double,
    lon1: Double,
    lat2: Double,
    lon2: Double,
  ): Double {
    val earthRadiusMeters = 6_371_000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a =
      sin(dLat / 2).pow(2) +
        cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return earthRadiusMeters * c
  }

  fun totalRouteDistanceMeters(waypoints: List<LocationBreadcrumb>): Double {
    if (waypoints.size < 2) return 0.0
    val sorted = waypoints.sortedBy { it.capturedAtEpochMs }
    var total = 0.0
    for (i in 1 until sorted.size) {
      val prev = sorted[i - 1]
      val curr = sorted[i]
      total += haversineMeters(prev.latitude, prev.longitude, curr.latitude, curr.longitude)
    }
    return total
  }

  fun resolveNearestSiteLabel(
    latitude: Double,
    longitude: Double,
    branches: List<BranchLocation>,
  ): Pair<String, Double> {
    if (branches.isEmpty()) {
      return String.format(Locale.US, "Field Pin (%.4f, %.4f)", latitude, longitude) to 0.0
    }
    val nearest =
      branches.minByOrNull { b -> haversineMeters(latitude, longitude, b.latitude, b.longitude) }!!
    val dist = haversineMeters(latitude, longitude, nearest.latitude, nearest.longitude)
    val label =
      when {
        dist <= nearest.geofenceRadiusMeters -> nearest.name
        dist <= 1_500.0 -> "Near ${nearest.code} (${dist.toInt()}m away)"
        else -> String.format(Locale.US, "Field Route • %.4f, %.4f", latitude, longitude)
      }
    return label to dist
  }

  fun formatDistance(meters: Double): String {
    return if (meters >= 1_000.0) {
      String.format(Locale.US, "%.2f km", meters / 1_000.0)
    } else {
      "${meters.toInt()} m"
    }
  }
}
