package com.meshalive.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.meshalive.app.R

// ---- Brand palette: emergency red on deep navy, calm off-white surfaces ----
val SosRed = Color(0xFFE53935)
val SosRedDark = Color(0xFFB71C1C)
val Navy = Color(0xFF0B1F3A)
val NavyMid = Color(0xFF15325C)
val GatewayGreen = Color(0xFF2E7D32)
val SafeGreen = Color(0xFF16A34A)
val MeBlue = Color(0xFF1E66D0)
val LowBatteryOrange = Color(0xFFEF6C00)
val NodeGrey = Color(0xFF64748B)
val RouteGold = Color(0xFFFFB300)
val Paper = Color(0xFFF6F7FB)
val Ink = Color(0xFF0F172A)
val Muted = Color(0xFF64748B)

val HeroGradient = Brush.linearGradient(listOf(Navy, NavyMid))
val SosGradient = Brush.radialGradient(listOf(Color(0xFFFF5A52), SosRed, SosRedDark))

val Poppins = FontFamily(
    Font(R.font.poppins_regular, FontWeight.Normal),
    Font(R.font.poppins_medium, FontWeight.Medium),
    Font(R.font.poppins_semibold, FontWeight.SemiBold),
    Font(R.font.poppins_bold, FontWeight.Bold),
    Font(R.font.poppins_extrabold, FontWeight.ExtraBold),
    Font(R.font.poppins_extrabold, FontWeight.Black),
)

private val Colors = lightColorScheme(
    primary = SosRed,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFDE8E7),
    onPrimaryContainer = SosRedDark,
    secondary = Navy,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE6ECF5),
    onSecondaryContainer = Navy,
    tertiary = SafeGreen,
    background = Paper,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = Color(0xFFEFF2F7),
    onSurfaceVariant = Muted,
    surfaceContainer = Color(0xFFF1F4F9),
    surfaceContainerLow = Color.White,
    surfaceContainerHigh = Color(0xFFEDF1F7),
    surfaceContainerHighest = Color(0xFFE6EBF2),
    outline = Color(0xFFCBD5E1),
    outlineVariant = Color(0xFFE2E8F0),
    error = SosRedDark,
)

private fun style(size: Int, weight: FontWeight, line: Int = (size * 1.35).toInt(), spacing: Double = 0.0) =
    TextStyle(fontFamily = Poppins, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp, letterSpacing = spacing.sp)

private val AppTypography = Typography(
    displayLarge = style(48, FontWeight.ExtraBold, 54),
    displayMedium = style(40, FontWeight.ExtraBold, 46),
    displaySmall = style(32, FontWeight.Bold, 38),
    headlineLarge = style(28, FontWeight.Bold, 34),
    headlineMedium = style(24, FontWeight.Bold, 30),
    headlineSmall = style(20, FontWeight.SemiBold, 26),
    titleLarge = style(20, FontWeight.SemiBold, 26),
    titleMedium = style(16, FontWeight.SemiBold, 22),
    titleSmall = style(14, FontWeight.SemiBold, 20),
    bodyLarge = style(16, FontWeight.Normal, 24),
    bodyMedium = style(14, FontWeight.Normal, 20),
    bodySmall = style(12, FontWeight.Normal, 17),
    labelLarge = style(14, FontWeight.Medium, 20),
    labelMedium = style(12, FontWeight.Medium, 16),
    labelSmall = style(11, FontWeight.Medium, 15, 0.2),
)

@Composable
fun MeshAliveTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, typography = AppTypography, content = content)
}
