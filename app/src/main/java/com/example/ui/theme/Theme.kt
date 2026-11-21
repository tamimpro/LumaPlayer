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
    primary = LuminousCyan,
    onPrimary = Color(0xFF001F26),
    primaryContainer = CyanContainerDark,
    onPrimaryContainer = Color(0xFF8EF4FF),
    secondary = ElectricBlue,
    onSecondary = Color.White,
    secondaryContainer = BlueContainerDark,
    onSecondaryContainer = Color(0xFFBDD8FF),
    tertiary = HdrGold,
    onTertiary = Color(0xFF001F2E),
    background = AmoledBlack,
    onBackground = CrispWhite,
    surface = AmoledBlack,
    onSurface = CrispWhite,
    surfaceVariant = ObsidianContainer,
    onSurfaceVariant = SlateSecondary,
    surfaceContainerLowest = AmoledBlack,
    surfaceContainerLow = ObsidianSurface,
    surfaceContainer = ObsidianContainer,
    surfaceContainerHigh = ObsidianContainerHigh,
    surfaceContainerHighest = Color(0xFF1B273D),
    outline = ObsidianBorder,
    outlineVariant = Color(0xFF151E2E),
    error = ErrorRed,
    onError = Color.White
)

private val CrispLightColorScheme = lightColorScheme(
    primary = LightPrimaryBlue,
    onPrimary = Color.White,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = Color(0xFF002D5E),
    secondary = LightSecondaryCyan,
    onSecondary = Color.White,
    secondaryContainer = LightSecondaryContainer,
    onSecondaryContainer = Color(0xFF00363D),
    tertiary = ElectricBlue,
    onTertiary = Color.White,
    background = LightCanvas,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnBackground,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    surfaceContainerLowest = LightSurface,
    surfaceContainerLow = Color(0xFFF8FAFC),
    surfaceContainer = LightSurfaceVariant,
    surfaceContainerHigh = LightSurfaceHigh,
    surfaceContainerHighest = Color(0xFFD8E1EC),
    outline = LightBorder,
    outlineVariant = Color(0xFFE2E8F0),
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
