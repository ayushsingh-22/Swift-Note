package com.amvarpvtltd.swiftNote.components

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.amvarpvtltd.swiftNote.design.NoteTheme
import java.security.SecureRandom
object BackgroundProvider {
    private var chosenIndex: Int? = null

    private val random = SecureRandom()

    // Palettes are functions so they read NoteTheme values at call-time (theme-aware)
    private val palettes: List<() -> List<Color>> = listOf(
        { listOf(NoteTheme.Background, NoteTheme.SurfaceVariant.copy(alpha = 0.3f), NoteTheme.Background) },
        { listOf(NoteTheme.PrimaryContainer.copy(alpha = 0.12f), NoteTheme.Primary.copy(alpha = 0.06f), NoteTheme.Background) },
        { listOf(NoteTheme.Surface.copy(alpha = 0.06f), NoteTheme.PrimaryContainer.copy(alpha = 0.15f), NoteTheme.Background) },
        { listOf(NoteTheme.Background, NoteTheme.Primary.copy(alpha = 0.06f), NoteTheme.SurfaceVariant.copy(alpha = 0.2f)) }
    )

    fun getBrush(): Brush {
        if (chosenIndex == null) {
            chosenIndex = random.nextInt(palettes.size)
        }
        val colors = palettes[chosenIndex!!].invoke()
        return Brush.verticalGradient(colors = colors)
    }
}

