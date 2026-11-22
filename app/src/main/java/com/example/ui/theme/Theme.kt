package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
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
fun LumaPlayerTheme(
    themeMode: AppThemeMode = AppThemeMode.AMOLED_DARK,
    forceDark: Boolean = false,
    content: @Composable () -> Unit
) {
    val isDark = when {
        forceDark -> true
        themeMode == AppThemeMode.AMOLED_DARK -> true
        themeMode == AppThemeMode.LIGHT -> false
        else -> isSystemInDarkTheme()
    }

    val colorScheme = if (isDark) AmoledDarkColorScheme else CrispLightColorScheme
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
