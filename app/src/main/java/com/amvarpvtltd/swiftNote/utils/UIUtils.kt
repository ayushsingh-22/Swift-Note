package com.amvarpvtltd.swiftNote.utils

import androidx.compose.animation.core.*
import androidx.compose.ui.graphics.Color

object UIUtils {

    /**
     * Calculate progress for character count indicators
     */
    fun calculateProgress(currentLength: Int, maxLength: Int): Float {
        return (currentLength.toFloat() / maxLength.toFloat()).coerceIn(0f, 1f)
    }

    /**
     * Get color based on character count progress
     * Now accepts maxLength so the red/error threshold is compared against the correct field limit.
     */
    fun getProgressColor(currentLength: Int, maxLength: Int): Color {
        return when {
            currentLength < Constants.WARNING_LENGTH -> Color(0xFF64748B) // Gray
            currentLength < Constants.MIN_CONTENT_LENGTH -> Color(0xFFF59E0B) // Warning/Orange
            currentLength >= maxLength -> Color(0xFFEF4444) // Error/Red when current reaches the provided max
            else -> Color(0xFF10B981) // Success/Green
        }
    }

    /**
     * Format character count display
     */
    fun formatCharacterCount(currentLength: Int, maxLength: Int): String {
        return "$currentLength/$maxLength"
    }

    /**
     * Get color animation specification
     */
    fun getColorAnimationSpec(): AnimationSpec<Color> {
        return tween(durationMillis = Constants.COLOR_ANIMATION_DURATION)
    }

    /**
     * Get spring animation specification for UI elements
     */
    fun getSpringAnimationSpec(): AnimationSpec<Float> {
        return spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        )
    }
}
