package com.amvarpvtltd.swiftNote.auth

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pure JUnit tests for [SyncMode].
 * No Android context needed — these only cover the enum's [SyncMode.fromStorage] companion
 * and the enum values themselves.
 *
 * Part of Phase 4 verification (SwiftNote Sync Mode Selection plan).
 */
class SyncModeTest {

    // ─── fromStorage: happy paths ─────────────────────────────────────────────

    @Test
    fun `fromStorage LOCAL_ONLY returns LOCAL_ONLY`() {
        assertEquals(SyncMode.LOCAL_ONLY, SyncMode.fromStorage("LOCAL_ONLY"))
    }

    @Test
    fun `fromStorage CONTINUOUS returns CONTINUOUS`() {
        assertEquals(SyncMode.CONTINUOUS, SyncMode.fromStorage("CONTINUOUS"))
    }

    @Test
    fun `fromStorage ONE_TIME_IMPORTED returns ONE_TIME_IMPORTED`() {
        assertEquals(SyncMode.ONE_TIME_IMPORTED, SyncMode.fromStorage("ONE_TIME_IMPORTED"))
    }

    // ─── fromStorage: fallback to LOCAL_ONLY ─────────────────────────────────

    @Test
    fun `fromStorage null falls back to LOCAL_ONLY - fresh install no key stored`() {
        // Existing installs have no KEY_SYNC_MODE → must behave as LOCAL_ONLY
        assertEquals(SyncMode.LOCAL_ONLY, SyncMode.fromStorage(null))
    }

    @Test
    fun `fromStorage empty string falls back to LOCAL_ONLY`() {
        assertEquals(SyncMode.LOCAL_ONLY, SyncMode.fromStorage(""))
    }

    @Test
    fun `fromStorage unknown string falls back to LOCAL_ONLY`() {
        // A future app version may add new enum values that this version doesn't understand
        assertEquals(SyncMode.LOCAL_ONLY, SyncMode.fromStorage("SOME_FUTURE_MODE"))
        assertEquals(SyncMode.LOCAL_ONLY, SyncMode.fromStorage("MULTI_ACCOUNT"))
        assertEquals(SyncMode.LOCAL_ONLY, SyncMode.fromStorage("42"))
    }

    @Test
    fun `fromStorage wrong case falls back to LOCAL_ONLY - lookup is case-sensitive`() {
        // Enum name matching is exact — "continuous" is NOT equal to "CONTINUOUS"
        assertEquals(SyncMode.LOCAL_ONLY, SyncMode.fromStorage("continuous"))
        assertEquals(SyncMode.LOCAL_ONLY, SyncMode.fromStorage("local_only"))
        assertEquals(SyncMode.LOCAL_ONLY, SyncMode.fromStorage("one_time_imported"))
        assertEquals(SyncMode.LOCAL_ONLY, SyncMode.fromStorage("Continuous"))
    }

    @Test
    fun `fromStorage whitespace-padded value falls back to LOCAL_ONLY`() {
        // SharedPreferences shouldn't produce padded values, but guard against it
        assertEquals(SyncMode.LOCAL_ONLY, SyncMode.fromStorage(" CONTINUOUS"))
        assertEquals(SyncMode.LOCAL_ONLY, SyncMode.fromStorage("CONTINUOUS "))
    }

    // ─── Round-trip ───────────────────────────────────────────────────────────

    @Test
    fun `every SyncMode value round-trips through name and fromStorage`() {
        // Guards against any future enum value accidentally not being deserializable
        SyncMode.entries.forEach { mode ->
            assertEquals(
                "Round-trip serialization failed for SyncMode.$mode",
                mode,
                SyncMode.fromStorage(mode.name)
            )
        }
    }

    // ─── Enum structure ───────────────────────────────────────────────────────

    @Test
    fun `SyncMode has exactly three values - LOCAL_ONLY CONTINUOUS ONE_TIME_IMPORTED`() {
        // Change this test intentionally if you add a new mode.
        // It prevents accidentally adding a mode without updating the migration in navbar_kt.
        assertEquals(3, SyncMode.entries.size)
    }

    @Test
    fun `SyncMode entries have expected names`() {
        val names = SyncMode.entries.map { it.name }.toSet()
        assertEquals(
            setOf("LOCAL_ONLY", "CONTINUOUS", "ONE_TIME_IMPORTED"),
            names
        )
    }

    @Test
    fun `LOCAL_ONLY is the default - first enum value`() {
        // SyncMode.fromStorage falls back to LOCAL_ONLY for unknown values.
        // Confirm LOCAL_ONLY is conceptually the "safe" default.
        assertEquals(SyncMode.LOCAL_ONLY, SyncMode.entries.first())
    }
}

