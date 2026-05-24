package com.amvarpvtltd.swiftNote.room

import org.junit.Assert.*
import org.junit.Test

class NoteEntityPhase4Test {
    @Test
    fun noteEntityDefaultsHaveCorrectValues() {
        val entity = NoteEntity(id = "t1", title = "T", description = "D", mymobiledeviceid = "d1", timestamp = 1000L, synced = false)
        assertFalse(entity.isPinned)
        assertFalse(entity.isArchived)
        assertEquals("", entity.category)
        assertNull(entity.colorKey)
    }

    @Test
    fun noteEntityWithPinAndCategory() {
        val entity = NoteEntity(id = "t2", title = "P", description = "I", mymobiledeviceid = "d1", timestamp = 2000L, synced = true, isPinned = true, category = "Work", colorKey = "green")
        assertTrue(entity.isPinned)
        assertEquals("Work", entity.category)
    }

    @Test
    fun copyPreservesPhase4Fields() {
        val o = NoteEntity(id = "t3", title = "O", description = "D", mymobiledeviceid = "d1", timestamp = 3000L, synced = false, isPinned = true, isArchived = true, category = "Personal")
        val c = o.copy(synced = true)
        assertTrue(c.isPinned)
        assertTrue(c.isArchived)
        assertEquals("Personal", c.category)
    }
}
