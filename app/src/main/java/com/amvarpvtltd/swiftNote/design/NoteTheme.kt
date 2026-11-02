package com.amvarpvtltd.swiftNote.design

import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import com.amvarpvtltd.swiftNote.ui.theme.SwiftNoteDarkColors
import com.amvarpvtltd.swiftNote.ui.theme.SwiftNoteLightColors
import com.amvarpvtltd.swiftNote.ui.theme.SwiftNoteStatusColors

/** Compatibility facade for existing UI. All colour roles come from the shared app palette. */
object NoteTheme {
    private var colors by mutableStateOf(SwiftNoteLightColors)
    internal fun applyColors(scheme: ColorScheme) { colors = scheme }
    private val dark get() = colors.background.luminance() < .5f
    val Primary get() = colors.primary
    val PrimaryVariant get() = colors.onPrimaryContainer
    val OnPrimary get() = colors.onPrimary
    val PrimaryContainer get() = colors.primaryContainer
    val OnPrimaryContainer get() = colors.onPrimaryContainer
    val Secondary get() = colors.secondary
    val SecondaryVariant get() = colors.onSecondaryContainer
    val OnSecondary get() = colors.onSecondary
    val SecondaryContainer get() = colors.secondaryContainer
    val OnSecondaryContainer get() = colors.onSecondaryContainer
    val Tertiary get() = colors.tertiary
    val OnTertiary get() = colors.onTertiary
    val TertiaryContainer get() = colors.tertiaryContainer
    val OnTertiaryContainer get() = colors.onTertiaryContainer
    val Surface get() = colors.surface
    val OnSurface get() = colors.onSurface
    val SurfaceVariant get() = colors.surfaceVariant
    val OnSurfaceVariant get() = colors.onSurfaceVariant
    val Background get() = colors.background
    val OnBackground get() = colors.onBackground
    val Error get() = colors.error
    val OnError get() = colors.onError
    val ErrorContainer get() = colors.errorContainer
    val OnErrorContainer get() = colors.onErrorContainer
    val Outline get() = colors.outlineVariant
    val OutlineVariant get() = colors.outlineVariant
    val Accent get() = colors.primary
    val AccentBlue get() = colors.primary
    val Success get() = if (dark) SwiftNoteStatusColors.successDark else SwiftNoteStatusColors.success
    val OnSuccess get() = if (dark) SwiftNoteStatusColors.onSuccessContainer else colors.onPrimary
    val SuccessContainer get() = if (dark) SwiftNoteStatusColors.successContainerDark else SwiftNoteStatusColors.successContainer
    val OnSuccessContainer get() = if (dark) SwiftNoteStatusColors.onSuccessContainerDark else SwiftNoteStatusColors.onSuccessContainer
    val Warning get() = if (dark) SwiftNoteStatusColors.warningDark else SwiftNoteStatusColors.warning
    val OnWarning get() = if (dark) SwiftNoteStatusColors.onWarningContainer else colors.onPrimary
    val WarningContainer get() = if (dark) SwiftNoteStatusColors.warningContainerDark else SwiftNoteStatusColors.warningContainer
    val OnWarningContainer get() = if (dark) SwiftNoteStatusColors.onWarningContainerDark else SwiftNoteStatusColors.onWarningContainer

    object Dark { val OnSurface get() = SwiftNoteDarkColors.onSurface }
    val NoteAccentColors get() = listOf(Primary, Tertiary, Secondary)
    fun getNoteAccentColor(index: Int) = NoteAccentColors[Math.floorMod(index, NoteAccentColors.size)]

    object Spacing {
        const val xs = 4
        const val sm = 8
        const val md = 12
        const val base = 16
        const val lg = 20
        const val xl = 24
        const val xxl = 32
        const val xxxl = 48
    }
    object Radius {
        const val sm = 8
        const val md = 12
        const val lg = 18
        const val xl = 24
        const val full = 100
    }
    object Elevation {
        val Card = 1.dp
        val CardHovered = 3.dp
        val Dialog = 6.dp
        val Toolbar = 2.dp
    }
    object Border {
        @Composable fun card() = BorderStroke(1.dp, Outline.copy(alpha = .6f))
        @Composable fun cardFocused() = BorderStroke(1.dp, Primary.copy(alpha = .5f))
    }

    fun ensureContrastRatio(foreground: Color, background: Color, minRatio: Float = 4.5f): Color {
        fun contrast(color: Color): Float {
            val a = color.luminance() + .05f
            val b = background.luminance() + .05f
            return maxOf(a, b) / minOf(a, b)
        }
        if (contrast(foreground) >= minRatio) return foreground
        return if (contrast(OnSurface) >= minRatio) OnSurface
        else if (background.luminance() > .5f) SwiftNoteLightColors.onSurface else SwiftNoteDarkColors.onSurface
    }
}
