package com.amvarpvtltd.swiftNote.utils

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Width buckets used by the responsive dimension system.
 *
 * - [COMPACT]  : screen width <  360 dp (small / budget / older phones)
 * - [MEDIUM]   : screen width 360..600 dp (the majority of modern phones)
 * - [EXPANDED] : screen width >  600 dp (large phones landscape, foldables, tablets)
 */
enum class WidthBucket { COMPACT, MEDIUM, EXPANDED }

/**
 * Responsive sizing system. Reads the device's screen width and exposes
 * scaled spacing, typography, and component dimensions.
 *
 * The [MEDIUM] bucket values intentionally match the legacy values in
 * [Constants] so existing phones look identical after migration. [COMPACT]
 * scales values down (~80–90%) and [EXPANDED] scales them up (~110–120%).
 *
 * Usage in any composable:
 * ```
 * val dims = rememberResponsiveDimensions()
 * Spacer(Modifier.height(dims.paddingMedium))
 * ```
 */
data class ResponsiveDimensions(
    val bucket: WidthBucket,
    val screenWidthDp: Int,
    val screenHeightDp: Int,

    // Spacing
    val paddingSmall: Dp,
    val paddingMedium: Dp,
    val paddingLarge: Dp,
    val paddingXL: Dp,

    // Corner radii
    val cornerSmall: Dp,
    val cornerMedium: Dp,
    val cornerLarge: Dp,
    val cornerXL: Dp,

    // Icons
    val iconSmall: Dp,
    val iconMedium: Dp,
    val iconLarge: Dp,

    // Components
    val fabSize: Dp,
    val cardMinHeight: Dp,
    val editorMinHeight: Dp,

    // Typography scale factor (multiply base sp values by this).
    // This is layered on top of the user's system font scale (which Compose
    // already respects via `.sp`) — it is NOT a replacement for it.
    val fontScale: Float,

    // Grid
    val gridColumns: Int,

    // Phase 7: Tablet/foldable content-width cap. On Expanded screens, long-form
    // text becomes uncomfortable to read when it stretches the full width. Apply
    // via [Modifier.responsiveContentWidth] on long-form content (note editors,
    // settings forms, dialogs). [Dp.Unspecified] on Compact/Medium means no cap.
    val contentMaxWidth: Dp
) {
    /** Scale a base sp value for this bucket. */
    fun scaledSp(base: Int): TextUnit = (base * fontScale).sp

    /** Scale a base sp value (float) for this bucket. */
    fun scaledSp(base: Float): TextUnit = (base * fontScale).sp
}

/**
 * Phase 7 helper. Caps content width to [ResponsiveDimensions.contentMaxWidth]
 * and horizontally centers the content within its parent. On Compact/Medium
 * buckets (where the cap is [Dp.Unspecified]) this is a pure no-op — the content
 * still fills the available width.
 *
 * Usage:
 * ```
 * Column(
 *     modifier = Modifier
 *         .fillMaxSize()
 *         .responsiveContentWidth(dims)
 * ) { … }
 * ```
 */
fun Modifier.responsiveContentWidth(dims: ResponsiveDimensions): Modifier {
    return if (dims.contentMaxWidth == Dp.Unspecified) {
        this
    } else {
        this
            .fillMaxWidth()
            .wrapContentWidth(Alignment.CenterHorizontally)
            .widthIn(max = dims.contentMaxWidth)
            .fillMaxWidth()
    }
}

/**
 * Returns a [ResponsiveDimensions] keyed on the current screen width/height.
 * Cheap to call — only recomputes on rotation / window resize.
 */
@Composable
fun rememberResponsiveDimensions(): ResponsiveDimensions {
    val config = LocalConfiguration.current
    val widthDp = config.screenWidthDp
    val heightDp = config.screenHeightDp
    return remember(widthDp, heightDp) {
        responsiveDimensionsFor(widthDp, heightDp)
    }
}

/**
 * Pure-function variant of [rememberResponsiveDimensions] used by unit tests
 * and any non-Composable callers. Selecting the bucket here (instead of inside
 * the Composable) keeps the responsive math testable without a Compose rule.
 */
fun responsiveDimensionsFor(widthDp: Int, heightDp: Int): ResponsiveDimensions {
    val isLandscape = widthDp > heightDp
    val bucket = when {
        widthDp < 360 -> WidthBucket.COMPACT
        widthDp > 600 -> WidthBucket.EXPANDED
        else -> WidthBucket.MEDIUM
    }

    return when (bucket) {
        WidthBucket.COMPACT -> ResponsiveDimensions(
            bucket = bucket,
            screenWidthDp = widthDp,
            screenHeightDp = heightDp,
            paddingSmall = 6.dp,
            paddingMedium = 12.dp,
            paddingLarge = 18.dp,
            paddingXL = 24.dp,
            cornerSmall = 10.dp,
            cornerMedium = 14.dp,
            cornerLarge = 16.dp,
            cornerXL = 20.dp,
            iconSmall = 14.dp,
            iconMedium = 18.dp,
            iconLarge = 20.dp,
            fabSize = 56.dp,
            cardMinHeight = 100.dp,
            editorMinHeight = 140.dp,
            fontScale = 0.9f,
            gridColumns = if (isLandscape) 2 else 1,
            contentMaxWidth = Dp.Unspecified
        )
        WidthBucket.MEDIUM -> ResponsiveDimensions(
            bucket = bucket,
            screenWidthDp = widthDp,
            screenHeightDp = heightDp,
            paddingSmall = 8.dp,
            paddingMedium = 15.dp,
            paddingLarge = 24.dp,
            paddingXL = 32.dp,
            cornerSmall = 12.dp,
            cornerMedium = 16.dp,
            cornerLarge = 20.dp,
            cornerXL = 24.dp,
            iconSmall = 16.dp,
            iconMedium = 20.dp,
            iconLarge = 24.dp,
            fabSize = 64.dp,
            cardMinHeight = 120.dp,
            editorMinHeight = 200.dp,
            fontScale = 1.0f,
            gridColumns = if (isLandscape) 3 else 2,
            contentMaxWidth = Dp.Unspecified
        )
        WidthBucket.EXPANDED -> ResponsiveDimensions(
            bucket = bucket,
            screenWidthDp = widthDp,
            screenHeightDp = heightDp,
            paddingSmall = 10.dp,
            paddingMedium = 20.dp,
            paddingLarge = 32.dp,
            paddingXL = 40.dp,
            cornerSmall = 14.dp,
            cornerMedium = 18.dp,
            cornerLarge = 24.dp,
            cornerXL = 28.dp,
            iconSmall = 18.dp,
            iconMedium = 24.dp,
            iconLarge = 28.dp,
            fabSize = 72.dp,
            cardMinHeight = 140.dp,
            editorMinHeight = 260.dp,
            fontScale = 1.1f,
            gridColumns = if (isLandscape) 4 else 3,
            contentMaxWidth = 720.dp
        )
    }
}




