package com.amvarpvtltd.swiftNote.utils

import android.content.Context
import android.content.SharedPreferences

class PreferenceManager private constructor(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun isFirstNotificationRequest(): Boolean {
        return prefs.getBoolean(KEY_FIRST_NOTIFICATION_REQUEST, true)
    }

    fun setFirstNotificationRequest(isFirst: Boolean) {
        prefs.edit().putBoolean(KEY_FIRST_NOTIFICATION_REQUEST, isFirst).apply()
    }

    fun getNotificationDenialCount(): Int {
        return prefs.getInt(KEY_NOTIFICATION_DENIAL_COUNT, 0)
    }

    fun incrementNotificationDenialCount() {
        val currentCount = getNotificationDenialCount()
        prefs.edit().putInt(KEY_NOTIFICATION_DENIAL_COUNT, currentCount + 1).apply()
    }

    fun resetNotificationDenialCount() {
        prefs.edit().putInt(KEY_NOTIFICATION_DENIAL_COUNT, 0).apply()
    }

    fun shouldShowPermissionRequest(): Boolean {
        val denialCount = getNotificationDenialCount()
        return denialCount <= 1 // Show request only for first time and one retry
    }

    fun hasSeenSettings(): Boolean {
        return prefs.getBoolean(KEY_HAS_SEEN_SETTINGS, false)
    }

    fun setHasSeenSettings(seen: Boolean) {
        prefs.edit().putBoolean(KEY_HAS_SEEN_SETTINGS, seen).apply()
    }

    fun getLastRequestTime(): Long {
        return prefs.getLong(KEY_LAST_REQUEST_TIME, 0)
    }

    fun setLastRequestTime(time: Long) {
        prefs.edit().putLong(KEY_LAST_REQUEST_TIME, time).apply()
    }

    companion object {
        private const val PREF_NAME = "SwiftNotePrefs"
        private const val KEY_FIRST_NOTIFICATION_REQUEST = "first_notification_request"
        private const val KEY_NOTIFICATION_DENIAL_COUNT = "notification_denial_count"
        private const val KEY_HAS_SEEN_SETTINGS = "has_seen_settings"
        private const val KEY_LAST_REQUEST_TIME = "last_request_time"

        // Minimum time between permission requests (24 hours)
        const val MIN_REQUEST_INTERVAL = 24 * 60 * 60 * 1000L

        @Volatile
        private var instance: PreferenceManager? = null

        fun getInstance(context: Context): PreferenceManager {
            return instance ?: synchronized(this) {
                instance ?: PreferenceManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
