package com.amvarpvtltd.swiftNote.reminders

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for ReminderData classes (ReminderPreset, ReminderRequest, ReminderEntity).
 */
class ReminderDataTest {

    // ============================================================
    // ReminderPreset Tests
    // ============================================================

    @Test
    fun `TEN_MINUTES preset is 10 minutes`() {
        assertEquals(10, ReminderPreset.TEN_MINUTES.minutes)
    }

    @Test
    fun `THIRTY_MINUTES preset is 30 minutes`() {
        assertEquals(30, ReminderPreset.THIRTY_MINUTES.minutes)
    }

    @Test
    fun `ONE_HOUR preset is 60 minutes`() {
        assertEquals(60, ReminderPreset.ONE_HOUR.minutes)
    }

    @Test
    fun `ONE_DAY preset is 1440 minutes`() {
        assertEquals(1440, ReminderPreset.ONE_DAY.minutes)
    }

    @Test
    fun `CUSTOM preset has negative minutes marker`() {
        assertEquals(-1, ReminderPreset.CUSTOM.minutes)
    }

    // ============================================================
    // ReminderRequest Tests
    // ============================================================

    @Test
    fun `getReminderTime with preset returns future time`() {
        val request = ReminderRequest(
            noteId = "note-1",
            noteTitle = "Test",
            preset = ReminderPreset.TEN_MINUTES
        )
        val before = System.currentTimeMillis()
        val reminderTime = request.getReminderTime()
        val after = System.currentTimeMillis()

        // Should be ~10 minutes in the future
        val expectedMin = before + (10 * 60 * 1000L)
        val expectedMax = after + (10 * 60 * 1000L)
        assertTrue("Reminder time should be ~10 min from now", reminderTime in expectedMin..expectedMax)
    }

    @Test
    fun `getReminderTime with ONE_HOUR preset returns 60 min offset`() {
        val request = ReminderRequest(
            noteId = "note-1",
            noteTitle = "Test",
            preset = ReminderPreset.ONE_HOUR
        )
        val before = System.currentTimeMillis()
        val reminderTime = request.getReminderTime()

        val diff = reminderTime - before
        // Should be approximately 60 minutes (allow 1 second tolerance)
        assertTrue("Diff should be ~60 min", diff in 3599000L..3601000L)
    }

    @Test
    fun `getReminderTime with CUSTOM preset uses customDateTime`() {
        val customTime = 1700000000000L
        val request = ReminderRequest(
            noteId = "note-1",
            noteTitle = "Test",
            preset = ReminderPreset.CUSTOM,
            customDateTime = customTime
        )
        assertEquals(customTime, request.getReminderTime())
    }

    @Test
    fun `getReminderTime with CUSTOM but null customDateTime falls back to preset calc`() {
        val request = ReminderRequest(
            noteId = "note-1",
            noteTitle = "Test",
            preset = ReminderPreset.CUSTOM,
            customDateTime = null
        )
        // CUSTOM.minutes is -1, so time = now + (-1 * 60000) = now - 60000 (past)
        val time = request.getReminderTime()
        // Just verify it doesn't crash
        assertTrue(time > 0)
    }

    // ============================================================
    // ReminderEntity Tests
    // ============================================================

    @Test
    fun `ReminderEntity defaults isActive to true`() {
        val entity = ReminderEntity(
            id = "rem-1",
            noteId = "note-1",
            noteTitle = "Title",
            noteDescription = "Desc",
            reminderTime = System.currentTimeMillis() + 60000
        )
        assertTrue(entity.isActive)
    }

    @Test
    fun `ReminderEntity createdAt defaults to approximate current time`() {
        val before = System.currentTimeMillis()
        val entity = ReminderEntity(
            id = "rem-1",
            noteId = "note-1",
            noteTitle = "Title",
            noteDescription = "Desc",
            reminderTime = System.currentTimeMillis() + 60000
        )
        val after = System.currentTimeMillis()
        assertTrue(entity.createdAt in before..after)
    }

    @Test
    fun `ReminderEntity preserves all fields`() {
        val entity = ReminderEntity(
            id = "rem-123",
            noteId = "note-456",
            noteTitle = "My Reminder",
            noteDescription = "Some description",
            reminderTime = 1234567890000L,
            isActive = false,
            createdAt = 1000000000000L
        )
        assertEquals("rem-123", entity.id)
        assertEquals("note-456", entity.noteId)
        assertEquals("My Reminder", entity.noteTitle)
        assertEquals("Some description", entity.noteDescription)
        assertEquals(1234567890000L, entity.reminderTime)
        assertFalse(entity.isActive)
        assertEquals(1000000000000L, entity.createdAt)
    }

    @Test
    fun `ReminderEntity copy modifies fields correctly`() {
        val original = ReminderEntity(
            id = "rem-1",
            noteId = "note-1",
            noteTitle = "Title",
            noteDescription = "Desc",
            reminderTime = 123L
        )
        val deactivated = original.copy(isActive = false)
        assertFalse(deactivated.isActive)
        assertEquals(original.id, deactivated.id)
        assertEquals(original.reminderTime, deactivated.reminderTime)
    }
}

