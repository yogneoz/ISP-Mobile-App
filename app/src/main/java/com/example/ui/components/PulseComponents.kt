package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.GpsOff
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.model.AuthChallengePurpose
import com.example.domain.model.BranchLocation
import com.example.domain.model.GeofenceEvaluation
import com.example.ui.theme.MonoMetricStyle
import com.example.ui.theme.MonoTelemetryStyle
import com.example.ui.theme.PulseTheme
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun StatusPill(
  text: String,
  containerColor: Color,
  contentColor: Color,
  modifier: Modifier = Modifier,
  icon: ImageVector? = null,
) {
  Surface(
    modifier = modifier,
    shape = RoundedCornerShape(999.dp),
    color = containerColor,
    contentColor = contentColor,
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      if (icon != null) {
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(13.dp))
      }
      Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

@Composable
fun MetricSummaryCard(
  title: String,
  value: String,
  subtitle: String,
  icon: ImageVector,
  accentColor: Color,
  modifier: Modifier = Modifier,
) {
  Card(
    modifier = modifier,
    shape = MaterialTheme.shapes.medium,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
  ) {
    Column(modifier = Modifier.padding(14.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = title,
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f),
        )
        Box(
          modifier =
            Modifier.size(28.dp)
              .clip(CircleShape)
              .background(accentColor.copy(alpha = 0.12f)),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            imageVector = icon,
            contentDescription = null,
            tint = accentColor,
            modifier = Modifier.size(16.dp),
          )
        }
      }
      Spacer(modifier = Modifier.height(8.dp))
      Text(
        text = value,
        style = MonoMetricStyle,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
      )
      Spacer(modifier = Modifier.height(2.dp))
      Text(
        text = subtitle,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

@Composable
fun OfflineSyncBanner(
  isOfflineSimulated: Boolean,
  pendingSyncCount: Int,
  onSyncNow: () -> Unit,
  onToggleOffline: (Boolean) -> Unit,
  modifier: Modifier = Modifier,
) {
  AnimatedVisibility(visible = isOfflineSimulated || pendingSyncCount > 0, modifier = modifier) {
    val semantic = PulseTheme.semantic
    val container = if (isOfflineSimulated) semantic.warningContainer else MaterialTheme.colorScheme.primaryContainer
    val content = if (isOfflineSimulated) semantic.onWarningContainer else MaterialTheme.colorScheme.onPrimaryContainer

    Surface(
      modifier = Modifier.fillMaxWidth(),
      shape = MaterialTheme.shapes.medium,
      color = container,
      contentColor = content,
    ) {
      Row(
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Row(
          modifier = Modifier.weight(1f),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Icon(
            imageVector = if (isOfflineSimulated) Icons.Filled.CloudOff else Icons.Filled.Sync,
            contentDescription = "Sync status",
            modifier = Modifier.size(20.dp),
          )
          Column {
            Text(
              text =
                if (isOfflineSimulated) "Offline Mode Active"
                else "$pendingSyncCount Action(s) Pending Sync",
              style = MaterialTheme.typography.labelLarge,
            )
            Text(
              text =
                if (isOfflineSimulated)
                  "Punches & leave actions saved in Room ($pendingSyncCount queued for WorkManager)."
                else "Network restored. Ready to push offline attendance & leave queues.",
              style = MaterialTheme.typography.bodySmall,
            )
          }
        }
        if (isOfflineSimulated) {
          TextButton(
            onClick = { onToggleOffline(false) },
            modifier = Modifier.testTag("go_online_button"),
          ) {
            Text("Go Online")
          }
        } else {
          TextButton(
            onClick = onSyncNow,
            modifier = Modifier.testTag("sync_now_button"),
          ) {
            Text("Sync Now")
          }
        }
      }
    }
  }
}

@Composable
fun GeofenceRadarCard(
  branch: BranchLocation,
  evaluation: GeofenceEvaluation?,
  hasPermission: Boolean,
  isLocating: Boolean,
  onRefreshLocation: () -> Unit,
  onRequestPermission: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val semantic = PulseTheme.semantic
  val isInside = evaluation?.isInsideGeofence == true
  val statusColor =
    when {
      !hasPermission || evaluation?.userLatitude == null -> semantic.warning
      isInside -> semantic.verified
      else -> semantic.danger
    }

  Card(
    modifier = modifier.fillMaxWidth(),
    shape = MaterialTheme.shapes.large,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, statusColor.copy(alpha = 0.35f)),
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "BRANCH GEOFENCE TELEMETRY",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Spacer(modifier = Modifier.height(2.dp))
          Text(
            text = branch.name,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
          )
        }
        IconButton(
          onClick = if (hasPermission) onRefreshLocation else onRequestPermission,
          modifier = Modifier.minimumInteractiveComponentSize().testTag("refresh_gps_button"),
        ) {
          Icon(
            imageVector = if (hasPermission) Icons.Filled.GpsFixed else Icons.Filled.GpsOff,
            contentDescription = "Refresh GPS fix",
            tint = MaterialTheme.colorScheme.primary,
          )
        }
      }

      Spacer(modifier = Modifier.height(12.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        // Custom Compose Canvas Geofence Radar
        Box(
          modifier = Modifier.size(104.dp),
          contentAlignment = Alignment.Center,
        ) {
          val surfaceBg = MaterialTheme.colorScheme.surfaceVariant
          val primaryClr = MaterialTheme.colorScheme.primary
          Canvas(modifier = Modifier.size(104.dp)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val maxR = min(size.width, size.height) / 2f - 4f
            val geofenceR = maxR * 0.72f

            // Outer ambient ring
            drawCircle(
              color = surfaceBg,
              radius = maxR,
              center = center,
            )

            // Geofence perimeter fill & dashed stroke
            drawCircle(
              color = statusColor.copy(alpha = 0.14f),
              radius = geofenceR,
              center = center,
            )
            drawCircle(
              color = statusColor,
              radius = geofenceR,
              center = center,
              style =
                Stroke(
                  width = 3f,
                  pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f),
                ),
            )

            // Branch center pin
            drawCircle(color = primaryClr, radius = 6f, center = center)

            // Employee relative position dot
            val dist = evaluation?.distanceMeters
            val uLat = evaluation?.userLatitude
            val uLon = evaluation?.userLongitude
            if (dist != null && uLat != null && uLon != null) {
              val ratio = (dist / branch.geofenceRadiusMeters).toFloat().coerceIn(0f, 1.32f)
              val dotR = ratio * geofenceR
              val bearing = atan2(uLon - branch.longitude, uLat - branch.latitude)
              val dx = (sin(bearing) * dotR).toFloat()
              val dy = (-cos(bearing) * dotR).toFloat()
              val dotOffset = Offset(center.x + dx, center.y + dy)
              drawLine(
                color = statusColor.copy(alpha = 0.6f),
                start = center,
                end = dotOffset,
                strokeWidth = 2f,
              )
              drawCircle(color = Color.White, radius = 8f, center = dotOffset)
              drawCircle(color = statusColor, radius = 5.5f, center = dotOffset)
            }
          }
        }

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
          val pillText =
            when {
              isLocating -> "Acquiring GPS Fix..."
              !hasPermission -> "Location Permission Required"
              evaluation?.userLatitude == null -> "GPS Fix Unavailable"
              isInside -> "Inside Perimeter (${evaluation.distanceMeters?.roundToInt()}m)"
              else -> "Outside Perimeter (${evaluation.distanceMeters?.roundToInt()}m)"
            }
          val pillBg =
            when {
              !hasPermission || evaluation?.userLatitude == null -> semantic.warningContainer
              isInside -> semantic.verifiedContainer
              else -> semantic.dangerContainer
            }
          val pillFg =
            when {
              !hasPermission || evaluation?.userLatitude == null -> semantic.onWarningContainer
              isInside -> semantic.onVerifiedContainer
              else -> semantic.onDangerContainer
            }

          StatusPill(text = pillText, containerColor = pillBg, contentColor = pillFg)

          Text(
            text = "Allowed Radius: ${branch.geofenceRadiusMeters.roundToInt()} m",
            style = MonoTelemetryStyle,
            color = MaterialTheme.colorScheme.onSurface,
          )
          Text(
            text =
              if (evaluation?.userLatitude != null && evaluation.userLongitude != null) {
                String.format(
                  Locale.US,
                  "GPS: %.5f, %.5f (±%.0fm)",
                  evaluation.userLatitude,
                  evaluation.userLongitude,
                  evaluation.gpsAccuracyMeters ?: 0f,
                )
              } else {
                "Branch: ${String.format(Locale.US, "%.5f, %.5f", branch.latitude, branch.longitude)}"
              },
            style = MonoTelemetryStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )

          if (!hasPermission) {
            OutlinedButton(
              onClick = onRequestPermission,
              modifier = Modifier.testTag("grant_location_button"),
            ) {
              Text("Grant GPS Access")
            }
          }
        }
      }
    }
  }
}

@Composable
fun PinVerificationDialog(
  purpose: AuthChallengePurpose,
  isSettingNewPin: Boolean,
  errorMessage: String?,
  canRetryBiometric: Boolean,
  onRetryBiometric: () -> Unit,
  onPinSubmitted: (String) -> Unit,
  onDismiss: () -> Unit,
) {
  var pinInput by remember { mutableStateOf("") }

  AlertDialog(
    onDismissRequest = onDismiss,
    icon = {
      Icon(
        imageVector = Icons.Filled.Lock,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
      )
    },
    title = {
      Text(
        text = if (isSettingNewPin) "Set 4-Digit Security PIN" else purpose.title,
        textAlign = TextAlign.Center,
      )
    },
    text = {
      Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Text(
          text =
            if (isSettingNewPin)
              "Create a fallback PIN for times when biometric hardware is unavailable."
            else "Enter your 4-digit PulseHR PIN to authorize this action.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(16.dp))

        // 4 PIN Dots
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
          repeat(4) { idx ->
            val filled = idx < pinInput.length
            Box(
              modifier =
                Modifier.size(18.dp)
                  .clip(CircleShape)
                  .background(
                    if (filled) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outlineVariant
                  )
            )
          }
        }

        if (!errorMessage.isNullOrBlank()) {
          Spacer(modifier = Modifier.height(10.dp))
          Text(
            text = errorMessage,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
          )
        }

        Spacer(modifier = Modifier.height(16.dp))
        NumericPinPad(
          onDigit = { digit ->
            if (pinInput.length < 4) {
              pinInput += digit
              if (pinInput.length == 4) {
                val submitted = pinInput
                pinInput = ""
                onPinSubmitted(submitted)
              }
            }
          },
          onBackspace = {
            if (pinInput.isNotEmpty()) pinInput = pinInput.dropLast(1)
          },
        )
      }
    },
    confirmButton = {
      if (canRetryBiometric && !isSettingNewPin) {
        TextButton(onClick = onRetryBiometric) {
          Icon(
            imageVector = Icons.Filled.Fingerprint,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
          )
          Spacer(modifier = Modifier.width(4.dp))
          Text("Use Biometrics")
        }
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancel") }
    },
  )
}

@Composable
fun NumericPinPad(
  onDigit: (String) -> Unit,
  onBackspace: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val rows =
    listOf(
      listOf("1", "2", "3"),
      listOf("4", "5", "6"),
      listOf("7", "8", "9"),
      listOf("", "0", "DEL"),
    )

  Column(
    modifier = modifier,
    verticalArrangement = Arrangement.spacedBy(8.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    rows.forEach { row ->
      Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        row.forEach { key ->
          when (key) {
            "" -> Spacer(modifier = Modifier.size(56.dp))
            "DEL" -> {
              Surface(
                modifier =
                  Modifier.size(56.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onBackspace)
                    .testTag("pin_backspace"),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant,
              ) {
                Box(contentAlignment = Alignment.Center) {
                  Icon(
                    imageVector = Icons.AutoMirrored.Filled.Backspace,
                    contentDescription = "Delete digit",
                    modifier = Modifier.size(20.dp),
                  )
                }
              }
            }
            else -> {
              Surface(
                modifier =
                  Modifier.size(56.dp)
                    .clip(CircleShape)
                    .clickable { onDigit(key) }
                    .testTag("pin_key_$key"),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant,
              ) {
                Box(contentAlignment = Alignment.Center) {
                  Text(
                    text = key,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                  )
                }
              }
            }
          }
        }
      }
    }
  }
}

@Composable
fun GeofenceBlockedDialog(
  branchName: String,
  evaluation: GeofenceEvaluation?,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    icon = {
      Icon(
        imageVector = Icons.Filled.GpsOff,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.error,
        modifier = Modifier.size(32.dp),
      )
    },
    title = { Text("Clock-In / Out Blocked") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
          text =
            evaluation?.failureReason
              ?: "Your current GPS coordinates fall outside the geofence boundary for $branchName.",
          style = MaterialTheme.typography.bodyMedium,
        )
        if (evaluation?.distanceMeters != null) {
          Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.errorContainer,
          ) {
            Column(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
              Text(
                text = "Distance: ${evaluation.distanceMeters.roundToInt()} m",
                style = MonoTelemetryStyle,
                color = MaterialTheme.colorScheme.onErrorContainer,
              )
              Text(
                text = "Allowed Radius: ${evaluation.branchRadiusMeters.roundToInt()} m",
                style = MonoTelemetryStyle,
                color = MaterialTheme.colorScheme.onErrorContainer,
              )
            }
          }
        }
      }
    },
    confirmButton = {
      Button(onClick = onDismiss, modifier = Modifier.testTag("geofence_blocked_ok")) {
        Text("Understood")
      }
    },
  )
}
