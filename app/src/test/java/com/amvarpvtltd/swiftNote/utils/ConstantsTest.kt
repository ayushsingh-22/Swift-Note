package com.amvarpvtltd.swiftNote.utils

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for Constants — validates configuration values are within expected ranges.
 */
class ConstantsTest {

    @Test
    fun `title max length is reasonable`() {
        assertTrue(Constants.TITLE_MAX_LENGTH > 0)
        assertTrue(Constants.TITLE_MAX_LENGTH <= 200)
    }

    @Test
    fun `description max length is reasonable`() {
        assertTrue(Constants.DESCRIPTION_MAX_LENGTH > Constants.TITLE_MAX_LENGTH)
        assertTrue(Constants.DESCRIPTION_MAX_LENGTH <= 100000)
    }

    @Test
    fun `min content length is less than title max`() {
        assertTrue(Constants.MIN_CONTENT_LENGTH < Constants.TITLE_MAX_LENGTH)
        assertTrue(Constants.MIN_CONTENT_LENGTH > 0)
    }

    @Test
    fun `search debounce delay is positive`() {
        assertTrue(Constants.SEARCH_DEBOUNCE_DELAY > 0)
        assertTrue(Constants.SEARCH_DEBOUNCE_DELAY <= 1000) // Not too slow
    }

    @Test
    fun `animation durations are positive`() {
        assertTrue(Constants.COLOR_ANIMATION_DURATION > 0)
        assertTrue(Constants.SPRING_ANIMATION_DELAY > 0)
        assertTrue(Constants.LOADING_DELAY > 0)
    }

    @Test
    fun `grid columns are at least 1`() {
        assertTrue(Constants.GRID_COLUMNS_PORTRAIT >= 1)
        assertTrue(Constants.GRID_COLUMNS_LANDSCAPE >= Constants.GRID_COLUMNS_PORTRAIT)
    }

    @Test
    fun `view mode constants are distinct`() {
        assertNotEquals(Constants.VIEW_MODE_LIST, Constants.VIEW_MODE_GRID)
        assertNotEquals(Constants.VIEW_MODE_GRID, Constants.VIEW_MODE_CARD)
        assertNotEquals(Constants.VIEW_MODE_LIST, Constants.VIEW_MODE_CARD)
    }

    @Test
    fun `default view mode is a valid mode`() {
        val validModes = listOf(Constants.VIEW_MODE_LIST, Constants.VIEW_MODE_GRID, Constants.VIEW_MODE_CARD)
        assertTrue(Constants.DEFAULT_VIEW_MODE in validModes)
    }

    @Test
    fun `share mime type is text`() {
        assertEquals("text/plain", Constants.SHARE_MIME_TYPE)
    }
}

