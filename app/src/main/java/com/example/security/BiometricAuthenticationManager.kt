package com.example.security

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.example.domain.model.AuditOutcome
import com.example.domain.model.AuthChallengePurpose
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

@Singleton
class BiometricAuthenticationManager @Inject constructor(
  private val auditRecorder: BiometricAuditRecorder,
) : BiometricPromptInterface {

  override fun getAvailabilityStatus(context: Context): BiometricAvailability {
    val manager = BiometricManager.from(context)
    val strongStatus = manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
    if (strongStatus == BiometricManager.BIOMETRIC_SUCCESS) {
      return BiometricAvailability.AVAILABLE_STRONG
    }

    val weakOrFaceStatus = manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK)
    if (weakOrFaceStatus == BiometricManager.BIOMETRIC_SUCCESS) {
      return BiometricAvailability.AVAILABLE_WEAK_OR_FACE
    }

    val deviceCredStatus =
      manager.canAuthenticate(
        BiometricManager.Authenticators.BIOMETRIC_STRONG or
          BiometricManager.Authenticators.DEVICE_CREDENTIAL
      )
    if (deviceCredStatus == BiometricManager.BIOMETRIC_SUCCESS) {
      return BiometricAvailability.DEVICE_CREDENTIAL_ONLY
    }

    return when (weakOrFaceStatus) {
      BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> BiometricAvailability.NO_HARDWARE
      BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> BiometricAvailability.HARDWARE_UNAVAILABLE
      BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricAvailability.NONE_ENROLLED
      BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED ->
        BiometricAvailability.SECURITY_UPDATE_REQUIRED
      else -> BiometricAvailability.UNSUPPORTED
    }
  }

  override fun getCapability(context: Context): BiometricCapability {
    return when (getAvailabilityStatus(context)) {
      BiometricAvailability.AVAILABLE_STRONG -> BiometricCapability.STRONG_BIOMETRIC
      BiometricAvailability.AVAILABLE_WEAK_OR_FACE -> BiometricCapability.BIOMETRIC_WEAK_OR_FACE
      BiometricAvailability.DEVICE_CREDENTIAL_ONLY -> BiometricCapability.DEVICE_CREDENTIAL_ONLY
      else -> BiometricCapability.PIN_FALLBACK_ONLY
    }
  }

  override fun canAuthenticateWithBiometrics(context: Context): Boolean =
    getCapability(context) != BiometricCapability.PIN_FALLBACK_ONLY

  override fun hasEnrolledFingerprintOrFace(context: Context): Boolean {
    val capability = getCapability(context)
    return capability == BiometricCapability.STRONG_BIOMETRIC ||
      capability == BiometricCapability.BIOMETRIC_WEAK_OR_FACE
  }

  override suspend fun authenticateForAppEntry(
    activity: FragmentActivity,
    employeeEmail: String,
  ): BiometricAuthOutcome {
    return authenticate(
      activity = activity,
      title = AuthChallengePurpose.APP_UNLOCK.title,
      subtitle = "Verify fingerprint or facial recognition to unlock $employeeEmail",
      negativeButtonText = "Use App PIN",
      confirmationRequired = false,
    )
  }

  override suspend fun authenticate(
    activity: FragmentActivity,
    title: String,
    subtitle: String,
    negativeButtonText: String,
    confirmationRequired: Boolean,
  ): BiometricAuthOutcome {
    val capability = getCapability(activity)
    if (capability == BiometricCapability.PIN_FALLBACK_ONLY) {
      auditRecorder.recordBiometricAttempt(
        purposeLabel = title,
        outcome = AuditOutcome.FALLBACK,
        authMethod = capability.name,
        failureReason = "Biometric hardware unavailable or not enrolled; routed to App PIN",
        screenName = "APP_LOCK",
      )
      return BiometricAuthOutcome.UsePinFallback
    }

    return suspendCancellableCoroutine { continuation ->
      val executor = ContextCompat.getMainExecutor(activity)
      val callback =
        object : BiometricPrompt.AuthenticationCallback() {
          override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
            super.onAuthenticationSucceeded(result)
            auditRecorder.recordBiometricAttempt(
              purposeLabel = title,
              outcome = AuditOutcome.SUCCESS,
              authMethod = resolveResultMethod(result, capability),
              screenName = "APP_LOCK",
            )
            if (continuation.isActive) continuation.resume(BiometricAuthOutcome.Succeeded)
          }

          override fun onAuthenticationFailed() {
            super.onAuthenticationFailed()
            auditRecorder.recordBiometricAttempt(
              purposeLabel = title,
              outcome = AuditOutcome.FAILED,
              authMethod = capability.name,
              failureReason = "Biometric sample did not match enrolled template",
              screenName = "APP_LOCK",
            )
          }

          override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
            super.onAuthenticationError(errorCode, errString)
            val outcome = mapErrorToOutcome(errorCode, errString.toString())
            recordMappedError(
              purposeLabel = title,
              capability = capability,
              errorCode = errorCode,
              errMessage = errString.toString(),
              outcome = outcome,
              screenName = "APP_LOCK",
            )
            if (!continuation.isActive) return
            continuation.resume(outcome)
          }
        }

      val prompt = BiometricPrompt(activity, executor, callback)
      val promptInfo =
        buildPromptInfo(
          capability = capability,
          title = title,
          subtitle = subtitle,
          negativeButtonText = negativeButtonText,
          confirmationRequired = confirmationRequired,
        )

      continuation.invokeOnCancellation { prompt.cancelAuthentication() }
      prompt.authenticate(promptInfo)
    }
  }

  override fun authenticate(
    activity: FragmentActivity,
    purpose: AuthChallengePurpose,
    onSuccess: () -> Unit,
    onUsePinFallback: () -> Unit,
    onError: (String) -> Unit,
  ) {
    val capability = getCapability(activity)
    if (capability == BiometricCapability.PIN_FALLBACK_ONLY) {
      auditRecorder.recordBiometricAttempt(
        purposeLabel = purpose.title,
        outcome = AuditOutcome.FALLBACK,
        authMethod = capability.name,
        failureReason = "Biometric hardware unavailable or not enrolled; routed to App PIN",
        screenName = purpose.name,
      )
      onUsePinFallback()
      return
    }

    val executor = ContextCompat.getMainExecutor(activity)
    val callback =
      object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
          super.onAuthenticationSucceeded(result)
          auditRecorder.recordBiometricAttempt(
            purposeLabel = purpose.title,
            outcome = AuditOutcome.SUCCESS,
            authMethod = resolveResultMethod(result, capability),
            screenName = purpose.name,
          )
          onSuccess()
        }

        override fun onAuthenticationFailed() {
          super.onAuthenticationFailed()
          auditRecorder.recordBiometricAttempt(
            purposeLabel = purpose.title,
            outcome = AuditOutcome.FAILED,
            authMethod = capability.name,
            failureReason = "Biometric sample did not match enrolled template",
            screenName = purpose.name,
          )
        }

        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
          super.onAuthenticationError(errorCode, errString)
          val outcome = mapErrorToOutcome(errorCode, errString.toString())
          recordMappedError(
            purposeLabel = purpose.title,
            capability = capability,
            errorCode = errorCode,
            errMessage = errString.toString(),
            outcome = outcome,
            screenName = purpose.name,
          )
          when (outcome) {
            BiometricAuthOutcome.Succeeded -> onSuccess()
            BiometricAuthOutcome.UsePinFallback -> onUsePinFallback()
            BiometricAuthOutcome.Cancelled -> onError("Authentication cancelled.")
            is BiometricAuthOutcome.Error -> onError(outcome.message)
          }
        }
      }

    val prompt = BiometricPrompt(activity, executor, callback)
    val promptInfo =
      buildPromptInfo(
        capability = capability,
        title = purpose.title,
        subtitle = purpose.subtitle,
        negativeButtonText = "Use App PIN",
        confirmationRequired = true,
      )
    prompt.authenticate(promptInfo)
  }

  private fun resolveResultMethod(
    result: BiometricPrompt.AuthenticationResult,
    fallbackCapability: BiometricCapability,
  ): String {
    return when (result.authenticationType) {
      BiometricPrompt.AUTHENTICATION_RESULT_TYPE_BIOMETRIC -> fallbackCapability.name
      BiometricPrompt.AUTHENTICATION_RESULT_TYPE_DEVICE_CREDENTIAL -> "DEVICE_CREDENTIAL"
      else -> fallbackCapability.name
    }
  }

  private fun recordMappedError(
    purposeLabel: String,
    capability: BiometricCapability,
    errorCode: Int,
    errMessage: String,
    outcome: BiometricAuthOutcome,
    screenName: String,
  ) {
    val (auditOutcome, reason) =
      when (outcome) {
        BiometricAuthOutcome.Cancelled ->
          AuditOutcome.CANCELLED to "Prompt dismissed by user (Code $errorCode)"
        BiometricAuthOutcome.UsePinFallback ->
          if (errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
            AuditOutcome.FALLBACK to "User switched to App PIN fallback"
          } else {
            AuditOutcome.FAILED to "$errMessage (Code $errorCode) • Redirected to PIN"
          }
        is BiometricAuthOutcome.Error ->
          AuditOutcome.FAILED to "$errMessage (Code $errorCode)"
        BiometricAuthOutcome.Succeeded ->
          AuditOutcome.SUCCESS to null
      }
    auditRecorder.recordBiometricAttempt(
      purposeLabel = purposeLabel,
      outcome = auditOutcome,
      authMethod = capability.name,
      failureReason = reason,
      screenName = screenName,
    )
  }

  private fun buildPromptInfo(
    capability: BiometricCapability,
    title: String,
    subtitle: String,
    negativeButtonText: String,
    confirmationRequired: Boolean,
  ): BiometricPrompt.PromptInfo {
    val builder =
      BiometricPrompt.PromptInfo.Builder()
        .setTitle(title)
        .setSubtitle(subtitle)
        .setConfirmationRequired(confirmationRequired)

    when (capability) {
      BiometricCapability.STRONG_BIOMETRIC,
      BiometricCapability.BIOMETRIC_WEAK_OR_FACE -> {
        // Allow both Class 3 (Fingerprint / 3D Face) and Class 2 (Facial Recognition) sensors.
        builder
          .setAllowedAuthenticators(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
              BiometricManager.Authenticators.BIOMETRIC_WEAK
          )
          .setNegativeButtonText(negativeButtonText)
      }
      BiometricCapability.DEVICE_CREDENTIAL_ONLY -> {
        // Note: setNegativeButtonText must NOT be called when DEVICE_CREDENTIAL is included.
        builder.setAllowedAuthenticators(
          BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        )
      }
      BiometricCapability.PIN_FALLBACK_ONLY -> {
        // Callers short-circuit before building PromptInfo.
      }
    }

    return builder.build()
  }

  private fun mapErrorToOutcome(errorCode: Int, errorMessage: String): BiometricAuthOutcome {
    return when (errorCode) {
      BiometricPrompt.ERROR_NEGATIVE_BUTTON,
      BiometricPrompt.ERROR_NO_BIOMETRICS,
      BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL,
      BiometricPrompt.ERROR_HW_NOT_PRESENT,
      BiometricPrompt.ERROR_HW_UNAVAILABLE,
      BiometricPrompt.ERROR_LOCKOUT,
      BiometricPrompt.ERROR_LOCKOUT_PERMANENT -> BiometricAuthOutcome.UsePinFallback
      BiometricPrompt.ERROR_USER_CANCELED,
      BiometricPrompt.ERROR_CANCELED -> BiometricAuthOutcome.Cancelled
      else -> BiometricAuthOutcome.Error(errorCode, errorMessage)
    }
  }
}
