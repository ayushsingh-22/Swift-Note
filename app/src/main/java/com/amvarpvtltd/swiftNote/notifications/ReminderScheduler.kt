package com.amvarpvtltd.swiftNote.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import com.amvarpvtltd.swiftNote.components.NotificationHelper
import com.amvarpvtltd.swiftNote.reminders.ReminderEntity
import com.amvarpvtltd.swiftNote.reminders.ReminderManager
import com.amvarpvtltd.swiftNote.reminders.ReminderReceiver

/**
 * AlarmManager-only reminder scheduler (Google-recommended approach for time-precise notifications).
 *
 * Strategy:
 *  1. If the app holds SCHEDULE_EXACT_ALARM / USE_EXACT_ALARM → setExactAndAllowWhileIdle
 *     (exact, fires through Doze).
 *  2. Otherwise → setAndAllowWhileIdle (inexact, ±15 min in Doze, no permission needed).
 *
 * Reboot resilience: BootReceiver (in ReminderManager.kt) re-arms all active reminders on
 * ACTION_BOOT_COMPLETED. Recurrence chaining is handled by ReminderReceiver after fire.
 *
 * WorkManager is intentionally NOT used here — reminders must fire at an exact time;
 * WorkManager's deferrable-execution model can drift by minutes-to-hours in Doze.
 */
class ReminderScheduler(private val context: Context) {

    private val systemNotificationHelper = SystemNotificationHelper(context)

    fun scheduleReminder(
        reminder: ReminderEntity,
        isSmartReminder: Boolean = false
    ) {
        val currentTime = System.currentTimeMillis()

        if (reminder.reminderTime <= currentTime) {
            Log.w("ReminderScheduler", "Cannot schedule reminder in the past")
            NotificationHelper.showWarning(
                title = "Reminder Not Set",
                message = "Cannot set reminder for a time in the past"
            )
            return
        }

        scheduleAlarm(reminder, isSmartReminder)

        val recurLabel = if (reminder.isRecurring) " (${reminder.recurrenceType.lowercase()})" else ""
        Log.d("ReminderScheduler", "Scheduled reminder ${reminder.id} for ${reminder.noteTitle}$recurLabel")

        NotificationHelper.showSuccess(
            title = "Reminder Set",
            message = "${if (isSmartReminder) "Smart reminder" else "Reminder"} set for \"${reminder.noteTitle}\"$recurLabel"
        )
    }

    /**
     * Schedule an AlarmManager alarm for the reminder.
     * Uses exact alarm when permission is granted, otherwise falls back to inexact (still wakes Doze).
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

            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    reminder.reminderTime,
                    pendingIntent
                )
                Log.d("ReminderScheduler", "Scheduled EXACT alarm for ${reminder.id}")
            } else {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    reminder.reminderTime,
                    pendingIntent
                )
                Log.d("ReminderScheduler", "Scheduled INEXACT alarm for ${reminder.id} (no exact-alarm permission)")
                NotificationHelper.showInfo(
                    title = "Inexact Reminder",
                    message = "Grant 'Alarms & Reminders' permission for precise timing"
                )
            }
        } catch (e: SecurityException) {
            Log.w("ReminderScheduler", "Could not schedule alarm: ${e.message}")
            NotificationHelper.showWarning(
                title = "Reminder Failed",
                message = "Could not schedule reminder — check app permissions"
            )
        } catch (e: Exception) {
            Log.e("ReminderScheduler", "Error scheduling alarm", e)
        }
    }

    fun cancelReminder(reminderId: String) {
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

        // Cancel any visible notification
        systemNotificationHelper.cancelNotification(reminderId)

        Log.d("ReminderScheduler", "Cancelled reminder $reminderId")

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

