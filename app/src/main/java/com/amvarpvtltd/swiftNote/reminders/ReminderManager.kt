package com.amvarpvtltd.swiftNote.reminders

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.amvarpvtltd.swiftNote.MainActivity
import com.amvarpvtltd.swiftNote.R
import com.amvarpvtltd.swiftNote.ai.DetectedReminder
import com.amvarpvtltd.swiftNote.room.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Manages reminders with persistent alarms that survive device reboots
 */
class ReminderManager private constructor(private val context: Context) {
    private val TAG = "ReminderManager"

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val database = AppDatabase.getInstance(context)
    private val reminderDao = database.reminderDao()

    companion object {
        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var INSTANCE: ReminderManager? = null

        private const val NOTIFICATION_CHANNEL_ID = "smart_reminders"
        private const val NOTIFICATION_CHANNEL_NAME = "Smart Reminders"

        const val EXTRA_REMINDER_ID = "reminder_id"
        const val EXTRA_REMINDER_TITLE = "reminder_title"
        const val EXTRA_REMINDER_DESCRIPTION = "reminder_description"
        const val EXTRA_NOTE_ID = "note_id"

        fun getInstance(context: Context): ReminderManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ReminderManager(context.applicationContext).also {
                    INSTANCE = it
                    it.createNotificationChannel()
                }
            }
        }
    }

    /**
     * Create notification channel for reminders
     */
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            NOTIFICATION_CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Notifications for smart reminders from your notes"
            enableVibration(true)
            enableLights(true)
            setShowBadge(true)
        }

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
        Log.d(TAG, "📱 Notification channel created")
    }

    /**
     * Schedule a reminder alarm
     */
    suspend fun scheduleReminder(
        reminder: DetectedReminder,
        noteId: String
    ): Result<String> = withContext(Dispatchers.IO) {
        return@withContext try {
            // Check if we can schedule exact alarms
            if (!canScheduleExactAlarms()) {
                // Don't fail hard if exact alarm permission isn't granted. Persist the reminder
                // and schedule a best-effort alarm. This ensures reminders are created even when
                // the system restricts exact alarms (app may request permission separately).
                Log.w(TAG, "⚠️ Exact alarm permission not granted — falling back to best-effort scheduling")
            }

            // Save reminder to database - Fixed field names to match ReminderEntity
            val reminderEntity = ReminderEntity(
                id = reminder.id,
                noteId = noteId,
                noteTitle = reminder.title,
                noteDescription = reminder.description,
                reminderTime = reminder.reminderDateTime,
                isActive = true,
                createdAt = System.currentTimeMillis()
            )

            reminderDao.insertReminder(reminderEntity) // Fixed method name
            Log.d(TAG, "💾 Reminder saved to database: ${reminder.title}")

            // Schedule the alarm
            val intent = Intent(context, ReminderReceiver::class.java).apply {
                putExtra(EXTRA_REMINDER_ID, reminder.id)
                putExtra(EXTRA_REMINDER_TITLE, reminder.title)
                putExtra(EXTRA_REMINDER_DESCRIPTION, reminder.description)
                putExtra(EXTRA_NOTE_ID, noteId)
            }

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                reminder.id.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Schedule exact alarm if possible, otherwise schedule a best-effort alarm
            try {
                if (canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        reminder.reminderDateTime,
                        pendingIntent
                    )
                } else {
                    // Best-effort fallback using set which may be inexact but still fires
                    alarmManager.set(
                        AlarmManager.RTC_WAKEUP,
                        reminder.reminderDateTime,
                        pendingIntent
                    )
                }
            } catch (e: SecurityException) {
                Log.w(TAG, "⚠️ SecurityException scheduling alarm, skipping alarm scheduling: ${e.message}")
            }

            val formatter = SimpleDateFormat("MMM dd, yyyy 'at' hh:mm a", Locale.getDefault())
            val dateTime = formatter.format(Date(reminder.reminderDateTime))

            Log.d(TAG, "⏰ Reminder scheduled: ${reminder.title} for $dateTime")
            Result.success("Reminder scheduled for $dateTime")

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error scheduling reminder", e)
            Result.failure(e)
        }
    }

    /**
     * Get all active reminders
     */
    suspend fun getAllActiveReminders(): List<ReminderEntity> = withContext(Dispatchers.IO) {
        return@withContext try {
            // Fixed: Collect the Flow to get the actual list
            val reminders = mutableListOf<ReminderEntity>()
            reminderDao.getAllActiveReminders().collect { reminderList ->
                reminders.addAll(reminderList)
            }
            reminders
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error getting all active reminders", e)
            emptyList()
        }
    }

    /**
     * Reschedule all active reminders (called after device reboot)
     */
    suspend fun rescheduleAllReminders(): Result<String> = withContext(Dispatchers.IO) {
        return@withContext try {
            val activeReminders = getAllActiveReminders() // Use the corrected method
            val currentTime = System.currentTimeMillis()
            var rescheduledCount = 0

            activeReminders.forEach { reminder ->
                if (reminder.reminderTime > currentTime) { // Fixed field name
                    // Reschedule future reminders
                    val intent = Intent(context, ReminderReceiver::class.java).apply {
                        putExtra(EXTRA_REMINDER_ID, reminder.id)
                        putExtra(EXTRA_REMINDER_TITLE, reminder.noteTitle) // Fixed field name
                        putExtra(EXTRA_REMINDER_DESCRIPTION, reminder.noteDescription) // Fixed field name
                        putExtra(EXTRA_NOTE_ID, reminder.noteId)
                    }

                    val pendingIntent = PendingIntent.getBroadcast(
                        context,
                        reminder.id.hashCode(),
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )

                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        reminder.reminderTime, // Fixed field name
                        pendingIntent
                    )
                    rescheduledCount++
                } else {
                    // Mark expired reminders as inactive - Fixed using deactivateReminder
                    reminderDao.deactivateReminder(reminder.id)
                }
            }

            Log.d(TAG, "🔄 Rescheduled $rescheduledCount reminders after reboot")
            Result.success("Rescheduled $rescheduledCount reminders")

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error rescheduling reminders", e)
            Result.failure(e)
        }
    }

    /**
     * Create a reminder from AI detection
     * This is a convenience method that wraps scheduleReminder with proper error handling
     */
    suspend fun createReminderFromDetection(
        detectedReminder: DetectedReminder,
        noteId: String
    ): Boolean {
        return try {
            val result = scheduleReminder(detectedReminder, noteId)
            result.isSuccess
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error creating reminder from detection", e)
            false
        }
    }

    /**
     * Check if the app can schedule exact alarms
     */
    fun canScheduleExactAlarms(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    /**
     * Show notification for triggered reminder
     */
    fun showReminderNotification(
        reminderId: String,
        title: String,
        description: String,
        noteId: String
    ) {
        try {
            // Check notification permissions first
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val notificationManager = NotificationManagerCompat.from(context)
                if (!notificationManager.areNotificationsEnabled()) {
                    Log.w(TAG, "⚠️ Notifications are disabled for this app")
                    return
                }
            }

            // Create intent to open the note
            val notificationIntent = Intent(context, MainActivity::class.java).apply {
                putExtra("noteId", noteId)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }

            val pendingIntent = PendingIntent.getActivity(
                context,
                reminderId.hashCode(),
                notificationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon( R.drawable.logo2)
                .setContentTitle("🔔 $title")
                .setContentText(description)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setVibrate(longArrayOf(0, 500, 100, 500))
                .setDefaults(NotificationCompat.DEFAULT_SOUND or NotificationCompat.DEFAULT_LIGHTS)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOnlyAlertOnce(false)
                .build()

            val notificationManager = NotificationManagerCompat.from(context)
            try {
                notificationManager.notify(reminderId.hashCode(), notification)
                Log.d(TAG, "📱 Reminder notification shown: $title")

                // Mark reminder as completed in database - Fixed using deactivateReminder
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        reminderDao.deactivateReminder(reminderId)
                        Log.d(TAG, "✅ Reminder marked as completed: $title")
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Error marking reminder as completed", e)
                    }
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "❌ Missing notification permission", e)
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error showing reminder notification", e)
        }
    }
}

/**
 * BroadcastReceiver to handle reminder alarms
 */
class ReminderReceiver : BroadcastReceiver() {
    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getStringExtra(ReminderManager.EXTRA_REMINDER_ID) ?: return
        val title = intent.getStringExtra(ReminderManager.EXTRA_REMINDER_TITLE) ?: "Reminder"
        val description = intent.getStringExtra(ReminderManager.EXTRA_REMINDER_DESCRIPTION) ?: ""
        val noteId = intent.getStringExtra(ReminderManager.EXTRA_NOTE_ID) ?: ""

        Log.d("ReminderReceiver", "🔔 Reminder triggered: $title")

        val reminderManager = ReminderManager.getInstance(context)
        reminderManager.showReminderNotification(reminderId, title, description, noteId)
    }
}

/**
 * BroadcastReceiver to handle device boot and reschedule alarms
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Intent.ACTION_BOOT_COMPLETED == intent.action ||
            Intent.ACTION_MY_PACKAGE_REPLACED == intent.action) {

            Log.d("BootReceiver", "📱 Device booted, rescheduling reminders...")

            CoroutineScope(Dispatchers.IO).launch {
                val reminderManager = ReminderManager.getInstance(context)
                reminderManager.rescheduleAllReminders()
            }
        }
    }
}
