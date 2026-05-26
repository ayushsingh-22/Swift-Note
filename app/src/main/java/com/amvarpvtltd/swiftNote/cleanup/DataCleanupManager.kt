package com.amvarpvtltd.swiftNote.cleanup

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import com.amvarpvtltd.swiftNote.auth.DeviceManager
import com.amvarpvtltd.swiftNote.auth.PassphraseManager
import com.amvarpvtltd.swiftNote.room.AppDatabase
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

/**
 * Handles cleanup of all app data when the app is uninstalled or reset.
 */
object DataCleanupManager {
    private const val TAG = "DataCleanupManager"

    // ─────────────────────────────────────────────────────────────────────────
    // Public unified entry point — the ONLY thing OnboardingScreen should call
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Wipes all local notes/reminders/pending-deletions AND best-effort deletes
     * Firebase data tied to whichever identity this device previously used.
     *
     * Identity resolution order:
     *   1. Stored passphrase from passphrase_prefs (most recent identity)
     *   2. Device ID (legacy case where passphrase == deviceId)
     *      — only treated as previous identity if Firebase actually has data there.
     *
     * - Local wipe is mandatory; if it fails the Result is a failure and the caller
     *   MUST NOT proceed to identity creation (user would see stale data).
     * - Firebase wipe is best-effort, capped at 3 seconds total, never throws.
     *
     * @return Result<CleanupSummary> — failure only if the local wipe itself failed.
     */
    suspend fun wipeLocalAndPreviousRemoteNotes(context: Context): Result<CleanupSummary> =
        withContext(Dispatchers.IO) {
            try {
                // 1. Resolve the previous identity (if any).
                val previousIdentity: String? = resolvePreviousIdentity(context)

                // 2. Local wipe — mandatory (notes + reminders + pending-deletions).
                val db = AppDatabase.getInstance(context)
                db.noteDao().deleteAllNotes()
                db.pendingDeletionDao().clearAllPendingDeletions()
                db.reminderDao().clearAll()
                Log.i(TAG, "✅ Local notes + reminders + pending-deletions wiped")

                // 3. Remote wipe — best-effort, capped at 3 s.
                var remoteSucceeded = false
                if (previousIdentity != null) {
                    remoteSucceeded = withTimeoutOrNull(3_000L) {
                        deleteFirebaseDataForAccount(previousIdentity)
                    } ?: run {
                        Log.w(TAG, "⚠️ Remote wipe timed out for previous identity (best-effort)")
                        false
                    }
                }

                Result.success(
                    CleanupSummary(
                        localNotesDeleted = true,
                        remoteAttempted = previousIdentity != null,
                        remoteSucceeded = remoteSucceeded,
                        // Do NOT log the actual passphrase at INFO level — only in DEBUG
                        previousIdentity = if (android.util.Log.isLoggable(TAG, android.util.Log.DEBUG))
                            previousIdentity else null
                    )
                )
            } catch (e: Exception) {
                Log.e(TAG, "❌ wipeLocalAndPreviousRemoteNotes failed", e)
                Result.failure(e)
            }
        }

    /**
     * Resolves what identity this device used before so we can target the right
     * Firebase path for cleanup.
     *
     * Priority:
     *   1. Stored encrypted passphrase → use directly.
     *   2. DeviceId — only if Firebase actually has data at users/{deviceId}.
     *      (Avoids a pointless Firebase round-trip for truly fresh installs.)
     *   3. null → no previous remote identity known; skip remote wipe.
     */
    private suspend fun resolvePreviousIdentity(context: Context): String? {
        val stored = PassphraseManager.getStoredPassphrase(context)
        if (!stored.isNullOrBlank()) {
            Log.d(TAG, "Previous identity: stored passphrase (length=${stored.length})")
            return stored
        }
        // Fallback: legacy reinstall case where passphrase == deviceId.
        val deviceId = DeviceManager.getOrCreateDeviceId(context)
        return if (firebaseHasDataForAccount(deviceId)) {
            Log.d(TAG, "Previous identity: deviceId (Firebase data exists)")
            deviceId
        } else {
            Log.d(TAG, "No previous remote identity found")
            null
        }
    }

    /**
     * Returns true if Firebase has notes under users/{accountId}.
     * Capped at 2 seconds. Treats network failure or timeout as false — safe
     * default because we only want to ATTEMPT remote cleanup when we have signal.
     */
    private suspend fun firebaseHasDataForAccount(accountId: String): Boolean =
        withTimeoutOrNull(2_000L) {
            try {
                PassphraseManager.ensureAuthenticated()
                val ref = FirebaseDatabase.getInstance()
                    .getReference("users").child(accountId).child("notes")
                val snap = ref.get().await()
                snap.exists() && snap.hasChildren()
            } catch (e: Exception) {
                Log.w(TAG, "firebaseHasDataForAccount check failed: ${e.message}")
                false
            }
        } ?: false

    /**
     * Deletes both notes AND reminders under users/{accountId}.
     * Returns true if at least one delete call succeeded.
     * Each sub-path is attempted independently so a failure on one doesn't skip the other.
     */
    private suspend fun deleteFirebaseDataForAccount(accountId: String): Boolean {
        if (accountId.isBlank()) return false
        return try {
            PassphraseManager.ensureAuthenticated()
            val userRef = FirebaseDatabase.getInstance()
                .getReference("users").child(accountId)

            var anySuccess = false

            // Delete notes
            try {
                userRef.child("notes").removeValue().await()
                anySuccess = true
                Log.i(TAG, "✅ Firebase /notes deleted for account")
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ Failed to delete Firebase /notes: ${e.message}")
            }

            // Delete reminders
            try {
                userRef.child("reminders").removeValue().await()
                anySuccess = true
                Log.i(TAG, "✅ Firebase /reminders deleted for account")
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ Failed to delete Firebase /reminders: ${e.message}")
            }

            anySuccess
        } catch (e: Exception) {
            Log.w(TAG, "deleteFirebaseDataForAccount failed: ${e.message}")
            false
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Start-Fresh dedicated helper
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Forcefully deletes the **entire** `users/{accountId}` Firebase node (notes, reminders,
     * metadata) and waits for the operation to complete before returning.
     *
     * Unlike the best-effort delete inside [wipeLocalAndPreviousRemoteNotes] (capped at 3 s),
     * this function uses a 10-second timeout and returns a typed [Result] so the caller can
     * decide whether to block navigation or show an error.
     *
     * Intended for the Start Fresh flow where we must guarantee Firebase is clean **before**
     * navigating to main — otherwise [NoteRepository.fetchNotes] will pull old notes back from
     * Firebase during its background sync and defeat the wipe.
     *
     * @param accountId The Firebase account key to delete (typically the device ID).
     */
    suspend fun forceWipeFirebaseAccount(accountId: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            if (accountId.isBlank()) return@withContext Result.success(Unit)
            try {
                PassphraseManager.ensureAuthenticated()
                val result = withTimeoutOrNull(10_000L) {
                    try {
                        FirebaseDatabase.getInstance()
                            .getReference("users")
                            .child(accountId)
                            .removeValue()
                            .await()
                        Log.i(TAG, "✅ Force-wiped Firebase account for Start Fresh")
                        true
                    } catch (e: Exception) {
                        Log.w(TAG, "⚠️ forceWipeFirebaseAccount inner delete failed: ${e.message}")
                        false
                    }
                }
                if (result == true) Result.success(Unit)
                else Result.failure(Exception("Firebase wipe timed out or failed — old notes may still exist remotely"))
            } catch (e: Exception) {
                Log.w(TAG, "forceWipeFirebaseAccount outer failed: ${e.message}")
                Result.failure(e)
            }
        }

    // ─────────────────────────────────────────────────────────────────────────
    // Legacy / specialised helpers — kept for existing callers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Clear only local notes (Room DB + pending deletions) without touching preferences or files.
     *
     * @deprecated Prefer [wipeLocalAndPreviousRemoteNotes] which also handles Firebase and
     *             resolves the previous identity automatically.
     */
    @Deprecated(
        message = "Use wipeLocalAndPreviousRemoteNotes() which handles both local and remote cleanup.",
        replaceWith = ReplaceWith("wipeLocalAndPreviousRemoteNotes(context)")
    )
    suspend fun clearLocalNotesOnly(context: Context): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext try {
            Log.d(TAG, "Clearing local notes only...")
            val db = AppDatabase.getInstance(context)
            db.noteDao().deleteAllNotes()
            db.pendingDeletionDao().clearAllPendingDeletions()
            Log.i(TAG, "✅ Local notes cleared successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to clear local notes", e)
            Result.failure(e)
        }
    }

    /**
     * Best-effort deletion of all notes stored in Firebase under the given passphrase/accountId.
     * Errors are swallowed — caller should treat this as fire-and-forget cleanup.
     *
     * @deprecated Prefer [wipeLocalAndPreviousRemoteNotes] which combines local + remote
     *             and resolves the previous identity automatically.
     */
    @Deprecated(
        message = "Use wipeLocalAndPreviousRemoteNotes() for a unified clean-up.",
        replaceWith = ReplaceWith("wipeLocalAndPreviousRemoteNotes(context)")
    )
    suspend fun deleteFirebaseNotesForAccount(accountId: String) = withContext(Dispatchers.IO) {
        if (accountId.isBlank()) return@withContext
        try {
            PassphraseManager.ensureAuthenticated()
            val database = FirebaseDatabase.getInstance()
            database.getReference("users").child(accountId).child("notes").removeValue().await()
            Log.i(TAG, "✅ Firebase notes deleted for account")
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ Failed to delete Firebase notes (best-effort, ignoring): ${e.message}")
        }
    }

    /**
     * Clear all app data including database, preferences, and files.
     */
    suspend fun clearAllAppData(context: Context): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext try {
            Log.d(TAG, "Starting complete app data cleanup...")
            clearDatabase(context)
            clearSharedPreferences(context)
            clearAppFiles(context)
            clearPassphraseData(context)
            Log.i(TAG, "✅ Complete app data cleanup successful")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to clear app data", e)
            Result.failure(e)
        }
    }

    private suspend fun clearDatabase(context: Context) = withContext(Dispatchers.IO) {
        try {
            val db = AppDatabase.getInstance(context)

            db.noteDao().run {
                val notes = getAllNotes()
                notes.forEach { delete(it) }
            }

            db.reminderDao().run {
                val reminders = getAllReminders()
                reminders.forEach { reminder -> deleteReminder(reminder) }
            }

            db.pendingDeletionDao().run {
                clearAllPendingDeletions()
            }

            db.close()
            AppDatabase.resetInstance()

            val dbPath = context.getDatabasePath("notes_database")
            if (dbPath.exists()) {
                dbPath.delete()
                Log.d(TAG, "Database file deleted")
            }
            File("${dbPath.absolutePath}-wal").takeIf { it.exists() }?.delete()
            File("${dbPath.absolutePath}-shm").takeIf { it.exists() }?.delete()

            Log.d(TAG, "✅ Database cleared successfully")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to clear database", e)
            throw e
        }
    }

    private fun clearSharedPreferences(context: Context) {
        try {
            val prefsDir = File(context.applicationInfo.dataDir, "shared_prefs")
            if (prefsDir.exists() && prefsDir.isDirectory) {
                prefsDir.listFiles()?.forEach { file ->
                    if (file.isFile && file.name.endsWith(".xml")) {
                        val prefName = file.name.removeSuffix(".xml")
                        val prefs = context.getSharedPreferences(prefName, Context.MODE_PRIVATE)
                        prefs.edit { clear() }
                        Log.d(TAG, "Cleared preferences: $prefName")
                    }
                }
            }

            val knownPrefs = listOf(
                "passphrase_prefs", "device_prefs", "theme_prefs",
                "app_settings", "reminder_prefs"
            )
            knownPrefs.forEach { prefName ->
                try {
                    context.getSharedPreferences(prefName, Context.MODE_PRIVATE).edit { clear() }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to clear preferences: $prefName", e)
                }
            }
            Log.d(TAG, "✅ SharedPreferences cleared successfully")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to clear SharedPreferences", e)
            throw e
        }
    }

    private fun clearAppFiles(context: Context) {
        try {
            context.cacheDir?.let { if (it.exists()) deleteDirectoryContents(it) }
            context.externalCacheDir?.let { if (it.exists()) deleteDirectoryContents(it) }
            context.filesDir?.let { if (it.exists()) deleteDirectoryContents(it) }
            context.getExternalFilesDir(null)?.let { if (it.exists()) deleteDirectoryContents(it) }
            Log.d(TAG, "✅ App files cleared successfully")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to clear app files", e)
            throw e
        }
    }

    private fun clearPassphraseData(context: Context) {
        try {
            PassphraseManager.clearStoredPassphrase(context)
            context.getSharedPreferences("device_prefs", Context.MODE_PRIVATE).edit { clear() }
            Log.d(TAG, "✅ Passphrase data cleared successfully")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to clear passphrase data", e)
            throw e
        }
    }

    private fun deleteDirectoryContents(directory: File) {
        if (!directory.exists() || !directory.isDirectory) return
        directory.listFiles()?.forEach { file ->
            if (file.isDirectory) { deleteDirectoryContents(file); file.delete() }
            else file.delete()
        }
    }

    /**
     * Clear data for app reset (keeps some settings).
     */
    suspend fun clearDataForReset(context: Context): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext try {
            Log.d(TAG, "Starting app reset data cleanup...")
            clearDatabase(context)
            context.cacheDir?.let { if (it.exists()) deleteDirectoryContents(it) }
            PassphraseManager.clearStoredPassphrase(context)
            Log.i(TAG, "✅ App reset cleanup successful")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to clear data for reset", e)
            Result.failure(e)
        }
    }
}

/**
 * Describes what [DataCleanupManager.wipeLocalAndPreviousRemoteNotes] did.
 * Safe to log — [previousIdentity] is null in non-DEBUG builds.
 */
data class CleanupSummary(
    val localNotesDeleted: Boolean,
    val remoteAttempted: Boolean,
    val remoteSucceeded: Boolean,
    /** Only populated in DEBUG builds to avoid leaking passphrase to logs. */
    val previousIdentity: String?
)
