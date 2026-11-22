package com.example.security

import android.content.Context
import androidx.fragment.app.FragmentActivity
import com.example.domain.model.AuthChallengePurpose

enum class BiometricCapability {
  STRONG_BIOMETRIC,
  BIOMETRIC_WEAK_OR_FACE,
  DEVICE_CREDENTIAL_ONLY,
  PIN_FALLBACK_ONLY,
}

enum class BiometricAvailability {
  AVAILABLE_STRONG,
  AVAILABLE_WEAK_OR_FACE,
  DEVICE_CREDENTIAL_ONLY,
  NO_HARDWARE,
  HARDWARE_UNAVAILABLE,
  NONE_ENROLLED,
  SECURITY_UPDATE_REQUIRED,
  UNSUPPORTED,
}

sealed interface BiometricAuthOutcome {
  data object Succeeded : BiometricAuthOutcome

  data object UsePinFallback : BiometricAuthOutcome

  data object Cancelled : BiometricAuthOutcome

  data class Error(val errorCode: Int, val message: String) : BiometricAuthOutcome
}

/**
 * Contract for system BiometricPrompt (fingerprint / facial recognition) authentication across app
 * entry and sensitive HR operations.
 */
interface BiometricPromptInterface {

  fun getAvailabilityStatus(context: Context): BiometricAvailability

  fun getCapability(context: Context): BiometricCapability

  fun canAuthenticateWithBiometrics(context: Context): Boolean

  fun hasEnrolledFingerprintOrFace(context: Context): Boolean

  suspend fun authenticateForAppEntry(
    activity: FragmentActivity,
    employeeEmail: String,
  ): BiometricAuthOutcome

  suspend fun authenticate(
    activity: FragmentActivity,
    title: String,
    subtitle: String,
    negativeButtonText: String = "Use App PIN",
    confirmationRequired: Boolean = true,
  ): BiometricAuthOutcome

  fun authenticate(
    activity: FragmentActivity,
    purpose: AuthChallengePurpose,
    onSuccess: () -> Unit,
    onUsePinFallback: () -> Unit,
    onError: (String) -> Unit,
  )
}
