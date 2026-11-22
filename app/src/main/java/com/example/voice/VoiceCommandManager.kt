package com.example.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface VoiceListeningUiState {
  data object Idle : VoiceListeningUiState

  data class Listening(
    val rmsDb: Float = 0f,
    val partialTranscript: String = "",
  ) : VoiceListeningUiState

  data class Authenticated(val matchedPhrase: String) : VoiceListeningUiState

  data class Rejected(
    val heardPhrase: String,
    val expectedPhrase: String,
  ) : VoiceListeningUiState

  data class Error(val message: String) : VoiceListeningUiState
}

@Singleton
class VoiceCommandManager @Inject constructor(
  @ApplicationContext private val context: Context,
) {

  private val mainHandler = Handler(Looper.getMainLooper())
  private var speechRecognizer: SpeechRecognizer? = null
  private var expectedPhrase: String = "Pulse check in"

  private val _state = MutableStateFlow<VoiceListeningUiState>(VoiceListeningUiState.Idle)
  val state: StateFlow<VoiceListeningUiState> = _state.asStateFlow()

  fun hasMicrophonePermission(): Boolean {
    return ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
      PackageManager.PERMISSION_GRANTED
  }

  fun isSpeechRecognitionAvailable(): Boolean =
    SpeechRecognizer.isRecognitionAvailable(context)

  fun resetState() {
    mainHandler.post {
      destroyRecognizerInternal()
      _state.value = VoiceListeningUiState.Idle
    }
  }

  fun stopListening() {
    mainHandler.post {
      runCatching { speechRecognizer?.stopListening() }
      if (_state.value is VoiceListeningUiState.Listening) {
        _state.value = VoiceListeningUiState.Idle
      }
    }
  }

  fun startListening(authPhrase: String) {
    if (!hasMicrophonePermission()) {
      _state.value =
        VoiceListeningUiState.Error(
          "Microphone permission (RECORD_AUDIO) is required for voice check-in."
        )
      return
    }
    if (!isSpeechRecognitionAvailable()) {
      _state.value =
        VoiceListeningUiState.Error(
          "Speech recognition service is not installed or unavailable on this device."
        )
      return
    }

    expectedPhrase = authPhrase
    mainHandler.post {
      destroyRecognizerInternal()
      try {
        val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer.setRecognitionListener(
          object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
              _state.value = VoiceListeningUiState.Listening(rmsDb = 0f, partialTranscript = "")
            }

            override fun onBeginningOfSpeech() {}

            override fun onRmsChanged(rmsdB: Float) {
              val current = _state.value
              if (current is VoiceListeningUiState.Listening) {
                _state.value = current.copy(rmsDb = rmsdB)
              }
            }

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {}

            override fun onError(error: Int) {
              val msg =
                when (error) {
                  SpeechRecognizer.ERROR_AUDIO -> "Microphone audio recording error."
                  SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                    "Microphone permission denied."
                  SpeechRecognizer.ERROR_NETWORK,
                  SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                    "Speech recognition network timeout. Try speaking clearly again."
                  SpeechRecognizer.ERROR_NO_MATCH ->
                    "Could not recognize speech. Tap mic and say \"$expectedPhrase\"."
                  SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
                    "Speech recognizer is busy. Please try again."
                  SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                    "No speech detected. Tap mic and say \"$expectedPhrase\"."
                  else -> "Voice recognition failed (code $error). Try again."
                }
              _state.value = VoiceListeningUiState.Error(msg)
            }

            override fun onResults(results: Bundle?) {
              val candidates =
                results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
              val verification =
                VoiceAuthVerifier.verifySpokenCandidates(candidates, expectedPhrase)
              if (verification.isAuthenticated && verification.matchedCandidate != null) {
                _state.value = VoiceListeningUiState.Authenticated(verification.matchedCandidate)
              } else {
                _state.value =
                  VoiceListeningUiState.Rejected(
                    heardPhrase =
                      verification.heardSummary.ifBlank { "(Unrecognized speech)" },
                    expectedPhrase = expectedPhrase,
                  )
              }
            }

            override fun onPartialResults(partialResults: Bundle?) {
              val partial =
                partialResults
                  ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                  ?.firstOrNull()
                  .orEmpty()
              val current = _state.value
              if (current is VoiceListeningUiState.Listening && partial.isNotBlank()) {
                _state.value = current.copy(partialTranscript = partial)
              }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
          }
        )

        val intent =
          Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
              RecognizerIntent.EXTRA_LANGUAGE_MODEL,
              RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
          }

        speechRecognizer = recognizer
        _state.value = VoiceListeningUiState.Listening(rmsDb = 0f, partialTranscript = "")
        recognizer.startListening(intent)
      } catch (e: Exception) {
        _state.value =
          VoiceListeningUiState.Error(e.message ?: "Unable to start microphone listener.")
      }
    }
  }

  private fun destroyRecognizerInternal() {
    speechRecognizer?.let { rec ->
      runCatching { rec.cancel() }
      runCatching { rec.destroy() }
    }
    speechRecognizer = null
  }
}
