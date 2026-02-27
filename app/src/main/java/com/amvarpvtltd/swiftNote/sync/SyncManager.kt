package com.amvarpvtltd.swiftNote.sync

import android.content.Context
import android.util.Log
import com.amvarpvtltd.swiftNote.auth.PassphraseManager
import com.amvarpvtltd.swiftNote.dataclass
import com.amvarpvtltd.swiftNote.reminders.ReminderEntity
import com.amvarpvtltd.swiftNote.room.AppDatabase
import com.amvarpvtltd.swiftNote.room.NoteEntityMapper
import com.amvarpvtltd.swiftNote.security.EncryptionUtil
import com.amvarpvtltd.swiftNote.myGlobalMobileDeviceId
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

object SyncManager {
    private const val TAG = "SyncManager"

    /**
     * Sync data from another device's passphrase to current device
     * This is a one-time copy operation, not continuous sync
     */
    suspend fun syncDataFromPassphrase(
        context: Context,
        sourcePassphrase: String,
        currentPassphrase: String
    ): Result<SyncResult> = withContext(Dispatchers.IO) {
        // Ensure app is authenticated before accessing Firebase
        val authResult = PassphraseManager.ensureAuthenticated()
        if (authResult.isFailure) {
            val ex = authResult.exceptionOrNull()
            Log.e(TAG, "Auth failed before sync: ${ex?.message}")
            return@withContext Result.failure(Exception("Authentication failed: ${ex?.message ?: "unknown"}"))
        }

        return@withContext try {
            val database = FirebaseDatabase.getInstance()
            val sourceRef = database.getReference("users").child(sourcePassphrase)

            // Fetch all notes from source device
            val notesSnapshot = sourceRef.child("notes").get().await()
            val remindersSnapshot = sourceRef.child("reminders").get().await()

            val syncedNotes = mutableListOf<dataclass>()
            // Keep reminder processing but no longer collect counts for stats

            // Get local database
            val db = AppDatabase.getInstance(context)
            val noteDao = db.noteDao()
            val reminderDao = db.reminderDao()

            // Helper to attempt decryption with candidate keys
            fun tryDecryptWithCandidates(encrypted: dataclass, candidates: List<String>): dataclass? {
                // If the title/description don't look encrypted, treat as plaintext
                val looksEncrypted = EncryptionUtil.isPotentiallyEncrypted(encrypted.title) || EncryptionUtil.isPotentiallyEncrypted(encrypted.description)

                if (!looksEncrypted) {
                    // Already plaintext
                    return dataclass(
                        title = encrypted.title,
                        description = encrypted.description,
                        id = encrypted.id,
                        mymobiledeviceid = encrypted.mymobiledeviceid,
                        timestamp = encrypted.timestamp
                    )
                }

                for (key in candidates) {
                    try {
                        val t = EncryptionUtil.decrypt(encrypted.title, key)
                        val d = EncryptionUtil.decrypt(encrypted.description, key)
                        if (t != null && d != null) {
                            Log.d(TAG, "Decryption successful for note ${encrypted.id} using key candidate: ${key.take(20)}")
                            return dataclass(title = t, description = d, id = encrypted.id, mymobiledeviceid = encrypted.mymobiledeviceid, timestamp = encrypted.timestamp)
                        }
                    } catch (e: Exception) {
                        Log.d(TAG, "Candidate key decryption failed for note ${encrypted.id}", e)
                    }
                }

                // For each candidate key, try many normalized variants to tolerate formatting/truncation differences
                for (rawKey in candidates) {
                    if (rawKey.isBlank()) continue
                    val vset = mutableSetOf<String>()
                    val k = rawKey.trim()
                    vset.add(k)
                    vset.add(k.lowercase())
                    vset.add(k.uppercase())
                    vset.add(k.replace("-", ""))
                    vset.add(k.replace(Regex("[^A-Za-z0-9]"), ""))

                    // try substrings/prefixes commonly used (16, 20, 32 chars)
                    val cleaned = k.replace(Regex("[^A-Za-z0-9]"), "")
                    listOf(16, 20, 32).forEach { n -> if (cleaned.length >= n) vset.add(cleaned.substring(0, n)) }

                    // URL-decoded variant
                    try { vset.add(java.net.URLDecoder.decode(k, "UTF-8").trim()) } catch (_: Exception) {}

                    // base64-decoded candidate if it looks like base64
                    try {
                        if (k.matches(Regex("^[A-Za-z0-9+/=]+$") )) {
                            val decoded = String(java.util.Base64.getDecoder().decode(k))
                            vset.add(decoded)
                        }
                    } catch (_: Exception) {}

                    // Now try each variant
                    for (variant in vset) {
                        try {
                            val preview = EncryptionUtil.getKeyPreview(variant)
                            Log.d(TAG, "Attempting decryption for note ${encrypted.id} using key variant preview=$preview (len=${variant.length})")
                            val t = EncryptionUtil.decrypt(encrypted.title, variant)
                            val d = EncryptionUtil.decrypt(encrypted.description, variant)
                            if (t != null && d != null) {
                                Log.i(TAG, "Decryption successful for note ${encrypted.id} using key preview=$preview")
                                return dataclass(title = t, description = d, id = encrypted.id, mymobiledeviceid = encrypted.mymobiledeviceid, timestamp = encrypted.timestamp)
                            }
                        } catch (e: Exception) {
                            Log.d(TAG, "Decryption attempt failed for variant (note=${encrypted.id})", e)
                        }
                    }
                }

                Log.e(TAG, "All decryption attempts failed for note ${encrypted.id}")
                return null
            }

            // Process notes
            if (notesSnapshot.exists()) {
                for (firstLevelChild in notesSnapshot.children) {
                    try {
                        if (firstLevelChild.child("title").exists() || firstLevelChild.child("description").exists()) {
                            val noteData = firstLevelChild.getValue(dataclass::class.java)
                            if (noteData != null) {
                                val candidates = listOfNotNull(currentPassphrase, noteData.mymobiledeviceid, sourcePassphrase, myGlobalMobileDeviceId).distinct()
                                val decrypted = tryDecryptWithCandidates(noteData, candidates)
                                if (decrypted == null) {
                                    Log.w(TAG, "Skipping note ${noteData.id} because decryption failed for all keys")
                                } else {
                                    val localNote = decrypted.copy(mymobiledeviceid = currentPassphrase, timestamp = decrypted.timestamp)
                                    val entity = NoteEntityMapper.toEntity(localNote, synced = false)
                                    noteDao.insert(entity)
                                    syncedNotes.add(localNote)
                                    Log.d(TAG, "Synced note: ${localNote.title}")
                                }
                            }
                        } else {
                            // device grouped nodes
                            for (noteChild in firstLevelChild.children) {
                                try {
                                    val noteData = noteChild.getValue(dataclass::class.java)
                                    if (noteData != null) {
                                        val candidates = listOfNotNull(currentPassphrase, noteData.mymobiledeviceid, sourcePassphrase, myGlobalMobileDeviceId).distinct()
                                        val decrypted = tryDecryptWithCandidates(noteData, candidates)
                                        if (decrypted == null) {
                                            Log.w(TAG, "Skipping nested note ${noteData.id} because decryption failed for all keys")
                                        } else {
                                            val localNote = decrypted.copy(mymobiledeviceid = currentPassphrase, timestamp = decrypted.timestamp)
                                            val entity = NoteEntityMapper.toEntity(localNote, synced = false)
                                            noteDao.insert(entity)
                                            syncedNotes.add(localNote)
                                            Log.d(TAG, "Synced note: ${localNote.title}")
                                        }
                                    }
                                } catch (ie: Exception) {
                                    Log.w(TAG, "Failed to sync nested note: ${noteChild.key}", ie)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to sync note node: ${firstLevelChild.key}", e)
                    }
                }
            }

            // Process reminders (keep functionality but don't count them for stats)
            if (remindersSnapshot.exists()) {
                for (reminderChild in remindersSnapshot.children) {
                    try {
                        val reminderData = reminderChild.getValue(ReminderEntity::class.java)
                        if (reminderData != null) {
                            // Save to local database
                            reminderDao.insertReminder(reminderData)
                            Log.d(TAG, "Synced reminder: ${reminderData.noteTitle}")
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to sync reminder: ${reminderChild.key}", e)
                    }
                }
            }

            // Now upload the synced data to current device's Firebase node
            uploadLocalDataToFirebase(context, currentPassphrase)

            val result = SyncResult(
                syncedNotesCount = syncedNotes.size,
                sourcePassphrase = sourcePassphrase,
                targetPassphrase = currentPassphrase
            )

            Log.d(TAG, "Sync completed: $result")
            Result.success(result)

        } catch (e: Exception) {
            Log.e(TAG, "Sync failed", e)
            Result.failure(e)
        }
    }

    /**
     * Upload all local data to Firebase under current device's passphrase
     */
    suspend fun uploadLocalDataToFirebase(
        context: Context,
        passphrase: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        // Ensure app is authenticated before uploading
        val authResult = PassphraseManager.ensureAuthenticated()
        if (authResult.isFailure) {
            val ex = authResult.exceptionOrNull()
            Log.e(TAG, "Auth failed before upload: ${ex?.message}")
            return@withContext Result.failure(Exception("Authentication failed: ${ex?.message ?: "unknown"}"))
        }

        return@withContext try {
            val database = FirebaseDatabase.getInstance()
            val userRef = database.getReference("users").child(passphrase)

            val db = AppDatabase.getInstance(context)
            val noteDao = db.noteDao()
            val reminderDao = db.reminderDao()

            // Get all local notes
            val localNotes = noteDao.getAllNotes()
            val localReminders = reminderDao.getAllReminders()

            // Upload notes
            val notesRef = userRef.child("notes")
            for (noteEntity in localNotes) {
                val note = NoteEntityMapper.toDomain(noteEntity)
                val encryptedNote = note.toEncryptedData()
                // Use the note's device id if present, otherwise fall back to the account passphrase
                val deviceId = if (encryptedNote.mymobiledeviceid.isNotEmpty()) encryptedNote.mymobiledeviceid else passphrase
                notesRef.child(deviceId).child(note.id).setValue(encryptedNote).await()
            }

            // Upload reminders
            val remindersRef = userRef.child("reminders")
            for (reminder in localReminders) {
                remindersRef.child(reminder.id).setValue(reminder).await()
            }

            // Update sync metadata
            userRef.child("lastSyncAt").setValue(System.currentTimeMillis()).await()
            userRef.child("totalNotes").setValue(localNotes.size).await()
            // Do not write totalReminders metadata anymore to avoid showing reminders in stats

            Log.d(TAG, "Uploaded ${localNotes.size} notes and ${localReminders.size} reminders to Firebase")
            Result.success(Unit)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to upload data to Firebase", e)
            Result.failure(e)
        }
    }

    /**
     * Get sync statistics for a passphrase
     */
    suspend fun getSyncStats(passphrase: String): Result<SyncStats> = withContext(Dispatchers.IO) {
        // Ensure app is authenticated before fetching stats
        val authResult = PassphraseManager.ensureAuthenticated()
        if (authResult.isFailure) {
            val ex = authResult.exceptionOrNull()
            Log.e(TAG, "Auth failed before getSyncStats: ${ex?.message}")
            return@withContext Result.failure(Exception("Authentication failed: ${ex?.message ?: "unknown"}"))
        }

        return@withContext try {
            val database = FirebaseDatabase.getInstance()
            val userRef = database.getReference("users").child(passphrase)

            val snapshot = userRef.get().await()

            if (snapshot.exists()) {
                val totalNotes = snapshot.child("totalNotes").getValue(Int::class.java) ?: 0
                val lastSyncAt = snapshot.child("lastSyncAt").getValue(Long::class.java) ?: 0L
                val createdAt = snapshot.child("createdAt").getValue(Long::class.java) ?: 0L

                val stats = SyncStats(
                    totalNotes = totalNotes,
                    lastSyncAt = lastSyncAt,
                    createdAt = createdAt
                )

                Result.success(stats)
            } else {
                Result.success(SyncStats())
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to get sync stats", e)
            Result.failure(e)
        }
    }
}

data class SyncResult(
    val syncedNotesCount: Int,
    val sourcePassphrase: String,
    val targetPassphrase: String
)

data class SyncStats(
    val totalNotes: Int = 0,
    val lastSyncAt: Long = 0L,
    val createdAt: Long = 0L
)
