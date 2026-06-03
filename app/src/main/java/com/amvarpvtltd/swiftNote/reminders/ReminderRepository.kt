package com.amvarpvtltd.swiftNote.reminders

import android.content.Context
import android.util.Log
import com.amvarpvtltd.swiftNote.auth.DeviceManager
import com.amvarpvtltd.swiftNote.auth.PassphraseManager
import com.amvarpvtltd.swiftNote.notifications.ReminderScheduler
import com.amvarpvtltd.swiftNote.room.AppDatabase
import com.amvarpvtltd.swiftNote.utils.NetworkManager
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.*

class ReminderRepository(private val context: Context) {

    private val reminderDao = AppDatabase.getInstance(context).reminderDao()
    private val reminderScheduler = ReminderScheduler(context)
    private val database = FirebaseDatabase.getInstance()

    /**
     * Resolve the Firebase path for reminders, mirroring [com.amvarpvtltd.swiftNote.repository.NoteRepository.resolveNotesRef]:
     *   users/{accountId}/reminders/{deviceId}
     * accountId = stored passphrase if synced, otherwise local deviceId.
     */
    private fun resolveRemindersRef(): DatabaseReference {
        val deviceId = DeviceManager.getOrCreateDeviceId(context)
        val accountId = PassphraseManager.getStoredPassphrase(context).takeIf { !it.isNullOrEmpty() } ?: deviceId
        return database.getReference("users").child(accountId).child("reminders").child(deviceId)
    }

    /**
     * Best-effort push of a single reminder to Firebase. Mirrors NoteRepository.syncSingleNote.
     * Silently no-ops when offline so the local DB stays the source of truth.
     *
     * SECURITY: never log accountId / deviceId / passphrase — these are the AES encryption keys
     * for all user content. Log only the reminder UUID and bounded error messages.
     */
    private suspend fun syncSingleReminder(reminder: ReminderEntity) {
        if (!NetworkManager.getInstance(context).isConnected()) {
            Log.d("ReminderRepository", "Offline — skipping Firebase push for reminder ${reminder.id}")
            return
        }
        try {
            // Firebase rules require auth != null — make sure anon sign-in resolved first.
            val authResult = PassphraseManager.ensureAuthenticated()
            if (authResult.isFailure) {
                Log.w("ReminderRepository", "Auth not ready, skipping push for reminder ${reminder.id}")
                return
            }
            val deviceId = DeviceManager.getOrCreateDeviceId(context)
            val accountId = PassphraseManager.getStoredPassphrase(context).takeIf { !it.isNullOrEmpty() } ?: deviceId
            val encrypted = reminder.toEncryptedData(accountId)
            database.getReference("users")
                .child(accountId)
                .child("reminders")
                .child(deviceId)
                .child(reminder.id)
                .setValue(encrypted)
                .await()
            // Log.d only — ProGuard strips this in release. Never logs accountId / deviceId.
            Log.d("ReminderRepository", "Pushed reminder ${reminder.id} to Firebase")
        } catch (e: Exception) {
            // Pass message only — full throwable stack can include path with accountId.
            Log.w("ReminderRepository", "Failed to push reminder ${reminder.id}: ${e.message}")
        }
    }

    /** Best-effort removal of a single reminder from Firebase. */
    private suspend fun deleteRemoteReminder(reminderId: String) {
        if (!NetworkManager.getInstance(context).isConnected()) return
        try {
            resolveRemindersRef().child(reminderId).removeValue().await()
        } catch (e: Exception) {
            Log.w("ReminderRepository", "Failed to remove remote reminder $reminderId: ${e.message}")
        }
    }

    suspend fun createReminder(request: ReminderRequest): Result<String> {
        return try {
            val reminderId = UUID.randomUUID().toString()
            val reminder = ReminderEntity(
                id = reminderId,
                noteId = request.noteId,
                noteTitle = request.noteTitle,
                noteDescription = request.noteDescription,
                reminderTime = request.getReminderTime(),
                // Phase 2: Include recurrence data
                recurrenceType = request.recurrenceType,
                recurrenceInterval = request.recurrenceInterval,
                recurrenceDaysOfWeek = request.recurrenceDaysOfWeek,
                recurrenceEndDate = request.recurrenceEndDate,
                parentReminderId = if (request.isRecurring) reminderId else null
            )

            withContext(Dispatchers.IO) {
                reminderDao.insertReminder(reminder)
                reminderScheduler.scheduleReminder(reminder)
                // Best-effort Firebase push so smart reminders show up under
                // users/{accountId}/reminders/{deviceId}/{reminderId} immediately on creation.
                syncSingleReminder(reminder)
            }

            Log.d("ReminderRepository", "Created reminder ${reminder.id} for note ${request.noteId}" +
                if (request.isRecurring) " (recurring: ${request.recurrenceType})" else "")
            Result.success(reminder.id)
        } catch (e: Exception) {
            Log.e("ReminderRepository", "Error creating reminder", e)
            Result.failure(e)
        }
    }

    suspend fun getRemindersForNote(noteId: String): List<ReminderEntity> {
        return withContext(Dispatchers.IO) {
            reminderDao.getRemindersForNote(noteId)
        }
    }

    fun getAllActiveReminders(): Flow<List<ReminderEntity>> {
        return reminderDao.getAllActiveReminders()
    }

    suspend fun cancelReminder(reminderId: String): Result<Unit> {
        return try {
            withContext(Dispatchers.IO) {
                reminderDao.deactivateReminder(reminderId)
                reminderScheduler.cancelReminder(reminderId)
                deleteRemoteReminder(reminderId)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("ReminderRepository", "Error cancelling reminder", e)
            Result.failure(e)
        }
    }

    suspend fun deleteRemindersForNote(noteId: String): Result<Unit> {
        return try {
            withContext(Dispatchers.IO) {
                val reminders = reminderDao.getRemindersForNote(noteId)
                reminders.forEach { reminder ->
                    reminderScheduler.cancelReminder(reminder.id)
                    deleteRemoteReminder(reminder.id)
                }
                reminderDao.deleteRemindersForNote(noteId)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("ReminderRepository", "Error deleting reminders for note", e)
            Result.failure(e)
        }
    }

    // BUG-031 FIX: Proper suspend function instead of launching unscoped coroutine
    suspend fun cleanupOldReminders() {
        withContext(Dispatchers.IO) {
            try {
                val currentTime = System.currentTimeMillis()
                reminderDao.cleanupOldReminders(currentTime)
            } catch (e: Exception) {
                Log.e("ReminderRepository", "Error cleaning up old reminders", e)
            }
        }
    }

    suspend fun rescheduleAllReminders() {
        try {
            withContext(Dispatchers.IO) {
                // Get all active reminders by retrieving the first snapshot of the Flow
                val reminders = getAllActiveReminders().first()
                val currentTime = System.currentTimeMillis()
                val futureReminders = reminders.filter { it.reminderTime > currentTime }
                reminderScheduler.rescheduleAllReminders(futureReminders)
            }
        } catch (e: Exception) {
            Log.e("ReminderRepository", "Error rescheduling reminders", e)
        }
    }
}
