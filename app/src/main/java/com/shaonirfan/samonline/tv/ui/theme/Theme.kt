package com.shaonirfan.samonline.tv.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Typography
import androidx.tv.material3.darkColorScheme

object SamColors {
    val Background = Color(0xFF0B0B0F)
    val Surface = Color(0xFF16161D)
    val SurfaceRaised = Color(0xFF22222C)
    val Accent = Color(0xFFE50914)
    val AccentSoft = Color(0xFFFF3B47)
    val TextPrimary = Color(0xFFF5F5F7)
    val TextSecondary = Color(0xFFB3B3BD)
    val TextMuted = Color(0xFF7D7D88)
    val FocusRing = Color(0xFFFFFFFF)
    val Progress = Color(0xFFE50914)
    val ProgressTrack = Color(0x66FFFFFF)
}

/** 10-foot UI metrics. 48dp horizontal / 27dp vertical keeps everything inside the TV overscan safe area. */
object Dimens {
    val SafeHorizontal = 48.dp
    val SafeVertical = 27.dp
    val PosterWidth = 150.dp
    val PosterHeight = 225.dp
    val WidePosterWidth = 260.dp
    val WidePosterHeight = 146.dp
    val RowSpacing = 20.dp
    val FocusScale = 1.08f
}

private val TvTypography = Typography(
    displayLarge = TextStyle(fontSize = 57.sp, lineHeight = 64.sp, fontWeight = FontWeight.Bold),
    displayMedium = TextStyle(fontSize = 45.sp, lineHeight = 52.sp, fontWeight = FontWeight.Bold),
    displaySmall = TextStyle(fontSize = 36.sp, lineHeight = 44.sp, fontWeight = FontWeight.Bold),
    headlineLarge = TextStyle(fontSize = 32.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.SemiBold),
    headlineSmall = TextStyle(fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 18.sp, lineHeight = 26.sp),
    bodyMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
)

private val SamColorScheme = darkColorScheme(
    primary = SamColors.Accent,
    onPrimary = Color.White,
    secondary = SamColors.SurfaceRaised,
    onSecondary = SamColors.TextPrimary,
    background = SamColors.Background,
    onBackground = SamColors.TextPrimary,
    surface = SamColors.Surface,
    onSurface = SamColors.TextPrimary,
    surfaceVariant = SamColors.SurfaceRaised,
    onSurfaceVariant = SamColors.TextSecondary,
    inverseSurface = Color.White,
    inverseOnSurface = Color.Black,
    border = Color.White,
)

@Composable
fun SamOnlineTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = SamColorScheme, typography = TvTypography, content = content)
}
