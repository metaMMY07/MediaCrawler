package dev.mediasearch.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/** The small, local set of appearance options used by the app shell. */
enum class ThemePalette(
    val key: String,
    val label: String,
    val description: String,
    val lightPrimary: Color,
    val lightPrimaryContainer: Color,
    val lightSecondary: Color,
    val lightSecondaryContainer: Color,
    val lightTertiary: Color,
    val lightTertiaryContainer: Color,
    val darkPrimary: Color,
    val darkPrimaryContainer: Color,
    val darkSecondary: Color,
    val darkSecondaryContainer: Color,
    val darkTertiary: Color,
    val darkTertiaryContainer: Color,
    val lightBackground: Color,
    val lightSurface: Color,
    val lightSurfaceVariant: Color,
    val darkBackground: Color,
    val darkSurface: Color,
    val darkSurfaceVariant: Color
) {
    SAGE(
        "sage", "鼠尾草", "柔和、自然的绿色",
        Color(0xFF49653B), Color(0xFFCBE9B8), Color(0xFF586451), Color(0xFFDCE8D2),
        Color(0xFF38656A), Color(0xFFBCEBF0), Color(0xFFB7CC9F), Color(0xFF334B27),
        Color(0xFFBCCAB2), Color(0xFF3E4A38), Color(0xFF9FD0D4), Color(0xFF214D51),
        Color(0xFFF7F9F0), Color(0xFFF7F9F0), Color(0xFFE1E9D9), Color(0xFF11150F),
        Color(0xFF11150F), Color(0xFF424A3E)
    ),
    OCEAN(
        "ocean", "海蓝", "清爽、安静的蓝色",
        Color(0xFF365F91), Color(0xFFD3E4FF), Color(0xFF535F70), Color(0xFFD7E3F7),
        Color(0xFF6B5778), Color(0xFFF2DAFF), Color(0xFFA6C8FF), Color(0xFF174777),
        Color(0xFFBBC7DB), Color(0xFF3B485A), Color(0xFFD7BDE7), Color(0xFF513967),
        Color(0xFFF8F9FF), Color(0xFFF8F9FF), Color(0xFFE0E6F2), Color(0xFF101419),
        Color(0xFF101419), Color(0xFF41474F)
    ),
    CORAL(
        "coral", "珊瑚", "温暖、明亮的橙红色",
        Color(0xFF9C4235), Color(0xFFFFDAD3), Color(0xFF77574F), Color(0xFFFFDBD2),
        Color(0xFF705D2E), Color(0xFFFBE2A8), Color(0xFFFFB4A8), Color(0xFF7E2A20),
        Color(0xFFE6BDB4), Color(0xFF5B4039), Color(0xFFDEC58E), Color(0xFF56461D),
        Color(0xFFFFF8F6), Color(0xFFFFF8F6), Color(0xFFF3DEDA), Color(0xFF1A110F),
        Color(0xFF1A110F), Color(0xFF514340)
    ),
    VIOLET(
        "violet", "紫罗兰", "柔韧、醒目的紫色",
        Color(0xFF675083), Color(0xFFECDCFF), Color(0xFF625B71), Color(0xFFE8DEF8),
        Color(0xFF7D5260), Color(0xFFFFD9E2), Color(0xFFD8B9F3), Color(0xFF4F3869),
        Color(0xFFC9BED2), Color(0xFF484255), Color(0xFFEAB8C5), Color(0xFF633B49),
        Color(0xFFFFF7FF), Color(0xFFFFF7FF), Color(0xFFEEE0F1), Color(0xFF17121B),
        Color(0xFF17121B), Color(0xFF49414C)
    ),
    TEAL(
        "teal", "青绿", "清晰、平衡的青绿色",
        Color(0xFF006A6A), Color(0xFF6FF7F6), Color(0xFF4A6363), Color(0xFFCCE8E7),
        Color(0xFF4F5F7A), Color(0xFFD9E2FF), Color(0xFF4CDADA), Color(0xFF004F50),
        Color(0xFFB0CCCC), Color(0xFF324B4B), Color(0xFFBAC6E3), Color(0xFF384663),
        Color(0xFFF2FAF9), Color(0xFFF2FAF9), Color(0xFFDCE9E8), Color(0xFF0D1414),
        Color(0xFF0D1414), Color(0xFF3D4949)
    );

    companion object {
        fun fromKey(key: String?): ThemePalette =
            entries.firstOrNull { it.key == key } ?: SAGE
    }
}

/**
 * SharedPreferences is intentionally limited to two scalar values. `apply()` keeps
 * changing a swatch off the UI thread while Compose state makes the change immediate.
 */
class ThemePreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    var useDynamicColors by mutableStateOf(preferences.getBoolean(KEY_DYNAMIC, true))
        private set

    var palette by mutableStateOf(ThemePalette.fromKey(preferences.getString(KEY_PALETTE, ThemePalette.SAGE.key)))
        private set

    fun useSystemColors(enabled: Boolean) {
        useDynamicColors = enabled
        preferences.edit().putBoolean(KEY_DYNAMIC, enabled).apply()
    }

    fun choosePalette(value: ThemePalette) {
        palette = value
        useDynamicColors = false
        preferences.edit().putString(KEY_PALETTE, value.key).putBoolean(KEY_DYNAMIC, false).apply()
    }

    private companion object {
        const val FILE_NAME = "collection_appearance"
        const val KEY_DYNAMIC = "use_dynamic_colors"
        const val KEY_PALETTE = "palette"
    }
}
