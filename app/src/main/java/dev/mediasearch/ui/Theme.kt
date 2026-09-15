package dev.mediasearch.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

val LocalThemePreferences = staticCompositionLocalOf<ThemePreferences> {
    error("ThemePreferences is only available below CollectionTheme")
}

@Composable
fun CollectionTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val preferences = remember { ThemePreferences(context) }
    val dark = isSystemInDarkTheme()
    val colors = when {
        preferences.useDynamicColors && Build.VERSION.SDK_INT >= 31 && dark -> dynamicDarkColorScheme(context)
        preferences.useDynamicColors && Build.VERSION.SDK_INT >= 31 -> dynamicLightColorScheme(context)
        dark -> preferences.palette.darkScheme()
        else -> preferences.palette.lightScheme()
    }
    CompositionLocalProvider(LocalThemePreferences provides preferences) {
        MaterialTheme(colorScheme = colors, typography = androidx.compose.material3.Typography(), content = content)
    }
}

private fun ThemePalette.lightScheme(): ColorScheme {
    val base = lightColorScheme(
        primary = lightPrimary,
        onPrimary = Color.White,
        primaryContainer = lightPrimaryContainer,
        onPrimaryContainer = Color(0xFF172018),
        secondary = lightSecondary,
        onSecondary = Color.White,
        secondaryContainer = lightSecondaryContainer,
        onSecondaryContainer = Color(0xFF182018),
        tertiary = lightTertiary,
        onTertiary = Color.White,
        onTertiaryContainer = Color(0xFF102024),
        tertiaryContainer = lightTertiaryContainer,
        background = lightBackground,
        onBackground = Color(0xFF1A1D18),
        surface = lightSurface,
        onSurface = Color(0xFF1A1D18),
        surfaceVariant = lightSurfaceVariant,
        onSurfaceVariant = Color(0xFF424840),
        outline = mix(lightPrimary, Color(0xFF4A4F48), .52f),
        outlineVariant = mix(lightSurfaceVariant, Color(0xFF7A8378), .58f),
        inverseSurface = Color(0xFF2F332D),
        inverseOnSurface = Color(0xFFF0F2E9),
        inversePrimary = lightPrimaryContainer,
        surfaceTint = lightPrimary
    )
    return base.copy(
        surfaceDim = mix(lightBackground, lightPrimaryContainer, .08f),
        surfaceBright = Color.White,
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = mix(lightSurface, lightBackground, .35f),
        surfaceContainer = mix(lightSurface, lightPrimaryContainer, .14f),
        surfaceContainerHigh = mix(lightSurface, lightPrimaryContainer, .22f),
        surfaceContainerHighest = mix(lightSurface, lightPrimaryContainer, .31f)
    )
}

private fun ThemePalette.darkScheme(): ColorScheme {
    val base = darkColorScheme(
        primary = darkPrimary,
        onPrimary = Color(0xFF172018),
        primaryContainer = darkPrimaryContainer,
        onPrimaryContainer = Color(0xFFD8F2C9),
        secondary = darkSecondary,
        onSecondary = Color(0xFF1A211A),
        secondaryContainer = darkSecondaryContainer,
        onSecondaryContainer = Color(0xFFDCE8D2),
        tertiary = darkTertiary,
        onTertiary = Color(0xFF172023),
        tertiaryContainer = darkTertiaryContainer,
        onTertiaryContainer = Color(0xFFE6F6F6),
        background = darkBackground,
        onBackground = Color(0xFFE1E5DB),
        surface = darkSurface,
        onSurface = Color(0xFFE1E5DB),
        surfaceVariant = darkSurfaceVariant,
        onSurfaceVariant = Color(0xFFC2C9BD),
        outline = mix(darkPrimary, Color(0xFF9DA89A), .38f),
        outlineVariant = mix(darkSurfaceVariant, Color(0xFF707A70), .45f),
        inverseSurface = Color(0xFFE1E5DB),
        inverseOnSurface = Color(0xFF2D322C),
        inversePrimary = lightPrimary,
        surfaceTint = darkPrimary
    )
    return base.copy(
        surfaceDim = mix(darkBackground, Color.Black, .12f),
        surfaceBright = mix(darkSurface, Color.White, .14f),
        surfaceContainerLowest = Color.Black,
        surfaceContainerLow = mix(darkSurface, darkBackground, .36f),
        surfaceContainer = mix(darkSurface, darkPrimaryContainer, .16f),
        surfaceContainerHigh = mix(darkSurface, darkPrimaryContainer, .24f),
        surfaceContainerHighest = mix(darkSurface, darkPrimaryContainer, .32f)
    )
}

private fun mix(first: Color, second: Color, amount: Float): Color {
    val t = amount.coerceIn(0f, 1f)
    return Color(
        red = first.red + (second.red - first.red) * t,
        green = first.green + (second.green - first.green) * t,
        blue = first.blue + (second.blue - first.blue) * t,
        alpha = first.alpha + (second.alpha - first.alpha) * t
    )
}
