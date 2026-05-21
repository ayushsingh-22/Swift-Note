package com.amvarpvtltd.swiftNote.components

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.amvarpvtltd.swiftNote.design.NoteTheme

/**
 * Premium background provider — subtle, barely-there vertical gradients.
 * No oversaturated gradients. Warm-mist to snow-canvas transitions only.
 */
object BackgroundProvider {
    private var chosenIndex: Int? = null

    // Palettes are functions so they read NoteTheme values at call-time (theme-aware)
    private val palettes: List<() -> List<Color>> = listOf(
        { listOf(NoteTheme.Background, NoteTheme.Background) }, // Pure clean canvas
        { listOf(NoteTheme.Background, NoteTheme.SurfaceVariant.copy(alpha = 0.15f)) }, // Subtle warm mist fade
        { listOf(NoteTheme.SurfaceVariant.copy(alpha = 0.08f), NoteTheme.Background) }, // Light top-down whisper
    )

    fun getBrush(): Brush {
        var index = chosenIndex
        if (index == null) {
            index = 0 // Default to clean canvas for premium feel
            chosenIndex = index
        }
        val safeIndex = index.coerceIn(0, palettes.lastIndex)
        val colors = palettes[safeIndex].invoke()
        return Brush.verticalGradient(colors = colors)
    }
}
