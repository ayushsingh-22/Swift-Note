package com.amvarpvtltd.swiftNote.permissions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

/**
 * Modular Permission Manager for handling notification permissions
 * This class can be easily extended to handle other permissions in the future
 */
class PermissionManager(
    private val activity: ComponentActivity,
    private val onPermissionResult: ((Boolean) -> Unit)? = null
) : DefaultLifecycleObserver {

    companion object {
        const val NOTIFICATION_PERMISSION = Manifest.permission.POST_NOTIFICATIONS
        private const val PREFS_NAME = "permission_prefs"
        private const val KEY_NOTIFICATION_REQUESTED = "notification_permission_requested"
    }

    private var notificationPermissionLauncher: ActivityResultLauncher<String>? = null

    /**
     * Configuration for permission messages and behavior
     */
    data class PermissionConfig(
        val rationaleMessage: String = "📱 SwiftNote needs notification permission to send you smart reminders for your notes.",
        val grantedMessage: String = "✅ Notification permission granted! You'll receive smart reminders.",
        val deniedMessage: String = "⚠️ Notification permission denied. You won't receive reminders.",
        val showToastMessages: Boolean = true,
        val requestOnlyOnce: Boolean = true
    )

    private val config = PermissionConfig()

    init {
        // Register the permission launcher
        registerPermissionLauncher()

        // Add this as a lifecycle observer
        activity.lifecycle.addObserver(this)
    }

    private fun registerPermissionLauncher() {
        notificationPermissionLauncher = activity.registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            handlePermissionResult(isGranted)
        }
    }

    /**
     * Check if notification permission is granted
     */
    fun isNotificationPermissionGranted(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                activity,
                NOTIFICATION_PERMISSION
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            // For Android 12 and below, permission is granted by default
            true
        }
    }

    /**
     * Request notification permission if needed
     */
    fun requestNotificationPermissionIfNeeded() {
        // Only request for Android 13+ (API 33+)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            // For older versions, permission is automatically granted
            handlePermissionResult(true)
            return
        }

        // Check if we should request only once
        if (config.requestOnlyOnce && hasPermissionBeenRequested()) {
            return
        }

        when {
            isNotificationPermissionGranted() -> {
                // Permission already granted
                handlePermissionResult(true)
            }

            activity.shouldShowRequestPermissionRationale(NOTIFICATION_PERMISSION) -> {
                // Show rationale and request permission
                if (config.showToastMessages) {
                    Toast.makeText(activity, config.rationaleMessage, Toast.LENGTH_LONG).show()
                }
                requestPermission()
            }

            else -> {
                // Directly request permission
                requestPermission()
            }
        }
    }

    /**
     * Force request permission (even if requested before)
     */
    fun forceRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (!isNotificationPermissionGranted()) {
                requestPermission()
            } else {
                handlePermissionResult(true)
            }
        } else {
            handlePermissionResult(true)
        }
    }

    private fun requestPermission() {
        markPermissionAsRequested()
        notificationPermissionLauncher?.launch(NOTIFICATION_PERMISSION)
    }

    private fun handlePermissionResult(isGranted: Boolean) {
        if (config.showToastMessages) {
            val message = if (isGranted) config.grantedMessage else config.deniedMessage
            Toast.makeText(activity, message, Toast.LENGTH_LONG).show()
        }

        // Call the custom callback if provided
        onPermissionResult?.invoke(isGranted)
    }

    private fun hasPermissionBeenRequested(): Boolean {
        val prefs = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_NOTIFICATION_REQUESTED, false)
    }

    private fun markPermissionAsRequested() {
        val prefs = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_NOTIFICATION_REQUESTED, true).apply()
    }

    /**
     * Clear the "requested" flag - useful for testing or if you want to re-ask
     */
    fun clearPermissionRequestFlag() {
        val prefs = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_NOTIFICATION_REQUESTED).apply()
    }

    /**
     * Check if the app can send notifications (considering all factors)
     */
    fun canSendNotifications(): Boolean {
        return isNotificationPermissionGranted()
    }

    override fun onDestroy(owner: LifecycleOwner) {
        super.onDestroy(owner)
        activity.lifecycle.removeObserver(this)
    }
}

/**
 * Extension function to easily create and use PermissionManager
 */
fun ComponentActivity.createPermissionManager(
    onPermissionResult: ((Boolean) -> Unit)? = null
): PermissionManager {
    return PermissionManager(this, onPermissionResult)
}
