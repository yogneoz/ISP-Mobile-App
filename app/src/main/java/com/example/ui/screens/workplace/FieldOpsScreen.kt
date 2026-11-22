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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EditLocationAlt
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.model.AppCapability
import com.example.domain.model.UserSession
import com.example.domain.model.VanStockItem
import com.example.domain.model.WorkOrder
import com.example.domain.model.WorkOrderStatus
import com.example.ui.components.StatusPill
import com.example.ui.theme.MonoTelemetryStyle
import com.example.ui.theme.PulseTheme

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FieldOpsScreen(
  session: UserSession,
  onOpenTravelMap: () -> Unit = {},
  viewModel: WorkplaceViewModel = hiltViewModel(),
) {
  val workOrders by viewModel.workOrders.collectAsStateWithLifecycle()
  val vanStock by viewModel.vanStock.collectAsStateWithLifecycle()
  val feedback by viewModel.feedback.collectAsStateWithLifecycle()

  val canJobs = session.hasCapability(AppCapability.JOBS_EXECUTE)
  val canStock = session.hasCapability(AppCapability.STOCK_VAN)
  var selectedTab by remember { mutableIntStateOf(if (canJobs) 0 else 1) }

  var completeTarget by remember { mutableStateOf<WorkOrder?>(null) }
  var routeLogTarget by remember { mutableStateOf<WorkOrder?>(null) }

  completeTarget?.let { order ->
    var opticalDbm by remember(order.id) { mutableStateOf("-18.4") }
    var otdrDb by remember(order.id) { mutableStateOf("0.42") }
    var signature by remember(order.id) { mutableStateOf("") }
    var cableMeters by remember(order.id) { mutableStateOf("45") }
    var serialUsed by remember(order.id) { mutableStateOf("ONT-2026-8841") }
    var photoCount by remember(order.id) { mutableStateOf("2") }
    var poleNote by remember(order.id) { mutableStateOf(order.poleRouteNote ?: "") }

    AlertDialog(
      onDismissRequest = { completeTarget = null },
      title = { Text("Complete ${order.ticketCode}") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(
            text = "${order.customerName} • Operational close succeeds offline/even if Accounting lags.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
              value = opticalDbm,
              onValueChange = { opticalDbm = it },
              label = { Text("Optical dBm") },
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
              singleLine = true,
              modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
              value = otdrDb,
              onValueChange = { otdrDb = it },
              label = { Text("OTDR Loss dB") },
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
              singleLine = true,
              modifier = Modifier.weight(1f),
            )
          }
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
              value = cableMeters,
              onValueChange = { cableMeters = it },
              label = { Text("Cable (m)") },
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
              singleLine = true,
              modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
              value = photoCount,
              onValueChange = { photoCount = it },
              label = { Text("Photos") },
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
              singleLine = true,
              modifier = Modifier.weight(1f),
            )
          }
          OutlinedTextField(
            value = serialUsed,
            onValueChange = { serialUsed = it },
            label = { Text("ONT Serial (van or BAD-test)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("wo_serial_input"),
          )
          OutlinedTextField(
            value = signature,
            onValueChange = { signature = it },
            label = { Text("Customer Signature Name *") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("wo_signature_input"),
          )
          OutlinedTextField(
            value = poleNote,
            onValueChange = { poleNote = it },
            label = { Text("Route & Pole / FDB Note") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )
        }
      },
      confirmButton = {
        Button(
          onClick = {
            viewModel.completeWorkOrder(
              workOrderId = order.id,
              opticalPowerDbm = opticalDbm.toDoubleOrNull() ?: -18.5,
              otdrLossDb = otdrDb.toDoubleOrNull() ?: 0.5,
              customerSignatureName = signature,
              consumedCableMeters = cableMeters.toIntOrNull() ?: 0,
              serialUsed = serialUsed,
              photoCount = photoCount.toIntOrNull() ?: 1,
              poleRouteNote = poleNote,
            )
            completeTarget = null
          },
          enabled = signature.isNotBlank(),
          modifier = Modifier.testTag("wo_complete_submit"),
        ) {
          Text("Close Job")
        }
      },
      dismissButton = {
        TextButton(onClick = { completeTarget = null }) { Text("Cancel") }
      },
    )
  }

  routeLogTarget?.let { order ->
    var routeText by remember(order.id) { mutableStateOf(order.poleRouteNote ?: "") }
    AlertDialog(
      onDismissRequest = { routeLogTarget = null },
      title = { Text("Route & Pole Log • ${order.ticketCode}") },
      text = {
        OutlinedTextField(
          value = routeText,
          onValueChange = { routeText = it },
          label = { Text("Pole ID / ODB-FDB Maintenance Notes") },
          minLines = 2,
          modifier = Modifier.fillMaxWidth(),
        )
      },
      confirmButton = {
        Button(
          onClick = {
            viewModel.addPoleRouteLog(order.id, routeText)
            routeLogTarget = null
          }
        ) {
          Text("Save Log")
        }
      },
      dismissButton = {
        TextButton(onClick = { routeLogTarget = null }) { Text("Cancel") }
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
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Text(
            text = "Field Work Orders & Van",
            style = MaterialTheme.typography.headlineMedium,
          )
          FilledTonalButton(
            onClick = onOpenTravelMap,
            modifier =
              Modifier.fillMaxWidth().height(48.dp).testTag("field_open_travel_map_button"),
          ) {
            Icon(
              imageVector = Icons.Filled.Map,
              contentDescription = null,
              modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Client Route & Travel History Map")
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
              Text(
                text = msg,
                style = MaterialTheme.typography.bodySmall,
                color =
                  if (isError) MaterialTheme.colorScheme.onErrorContainer
                  else PulseTheme.semantic.onVerifiedContainer,
                modifier = Modifier.weight(1f),
              )
              TextButton(onClick = viewModel::clearFeedback) { Text("OK") }
            }
          }
        }
      }

      if (canJobs && canStock) {
        item {
          TabRow(selectedTabIndex = selectedTab) {
            Tab(
              selected = selectedTab == 0,
              onClick = { selectedTab = 0 },
              text = { Text("My Work Orders (${workOrders.size})") },
            )
            Tab(
              selected = selectedTab == 1,
              onClick = { selectedTab = 1 },
              text = { Text("My Van (${vanStock.size})") },
            )
          }
        }
      }

      if (selectedTab == 0 && canJobs) {
        items(workOrders, key = { it.id }) { order ->
          WorkOrderCard(
            order = order,
            onOpenComplete = { completeTarget = order },
            onOpenRouteLog = { routeLogTarget = order },
          )
        }
      } else if (canStock) {
        items(vanStock, key = { it.id }) { stock ->
          VanStockCard(
            item = stock,
            onRequestRestock = { viewModel.requestVanRestock(stock.id, if (stock.serialNumber == null) 100 else 1) },
          )
        }
      }

      item { Spacer(modifier = Modifier.height(8.dp)) }
    }
  }
}

@Composable
private fun WorkOrderCard(
  order: WorkOrder,
  onOpenComplete: () -> Unit,
  onOpenRouteLog: () -> Unit,
) {
  val semantic = PulseTheme.semantic
  val isUrgentSla = order.slaMinutesRemaining <= 60 && order.status != WorkOrderStatus.CLOSED
  val (bg, fg) =
    when (order.status) {
      WorkOrderStatus.CLOSED -> semantic.verifiedContainer to semantic.onVerifiedContainer
      WorkOrderStatus.COMPLETED_LOCAL -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
      WorkOrderStatus.FLAGGED -> semantic.warningContainer to semantic.onWarningContainer
      else -> if (isUrgentSla) semantic.dangerContainer to semantic.onDangerContainer else semantic.warningContainer to semantic.onWarningContainer
    }

  Card(
    modifier = Modifier.fillMaxWidth().testTag("wo_card_${order.id}"),
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
          Icon(Icons.Filled.Router, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
          Text(
            text = "${order.ticketCode} • ${order.taskType}",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
          )
        }
        StatusPill(text = order.status.label, containerColor = bg, contentColor = fg)
      }

      Text(text = order.customerName, style = MaterialTheme.typography.titleMedium)
      Text(
        text = "${order.address} • SLA: ${order.slaDeadlineLabel} (${order.slaMinutesRemaining}m)",
        style = MonoTelemetryStyle,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      if (!order.poleRouteNote.isNullOrBlank()) {
        Text(
          text = "Route/Pole: ${order.poleRouteNote}",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.primary,
        )
      }
      if (order.opticalPowerDbm != null) {
        Text(
          text = "Optical ${order.opticalPowerDbm} dBm • OTDR ${order.otdrLossDb ?: 0.0} dB • Serial: ${order.serialUsed ?: "—"}",
          style = MonoTelemetryStyle,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        OutlinedButton(
          onClick = onOpenRouteLog,
          modifier = Modifier.weight(1f),
        ) {
          Icon(Icons.Filled.EditLocationAlt, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text("Pole Log")
        }
        Button(
          onClick = onOpenComplete,
          enabled = order.status != WorkOrderStatus.CLOSED && order.status != WorkOrderStatus.COMPLETED_LOCAL,
          modifier = Modifier.weight(1f).testTag("wo_complete_btn_${order.id}"),
        ) {
          Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text(if (order.status == WorkOrderStatus.CLOSED) "Closed" else "Complete")
        }
      }
    }
  }
}

@Composable
private fun VanStockCard(
  item: VanStockItem,
  onRequestRestock: () -> Unit,
) {
  val semantic = PulseTheme.semantic
  val isNegativeOrLow = item.quantityOnHand < 0 || item.quantityOnHand < item.minThreshold
  Card(
    modifier = Modifier.fillMaxWidth(),
    shape = MaterialTheme.shapes.medium,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
  ) {
    Row(
      modifier = Modifier.padding(14.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Icon(
        imageVector = if (item.quantityOnHand < 0) Icons.Filled.Warning else Icons.Filled.Inventory2,
        contentDescription = null,
        tint = if (isNegativeOrLow) semantic.warning else MaterialTheme.colorScheme.primary,
      )
      Column(modifier = Modifier.weight(1f)) {
        Text(text = item.itemName, style = MaterialTheme.typography.titleSmall)
        Text(
          text =
            "${item.sku} • ${item.vanCode}" +
              (item.serialNumber?.let { " • SN: $it" } ?: ""),
          style = MonoTelemetryStyle,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
          text =
            if (item.quantityOnHand < 0) "Balance: ${item.quantityOnHand} ${item.unit} (Flagged for Stock Audit)"
            else "On Hand: ${item.quantityOnHand} ${item.unit} (Min ${item.minThreshold})",
          style = MaterialTheme.typography.bodySmall,
          color = if (isNegativeOrLow) semantic.warning else semantic.verified,
        )
      }
      OutlinedButton(onClick = onRequestRestock) {
        Text("Restock")
      }
    }
  }
}
