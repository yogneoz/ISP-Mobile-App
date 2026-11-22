package com.example.ui.screens.route

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.filled.BusinessCenter
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.geofence.ClientVisitPin
import com.example.domain.model.LocationBreadcrumb
import com.example.domain.model.RouteEventType
import com.example.domain.model.SyncStatus
import com.example.ui.components.MetricSummaryCard
import com.example.ui.components.StatusPill
import com.example.ui.theme.MonoTelemetryStyle
import com.example.ui.theme.PulseTheme
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TravelRouteMapScreen(
  onBack: () -> Unit,
  viewModel: TravelRouteMapViewModel = hiltViewModel(),
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val isPlaying by viewModel.isPlayingTimeline.collectAsStateWithLifecycle()
  val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()
  val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()
  val semantic = PulseTheme.semantic

  var zoom by remember { mutableFloatStateOf(1f) }
  var panOffset by remember { mutableStateOf(Offset.Zero) }

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Column {
            Text("Travel & Client Route Map", style = MaterialTheme.typography.titleMedium)
            Text(
              text = uiState.profile?.fullName ?: "Field Employee Route History",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        },
        navigationIcon = {
          IconButton(onClick = onBack, modifier = Modifier.testTag("travel_map_back_button")) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        },
        actions = {
          StatusPill(
            text = if (uiState.isCurrentlyCheckedIn) "LIVE SHIFT" else "HISTORY",
            containerColor =
              if (uiState.isCurrentlyCheckedIn) semantic.verifiedContainer
              else MaterialTheme.colorScheme.secondaryContainer,
            contentColor =
              if (uiState.isCurrentlyCheckedIn) semantic.onVerifiedContainer
              else MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(end = 12.dp),
          )
        },
      )
    }
  ) { innerPadding ->
    Box(
      modifier = Modifier.fillMaxSize().padding(innerPadding),
      contentAlignment = Alignment.TopCenter,
    ) {
      LazyColumn(
        modifier = Modifier.widthIn(max = 900.dp).fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
      ) {
        // Optional feedback banner
        if (!statusMessage.isNullOrBlank()) {
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
                  text = statusMessage!!,
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onPrimaryContainer,
                  modifier = Modifier.weight(1f),
                )
                TextButton(onClick = viewModel::clearStatusMessage) { Text("OK") }
              }
            }
          }
        }

        // Workday Date / Shift Filter Chips
        item {
          Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            uiState.availableDates.forEach { dateIso ->
              FilterChip(
                selected = uiState.selectedDateFilter == dateIso,
                onClick = {
                  zoom = 1f
                  panOffset = Offset.Zero
                  viewModel.selectDateFilter(dateIso)
                },
                label = { Text(dateIso) },
                modifier = Modifier.testTag("map_date_chip_$dateIso"),
              )
            }
            FilterChip(
              selected = uiState.selectedDateFilter == "ALL",
              onClick = {
                zoom = 1f
                panOffset = Offset.Zero
                viewModel.selectDateFilter("ALL")
              },
              label = { Text("All History") },
              modifier = Modifier.testTag("map_date_chip_ALL"),
            )
          }
        }

        // Layer Toggles
        item {
          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            FilterChip(
              selected = uiState.layers.showTravelPath,
              onClick = {
                viewModel.toggleLayer { it.copy(showTravelPath = !it.showTravelPath) }
              },
              label = { Text("Travel Path") },
            )
            FilterChip(
              selected = uiState.layers.showClientVisits,
              onClick = {
                viewModel.toggleLayer { it.copy(showClientVisits = !it.showClientVisits) }
              },
              label = { Text("Client Visits") },
            )
            FilterChip(
              selected = uiState.layers.showGeofence,
              onClick = {
                viewModel.toggleLayer { it.copy(showGeofence = !it.showGeofence) }
              },
              label = { Text("HQ Geofence") },
            )
            FilterChip(
              selected = uiState.layers.showLabels,
              onClick = {
                viewModel.toggleLayer { it.copy(showLabels = !it.showLabels) }
              },
              label = { Text("Map Labels") },
            )
          }
        }

        // Interactive Geospatial Map Card
        item {
          Card(
            modifier = Modifier.fillMaxWidth().testTag("travel_map_card"),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
          ) {
            Column {
              Box(modifier = Modifier.fillMaxWidth().height(360.dp)) {
                InteractiveGeospatialMapCanvas(
                  uiState = uiState,
                  zoom = zoom,
                  panOffset = panOffset,
                  onZoomChange = { newZoom -> zoom = newZoom.coerceIn(0.8f, 6f) },
                  onPanChange = { delta -> panOffset += delta },
                  projectNormalized = viewModel::projectToNormalized,
                  onSelectWaypoint = viewModel::selectStopIndex,
                  onSelectClient = viewModel::selectClientPin,
                  modifier = Modifier.fillMaxSize().testTag("travel_route_interactive_canvas"),
                )

                // Top-left Compass & Coordinate Bounds HUD
                Surface(
                  shape = MaterialTheme.shapes.small,
                  color = MaterialTheme.colorScheme.surface.copy(alpha = 0.90f),
                  border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                  modifier = Modifier.align(Alignment.TopStart).padding(10.dp),
                ) {
                  Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                  ) {
                    Icon(
                      imageVector = Icons.Filled.Explore,
                      contentDescription = "North Orientation",
                      tint = MaterialTheme.colorScheme.primary,
                      modifier = Modifier.size(14.dp),
                    )
                    Text(
                      text = String.format(
                        Locale.US,
                        "N • %.3f°, %.3f° • %.1fx",
                        uiState.bounds.centerLat,
                        uiState.bounds.centerLon,
                        zoom,
                      ),
                      style = MonoTelemetryStyle,
                    )
                  }
                }

                // Bottom-left Scale Bar & Legend
                Surface(
                  shape = MaterialTheme.shapes.small,
                  color = MaterialTheme.colorScheme.surface.copy(alpha = 0.90f),
                  border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                  modifier = Modifier.align(Alignment.BottomStart).padding(10.dp),
                ) {
                  val approxViewMeters = uiState.boundsWidthMeters / zoom.coerceAtLeast(0.8f)
                  val scaleMeters = (approxViewMeters * 0.25).coerceAtLeast(50.0)
                  Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)) {
                    Text(
                      text = "Scale ≈ ${viewModel.formatDistance(scaleMeters)}",
                      style = MonoTelemetryStyle,
                    )
                    Row(
                      horizontalArrangement = Arrangement.spacedBy(8.dp),
                      verticalAlignment = Alignment.CenterVertically,
                    ) {
                      MapLegendItem("In", semantic.verified)
                      MapLegendItem("Out", semantic.warning)
                      MapLegendItem("Client", MaterialTheme.colorScheme.tertiary)
                    }
                  }
                }

                // Right-side Zoom & Fit-to-Bounds Floating Map Controls
                Column(
                  modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp),
                  verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                  FilledTonalIconButton(
                    onClick = { zoom = (zoom * 1.35f).coerceIn(0.8f, 6f) },
                    modifier = Modifier.size(48.dp).testTag("map_zoom_in_button"),
                  ) {
                    Icon(Icons.Filled.Add, contentDescription = "Zoom In")
                  }
                  FilledTonalIconButton(
                    onClick = { zoom = (zoom / 1.35f).coerceIn(0.8f, 6f) },
                    modifier = Modifier.size(48.dp).testTag("map_zoom_out_button"),
                  ) {
                    Icon(Icons.Filled.Remove, contentDescription = "Zoom Out")
                  }
                  FilledTonalIconButton(
                    onClick = {
                      zoom = 1f
                      panOffset = Offset.Zero
                    },
                    modifier = Modifier.size(48.dp).testTag("map_reset_bounds_button"),
                  ) {
                    Icon(Icons.Filled.CenterFocusStrong, contentDescription = "Fit Route Bounds")
                  }
                }
              }

              HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

              // Pin Live Location Bar
              Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Text(
                  text = "Drag to pan • Pinch or +/- to zoom • Tap pins to inspect",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedButton(
                  onClick = viewModel::recordCurrentPinToMap,
                  enabled = !isBusy,
                  modifier = Modifier.testTag("map_pin_current_gps_button"),
                ) {
                  Icon(
                    Icons.Filled.AddLocationAlt,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                  )
                  Spacer(modifier = Modifier.width(6.dp))
                  Text("Pin GPS")
                }
              }
            }
          }
        }

        // Summary KPI Cards
        item {
          val visitedClients = uiState.clientVisitPins.count { it.visited }
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            MetricSummaryCard(
              title = "Distance",
              value = viewModel.formatDistance(uiState.totalDistanceMeters),
              subtitle = "Traveled route",
              icon = Icons.Filled.Route,
              accentColor = MaterialTheme.colorScheme.primary,
              modifier = Modifier.weight(1f),
            )
            MetricSummaryCard(
              title = "GPS Stops",
              value = "${uiState.visibleWaypoints.size}",
              subtitle = "Waypoints logged",
              icon = Icons.Filled.CheckCircle,
              accentColor = semantic.verified,
              modifier = Modifier.weight(1f),
            )
            MetricSummaryCard(
              title = "Client Visits",
              value = "$visitedClients/${uiState.clientVisitPins.size}",
              subtitle = "Completed stops",
              icon = Icons.Filled.BusinessCenter,
              accentColor = semantic.warning,
              modifier = Modifier.weight(1f),
            )
          }
        }

        // Workday Timeline Playback & Selected Stop Inspector
        if (uiState.visibleWaypoints.isNotEmpty()) {
          item {
            val selectedWp = uiState.visibleWaypoints.getOrNull(uiState.selectedStopIndex)
            val segDist = viewModel.segmentDistanceFromPrevious(uiState.selectedStopIndex)
            Card(
              modifier = Modifier.fillMaxWidth().testTag("travel_timeline_scrubber_card"),
              shape = MaterialTheme.shapes.medium,
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
              border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
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
                  Text(
                    text = "Workday Playback (${uiState.selectedStopIndex + 1} / ${uiState.visibleWaypoints.size})",
                    style = MaterialTheme.typography.titleSmall,
                  )
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                      onClick = { viewModel.stepTimeline(-1) },
                      enabled = uiState.selectedStopIndex > 0,
                      modifier = Modifier.testTag("timeline_prev_button"),
                    ) {
                      Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous Stop")
                    }
                    FilledTonalIconButton(
                      onClick = viewModel::togglePlayTimeline,
                      enabled = uiState.visibleWaypoints.size > 1,
                      modifier = Modifier.testTag("timeline_play_pause_button"),
                    ) {
                      Icon(
                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) "Pause Playback" else "Play Route",
                      )
                    }
                    IconButton(
                      onClick = { viewModel.stepTimeline(1) },
                      enabled = uiState.selectedStopIndex < uiState.visibleWaypoints.lastIndex,
                      modifier = Modifier.testTag("timeline_next_button"),
                    ) {
                      Icon(Icons.Filled.SkipNext, contentDescription = "Next Stop")
                    }
                  }
                }

                if (uiState.visibleWaypoints.size > 1) {
                  Slider(
                    value = uiState.selectedStopIndex.toFloat(),
                    onValueChange = { viewModel.selectStopIndex(it.toInt()) },
                    valueRange = 0f..uiState.visibleWaypoints.lastIndex.toFloat(),
                    steps = (uiState.visibleWaypoints.size - 2).coerceAtLeast(0),
                    modifier = Modifier.testTag("timeline_scrubber_slider"),
                  )
                }

                if (selectedWp != null) {
                  Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    modifier = Modifier.fillMaxWidth(),
                  ) {
                    Column(
                      modifier = Modifier.padding(10.dp),
                      verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                      Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                      ) {
                        Text(
                          text = "#${uiState.selectedStopIndex + 1} • ${selectedWp.siteLabel}",
                          style = MaterialTheme.typography.labelLarge,
                          modifier = Modifier.weight(1f),
                          maxLines = 1,
                          overflow = TextOverflow.Ellipsis,
                        )
                        StatusPill(
                          text = selectedWp.eventType.label,
                          containerColor = MaterialTheme.colorScheme.primaryContainer,
                          contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                      }
                      Text(
                        text =
                          String.format(
                            Locale.US,
                            "%s %s • %.5f, %.5f%s",
                            selectedWp.dateIso,
                            selectedWp.capturedTimeLabel,
                            selectedWp.latitude,
                            selectedWp.longitude,
                            segDist?.let { " • +${viewModel.formatDistance(it)} leg" } ?: "",
                          ),
                        style = MonoTelemetryStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                      )
                    }
                  }
                }
              }
            }
          }
        }

        // Client Visits Route Breakdown Section
        if (uiState.clientVisitPins.isNotEmpty()) {
          item {
            Text(
              text = "Client Visit Stops (${uiState.clientVisitPins.count { it.visited }} Visited)",
              style = MaterialTheme.typography.titleMedium,
            )
          }
          itemsIndexed(uiState.clientVisitPins, key = { _, p -> p.workOrderId }) { _, pin ->
            val isSelectedClient = uiState.selectedClientWorkOrderId == pin.workOrderId
            Card(
              onClick = { viewModel.selectClientPin(pin) },
              modifier = Modifier.fillMaxWidth().testTag("client_visit_card_${pin.ticketCode}"),
              shape = MaterialTheme.shapes.medium,
              colors =
                CardDefaults.cardColors(
                  containerColor =
                    if (isSelectedClient) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                    else MaterialTheme.colorScheme.surface
                ),
              border =
                BorderStroke(
                  width = if (isSelectedClient) 1.5.dp else 1.dp,
                  color =
                    if (isSelectedClient) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outlineVariant,
                ),
            ) {
              Row(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                  ) {
                    Text(
                      text = pin.customerName,
                      style = MaterialTheme.typography.titleSmall,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                      text = pin.ticketCode,
                      style = MonoTelemetryStyle,
                      color = MaterialTheme.colorScheme.primary,
                    )
                  }
                  Text(
                    text = pin.address,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                  Text(
                    text =
                      if (pin.visitedAtLabel != null) "Visited at ${pin.visitedAtLabel}"
                      else "Scheduled • ${pin.taskType}",
                    style = MonoTelemetryStyle,
                    color =
                      if (pin.visited) semantic.verified
                      else MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
                Spacer(modifier = Modifier.width(8.dp))
                StatusPill(
                  text = if (pin.visited) "VISITED" else "PENDING",
                  containerColor =
                    if (pin.visited) semantic.verifiedContainer else semantic.warningContainer,
                  contentColor =
                    if (pin.visited) semantic.onVerifiedContainer else semantic.onWarningContainer,
                )
              }
            }
          }
        }

        // Chronological Travel History Points
        item {
          Text(
            text = "Workday Travel Breadcrumbs (${uiState.visibleWaypoints.size})",
            style = MaterialTheme.typography.titleMedium,
          )
        }

        itemsIndexed(uiState.visibleWaypoints, key = { _, wp -> wp.id }) { index, wp ->
          val isSelected = index == uiState.selectedStopIndex
          val segDist = viewModel.segmentDistanceFromPrevious(index)
          Surface(
            shape = MaterialTheme.shapes.medium,
            color =
              if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
              else MaterialTheme.colorScheme.surface,
            border =
              BorderStroke(
                width = if (isSelected) 1.5.dp else 1.dp,
                color =
                  if (isSelected) MaterialTheme.colorScheme.primary
                  else MaterialTheme.colorScheme.outlineVariant,
              ),
            modifier =
              Modifier.fillMaxWidth()
                .clickable { viewModel.selectStopIndex(index) }
                .testTag("map_stop_row_${index + 1}"),
          ) {
            Row(
              modifier = Modifier.padding(12.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              val accent =
                when (wp.eventType) {
                  RouteEventType.CHECK_IN -> semantic.verified
                  RouteEventType.CHECK_OUT -> semantic.warning
                  RouteEventType.WAYPOINT, RouteEventType.MANUAL_PUSH ->
                    MaterialTheme.colorScheme.primary
                }
              Box(
                modifier = Modifier.size(28.dp).clip(CircleShape).background(accent),
                contentAlignment = Alignment.Center,
              ) {
                Text(
                  text = "${index + 1}",
                  style = MaterialTheme.typography.labelMedium,
                  fontWeight = FontWeight.Bold,
                  color = MaterialTheme.colorScheme.surface,
                )
              }

              Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                  text = wp.siteLabel,
                  style = MaterialTheme.typography.labelLarge,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
                Text(
                  text =
                    String.format(
                      Locale.US,
                      "%s • %.5f, %.5f%s",
                      wp.capturedTimeLabel,
                      wp.latitude,
                      wp.longitude,
                      segDist?.let { " • ${viewModel.formatDistance(it)}" } ?: "",
                    ),
                  style = MonoTelemetryStyle,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }

              Icon(
                imageVector =
                  if (wp.syncStatus == SyncStatus.SYNCED) Icons.Filled.CloudDone
                  else Icons.Filled.Sync,
                contentDescription = wp.syncStatus.label,
                tint = if (wp.syncStatus == SyncStatus.SYNCED) semantic.verified else semantic.warning,
                modifier = Modifier.size(18.dp),
              )
            }
          }
        }

        item { Spacer(modifier = Modifier.height(12.dp)) }
      }
    }
  }
}

@Composable
private fun InteractiveGeospatialMapCanvas(
  uiState: TravelMapUiState,
  zoom: Float,
  panOffset: Offset,
  onZoomChange: (Float) -> Unit,
  onPanChange: (Offset) -> Unit,
  projectNormalized: (Double, Double) -> Pair<Float, Float>,
  onSelectWaypoint: (Int) -> Unit,
  onSelectClient: (ClientVisitPin) -> Unit,
  modifier: Modifier = Modifier,
) {
  val semantic = PulseTheme.semantic
  val textMeasurer = rememberTextMeasurer()
  val labelStyle =
    MaterialTheme.typography.labelSmall.copy(
      fontSize = 10.sp,
      fontWeight = FontWeight.SemiBold,
    )

  val primaryColor = MaterialTheme.colorScheme.primary
  val tertiaryColor = MaterialTheme.colorScheme.tertiary
  val mapBgColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f)
  val parkTintColor = semantic.verified.copy(alpha = 0.08f)
  val waterTintColor = primaryColor.copy(alpha = 0.07f)
  val roadCasingColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.75f)
  val roadFillColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.90f)
  val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
  val surfaceColor = MaterialTheme.colorScheme.surface
  val textColor = MaterialTheme.colorScheme.onSurface
  val checkInColor = semantic.verified
  val checkOutColor = semantic.warning

  Box(modifier = modifier.clipToBounds()) {
    Canvas(
      modifier =
        Modifier.fillMaxSize()
          .pointerInput(uiState.visibleWaypoints, uiState.clientVisitPins, zoom, panOffset) {
            detectTransformGestures { _, pan, gestureZoom, _ ->
              if (gestureZoom != 1f) {
                onZoomChange(zoom * gestureZoom)
              }
              if (pan != Offset.Zero) {
                onPanChange(pan)
              }
            }
          }
          .pointerInput(uiState.visibleWaypoints, uiState.clientVisitPins, zoom, panOffset) {
            detectTapGestures { tapOffset ->
              val pad = 32.dp.toPx()
              val usableW = (size.width - pad * 2).coerceAtLeast(1f)
              val usableH = (size.height - pad * 2).coerceAtLeast(1f)
              val cx = size.width / 2f
              val cy = size.height / 2f

              fun toScreen(lat: Double, lon: Double): Offset {
                val (nx, ny) = projectNormalized(lat, lon)
                val baseX = pad + nx * usableW
                val baseY = pad + ny * usableH
                return Offset(
                  x = (baseX - cx) * zoom + cx + panOffset.x,
                  y = (baseY - cy) * zoom + cy + panOffset.y,
                )
              }

              var closestWpIdx = -1
              var closestWpDist = 42.dp.toPx()
              uiState.visibleWaypoints.forEachIndexed { idx, wp ->
                val p = toScreen(wp.latitude, wp.longitude)
                val d = hypot(p.x - tapOffset.x, p.y - tapOffset.y)
                if (d < closestWpDist) {
                  closestWpDist = d
                  closestWpIdx = idx
                }
              }

              var closestClient: ClientVisitPin? = null
              var closestClientDist = 42.dp.toPx()
              uiState.clientVisitPins.forEach { pin ->
                val p = toScreen(pin.latitude, pin.longitude)
                val d = hypot(p.x - tapOffset.x, p.y - tapOffset.y)
                if (d < closestClientDist) {
                  closestClientDist = d
                  closestClient = pin
                }
              }

              if (closestWpIdx >= 0 && closestWpDist <= closestClientDist) {
                onSelectWaypoint(closestWpIdx)
              } else if (closestClient != null) {
                onSelectClient(closestClient!!)
              }
            }
          }
    ) {
      drawRect(color = mapBgColor)

      val pad = 32.dp.toPx()
      val usableW = (size.width - pad * 2).coerceAtLeast(1f)
      val usableH = (size.height - pad * 2).coerceAtLeast(1f)
      val cx = size.width / 2f
      val cy = size.height / 2f

      fun toScreen(lat: Double, lon: Double): Offset {
        val (nx, ny) = projectNormalized(lat, lon)
        val baseX = pad + nx * usableW
        val baseY = pad + ny * usableH
        return Offset(
          x = (baseX - cx) * zoom + cx + panOffset.x,
          y = (baseY - cy) * zoom + cy + panOffset.y,
        )
      }

      // Cartographic land parcels, campus greenery & bay edge contour
      drawRect(
        color = waterTintColor,
        topLeft = Offset(size.width * 0.72f, 0f),
        size = Size(size.width * 0.28f, size.height * 0.24f),
      )
      drawRect(
        color = parkTintColor,
        topLeft = Offset(size.width * 0.08f, size.height * 0.14f),
        size = Size(size.width * 0.28f, size.height * 0.26f),
      )
      drawRect(
        color = parkTintColor,
        topLeft = Offset(size.width * 0.52f, size.height * 0.56f),
        size = Size(size.width * 0.34f, size.height * 0.24f),
      )

      // Coordinate Graticule Gridlines
      for (i in 1..4) {
        val x = size.width * (i / 5f)
        val y = size.height * (i / 5f)
        drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.dp.toPx())
        drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
      }

      // Arterial Corridor Streets projected around Bay Area branch/client coordinates
      val corridorRoads =
        listOf(
          // Shoreline Blvd N-S corridor
          listOf(37.4310 to -122.0789, 37.4180 to -122.0792, 37.4046 to -122.0789, 37.3948 to -122.0785, 37.3820 to -122.0788),
          // El Camino Real NW-SE arterial
          listOf(37.4020 to -122.1040, 37.3940 to -122.0930, 37.3861 to -122.0839, 37.3760 to -122.0710),
          // Charleston / Amphitheatre E-W connector
          listOf(37.4220 to -122.1020, 37.4220 to -122.0840, 37.4205 to -122.0690),
          // Castro / Evelyn cross corridor
          listOf(37.3948 to -122.0960, 37.3948 to -122.0785, 37.3955 to -122.0660),
        )
      corridorRoads.forEach { polylineCoords ->
        val pts = polylineCoords.map { (lat, lon) -> toScreen(lat, lon) }
        val roadPath =
          Path().apply {
            moveTo(pts.first().x, pts.first().y)
            pts.drop(1).forEach { lineTo(it.x, it.y) }
          }
        drawPath(
          path = roadPath,
          color = roadCasingColor,
          style = Stroke(width = 7.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
        drawPath(
          path = roadPath,
          color = roadFillColor,
          style = Stroke(width = 4.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
      }

      // Layer 1: HQ Branch Office Geofence Perimeter Circle
      val primaryBranch = uiState.profile?.branch ?: uiState.branches.firstOrNull()
      if (uiState.layers.showGeofence && primaryBranch != null) {
        val hqPos = toScreen(primaryBranch.latitude, primaryBranch.longitude)
        val pxPerMeter =
          ((usableW * zoom) / uiState.boundsWidthMeters.coerceAtLeast(100.0)).toFloat()
        val radiusPx = (primaryBranch.geofenceRadiusMeters.toFloat() * pxPerMeter).coerceIn(16.dp.toPx(), 90.dp.toPx())
        drawCircle(
          color = checkInColor.copy(alpha = 0.14f),
          radius = radiusPx,
          center = hqPos,
        )
        drawCircle(
          color = checkInColor.copy(alpha = 0.7f),
          radius = radiusPx,
          center = hqPos,
          style =
            Stroke(
              width = 1.5.dp.toPx(),
              pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f),
            ),
        )
        drawRect(
          color = surfaceColor,
          topLeft = Offset(hqPos.x - 7.dp.toPx(), hqPos.y - 7.dp.toPx()),
          size = Size(14.dp.toPx(), 14.dp.toPx()),
        )
        drawRect(
          color = checkInColor,
          topLeft = Offset(hqPos.x - 5.dp.toPx(), hqPos.y - 5.dp.toPx()),
          size = Size(10.dp.toPx(), 10.dp.toPx()),
        )
        if (uiState.layers.showLabels) {
          drawPinLabel(
            textMeasurer = textMeasurer,
            text = primaryBranch.code,
            anchor = hqPos + Offset(10.dp.toPx(), -14.dp.toPx()),
            style = labelStyle.copy(color = checkInColor),
            bgColor = surfaceColor.copy(alpha = 0.88f),
          )
        }
      }

      // Layer 2: Client Visit Route Pins & Dispatch Sequence Line
      if (uiState.layers.showClientVisits && uiState.clientVisitPins.isNotEmpty()) {
        val clientOffsets = uiState.clientVisitPins.map { toScreen(it.latitude, it.longitude) }
        if (clientOffsets.size > 1) {
          val dispatchPath =
            Path().apply {
              moveTo(clientOffsets.first().x, clientOffsets.first().y)
              clientOffsets.drop(1).forEach { lineTo(it.x, it.y) }
            }
          drawPath(
            path = dispatchPath,
            color = tertiaryColor.copy(alpha = 0.32f),
            style =
              Stroke(
                width = 2.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 10f), 0f),
              ),
          )
        }

        uiState.clientVisitPins.forEachIndexed { idx, pin ->
          val center = clientOffsets[idx]
          val isSelectedClient = uiState.selectedClientWorkOrderId == pin.workOrderId
          val diamondR = if (isSelectedClient) 11.dp.toPx() else 8.5.dp.toPx()
          val pinColor = if (pin.visited) checkInColor else tertiaryColor
          val diamond =
            Path().apply {
              moveTo(center.x, center.y - diamondR)
              lineTo(center.x + diamondR, center.y)
              lineTo(center.x, center.y + diamondR)
              lineTo(center.x - diamondR, center.y)
              close()
            }
          if (isSelectedClient) {
            drawCircle(color = pinColor.copy(alpha = 0.25f), radius = 18.dp.toPx(), center = center)
          }
          drawPath(path = diamond, color = surfaceColor)
          drawPath(
            path = diamond,
            color = pinColor,
            style = Stroke(width = 2.5.dp.toPx(), join = StrokeJoin.Round),
          )
          drawCircle(color = pinColor, radius = 3.5.dp.toPx(), center = center)

          if (uiState.layers.showLabels) {
            drawPinLabel(
              textMeasurer = textMeasurer,
              text = "${pin.ticketCode}${if (pin.visited) " ✓" else ""}",
              anchor = center + Offset(11.dp.toPx(), -6.dp.toPx()),
              style = labelStyle.copy(color = textColor),
              bgColor = surfaceColor.copy(alpha = 0.9f),
            )
          }
        }
      }

      // Layer 3: Collected Travel History Route Polyline + Directional Chevrons + Stop Pins
      val waypoints = uiState.visibleWaypoints
      if (uiState.layers.showTravelPath && waypoints.isNotEmpty()) {
        val wpOffsets = waypoints.map { toScreen(it.latitude, it.longitude) }
        val activeCutoff = uiState.selectedStopIndex.coerceIn(0, waypoints.lastIndex)

        // Draw future/full route subtle track when scrubbing backward
        if (wpOffsets.size > 1) {
          val fullPath =
            Path().apply {
              moveTo(wpOffsets.first().x, wpOffsets.first().y)
              wpOffsets.drop(1).forEach { lineTo(it.x, it.y) }
            }
          drawPath(
            path = fullPath,
            color = primaryColor.copy(alpha = 0.22f),
            style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
          )
        }

        // Draw traveled path up to active scrub index
        val traveledCount = (activeCutoff + 1).coerceAtLeast(1)
        if (traveledCount > 1) {
          val activePath =
            Path().apply {
              moveTo(wpOffsets.first().x, wpOffsets.first().y)
              for (i in 1 until traveledCount) {
                lineTo(wpOffsets[i].x, wpOffsets[i].y)
              }
            }
          drawPath(
            path = activePath,
            color = surfaceColor.copy(alpha = 0.9f),
            style = Stroke(width = 7.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
          )
          drawPath(
            path = activePath,
            color = primaryColor,
            style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
          )

          // Directional arrowheads on segment midpoints
          for (i in 1 until traveledCount) {
            val a = wpOffsets[i - 1]
            val b = wpOffsets[i]
            val distPx = hypot(b.x - a.x, b.y - a.y)
            if (distPx > 28.dp.toPx()) {
              val mid = Offset((a.x + b.x) / 2f, (a.y + b.y) / 2f)
              val angle = atan2((b.y - a.y).toDouble(), (b.x - a.x).toDouble())
              val arrowLen = 7.dp.toPx()
              val wingAngle = Math.toRadians(28.0)
              val p1 =
                Offset(
                  x = mid.x - (arrowLen * cos(angle - wingAngle)).toFloat(),
                  y = mid.y - (arrowLen * sin(angle - wingAngle)).toFloat(),
                )
              val p2 =
                Offset(
                  x = mid.x - (arrowLen * cos(angle + wingAngle)).toFloat(),
                  y = mid.y - (arrowLen * sin(angle + wingAngle)).toFloat(),
                )
              drawLine(surfaceColor, mid, p1, strokeWidth = 3.5.dp.toPx(), cap = StrokeCap.Round)
              drawLine(surfaceColor, mid, p2, strokeWidth = 3.5.dp.toPx(), cap = StrokeCap.Round)
              drawLine(primaryColor, mid, p1, strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
              drawLine(primaryColor, mid, p2, strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
            }
          }
        }

        // Waypoint Pins + Active Playback Halo
        wpOffsets.forEachIndexed { idx, center ->
          val wp = waypoints[idx]
          val isSelected = idx == activeCutoff
          val pinColor =
            when (wp.eventType) {
              RouteEventType.CHECK_IN -> checkInColor
              RouteEventType.CHECK_OUT -> checkOutColor
              RouteEventType.WAYPOINT, RouteEventType.MANUAL_PUSH -> primaryColor
            }
          if (isSelected) {
            drawCircle(color = pinColor.copy(alpha = 0.25f), radius = 19.dp.toPx(), center = center)
          }
          val radius = if (isSelected) 11.dp.toPx() else 8.5.dp.toPx()
          drawCircle(color = surfaceColor, radius = radius + 2.dp.toPx(), center = center)
          drawCircle(color = pinColor, radius = radius, center = center)

          val numText = "${idx + 1}"
          val measured =
            textMeasurer.measure(
              text = numText,
              style = labelStyle.copy(color = surfaceColor, fontSize = 9.sp),
            )
          drawText(
            textLayoutResult = measured,
            topLeft =
              Offset(
                x = center.x - measured.size.width / 2f,
                y = center.y - measured.size.height / 2f,
              ),
          )
        }
      }
    }
  }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPinLabel(
  textMeasurer: TextMeasurer,
  text: String,
  anchor: Offset,
  style: androidx.compose.ui.text.TextStyle,
  bgColor: Color,
) {
  val measured = textMeasurer.measure(text = text, style = style)
  val padH = 4.dp.toPx()
  val padV = 2.dp.toPx()
  val boxLeft = anchor.x.coerceIn(4f, (size.width - measured.size.width - padH * 2 - 4f).coerceAtLeast(4f))
  val boxTop = anchor.y.coerceIn(4f, (size.height - measured.size.height - padV * 2 - 4f).coerceAtLeast(4f))
  drawRoundRect(
    color = bgColor,
    topLeft = Offset(boxLeft, boxTop),
    size = Size(measured.size.width + padH * 2, measured.size.height + padV * 2),
    cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx(), 4.dp.toPx()),
  )
  drawText(
    textLayoutResult = measured,
    topLeft = Offset(boxLeft + padH, boxTop + padV),
  )
}

@Composable
private fun MapLegendItem(label: String, color: Color) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(3.dp),
  ) {
    Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(color))
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}
