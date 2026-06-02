package com.amvarpvtltd.swiftNote.utils

import com.amvarpvtltd.swiftNote.checklist.ChecklistItem
import com.amvarpvtltd.swiftNote.checklist.ChecklistParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for AutoTitleGenerator (Phase 4).
 * Uses Robolectric so org.json classes are available for ChecklistParser tests.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class AutoTitleGeneratorTest {

    @Test
    fun `generate returns empty string for blank input`() {
        assertEquals("", AutoTitleGenerator.generate(""))
        assertEquals("", AutoTitleGenerator.generate("   "))
    }

    @Test
    fun `generate extracts action verb phrase`() {
        val description = "Buy milk and eggs from the grocery store tomorrow morning"
        val result = AutoTitleGenerator.generate(description)
        assertTrue("Should start with action: $result", result.startsWith("Buy"))
        assertTrue("Should be concise: $result", result.length <= 40)
    }

    @Test
    fun `generate handles question content`() {
        val description = "How do I configure Firebase authentication in my Android app?"
        val result = AutoTitleGenerator.generate(description)
        assertTrue("Should contain question: $result", result.contains("?") || result.startsWith("How"))
        assertTrue("Should be concise: $result", result.length <= 41) // 40 + ellipsis
    }

    @Test
    fun `generate extracts URL domain`() {
        val description = "https://developer.android.com/jetpack/compose"
        val result = AutoTitleGenerator.generate(description)
        assertTrue("Should mention link: $result", result.contains("Link"))
        assertTrue("Should contain domain: $result", result.contains("developer.android.com"))
    }

    @Test
    fun `generate produces short key phrase from long paragraph`() {
        val description = "The quarterly report for the marketing department needs to be reviewed and submitted before the deadline on Friday evening"
        val result = AutoTitleGenerator.generate(description)
        assertTrue("Should be concise (<= 41): $result (${result.length})", result.length <= 41)
        assertTrue("Should be meaningful (>= 5): $result", result.length >= 5)
    }

    @Test
    fun `generate keeps short first line as-is`() {
        val description = "Team standup notes"
        assertEquals("Team standup notes", AutoTitleGenerator.generate(description))
    }

    @Test
    fun `generate strips HTML tags and extracts meaning`() {
        val description = "<b>Important</b> meeting at <i>3pm</i>"
        val result = AutoTitleGenerator.generate(description)
        assertFalse("Should not contain HTML: $result", result.contains("<"))
        assertTrue("Should be meaningful: $result", result.isNotBlank())
    }

    @Test
    fun `generate handles heading HTML`() {
        val description = "<h1>Project Update</h1><p>Details about the project status and milestones</p>"
        val result = AutoTitleGenerator.generate(description)
        assertTrue("Should be meaningful: $result", result.isNotBlank())
        assertTrue("Should be concise: $result", result.length <= 41)
    }

    @Test
    fun `generate skips blank lines and uses meaningful content`() {
        val description = "\n\n  \nFinish the presentation slides\nAdd charts and graphs"
        val result = AutoTitleGenerator.generate(description)
        assertTrue("Should start with Finish: $result", result.startsWith("Finish"))
    }

    @Test
    fun `generate handles checklist - single item`() {
        val items = listOf(
            ChecklistItem(text = "Buy groceries", isChecked = false, order = 0)
        )
        val description = ChecklistParser.serializeItems(items)
        assertEquals("Buy groceries", AutoTitleGenerator.generate(description))
    }

    @Test
    fun `generate handles checklist - multiple items`() {
        val items = listOf(
            ChecklistItem(text = "Buy groceries", isChecked = false, order = 0),
            ChecklistItem(text = "Call dentist", isChecked = false, order = 1),
            ChecklistItem(text = "Pay electricity bill", isChecked = false, order = 2),
            ChecklistItem(text = "Pick up laundry", isChecked = true, order = 3)
        )
        val description = ChecklistParser.serializeItems(items)
        val result = AutoTitleGenerator.generate(description)
        assertTrue("Should be non-empty: $result", result.isNotBlank())
        assertTrue("Should be concise: $result", result.length <= 41)
    }

    @Test
    fun `generate prefers unchecked checklist items`() {
        val items = listOf(
            ChecklistItem(text = "Already done", isChecked = true, order = 0),
            ChecklistItem(text = "Still pending task", isChecked = false, order = 1)
        )
        val description = ChecklistParser.serializeItems(items)
        assertEquals("Still pending task", AutoTitleGenerator.generate(description))
    }

    @Test
    fun `generate falls back to checked item if all are checked`() {
        val items = listOf(
            ChecklistItem(text = "All done first", isChecked = true, order = 0),
            ChecklistItem(text = "All done second", isChecked = true, order = 1)
        )
        val description = ChecklistParser.serializeItems(items)
        assertEquals("All done first", AutoTitleGenerator.generate(description))
    }

    @Test
    fun `canGenerateTitle returns true for valid description`() {
        assertTrue(AutoTitleGenerator.canGenerateTitle("This is a valid note description"))
    }

    @Test
    fun `canGenerateTitle returns false for blank description`() {
        assertFalse(AutoTitleGenerator.canGenerateTitle(""))
        assertFalse(AutoTitleGenerator.canGenerateTitle("   "))
    }

    @Test
    fun `canGenerateTitle returns false for very short description`() {
        assertFalse(AutoTitleGenerator.canGenerateTitle("Hi"))
    }

    @Test
    fun `generate handles list-style content`() {
        val description = "- Milk\n- Eggs\n- Bread\n- Butter\n- Cheese"
        val result = AutoTitleGenerator.generate(description)
        assertTrue("Should be non-empty: $result", result.isNotBlank())
        assertTrue("Should be concise: $result", result.length <= 41)
    }

    @Test
    fun `generate never exceeds max length`() {
        val descriptions = listOf(
            "A".repeat(200),
            "word ".repeat(50),
            "Buy milk eggs bread butter cheese yogurt cereal pasta rice beans lentils",
            "How do I implement a very complex feature with multiple dependencies and configurations across the entire application?"
        )
        descriptions.forEach { desc ->
            val result = AutoTitleGenerator.generate(desc)
            assertTrue("Title too long (${result.length}): $result", result.length <= 41) // 40 + ellipsis
        }
    }
}

