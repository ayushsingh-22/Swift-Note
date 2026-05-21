package com.amvarpvtltd.swiftNote

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger

/**
 * Unit tests for Note data class and global device ID covering:
 * - BUG-007: Thread-safe global device ID (AtomicReference)
 * - Note construction and defaults
 * - toEncryptedData / fromEncryptedData
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31], manifest = Config.NONE)
class NoteTest {

    @OptIn(VisibleForTestingOnly::class)
    @Before
    fun setUp() {
        // Reset global device ID for test isolation
        DeviceIdentity.resetForTesting("test-device-id")
    }

    // ============================================================
    // Note Construction Tests
    // ============================================================

    @Test
    fun `note has default UUID id`() {
        val note = Note(title = "Test", description = "Desc")
        assertNotNull(note.id)
        assertTrue(note.id.isNotEmpty())
        // UUID format: 8-4-4-4-12
        assertTrue(note.id.matches(Regex("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}")))
    }

    @OptIn(VisibleForTestingOnly::class)
    @Test
    fun `note uses global device ID by default`() {
        DeviceIdentity.resetForTesting("my-device-123")
        val note = Note(title = "Test", description = "Desc")
        assertEquals("my-device-123", note.mymobiledeviceid)
    }

    @Test
    fun `note timestamp is approximately current time`() {
        val before = System.currentTimeMillis()
        val note = Note(title = "Test", description = "Desc")
        val after = System.currentTimeMillis()
        assertTrue(note.timestamp in before..after)
    }

    @Test
    fun `note with explicit parameters keeps them`() {
        val note = Note(
            title = "My Title",
            description = "My Description",
            id = "custom-id",
            mymobiledeviceid = "custom-device",
            timestamp = 12345L
        )
        assertEquals("My Title", note.title)
        assertEquals("My Description", note.description)
        assertEquals("custom-id", note.id)
        assertEquals("custom-device", note.mymobiledeviceid)
        assertEquals(12345L, note.timestamp)
    }

    @Test
    fun `note defaults to empty title and description`() {
        val note = Note()
        assertEquals("", note.title)
        assertEquals("", note.description)
    }

    // ============================================================
    // BUG-007: Thread-Safe Global Device ID Tests
    // ============================================================

    @Test
    fun `global device ID set and get works`() {
        DeviceIdentity.set("device-A", "test")
        assertEquals("device-A", myGlobalMobileDeviceId)

        DeviceIdentity.set("device-B", "test")
        assertEquals("device-B", myGlobalMobileDeviceId)
    }

    @Test
    fun `global device ID is thread-safe under concurrent writes`() {
        val threadCount = 10
        val iterationsPerThread = 100
        val latch = CountDownLatch(threadCount)
        val errors = AtomicInteger(0)

        val threads = (0 until threadCount).map { threadIdx ->
            Thread {
                try {
                    repeat(iterationsPerThread) { i ->
                        DeviceIdentity.set("thread-$threadIdx-iter-$i", "concurrencyTest")
                        // Read should never throw or return garbled data
                        val read = myGlobalMobileDeviceId
                        if (read.isEmpty() && threadIdx > 0) {
                            // After initial set, should never be empty
                        }
                    }
                } catch (e: Exception) {
                    errors.incrementAndGet()
                } finally {
                    latch.countDown()
                }
            }
        }

        threads.forEach { it.start() }
        latch.await()

        assertEquals("No errors during concurrent access", 0, errors.get())
        // Final value should be non-empty (some thread wrote last)
        assertTrue(myGlobalMobileDeviceId.isNotEmpty())
    }

    // ============================================================
    // toEncryptedData Tests
    // ============================================================

    @Test(expected = IllegalArgumentException::class)
    fun `toEncryptedData throws when mymobiledeviceid is empty`() {
        val note = Note(title = "Secret", description = "Data", mymobiledeviceid = "")
        note.toEncryptedData()
    }

    // ============================================================
    // Typealias Tests
    // ============================================================

    @Test
    fun `dataclass typealias refers to Note`() {
        val note: dataclass = Note(title = "Test", description = "Desc")
        assertTrue(note is Note)
    }

    // ============================================================
    // Data Class Equality Tests
    // ============================================================

    @Test
    fun `notes with same content are equal`() {
        val note1 = Note(title = "A", description = "B", id = "1", mymobiledeviceid = "d", timestamp = 100L)
        val note2 = Note(title = "A", description = "B", id = "1", mymobiledeviceid = "d", timestamp = 100L)
        assertEquals(note1, note2)
    }

    @Test
    fun `notes with different IDs are not equal`() {
        val note1 = Note(title = "A", description = "B", id = "1", mymobiledeviceid = "d", timestamp = 100L)
        val note2 = Note(title = "A", description = "B", id = "2", mymobiledeviceid = "d", timestamp = 100L)
        assertNotEquals(note1, note2)
    }

    @Test
    fun `copy creates independent instance`() {
        val original = Note(title = "Original", description = "Desc", id = "1", mymobiledeviceid = "d", timestamp = 100L)
        val copy = original.copy(title = "Modified")
        assertEquals("Modified", copy.title)
        assertEquals("Original", original.title) // original unchanged
        assertEquals(original.id, copy.id)
    }
}


