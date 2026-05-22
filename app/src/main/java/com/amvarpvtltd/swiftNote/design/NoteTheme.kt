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
    // Primary — Deep Indigo (modern 2025-26 note-app premium feel)
    var Primary by mutableStateOf(Color(0xFF6366F1))
    var PrimaryVariant by mutableStateOf(Color(0xFF4F46E5))
    var OnPrimary by mutableStateOf(Color(0xFFFFFFFF))
    var PrimaryContainer by mutableStateOf(Color(0xFFEEF2FF))
    var OnPrimaryContainer by mutableStateOf(Color(0xFF1E1B4B))

    // Secondary — Slate neutrals with indigo tint
    var Secondary by mutableStateOf(Color(0xFF64748B))
    var SecondaryVariant by mutableStateOf(Color(0xFF475569))
    var OnSecondary by mutableStateOf(Color(0xFFFFFFFF))
    var SecondaryContainer by mutableStateOf(Color(0xFFF1F5F9))
    var OnSecondaryContainer by mutableStateOf(Color(0xFF1E293B))

    // Surface — Ultra-clean white with faint indigo tint on background
    var Surface by mutableStateOf(Color(0xFFFFFFFF))
    var OnSurface by mutableStateOf(Color(0xFF0F172A))
    var SurfaceVariant by mutableStateOf(Color(0xFFF8FAFC))
    var OnSurfaceVariant by mutableStateOf(Color(0xFF64748B))

    // Background — Subtle indigo-washed white
    var Background by mutableStateOf(Color(0xFFF5F7FF))
    var OnBackground by mutableStateOf(Color(0xFF0F172A))

    // Error — Vivid Crimson
    var Error by mutableStateOf(Color(0xFFEF4444))
    var OnError by mutableStateOf(Color(0xFFFFFFFF))
    var ErrorContainer by mutableStateOf(Color(0xFFFEE2E2))
    var OnErrorContainer by mutableStateOf(Color(0xFF7F1D1D))

    // Warning — Warm Amber
    var Warning by mutableStateOf(Color(0xFFF59E0B))
    var OnWarning by mutableStateOf(Color(0xFFFFFFFF))
    var WarningContainer by mutableStateOf(Color(0xFFFEF3C7))
    var OnWarningContainer by mutableStateOf(Color(0xFF78350F))

    // Success — Vibrant Emerald
    var Success by mutableStateOf(Color(0xFF10B981))
    var OnSuccess by mutableStateOf(Color(0xFFFFFFFF))
    var SuccessContainer by mutableStateOf(Color(0xFFD1FAE5))
    var OnSuccessContainer by mutableStateOf(Color(0xFF064E3B))

    // Outline — Light slate border
    var Outline by mutableStateOf(Color(0xFFE2E8F0))
    var OutlineVariant by mutableStateOf(Color(0xFFCBD5E1))

    // ─── Premium Palette: Dark Theme ────────────────────────────────────
    object Dark {
        var Primary by mutableStateOf(Color(0xFF818CF8))
        var OnPrimary by mutableStateOf(Color(0xFF1E1B4B))
        var PrimaryContainer by mutableStateOf(Color(0xFF3730A3))
        var OnPrimaryContainer by mutableStateOf(Color(0xFFE0E7FF))

        var Secondary by mutableStateOf(Color(0xFF94A3B8))
        var OnSecondary by mutableStateOf(Color(0xFF1E293B))
        var SecondaryContainer by mutableStateOf(Color(0xFF1E2D3D))
        var OnSecondaryContainer by mutableStateOf(Color(0xFFCBD5E1))

        var Surface by mutableStateOf(Color(0xFF161B22))
        var OnSurface by mutableStateOf(Color(0xFFECF0F7))
        var SurfaceVariant by mutableStateOf(Color(0xFF21262D))
        var OnSurfaceVariant by mutableStateOf(Color(0xFF8B949E))

        var Background by mutableStateOf(Color(0xFF0D1117))
        var OnBackground by mutableStateOf(Color(0xFFECF0F7))

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

        var Outline by mutableStateOf(Color(0xFF30363D))
        var OutlineVariant by mutableStateOf(Color(0xFF3C4450))
    }

    // ─── Note Card Accent Rotation ──────────────────────────────────────
    val NoteAccentColors = listOf(
        Color(0xFF6366F1), // Indigo
        Color(0xFF8B5CF6), // Violet
        Color(0xFF06B6D4), // Cyan
        Color(0xFF10B981), // Emerald
        Color(0xFFF59E0B), // Amber
        Color(0xFFEF4444), // Rose
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
