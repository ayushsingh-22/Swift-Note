package com.amvarpvtltd.swiftNote.permissions

import android.content.Context
import androidx.activity.ComponentActivity

/**
 * Utility class for easy permission management throughout the app
 * This provides a simple interface for checking permissions from any part of the app
 */
object PermissionUtils {

    /**
     * Check if notification permission is granted
     * Can be called from anywhere in the app with just a context
     */
    fun hasNotificationPermission(context: Context): Boolean {
        return when (context) {
            is ComponentActivity -> {
                val manager = PermissionManager(context)
                manager.isNotificationPermissionGranted()
            }
            else -> {
                // For non-activity contexts, create a temporary manager
                android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU ||
                androidx.core.content.ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            }
        }
    }

    /**
     * Get permission status as a readable string
     */
    fun getPermissionStatusString(context: Context): String {
        return if (hasNotificationPermission(context)) {
            "✅ Notifications Enabled"
        } else {
            "⚠️ Notifications Disabled"
        }
    }

    /**
     * Constants for permission-related features
     */
    object Constants {
        const val NOTIFICATION_CHANNEL_ID = "SwiftNote_reminders"
        const val NOTIFICATION_CHANNEL_NAME = "Smart Reminders"
        const val NOTIFICATION_CHANNEL_DESCRIPTION = "Notifications for your smart reminders"
    }
}
