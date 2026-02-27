package com.amvarpvtltd.swiftNote.auth

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import androidx.core.content.edit
import com.amvarpvtltd.swiftNote.generateUniqueDeviceId

private const val TAG = "DeviceIdManager"

object DeviceIdManager {
    private const val PREFS = "device_id_prefs"
    private const val KEY_DEVICE_ID = "device_id"

    @SuppressLint("HardwareIds")
    fun getOrCreateDeviceId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val existing = prefs.getString(KEY_DEVICE_ID, null)
        if (!existing.isNullOrEmpty()) return existing

        val newId = try {
            // Prefer system-based stable id
            generateUniqueDeviceId(context)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get system device id, falling back to UUID", e)
            java.util.UUID.randomUUID().toString()
        }

        prefs.edit { putString(KEY_DEVICE_ID, newId) }
        return newId
    }

    fun markFirstLaunchComplete(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putBoolean("first_launch_complete", true)
        }
    }
}
