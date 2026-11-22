package com.example.ui.screens.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.model.RoleBundle
import com.example.domain.model.UserSession
import com.example.security.BiometricAvailability
import com.example.ui.components.NumericPinPad
import com.example.ui.components.StatusPill
import com.example.ui.theme.MidnightSlate
import com.example.ui.theme.MonoTelemetryStyle
import com.example.ui.theme.PulseTheme

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LoginScreen(
  isLoading: Boolean,
  errorMessage: String?,
  onLogin: (email: String, password: String, bundles: List<RoleBundle>, initialPin: String) -> Unit,
) {
  var selectedBundles by remember { mutableStateOf(setOf(RoleBundle.FIELD_TECHNICIAN)) }
  var email by remember { mutableStateOf(RoleBundle.FIELD_TECHNICIAN.defaultEmail) }
  var password by remember { mutableStateOf("Enterprise#2026") }
  var securityPin by remember { mutableStateOf("1234") }

  val combinedCapabilities = remember(selectedBundles) {
    RoleBundle.capabilitiesForBundles(selectedBundles)
  }

  Scaffold(
    containerColor = MaterialTheme.colorScheme.background,
  ) { innerPadding ->
    Box(
      modifier =
        Modifier.fillMaxSize()
          .padding(innerPadding)
          .verticalScroll(rememberScrollState()),
      contentAlignment = Alignment.Center,
    ) {
      Column(
        modifier = Modifier.widthIn(max = 540.dp).fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(14.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Image(
            painter = painterResource(id = R.drawable.img_pulsehr_icon),
            contentDescription = "PulseHR Logo",
            modifier = Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)),
          )
          Column {
            Text(
              text = "PulseHR • MyWorkplace v2",
              style = MaterialTheme.typography.headlineSmall,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
              text = "Offline-First Field & Enterprise HR Suite",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }

        Spacer(modifier = Modifier.height(18.dp))

        Card(
          modifier = Modifier.fillMaxWidth(),
          shape = MaterialTheme.shapes.large,
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
          Column(modifier = Modifier.padding(20.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                text = "Sign In & Capability Bundles",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
              )
              StatusPill(
                text = "TLS + 24h Token",
                containerColor = PulseTheme.semantic.verifiedContainer,
                contentColor = PulseTheme.semantic.onVerifiedContainer,
                icon = Icons.Filled.Security,
              )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
              text = "Select 1 or more Capability Bundles (HR grants bundles, not fixed job tiers)",
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              RoleBundle.entries.forEach { bundle ->
                val isSelected = bundle in selectedBundles
                FilterChip(
                  selected = isSelected,
                  onClick = {
                    val next =
                      if (isSelected && selectedBundles.size > 1) {
                        selectedBundles - bundle
                      } else if (isSelected) {
                        selectedBundles
                      } else {
                        selectedBundles + bundle
                      }
                    selectedBundles = next
                    email =
                      next.maxByOrNull { it.baseRole.ordinal }?.defaultEmail
                        ?: bundle.defaultEmail
                  },
                  label = { Text(bundle.badgeLabel) },
                  leadingIcon =
                    if (isSelected) {
                      { Icon(Icons.Filled.Badge, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    } else null,
                  modifier = Modifier.testTag("bundle_chip_${bundle.name}"),
                )
              }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Surface(
              shape = MaterialTheme.shapes.small,
              color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
              modifier = Modifier.fillMaxWidth(),
            ) {
              Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                  text = "Granted Capabilities (${combinedCapabilities.size}):",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                  text = combinedCapabilities.joinToString(" • ") { it.key },
                  style = MonoTelemetryStyle,
                  color = MaterialTheme.colorScheme.primary,
                )
              }
            }

            Spacer(modifier = Modifier.height(14.dp))

            OutlinedTextField(
              value = email,
              onValueChange = { email = it },
              label = { Text("Corporate Email") },
              singleLine = true,
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
              modifier = Modifier.fillMaxWidth().testTag("login_email_input"),
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
              value = password,
              onValueChange = { password = it },
              label = { Text("Enterprise Password") },
              singleLine = true,
              visualTransformation = PasswordVisualTransformation(),
              modifier = Modifier.fillMaxWidth().testTag("login_password_input"),
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
              value = securityPin,
              onValueChange = { input ->
                if (input.length <= 4 && input.all { it.isDigit() }) securityPin = input
              },
              label = { Text("4-Digit Biometric Fallback PIN") },
              singleLine = true,
              visualTransformation = PasswordVisualTransformation(),
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
              supportingText = {
                Text("Device-bound token valid 24h offline • AES-256 GCM Keystore.")
              },
              modifier = Modifier.fillMaxWidth().testTag("login_pin_input"),
            )

            if (!errorMessage.isNullOrBlank()) {
              Spacer(modifier = Modifier.height(10.dp))
              Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier.fillMaxWidth(),
              ) {
                Text(
                  text = errorMessage,
                  color = MaterialTheme.colorScheme.onErrorContainer,
                  style = MaterialTheme.typography.bodySmall,
                  modifier = Modifier.padding(10.dp),
                )
              }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Button(
              onClick = { onLogin(email, password, selectedBundles.toList(), securityPin) },
              enabled = !isLoading,
              modifier = Modifier.fillMaxWidth().height(52.dp).testTag("login_submit_button"),
            ) {
              if (isLoading) {
                CircularProgressIndicator(
                  modifier = Modifier.size(20.dp),
                  strokeWidth = 2.dp,
                  color = MaterialTheme.colorScheme.onPrimary,
                )
                Spacer(modifier = Modifier.width(8.dp))
              }
              Text("Register Device & Sign In")
            }
          }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Text(
          text = "Sensitive screens (Payslip, Bank, PAN, SSF, Vault) require online verification and are write-audited per device.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Center,
        )
      }
    }
  }
}

@Composable
fun AppLockScreen(
  session: UserSession,
  lockReason: String?,
  biometricAvailability: BiometricAvailability,
  biometricError: String?,
  isBiometricPromptActive: Boolean,
  onTriggerBiometric: () -> Unit,
  onVerifyPin: (String) -> Boolean,
  onSignOut: () -> Unit,
) {
  var pinInput by remember { mutableStateOf("") }
  var pinError by remember { mutableStateOf<String?>(null) }

  val canUseBiometrics =
    biometricAvailability == BiometricAvailability.AVAILABLE_STRONG ||
      biometricAvailability == BiometricAvailability.AVAILABLE_WEAK_OR_FACE ||
      biometricAvailability == BiometricAvailability.DEVICE_CREDENTIAL_ONLY

  val availabilityLabel =
    when (biometricAvailability) {
      BiometricAvailability.AVAILABLE_STRONG -> "Fingerprint & Face Unlock Ready"
      BiometricAvailability.AVAILABLE_WEAK_OR_FACE -> "Facial Recognition Ready"
      BiometricAvailability.DEVICE_CREDENTIAL_ONLY -> "Device Lock Credential Ready"
      BiometricAvailability.NONE_ENROLLED -> "No Biometrics Enrolled — PIN Required"
      BiometricAvailability.NO_HARDWARE -> "No Biometric Sensor — PIN Required"
      BiometricAvailability.HARDWARE_UNAVAILABLE -> "Biometric Sensor Busy — PIN Fallback"
      BiometricAvailability.SECURITY_UPDATE_REQUIRED -> "Security Update Required — PIN Fallback"
      BiometricAvailability.UNSUPPORTED -> "Biometrics Unsupported — PIN Fallback"
    }

  LaunchedEffect(session.userId, canUseBiometrics) {
    if (canUseBiometrics) {
      onTriggerBiometric()
    }
  }

  Scaffold(
    contentWindowInsets = WindowInsets.safeDrawing,
  ) { innerPadding ->
    Box(
      modifier =
        Modifier.fillMaxSize()
          .background(
            Brush.verticalGradient(
              colors =
                listOf(
                  MidnightSlate,
                  MaterialTheme.colorScheme.background,
                )
            )
          )
          .padding(innerPadding)
          .verticalScroll(rememberScrollState()),
      contentAlignment = Alignment.Center,
    ) {
      Column(
        modifier = Modifier.widthIn(max = 440.dp).fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Box(
          modifier =
            Modifier.size(72.dp)
              .clip(CircleShape)
              .background(MaterialTheme.colorScheme.primaryContainer),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            imageVector = Icons.Filled.VerifiedUser,
            contentDescription = "Session Locked",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(36.dp),
          )
        }

        Spacer(modifier = Modifier.height(14.dp))
        Text(
          text = "PulseHR Vault Locked",
          style = MaterialTheme.typography.headlineSmall,
          color = Color.White,
          fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
          text = "${session.email} • ${session.deviceId}",
          style = MaterialTheme.typography.bodySmall,
          color = Color.White.copy(alpha = 0.85f),
        )
        Spacer(modifier = Modifier.height(8.dp))
        StatusPill(
          text = availabilityLabel,
          containerColor =
            if (canUseBiometrics) PulseTheme.semantic.verifiedContainer
            else PulseTheme.semantic.warningContainer,
          contentColor =
            if (canUseBiometrics) PulseTheme.semantic.onVerifiedContainer
            else PulseTheme.semantic.onWarningContainer,
          icon =
            if (biometricAvailability == BiometricAvailability.AVAILABLE_WEAK_OR_FACE) {
              Icons.Filled.Face
            } else {
              Icons.Filled.Fingerprint
            },
        )
        if (!lockReason.isNullOrBlank()) {
          Spacer(modifier = Modifier.height(6.dp))
          StatusPill(
            text = lockReason,
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            icon = Icons.Filled.Lock,
          )
        }

        Spacer(modifier = Modifier.height(18.dp))

        if (canUseBiometrics) {
          Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
          ) {
            Column(
              modifier = Modifier.padding(18.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
            ) {
              Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Icon(
                  imageVector = Icons.Filled.Fingerprint,
                  contentDescription = "Fingerprint authentication",
                  tint = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.size(28.dp),
                )
                Icon(
                  imageVector = Icons.Filled.Face,
                  contentDescription = "Facial recognition authentication",
                  tint = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.size(28.dp),
                )
              }
              Spacer(modifier = Modifier.height(8.dp))
              Text(
                text = "Biometric Entry Verification",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
              )
              Spacer(modifier = Modifier.height(4.dp))
              Text(
                text =
                  "Confirm your fingerprint or facial recognition via Android BiometricPrompt to unlock PulseHR.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
              )

              if (!biometricError.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                  shape = MaterialTheme.shapes.small,
                  color = MaterialTheme.colorScheme.errorContainer,
                  modifier = Modifier.fillMaxWidth().testTag("biometric_entry_error"),
                ) {
                  Text(
                    text = biometricError,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(10.dp),
                  )
                }
              }

              Spacer(modifier = Modifier.height(14.dp))
              Button(
                onClick = onTriggerBiometric,
                enabled = !isBiometricPromptActive,
                modifier =
                  Modifier.fillMaxWidth().height(50.dp).testTag("unlock_biometric_button"),
              ) {
                if (isBiometricPromptActive) {
                  CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                  )
                  Spacer(modifier = Modifier.width(8.dp))
                  Text("Awaiting Biometric…")
                } else {
                  Icon(Icons.Filled.Fingerprint, contentDescription = null)
                  Spacer(modifier = Modifier.width(8.dp))
                  Text("Verify Fingerprint or Face")
                }
              }
            }
          }

          Spacer(modifier = Modifier.height(14.dp))
        }

        Card(
          modifier = Modifier.fillMaxWidth(),
          shape = MaterialTheme.shapes.large,
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
          Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
          ) {
            Text(
              text =
                if (canUseBiometrics) "Or Enter 4-Digit Fallback PIN"
                else "Enter 4-Digit Security PIN",
              style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(14.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
              repeat(4) { idx ->
                val filled = idx < pinInput.length
                Box(
                  modifier =
                    Modifier.size(16.dp)
                      .clip(CircleShape)
                      .background(
                        if (filled) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant
                      )
                )
              }
            }

            if (!pinError.isNullOrBlank()) {
              Spacer(modifier = Modifier.height(10.dp))
              Text(
                text = pinError!!,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
              )
            }

            Spacer(modifier = Modifier.height(18.dp))

            NumericPinPad(
              onDigit = { digit ->
                pinError = null
                if (pinInput.length < 4) {
                  pinInput += digit
                  if (pinInput.length == 4) {
                    val ok = onVerifyPin(pinInput)
                    if (!ok) {
                      pinError = "Incorrect PIN. Try again."
                    }
                    pinInput = ""
                  }
                }
              },
              onBackspace = {
                if (pinInput.isNotEmpty()) pinInput = pinInput.dropLast(1)
              },
            )

            Spacer(modifier = Modifier.height(10.dp))
            TextButton(
              onClick = onSignOut,
              modifier = Modifier.testTag("lock_signout_button"),
            ) {
              Text("Sign Out & Switch Account")
            }
          }
        }
      }
    }
  }
}
