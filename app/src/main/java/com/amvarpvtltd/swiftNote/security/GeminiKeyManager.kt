package com.amvarpvtltd.swiftNote.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import org.json.JSONObject

/**
 * Manages user's Gemini API keys securely using EncryptedSharedPreferences.
 *
 * Supports MULTIPLE API keys so users can:
 * - Add backup keys in case one hits rate limits
 * - Rotate keys without downtime
 * - Use different keys for different Google accounts
 *
 * Keys never leave the device. No key is bundled by the developer.
 * Gemini only works when the user provides their own free API key.
 *
 * Get a free key: https://aistudio.google.com/apikey
 */
object GeminiKeyManager {

    private const val TAG = "GeminiKeyManager"
    private const val PREFS_NAME = "gemini_secure_prefs"
    private const val KEY_API_KEYS = "gemini_api_keys_json"
    private const val KEY_ACTIVE_KEY_ID = "gemini_active_key_id"
    private const val KEY_ENABLED = "gemini_enabled"

    // In-memory cache
    private var cachedKeys: List<GeminiApiKey>? = null
    private var cachedActiveKeyId: String? = null

    private fun getPrefs(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        return EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    // ──────────────────── Multi-Key Management ────────────────────

    /**
     * Get all stored API keys.
     */
    fun getAllKeys(context: Context): List<GeminiApiKey> {
        if (cachedKeys != null) return cachedKeys!!

        val prefs = getPrefs(context)
        val json = prefs.getString(KEY_API_KEYS, "[]") ?: "[]"
        val keys = mutableListOf<GeminiApiKey>()

        try {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                keys.add(
                    GeminiApiKey(
                        id = obj.getString("id"),
                        label = obj.getString("label"),
                        apiKey = obj.getString("apiKey"),
                        addedAt = obj.getLong("addedAt"),
                        lastUsed = obj.optLong("lastUsed", 0L),
                        usageCount = obj.optInt("usageCount", 0)
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading keys: ${e.message}")
        }

        cachedKeys = keys
        return keys
    }

    /**
     * Add a new API key.
     */
    fun addApiKey(context: Context, label: String, apiKey: String): GeminiApiKey {
        val keys = getAllKeys(context).toMutableList()
        val newKey = GeminiApiKey(
            id = "key_${System.currentTimeMillis()}",
            label = label.ifBlank { "Key ${keys.size + 1}" },
            apiKey = apiKey.trim(),
            addedAt = System.currentTimeMillis()
        )
        keys.add(newKey)
        saveKeys(context, keys)

        // If this is the first key, auto-activate and enable
        if (keys.size == 1) {
            setActiveKeyId(context, newKey.id)
            setEnabled(context, true)
        }

        return newKey
    }

    /**
     * Remove an API key by ID.
     */
    fun removeApiKey(context: Context, keyId: String) {
        val keys = getAllKeys(context).toMutableList()
        keys.removeAll { it.id == keyId }
        saveKeys(context, keys)

        // If we removed the active key, switch to next available
        if (getActiveKeyId(context) == keyId) {
            val nextKey = keys.firstOrNull()
            if (nextKey != null) {
                setActiveKeyId(context, nextKey.id)
            } else {
                setActiveKeyId(context, null)
                setEnabled(context, false)
            }
        }
    }

    /**
     * Update a key's label.
     */
    fun updateKeyLabel(context: Context, keyId: String, newLabel: String) {
        val keys = getAllKeys(context).toMutableList()
        val idx = keys.indexOfFirst { it.id == keyId }
        if (idx >= 0) {
            keys[idx] = keys[idx].copy(label = newLabel)
            saveKeys(context, keys)
        }
    }

    /**
     * Record usage of the active key (called after each Gemini API call).
     */
    fun recordUsage(context: Context) {
        val activeId = getActiveKeyId(context) ?: return
        val keys = getAllKeys(context).toMutableList()
        val idx = keys.indexOfFirst { it.id == activeId }
        if (idx >= 0) {
            keys[idx] = keys[idx].copy(
                lastUsed = System.currentTimeMillis(),
                usageCount = keys[idx].usageCount + 1
            )
            saveKeys(context, keys)
        }
    }

    // ──────────────────── Active Key ────────────────────

    /**
     * Get the currently active key ID.
     */
    fun getActiveKeyId(context: Context): String? {
        if (cachedActiveKeyId != null) return cachedActiveKeyId
        val id = getPrefs(context).getString(KEY_ACTIVE_KEY_ID, null)
        cachedActiveKeyId = id
        return id
    }

    /**
     * Set which key is active.
     */
    fun setActiveKeyId(context: Context, keyId: String?) {
        getPrefs(context).edit().putString(KEY_ACTIVE_KEY_ID, keyId).apply()
        cachedActiveKeyId = keyId
    }

    /**
     * Get the currently active API key string (for use by GeminiReminderParser).
     * Returns empty string if none configured.
     */
    fun getActiveApiKey(context: Context): String {
        val activeId = getActiveKeyId(context) ?: return ""
        val keys = getAllKeys(context)
        return keys.find { it.id == activeId }?.apiKey ?: keys.firstOrNull()?.apiKey ?: ""
    }

    // ──────────────────── Enable/Disable ────────────────────

    /**
     * Check if Gemini is enabled by user AND has at least one key.
     */
    fun isEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_ENABLED, false) && getActiveApiKey(context).isNotBlank()
    }

    /**
     * Check if at least one key is configured (regardless of enabled state).
     */
    fun isConfigured(context: Context): Boolean {
        return getAllKeys(context).isNotEmpty()
    }

    /**
     * Enable or disable Gemini (without removing keys).
     */
    fun setEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    // ──────────────────── Helpers ────────────────────

    private fun saveKeys(context: Context, keys: List<GeminiApiKey>) {
        val array = JSONArray()
        keys.forEach { key ->
            array.put(JSONObject().apply {
                put("id", key.id)
                put("label", key.label)
                put("apiKey", key.apiKey)
                put("addedAt", key.addedAt)
                put("lastUsed", key.lastUsed)
                put("usageCount", key.usageCount)
            })
        }
        getPrefs(context).edit().putString(KEY_API_KEYS, array.toString()).apply()
        cachedKeys = keys
    }

    /**
     * Clear all keys and disable Gemini.
     */
    fun clearAll(context: Context) {
        getPrefs(context).edit().clear().apply()
        cachedKeys = null
        cachedActiveKeyId = null
    }
}

/**
 * Represents a stored Gemini API key.
 */
data class GeminiApiKey(
    val id: String,
    val label: String,
    val apiKey: String,
    val addedAt: Long,
    val lastUsed: Long = 0L,
    val usageCount: Int = 0
) {
    /** Masked version for display (e.g., "AIzaSy...bvRc") */
    val maskedKey: String
        get() = if (apiKey.length > 10) {
            "${apiKey.take(6)}...${apiKey.takeLast(4)}"
        } else "••••••"
}

