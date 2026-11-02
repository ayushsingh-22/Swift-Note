package com.amvarpvtltd.swiftNote.theme

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode {
    LIGHT, DARK, SYSTEM
}

/**
 * Global singleton for app-wide reactive theme state.
 * This is the SINGLE SOURCE OF TRUTH for the current theme.
 * Both MyApp (ProvideNoteTheme) and NotesScreen (toggle button) observe/update this.
 * Using StateFlow ensures all observers react immediately when the theme changes.
 */
object AppThemeState {
    private val _themeMode = MutableStateFlow(ThemeMode.SYSTEM)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    /** Call once at app start to load the saved preference. */
    fun initialize(context: Context) {
        _themeMode.value = ThemeManager.getThemeMode(context)
    }

    /** Toggle/set theme — updates the flow AND persists to SharedPreferences. */
    fun setTheme(context: Context, mode: ThemeMode) {
        _themeMode.value = mode
        ThemeManager.setThemeMode(context, mode)
    }
}

object ThemeManager {
    fun getThemeMode(context: Context): ThemeMode {
        val prefs = context.getSharedPreferences("note_theme_prefs", Context.MODE_PRIVATE)
        val themeName = prefs.getString("theme_mode", "system")
        return when (themeName) {
            "light" -> ThemeMode.LIGHT
            "dark" -> ThemeMode.DARK
            else -> ThemeMode.SYSTEM
        }
    }

    fun setThemeMode(context: Context, themeMode: ThemeMode) {
        val prefs = context.getSharedPreferences("note_theme_prefs", Context.MODE_PRIVATE)
        val themeName = when (themeMode) {
            ThemeMode.LIGHT -> "light"
            ThemeMode.DARK -> "dark"
            ThemeMode.SYSTEM -> "system"
        }
        prefs.edit().putString("theme_mode", themeName).apply()
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

/** Compatibility entry point: every screen now uses the same Material 3 palette. */
@Composable
fun ProvideNoteTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    com.amvarpvtltd.swiftNote.ui.theme.SelfNoteTheme(
        darkTheme = isDarkMode(themeMode),
        content = content
    )
}
