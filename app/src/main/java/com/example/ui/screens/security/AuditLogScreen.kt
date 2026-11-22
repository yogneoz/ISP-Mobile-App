package com.example.ui.screens.security

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PhonelinkLock
import androidx.compose.material.icons.filled.Pin
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.model.AuditLogEntry
import com.example.domain.model.AuditOutcome
import com.example.security.BiometricAvailability
import com.example.ui.components.StatusPill
import com.example.ui.theme.MonoMetricStyle
import com.example.ui.theme.MonoTelemetryStyle
import com.example.ui.theme.PulseTheme

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AuditLogScreen(
  onBack: () -> Unit,
  onTriggerBiometricVerify: () -> Unit,
  viewModel: AuditLogViewModel = hiltViewModel(),
) {
  val selectedFilter by viewModel.selectedFilter.collectAsStateWithLifecycle()
  val logs by viewModel.filteredLogs.collectAsStateWithLifecycle()
  val summary by viewModel.summary.collectAsStateWithLifecycle()
  val semantic = PulseTheme.semantic

  Scaffold(
    contentWindowInsets = WindowInsets(0, 0, 0, 0),
    topBar = {
      TopAppBar(
        title = {
          Column {
            Text("Security Audit Log", style = MaterialTheme.typography.titleLarge)
            Text(
              text = "${summary.totalEvents} recorded security event(s)",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        },
        navigationIcon = {
          IconButton(
            onClick = onBack,
            modifier = Modifier.minimumInteractiveComponentSize().testTag("audit_log_back_button"),
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = "Navigate back",
            )
          }
        },
      )
    },
  ) { padding ->
    Box(
      modifier = Modifier.fillMaxSize().padding(padding),
      contentAlignment = Alignment.TopCenter,
    ) {
      LazyColumn(
        modifier = Modifier.widthIn(max = 840.dp).fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        item {
          SecurityPostureSummaryCard(
            summary = summary,
            onTriggerBiometricVerify = onTriggerBiometricVerify,
          )
        }

        item {
          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            AuditLogFilter.entries.forEach { filter ->
              FilterChip(
                selected = selectedFilter == filter,
                onClick = { viewModel.setFilter(filter) },
                label = { Text(filter.label) },
                modifier = Modifier.testTag("audit_filter_${filter.name}"),
              )
            }
          }
        }

        if (logs.isEmpty()) {
          item {
            Card(
              modifier = Modifier.fillMaxWidth().testTag("audit_log_empty_state"),
              shape = MaterialTheme.shapes.large,
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
              border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
              Column(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                Icon(
                  imageVector = Icons.Filled.Security,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.size(36.dp),
                )
                Text(
                  text = "No matching audit records",
                  style = MaterialTheme.typography.titleMedium,
                )
                Text(
                  text =
                    "Run a live biometric verification above or switch filter tabs to view device binding and sensitive access logs.",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          }
        } else {
          items(logs, key = { it.id }) { entry ->
            AuditLogEventCard(entry = entry, semantic = semantic)
          }
        }

        item { Spacer(modifier = Modifier.height(12.dp)) }
      }
    }
  }
}

@Composable
private fun SecurityPostureSummaryCard(
  summary: AuditLogSummary,
  onTriggerBiometricVerify: () -> Unit,
) {
  val semantic = PulseTheme.semantic
  val heroStart = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
  val heroEnd = MaterialTheme.colorScheme.surface

  Card(
    modifier = Modifier.fillMaxWidth().testTag("audit_log_summary_card"),
    shape = MaterialTheme.shapes.large,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
  ) {
    Column(
      modifier =
        Modifier.fillMaxWidth()
          .background(Brush.verticalGradient(listOf(heroStart, heroEnd)))
          .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
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
          Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(40.dp),
          ) {
            Box(contentAlignment = Alignment.Center) {
              Icon(
                imageVector = Icons.Filled.PhonelinkLock,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
              )
            }
          }
          Column {
            Text(
              text = "Biometric & Device Oversight",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
            )
            Text(
              text = summary.deviceModel.ifBlank { "Android Security Enclave" },
              style = MonoTelemetryStyle,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
        val sensorLabel =
          when (summary.biometricAvailability) {
            BiometricAvailability.AVAILABLE_STRONG -> "Class 3 Ready"
            BiometricAvailability.AVAILABLE_WEAK_OR_FACE -> "Biometric Ready"
            BiometricAvailability.DEVICE_CREDENTIAL_ONLY -> "Device PIN"
            else -> "PIN Fallback"
          }
        StatusPill(
          text = sensorLabel,
          containerColor = semantic.verifiedContainer,
          contentColor = semantic.onVerifiedContainer,
          icon = Icons.Filled.VerifiedUser,
        )
      }

      Text(
        text = "Registered Device ID: ${summary.deviceId}",
        style = MonoTelemetryStyle,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        AuditCountMetricTile(
          label = "Verified",
          count = summary.biometricSuccessCount,
          containerColor = semantic.verifiedContainer,
          contentColor = semantic.onVerifiedContainer,
          modifier = Modifier.weight(1f),
        )
        AuditCountMetricTile(
          label = "Failed",
          count = summary.biometricFailedCount,
          containerColor = semantic.dangerContainer,
          contentColor = semantic.onDangerContainer,
          modifier = Modifier.weight(1f),
        )
        AuditCountMetricTile(
          label = "PIN / Cancel",
          count = summary.fallbackOrCancelledCount,
          containerColor = semantic.warningContainer,
          contentColor = semantic.onWarningContainer,
          modifier = Modifier.weight(1f),
        )
      }

      Button(
        onClick = onTriggerBiometricVerify,
        modifier =
          Modifier.fillMaxWidth()
            .heightIn(min = 48.dp)
            .testTag("audit_run_biometric_check_button"),
      ) {
        Icon(Icons.Filled.Fingerprint, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text("Run Live Biometric Verification")
      }
    }
  }
}

@Composable
private fun AuditCountMetricTile(
  label: String,
  count: Int,
  containerColor: Color,
  contentColor: Color,
  modifier: Modifier = Modifier,
) {
  Surface(
    modifier = modifier,
    shape = MaterialTheme.shapes.medium,
    color = containerColor.copy(alpha = 0.78f),
  ) {
    Column(
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
      verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
      Text(
        text = count.toString(),
        style = MonoMetricStyle,
        color = contentColor,
        fontWeight = FontWeight.Bold,
      )
      Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = contentColor,
      )
    }
  }
}

@Composable
private fun AuditLogEventCard(
  entry: AuditLogEntry,
  semantic: com.example.ui.theme.PulseSemanticColors,
) {
  val (pillBg, pillFg, badgeIcon) =
    when (entry.outcome) {
      AuditOutcome.SUCCESS ->
        Triple(semantic.verifiedContainer, semantic.onVerifiedContainer, Icons.Filled.CheckCircle)
      AuditOutcome.FAILED ->
        Triple(semantic.dangerContainer, semantic.onDangerContainer, Icons.Filled.Error)
      AuditOutcome.CANCELLED ->
        Triple(semantic.warningContainer, semantic.onWarningContainer, Icons.Filled.Cancel)
      AuditOutcome.FALLBACK ->
        Triple(semantic.warningContainer, semantic.onWarningContainer, Icons.Filled.Pin)
      AuditOutcome.INFO ->
        Triple(
          MaterialTheme.colorScheme.primaryContainer,
          MaterialTheme.colorScheme.onPrimaryContainer,
          Icons.Filled.Info,
        )
    }

  Card(
    modifier = Modifier.fillMaxWidth().testTag("audit_log_item_${entry.id}"),
    shape = MaterialTheme.shapes.medium,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border =
      BorderStroke(
        width = 1.dp,
        color =
          if (entry.outcome == AuditOutcome.FAILED) semantic.danger.copy(alpha = 0.55f)
          else MaterialTheme.colorScheme.outlineVariant,
      ),
  ) {
    Column(
      modifier = Modifier.padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
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
          Box(
            modifier =
              Modifier.size(32.dp)
                .clip(CircleShape)
                .background(pillBg),
            contentAlignment = Alignment.Center,
          ) {
            Icon(
              imageVector =
                if (entry.eventCategory == "BIOMETRIC_AUTH") Icons.Filled.Fingerprint else badgeIcon,
              contentDescription = null,
              tint = pillFg,
              modifier = Modifier.size(18.dp),
            )
          }
          Column {
            Text(
              text = entry.actionLabel,
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.SemiBold,
            )
            Text(
              text = entry.timestampLabel,
              style = MonoTelemetryStyle,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
        Spacer(modifier = Modifier.width(8.dp))
        StatusPill(
          text = entry.outcome.label,
          containerColor = pillBg,
          contentColor = pillFg,
          icon = badgeIcon,
        )
      }

      if (!entry.failureReason.isNullOrBlank()) {
        Surface(
          shape = MaterialTheme.shapes.small,
          color = pillBg.copy(alpha = 0.55f),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text(
            text = entry.failureReason,
            style = MaterialTheme.typography.bodySmall,
            color = pillFg,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
          )
        }
      }

      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

      Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
          text = "Method: ${entry.authMethod}  •  Context: ${entry.screenName}",
          style = MonoTelemetryStyle,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
          text =
            "Device: ${entry.deviceModel.ifBlank { "Bound Android Endpoint" }} (${entry.deviceId})",
          style = MonoTelemetryStyle,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
          text = "Actor: ${entry.actorEmail} (${entry.actorId})",
          style = MonoTelemetryStyle,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}
