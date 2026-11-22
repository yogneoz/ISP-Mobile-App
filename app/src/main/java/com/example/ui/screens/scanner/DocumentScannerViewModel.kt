package com.example.ui.screens.scanner

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.domain.model.DocumentType
import com.example.domain.repository.DocumentRepository
import com.example.domain.repository.RepoResult
import com.example.scanner.DetectedDocumentQuad
import com.example.scanner.DocumentFilterMode
import com.example.scanner.DocumentOcrExtraction
import com.example.scanner.DocumentScanProcessor
import com.example.scanner.DocumentTextAnalyzer
import com.example.scanner.LiveOcrHint
import com.example.scanner.NormalizedPoint
import com.example.scanner.ScanExportFormat
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ScannerStage {
  CAMERA_CAPTURE,
  CROP_ADJUST,
  FILTER_REVIEW,
}

data class DocumentScannerUiState(
  val stage: ScannerStage = ScannerStage.CAMERA_CAPTURE,
  val liveQuad: DetectedDocumentQuad = DocumentScanProcessor.defaultQuad(),
  val liveOcrHint: LiveOcrHint = LiveOcrHint(),
  val flashEnabled: Boolean = false,
  val autoCaptureEnabled: Boolean = false,
  val isProcessing: Boolean = false,
  val isRunningOcr: Boolean = false,
  val isSaving: Boolean = false,
  val activeRawBitmap: Bitmap? = null,
  val activeQuad: DetectedDocumentQuad = DocumentScanProcessor.defaultQuad(),
  val activeCroppedBitmap: Bitmap? = null,
  val activeEnhancedBitmap: Bitmap? = null,
  val selectedFilter: DocumentFilterMode = DocumentFilterMode.MAGIC_SCAN,
  val completedPages: List<Bitmap> = emptyList(),
  val exportFormat: ScanExportFormat = ScanExportFormat.PDF,
  val documentTitle: String =
    "Scanned_Doc_${SimpleDateFormat("MMMdd_HHmm", Locale.US).format(Date())}",
  val documentType: DocumentType = DocumentType.COMPLIANCE,
  val ocrExtraction: DocumentOcrExtraction? = null,
  val hasUserEditedTitle: Boolean = false,
  val hasUserEditedCategory: Boolean = false,
  val estimatedSizeBytes: Long = 0L,
  val errorMessage: String? = null,
  val savedSuccessMessage: String? = null,
)

@HiltViewModel
class DocumentScannerViewModel @Inject constructor(
  @ApplicationContext private val context: Context,
  private val documentRepository: DocumentRepository,
  private val documentTextAnalyzer: DocumentTextAnalyzer,
) : ViewModel() {

  private val _uiState = MutableStateFlow(DocumentScannerUiState())
  val uiState: StateFlow<DocumentScannerUiState> = _uiState.asStateFlow()

  fun updateLiveQuad(quad: DetectedDocumentQuad) {
    _uiState.update { it.copy(liveQuad = quad) }
  }

  fun updateLiveOcrHint(hint: LiveOcrHint) {
    _uiState.update { it.copy(liveOcrHint = hint) }
  }

  fun analyzeLiveFrameForOcr(bitmap: Bitmap, onDone: () -> Unit = {}) {
    viewModelScope.launch {
      try {
        val hint =
          withContext(Dispatchers.Default) {
            documentTextAnalyzer.detectLiveTextHint(bitmap)
          }
        _uiState.update { it.copy(liveOcrHint = hint) }
      } catch (_: Exception) {
        // Ignore transient camera analysis failure
      } finally {
        bitmap.recycle()
        onDone()
      }
    }
  }

  fun toggleFlash() {
    _uiState.update { it.copy(flashEnabled = !it.flashEnabled) }
  }

  fun toggleAutoCapture() {
    _uiState.update { it.copy(autoCaptureEnabled = !it.autoCaptureEnabled) }
  }

  fun onCapturedBitmap(bitmap: Bitmap) {
    viewModelScope.launch {
      _uiState.update { it.copy(isProcessing = true, errorMessage = null) }
      val detected = withContext(Dispatchers.Default) { DocumentScanProcessor.detectDocumentEdges(bitmap) }
      _uiState.update {
        it.copy(
          stage = ScannerStage.CROP_ADJUST,
          activeRawBitmap = bitmap,
          activeQuad = detected,
          isProcessing = false,
        )
      }
    }
  }

  fun onImportedPhotoUri(uri: Uri) {
    viewModelScope.launch {
      _uiState.update { it.copy(isProcessing = true, errorMessage = null) }
      val decoded =
        withContext(Dispatchers.IO) { DocumentScanProcessor.decodeScaledBitmapFromUri(context, uri) }
      if (decoded == null) {
        _uiState.update {
          it.copy(isProcessing = false, errorMessage = "Unable to decode selected image.")
        }
        return@launch
      }
      val detected =
        withContext(Dispatchers.Default) { DocumentScanProcessor.detectDocumentEdges(decoded) }
      _uiState.update {
        it.copy(
          stage = ScannerStage.CROP_ADJUST,
          activeRawBitmap = decoded,
          activeQuad = detected,
          isProcessing = false,
        )
      }
    }
  }

  fun updateCropCorner(cornerIndex: Int, point: NormalizedPoint) {
    _uiState.update { state ->
      state.copy(activeQuad = state.activeQuad.withUpdatedCorner(cornerIndex, point))
    }
  }

  fun reRunAutoEdgeDetection() {
    val raw = _uiState.value.activeRawBitmap ?: return
    viewModelScope.launch {
      _uiState.update { it.copy(isProcessing = true) }
      val detected = withContext(Dispatchers.Default) { DocumentScanProcessor.detectDocumentEdges(raw) }
      _uiState.update { it.copy(activeQuad = detected, isProcessing = false) }
    }
  }

  fun useFullFrameQuad() {
    _uiState.update {
      it.copy(
        activeQuad =
          DetectedDocumentQuad(
            topLeft = NormalizedPoint(0.02f, 0.02f),
            topRight = NormalizedPoint(0.98f, 0.02f),
            bottomRight = NormalizedPoint(0.98f, 0.98f),
            bottomLeft = NormalizedPoint(0.02f, 0.98f),
            confidence = 1f,
          )
      )
    }
  }

  fun applyCropAndEnhance() {
    val state = _uiState.value
    val raw = state.activeRawBitmap ?: return
    val quad = state.activeQuad
    val filter = state.selectedFilter

    viewModelScope.launch {
      _uiState.update { it.copy(isProcessing = true, errorMessage = null) }
      val (cropped, enhanced, estBytes) =
        withContext(Dispatchers.Default) {
          val c = DocumentScanProcessor.warpAndCrop(raw, quad)
          val e = DocumentScanProcessor.applyFilter(c, filter)
          val bytes = DocumentScanProcessor.estimateJpegSizeBytes(e)
          Triple(c, e, bytes)
        }
      _uiState.update {
        it.copy(
          stage = ScannerStage.FILTER_REVIEW,
          activeCroppedBitmap = cropped,
          activeEnhancedBitmap = enhanced,
          estimatedSizeBytes = estBytes,
          isProcessing = false,
        )
      }
      runOcrOnBitmap(enhanced)
    }
  }

  fun rerunMlKitOcr() {
    val target = _uiState.value.activeEnhancedBitmap ?: _uiState.value.activeCroppedBitmap ?: return
    viewModelScope.launch {
      runOcrOnBitmap(target)
    }
  }

  private suspend fun runOcrOnBitmap(bitmap: Bitmap) {
    _uiState.update { it.copy(isRunningOcr = true) }
    val extraction =
      try {
        withContext(Dispatchers.Default) {
          documentTextAnalyzer.analyzeBitmap(bitmap)
        }
      } catch (_: Exception) {
        null
      }

    _uiState.update { state ->
      if (extraction == null) {
        state.copy(isRunningOcr = false)
      } else {
        val nextCategory =
          if (!state.hasUserEditedCategory && extraction.hasRecognizedText) {
            extraction.suggestedCategory
          } else {
            state.documentType
          }
        val nextTitle =
          if (!state.hasUserEditedTitle && extraction.hasRecognizedText) {
            extraction.suggestedTitle
          } else {
            state.documentTitle
          }
        state.copy(
          isRunningOcr = false,
          ocrExtraction = extraction,
          documentType = nextCategory,
          documentTitle = nextTitle,
        )
      }
    }
  }

  fun applySuggestedOcrTitle() {
    val suggested = _uiState.value.ocrExtraction?.suggestedTitle ?: return
    _uiState.update { it.copy(documentTitle = suggested, hasUserEditedTitle = false) }
  }

  fun selectFilter(mode: DocumentFilterMode) {
    val cropped = _uiState.value.activeCroppedBitmap ?: return
    viewModelScope.launch {
      _uiState.update { it.copy(selectedFilter = mode, isProcessing = true) }
      val (enhanced, estBytes) =
        withContext(Dispatchers.Default) {
          val e = DocumentScanProcessor.applyFilter(cropped, mode)
          val bytes = DocumentScanProcessor.estimateJpegSizeBytes(e)
          e to bytes
        }
      _uiState.update {
        it.copy(
          activeEnhancedBitmap = enhanced,
          estimatedSizeBytes = estBytes,
          isProcessing = false,
        )
      }
      runOcrOnBitmap(enhanced)
    }
  }

  fun rotateCurrentScan90() {
    val cropped = _uiState.value.activeCroppedBitmap ?: return
    val filter = _uiState.value.selectedFilter
    viewModelScope.launch {
      _uiState.update { it.copy(isProcessing = true) }
      val (rotatedCrop, enhanced, estBytes) =
        withContext(Dispatchers.Default) {
          val rc = DocumentScanProcessor.rotateBitmap90(cropped)
          val e = DocumentScanProcessor.applyFilter(rc, filter)
          val bytes = DocumentScanProcessor.estimateJpegSizeBytes(e)
          Triple(rc, e, bytes)
        }
      _uiState.update {
        it.copy(
          activeCroppedBitmap = rotatedCrop,
          activeEnhancedBitmap = enhanced,
          estimatedSizeBytes = estBytes,
          isProcessing = false,
        )
      }
      runOcrOnBitmap(enhanced)
    }
  }

  fun backToCropAdjust() {
    _uiState.update { it.copy(stage = ScannerStage.CROP_ADJUST) }
  }

  fun retakeCurrentPage() {
    _uiState.update {
      it.copy(
        stage = ScannerStage.CAMERA_CAPTURE,
        activeRawBitmap = null,
        activeCroppedBitmap = null,
        activeEnhancedBitmap = null,
        ocrExtraction = null,
        errorMessage = null,
      )
    }
  }

  fun addPageAndScanNext() {
    val current = _uiState.value.activeEnhancedBitmap ?: return
    _uiState.update { state ->
      state.copy(
        completedPages = state.completedPages + current,
        activeRawBitmap = null,
        activeCroppedBitmap = null,
        activeEnhancedBitmap = null,
        exportFormat = ScanExportFormat.PDF,
        stage = ScannerStage.CAMERA_CAPTURE,
      )
    }
  }

  fun updateTitle(title: String) {
    _uiState.update { it.copy(documentTitle = title, hasUserEditedTitle = true) }
  }

  fun updateDocumentType(type: DocumentType) {
    _uiState.update { it.copy(documentType = type, hasUserEditedCategory = true) }
  }

  fun updateExportFormat(format: ScanExportFormat) {
    val state = _uiState.value
    if (state.completedPages.isNotEmpty() && format == ScanExportFormat.JPEG) {
      return // Multi-page documents must export as PDF
    }
    _uiState.update { it.copy(exportFormat = format) }
  }

  fun saveToDocumentVault() {
    val state = _uiState.value
    val allPages =
      buildList {
        addAll(state.completedPages)
        state.activeEnhancedBitmap?.let { add(it) }
      }
    if (allPages.isEmpty()) {
      _uiState.update { it.copy(errorMessage = "Capture at least one scanned page first.") }
      return
    }

    val title = state.documentTitle.trim().ifBlank { "Digitized HR Document" }
    val format = if (allPages.size > 1) ScanExportFormat.PDF else state.exportFormat
    val ocrMetadata = state.ocrExtraction?.toOcrMetadata()

    viewModelScope.launch {
      _uiState.update { it.copy(isSaving = true, errorMessage = null) }
      try {
        val exported =
          withContext(Dispatchers.IO) {
            DocumentScanProcessor.exportScanToVaultFile(
              context = context,
              pages = allPages,
              format = format,
              baseFileName = title,
            )
          }
        when (
          val res =
            documentRepository.uploadDocument(
              uri = exported.uri,
              title = title,
              documentType = state.documentType,
              ocrMetadata = ocrMetadata,
            )
        ) {
          is RepoResult.Success -> {
            val sizeLabel =
              if (res.data.fileSizeBytes >= 1024 * 1024) {
                String.format(Locale.US, "%.2f MB", res.data.fileSizeBytes / (1024.0 * 1024.0))
              } else {
                "${(res.data.fileSizeBytes / 1024).coerceAtLeast(1)} KB"
              }
            val ocrNote =
              if (ocrMetadata?.extractedText != null) " • OCR indexed (${state.documentType.label})"
              else ""
            _uiState.update {
              it.copy(
                isSaving = false,
                savedSuccessMessage =
                  "Saved ${allPages.size} page(s) as ${res.data.fileName} ($sizeLabel)$ocrNote.",
              )
            }
          }
          is RepoResult.Error -> {
            _uiState.update { it.copy(isSaving = false, errorMessage = res.message) }
          }
        }
      } catch (e: Exception) {
        _uiState.update {
          it.copy(isSaving = false, errorMessage = e.message ?: "Failed to export scanned document.")
        }
      }
    }
  }

  fun reportError(message: String) {
    _uiState.update { it.copy(errorMessage = message) }
  }

  fun clearError() {
    _uiState.update { it.copy(errorMessage = null) }
  }
}
