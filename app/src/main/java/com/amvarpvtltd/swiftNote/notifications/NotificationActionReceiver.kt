package com.amvarpvtltd.swiftNote.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.amvarpvtltd.swiftNote.components.NotificationHelper
import com.amvarpvtltd.swiftNote.reminders.ReminderEntity

class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getStringExtra("reminderId") ?: return
        val noteId = intent.getStringExtra("noteId")
        val noteTitle = intent.getStringExtra("noteTitle")
        val noteDescription = intent.getStringExtra("noteDescription")

        // System notification helper - for canceling notifications
        val systemNotificationHelper = SystemNotificationHelper(context)

        when (intent.action) {
            "MARK_DONE" -> {
                // Cancel system notification
                systemNotificationHelper.cancelNotification(reminderId)

                // Show in-app notification feedback (will be shown next time user opens app)
                NotificationHelper.showSuccess(
                    title = "Reminder Completed",
                    message = "Reminder for \"$noteTitle\" marked as done"
                )

                // Still show a Toast for immediate feedback
                Toast.makeText(context, "Reminder marked as done", Toast.LENGTH_SHORT).show()
            }
            "SNOOZE" -> {
                // Cancel system notification
                systemNotificationHelper.cancelNotification(reminderId)

                if (noteId != null && noteTitle != null) {
                    val snoozeTime = System.currentTimeMillis() + 10 * 60 * 1000 // 10 min
                    val reminder = ReminderEntity(
                        id = "${reminderId}_snoozed_${System.currentTimeMillis()}",
                        noteId = noteId,
                        noteTitle = noteTitle,
                        noteDescription = noteDescription ?: "",
                        reminderTime = snoozeTime,
                        isActive = true
                    )
                    ReminderScheduler(context).scheduleReminder(reminder)

                    // Show in-app notification feedback (will be shown next time user opens app)
                    NotificationHelper.showInfo(
                        title = "Reminder Snoozed",
                        message = "Reminder for \"$noteTitle\" snoozed for 10 minutes"
                    )

                    // Still show a Toast for immediate feedback
                    Toast.makeText(context, "Reminder snoozed for 10 minutes", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
