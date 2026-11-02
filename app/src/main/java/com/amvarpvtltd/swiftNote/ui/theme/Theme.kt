package com.amvarpvtltd.swiftNote.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import com.amvarpvtltd.swiftNote.design.NoteTheme
import com.amvarpvtltd.swiftNote.theme.AppThemeState
import com.amvarpvtltd.swiftNote.theme.ThemeMode

val LocalAppTheme = staticCompositionLocalOf { SwiftNoteLightColors }

private val SwiftNoteShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun SelfNoteTheme(
    darkTheme: Boolean? = null,
    // Kept for source compatibility. SwiftNote deliberately keeps its approved brand palette.
    @Suppress("UNUSED_PARAMETER") dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val mode by AppThemeState.themeMode.collectAsState()
    val dark = darkTheme ?: when (mode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val colors = if (dark) SwiftNoteDarkColors else SwiftNoteLightColors
    SideEffect { NoteTheme.applyColors(colors) }
    CompositionLocalProvider(LocalAppTheme provides colors) {
        MaterialTheme(colorScheme = colors, typography = Typography, shapes = SwiftNoteShapes, content = content)
    }
}
