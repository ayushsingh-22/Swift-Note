package com.amvarpvtltd.swiftNote.utils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
class ResponsiveDimensionsTest {
    // Bucket boundaries
    @Test fun width_320_is_COMPACT() {
        assertEquals(WidthBucket.COMPACT, responsiveDimensionsFor(320, 640).bucket)
    }
    @Test fun width_359_is_COMPACT_just_below_boundary() {
        assertEquals(WidthBucket.COMPACT, responsiveDimensionsFor(359, 800).bucket)
    }
    @Test fun width_360_is_MEDIUM_inclusive_lower_boundary() {
        assertEquals(WidthBucket.MEDIUM, responsiveDimensionsFor(360, 800).bucket)
    }
    @Test fun width_393_is_MEDIUM_pixel_4a() {
        assertEquals(WidthBucket.MEDIUM, responsiveDimensionsFor(393, 851).bucket)
    }
    @Test fun width_412_is_MEDIUM_pixel_8_pro() {
        assertEquals(WidthBucket.MEDIUM, responsiveDimensionsFor(412, 915).bucket)
    }
    @Test fun width_600_is_MEDIUM_inclusive_upper_boundary() {
        assertEquals(WidthBucket.MEDIUM, responsiveDimensionsFor(600, 800).bucket)
    }
    @Test fun width_601_is_EXPANDED_just_above_boundary() {
        assertEquals(WidthBucket.EXPANDED, responsiveDimensionsFor(601, 800).bucket)
    }
    @Test fun width_800_is_EXPANDED_tablet() {
        assertEquals(WidthBucket.EXPANDED, responsiveDimensionsFor(800, 1280).bucket)
    }
    // MEDIUM mirrors legacy Constants (migration safety)
    @Test fun medium_padding_matches_legacy_constants() {
        val d = responsiveDimensionsFor(393, 851)
        assertEquals(Constants.PADDING_SMALL.toFloat(),  d.paddingSmall.value)
        assertEquals(Constants.PADDING_MEDIUM.toFloat(), d.paddingMedium.value)
        assertEquals(Constants.PADDING_LARGE.toFloat(),  d.paddingLarge.value)
        assertEquals(Constants.PADDING_XL.toFloat(),     d.paddingXL.value)
    }
    @Test fun medium_corner_radii_match_legacy_constants() {
        val d = responsiveDimensionsFor(393, 851)
        assertEquals(Constants.CORNER_RADIUS_SMALL.toFloat(),  d.cornerSmall.value)
        assertEquals(Constants.CORNER_RADIUS_MEDIUM.toFloat(), d.cornerMedium.value)
        assertEquals(Constants.CORNER_RADIUS_LARGE.toFloat(),  d.cornerLarge.value)
        assertEquals(Constants.CORNER_RADIUS_XL.toFloat(),     d.cornerXL.value)
    }
    @Test fun medium_icon_sizes_match_legacy_constants() {
        val d = responsiveDimensionsFor(393, 851)
        assertEquals(Constants.ICON_SIZE_SMALL.toFloat(),  d.iconSmall.value)
        assertEquals(Constants.ICON_SIZE_MEDIUM.toFloat(), d.iconMedium.value)
        assertEquals(Constants.ICON_SIZE_LARGE.toFloat(),  d.iconLarge.value)
    }
    @Test fun medium_fab_size_matches_legacy_constants() {
        val d = responsiveDimensionsFor(393, 851)
        assertEquals(Constants.FAB_SIZE.toFloat(), d.fabSize.value)
    }
    @Test fun medium_font_scale_is_identity() {
        assertEquals(1.0f, responsiveDimensionsFor(393, 851).fontScale)
    }
    // Monotonic scaling
    @Test fun padding_scales_monotonically_across_buckets() {
        val c = responsiveDimensionsFor(320, 640)
        val m = responsiveDimensionsFor(393, 851)
        val e = responsiveDimensionsFor(800, 1280)
        assertTrue(c.paddingMedium.value < m.paddingMedium.value)
        assertTrue(m.paddingMedium.value < e.paddingMedium.value)
        assertTrue(c.paddingLarge.value  < m.paddingLarge.value)
        assertTrue(m.paddingLarge.value  < e.paddingLarge.value)
    }
    @Test fun fab_size_scales_monotonically_across_buckets() {
        val c = responsiveDimensionsFor(320, 640).fabSize.value
        val m = responsiveDimensionsFor(393, 851).fabSize.value
        val e = responsiveDimensionsFor(800, 1280).fabSize.value
        assertTrue("compact fab < medium fab", c < m)
        assertTrue("medium fab < expanded fab", m < e)
    }
    @Test fun editor_min_height_scales_monotonically_across_buckets() {
        val c = responsiveDimensionsFor(320, 640).editorMinHeight.value
        val m = responsiveDimensionsFor(393, 851).editorMinHeight.value
        val e = responsiveDimensionsFor(800, 1280).editorMinHeight.value
        assertTrue(c < m)
        assertTrue(m < e)
    }
    @Test fun font_scale_scales_monotonically_across_buckets() {
        val c = responsiveDimensionsFor(320, 640).fontScale
        val m = responsiveDimensionsFor(393, 851).fontScale
        val e = responsiveDimensionsFor(800, 1280).fontScale
        assertTrue("compact < medium fontScale", c < m)
        assertTrue("medium < expanded fontScale", m < e)
    }
    // Landscape orientation
    @Test fun landscape_on_compact_yields_more_grid_columns_than_portrait() {
        val portrait  = responsiveDimensionsFor(320, 640).gridColumns
        val landscape = responsiveDimensionsFor(640, 320).gridColumns
        assertTrue("landscape > portrait", landscape > portrait)
    }
    @Test fun landscape_on_medium_yields_more_grid_columns_than_portrait() {
        val portrait  = responsiveDimensionsFor(393, 851).gridColumns
        val landscape = responsiveDimensionsFor(851, 393).gridColumns
        assertTrue("landscape > portrait", landscape > portrait)
    }
    // scaledSp helper
    @Test fun scaledSp_is_identity_on_medium() {
        val d = responsiveDimensionsFor(393, 851)
        assertEquals(36f, d.scaledSp(36).value)
    }
    @Test fun scaledSp_scales_down_on_compact() {
        val d = responsiveDimensionsFor(320, 640)
        assertEquals(36 * 0.9f, d.scaledSp(36).value)
    }
    @Test fun scaledSp_scales_up_on_expanded() {
        val d = responsiveDimensionsFor(800, 1280)
        assertEquals(36 * 1.1f, d.scaledSp(36).value)
    }    // ── Phase 7: contentMaxWidth tablet cap ─────────────────────────────────
    @Test fun compact_has_unspecified_content_max_width_no_cap() {
        assertEquals(
            androidx.compose.ui.unit.Dp.Unspecified,
            responsiveDimensionsFor(320, 640).contentMaxWidth
        )
    }
    @Test fun medium_has_unspecified_content_max_width_no_cap() {
        assertEquals(
            androidx.compose.ui.unit.Dp.Unspecified,
            responsiveDimensionsFor(393, 851).contentMaxWidth
        )
    }
    @Test fun expanded_has_720dp_content_max_width_cap() {
        assertEquals(720f, responsiveDimensionsFor(800, 1280).contentMaxWidth.value)
    }
    @Test fun foldable_unfolded_has_cap() {
        // 601dp - just above the EXPANDED threshold
        assertEquals(720f, responsiveDimensionsFor(601, 800).contentMaxWidth.value)
    }
}