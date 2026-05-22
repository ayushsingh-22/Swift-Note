package com.amvarpvtltd.swiftNote.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import com.amvarpvtltd.swiftNote.components.NotificationHelper
import com.amvarpvtltd.swiftNote.reminders.ReminderEntity
import com.amvarpvtltd.swiftNote.room.AppDatabase
import kotlinx.coroutines.runBlocking

class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getStringExtra("reminderId") ?: return
        val noteId = intent.getStringExtra("noteId")
        val noteTitle = intent.getStringExtra("noteTitle")
        val noteDescription = intent.getStringExtra("noteDescription")

        val systemNotificationHelper = SystemNotificationHelper(context)

        when (intent.action) {
            "MARK_DONE" -> {
                // Cancel system notification
                systemNotificationHelper.cancelNotification(reminderId)

                // Phase 2: If this is a recurring reminder, stop the entire chain
                runBlocking {
                    try {
                        val db = AppDatabase.getInstance(context)
                        val reminder = db.reminderDao().getReminderById(reminderId)
                        if (reminder != null && reminder.isRecurring) {
                            // Deactivate the entire recurring chain
                            val parentId = reminder.parentReminderId ?: reminder.id
                            db.reminderDao().deactivateRecurringChain(parentId)
                            Log.d("NotificationAction", "Stopped recurring chain: $parentId")
                        } else {
                            // Single reminder — just deactivate this one
                            db.reminderDao().deactivateReminder(reminderId)
                        }
                    } catch (e: Exception) {
                        Log.e("NotificationAction", "Error deactivating reminder", e)
                    }
                }

                NotificationHelper.showSuccess(
                    title = "Reminder Completed",
                    message = "Reminder for \"$noteTitle\" marked as done"
                )
                Toast.makeText(context, "Reminder marked as done", Toast.LENGTH_SHORT).show()
            }
            "SNOOZE" -> {
                // Cancel system notification
                systemNotificationHelper.cancelNotification(reminderId)

                if (noteId != null && noteTitle != null) {
                    // Phase 2: Snooze only snoozes THIS instance — recurring chain continues independently
                    val snoozeTime = System.currentTimeMillis() + 10 * 60 * 1000 // 10 min
                    val snoozedReminder = ReminderEntity(
                        id = "${reminderId}_snoozed_${System.currentTimeMillis()}",
                        noteId = noteId,
                        noteTitle = noteTitle,
                        noteDescription = noteDescription ?: "",
                        reminderTime = snoozeTime,
                        isActive = true,
                        // Snoozed reminders are NOT recurring — they're one-time rescheduled instances
                        recurrenceType = com.amvarpvtltd.swiftNote.reminders.RecurrenceType.NONE
                    )
                    ReminderScheduler(context).scheduleReminder(snoozedReminder)

                    NotificationHelper.showInfo(
                        title = "Reminder Snoozed",
                        message = "Reminder for \"$noteTitle\" snoozed for 10 minutes"
                    )
                    Toast.makeText(context, "Reminder snoozed for 10 minutes", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
