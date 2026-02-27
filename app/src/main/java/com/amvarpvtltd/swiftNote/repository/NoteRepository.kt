package com.amvarpvtltd.swiftNote.repository

import android.content.Context
import android.util.Log
import com.amvarpvtltd.swiftNote.dataclass
import com.amvarpvtltd.swiftNote.offline.OfflineNoteManager
import com.amvarpvtltd.swiftNote.auth.DeviceManager
import com.amvarpvtltd.swiftNote.auth.PassphraseManager
import com.amvarpvtltd.swiftNote.utils.NetworkManager
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.tasks.await

class NoteRepository(val context: Context? = null) {
    private val database = FirebaseDatabase.getInstance()

    companion object {
        private const val TAG = "NoteRepository"
    }

    /**
     * Resolve the Firebase path for notes:
     * users/{accountId}/notes/{deviceId}
     * accountId = stored passphrase if present, otherwise deviceId
     */
    private fun resolveNotesRef(): DatabaseReference {
        val ctx = context ?: throw IllegalStateException("Context required to resolve notesRef")
        val deviceId = DeviceManager.getOrCreateDeviceId(ctx)
        val accountId = PassphraseManager.getStoredPassphrase(ctx).takeIf { !it.isNullOrEmpty() } ?: deviceId
        return database.getReference("users").child(accountId).child("notes").child(deviceId)
    }

    /**
     * Save a new note or update an existing one - OFFLINE FIRST
     * Always saves to Room database first, then attempts cloud sync
     */
    suspend fun saveNote(
        title: String,
        description: String,
        noteId: String? = null,
        context: Context
    ): Result<String> {
        val note = dataclass(title = title.trim(), description = description.trim())
        // Ensure note carries the current device id
        val deviceIdForNote = DeviceManager.getOrCreateDeviceId(context)
        note.mymobiledeviceid = deviceIdForNote
        if (noteId != null) {
            note.id = noteId
        }

        // ALWAYS save to Room database first (offline-first)
        val offlineManager = OfflineNoteManager(context)
        val offlineResult = offlineManager.saveNoteOffline(note)

        if (offlineResult.isFailure) {
            Log.e(TAG, "Failed to save note offline: ${offlineResult.exceptionOrNull()?.message}")
            return offlineResult
        }

        Log.d(TAG, "Note saved offline successfully: ${note.id}")

        // Try to sync to cloud in background if online
        val networkManager = NetworkManager.getInstance(context)
        if (networkManager.isConnected()) {
            try {
                val encryptedNote = note.toEncryptedData()
                resolveNotesRef().child(note.id).setValue(encryptedNote).await()
                Log.d(TAG, "Note synced to cloud successfully: ${note.id}")

                // Mark as synced in local database
                offlineManager.markNoteAsSynced(note.id)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to sync note to cloud (will retry later): ${e.message}")
                // Note is still saved offline, so this is acceptable
            }
        } else {
            Log.d(TAG, "Offline mode - note will sync when connection is restored")
        }

        return Result.success(note.id)
    }

    /**
     * Load a note by ID - OFFLINE FIRST
     * Always loads from Room database first
     */
    suspend fun loadNote(noteId: String, context: Context? = null): Result<dataclass> {
        Log.d(TAG, "Loading note with ID: $noteId")

        // Always try offline storage first (offline-first)
        context?.let { ctx ->
            try {
                val offlineManager = OfflineNoteManager(ctx)
                Log.d(TAG, "Attempting to load note from offline storage: $noteId")

                val offlineNote = offlineManager.getNoteById(noteId)
                if (offlineNote != null) {
                    Log.d(TAG, "✅ Note loaded successfully from offline storage: $noteId")
                    Log.d(TAG, "Note title: ${offlineNote.title}")
                    Log.d(TAG, "Note description length: ${offlineNote.description.length}")
                    return Result.success(offlineNote)
                } else {
                    Log.w(TAG, "❌ Note NOT found in offline storage: $noteId")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error accessing offline storage for note: $noteId", e)
            }
        } ?: run {
            Log.w(TAG, "❌ No context provided for offline storage access")
        }

        // Fallback to online storage only if not found offline and online
        val networkManager = context?.let { NetworkManager.getInstance(it) }
        val isOnline = networkManager?.isConnected() == true

        Log.d(TAG, "Offline search failed. Network status: ${if (isOnline) "ONLINE" else "OFFLINE"}")

        if (isOnline) {
            Log.d(TAG, "Attempting to load note from cloud: $noteId")
            return try {
                val snapshot = resolveNotesRef().child(noteId).get().await()
                if (snapshot.exists()) {
                    val encryptedNote = snapshot.getValue(dataclass::class.java)
                    if (encryptedNote != null) {
                        val decryptedNote = dataclass.fromEncryptedData(encryptedNote)
                        Log.d(TAG, "✅ Note loaded from cloud: $noteId")

                        // Save to offline storage for future access
                        context?.let { ctx ->
                            try {
                                val offlineManager = OfflineNoteManager(ctx)
                                offlineManager.saveNoteOffline(decryptedNote, synced = true)
                                Log.d(TAG, "✅ Note cached offline for future access: $noteId")
                            } catch (e: Exception) {
                                Log.w(TAG, "⚠️ Failed to cache note offline: ${e.message}")
                            }
                        }

                        Result.success(decryptedNote)
                    } else {
                        Log.e(TAG, "❌ Note data is null for ID: $noteId")
                        Result.failure(Exception("Note data is null"))
                    }
                } else {
                    Log.e(TAG, "❌ Note not found in cloud: $noteId")
                    Result.failure(Exception("Note not found in cloud"))
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to load note from cloud: ${e.message}", e)
                Result.failure(e)
            }
        } else {
            Log.e(TAG, "❌ Note not found offline and device is offline")
            return Result.failure(Exception("Note not found offline and device is offline. Please check your internet connection or ensure the note exists locally."))
        }
    }

    /**
     * Delete a note by ID - OFFLINE FIRST
     */
    suspend fun deleteNote(noteId: String, context: Context): Result<String> {
        // Always delete from offline storage first
        val offlineManager = OfflineNoteManager(context)
        val offlineResult = offlineManager.deleteNoteOffline(noteId)

        if (offlineResult.isFailure) {
            Log.e(TAG, "Failed to delete note offline: ${offlineResult.exceptionOrNull()?.message}")
            return offlineResult
        }

        Log.d(TAG, "Note deleted offline successfully: $noteId")

        // Try to delete from cloud if online
        val networkManager = NetworkManager.getInstance(context)
        if (networkManager.isConnected()) {
            try {
                resolveNotesRef().child(noteId).removeValue().await()
                Log.d(TAG, "Note deleted from cloud successfully: $noteId")

                // Mark deletion as synced (remove from pending deletions)
                offlineManager.markDeletionAsSynced(noteId)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to delete note from cloud (will retry later): ${e.message}")
                // Note is already deleted offline and marked for sync, which is acceptable
            }
        } else {
            Log.d(TAG, "Offline mode - note deleted locally and will sync deletion when online")
        }

        return Result.success(noteId)
    }

    /**
     * Get all notes - OFFLINE FIRST
     * Always loads from Room database for immediate display
     */
    suspend fun fetchNotes(): Result<List<dataclass>> {
        return try {
            // ALWAYS load from offline storage first for immediate display
            context?.let { ctx ->
                val offlineManager = OfflineNoteManager(ctx)
                val offlineNotes = offlineManager.getAllNotes()
                Log.d(TAG, "Loaded ${offlineNotes.size} notes from offline storage")

                // Background sync from cloud if online
                val networkManager = NetworkManager.getInstance(ctx)
                if (networkManager.isConnected()) {
                    syncFromCloudInBackground(offlineManager)
                }

                Result.success(offlineNotes)
            } ?: Result.failure(Exception("No context available"))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch notes: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Background sync from cloud to update local database
     * Now handles pending deletions properly
     */
    private suspend fun syncFromCloudInBackground(offlineManager: OfflineNoteManager) {
        try {
            Log.d(TAG, "Starting background sync from cloud...")

            // FIRST: Process pending deletions before downloading from cloud
            val pendingDeletions = offlineManager.getPendingDeletions()
            Log.d(TAG, "Found ${pendingDeletions.size} pending deletions to process")

            pendingDeletions.forEach { pendingDeletion ->
                try {
                    Log.d(TAG, "Processing pending deletion: ${pendingDeletion.noteId}")
                    resolveNotesRef().child(pendingDeletion.noteId).removeValue().await()
                    offlineManager.markDeletionAsSynced(pendingDeletion.noteId)
                    Log.d(TAG, "✅ Successfully deleted from Firebase: ${pendingDeletion.noteId}")
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Failed to delete from Firebase: ${pendingDeletion.noteId}", e)
                }
            }

            // SECOND: Get current local notes to avoid overriding deletions
            val localNotes = offlineManager.getAllNotes()
            val localNoteIds = localNotes.map { it.id }.toSet()
            val pendingDeletionIds = pendingDeletions.map { it.noteId }.toSet()

            // THIRD: Download from Firebase
            val snapshot = resolveNotesRef().get().await()
            val cloudNotes = mutableListOf<dataclass>()

            snapshot.children.forEach { childSnapshot ->
                try {
                    val encryptedNote = childSnapshot.getValue(dataclass::class.java)
                    if (encryptedNote != null) {
                        val decryptedNote = dataclass.fromEncryptedData(encryptedNote)

                        // Only add cloud notes that aren't pending deletion and aren't already deleted locally
                        if (!pendingDeletionIds.contains(decryptedNote.id)) {
                            cloudNotes.add(decryptedNote)
                        } else {
                            Log.d(TAG, "Skipping cloud note ${decryptedNote.id} - pending deletion")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error decrypting note: ${childSnapshot.key}", e)
                }
            }

            // FOURTH: Update local database with cloud notes (excluding deleted ones)
            cloudNotes.forEach { note ->
                // Only save if not in pending deletions
                if (!pendingDeletionIds.contains(note.id)) {
                    offlineManager.saveNoteOffline(note, synced = true)
                }
            }

            Log.d(TAG, "Background sync completed: ${cloudNotes.size} notes from cloud, ${pendingDeletions.size} deletions processed")
        } catch (e: Exception) {
            Log.w(TAG, "Background sync from cloud failed: ${e.message}")
        }
    }

    /**
     * Sync offline notes and deletions to Firebase
     */
    suspend fun syncOfflineNotes(context: Context): Result<String> {
        val offlineManager = OfflineNoteManager(context)
        val pendingNotes = offlineManager.getPendingSyncNotes()
        val pendingDeletions = offlineManager.getPendingDeletions()

        if (pendingNotes.isEmpty() && pendingDeletions.isEmpty()) {
            return Result.success("No changes to sync")
        }

        return try {
            var syncedNotesCount = 0
            var syncedDeletionsCount = 0

            // Sync pending note additions/updates
            pendingNotes.forEach { note ->
                try {
                    val encryptedNote = note.toEncryptedData()
                    resolveNotesRef().child(note.id).setValue(encryptedNote).await()
                    offlineManager.markNoteAsSynced(note.id)
                    syncedNotesCount++
                    Log.d(TAG, "✅ Synced note to cloud: ${note.id}")
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Failed to sync note: ${note.id}", e)
                }
            }

            // Sync pending deletions
            pendingDeletions.forEach { pendingDeletion ->
                try {
                    resolveNotesRef().child(pendingDeletion.noteId).removeValue().await()
                    offlineManager.markDeletionAsSynced(pendingDeletion.noteId)
                    syncedDeletionsCount++
                    Log.d(TAG, "✅ Synced deletion to cloud: ${pendingDeletion.noteId}")
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Failed to sync deletion: ${pendingDeletion.noteId}", e)
                }
            }

            val resultMessage = buildString {
                if (syncedNotesCount > 0) {
                    append("Synced $syncedNotesCount note${if (syncedNotesCount != 1) "s" else ""}")
                }
                if (syncedDeletionsCount > 0) {
                    if (syncedNotesCount > 0) append(" and ")
                    append("deleted $syncedDeletionsCount note${if (syncedDeletionsCount != 1) "s" else ""}")
                }
            }

            Log.d(TAG, "Sync completed: $resultMessage")
            Result.success(resultMessage)
        } catch (e: Exception) {
            Log.e(TAG, "Sync failed: ${e.message}", e)
            Result.failure(e)
        }
    }
}
