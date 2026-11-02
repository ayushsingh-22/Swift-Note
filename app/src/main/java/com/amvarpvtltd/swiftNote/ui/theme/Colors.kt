package com.amvarpvtltd.swiftNote.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Approved welcome-tour palette, shared by every screen and by the legacy NoteTheme facade.
val SwiftNoteLightColors = lightColorScheme(
    primary = Color(0xFF7050CE), onPrimary = Color(0xFFFCFAFF),
    primaryContainer = Color(0xFFECE5FA), onPrimaryContainer = Color(0xFF4A3285),
    secondary = Color(0xFF686170), onSecondary = Color(0xFFFEFCFF),
    secondaryContainer = Color(0xFFF1EDF6), onSecondaryContainer = Color(0xFF272230),
    tertiary = Color(0xFF7050CE), onTertiary = Color(0xFFFCFAFF),
    tertiaryContainer = Color(0xFFECE5FA), onTertiaryContainer = Color(0xFF4A3285),
    background = Color(0xFFFAF8FD), onBackground = Color(0xFF272230),
    surface = Color(0xFFFEFCFF), onSurface = Color(0xFF272230),
    surfaceVariant = Color(0xFFF1EDF6), onSurfaceVariant = Color(0xFF686170),
    surfaceDim = Color(0xFFF1EDF6), surfaceBright = Color(0xFFFEFCFF),
    surfaceContainerLowest = Color(0xFFFEFCFF), surfaceContainerLow = Color(0xFFFAF8FD),
    surfaceContainer = Color(0xFFF1EDF6), surfaceContainerHigh = Color(0xFFECE5FA),
    surfaceContainerHighest = Color(0xFFECE5FA),
    outline = Color(0xFF8B8198), outlineVariant = Color(0xFFDED7E8),
    error = Color(0xFFB3261E), onError = Color(0xFFFFFBFE),
    errorContainer = Color(0xFFF9DEDC), onErrorContainer = Color(0xFF410E0B)
)
val SwiftNoteDarkColors = darkColorScheme(
    primary = Color(0xFFC5ADFF), onPrimary = Color(0xFF30204F),
    primaryContainer = Color(0xFF362A4D), onPrimaryContainer = Color(0xFFE2D4FF),
    secondary = Color(0xFFC1B8CC), onSecondary = Color(0xFF25212D),
    secondaryContainer = Color(0xFF302A39), onSecondaryContainer = Color(0xFFF0EAF6),
    tertiary = Color(0xFFC5ADFF), onTertiary = Color(0xFF30204F),
    tertiaryContainer = Color(0xFF362A4D), onTertiaryContainer = Color(0xFFE2D4FF),
    background = Color(0xFF19171E), onBackground = Color(0xFFF0EAF6),
    surface = Color(0xFF25212D), onSurface = Color(0xFFF0EAF6),
    surfaceVariant = Color(0xFF302A39), onSurfaceVariant = Color(0xFFC1B8CC),
    surfaceDim = Color(0xFF19171E), surfaceBright = Color(0xFF302A39),
    surfaceContainerLowest = Color(0xFF19171E), surfaceContainerLow = Color(0xFF25212D),
    surfaceContainer = Color(0xFF25212D), surfaceContainerHigh = Color(0xFF302A39),
    surfaceContainerHighest = Color(0xFF362A4D),
    outline = Color(0xFF94899F), outlineVariant = Color(0xFF494151),
    error = Color(0xFFF2B8B5), onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18), onErrorContainer = Color(0xFFF9DEDC)
)

// Existing semantic colours remain reserved for errors, overdue warnings and success feedback.
internal object SwiftNoteStatusColors {
    val success = Color(0xFF064E3B)
    val successDark = Color(0xFF34D399)
    val successContainer = Color(0xFFD1FAE5)
    val successContainerDark = Color(0xFF065F46)
    val onSuccessContainer = Color(0xFF064E3B)
    val onSuccessContainerDark = Color(0xFFA7F3D0)
    val warning = Color(0xFF78350F)
    val warningDark = Color(0xFFFBBF24)
    val warningContainer = Color(0xFFFEF3C7)
    val warningContainerDark = Color(0xFF78350F)
    val onWarningContainer = Color(0xFF78350F)
    val onWarningContainerDark = Color(0xFFFEF3C7)
}
