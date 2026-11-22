package com.example.ui.screens.leaves

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.model.AuthChallengePurpose
import com.example.domain.model.LeaveBalance
import com.example.domain.model.LeaveRequest
import com.example.domain.model.LeaveStatus
import com.example.domain.model.SyncStatus
import com.example.domain.model.UserSession
import com.example.ui.components.StatusPill
import com.example.ui.theme.MonoMetricStyle
import com.example.ui.theme.MonoTelemetryStyle
import com.example.ui.theme.PulseTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.max

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun LeavesScreen(
  session: UserSession,
  onRequestAuth: (AuthChallengePurpose, () -> Unit) -> Unit,
  viewModel: LeavesViewModel = hiltViewModel(),
) {
  val balances by viewModel.balances.collectAsStateWithLifecycle()
  val myRequests by viewModel.myRequests.collectAsStateWithLifecycle()
  val teamRequests by viewModel.teamRequests.collectAsStateWithLifecycle()
  val feedback by viewModel.feedbackMessage.collectAsStateWithLifecycle()

  var selectedTab by remember { mutableIntStateOf(0) }
  var statusFilter by remember { mutableStateOf<LeaveStatus?>(null) }
  var showApplyDialog by remember { mutableStateOf(false) }
  var reviewTarget by remember { mutableStateOf<Pair<LeaveRequest, Boolean>?>(null) }

  if (showApplyDialog) {
    ApplyLeaveDialog(
      balances = balances,
      onDismiss = { showApplyDialog = false },
      onSubmitWithBiometric = { leaveTypeId, startDate, endDate, days, reason ->
        onRequestAuth(AuthChallengePurpose.LEAVE_SUBMIT) {
          viewModel.submitLeave(leaveTypeId, startDate, endDate, days, reason) {
            showApplyDialog = false
          }
        }
      },
    )
  }

  reviewTarget?.let { (request, approve) ->
    var note by remember { mutableStateOf("") }
    AlertDialog(
      onDismissRequest = { reviewTarget = null },
      title = { Text(if (approve) "Approve Leave Request" else "Decline Leave Request") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Text("${request.employeeName} • ${request.leaveTypeName} (${request.totalDays.toInt()}d)")
          Text(
            text = "${request.startDate} → ${request.endDate}",
            style = MonoTelemetryStyle,
          )
          OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            label = { Text("Reviewer Note (optional)") },
            modifier = Modifier.fillMaxWidth(),
          )
        }
      },
      confirmButton = {
        Button(
          onClick = {
            viewModel.reviewTeamRequest(request.id, approve, note)
            reviewTarget = null
          },
          colors =
            ButtonDefaults.buttonColors(
              containerColor =
                if (approve) PulseTheme.semantic.verified else MaterialTheme.colorScheme.error
            ),
        ) {
          Text(if (approve) "Approve" else "Decline")
        }
      },
      dismissButton = {
        TextButton(onClick = { reviewTarget = null }) { Text("Cancel") }
      },
    )
  }

  Scaffold(
    floatingActionButton = {
      FloatingActionButton(
        onClick = { showApplyDialog = true },
        modifier = Modifier.testTag("apply_leave_fab"),
      ) {
        Icon(Icons.Filled.Add, contentDescription = "Apply for Leave")
      }
    }
  ) { innerPadding ->
    Box(
      modifier = Modifier.fillMaxSize().padding(innerPadding),
      contentAlignment = Alignment.TopCenter,
    ) {
      LazyColumn(
        modifier = Modifier.widthIn(max = 840.dp).fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        item {
          Text(
            text = "Leave Management",
            style = MaterialTheme.typography.headlineMedium,
          )
        }

        if (!feedback.isNullOrBlank()) {
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
                  text = feedback!!,
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onPrimaryContainer,
                  modifier = Modifier.weight(1f),
                )
                TextButton(onClick = viewModel::clearFeedback) { Text("OK") }
              }
            }
          }
        }

        // Leave Balances Carousel (hr_leave_balances)
        item {
          Text(
            text = "Real-Time Leave Quotas",
            style = MaterialTheme.typography.titleMedium,
          )
          Spacer(modifier = Modifier.height(8.dp))
          LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(balances, key = { it.id }) { bal ->
              LeaveBalanceCard(balance = bal)
            }
          }
        }

        if (session.canApproveAnything()) {
          item {
            TabRow(selectedTabIndex = selectedTab) {
              Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("My Requests (${myRequests.size})") },
              )
              Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = {
                  val pendingCnt = teamRequests.count { it.status == LeaveStatus.PENDING }
                  Text("Team Approvals ($pendingCnt)")
                },
              )
            }
          }
        }

        item {
          val calendarRequests =
            if (selectedTab == 1 && session.canApproveAnything()) teamRequests else myRequests
          LeaveCalendarView(requests = calendarRequests)
        }

        if (selectedTab == 0 || !session.canApproveAnything()) {
          item {
            FlowRow(
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              FilterChip(
                selected = statusFilter == null,
                onClick = { statusFilter = null },
                label = { Text("All") },
              )
              LeaveStatus.entries.forEach { st ->
                FilterChip(
                  selected = statusFilter == st,
                  onClick = { statusFilter = if (statusFilter == st) null else st },
                  label = { Text(st.label) },
                )
              }
            }
          }

          val filtered =
            if (statusFilter == null) myRequests else myRequests.filter { it.status == statusFilter }

          if (filtered.isEmpty()) {
            item {
              EmptyLeavesCard(
                message = "No leave requests match this filter. Tap + to apply for leave."
              )
            }
          } else {
            items(filtered, key = { it.id }) { req ->
              LeaveRequestCard(
                request = req,
                isTeamQueue = false,
                onCancel = { viewModel.cancelRequest(req.id) },
                onApprove = {},
                onReject = {},
              )
            }
          }
        } else {
          if (teamRequests.isEmpty()) {
            item { EmptyLeavesCard(message = "No team leave requests awaiting review.") }
          } else {
            items(teamRequests, key = { it.id }) { req ->
              LeaveRequestCard(
                request = req,
                isTeamQueue = true,
                isSelfSubmitted = req.employeeId == session.employeeId,
                onCancel = {},
                onApprove = { reviewTarget = req to true },
                onReject = { reviewTarget = req to false },
              )
            }
          }
        }

        item { Spacer(modifier = Modifier.height(64.dp)) }
      }
    }
  }
}

@Composable
private fun LeaveBalanceCard(balance: LeaveBalance) {
  val progress =
    if (balance.allocatedDays > 0) (balance.remainingDays / balance.allocatedDays).toFloat().coerceIn(0f, 1f)
    else 0f

  Card(
    modifier = Modifier.width(188.dp),
    shape = MaterialTheme.shapes.medium,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
  ) {
    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        StatusPill(
          text = balance.leaveCode,
          containerColor = MaterialTheme.colorScheme.primaryContainer,
          contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Text(
          text = "${balance.remainingDays.toInt()} / ${balance.allocatedDays.toInt()}d",
          style = MonoMetricStyle,
        )
      }
      Text(
        text = balance.leaveTypeName,
        style = MaterialTheme.typography.titleSmall,
        maxLines = 1,
      )
      LinearProgressIndicator(
        progress = { progress },
        modifier = Modifier.fillMaxWidth().height(6.dp),
      )
      Text(
        text = "Used: ${balance.usedDays.toInt()}d • Pending: ${balance.pendingDays.toInt()}d",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
private fun LeaveRequestCard(
  request: LeaveRequest,
  isTeamQueue: Boolean,
  isSelfSubmitted: Boolean = false,
  onCancel: () -> Unit,
  onApprove: () -> Unit,
  onReject: () -> Unit,
) {
  val semantic = PulseTheme.semantic
  val (bg, fg) =
    when (request.status) {
      LeaveStatus.APPROVED -> semantic.verifiedContainer to semantic.onVerifiedContainer
      LeaveStatus.PENDING -> semantic.warningContainer to semantic.onWarningContainer
      LeaveStatus.REJECTED -> semantic.dangerContainer to semantic.onDangerContainer
      LeaveStatus.CANCELLED ->
        MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }

  Card(
    modifier = Modifier.fillMaxWidth(),
    shape = MaterialTheme.shapes.medium,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
  ) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          if (isTeamQueue) {
            Text(text = request.employeeName, style = MaterialTheme.typography.titleMedium)
          }
          Text(
            text = request.leaveTypeName,
            style = if (isTeamQueue) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleMedium,
          )
        }
        Row(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          if (request.syncStatus == SyncStatus.PENDING_SYNC) {
            StatusPill(
              text = "Pending Sync",
              containerColor = semantic.warningContainer,
              contentColor = semantic.onWarningContainer,
              icon = Icons.Filled.Sync,
            )
          }
          StatusPill(text = request.status.label, containerColor = bg, contentColor = fg)
        }
      }

      Text(
        text = "${request.startDate} → ${request.endDate}  (${request.totalDays.toInt()} day(s))",
        style = MonoTelemetryStyle,
        color = MaterialTheme.colorScheme.primary,
      )
      Text(
        text = request.reason,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      if (!request.reviewerNote.isNullOrBlank()) {
        Text(
          text = "Note: ${request.reviewerNote}",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      if (!isTeamQueue && request.status == LeaveStatus.PENDING) {
        OutlinedButton(
          onClick = onCancel,
          modifier = Modifier.testTag("cancel_leave_${request.id}"),
        ) {
          Text("Cancel Request")
        }
      }

      if (isTeamQueue && request.status == LeaveStatus.PENDING) {
        if (isSelfSubmitted) {
          StatusPill(
            text = "Self-request locked (peer approval required)",
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
          )
        } else {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            OutlinedButton(
              onClick = onReject,
              modifier = Modifier.weight(1f).testTag("reject_leave_${request.id}"),
            ) {
              Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(4.dp))
              Text("Decline")
            }
            Button(
              onClick = onApprove,
              colors = ButtonDefaults.buttonColors(containerColor = semantic.verified),
              modifier = Modifier.weight(1f).testTag("approve_leave_${request.id}"),
            ) {
              Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(4.dp))
              Text("Approve")
            }
          }
        }
      }
    }
  }
}

@Composable
private fun EmptyLeavesCard(message: String) {
  Card(
    modifier = Modifier.fillMaxWidth(),
    shape = MaterialTheme.shapes.medium,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(28.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Icon(
        Icons.Filled.EventNote,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(36.dp),
      )
      Text(text = message, style = MaterialTheme.typography.bodyMedium)
    }
  }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun ApplyLeaveDialog(
  balances: List<LeaveBalance>,
  onDismiss: () -> Unit,
  onSubmitWithBiometric: (leaveTypeId: String, start: String, end: String, days: Double, reason: String) -> Unit,
) {
  var selectedTypeId by remember(balances) {
    mutableStateOf(balances.firstOrNull()?.leaveTypeId ?: "LT_ANNUAL")
  }
  var startDate by remember { mutableStateOf("2026-11-20") }
  var endDate by remember { mutableStateOf("2026-11-21") }
  var totalDays by remember { mutableStateOf(2.0) }
  var reason by remember { mutableStateOf("") }
  var showDatePicker by remember { mutableStateOf(false) }

  if (showDatePicker) {
    val pickerState = rememberDateRangePickerState()
    DatePickerDialog(
      onDismissRequest = { showDatePicker = false },
      confirmButton = {
        TextButton(
          onClick = {
            val s = pickerState.selectedStartDateMillis
            val e = pickerState.selectedEndDateMillis ?: s
            if (s != null && e != null) {
              val fmt =
                SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                  timeZone = TimeZone.getTimeZone("UTC")
                }
              startDate = fmt.format(Date(s))
              endDate = fmt.format(Date(e))
              totalDays = max(1.0, ((e - s) / 86_400_000L + 1).toDouble())
            }
            showDatePicker = false
          }
        ) {
          Text("Apply Dates")
        }
      },
      dismissButton = {
        TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
      },
    ) {
      DateRangePicker(state = pickerState, modifier = Modifier.height(420.dp))
    }
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Apply for Leave") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Leave Type", style = MaterialTheme.typography.labelMedium)
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          balances.forEach { bal ->
            FilterChip(
              selected = selectedTypeId == bal.leaveTypeId,
              onClick = { selectedTypeId = bal.leaveTypeId },
              label = { Text("${bal.leaveCode} (${bal.remainingDays.toInt()}d)") },
            )
          }
        }

        OutlinedButton(
          onClick = { showDatePicker = true },
          modifier = Modifier.fillMaxWidth().testTag("pick_leave_dates_button"),
        ) {
          Icon(Icons.Filled.DateRange, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(8.dp))
          Text("$startDate → $endDate (${totalDays.toInt()}d)")
        }

        OutlinedTextField(
          value = reason,
          onValueChange = { reason = it },
          label = { Text("Reason for Leave") },
          minLines = 2,
          modifier = Modifier.fillMaxWidth().testTag("leave_reason_input"),
        )
      }
    },
    confirmButton = {
      Button(
        onClick = { onSubmitWithBiometric(selectedTypeId, startDate, endDate, totalDays, reason) },
        enabled = reason.isNotBlank(),
        modifier = Modifier.testTag("submit_leave_biometric_button"),
      ) {
        Icon(Icons.Filled.Fingerprint, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text("Verify & Submit")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancel") }
    },
  )
}
