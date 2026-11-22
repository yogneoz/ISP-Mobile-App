package com.example.ui.screens.workplace

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
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
import com.example.domain.model.ExpenseClaim
import com.example.domain.model.SyncStatus
import com.example.ui.components.StatusPill
import com.example.ui.components.SubmitExpenseClaimDialog
import com.example.ui.theme.MonoMetricStyle
import com.example.ui.theme.MonoTelemetryStyle
import com.example.ui.theme.PulseTheme
import java.util.Locale

@Composable
fun ClaimsScreen(
  onOpenReceiptScanner: () -> Unit,
  viewModel: WorkplaceViewModel = hiltViewModel(),
) {
  val claims by viewModel.claims.collectAsStateWithLifecycle()
  val feedback by viewModel.feedback.collectAsStateWithLifecycle()
  var showSubmitDialog by remember { mutableStateOf(false) }

  if (showSubmitDialog) {
    SubmitExpenseClaimDialog(
      onDismiss = { showSubmitDialog = false },
      onSubmit = { category, amount, ticketCode, description, attachReceipt ->
        viewModel.submitExpenseClaim(category, amount, ticketCode, description, attachReceipt)
        showSubmitDialog = false
      },
    )
  }

  Scaffold(
    floatingActionButton = {
      FloatingActionButton(
        onClick = { showSubmitDialog = true },
        modifier = Modifier.testTag("new_claim_fab"),
      ) {
        Icon(Icons.Filled.Add, contentDescription = "New Claim")
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
        verticalArrangement = Arrangement.spacedBy(14.dp),
      ) {
        item {
          Text(
            text = "Claims & Overtime",
            style = MaterialTheme.typography.headlineMedium,
          )
          Text(
            text = "Fuel, travel, meal, local purchases & call-out claims with async Accounting outbox.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
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
                Text(
                  text = msg,
                  style = MaterialTheme.typography.bodySmall,
                  modifier = Modifier.weight(1f),
                )
                TextButton(onClick = viewModel::clearFeedback) { Text("OK") }
              }
            }
          }
        }

        item {
          OutlinedButton(
            onClick = onOpenReceiptScanner,
            modifier = Modifier.fillMaxWidth().testTag("claim_scan_receipt_btn"),
          ) {
            Icon(Icons.Filled.DocumentScanner, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Scan Paper Receipt for Vault")
          }
        }

        items(claims, key = { it.id }) { claim ->
          ClaimCard(claim = claim)
        }

        item { Spacer(modifier = Modifier.height(64.dp)) }
      }
    }
  }
}

@Composable
private fun ClaimCard(claim: ExpenseClaim) {
  val semantic = PulseTheme.semantic
  val (bg, fg) =
    when (claim.status) {
      "APPROVED" -> semantic.verifiedContainer to semantic.onVerifiedContainer
      "REJECTED" -> semantic.dangerContainer to semantic.onDangerContainer
      else -> semantic.warningContainer to semantic.onWarningContainer
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
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Icon(Icons.Filled.ReceiptLong, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
          Text(claim.category.label, style = MaterialTheme.typography.titleMedium)
        }
        StatusPill(text = claim.status, containerColor = bg, contentColor = fg)
      }
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Text(
          text = "$${String.format(Locale.US, "%,.2f", claim.amount)}",
          style = MonoMetricStyle,
        )
        Text(
          text = "Ticket: ${claim.ticketCode ?: "Branch Overhead"} • ${claim.submittedAt}",
          style = MonoTelemetryStyle,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      Text(
        text = claim.description,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Text(
        text =
          "Accounting Outbox: ${claim.accountingOutboxStatus}" +
            if (claim.syncStatus == SyncStatus.PENDING_SYNC) " (Queued Offline)" else "",
        style = MonoTelemetryStyle,
        color = MaterialTheme.colorScheme.primary,
      )
    }
  }
}
