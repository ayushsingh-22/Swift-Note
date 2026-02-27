package com.amvarpvtltd.swiftNote.theme

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.amvarpvtltd.swiftNote.utils.Constants

enum class ThemeMode {
    LIGHT, DARK, SYSTEM
}

object ThemeManager {
    fun getThemeMode(context: Context): ThemeMode {
        val prefs = context.getSharedPreferences(Constants.THEME_PREFERENCES, Context.MODE_PRIVATE)
        val themeName = prefs.getString(Constants.THEME_MODE_KEY, Constants.DEFAULT_THEME_MODE)
        return when (themeName) {
            "light" -> ThemeMode.LIGHT
            "dark" -> ThemeMode.DARK
            else -> ThemeMode.SYSTEM
        }
    }

    fun setThemeMode(context: Context, themeMode: ThemeMode) {
        val prefs = context.getSharedPreferences(Constants.THEME_PREFERENCES, Context.MODE_PRIVATE)
        val themeName = when (themeMode) {
            ThemeMode.LIGHT -> "light"
            ThemeMode.DARK -> "dark"
            ThemeMode.SYSTEM -> "system"
        }
        prefs.edit().putString(Constants.THEME_MODE_KEY, themeName).apply()
    }
}

@Composable
fun rememberThemeState(): MutableState<ThemeMode> {
    val context = LocalContext.current
    return remember {
        mutableStateOf(ThemeManager.getThemeMode(context))
    }
}

@Composable
fun isDarkMode(themeMode: ThemeMode): Boolean {
    return when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
}

// Enhanced NoteTheme with dynamic dark/light mode support
object DynamicNoteTheme {
    @Composable
    fun colors(isDark: Boolean) = if (isDark) {
        DarkColors
    } else {
        LightColors
    }

    private val LightColors = NoteThemeColors(
        primary = Color(0xFF6366F1),
        primaryVariant = Color(0xFF4F46E5),
        secondary = Color(0xFF06B6D4),
        secondaryVariant = Color(0xFF0891B2),
        background = Color(0xFFFAFAFA),
        surface = Color(0xFFFFFFFF),
        surfaceVariant = Color(0xFFF1F5F9),
        onPrimary = Color(0xFFFFFFFF),
        onBackground = Color(0xFF1E293B),
        onSurface = Color(0xFF334155),
        onSurfaceVariant = Color(0xFF64748B),
        success = Color(0xFF10B981),
        warning = Color(0xFFF59E0B),
        error = Color(0xFFEF4444),
        errorContainer = Color(0xFFFEE2E2)
    )

    private val DarkColors = NoteThemeColors(
        primary = Color(0xFF818CF8),
        primaryVariant = Color(0xFF6366F1),
        secondary = Color(0xFF22D3EE),
        secondaryVariant = Color(0xFF06B6D4),
        background = Color(0xFF0A0F1A), // Much darker background
        surface = Color(0xFF1A1F2E), // Darker surface
        surfaceVariant = Color(0xFF2A2F3E), // Darker surface variant
        onPrimary = Color(0xFF0F172A),
        onBackground = Color(0xFFF8FAFC), // Higher contrast text
        onSurface = Color(0xFFF1F5F9), // Higher contrast surface text
        onSurfaceVariant = Color(0xFFE2E8F0), // Better contrast for secondary text
        success = Color(0xFF34D399),
        warning = Color(0xFFFBBF24),
        error = Color(0xFFF87171),
        errorContainer = Color(0xFF7F1D1D)
    )
}

data class NoteThemeColors(
    val primary: Color,
    val primaryVariant: Color,
    val secondary: Color,
    val secondaryVariant: Color,
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val onPrimary: Color,
    val onBackground: Color,
    val onSurface: Color,
    val onSurfaceVariant: Color,
    val success: Color,
    val warning: Color,
    val error: Color,
    val errorContainer: Color
)

// Composable function to provide theme colors
@Composable
fun ProvideNoteTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val isDark = isDarkMode(themeMode)
    val colors = DynamicNoteTheme.colors(isDark)

    // Update the existing NoteTheme object
    LaunchedEffect(colors) {
        with(colors) {
            com.amvarpvtltd.swiftNote.design.NoteTheme.Primary = primary
            com.amvarpvtltd.swiftNote.design.NoteTheme.PrimaryVariant = primaryVariant
            com.amvarpvtltd.swiftNote.design.NoteTheme.Secondary = secondary
            com.amvarpvtltd.swiftNote.design.NoteTheme.SecondaryVariant = secondaryVariant
            com.amvarpvtltd.swiftNote.design.NoteTheme.Background = background
            com.amvarpvtltd.swiftNote.design.NoteTheme.Surface = surface
            com.amvarpvtltd.swiftNote.design.NoteTheme.SurfaceVariant = surfaceVariant
            com.amvarpvtltd.swiftNote.design.NoteTheme.OnPrimary = onPrimary
            com.amvarpvtltd.swiftNote.design.NoteTheme.OnBackground = onBackground
            com.amvarpvtltd.swiftNote.design.NoteTheme.OnSurface = onSurface
            com.amvarpvtltd.swiftNote.design.NoteTheme.OnSurfaceVariant = onSurfaceVariant
            com.amvarpvtltd.swiftNote.design.NoteTheme.Success = success
            com.amvarpvtltd.swiftNote.design.NoteTheme.Warning = warning
            com.amvarpvtltd.swiftNote.design.NoteTheme.Error = error
            com.amvarpvtltd.swiftNote.design.NoteTheme.ErrorContainer = errorContainer
        }
    }

    content()
}
