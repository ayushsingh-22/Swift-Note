package com.amvarpvtltd.swiftNote.ai

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.amvarpvtltd.swiftNote.checklist.ChecklistParser
import com.amvarpvtltd.swiftNote.richtext.RichTextBridge
import com.amvarpvtltd.swiftNote.security.GeminiKeyManager
import com.amvarpvtltd.swiftNote.utils.AutoTitleGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * AI-powered title generator that routes through [LlmService].
 *
 * - Supports both Gemini and Groq providers transparently.
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

        // ── Rate-limit constants ──────────────────────────────────────────────
        private const val MAX_CALLS_PER_MINUTE = 3
        private const val MAX_CALLS_PER_DAY = 60
        private const val PREFS_NAME = "ai_title_generator_prefs"
        private const val KEY_DAY = "daily_call_date"
        private const val KEY_DAY_COUNT = "daily_call_count"

        private val callTimestamps = mutableListOf<Long>()
    }

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val llmService by lazy { LlmService.getInstance(context) }

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

        val title = tryGenerate(plainText)
        if (!title.isNullOrBlank() && title.length >= 3) {
            Log.d(TAG, "✨ AI title: \"$title\"")
            return title
        }

        Log.d(TAG, "📝 LLM failed/empty, using rule-based fallback")
        return AutoTitleGenerator.generate(description)
    }

    // ────────────────────────── LLM CALL ──────────────────────────────────────

    private suspend fun tryGenerate(plainText: String): String? = withContext(Dispatchers.IO) {
        val prompt = buildPrompt(plainText)

        val raw = llmService.generateText(
            prompt = prompt,
            systemPrompt = "You are a title generator. Reply with ONLY a short title (2-6 words), nothing else.",
            temperature = 0.3f,
            maxTokens = 30
        )

        if (raw.isNullOrBlank()) return@withContext null

        val cleaned = cleanResponse(raw)
        if (cleaned != null) {
            recordCall()
        }
        cleaned
    }

    // ────────────────────────── RATE LIMITING ────────────────────────────────

    private fun canMakeCall(): Boolean {
        val now = System.currentTimeMillis()
        callTimestamps.removeAll { now - it > 60_000 }
        if (callTimestamps.size >= MAX_CALLS_PER_MINUTE) {
            Log.w(TAG, "⚠️ Per-minute cap reached ($MAX_CALLS_PER_MINUTE/min)")
            return false
        }

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
