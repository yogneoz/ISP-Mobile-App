package com.example.ui.screens.scanner

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.FilterBAndW
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.TextSnippet
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.model.DocumentType
import com.example.scanner.DetectedDocumentQuad
import com.example.scanner.DocumentFilterMode
import com.example.scanner.DocumentScanProcessor
import com.example.scanner.NormalizedPoint
import com.example.scanner.ScanExportFormat
import com.example.ui.components.StatusPill
import com.example.ui.theme.MonoTelemetryStyle
import com.example.ui.theme.PulseTheme
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min

@Composable
fun DocumentScannerScreen(
  onBack: () -> Unit,
  onDocumentSaved: (String) -> Unit,
  viewModel: DocumentScannerViewModel = hiltViewModel(),
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val context = LocalContext.current

  LaunchedEffect(state.savedSuccessMessage) {
    val msg = state.savedSuccessMessage
    if (!msg.isNullOrBlank()) {
      onDocumentSaved(msg)
    }
  }

  BackHandler {
    when (state.stage) {
      ScannerStage.FILTER_REVIEW -> viewModel.backToCropAdjust()
      ScannerStage.CROP_ADJUST -> viewModel.retakeCurrentPage()
      ScannerStage.CAMERA_CAPTURE -> onBack()
    }
  }

  val photoImportLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
      uri?.let { viewModel.onImportedPhotoUri(it) }
    }

  var hasCameraPermission by remember {
    mutableStateOf(
      ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED
    )
  }

  val cameraPermissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
      hasCameraPermission = granted
    }

  when (state.stage) {
    ScannerStage.CAMERA_CAPTURE -> {
      CameraCaptureStage(
        state = state,
        hasCameraPermission = hasCameraPermission,
        onRequestCameraPermission = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) },
        onImportPhoto = {
          photoImportLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
          )
        },
        onToggleFlash = viewModel::toggleFlash,
        onToggleAutoCapture = viewModel::toggleAutoCapture,
        onLiveQuadUpdated = viewModel::updateLiveQuad,
        onAnalyzeLiveFrameForOcr = viewModel::analyzeLiveFrameForOcr,
        onBitmapCaptured = viewModel::onCapturedBitmap,
        onError = viewModel::reportError,
        onClose = onBack,
        onClearError = viewModel::clearError,
      )
    }

    ScannerStage.CROP_ADJUST -> {
      CropAdjustStage(
        state = state,
        onCornerDrag = viewModel::updateCropCorner,
        onAutoDetect = viewModel::reRunAutoEdgeDetection,
        onFullFrame = viewModel::useFullFrameQuad,
        onRetake = viewModel::retakeCurrentPage,
        onApplyCrop = viewModel::applyCropAndEnhance,
      )
    }

    ScannerStage.FILTER_REVIEW -> {
      FilterReviewStage(
        state = state,
        onSelectFilter = viewModel::selectFilter,
        onRotate = viewModel::rotateCurrentScan90,
        onRecrop = viewModel::backToCropAdjust,
        onAddPage = viewModel::addPageAndScanNext,
        onRerunOcr = viewModel::rerunMlKitOcr,
        onApplySuggestedTitle = viewModel::applySuggestedOcrTitle,
        onTitleChange = viewModel::updateTitle,
        onTypeChange = viewModel::updateDocumentType,
        onFormatChange = viewModel::updateExportFormat,
        onSave = viewModel::saveToDocumentVault,
        onClearError = viewModel::clearError,
      )
    }
  }
}

@Composable
private fun CameraCaptureStage(
  state: DocumentScannerUiState,
  hasCameraPermission: Boolean,
  onRequestCameraPermission: () -> Unit,
  onImportPhoto: () -> Unit,
  onToggleFlash: () -> Unit,
  onToggleAutoCapture: () -> Unit,
  onLiveQuadUpdated: (DetectedDocumentQuad) -> Unit,
  onAnalyzeLiveFrameForOcr: (Bitmap, () -> Unit) -> Unit,
  onBitmapCaptured: (Bitmap) -> Unit,
  onError: (String) -> Unit,
  onClose: () -> Unit,
  onClearError: () -> Unit,
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }
  val analysisExecutor = remember { Executors.newSingleThreadExecutor() }

  var cameraRef by remember { mutableStateOf<Camera?>(null) }
  var imageCaptureRef by remember { mutableStateOf<ImageCapture?>(null) }
  var cameraUnavailableMessage by remember { mutableStateOf<String?>(null) }
  var isTakingPhoto by remember { mutableStateOf(false) }
  val latestAutoCapture by rememberUpdatedState(state.autoCaptureEnabled)

  DisposableEffect(Unit) {
    onDispose {
      runCatching {
        ProcessCameraProvider.getInstance(context).get().unbindAll()
      }
      analysisExecutor.shutdown()
    }
  }

  LaunchedEffect(state.flashEnabled, cameraRef) {
    cameraRef?.cameraControl?.enableTorch(state.flashEnabled)
  }

  val isLocked = state.liveQuad.isLocked
  val quadStrokeColor by
    animateColorAsState(
      targetValue = if (isLocked) Color(0xFF22C55E) else Color(0xFFFBBF24),
      label = "quadStroke",
    )

  Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF0B1120)) {
    Column(modifier = Modifier.fillMaxSize()) {
      // Top studio toolbar
      Row(
        modifier =
          Modifier.fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        IconButton(
          onClick = onClose,
          modifier = Modifier.minimumInteractiveComponentSize().testTag("scanner_close_button"),
        ) {
          Icon(
            Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Back to Document Vault",
            tint = Color.White,
          )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          Text(
            text = "HR Document Scanner",
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
          )
          if (state.completedPages.isNotEmpty()) {
            Text(
              text = "Scanning Page ${state.completedPages.size + 1}",
              style = MaterialTheme.typography.labelSmall,
              color = Color(0xFF93C5FD),
            )
          }
        }

        Row {
          IconButton(
            onClick = onToggleAutoCapture,
            modifier = Modifier.minimumInteractiveComponentSize().testTag("scanner_autocapture_toggle"),
          ) {
            Icon(
              Icons.Filled.CenterFocusStrong,
              contentDescription = "Toggle auto edge snap",
              tint = if (state.autoCaptureEnabled) Color(0xFF22C55E) else Color.White.copy(alpha = 0.7f),
            )
          }
          IconButton(
            onClick = onToggleFlash,
            modifier = Modifier.minimumInteractiveComponentSize().testTag("scanner_flash_toggle"),
          ) {
            Icon(
              if (state.flashEnabled) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
              contentDescription = "Toggle flash torch",
              tint = if (state.flashEnabled) Color(0xFFFBBF24) else Color.White,
            )
          }
        }
      }

      if (!state.errorMessage.isNullOrBlank()) {
        Surface(
          color = MaterialTheme.colorScheme.errorContainer,
          shape = MaterialTheme.shapes.medium,
          modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Text(
              text = state.errorMessage,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onErrorContainer,
              modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onClearError) { Text("OK") }
          }
        }
      }

      // Viewfinder Area
      Box(
        modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
      ) {
        if (!hasCameraPermission) {
          Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
            modifier = Modifier.widthIn(max = 440.dp).fillMaxWidth(),
          ) {
            Column(
              modifier = Modifier.padding(24.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              Icon(
                Icons.Filled.DocumentScanner,
                contentDescription = null,
                tint = Color(0xFF60A5FA),
                modifier = Modifier.size(48.dp),
              )
              Text(
                text = "Camera Access for Document Digitization",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                textAlign = TextAlign.Center,
              )
              Text(
                text =
                  "Allow camera permission to detect receipt & HR form boundaries automatically and apply shadow-removal enhancement filters.",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFCBD5E1),
                textAlign = TextAlign.Center,
              )
              Button(
                onClick = onRequestCameraPermission,
                modifier = Modifier.fillMaxWidth().testTag("grant_camera_permission_button"),
              ) {
                Icon(Icons.Filled.CameraAlt, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Enable Camera Scanner")
              }
              OutlinedButton(
                onClick = onImportPhoto,
                modifier = Modifier.fillMaxWidth().testTag("import_doc_photo_fallback_button"),
              ) {
                Icon(Icons.Filled.PhotoLibrary, contentDescription = null, tint = Color.White)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Import Photo Instead", color = Color.White)
              }
            }
          }
        } else if (cameraUnavailableMessage != null) {
          Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            modifier = Modifier.widthIn(max = 440.dp).fillMaxWidth(),
          ) {
            Column(
              modifier = Modifier.padding(24.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              Icon(
                Icons.Filled.PhotoLibrary,
                contentDescription = null,
                tint = Color(0xFFFBBF24),
                modifier = Modifier.size(44.dp),
              )
              Text(
                text = "Camera Preview Unavailable",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
              )
              Text(
                text = cameraUnavailableMessage ?: "",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFCBD5E1),
                textAlign = TextAlign.Center,
              )
              Button(
                onClick = onImportPhoto,
                modifier = Modifier.fillMaxWidth().testTag("import_doc_photo_unavailable_button"),
              ) {
                Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Select Receipt / Form Photo")
              }
            }
          }
        } else {
          // Live 3:4 Portrait Viewfinder with real-time edge quad overlay
          Box(
            modifier =
              Modifier.fillMaxWidth()
                .aspectRatio(3f / 4f)
                .clip(RoundedCornerShape(18.dp))
                .background(Color.Black)
          ) {
            val previewView =
              remember(context) {
                PreviewView(context).apply {
                  scaleType = PreviewView.ScaleType.FIT_CENTER
                  implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                }
              }

            LaunchedEffect(lifecycleOwner, previewView) {
              val future = ProcessCameraProvider.getInstance(context)
              future.addListener(
                {
                  try {
                    val provider = future.get()
                    val selector =
                      when {
                        provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) ->
                          CameraSelector.DEFAULT_BACK_CAMERA
                        provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) ->
                          CameraSelector.DEFAULT_FRONT_CAMERA
                        else -> null
                      }
                    if (selector == null) {
                      cameraUnavailableMessage =
                        "No physical camera detected on this emulator/device. Import a receipt or form photo below to run edge detection and filters."
                      return@addListener
                    }

                    val preview =
                      Preview.Builder()
                        .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                        .build()
                        .also { it.surfaceProvider = previewView.surfaceProvider }

                    val imageCapture =
                      ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                        .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                        .build()
                    imageCaptureRef = imageCapture

                    var lastAnalyzedMs = 0L
                    var lastOcrMs = 0L
                    var isLiveOcrBusy = false
                    var stableFrames = 0
                    var prevQuad: DetectedDocumentQuad? = null

                    val imageAnalysis =
                      ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                        .build()
                        .also { analysis ->
                          analysis.setAnalyzer(analysisExecutor) { imageProxy ->
                            try {
                              val now = System.currentTimeMillis()
                              if (now - lastAnalyzedMs >= 180L) {
                                lastAnalyzedMs = now
                                val frameBmp = imageProxyToUprightBitmap(imageProxy)
                                val quad = DocumentScanProcessor.detectDocumentEdges(frameBmp)

                                if (now - lastOcrMs >= 1100L && !isLiveOcrBusy) {
                                  lastOcrMs = now
                                  isLiveOcrBusy = true
                                  onAnalyzeLiveFrameForOcr(frameBmp) { isLiveOcrBusy = false }
                                } else {
                                  frameBmp.recycle()
                                }

                                if (latestAutoCapture && quad.confidence >= 0.62f && !isTakingPhoto) {
                                  val p = prevQuad
                                  val moved =
                                    if (p == null) 1f
                                    else
                                      abs(p.topLeft.x - quad.topLeft.x) +
                                        abs(p.topLeft.y - quad.topLeft.y) +
                                        abs(p.bottomRight.x - quad.bottomRight.x) +
                                        abs(p.bottomRight.y - quad.bottomRight.y)
                                  stableFrames = if (moved < 0.08f) stableFrames + 1 else 0
                                  prevQuad = quad
                                  if (stableFrames >= 4) {
                                    stableFrames = 0
                                    mainExecutor.execute {
                                      if (!isTakingPhoto) {
                                        isTakingPhoto = true
                                        captureSingleFrame(
                                          imageCapture = imageCapture,
                                          executor = mainExecutor,
                                          onSuccess = { bmp ->
                                            isTakingPhoto = false
                                            onBitmapCaptured(bmp)
                                          },
                                          onFailure = { msg ->
                                            isTakingPhoto = false
                                            onError(msg)
                                          },
                                        )
                                      }
                                    }
                                  }
                                } else {
                                  stableFrames = 0
                                }

                                mainExecutor.execute { onLiveQuadUpdated(quad) }
                              }
                            } finally {
                              imageProxy.close()
                            }
                          }
                        }

                    provider.unbindAll()
                    cameraRef =
                      provider.bindToLifecycle(
                        lifecycleOwner,
                        selector,
                        preview,
                        imageCapture,
                        imageAnalysis,
                      )
                  } catch (e: Exception) {
                    cameraUnavailableMessage =
                      e.message ?: "Unable to initialize CameraX preview. Use photo import below."
                  }
                },
                mainExecutor,
              )
            }

            AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

            // Real-time Edge Detection Quad Overlay
            Canvas(modifier = Modifier.fillMaxSize()) {
              val q = state.liveQuad
              val tl = Offset(q.topLeft.x * size.width, q.topLeft.y * size.height)
              val tr = Offset(q.topRight.x * size.width, q.topRight.y * size.height)
              val br = Offset(q.bottomRight.x * size.width, q.bottomRight.y * size.height)
              val bl = Offset(q.bottomLeft.x * size.width, q.bottomLeft.y * size.height)

              val path =
                Path().apply {
                  moveTo(tl.x, tl.y)
                  lineTo(tr.x, tr.y)
                  lineTo(br.x, br.y)
                  lineTo(bl.x, bl.y)
                  close()
                }
              drawPath(path = path, color = quadStrokeColor.copy(alpha = 0.14f))
              drawPath(
                path = path,
                color = quadStrokeColor,
                style =
                  Stroke(
                    width = if (isLocked) 5f else 3f,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                    pathEffect =
                      if (isLocked) null else PathEffect.dashPathEffect(floatArrayOf(16f, 12f), 0f),
                  ),
              )
              listOf(tl, tr, br, bl).forEach { corner ->
                drawCircle(color = Color.White, radius = 10f, center = corner)
                drawCircle(color = quadStrokeColor, radius = 6f, center = corner)
              }
            }

            // Edge Status Pill floating at top inside viewfinder
            Surface(
              shape = RoundedCornerShape(999.dp),
              color = Color.Black.copy(alpha = 0.65f),
              modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp),
            ) {
              Text(
                text =
                  when {
                    isLocked && state.autoCaptureEnabled ->
                      "Edges Locked (${(state.liveQuad.confidence * 100).toInt()}%) • Hold steady"
                    isLocked ->
                      "Edges Locked (${(state.liveQuad.confidence * 100).toInt()}%) • Tap shutter"
                    else -> "Align receipt, certificate, or HR form inside frame"
                  },
                style = MaterialTheme.typography.labelMedium,
                color = if (isLocked) Color(0xFF4ADE80) else Color.White,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
              )
            }

            if (state.liveOcrHint.blockCount > 0) {
              Surface(
                shape = RoundedCornerShape(999.dp),
                color = Color(0xFF0F172A).copy(alpha = 0.85f),
                border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.65f)),
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                  Icon(
                    imageVector = Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    tint = Color(0xFF38BDF8),
                    modifier = Modifier.size(14.dp),
                  )
                  Text(
                    text =
                      "ML Kit OCR Ready • ${state.liveOcrHint.blockCount} block(s)" +
                        (state.liveOcrHint.categoryHint?.let { " • ${it.label}" } ?: ""),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                  )
                }
              }
            }

            if (state.isProcessing || isTakingPhoto) {
              Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center,
              ) {
                CircularProgressIndicator(color = Color.White)
              }
            }
          }
        }
      }

      // Bottom Capture Controls (WindowInsets safe)
      Row(
        modifier =
          Modifier.fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          modifier = Modifier.semantics(mergeDescendants = true) {},
        ) {
          IconButton(
            onClick = onImportPhoto,
            modifier =
              Modifier.size(52.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.14f))
                .testTag("scanner_import_gallery_button"),
          ) {
            Icon(Icons.Filled.PhotoLibrary, contentDescription = "Import photo", tint = Color.White)
          }
          Spacer(modifier = Modifier.height(4.dp))
          Text("Import", style = MaterialTheme.typography.labelSmall, color = Color.White)
        }

        Surface(
          onClick = {
            val capture = imageCaptureRef
            if (capture != null && !isTakingPhoto) {
              isTakingPhoto = true
              captureSingleFrame(
                imageCapture = capture,
                executor = mainExecutor,
                onSuccess = { bmp ->
                  isTakingPhoto = false
                  onBitmapCaptured(bmp)
                },
                onFailure = { err ->
                  isTakingPhoto = false
                  onError(err)
                },
              )
            }
          },
          enabled = hasCameraPermission && cameraUnavailableMessage == null && !isTakingPhoto,
          shape = CircleShape,
          color = Color.White,
          border = BorderStroke(4.dp, Color(0xFF38BDF8)),
          modifier = Modifier.size(72.dp).testTag("scanner_shutter_button"),
        ) {
          Box(contentAlignment = Alignment.Center) {
            Icon(
              Icons.Filled.CameraAlt,
              contentDescription = "Capture document scan",
              tint = Color(0xFF0F172A),
              modifier = Modifier.size(30.dp),
            )
          }
        }

        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          modifier = Modifier.semantics(mergeDescendants = true) {},
        ) {
          Surface(
            shape = CircleShape,
            color = Color.White.copy(alpha = 0.14f),
            modifier = Modifier.size(52.dp),
          ) {
            Box(contentAlignment = Alignment.Center) {
              Text(
                text = "${state.completedPages.size}",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold,
              )
            }
          }
          Spacer(modifier = Modifier.height(4.dp))
          Text("Pages", style = MaterialTheme.typography.labelSmall, color = Color.White)
        }
      }
    }
  }
}

@Composable
private fun CropAdjustStage(
  state: DocumentScannerUiState,
  onCornerDrag: (Int, NormalizedPoint) -> Unit,
  onAutoDetect: () -> Unit,
  onFullFrame: () -> Unit,
  onRetake: () -> Unit,
  onApplyCrop: () -> Unit,
) {
  val rawBitmap = state.activeRawBitmap ?: return
  val quad = state.activeQuad
  val density = LocalDensity.current
  val primaryColor = MaterialTheme.colorScheme.primary

  Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF0F172A)) {
    Column(modifier = Modifier.fillMaxSize()) {
      Row(
        modifier =
          Modifier.fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        IconButton(
          onClick = onRetake,
          modifier = Modifier.minimumInteractiveComponentSize().testTag("crop_retake_button"),
        ) {
          Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retake", tint = Color.White)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          Text("Adjust Document Corners", style = MaterialTheme.typography.titleMedium, color = Color.White)
          Text(
            text = "Drag handles to fine-tune perspective warp",
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFF94A3B8),
          )
        }
        IconButton(
          onClick = onAutoDetect,
          modifier = Modifier.minimumInteractiveComponentSize().testTag("crop_autodetect_button"),
        ) {
          Icon(Icons.Filled.CropFree, contentDescription = "Re-detect edges", tint = Color(0xFF38BDF8))
        }
      }

      BoxWithConstraints(
        modifier = Modifier.weight(1f).fillMaxWidth().padding(16.dp),
        contentAlignment = Alignment.Center,
      ) {
        val boxW = with(density) { maxWidth.toPx() }
        val boxH = with(density) { maxHeight.toPx() }
        val scale = min(boxW / rawBitmap.width.toFloat(), boxH / rawBitmap.height.toFloat())
        val imgW = rawBitmap.width * scale
        val imgH = rawBitmap.height * scale
        val offsetX = (boxW - imgW) / 2f
        val offsetY = (boxH - imgH) / 2f

        var draggingIndex by remember { mutableIntStateOf(-1) }

        Image(
          bitmap = rawBitmap.asImageBitmap(),
          contentDescription = "Captured document for edge cropping",
          contentScale = ContentScale.Fit,
          modifier = Modifier.fillMaxSize(),
        )

        Canvas(
          modifier =
            Modifier.fillMaxSize().pointerInput(quad, imgW, imgH, offsetX, offsetY) {
              detectDragGestures(
                onDragStart = { startOffset ->
                  val pts =
                    quad.asList().map { p ->
                      Offset(offsetX + p.x * imgW, offsetY + p.y * imgH)
                    }
                  val nearest =
                    pts.withIndex().minByOrNull { (_, c) ->
                      hypot(c.x - startOffset.x, c.y - startOffset.y)
                    }
                  draggingIndex =
                    if (nearest != null && hypot(nearest.value.x - startOffset.x, nearest.value.y - startOffset.y) < 110f) {
                      nearest.index
                    } else -1
                },
                onDragEnd = { draggingIndex = -1 },
                onDragCancel = { draggingIndex = -1 },
                onDrag = { change, dragAmount ->
                  if (draggingIndex in 0..3) {
                    change.consume()
                    val current = quad.asList()[draggingIndex]
                    val nextX = current.x + dragAmount.x / imgW
                    val nextY = current.y + dragAmount.y / imgH
                    onCornerDrag(draggingIndex, NormalizedPoint(nextX, nextY))
                  }
                },
              )
            }
        ) {
          val corners = quad.asList().map { p -> Offset(offsetX + p.x * imgW, offsetY + p.y * imgH) }
          val path =
            Path().apply {
              moveTo(corners[0].x, corners[0].y)
              lineTo(corners[1].x, corners[1].y)
              lineTo(corners[2].x, corners[2].y)
              lineTo(corners[3].x, corners[3].y)
              close()
            }
          drawPath(path = path, color = primaryColor.copy(alpha = 0.14f))
          drawPath(
            path = path,
            color = Color(0xFF38BDF8),
            style = Stroke(width = 5f, cap = StrokeCap.Round, join = StrokeJoin.Round),
          )
          corners.forEachIndexed { idx, c ->
            val active = idx == draggingIndex
            drawCircle(
              color = Color.White,
              radius = if (active) 24f else 18f,
              center = c,
            )
            drawCircle(
              color = Color(0xFF0284C7),
              radius = if (active) 16f else 11f,
              center = c,
            )
          }
        }

        if (state.isProcessing) {
          Box(
            modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center,
          ) {
            CircularProgressIndicator(color = Color.White)
          }
        }
      }

      Row(
        modifier =
          Modifier.fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        OutlinedButton(
          onClick = onFullFrame,
          modifier = Modifier.weight(1f).testTag("crop_full_frame_button"),
        ) {
          Icon(Icons.Filled.Crop, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text("Full Frame", color = Color.White)
        }
        Button(
          onClick = onApplyCrop,
          enabled = !state.isProcessing,
          modifier = Modifier.weight(1.4f).testTag("apply_crop_enhance_button"),
        ) {
          Icon(Icons.Filled.AutoFixHigh, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text("Deskew & Enhance")
        }
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterReviewStage(
  state: DocumentScannerUiState,
  onSelectFilter: (DocumentFilterMode) -> Unit,
  onRotate: () -> Unit,
  onRecrop: () -> Unit,
  onAddPage: () -> Unit,
  onRerunOcr: () -> Unit,
  onApplySuggestedTitle: () -> Unit,
  onTitleChange: (String) -> Unit,
  onTypeChange: (DocumentType) -> Unit,
  onFormatChange: (ScanExportFormat) -> Unit,
  onSave: () -> Unit,
  onClearError: () -> Unit,
) {
  val enhancedBitmap = state.activeEnhancedBitmap ?: return
  val semantic = PulseTheme.semantic
  val clipboardManager = LocalClipboardManager.current
  val pageCount = state.completedPages.size + 1

  Scaffold(
    contentWindowInsets = WindowInsets.safeDrawing,
    topBar = {
      Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
        Row(
          modifier =
            Modifier.fillMaxWidth()
              .statusBarsPadding()
              .padding(horizontal = 12.dp, vertical = 10.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          IconButton(
            onClick = onRecrop,
            modifier = Modifier.minimumInteractiveComponentSize().testTag("review_back_to_crop"),
          ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to crop edges")
          }
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Enhance & Digitization", style = MaterialTheme.typography.titleMedium)
            Text(
              text = "$pageCount page(s) • ${state.selectedFilter.label}",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          IconButton(
            onClick = onRotate,
            modifier = Modifier.minimumInteractiveComponentSize().testTag("review_rotate_button"),
          ) {
            Icon(Icons.Filled.RotateRight, contentDescription = "Rotate 90 degrees")
          }
        }
      }
    },
  ) { innerPadding ->
    Box(
      modifier = Modifier.fillMaxSize().padding(innerPadding),
      contentAlignment = Alignment.TopCenter,
    ) {
      Column(
        modifier =
          Modifier.widthIn(max = 840.dp)
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
      ) {
        if (!state.errorMessage.isNullOrBlank()) {
          Surface(
            color = MaterialTheme.colorScheme.errorContainer,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                text = state.errorMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f),
              )
              TextButton(onClick = onClearError) { Text("OK") }
            }
          }
        }

        // Document Enhancement Preview Stage
        Card(
          modifier = Modifier.fillMaxWidth(),
          shape = MaterialTheme.shapes.large,
          colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
          Box(
            modifier = Modifier.fillMaxWidth().height(320.dp).padding(12.dp),
            contentAlignment = Alignment.Center,
          ) {
            Image(
              bitmap = enhancedBitmap.asImageBitmap(),
              contentDescription = "Enhanced digitized document preview",
              contentScale = ContentScale.Fit,
              modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)),
            )
            if (state.isProcessing) {
              Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center,
              ) {
                CircularProgressIndicator(color = Color.White)
              }
            }
          }
        }

        // Image Enhancement Filter Selector
        Text("Enhancement Filter", style = MaterialTheme.typography.titleSmall)
        LazyRow(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          contentPadding = PaddingValues(vertical = 2.dp),
        ) {
          items(DocumentFilterMode.entries) { mode ->
            val icon =
              when (mode) {
                DocumentFilterMode.MAGIC_SCAN -> Icons.Filled.AutoFixHigh
                DocumentFilterMode.GRAYSCALE -> Icons.Filled.Contrast
                DocumentFilterMode.HIGH_CONTRAST_BW -> Icons.Filled.FilterBAndW
                DocumentFilterMode.ORIGINAL -> Icons.Filled.Image
              }
            FilterChip(
              selected = state.selectedFilter == mode,
              onClick = { onSelectFilter(mode) },
              leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp)) },
              label = { Text(mode.label) },
              modifier = Modifier.testTag("filter_chip_${mode.name}"),
            )
          }
        }
        Text(
          text = state.selectedFilter.description,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // Multi-page & Crop Actions
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          OutlinedButton(
            onClick = onRecrop,
            modifier = Modifier.weight(1f).testTag("review_recrop_button"),
          ) {
            Icon(Icons.Filled.Crop, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Adjust Edges")
          }
          OutlinedButton(
            onClick = onAddPage,
            modifier = Modifier.weight(1f).testTag("review_add_page_button"),
          ) {
            Icon(Icons.Filled.NoteAdd, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("+ Add Page ($pageCount)")
          }
        }

        // ML Kit Text Recognition & Auto-Categorization Card
        Card(
          modifier = Modifier.fillMaxWidth().testTag("mlkit_ocr_card"),
          shape = MaterialTheme.shapes.large,
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
          Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
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
                  imageVector = Icons.Filled.AutoAwesome,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.primary,
                )
                Column {
                  Text("ML Kit OCR & Auto-Tagging", style = MaterialTheme.typography.titleMedium)
                  Text(
                    text = "On-device text extraction for receipts, certificates & forms",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              }
              IconButton(
                onClick = onRerunOcr,
                enabled = !state.isRunningOcr && !state.isProcessing,
                modifier = Modifier.minimumInteractiveComponentSize().testTag("rerun_mlkit_ocr_button"),
              ) {
                Icon(
                  imageVector = Icons.Filled.Refresh,
                  contentDescription = "Re-run ML Kit text recognition",
                )
              }
            }

            if (state.isRunningOcr) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
              ) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Text(
                  text = "Running Google ML Kit text recognition...",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            } else {
              val extraction = state.ocrExtraction
              if (extraction != null && extraction.hasRecognizedText) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  StatusPill(
                    text =
                      "Auto-Categorized: ${extraction.suggestedCategory.label} (${extraction.confidencePercent()}%)",
                    containerColor = semantic.verifiedContainer,
                    contentColor = semantic.onVerifiedContainer,
                    icon = Icons.Filled.TextSnippet,
                  )
                }
                Text(
                  text = extraction.categorizationReason,
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                FlowRow(
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                  verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                  extraction.keyValueFields.forEach { (k, v) ->
                    Surface(
                      shape = MaterialTheme.shapes.small,
                      color = MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                      Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                        Text(
                          text = k,
                          style = MaterialTheme.typography.labelSmall,
                          color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                          text = v,
                          style = MonoTelemetryStyle,
                          fontWeight = FontWeight.SemiBold,
                        )
                      }
                    }
                  }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                Surface(
                  shape = MaterialTheme.shapes.medium,
                  color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                  modifier = Modifier.fillMaxWidth().testTag("ocr_extracted_text_preview"),
                ) {
                  Text(
                    text = extraction.rawText.take(420),
                    style = MonoTelemetryStyle,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(10.dp),
                  )
                }

                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                  OutlinedButton(
                    onClick = {
                      clipboardManager.setText(AnnotatedString(extraction.rawText))
                    },
                    modifier = Modifier.weight(1f).testTag("copy_ocr_text_button"),
                  ) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Copy OCR Text")
                  }
                  if (state.hasUserEditedTitle) {
                    OutlinedButton(
                      onClick = onApplySuggestedTitle,
                      modifier = Modifier.weight(1f).testTag("apply_ocr_title_button"),
                    ) {
                      Text("Use OCR Title")
                    }
                  }
                }
              } else {
                Text(
                  text =
                    extraction?.categorizationReason
                      ?: "Tap refresh after cropping or switching to B&W Text filter to extract text.",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          }
        }

        // Vault Metadata & Export Card
        Card(
          modifier = Modifier.fillMaxWidth(),
          shape = MaterialTheme.shapes.large,
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
          Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text("Save to Compliance Vault", style = MaterialTheme.typography.titleMedium)
              val estKb = (state.estimatedSizeBytes / 1024L).coerceAtLeast(1L)
              val sizeText =
                if (estKb >= 1024L) String.format(Locale.US, "~%.2f MB", estKb / 1024.0)
                else "~$estKb KB"
              StatusPill(
                text = "$sizeText / 5 MB",
                containerColor = semantic.verifiedContainer,
                contentColor = semantic.onVerifiedContainer,
              )
            }

            OutlinedTextField(
              value = state.documentTitle,
              onValueChange = onTitleChange,
              label = { Text("Document / Receipt Title") },
              singleLine = true,
              modifier = Modifier.fillMaxWidth().testTag("scan_title_input"),
            )

            Text("Document Category", style = MaterialTheme.typography.labelMedium)
            FlowRow(
              horizontalArrangement = Arrangement.spacedBy(6.dp),
              verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              DocumentType.entries
                .filter { it != DocumentType.PROFILE_PHOTO }
                .forEach { docType ->
                  FilterChip(
                    selected = state.documentType == docType,
                    onClick = { onTypeChange(docType) },
                    label = { Text(docType.label) },
                  )
                }
            }

            Text("Output Format", style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              ScanExportFormat.entries.forEach { fmt ->
                val enabled = !(pageCount > 1 && fmt == ScanExportFormat.JPEG)
                FilterChip(
                  selected = state.exportFormat == fmt,
                  enabled = enabled,
                  onClick = { onFormatChange(fmt) },
                  leadingIcon = {
                    Icon(
                      if (fmt == ScanExportFormat.PDF) Icons.Filled.PictureAsPdf else Icons.Filled.Image,
                      contentDescription = null,
                      modifier = Modifier.size(16.dp),
                    )
                  },
                  label = { Text(fmt.label) },
                  modifier = Modifier.testTag("export_format_${fmt.name}"),
                )
              }
            }

            Text(
              text =
                "Perspective-warped, enhanced, and stored in hr_employee_documents with ≤ 5.00 MB validation.",
              style = MonoTelemetryStyle,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Button(
              onClick = onSave,
              enabled = !state.isSaving && !state.isProcessing,
              modifier = Modifier.fillMaxWidth().height(50.dp).testTag("save_scanned_doc_button"),
            ) {
              if (state.isSaving) {
                CircularProgressIndicator(
                  modifier = Modifier.size(20.dp),
                  strokeWidth = 2.dp,
                  color = MaterialTheme.colorScheme.onPrimary,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Digitizing & Saving...")
              } else {
                Icon(Icons.Filled.Check, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Save Digitized Document to Vault")
              }
            }
          }
        }
      }
    }
  }
}

private fun imageProxyToUprightBitmap(image: ImageProxy): Bitmap {
  val raw = image.toBitmap()
  val rotation = image.imageInfo.rotationDegrees
  if (rotation == 0) return raw
  val m = Matrix().apply { postRotate(rotation.toFloat()) }
  val rotated = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true)
  if (rotated !== raw) raw.recycle()
  return rotated
}

private fun captureSingleFrame(
  imageCapture: ImageCapture,
  executor: java.util.concurrent.Executor,
  onSuccess: (Bitmap) -> Unit,
  onFailure: (String) -> Unit,
) {
  imageCapture.takePicture(
    executor,
    object : ImageCapture.OnImageCapturedCallback() {
      override fun onCaptureSuccess(image: ImageProxy) {
        try {
          val bmp = imageProxyToUprightBitmap(image)
          onSuccess(bmp)
        } catch (e: Exception) {
          onFailure(e.message ?: "Capture decode failed.")
        } finally {
          image.close()
        }
      }

      override fun onError(exception: ImageCaptureException) {
        onFailure(exception.message ?: "Camera capture failed.")
      }
    },
  )
}
