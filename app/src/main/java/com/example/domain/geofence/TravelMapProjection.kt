package com.example.domain.geofence

import com.example.domain.model.BranchLocation
import com.example.domain.model.LocationBreadcrumb
import com.example.domain.model.WorkOrder
import com.example.domain.model.WorkOrderStatus
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.tan

data class ClientVisitPin(
  val workOrderId: String,
  val ticketCode: String,
  val customerName: String,
  val address: String,
  val taskType: String,
  val status: WorkOrderStatus,
  val latitude: Double,
  val longitude: Double,
  val visited: Boolean,
  val visitedAtLabel: String?,
  val matchedWaypointId: String?,
)

data class MercatorBounds(
  val minX: Double,
  val maxX: Double,
  val minY: Double,
  val maxY: Double,
  val centerLat: Double,
  val centerLon: Double,
)

@Singleton
class TravelMapProjection @Inject constructor(
  private val routeCalculator: RouteLocationCalculator,
) {

  fun latToMercatorY(lat: Double): Double {
    val clamped = lat.coerceIn(-85.0, 85.0)
    val rad = Math.toRadians(clamped)
    return (1.0 - ln(tan(rad) + 1.0 / cos(rad)) / PI) / 2.0
  }

  fun lonToMercatorX(lon: Double): Double = (lon + 180.0) / 360.0

  fun computeBounds(
    waypoints: List<LocationBreadcrumb>,
    clientPins: List<ClientVisitPin>,
    branches: List<BranchLocation>,
  ): MercatorBounds {
    val allCoords =
      buildList {
        waypoints.forEach { add(it.latitude to it.longitude) }
        clientPins.forEach { add(it.latitude to it.longitude) }
        if (waypoints.isEmpty() && clientPins.isEmpty()) {
          branches.forEach { add(it.latitude to it.longitude) }
        } else {
          branches.firstOrNull()?.let { add(it.latitude to it.longitude) }
        }
      }.ifEmpty {
        listOf(37.4220 to -122.0840, 37.3861 to -122.0839)
      }

    val minLat = allCoords.minOf { it.first }
    val maxLat = allCoords.maxOf { it.first }
    val minLon = allCoords.minOf { it.second }
    val maxLon = allCoords.maxOf { it.second }

    val centerLat = (minLat + maxLat) / 2.0
    val centerLon = (minLon + maxLon) / 2.0

    val rawMinX = lonToMercatorX(minLon)
    val rawMaxX = lonToMercatorX(maxLon)
    val rawMinY = latToMercatorY(maxLat) // North is smaller Y
    val rawMaxY = latToMercatorY(minLat)

    val minSpan = 0.00014 // roughly ~1.5km window minimum so zoom looks balanced
    val spanX = max(rawMaxX - rawMinX, minSpan)
    val spanY = max(rawMaxY - rawMinY, minSpan)
    val uniformSpan = max(spanX, spanY) * 1.28

    val midX = (rawMinX + rawMaxX) / 2.0
    val midY = (rawMinY + rawMaxY) / 2.0

    return MercatorBounds(
      minX = midX - uniformSpan / 2.0,
      maxX = midX + uniformSpan / 2.0,
      minY = midY - uniformSpan / 2.0,
      maxY = midY + uniformSpan / 2.0,
      centerLat = centerLat,
      centerLon = centerLon,
    )
  }

  /**
   * Normalizes a latitude/longitude coordinate into [0..1] viewport space inside [bounds].
   */
  fun projectNormalized(
    lat: Double,
    lon: Double,
    bounds: MercatorBounds,
  ): Pair<Float, Float> {
    val spanX = (bounds.maxX - bounds.minX).coerceAtLeast(1e-9)
    val spanY = (bounds.maxY - bounds.minY).coerceAtLeast(1e-9)
    val nx = ((lonToMercatorX(lon) - bounds.minX) / spanX).toFloat()
    val ny = ((latToMercatorY(lat) - bounds.minY) / spanY).toFloat()
    return nx to ny
  }

  /**
   * Computes approximate geographic width in meters across [bounds] at its center latitude.
   */
  fun boundsWidthMeters(bounds: MercatorBounds): Double {
    val earthCircumference = 40_075_016.7 * cos(Math.toRadians(bounds.centerLat))
    return (bounds.maxX - bounds.minX) * earthCircumference
  }

  /**
   * Pairs enterprise WorkOrders (client jobs) with realistic corridor coordinates and checks which
   * clients have a nearby employee LocationBreadcrumb recorded during the selected travel window.
   */
  fun buildClientVisitPins(
    workOrders: List<WorkOrder>,
    waypoints: List<LocationBreadcrumb>,
  ): List<ClientVisitPin> {
    return workOrders.mapIndexed { idx, order ->
      val (lat, lon) = resolveCoordinatesForWorkOrder(order, idx)
      val matchedWaypoint =
        waypoints.firstOrNull { wp ->
          routeCalculator.haversineMeters(wp.latitude, wp.longitude, lat, lon) <= 380.0
        }
      ClientVisitPin(
        workOrderId = order.id,
        ticketCode = order.ticketCode,
        customerName = order.customerName,
        address = order.address,
        taskType = order.taskType,
        status = order.status,
        latitude = lat,
        longitude = lon,
        visited = matchedWaypoint != null || order.status == WorkOrderStatus.CLOSED || order.status == WorkOrderStatus.COMPLETED_LOCAL,
        visitedAtLabel = matchedWaypoint?.capturedTimeLabel,
        matchedWaypointId = matchedWaypoint?.id,
      )
    }
  }

  private fun resolveCoordinatesForWorkOrder(order: WorkOrder, index: Int): Pair<Double, Double> {
    val addr = order.address.lowercase()
    return when {
      "castro" in addr || order.ticketCode == "FTTH-4821" -> 37.3948 to -122.0785
      "el camino" in addr || order.ticketCode == "LOS-4809" -> 37.3861 to -122.0839
      "shoreline" in addr || order.ticketCode == "UPG-4794" -> 37.4046 to -122.0789
      else -> {
        val latOffset = ((index % 3) - 1) * 0.011
        val lonOffset = ((index / 2) - 0.5) * 0.012
        (37.402 + latOffset) to (-122.081 + lonOffset)
      }
    }
  }
}
