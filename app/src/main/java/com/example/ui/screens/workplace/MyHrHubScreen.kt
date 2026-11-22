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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.model.UserSession
import com.example.ui.components.StatusPill
import com.example.ui.theme.MonoTelemetryStyle
import com.example.ui.theme.PulseTheme

@Composable
fun MyHrHubScreen(
  session: UserSession,
  isOfflineSimulated: Boolean,
  onOpenAttendance: () -> Unit,
  onOpenLeaves: () -> Unit,
  onOpenPayroll: () -> Unit,
  onOpenProfileDocs: () -> Unit,
  workplaceViewModel: WorkplaceViewModel = hiltViewModel(),
) {
  val auditLogs by workplaceViewModel.auditLogs.collectAsStateWithLifecycle()

  Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    LazyColumn(
      modifier = Modifier.widthIn(max = 840.dp).fillMaxSize(),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      item {
        Text(
          text = "My HR (ess.view)",
          style = MaterialTheme.typography.headlineMedium,
        )
        Text(
          text = "Personal attendance, leave, payslip & compliance documents. Sensitive records are audited & blocked offline.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      item {
        HrHubModuleCard(
          title = "Attendance, Radar & Reminder Schedule",
          subtitle = "Geofence verification, GPS integrity scoring, voice punch & work-hours arrival/departure alerts",
          icon = Icons.Filled.CalendarMonth,
          badge = "attendance.punch",
          onClick = onOpenAttendance,
          testTag = "hr_hub_attendance",
        )
      }

      item {
        HrHubModuleCard(
          title = "Leaves & Holiday Schedule",
          subtitle = "Monthly LeaveCalendarView, quotas & analytics",
          icon = Icons.Filled.EventNote,
          badge = "ess.view",
          onClick = onOpenLeaves,
          testTag = "hr_hub_leaves",
        )
      }

      item {
        HrHubModuleCard(
          title = "Payroll & Payslips (Transient Stream)",
          subtitle =
            if (isOfflineSimulated) "Blocked while offline — reconnect & verify BiometricPrompt to view"
            else "Requires Biometric / PIN re-auth • Never cached to shared storage",
          icon = Icons.Filled.Payments,
          badge = if (isOfflineSimulated) "Offline Blocked" else "Biometric + Audited",
          onClick = onOpenPayroll,
          testTag = "hr_hub_payroll",
        )
      }

      item {
        HrHubModuleCard(
          title = "Profile, Bank/PAN & Document Vault",
          subtitle = "Camera document scanner (Sobel + filters), FCM push toggles & bound device security",
          icon = Icons.Filled.AccountCircle,
          badge = session.deviceId,
          onClick = onOpenProfileDocs,
          testTag = "hr_hub_profile",
        )
      }

      item {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier.padding(top = 6.dp),
        ) {
          Icon(Icons.Filled.History, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
          Text("Recent Sensitive Access & Override Audit Log", style = MaterialTheme.typography.titleMedium)
        }
      }

      items(auditLogs.take(6), key = { it.id }) { log ->
        Surface(
          shape = MaterialTheme.shapes.medium,
          color = MaterialTheme.colorScheme.surface,
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Text(log.screenName, style = MaterialTheme.typography.labelLarge)
              Text(log.timestampLabel, style = MonoTelemetryStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(log.actionLabel, style = MaterialTheme.typography.bodySmall)
            Text(
              text = "${log.actorEmail} • Device: ${log.deviceId}",
              style = MonoTelemetryStyle,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }

      item { Spacer(modifier = Modifier.height(12.dp)) }
    }
  }
}

@Composable
private fun HrHubModuleCard(
  title: String,
  subtitle: String,
  icon: ImageVector,
  badge: String,
  onClick: () -> Unit,
  testTag: String,
) {
  Card(
    onClick = onClick,
    modifier = Modifier.fillMaxWidth().testTag(testTag),
    shape = MaterialTheme.shapes.medium,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
  ) {
    Row(
      modifier = Modifier.padding(16.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(30.dp),
      )
      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        StatusPill(
          text = badge,
          containerColor = PulseTheme.semantic.verifiedContainer.copy(alpha = 0.7f),
          contentColor = PulseTheme.semantic.onVerifiedContainer,
          icon = Icons.Filled.Security,
        )
      }
      Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
    }
  }
}
