package com.example.ui.screens.attendance

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.model.AttendanceRecord
import com.example.domain.model.AttendanceStatus
import com.example.domain.model.AuthChallengePurpose
import com.example.domain.model.SyncStatus
import com.example.domain.model.UserSession
import com.example.domain.model.WorkHoursScheduleConfig
import com.example.ui.components.EmployeeRouteTrackerCard
import com.example.ui.components.EmployeeShiftsCard
import com.example.ui.components.GeofenceBlockedDialog
import com.example.ui.components.GeofenceRadarCard
import com.example.ui.components.OfflineSyncBanner
import com.example.ui.components.StatusPill
import com.example.ui.components.VoiceCheckInDialog
import com.example.ui.theme.MonoTelemetryStyle
import com.example.ui.theme.PulseTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AttendanceScreen(
  session: UserSession,
  isOfflineSimulated: Boolean,
  pendingSyncCount: Int,
  onToggleOffline: (Boolean) -> Unit,
  onSyncNow: () -> Unit,
  onRequestAuth: (AuthChallengePurpose, () -> Unit) -> Unit,
  onNavigateToTravelMap: () -> Unit = {},
  viewModel: AttendanceViewModel = hiltViewModel(),
) {
  val profile by viewModel.profile.collectAsStateWithLifecycle()
  val branches by viewModel.branches.collectAsStateWithLifecycle()
  val records by viewModel.attendanceRecords.collectAsStateWithLifecycle()
  val pendingActions by viewModel.pendingSyncActions.collectAsStateWithLifecycle()
  val hasLocationPermission by viewModel.hasLocationPermission.collectAsStateWithLifecycle()
  val isLocating by viewModel.isLocating.collectAsStateWithLifecycle()
  val geofenceEval by viewModel.geofenceEvaluation.collectAsStateWithLifecycle()
  val punchConfirmation by viewModel.punchConfirmation.collectAsStateWithLifecycle()
  val blockedEval by viewModel.blockedEvaluation.collectAsStateWithLifecycle()
  val statusBanner by viewModel.statusBanner.collectAsStateWithLifecycle()
  val voiceCheckInSession by viewModel.voiceCheckInSession.collectAsStateWithLifecycle()
  val voiceListeningState by viewModel.voiceListeningState.collectAsStateWithLifecycle()
  val hasMicPermission by viewModel.hasMicPermission.collectAsStateWithLifecycle()
  val voiceAuthPhrase by viewModel.voiceAuthPhrase.collectAsStateWithLifecycle()
  val workHoursSchedule by viewModel.workHoursSchedule.collectAsStateWithLifecycle()
  val activeRouteSessionId by viewModel.activeRouteSessionId.collectAsStateWithLifecycle()
  val lastRouteSyncEpochMs by viewModel.lastRouteSyncEpochMs.collectAsStateWithLifecycle()
  val routeSession by viewModel.activeOrLatestRouteSession.collectAsStateWithLifecycle()
  val isRouteActionBusy by viewModel.isRouteActionBusy.collectAsStateWithLifecycle()
  val employeeShifts by viewModel.employeeShifts.collectAsStateWithLifecycle()
  val proactiveShiftSuggestion by viewModel.proactiveShiftSuggestion.collectAsStateWithLifecycle()

  var selectedDateAd by remember { mutableStateOf("2026-11-14") }
  var showCalibrateDialog by remember { mutableStateOf(false) }

  val permissionLauncher =
    rememberLauncherForActivityResult(
      contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
      viewModel.onLocationPermissionUpdated()
    }

  val todayAd = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
  val todayRecord = records.firstOrNull { it.attendanceDateAd == todayAd }

  if (blockedEval != null && profile != null) {
    GeofenceBlockedDialog(
      branchName = profile!!.branch.name,
      evaluation = blockedEval,
      onDismiss = viewModel::dismissBlockedDialog,
    )
  }

  voiceCheckInSession?.let { vSession ->
    VoiceCheckInDialog(
      sessionInfo = vSession,
      listeningState = voiceListeningState,
      expectedPhrase = voiceAuthPhrase,
      hasMicPermission = hasMicPermission,
      onMicPermissionGranted = viewModel::onMicPermissionGranted,
      onStartListening = viewModel::startVoiceListening,
      onStopListening = viewModel::stopVoiceListening,
      onUpdatePhrase = viewModel::updateVoiceAuthPhrase,
      onDismiss = viewModel::dismissVoiceCheckIn,
    )
  }

  punchConfirmation?.let { confirmation ->
    AlertDialog(
      onDismissRequest = viewModel::dismissPunchConfirmation,
      icon = {
        Icon(
          imageVector = Icons.Filled.Fingerprint,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(32.dp),
        )
      },
      title = {
        Text(
          if (confirmation.isClockIn) "Confirm Biometric Clock-In"
          else "Confirm Biometric Clock-Out"
        )
      },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(
            "Geofence boundary verified for ${confirmation.branch.name}. Authenticate to commit attendance punch (source = MOBILE)."
          )
          Surface(
            shape = MaterialTheme.shapes.small,
            color = PulseTheme.semantic.verifiedContainer,
          ) {
            Column(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
              Text(
                text =
                  "Distance: ${confirmation.evaluation.distanceMeters?.roundToInt() ?: 0}m / ${confirmation.branch.geofenceRadiusMeters.roundToInt()}m",
                style = MonoTelemetryStyle,
                color = PulseTheme.semantic.onVerifiedContainer,
              )
              Text(
                text =
                  String.format(
                    Locale.US,
                    "Coords: %.5f, %.5f",
                    confirmation.evaluation.userLatitude ?: 0.0,
                    confirmation.evaluation.userLongitude ?: 0.0,
                  ),
                style = MonoTelemetryStyle,
                color = PulseTheme.semantic.onVerifiedContainer,
              )
              Text(
                text = "Punch Time: ${confirmation.timestampLabel}",
                style = MonoTelemetryStyle,
                color = PulseTheme.semantic.onVerifiedContainer,
              )
            }
          }
        }
      },
      confirmButton = {
        Button(
          onClick = {
            onRequestAuth(AuthChallengePurpose.CLOCK_PUNCH) {
              viewModel.executeVerifiedPunch()
            }
          },
          modifier = Modifier.testTag("attendance_confirm_biometric_punch"),
        ) {
          Icon(Icons.Filled.Fingerprint, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text("Authenticate & Punch")
        }
      },
      dismissButton = {
        TextButton(onClick = viewModel::dismissPunchConfirmation) { Text("Cancel") }
      },
    )
  }

  if (showCalibrateDialog && profile != null) {
    var sliderRadius by remember(profile) {
      mutableFloatStateOf(profile!!.branch.geofenceRadiusMeters.toFloat())
    }
    var pinToGps by remember { mutableStateOf(false) }

    AlertDialog(
      onDismissRequest = { showCalibrateDialog = false },
      title = { Text("Calibrate Branch Geofence") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Text(
            text = profile!!.branch.name,
            style = MaterialTheme.typography.titleSmall,
          )
          Text(
            text = "Radius: ${sliderRadius.roundToInt()} meters",
            style = MonoTelemetryStyle,
          )
          Slider(
            value = sliderRadius,
            onValueChange = { sliderRadius = it },
            valueRange = 50f..1500f,
          )
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              text = "Set branch center to current GPS",
              style = MaterialTheme.typography.bodySmall,
              modifier = Modifier.weight(1f),
            )
            Switch(checked = pinToGps, onCheckedChange = { pinToGps = it })
          }
        }
      },
      confirmButton = {
        Button(
          onClick = {
            viewModel.calibrateBranchRadius(sliderRadius.toDouble(), pinToGps)
            showCalibrateDialog = false
          }
        ) {
          Text("Save Geofence")
        }
      },
      dismissButton = {
        TextButton(onClick = { showCalibrateDialog = false }) { Text("Cancel") }
      },
    )
  }

  Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    LazyColumn(
      modifier = Modifier.widthIn(max = 840.dp).fillMaxSize(),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      item {
        Text(
          text = "Attendance & Geofencing",
          style = MaterialTheme.typography.headlineMedium,
        )
      }

      item {
        OfflineSyncBanner(
          isOfflineSimulated = isOfflineSimulated,
          pendingSyncCount = pendingSyncCount,
          onSyncNow = onSyncNow,
          onToggleOffline = onToggleOffline,
        )
      }

      if (!statusBanner.isNullOrBlank()) {
        item {
          Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                text = statusBanner!!,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.weight(1f),
              )
              TextButton(onClick = viewModel::clearStatusBanner) { Text("Dismiss") }
            }
          }
        }
      }

      // Geofence Telemetry Radar
      profile?.let { emp ->
        item {
          GeofenceRadarCard(
            branch = emp.branch,
            evaluation = geofenceEval,
            hasPermission = hasLocationPermission,
            isLocating = isLocating,
            onRefreshLocation = viewModel::refreshGpsLocation,
            onRequestPermission = {
              permissionLauncher.launch(
                arrayOf(
                  Manifest.permission.ACCESS_FINE_LOCATION,
                  Manifest.permission.ACCESS_COARSE_LOCATION,
                )
              )
            },
          )
        }

        // Branch Assignment & Punch Actions
        item {
          Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
          ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Text(
                  text = "Assigned Branch Perimeter",
                  style = MaterialTheme.typography.titleMedium,
                )
                if (session.role.canManageGeofences()) {
                  TextButton(
                    onClick = { showCalibrateDialog = true },
                    modifier = Modifier.testTag("calibrate_geofence_button"),
                  ) {
                    Icon(Icons.Filled.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Calibrate")
                  }
                }
              }

              FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
              ) {
                branches.forEach { branch ->
                  FilterChip(
                    selected = emp.branch.id == branch.id,
                    onClick = { viewModel.switchAssignedBranch(branch.id) },
                    label = { Text("${branch.code} (${branch.geofenceRadiusMeters.roundToInt()}m)") },
                    leadingIcon = {
                      Icon(Icons.Filled.LocationOn, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                    modifier = Modifier.testTag("branch_chip_${branch.id}"),
                  )
                }
              }

              val hasCheckedIn = todayRecord?.checkInTime != null
              val hasCheckedOut = todayRecord?.checkOutTime != null
              val semantic = PulseTheme.semantic

              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
              ) {
                Button(
                  onClick = { viewModel.initiateClockAction(isClockIn = true) },
                  enabled = !hasCheckedIn,
                  modifier = Modifier.weight(1f).height(50.dp).testTag("attendance_clock_in_button"),
                ) {
                  Icon(Icons.Filled.Fingerprint, contentDescription = null, modifier = Modifier.size(18.dp))
                  Spacer(modifier = Modifier.width(6.dp))
                  Text(if (hasCheckedIn) "In: ${todayRecord?.checkInTime}" else "Clock In")
                }

                Button(
                  onClick = { viewModel.initiateClockAction(isClockIn = false) },
                  enabled = hasCheckedIn && !hasCheckedOut,
                  colors = ButtonDefaults.buttonColors(containerColor = semantic.warning),
                  modifier = Modifier.weight(1f).height(50.dp).testTag("attendance_clock_out_button"),
                ) {
                  Icon(Icons.Filled.Fingerprint, contentDescription = null, modifier = Modifier.size(18.dp))
                  Spacer(modifier = Modifier.width(6.dp))
                  Text(if (hasCheckedOut) "Out: ${todayRecord?.checkOutTime}" else "Clock Out")
                }
              }

              if (!hasCheckedIn) {
                OutlinedButton(
                  onClick = viewModel::initiateVoiceCheckIn,
                  modifier =
                    Modifier.fillMaxWidth().height(48.dp).testTag("attendance_voice_checkin_button"),
                ) {
                  Icon(Icons.Filled.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
                  Spacer(modifier = Modifier.width(8.dp))
                  Text("Voice Command Check-In (\"$voiceAuthPhrase\")")
                }
              }
            }
          }
        }
      }

      // Field Check-In / Check-Out Toggle + Periodic Location Sync & Visited Route Map
      item {
        EmployeeRouteTrackerCard(
          isCheckedIn = !activeRouteSessionId.isNullOrBlank(),
          hasLocationPermission = hasLocationPermission,
          isBusy = isRouteActionBusy,
          routeSession = routeSession,
          lastSyncEpochMs = lastRouteSyncEpochMs,
          formatDistance = viewModel::formatRouteDistance,
          onToggleCheckInOut = viewModel::toggleRouteCheckInOut,
          onPushLocationNow = viewModel::pushLocationNow,
          onSyncDatabaseNow = viewModel::syncRouteDatabaseNow,
          onOpenTravelMap = onNavigateToTravelMap,
          onRequestLocationPermission = {
            permissionLauncher.launch(
              arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
              )
            )
          },
        )
      }

      // Active & Upcoming Synced Multi-Shift Schedule
      item {
        EmployeeShiftsCard(
          shifts = employeeShifts,
          proactiveSuggestion = proactiveShiftSuggestion,
          onClockInSuggestedShift = viewModel::toggleRouteCheckInOut,
        )
      }

      // Work-Hours Geofence Notification Scheduler
      item {
        GeofenceReminderScheduleCard(
          schedule = workHoursSchedule,
          onUpdateSchedule = viewModel::updateWorkHoursSchedule,
          onTestEntryAlert = { viewModel.triggerGeofenceReminderPreview(isEntry = true) },
          onTestExitAlert = { viewModel.triggerGeofenceReminderPreview(isEntry = false) },
          onTestProactiveSuggestion = viewModel::triggerProactiveShiftSuggestionPreview,
        )
      }

      // Monthly Attendance Calendar (November 2026)
      item {
        AttendanceCalendarCard(
          records = records,
          selectedDateAd = selectedDateAd,
          onSelectDate = { selectedDateAd = it },
        )
      }

      // Selected Day Detail Card
      item {
        val selectedRecord = records.firstOrNull { it.attendanceDateAd == selectedDateAd }
        SelectedDayDetailCard(dateAd = selectedDateAd, record = selectedRecord)
      }

      // Offline Sync & WorkManager Testing Controls
      item {
        Card(
          modifier = Modifier.fillMaxWidth(),
          shape = MaterialTheme.shapes.medium,
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
          Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f),
              ) {
                Icon(
                  imageVector = Icons.Filled.CloudOff,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.primary,
                )
                Column {
                  Text("Offline Sync Testing", style = MaterialTheme.typography.titleSmall)
                  Text(
                    "Queue attendance punches in Room and auto-sync via WorkManager.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              }
              Switch(
                checked = isOfflineSimulated,
                onCheckedChange = onToggleOffline,
                modifier = Modifier.testTag("offline_mode_switch"),
              )
            }

            if (pendingActions.isNotEmpty()) {
              pendingActions.forEach { action ->
                Surface(
                  shape = MaterialTheme.shapes.small,
                  color = MaterialTheme.colorScheme.surfaceVariant,
                  modifier = Modifier.fillMaxWidth(),
                ) {
                  Row(
                    modifier = Modifier.padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                  ) {
                    Column(modifier = Modifier.weight(1f)) {
                      Text(action.description, style = MaterialTheme.typography.labelLarge)
                      Text(
                        "Status: Queued (${action.lastError ?: "Waiting network"})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                      )
                    }
                    Icon(Icons.Filled.Sync, contentDescription = null)
                  }
                }
              }
              OutlinedButton(
                onClick = onSyncNow,
                enabled = !isOfflineSimulated,
                modifier = Modifier.fillMaxWidth(),
              ) {
                Text("Force WorkManager Sync Now")
              }
            }
          }
        }
      }

      item { Spacer(modifier = Modifier.height(8.dp)) }
    }
  }
}

@Composable
private fun AttendanceCalendarCard(
  records: List<AttendanceRecord>,
  selectedDateAd: String,
  onSelectDate: (String) -> Unit,
) {
  val semantic = PulseTheme.semantic
  val recordMap = remember(records) { records.associateBy { it.attendanceDateAd } }

  // November 2026 starts on Sunday (dayOfWeek offset 0) and has 30 days
  val daysInMonth = 30
  val dayHeaders = listOf("S", "M", "T", "W", "T", "F", "S")

  Card(
    modifier = Modifier.fillMaxWidth(),
    shape = MaterialTheme.shapes.large,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Text(
        text = "November 2026 Attendance",
        style = MaterialTheme.typography.titleMedium,
      )
      Spacer(modifier = Modifier.height(10.dp))

      Row(modifier = Modifier.fillMaxWidth()) {
        dayHeaders.forEach { header ->
          Text(
            text = header,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
          )
        }
      }
      Spacer(modifier = Modifier.height(6.dp))

      val totalCells = 35
      for (week in 0 until 5) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          for (col in 0 until 7) {
            val dayNum = week * 7 + col + 1
            if (dayNum <= daysInMonth) {
              val dateStr = String.format(Locale.US, "2026-11-%02d", dayNum)
              val rec = recordMap[dateStr]
              val isSelected = dateStr == selectedDateAd
              val dotColor =
                when (rec?.status) {
                  AttendanceStatus.PRESENT -> semantic.verified
                  AttendanceStatus.ABSENT -> semantic.danger
                  AttendanceStatus.LEAVE -> MaterialTheme.colorScheme.primary
                  AttendanceStatus.HALF_DAY -> semantic.warning
                  AttendanceStatus.HOLIDAY -> semantic.holiday
                  AttendanceStatus.WEEKEND -> MaterialTheme.colorScheme.outline
                  null -> Color.Transparent
                }

              Box(
                modifier =
                  Modifier.weight(1f)
                    .aspectRatio(1f)
                    .clip(MaterialTheme.shapes.small)
                    .background(
                      if (isSelected) MaterialTheme.colorScheme.primaryContainer
                      else Color.Transparent
                    )
                    .border(
                      width = if (isSelected) 1.5.dp else 0.dp,
                      color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                      shape = MaterialTheme.shapes.small,
                    )
                    .clickable { onSelectDate(dateStr) }
                    .testTag("calendar_day_$dayNum"),
                contentAlignment = Alignment.Center,
              ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                  Text(
                    text = dayNum.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                  )
                  Spacer(modifier = Modifier.height(3.dp))
                  Box(
                    modifier =
                      Modifier.size(6.dp)
                        .clip(CircleShape)
                        .background(dotColor)
                  )
                }
              }
            } else {
              Spacer(modifier = Modifier.weight(1f))
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(12.dp))
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        LegendDot("Present", semantic.verified)
        LegendDot("Half Day", semantic.warning)
        LegendDot("Leave", MaterialTheme.colorScheme.primary)
        LegendDot("Absent", semantic.danger)
        LegendDot("Holiday", semantic.holiday)
      }
    }
  }
}

@Composable
private fun LegendDot(label: String, color: Color) {
  Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(color))
    Text(text = label, style = MaterialTheme.typography.bodySmall)
  }
}

@Composable
private fun SelectedDayDetailCard(
  dateAd: String,
  record: AttendanceRecord?,
) {
  val semantic = PulseTheme.semantic
  Card(
    modifier = Modifier.fillMaxWidth(),
    shape = MaterialTheme.shapes.medium,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
  ) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(text = "Log Details • $dateAd", style = MaterialTheme.typography.titleSmall)
        if (record != null) {
          val (bg, fg) =
            when (record.status) {
              AttendanceStatus.PRESENT -> semantic.verifiedContainer to semantic.onVerifiedContainer
              AttendanceStatus.ABSENT -> semantic.dangerContainer to semantic.onDangerContainer
              AttendanceStatus.HALF_DAY -> semantic.warningContainer to semantic.onWarningContainer
              AttendanceStatus.HOLIDAY -> semantic.holidayContainer to semantic.onHolidayContainer
              AttendanceStatus.LEAVE ->
                MaterialTheme.colorScheme.primaryContainer to
                  MaterialTheme.colorScheme.onPrimaryContainer
              AttendanceStatus.WEEKEND ->
                MaterialTheme.colorScheme.surfaceVariant to
                  MaterialTheme.colorScheme.onSurfaceVariant
            }
          StatusPill(text = record.status.label, containerColor = bg, contentColor = fg)
        }
      }

      if (record == null) {
        Text(
          text = "No punch log recorded for this date.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      } else {
        Text(
          text =
            "Check In: ${record.checkInTime ?: "—"}   |   Check Out: ${record.checkOutTime ?: "—"}",
          style = MonoTelemetryStyle,
        )
        Text(
          text =
            "Source: ${record.source}   |   Sync: ${record.syncStatus.label}",
          style = MonoTelemetryStyle,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (record.distanceFromBranchMeters != null) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Icon(
              imageVector = Icons.Filled.CheckCircle,
              contentDescription = null,
              tint =
                if (record.syncStatus == SyncStatus.SYNCED) semantic.verified
                else semantic.warning,
              modifier = Modifier.size(16.dp),
            )
            Text(
              text =
                "Geofence distance at punch: ${record.distanceFromBranchMeters.roundToInt()}m",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          StatusPill(
            text = "${record.integrityStatus} (Risk ${record.riskScore})",
            containerColor =
              if (record.integrityStatus == "VERIFIED") semantic.verifiedContainer
              else semantic.warningContainer,
            contentColor =
              if (record.integrityStatus == "VERIFIED") semantic.onVerifiedContainer
              else semantic.onWarningContainer,
          )
          if (!record.integrityNotes.isNullOrBlank()) {
            Text(
              text = record.integrityNotes,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GeofenceReminderScheduleCard(
  schedule: WorkHoursScheduleConfig,
  onUpdateSchedule: (WorkHoursScheduleConfig) -> Unit,
  onTestEntryAlert: () -> Unit,
  onTestExitAlert: () -> Unit,
  onTestProactiveSuggestion: () -> Unit,
) {
  Card(
    modifier = Modifier.fillMaxWidth().testTag("geofence_scheduler_card"),
    shape = MaterialTheme.shapes.medium,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
  ) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp),
          modifier = Modifier.weight(1f),
        ) {
          Icon(
            imageVector = Icons.Filled.NotificationsActive,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
          )
          Column {
            Text("Work-Hours Geofence Alerts", style = MaterialTheme.typography.titleMedium)
            Text(
              text = "Window: ${schedule.formattedWindow()}",
              style = MonoTelemetryStyle,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
        Switch(
          checked = schedule.enabled,
          onCheckedChange = { onUpdateSchedule(schedule.copy(enabled = it)) },
          modifier = Modifier.testTag("geofence_alerts_enabled_switch"),
        )
      }

      Text(
        text =
          "Alerts employees to clock in upon entering the office geofence (without today's check-in) or clock out upon leaving during shift hours.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        val presets =
          listOf(
            Triple("08:30–18:30", 8 to 30, 18 to 30),
            Triple("09:00–18:00", 9 to 0, 18 to 0),
            Triple("07:00–19:00", 7 to 0, 19 to 0),
          )
        presets.forEach { (label, start, end) ->
          val isSelected =
            schedule.startHour == start.first &&
              schedule.startMinute == start.second &&
              schedule.endHour == end.first &&
              schedule.endMinute == end.second
          FilterChip(
            selected = isSelected,
            enabled = schedule.enabled,
            onClick = {
              onUpdateSchedule(
                schedule.copy(
                  startHour = start.first,
                  startMinute = start.second,
                  endHour = end.first,
                  endMinute = end.second,
                )
              )
            },
            label = { Text(label) },
          )
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text("Weekdays Only (Mon–Fri)", style = MaterialTheme.typography.bodyMedium)
        Switch(
          checked = schedule.weekdaysOnly,
          enabled = schedule.enabled,
          onCheckedChange = { onUpdateSchedule(schedule.copy(weekdaysOnly = it)) },
          modifier = Modifier.testTag("geofence_weekdays_only_switch"),
        )
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        OutlinedButton(
          onClick = onTestEntryAlert,
          enabled = schedule.enabled,
          modifier = Modifier.weight(1f).testTag("test_entry_reminder_button"),
        ) {
          Text("Test Entry Alert")
        }
        OutlinedButton(
          onClick = onTestExitAlert,
          enabled = schedule.enabled,
          modifier = Modifier.weight(1f).testTag("test_exit_reminder_button"),
        ) {
          Text("Test Exit Alert")
        }
      }
      OutlinedButton(
        onClick = onTestProactiveSuggestion,
        enabled = schedule.enabled,
        modifier = Modifier.fillMaxWidth().testTag("test_proactive_shift_suggestion_button"),
      ) {
        Text("Preview Off-Site Shift Suggestion")
      }
    }
  }
}
