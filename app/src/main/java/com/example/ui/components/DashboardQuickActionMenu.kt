package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.theme.PulseTheme

@Composable
fun DashboardQuickActionMenu(
  expanded: Boolean,
  onExpandedChange: (Boolean) -> Unit,
  hasCheckedIn: Boolean,
  hasCheckedOut: Boolean,
  checkInTimeLabel: String?,
  checkOutTimeLabel: String?,
  onClockInClick: () -> Unit,
  onClockOutClick: () -> Unit,
  onSubmitExpenseClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val semantic = PulseTheme.semantic
  val fabRotation by animateFloatAsState(
    targetValue = if (expanded) 45f else 0f,
    label = "quick_action_fab_rotation",
  )

  Box(modifier = modifier.fillMaxSize()) {
    AnimatedVisibility(
      visible = expanded,
      enter = fadeIn(),
      exit = fadeOut(),
    ) {
      Box(
        modifier =
          Modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.34f))
            .clickable(
              interactionSource = remember { MutableInteractionSource() },
              indication = null,
            ) {
              onExpandedChange(false)
            }
            .testTag("quick_action_scrim")
      )
    }

    Column(
      modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
      horizontalAlignment = Alignment.End,
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      AnimatedVisibility(
        visible = expanded,
        enter = fadeIn() + expandVertically(expandFrom = Alignment.Bottom) + slideInVertically { it / 3 },
        exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Bottom),
      ) {
        Column(
          horizontalAlignment = Alignment.End,
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          QuickActionMenuItem(
            title = "Submit Expense",
            subtitle = "Fuel, meal, travel or OT claim",
            icon = Icons.AutoMirrored.Filled.ReceiptLong,
            badgeContainerColor = MaterialTheme.colorScheme.secondaryContainer,
            badgeContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            onClick = {
              onExpandedChange(false)
              onSubmitExpenseClick()
            },
            modifier = Modifier.testTag("quick_action_submit_expense"),
          )

          QuickActionMenuItem(
            title = "Clock Out",
            subtitle =
              when {
                hasCheckedOut -> "Logged at ${checkOutTimeLabel ?: "today"}"
                hasCheckedIn -> "Complete shift & verify punch"
                else -> "Clock in first before clocking out"
              },
            icon = Icons.AutoMirrored.Filled.Logout,
            badgeContainerColor = semantic.warningContainer,
            badgeContentColor = semantic.onWarningContainer,
            onClick = {
              onExpandedChange(false)
              onClockOutClick()
            },
            modifier = Modifier.testTag("quick_action_clock_out"),
          )

          QuickActionMenuItem(
            title = "Clock In",
            subtitle =
              if (hasCheckedIn) "Logged at ${checkInTimeLabel ?: "today"}"
              else "Instant biometric & GPS punch",
            icon = Icons.AutoMirrored.Filled.Login,
            badgeContainerColor =
              if (hasCheckedIn) semantic.verifiedContainer
              else MaterialTheme.colorScheme.primaryContainer,
            badgeContentColor =
              if (hasCheckedIn) semantic.onVerifiedContainer
              else MaterialTheme.colorScheme.onPrimaryContainer,
            onClick = {
              onExpandedChange(false)
              onClockInClick()
            },
            modifier = Modifier.testTag("quick_action_clock_in"),
          )
        }
      }

      FloatingActionButton(
        onClick = { onExpandedChange(!expanded) },
        shape = FloatingActionButtonDefaults.extendedFabShape,
        containerColor =
          if (expanded) MaterialTheme.colorScheme.secondaryContainer
          else MaterialTheme.colorScheme.primary,
        contentColor =
          if (expanded) MaterialTheme.colorScheme.onSecondaryContainer
          else MaterialTheme.colorScheme.onPrimary,
        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp),
        modifier =
          Modifier.minimumInteractiveComponentSize()
            .semantics {
              contentDescription =
                if (expanded) "Close Quick Action menu" else "Open Quick Action menu"
            }
            .testTag("dashboard_quick_action_fab"),
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 16.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Icon(
            imageVector = Icons.Filled.Add,
            contentDescription = null,
            modifier = Modifier.rotate(fabRotation),
          )
          Text(
            text = if (expanded) "Close" else "Quick Action",
            style = MaterialTheme.typography.labelLarge,
          )
        }
      }
    }
  }
}

@Composable
private fun QuickActionMenuItem(
  title: String,
  subtitle: String,
  icon: ImageVector,
  badgeContainerColor: Color,
  badgeContentColor: Color,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Surface(
    onClick = onClick,
    shape = RoundedCornerShape(16.dp),
    color = MaterialTheme.colorScheme.surface,
    contentColor = MaterialTheme.colorScheme.onSurface,
    tonalElevation = 4.dp,
    shadowElevation = 6.dp,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    modifier = modifier.heightIn(min = 52.dp),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Column(horizontalAlignment = Alignment.End) {
        Text(
          text = title,
          style = MaterialTheme.typography.labelLarge,
          fontWeight = FontWeight.SemiBold,
          color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
          text = subtitle,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      Surface(
        shape = CircleShape,
        color = badgeContainerColor,
        contentColor = badgeContentColor,
        modifier = Modifier.size(40.dp),
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
          )
        }
      }
    }
  }
}
