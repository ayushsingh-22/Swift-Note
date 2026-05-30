package com.amvarpvtltd.swiftNote.ai

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.amvarpvtltd.swiftNote.security.GeminiKeyManager
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.GenerationConfig
import com.google.ai.client.generativeai.type.ResponseStoppedException
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.UUID

/**
 * Gemini-powered intelligent reminder parser using the FREE Gemini Developer API.
 *
 * Gemini is ONLY available when the USER provides their own API key in Settings.
 * No developer key is bundled — zero cost for the app publisher.
 *
 * Get a free key: https://aistudio.google.com/apikey
 *
 * Supports multiple user-provided keys with automatic rotation on rate limits.
 */
class GeminiReminderParser private constructor(private val context: Context) {

    private val TAG = "GeminiReminderParser"

    companion object {
        @Volatile
        private var INSTANCE: GeminiReminderParser? = null

        fun getInstance(context: Context): GeminiReminderParser {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: GeminiReminderParser(context.applicationContext).also { INSTANCE = it }
            }
        }

        /** Same ordered list as AITitleGenerator — shared free-tier model pool. */
        private val FREE_MODELS = listOf(
            "gemini-2.5-flash",
            "gemini-2.5-flash-lite",
            "gemini-2.0-flash",
            "gemini-2.0-flash-lite",
            "gemini-2.5-pro",
            "gemini-1.5-flash",
            "gemini-1.5-flash-8b",
            "gemini-1.5-pro",
        )

        // Rate limiting: max 10 reminder calls per minute (conservative free-tier)
        private const val MAX_CALLS_PER_MINUTE = 10
        private val callTimestamps = mutableListOf<Long>()

        private const val PREFS_NAME          = "gemini_reminder_parser_prefs"
        private const val KEY_LAST_GOOD_MODEL = "last_working_model"

        /**
         * Call this when user updates/adds/removes API keys to force model re-creation.
         */
        fun invalidate() {
            INSTANCE?.modelCache?.clear()
            INSTANCE?.currentApiKey = null
        }
    }

    private val prefs by lazy { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

    private var currentApiKey: String? = null
    private val modelCache = mutableMapOf<String, GenerativeModel>()

    /**
     * Get or create a GenerativeModel for the given model name.
     */
    private fun getModel(modelName: String): GenerativeModel? {
        val userKey = GeminiKeyManager.getActiveApiKey(context)
        if (userKey.isBlank()) return null

        if (userKey != currentApiKey) {
            currentApiKey = userKey
            modelCache.clear()
        }

        return modelCache.getOrPut(modelName) {
            GenerativeModel(
                modelName = modelName,
                apiKey = userKey,
                generationConfig = GenerationConfig.Builder().apply {
                    temperature = 0.1f
                    maxOutputTokens = 1024
                    topP = 0.8f
                }.build()
            )
        }
    }

    /**
     * Ordered model list: last-known-good model first, then remaining models.
     */
    private fun orderedModels(): List<String> {
        val lastGood = prefs.getString(KEY_LAST_GOOD_MODEL, null)?.takeIf { it in FREE_MODELS }
        return if (lastGood != null) listOf(lastGood) + FREE_MODELS.filter { it != lastGood }
               else FREE_MODELS
    }

    /**
     * Check if Gemini is available (user has provided API key and enabled it).
     */
    fun isAvailable(): Boolean = GeminiKeyManager.isEnabled(context)

    /**
     * Show a toast message on the main thread (safe to call from IO coroutine).
     */
    private fun showToast(message: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Check if we're within rate limits.
     */
    private fun canMakeCall(): Boolean {
        val now = System.currentTimeMillis()
        callTimestamps.removeAll { now - it > 60_000 }
        return callTimestamps.size < MAX_CALLS_PER_MINUTE
    }

    private fun recordCall() {
        callTimestamps.add(System.currentTimeMillis())
    }

    /**
     * Parse natural language text using Gemini to extract reminder information.
     * Returns structured ParsedReminderIntent with all detected properties.
     *
     * @param text The user's note text (can be English, Hindi, Hinglish, or mixed)
     * @param noteTitle Optional note title for context
     * @return ParsedReminderIntent or null if no reminder intent detected
     */
    suspend fun parseReminderIntent(
        text: String,
        noteTitle: String = ""
    ): ParsedReminderIntent? = withContext(Dispatchers.IO) {
        if (text.isBlank()) return@withContext null

        if (!isAvailable()) {
            Log.d(TAG, "⚠️ Gemini not available (user hasn't added API key in Settings)")
            return@withContext null
        }

        if (!canMakeCall()) {
            Log.w(TAG, "⚠️ Rate limit reached, skipping Gemini call")
            showToast("AI rate limit reached. Please wait a moment before trying again.")
            return@withContext null
        }

        val truncatedText = if (text.length > 800) {
            Log.d(TAG, "✂️ Truncating input from ${text.length} to 800 chars to avoid MAX_TOKENS")
            text.take(800) + "…"
        } else text

        val currentTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss EEEE", Locale.ENGLISH)
            .format(Calendar.getInstance().time)
        val prompt = buildPrompt(truncatedText, noteTitle, currentTime)

        for (modelName in orderedModels()) {
            try {
                val model = getModel(modelName) ?: return@withContext null

                Log.d(TAG, "🤖 Trying $modelName for reminder extraction…")
                recordCall()
                GeminiKeyManager.recordUsage(context)

                val response = try {
                    model.generateContent(content { text(prompt) })
                } catch (e: com.google.ai.client.generativeai.type.ResponseStoppedException) {
                    Log.w(TAG, "⚠️ $modelName hit MAX_TOKENS — attempting to parse partial response")
                    e.response
                }

                val responseText = response.text?.trim()
                if (responseText.isNullOrBlank()) {
                    Log.w(TAG, "⚠️ $modelName returned empty response, trying next model")
                    continue
                }

                Log.d(TAG, "🤖 $modelName response received (${responseText.length} chars)")

                // Save as last-known-good model
                prefs.edit().putString(KEY_LAST_GOOD_MODEL, modelName).apply()
                return@withContext parseGeminiResponse(responseText)

            } catch (e: Exception) {
                val msg = e.message?.lowercase() ?: ""
                when {
                    msg.contains("resource_exhausted") || msg.contains("429") ||
                    msg.contains("quota") || msg.contains("rate limit") -> {
                        Log.w(TAG, "⏳ $modelName: quota exceeded → trying next model")
                        continue
                    }
                    msg.contains("invalid api key") || msg.contains("permission_denied") ||
                    msg.contains("unauthenticated") -> {
                        Log.e(TAG, "🔐 $modelName: auth error — aborting")
                        return@withContext null
                    }
                    else -> {
                        Log.e(TAG, "❌ $modelName: ${e.message} → trying next model")
                        continue
                    }
                }
            }
        }

        Log.e(TAG, "🚫 All models exhausted for reminder parsing")
        showToast("Gemini AI unavailable. Try again later or add another API key in AI Settings.")
        return@withContext null
    }

    /**
     * Validate an API key by making a minimal test call, trying each free model in order.
     * Returns true if ANY model responds successfully or returns a quota error
     * (quota error = key is authenticated, just temporarily limited).
     */
    suspend fun validateApiKey(apiKey: String): Boolean = withContext(Dispatchers.IO) {
        for (modelName in FREE_MODELS) {
            try {
                val testModel = GenerativeModel(
                    modelName = modelName,
                    apiKey = apiKey,
                    generationConfig = GenerationConfig.Builder().apply {
                        temperature = 0.1f
                        maxOutputTokens = 32
                    }.build()
                )
                val response = testModel.generateContent(content { text("Reply with just: OK") })
                if (!response.text.isNullOrBlank()) {
                    Log.i(TAG, "✅ Key valid — $modelName responded successfully")
                    // Save as starting model for this key
                    prefs.edit().putString(KEY_LAST_GOOD_MODEL, modelName).apply()
                    return@withContext true
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ $modelName validation: ${e.message}")
                val msg = e.message?.lowercase() ?: ""
                when {
                    // Quota error = key IS authenticated — accept the key
                    msg.contains("resource_exhausted") || msg.contains("429") ||
                    msg.contains("quota") || msg.contains("rate limit") -> {
                        Log.i(TAG, "✅ Key valid (quota hit on $modelName — key accepted)")
                        showToast("API key verified ✓  (quota limit reached — you may need to wait a moment before first use)")
                        return@withContext true
                    }
                    // Auth error = truly bad key — try next model name (might be unsupported)
                    msg.contains("invalid api key") || msg.contains("permission_denied") ||
                    msg.contains("unauthenticated") -> {
                        // The key itself is rejected; no point trying other model names
                        Log.e(TAG, "🔐 Key rejected by $modelName — invalid key")
                        return@withContext false
                    }
                    // Model not found / unsupported — try next model
                    msg.contains("not found") || msg.contains("404") ||
                    msg.contains("model") -> {
                        Log.w(TAG, "⚠️ $modelName not available, trying next model…")
                        continue
                    }
                    else -> continue
                }
            }
        }
        Log.e(TAG, "❌ Key could not be validated against any model")
        false
    }

    /**
     * Build the structured prompt for Gemini.
     */
    private fun buildPrompt(text: String, noteTitle: String, currentTime: String): String {
        return """You are a reminder extraction AI for a note-taking app. Analyze the following text and extract reminder information.

CURRENT DATE/TIME: $currentTime

NOTE TITLE: "${noteTitle.ifBlank { "Untitled" }}"
NOTE TEXT: "$text"

Extract ALL reminder intents from the text. The text may be in English, Hindi, Hinglish (mixed Hindi-English), or any combination.

Common Hinglish patterns to understand:
- "kal" = tomorrow, "aaj" = today, "parso" = day after tomorrow
- "subah/subh" = morning, "shaam" = evening, "raat" = night, "dopahar" = afternoon
- "baje/bje/bjey" = o'clock (e.g., "5 baje" = 5 o'clock)
- "roz" = daily, "har din" = every day, "har hafte" = every week
- "har mahine" = every month, "har saal" = every year
- "yaad dilana/dila" = remind, "yaad karna" = remember
- "karna hai" = have to do, "hona hai" = to happen

Respond ONLY with valid JSON in this exact format (no markdown, no explanation):
{"hasReminder":true,"reminders":[{"title":"short reminder title","description":"full context","dateTime":"YYYY-MM-DD HH:mm","confidence":0.9,"recurrence":{"type":"NONE","interval":1,"daysOfWeek":null},"extractedPhrase":"original text fragment"}]}

Rules:
- If no clear reminder intent exists, return {"hasReminder": false, "reminders": []}
- For ambiguous times without AM/PM: morning context (subah/morning) = AM, evening context (shaam/raat) = PM
- For standalone numbers 1-6 without context, assume PM. For 7-11 without context, assume AM.
- "kal" when said on ${currentTime.split(" ").last()} means the NEXT day
- confidence should be 0.9+ for explicit reminders, 0.7-0.9 for implied ones
- Keep title concise (3-6 words max)
- ALWAYS include the full dateTime even for recurring reminders (use the NEXT occurrence)
- type must be one of: NONE, DAILY, WEEKLY, MONTHLY, YEARLY"""
    }

    /**
     * Parse Gemini's JSON response into structured data.
     */
    private fun parseGeminiResponse(responseText: String): ParsedReminderIntent? {
        try {
            // Clean up response — Gemini sometimes wraps in ```json ... ```
            val cleanJson = responseText
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()

            val json = JSONObject(cleanJson)

            if (!json.optBoolean("hasReminder", false)) {
                return null
            }

            val remindersArray = json.optJSONArray("reminders") ?: return null
            if (remindersArray.length() == 0) return null

            val reminders = mutableListOf<GeminiDetectedReminder>()

            for (i in 0 until remindersArray.length()) {
                val reminderJson = remindersArray.getJSONObject(i)

                val dateTimeStr = reminderJson.optString("dateTime", "")
                val timestamp = parseDateTimeString(dateTimeStr) ?: continue

                // Only accept future reminders
                if (timestamp <= System.currentTimeMillis()) continue

                val recurrenceJson = reminderJson.optJSONObject("recurrence")
                val recurrence = if (recurrenceJson != null) {
                    val type = recurrenceJson.optString("type", "NONE")
                    if (type != "NONE") {
                        DetectedRecurrence(
                            type = type,
                            interval = recurrenceJson.optInt("interval", 1),
                            daysOfWeek = recurrenceJson.optString("daysOfWeek", "").ifBlank { null }
                        )
                    } else null
                } else null

                reminders.add(
                    GeminiDetectedReminder(
                        title = reminderJson.optString("title", "Reminder"),
                        description = reminderJson.optString("description", ""),
                        dateTime = timestamp,
                        confidence = reminderJson.optDouble("confidence", 0.8).toFloat(),
                        recurrence = recurrence,
                        extractedPhrase = reminderJson.optString("extractedPhrase", "")
                    )
                )
            }

            return if (reminders.isNotEmpty()) {
                ParsedReminderIntent(reminders = reminders)
            } else null

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error parsing Gemini response: ${e.message}", e)
            return null
        }
    }

    /**
     * Parse a date-time string in "YYYY-MM-DD HH:mm" format.
     */
    private fun parseDateTimeString(dateTimeStr: String): Long? {
        if (dateTimeStr.isBlank()) return null
        return try {
            val format = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            format.parse(dateTimeStr)?.time
        } catch (e: Exception) {
            Log.w(TAG, "Could not parse dateTime: $dateTimeStr")
            null
        }
    }

    /**
     * Convert Gemini results to the standard DetectedReminder format used throughout the app.
     */
    fun toDetectedReminders(parsed: ParsedReminderIntent, noteTitle: String): List<DetectedReminder> {
        return parsed.reminders.map { geminiReminder ->
            DetectedReminder(
                id = UUID.randomUUID().toString(),
                title = geminiReminder.title,
                description = geminiReminder.description,
                extractedText = geminiReminder.extractedPhrase,
                reminderDateTime = geminiReminder.dateTime,
                confidence = geminiReminder.confidence,
                entityType = "GeminiAI",
                originalNoteTitle = noteTitle,
                detectedRecurrence = geminiReminder.recurrence
            )
        }
    }
}

/**
 * Structured result from Gemini parsing.
 */
data class ParsedReminderIntent(
    val reminders: List<GeminiDetectedReminder>
)

/**
 * Single reminder detected by Gemini.
 */
data class GeminiDetectedReminder(
    val title: String,
    val description: String,
    val dateTime: Long,
    val confidence: Float,
    val recurrence: DetectedRecurrence?,
    val extractedPhrase: String
)

