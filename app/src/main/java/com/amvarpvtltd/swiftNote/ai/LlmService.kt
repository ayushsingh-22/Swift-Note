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
 * Routes requests per [AiModePreferences]: pinned to one provider, or Automatic mode
 * which tries the preferred/default-ordered configured provider and falls back to the
 * other configured provider only on retryable failures.
 *
 * Usage:
 *   val result = LlmService.getInstance(context).generateText(prompt, maxTokens = 30)
 */
class LlmService private constructor(private val context: Context) {

    companion object {
        private const val TAG = "LlmService"
        private const val PREFS_NAME = "llm_service_prefs"
        private const val KEY_LAST_GOOD_MODEL = "last_working_model"
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
    private val callTimestamps = mutableMapOf<LlmProvider, MutableList<Long>>()

    /** Provider that actually fulfilled the most recent successful [generateText] call. */
    private var lastUsedProvider: LlmProvider? = null

    // ────────────────────────── PUBLIC API ─────────────────────────────────────

    /**
     * Check if any LLM key is configured and enabled.
     */
    fun isAvailable(): Boolean = GeminiKeyManager.isEnabled(context)

    /**
     * Get the provider that handled the most recent successful call, falling back to
     * the active key's provider if nothing has run yet this session.
     */
    fun getActiveProvider(): LlmProvider {
        lastUsedProvider?.let { return it }
        val activeKey = GeminiKeyManager.getActiveKeyObject(context) ?: return LlmProvider.GEMINI
        return LlmProvider.fromName(activeKey.provider)
    }

    /**
     * Generate text using the user's configured AI mode (Automatic / Gemini / Groq — see
     * [AiModePreferences]). In Automatic mode, tries the preferred (or default-ordered)
     * configured provider first and falls back to the other configured provider only on
     * retryable failures (rate limit / timeout / network). Invalid-key / auth failures never
     * trigger a silent cross-provider fallback — the call simply fails so the caller's existing
     * ML Kit/regex fallback takes over, and the bad key surfaces in AI Settings.
     *
     * @param prompt Full prompt (for Gemini) or user message (for Groq)
     * @param systemPrompt Optional system prompt (used by Groq; ignored by Gemini which uses prompt only)
     * @param temperature Creativity control (0.0–1.0)
     * @param maxTokens Max response tokens
     * @return Generated text, or null if all eligible providers failed / rate limited
     */
    suspend fun generateText(
        prompt: String,
        systemPrompt: String? = null,
        temperature: Float = 0.3f,
        maxTokens: Int = 256
    ): String? {
        if (!isAvailable()) return null

        val configured = LlmProvider.entries.filter { GeminiKeyManager.hasKeyForProvider(context, it) }
        if (configured.isEmpty()) return null

        val mode = AiModePreferences.getMode(context)
        val order: List<LlmProvider> = when (mode) {
            AiMode.GEMINI -> listOf(LlmProvider.GEMINI)
            AiMode.GROQ -> listOf(LlmProvider.GROQ)
            AiMode.AUTOMATIC -> {
                val preferred = AiModePreferences.getPreferredProvider(context)
                val ordered = if (preferred != null) {
                    listOf(preferred) + LlmProvider.entries.filterNot { it == preferred }
                } else {
                    LlmProvider.entries.toList()
                }
                if (AiModePreferences.isFallbackEnabled(context)) ordered else ordered.take(1)
            }
        }.filter { it in configured }

        for (provider in order) {
            val key = GeminiKeyManager.getKeyForProvider(context, provider) ?: continue
            if (!canMakeCall(provider)) {
                Log.w(TAG, "⚠️ $provider rate/daily limited — trying next provider")
                continue
            }

            val result = when (provider) {
                LlmProvider.GEMINI -> tryGeminiModels(key.apiKey, prompt, temperature, maxTokens)
                LlmProvider.GROQ -> tryGroqModels(key.apiKey, prompt, systemPrompt, temperature, maxTokens)
            }

            when (result) {
                is ProviderCallResult.Success -> {
                    recordCall(provider)
                    GeminiKeyManager.recordUsage(context, key.id)
                    lastUsedProvider = provider
                    return result.text
                }
                is ProviderCallResult.AuthError -> {
                    Log.e(TAG, "🔐 $provider auth error — aborting (no silent fallback): ${result.message}")
                    return null
                }
                is ProviderCallResult.RetryableError -> {
                    Log.w(TAG, "⏳ $provider retryable failure — trying next provider: ${result.message}")
                    continue
                }
            }
        }
        Log.e(TAG, "🚫 All configured providers exhausted")
        return null
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
    ): ProviderCallResult {
        if (apiKey != currentApiKey) {
            currentApiKey = apiKey
            geminiModelCache.clear()
        }

        val lastGood = prefs.getString(lastGoodModelKey(LlmProvider.GEMINI), null)
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
                    return ProviderCallResult.Success(text)
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
                        return ProviderCallResult.AuthError(e.message ?: "Invalid Gemini API key")
                    }
                    else -> {
                        Log.w(TAG, "❌ Gemini/$modelName: ${e.message?.take(80)} → next")
                        continue
                    }
                }
            }
        }
        Log.e(TAG, "🚫 All Gemini models exhausted")
        return ProviderCallResult.RetryableError("All Gemini models exhausted or rate limited")
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
    ): ProviderCallResult {
        val availableModels = getGroqModels(apiKey)
        val lastGood = prefs.getString(lastGoodModelKey(LlmProvider.GROQ), null)
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
                    return ProviderCallResult.Success(text)
                }
            } catch (e: GroqApiException) {
                when {
                    e.statusCode == 429 -> {
                        Log.w(TAG, "⏳ Groq/$modelName: rate limited → next")
                        continue
                    }
                    e.statusCode == 401 || e.statusCode == 403 -> {
                        Log.e(TAG, "🔐 Groq auth error — aborting")
                        return ProviderCallResult.AuthError(e.message ?: "Invalid Groq API key")
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
        return ProviderCallResult.RetryableError("All Groq models exhausted or rate limited")
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

    private fun lastGoodModelKey(provider: LlmProvider) = "${KEY_LAST_GOOD_MODEL}_${provider.name}"

    private fun saveLastGoodModel(model: String, provider: LlmProvider) {
        prefs.edit { putString(lastGoodModelKey(provider), model) }
    }

    /** Rate limits are tracked independently per provider so one provider being
     * throttled never blocks Automatic mode from trying the other. */
    private fun canMakeCall(provider: LlmProvider): Boolean {
        val maxPerMinute = if (provider == LlmProvider.GROQ) GROQ_MAX_PER_MINUTE else GEMINI_MAX_PER_MINUTE
        val maxPerDay    = if (provider == LlmProvider.GROQ) GROQ_MAX_PER_DAY    else GEMINI_MAX_PER_DAY

        val now = System.currentTimeMillis()
        val timestamps = callTimestamps.getOrPut(provider) { mutableListOf() }
        timestamps.removeAll { now - it > 60_000 }
        if (timestamps.size >= maxPerMinute) return false

        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val dayKey = "${KEY_DAY}_${provider.name}"
        val countKey = "${KEY_DAY_COUNT}_${provider.name}"
        val savedDay = prefs.getString(dayKey, "")
        val dayCount = if (savedDay == today) prefs.getInt(countKey, 0) else 0

        return dayCount < maxPerDay
    }

    private fun recordCall(provider: LlmProvider) {
        callTimestamps.getOrPut(provider) { mutableListOf() }.add(System.currentTimeMillis())
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val dayKey = "${KEY_DAY}_${provider.name}"
        val countKey = "${KEY_DAY_COUNT}_${provider.name}"
        val savedDay = prefs.getString(dayKey, "")
        val current = if (savedDay == today) prefs.getInt(countKey, 0) else 0
        prefs.edit { putString(dayKey, today); putInt(countKey, current + 1) }
    }
}

/**
 * Outcome of a single provider's generation attempt — lets [LlmService.generateText]
 * distinguish "try the next provider" (retryable) from "stop, don't fall back silently"
 * (auth/invalid-key, per the fallback rules in AGENTS.md / AI settings UX).
 */
private sealed class ProviderCallResult {
    data class Success(val text: String) : ProviderCallResult()
    data class AuthError(val message: String) : ProviderCallResult()
    data class RetryableError(val message: String) : ProviderCallResult()
}

/**
 * Result of validating an API key.
 */
sealed class KeyValidationResult {
    object Success : KeyValidationResult()
    data class Failed(val errorMessage: String) : KeyValidationResult()
}

