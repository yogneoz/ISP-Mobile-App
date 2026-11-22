package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.DonutLarge
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.HolidayCategory
import com.example.domain.model.HolidaySchedule
import com.example.domain.model.LeaveBalance
import com.example.ui.theme.MonoMetricStyle
import com.example.ui.theme.MonoTelemetryStyle
import com.example.ui.theme.PulseTheme
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

private enum class LeaveChartMode(val label: String) {
  DONUT("Allocation Donut"),
  BARS("Quota Bars"),
}

private enum class HolidayFilter(val label: String) {
  ALL("All"),
  MANDATORY("Public & Company"),
  BRANCH("Branch Floating"),
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InteractiveLeaveBalanceChartCard(
  balances: List<LeaveBalance>,
  onPlanLeave: () -> Unit,
  modifier: Modifier = Modifier,
  onOpenFullAnalytics: (() -> Unit)? = null,
) {
  val semantic = PulseTheme.semantic
  var chartMode by remember { mutableStateOf(LeaveChartMode.DONUT) }
  var selectedLeaveTypeId by remember { mutableStateOf<String?>(null) }

  val selectedBalance = balances.firstOrNull { it.leaveTypeId == selectedLeaveTypeId }

  val totalAllocated = selectedBalance?.allocatedDays ?: balances.sumOf { it.allocatedDays }
  val totalRemaining = selectedBalance?.remainingDays ?: balances.sumOf { it.remainingDays }
  val totalUsed = selectedBalance?.usedDays ?: balances.sumOf { it.usedDays }
  val totalPending = selectedBalance?.pendingDays ?: balances.sumOf { it.pendingDays }

  val safeTotal = max(totalAllocated, 1.0)
  val remainingSweep by
    animateFloatAsState(
      targetValue = ((totalRemaining / safeTotal) * 360.0).toFloat().coerceIn(0f, 360f),
      animationSpec = tween(durationMillis = 450),
      label = "remainingSweep",
    )
  val pendingSweep by
    animateFloatAsState(
      targetValue = ((totalPending / safeTotal) * 360.0).toFloat().coerceIn(0f, 360f),
      animationSpec = tween(durationMillis = 450),
      label = "pendingSweep",
    )
  val usedSweep by
    animateFloatAsState(
      targetValue = ((totalUsed / safeTotal) * 360.0).toFloat().coerceIn(0f, 360f),
      animationSpec = tween(durationMillis = 450),
      label = "usedSweep",
    )

  var highlightedSegment by remember { mutableStateOf("Remaining") }

  Card(
    modifier = modifier.fillMaxWidth().testTag("leave_balance_chart_card"),
    shape = MaterialTheme.shapes.large,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
  ) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
          Icon(
            imageVector = Icons.Filled.EventAvailable,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
          )
          Column {
            Text(
              text = "Leave Balance Analytics",
              style = MaterialTheme.typography.titleMedium,
            )
            Text(
              text = selectedBalance?.leaveTypeName ?: "All Leave Categories Combined",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
        if (onOpenFullAnalytics != null) {
          TextButton(
            onClick = onOpenFullAnalytics,
            modifier = Modifier.testTag("open_full_analytics_button"),
          ) {
            Text("Analytics")
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowForward,
              contentDescription = null,
              modifier = Modifier.size(16.dp),
            )
          }
        }
      }

      // Mode Selector & Category Filter Chips
      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        FilterChip(
          selected = chartMode == LeaveChartMode.DONUT,
          onClick = { chartMode = LeaveChartMode.DONUT },
          label = { Text(LeaveChartMode.DONUT.label) },
          leadingIcon = {
            Icon(Icons.Filled.DonutLarge, contentDescription = null, modifier = Modifier.size(16.dp))
          },
          modifier = Modifier.testTag("chart_mode_donut"),
        )
        FilterChip(
          selected = chartMode == LeaveChartMode.BARS,
          onClick = { chartMode = LeaveChartMode.BARS },
          label = { Text(LeaveChartMode.BARS.label) },
          leadingIcon = {
            Icon(Icons.Filled.BarChart, contentDescription = null, modifier = Modifier.size(16.dp))
          },
          modifier = Modifier.testTag("chart_mode_bars"),
        )
      }

      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        FilterChip(
          selected = selectedLeaveTypeId == null,
          onClick = { selectedLeaveTypeId = null },
          label = { Text("All (${balances.sumOf { it.remainingDays }.toInt()}d)") },
          modifier = Modifier.testTag("leave_chip_ALL"),
        )
        balances.forEach { bal ->
          FilterChip(
            selected = selectedLeaveTypeId == bal.leaveTypeId,
            onClick = {
              selectedLeaveTypeId =
                if (selectedLeaveTypeId == bal.leaveTypeId) null else bal.leaveTypeId
            },
            label = { Text("${bal.leaveCode}: ${bal.remainingDays.toInt()}d") },
            modifier = Modifier.testTag("leave_chip_${bal.leaveCode}"),
          )
        }
      }

      if (balances.isEmpty()) {
        Text(
          text = "Loading leave quota telemetry…",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      } else if (chartMode == LeaveChartMode.DONUT) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
          val remainingClr = MaterialTheme.colorScheme.primary
          val pendingClr = semantic.warning
          val usedClr = MaterialTheme.colorScheme.outline
          val trackClr = MaterialTheme.colorScheme.surfaceVariant

          Box(
            modifier = Modifier.size(144.dp),
            contentAlignment = Alignment.Center,
          ) {
            Canvas(
              modifier =
                Modifier.size(144.dp).pointerInput(remainingSweep, pendingSweep, usedSweep) {
                  detectTapGestures { offset ->
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    val dist = hypot(offset.x - cx, offset.y - cy)
                    if (dist in (size.width * 0.22f)..(size.width * 0.52f)) {
                      var angle = Math.toDegrees(atan2((offset.y - cy).toDouble(), (offset.x - cx).toDouble()))
                      angle = (angle + 90.0 + 360.0) % 360.0
                      highlightedSegment =
                        when {
                          angle <= remainingSweep -> "Remaining"
                          angle <= remainingSweep + pendingSweep -> "Pending"
                          else -> "Used"
                        }
                    }
                  }
                }
            ) {
              val strokeWidth = 22.dp.toPx()
              val diameter = min(size.width, size.height) - strokeWidth
              val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
              val arcSize = Size(diameter, diameter)

              drawArc(
                color = trackClr,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth),
              )

              var currentStart = -90f
              if (remainingSweep > 0f) {
                drawArc(
                  color = remainingClr,
                  startAngle = currentStart,
                  sweepAngle = remainingSweep,
                  useCenter = false,
                  topLeft = topLeft,
                  size = arcSize,
                  style =
                    Stroke(
                      width = if (highlightedSegment == "Remaining") strokeWidth + 4.dp.toPx() else strokeWidth,
                      cap = StrokeCap.Butt,
                    ),
                )
                currentStart += remainingSweep
              }
              if (pendingSweep > 0f) {
                drawArc(
                  color = pendingClr,
                  startAngle = currentStart,
                  sweepAngle = pendingSweep,
                  useCenter = false,
                  topLeft = topLeft,
                  size = arcSize,
                  style =
                    Stroke(
                      width = if (highlightedSegment == "Pending") strokeWidth + 4.dp.toPx() else strokeWidth,
                      cap = StrokeCap.Butt,
                    ),
                )
                currentStart += pendingSweep
              }
              if (usedSweep > 0f) {
                drawArc(
                  color = usedClr,
                  startAngle = currentStart,
                  sweepAngle = usedSweep,
                  useCenter = false,
                  topLeft = topLeft,
                  size = arcSize,
                  style =
                    Stroke(
                      width = if (highlightedSegment == "Used") strokeWidth + 4.dp.toPx() else strokeWidth,
                      cap = StrokeCap.Butt,
                    ),
                )
              }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              Text(
                text =
                  when (highlightedSegment) {
                    "Pending" -> "${totalPending.toInt()}d"
                    "Used" -> "${totalUsed.toInt()}d"
                    else -> "${totalRemaining.toInt()}d"
                  },
                style = MonoMetricStyle.copy(fontSize = 24.sp),
                color = MaterialTheme.colorScheme.onSurface,
              )
              Text(
                text = "$highlightedSegment / ${totalAllocated.toInt()}d",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }

          Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            ChartLegendStatRow(
              label = "Remaining",
              days = totalRemaining,
              total = safeTotal,
              color = remainingClr,
              isHighlighted = highlightedSegment == "Remaining",
              onClick = { highlightedSegment = "Remaining" },
            )
            ChartLegendStatRow(
              label = "Pending",
              days = totalPending,
              total = safeTotal,
              color = pendingClr,
              isHighlighted = highlightedSegment == "Pending",
              onClick = { highlightedSegment = "Pending" },
            )
            ChartLegendStatRow(
              label = "Used",
              days = totalUsed,
              total = safeTotal,
              color = usedClr,
              isHighlighted = highlightedSegment == "Used",
              onClick = { highlightedSegment = "Used" },
            )
          }
        }
      } else {
        // Stacked Horizontal Utilization Bars per Leave Type
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          balances.forEach { bal ->
            val isSelected = selectedLeaveTypeId == bal.leaveTypeId
            val alloc = max(bal.allocatedDays, 1.0)
            val remRatio = (bal.remainingDays / alloc).toFloat().coerceIn(0f, 1f)
            val pendRatio = (bal.pendingDays / alloc).toFloat().coerceIn(0f, 1f)
            val usedRatio = (bal.usedDays / alloc).toFloat().coerceIn(0f, 1f)

            val remClr = MaterialTheme.colorScheme.primary
            val pendClr = semantic.warning
            val usedClr = MaterialTheme.colorScheme.outline
            val trackClr = MaterialTheme.colorScheme.surfaceVariant

            Surface(
              onClick = {
                selectedLeaveTypeId =
                  if (selectedLeaveTypeId == bal.leaveTypeId) null else bal.leaveTypeId
              },
              shape = MaterialTheme.shapes.small,
              color =
                if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                else Color.Transparent,
              border =
                if (isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                else null,
              modifier = Modifier.fillMaxWidth().testTag("leave_bar_${bal.leaveCode}"),
            ) {
              Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Text(
                    text = "${bal.leaveCode} • ${bal.leaveTypeName}",
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                  )
                  Text(
                    text = "${bal.remainingDays.toInt()} left / ${bal.allocatedDays.toInt()}d",
                    style = MonoTelemetryStyle,
                    color = MaterialTheme.colorScheme.primary,
                  )
                }

                Canvas(modifier = Modifier.fillMaxWidth().height(12.dp)) {
                  val r = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                  drawRoundRect(color = trackClr, cornerRadius = r)
                  var x = 0f
                  val wRem = size.width * remRatio
                  if (wRem > 0f) {
                    drawRoundRect(
                      color = remClr,
                      topLeft = Offset(x, 0f),
                      size = Size(wRem, size.height),
                      cornerRadius = r,
                    )
                    x += wRem
                  }
                  val wPend = size.width * pendRatio
                  if (wPend > 0f) {
                    drawRect(
                      color = pendClr,
                      topLeft = Offset(x, 0f),
                      size = Size(wPend, size.height),
                    )
                    x += wPend
                  }
                  val wUsed = size.width * usedRatio
                  if (wUsed > 0f) {
                    drawRect(
                      color = usedClr,
                      topLeft = Offset(x, 0f),
                      size = Size(wUsed, size.height),
                    )
                  }
                }
              }
            }
          }
        }
      }

      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = "Tap any ring segment or leave chip to inspect quota",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.weight(1f),
        )
        TextButton(
          onClick = onPlanLeave,
          modifier = Modifier.testTag("chart_plan_leave_button"),
        ) {
          Text("Apply Leave")
        }
      }
    }
  }
}

@Composable
private fun ChartLegendStatRow(
  label: String,
  days: Double,
  total: Double,
  color: Color,
  isHighlighted: Boolean,
  onClick: () -> Unit,
) {
  val pct = ((days / total) * 100.0).toInt().coerceIn(0, 100)
  Surface(
    onClick = onClick,
    shape = MaterialTheme.shapes.small,
    color =
      if (isHighlighted) color.copy(alpha = 0.14f)
      else Color.Transparent,
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(color))
        Text(
          text = label,
          style = MaterialTheme.typography.bodySmall,
          fontWeight = if (isHighlighted) FontWeight.Bold else FontWeight.Medium,
        )
      }
      Text(
        text = "${days.toInt()}d ($pct%)",
        style = MonoTelemetryStyle,
        color = MaterialTheme.colorScheme.onSurface,
      )
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InteractiveUpcomingHolidayChartCard(
  holidays: List<HolidaySchedule>,
  onPlanBridgeLeave: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val semantic = PulseTheme.semantic
  var activeFilter by remember { mutableStateOf(HolidayFilter.ALL) }

  val filteredHolidays =
    remember(holidays, activeFilter) {
      when (activeFilter) {
        HolidayFilter.ALL -> holidays
        HolidayFilter.MANDATORY ->
          holidays.filter {
            it.category == HolidayCategory.PUBLIC || it.category == HolidayCategory.CORPORATE
          }
        HolidayFilter.BRANCH -> holidays.filter { it.category == HolidayCategory.FLOATING }
      }
    }

  var selectedHolidayId by remember(filteredHolidays) {
    mutableStateOf(filteredHolidays.firstOrNull()?.id)
  }

  val selectedHoliday =
    filteredHolidays.firstOrNull { it.id == selectedHolidayId } ?: filteredHolidays.firstOrNull()

  Card(
    modifier = modifier.fillMaxWidth().testTag("upcoming_holidays_chart_card"),
    shape = MaterialTheme.shapes.large,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
  ) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
          Icon(
            imageVector = Icons.Filled.Celebration,
            contentDescription = null,
            tint = semantic.holiday,
          )
          Column {
            Text(
              text = "Upcoming Holiday Horizon",
              style = MaterialTheme.typography.titleMedium,
            )
            Text(
              text = "Tap any holiday marker to preview branch schedule",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
        selectedHoliday?.let { next ->
          StatusPill(
            text = "In ${next.daysUntil}d",
            containerColor = semantic.holidayContainer,
            contentColor = semantic.onHolidayContainer,
          )
        }
      }

      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        HolidayFilter.entries.forEach { filter ->
          FilterChip(
            selected = activeFilter == filter,
            onClick = { activeFilter = filter },
            label = { Text(filter.label) },
            modifier = Modifier.testTag("holiday_filter_${filter.name}"),
          )
        }
      }

      if (filteredHolidays.isNotEmpty()) {
        // Interactive Canvas Timeline Chart (0 to maxDays horizon)
        val maxHorizonDays = max(60, (filteredHolidays.maxOfOrNull { it.daysUntil } ?: 50) + 5)
        val axisColor = MaterialTheme.colorScheme.outlineVariant
        val holidayNodeColor = semantic.holiday
        val floatingNodeColor = semantic.warning
        val corporateNodeColor = MaterialTheme.colorScheme.primary

        Box(
          modifier =
            Modifier.fillMaxWidth()
              .clip(MaterialTheme.shapes.medium)
              .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
              .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
          Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Text(
                text = "TODAY",
                style = MonoTelemetryStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              Text(
                text = "+30 DAYS",
                style = MonoTelemetryStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              Text(
                text = "+${maxHorizonDays}D",
                style = MonoTelemetryStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }

            Canvas(
              modifier =
                Modifier.fillMaxWidth().height(54.dp).pointerInput(filteredHolidays) {
                  detectTapGestures { tapOffset ->
                    val pad = 16.dp.toPx()
                    val usableWidth = (size.width - pad * 2f).coerceAtLeast(1f)
                    val tapped =
                      filteredHolidays.minByOrNull { holiday ->
                        val ratio = (holiday.daysUntil.toFloat() / maxHorizonDays).coerceIn(0f, 1f)
                        val nodeX = pad + usableWidth * ratio
                        kotlin.math.abs(tapOffset.x - nodeX)
                      }
                    tapped?.let { selectedHolidayId = it.id }
                  }
                }
            ) {
              val pad = 16.dp.toPx()
              val usableWidth = (size.width - pad * 2f).coerceAtLeast(1f)
              val trackY = size.height / 2f

              // Horizon axis line
              drawLine(
                color = axisColor,
                start = Offset(pad, trackY),
                end = Offset(size.width - pad, trackY),
                strokeWidth = 4.dp.toPx(),
                cap = StrokeCap.Round,
              )

              // Month tick mark at +30 days
              val midX = pad + usableWidth * (30f / maxHorizonDays)
              drawLine(
                color = axisColor,
                start = Offset(midX, trackY - 8.dp.toPx()),
                end = Offset(midX, trackY + 8.dp.toPx()),
                strokeWidth = 1.5.dp.toPx(),
              )

              // Holiday nodes
              filteredHolidays.forEach { holiday ->
                val ratio = (holiday.daysUntil.toFloat() / maxHorizonDays).coerceIn(0f, 1f)
                val x = pad + usableWidth * ratio
                val isSelected = holiday.id == selectedHoliday?.id
                val nodeColor =
                  when (holiday.category) {
                    HolidayCategory.PUBLIC -> holidayNodeColor
                    HolidayCategory.CORPORATE -> corporateNodeColor
                    HolidayCategory.FLOATING -> floatingNodeColor
                  }

                if (isSelected) {
                  drawCircle(
                    color = nodeColor.copy(alpha = 0.22f),
                    radius = 18.dp.toPx(),
                    center = Offset(x, trackY),
                  )
                }
                drawCircle(
                  color = Color.White,
                  radius = if (isSelected) 10.dp.toPx() else 7.dp.toPx(),
                  center = Offset(x, trackY),
                )
                drawCircle(
                  color = nodeColor,
                  radius = if (isSelected) 7.5.dp.toPx() else 5.dp.toPx(),
                  center = Offset(x, trackY),
                )
              }
            }
          }
        }

        // Selected Holiday Detail Callout
        selectedHoliday?.let { holiday ->
          Surface(
            shape = MaterialTheme.shapes.medium,
            color = semantic.holidayContainer.copy(alpha = 0.45f),
            border = BorderStroke(1.dp, semantic.holiday.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth().testTag("selected_holiday_callout"),
          ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Text(
                  text = holiday.title,
                  style = MaterialTheme.typography.titleSmall,
                  fontWeight = FontWeight.SemiBold,
                  modifier = Modifier.weight(1f),
                )
                Text(
                  text = "${holiday.dayLabel} • ${holiday.branchScope}",
                  style = MonoTelemetryStyle,
                  color = semantic.onHolidayContainer,
                )
              }
              Text(
                text = holiday.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }

        // Selectable Holiday Schedule Rows
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          filteredHolidays.forEach { holiday ->
            val isSelected = holiday.id == selectedHoliday?.id
            val badgeBg =
              when (holiday.category) {
                HolidayCategory.PUBLIC -> semantic.holidayContainer
                HolidayCategory.CORPORATE -> MaterialTheme.colorScheme.primaryContainer
                HolidayCategory.FLOATING -> semantic.warningContainer
              }
            val badgeFg =
              when (holiday.category) {
                HolidayCategory.PUBLIC -> semantic.onHolidayContainer
                HolidayCategory.CORPORATE -> MaterialTheme.colorScheme.onPrimaryContainer
                HolidayCategory.FLOATING -> semantic.onWarningContainer
              }

            Surface(
              onClick = { selectedHolidayId = holiday.id },
              shape = MaterialTheme.shapes.small,
              color =
                if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
              border =
                if (isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                else null,
              modifier = Modifier.fillMaxWidth().testTag("holiday_row_${holiday.id}"),
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
              ) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(10.dp),
                  modifier = Modifier.weight(1f),
                ) {
                  Icon(
                    imageVector = Icons.Filled.CalendarToday,
                    contentDescription = null,
                    tint = semantic.holiday,
                    modifier = Modifier.size(16.dp),
                  )
                  Column {
                    Text(
                      text = holiday.title,
                      style = MaterialTheme.typography.bodyMedium,
                      fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                      text = "${holiday.dayLabel} (${holiday.dateIso})",
                      style = MonoTelemetryStyle,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  }
                }
                StatusPill(
                  text = "${holiday.daysUntil}d",
                  containerColor = badgeBg,
                  contentColor = badgeFg,
                )
              }
            }
          }
        }
      } else {
        Text(
          text = "No holidays match this filter.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = "Plan PTO around upcoming holidays",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(
          onClick = onPlanBridgeLeave,
          modifier = Modifier.testTag("holiday_plan_leave_button"),
        ) {
          Text("Open Leave Planner")
        }
      }
    }
  }
}
