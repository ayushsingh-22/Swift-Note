package com.amvarpvtltd.swiftNote.categories

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.ui.graphics.Color
import org.json.JSONArray
import org.json.JSONObject

/**
 * Phase 4: CategoryManager — manages note categories with preset colors.
 * Categories = colors: one field drives both organization and visual identity.
 */
object CategoryManager {

    private const val PREFS_NAME = "swiftnote_categories"
    private const val KEY_CUSTOM_CATEGORIES = "custom_categories"
    private const val MAX_CUSTOM_CATEGORIES = 20

    // Pre-seeded default categories with colors
    val defaultCategories = listOf(
        Category("Personal", 0xFF4A90D9, "personal"),   // Blue
        Category("Work", 0xFF4CAF50, "work"),            // Green
        Category("Shopping", 0xFFFF9800, "shopping"),    // Orange
        Category("Health", 0xFFE53935, "health"),        // Red
        Category("Finance", 0xFF009688, "finance"),      // Teal
        Category("Travel", 0xFFFFC107, "travel"),        // Yellow/Amber
        Category("Ideas", 0xFF9C27B0, "ideas"),          // Purple
        Category("Learning", 0xFFE91E63, "learning")     // Pink
    )

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Get all categories (defaults + custom).
     */
    fun getAll(context: Context): List<Category> {
        return defaultCategories + getCustomCategories(context)
    }

    /**
     * Get the color for a category name. Returns null if not found.
     */
    fun getColor(context: Context, categoryName: String): Long? {
        if (categoryName.isBlank()) return null
        return getAll(context).find { it.name.equals(categoryName, ignoreCase = true) }?.colorHex
    }

    /**
     * Get a Color object for a category name.
     */
    fun getCategoryColor(context: Context, categoryName: String): Color? {
        val hex = getColor(context, categoryName) ?: return null
        return Color(hex)
    }

    /**
     * Add a custom category. Returns false if limit reached or name already exists.
     */
    fun addCustom(context: Context, name: String, colorHex: Long): Boolean {
        val existing = getAll(context)
        if (existing.size >= defaultCategories.size + MAX_CUSTOM_CATEGORIES) return false
        if (existing.any { it.name.equals(name, ignoreCase = true) }) return false

        val customs = getCustomCategories(context).toMutableList()
        customs.add(Category(name, colorHex, name.lowercase().replace(" ", "_")))
        saveCustomCategories(context, customs)
        return true
    }

    /**
     * Remove a custom category by name.
     */
    fun removeCustom(context: Context, name: String): Boolean {
        val customs = getCustomCategories(context).toMutableList()
        val removed = customs.removeAll { it.name.equals(name, ignoreCase = true) }
        if (removed) saveCustomCategories(context, customs)
        return removed
    }

    private fun getCustomCategories(context: Context): List<Category> {
        val prefs = getPrefs(context)
        val json = prefs.getString(KEY_CUSTOM_CATEGORIES, null) ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                Category(
                    name = obj.getString("name"),
                    colorHex = obj.getLong("colorHex"),
                    key = obj.getString("key")
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveCustomCategories(context: Context, categories: List<Category>) {
        val arr = JSONArray()
        categories.forEach { cat ->
            arr.put(JSONObject().apply {
                put("name", cat.name)
                put("colorHex", cat.colorHex)
                put("key", cat.key)
            })
        }
        getPrefs(context).edit().putString(KEY_CUSTOM_CATEGORIES, arr.toString()).apply()
    }

    /**
     * Preset colors for custom category creation.
     */
    val presetColors = listOf(
        0xFF4A90D9L, // Blue
        0xFF4CAF50L, // Green
        0xFFFF9800L, // Orange
        0xFFE53935L, // Red
        0xFF009688L, // Teal
        0xFFFFC107L, // Amber
        0xFF9C27B0L, // Purple
        0xFFE91E63L, // Pink
        0xFF795548L, // Brown
        0xFF607D8BL, // Blue Grey
        0xFF3F51B5L, // Indigo
        0xFF00BCD4L  // Cyan
    )
}

data class Category(
    val name: String,
    val colorHex: Long,
    val key: String
) {
    val color: Color get() = Color(colorHex)
}

