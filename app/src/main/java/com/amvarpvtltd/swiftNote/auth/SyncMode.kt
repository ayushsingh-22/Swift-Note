package com.amvarpvtltd.swiftNote.auth

/**
 * Tracks how the current device identity was established.
 * Stored alongside the passphrase in passphrase_prefs.
 *
 * Used to:
 *  - Drive the "Disconnect" card visibility in SyncSettingsScreen (only shown for CONTINUOUS)
 *  - Record what type of restore the user performed (for future UX indicators)
 *
 * Persistence: written via [PassphraseManager.setSyncMode], read via [PassphraseManager.getSyncMode].
 */
enum class SyncMode {

    /**
     * Identity = deviceId. No external account linked.
     * Default for Start Fresh and for existing installs with no stored marker.
     */
    LOCAL_ONLY,

    /**
     * Identity = sourcePassphrase (adopted from another device via Restore).
     * This device shares a Firebase account with the source device.
     * Future reads/writes go to users/{sourcePassphrase}/.
     */
    CONTINUOUS,

    /**
     * Identity = deviceId (kept after a one-time import).
     * Notes were imported from an external account once; the link was then dropped.
     * Future reads/writes go to users/{deviceId}/ — the source is not affected.
     */
    ONE_TIME_IMPORTED;

    companion object {
        /**
         * Safely deserializes a stored raw string. Returns [LOCAL_ONLY] for null / unknown values
         * so that existing installs without the marker behave as local-only (no behavior change).
         */
        fun fromStorage(raw: String?): SyncMode =
            entries.firstOrNull { it.name == raw } ?: LOCAL_ONLY
    }
}

