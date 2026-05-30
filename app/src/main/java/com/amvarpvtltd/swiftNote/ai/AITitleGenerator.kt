package com.amvarpvtltd.swiftNote.ai

import android.content.Context
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

/**
 * AI-powered title generator reusing the SAME Gemini setup the user already configured.
 *
 * Uses the same API key from AI Settings (GeminiKeyManager) — no extra setup needed.
 * Strategy: Try Gemini → fall back to rule-based [AutoTitleGenerator] on failure.
 */
class AITitleGenerator private constructor(private val context: Context) {

    companion object {
        private const val TAG = "AITitleGenerator"

        @Volatile
        private var INSTANCE: AITitleGenerator? = null

        fun getInstance(context: Context): AITitleGenerator {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AITitleGenerator(context.applicationContext).also { INSTANCE = it }
            }
        }

        // Rate limit: max 5 title calls per minute (preserve free-tier quota)
        private const val MAX_CALLS_PER_MINUTE = 5
        private val callTimestamps = mutableListOf<Long>()

        private const val TIMEOUT_MS = 8000L
    }

    // Same pattern as GeminiReminderParser — cache model, recreate on key change
    private var currentApiKey: String? = null
    private var generativeModel: GenerativeModel? = null

    /**
     * Reuses the same API key from AI Settings (GeminiKeyManager).
     * Same model (gemini-2.0-flash), just with lower maxOutputTokens for titles.
     */
    private fun getModel(): GenerativeModel? {
        val userKey = GeminiKeyManager.getActiveApiKey(context)
        if (userKey.isBlank()) {
            Log.d(TAG, "❌ No Gemini API key found in AI Settings")
            return null
        }

        if (userKey != currentApiKey) {
            currentApiKey = userKey
            generativeModel = GenerativeModel(
                modelName = "gemini-2.0-flash",
                apiKey = userKey,
                generationConfig = GenerationConfig.Builder().apply {
                    temperature = 0.3f
                    maxOutputTokens = 30
                    topP = 0.9f
                }.build()
            )
            Log.d(TAG, "✅ Gemini model created (same key as AI Settings)")
        }

        return generativeModel
    }

    // ──────────────────── PUBLIC API ────────────────────

    /**
     * Check if Gemini is available — same key user already added in AI Settings.
     */
    fun isAvailable(): Boolean {
        return GeminiKeyManager.getActiveApiKey(context).isNotBlank()
    }

    /**
     * Generate a title using Gemini, falling back to rule-based.
     * Call from a coroutine on IO dispatcher.
     */
    suspend fun generate(description: String): String {
        if (description.isBlank()) {
            Log.d(TAG, "⚠️ Description blank, skipping")
            return ""
        }

        val plainText = getPlainText(description)
        if (plainText.isBlank()) {
            Log.d(TAG, "⚠️ Plain text blank after stripping, skipping")
            return ""
        }

        Log.d(TAG, "🔍 Triggered. Text: ${plainText.take(60)}... (${plainText.length} chars)")
        Log.d(TAG, "🔑 Key present: ${GeminiKeyManager.getActiveApiKey(context).isNotBlank()}")

        if (!canMakeCall()) {
            Log.w(TAG, "⚠️ Rate limited ($MAX_CALLS_PER_MINUTE/min), using rules")
            return AutoTitleGenerator.generate(description)
        }

        // Try Gemini (same key from AI Settings)
        val title = tryGemini(plainText)
        if (!title.isNullOrBlank() && title.length >= 3) {
            Log.d(TAG, "✨ AI title: \"$title\"")
            return title
        }

        Log.d(TAG, "📝 Gemini failed/empty, using rule-based fallback")
        return AutoTitleGenerator.generate(description)
    }

    // ──────────────────── GEMINI CALL ────────────────────

    private suspend fun tryGemini(plainText: String): String? = withContext(Dispatchers.IO) {
        try {
            val model = getModel() ?: return@withContext null

            recordCall()
            GeminiKeyManager.recordUsage(context)

            val prompt = buildPrompt(plainText)
            Log.d(TAG, "🤖 Sending to Gemini (${prompt.length} chars)...")

            val response = withTimeoutOrNull(TIMEOUT_MS) {
                model.generateContent(content { text(prompt) })
            }

            if (response == null) {
                Log.w(TAG, "❌ Timed out (${TIMEOUT_MS}ms)")
                return@withContext null
            }

            val raw = response.text?.trim()
            if (raw.isNullOrBlank()) {
                Log.w(TAG, "❌ Empty response from Gemini")
                return@withContext null
            }

            Log.d(TAG, "🤖 Raw response: \"$raw\"")
            return@withContext cleanResponse(raw)

        } catch (e: Exception) {
            Log.e(TAG, "❌ Gemini failed: ${e.message}", e)
            return@withContext null
        }
    }

    // ──────────────────── PROMPT ────────────────────

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

    // ──────────────────── RESPONSE CLEANING ────────────────────

    private fun cleanResponse(raw: String): String? {
        var cleaned = raw
            .removePrefix("Title:")
            .removePrefix("title:")
            .trim()
            .removeSurrounding("\"")
            .removeSurrounding("'")
            .removeSurrounding("`")
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

    // ──────────────────── RATE LIMITING ────────────────────

    private fun canMakeCall(): Boolean {
        val now = System.currentTimeMillis()
        callTimestamps.removeAll { now - it > 60_000 }
        return callTimestamps.size < MAX_CALLS_PER_MINUTE
    }

    private fun recordCall() {
        callTimestamps.add(System.currentTimeMillis())
    }

    // ──────────────────── UTILS ────────────────────

    private fun getPlainText(description: String): String {
        if (ChecklistParser.isChecklistContent(description)) {
            val items = ChecklistParser.parseItems(description)
            return items.filter { it.text.isNotBlank() }
                .joinToString("\n") { "- ${it.text}" }
        }

        return if (RichTextBridge.containsHtml(description)) {
            RichTextBridge.stripHtmlToPlainText(description)
        } else {
            description
        }.trim()
    }
}
