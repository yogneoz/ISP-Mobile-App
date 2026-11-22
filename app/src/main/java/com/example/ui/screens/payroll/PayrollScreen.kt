package com.example.ui.screens.payroll

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.model.AuthChallengePurpose
import com.example.domain.model.PayrollCategory
import com.example.domain.model.PayrollRun
import com.example.domain.model.PayrollRunLine
import com.example.ui.components.StatusPill
import com.example.ui.theme.MonoMetricStyle
import com.example.ui.theme.MonoTelemetryStyle
import com.example.ui.theme.PulseTheme
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PayrollScreen(
  isPayrollUnlocked: Boolean,
  isOfflineSimulated: Boolean = false,
  onRequestUnlock: (AuthChallengePurpose, () -> Unit) -> Unit,
  onRelockPayroll: () -> Unit,
  viewModel: PayrollViewModel = hiltViewModel(),
) {
  val runs by viewModel.payrollRuns.collectAsStateWithLifecycle()
  var selectedIndex by remember { mutableIntStateOf(0) }

  if (isOfflineSimulated) {
    Box(
      modifier = Modifier.fillMaxSize().padding(24.dp),
      contentAlignment = Alignment.Center,
    ) {
      Card(
        modifier = Modifier.widthIn(max = 460.dp).fillMaxWidth().testTag("payroll_offline_blocked_card"),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.45f)),
      ) {
        Column(
          modifier = Modifier.padding(24.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Icon(
            imageVector = Icons.Filled.Lock,
            contentDescription = "Payslips Blocked Offline",
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(44.dp),
          )
          Text(
            text = "Payslips Blocked Offline",
            style = MaterialTheme.typography.headlineSmall,
          )
          Text(
            text =
              "Per MyWorkplace field-phone security policy, payslips are streamed transiently without shared-storage caching. Reconnect to view or export your pay statement.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
          )
        }
      }
    }
    return
  }

  if (!isPayrollUnlocked) {
    Box(
      modifier = Modifier.fillMaxSize().padding(24.dp),
      contentAlignment = Alignment.Center,
    ) {
      Card(
        modifier = Modifier.widthIn(max = 460.dp).fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
      ) {
        Column(
          modifier = Modifier.padding(24.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Icon(
            imageVector = Icons.Filled.Lock,
            contentDescription = "Payslip Locked",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(44.dp),
          )
          Text(
            text = "Payslip Vault Protected",
            style = MaterialTheme.typography.headlineSmall,
          )
          Text(
            text =
              "Salary components, SSF contributions, and TDS tax slabs are masked. Verify with biometrics or PIN to view.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
          )
          Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Row(
              modifier = Modifier.padding(14.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Text("Net Take-Home", style = MaterialTheme.typography.labelLarge)
              Text("$ •,•••.••", style = MonoMetricStyle)
            }
          }
          Button(
            onClick = { onRequestUnlock(AuthChallengePurpose.PAYSLIP_VIEW) {} },
            modifier = Modifier.fillMaxWidth().height(50.dp).testTag("unlock_payroll_button"),
          ) {
            Icon(Icons.Filled.Fingerprint, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Authenticate to View Payslips")
          }
        }
      }
    }
    return
  }

  val activeRun = runs.getOrNull(selectedIndex.coerceIn(0, (runs.size - 1).coerceAtLeast(0)))

  Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    LazyColumn(
      modifier = Modifier.widthIn(max = 840.dp).fillMaxSize(),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = "Payroll & Payslips",
            style = MaterialTheme.typography.headlineMedium,
          )
          IconButton(
            onClick = onRelockPayroll,
            modifier = Modifier.testTag("relock_payroll_button"),
          ) {
            Icon(Icons.Filled.VisibilityOff, contentDescription = "Mask Payslip")
          }
        }
      }

      if (runs.isNotEmpty()) {
        item {
          FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            runs.forEachIndexed { idx, run ->
              FilterChip(
                selected = idx == selectedIndex,
                onClick = { selectedIndex = idx },
                label = { Text(run.monthLabel) },
                modifier = Modifier.testTag("payroll_month_${run.id}"),
              )
            }
          }
        }
      }

      if (activeRun != null) {
        item { PayslipSummaryDonutCard(run = activeRun) }

        val earnings = activeRun.lines.filter { it.category == PayrollCategory.EARNING }
        val ssfLines = activeRun.lines.filter { it.category == PayrollCategory.SSF }
        val tdsLines = activeRun.lines.filter { it.category == PayrollCategory.TDS || it.category == PayrollCategory.DEDUCTION }

        item {
          PayslipSectionCard(
            title = "Earnings & Allowances",
            totalAmount = activeRun.grossPay,
            accentColor = PulseTheme.semantic.verified,
            lines = earnings,
          )
        }

        item {
          PayslipSectionCard(
            title = "SSF Statutory Contributions",
            totalAmount = activeRun.ssfTotal,
            accentColor = MaterialTheme.colorScheme.primary,
            lines = ssfLines,
          )
        }

        item {
          PayslipSectionCard(
            title = "TDS Income Tax Slabs",
            totalAmount = activeRun.tdsTax,
            accentColor = PulseTheme.semantic.warning,
            lines = tdsLines,
          )
        }
      }

      item { Spacer(modifier = Modifier.height(8.dp)) }
    }
  }
}

@Composable
private fun PayslipSummaryDonutCard(run: PayrollRun) {
  val semantic = PulseTheme.semantic
  val netColor = semantic.verified
  val ssfColor = MaterialTheme.colorScheme.primary
  val tdsColor = semantic.warning

  Card(
    modifier = Modifier.fillMaxWidth(),
    shape = MaterialTheme.shapes.large,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
  ) {
    Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column {
          Text(text = run.monthLabel, style = MaterialTheme.typography.titleLarge)
          Text(
            text = "Pay Date: ${run.payDate} • ${run.periodStart} to ${run.periodEnd}",
            style = MonoTelemetryStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        StatusPill(
          text = run.status,
          containerColor = semantic.verifiedContainer,
          contentColor = semantic.onVerifiedContainer,
        )
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
      ) {
        Box(modifier = Modifier.size(116.dp), contentAlignment = Alignment.Center) {
          Canvas(modifier = Modifier.size(116.dp)) {
            val total = run.grossPay.coerceAtLeast(1.0).toFloat()
            val netSweep = (run.netPay.toFloat() / total) * 360f
            val ssfSweep = (run.ssfTotal.toFloat() / total) * 360f
            val tdsSweep = (run.tdsTax.toFloat() / total) * 360f
            val stroke = Stroke(width = 22f, cap = StrokeCap.Butt)

            drawArc(
              color = netColor,
              startAngle = -90f,
              sweepAngle = netSweep,
              useCenter = false,
              style = stroke,
            )
            drawArc(
              color = ssfColor,
              startAngle = -90f + netSweep,
              sweepAngle = ssfSweep,
              useCenter = false,
              style = stroke,
            )
            drawArc(
              color = tdsColor,
              startAngle = -90f + netSweep + ssfSweep,
              sweepAngle = tdsSweep,
              useCenter = false,
              style = stroke,
            )
          }
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("NET", style = MaterialTheme.typography.labelSmall)
            Text(
              text = "$${String.format(Locale.US, "%,.0f", run.netPay)}",
              style = MonoMetricStyle.copy(fontSize = 16.sp),
            )
          }
        }

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          BreakdownMetricRow("Gross Pay", run.grossPay, MaterialTheme.colorScheme.onSurface)
          BreakdownMetricRow("Net Take-Home", run.netPay, netColor)
          BreakdownMetricRow("SSF Deduction", run.ssfTotal, ssfColor)
          BreakdownMetricRow("TDS Tax", run.tdsTax, tdsColor)
        }
      }
    }
  }
}

@Composable
private fun BreakdownMetricRow(label: String, amount: Double, color: Color) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
      text = "$${String.format(Locale.US, "%,.2f", amount)}",
      style = MonoTelemetryStyle,
      fontWeight = FontWeight.Bold,
      color = color,
    )
  }
}

@Composable
private fun PayslipSectionCard(
  title: String,
  totalAmount: Double,
  accentColor: Color,
  lines: List<PayrollRunLine>,
) {
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
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        Text(
          text = "$${String.format(Locale.US, "%,.2f", totalAmount)}",
          style = MonoTelemetryStyle,
          fontWeight = FontWeight.Bold,
          color = accentColor,
        )
      }
      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
      lines.forEach { line ->
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.Top,
        ) {
          Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(text = line.componentName, style = MaterialTheme.typography.bodyMedium)
            Text(
              text = line.description,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          Text(
            text = "$${String.format(Locale.US, "%,.2f", line.amount)}",
            style = MonoTelemetryStyle,
            color = MaterialTheme.colorScheme.onSurface,
          )
        }
      }
    }
  }
}
