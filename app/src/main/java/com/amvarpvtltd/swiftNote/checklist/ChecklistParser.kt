package com.amvarpvtltd.swiftNote.checklist

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Data class representing a single checklist item.
 */
data class ChecklistItem(
    val id: String = UUID.randomUUID().toString(),
    val text: String = "",
    val isChecked: Boolean = false,
    val order: Int = 0
)

/**
 * Parser for checklist content stored inline in the note description field.
 *
 * Format: [[CHECKLIST_V1]]<json-array-of-items>
 * A note is a checklist note iff description starts with the CHECKLIST_V1 prefix.
 */
object ChecklistParser {

    private const val PREFIX = "[[CHECKLIST_V1]]"
    private const val MAX_ITEMS = 100

    /**
     * Returns true if the description represents a checklist note.
     */
    fun isChecklistContent(description: String): Boolean {
        return description.trimStart().startsWith(PREFIX)
    }

    /**
     * Parse items from an encoded checklist description.
     * Returns empty list if parsing fails (fallback: treat as text note).
     */
    fun parseItems(description: String): List<ChecklistItem> {
        if (!isChecklistContent(description)) return emptyList()
        return try {
            val json = description.trimStart().removePrefix(PREFIX)
            val array = JSONArray(json)
            val items = mutableListOf<ChecklistItem>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                items.add(
                    ChecklistItem(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        text = obj.optString("text", ""),
                        isChecked = obj.optBoolean("isChecked", false),
                        order = obj.optInt("order", i)
                    )
                )
            }
            items.sortedBy { it.order }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Serialize a list of checklist items into the storage format.
     */
    fun serializeItems(items: List<ChecklistItem>): String {
        val array = JSONArray()
        items.forEachIndexed { index, item ->
            val obj = JSONObject().apply {
                put("id", item.id)
                put("text", item.text)
                put("isChecked", item.isChecked)
                put("order", index)
            }
            array.put(obj)
        }
        return "$PREFIX$array"
    }

    /**
     * Toggle the checked state of a specific item and return the new serialized description.
     */
    fun toggleItem(description: String, itemId: String): String {
        val items = parseItems(description).map { item ->
            if (item.id == itemId) item.copy(isChecked = !item.isChecked) else item
        }
        return serializeItems(items)
    }

    /**
     * Returns (checked, total) counts for progress display.
     */
    fun progress(description: String): Pair<Int, Int> {
        val items = parseItems(description)
        if (items.isEmpty()) return Pair(0, 0)
        val checked = items.count { it.isChecked }
        return Pair(checked, items.size)
    }

    /**
     * Convert plain text (newline-separated) to checklist items.
     * Each non-blank line becomes an unchecked item.
     */
    fun textToChecklist(text: String): List<ChecklistItem> {
        return text.lines()
            .filter { it.isNotBlank() }
            .take(MAX_ITEMS)
            .mapIndexed { index, line ->
                ChecklistItem(
                    text = line.trim(),
                    isChecked = false,
                    order = index
                )
            }
    }

    /**
     * Convert checklist items back to plain text (newline-separated).
     */
    fun checklistToText(items: List<ChecklistItem>): String {
        return items.joinToString("\n") { item ->
            if (item.isChecked) "✓ ${item.text}" else item.text
        }
    }

    /**
     * Get a plain-text preview suitable for note cards:
     * First 3 unchecked items, or "All done!" if everything is checked.
     */
    fun getPreviewText(description: String): String {
        val items = parseItems(description)
        if (items.isEmpty()) return ""
        val unchecked = items.filter { !it.isChecked }
        if (unchecked.isEmpty()) return "✓ All items completed!"
        return unchecked.take(3).joinToString("\n") { "○ ${it.text}" }
    }

    /**
     * Returns true if all checklist items are completed.
     */
    fun isAllDone(description: String): Boolean {
        val items = parseItems(description)
        return items.isNotEmpty() && items.all { it.isChecked }
    }

    /**
     * Check if adding more items would exceed the limit.
     */
    fun canAddMoreItems(items: List<ChecklistItem>): Boolean {
        return items.size < MAX_ITEMS
    }
}

