package com.example.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Brand Core Palette — Swiss Enterprise Precision
val CobaltPrimary = Color(0xFF1D4ED8)
val CobaltDark = Color(0xFF1E40AF)
val CobaltLight = Color(0xFF60A5FA)
val CobaltContainer = Color(0xFFDBEAFE)
val OnCobaltContainer = Color(0xFF0F296B)

val MidnightSlate = Color(0xFF0F172A)
val SlateSurface = Color(0xFF1E293B)
val SlateVariant = Color(0xFF334155)
val CoolOffWhite = Color(0xFFF8FAFC)
val CrispSurface = Color(0xFFFFFFFF)
val SubtleBorder = Color(0xFFE2E8F0)

val EmeraldVerified = Color(0xFF059669)
val EmeraldContainer = Color(0xFFD1FAE5)
val OnEmeraldContainer = Color(0xFF064E3B)

val AmberWarning = Color(0xFFD97706)
val AmberContainer = Color(0xFFFEF3C7)
val OnAmberContainer = Color(0xFF78350F)

val CrimsonAlert = Color(0xFFDC2626)
val CrimsonContainer = Color(0xFFFEE2E2)
val OnCrimsonContainer = Color(0xFF7F1D1D)

val TealHoliday = Color(0xFF0D9488)
val TealContainer = Color(0xFFCCFBF1)
val OnTealContainer = Color(0xFF134E4A)

@Immutable
data class PulseSemanticColors(
  val verified: Color,
  val verifiedContainer: Color,
  val onVerifiedContainer: Color,
  val warning: Color,
  val warningContainer: Color,
  val onWarningContainer: Color,
  val danger: Color,
  val dangerContainer: Color,
  val onDangerContainer: Color,
  val holiday: Color,
  val holidayContainer: Color,
  val onHolidayContainer: Color,
  val subtleDivider: Color,
)

val LightPulseColors =
  PulseSemanticColors(
    verified = EmeraldVerified,
    verifiedContainer = EmeraldContainer,
    onVerifiedContainer = OnEmeraldContainer,
    warning = AmberWarning,
    warningContainer = AmberContainer,
    onWarningContainer = OnAmberContainer,
    danger = CrimsonAlert,
    dangerContainer = CrimsonContainer,
    onDangerContainer = OnCrimsonContainer,
    holiday = TealHoliday,
    holidayContainer = TealContainer,
    onHolidayContainer = OnTealContainer,
    subtleDivider = SubtleBorder,
  )

val DarkPulseColors =
  PulseSemanticColors(
    verified = Color(0xFF34D399),
    verifiedContainer = Color(0xFF064E3B),
    onVerifiedContainer = Color(0xFFA7F3D0),
    warning = Color(0xFFFBBF24),
    warningContainer = Color(0xFF78350F),
    onWarningContainer = Color(0xFFFDE68A),
    danger = Color(0xFFF87171),
    dangerContainer = Color(0xFF7F1D1D),
    onDangerContainer = Color(0xFFFECACA),
    holiday = Color(0xFF2DD4BF),
    holidayContainer = Color(0xFF134E4A),
    onHolidayContainer = Color(0xFF99F6E4),
    subtleDivider = Color(0xFF334155),
  )

val LocalPulseColors = staticCompositionLocalOf { LightPulseColors }
