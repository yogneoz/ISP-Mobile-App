package com.example.ui.screens.leaves

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.model.LeaveRequest
import com.example.domain.model.LeaveStatus
import com.example.ui.components.StatusPill
import com.example.ui.theme.MonoTelemetryStyle
import com.example.ui.theme.PulseTheme
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

internal data class LeaveCalendarDay(
  val dayOfMonth: Int,
  val dateIso: String,
  val approvedRequests: List<LeaveRequest>,
  val pendingRequests: List<LeaveRequest>,
  val isToday: Boolean,
) {
  val hasApproved: Boolean get() = approvedRequests.isNotEmpty()
  val hasPending: Boolean get() = pendingRequests.isNotEmpty()
  val allRequests: List<LeaveRequest> get() = approvedRequests + pendingRequests
}

internal data class LeaveCalendarMonthGrid(
  val year: Int,
  val monthZeroBased: Int,
  val monthTitle: String,
  val cells: List<LeaveCalendarDay?>,
  val approvedDayCount: Int,
  val pendingDayCount: Int,
)

internal object LeaveCalendarBuilder {
  private val weekdayNames = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")

  fun weekdayHeaders(): List<String> = weekdayNames

  fun buildMonthGrid(
    year: Int,
    monthZeroBased: Int,
    requests: List<LeaveRequest>,
    todayIso: String = currentTodayIso(),
  ): LeaveCalendarMonthGrid {
    val activeRequests =
      requests.filter {
        it.status == LeaveStatus.APPROVED || it.status == LeaveStatus.PENDING
      }

    val cal =
      Calendar.getInstance(Locale.US).apply {
        clear()
        set(Calendar.YEAR, year)
        set(Calendar.MONTH, monthZeroBased)
        set(Calendar.DAY_OF_MONTH, 1)
      }

    val monthTitle = SimpleDateFormat("MMMM yyyy", Locale.US).format(cal.time)
    val firstDayOffset = (cal.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY).coerceIn(0, 6)
    val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)

    val dayCells = ArrayList<LeaveCalendarDay?>(42)
    repeat(firstDayOffset) { dayCells.add(null) }

    var approvedDays = 0
    var pendingDays = 0

    for (day in 1..daysInMonth) {
      val dateIso = formatIsoDate(year, monthZeroBased, day)
      val matching =
        activeRequests.filter { req ->
          isDateInRange(dateIso, req.startDate, req.endDate)
        }
      val approved = matching.filter { it.status == LeaveStatus.APPROVED }
      val pending = matching.filter { it.status == LeaveStatus.PENDING }
      if (approved.isNotEmpty()) approvedDays++
      if (pending.isNotEmpty()) pendingDays++

      dayCells.add(
        LeaveCalendarDay(
          dayOfMonth = day,
          dateIso = dateIso,
          approvedRequests = approved,
          pendingRequests = pending,
          isToday = dateIso == todayIso,
        )
      )
    }

    while (dayCells.size % 7 != 0) {
      dayCells.add(null)
    }

    return LeaveCalendarMonthGrid(
      year = year,
      monthZeroBased = monthZeroBased,
      monthTitle = monthTitle,
      cells = dayCells,
      approvedDayCount = approvedDays,
      pendingDayCount = pendingDays,
    )
  }

  fun parseYearMonth(dateIso: String): Pair<Int, Int>? {
    val parts = dateIso.trim().split("-")
    if (parts.size != 3) return null
    val year = parts[0].toIntOrNull() ?: return null
    val monthOneBased = parts[1].toIntOrNull() ?: return null
    if (monthOneBased !in 1..12) return null
    return year to (monthOneBased - 1)
  }

  fun isDateInRange(dateIso: String, startIso: String, endIso: String): Boolean {
    val normalizedStart = startIso.trim()
    val normalizedEnd = endIso.trim().ifEmpty { normalizedStart }
    val (low, high) =
      if (normalizedStart <= normalizedEnd) normalizedStart to normalizedEnd
      else normalizedEnd to normalizedStart
    return dateIso in low..high
  }

  fun formatIsoDate(year: Int, monthZeroBased: Int, dayOfMonth: Int): String {
    return String.format(Locale.US, "%04d-%02d-%02d", year, monthZeroBased + 1, dayOfMonth)
  }

  fun currentTodayIso(): String {
    val now = Calendar.getInstance(Locale.US)
    return formatIsoDate(
      now.get(Calendar.YEAR),
      now.get(Calendar.MONTH),
      now.get(Calendar.DAY_OF_MONTH),
    )
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LeaveCalendarView(
  requests: List<LeaveRequest>,
  modifier: Modifier = Modifier,
  onRequestSelected: ((LeaveRequest) -> Unit)? = null,
) {
  val semantic = PulseTheme.semantic
  val activeRequests =
    remember(requests) {
      requests.filter { it.status == LeaveStatus.APPROVED || it.status == LeaveStatus.PENDING }
    }

  val initialDate =
    remember {
      val firstActive = activeRequests.firstOrNull()?.startDate
      val parsed = firstActive?.let { LeaveCalendarBuilder.parseYearMonth(it) }
      if (parsed != null) {
        parsed
      } else {
        2026 to Calendar.NOVEMBER
      }
    }

  var displayedYear by remember { mutableIntStateOf(initialDate.first) }
  var displayedMonth by remember { mutableIntStateOf(initialDate.second) }
  var selectedDateIso by remember {
    mutableStateOf(
      activeRequests.firstOrNull()?.startDate
        ?: LeaveCalendarBuilder.formatIsoDate(initialDate.first, initialDate.second, 1)
    )
  }

  val monthGrid =
    remember(displayedYear, displayedMonth, activeRequests) {
      LeaveCalendarBuilder.buildMonthGrid(
        year = displayedYear,
        monthZeroBased = displayedMonth,
        requests = activeRequests,
      )
    }

  val selectedDay =
    remember(monthGrid, selectedDateIso) {
      monthGrid.cells.filterNotNull().firstOrNull { it.dateIso == selectedDateIso }
        ?: monthGrid.cells.filterNotNull().firstOrNull { it.allRequests.isNotEmpty() }
        ?: monthGrid.cells.filterNotNull().firstOrNull()
    }

  fun shiftMonth(deltaMonths: Int) {
    val cal =
      Calendar.getInstance(Locale.US).apply {
        clear()
        set(Calendar.YEAR, displayedYear)
        set(Calendar.MONTH, displayedMonth)
        set(Calendar.DAY_OF_MONTH, 1)
        add(Calendar.MONTH, deltaMonths)
      }
    displayedYear = cal.get(Calendar.YEAR)
    displayedMonth = cal.get(Calendar.MONTH)
    selectedDateIso = LeaveCalendarBuilder.formatIsoDate(displayedYear, displayedMonth, 1)
  }

  OutlinedCard(
    modifier = modifier.fillMaxWidth().testTag("leave_calendar_view"),
    shape = MaterialTheme.shapes.large,
    colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
  ) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      // Header Row with Month Switcher
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
            imageVector = Icons.Filled.CalendarMonth,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
          )
          Column {
            Text(
              text = "Leave Schedule Calendar",
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
              text = monthGrid.monthTitle,
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.SemiBold,
              modifier = Modifier.testTag("leave_calendar_month_title"),
            )
          }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
          TextButton(
            onClick = {
              val target =
                activeRequests.firstOrNull()?.startDate?.let {
                  LeaveCalendarBuilder.parseYearMonth(it)
                } ?: (2026 to Calendar.NOVEMBER)
              displayedYear = target.first
              displayedMonth = target.second
              selectedDateIso =
                activeRequests.firstOrNull()?.startDate
                  ?: LeaveCalendarBuilder.formatIsoDate(target.first, target.second, 1)
            },
            modifier = Modifier.testTag("leave_calendar_today_button"),
          ) {
            Text("Active")
          }
          IconButton(
            onClick = { shiftMonth(-1) },
            modifier =
              Modifier.minimumInteractiveComponentSize().testTag("leave_calendar_prev_month"),
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
              contentDescription = "Previous month",
            )
          }
          IconButton(
            onClick = { shiftMonth(1) },
            modifier =
              Modifier.minimumInteractiveComponentSize().testTag("leave_calendar_next_month"),
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
              contentDescription = "Next month",
            )
          }
        }
      }

      // Monthly Approved vs Pending Chips
      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        AssistChip(
          onClick = {},
          label = { Text("${monthGrid.approvedDayCount}d Approved") },
          leadingIcon = {
            Icon(
              imageVector = Icons.Filled.CheckCircle,
              contentDescription = null,
              tint = semantic.verified,
              modifier = Modifier.size(16.dp),
            )
          },
          colors =
            AssistChipDefaults.assistChipColors(
              containerColor = semantic.verifiedContainer.copy(alpha = 0.5f),
              labelColor = semantic.onVerifiedContainer,
            ),
        )
        AssistChip(
          onClick = {},
          label = { Text("${monthGrid.pendingDayCount}d Pending") },
          leadingIcon = {
            Icon(
              imageVector = Icons.Filled.Schedule,
              contentDescription = null,
              tint = semantic.warning,
              modifier = Modifier.size(16.dp),
            )
          },
          colors =
            AssistChipDefaults.assistChipColors(
              containerColor = semantic.warningContainer.copy(alpha = 0.55f),
              labelColor = semantic.onWarningContainer,
            ),
        )
      }

      // Weekday Headers
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        LeaveCalendarBuilder.weekdayHeaders().forEach { weekday ->
          Text(
            text = weekday,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
          )
        }
      }

      // Month-View Grid
      val weeks = monthGrid.cells.chunked(7)
      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        weeks.forEach { week ->
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            week.forEach { day ->
              if (day == null) {
                Spacer(modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp))
              } else {
                val isSelected = day.dateIso == selectedDay?.dateIso
                LeaveCalendarDayCell(
                  day = day,
                  isSelected = isSelected,
                  onClick = { selectedDateIso = day.dateIso },
                  modifier = Modifier.weight(1f),
                )
              }
            }
          }
        }
      }

      // Legend Row
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        CalendarLegendItem(label = "Approved", color = semantic.verified)
        CalendarLegendItem(label = "Pending", color = semantic.warning)
        CalendarLegendItem(label = "Selected", color = MaterialTheme.colorScheme.primary)
      }

      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

      // Selected Day Leave Details
      if (selectedDay != null) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Text(
            text = "Schedule • ${selectedDay.dateIso}",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.testTag("leave_calendar_selected_date_label"),
          )

          if (selectedDay.allRequests.isEmpty()) {
            Text(
              text = "No approved or pending leave requests on this date.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          } else {
            selectedDay.allRequests.forEach { req ->
              val isApproved = req.status == LeaveStatus.APPROVED
              val pillBg = if (isApproved) semantic.verifiedContainer else semantic.warningContainer
              val pillFg =
                if (isApproved) semantic.onVerifiedContainer else semantic.onWarningContainer

              Surface(
                onClick = { onRequestSelected?.invoke(req) },
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                modifier = Modifier.fillMaxWidth().testTag("leave_calendar_detail_${req.id}"),
              ) {
                ListItem(
                  colors =
                    ListItemDefaults.colors(containerColor = Color.Transparent),
                  headlineContent = {
                    Text(
                      text = "${req.leaveTypeName} • ${req.employeeName}",
                      style = MaterialTheme.typography.bodyMedium,
                      fontWeight = FontWeight.Medium,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                    )
                  },
                  supportingContent = {
                    Text(
                      text = "${req.startDate} → ${req.endDate} (${req.totalDays.toInt()}d)",
                      style = MonoTelemetryStyle,
                    )
                  },
                  trailingContent = {
                    StatusPill(
                      text = req.status.label,
                      containerColor = pillBg,
                      contentColor = pillFg,
                    )
                  },
                )
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun LeaveCalendarDayCell(
  day: LeaveCalendarDay,
  isSelected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val semantic = PulseTheme.semantic
  val containerColor =
    when {
      isSelected -> MaterialTheme.colorScheme.primaryContainer
      day.hasApproved && day.hasPending -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
      day.hasApproved -> semantic.verifiedContainer.copy(alpha = 0.65f)
      day.hasPending -> semantic.warningContainer.copy(alpha = 0.7f)
      else -> Color.Transparent
    }
  val contentColor =
    when {
      isSelected -> MaterialTheme.colorScheme.onPrimaryContainer
      day.hasApproved -> semantic.onVerifiedContainer
      day.hasPending -> semantic.onWarningContainer
      else -> MaterialTheme.colorScheme.onSurface
    }
  val borderStroke =
    when {
      isSelected -> BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
      day.isToday -> BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
      else -> null
    }

  Surface(
    onClick = onClick,
    modifier = modifier.defaultMinSize(minHeight = 48.dp).testTag("leave_calendar_day_${day.dayOfMonth}"),
    shape = MaterialTheme.shapes.small,
    color = containerColor,
    contentColor = contentColor,
    border = borderStroke,
  ) {
    Column(
      modifier = Modifier.padding(vertical = 6.dp, horizontal = 2.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
    ) {
      Text(
        text = day.dayOfMonth.toString(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight =
          if (isSelected || day.hasApproved || day.hasPending) FontWeight.Bold
          else FontWeight.Medium,
      )
      Spacer(modifier = Modifier.height(4.dp))
      Row(
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        if (day.hasApproved) {
          Box(
            modifier =
              Modifier.size(6.dp)
                .clip(CircleShape)
                .background(semantic.verified)
          )
        }
        if (day.hasPending) {
          Box(
            modifier =
              Modifier.size(6.dp)
                .clip(CircleShape)
                .background(semantic.warning)
          )
        }
        if (!day.hasApproved && !day.hasPending) {
          Spacer(modifier = Modifier.height(6.dp))
        }
      }
    }
  }
}

@Composable
private fun CalendarLegendItem(label: String, color: Color) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(color))
    Spacer(modifier = Modifier.width(6.dp))
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}
