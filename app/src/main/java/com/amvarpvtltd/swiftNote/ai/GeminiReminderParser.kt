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

Extract ALL reminder intents from the text. **The text may be written in ANY language you support** — including but not limited to English, Hindi, Hinglish (mixed Hindi-English), Spanish, French, German, Portuguese, Italian, Dutch, Russian, Polish, Ukrainian, Romanian, Greek, Albanian, Serbian, Croatian, Czech, Slovak, Hungarian, Bulgarian, Arabic, Hebrew, Persian, Turkish, Japanese, Chinese (Simplified/Traditional), Korean, Indonesian, Malay, Filipino, Vietnamese, Thai, Burmese, Khmer, Bengali, Tamil, Telugu, Marathi, Gujarati, Punjabi, Urdu, Malayalam, Kannada, Sinhala, Nepali, Swahili, Amharic, Yoruba, Zulu, or any mix of the above (code-switched / transliterated). Understand the text natively in whatever language it is — do NOT translate it, just extract the reminder intent.

Common Hinglish patterns to understand (one example dialect — apply the same logic for every other language using its own equivalents):
- "kal" = tomorrow, "aaj" = today, "parso" = day after tomorrow
- "subah/subh" = morning, "shaam" = evening, "raat" = night, "dopahar" = afternoon
- "baje/bje/bjey" = o'clock (e.g., "5 baje" = 5 o'clock)
- "roz" = daily, "har din" = every day, "har hafte" = every week
- "har mahine" = every month, "har saal" = every year
- "yaad dilana/dila" = remind, "yaad karna" = remember
- "karna hai" = have to do, "hona hai" = to happen

For other languages, apply equivalent reasoning to native words for: today/tomorrow/yesterday, morning/afternoon/evening/night, hour/minute, daily/weekly/monthly/yearly, remind/remember/don't-forget, meeting/call/appointment/deadline, etc. (e.g., Spanish "mañana"/"recuérdame", French "demain"/"rappelle-moi", German "morgen"/"erinnere mich", Japanese "明日"/"思い出させて", Arabic "غدا"/"ذكرني", Chinese "明天"/"提醒我", Portuguese "amanhã"/"lembre-me", Russian "завтра"/"напомни мне", etc.).

The output JSON fields ("title", "description", "extractedPhrase") MUST be in the SAME language as the input text — preserve the user's language. Only the JSON keys and enum values (NONE/DAILY/WEEKLY/MONTHLY/YEARLY) stay in English.

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
- CRITICAL — RELATIVE DURATION RULE (LANGUAGE-AGNOSTIC): Any expression that means "after / in / within N <time-unit>" in ANY language is ALWAYS computed as CURRENT DATE/TIME + that duration. It represents NOW + duration, NOT a future calendar day. Do NOT combine it with any day/date word that appears elsewhere in the sentence (tomorrow / yesterday / next week / a weekday name / etc.) — those day words only describe WHAT the reminder is about, not WHEN to fire it. The trigger time is ALWAYS the duration expression when both are present.

  Examples of "in N <unit>" phrases across languages (treat all of these the same way):
    • English: "in 1 minute", "in 5 mins", "after 2 hours", "in 3 days"
    • Hinglish/Hindi: "1 min mein", "5 minute mein", "2 ghante baad", "3 din baad"
    • Spanish: "en 1 minuto", "dentro de 5 minutos", "en 2 horas"
    • Portuguese: "em 1 minuto", "daqui a 5 minutos", "em 2 horas"
    • French: "dans 1 minute", "dans 5 minutes", "dans 2 heures"
    • Italian: "tra 1 minuto", "fra 5 minuti", "tra 2 ore"
    • German: "in 1 Minute", "in 5 Minuten", "in 2 Stunden"
    • Dutch: "over 1 minuut", "over 5 minuten", "over 2 uur"
    • Russian: "через 1 минуту", "через 5 минут", "через 2 часа"
    • Polish: "za 1 minutę", "za 5 minut", "za 2 godziny"
    • Ukrainian: "через 1 хвилину", "через 5 хвилин"
    • Romanian: "în 1 minut", "peste 5 minute"
    • Greek: "σε 1 λεπτό", "σε 5 λεπτά"
    • Albanian: "në 1 minutë", "në 5 minuta", "pas 2 orësh", "brenda 10 minutash"
    • Serbian/Croatian: "za 1 minut", "za 5 minuta"
    • Hungarian: "1 perc múlva", "5 perc múlva"
    • Czech: "za 1 minutu", "za 5 minut"
    • Turkish: "1 dakika sonra", "5 dakika içinde"
    • Arabic: "بعد دقيقة", "بعد ٥ دقائق", "خلال ساعتين"
    • Hebrew: "בעוד דקה", "בעוד 5 דקות"
    • Persian: "بعد از یک دقیقه", "تا ۵ دقیقه دیگر"
    • Japanese: "1分後", "5分後", "2時間後"
    • Chinese: "1分钟后", "5分钟以内", "2小时后"
    • Korean: "1분 후", "5분 뒤에", "2시간 안에"
    • Indonesian/Malay: "dalam 1 menit", "1 minit lagi"
    • Vietnamese: "trong 1 phút", "sau 5 phút"
    • Thai: "อีก 1 นาที", "ใน 5 นาที"
    • Bengali: "১ মিনিটে", "৫ মিনিট পরে"
    • Tamil: "1 நிமிடத்தில்", "5 நிமிடம் கழித்து"

  WORKED EXAMPLE: Current time = 2026-06-01 23:30. Input (Albanian): "Unë dua që të më rikthehet në 1 minutë se nesër është intervista ime." ("Remind me in 1 minute that tomorrow is my interview.") Correct dateTime = 2026-06-01 23:31 — NOT 2026-06-02. The word "nesër" (tomorrow) is event context only; "në 1 minutë" (in 1 minute) is the trigger.

- When the text contains BOTH a day word (today/tomorrow/next-week/a weekday/etc. in ANY language) AND a duration expression ("in/after N min/hours/days" in ANY language), ALWAYS use the duration expression as the trigger time and treat the day word as event context only."""
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

