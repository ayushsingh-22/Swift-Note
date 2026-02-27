package com.amvarpvtltd.swiftNote

import android.app.Application
import com.google.firebase.FirebaseApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import android.util.Log

class MyApplication : Application() {
    companion object {
        private const val TAG = "MyApplication"
        private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    }

    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
        AppContext.appContext = applicationContext

        // Ensure we have a stable device/account id available globally for older code paths
        try {
            val storedPass = com.amvarpvtltd.swiftNote.auth.PassphraseManager.getStoredPassphrase(this)
            val deviceId = com.amvarpvtltd.swiftNote.auth.DeviceManager.getOrCreateDeviceId(this)
            myGlobalMobileDeviceId = storedPass ?: deviceId
        } catch (e: Exception) {
            // If anything fails, fallback to a random UUID
            myGlobalMobileDeviceId = java.util.UUID.randomUUID().toString()
        }

        Log.d(TAG, "MyApplication initialized")
    }

    override fun onTerminate() {
        super.onTerminate()
        Log.d(TAG, "MyApplication terminating")
        // Note: onTerminate() is only called in emulated environments
        // Real cleanup happens through the UninstallCleanupReceiver
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
