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
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.amvarpvtltd.swiftNote.MainActivity
import com.amvarpvtltd.swiftNote.R
import com.amvarpvtltd.swiftNote.ai.DetectedReminder
import com.amvarpvtltd.swiftNote.checklist.ChecklistParser
import com.amvarpvtltd.swiftNote.room.AppDatabase
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Manages reminders with persistent alarms that survive device reboots
 */
class ReminderManager private constructor(private val context: Context) {
    private val TAG = "ReminderManager"

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val database = AppDatabase.getInstance(context)
    private val reminderDao = database.reminderDao()

    private val exceptionHandler = CoroutineExceptionHandler { _, exception ->
        Log.e(TAG, "Uncaught exception in ReminderManager scope", exception)
    }

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
     * Schedule a reminder alarm with Android 12-15 compliance.
     * Strategy:
     *  1. Try setExactAndAllowWhileIdle (requires SCHEDULE_EXACT_ALARM or USE_EXACT_ALARM)
     *  2. Fallback: setAndAllowWhileIdle (inexact but still fires during Doze)
     *  3. Final fallback: WorkManager OneTimeWorkRequest (guaranteed execution)
     */
    suspend fun scheduleReminder(
        reminder: DetectedReminder,
        noteId: String
    ): Result<String> = withContext(Dispatchers.IO) {
        return@withContext try {
            // Save reminder to database first (always persisted regardless of alarm success)
            val reminderEntity = ReminderEntity(
                id = reminder.id,
                noteId = noteId,
                noteTitle = reminder.title,
                noteDescription = reminder.description,
                reminderTime = reminder.reminderDateTime,
                isActive = true,
                createdAt = System.currentTimeMillis()
            )

            reminderDao.insertReminder(reminderEntity)
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

            scheduleAlarmWithFallback(
                triggerAtMillis = reminder.reminderDateTime,
                pendingIntent = pendingIntent,
                reminderId = reminder.id,
                noteId = noteId,
                title = reminder.title,
                description = reminder.description
            )

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
     * Android 12-15 compliant alarm scheduling with redundancy:
     * 1. Try exact alarm (best precision) OR inexact setAndAllowWhileIdle
     * 2. ALWAYS also schedule WorkManager as redundant backup (guaranteed execution)
     * The WorkManager job will check if notification was already shown before duplicating.
     */
    private fun scheduleAlarmWithFallback(
        triggerAtMillis: Long,
        pendingIntent: PendingIntent,
        reminderId: String,
        noteId: String,
        title: String,
        description: String
    ) {
        val delay = triggerAtMillis - System.currentTimeMillis()
        if (delay <= 0) {
            Log.w(TAG, "Reminder time is in the past, firing notification immediately")
            // Fire immediately instead of silently skipping
            showReminderNotification(reminderId, title, description, noteId)
            return
        }

        var alarmScheduled = false

        try {
            if (canScheduleExactAlarms()) {
                // Best case: exact alarm fires at the precise time
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
                Log.d(TAG, "Scheduled exact alarm for $reminderId")
                alarmScheduled = true
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "SecurityException on exact alarm: ${e.message}")
        }

        if (!alarmScheduled) {
            // Fallback 1: setAndAllowWhileIdle (inexact but fires during Doze)
            try {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
                Log.d(TAG, "Scheduled inexact (setAndAllowWhileIdle) alarm for $reminderId")
                alarmScheduled = true
            } catch (e: SecurityException) {
                Log.w(TAG, "SecurityException on setAndAllowWhileIdle: ${e.message}")
            }
        }

        // ALWAYS schedule WorkManager as redundant backup — ensures notification fires
        // even if AlarmManager is killed by OEM battery optimization or Doze
        scheduleWorkManagerFallback(delay, reminderId, noteId, title, description)
    }

    /**
     * WorkManager-based reminder fallback for when AlarmManager is completely restricted.
     */
    private fun scheduleWorkManagerFallback(
        delayMs: Long,
        reminderId: String,
        noteId: String,
        title: String,
        description: String
    ) {
        val workRequest = OneTimeWorkRequestBuilder<com.amvarpvtltd.swiftNote.notifications.ReminderWorker>()
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(
                "reminderId" to reminderId,
                "noteId" to noteId,
                "noteTitle" to title,
                "noteDescription" to description,
                "isSmartReminder" to true
            ))
            .addTag("reminder_fallback_$reminderId")
            .build()

        WorkManager.getInstance(context).enqueue(workRequest)
        Log.d(TAG, "Scheduled WorkManager fallback for reminder $reminderId (delay: ${delayMs / 1000}s)")
    }

    /**
     * Get all active reminders
     */
    suspend fun getAllActiveReminders(): List<ReminderEntity> = withContext(Dispatchers.IO) {
        return@withContext try {
            reminderDao.getAllActiveReminders().first()
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error getting all active reminders", e)
            emptyList()
        }
    }

    /**
     * Reschedule all active reminders (called after device reboot or permission grant)
     */
    suspend fun rescheduleAllReminders(): Result<String> = withContext(Dispatchers.IO) {
        return@withContext try {
            val activeReminders = getAllActiveReminders()
            val currentTime = System.currentTimeMillis()
            var rescheduledCount = 0

            activeReminders.forEach { reminder ->
                if (reminder.reminderTime > currentTime) {
                    // Reschedule future reminders
                    val intent = Intent(context, ReminderReceiver::class.java).apply {
                        putExtra(EXTRA_REMINDER_ID, reminder.id)
                        putExtra(EXTRA_REMINDER_TITLE, reminder.noteTitle)
                        putExtra(EXTRA_REMINDER_DESCRIPTION, reminder.noteDescription)
                        putExtra(EXTRA_NOTE_ID, reminder.noteId)
                    }

                    val pendingIntent = PendingIntent.getBroadcast(
                        context,
                        reminder.id.hashCode(),
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )

                    // Use the same graduated fallback as scheduleReminder
                    scheduleAlarmWithFallback(
                        triggerAtMillis = reminder.reminderTime,
                        pendingIntent = pendingIntent,
                        reminderId = reminder.id,
                        noteId = reminder.noteId,
                        title = reminder.noteTitle,
                        description = reminder.noteDescription
                    )
                    rescheduledCount++
                } else {
                    // Mark expired reminders as inactive
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
     * Open system settings to allow the user to grant exact alarm permission (Android 12+).
     * Returns true if the intent was launched, false if not needed or not applicable.
     */
    fun requestExactAlarmPermission(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !canScheduleExactAlarms()) {
            try {
                val intent = Intent(
                    android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    android.net.Uri.parse("package:${context.packageName}")
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                return true
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error opening exact alarm settings", e)
            }
        }
        return false
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

            // Format checklist content for readable notification display
            val displayDescription = if (ChecklistParser.isChecklistContent(description)) {
                val (checked, total) = ChecklistParser.progress(description)
                val items = ChecklistParser.parseItems(description)
                val unchecked = items.filter { !it.isChecked }.take(3)
                if (unchecked.isEmpty()) {
                    "✓ All $total items completed!"
                } else {
                    "Checklist ($checked/$total done): " + unchecked.joinToString(", ") { it.text }
                }
            } else {
                description
            }

            val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon( R.drawable.logo2)
                .setContentTitle("🔔 $title")
                .setContentText(displayDescription)
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

                // Mark reminder as completed SYNCHRONOUSLY using runBlocking
                // This ensures it completes before the BroadcastReceiver's goAsync() finishes
                kotlinx.coroutines.runBlocking(Dispatchers.IO) {
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
0 * BroadcastReceiver to handle reminder alarms.
 * Uses goAsync() to prevent the system from killing the process before
 * the notification is fully shown (critical when app is not in foreground).
 */
class ReminderReceiver : BroadcastReceiver() {
    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getStringExtra(ReminderManager.EXTRA_REMINDER_ID) ?: return
        val title = intent.getStringExtra(ReminderManager.EXTRA_REMINDER_TITLE) ?: "Reminder"
        val description = intent.getStringExtra(ReminderManager.EXTRA_REMINDER_DESCRIPTION) ?: ""
        val noteId = intent.getStringExtra(ReminderManager.EXTRA_NOTE_ID) ?: ""

        Log.d("ReminderReceiver", "🔔 Reminder triggered: $title")

        // Use goAsync() to get more execution time (~30s instead of ~10s)
        // This is critical when the app is killed/background — prevents premature process death
        val pendingResult = goAsync()

        try {
            val reminderManager = ReminderManager.getInstance(context)
            reminderManager.showReminderNotification(reminderId, title, description, noteId)
        } catch (e: Exception) {
            Log.e("ReminderReceiver", "❌ Error showing notification", e)
        } finally {
            // Must call finish() to release the wake lock
            pendingResult.finish()
        }
    }
}

/**
 * BroadcastReceiver to handle device boot and reschedule alarms.
 * Uses goAsync() for safe background execution.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Intent.ACTION_BOOT_COMPLETED == intent.action ||
            Intent.ACTION_MY_PACKAGE_REPLACED == intent.action) {

            Log.d("BootReceiver", "📱 Device booted, rescheduling reminders...")

            val pendingResult = goAsync()

            val exceptionHandler = CoroutineExceptionHandler { _, exception ->
                Log.e("BootReceiver", "Uncaught exception in BootReceiver scope", exception)
                pendingResult.finish()
            }
            CoroutineScope(Dispatchers.IO + SupervisorJob() + exceptionHandler).launch {
                try {
                    val reminderManager = ReminderManager.getInstance(context)
                    reminderManager.rescheduleAllReminders()
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}

/**
 * BroadcastReceiver for ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED (Android 12+).
 * When the user grants or revokes exact alarm permission from system settings,
 * reschedule all active reminders to use the best available alarm type.
 */
class ExactAlarmPermissionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            intent.action == AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED) {

            Log.d("ExactAlarmPermReceiver", "Exact alarm permission state changed — rescheduling reminders")

            val pendingResult = goAsync()

            val exceptionHandler = CoroutineExceptionHandler { _, exception ->
                Log.e("ExactAlarmPermReceiver", "Error rescheduling after permission change", exception)
                pendingResult.finish()
            }
            CoroutineScope(Dispatchers.IO + SupervisorJob() + exceptionHandler).launch {
                try {
                    val reminderManager = ReminderManager.getInstance(context)
                    reminderManager.rescheduleAllReminders()
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}

