package com.amvarpvtltd.swiftNote.design

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

object NoteTheme {
    // ─── Premium Palette: Light Theme ───────────────────────────────────
    // Primary accent — Teal (controlled saturation, no purple/neon)
    var Primary by mutableStateOf(Color(0xFF0D9488))
    var PrimaryVariant by mutableStateOf(Color(0xFF0F766E))
    var OnPrimary by mutableStateOf(Color(0xFFFFFFFF))
    var PrimaryContainer by mutableStateOf(Color(0xFFCCFBF1))
    var OnPrimaryContainer by mutableStateOf(Color(0xFF042F2E))

    // Secondary — Slate neutrals for UI chrome
    var Secondary by mutableStateOf(Color(0xFF4A5568))
    var SecondaryVariant by mutableStateOf(Color(0xFF334155))
    var OnSecondary by mutableStateOf(Color(0xFFFFFFFF))
    var SecondaryContainer by mutableStateOf(Color(0xFFF1F5F9))
    var OnSecondaryContainer by mutableStateOf(Color(0xFF1E293B))

    // Surface — Snow Canvas / Pure Surface
    var Surface by mutableStateOf(Color(0xFFFFFFFF))
    var OnSurface by mutableStateOf(Color(0xFF1A1D23))
    var SurfaceVariant by mutableStateOf(Color(0xFFF8F9FA))
    var OnSurfaceVariant by mutableStateOf(Color(0xFF4A5568))

    // Background — Snow Canvas
    var Background by mutableStateOf(Color(0xFFFAFBFC))
    var OnBackground by mutableStateOf(Color(0xFF1A1D23))

    // Error — Danger Crimson
    var Error by mutableStateOf(Color(0xFFDC2626))
    var OnError by mutableStateOf(Color(0xFFFFFFFF))
    var ErrorContainer by mutableStateOf(Color(0xFFFEE2E2))
    var OnErrorContainer by mutableStateOf(Color(0xFF7F1D1D))

    // Warning — Caution Amber
    var Warning by mutableStateOf(Color(0xFFD97706))
    var OnWarning by mutableStateOf(Color(0xFFFFFFFF))
    var WarningContainer by mutableStateOf(Color(0xFFFEF3C7))
    var OnWarningContainer by mutableStateOf(Color(0xFF78350F))

    // Success — Verdant Green
    var Success by mutableStateOf(Color(0xFF059669))
    var OnSuccess by mutableStateOf(Color(0xFFFFFFFF))
    var SuccessContainer by mutableStateOf(Color(0xFFD1FAE5))
    var OnSuccessContainer by mutableStateOf(Color(0xFF064E3B))

    // Outline — Whisper Border
    var Outline by mutableStateOf(Color(0xFFE2E8F0))
    var OutlineVariant by mutableStateOf(Color(0xFFCBD5E1))

    // ─── Premium Palette: Dark Theme ────────────────────────────────────
    object Dark {
        var Primary by mutableStateOf(Color(0xFF2DD4BF))
        var OnPrimary by mutableStateOf(Color(0xFF042F2E))
        var PrimaryContainer by mutableStateOf(Color(0xFF0F766E))
        var OnPrimaryContainer by mutableStateOf(Color(0xFFCCFBF1))

        var Secondary by mutableStateOf(Color(0xFF94A3B8))
        var OnSecondary by mutableStateOf(Color(0xFF1E293B))
        var SecondaryContainer by mutableStateOf(Color(0xFF334155))
        var OnSecondaryContainer by mutableStateOf(Color(0xFFF1F5F9))

        var Surface by mutableStateOf(Color(0xFF1C2128))
        var OnSurface by mutableStateOf(Color(0xFFF0F4F8))
        var SurfaceVariant by mutableStateOf(Color(0xFF282E36))
        var OnSurfaceVariant by mutableStateOf(Color(0xFF94A3B8))

        var Background by mutableStateOf(Color(0xFF0F1419))
        var OnBackground by mutableStateOf(Color(0xFFF0F4F8))

        var Error by mutableStateOf(Color(0xFFF87171))
        var OnError by mutableStateOf(Color(0xFF7F1D1D))
        var ErrorContainer by mutableStateOf(Color(0xFF991B1B))
        var OnErrorContainer by mutableStateOf(Color(0xFFFECACA))

        var Warning by mutableStateOf(Color(0xFFFBBF24))
        var OnWarning by mutableStateOf(Color(0xFF78350F))
        var WarningContainer by mutableStateOf(Color(0xFF92400E))
        var OnWarningContainer by mutableStateOf(Color(0xFFFEF3C7))

        var Success by mutableStateOf(Color(0xFF34D399))
        var OnSuccess by mutableStateOf(Color(0xFF064E3B))
        var SuccessContainer by mutableStateOf(Color(0xFF065F46))
        var OnSuccessContainer by mutableStateOf(Color(0xFFA7F3D0))

        var Outline by mutableStateOf(Color(0xFF334155))
        var OutlineVariant by mutableStateOf(Color(0xFF475569))
    }

    // ─── Note Card Accent Rotation ──────────────────────────────────────
    val NoteAccentColors = listOf(
        Color(0xFF0D9488), // Teal
        Color(0xFFD97706), // Amber
        Color(0xFFE11D48), // Rose
        Color(0xFF4F46E5), // Indigo
        Color(0xFF059669), // Emerald
        Color(0xFF0284C7), // Sky
    )

    fun getNoteAccentColor(index: Int): Color {
        return NoteAccentColors[index % NoteAccentColors.size]
    }

    // ─── Spacing Scale (4dp base) ───────────────────────────────────────
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

    // ─── Corner Radii ───────────────────────────────────────────────────
    object Radius {
        const val sm = 8
        const val md = 12
        const val lg = 16
        const val xl = 24
        const val full = 100
    }

    // Update colors based on the current theme
    @Composable
    fun updateColors(isDark: Boolean) {
        val colors = MaterialTheme.colorScheme
        if (isDark) {
            Primary = Dark.Primary
            OnPrimary = Dark.OnPrimary
            PrimaryContainer = Dark.PrimaryContainer
            OnPrimaryContainer = Dark.OnPrimaryContainer

            Secondary = Dark.Secondary
            OnSecondary = Dark.OnSecondary
            SecondaryContainer = Dark.SecondaryContainer
            OnSecondaryContainer = Dark.OnSecondaryContainer

            Surface = Dark.Surface
            OnSurface = Dark.OnSurface
            SurfaceVariant = Dark.SurfaceVariant
            OnSurfaceVariant = Dark.OnSurfaceVariant

            Background = Dark.Background
            OnBackground = Dark.OnBackground

            Error = Dark.Error
            OnError = Dark.OnError
            ErrorContainer = Dark.ErrorContainer
            OnErrorContainer = Dark.OnErrorContainer

            Warning = Dark.Warning
            OnWarning = Dark.OnWarning
            WarningContainer = Dark.WarningContainer
            OnWarningContainer = Dark.OnWarningContainer

            Success = Dark.Success
            OnSuccess = Dark.OnSuccess
            SuccessContainer = Dark.SuccessContainer
            OnSuccessContainer = Dark.OnSuccessContainer

            Outline = Dark.Outline
            OutlineVariant = Dark.OutlineVariant
        }
    }

    // Helper function to ensure proper contrast ratio
    fun ensureContrastRatio(foreground: Color, background: Color, minRatio: Float = 4.5f): Color {
        val contrast = getContrastRatio(foreground, background)
        if (contrast >= minRatio) return foreground

        return if (background.luminance() > 0.5f) {
            foreground.copy(alpha = 1f).darker(steps = ((minRatio - contrast) * 2).toInt())
        } else {
            foreground.copy(alpha = 1f).lighter(steps = ((minRatio - contrast) * 2).toInt())
        }
    }

    private fun getContrastRatio(color1: Color, color2: Color): Float {
        val l1 = color1.luminance() + 0.05f
        val l2 = color2.luminance() + 0.05f
        return if (l1 > l2) l1 / l2 else l2 / l1
    }

    private fun Color.lighter(steps: Int = 1): Color {
        var result = this
        repeat(steps) {
            result = Color(
                red = (result.red + (1f - result.red) * 0.1f).coerceIn(0f, 1f),
                green = (result.green + (1f - result.green) * 0.1f).coerceIn(0f, 1f),
                blue = (result.blue + (1f - result.blue) * 0.1f).coerceIn(0f, 1f),
                alpha = result.alpha
            )
        }
        return result
    }

    private fun Color.darker(steps: Int = 1): Color {
        var result = this
        repeat(steps) {
            result = Color(
                red = (result.red * 0.9f).coerceIn(0f, 1f),
                green = (result.green * 0.9f).coerceIn(0f, 1f),
                blue = (result.blue * 0.9f).coerceIn(0f, 1f),
                alpha = result.alpha
            )
        }
        return result
    }
}
