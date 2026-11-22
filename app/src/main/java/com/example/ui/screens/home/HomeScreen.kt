package com.example.ui.screens.home

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.GpsOff
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PendingActions
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.model.AppCapability
import com.example.domain.model.AuthChallengePurpose
import com.example.domain.model.DutyStatus
import com.example.domain.model.OfflineAccessMode
import com.example.domain.model.UserSession
import com.example.ui.SyncStatusSummary
import com.example.ui.components.DashboardQuickActionMenu
import com.example.ui.components.EmployeeRouteTrackerCard
import com.example.ui.components.EmployeeShiftsCard
import com.example.ui.components.GeofenceBlockedDialog
import com.example.ui.components.InteractiveLeaveBalanceChartCard
import com.example.ui.components.InteractiveUpcomingHolidayChartCard
import com.example.ui.components.MetricSummaryCard
import com.example.ui.components.OfflineSyncBanner
import com.example.ui.components.StatusPill
import com.example.ui.components.SubmitExpenseClaimDialog
import com.example.ui.components.VoiceCheckInDialog
import com.example.ui.screens.attendance.AttendanceViewModel
import com.example.ui.screens.workplace.WorkplaceViewModel
import com.example.ui.theme.MonoMetricStyle
import com.example.ui.theme.MonoTelemetryStyle
import com.example.ui.theme.PulseTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
  session: UserSession,
  isOfflineSimulated: Boolean,
  pendingSyncCount: Int,
  dutyStatus: DutyStatus = DutyStatus.ON_DUTY,
  syncStatusSummary: SyncStatusSummary = SyncStatusSummary(),
  unresolvedNeedsAttentionCount: Int = 0,
  onChangeDutyStatus: (DutyStatus) -> Unit = {},
  onToggleOffline: (Boolean) -> Unit,
  onSyncNow: () -> Unit,
  onLockSession: () -> Unit,
  onNavigateToAttendance: () -> Unit,
  onNavigateToLeaves: () -> Unit,
  onNavigateToAnalytics: () -> Unit = {},
  onNavigateToField: () -> Unit = {},
  onNavigateToOps: (String?) -> Unit = {},
  onNavigateToTravelMap: () -> Unit = {},
  onRequestAuth: (AuthChallengePurpose, () -> Unit) -> Unit,
  viewModel: AttendanceViewModel = hiltViewModel(),
  workplaceViewModel: WorkplaceViewModel = hiltViewModel(),
) {
  val profile by viewModel.profile.collectAsStateWithLifecycle()
  val records by viewModel.attendanceRecords.collectAsStateWithLifecycle()
  val stats by viewModel.summaryStats.collectAsStateWithLifecycle()
  val leaveBalances by viewModel.leaveBalances.collectAsStateWithLifecycle()
  val upcomingHolidays by viewModel.upcomingHolidays.collectAsStateWithLifecycle()
  val announcements by viewModel.announcements.collectAsStateWithLifecycle()
  val teamPending by viewModel.teamPendingApprovals.collectAsStateWithLifecycle()
  val hasLocationPermission by viewModel.hasLocationPermission.collectAsStateWithLifecycle()
  val geofenceEval by viewModel.geofenceEvaluation.collectAsStateWithLifecycle()
  val punchConfirmation by viewModel.punchConfirmation.collectAsStateWithLifecycle()
  val blockedEval by viewModel.blockedEvaluation.collectAsStateWithLifecycle()
  val statusBanner by viewModel.statusBanner.collectAsStateWithLifecycle()
  val voiceCheckInSession by viewModel.voiceCheckInSession.collectAsStateWithLifecycle()
  val voiceListeningState by viewModel.voiceListeningState.collectAsStateWithLifecycle()
  val hasMicPermission by viewModel.hasMicPermission.collectAsStateWithLifecycle()
  val voiceAuthPhrase by viewModel.voiceAuthPhrase.collectAsStateWithLifecycle()
  val activeRouteSessionId by viewModel.activeRouteSessionId.collectAsStateWithLifecycle()
  val lastRouteSyncEpochMs by viewModel.lastRouteSyncEpochMs.collectAsStateWithLifecycle()
  val routeSession by viewModel.activeOrLatestRouteSession.collectAsStateWithLifecycle()
  val isRouteActionBusy by viewModel.isRouteActionBusy.collectAsStateWithLifecycle()
  val employeeShifts by viewModel.employeeShifts.collectAsStateWithLifecycle()
  val proactiveShiftSuggestion by viewModel.proactiveShiftSuggestion.collectAsStateWithLifecycle()
  val expenseFeedback by workplaceViewModel.feedback.collectAsStateWithLifecycle()

  var isQuickMenuExpanded by remember { mutableStateOf(false) }
  var showSubmitExpenseDialog by remember { mutableStateOf(false) }
  var pendingQuickClockAction by remember { mutableStateOf<Boolean?>(null) }
  val snackbarHostState = remember { SnackbarHostState() }

  BackHandler(enabled = isQuickMenuExpanded) {
    isQuickMenuExpanded = false
  }

  LaunchedEffect(statusBanner) {
    val msg = statusBanner
    if (!msg.isNullOrBlank()) {
      snackbarHostState.showSnackbar(msg)
    }
  }

  LaunchedEffect(expenseFeedback) {
    val msg = expenseFeedback?.first
    if (!msg.isNullOrBlank()) {
      snackbarHostState.showSnackbar(msg)
    }
  }

  val permissionLauncher =
    rememberLauncherForActivityResult(
      contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
      viewModel.onLocationPermissionUpdated()
      val pending = pendingQuickClockAction
      pendingQuickClockAction = null
      if (pending != null) {
        viewModel.initiateClockAction(isClockIn = pending)
      }
    }

  val todayAd = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
  val todayRecord = records.firstOrNull { it.attendanceDateAd == todayAd }

  if (showSubmitExpenseDialog) {
    SubmitExpenseClaimDialog(
      onDismiss = { showSubmitExpenseDialog = false },
      onSubmit = { category, amount, ticketCode, description, attachReceipt ->
        workplaceViewModel.submitExpenseClaim(
          category = category,
          amountText = amount,
          ticketCode = ticketCode,
          description = description,
          receiptAttached = attachReceipt,
        )
        showSubmitExpenseDialog = false
      },
    )
  }

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
          if (confirmation.isClockIn) "Biometric Clock-In Confirmation"
          else "Biometric Clock-Out Confirmation"
        )
      },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text("Geofence verified within ${confirmation.branch.name}.")
          Surface(
            shape = MaterialTheme.shapes.small,
            color = PulseTheme.semantic.verifiedContainer,
          ) {
            Column(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
              Text(
                text = "Distance: ${confirmation.evaluation.distanceMeters?.roundToInt() ?: 0}m / ${confirmation.branch.geofenceRadiusMeters.roundToInt()}m",
                style = MonoTelemetryStyle,
                color = PulseTheme.semantic.onVerifiedContainer,
              )
              Text(
                text = String.format(
                  Locale.US,
                  "GPS: %.5f, %.5f • Time: %s",
                  confirmation.evaluation.userLatitude ?: 0.0,
                  confirmation.evaluation.userLongitude ?: 0.0,
                  confirmation.timestampLabel,
                ),
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
          modifier = Modifier.testTag("home_confirm_biometric_punch"),
        ) {
          Icon(Icons.Filled.Fingerprint, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text("Verify & Submit")
        }
      },
      dismissButton = {
        TextButton(onClick = viewModel::dismissPunchConfirmation) { Text("Cancel") }
      },
    )
  }

  Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    LazyColumn(
      modifier = Modifier.widthIn(max = 840.dp).fillMaxSize(),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      // Top Greeting Bar
      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f),
          ) {
            val displayName = profile?.fullName ?: session.email
            val initials =
              displayName
                .trim()
                .split(Regex("\\s+|@|\\."))
                .filter { it.isNotBlank() }
                .take(2)
                .joinToString("") { it.first().uppercaseChar().toString() }
                .ifBlank { "HR" }
            Surface(
              modifier = Modifier.size(46.dp).clip(CircleShape),
              color = MaterialTheme.colorScheme.primaryContainer,
            ) {
              Box(contentAlignment = Alignment.Center) {
                Text(
                  text = initials,
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.Bold,
                  color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
              }
            }
            Column {
              Text(
                text = profile?.fullName ?: session.email,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
              FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
              ) {
                StatusPill(
                  text = session.assignedBundles.firstOrNull() ?: session.role.badgeLabel,
                  containerColor = MaterialTheme.colorScheme.primaryContainer,
                  contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                StatusPill(
                  text = session.branchScope,
                  containerColor = MaterialTheme.colorScheme.secondaryContainer,
                  contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                )
              }
            }
          }
          IconButton(
            onClick = onLockSession,
            modifier = Modifier.minimumInteractiveComponentSize().testTag("lock_session_button"),
          ) {
            Icon(
              imageVector = Icons.Filled.Lock,
              contentDescription = "Lock Session",
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }

      // Persistent v2 Sync Status Badge + 24h Offline Token Posture
      item {
        val semantic = PulseTheme.semantic
        Card(
          modifier = Modifier.fillMaxWidth().testTag("sync_status_badge_card"),
          shape = MaterialTheme.shapes.medium,
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
          Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                  imageVector = Icons.Filled.Sync,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.size(16.dp),
                )
                Text(
                  text =
                    when {
                      syncStatusSummary.isUploading -> "Sync Status: Uploading…"
                      syncStatusSummary.failedCount > 0 ->
                        "Sync: ${syncStatusSummary.queuedCount} queued • ${syncStatusSummary.failedCount} retry"
                      syncStatusSummary.queuedCount > 0 ->
                        "Sync: ${syncStatusSummary.queuedCount} action(s) queued offline"
                      else -> "Sync Status: All records current"
                    },
                  style = MaterialTheme.typography.labelLarge,
                )
              }
              StatusPill(
                text = syncStatusSummary.offlineAccessMode.label,
                containerColor =
                  if (syncStatusSummary.offlineAccessMode == OfflineAccessMode.ONLINE)
                    semantic.verifiedContainer
                  else semantic.warningContainer,
                contentColor =
                  if (syncStatusSummary.offlineAccessMode == OfflineAccessMode.ONLINE)
                    semantic.onVerifiedContainer
                  else semantic.onWarningContainer,
              )
            }

            if (syncStatusSummary.queueCapWarning) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
              ) {
                Icon(
                  Icons.Filled.Warning,
                  contentDescription = null,
                  tint = semantic.danger,
                  modifier = Modifier.size(16.dp),
                )
                Text(
                  text = "Queue cap warning (>= 20 queued items). Reconnect soon to flush queue.",
                  style = MaterialTheme.typography.bodySmall,
                  color = semantic.danger,
                )
              }
            }
          }
        }
      }

      // Duty Status Selector on Today Screen
      item {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Text(
            text = "Current Duty Status",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            DutyStatus.entries.forEach { st ->
              FilterChip(
                selected = dutyStatus == st,
                onClick = { onChangeDutyStatus(st) },
                label = { Text(st.label) },
                modifier = Modifier.testTag("duty_chip_${st.name}"),
              )
            }
          }
        }
      }

      // Needs Attention Inbox Alert Banner (from 409 conflict / held punch)
      if (unresolvedNeedsAttentionCount > 0) {
        item {
          val semantic = PulseTheme.semantic
          Card(
            onClick = { onNavigateToOps("NEEDS_ATTENTION") },
            modifier = Modifier.fillMaxWidth().testTag("home_needs_attention_card"),
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(containerColor = semantic.dangerContainer),
          ) {
            Row(
              modifier = Modifier.padding(14.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f),
              ) {
                Icon(
                  imageVector = Icons.Filled.ErrorOutline,
                  contentDescription = null,
                  tint = semantic.onDangerContainer,
                )
                Column {
                  Text(
                    text = "Needs Attention ($unresolvedNeedsAttentionCount)",
                    style = MaterialTheme.typography.titleSmall,
                    color = semantic.onDangerContainer,
                  )
                  Text(
                    text = "Sync conflict or serial mismatch — captured photos & OTDR telemetry preserved. Tap to resolve.",
                    style = MaterialTheme.typography.bodySmall,
                    color = semantic.onDangerContainer,
                  )
                }
              }
              Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "Open Needs Attention Inbox",
                tint = semantic.onDangerContainer,
              )
            }
          }
        }
      }

      // Offline / Pending Sync Banner
      item {
        OfflineSyncBanner(
          isOfflineSimulated = isOfflineSimulated,
          pendingSyncCount = pendingSyncCount,
          onSyncNow = onSyncNow,
          onToggleOffline = onToggleOffline,
        )
      }

      // Status Feedback Banner
      if (!statusBanner.isNullOrBlank()) {
        item {
          Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.fillMaxWidth().testTag("home_status_banner"),
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

      // Expense Claim Quick Submission Feedback Banner
      if (expenseFeedback != null) {
        val (msg, isError) = expenseFeedback!!
        item {
          Surface(
            shape = MaterialTheme.shapes.medium,
            color =
              if (isError) MaterialTheme.colorScheme.errorContainer
              else PulseTheme.semantic.verifiedContainer,
            modifier = Modifier.fillMaxWidth().testTag("home_expense_feedback_banner"),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                text = msg,
                style = MaterialTheme.typography.bodySmall,
                color =
                  if (isError) MaterialTheme.colorScheme.onErrorContainer
                  else PulseTheme.semantic.onVerifiedContainer,
                modifier = Modifier.weight(1f),
              )
              TextButton(onClick = workplaceViewModel::clearFeedback) { Text("Dismiss") }
            }
          }
        }
      }

      // Hero One-Tap Biometric & Geofence Clock-In/Out Widget
      item {
        val semantic = PulseTheme.semantic
        val isInside = geofenceEval?.isInsideGeofence == true
        val hasCheckedIn = todayRecord?.checkInTime != null
        val hasCheckedOut = todayRecord?.checkOutTime != null

        Card(
          modifier = Modifier.fillMaxWidth(),
          shape = MaterialTheme.shapes.large,
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
          Column {
            Surface(
              color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
              modifier = Modifier.fillMaxWidth(),
            ) {
              Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                  Icon(
                    imageVector = Icons.Filled.LocationOn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                  )
                  Text(
                    text = profile?.branch?.name ?: "Assigned Branch",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                  )
                }
                val geoText =
                  when {
                    !hasLocationPermission -> "GPS Permission Required"
                    geofenceEval?.userLatitude == null -> "Waiting for GPS Fix"
                    isInside ->
                      "Inside Geofence • ${geofenceEval?.distanceMeters?.roundToInt()}m"
                    else ->
                      "Outside Geofence • ${geofenceEval?.distanceMeters?.roundToInt()}m"
                  }
                StatusPill(
                  text = geoText,
                  containerColor =
                    when {
                      !hasLocationPermission || geofenceEval?.userLatitude == null ->
                        semantic.warningContainer
                      isInside -> semantic.verifiedContainer
                      else -> semantic.dangerContainer
                    },
                  contentColor =
                    when {
                      !hasLocationPermission || geofenceEval?.userLatitude == null ->
                        semantic.onWarningContainer
                      isInside -> semantic.onVerifiedContainer
                      else -> semantic.onDangerContainer
                    },
                  icon = if (isInside) Icons.Filled.GpsFixed else Icons.Filled.GpsOff,
                )
              }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Column(modifier = Modifier.padding(16.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Column {
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                  ) {
                    Text(
                      text = "TODAY'S SHIFT ($todayAd)",
                      style = MaterialTheme.typography.labelSmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (todayRecord != null) {
                      StatusPill(
                        text = "${todayRecord.integrityStatus} (${todayRecord.riskScore})",
                        containerColor =
                          if (todayRecord.integrityStatus == "VERIFIED") semantic.verifiedContainer
                          else semantic.warningContainer,
                        contentColor =
                          if (todayRecord.integrityStatus == "VERIFIED") semantic.onVerifiedContainer
                          else semantic.onWarningContainer,
                      )
                    }
                  }
                  Spacer(modifier = Modifier.height(4.dp))
                  Text(
                    text =
                      when {
                        hasCheckedIn && hasCheckedOut ->
                          "${todayRecord?.checkInTime} → ${todayRecord?.checkOutTime}"
                        hasCheckedIn -> "Clocked in at ${todayRecord?.checkInTime}"
                        else -> "Not clocked in yet"
                      },
                    style = MonoMetricStyle.copy(fontSize = 17.sp),
                    color = MaterialTheme.colorScheme.onSurface,
                  )
                }
                TextButton(
                  onClick = onNavigateToAttendance,
                  modifier = Modifier.testTag("view_geofence_radar_button"),
                ) {
                  Text("Radar")
                  Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                  )
                }
              }

              Spacer(modifier = Modifier.height(14.dp))

              if (!hasLocationPermission) {
                OutlinedButton(
                  onClick = {
                    permissionLauncher.launch(
                      arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                      )
                    )
                  },
                  modifier = Modifier.fillMaxWidth().height(50.dp).testTag("home_grant_gps"),
                ) {
                  Icon(Icons.Filled.GpsFixed, contentDescription = null)
                  Spacer(modifier = Modifier.width(8.dp))
                  Text("Enable GPS for Geofence Check-In")
                }
              } else if (!hasCheckedOut) {
                val isClockInAction = !hasCheckedIn
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                  Button(
                    onClick = { viewModel.initiateClockAction(isClockIn = isClockInAction) },
                    colors =
                      ButtonDefaults.buttonColors(
                        containerColor =
                          if (isClockInAction) MaterialTheme.colorScheme.primary
                          else semantic.warning
                      ),
                    modifier =
                      Modifier.fillMaxWidth().height(52.dp).testTag("home_biometric_punch_button"),
                  ) {
                    Icon(Icons.Filled.Fingerprint, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                      if (isClockInAction) "One-Tap Biometric Clock In"
                      else "One-Tap Biometric Clock Out"
                    )
                  }
                  if (isClockInAction) {
                    OutlinedButton(
                      onClick = viewModel::initiateVoiceCheckIn,
                      modifier =
                        Modifier.fillMaxWidth().height(48.dp).testTag("home_voice_checkin_button"),
                    ) {
                      Icon(Icons.Filled.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
                      Spacer(modifier = Modifier.width(8.dp))
                      Text("Voice Check-In (\"$voiceAuthPhrase\")")
                    }
                  }
                }
              } else {
                Surface(
                  shape = MaterialTheme.shapes.small,
                  color = semantic.verifiedContainer,
                  modifier = Modifier.fillMaxWidth(),
                ) {
                  Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                  ) {
                    Icon(
                      Icons.Filled.CheckCircle,
                      contentDescription = null,
                      tint = semantic.onVerifiedContainer,
                    )
                    Text(
                      text = "Today's attendance shift completed & logged.",
                      style = MaterialTheme.typography.labelLarge,
                      color = semantic.onVerifiedContainer,
                    )
                  }
                }
              }
            }
          }
        }
      }

      // Field Check-In / Check-Out Toggle + Periodic DB Sync + Employee Visited Route Map
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

      // Active & Upcoming Multi-Shift Roster Schedule
      item {
        EmployeeShiftsCard(
          shifts = employeeShifts,
          proactiveSuggestion = proactiveShiftSuggestion,
          onClockInSuggestedShift = viewModel::toggleRouteCheckInOut,
        )
      }

      // Quick Attendance Summary Grid
      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          MetricSummaryCard(
            title = "Present",
            value = "${stats.presentDays}d",
            subtitle = "This month",
            icon = Icons.Filled.CheckCircle,
            accentColor = PulseTheme.semantic.verified,
            modifier = Modifier.weight(1f),
          )
          MetricSummaryCard(
            title = "Leave Bal.",
            value = "${stats.totalLeaveRemaining.toInt()}d",
            subtitle = "Available",
            icon = Icons.Filled.EventAvailable,
            accentColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
          )
          MetricSummaryCard(
            title = "Overtime",
            value = String.format(Locale.US, "%.1fh", stats.overtimeHours),
            subtitle = "Verified",
            icon = Icons.Filled.Schedule,
            accentColor = PulseTheme.semantic.warning,
            modifier = Modifier.weight(1f),
          )
        }
      }

      // Interactive Leave Balances & Upcoming Holiday Charts
      item {
        InteractiveLeaveBalanceChartCard(
          balances = leaveBalances,
          onPlanLeave = onNavigateToLeaves,
          onOpenFullAnalytics = onNavigateToAnalytics,
        )
      }

      item {
        InteractiveUpcomingHolidayChartCard(
          holidays = upcomingHolidays,
          onPlanBridgeLeave = onNavigateToLeaves,
        )
      }

      // Field Execution Quick Jump (jobs.execute)
      if (session.hasCapability(AppCapability.JOBS_EXECUTE)) {
        item {
          Card(
            onClick = onNavigateToField,
            modifier = Modifier.fillMaxWidth().testTag("home_field_shortcut_card"),
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
          ) {
            Row(
              modifier = Modifier.padding(14.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f),
              ) {
                Icon(
                  imageVector = Icons.Filled.Build,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.primary,
                )
                Column {
                  Text("Field Work Orders & Van Stock", style = MaterialTheme.typography.titleSmall)
                  Text(
                    text = "Complete SLA jobs offline, capture optical dBm/OTDR & manage van serials.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              }
              Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Open Field Ops")
            }
          }
        }
      }

      // Role-Based Pending Approvals (capability-driven)
      if (session.canApproveAnything()) {
        item {
          Card(
            modifier =
              Modifier.fillMaxWidth()
                .clickable(onClick = onNavigateToLeaves)
                .testTag("home_pending_approvals_card"),
            shape = MaterialTheme.shapes.medium,
            colors =
              CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
              ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
          ) {
            Row(
              modifier = Modifier.padding(16.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f),
              ) {
                Icon(
                  imageVector = Icons.Filled.PendingActions,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.size(28.dp),
                )
                Column {
                  Text(
                    text = "Team Approval Queue (${teamPending.size})",
                    style = MaterialTheme.typography.titleMedium,
                  )
                  Text(
                    text =
                      if (teamPending.isEmpty()) "All team leave requests reviewed."
                      else "Tap to review & approve pending employee requests.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              }
              Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "Open Leave Approvals",
                tint = MaterialTheme.colorScheme.primary,
              )
            }
          }
        }
      }

      // Company Announcements Header
      item {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Icon(
            imageVector = Icons.Filled.Campaign,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
          )
          Text(
            text = "Enterprise Announcements",
            style = MaterialTheme.typography.titleMedium,
          )
        }
      }

      items(announcements, key = { it.id }) { item ->
        Card(
          modifier = Modifier.fillMaxWidth(),
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
              StatusPill(
                text = item.category,
                containerColor =
                  if (item.isUrgent) PulseTheme.semantic.dangerContainer
                  else MaterialTheme.colorScheme.surfaceVariant,
                contentColor =
                  if (item.isUrgent) PulseTheme.semantic.onDangerContainer
                  else MaterialTheme.colorScheme.onSurfaceVariant,
              )
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
              ) {
                Icon(
                  Icons.Filled.AccessTime,
                  contentDescription = null,
                  modifier = Modifier.size(13.dp),
                  tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                  text = item.publishedAt,
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = item.title, style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
              text = item.summary,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }

      item { Spacer(modifier = Modifier.height(84.dp)) }
    }

    SnackbarHost(
      hostState = snackbarHostState,
      modifier =
        Modifier.align(Alignment.BottomCenter)
          .padding(start = 16.dp, end = 16.dp, bottom = 84.dp),
    )

    DashboardQuickActionMenu(
      expanded = isQuickMenuExpanded,
      onExpandedChange = { isQuickMenuExpanded = it },
      hasCheckedIn = todayRecord?.checkInTime != null,
      hasCheckedOut = todayRecord?.checkOutTime != null,
      checkInTimeLabel = todayRecord?.checkInTime,
      checkOutTimeLabel = todayRecord?.checkOutTime,
      onClockInClick = {
        val eligible = todayRecord?.checkInTime == null
        if (eligible && !hasLocationPermission) {
          pendingQuickClockAction = true
          permissionLauncher.launch(
            arrayOf(
              Manifest.permission.ACCESS_FINE_LOCATION,
              Manifest.permission.ACCESS_COARSE_LOCATION,
            )
          )
        } else {
          viewModel.initiateClockAction(isClockIn = true)
        }
      },
      onClockOutClick = {
        val eligible = todayRecord?.checkInTime != null && todayRecord.checkOutTime == null
        if (eligible && !hasLocationPermission) {
          pendingQuickClockAction = false
          permissionLauncher.launch(
            arrayOf(
              Manifest.permission.ACCESS_FINE_LOCATION,
              Manifest.permission.ACCESS_COARSE_LOCATION,
            )
          )
        } else {
          viewModel.initiateClockAction(isClockIn = false)
        }
      },
      onSubmitExpenseClick = { showSubmitExpenseDialog = true },
    )
  }
}
