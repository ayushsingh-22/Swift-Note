package com.amvarpvtltd.swiftNote.cleanup

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Handles cleanup when the app is being uninstalled or package is being replaced
 */
class UninstallCleanupReceiver : BroadcastReceiver() {
    private val TAG = "UninstallCleanupReceiver"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return

        val action = intent.action
        val packageName = intent.data?.schemeSpecificPart

        Log.d(TAG, "Received action: $action for package: $packageName")

        // Only process if it's our own package
        if (packageName != context.packageName) return

        when (action) {
            Intent.ACTION_PACKAGE_FULLY_REMOVED -> {
                // App is being completely uninstalled
                Log.i(TAG, "App is being uninstalled - starting cleanup")
                performCleanup(context, isFullUninstall = true)
            }
            Intent.ACTION_PACKAGE_DATA_CLEARED -> {
                // App data is being cleared
                Log.i(TAG, "App data is being cleared - starting cleanup")
                performCleanup(context, isFullUninstall = false)
            }
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                // App is being updated - optional cleanup
                Log.i(TAG, "App is being updated")
                // We typically don't clean data on updates
            }
        }
    }

    private fun performCleanup(context: Context, isFullUninstall: Boolean) {
        scope.launch {
            try {
                if (isFullUninstall) {
                    // Complete cleanup for uninstall
                    val result = DataCleanupManager.clearAllAppData(context)
                    if (result.isSuccess) {
                        Log.i(TAG, "✅ Complete cleanup successful for uninstall")
                    } else {
                        Log.e(TAG, "❌ Cleanup failed for uninstall: ${result.exceptionOrNull()?.message}")
                    }
                } else {
                    // Partial cleanup for data clear
                    val result = DataCleanupManager.clearDataForReset(context)
                    if (result.isSuccess) {
                        Log.i(TAG, "✅ Reset cleanup successful")
                    } else {
                        Log.e(TAG, "❌ Reset cleanup failed: ${result.exceptionOrNull()?.message}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Cleanup operation failed", e)
            }
        }
    }
}
