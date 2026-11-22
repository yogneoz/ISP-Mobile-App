package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColorScheme =
  lightColorScheme(
    primary = CobaltPrimary,
    onPrimary = Color.White,
    primaryContainer = CobaltContainer,
    onPrimaryContainer = OnCobaltContainer,
    secondary = EmeraldVerified,
    onSecondary = Color.White,
    secondaryContainer = EmeraldContainer,
    onSecondaryContainer = OnEmeraldContainer,
    tertiary = TealHoliday,
    onTertiary = Color.White,
    tertiaryContainer = TealContainer,
    onTertiaryContainer = OnTealContainer,
    error = CrimsonAlert,
    onError = Color.White,
    errorContainer = CrimsonContainer,
    onErrorContainer = OnCrimsonContainer,
    background = CoolOffWhite,
    onBackground = MidnightSlate,
    surface = CrispSurface,
    onSurface = MidnightSlate,
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = Color(0xFF475569),
    outline = Color(0xFFCBD5E1),
    outlineVariant = SubtleBorder,
  )

private val DarkColorScheme =
  darkColorScheme(
    primary = CobaltLight,
    onPrimary = Color(0xFF0A1E52),
    primaryContainer = CobaltDark,
    onPrimaryContainer = CobaltContainer,
    secondary = Color(0xFF34D399),
    onSecondary = Color(0xFF022C22),
    secondaryContainer = Color(0xFF064E3B),
    onSecondaryContainer = Color(0xFFA7F3D0),
    tertiary = Color(0xFF2DD4BF),
    onTertiary = Color(0xFF042F2E),
    tertiaryContainer = Color(0xFF134E4A),
    onTertiaryContainer = Color(0xFF99F6E4),
    error = Color(0xFFF87171),
    onError = Color(0xFF450A0A),
    errorContainer = Color(0xFF7F1D1D),
    onErrorContainer = Color(0xFFFECACA),
    background = Color(0xFF0B1120),
    onBackground = Color(0xFFF8FAFC),
    surface = Color(0xFF111827),
    onSurface = Color(0xFFF8FAFC),
    surfaceVariant = SlateSurface,
    onSurfaceVariant = Color(0xFF94A3B8),
    outline = Color(0xFF475569),
    outlineVariant = SlateVariant,
  )

val PulseShapes =
  Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
  )

@Composable
fun PulseHrTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  content: @Composable () -> Unit,
) {
  val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
  val semanticColors = if (darkTheme) DarkPulseColors else LightPulseColors

  CompositionLocalProvider(LocalPulseColors provides semanticColors) {
    MaterialTheme(
      colorScheme = colorScheme,
      typography = PulseTypography,
      shapes = PulseShapes,
      content = content,
    )
  }
}

object PulseTheme {
  val semantic: PulseSemanticColors
    @Composable get() = LocalPulseColors.current
}
