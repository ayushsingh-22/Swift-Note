package com.amvarpvtltd.swiftNote.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.amvarpvtltd.swiftNote.MainActivity
import com.amvarpvtltd.swiftNote.R
import com.amvarpvtltd.swiftNote.checklist.ChecklistParser
import com.amvarpvtltd.swiftNote.richtext.RichTextBridge

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
            lightColor = ContextCompat.getColor(context, R.color.primary)
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

        // Strip HTML tags and decode HTML entities so the notification body
        // shows clean readable text (e.g. no "<p>…&period;</p>")
        val rawDescription = RichTextBridge.stripHtmlToPlainText(noteDescription).trim()

        // Format checklist content into readable text for notification display
        val displayDescription = if (ChecklistParser.isChecklistContent(noteDescription)) {
            val (checked, total) = ChecklistParser.progress(noteDescription)
            val items = ChecklistParser.parseItems(noteDescription)
            val unchecked = items.filter { !it.isChecked }.take(3)
            if (unchecked.isEmpty()) {
                "✓ All $total items completed!"
            } else {
                "Checklist ($checked/$total done): " + unchecked.joinToString(", ") { it.text }
            }
        } else {
            rawDescription.ifEmpty { "Tap to open your note" }
        }

        // Create an intent that will open the specific note when clicked
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra("noteId", noteId)
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

        // Clean, context-aware title with a single bell emoji
        val displayTitle = when {
            noteTitle.contains("appointment", ignoreCase = true) -> "📅 $noteTitle"
            noteTitle.contains("meeting", ignoreCase = true)     -> "🤝 $noteTitle"
            noteTitle.contains("call", ignoreCase = true)        -> "📞 $noteTitle"
            noteTitle.contains("deadline", ignoreCase = true)    -> "⏰ $noteTitle"
            isSmartReminder -> "🤖 $noteTitle"
            else -> "🔔 $noteTitle"
        }

        // App logo as large icon for rich notification appearance
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            // Small monochrome icon (shown in status bar)
            .setSmallIcon(R.drawable.logo2)
            // Round app logo as the large icon in the notification drawer.
            // Uses getAppIconBitmap() which correctly handles adaptive icon XML on API 26+.
            .setLargeIcon(getAppIconBitmap())
            .setContentTitle(displayTitle)
            .setContentText(displayDescription)
            // BigTextStyle expands the notification to show the full note preview
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(displayDescription)
                    .setBigContentTitle(displayTitle)
                    .setSummaryText(if (isSmartReminder) "SwiftNote · Smart Reminder" else "SwiftNote · Reminder")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            // Brand teal color tints the small icon and accent strip
            .setColor(ContextCompat.getColor(context, R.color.primary))
            .setColorized(false)          // let system handle background; colorized can look busy
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            // Named action buttons (system icons are monochrome; labels are what users see)
            .addAction(
                NotificationCompat.Action.Builder(
                    android.R.drawable.ic_menu_close_clear_cancel,
                    "✓  Done",
                    markDonePendingIntent
                ).build()
            )
            .addAction(
                NotificationCompat.Action.Builder(
                    android.R.drawable.ic_popup_reminder,
                    "⏱  Snooze 10 min",
                    snoozePendingIntent
                ).build()
            )
            .setOngoing(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

        val notificationId = NOTIFICATION_ID_BASE + reminderId.hashCode()

        try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        } catch (e: SecurityException) {
            android.util.Log.e("SystemNotificationHelper", "Permission denied for notifications", e)
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

    /**
     * Renders the app launcher icon to a Bitmap suitable for [NotificationCompat.Builder.setLargeIcon].
     *
     * Problem: On Android 8+ (API 26+) R.mipmap.ic_launcher_round is an AdaptiveIconDrawable (XML),
     * not a raw PNG, so BitmapFactory.decodeResource() returns null on those devices.
     * This helper uses ContextCompat.getDrawable() + Canvas to rasterize ANY drawable type.
     * Falls back to R.drawable.logo2 (plain PNG) if the launcher icon can't be decoded.
     */
    private fun getAppIconBitmap(): Bitmap? {
        return try {
            val size = 192
            val drawable = ContextCompat.getDrawable(context, R.mipmap.ic_launcher_round)
                ?: ContextCompat.getDrawable(context, R.drawable.logo2)
                ?: return null
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, size, size)
            drawable.draw(canvas)
            bitmap
        } catch (e: Exception) {
            android.util.Log.w("SystemNotificationHelper", "Failed to render app icon, using fallback", e)
            try {
                android.graphics.BitmapFactory.decodeResource(context.resources, R.drawable.logo2)
            } catch (_: Exception) {
                null
            }
        }
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
