package com.amvarpvtltd.swiftNote.reminders

import android.content.Context
import android.util.Log
import com.amvarpvtltd.swiftNote.notifications.ReminderScheduler
import com.amvarpvtltd.swiftNote.room.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*

class ReminderRepository(private val context: Context) {

    private val reminderDao = AppDatabase.getInstance(context).reminderDao()
    private val reminderScheduler = ReminderScheduler(context)
    private val scope = CoroutineScope(Dispatchers.IO)

    suspend fun createReminder(request: ReminderRequest): Result<String> {
        return try {
            val reminder = ReminderEntity(
                id = UUID.randomUUID().toString(),
                noteId = request.noteId,
                noteTitle = request.noteTitle,
                noteDescription = request.noteDescription,
                reminderTime = request.getReminderTime()
            )

            withContext(Dispatchers.IO) {
                reminderDao.insertReminder(reminder)
                reminderScheduler.scheduleReminder(reminder)
            }

            Log.d("ReminderRepository", "Created reminder for note: ${request.noteTitle}")
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
                }
                reminderDao.deleteRemindersForNote(noteId)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("ReminderRepository", "Error deleting reminders for note", e)
            Result.failure(e)
        }
    }

    suspend fun cleanupOldReminders() {
        scope.launch {
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
                // Get all active reminders by collecting the Flow
                getAllActiveReminders().collect { reminders ->
                    val currentTime = System.currentTimeMillis()
                    val futureReminders = reminders.filter { it.reminderTime > currentTime }
                    reminderScheduler.rescheduleAllReminders(futureReminders)
                }
            }
        } catch (e: Exception) {
            Log.e("ReminderRepository", "Error rescheduling reminders", e)
        }
    }
}
