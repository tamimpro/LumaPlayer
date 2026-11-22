package com.example.ui.theme

import android.app.Activity
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.example.data.model.AppThemeMode

private val AmoledDarkColorScheme = darkColorScheme(
    primary = CinemaAmber,
    onPrimary = Color(0xFF1F1200),
    primaryContainer = CinemaAmberContainerDark,
    onPrimaryContainer = CinemaAmberSoft,
    secondary = ChampagneGold,
    onSecondary = Color(0xFF1F1406),
    secondaryContainer = ChampagneContainerDark,
    onSecondaryContainer = Color(0xFFF3D5AE),
    tertiary = TimelineIceCyan,
    onTertiary = Color(0xFF001F26),
    background = AmoledBlack,
    onBackground = WarmIvory,
    surface = AmoledBlack,
    onSurface = WarmIvory,
    surfaceVariant = WarmObsidianContainer,
    onSurfaceVariant = WarmStoneSecondary,
    surfaceContainerLowest = AmoledBlack,
    surfaceContainerLow = WarmObsidianLow,
    surfaceContainer = WarmObsidianContainer,
    surfaceContainerHigh = WarmObsidianHigh,
    surfaceContainerHighest = WarmObsidianHighest,
    outline = WarmBorder,
    outlineVariant = WarmBorderSubtle,
    error = ErrorRed,
    onError = Color.White
)

private val CrispLightColorScheme = lightColorScheme(
    primary = LightPrimaryAmber,
    onPrimary = Color.White,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = Color(0xFF3E2000),
    secondary = LightSecondaryGold,
    onSecondary = Color.White,
    secondaryContainer = LightSecondaryContainer,
    onSecondaryContainer = Color(0xFF331E09),
    tertiary = Color(0xFF007B8F),
    onTertiary = Color.White,
    background = LightCanvas,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnBackground,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    surfaceContainerLowest = LightSurface,
    surfaceContainerLow = Color(0xFFF7F5F0),
    surfaceContainer = LightSurfaceVariant,
    surfaceContainerHigh = LightSurfaceHigh,
    surfaceContainerHighest = Color(0xFFDCD6CA),
    outline = LightBorder,
    outlineVariant = Color(0xFFE6E0D6),
    error = ErrorRed,
    onError = Color.White
)

val LumaShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
private fun animateSchemeColor(target: Color, label: String): Color {
    val animated by animateColorAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = LumaMotion.THEME_CROSSFADE_MS),
        label = label
    )
    return animated
}

@Composable
private fun rememberAnimatedColorScheme(target: ColorScheme, animate: Boolean): ColorScheme {
    if (!animate) return target
    return target.copy(
        primary = animateSchemeColor(target.primary, "primary"),
        primaryContainer = animateSchemeColor(target.primaryContainer, "primaryContainer"),
        background = animateSchemeColor(target.background, "background"),
        onBackground = animateSchemeColor(target.onBackground, "onBackground"),
        surface = animateSchemeColor(target.surface, "surface"),
        onSurface = animateSchemeColor(target.onSurface, "onSurface"),
        surfaceContainerLow = animateSchemeColor(target.surfaceContainerLow, "surfaceContainerLow"),
        surfaceContainer = animateSchemeColor(target.surfaceContainer, "surfaceContainer"),
        surfaceContainerHigh = animateSchemeColor(target.surfaceContainerHigh, "surfaceContainerHigh"),
        outlineVariant = animateSchemeColor(target.outlineVariant, "outlineVariant")
    )
}

@Composable
fun LumaPlayerTheme(
    themeMode: AppThemeMode = AppThemeMode.LIGHT,
    forceDark: Boolean = false,
    content: @Composable () -> Unit
) {
    val isDark = when {
        forceDark -> true
        themeMode == AppThemeMode.AMOLED_DARK -> true
        themeMode == AppThemeMode.LIGHT -> false
        else -> isSystemInDarkTheme()
    }

    val targetScheme = if (isDark) AmoledDarkColorScheme else CrispLightColorScheme
    val colorScheme = rememberAnimatedColorScheme(target = targetScheme, animate = !forceDark)

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !isDark
                controller.isAppearanceLightNavigationBars = !isDark
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = LumaTypography,
        shapes = LumaShapes,
        content = content
    )
}
