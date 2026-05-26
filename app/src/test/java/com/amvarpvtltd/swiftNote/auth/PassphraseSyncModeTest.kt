package com.amvarpvtltd.swiftNote.auth

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Robolectric tests for [PassphraseManager.getSyncMode] and [PassphraseManager.setSyncMode].
 *
 * These methods only touch SharedPreferences ("passphrase_prefs" / "sync_mode" key) —
 * no Firebase, no Keystore — so Robolectric is sufficient without any mocking.
 *
 * Part of Phase 4 verification (SwiftNote Sync Mode Selection plan).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31], manifest = Config.NONE)
class PassphraseSyncModeTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        // Wipe the prefs before each test so tests are fully isolated
        context.getSharedPreferences("passphrase_prefs", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    // ─── Default / fresh install ──────────────────────────────────────────────

    @Test
    fun `getSyncMode returns LOCAL_ONLY when no key stored - fresh install`() {
        val mode = PassphraseManager.getSyncMode(context)
        assertEquals(SyncMode.LOCAL_ONLY, mode)
    }

    @Test
    fun `getSyncMode returns LOCAL_ONLY after prefs cleared - simulates migration scenario`() {
        // Existing users who update from a pre-Phase1 build have no sync_mode key.
        // getSyncMode must return LOCAL_ONLY as a safe default.
        PassphraseManager.setSyncMode(context, SyncMode.CONTINUOUS)
        context.getSharedPreferences("passphrase_prefs", Context.MODE_PRIVATE)
            .edit().remove("sync_mode").commit()
        assertEquals(SyncMode.LOCAL_ONLY, PassphraseManager.getSyncMode(context))
    }

    // ─── Write + read round-trips ─────────────────────────────────────────────

    @Test
    fun `setSyncMode LOCAL_ONLY then getSyncMode returns LOCAL_ONLY`() {
        PassphraseManager.setSyncMode(context, SyncMode.LOCAL_ONLY)
        assertEquals(SyncMode.LOCAL_ONLY, PassphraseManager.getSyncMode(context))
    }

    @Test
    fun `setSyncMode CONTINUOUS then getSyncMode returns CONTINUOUS`() {
        PassphraseManager.setSyncMode(context, SyncMode.CONTINUOUS)
        assertEquals(SyncMode.CONTINUOUS, PassphraseManager.getSyncMode(context))
    }

    @Test
    fun `setSyncMode ONE_TIME_IMPORTED then getSyncMode returns ONE_TIME_IMPORTED`() {
        PassphraseManager.setSyncMode(context, SyncMode.ONE_TIME_IMPORTED)
        assertEquals(SyncMode.ONE_TIME_IMPORTED, PassphraseManager.getSyncMode(context))
    }

    @Test
    fun `all SyncMode values survive a full round-trip through PassphraseManager`() {
        SyncMode.entries.forEach { mode ->
            PassphraseManager.setSyncMode(context, mode)
            assertEquals(
                "Round-trip failed for mode: $mode",
                mode,
                PassphraseManager.getSyncMode(context)
            )
        }
    }

    // ─── Overwrite / transition ────────────────────────────────────────────────

    @Test
    fun `mode transitions from CONTINUOUS to LOCAL_ONLY on disconnect`() {
        // Simulates a user disconnecting from Continuous Sync
        PassphraseManager.setSyncMode(context, SyncMode.CONTINUOUS)
        PassphraseManager.setSyncMode(context, SyncMode.LOCAL_ONLY)
        assertEquals(SyncMode.LOCAL_ONLY, PassphraseManager.getSyncMode(context))
    }

    @Test
    fun `mode transitions from LOCAL_ONLY to CONTINUOUS on restore`() {
        PassphraseManager.setSyncMode(context, SyncMode.LOCAL_ONLY)
        PassphraseManager.setSyncMode(context, SyncMode.CONTINUOUS)
        assertEquals(SyncMode.CONTINUOUS, PassphraseManager.getSyncMode(context))
    }

    @Test
    fun `mode transitions from CONTINUOUS to ONE_TIME_IMPORTED on import`() {
        PassphraseManager.setSyncMode(context, SyncMode.CONTINUOUS)
        PassphraseManager.setSyncMode(context, SyncMode.ONE_TIME_IMPORTED)
        assertEquals(SyncMode.ONE_TIME_IMPORTED, PassphraseManager.getSyncMode(context))
    }

    @Test
    fun `setSyncMode is idempotent - storing same value twice`() {
        PassphraseManager.setSyncMode(context, SyncMode.CONTINUOUS)
        PassphraseManager.setSyncMode(context, SyncMode.CONTINUOUS)
        assertEquals(SyncMode.CONTINUOUS, PassphraseManager.getSyncMode(context))
    }

    // ─── Persistence ─────────────────────────────────────────────────────────

    @Test
    fun `getSyncMode is stable across multiple reads`() {
        PassphraseManager.setSyncMode(context, SyncMode.CONTINUOUS)
        repeat(5) {
            assertEquals(SyncMode.CONTINUOUS, PassphraseManager.getSyncMode(context))
        }
    }

    @Test
    fun `sync mode stored as plain string name in passphrase_prefs`() {
        // Verifies the storage key and raw value so that future migrations can
        // depend on the exact format without breaking.
        PassphraseManager.setSyncMode(context, SyncMode.CONTINUOUS)
        val prefs = context.getSharedPreferences("passphrase_prefs", Context.MODE_PRIVATE)
        assertEquals("CONTINUOUS", prefs.getString("sync_mode", null))

        PassphraseManager.setSyncMode(context, SyncMode.ONE_TIME_IMPORTED)
        assertEquals("ONE_TIME_IMPORTED", prefs.getString("sync_mode", null))
    }

    // ─── Isolation from passphrase operations ─────────────────────────────────

    @Test
    fun `clearStoredPassphrase does NOT clear sync mode`() {
        // IMPORTANT design decision: sync mode has a separate lifecycle from the passphrase.
        // Disconnect / Start Fresh flows explicitly call setSyncMode(LOCAL_ONLY) as a
        // separate step — clearStoredPassphrase only clears passphrase fields.
        // If this test fails, update the code AND the disconnect handlers in Phase 5.
        PassphraseManager.setSyncMode(context, SyncMode.CONTINUOUS)
        PassphraseManager.clearStoredPassphrase(context)
        // Sync mode must survive the passphrase clear
        assertEquals(SyncMode.CONTINUOUS, PassphraseManager.getSyncMode(context))
    }

    @Test
    fun `sync mode key is independent of passphrase keys in prefs`() {
        // Verify no key collision between KEY_PASSPHRASE* and KEY_SYNC_MODE
        PassphraseManager.setSyncMode(context, SyncMode.CONTINUOUS)
        val prefs = context.getSharedPreferences("passphrase_prefs", Context.MODE_PRIVATE)
        // sync_mode key exists
        assertTrue(prefs.contains("sync_mode"))
        // passphrase encryption key should not have been touched by setSyncMode
        assertFalse(
            "setSyncMode must not write to device_passphrase_enc",
            prefs.contains("device_passphrase_enc")
        )
    }

    // ─── Corrupted / future format ────────────────────────────────────────────

    @Test
    fun `getSyncMode returns LOCAL_ONLY if stored value is corrupted or from future version`() {
        context.getSharedPreferences("passphrase_prefs", Context.MODE_PRIVATE)
            .edit().putString("sync_mode", "CORRUPTED_UNKNOWN_MODE").commit()
        assertEquals(SyncMode.LOCAL_ONLY, PassphraseManager.getSyncMode(context))
    }

    @Test
    fun `getSyncMode returns LOCAL_ONLY for empty string stored value`() {
        context.getSharedPreferences("passphrase_prefs", Context.MODE_PRIVATE)
            .edit().putString("sync_mode", "").commit()
        assertEquals(SyncMode.LOCAL_ONLY, PassphraseManager.getSyncMode(context))
    }
}


