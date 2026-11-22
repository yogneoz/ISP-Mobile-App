package com.example.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.PendingActions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.InteractiveLeaveBalanceChartCard
import com.example.ui.components.InteractiveUpcomingHolidayChartCard
import com.example.ui.components.MetricSummaryCard
import com.example.ui.screens.attendance.AttendanceViewModel
import com.example.ui.theme.PulseTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeaveHolidayDashboardScreen(
  onBack: () -> Unit,
  onNavigateToLeaves: () -> Unit,
  viewModel: AttendanceViewModel = hiltViewModel(),
) {
  val profile by viewModel.profile.collectAsStateWithLifecycle()
  val balances by viewModel.leaveBalances.collectAsStateWithLifecycle()
  val holidays by viewModel.upcomingHolidays.collectAsStateWithLifecycle()

  val totalRemaining = balances.sumOf { it.remainingDays }.toInt()
  val totalPending = balances.sumOf { it.pendingDays }.toInt()
  val nextHolidayDays = holidays.firstOrNull()?.daysUntil

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Text(
            text = "Leave & Holiday Analytics",
            style = MaterialTheme.typography.titleLarge,
          )
        },
        navigationIcon = {
          IconButton(
            onClick = onBack,
            modifier =
              Modifier.minimumInteractiveComponentSize().testTag("dashboard_analytics_back"),
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = "Back to Home",
            )
          }
        },
      )
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
            text =
              "${profile?.fullName ?: "Employee"} • ${profile?.branch?.name ?: "Assigned Branch"}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }

        item {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            MetricSummaryCard(
              title = "Remaining",
              value = "${totalRemaining}d",
              subtitle = "Across ${balances.size} types",
              icon = Icons.Filled.EventAvailable,
              accentColor = MaterialTheme.colorScheme.primary,
              modifier = Modifier.weight(1f),
            )
            MetricSummaryCard(
              title = "Pending",
              value = "${totalPending}d",
              subtitle = "In approval",
              icon = Icons.Filled.PendingActions,
              accentColor = PulseTheme.semantic.warning,
              modifier = Modifier.weight(1f),
            )
            MetricSummaryCard(
              title = "Next Holiday",
              value = if (nextHolidayDays != null) "${nextHolidayDays}d" else "—",
              subtitle = holidays.firstOrNull()?.dayLabel ?: "None",
              icon = Icons.Filled.Celebration,
              accentColor = PulseTheme.semantic.holiday,
              modifier = Modifier.weight(1f),
            )
          }
        }

        item {
          InteractiveLeaveBalanceChartCard(
            balances = balances,
            onPlanLeave = onNavigateToLeaves,
          )
        }

        item {
          InteractiveUpcomingHolidayChartCard(
            holidays = holidays,
            onPlanBridgeLeave = onNavigateToLeaves,
          )
        }

        item { Spacer(modifier = Modifier.height(8.dp)) }
      }
    }
  }
}
