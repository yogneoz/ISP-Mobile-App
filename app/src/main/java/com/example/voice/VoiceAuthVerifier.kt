package com.example.voice

data class VoicePhraseMatchResult(
  val isAuthenticated: Boolean,
  val matchedCandidate: String?,
  val heardSummary: String,
)

object VoiceAuthVerifier {

  fun normalize(text: String): String {
    return text
      .lowercase()
      .replace('-', ' ')
      .replace('_', ' ')
      .replace(Regex("[^a-z0-9\\s]"), " ")
      .replace(Regex("\\s+"), " ")
      .trim()
  }

  fun verifySpokenCandidates(
    candidates: List<String>,
    expectedPhrase: String,
  ): VoicePhraseMatchResult {
    val normalizedExpected = normalize(expectedPhrase)
    val heardSummary = candidates.firstOrNull { it.isNotBlank() }?.trim().orEmpty()
    if (normalizedExpected.isBlank() || candidates.isEmpty()) {
      return VoicePhraseMatchResult(
        isAuthenticated = false,
        matchedCandidate = null,
        heardSummary = heardSummary,
      )
    }

    val acceptedForms = buildSet {
      add(normalizedExpected)
      add(normalizedExpected.replace("check in", "checkin"))
      add(normalizedExpected.replace("checkin", "check in"))
    }

    val matched =
      candidates.firstOrNull { raw ->
        val norm = normalize(raw)
        val normCollapsed = norm.replace("check in", "checkin")
        acceptedForms.any { expected ->
          val expCollapsed = expected.replace("check in", "checkin")
          norm == expected || normCollapsed == expCollapsed
        }
      }

    return VoicePhraseMatchResult(
      isAuthenticated = matched != null,
      matchedCandidate = matched?.trim(),
      heardSummary = heardSummary,
    )
  }
}
