package com.amvarpvtltd.swiftNote.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.work.*
import com.amvarpvtltd.swiftNote.components.NotificationHelper
import com.amvarpvtltd.swiftNote.reminders.RecurrenceCalculator
import com.amvarpvtltd.swiftNote.reminders.RecurrenceType
import com.amvarpvtltd.swiftNote.reminders.ReminderEntity
import com.amvarpvtltd.swiftNote.reminders.ReminderManager
import com.amvarpvtltd.swiftNote.reminders.ReminderReceiver
import com.amvarpvtltd.swiftNote.room.AppDatabase
import kotlinx.coroutines.runBlocking
import java.util.UUID
import java.util.concurrent.TimeUnit

class ReminderScheduler(private val context: Context) {

    private val systemNotificationHelper = SystemNotificationHelper(context)

    fun scheduleReminder(
        reminder: ReminderEntity,
        isSmartReminder: Boolean = false
    ) {
        val currentTime = System.currentTimeMillis()

        if (reminder.reminderTime <= currentTime) {
            Log.w("ReminderScheduler", "Cannot schedule reminder in the past")
            // Show in-app notification about the error
            NotificationHelper.showWarning(
                title = "Reminder Not Set",
                message = "Cannot set reminder for a time in the past"
            )
            return
        }

        val delay = reminder.reminderTime - currentTime

        // Strategy 1: Schedule AlarmManager for precise timing (fires even in Doze)
        scheduleAlarm(reminder, isSmartReminder)

        // Strategy 2: ALSO schedule WorkManager as redundant backup (guaranteed execution)
        val workRequest = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(
                "reminderId" to reminder.id,
                "noteId" to reminder.noteId,
                "noteTitle" to reminder.noteTitle,
                "noteDescription" to reminder.noteDescription,
                "isSmartReminder" to isSmartReminder,
                "reminderTime" to reminder.reminderTime,
                // Phase 2: Pass recurrence info to worker
                "recurrenceType" to reminder.recurrenceType,
                "recurrenceInterval" to reminder.recurrenceInterval,
                "recurrenceDaysOfWeek" to (reminder.recurrenceDaysOfWeek ?: ""),
                "recurrenceEndDate" to (reminder.recurrenceEndDate ?: -1L),
                "parentReminderId" to (reminder.parentReminderId ?: reminder.id)
            ))
            .addTag("reminder_${reminder.id}")
            .build()

        WorkManager.getInstance(context).enqueue(workRequest)

        val reminderType = if (isSmartReminder) "Smart reminder" else "Reminder"
        val recurLabel = if (reminder.isRecurring) " (${reminder.recurrenceType.lowercase()})" else ""
        Log.d("ReminderScheduler", "Scheduled $reminderType${recurLabel} ${reminder.id} for ${reminder.noteTitle}")

        // Show in-app notification confirmation
        NotificationHelper.showSuccess(
            title = "Reminder Set",
            message = "${if (isSmartReminder) "Smart reminder" else "Reminder"} set for \"${reminder.noteTitle}\"$recurLabel"
        )
    }

    /**
     * Schedule an AlarmManager alarm for precise delivery (critical for background/killed state).
     * Uses exact alarm if permission granted, otherwise setAndAllowWhileIdle.
     */
    private fun scheduleAlarm(reminder: ReminderEntity, isSmartReminder: Boolean) {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

            val intent = Intent(context, ReminderReceiver::class.java).apply {
                putExtra(ReminderManager.EXTRA_REMINDER_ID, reminder.id)
                putExtra(ReminderManager.EXTRA_REMINDER_TITLE, reminder.noteTitle)
                putExtra(ReminderManager.EXTRA_REMINDER_DESCRIPTION, reminder.noteDescription)
                putExtra(ReminderManager.EXTRA_NOTE_ID, reminder.noteId)
            }

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                reminder.id.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val canExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                alarmManager.canScheduleExactAlarms()
            } else {
                true
            }

            if (canExact) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    reminder.reminderTime,
                    pendingIntent
                )
                Log.d("ReminderScheduler", "Scheduled exact alarm for ${reminder.id}")
            } else {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    reminder.reminderTime,
                    pendingIntent
                )
                Log.d("ReminderScheduler", "Scheduled inexact alarm for ${reminder.id}")
            }
        } catch (e: SecurityException) {
            Log.w("ReminderScheduler", "Could not schedule alarm: ${e.message}")
            // WorkManager backup is already scheduled, so this is OK
        } catch (e: Exception) {
            Log.e("ReminderScheduler", "Error scheduling alarm", e)
        }
    }

    fun cancelReminder(reminderId: String) {
        // Cancel WorkManager task
        WorkManager.getInstance(context).cancelAllWorkByTag("reminder_$reminderId")

        // Cancel AlarmManager alarm
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, ReminderReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                reminderId.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
        } catch (e: Exception) {
            Log.w("ReminderScheduler", "Error cancelling alarm: ${e.message}")
        }

        // Cancel any existing notification
        systemNotificationHelper.cancelNotification(reminderId)

        Log.d("ReminderScheduler", "Cancelled reminder $reminderId")

        // Show in-app notification
        NotificationHelper.showInfo(
            title = "Reminder Canceled",
            message = "The reminder has been canceled"
        )
    }

    fun rescheduleAllReminders(reminders: List<ReminderEntity>, isSmartReminder: Boolean = false) {
        val currentTime = System.currentTimeMillis()

        reminders.filter { it.isActive && it.reminderTime > currentTime }
            .forEach { scheduleReminder(it, isSmartReminder) }
    }
}

class ReminderWorker(
    context: Context,
    workerParams: WorkerParameters
) : Worker(context, workerParams) {

    override fun doWork(): Result {
        val reminderId = inputData.getString("reminderId") ?: return Result.failure()
        val noteId = inputData.getString("noteId") ?: return Result.failure()
        val noteTitle = inputData.getString("noteTitle") ?: return Result.failure()
        val noteDescription = inputData.getString("noteDescription") ?: ""
        val isSmartReminder = inputData.getBoolean("isSmartReminder", false)

        // Phase 2: Recurrence data
        val recurrenceType = inputData.getString("recurrenceType") ?: RecurrenceType.NONE
        val recurrenceInterval = inputData.getInt("recurrenceInterval", 1)
        val recurrenceDaysOfWeek = inputData.getString("recurrenceDaysOfWeek")?.ifBlank { null }
        val recurrenceEndDateRaw = inputData.getLong("recurrenceEndDate", -1L)
        val recurrenceEndDate = if (recurrenceEndDateRaw > 0) recurrenceEndDateRaw else null
        val parentReminderId = inputData.getString("parentReminderId") ?: reminderId

        // Check if notification was already shown by AlarmManager (avoid duplicate)
        val db = AppDatabase.getInstance(applicationContext)
        val reminder = runBlocking {
            try {
                db.reminderDao().getReminderById(reminderId)
            } catch (e: Exception) {
                null
            }
        }

        // If reminder is already inactive (was fired by AlarmManager path), skip
        if (reminder != null && !reminder.isActive) {
            Log.d("ReminderWorker", "Reminder $reminderId already handled by AlarmManager, skipping")
            return Result.success()
        }

        // Show notification
        val systemNotificationHelper = SystemNotificationHelper(applicationContext)
        systemNotificationHelper.showReminderNotification(
            reminderId = reminderId,
            noteId = noteId,
            noteTitle = noteTitle,
            noteDescription = noteDescription,
            isSmartReminder = isSmartReminder
        )

        Log.d("ReminderWorker", "Showed reminder notification for note: $noteTitle")

        // Mark as inactive so AlarmManager path doesn't duplicate
        runBlocking {
            try {
                db.reminderDao().deactivateReminder(reminderId)
            } catch (e: Exception) {
                Log.e("ReminderWorker", "Error deactivating reminder", e)
            }
        }

        // Phase 2: Schedule next occurrence for recurring reminders
        if (recurrenceType != RecurrenceType.NONE) {
            scheduleNextRecurrence(
                reminderId = reminderId,
                noteId = noteId,
                noteTitle = noteTitle,
                noteDescription = noteDescription,
                recurrenceType = recurrenceType,
                recurrenceInterval = recurrenceInterval,
                recurrenceDaysOfWeek = recurrenceDaysOfWeek,
                recurrenceEndDate = recurrenceEndDate,
                parentReminderId = parentReminderId,
                currentReminderTime = inputData.getLong("reminderTime", System.currentTimeMillis())
            )
        }

        return Result.success()
    }

    /**
     * Phase 2: After a recurring reminder fires, compute + schedule the next occurrence.
     * Risk mitigation: Won't schedule if end date passed or next time is null.
     */
    private fun scheduleNextRecurrence(
        reminderId: String,
        noteId: String,
        noteTitle: String,
        noteDescription: String,
        recurrenceType: String,
        recurrenceInterval: Int,
        recurrenceDaysOfWeek: String?,
        recurrenceEndDate: Long?,
        parentReminderId: String,
        currentReminderTime: Long
    ) {
        val nextTime = RecurrenceCalculator.getNextOccurrence(
            currentReminderTime = currentReminderTime,
            recurrenceType = recurrenceType,
            recurrenceInterval = recurrenceInterval,
            recurrenceDaysOfWeek = recurrenceDaysOfWeek,
            recurrenceEndDate = recurrenceEndDate
        )

        if (nextTime == null) {
            Log.d("ReminderWorker", "Recurring chain ended for $parentReminderId (end date reached or no next)")
            return
        }

        val nextReminder = ReminderEntity(
            id = UUID.randomUUID().toString(),
            noteId = noteId,
            noteTitle = noteTitle,
            noteDescription = noteDescription,
            reminderTime = nextTime,
            isActive = true,
            recurrenceType = recurrenceType,
            recurrenceInterval = recurrenceInterval,
            recurrenceDaysOfWeek = recurrenceDaysOfWeek,
            recurrenceEndDate = recurrenceEndDate,
            parentReminderId = parentReminderId
        )

        // Save to DB and schedule
        runBlocking {
            try {
                val db = AppDatabase.getInstance(applicationContext)
                db.reminderDao().insertReminder(nextReminder)
                ReminderScheduler(applicationContext).scheduleReminder(nextReminder)
                Log.d("ReminderWorker", "Scheduled next recurrence at $nextTime for chain $parentReminderId")
            } catch (e: Exception) {
                Log.e("ReminderWorker", "Error scheduling next recurrence", e)
            }
        }
    }
}
