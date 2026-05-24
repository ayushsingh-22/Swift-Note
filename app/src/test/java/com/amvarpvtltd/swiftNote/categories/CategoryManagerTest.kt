package com.amvarpvtltd.swiftNote.categories

import org.junit.Assert.*
import org.junit.Test

/**
 * Phase 4: Unit tests for CategoryManager.
 * Tests default categories, color resolution, and presets.
 */
class CategoryManagerTest {

    @Test
    fun `default categories has 8 entries`() {
        assertEquals(8, CategoryManager.defaultCategories.size)
    }

    @Test
    fun `default categories have unique names`() {
        val names = CategoryManager.defaultCategories.map { it.name }
        assertEquals(names.size, names.distinct().size)
    }

    @Test
    fun `default categories have unique keys`() {
        val keys = CategoryManager.defaultCategories.map { it.key }
        assertEquals(keys.size, keys.distinct().size)
    }

    @Test
    fun `default categories have valid color hex values`() {
        CategoryManager.defaultCategories.forEach { cat ->
            assertTrue("Color hex should be > 0 for ${cat.name}", cat.colorHex > 0)
            // Color should have alpha channel (0xFF prefix)
            assertTrue("Color should have alpha for ${cat.name}", cat.colorHex and 0xFF000000L != 0L)
        }
    }

    @Test
    fun `preset colors list is not empty`() {
        assertTrue(CategoryManager.presetColors.isNotEmpty())
        assertTrue(CategoryManager.presetColors.size >= 8)
    }

    @Test
    fun `category data class creates valid color`() {
        val cat = Category("Test", 0xFF4A90D9, "test")
        assertNotNull(cat.color)
        assertEquals("Test", cat.name)
        assertEquals("test", cat.key)
    }

    @Test
    fun `expected default categories exist`() {
        val names = CategoryManager.defaultCategories.map { it.name }
        assertTrue(names.contains("Personal"))
        assertTrue(names.contains("Work"))
        assertTrue(names.contains("Shopping"))
        assertTrue(names.contains("Health"))
        assertTrue(names.contains("Finance"))
        assertTrue(names.contains("Travel"))
        assertTrue(names.contains("Ideas"))
        assertTrue(names.contains("Learning"))
    }
}

