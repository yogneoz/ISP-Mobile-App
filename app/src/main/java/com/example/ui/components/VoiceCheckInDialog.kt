package com.example.ui.components

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.ui.screens.attendance.PunchConfirmationState
import com.example.ui.theme.MonoTelemetryStyle
import com.example.ui.theme.PulseTheme
import com.example.voice.VoiceListeningUiState
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun VoiceCheckInDialog(
  sessionInfo: PunchConfirmationState,
  listeningState: VoiceListeningUiState,
  expectedPhrase: String,
  hasMicPermission: Boolean,
  onMicPermissionGranted: () -> Unit,
  onStartListening: () -> Unit,
  onStopListening: () -> Unit,
  onUpdatePhrase: (String) -> Unit,
  onDismiss: () -> Unit,
) {
  val semantic = PulseTheme.semantic
  var editingPhrase by remember { mutableStateOf(false) }
  var phraseDraft by remember(expectedPhrase) { mutableStateOf(expectedPhrase) }

  val micPermissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
      if (granted) {
        onMicPermissionGranted()
      }
    }

  val isListening = listeningState is VoiceListeningUiState.Listening
  val infiniteTransition = rememberInfiniteTransition(label = "micPulse")
  val pulsePhase by
    infiniteTransition.animateFloat(
      initialValue = 0f,
      targetValue = (Math.PI * 2.0).toFloat(),
      animationSpec =
        infiniteRepeatable(
          animation = tween(durationMillis = 900),
          repeatMode = RepeatMode.Restart,
        ),
      label = "wavePhase",
    )

  AlertDialog(
    onDismissRequest = onDismiss,
    icon = {
      val bg =
        when (listeningState) {
          is VoiceListeningUiState.Authenticated -> semantic.verifiedContainer
          is VoiceListeningUiState.Rejected,
          is VoiceListeningUiState.Error -> semantic.dangerContainer
          is VoiceListeningUiState.Listening -> MaterialTheme.colorScheme.primaryContainer
          VoiceListeningUiState.Idle -> MaterialTheme.colorScheme.surfaceVariant
        }
      val fg =
        when (listeningState) {
          is VoiceListeningUiState.Authenticated -> semantic.onVerifiedContainer
          is VoiceListeningUiState.Rejected,
          is VoiceListeningUiState.Error -> semantic.onDangerContainer
          is VoiceListeningUiState.Listening -> MaterialTheme.colorScheme.onPrimaryContainer
          VoiceListeningUiState.Idle -> MaterialTheme.colorScheme.onSurfaceVariant
        }
      Box(
        modifier = Modifier.size(52.dp).clip(CircleShape).background(bg),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          imageVector =
            when (listeningState) {
              is VoiceListeningUiState.Authenticated -> Icons.Filled.CheckCircle
              is VoiceListeningUiState.Rejected,
              is VoiceListeningUiState.Error -> Icons.Filled.Warning
              else -> Icons.Filled.Mic
            },
          contentDescription = "Voice authentication status",
          tint = fg,
          modifier = Modifier.size(28.dp),
        )
      }
    },
    title = {
      Text(
        text = "Voice Check-In Authentication",
        textAlign = TextAlign.Center,
      )
    },
    text = {
      Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        StatusPill(
          text =
            "Inside ${sessionInfo.branch.code} (${sessionInfo.evaluation.distanceMeters?.roundToInt() ?: 0}m / ${sessionInfo.branch.geofenceRadiusMeters.roundToInt()}m)",
          containerColor = semantic.verifiedContainer,
          contentColor = semantic.onVerifiedContainer,
          icon = Icons.Filled.GpsFixed,
        )

        // Required Phrase Card
        Surface(
          shape = MaterialTheme.shapes.medium,
          color = MaterialTheme.colorScheme.surfaceVariant,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                text = "AUTHENTICATION PHRASE",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              TextButton(
                onClick = { editingPhrase = !editingPhrase },
                modifier = Modifier.testTag("toggle_edit_voice_phrase"),
              ) {
                Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(if (editingPhrase) "Done" else "Change")
              }
            }

            if (editingPhrase) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                OutlinedTextField(
                  value = phraseDraft,
                  onValueChange = { phraseDraft = it },
                  singleLine = true,
                  label = { Text("Custom phrase") },
                  modifier = Modifier.weight(1f).testTag("voice_phrase_input"),
                )
                Button(
                  onClick = {
                    onUpdatePhrase(phraseDraft)
                    editingPhrase = false
                  },
                  enabled = phraseDraft.trim().split(Regex("\\s+")).size >= 2,
                  modifier = Modifier.testTag("save_voice_phrase_button"),
                ) {
                  Text("Save")
                }
              }
            } else {
              Text(
                text = "Say: \"$expectedPhrase\"",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
              )
            }
          }
        }

        // Animated RMS Equalizer Canvas while listening
        if (isListening) {
          val rmsNormalized =
            (((listeningState as VoiceListeningUiState.Listening).rmsDb + 2f) / 12f).coerceIn(
              0.15f,
              1f,
            )
          val barColor = MaterialTheme.colorScheme.primary
          Canvas(modifier = Modifier.fillMaxWidth().height(42.dp)) {
            val barCount = 9
            val barWidth = 10f
            val spacing = (size.width - barCount * barWidth) / (barCount + 1)
            val centerY = size.height / 2f
            for (i in 0 until barCount) {
              val wave = abs(sin(pulsePhase + i * 0.65f))
              val heightFraction = (0.25f + wave * rmsNormalized * 0.75f).coerceIn(0.2f, 1f)
              val barH = size.height * heightFraction
              val x = spacing * (i + 1) + barWidth * i
              drawRoundRect(
                color = barColor,
                topLeft = Offset(x, centerY - barH / 2f),
                size = Size(barWidth, barH),
                cornerRadius = CornerRadius(5f, 5f),
              )
            }
          }
        }

        // Recognition Feedback Box
        when (listeningState) {
          VoiceListeningUiState.Idle -> {
            Text(
              text =
                if (hasMicPermission) "Tap Start Listening and speak your authentication phrase."
                else "Allow microphone access to verify your spoken check-in phrase.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              textAlign = TextAlign.Center,
            )
          }
          is VoiceListeningUiState.Listening -> {
            Text(
              text =
                if (listeningState.partialTranscript.isNotBlank())
                  "Hearing: \"${listeningState.partialTranscript}\""
                else "Listening via microphone…",
              style = MonoTelemetryStyle,
              color = MaterialTheme.colorScheme.primary,
              textAlign = TextAlign.Center,
            )
          }
          is VoiceListeningUiState.Authenticated -> {
            Text(
              text = "Authenticated (\"${listeningState.matchedPhrase}\"). Recording clock-in…",
              style = MaterialTheme.typography.bodySmall,
              color = semantic.verified,
              textAlign = TextAlign.Center,
            )
          }
          is VoiceListeningUiState.Rejected -> {
            Surface(
              color = semantic.dangerContainer,
              shape = MaterialTheme.shapes.small,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Text(
                text =
                  "Authentication rejected. Heard \"${listeningState.heardPhrase}\" — expected \"${listeningState.expectedPhrase}\".",
                style = MaterialTheme.typography.bodySmall,
                color = semantic.onDangerContainer,
                modifier = Modifier.padding(10.dp),
              )
            }
          }
          is VoiceListeningUiState.Error -> {
            Surface(
              color = semantic.dangerContainer,
              shape = MaterialTheme.shapes.small,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Text(
                text = listeningState.message,
                style = MaterialTheme.typography.bodySmall,
                color = semantic.onDangerContainer,
                modifier = Modifier.padding(10.dp),
              )
            }
          }
        }
      }
    },
    confirmButton = {
      when {
        !hasMicPermission -> {
          Button(
            onClick = { micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
            modifier = Modifier.testTag("grant_mic_permission_button"),
          ) {
            Icon(Icons.Filled.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Enable Microphone")
          }
        }
        isListening -> {
          OutlinedButton(
            onClick = onStopListening,
            modifier = Modifier.testTag("stop_voice_listening_button"),
          ) {
            Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Stop")
          }
        }
        else -> {
          Button(
            onClick = onStartListening,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            modifier = Modifier.testTag("start_voice_listening_button"),
          ) {
            Icon(Icons.Filled.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Speak Check-In Phrase")
          }
        }
      }
    },
    dismissButton = {
      TextButton(
        onClick = onDismiss,
        modifier = Modifier.testTag("dismiss_voice_dialog_button"),
      ) {
        Text("Cancel")
      }
    },
  )
}
