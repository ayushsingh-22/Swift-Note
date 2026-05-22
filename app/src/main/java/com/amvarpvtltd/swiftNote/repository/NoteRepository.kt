package com.amvarpvtltd.swiftNote.repository

import android.content.Context
import android.util.Log
import com.amvarpvtltd.swiftNote.dataclass
import com.amvarpvtltd.swiftNote.offline.OfflineNoteManager
import com.amvarpvtltd.swiftNote.auth.DeviceManager
import com.amvarpvtltd.swiftNote.auth.PassphraseManager
import com.amvarpvtltd.swiftNote.room.AppDatabase
import com.amvarpvtltd.swiftNote.room.NoteEntityMapper
import com.amvarpvtltd.swiftNote.utils.NetworkManager
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class NoteRepository(val context: Context? = null) {
    private val database = FirebaseDatabase.getInstance()
    // BUG-018 FIX: Cache OfflineNoteManager instance — avoids re-creating per call
    private val offlineManager: OfflineNoteManager? = context?.let { OfflineNoteManager(it) }

    companion object {
        private const val TAG = "NoteRepository"
        // Mutex to prevent concurrent sync operations from colliding
        private val syncMutex = Mutex()

        // BUG-040 FIX: Singleton instance to avoid redundant repository/manager creation
        @Volatile
        private var INSTANCE: NoteRepository? = null

        fun getInstance(context: Context): NoteRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: NoteRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
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
     * Observe all notes reactively via Room Flow.
     * The list updates automatically when the database changes.
     */
    fun observeNotes(): Flow<List<dataclass>> {
        require(context != null) { "No context available for observeNotes" }
        val db = AppDatabase.getInstance(context)
        return db.noteDao().observeAllNotes()
            .map { entities -> entities.map { NoteEntityMapper.toDomain(it) } }
            .flowOn(Dispatchers.IO)
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
        // Always update the updatedAt timestamp on save/update
        note.updatedAt = System.currentTimeMillis()

        // ALWAYS save to Room database first (offline-first)
        val manager = offlineManager ?: return Result.failure(Exception("No context available"))
        val offlineResult = manager.saveNoteOffline(note)

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
        val manager = offlineManager
        if (manager != null) {
            try {
                Log.d(TAG, "Attempting to load note from offline storage: $noteId")
                val offlineNote = manager.getNoteById(noteId)
                if (offlineNote != null) {
                    Log.d(TAG, "Note loaded successfully from offline storage: $noteId")
                    return Result.success(offlineNote)
                } else {
                    Log.w(TAG, "Note NOT found in offline storage: $noteId")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error accessing offline storage for note: $noteId", e)
            }
        } else {
            Log.w(TAG, "No context/offlineManager available for offline storage access")
        }

        // Fallback to online storage only if not found offline and online
        val networkManager = context?.let { NetworkManager.getInstance(it) }
        val isOnline = networkManager?.isConnected() == true

        if (isOnline) {
            Log.d(TAG, "Attempting to load note from cloud: $noteId")
            return try {
                val snapshot = resolveNotesRef().child(noteId).get().await()
                if (snapshot.exists()) {
                    val encryptedNote = snapshot.getValue(dataclass::class.java)
                    if (encryptedNote != null) {
                        val decryptedNote = dataclass.fromEncryptedData(encryptedNote)
                        Log.d(TAG, "Note loaded from cloud: $noteId")

                        // Save to offline storage for future access
                        try {
                            offlineManager?.saveNoteOffline(decryptedNote, synced = true)
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to cache note offline: ${e.message}")
                        }

                        Result.success(decryptedNote)
                    } else {
                        Result.failure(Exception("Note data is null"))
                    }
                } else {
                    Result.failure(Exception("Note not found in cloud"))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load note from cloud: ${e.message}", e)
                Result.failure(e)
            }
        } else {
            return Result.failure(Exception("Note not found offline and device is offline. Please check your internet connection or ensure the note exists locally."))
        }
    }

    /**
     * Delete a note by ID - OFFLINE FIRST
     */
    suspend fun deleteNote(noteId: String, context: Context): Result<String> {
        // Always delete from offline storage first
        val manager = offlineManager ?: return Result.failure(Exception("No context available"))
        val offlineResult = manager.deleteNoteOffline(noteId)

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
            val manager = offlineManager
            if (manager != null) {
                val offlineNotes = manager.getAllNotes()
                Log.d(TAG, "Loaded ${offlineNotes.size} notes from offline storage")

                // Background sync from cloud if online
                val ctx = context!!
                val networkManager = NetworkManager.getInstance(ctx)
                if (networkManager.isConnected()) {
                    try {
                        syncFromCloudInBackground(manager)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error in background cloud sync", e)
                    }
                }

                Result.success(offlineNotes)
            } else {
                Result.failure(Exception("No context available"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch notes: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Background sync from cloud to update local database.
     * Handles pending deletions first, then downloads cloud data.
     * Uses timestamp-based conflict resolution: local edits (newer) win over cloud (older).
     */
    private suspend fun syncFromCloudInBackground(offlineManager: OfflineNoteManager) {
        try {
            Log.d(TAG, "Starting background sync from cloud...")

            // FIRST: Process pending deletions before downloading from cloud
            val pendingDeletions = offlineManager.getPendingDeletions()
            Log.d(TAG, "Found ${pendingDeletions.size} pending deletions to process")

            pendingDeletions.forEach { pendingDeletion ->
                try {
                    resolveNotesRef().child(pendingDeletion.noteId).removeValue().await()
                    offlineManager.markDeletionAsSynced(pendingDeletion.noteId)
                    Log.d(TAG, "Successfully deleted from Firebase: ${pendingDeletion.noteId}")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to delete from Firebase: ${pendingDeletion.noteId}", e)
                }
            }

            // SECOND: Get current local notes to check for conflicts
            val localNotes = offlineManager.getAllNotes()
            val localNoteMap = localNotes.associateBy { it.id }
            val pendingDeletionIds = pendingDeletions.map { it.noteId }.toSet()

            // Also get pending (unsynced) note IDs — these represent local edits that should NOT be overwritten
            val pendingSyncNotes = offlineManager.getPendingSyncNotes()
            val pendingSyncIds = pendingSyncNotes.map { it.id }.toSet()

            // THIRD: Download from Firebase
            val snapshot = resolveNotesRef().get().await()
            val cloudNotes = mutableListOf<dataclass>()

            snapshot.children.forEach { childSnapshot ->
                try {
                    val encryptedNote = childSnapshot.getValue(dataclass::class.java)
                    if (encryptedNote != null) {
                        val decryptedNote = dataclass.fromEncryptedData(encryptedNote)

                        // Skip cloud notes that are pending local deletion
                        if (pendingDeletionIds.contains(decryptedNote.id)) {
                            Log.d(TAG, "Skipping cloud note ${decryptedNote.id} - pending deletion")
                            return@forEach
                        }

                        // CONFLICT RESOLUTION: If note exists locally with pending changes,
                        // keep the local version (last-write-wins by user intent).
                        // The local pending change will be uploaded on next sync.
                        if (pendingSyncIds.contains(decryptedNote.id)) {
                            Log.d(TAG, "Skipping cloud note ${decryptedNote.id} - local pending edit takes priority")
                            return@forEach
                        }

                        // Timestamp-based conflict resolution for synced notes:
                        // Only overwrite if cloud is newer than local
                        val localNote = localNoteMap[decryptedNote.id]
                        if (localNote != null && localNote.timestamp > decryptedNote.timestamp) {
                            Log.d(TAG, "Skipping cloud note ${decryptedNote.id} - local is newer (${localNote.timestamp} > ${decryptedNote.timestamp})")
                            return@forEach
                        }

                        cloudNotes.add(decryptedNote)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error decrypting note: ${childSnapshot.key}", e)
                }
            }

            // FOURTH: Update local database with cloud notes that passed conflict checks
            cloudNotes.forEach { note ->
                offlineManager.saveNoteOffline(note, synced = true)
            }

            Log.d(TAG, "Background sync completed: ${cloudNotes.size} notes from cloud, ${pendingDeletions.size} deletions processed")
        } catch (e: Exception) {
            Log.w(TAG, "Background sync from cloud failed: ${e.message}")
        }
    }

    /**
     * Sync offline notes and deletions to Firebase.
     * Protected by syncMutex to prevent concurrent sync operations.
     * Each item is synced individually — if app crashes mid-sync, already-synced items
     * are safely marked, and remaining items will retry on next sync (idempotent).
     */
    suspend fun syncOfflineNotes(context: Context): Result<String> = withContext(Dispatchers.IO) {
        syncMutex.withLock {
            val manager = offlineManager ?: return@withContext Result.failure(Exception("No context"))
            val pendingNotes = manager.getPendingSyncNotes()
            val pendingDeletions = manager.getPendingDeletions()

            if (pendingNotes.isEmpty() && pendingDeletions.isEmpty()) {
                return@withContext Result.success("No changes to sync")
            }

            return@withContext try {
                var syncedNotesCount = 0
                var syncedDeletionsCount = 0

                // Sync pending note additions/updates — each marked as synced immediately after success
                // This ensures crash between items doesn't re-sync already completed ones
                pendingNotes.forEach { note ->
                    try {
                        val encryptedNote = note.toEncryptedData()
                        resolveNotesRef().child(note.id).setValue(encryptedNote).await()
                        // Mark immediately after successful upload — crash-safe
                        offlineManager.markNoteAsSynced(note.id)
                        syncedNotesCount++
                        Log.d(TAG, "Synced note to cloud: ${note.id}")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to sync note: ${note.id}", e)
                        // Continue with next note — don't fail entire batch
                    }
                }

                // Sync pending deletions — each cleared immediately after success
                pendingDeletions.forEach { pendingDeletion ->
                    try {
                        resolveNotesRef().child(pendingDeletion.noteId).removeValue().await()
                        // Mark immediately after successful deletion — crash-safe
                        offlineManager.markDeletionAsSynced(pendingDeletion.noteId)
                        syncedDeletionsCount++
                        Log.d(TAG, "Synced deletion to cloud: ${pendingDeletion.noteId}")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to sync deletion: ${pendingDeletion.noteId}", e)
                        // Continue with next deletion — don't fail entire batch
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
}
