package com.amvarpvtltd.swiftNote.ai

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.amvarpvtltd.swiftNote.checklist.ChecklistParser
import com.amvarpvtltd.swiftNote.richtext.RichTextBridge
import com.amvarpvtltd.swiftNote.security.GeminiKeyManager
import com.amvarpvtltd.swiftNote.utils.AutoTitleGenerator
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
 * AI-powered title generator that rotates across all free-tier Gemini models.
 *
 * - Tries models in order; on quota/rate-limit skips to the next model.
 * - Remembers the last model that succeeded and tries it FIRST on the next call.
 * - Enforces a per-minute AND a daily safety cap to protect free-tier quota.
 * - Falls back to [AutoTitleGenerator] if every model is exhausted.
 */
class AITitleGenerator private constructor(private val context: Context) {

    companion object {
        private const val TAG = "AITitleGenerator"

        @Volatile
        private var INSTANCE: AITitleGenerator? = null

        fun getInstance(context: Context): AITitleGenerator =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: AITitleGenerator(context.applicationContext).also { INSTANCE = it }
            }

        /**
         * Free-tier Gemini models in preference order (fastest / cheapest first).
         * The list is tried top-to-bottom on quota errors; the winner is cached.
         */
        val FREE_MODELS = listOf(
            "gemini-2.5-flash",           // fastest, newest — try first
            "gemini-2.5-flash-lite",      // ultra-light version
            "gemini-2.0-flash",           // stable workhorse
            "gemini-2.0-flash-lite",      // lighter 2.0 variant
            "gemini-2.5-pro",             // most capable free model
            "gemini-1.5-flash",           // reliable older model
            "gemini-1.5-flash-8b",        // smallest / most quota-friendly
            "gemini-1.5-pro",             // last resort
        )

        // ── Rate-limit constants ──────────────────────────────────────────────
        /** Max title calls per 60-second window (conservative — preserves reminder quota too). */
        private const val MAX_CALLS_PER_MINUTE = 3
        /**
         * Daily safety cap across ALL models.
         * Free tier gives ~1 500 req/day per model; 60 total is ~4 % — very safe.
         */
        private const val MAX_CALLS_PER_DAY = 60
        /** Per-model network timeout. */
        private const val TIMEOUT_MS = 10_000L

        // ── SharedPrefs keys ──────────────────────────────────────────────────
        private const val PREFS_NAME          = "ai_title_generator_prefs"
        private const val KEY_LAST_GOOD_MODEL = "last_working_model"
        private const val KEY_DAY             = "daily_call_date"
        private const val KEY_DAY_COUNT       = "daily_call_count"

        // In-memory per-minute window
        private val callTimestamps = mutableListOf<Long>()
    }

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    // Model cache — recreated only when the API key changes
    private var currentApiKey: String? = null
    private val modelCache = mutableMapOf<String, GenerativeModel>()

    // ────────────────────────── PUBLIC API ───────────────────────────────────

    fun isAvailable(): Boolean = GeminiKeyManager.getActiveApiKey(context).isNotBlank()

    suspend fun generate(description: String): String {
        if (description.isBlank()) return ""
        val plainText = getPlainText(description)
        if (plainText.isBlank()) return ""

        Log.d(TAG, "🔍 Triggered. Text: ${plainText.take(60)}... (${plainText.length} chars)")
        Log.d(TAG, "🔑 Key present: ${GeminiKeyManager.getActiveApiKey(context).isNotBlank()}")

        if (!canMakeCall()) {
            Log.w(TAG, "⚠️ Rate/daily limited, using rules")
            return AutoTitleGenerator.generate(description)
        }

        val title = tryAllModels(plainText)
        if (!title.isNullOrBlank() && title.length >= 3) {
            Log.d(TAG, "✨ AI title: \"$title\"")
            return title
        }

        Log.d(TAG, "📝 Gemini failed/empty, using rule-based fallback")
        return AutoTitleGenerator.generate(description)
    }

    // ────────────────────────── MODEL ROTATION ───────────────────────────────

    private suspend fun tryAllModels(plainText: String): String? {
        val userKey = GeminiKeyManager.getActiveApiKey(context)
        if (userKey.isBlank()) return null

        // Invalidate model cache if the API key changed
        if (userKey != currentApiKey) {
            currentApiKey = userKey
            modelCache.clear()
            Log.d(TAG, "🔄 API key changed — model cache cleared")
        }

        // Put the last known-good model at the front so we skip the search phase
        val lastGood = prefs.getString(KEY_LAST_GOOD_MODEL, null)?.takeIf { it in FREE_MODELS }
        val orderedModels = if (lastGood != null) {
            listOf(lastGood) + FREE_MODELS.filter { it != lastGood }
        } else {
            FREE_MODELS
        }

        for (modelName in orderedModels) {
            when (val result = tryModel(modelName, userKey, plainText)) {
                is ModelResult.Success -> {
                    // Persist the winner so next call starts here
                    if (lastGood != modelName) {
                        prefs.edit().putString(KEY_LAST_GOOD_MODEL, modelName).apply()
                        Log.d(TAG, "💾 Saved working model: $modelName")
                    }
                    recordCall()
                    GeminiKeyManager.recordUsage(context)
                    return result.title
                }
                is ModelResult.QuotaError -> {
                    Log.w(TAG, "⏳ $modelName: quota/rate exceeded → trying next model")
                }
                is ModelResult.AuthError  -> {
                    Log.e(TAG, "🔐 $modelName: auth/permission error — no point retrying with same key")
                    return null
                }
                is ModelResult.Timeout    -> Log.w(TAG, "⏱ $modelName: timed out → trying next model")
                is ModelResult.OtherError -> Log.w(TAG, "❌ $modelName: ${result.message} → trying next model")
            }
        }

        Log.e(TAG, "🚫 All ${FREE_MODELS.size} models exhausted")
        return null
    }

    private suspend fun tryModel(
        modelName: String,
        apiKey: String,
        plainText: String
    ): ModelResult = withContext(Dispatchers.IO) {
        try {
            val model = modelCache.getOrPut(modelName) {
                GenerativeModel(
                    modelName = modelName,
                    apiKey = apiKey,
                    generationConfig = GenerationConfig.Builder().apply {
                        temperature = 0.3f
                        maxOutputTokens = 30
                        topP = 0.9f
                    }.build()
                )
            }

            Log.d(TAG, "🤖 Trying $modelName (${plainText.length} chars)…")
            val response = withTimeoutOrNull(TIMEOUT_MS) {
                model.generateContent(content { text(buildPrompt(plainText)) })
            } ?: return@withContext ModelResult.Timeout

            val raw = response.text?.trim()
            if (raw.isNullOrBlank()) return@withContext ModelResult.OtherError("Empty response")

            val cleaned = cleanResponse(raw)
                ?: return@withContext ModelResult.OtherError("Bad response format: \"$raw\"")

            ModelResult.Success(cleaned)

        } catch (e: Exception) {
            classifyException(e)
        }
    }

    private fun classifyException(e: Exception): ModelResult {
        val msg = e.message?.lowercase() ?: ""
        return when {
            // Coroutine cancelled (composition left, user navigated away) — not a quota issue
            e is kotlinx.coroutines.CancellationException ||
            msg.contains("coroutine scope left") ||
            msg.contains("cancellationexception") ->
                ModelResult.OtherError("Composition cancelled")

            // Quota / rate-limit (429 / RESOURCE_EXHAUSTED) — valid key, skip model
            msg.contains("resource_exhausted") || msg.contains("quota") ||
            msg.contains("429") || msg.contains("rate limit") ->
                ModelResult.QuotaError

            // Auth errors — wrong / revoked key, no point continuing
            msg.contains("invalid api key") || msg.contains("api_key_invalid") ||
            msg.contains("permission_denied") || msg.contains("unauthenticated") ||
            msg.contains("403") || msg.contains("401") ->
                ModelResult.AuthError

            else -> ModelResult.OtherError(e.message ?: "Unknown error")
        }
    }

    // ────────────────────────── RATE LIMITING ────────────────────────────────

    private fun canMakeCall(): Boolean {
        val now = System.currentTimeMillis()

        // Per-minute window
        callTimestamps.removeAll { now - it > 60_000 }
        if (callTimestamps.size >= MAX_CALLS_PER_MINUTE) {
            Log.w(TAG, "⚠️ Per-minute cap reached ($MAX_CALLS_PER_MINUTE/min)")
            return false
        }

        // Daily cap
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val savedDay = prefs.getString(KEY_DAY, "")
        val dayCount = if (savedDay == today) prefs.getInt(KEY_DAY_COUNT, 0)
                       else { resetDailyCounter(today); 0 }

        if (dayCount >= MAX_CALLS_PER_DAY) {
            Log.w(TAG, "⚠️ Daily cap reached ($MAX_CALLS_PER_DAY/day)")
            return false
        }
        return true
    }

    /** Called only on SUCCESS to avoid counting failed attempts. */
    private fun recordCall() {
        callTimestamps.add(System.currentTimeMillis())
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val savedDay = prefs.getString(KEY_DAY, "")
        val current = if (savedDay == today) prefs.getInt(KEY_DAY_COUNT, 0) else 0
        prefs.edit()
            .putString(KEY_DAY, today)
            .putInt(KEY_DAY_COUNT, current + 1)
            .apply()
    }

    private fun resetDailyCounter(today: String) {
        prefs.edit().putString(KEY_DAY, today).putInt(KEY_DAY_COUNT, 0).apply()
    }

    // ────────────────────────── PROMPT ───────────────────────────────────────

    private fun buildPrompt(plainText: String): String {
        val content = if (plainText.length > 300) plainText.take(300) + "..." else plainText
        return """Generate a short title (2-6 words) for this note. Reply with ONLY the title, nothing else.

Rules:
- Maximum 6 words, title case
- No quotes, no explanation, no punctuation at end
- Capture the core topic/action
- If it's a task, start with the verb

Note:
$content

Title:"""
    }

    // ────────────────────────── RESPONSE CLEANING ────────────────────────────

    private fun cleanResponse(raw: String): String? {
        var cleaned = raw
            .removePrefix("Title:").removePrefix("title:")
            .trim()
            .removeSurrounding("\"").removeSurrounding("'").removeSurrounding("`")
            .trim()
            .replace(Regex("\\.$"), "")
            .trim()

        if (cleaned.length < 3 || cleaned.length > 50) {
            Log.d(TAG, "🧹 Rejected (length=${cleaned.length}): \"$cleaned\"")
            return null
        }
        if (cleaned.contains("\n")) cleaned = cleaned.lines().first().trim()
        if (cleaned.split(" ").size > 8) {
            Log.d(TAG, "🧹 Rejected (too many words): \"$cleaned\"")
            return null
        }
        if (cleaned.lowercase().let {
                it.startsWith("here") || it.startsWith("note about") || it.startsWith("title")
            }) {
            Log.d(TAG, "🧹 Rejected (bad prefix): \"$cleaned\"")
            return null
        }
        Log.d(TAG, "🧹 Accepted: \"$cleaned\"")
        return AutoTitleGenerator.truncate(cleaned)
    }

    // ────────────────────────── UTILS ────────────────────────────────────────

    private fun getPlainText(description: String): String {
        if (ChecklistParser.isChecklistContent(description)) {
            return ChecklistParser.parseItems(description)
                .filter { it.text.isNotBlank() }
                .joinToString("\n") { "- ${it.text}" }
        }
        return if (RichTextBridge.containsHtml(description)) {
            RichTextBridge.stripHtmlToPlainText(description)
        } else {
            description
        }.trim()
    }
}

// ──────────────────────── Sealed result hierarchy ────────────────────────────

private sealed class ModelResult {
    data class Success(val title: String) : ModelResult()
    object QuotaError                     : ModelResult()
    object AuthError                      : ModelResult()
    object Timeout                        : ModelResult()
    data class OtherError(val message: String) : ModelResult()
}
