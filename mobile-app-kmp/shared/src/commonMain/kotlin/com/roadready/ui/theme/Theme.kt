package com.roadready.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Precision Voyager inspired dark theme colors
val Background = Color(0xFF0F172A) // Deep Slate
val Surface = Color(0xFF1E293B) // Lighter Slate
val SurfaceVariant = Color(0xFF334155)
val SurfaceContainer = Color(0xFF1E293B).copy(alpha = 0.8f)

val Primary = Color(0xFF2563EB) // Vibrant Blue
val PrimaryContainer = Color(0xFF1D4ED8)
val OnPrimary = Color.White

val Secondary = Color(0xFF10B981) // Teal
val SecondaryContainer = Color(0xFF047857)
val OnSecondary = Color.White

val Accent = Secondary
val AccentLight = Secondary

val Glass = Color(0xFFFFFFFF).copy(alpha = 0.08f)
val GlassStroke = Color(0xFFFFFFFF).copy(alpha = 0.15f)

val TextPrimary = Color(0xFFF8FAFC)
val TextSecondary = Color(0xFFCBD5E1)
val TextMuted = Color(0xFF94A3B8)

val Error = Color(0xFFEF4444)
val Warning = Color(0xFFF59E0B)
val Success = Color(0xFF10B981)

private val RoadReadyColors = darkColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    primaryContainer = PrimaryContainer,
    secondary = Secondary,
    onSecondary = OnSecondary,
    secondaryContainer = SecondaryContainer,
    background = Background,
    onBackground = TextPrimary,
    surface = Surface,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = TextSecondary,
    error = Error,
    onError = Color.White,
    outline = GlassStroke,
    outlineVariant = GlassStroke.copy(alpha = 0.1f)
)

private val RoadReadyTypography = Typography(
    displayLarge = TextStyle(
        fontWeight = FontWeight.ExtraBold,
        fontSize = 36.sp,
        letterSpacing = (-0.02).sp,
        color = TextPrimary,
    ),
    displayMedium = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        letterSpacing = (-0.01).sp,
        color = TextPrimary,
    ),
    headlineLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        letterSpacing = (-0.01).sp,
        color = TextPrimary,
    ),
    headlineMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        color = TextPrimary,
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        color = TextPrimary,
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        color = TextPrimary,
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        color = TextPrimary,
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        color = TextSecondary,
    ),
    bodySmall = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        color = TextMuted,
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        color = TextPrimary,
    ),
    labelMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        color = TextSecondary,
    ),
)

@Composable
fun RoadReadyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = RoadReadyColors,
        typography = RoadReadyTypography,
        content = content,
    )
}
