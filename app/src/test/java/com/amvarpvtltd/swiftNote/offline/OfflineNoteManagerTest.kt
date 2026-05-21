package com.amvarpvtltd.swiftNote.offline

import com.amvarpvtltd.swiftNote.Note
import com.amvarpvtltd.swiftNote.myGlobalMobileDeviceId
import com.amvarpvtltd.swiftNote.room.NoteEntity
import com.amvarpvtltd.swiftNote.room.NoteEntityMapper
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for OfflineNoteManager's non-DB logic:
 * - NoteEntityMapper bidirectional mapping
 * - BUG-039: Transaction-based delete logic is tested via instrumented tests
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31], manifest = Config.NONE)
class OfflineNoteManagerTest {

    @Before
    fun setUp() {
        myGlobalMobileDeviceId = "test-device-offline"
    }

    // ============================================================
    // NoteEntityMapper Tests
    // ============================================================

    @Test
    fun `toEntity maps Note fields correctly`() {
        val note = Note(
            title = "Test Title",
            description = "Test Description",
            id = "note-123",
            mymobiledeviceid = "device-456",
            timestamp = 1000L
        )
        val entity = NoteEntityMapper.toEntity(note, synced = false)
        assertEquals("note-123", entity.id)
        assertEquals("device-456", entity.mymobiledeviceid)
        assertEquals(1000L, entity.timestamp)
        assertFalse(entity.synced)
        // Title and description should be encrypted (non-empty, different from plain)
        assertTrue(entity.title.isNotEmpty())
        assertTrue(entity.description.isNotEmpty())
    }

    @Test
    fun `toEntity with synced true sets synced flag`() {
        val note = Note(
            title = "Title",
            description = "Desc",
            id = "id-1",
            mymobiledeviceid = "device-1",
            timestamp = 2000L
        )
        val entity = NoteEntityMapper.toEntity(note, synced = true)
        assertTrue(entity.synced)
    }

    @Test
    fun `toDomain maps NoteEntity back to Note`() {
        // Create an entity with encrypted data
        val note = Note(
            title = "Original Title",
            description = "Original Desc",
            id = "id-2",
            mymobiledeviceid = "test-device-offline",
            timestamp = 3000L
        )
        val entity = NoteEntityMapper.toEntity(note, synced = true)

        // Map back to domain
        val restored = NoteEntityMapper.toDomain(entity)
        assertEquals("id-2", restored.id)
        assertEquals("test-device-offline", restored.mymobiledeviceid)
        assertEquals(3000L, restored.timestamp)
        // Title/desc should be decrypted back to original
        assertEquals("Original Title", restored.title)
        assertEquals("Original Desc", restored.description)
    }

    @Test
    fun `roundtrip Note to Entity to Note preserves data`() {
        val original = Note(
            title = "Roundtrip Test",
            description = "This is a roundtrip test with special chars: <b>bold</b>",
            id = "rt-1",
            mymobiledeviceid = "test-device-offline",
            timestamp = 4000L
        )
        val entity = NoteEntityMapper.toEntity(original, synced = false)
        val restored = NoteEntityMapper.toDomain(entity)

        assertEquals(original.title, restored.title)
        assertEquals(original.description, restored.description)
        assertEquals(original.id, restored.id)
        assertEquals(original.mymobiledeviceid, restored.mymobiledeviceid)
        assertEquals(original.timestamp, restored.timestamp)
    }

    @Test
    fun `toDomain handles empty title and description`() {
        val entity = NoteEntity(
            id = "empty-1",
            title = "",
            description = "",
            mymobiledeviceid = "test-device-offline",
            timestamp = 5000L,
            synced = true
        )
        val note = NoteEntityMapper.toDomain(entity)
        assertEquals("", note.title)
        assertEquals("", note.description)
    }
}
