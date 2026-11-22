package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.WorkOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.model.EmployeeShift
import com.example.domain.model.ShiftWorkMode
import com.example.location.GeofenceReminderScheduler
import com.example.location.ProactiveShiftSuggestion
import com.example.ui.theme.MonoTelemetryStyle
import com.example.ui.theme.PulseTheme
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EmployeeShiftsCard(
  shifts: List<EmployeeShift>,
  modifier: Modifier = Modifier,
  proactiveSuggestion: ProactiveShiftSuggestion? = null,
  onClockInSuggestedShift: (() -> Unit)? = null,
) {
  val semantic = PulseTheme.semantic
  var showAllUpcoming by remember { mutableStateOf(false) }

  val nowCalendar = remember(shifts) { Calendar.getInstance() }
  val todayAd = remember(shifts) {
    SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(nowCalendar.timeInMillis))
  }
  val activeShift = remember(shifts) {
    GeofenceReminderScheduler.findActiveOrJustEndedShift(shifts, nowCalendar)
  }
  val isCurrentlyInsideActiveWindow = remember(activeShift, shifts) {
    activeShift != null && GeofenceReminderScheduler.isWithinShiftWindow(activeShift, nowCalendar, 0)
  }

  val upcomingShifts = remember(shifts, activeShift, nowCalendar) {
    shifts.filter { shift ->
      shift.id != activeShift?.id &&
        GeofenceReminderScheduler.isUpcomingShift(shift, nowCalendar)
    }
  }
  val primarySpotlight = activeShift ?: upcomingShifts.firstOrNull()
  val remainingUpcoming = remember(upcomingShifts, primarySpotlight) {
    upcomingShifts.filter { it.id != primarySpotlight?.id }
  }

  Card(
    modifier = modifier.fillMaxWidth().testTag("employee_shifts_card"),
    shape = MaterialTheme.shapes.large,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
  ) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
            imageVector = Icons.Filled.CalendarMonth,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
          )
          Column {
            Text(
              text = "Active & Upcoming Shifts",
              style = MaterialTheme.typography.titleMedium,
            )
            Text(
              text = "Synced from employee roster • Multi & overnight aware",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
        StatusPill(
          text = "${shifts.size} Synced",
          containerColor = MaterialTheme.colorScheme.primaryContainer,
          contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        )
      }

      if (proactiveSuggestion != null) {
        Surface(
          shape = MaterialTheme.shapes.medium,
          color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.45f)),
          modifier = Modifier.fillMaxWidth().testTag("proactive_shift_suggestion_banner"),
        ) {
          Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f),
              ) {
                Icon(
                  imageVector = Icons.Filled.AutoAwesome,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.secondary,
                  modifier = Modifier.size(16.dp),
                )
                Text(
                  text = "PROACTIVE OFF-SITE SUGGESTION",
                  style = MaterialTheme.typography.labelMedium,
                  fontWeight = FontWeight.Bold,
                  color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
              }
              StatusPill(
                text = "${proactiveSuggestion.confidenceScore}% match",
                containerColor = semantic.verifiedContainer,
                contentColor = semantic.onVerifiedContainer,
              )
            }
            Text(
              text = "${proactiveSuggestion.shift.shiftName} (${proactiveSuggestion.shift.formattedTimeWindow()})",
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
              text = proactiveSuggestion.reasonSummary,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (onClockInSuggestedShift != null) {
              Button(
                onClick = onClockInSuggestedShift,
                modifier = Modifier.fillMaxWidth().testTag("suggested_shift_clock_in_button"),
              ) {
                Text("Clock In for ${proactiveSuggestion.shift.shiftCode}")
              }
            }
          }
        }
      }

      if (primarySpotlight == null) {
        Text(
          text = "No upcoming shifts synced from the employee database yet.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      } else {
        val isSpotlightActiveNow = primarySpotlight.id == activeShift?.id
        Surface(
          shape = MaterialTheme.shapes.medium,
          color =
            if (isSpotlightActiveNow) {
              if (isCurrentlyInsideActiveWindow) semantic.verifiedContainer.copy(alpha = 0.65f)
              else semantic.warningContainer.copy(alpha = 0.65f)
            } else {
              MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
            },
          border =
            BorderStroke(
              1.dp,
              if (isSpotlightActiveNow) {
                if (isCurrentlyInsideActiveWindow) semantic.verified.copy(alpha = 0.45f)
                else semantic.warning.copy(alpha = 0.45f)
              } else {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
              },
            ),
          modifier = Modifier.fillMaxWidth().testTag("active_shift_banner"),
        ) {
          Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            FlowRow(
              horizontalArrangement = Arrangement.spacedBy(6.dp),
              verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              val statusLabel =
                when {
                  isSpotlightActiveNow && isCurrentlyInsideActiveWindow -> "ACTIVE NOW"
                  isSpotlightActiveNow -> "POST-SHIFT GRACE"
                  else -> "NEXT SHIFT"
                }
              StatusPill(
                text = statusLabel,
                containerColor =
                  when {
                    isSpotlightActiveNow && isCurrentlyInsideActiveWindow -> semantic.verifiedContainer
                    isSpotlightActiveNow -> semantic.warningContainer
                    else -> MaterialTheme.colorScheme.primaryContainer
                  },
                contentColor =
                  when {
                    isSpotlightActiveNow && isCurrentlyInsideActiveWindow -> semantic.onVerifiedContainer
                    isSpotlightActiveNow -> semantic.onWarningContainer
                    else -> MaterialTheme.colorScheme.onPrimaryContainer
                  },
              )
              StatusPill(
                text = primarySpotlight.workMode.label,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
              )
              if (primarySpotlight.crossesMidnight) {
                StatusPill(
                  text = "Overnight",
                  containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                  contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                  icon = Icons.Filled.Bedtime,
                )
              }
              if (primarySpotlight.sequenceNo > 1) {
                StatusPill(
                  text = "Split #${primarySpotlight.sequenceNo}",
                  containerColor = MaterialTheme.colorScheme.secondaryContainer,
                  contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                )
              }
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
              Text(
                text = primarySpotlight.shiftName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
              )
              Text(
                text =
                  "${primarySpotlight.shiftDateAd} • ${primarySpotlight.formattedTimeWindow()} • ${primarySpotlight.branchCode} (${primarySpotlight.shiftCode})",
                style = MonoTelemetryStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }

            val geofencePolicyText =
              when {
                primarySpotlight.workMode == ShiftWorkMode.FIELD &&
                  !primarySpotlight.requireGeofenceCheckOut ->
                  "Entry check-in at HQ/Field • Shift-end clock-out alert if away from HQ"
                primarySpotlight.requireGeofenceCheckIn && primarySpotlight.requireGeofenceCheckOut ->
                  "Strict Office Geofence • Entry & Exit alerts active"
                else ->
                  "Hybrid dispatch policy • Off-site shift-end reminder active"
              }
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Icon(
                imageVector = Icons.Filled.LocationOn,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(14.dp),
              )
              Text(
                text = geofencePolicyText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }

        if (remainingUpcoming.isNotEmpty()) {
          HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              text = "Upcoming Roster (${remainingUpcoming.size})",
              style = MaterialTheme.typography.labelLarge,
            )
            if (remainingUpcoming.size > 3) {
              TextButton(onClick = { showAllUpcoming = !showAllUpcoming }) {
                Text(if (showAllUpcoming) "Show Fewer" else "View All")
              }
            }
          }

          val visibleUpcoming =
            if (showAllUpcoming) remainingUpcoming else remainingUpcoming.take(3)
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            visibleUpcoming.forEach { shift ->
              UpcomingShiftRow(shift = shift)
            }
          }
        }
      }
    }
  }
}

@Composable
private fun UpcomingShiftRow(shift: EmployeeShift) {
  Surface(
    shape = MaterialTheme.shapes.small,
    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
    modifier = Modifier.fillMaxWidth().testTag("upcoming_shift_item_${shift.id}"),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Icon(
            imageVector =
              if (shift.crossesMidnight) Icons.Filled.Bedtime else Icons.Filled.AccessTime,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(15.dp),
          )
          Text(
            text = shift.shiftName,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
        Text(
          text = "${shift.shiftDateAd} • ${shift.formattedTimeWindow()} • ${shift.branchCode}",
          style = MonoTelemetryStyle,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      Spacer(modifier = Modifier.width(8.dp))

      Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        StatusPill(
          text = shift.workMode.label,
          containerColor = MaterialTheme.colorScheme.secondaryContainer,
          contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
          icon = Icons.Filled.WorkOutline,
        )
        if (shift.sequenceNo > 1) {
          Text(
            text = "Split #${shift.sequenceNo}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
          )
        } else if (shift.isStandby24x7) {
          Text(
            text = "24x7 Standby",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.tertiary,
          )
        }
      }
    }
  }
}
