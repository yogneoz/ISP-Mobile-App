package com.example.scanner

import android.graphics.Bitmap
import com.example.domain.model.DocumentOcrMetadata
import com.example.domain.model.DocumentType
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.math.min
import kotlinx.coroutines.suspendCancellableCoroutine

data class LiveOcrHint(
  val blockCount: Int = 0,
  val lineCount: Int = 0,
  val categoryHint: DocumentType? = null,
)

data class DocumentOcrExtraction(
  val rawText: String,
  val lineCount: Int,
  val blockCount: Int,
  val suggestedCategory: DocumentType,
  val confidenceScore: Float,
  val categorizationReason: String,
  val suggestedTitle: String,
  val vendorOrIssuer: String? = null,
  val detectedDate: String? = null,
  val detectedAmount: String? = null,
  val detectedReferenceId: String? = null,
  val keyValueFields: List<Pair<String, String>> = emptyList(),
) {
  val hasRecognizedText: Boolean
    get() = rawText.isNotBlank()

  fun confidencePercent(): Int = (confidenceScore * 100f).toInt().coerceIn(0, 100)

  fun toOcrMetadata(): DocumentOcrMetadata =
    DocumentOcrMetadata(
      extractedText = rawText.trim().takeIf { it.isNotBlank() },
      detectedVendorOrIssuer = vendorOrIssuer,
      detectedDate = detectedDate,
      detectedAmount = detectedAmount,
      detectedReferenceId = detectedReferenceId,
      ocrConfidenceLabel =
        if (hasRecognizedText) {
          "ML Kit OCR • ${confidencePercent()}% ${suggestedCategory.label}"
        } else {
          null
        },
    )
}

interface DocumentTextAnalyzer {
  suspend fun analyzeBitmap(bitmap: Bitmap): DocumentOcrExtraction

  suspend fun detectLiveTextHint(bitmap: Bitmap): LiveOcrHint
}

@Singleton
class MlKitDocumentTextAnalyzer @Inject constructor() : DocumentTextAnalyzer {

  private val recognizer by lazy {
    TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
  }

  override suspend fun analyzeBitmap(bitmap: Bitmap): DocumentOcrExtraction {
    val result = runMlKitRecognition(bitmap) ?: return DocumentOcrParser.emptyResult()
    val blocks = result.textBlocks.mapNotNull { block -> block.text.trim().takeIf { it.isNotEmpty() } }
    return DocumentOcrParser.parseAndCategorize(rawText = result.text, blocks = blocks)
  }

  override suspend fun detectLiveTextHint(bitmap: Bitmap): LiveOcrHint {
    val result = runMlKitRecognition(bitmap) ?: return LiveOcrHint()
    val blocks = result.textBlocks.mapNotNull { block -> block.text.trim().takeIf { it.isNotEmpty() } }
    if (blocks.isEmpty() || result.text.isBlank()) return LiveOcrHint()
    val lines = result.text.lines().count { it.isNotBlank() }
    val parsed = DocumentOcrParser.parseAndCategorize(rawText = result.text, blocks = blocks)
    return LiveOcrHint(
      blockCount = blocks.size,
      lineCount = lines,
      categoryHint =
        if (parsed.confidenceScore >= 0.60f) parsed.suggestedCategory else null,
    )
  }

  private suspend fun runMlKitRecognition(bitmap: Bitmap): Text? {
    val inputImage = InputImage.fromBitmap(bitmap, 0)
    return suspendCancellableCoroutine { cont ->
      recognizer
        .process(inputImage)
        .addOnSuccessListener { visionText ->
          if (cont.isActive) cont.resume(visionText)
        }
        .addOnFailureListener {
          if (cont.isActive) cont.resume(null)
        }
    }
  }
}

object DocumentOcrParser {

  private val GENERIC_HEADER_WORDS =
    setOf(
      "receipt",
      "tax invoice",
      "invoice",
      "welcome",
      "thank you",
      "customer copy",
      "merchant copy",
      "original",
      "duplicate",
      "official document",
      "page 1",
    )

  private val DATE_PATTERNS =
    listOf(
      Regex("\\b(20\\d{2}[-/]\\d{1,2}[-/]\\d{1,2})\\b"),
      Regex("\\b(\\d{1,2}[-/]\\d{1,2}[-/]20\\d{2})\\b"),
      Regex(
        "\\b((?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Sept|Oct|Nov|Dec)[a-z]*\\s+\\d{1,2},?\\s+20\\d{2})\\b",
        RegexOption.IGNORE_CASE,
      ),
      Regex(
        "\\b(\\d{1,2}\\s+(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Sept|Oct|Nov|Dec)[a-z]*\\s+20\\d{2})\\b",
        RegexOption.IGNORE_CASE,
      ),
    )

  private val TOTAL_LINE_REGEX =
    Regex(
      "(?:grand\\s+total|total\\s+due|amount\\s+due|net\\s+payable|balance\\s+due|amount\\s+paid|total|subtotal)",
      RegexOption.IGNORE_CASE,
    )

  private val CURRENCY_AMOUNT_REGEX =
    Regex(
      "(\\$|USD|NPR|Rs\\.?|EUR|GBP|€|£)?\\s*(\\d{1,3}(?:,\\d{3})*(?:\\.\\d{2})|\\d+\\.\\d{2})",
      RegexOption.IGNORE_CASE,
    )

  private val REFERENCE_ID_REGEX =
    Regex(
      "(?:receipt|inv(?:oice)?|order|trans(?:action)?|cert(?:ificate)?|license|lic|pan|tin|ein|form|ref(?:erence)?|ticket|reg(?:istration)?)\\s*(?:#|no\\.?|id|number|code)?\\s*[:\\-#]?\\s*([A-Z0-9][A-Z0-9\\-/]{3,22})",
      RegexOption.IGNORE_CASE,
    )

  private val LABELED_ISSUER_REGEX =
    Regex(
      "^(?:vendor|merchant|store|issued\\s+by|issuer|authority|organization|company|from)\\s*[:\\-]\\s*(.+)$",
      RegexOption.IGNORE_CASE,
    )

  fun emptyResult(): DocumentOcrExtraction {
    val stamp = SimpleDateFormat("MMMdd_HHmm", Locale.US).format(Date())
    return DocumentOcrExtraction(
      rawText = "",
      lineCount = 0,
      blockCount = 0,
      suggestedCategory = DocumentType.COMPLIANCE,
      confidenceScore = 0.0f,
      categorizationReason = "No readable text detected yet — try B&W Text filter or brighter lighting",
      suggestedTitle = "Scanned_Doc_$stamp",
    )
  }

  fun parseAndCategorize(
    rawText: String,
    blocks: List<String> = emptyList(),
  ): DocumentOcrExtraction {
    val cleaned = rawText.replace("\r\n", "\n").trim()
    if (cleaned.isBlank()) return emptyResult()

    val lines = cleaned.lines().map { it.trim() }.filter { it.isNotEmpty() }
    val effectiveBlockCount = if (blocks.isNotEmpty()) blocks.size else 1
    val lower = cleaned.lowercase(Locale.US)

    val detectedDate = extractDate(cleaned)
    val detectedAmount = extractAmount(lines, cleaned)
    val detectedRefId = extractReferenceId(lines)
    val vendorOrIssuer = extractVendorOrIssuer(lines)

    val (category, score, reason) = classifyDocument(lower, detectedAmount, detectedRefId)
    val suggestedTitle =
      buildSuggestedTitle(
        category = category,
        vendorOrIssuer = vendorOrIssuer,
        detectedDate = detectedDate,
        detectedReferenceId = detectedRefId,
      )

    val keyValues =
      buildList {
        vendorOrIssuer?.let { add("Vendor / Issuer" to it) }
        detectedAmount?.let { add("Detected Amount" to it) }
        detectedDate?.let { add("Document Date" to it) }
        detectedRefId?.let { add("Reference ID" to it) }
        add("OCR Coverage" to "${lines.size} line(s) • $effectiveBlockCount block(s)")
      }

    return DocumentOcrExtraction(
      rawText = cleaned,
      lineCount = lines.size,
      blockCount = effectiveBlockCount,
      suggestedCategory = category,
      confidenceScore = score,
      categorizationReason = reason,
      suggestedTitle = suggestedTitle,
      vendorOrIssuer = vendorOrIssuer,
      detectedDate = detectedDate,
      detectedAmount = detectedAmount,
      detectedReferenceId = detectedRefId,
      keyValueFields = keyValues,
    )
  }

  private fun extractDate(text: String): String? {
    for (pattern in DATE_PATTERNS) {
      val match = pattern.find(text)
      if (match != null) {
        return match.groupValues[1].trim()
      }
    }
    return null
  }

  private fun extractAmount(lines: List<String>, fullText: String): String? {
    // Pass 1: Prefer explicit total/amount due lines from bottom upward
    for (line in lines.asReversed()) {
      if (TOTAL_LINE_REGEX.containsMatchIn(line)) {
        val match = CURRENCY_AMOUNT_REGEX.findAll(line).lastOrNull()
        if (match != null) {
          val prefix = match.groupValues[1].trim()
          val num = match.groupValues[2].trim()
          return if (prefix.isNotBlank()) "$prefix $num".replace("$ ", "$") else "$$num"
        }
      }
    }

    // Pass 2: Any currency-prefixed decimal value in the document
    val explicitCurrencyMatches =
      CURRENCY_AMOUNT_REGEX.findAll(fullText).filter { it.groupValues[1].isNotBlank() }.toList()
    val bestExplicit =
      explicitCurrencyMatches.maxByOrNull {
        it.groupValues[2].replace(",", "").toDoubleOrNull() ?: 0.0
      }
    if (bestExplicit != null) {
      val prefix = bestExplicit.groupValues[1].trim()
      val num = bestExplicit.groupValues[2].trim()
      return "$prefix $num".replace("$ ", "$")
    }
    return null
  }

  private fun extractReferenceId(lines: List<String>): String? {
    for (line in lines) {
      val match = REFERENCE_ID_REGEX.find(line)
      if (match != null) {
        val candidate = match.groupValues[1].trim().trimEnd('.', ',', ';')
        if (candidate.any { it.isDigit() } && candidate.length >= 4) {
          return candidate.uppercase(Locale.US)
        }
      }
    }
    return null
  }

  private fun extractVendorOrIssuer(lines: List<String>): String? {
    for (line in lines) {
      val labeled = LABELED_ISSUER_REGEX.find(line)
      if (labeled != null) {
        val value = labeled.groupValues[1].trim()
        if (value.length in 3..64) return value
      }
    }

    for (line in lines.take(7)) {
      val normalized = line.lowercase(Locale.US)
      if (normalized in GENERIC_HEADER_WORDS) continue
      if (line.length < 3 || line.length > 56) continue
      val letterCount = line.count { it.isLetter() }
      val digitCount = line.count { it.isDigit() }
      if (letterCount >= 3 && letterCount >= digitCount) {
        return line
      }
    }
    return null
  }

  private fun classifyDocument(
    lowerText: String,
    detectedAmount: String?,
    detectedRefId: String?,
  ): Triple<DocumentType, Float, String> {
    val scores = mutableMapOf<DocumentType, Int>()
    val reasons = mutableMapOf<DocumentType, MutableList<String>>()

    fun addSignal(type: DocumentType, weight: Int, keyword: String) {
      if (lowerText.contains(keyword)) {
        scores[type] = (scores[type] ?: 0) + weight
        reasons.getOrPut(type) { mutableListOf() }.add(keyword)
      }
    }

    // Expense Receipt signals
    listOf(
      "receipt" to 4,
      "subtotal" to 4,
      "grand total" to 4,
      "amount due" to 3,
      "sales tax" to 3,
      "vat" to 2,
      "gst" to 2,
      "visa" to 3,
      "mastercard" to 3,
      "cash" to 2,
      "change due" to 3,
      "merchant" to 2,
      "invoice" to 2,
      "fuel" to 3,
      "gallons" to 3,
      "qty" to 2,
      "thank you for" to 2,
    ).forEach { (kw, w) -> addSignal(DocumentType.RECEIPT, w, kw) }
    if (detectedAmount != null) {
      scores[DocumentType.RECEIPT] = (scores[DocumentType.RECEIPT] ?: 0) + 3
      reasons.getOrPut(DocumentType.RECEIPT) { mutableListOf() }.add("currency total")
    }

    // Certificate / License signals
    listOf(
      "certificate" to 5,
      "this is to certify" to 6,
      "hereby certifies" to 6,
      "awarded to" to 4,
      "completion" to 3,
      "training" to 3,
      "osha" to 4,
      "diploma" to 4,
      "license" to 3,
      "accredited" to 3,
      "credential" to 3,
      "valid thru" to 3,
      "certification" to 4,
      "issued by" to 2,
    ).forEach { (kw, w) -> addSignal(DocumentType.CERTIFICATE, w, kw) }

    // Tax / PAN signals
    listOf(
      "permanent account number" to 6,
      "pan no" to 5,
      "pan:" to 5,
      "inland revenue" to 5,
      "taxpayer" to 4,
      "tax registration" to 5,
      "tds" to 3,
      "tin" to 2,
      "ein" to 2,
      "w-2" to 4,
      "1099" to 4,
      "withholding" to 3,
    ).forEach { (kw, w) -> addSignal(DocumentType.TAX_PAN, w, kw) }

    // HR / Onboarding Form signals
    listOf(
      "reimbursement form" to 5,
      "leave form" to 5,
      "onboarding" to 4,
      "enrollment" to 4,
      "application form" to 5,
      "hr form" to 5,
      "employee id" to 3,
      "department" to 2,
      "supervisor" to 3,
      "applicant" to 3,
      "signature" to 2,
      "declaration" to 3,
      "requisition" to 4,
      "form" to 2,
    ).forEach { (kw, w) -> addSignal(DocumentType.HR_FORM, w, kw) }

    // ID Proof signals
    listOf(
      "passport" to 6,
      "national id" to 6,
      "citizenship" to 5,
      "driver license" to 5,
      "driver's license" to 5,
      "date of birth" to 4,
      "nationality" to 3,
      "identity card" to 5,
    ).forEach { (kw, w) -> addSignal(DocumentType.ID_PROOF, w, kw) }

    // Employment Contract signals
    listOf(
      "employment agreement" to 6,
      "contract" to 4,
      "terms of employment" to 5,
      "confidentiality" to 3,
      "non-disclosure" to 4,
      "compensation" to 3,
      "effective date" to 2,
    ).forEach { (kw, w) -> addSignal(DocumentType.CONTRACT, w, kw) }

    val best = scores.maxByOrNull { it.value }
    if (best == null || best.value < 2) {
      val fallbackReason =
        if (detectedRefId != null) {
          "Recognized general text with reference ID $detectedRefId"
        } else {
          "Recognized text saved under Compliance Filing"
        }
      return Triple(DocumentType.COMPLIANCE, 0.54f, fallbackReason)
    }

    val winner = best.key
    val totalScore = best.value
    val confidence = (0.58f + min(totalScore, 14) * 0.028f).coerceIn(0.60f, 0.98f)
    val matchedLabels = reasons[winner].orEmpty().distinct().take(3).joinToString(", ")
    return Triple(winner, confidence, "Matched ${winner.label} signals ($matchedLabels)")
  }

  private fun buildSuggestedTitle(
    category: DocumentType,
    vendorOrIssuer: String?,
    detectedDate: String?,
    detectedReferenceId: String?,
  ): String {
    val shortPrefix =
      when (category) {
        DocumentType.RECEIPT -> "Receipt"
        DocumentType.CERTIFICATE -> "Certificate"
        DocumentType.HR_FORM -> "HR Form"
        DocumentType.ID_PROOF -> "ID Proof"
        DocumentType.TAX_PAN -> "Tax Record"
        DocumentType.CONTRACT -> "Contract"
        DocumentType.COMPLIANCE -> "Compliance Doc"
        DocumentType.PROFILE_PHOTO -> "Photo"
      }

    val primaryEntity =
      vendorOrIssuer
        ?.take(28)
        ?.trim()
        ?.takeIf { it.isNotBlank() }

    val parts =
      buildList {
        if (primaryEntity != null) add(primaryEntity)
        add(shortPrefix)
        if (!detectedReferenceId.isNullOrBlank() && primaryEntity == null) {
          add(detectedReferenceId)
        }
        if (!detectedDate.isNullOrBlank()) {
          add(detectedDate)
        }
      }
    return parts.distinct().joinToString(" • ").take(64)
  }
}
