package com.amvarpvtltd.swiftNote.offline

import android.content.Context
import android.util.Log
import com.amvarpvtltd.swiftNote.dataclass
import com.amvarpvtltd.swiftNote.room.AppDatabase
import com.amvarpvtltd.swiftNote.room.NoteEntityMapper
import com.amvarpvtltd.swiftNote.room.PendingDeletionEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class OfflineNoteManager(context: Context) {
    private val TAG = "OfflineNoteManager"
    private val db = AppDatabase.getInstance(context.applicationContext)
    private val noteDao = db.noteDao()
    private val pendingDeletionDao = db.pendingDeletionDao()

    private val _offlineNotes = MutableStateFlow<List<dataclass>>(emptyList())
    val offlineNotes: StateFlow<List<dataclass>> = _offlineNotes.asStateFlow()

    private val _pendingSyncNotes = MutableStateFlow<List<dataclass>>(emptyList())
    val pendingSyncNotes: StateFlow<List<dataclass>> = _pendingSyncNotes.asStateFlow()

    private val _pendingDeletions = MutableStateFlow<List<PendingDeletionEntity>>(emptyList())
    val pendingDeletions: StateFlow<List<PendingDeletionEntity>> = _pendingDeletions.asStateFlow()

    init {
        // Load data from DB
        // initial load
        CoroutineScope(Dispatchers.IO).launch {
            refreshLocalNotes()
            refreshPendingNotes()
            refreshPendingDeletions()
        }
    }

    suspend fun saveNoteOffline(note: dataclass, synced: Boolean = false): Result<String> {
        return try {
            withContext(Dispatchers.IO) {
                val entity = NoteEntityMapper.toEntity(note, synced)
                noteDao.insert(entity)
            }
            refreshLocalNotes()
            refreshPendingNotes()
            Log.d(TAG, "Note saved offline: ${note.id} (synced: $synced)")
            Result.success("Note saved offline successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving note offline", e)
            Result.failure(e)
        }
    }

    suspend fun deleteNoteOffline(noteId: String): Result<String> {
        return try {
            withContext(Dispatchers.IO) {
                // First, check if the note exists and get its device ID
                val noteEntity = noteDao.getNoteById(noteId)
                if (noteEntity != null) {
                    // Delete the note from local storage
                    noteDao.delete(noteEntity)

                    // Add to pending deletions for Firebase sync
                    val pendingDeletion = PendingDeletionEntity(
                        noteId = noteId,
                        mymobiledeviceid = noteEntity.mymobiledeviceid
                    )
                    pendingDeletionDao.insert(pendingDeletion)

                    Log.d(TAG, "Note deleted offline and marked for Firebase deletion: $noteId")
                } else {
                    Log.w(TAG, "Note not found for deletion: $noteId")
                }
            }
            refreshLocalNotes()
            refreshPendingNotes()
            refreshPendingDeletions()
            Result.success("Note deleted offline successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting note offline", e)
            Result.failure(e)
        }
    }

    suspend fun getNoteById(noteId: String): dataclass? {
        return try {
            Log.d(TAG, "Attempting to get note by ID: $noteId")

            // Ensure we're running on IO dispatcher for database operations
            val entity = withContext(Dispatchers.IO) {
                noteDao.getNoteById(noteId)
            }

            if (entity != null) {
                Log.d(TAG, "Found note entity in database: $noteId")
                val domainNote = NoteEntityMapper.toDomain(entity)
                Log.d(TAG, "Successfully converted entity to domain object: ${domainNote.title}")
                domainNote
            } else {
                Log.w(TAG, "No note found in database for ID: $noteId")

                // Debug: Check if any notes exist in database
                val allNotes = withContext(Dispatchers.IO) {
                    noteDao.getAllNotes()
                }
                Log.d(TAG, "Total notes in database: ${allNotes.size}")
                allNotes.forEach { note ->
                    Log.d(TAG, "Available note ID: ${note.id}")
                }

                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting note by ID: $noteId", e)
            null
        }
    }

    suspend fun getAllNotes(): List<dataclass> {
        return try {
            withContext(Dispatchers.IO) {
                val entities = noteDao.getAllNotes()
                entities.map { NoteEntityMapper.toDomain(it) }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting all notes", e)
            emptyList()
        }
    }

    suspend fun getPendingSyncNotes(): List<dataclass> {
        return try {
            withContext(Dispatchers.IO) {
                val pending = noteDao.getPendingNotes()
                pending.map { NoteEntityMapper.toDomain(it) }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting pending sync notes", e)
            emptyList()
        }
    }

    suspend fun getPendingDeletions(): List<PendingDeletionEntity> {
        return try {
            withContext(Dispatchers.IO) {
                pendingDeletionDao.getAllPendingDeletions()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting pending deletions", e)
            emptyList()
        }
    }

    suspend fun markNoteAsSynced(noteId: String): Result<String> {
        return try {
            withContext(Dispatchers.IO) {
                val entity = noteDao.getNoteById(noteId)
                if (entity != null) {
                    val syncedEntity = entity.copy(synced = true)
                    noteDao.update(syncedEntity)
                    Log.d(TAG, "Note marked as synced: $noteId")
                } else {
                    Log.w(TAG, "Note not found for sync marking: $noteId")
                    return@withContext Result.failure<String>(Exception("Note not found"))
                }
            }
            refreshPendingNotes()
            Result.success("Note marked as synced")
        } catch (e: Exception) {
            Log.e(TAG, "Error marking note as synced: $noteId", e)
            Result.failure(e)
        }
    }

    suspend fun markDeletionAsSynced(noteId: String): Result<String> {
        return try {
            withContext(Dispatchers.IO) {
                val removedCount = pendingDeletionDao.removePendingDeletion(noteId)
                if (removedCount > 0) {
                    Log.d(TAG, "Pending deletion removed for synced note: $noteId")
                } else {
                    Log.w(TAG, "No pending deletion found for note: $noteId")
                }
            }
            refreshPendingDeletions()
            Result.success("Deletion marked as synced")
        } catch (e: Exception) {
            Log.e(TAG, "Error marking deletion as synced: $noteId", e)
            Result.failure(e)
        }
    }

    fun hasPendingSync(): Boolean = pendingSyncNotes.value.isNotEmpty() || pendingDeletions.value.isNotEmpty()

    private suspend fun refreshLocalNotes() {
        try {
            withContext(Dispatchers.IO) {
                val entities = noteDao.getAllNotes()
                _offlineNotes.value = entities.map { NoteEntityMapper.toDomain(it) }
                Log.d(TAG, "Refreshed local notes: ${entities.size}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error refreshing local notes", e)
        }
    }

    private suspend fun refreshPendingNotes() {
        try {
            withContext(Dispatchers.IO) {
                val pending = noteDao.getPendingNotes()
                _pendingSyncNotes.value = pending.map { NoteEntityMapper.toDomain(it) }
                Log.d(TAG, "Refreshed pending sync notes: ${pending.size}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error refreshing pending notes", e)
        }
    }

    private suspend fun refreshPendingDeletions() {
        try {
            withContext(Dispatchers.IO) {
                val pendingDeletions = pendingDeletionDao.getAllPendingDeletions()
                _pendingDeletions.value = pendingDeletions
                Log.d(TAG, "Refreshed pending deletions: ${pendingDeletions.size}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error refreshing pending deletions", e)
        }
    }

    fun clearSyncedNotes() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val pending = noteDao.getPendingNotes()
                pending.forEach { noteDao.update(it.copy(synced = true)) }
                refreshPendingNotes()
                Log.d(TAG, "Cleared synced notes from pending list")
            } catch (e: Exception) {
                Log.e(TAG, "Error clearing synced notes", e)
            }
        }
    }

    // Reassign all local notes to a new deviceId and mark them pending sync
    suspend fun reassignDeviceId(newDeviceId: String) {
        withContext(Dispatchers.IO) {
            val all = noteDao.getAllNotes()
            all.forEach { entity ->
                val updated = entity.copy(mymobiledeviceid = newDeviceId, synced = false)
                noteDao.update(updated)
            }
        }
        refreshLocalNotes()
        refreshPendingNotes()
        Log.d(TAG, "Reassigned ${offlineNotes.value.size} notes to new deviceId")
    }
}
