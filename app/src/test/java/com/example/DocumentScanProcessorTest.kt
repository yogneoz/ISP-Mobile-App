package com.example

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import com.example.scanner.DocumentFilterMode
import com.example.scanner.DocumentScanProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [36])
class DocumentScanProcessorTest {

  @Test
  fun `detectDocumentEdges locks onto bright trapezoidal paper sheet over dark desk`() {
    val bmp = Bitmap.createBitmap(300, 400, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    canvas.drawColor(Color.rgb(24, 28, 36)) // Dark desk background

    // Draw slightly perspective-tilted paper receipt inside frame
    val paperPaint = Paint().apply { color = Color.rgb(242, 240, 232) }
    val paperPath =
      Path().apply {
        moveTo(54f, 64f) // TL (0.18, 0.16)
        lineTo(255f, 76f) // TR (0.85, 0.19)
        lineTo(264f, 344f) // BR (0.88, 0.86)
        lineTo(42f, 332f) // BL (0.14, 0.83)
        close()
      }
    canvas.drawPath(paperPath, paperPaint)

    // Draw simulated printed text lines inside receipt
    val inkPaint = Paint().apply {
      color = Color.rgb(35, 38, 45)
      strokeWidth = 4f
    }
    canvas.drawLine(80f, 130f, 220f, 132f, inkPaint)
    canvas.drawLine(80f, 165f, 210f, 167f, inkPaint)
    canvas.drawLine(80f, 210f, 195f, 212f, inkPaint)

    val quad = DocumentScanProcessor.detectDocumentEdges(bmp)
    assertTrue("Expected locked quad, confidence=${quad.confidence}", quad.isLocked)
    assertTrue(quad.topLeft.x in 0.10f..0.28f)
    assertTrue(quad.topLeft.y in 0.10f..0.26f)
    assertTrue(quad.bottomRight.x in 0.78f..0.94f)
    assertTrue(quad.bottomRight.y in 0.78f..0.92f)

    val cropped = DocumentScanProcessor.warpAndCrop(bmp, quad)
    assertTrue(cropped.width >= 160)
    assertTrue(cropped.height >= 160)
    cropped.recycle()
    bmp.recycle()
  }

  @Test
  fun `detectDocumentEdges returns unlocked fallback on uniform empty scene`() {
    val blank = Bitmap.createBitmap(240, 320, Bitmap.Config.ARGB_8888)
    blank.eraseColor(Color.rgb(90, 96, 104))

    val quad = DocumentScanProcessor.detectDocumentEdges(blank)
    assertFalse(quad.isLocked)
    blank.recycle()
  }

  @Test
  fun `enhancement filters lift shadowed paper and enforce grayscale and bw contrast`() {
    val bmp = Bitmap.createBitmap(120, 160, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    // Unevenly lit dull paper background
    canvas.drawColor(Color.rgb(168, 162, 150))
    val ink = Paint().apply { color = Color.rgb(32, 34, 40) }
    canvas.drawRect(30f, 50f, 90f, 70f, ink)

    val magic = DocumentScanProcessor.applyFilter(bmp, DocumentFilterMode.MAGIC_SCAN)
    val magicCorner = magic.getPixel(10, 10)
    val magicPaperLum =
      ((Color.red(magicCorner) * 77 + Color.green(magicCorner) * 150 + Color.blue(magicCorner) * 29) ushr 8)
    assertTrue("Expected Magic Scan to lift paper background white, got $magicPaperLum", magicPaperLum >= 230)

    val gray = DocumentScanProcessor.applyFilter(bmp, DocumentFilterMode.GRAYSCALE)
    val gPixel = gray.getPixel(45, 60)
    assertEquals(Color.red(gPixel), Color.green(gPixel))
    assertEquals(Color.green(gPixel), Color.blue(gPixel))

    val bw = DocumentScanProcessor.applyFilter(bmp, DocumentFilterMode.HIGH_CONTRAST_BW)
    val bwPaper = Color.red(bw.getPixel(10, 10))
    val bwInk = Color.red(bw.getPixel(50, 60))
    assertEquals(255, bwPaper)
    assertTrue("Expected dark ink in B&W mode, got $bwInk", bwInk < 48)

    magic.recycle()
    gray.recycle()
    bw.recycle()
    bmp.recycle()
  }
}
