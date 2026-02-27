package com.amvarpvtltd.swiftNote.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.amvarpvtltd.swiftNote.MainActivity
import com.amvarpvtltd.swiftNote.R

/**
 * Helper class for managing system-level Android notifications
 * (This is different from the in-app NotificationHelper which handles UI notifications)
 */
class SystemNotificationHelper(private val context: Context) {

    companion object {
        const val CHANNEL_ID = "note_reminders"
        const val CHANNEL_NAME = "Note Reminders"
        const val CHANNEL_DESCRIPTION = "Notifications for note reminders"
        const val NOTIFICATION_ID_BASE = 1000
    }

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = CHANNEL_DESCRIPTION
            enableVibration(true)
            enableLights(true)
            lightColor = ContextCompat.getColor(context, R.color.purple_500)
            setShowBadge(true)
        }

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    /**
     * Show a unified reminder notification that opens the specific note when clicked
     */
    fun showReminderNotification(
        reminderId: String,
        noteId: String,
        noteTitle: String,
        noteDescription: String,
        isSmartReminder: Boolean = false
    ) {
        if (!hasNotificationPermission()) {
            return
        }

        // Create an intent that will open the specific note when clicked
        val intent = Intent(context, MainActivity::class.java).apply {
            // Pass noteId to MainActivity to trigger navigation to the specific note
            putExtra("noteId", noteId)
            // Clear any previous activities and start a fresh instance
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            noteId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action: Mark as Done
        val markDoneIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = "MARK_DONE"
            putExtra("reminderId", reminderId)
            putExtra("noteId", noteId)
            putExtra("noteTitle", noteTitle)
        }
        val markDonePendingIntent = PendingIntent.getBroadcast(
            context,
            (reminderId + "_done").hashCode(),
            markDoneIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action: Snooze 10 min
        val snoozeIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = "SNOOZE"
            putExtra("reminderId", reminderId)
            putExtra("noteId", noteId)
            putExtra("noteTitle", noteTitle)
            putExtra("noteDescription", noteDescription)
        }
        val snoozePendingIntent = PendingIntent.getBroadcast(
            context,
            (reminderId + "_snooze").hashCode(),
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Determine icon based on reminder type
        val icon = if (isSmartReminder) "🤖 " else "📝 "

        // Format title with emoji based on content
        val displayTitle = when {
            noteTitle.contains("appointment", ignoreCase = true) -> "📅 $noteTitle"
            noteTitle.contains("meeting", ignoreCase = true) -> "🤝 $noteTitle"
            noteTitle.contains("call", ignoreCase = true) -> "📞 $noteTitle"
            noteTitle.contains("deadline", ignoreCase = true) -> "⏰ $noteTitle"
            noteTitle.contains("reminder", ignoreCase = true) -> "🔔 $noteTitle"
            else -> "$icon$noteTitle"
        }

        // Create a rich notification with large text style
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.logo2)
            .setContentTitle(displayTitle)
            .setContentText(noteDescription.ifEmpty { "Tap to view your note" })
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText(noteDescription.ifEmpty { "Tap to view your note" })
                .setBigContentTitle(displayTitle)
                .setSummaryText("SwiftNote Reminder"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setColor(ContextCompat.getColor(context, R.color.purple_500))
            .setColorized(true)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Done", markDonePendingIntent)
            .addAction(android.R.drawable.ic_popup_reminder, "Snooze 10 min", snoozePendingIntent)
            .setOngoing(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

        val notificationId = NOTIFICATION_ID_BASE + reminderId.hashCode()

        try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        } catch (e: SecurityException) {
            android.util.Log.e("SystemNotificationHelper", "Permission denied for notifications", e)
            android.widget.Toast.makeText(
                context,
                "Notification permission required for reminders",
                android.widget.Toast.LENGTH_SHORT
            ).show()

            // Show in-app notification using our new NotificationComponent
            com.amvarpvtltd.swiftNote.components.NotificationHelper.showError(
                title = "Permission Required",
                message = "Notification permission required for reminders"
            )
        }
    }

    fun cancelNotification(reminderId: String) {
        val notificationId = NOTIFICATION_ID_BASE + reminderId.hashCode()
        NotificationManagerCompat.from(context).cancel(notificationId)
    }

    private fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }
}
