package com.example.ui.screens.workplace

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SupervisorAccount
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.model.AppCapability
import com.example.domain.model.ApprovalItem
import com.example.domain.model.NeedsAttentionItem
import com.example.domain.model.UserSession
import com.example.domain.model.WorkOrder
import com.example.ui.components.StatusPill
import com.example.ui.theme.MonoTelemetryStyle
import com.example.ui.theme.PulseTheme

private enum class OpsSection(val label: String) {
  NEEDS_ATTENTION("Needs Attention"),
  NOTICES("Notices"),
  DISPATCH("Dispatch"),
  ROSTER("Roster"),
  APPROVALS("Approvals"),
  STOCK_AUDIT("Stock Audit"),
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OpsHubScreen(
  session: UserSession,
  initialSectionName: String? = null,
  onOpenAuditLog: () -> Unit = {},
  viewModel: WorkplaceViewModel = hiltViewModel(),
) {
  val needsAttention by viewModel.needsAttention.collectAsStateWithLifecycle()
  val announcements by viewModel.announcements.collectAsStateWithLifecycle()
  val workOrders by viewModel.workOrders.collectAsStateWithLifecycle()
  val roster by viewModel.rosterEntries.collectAsStateWithLifecycle()
  val approvals by viewModel.approvals.collectAsStateWithLifecycle()
  val delegations by viewModel.delegations.collectAsStateWithLifecycle()
  val auditRows by viewModel.stockAuditRows.collectAsStateWithLifecycle()
  val feedback by viewModel.feedback.collectAsStateWithLifecycle()

  val availableSections =
    remember(session.capabilities) {
      buildList {
        add(OpsSection.NEEDS_ATTENTION)
        if (session.hasCapability(AppCapability.DISPATCH_ASSIGN)) add(OpsSection.DISPATCH)
        if (session.hasCapability(AppCapability.ROSTER_MANAGE)) add(OpsSection.ROSTER)
        if (
          session.hasCapability(AppCapability.APPROVE_ATTENDANCE_OT) ||
            session.hasCapability(AppCapability.APPROVE_EXPENSE)
        ) {
          add(OpsSection.APPROVALS)
        }
        if (session.hasCapability(AppCapability.AUDIT_STOCK)) add(OpsSection.STOCK_AUDIT)
        add(OpsSection.NOTICES)
      }
    }

  var activeSection by
    remember(availableSections, initialSectionName) {
      val parsed =
        initialSectionName?.let { runCatching { OpsSection.valueOf(it) }.getOrNull() }
          ?.takeIf { it in availableSections }
      mutableStateOf(parsed ?: availableSections.first())
    }

  var resolveTarget by remember { mutableStateOf<NeedsAttentionItem?>(null) }
  var dispatchTarget by remember { mutableStateOf<WorkOrder?>(null) }
  var showDelegationDialog by remember { mutableStateOf(false) }

  resolveTarget?.let { item ->
    var correctedSerial by remember(item.id) { mutableStateOf("ONT-2026-8842") }
    AlertDialog(
      onDismissRequest = { resolveTarget = null },
      title = { Text("Resolve ${item.errorCode}") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(item.message, style = MaterialTheme.typography.bodySmall)
          Text(
            text = "Preserved capture: ${item.capturedSummary}",
            style = MonoTelemetryStyle,
            color = MaterialTheme.colorScheme.primary,
          )
          if (item.errorCode == "SERIAL_NOT_IN_VAN") {
            OutlinedTextField(
              value = correctedSerial,
              onValueChange = { correctedSerial = it },
              label = { Text("Corrected Van Serial") },
              singleLine = true,
              modifier = Modifier.fillMaxWidth().testTag("na_fix_serial_input"),
            )
          }
        }
      },
      confirmButton = {
        Button(
          onClick = {
            viewModel.resolveNeedsAttention(
              item.id,
              if (item.errorCode == "SERIAL_NOT_IN_VAN") correctedSerial else null,
            )
            resolveTarget = null
          },
          modifier = Modifier.testTag("na_resolve_confirm"),
        ) {
          Text(if (item.errorCode == "SERIAL_NOT_IN_VAN") "Correct & Replay" else "Mark Resolved")
        }
      },
      dismissButton = {
        TextButton(onClick = { resolveTarget = null }) { Text("Cancel") }
      },
    )
  }

  dispatchTarget?.let { order ->
    var selectedCrewId by remember { mutableStateOf("emp_207") }
    var selectedCrewName by remember { mutableStateOf("Carlos Mendez") }
    var leaveWarning by remember { mutableStateOf("Half-Day Approved Leave Today") }
    var overrideReason by remember { mutableStateOf("") }

    AlertDialog(
      onDismissRequest = { dispatchTarget = null },
      title = { Text("Dispatch ${order.ticketCode}") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text("Assign crew for ${order.customerName} (${session.branchScope})")
          roster.forEach { entry ->
            FilterChip(
              selected = selectedCrewId == entry.employeeId,
              onClick = {
                selectedCrewId = entry.employeeId
                selectedCrewName = entry.employeeName
                leaveWarning = entry.leaveStatusWarning ?: ""
              },
              label = {
                Text(
                  entry.employeeName +
                    (entry.leaveStatusWarning?.let { " • Leave Alert" } ?: " • Ready")
                )
              },
            )
          }
          if (leaveWarning.isNotBlank()) {
            Surface(
              shape = MaterialTheme.shapes.small,
              color = PulseTheme.semantic.warningContainer,
            ) {
              Text(
                text = "Advisory HR Projection: $leaveWarning. Override reason is logged.",
                style = MaterialTheme.typography.bodySmall,
                color = PulseTheme.semantic.onWarningContainer,
                modifier = Modifier.padding(8.dp),
              )
            }
            OutlinedTextField(
              value = overrideReason,
              onValueChange = { overrideReason = it },
              label = { Text("Logged Override Reason *") },
              singleLine = true,
              modifier = Modifier.fillMaxWidth().testTag("dispatch_override_reason"),
            )
          }
        }
      },
      confirmButton = {
        Button(
          onClick = {
            viewModel.assignDispatchTicket(
              workOrderId = order.id,
              assigneeEmployeeId = selectedCrewId,
              assigneeName = selectedCrewName,
              overrideLeaveWarning = leaveWarning.isNotBlank(),
              overrideReason = overrideReason,
            )
            dispatchTarget = null
          },
          enabled = leaveWarning.isBlank() || overrideReason.isNotBlank(),
          modifier = Modifier.testTag("dispatch_confirm_btn"),
        ) {
          Text("Assign Crew")
        }
      },
      dismissButton = {
        TextButton(onClick = { dispatchTarget = null }) { Text("Cancel") }
      },
    )
  }

  if (showDelegationDialog) {
    var delegateName by remember { mutableStateOf("Sita Gurung") }
    var endDate by remember { mutableStateOf("2026-12-05") }
    AlertDialog(
      onDismissRequest = { showDelegationDialog = false },
      title = { Text("Delegate Approvals (Time-Bounded)") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Text(
            text = "Delegated approvals carry an audit tag indicating the delegator and end date.",
            style = MaterialTheme.typography.bodySmall,
          )
          OutlinedTextField(
            value = delegateName,
            onValueChange = { delegateName = it },
            label = { Text("Delegate Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )
          OutlinedTextField(
            value = endDate,
            onValueChange = { endDate = it },
            label = { Text("Delegation End Date (YYYY-MM-DD)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )
        }
      },
      confirmButton = {
        Button(
          onClick = {
            viewModel.createDelegation("emp_102", delegateName, endDate)
            showDelegationDialog = false
          }
        ) {
          Text("Activate Delegation")
        }
      },
      dismissButton = {
        TextButton(onClick = { showDelegationDialog = false }) { Text("Cancel") }
      },
    )
  }

  Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    LazyColumn(
      modifier = Modifier.widthIn(max = 840.dp).fillMaxSize(),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = "Workspace & Governance",
              style = MaterialTheme.typography.headlineMedium,
            )
            Text(
              text = "Scope: ${session.branchScope} • Modules shown by granted capabilities",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          Spacer(modifier = Modifier.width(8.dp))
          OutlinedButton(
            onClick = onOpenAuditLog,
            modifier =
              Modifier
                .heightIn(min = 48.dp)
                .testTag("open_audit_log_from_ops_button"),
          ) {
            Icon(Icons.Filled.FactCheck, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Audit Log")
          }
        }
      }

      if (feedback != null) {
        val (msg, isError) = feedback!!
        item {
          Surface(
            shape = MaterialTheme.shapes.medium,
            color =
              if (isError) MaterialTheme.colorScheme.errorContainer
              else PulseTheme.semantic.verifiedContainer,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(msg, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
              TextButton(onClick = viewModel::clearFeedback) { Text("OK") }
            }
          }
        }
      }

      item {
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          availableSections.forEach { sec ->
            FilterChip(
              selected = activeSection == sec,
              onClick = { activeSection = sec },
              label = {
                if (sec == OpsSection.NEEDS_ATTENTION) {
                  val openCount = needsAttention.count { !it.resolved }
                  Text("${sec.label} ($openCount)")
                } else {
                  Text(sec.label)
                }
              },
              modifier = Modifier.testTag("ops_tab_${sec.name}"),
            )
          }
        }
      }

      when (activeSection) {
        OpsSection.NEEDS_ATTENTION -> {
          if (needsAttention.isEmpty()) {
            item {
              Text(
                "All captured sync items and held punches resolved.",
                style = MaterialTheme.typography.bodyMedium,
              )
            }
          } else {
            items(needsAttention, key = { it.id }) { item ->
              NeedsAttentionCard(item = item, onResolve = { resolveTarget = item })
            }
          }
        }

        OpsSection.NOTICES -> {
          items(announcements, key = { it.id }) { ann ->
            Card(
              modifier = Modifier.fillMaxWidth(),
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
              border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
              Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                  StatusPill(
                    text = ann.category,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    icon = Icons.Filled.Campaign,
                  )
                  Text(ann.publishedAt, style = MaterialTheme.typography.bodySmall)
                }
                Text(ann.title, style = MaterialTheme.typography.titleSmall)
                Text(ann.summary, style = MaterialTheme.typography.bodySmall)
              }
            }
          }
        }

        OpsSection.DISPATCH -> {
          items(workOrders, key = { it.id }) { wo ->
            Card(
              modifier = Modifier.fillMaxWidth(),
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
              border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
              Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Text("${wo.ticketCode} • ${wo.customerName}", style = MaterialTheme.typography.titleSmall)
                  Text(
                    "Assigned: ${wo.assignedEmployeeName} • SLA ${wo.slaDeadlineLabel}",
                    style = MonoTelemetryStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
                OutlinedButton(
                  onClick = { dispatchTarget = wo },
                  modifier = Modifier.testTag("dispatch_assign_${wo.id}"),
                ) {
                  Text("Reassign")
                }
              }
            }
          }
        }

        OpsSection.ROSTER -> {
          items(roster, key = { it.id }) { entry ->
            Card(
              modifier = Modifier.fillMaxWidth(),
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
              border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
              Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                  Text(entry.employeeName, style = MaterialTheme.typography.titleSmall)
                  if (entry.standby24x7) {
                    StatusPill(
                      text = "24/7 Standby",
                      containerColor = PulseTheme.semantic.warningContainer,
                      contentColor = PulseTheme.semantic.onWarningContainer,
                      icon = Icons.Filled.Schedule,
                    )
                  }
                }
                Text("${entry.roleLabel} • ${entry.shiftLabel} (${entry.assignedBranchCode})", style = MaterialTheme.typography.bodySmall)
                if (!entry.leaveStatusWarning.isNullOrBlank()) {
                  Text(
                    text = "HR Read-Only Projection: ${entry.leaveStatusWarning}",
                    style = MonoTelemetryStyle,
                    color = PulseTheme.semantic.warning,
                  )
                }
              }
            }
          }
        }

        OpsSection.APPROVALS -> {
          item {
            Card(
              modifier = Modifier.fillMaxWidth(),
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            ) {
              Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Filled.SwapHoriz, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Delegations (${delegations.size})", style = MaterialTheme.typography.titleSmall)
                  }
                  TextButton(onClick = { showDelegationDialog = true }) { Text("Delegate") }
                }
                delegations.forEach { del ->
                  Text(
                    text = "${del.delegatorName} → ${del.delegateName} (until ${del.endDate})",
                    style = MonoTelemetryStyle,
                  )
                }
              }
            }
          }

          items(approvals, key = { it.id }) { apr ->
            ApprovalCard(
              item = apr,
              session = session,
              onApprove = { viewModel.actOnApproval(apr.id, approve = true, note = "Approved") },
              onReject = { viewModel.actOnApproval(apr.id, approve = false, note = "Declined") },
            )
          }
        }

        OpsSection.STOCK_AUDIT -> {
          items(auditRows, key = { it.id }) { row ->
            Card(
              modifier = Modifier.fillMaxWidth(),
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
              border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
              Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                  Text(row.itemName, style = MaterialTheme.typography.titleSmall)
                  StatusPill(
                    text = if (row.variance == 0) "Reconciled" else "Variance ${row.variance}",
                    containerColor =
                      if (row.variance == 0) PulseTheme.semantic.verifiedContainer
                      else PulseTheme.semantic.warningContainer,
                    contentColor =
                      if (row.variance == 0) PulseTheme.semantic.onVerifiedContainer
                      else PulseTheme.semantic.onWarningContainer,
                    icon = Icons.Filled.FactCheck,
                  )
                }
                Text(
                  text = "${row.technicianName} • Issued ${row.warehouseIssuedQty} • Consumed ${row.ticketConsumedQty} • Van ${row.vanBalanceQty}",
                  style = MonoTelemetryStyle,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          }
        }
      }

      item { Spacer(modifier = Modifier.height(12.dp)) }
    }
  }
}

@Composable
private fun NeedsAttentionCard(
  item: NeedsAttentionItem,
  onResolve: () -> Unit,
) {
  val semantic = PulseTheme.semantic
  Card(
    modifier = Modifier.fillMaxWidth().testTag("na_card_${item.id}"),
    shape = MaterialTheme.shapes.medium,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border =
      BorderStroke(
        1.dp,
        if (item.resolved) MaterialTheme.colorScheme.outlineVariant else semantic.warning,
      ),
  ) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Icon(
            Icons.Filled.ErrorOutline,
            contentDescription = null,
            tint = if (item.resolved) semantic.verified else semantic.warning,
          )
          Text(item.errorCode, style = MaterialTheme.typography.titleSmall)
        }
        StatusPill(
          text = if (item.resolved) "Resolved" else "Action Needed",
          containerColor = if (item.resolved) semantic.verifiedContainer else semantic.warningContainer,
          contentColor = if (item.resolved) semantic.onVerifiedContainer else semantic.onWarningContainer,
        )
      }
      Text(item.title, style = MaterialTheme.typography.titleMedium)
      Text(item.message, style = MaterialTheme.typography.bodySmall)
      Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Text(
          text = item.capturedSummary,
          style = MonoTelemetryStyle,
          modifier = Modifier.padding(10.dp),
        )
      }
      if (!item.resolved) {
        Button(
          onClick = onResolve,
          modifier = Modifier.fillMaxWidth().testTag("na_resolve_${item.id}"),
        ) {
          Text(if (item.errorCode == "SERIAL_NOT_IN_VAN") "Correct Serial & Replay" else "Review & Resolve")
        }
      }
    }
  }
}

@Composable
private fun ApprovalCard(
  item: ApprovalItem,
  session: UserSession,
  onApprove: () -> Unit,
  onReject: () -> Unit,
) {
  val semantic = PulseTheme.semantic
  val isSelf = item.isSelfSubmitted(session.employeeId)
  val canAct = item.canBeActedOnBy(session.employeeId, session.capabilities)

  Card(
    modifier = Modifier.fillMaxWidth().testTag("approval_card_${item.id}"),
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
        Text(item.requesterName, style = MaterialTheme.typography.titleSmall)
        StatusPill(
          text = if (isSelf) "Self-Approval Blocked" else item.status,
          containerColor =
            when {
              isSelf -> semantic.dangerContainer
              item.status == "APPROVED" -> semantic.verifiedContainer
              item.status == "REJECTED" -> semantic.dangerContainer
              else -> semantic.warningContainer
            },
          contentColor =
            when {
              isSelf -> semantic.onDangerContainer
              item.status == "APPROVED" -> semantic.onVerifiedContainer
              item.status == "REJECTED" -> semantic.onDangerContainer
              else -> semantic.onWarningContainer
            },
          icon = if (isSelf) Icons.Filled.Block else Icons.Filled.SupervisorAccount,
        )
      }
      Text("${item.summary} (${item.metricLabel})", style = MaterialTheme.typography.bodyMedium)
      Text(item.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      if (!item.delegatedFrom.isNullOrBlank()) {
        Text(
          text = "Delegated Authority from ${item.delegatedFrom}",
          style = MonoTelemetryStyle,
          color = MaterialTheme.colorScheme.primary,
        )
      }
      if (item.status == "PENDING") {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedButton(
            onClick = onReject,
            enabled = canAct,
            modifier = Modifier.weight(1f),
          ) {
            Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Decline")
          }
          Button(
            onClick = onApprove,
            enabled = canAct,
            colors = ButtonDefaults.buttonColors(containerColor = semantic.verified),
            modifier = Modifier.weight(1f),
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
