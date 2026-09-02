package com.amvarpvtltd.swiftNote.ai

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import com.amvarpvtltd.swiftNote.security.GeminiKeyManager
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.GenerationConfig
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Unified LLM service that abstracts over Gemini and Groq.
 *
 * This is the single entry-point for AI calls across the app.
 * Routes requests to the correct provider based on the active key's provider setting.
 *
 * Usage:
 *   val result = LlmService.getInstance(context).generateText(prompt, maxTokens = 30)
 */
class LlmService private constructor(private val context: Context) {

    companion object {
        private const val TAG = "LlmService"
        private const val PREFS_NAME = "llm_service_prefs"
        private const val KEY_LAST_GOOD_MODEL = "last_working_model"
        private const val KEY_LAST_GOOD_PROVIDER = "last_working_provider"
        private const val KEY_DAY = "daily_call_date"
        private const val KEY_DAY_COUNT = "daily_call_count"
        private const val KEY_GROQ_MODELS_CACHE = "groq_models_cache"
        private const val KEY_GROQ_MODELS_CACHE_TIME = "groq_models_cache_time"
        private const val GROQ_MODELS_CACHE_TTL_MS = 24 * 60 * 60 * 1000L // refresh daily

        // Gemini free-tier limits
        private const val GEMINI_MAX_PER_MINUTE = 5
        private const val GEMINI_MAX_PER_DAY    = 80

        // Groq free-tier limits
        private const val GROQ_MAX_PER_MINUTE = 30
        private const val GROQ_MAX_PER_DAY    = 250

        private const val TIMEOUT_MS = 12_000L

        private val callTimestamps = mutableListOf<Long>()

        @SuppressLint("StaticFieldLeak") // Stores applicationContext only — safe
        @Volatile
        private var INSTANCE: LlmService? = null

        fun getInstance(context: Context): LlmService =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: LlmService(context.applicationContext).also { INSTANCE = it }
            }

        /** Call when user adds/removes/changes keys to reset cached state. */
        fun invalidate() {
            INSTANCE?.geminiModelCache?.clear()
            INSTANCE?.currentApiKey = null
            INSTANCE?.prefs?.edit {
                remove(KEY_GROQ_MODELS_CACHE_TIME) // force a fresh /models fetch next call
            }
        }
    }

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private var currentApiKey: String? = null
    private val geminiModelCache = mutableMapOf<String, GenerativeModel>()

    // ────────────────────────── PUBLIC API ─────────────────────────────────────

    /**
     * Check if any LLM key is configured and enabled.
     */
    fun isAvailable(): Boolean = GeminiKeyManager.isEnabled(context)

    /**
     * Get the active provider (based on the currently active key's setting).
     */
    fun getActiveProvider(): LlmProvider {
        val activeKey = GeminiKeyManager.getActiveKeyObject(context) ?: return LlmProvider.GEMINI
        return LlmProvider.fromName(activeKey.provider)
    }

    /**
     * Generate text using the active LLM provider.
     *
     * @param prompt Full prompt (for Gemini) or user message (for Groq)
     * @param systemPrompt Optional system prompt (used by Groq; ignored by Gemini which uses prompt only)
     * @param temperature Creativity control (0.0–1.0)
     * @param maxTokens Max response tokens
     * @return Generated text, or null if all models failed / rate limited
     */
    suspend fun generateText(
        prompt: String,
        systemPrompt: String? = null,
        temperature: Float = 0.3f,
        maxTokens: Int = 256
    ): String? {
        if (!isAvailable()) return null
        if (!canMakeCall()) {
            Log.w(TAG, "⚠️ Rate/daily limited")
            return null
        }

        val activeKey = GeminiKeyManager.getActiveKeyObject(context) ?: return null
        val provider = LlmProvider.fromName(activeKey.provider)
        val apiKey = activeKey.apiKey

        val result = when (provider) {
            LlmProvider.GEMINI -> tryGeminiModels(apiKey, prompt, temperature, maxTokens)
            LlmProvider.GROQ -> tryGroqModels(apiKey, prompt, systemPrompt, temperature, maxTokens)
        }

        if (result != null) {
            recordCall()
            GeminiKeyManager.recordUsage(context)
        }
        return result
    }

    /**
     * Validate an API key for a given provider.
     * Returns a [KeyValidationResult] with success/failure + message.
     */
    suspend fun validateKey(apiKey: String, provider: LlmProvider): KeyValidationResult {
        return when (provider) {
            LlmProvider.GEMINI -> validateGeminiKey(apiKey)
            LlmProvider.GROQ -> validateGroqKey(apiKey)
        }
    }

    // ────────────────────────── GEMINI ─────────────────────────────────────────

    private suspend fun tryGeminiModels(
        apiKey: String,
        prompt: String,
        temperature: Float,
        maxTokens: Int
    ): String? {
        if (apiKey != currentApiKey) {
            currentApiKey = apiKey
            geminiModelCache.clear()
        }

        val lastGood = prefs.getString(KEY_LAST_GOOD_MODEL, null)
            ?.takeIf { it in LlmModels.GEMINI_MODELS }
        val orderedModels = if (lastGood != null) {
            listOf(lastGood) + LlmModels.GEMINI_MODELS.filter { it != lastGood }
        } else LlmModels.GEMINI_MODELS

        for (modelName in orderedModels) {
            try {
                val model = geminiModelCache.getOrPut(modelName) {
                    GenerativeModel(
                        modelName = modelName,
                        apiKey = apiKey,
                        generationConfig = GenerationConfig.Builder().apply {
                            this.temperature = temperature
                            this.maxOutputTokens = maxTokens
                            topP = 0.9f
                        }.build()
                    )
                }

                Log.d(TAG, "🤖 Trying Gemini/$modelName…")
                val response = withTimeoutOrNull(TIMEOUT_MS) {
                    model.generateContent(content { text(prompt) })
                } ?: continue

                val text = response.text?.trim()
                if (!text.isNullOrBlank()) {
                    saveLastGoodModel(modelName, LlmProvider.GEMINI)
                    return text
                }
            } catch (e: Exception) {
                val msg = e.message?.lowercase() ?: ""
                when {
                    e is kotlinx.coroutines.CancellationException -> throw e
                    msg.contains("resource_exhausted") || msg.contains("quota") ||
                    msg.contains("429") || msg.contains("rate limit") -> {
                        Log.w(TAG, "⏳ Gemini/$modelName: quota → next")
                        continue
                    }
                    msg.contains("invalid api key") || msg.contains("permission_denied") ||
                    msg.contains("unauthenticated") -> {
                        Log.e(TAG, "🔐 Gemini auth error — aborting")
                        return null
                    }
                    else -> {
                        Log.w(TAG, "❌ Gemini/$modelName: ${e.message?.take(80)} → next")
                        continue
                    }
                }
            }
        }
        Log.e(TAG, "🚫 All Gemini models exhausted")
        return null
    }

    private suspend fun validateGeminiKey(apiKey: String): KeyValidationResult =
        withContext(Dispatchers.IO) {
            // Quick format sanity check. Google is migrating Gemini keys from the legacy
            // "AIza..." Standard format to the new "AQ.Ab..." Auth format — AIza keys are
            // being phased out (unrestricted ones rejected since 2026-06-19, all of them
            // from September 2026), so both prefixes must be accepted during the transition.
            val trimmed = apiKey.trim()
            val looksValid = trimmed.length >= 20 &&
                (trimmed.startsWith("AIza") || trimmed.startsWith("AQ."))
            if (!looksValid) {
                return@withContext KeyValidationResult.Failed(
                    "Invalid API key format. Gemini keys start with \"AIza\" or \"AQ.\" and are at least 20 characters long."
                )
            }

            var sawAuthError = false
            var lastError: String? = null

            for (modelName in LlmModels.GEMINI_MODELS) {
                try {
                    val testModel = GenerativeModel(
                        modelName = modelName,
                        apiKey = trimmed,
                        generationConfig = GenerationConfig.Builder().apply {
                            temperature = 0.1f
                            maxOutputTokens = 32
                        }.build()
                    )
                    val response = withTimeoutOrNull(TIMEOUT_MS) {
                        testModel.generateContent(content { text("Reply with just: OK") })
                    } ?: run {
                        lastError = "timeout"
                        continue
                    }
                    if (!response.text.isNullOrBlank()) {
                        saveLastGoodModel(modelName, LlmProvider.GEMINI)
                        return@withContext KeyValidationResult.Success
                    }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    val msg = e.message?.lowercase() ?: ""
                    lastError = e.message?.take(120)
                    when {
                        msg.contains("resource_exhausted") || msg.contains("429") ||
                        msg.contains("quota") || msg.contains("rate limit") -> {
                            return@withContext KeyValidationResult.Success // Key is valid, just rate-limited
                        }
                        // Auth/key errors — match every realistic variant Gemini returns.
                        msg.contains("api key not valid") ||           // ← most common real-world response
                        msg.contains("invalid api key") ||
                        msg.contains("api_key_invalid") ||
                        msg.contains("api key expired") ||
                        msg.contains("permission_denied") ||
                        msg.contains("permission denied") ||
                        msg.contains("unauthenticated") ||
                        msg.contains("unauthorized") ||
                        msg.contains("401") ||
                        msg.contains("403") -> {
                            return@withContext KeyValidationResult.Failed(
                                "Invalid API key. Please check it and try again. (Get one at aistudio.google.com/apikey)"
                            )
                        }
                        msg.contains("400") || msg.contains("bad request") -> {
                            // 400 from the Gemini endpoint almost always means a malformed/invalid key.
                            sawAuthError = true
                            continue
                        }
                        msg.contains("not found") || msg.contains("404") -> continue
                        msg.contains("network") || msg.contains("unable to resolve") ||
                        msg.contains("timeout") || msg.contains("unreachable") ||
                        msg.contains("connection") -> {
                            return@withContext KeyValidationResult.Failed(
                                "Network error. Check your internet connection and try again."
                            )
                        }
                        else -> continue
                    }
                }
            }
            if (sawAuthError) {
                KeyValidationResult.Failed(
                    "Invalid API key. Please check it and try again. (Get one at aistudio.google.com/apikey)"
                )
            } else {
                KeyValidationResult.Failed(
                    "Could not validate key against any model. " +
                    (lastError?.let { "Last error: $it. " } ?: "") +
                    "Check your internet connection and that the key is correct."
                )
            }
        }

    // ────────────────────────── GROQ ──────────────────────────────────────────

    /**
     * Get the list of Groq models to try, preferring Groq's live /models endpoint
     * (cached for [GROQ_MODELS_CACHE_TTL_MS]) since Groq retires models often and a
     * hardcoded list otherwise silently starts erroring out (404 model_not_found).
     * Falls back to the static [LlmModels.GROQ_MODELS] list if the fetch fails.
     */
    private suspend fun getGroqModels(apiKey: String): List<String> {
        val cachedAt = prefs.getLong(KEY_GROQ_MODELS_CACHE_TIME, 0L)
        val isFresh = System.currentTimeMillis() - cachedAt < GROQ_MODELS_CACHE_TTL_MS
        if (isFresh) {
            val cached = prefs.getString(KEY_GROQ_MODELS_CACHE, null)
                ?.split(",")
                ?.filter { it.isNotBlank() }
            if (!cached.isNullOrEmpty()) return cached
        }

        val fetched = GroqClient.fetchAvailableModels(apiKey)
        if (!fetched.isNullOrEmpty()) {
            prefs.edit {
                putString(KEY_GROQ_MODELS_CACHE, fetched.joinToString(","))
                putLong(KEY_GROQ_MODELS_CACHE_TIME, System.currentTimeMillis())
            }
            return fetched
        }

        // Live fetch failed (offline, etc) — reuse a stale cache before falling back to static list.
        val stale = prefs.getString(KEY_GROQ_MODELS_CACHE, null)
            ?.split(",")
            ?.filter { it.isNotBlank() }
        return if (!stale.isNullOrEmpty()) stale else LlmModels.GROQ_MODELS
    }

    private suspend fun tryGroqModels(
        apiKey: String,
        prompt: String,
        systemPrompt: String?,
        temperature: Float,
        maxTokens: Int
    ): String? {
        val availableModels = getGroqModels(apiKey)
        val lastGood = prefs.getString(KEY_LAST_GOOD_MODEL, null)
            ?.takeIf { it in availableModels }
        val orderedModels = if (lastGood != null) {
            listOf(lastGood) + availableModels.filter { it != lastGood }
        } else availableModels

        for (modelName in orderedModels) {
            try {
                Log.d(TAG, "🤖 Trying Groq/$modelName…")
                val text = GroqClient.generateContent(
                    apiKey = apiKey,
                    model = modelName,
                    systemPrompt = systemPrompt,
                    userMessage = prompt,
                    temperature = temperature,
                    maxTokens = maxTokens
                )
                if (text.isNotBlank()) {
                    saveLastGoodModel(modelName, LlmProvider.GROQ)
                    return text
                }
            } catch (e: GroqApiException) {
                when {
                    e.statusCode == 429 -> {
                        Log.w(TAG, "⏳ Groq/$modelName: rate limited → next")
                        continue
                    }
                    e.statusCode == 401 || e.statusCode == 403 -> {
                        Log.e(TAG, "🔐 Groq auth error — aborting")
                        return null
                    }
                    e.statusCode == 404 -> {
                        Log.w(TAG, "⚠️ Groq/$modelName: not found → next")
                        continue
                    }
                    else -> {
                        Log.w(TAG, "❌ Groq/$modelName: ${e.message?.take(80)} → next")
                        continue
                    }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.w(TAG, "❌ Groq/$modelName: ${e.message?.take(80)} → next")
                continue
            }
        }
        Log.e(TAG, "🚫 All Groq models exhausted")
        return null
    }

    private suspend fun validateGroqKey(apiKey: String): KeyValidationResult {
        return when (val result = GroqClient.validateKey(apiKey)) {
            is GroqClient.ValidationResult.Success -> KeyValidationResult.Success
            is GroqClient.ValidationResult.QuotaButValid -> KeyValidationResult.Success
            is GroqClient.ValidationResult.AuthError -> KeyValidationResult.Failed(
                "Invalid Groq API key: ${result.message}"
            )
            is GroqClient.ValidationResult.AllModelsFailed -> KeyValidationResult.Failed(
                "Could not validate key. Check your internet connection."
            )
        }
    }

    // ────────────────────────── HELPERS ────────────────────────────────────────

    private fun saveLastGoodModel(model: String, provider: LlmProvider) {
        prefs.edit {
            putString(KEY_LAST_GOOD_MODEL, model)
            putString(KEY_LAST_GOOD_PROVIDER, provider.name)
        }
    }

    private fun canMakeCall(): Boolean {
        val provider = getActiveProvider()
        val maxPerMinute = if (provider == LlmProvider.GROQ) GROQ_MAX_PER_MINUTE else GEMINI_MAX_PER_MINUTE
        val maxPerDay    = if (provider == LlmProvider.GROQ) GROQ_MAX_PER_DAY    else GEMINI_MAX_PER_DAY

        val now = System.currentTimeMillis()
        callTimestamps.removeAll { now - it > 60_000 }
        if (callTimestamps.size >= maxPerMinute) return false

        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val savedDay = prefs.getString(KEY_DAY, "")
        val dayCount = if (savedDay == today) prefs.getInt(KEY_DAY_COUNT, 0)
        else {
            prefs.edit { putString(KEY_DAY, today); putInt(KEY_DAY_COUNT, 0) }
            0
        }

        return dayCount < maxPerDay
    }

    private fun recordCall() {
        callTimestamps.add(System.currentTimeMillis())
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val savedDay = prefs.getString(KEY_DAY, "")
        val current = if (savedDay == today) prefs.getInt(KEY_DAY_COUNT, 0) else 0
        prefs.edit { putString(KEY_DAY, today); putInt(KEY_DAY_COUNT, current + 1) }
    }
}

/**
 * Result of validating an API key.
 */
sealed class KeyValidationResult {
    object Success : KeyValidationResult()
    data class Failed(val errorMessage: String) : KeyValidationResult()
}

