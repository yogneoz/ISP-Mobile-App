package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.model.EmployeeRouteSession
import com.example.domain.model.LocationBreadcrumb
import com.example.domain.model.RouteEventType
import com.example.domain.model.SyncStatus
import com.example.ui.theme.MonoTelemetryStyle
import com.example.ui.theme.PulseTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EmployeeRouteTrackerCard(
  isCheckedIn: Boolean,
  hasLocationPermission: Boolean,
  isBusy: Boolean,
  routeSession: EmployeeRouteSession?,
  lastSyncEpochMs: Long?,
  formatDistance: (Double) -> String,
  onToggleCheckInOut: () -> Unit,
  onPushLocationNow: () -> Unit,
  onSyncDatabaseNow: () -> Unit,
  onRequestLocationPermission: () -> Unit,
  onOpenTravelMap: () -> Unit = {},
  modifier: Modifier = Modifier,
) {
  val semantic = PulseTheme.semantic
  var showAllStops by remember { mutableStateOf(false) }

  Card(
    modifier = modifier.fillMaxWidth().testTag("employee_route_tracker_card"),
    shape = MaterialTheme.shapes.large,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
  ) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      // Header + Active Status Pill
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier.weight(1f),
        ) {
          Icon(
            imageVector = Icons.Filled.Route,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
          )
          Column {
            Text(
              text = "Field Check-In & Visited Route",
              style = MaterialTheme.typography.titleMedium,
            )
            Text(
              text =
                if (isCheckedIn) {
                  "Live GPS tracking + periodic DB sync active"
                } else {
                  "Check in to record & sync visited route stops"
                },
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
        StatusPill(
          text = if (isCheckedIn) "CHECKED IN" else "CHECKED OUT",
          containerColor =
            if (isCheckedIn) semantic.verifiedContainer
            else MaterialTheme.colorScheme.surfaceVariant,
          contentColor =
            if (isCheckedIn) semantic.onVerifiedContainer
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      // Prominent Check-In / Check-Out Toggle + Quick Location Actions
      if (!hasLocationPermission) {
        OutlinedButton(
          onClick = onRequestLocationPermission,
          modifier = Modifier.fillMaxWidth().height(50.dp).testTag("route_grant_location_button"),
        ) {
          Icon(Icons.Filled.GpsFixed, contentDescription = null)
          Spacer(modifier = Modifier.width(8.dp))
          Text("Grant GPS Permission for Route Check-In")
        }
      } else {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Button(
            onClick = onToggleCheckInOut,
            enabled = !isBusy,
            colors =
              ButtonDefaults.buttonColors(
                containerColor =
                  if (isCheckedIn) semantic.warning else MaterialTheme.colorScheme.primary
              ),
            modifier =
              Modifier.fillMaxWidth().height(52.dp).testTag("route_checkin_checkout_button"),
          ) {
            if (isBusy) {
              CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary,
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text("Acquiring GPS & Syncing...")
            } else if (isCheckedIn) {
              Icon(
                imageVector = Icons.AutoMirrored.Filled.Logout,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text("Check Out & Complete Route")
            } else {
              Icon(
                imageVector = Icons.AutoMirrored.Filled.Login,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text("Check In & Start Route Tracking")
            }
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            OutlinedButton(
              onClick = onPushLocationNow,
              enabled = !isBusy,
              modifier = Modifier.weight(1f).height(48.dp).testTag("push_location_now_button"),
            ) {
              Icon(
                imageVector = Icons.Filled.AddLocationAlt,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text("Push Location", maxLines = 1, overflow = TextOverflow.Ellipsis)
            }

            OutlinedButton(
              onClick = onSyncDatabaseNow,
              enabled = !isBusy,
              modifier = Modifier.weight(1f).height(48.dp).testTag("sync_route_db_button"),
            ) {
              Icon(
                imageVector = Icons.Filled.Sync,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text("Sync DB Now", maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
          }
        }
      }

      // Periodic Sync Cadence & Telemetry Summary
      val lastSyncLabel =
        lastSyncEpochMs?.let {
          SimpleDateFormat("hh:mm:ss a", Locale.US).format(Date(it))
        } ?: "Pending initial sync"

      Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(
          modifier = Modifier.padding(10.dp),
          verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Icon(
                imageVector = Icons.Filled.Schedule,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.primary,
              )
              Text(
                text = "Auto Sync: 60s foreground • 15m WorkManager",
                style = MaterialTheme.typography.labelSmall,
              )
            }
            if (routeSession != null) {
              val pending = routeSession.pendingPointsCount
              StatusPill(
                text = if (pending == 0) "DB Synced" else "$pending Queued",
                containerColor =
                  if (pending == 0) semantic.verifiedContainer else semantic.warningContainer,
                contentColor =
                  if (pending == 0) semantic.onVerifiedContainer else semantic.onWarningContainer,
              )
            }
          }
          Text(
            text = "Last DB Push: $lastSyncLabel",
            style = MonoTelemetryStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      // Visited Route Vector Map + Chronological Breadcrumb Timeline
      if (routeSession != null && routeSession.waypoints.isNotEmpty()) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        FlowRow(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          Text(
            text = "Route • ${routeSession.dateIso} (${routeSession.waypoints.size} stops)",
            style = MaterialTheme.typography.titleSmall,
          )
          Text(
            text = "Distance: ${formatDistance(routeSession.totalDistanceMeters)}",
            style = MonoTelemetryStyle,
            color = MaterialTheme.colorScheme.primary,
          )
        }

        RoutePathCanvas(
          waypoints = routeSession.waypoints,
          modifier = Modifier.fillMaxWidth().height(152.dp),
        )

        val visibleStops =
          if (showAllStops || routeSession.waypoints.size <= 4) {
            routeSession.waypoints
          } else {
            routeSession.waypoints.takeLast(4)
          }

        if (routeSession.waypoints.size > 4) {
          TextButton(
            onClick = { showAllStops = !showAllStops },
            modifier = Modifier.align(Alignment.End),
          ) {
            Text(
              if (showAllStops) "Show Recent 4 Stops"
              else "View All ${routeSession.waypoints.size} Route Stops"
            )
          }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          val offset = routeSession.waypoints.size - visibleStops.size
          visibleStops.forEachIndexed { idx, point ->
            RouteWaypointRow(
              stepNumber = offset + idx + 1,
              point = point,
            )
          }
        }
      } else {
        Text(
          text = "No route breadcrumbs recorded yet. Tap Check In to start recording your visited route.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      FilledTonalButton(
        onClick = onOpenTravelMap,
        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("open_travel_route_map_button"),
      ) {
        Icon(
          imageVector = Icons.Filled.Map,
          contentDescription = null,
          modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text("Open Interactive Travel & Client Map")
      }
    }
  }
}

@Composable
private fun RoutePathCanvas(
  waypoints: List<LocationBreadcrumb>,
  modifier: Modifier = Modifier,
) {
  val semantic = PulseTheme.semantic
  val primaryColor = MaterialTheme.colorScheme.primary
  val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
  val bgColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
  val checkInColor = semantic.verified
  val checkOutColor = semantic.warning
  val dotBorderColor = MaterialTheme.colorScheme.surface

  Surface(
    shape = MaterialTheme.shapes.medium,
    color = bgColor,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    modifier = modifier.testTag("employee_route_canvas"),
  ) {
    Canvas(modifier = Modifier.fillMaxSize().padding(18.dp)) {
      // Subtle coordinate grid
      val gridSteps = 4
      for (i in 0..gridSteps) {
        val x = size.width * (i / gridSteps.toFloat())
        val y = size.height * (i / gridSteps.toFloat())
        drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.dp.toPx())
        drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
      }

      if (waypoints.isEmpty()) return@Canvas

      val minLat = waypoints.minOf { it.latitude }
      val maxLat = waypoints.maxOf { it.latitude }
      val minLon = waypoints.minOf { it.longitude }
      val maxLon = waypoints.maxOf { it.longitude }
      val latSpan = (maxLat - minLat).coerceAtLeast(0.0006)
      val lonSpan = (maxLon - minLon).coerceAtLeast(0.0006)

      val offsets =
        waypoints.mapIndexed { index, pt ->
          val nx =
            if (maxLon == minLon) {
              if (waypoints.size == 1) 0.5f
              else 0.12f + 0.76f * (index.toFloat() / (waypoints.size - 1).coerceAtLeast(1))
            } else {
              ((pt.longitude - minLon) / lonSpan).toFloat().coerceIn(0.06f, 0.94f)
            }
          val ny =
            if (maxLat == minLat) {
              0.5f
            } else {
              (1f - ((pt.latitude - minLat) / latSpan).toFloat()).coerceIn(0.08f, 0.92f)
            }
          Offset(x = nx * size.width, y = ny * size.height)
        }

      if (offsets.size > 1) {
        val path = Path().apply {
          moveTo(offsets.first().x, offsets.first().y)
          offsets.drop(1).forEach { lineTo(it.x, it.y) }
        }
        drawPath(
          path = path,
          color = primaryColor,
          style =
            Stroke(
              width = 3.5.dp.toPx(),
              cap = StrokeCap.Round,
              pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f),
            ),
        )
      }

      offsets.forEachIndexed { index, center ->
        val event = waypoints[index].eventType
        val pinColor =
          when (event) {
            RouteEventType.CHECK_IN -> checkInColor
            RouteEventType.CHECK_OUT -> checkOutColor
            RouteEventType.WAYPOINT, RouteEventType.MANUAL_PUSH -> primaryColor
          }
        drawCircle(color = dotBorderColor, radius = 9.dp.toPx(), center = center)
        drawCircle(color = pinColor, radius = 6.5.dp.toPx(), center = center)
      }
    }
  }
}

@Composable
private fun RouteWaypointRow(
  stepNumber: Int,
  point: LocationBreadcrumb,
) {
  val semantic = PulseTheme.semantic
  val accent =
    when (point.eventType) {
      RouteEventType.CHECK_IN -> semantic.verified
      RouteEventType.CHECK_OUT -> semantic.warning
      RouteEventType.WAYPOINT, RouteEventType.MANUAL_PUSH -> MaterialTheme.colorScheme.primary
    }

  Row(
    modifier = Modifier.fillMaxWidth().testTag("route_waypoint_row_$stepNumber"),
    verticalAlignment = Alignment.Top,
    horizontalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    Box(
      modifier = Modifier.size(26.dp).clip(CircleShape).background(accent),
      contentAlignment = Alignment.Center,
    ) {
      Text(
        text = stepNumber.toString(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.surface,
      )
    }

    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = point.siteLabel,
          style = MaterialTheme.typography.labelLarge,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          Icon(
            imageVector =
              if (point.syncStatus == SyncStatus.SYNCED) Icons.Filled.CloudDone
              else Icons.Filled.Sync,
            contentDescription = point.syncStatus.label,
            modifier = Modifier.size(14.dp),
            tint =
              if (point.syncStatus == SyncStatus.SYNCED) semantic.verified else semantic.warning,
          )
          Text(
            text = point.eventType.label,
            style = MaterialTheme.typography.labelSmall,
            color = accent,
          )
        }
      }

      Text(
        text =
          String.format(
            Locale.US,
            "%s • %.5f, %.5f%s",
            point.capturedTimeLabel,
            point.latitude,
            point.longitude,
            point.accuracyMeters?.let { " (±${it.toInt()}m)" } ?: "",
          ),
        style = MonoTelemetryStyle,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}
