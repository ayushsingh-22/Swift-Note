package com.amvarpvtltd.swiftNote

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.content.ContextCompat
import com.google.firebase.FirebaseApp
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import android.util.Log

class MyApplication : Application() {
    companion object {
        private const val TAG = "MyApplication"
        const val CHANNEL_NOTE_REMINDERS = "note_reminders"
        const val CHANNEL_SMART_REMINDERS = "smart_reminders"
        private val exceptionHandler = CoroutineExceptionHandler { _, exception ->
            Log.e(TAG, "Uncaught exception in applicationScope", exception)
        }
        private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main + exceptionHandler)
    }

    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
        AppContext.appContext = applicationContext

        // Create notification channels at startup before any notification is posted
        createNotificationChannels()

        // Phase 5B: Enqueue widget update worker
        com.amvarpvtltd.swiftNote.widget.WidgetUpdateWorker.enqueue(this)

        // Ensure we have a stable device/account id available globally for older code paths
        try {
            val storedPass = com.amvarpvtltd.swiftNote.auth.PassphraseManager.getStoredPassphrase(this)
            val deviceId = com.amvarpvtltd.swiftNote.auth.DeviceManager.getOrCreateDeviceId(this)
            DeviceIdentity.set(storedPass ?: deviceId, "Application.onCreate")
        } catch (e: Exception) {
            // If anything fails, fallback to a random UUID
            DeviceIdentity.set(java.util.UUID.randomUUID().toString(), "Application.onCreate:fallback")
        }

        Log.d(TAG, "MyApplication initialized")
    }

    /**
     * Create all notification channels at app startup.
     * Channels must exist before any notification is posted (Android 8+).
     * Creating an existing channel is a no-op, so this is safe to call multiple times.
     */
    private fun createNotificationChannels() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Channel for note reminders (used by SystemNotificationHelper)
        val reminderChannel = NotificationChannel(
            CHANNEL_NOTE_REMINDERS,
            "Note Reminders",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Notifications for note reminders"
            enableVibration(true)
            enableLights(true)
            lightColor = ContextCompat.getColor(this@MyApplication, R.color.purple_500)
            setShowBadge(true)
        }

        // Channel for smart reminders (used by ReminderManager)
        val smartReminderChannel = NotificationChannel(
            CHANNEL_SMART_REMINDERS,
            "Smart Reminders",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Notifications for smart reminders from your notes"
            enableVibration(true)
            enableLights(true)
            setShowBadge(true)
        }

        notificationManager.createNotificationChannel(reminderChannel)
        notificationManager.createNotificationChannel(smartReminderChannel)
    }

    override fun onTerminate() {
        super.onTerminate()
        Log.d(TAG, "MyApplication terminating")
    }

    /**
     * Manually trigger data cleanup (for testing or manual reset)
     */
    fun clearAllAppData() {
        applicationScope.launch {
            try {
                val result = com.amvarpvtltd.swiftNote.cleanup.DataCleanupManager.clearAllAppData(applicationContext)
                if (result.isSuccess) {
                    Log.i(TAG, "✅ Manual app data cleanup successful")
                } else {
                    Log.e(TAG, "❌ Manual app data cleanup failed: ${result.exceptionOrNull()?.message}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Manual cleanup operation failed", e)
            }
        }
    }
}
