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

        // Rate limiting: max 15 Gemini calls per minute (free tier limit)
        private const val MAX_CALLS_PER_MINUTE = 15
        private val callTimestamps = mutableListOf<Long>()

        /**
         * Call this when user updates/adds/removes API keys to force model re-creation.
         */
        fun invalidate() {
            INSTANCE?.generativeModel = null
            INSTANCE?.currentApiKey = null
        }
    }

    private var currentApiKey: String? = null
    private var generativeModel: GenerativeModel? = null

    /**
     * Get or create the GenerativeModel using user's active stored API key.
     */
    private fun getModel(): GenerativeModel? {
        val userKey = GeminiKeyManager.getActiveApiKey(context)

        if (userKey.isBlank()) {
            return null
        }

        // Recreate model if key changed
        if (userKey != currentApiKey) {
            currentApiKey = userKey
            generativeModel = GenerativeModel(
                modelName = "gemini-2.0-flash",
                apiKey = userKey,
                generationConfig = GenerationConfig.Builder().apply {
                    temperature = 0.1f  // Low temperature for structured extraction
                    maxOutputTokens = 1024  // Increased from 512 — long notes produced MAX_TOKENS
                    topP = 0.8f
                }.build()
            )
        }

        return generativeModel
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

        val model = getModel()
        if (model == null) {
            Log.w(TAG, "⚠️ Could not create Gemini model")
            return@withContext null
        }

        if (!canMakeCall()) {
            Log.w(TAG, "⚠️ Rate limit reached, skipping Gemini call")
            showToast("AI rate limit reached. Please wait a moment before trying again.")
            return@withContext null
        }

        // Truncate input to prevent MAX_TOKENS on long shared content.
        // Reminder intent is almost always in the first portion of a note.
        val truncatedText = if (text.length > 800) {
            Log.d(TAG, "✂️ Truncating input from ${text.length} to 800 chars to avoid MAX_TOKENS")
            text.take(800) + "…"
        } else text

        try {
            recordCall()
            // Track usage for the active key
            GeminiKeyManager.recordUsage(context)

            val currentTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss EEEE", Locale.ENGLISH)
                .format(Calendar.getInstance().time)

            val prompt = buildPrompt(truncatedText, noteTitle, currentTime)

            Log.d(TAG, "🤖 Sending to Gemini Developer API for analysis...")

            val response = try {
                model.generateContent(content { text(prompt) })
            } catch (e: ResponseStoppedException) {
                // MAX_TOKENS: model generated partial output — try to salvage it
                Log.w(TAG, "⚠️ Gemini hit MAX_TOKENS — attempting to parse partial response")
                e.response
            }

            val responseText = response.text?.trim()
            if (responseText.isNullOrBlank()) return@withContext null

            Log.d(TAG, "🤖 Gemini response received (${responseText.length} chars)")

            return@withContext parseGeminiResponse(responseText)

        } catch (e: Exception) {
            Log.e(TAG, "❌ Gemini parsing failed: ${e.message}", e)
            val message = e.message?.lowercase() ?: ""
            if (message.contains("resource_exhausted") || message.contains("429") ||
                message.contains("quota") || message.contains("rate limit")) {
                showToast("Gemini API limit reached. Try again later or add another API key in AI Settings.")
            }
            return@withContext null
        }
    }

    /**
     * Validate an API key by making a minimal test call.
     * Returns true if the key works, false otherwise.
     */
    suspend fun validateApiKey(apiKey: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val testModel = GenerativeModel(
                modelName = "gemini-2.0-flash",
                apiKey = apiKey,
                generationConfig = GenerationConfig.Builder().apply {
                    temperature = 0.1f
                    maxOutputTokens = 32
                }.build()
            )
            val response = testModel.generateContent(content { text("Reply with just: OK") })
            !response.text.isNullOrBlank()
        } catch (e: Exception) {
            Log.e(TAG, "❌ API key validation failed: ${e.message}")
            val message = e.message?.lowercase() ?: ""
            if (message.contains("resource_exhausted") || message.contains("429") ||
                message.contains("quota") || message.contains("rate limit")) {
                showToast("API rate limit exceeded. Please wait and try again.")
            }
            false
        }
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

