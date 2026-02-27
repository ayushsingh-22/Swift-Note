package com.amvarpvtltd.swiftNote.cleanup

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import com.amvarpvtltd.swiftNote.auth.PassphraseManager
import com.amvarpvtltd.swiftNote.room.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Handles cleanup of all app data when the app is uninstalled or reset
 */
object DataCleanupManager {
    private const val TAG = "DataCleanupManager"

    /**
     * Clear all app data including database, preferences, and files
     */
    suspend fun clearAllAppData(context: Context): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext try {
            Log.d(TAG, "Starting complete app data cleanup...")

            // 1. Clear Room database
            clearDatabase(context)

            // 2. Clear all SharedPreferences
            clearSharedPreferences(context)

            // 3. Clear app cache and files
            clearAppFiles(context)

            // 4. Clear passphrase data
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

            // Clear all tables
            db.noteDao().run {
                val notes = getAllNotes()
                notes.forEach { delete(it) }
            }

            db.reminderDao().run {
                val reminders = getAllReminders()
                reminders.forEach { reminder ->
                    // deleteReminder accepts ReminderEntity
                    deleteReminder(reminder)
                }
            }

            db.pendingDeletionDao().run {
                // Clear pending deletions via DAO helper
                clearAllPendingDeletions()
            }

            // Close database
            db.close()

            // Delete database files
            val dbPath = context.getDatabasePath("notes_database")
            if (dbPath.exists()) {
                dbPath.delete()
                Log.d(TAG, "Database file deleted")
            }

            // Delete WAL and SHM files
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
            // Get all preference files in the app
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

            // Specifically clear known preference files
            val knownPrefs = listOf(
                "passphrase_prefs",
                "device_prefs",
                "theme_prefs",
                "app_settings",
                "reminder_prefs"
            )

            knownPrefs.forEach { prefName ->
                try {
                    val prefs = context.getSharedPreferences(prefName, Context.MODE_PRIVATE)
                    prefs.edit { clear() }
                    Log.d(TAG, "Cleared known preferences: $prefName")
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
            // Clear cache directory
            context.cacheDir?.let { cacheDir ->
                if (cacheDir.exists()) {
                    deleteDirectoryContents(cacheDir)
                    Log.d(TAG, "Cache directory cleared")
                }
            }

            // Clear external cache directory
            context.externalCacheDir?.let { externalCacheDir ->
                if (externalCacheDir.exists()) {
                    deleteDirectoryContents(externalCacheDir)
                    Log.d(TAG, "External cache directory cleared")
                }
            }

            // Clear files directory
            context.filesDir?.let { filesDir ->
                if (filesDir.exists()) {
                    deleteDirectoryContents(filesDir)
                    Log.d(TAG, "Files directory cleared")
                }
            }

            // Clear external files directory
            context.getExternalFilesDir(null)?.let { externalFilesDir ->
                if (externalFilesDir.exists()) {
                    deleteDirectoryContents(externalFilesDir)
                    Log.d(TAG, "External files directory cleared")
                }
            }

            Log.d(TAG, "✅ App files cleared successfully")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to clear app files", e)
            throw e
        }
    }

    private fun clearPassphraseData(context: Context) {
        try {
            // Clear stored passphrase
            PassphraseManager.clearStoredPassphrase(context)

            // Clear device manager data if it exists
            try {
                val devicePrefs = context.getSharedPreferences("device_prefs", Context.MODE_PRIVATE)
                devicePrefs.edit { clear() }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to clear device preferences", e)
            }

            Log.d(TAG, "✅ Passphrase data cleared successfully")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to clear passphrase data", e)
            throw e
        }
    }

    private fun deleteDirectoryContents(directory: File) {
        if (!directory.exists() || !directory.isDirectory) return

        directory.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                deleteDirectoryContents(file)
                file.delete()
            } else {
                file.delete()
            }
        }
    }

    /**
     * Clear data for app reset (keeps some settings)
     */
    suspend fun clearDataForReset(context: Context): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext try {
            Log.d(TAG, "Starting app reset data cleanup...")

            // Clear database but keep theme preferences
            clearDatabase(context)

            // Clear cache but not settings
            context.cacheDir?.let { cacheDir ->
                if (cacheDir.exists()) {
                    deleteDirectoryContents(cacheDir)
                }
            }

            // Clear passphrase but keep device settings
            PassphraseManager.clearStoredPassphrase(context)

            Log.i(TAG, "✅ App reset cleanup successful")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to clear data for reset", e)
            Result.failure(e)
        }
    }
}
