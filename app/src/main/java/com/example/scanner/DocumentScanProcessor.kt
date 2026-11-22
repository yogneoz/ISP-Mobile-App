package com.example.scanner

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import android.media.ExifInterface
import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.core.content.FileProvider
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@Immutable
data class NormalizedPoint(val x: Float, val y: Float)

@Immutable
data class DetectedDocumentQuad(
  val topLeft: NormalizedPoint,
  val topRight: NormalizedPoint,
  val bottomRight: NormalizedPoint,
  val bottomLeft: NormalizedPoint,
  val confidence: Float,
) {
  val isLocked: Boolean
    get() = confidence >= 0.45f

  fun asList(): List<NormalizedPoint> = listOf(topLeft, topRight, bottomRight, bottomLeft)

  fun withUpdatedCorner(index: Int, point: NormalizedPoint): DetectedDocumentQuad {
    val clamped = NormalizedPoint(point.x.coerceIn(0.02f, 0.98f), point.y.coerceIn(0.02f, 0.98f))
    return when (index) {
      0 -> copy(topLeft = clamped)
      1 -> copy(topRight = clamped)
      2 -> copy(bottomRight = clamped)
      else -> copy(bottomLeft = clamped)
    }
  }
}

enum class DocumentFilterMode(val label: String, val description: String) {
  MAGIC_SCAN("Magic Scan", "Shadow removal & crisp white paper"),
  GRAYSCALE("Grayscale", "Balanced monochrome HR form"),
  HIGH_CONTRAST_BW("B&W Text", "High-contrast sharp receipt text"),
  ORIGINAL("Original", "Perspective-corrected color capture"),
}

enum class ScanExportFormat(val label: String, val extension: String, val mimeType: String) {
  PDF("PDF Document", "pdf", "application/pdf"),
  JPEG("Enhanced JPEG", "jpg", "image/jpeg"),
}

data class ExportedScanResult(
  val uri: Uri,
  val fileName: String,
  val mimeType: String,
  val fileSizeBytes: Long,
)

object DocumentScanProcessor {

  private const val MAX_OUTPUT_DIMENSION = 1800
  private const val MAX_VAULT_BYTES = 5L * 1024L * 1024L

  fun defaultQuad(): DetectedDocumentQuad =
    DetectedDocumentQuad(
      topLeft = NormalizedPoint(0.10f, 0.14f),
      topRight = NormalizedPoint(0.90f, 0.14f),
      bottomRight = NormalizedPoint(0.90f, 0.86f),
      bottomLeft = NormalizedPoint(0.10f, 0.86f),
      confidence = 0.15f,
    )

  /**
   * Automatically detects rectangular or trapezoidal document boundaries on a dark/contrasting
   * surface using smoothed Sobel gradient extraction, connected-neighbor filtering, and trimmed
   * diagonal extreme projection.
   */
  fun detectDocumentEdges(bitmap: Bitmap): DetectedDocumentQuad {
    val srcW = bitmap.width
    val srcH = bitmap.height
    if (srcW < 32 || srcH < 32) return defaultQuad()

    val targetMax = 180
    val scale = min(1f, targetMax.toFloat() / max(srcW, srcH).toFloat())
    val w = (srcW * scale).roundToInt().coerceAtLeast(32)
    val h = (srcH * scale).roundToInt().coerceAtLeast(32)
    val small =
      if (w == srcW && h == srcH) bitmap
      else Bitmap.createScaledBitmap(bitmap, w, h, true)

    val pixels = IntArray(w * h)
    small.getPixels(pixels, 0, w, 0, 0, w, h)
    if (small !== bitmap) small.recycle()

    val lum = IntArray(w * h)
    for (i in pixels.indices) {
      val p = pixels[i]
      val r = (p ushr 16) and 0xFF
      val g = (p ushr 8) and 0xFF
      val b = p and 0xFF
      lum[i] = (r * 77 + g * 150 + b * 29) ushr 8
    }

    // 3x3 box blur suppresses text strokes & surface grain while retaining sheet edges
    val smooth = IntArray(w * h)
    for (y in 1 until h - 1) {
      val row = y * w
      for (x in 1 until w - 1) {
        val sum =
          lum[row - w + x - 1] + lum[row - w + x] + lum[row - w + x + 1] +
            lum[row + x - 1] + lum[row + x] + lum[row + x + 1] +
            lum[row + w + x - 1] + lum[row + w + x] + lum[row + w + x + 1]
        smooth[row + x] = sum / 9
      }
    }

    val grad = IntArray(w * h)
    var maxGrad = 0
    val margin = 2
    for (y in margin until h - margin) {
      val row = y * w
      for (x in margin until w - margin) {
        val gx =
          -smooth[row - w + x - 1] + smooth[row - w + x + 1] -
            2 * smooth[row + x - 1] + 2 * smooth[row + x + 1] -
            smooth[row + w + x - 1] + smooth[row + w + x + 1]
        val gy =
          -smooth[row - w + x - 1] - 2 * smooth[row - w + x] - smooth[row - w + x + 1] +
            smooth[row + w + x - 1] + 2 * smooth[row + w + x] + smooth[row + w + x + 1]
        val mag = (abs(gx) + abs(gy)) / 4
        grad[row + x] = mag
        if (mag > maxGrad) maxGrad = mag
      }
    }

    if (maxGrad < 26) return defaultQuad()

    val threshold = max(24, (maxGrad * 0.25f).roundToInt())
    val strongMask = BooleanArray(w * h)
    for (y in margin until h - margin) {
      val row = y * w
      for (x in margin until w - margin) {
        if (grad[row + x] >= threshold) {
          strongMask[row + x] = true
        }
      }
    }

    // Filter out isolated noise speckles: retain edge pixels with at least 2 neighbors in 5x5
    val edgeXs = ArrayList<Int>(512)
    val edgeYs = ArrayList<Int>(512)
    for (y in margin + 1 until h - margin - 1) {
      for (x in margin + 1 until w - margin - 1) {
        if (!strongMask[y * w + x]) continue
        var neighbors = 0
        for (dy in -2..2) {
          val ny = y + dy
          if (ny < 0 || ny >= h) continue
          for (dx in -2..2) {
            if (dx == 0 && dy == 0) continue
            val nx = x + dx
            if (nx in 0 until w && strongMask[ny * w + nx]) {
              neighbors++
              if (neighbors >= 2) break
            }
          }
          if (neighbors >= 2) break
        }
        if (neighbors >= 2) {
          edgeXs.add(x)
          edgeYs.add(y)
        }
      }
    }

    if (edgeXs.size < 24) return defaultQuad()

    // Compute 4 corner projections with trimmed 2% outlier resistance
    val count = edgeXs.size
    val sumScores = IntArray(count)
    val diffScores = IntArray(count)
    for (i in 0 until count) {
      sumScores[i] = edgeXs[i] + edgeYs[i]
      diffScores[i] = edgeXs[i] - edgeYs[i]
    }

    val indicesBySum = (0 until count).sortedBy { sumScores[it] }
    val indicesByDiff = (0 until count).sortedBy { diffScores[it] }
    val trim = (count * 0.015f).roundToInt().coerceIn(0, max(0, count / 10))

    val tlIdx = indicesBySum[trim]
    val brIdx = indicesBySum[count - 1 - trim]
    val blIdx = indicesByDiff[trim]
    val trIdx = indicesByDiff[count - 1 - trim]

    val tl = NormalizedPoint(edgeXs[tlIdx].toFloat() / w, edgeYs[tlIdx].toFloat() / h)
    val tr = NormalizedPoint(edgeXs[trIdx].toFloat() / w, edgeYs[trIdx].toFloat() / h)
    val br = NormalizedPoint(edgeXs[brIdx].toFloat() / w, edgeYs[brIdx].toFloat() / h)
    val bl = NormalizedPoint(edgeXs[blIdx].toFloat() / w, edgeYs[blIdx].toFloat() / h)

    val quadArea = polygonArea(tl, tr, br, bl)
    if (quadArea < 0.12f || quadArea > 0.96f || !isConvexQuad(tl, tr, br, bl)) {
      return defaultQuad()
    }

    val topLen = hypot(tr.x - tl.x, tr.y - tl.y)
    val rightLen = hypot(br.x - tr.x, br.y - tr.y)
    val bottomLen = hypot(br.x - bl.x, br.y - bl.y)
    val leftLen = hypot(bl.x - tl.x, bl.y - tl.y)
    if (min(min(topLen, bottomLen), min(leftLen, rightLen)) < 0.20f) {
      return defaultQuad()
    }

    // Verify edge pixel support along the 4 polygon boundaries
    val edgeSupport =
      (sampleEdgeSupport(tl, tr, strongMask, w, h) +
        sampleEdgeSupport(tr, br, strongMask, w, h) +
        sampleEdgeSupport(br, bl, strongMask, w, h) +
        sampleEdgeSupport(bl, tl, strongMask, w, h)) / 4f

    if (edgeSupport < 0.25f) return defaultQuad()

    val confidence = (0.42f + edgeSupport * 0.56f).coerceIn(0.45f, 0.99f)
    return DetectedDocumentQuad(
      topLeft = clampNormalized(tl),
      topRight = clampNormalized(tr),
      bottomRight = clampNormalized(br),
      bottomLeft = clampNormalized(bl),
      confidence = confidence,
    )
  }

  private fun sampleEdgeSupport(
    a: NormalizedPoint,
    b: NormalizedPoint,
    strongMask: BooleanArray,
    w: Int,
    h: Int,
  ): Float {
    val samples = 24
    var hits = 0
    for (s in 0..samples) {
      val t = s.toFloat() / samples
      val px = ((a.x + (b.x - a.x) * t) * w).roundToInt().coerceIn(0, w - 1)
      val py = ((a.y + (b.y - a.y) * t) * h).roundToInt().coerceIn(0, h - 1)
      var found = false
      for (dy in -3..3) {
        val ny = (py + dy).coerceIn(0, h - 1)
        for (dx in -3..3) {
          val nx = (px + dx).coerceIn(0, w - 1)
          if (strongMask[ny * w + nx]) {
            found = true
            break
          }
        }
        if (found) break
      }
      if (found) hits++
    }
    return hits.toFloat() / (samples + 1)
  }

  private fun polygonArea(
    p0: NormalizedPoint,
    p1: NormalizedPoint,
    p2: NormalizedPoint,
    p3: NormalizedPoint,
  ): Float {
    val cross =
      (p0.x * p1.y - p1.x * p0.y) +
        (p1.x * p2.y - p2.x * p1.y) +
        (p2.x * p3.y - p3.x * p2.y) +
        (p3.x * p0.y - p0.x * p3.y)
    return abs(cross) * 0.5f
  }

  private fun isConvexQuad(
    p0: NormalizedPoint,
    p1: NormalizedPoint,
    p2: NormalizedPoint,
    p3: NormalizedPoint,
  ): Boolean {
    val pts = listOf(p0, p1, p2, p3)
    var sign = 0
    for (i in 0 until 4) {
      val a = pts[i]
      val b = pts[(i + 1) % 4]
      val c = pts[(i + 2) % 4]
      val cross = (b.x - a.x) * (c.y - b.y) - (b.y - a.y) * (c.x - b.x)
      if (abs(cross) > 1e-5f) {
        val cur = if (cross > 0f) 1 else -1
        if (sign == 0) sign = cur else if (sign != cur) return false
      }
    }
    return sign != 0
  }

  private fun clampNormalized(p: NormalizedPoint): NormalizedPoint =
    NormalizedPoint(p.x.coerceIn(0.02f, 0.98f), p.y.coerceIn(0.02f, 0.98f))

  /**
   * Performs projective perspective transform (deskew & crop) from the 4 detected quad corners to a
   * flat top-down rectangular scan bitmap.
   */
  fun warpAndCrop(source: Bitmap, quad: DetectedDocumentQuad): Bitmap {
    val w = source.width.toFloat()
    val h = source.height.toFloat()

    val tlX = quad.topLeft.x * w
    val tlY = quad.topLeft.y * h
    val trX = quad.topRight.x * w
    val trY = quad.topRight.y * h
    val brX = quad.bottomRight.x * w
    val brY = quad.bottomRight.y * h
    val blX = quad.bottomLeft.x * w
    val blY = quad.bottomLeft.y * h

    val estW = max(hypot(trX - tlX, trY - tlY), hypot(brX - blX, brY - blY)).coerceAtLeast(160f)
    val estH = max(hypot(blX - tlX, blY - tlY), hypot(brX - trX, brY - trY)).coerceAtLeast(160f)

    val downScale = min(1f, MAX_OUTPUT_DIMENSION.toFloat() / max(estW, estH))
    val outW = (estW * downScale).roundToInt().coerceAtLeast(160)
    val outH = (estH * downScale).roundToInt().coerceAtLeast(160)

    val srcPts = floatArrayOf(tlX, tlY, trX, trY, brX, brY, blX, blY)
    val dstPts = floatArrayOf(0f, 0f, outW.toFloat(), 0f, outW.toFloat(), outH.toFloat(), 0f, outH.toFloat())

    val matrix = Matrix()
    val mapped = matrix.setPolyToPoly(srcPts, 0, dstPts, 0, 4)

    val output = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(output)
    canvas.drawColor(Color.WHITE)

    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    if (mapped) {
      canvas.drawBitmap(source, matrix, paint)
    } else {
      val srcRect =
        Rect(
          min(tlX, blX).roundToInt().coerceIn(0, source.width - 1),
          min(tlY, trY).roundToInt().coerceIn(0, source.height - 1),
          max(trX, brX).roundToInt().coerceIn(1, source.width),
          max(blY, brY).roundToInt().coerceIn(1, source.height),
        )
      canvas.drawBitmap(source, srcRect, Rect(0, 0, outW, outH), paint)
    }
    return output
  }

  fun rotateBitmap90(bitmap: Bitmap): Bitmap {
    val matrix = Matrix().apply { postRotate(90f) }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
  }

  /**
   * Applies document digitization enhancement filters (illumination normalization / shadow removal,
   * contrast stretch, grayscale, or adaptive high-contrast binarization).
   */
  fun applyFilter(source: Bitmap, mode: DocumentFilterMode): Bitmap {
    if (mode == DocumentFilterMode.ORIGINAL) {
      return source.copy(Bitmap.Config.ARGB_8888, false)
    }

    val w = source.width
    val h = source.height
    val pixels = IntArray(w * h)
    source.getPixels(pixels, 0, w, 0, 0, w, h)

    // Estimate coarse local white-paper background illumination (20x24 grid) to remove shadows
    val gw = 20
    val gh = 24
    val bgGrid = IntArray(gw * gh)
    val cellW = max(1f, w.toFloat() / gw)
    val cellH = max(1f, h.toFloat() / gh)

    for (gy in 0 until gh) {
      val y0 = (gy * cellH).toInt().coerceIn(0, h - 1)
      val y1 = min(h, ((gy + 1) * cellH).toInt().coerceAtLeast(y0 + 1))
      for (gx in 0 until gw) {
        val x0 = (gx * cellW).toInt().coerceIn(0, w - 1)
        val x1 = min(w, ((gx + 1) * cellW).toInt().coerceAtLeast(x0 + 1))
        var maxLum = 96
        val stepX = max(1, (x1 - x0) / 8)
        val stepY = max(1, (y1 - y0) / 8)
        var y = y0
        while (y < y1) {
          val row = y * w
          var x = x0
          while (x < x1) {
            val p = pixels[row + x]
            val l = (((p ushr 16) and 0xFF) * 77 + ((p ushr 8) and 0xFF) * 150 + (p and 0xFF) * 29) ushr 8
            if (l > maxLum) maxLum = l
            x += stepX
          }
          y += stepY
        }
        bgGrid[gy * gw + gx] = maxLum
      }
    }

    // Smooth background grid once so tile seams never appear
    val smoothBg = bgGrid.copyOf()
    for (gy in 1 until gh - 1) {
      for (gx in 1 until gw - 1) {
        val idx = gy * gw + gx
        smoothBg[idx] =
          (bgGrid[idx - gw] + bgGrid[idx - 1] + 2 * bgGrid[idx] + bgGrid[idx + 1] + bgGrid[idx + gw]) / 6
      }
    }

    val outPixels = IntArray(w * h)
    for (y in 0 until h) {
      val fy = (y / cellH).coerceIn(0f, (gh - 1).toFloat())
      val gy0 = fy.toInt().coerceIn(0, gh - 1)
      val gy1 = min(gh - 1, gy0 + 1)
      val ty = fy - gy0
      val row = y * w

      for (x in 0 until w) {
        val fx = (x / cellW).coerceIn(0f, (gw - 1).toFloat())
        val gx0 = fx.toInt().coerceIn(0, gw - 1)
        val gx1 = min(gw - 1, gx0 + 1)
        val tx = fx - gx0

        val b00 = smoothBg[gy0 * gw + gx0].toFloat()
        val b10 = smoothBg[gy0 * gw + gx1].toFloat()
        val b01 = smoothBg[gy1 * gw + gx0].toFloat()
        val b11 = smoothBg[gy1 * gw + gx1].toFloat()
        val bgLum =
          ((b00 * (1f - tx) + b10 * tx) * (1f - ty) + (b01 * (1f - tx) + b11 * tx) * ty)
            .coerceAtLeast(80f)

        val p = pixels[row + x]
        val r = (p ushr 16) and 0xFF
        val g = (p ushr 8) and 0xFF
        val b = p and 0xFF

        outPixels[row + x] =
          when (mode) {
            DocumentFilterMode.MAGIC_SCAN -> {
              val gain = (250f / bgLum).coerceIn(1.0f, 2.35f)
              val nr = contrastBoost((r * gain).roundToInt(), 1.24f)
              val ng = contrastBoost((g * gain).roundToInt(), 1.24f)
              val nb = contrastBoost((b * gain).roundToInt(), 1.24f)
              (0xFF shl 24) or (nr shl 16) or (ng shl 8) or nb
            }
            DocumentFilterMode.GRAYSCALE -> {
              val lum = (r * 77 + g * 150 + b * 29) ushr 8
              val gain = (248f / bgLum).coerceIn(1.0f, 2.2f)
              val v = contrastBoost((lum * gain).roundToInt(), 1.32f)
              (0xFF shl 24) or (v shl 16) or (v shl 8) or v
            }
            DocumentFilterMode.HIGH_CONTRAST_BW -> {
              val lum = ((r * 77 + g * 150 + b * 29) ushr 8).toFloat()
              val ratio = lum / bgLum
              // Soft-threshold ramp prevents jagged strokes while snapping paper to crisp 255
              val v =
                when {
                  ratio <= 0.64f -> ((ratio / 0.64f) * 32f).roundToInt().coerceIn(0, 36)
                  ratio >= 0.84f -> 255
                  else -> {
                    val t = (ratio - 0.64f) / 0.20f
                    (36f + t * (255f - 36f)).roundToInt().coerceIn(0, 255)
                  }
                }
              (0xFF shl 24) or (v shl 16) or (v shl 8) or v
            }
            DocumentFilterMode.ORIGINAL -> p
          }
      }
    }

    return Bitmap.createBitmap(outPixels, w, h, Bitmap.Config.ARGB_8888)
  }

  private fun contrastBoost(channel: Int, factor: Float): Int {
    val c = channel.coerceIn(0, 255)
    // Lift highlights softly above 228 toward crisp paper white while deepening dark ink
    if (c >= 232) return 255
    val shifted = ((c - 128f) * factor + 132f).roundToInt()
    return shifted.coerceIn(0, 255)
  }

  fun decodeScaledBitmapFromUri(context: Context, uri: Uri, maxDim: Int = 2048): Bitmap? {
    val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { stream ->
      BitmapFactory.decodeStream(stream, null, boundsOpts)
    } ?: return null

    var sampleSize = 1
    while (boundsOpts.outWidth / sampleSize > maxDim || boundsOpts.outHeight / sampleSize > maxDim) {
      sampleSize *= 2
    }

    val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
    val raw =
      context.contentResolver.openInputStream(uri)?.use { stream ->
        BitmapFactory.decodeStream(stream, null, decodeOpts)
      } ?: return null

    val exifOrientation =
      runCatching {
        context.contentResolver.openInputStream(uri)?.use { stream ->
          ExifInterface(stream).getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL,
          )
        } ?: ExifInterface.ORIENTATION_NORMAL
      }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

    val rotationDegrees =
      when (exifOrientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
        else -> 0f
      }
    if (rotationDegrees == 0f) return raw

    val m = Matrix().apply { postRotate(rotationDegrees) }
    val rotated = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true)
    if (rotated !== raw) raw.recycle()
    return rotated
  }

  fun estimateJpegSizeBytes(bitmap: Bitmap, quality: Int = 88): Long {
    val baos = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos)
    return baos.size().toLong()
  }

  /**
   * Exports the scanned & enhanced document to internal app files storage as either a multi/single-page
   * PDF (`application/pdf`) or high-clarity JPEG (`image/jpeg`) guaranteed <= 5 MB and returns a
   * `FileProvider` URI ready for Document Vault persistence.
   */
  fun exportScanToVaultFile(
    context: Context,
    pages: List<Bitmap>,
    format: ScanExportFormat,
    baseFileName: String,
  ): ExportedScanResult {
    require(pages.isNotEmpty()) { "At least one scanned page is required." }

    val dir = File(context.filesDir, "scanned_docs").apply { mkdirs() }
    val cleanStem =
      baseFileName
        .trim()
        .lowercase()
        .replace(Regex("[^a-z0-9_-]+"), "_")
        .trim('_')
        .ifBlank { "hr_scan" }
    val stamp = System.currentTimeMillis()
    val fileName = "${cleanStem}_${stamp}.${format.extension}"
    val targetFile = File(dir, fileName)

    when (format) {
      ScanExportFormat.JPEG -> {
        val page = pages.first()
        var quality = 90
        var jpegBytes: ByteArray
        do {
          val baos = ByteArrayOutputStream()
          page.compress(Bitmap.CompressFormat.JPEG, quality, baos)
          jpegBytes = baos.toByteArray()
          quality -= 10
        } while (jpegBytes.size > MAX_VAULT_BYTES && quality >= 50)
        FileOutputStream(targetFile).use { it.write(jpegBytes) }
      }

      ScanExportFormat.PDF -> {
        val pdfDoc = PdfDocument()
        try {
          pages.forEachIndexed { idx, bmp ->
            // Standard 595pt width (A4 proportion at 72dpi) scaling page height cleanly
            val pageW = 595
            val pageH = ((bmp.height.toFloat() / bmp.width.toFloat()) * pageW).roundToInt().coerceIn(240, 1200)
            val pageInfo = PdfDocument.PageInfo.Builder(pageW, pageH, idx + 1).create()
            val page = pdfDoc.startPage(pageInfo)
            val destRect = Rect(0, 0, pageW, pageH)
            page.canvas.drawBitmap(bmp, null, destRect, Paint(Paint.FILTER_BITMAP_FLAG))
            pdfDoc.finishPage(page)
          }
          FileOutputStream(targetFile).use { out -> pdfDoc.writeTo(out) }
        } finally {
          pdfDoc.close()
        }
      }
    }

    val authority = "${context.packageName}.fileprovider"
    val contentUri = FileProvider.getUriForFile(context, authority, targetFile)
    return ExportedScanResult(
      uri = contentUri,
      fileName = fileName,
      mimeType = format.mimeType,
      fileSizeBytes = targetFile.length(),
    )
  }
}
