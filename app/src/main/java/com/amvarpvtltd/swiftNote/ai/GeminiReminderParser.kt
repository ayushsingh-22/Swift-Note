package com.amvarpvtltd.swiftNote.ai

import android.content.Context
import android.util.Log
import com.amvarpvtltd.swiftNote.security.GeminiKeyManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.UUID

/**
 * LLM-powered intelligent reminder parser.
 *
 * Routes through [LlmService] to support both Gemini and Groq transparently.
 * Only available when the USER provides their own API key in Settings.
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

        // Rate limiting: max 10 reminder calls per minute (conservative free-tier)
        private const val MAX_CALLS_PER_MINUTE = 10
        private val callTimestamps = mutableListOf<Long>()

        /**
         * Call this when user updates/adds/removes API keys to force re-creation.
         */
        fun invalidate() {
            LlmService.invalidate()
        }
    }

    private val llmService by lazy { LlmService.getInstance(context) }

    /**
     * Check if AI is available (user has provided API key and enabled it).
     */
    fun isAvailable(): Boolean = GeminiKeyManager.isEnabled(context)

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
     * Parse natural language text using AI to extract reminder information.
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
            Log.d(TAG, "⚠️ AI not available (user hasn't added API key in Settings)")
            return@withContext null
        }

        if (!canMakeCall()) {
            Log.w(TAG, "⚠️ Rate limit reached, skipping AI call")
            return@withContext null
        }

        val truncatedText = if (text.length > 800) {
            Log.d(TAG, "✂️ Truncating input from ${text.length} to 800 chars")
            text.take(800) + "…"
        } else text

        val currentTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss EEEE", Locale.ENGLISH)
            .format(Calendar.getInstance().time)
        val prompt = buildPrompt(truncatedText, noteTitle, currentTime)

        Log.d(TAG, "🤖 Sending to LLM for reminder extraction…")
        recordCall()
        GeminiKeyManager.recordUsage(context)

        val responseText = llmService.generateText(
            prompt = prompt,
            systemPrompt = "You are a reminder extraction AI for a note-taking app. Respond ONLY with valid JSON.",
            temperature = 0.1f,
            maxTokens = 1024
        )

        if (responseText.isNullOrBlank()) {
            Log.w(TAG, "⚠️ LLM returned empty response")
            return@withContext null
        }

        Log.d(TAG, "🤖 Response received (${responseText.length} chars)")
        return@withContext parseResponse(responseText)
    }

    /**
     * Validate an API key by making a minimal test call.
     * Delegates to [LlmService.validateKey].
     */
    suspend fun validateApiKey(apiKey: String, provider: LlmProvider = LlmProvider.GEMINI): KeyValidationResult {
        return LlmService.getInstance(context).validateKey(apiKey, provider)
    }

    /**
     * Build the structured prompt.
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
- Only create a reminder when the sentence or a nearby sentence describes an actionable future task, event, or follow-up for the user
- Ignore technical specs, ratios, standards, versions, citations, and informational prose even if they contain time-like or date-like text (examples: "18/8", "IS 15997", "daily water use")
- A standalone time/date like "5pm" or "05/09" is not enough unless it is connected to a task, meeting, appointment, reminder, deadline, or similar action
- For ambiguous times without AM/PM: morning context (subah/morning) = AM, evening context (shaam/raat) = PM
- For standalone numbers 1-6 without context, assume PM. For 7-11 without context, assume AM.
- "kal" when said on ${currentTime.split(" ").last()} means the NEXT day
- confidence should be 0.9+ for explicit reminders, 0.7-0.9 for implied ones
- Keep title concise (3-6 words max)
- ALWAYS include the full dateTime even for recurring reminders (use the NEXT occurrence)
- type must be one of: NONE, DAILY, WEEKLY, MONTHLY, YEARLY
- CRITICAL — RELATIVE DURATION RULE: Expressions like "in X minutes", "in X hours", "in X days", "in 1 min", "5 min mein", etc. are ALWAYS computed relative to the CURRENT DATE/TIME shown above. They represent NOW + that duration. Do NOT combine them with any other day/date mentioned elsewhere in the note. Example: if current time is 2026-06-01 23:30 and the text says "Tomorrow is my meeting, remind me in 1 min", the reminder dateTime must be 2026-06-01 23:31 — NOT Jun 2 00:01. The word "tomorrow" in that example only tells you WHAT the reminder is about (the meeting), not WHEN to fire the reminder.
- When the text contains BOTH a day word (today/tomorrow/kal/etc.) AND a duration expression (in X min/hours), always use the duration expression as the trigger time and treat the day word as event context only."""
    }

    /**
     * Parse AI JSON response into structured data.
     */
    private fun parseResponse(responseText: String): ParsedReminderIntent? {
        try {
            // Clean up response — AI sometimes wraps in ```json ... ```
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
            Log.e(TAG, "❌ Error parsing AI response: ${e.message}", e)
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
     * Convert AI results to the standard DetectedReminder format used throughout the app.
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
                entityType = "AI-${llmService.getActiveProvider().displayName}",
                originalNoteTitle = noteTitle,
                detectedRecurrence = geminiReminder.recurrence
            )
        }
    }
}

/**
 * Structured result from AI parsing.
 */
data class ParsedReminderIntent(
    val reminders: List<GeminiDetectedReminder>
)

/**
 * Single reminder detected by AI.
 */
data class GeminiDetectedReminder(
    val title: String,
    val description: String,
    val dateTime: Long,
    val confidence: Float,
    val recurrence: DetectedRecurrence?,
    val extractedPhrase: String
)

