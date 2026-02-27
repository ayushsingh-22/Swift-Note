package com.amvarpvtltd.swiftNote.design

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

object NoteTheme {
    // Primary colors with improved contrast
    var Primary by mutableStateOf(Color(0xFF6750A4))
    var PrimaryVariant by mutableStateOf(Color(0xFF4F46E5))
    var OnPrimary by mutableStateOf(Color(0xFFFFFFFF))
    var PrimaryContainer by mutableStateOf(Color(0xFFEADDFF))
    var OnPrimaryContainer by mutableStateOf(Color(0xFF21005D))

    // Secondary colors with better visibility
    var Secondary by mutableStateOf(Color(0xFF625B71))
    var SecondaryVariant by mutableStateOf(Color(0xFF0891B2))
    var OnSecondary by mutableStateOf(Color(0xFFFFFFFF))
    var SecondaryContainer by mutableStateOf(Color(0xFFE8DEF8))
    var OnSecondaryContainer by mutableStateOf(Color(0xFF1D192B))

    // Surface colors with improved contrast
    var Surface by mutableStateOf(Color(0xFFFEF7FF))
    var OnSurface by mutableStateOf(Color(0xFF1D1B20))
    var SurfaceVariant by mutableStateOf(Color(0xFFE7E0EC))
    var OnSurfaceVariant by mutableStateOf(Color(0xFF49454F))

    // Background colors
    var Background by mutableStateOf(Color(0xFFFEF7FF))
    var OnBackground by mutableStateOf(Color(0xFF1D1B20))

    // Error colors with better visibility
    var Error by mutableStateOf(Color(0xFFB3261E))
    var OnError by mutableStateOf(Color(0xFFFFFFFF))
    var ErrorContainer by mutableStateOf(Color(0xFFF9DEDC))
    var OnErrorContainer by mutableStateOf(Color(0xFF410E0B))

    // Warning colors with improved contrast
    var Warning by mutableStateOf(Color(0xFFEE8B22))
    var OnWarning by mutableStateOf(Color(0xFFFFFFFF))
    var WarningContainer by mutableStateOf(Color(0xFFFFEDD9))
    var OnWarningContainer by mutableStateOf(Color(0xFF2E1500))

    // Success colors with better visibility
    var Success by mutableStateOf(Color(0xFF146C2E))
    var OnSuccess by mutableStateOf(Color(0xFFFFFFFF))
    var SuccessContainer by mutableStateOf(Color(0xFFAEF0BE))
    var OnSuccessContainer by mutableStateOf(Color(0xFF002108))

    // Outline colors
    var Outline by mutableStateOf(Color(0xFF79747E))
    var OutlineVariant by mutableStateOf(Color(0xFFCAC4D0))

    // Dark theme colors with enhanced contrast
    object Dark {
        var Primary by mutableStateOf(Color(0xFFD0BCFF))
        var OnPrimary by mutableStateOf(Color(0xFF381E72))
        var PrimaryContainer by mutableStateOf(Color(0xFF4F378B))
        var OnPrimaryContainer by mutableStateOf(Color(0xFFEADDFF))

        var Secondary by mutableStateOf(Color(0xFFCCC2DC))
        var OnSecondary by mutableStateOf(Color(0xFF332D41))
        var SecondaryContainer by mutableStateOf(Color(0xFF4A4458))
        var OnSecondaryContainer by mutableStateOf(Color(0xFFE8DEF8))

        var Surface by mutableStateOf(Color(0xFF1C1B1F))
        var OnSurface by mutableStateOf(Color(0xFFE6E1E5))
        var SurfaceVariant by mutableStateOf(Color(0xFF49454F))
        var OnSurfaceVariant by mutableStateOf(Color(0xFFCAC4D0))

        var Background by mutableStateOf(Color(0xFF1C1B1F))
        var OnBackground by mutableStateOf(Color(0xFFE6E1E5))

        var Error by mutableStateOf(Color(0xFFF2B8B5))
        var OnError by mutableStateOf(Color(0xFF601410))
        var ErrorContainer by mutableStateOf(Color(0xFF8C1D18))
        var OnErrorContainer by mutableStateOf(Color(0xFFF9DEDC))

        var Warning by mutableStateOf(Color(0xFFFFB86C))
        var OnWarning by mutableStateOf(Color(0xFF432B00))
        var WarningContainer by mutableStateOf(Color(0xFF5C3F00))
        var OnWarningContainer by mutableStateOf(Color(0xFFFFDDB3))

        var Success by mutableStateOf(Color(0xFF67DD8B))
        var OnSuccess by mutableStateOf(Color(0xFF003912))
        var SuccessContainer by mutableStateOf(Color(0xFF00531C))
        var OnSuccessContainer by mutableStateOf(Color(0xFF89F8A5))

        var Outline by mutableStateOf(Color(0xFF938F99))
        var OutlineVariant by mutableStateOf(Color(0xFF49454F))
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
            // Dark text on light background
            foreground.copy(alpha = 1f).darker(steps = ((minRatio - contrast) * 2).toInt())
        } else {
            // Light text on dark background
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
