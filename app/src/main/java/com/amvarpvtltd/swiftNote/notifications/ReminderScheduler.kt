package com.amvarpvtltd.swiftNote.notifications

import android.app.AlarmManager
import android.content.Context
import android.util.Log
import androidx.work.*
import com.amvarpvtltd.swiftNote.components.NotificationHelper
import com.amvarpvtltd.swiftNote.reminders.ReminderEntity
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

        // Use WorkManager for reliable background execution
        val workRequest = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(reminder.reminderTime - currentTime, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(
                "reminderId" to reminder.id,
                "noteId" to reminder.noteId,
                "noteTitle" to reminder.noteTitle,
                "noteDescription" to reminder.noteDescription,
                "isSmartReminder" to isSmartReminder
            ))
            .addTag("reminder_${reminder.id}")
            .build()

        WorkManager.getInstance(context).enqueue(workRequest)

        val reminderType = if (isSmartReminder) "Smart reminder" else "Reminder"
        Log.d("ReminderScheduler", "Scheduled $reminderType ${reminder.id} for ${reminder.noteTitle}")

        // Show in-app notification confirmation
        NotificationHelper.showSuccess(
            title = "Reminder Set",
            message = "${if (isSmartReminder) "Smart reminder" else "Reminder"} set for \"${reminder.noteTitle}\""
        )
    }

    fun cancelReminder(reminderId: String) {
        // Cancel WorkManager task
        WorkManager.getInstance(context).cancelAllWorkByTag("reminder_$reminderId")

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

        val systemNotificationHelper = SystemNotificationHelper(applicationContext)
        systemNotificationHelper.showReminderNotification(
            reminderId = reminderId,
            noteId = noteId,
            noteTitle = noteTitle,
            noteDescription = noteDescription,
            isSmartReminder = isSmartReminder
        )

        Log.d("ReminderWorker", "Showed reminder notification for note: $noteTitle")

        return Result.success()
    }
}
