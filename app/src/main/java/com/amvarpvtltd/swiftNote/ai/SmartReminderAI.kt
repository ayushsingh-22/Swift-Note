package com.amvarpvtltd.swiftNote.ai

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import com.google.mlkit.nl.entityextraction.DateTimeEntity
import com.google.mlkit.nl.entityextraction.EntityAnnotation
import com.google.mlkit.nl.entityextraction.EntityExtraction
import com.google.mlkit.nl.entityextraction.EntityExtractor
import com.google.mlkit.nl.entityextraction.EntityExtractorOptions
import com.amvarpvtltd.swiftNote.richtext.RichTextBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume

class SmartReminderAI(private val context: Context) {
    private val TAG = "SmartReminderAI"

    private lateinit var entityExtractor: EntityExtractor
    private var isInitialized = false

    companion object {
        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var INSTANCE: SmartReminderAI? = null

        fun getInstance(context: Context): SmartReminderAI {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SmartReminderAI(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    /**
     * Initialize ML Kit Entity Extractor
     */
    suspend fun initialize(): Result<String> = withContext(Dispatchers.IO) {
        return@withContext try {
            suspendCancellableCoroutine<Result<String>> { continuation ->
                val options = EntityExtractorOptions.Builder(EntityExtractorOptions.ENGLISH)
                    .build()

                entityExtractor = EntityExtraction.getClient(options)

                entityExtractor.downloadModelIfNeeded()
                    .addOnSuccessListener {
                        Log.d(TAG, "✅ ML Kit Entity Extractor initialized successfully")
                        isInitialized = true
                        continuation.resume(Result.success("AI initialized successfully"))
                    }
                    .addOnFailureListener { exception ->
                        Log.e(TAG, "❌ Failed to initialize ML Kit Entity Extractor", exception)
                        continuation.resume(Result.failure(exception))
                    }

                continuation.invokeOnCancellation {
                    Log.d(TAG, "🔄 Entity Extractor initialization cancelled")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error initializing Entity Extractor", e)
            Result.failure(e)
        }
    }

    /**
     * Analyze text for date/time entities and return detected reminders.
     *
     * Pipeline (smartest → fastest):
     *   1. AI (cloud, ~1-2s) — tried first if available (user has API key, Gemini or Groq)
     *   2. ML Kit Entity Extraction (on-device, instant) — fallback if AI fails/unavailable
     *   3. Regex fallback (on-device, instant, handles Hinglish) — last resort
     */
    suspend fun analyzeTextForReminders(
        text: String,
        noteTitle: String = "Untitled"
    ): Result<List<DetectedReminder>> = withContext(Dispatchers.IO) {
        // ───────────────────────────────────────────────────────────────
        // LLM-first path (multilingual).
        // When the user has configured an API key, bypass the English/Hinglish-only
        // ReminderIntentAnalyzer keyword gate and send the raw text directly to the
        // LLM. The LLM can natively understand any language it supports (Spanish,
        // French, German, Japanese, Arabic, Chinese, Portuguese, Russian, Bengali,
        // Tamil, Marathi, Gujarati, Punjabi, etc.) and will respond with
        // hasReminder=false when no intent is present, so this is safe.
        // ───────────────────────────────────────────────────────────────
        val geminiAvailable = try {
            GeminiReminderParser.getInstance(context).isAvailable()
        } catch (_: Exception) { false }

        if (geminiAvailable) {
            // Defensive HTML strip in case caller passed rich-text content.
            val plain = try { RichTextBridge.stripHtmlToPlainText(text) } catch (_: Exception) { text }
            val llmInput = plain.trim().ifBlank { text.trim() }

            if (llmInput.isNotBlank()) {
                Log.d(TAG, "🌍 AI available — routing text directly to LLM (multilingual mode)")
                val geminiResults = tryGeminiParsing(llmInput, noteTitle)
                if (geminiResults.isNotEmpty()) {
                    Log.d(TAG, "AI returned ${geminiResults.size} reminder(s) - using AI response")
                    return@withContext Result.success(geminiResults)
                }
                Log.d(TAG, "AI returned no results - falling back to on-device methods")
            }
        }

        // ───────────────────────────────────────────────────────────────
        // On-device fallback (ML Kit + regex) — keyword-gated, English/Hinglish only.
        // ───────────────────────────────────────────────────────────────
        val analysisText = ReminderIntentAnalyzer.buildAnalysisText(
            noteBody = text,
            noteTitle = noteTitle
        )

        if (analysisText.isNullOrBlank()) {
            Log.d(TAG, "No sentence-level reminder intent found, skipping analysis")
            return@withContext Result.success(emptyList())
        }

        Log.d(TAG, "Analyzing reminder-focused text: ${analysisText.take(100)}...")


        if (!isInitialized) {
            Log.w(TAG, "Entity Extractor not initialized, attempting to initialize...")
            val initResult = initialize()
            if (initResult.isFailure) {
                Log.w(TAG, "ML Kit init failed, using regex fallback: ${initResult.exceptionOrNull()?.message}")
                val regexResults = try {
                    regexFallbackForReminders(analysisText, noteTitle)
                } catch (_: Exception) {
                    emptyList()
                }
                return@withContext Result.success(regexResults)
            }
        }

        return@withContext try {
            suspendCancellableCoroutine<Result<List<DetectedReminder>>> { continuation ->
                entityExtractor.annotate(analysisText)
                    .addOnSuccessListener { entityAnnotations ->
                        val detectedReminders = processEntityAnnotations(
                            entityAnnotations,
                            analysisText,
                            noteTitle
                        )

                        val finalResults = detectedReminders.ifEmpty {
                            val fallback = regexFallbackForReminders(analysisText, noteTitle)
                            if (fallback.isNotEmpty()) {
                                Log.d(TAG, "Regex fallback detected ${fallback.size} reminder(s)")
                            }
                            fallback
                        }

                        Log.d(TAG, "Found ${finalResults.size} potential reminders (on-device)")
                        continuation.resume(Result.success(finalResults))
                    }
                    .addOnFailureListener { exception ->
                        Log.e(TAG, "ML Kit annotation failed, using regex fallback", exception)

                        val fallback = regexFallbackForReminders(analysisText, noteTitle)
                        if (fallback.isNotEmpty()) {
                            Log.d(TAG, "Regex fallback detected ${fallback.size} reminder(s) after ML Kit failure")
                        }
                        continuation.resume(Result.success(fallback))
                    }

                continuation.invokeOnCancellation {
                    Log.d(TAG, "Text analysis cancelled")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in analyzeTextForReminders - falling back to regex", e)
            val regexResults = try {
                regexFallbackForReminders(analysisText, noteTitle)
            } catch (_: Exception) {
                emptyList()
            }
            return@withContext Result.success(regexResults)
        }
    }

    /**
     * Try parsing with AI (Gemini or Groq). Returns empty list on failure (never throws).
     * This is the intelligent fallback for when on-device methods can't parse the text.
     */
    private suspend fun tryGeminiParsing(text: String, noteTitle: String): List<DetectedReminder> {
        return try {
            val gemini = GeminiReminderParser.getInstance(context)
            val parsed = gemini.parseReminderIntent(text, noteTitle)
            if (parsed != null) {
                val results = gemini.toDetectedReminders(parsed, noteTitle)
                Log.d(TAG, "🤖 AI detected ${results.size} reminder(s)")
                results
            } else {
                Log.d(TAG, "🤖 AI found no reminder intent")
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ AI parsing failed silently: ${e.message}")
            emptyList()
        }
    }

    /**
     * Explicitly analyze text using AI (bypasses ML Kit + regex).
     * Use this when:
     * - User taps "Analyze with AI" button
     * - On-device methods returned no results but user insists there's a reminder
     * - Text is in a language that regex/ML Kit can't handle well
     *
     * @return Result with list of detected reminders, or failure if AI is unavailable
     */
    @Suppress("unused") // Public API for future "Analyze with AI" UI action
    suspend fun analyzeWithGemini(
        text: String,
        noteTitle: String = "Untitled"
    ): Result<List<DetectedReminder>> = withContext(Dispatchers.IO) {
        return@withContext try {
            val gemini = GeminiReminderParser.getInstance(context)
            val parsed = gemini.parseReminderIntent(text, noteTitle)
            if (parsed != null) {
                val results = gemini.toDetectedReminders(parsed, noteTitle)
                Log.d(TAG, "🤖 AI explicit analysis: ${results.size} reminder(s)")
                Result.success(results)
            } else {
                Result.success(emptyList())
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Gemini explicit analysis failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Process ML Kit entity annotations to extract date/time information
     */
    private fun processEntityAnnotations(
        annotations: List<EntityAnnotation>,
        originalText: String,
        noteTitle: String
    ): List<DetectedReminder> {
        val reminders = mutableListOf<DetectedReminder>()
        val currentTime = System.currentTimeMillis()

        annotations.forEach { annotation ->
            annotation.entities.forEach { entity ->
                when (entity) {
                    is DateTimeEntity -> {
                        val reminderDateTime = processDateTimeEntity(entity)

                        if (reminderDateTime != null && reminderDateTime > currentTime) {
                            val extractedText = originalText.substring(
                                annotation.start,
                                annotation.end.coerceAtMost(originalText.length)
                            )

                            val reminder = DetectedReminder(
                                id = UUID.randomUUID().toString(),
                                title = generateReminderTitle(extractedText, noteTitle),
                                description = generateReminderDescription(extractedText, originalText),
                                extractedText = extractedText,
                                reminderDateTime = reminderDateTime,
                                confidence = calculateConfidence(entity, extractedText),
                                entityType = "DateTime",
                                originalNoteTitle = noteTitle
                            )

                            reminders.add(reminder)
                            Log.d(TAG, "📅 Detected reminder: ${reminder.title} at ${formatDateTime(reminderDateTime)}")
                        }
                    }
                }
            }
        }

        // Bucket by minute so ML Kit overlapping annotations for the same phrase
        // (e.g. "in 2 min", "2 min", "2") collapse to a single reminder even when
        // their timestampMillis differ by sub-second drift.
        return reminders
            .distinctBy { it.reminderDateTime / 60_000L }
            .sortedBy { it.reminderDateTime }
    }
    private fun processDateTimeEntity(entity: DateTimeEntity): Long? {
        return try {
            val calendar = Calendar.getInstance()

            when (entity.dateTimeGranularity) {
                DateTimeEntity.GRANULARITY_DAY -> {
                    val timestamp = entity.timestampMillis
                    calendar.timeInMillis = timestamp
                    // Set to 9 AM if only date is specified
                    calendar.set(Calendar.HOUR_OF_DAY, 9)
                    calendar.set(Calendar.MINUTE, 0)
                    calendar.set(Calendar.SECOND, 0)
                    calendar.timeInMillis
                }
                DateTimeEntity.GRANULARITY_HOUR -> entity.timestampMillis
                DateTimeEntity.GRANULARITY_MINUTE -> entity.timestampMillis
                else -> entity.timestampMillis
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error processing DateTimeEntity", e)
            null
        }
    }

    /**
     * Generate a meaningful reminder title
     */
    private fun generateReminderTitle(extractedText: String, noteTitle: String): String {
        return when {
            extractedText.contains("appointment", ignoreCase = true) -> "📅 Appointment Reminder"
            extractedText.contains("meeting", ignoreCase = true) -> "🤝 Meeting Reminder"
            extractedText.contains("call", ignoreCase = true) -> "📞 Call Reminder"
            extractedText.contains("deadline", ignoreCase = true) -> "⏰ Deadline Reminder"
            extractedText.contains("reminder", ignoreCase = true) -> "🔔 Personal Reminder"
            noteTitle.isNotBlank() && noteTitle != "Untitled" -> "📝 $noteTitle"
            else -> "🔔 Smart Reminder"
        }
    }

    /**
     * Generate reminder description from context
     */
    private fun generateReminderDescription(extractedText: String, fullText: String): String {
        // Find the sentence containing the extracted text
        val sentences = fullText.split(Regex("[.!?]+"))
        val relevantSentence = sentences.find { it.contains(extractedText, ignoreCase = true) }

        return relevantSentence?.trim()?.takeIf { it.isNotBlank() }
            ?: extractedText.trim().takeIf { it.isNotBlank() }
            ?: "Reminder from your note"
    }

    /**
     * Format date/time for display
     */
    private fun formatDateTime(timestamp: Long): String {
        val formatter = SimpleDateFormat("MMM dd, yyyy 'at' hh:mm a", Locale.getDefault())
        return formatter.format(Date(timestamp))
    }

    /**
     * Check if text contains potential reminder keywords
     */
    fun hasReminderKeywords(text: String): Boolean {
        return ReminderIntentAnalyzer.hasReminderIntent(noteBody = text)
    }

    /**
     * Calculate confidence for the detected reminder based on entity characteristics
     */
    private fun calculateConfidence(entity: DateTimeEntity, extractedText: String): Float {

        var confidence = 0.7f // Base confidence

        // Higher confidence for more specific time granularity
        when (entity.dateTimeGranularity) {
            DateTimeEntity.GRANULARITY_MINUTE -> confidence += 0.2f
            DateTimeEntity.GRANULARITY_HOUR -> confidence += 0.15f
            DateTimeEntity.GRANULARITY_DAY -> confidence += 0.1f
            else -> confidence += 0.05f
        }

        // Higher confidence for explicit reminder keywords
        val reminderKeywords = listOf("remind", "appointment", "meeting", "deadline", "alarm")
        if (reminderKeywords.any { extractedText.contains(it, ignoreCase = true) }) {
            confidence += 0.1f
        }

        // Ensure confidence is between 0.0 and 1.0
        return confidence.coerceIn(0.0f, 1.0f)
    }

    private fun regexFallbackForReminders(text: String, noteTitle: String): List<DetectedReminder> {
        val reminders = mutableListOf<DetectedReminder>()
        try {
             // Phase 2: Detect recurrence from the full text (applied to all reminders found)
             val recurrence = detectRecurrence(text)

             // Helper to build DetectedReminder from a timestamp and matched snippet
             fun buildReminder(timestamp: Long, snippet: String): DetectedReminder {
                 return DetectedReminder(
                     id = UUID.randomUUID().toString(),
                     title = generateReminderTitle(snippet, noteTitle),
                     description = generateReminderDescription(snippet, text),
                     extractedText = snippet,
                     reminderDateTime = timestamp,
                     confidence = if (recurrence != null) 0.85f else 0.7f,
                     entityType = "RegexFallback",
                     originalNoteTitle = noteTitle,
                     detectedRecurrence = recurrence
                 )
             }

            // Pattern: numeric minutes in English/Hinglish (e.g., "5 min", "5 mins", "5 minm", "5 min mein", "5 min mai")
            val minuteVariants = Regex("\\b(\\d{1,3})\\s*(?:min|mins|minm|minute|minutes)\\s*(?:mai|mein)?\\b", RegexOption.IGNORE_CASE)
            minuteVariants.findAll(text).forEach { m ->
                val n = m.groupValues[1].toIntOrNull() ?: return@forEach
                val cal = Calendar.getInstance()
                cal.add(Calendar.MINUTE, n)
                cal.set(Calendar.SECOND, 0)
                // Only add future times
                if (cal.timeInMillis > System.currentTimeMillis()) reminders.add(buildReminder(cal.timeInMillis, m.value.trim()))
            }

             // Pattern: (today|tomorrow|tonight)( at)? TIME?
             val relativePattern = Regex("\\b(today|tomorrow|tonight)\\b(?:\\s+at)?\\s*(\\d{1,2}(:\\d{2})?\\s?(?:am|pm)?)?",
                 RegexOption.IGNORE_CASE)
            // Check once whether the full text already has a relative-duration expression
            // ("in X min/hours" or "N min"). If so, bare day words are context only — skip them.
            val textHasRelativeDuration = Regex(
                "\\bin\\s+\\d+\\s*(?:min|mins|minute|minutes|hour|hours)\\b|\\b\\d+\\s*(?:min|mins|minm|minute|minutes)\\s*(?:mai|mein)?\\b",
                RegexOption.IGNORE_CASE
            ).containsMatchIn(text)
            relativePattern.findAll(text).forEach { m ->
                val whenWord = m.groupValues[1].lowercase()
                val timePart = m.groupValues.getOrNull(2)
                // If no explicit time was captured and the text already has a "in X min/hours"
                // duration expression, this day word is only providing event context — skip it.
                if (timePart.isNullOrBlank() && textHasRelativeDuration) return@forEach
                val cal = Calendar.getInstance()
                when (whenWord) {
                    "today", "tonight" -> { /* keep today */ }
                    "tomorrow" -> cal.add(Calendar.DATE, 1)
                }

                // default time for 'tonight' = 20:00, for others if missing = 09:00
                var hour = 9; var minute = 0
                if (whenWord == "tonight") { hour = 20 }

                if (!timePart.isNullOrBlank()) {
                    val t = parseTimeStringToHourMinute(timePart)
                    if (t != null) { hour = t.first; minute = t.second }
                }

                cal.set(Calendar.HOUR_OF_DAY, hour)
                cal.set(Calendar.MINUTE, minute)
                cal.set(Calendar.SECOND, 0)
                val ts = cal.timeInMillis
                if (ts > System.currentTimeMillis()) reminders.add(buildReminder(ts, m.value.trim()))
            }

            // Pattern: Hinglish relative day (kal/aaj/parso) + optional time
            // Handles: "kal 5 baje", "aaj subah", "kal morning", "kal 8 bje subah"
            val hinglishDayPattern = Regex(
                "\\b(kal|aaj|parso)\\b(?:\\s+(?:ko|ke))?(?:\\s+(\\d{1,2})\\s*(?:baje|bje|bjey|bajke))?(?:\\s*(?:subah|subh|morning|shaam|evening|raat|night|dopahar|afternoon))?",
                RegexOption.IGNORE_CASE
            )
            hinglishDayPattern.findAll(text).forEach { m ->
                val whenWord = m.groupValues[1].lowercase()
                val hourStr = m.groupValues.getOrNull(2)
                val cal = Calendar.getInstance()
                when (whenWord) {
                    "kal" -> cal.add(Calendar.DATE, 1)
                    "parso" -> cal.add(Calendar.DATE, 2)
                    "aaj" -> { /* today */ }
                }

                // Try extracting hour from "N baje/bje" pattern
                var hour = 9
                val minute = 0
                if (!hourStr.isNullOrBlank()) {
                    val h = hourStr.toIntOrNull()
                    if (h != null && h in 1..12) {
                        // Determine AM/PM from context: subah/morning = AM, shaam/evening/raat/night = PM
                        val fullMatch = m.value.lowercase()
                        hour = if (fullMatch.contains("subah") || fullMatch.contains("subh") || fullMatch.contains("morning")) {
                            if (h == 12) 0 else h
                        } else if (fullMatch.contains("shaam") || fullMatch.contains("evening") || fullMatch.contains("raat") || fullMatch.contains("night")) {
                            if (h == 12) 12 else h + 12
                        } else {
                            // Ambiguous — if <= 6, assume PM; if > 6, assume AM (common Indian usage for daily tasks)
                            if (h in 1..6) h + 12 else h
                        }
                    }
                } else {
                    // No explicit time — check for time-of-day words in wider context
                    val fullMatch = m.value.lowercase()
                    hour = when {
                        fullMatch.contains("subah") || fullMatch.contains("subh") || fullMatch.contains("morning") -> 7
                        fullMatch.contains("dopahar") || fullMatch.contains("afternoon") -> 14
                        fullMatch.contains("shaam") || fullMatch.contains("evening") -> 18
                        fullMatch.contains("raat") || fullMatch.contains("night") -> 21
                        else -> 9 // default
                    }
                }

                cal.set(Calendar.HOUR_OF_DAY, hour)
                cal.set(Calendar.MINUTE, minute)
                cal.set(Calendar.SECOND, 0)
                val ts = cal.timeInMillis
                if (ts > System.currentTimeMillis()) reminders.add(buildReminder(ts, m.value.trim()))
            }

            // Pattern: N baje/bje/bjey (standalone Hinglish o'clock without day context)
            // Handles: "5 baje", "5 bje subah", "8 baje shaam"
            val bajePattern = Regex(
                "\\b(\\d{1,2})\\s*(?:baje|bje|bjey|bajke)\\s*((?:subah|subh|morning|shaam|evening|raat|night|dopahar|afternoon))?",
                RegexOption.IGNORE_CASE
            )
            bajePattern.findAll(text).forEach { m ->
                val hourNum = m.groupValues[1].toIntOrNull() ?: return@forEach
                val timeOfDay = m.groupValues.getOrNull(2)?.lowercase()?.takeIf { it.isNotBlank() }
                if (hourNum !in 1..12) return@forEach

                val hour = when {
                    timeOfDay != null && (timeOfDay.startsWith("sub") || timeOfDay == "morning") -> {
                        if (hourNum == 12) 0 else hourNum
                    }
                    timeOfDay != null && (timeOfDay.startsWith("sha") || timeOfDay == "evening" || timeOfDay.startsWith("raa") || timeOfDay == "night") -> {
                        if (hourNum == 12) 12 else hourNum + 12
                    }
                    else -> {
                        // Check nearby context for subah/shaam
                        val contextLower = text.lowercase()
                        if (contextLower.contains("subah") || contextLower.contains("subh") || contextLower.contains("morning")) {
                            if (hourNum == 12) 0 else hourNum
                        } else if (contextLower.contains("shaam") || contextLower.contains("evening") || contextLower.contains("raat") || contextLower.contains("night")) {
                            if (hourNum == 12) 12 else hourNum + 12
                        } else {
                            if (hourNum in 1..6) hourNum + 12 else hourNum
                        }
                    }
                }

                val cal = Calendar.getInstance()
                cal.set(Calendar.HOUR_OF_DAY, hour)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                // If time already passed today, schedule for tomorrow
                if (cal.timeInMillis <= System.currentTimeMillis()) cal.add(Calendar.DATE, 1)
                reminders.add(buildReminder(cal.timeInMillis, m.value.trim()))
            }

            // Pattern: Weekday (next)? (at)? TIME?
            val weekdays = listOf("monday","tuesday","wednesday","thursday","friday","saturday","sunday")
            val weekdayPattern = Regex("\\b(?:next\\s+)?(${weekdays.joinToString("|")})\\b(?:\\s+at)?\\s*(\\d{1,2}(:\\d{2})?\\s?(?:am|pm)?)?",
                RegexOption.IGNORE_CASE)
            weekdayPattern.findAll(text).forEach { m ->
                val dayName = m.groupValues[1].lowercase()
                val timePart = m.groupValues.getOrNull(2)
                val targetDow = weekdays.indexOf(dayName) + 1 // Calendar.SUNDAY=1
                val cal = Calendar.getInstance()
                val todayDow = cal.get(Calendar.DAY_OF_WEEK)
                var daysAhead = (targetDow - todayDow + 7) % 7
                if (daysAhead == 0) daysAhead = 7 // schedule next week if same day mentioned
                cal.add(Calendar.DATE, daysAhead)

                var hour = 9; var minute = 0
                if (!timePart.isNullOrBlank()) {
                    val t = parseTimeStringToHourMinute(timePart)
                    if (t != null) { hour = t.first; minute = t.second }
                }

                cal.set(Calendar.HOUR_OF_DAY, hour)
                cal.set(Calendar.MINUTE, minute)
                cal.set(Calendar.SECOND, 0)
                val ts = cal.timeInMillis
                if (ts > System.currentTimeMillis()) reminders.add(buildReminder(ts, m.value.trim()))
            }

            // Pattern: explicit time without date -> schedule next occurrence of that time
            val timeOnlyPattern = Regex("\\b(\\d{1,2}(:\\d{2})?\\s?(?:am|pm))\\b", RegexOption.IGNORE_CASE)
            timeOnlyPattern.findAll(text).forEach { m ->
                val timePart = m.groupValues[1]
                val parsed = parseTimeStringToHourMinute(timePart)
                if (parsed != null) {
                    val cal = Calendar.getInstance()
                    cal.set(Calendar.HOUR_OF_DAY, parsed.first)
                    cal.set(Calendar.MINUTE, parsed.second)
                    cal.set(Calendar.SECOND, 0)
                    if (cal.timeInMillis <= System.currentTimeMillis()) cal.add(Calendar.DATE, 1)
                    val ts = cal.timeInMillis
                    reminders.add(buildReminder(ts, m.value.trim()))
                }
            }

            // Pattern: 'at 5' or 'at 17' without am/pm -> schedule next occurrence of that hour
            val atHourPattern = Regex("\\bat\\s+(\\d{1,2})(?::(\\d{2}))?\\b(?!\\s*(?:am|pm))", RegexOption.IGNORE_CASE)
            atHourPattern.findAll(text).forEach { m ->
                val hourStr = m.groupValues[1]
                val minStr = m.groupValues.getOrNull(2)
                val hour = hourStr.toIntOrNull() ?: return@forEach
                val minute = minStr?.toIntOrNull() ?: 0
                if (hour in 0..23 && minute in 0..59) {
                    val cal = Calendar.getInstance()
                    cal.set(Calendar.HOUR_OF_DAY, hour)
                    cal.set(Calendar.MINUTE, minute)
                    cal.set(Calendar.SECOND, 0)
                    if (cal.timeInMillis <= System.currentTimeMillis()) cal.add(Calendar.DATE, 1)
                    reminders.add(buildReminder(cal.timeInMillis, m.value.trim()))
                }
            }

            // Pattern: 'in X hours' or 'in X minutes'
            val inHoursPattern = Regex("\\bin\\s+(\\d{1,3})\\s+hours?\\b", RegexOption.IGNORE_CASE)
            inHoursPattern.findAll(text).forEach { m ->
                val n = m.groupValues[1].toIntOrNull() ?: return@forEach
                val cal = Calendar.getInstance()
                cal.add(Calendar.HOUR_OF_DAY, n)
                cal.set(Calendar.SECOND, 0)
                reminders.add(buildReminder(cal.timeInMillis, m.value.trim()))
            }
            val inMinutesPattern = Regex("\\bin\\s+(\\d{1,3})\\s+minutes?\\b", RegexOption.IGNORE_CASE)
            inMinutesPattern.findAll(text).forEach { m ->
                val n = m.groupValues[1].toIntOrNull() ?: return@forEach
                val cal = Calendar.getInstance()
                cal.add(Calendar.MINUTE, n)
                cal.set(Calendar.SECOND, 0)
                reminders.add(buildReminder(cal.timeInMillis, m.value.trim()))
            }

            // Pattern: numeric date like 05/09 or 5-9 or 2025-09-05 -> try MM/DD or DD/MM where reasonable
            val numericDatePattern = Regex("\\b(\\d{1,4})[/-](\\d{1,2})(?:[/-](\\d{2,4}))?\\b")
            numericDatePattern.findAll(text).forEach { m ->
                if (!isLikelyNumericDateMatch(text, m)) return@forEach
                try {
                    val p1 = m.groupValues[1].toIntOrNull() ?: return@forEach
                    val p2 = m.groupValues[2].toIntOrNull() ?: return@forEach
                    val p3 = m.groupValues.getOrNull(3)?.toIntOrNull()
                    val cal = Calendar.getInstance()
                    if (p1 > 31) {
                        // treat as year-month-day or year-month-day
                        // p1 = year, p2 = month
                        val year = p1
                        val month = (p2 - 1).coerceIn(0,11)
                        val day = p3 ?: cal.get(Calendar.DAY_OF_MONTH)
                        cal.set(Calendar.YEAR, year)
                        cal.set(Calendar.MONTH, month)
                        cal.set(Calendar.DAY_OF_MONTH, day)
                    } else {
                        // ambiguous: assume MM/DD if first <=12, else DD/MM
                        val month = if (p1 in 1..12) p1 - 1 else p2 - 1
                        val day = if (p1 in 1..12) p2 else p1
                        cal.set(Calendar.MONTH, month.coerceIn(0,11))
                        cal.set(Calendar.DAY_OF_MONTH, day.coerceIn(1,31))
                        // if year present, set it, else default this year, roll over if passed
                        if (p3 != null) {
                            val year = if (p3 < 100) 2000 + p3 else p3
                            cal.set(Calendar.YEAR, year)
                        }
                        if (cal.timeInMillis <= System.currentTimeMillis()) cal.add(Calendar.YEAR, 1)
                    }
                    // default time
                    cal.set(Calendar.HOUR_OF_DAY, 9)
                    cal.set(Calendar.MINUTE, 0)
                    cal.set(Calendar.SECOND, 0)
                    if (cal.timeInMillis > System.currentTimeMillis()) reminders.add(buildReminder(cal.timeInMillis, m.value.trim()))
                } catch (_: Exception) {
                    // ignore
                }
            }

             // Pattern: Month name + day (e.g., Jan 5 or January 5)
             val monthNames = mapOf(
                 "jan" to 0, "feb" to 1, "mar" to 2, "apr" to 3, "may" to 4, "jun" to 5,
                 "jul" to 6, "aug" to 7, "sep" to 8, "oct" to 9, "nov" to 10, "dec" to 11
             )
             val monthPattern = Regex("\\b(jan|feb|mar|apr|jun|jul|aug|sep|oct|nov|dec|january|february|march|april|may|june|july|august|september|october|november|december)\\s+(\\d{1,2})(?:\\s+at\\s+(\\d{1,2}(:\\d{2})?\\s?(?:am|pm)?))?",
                 RegexOption.IGNORE_CASE)
             monthPattern.findAll(text).forEach { m ->
                 val mon = m.groupValues[1].lowercase().take(3)
                 val day = m.groupValues[2].toIntOrNull() ?: return@forEach
                 val timePart = m.groupValues.getOrNull(3)
                 val cal = Calendar.getInstance()
                 val monthIdx = monthNames[mon] ?: return@forEach
                 cal.set(Calendar.MONTH, monthIdx)
                 cal.set(Calendar.DAY_OF_MONTH, day)
                 // if month-day already passed this year, set next year
                 if (cal.timeInMillis <= System.currentTimeMillis()) cal.add(Calendar.YEAR, 1)

                 var hour = 9; var minute = 0
                 if (!timePart.isNullOrBlank()) {
                     val t = parseTimeStringToHourMinute(timePart)
                     if (t != null) { hour = t.first; minute = t.second }
                 }
                 cal.set(Calendar.HOUR_OF_DAY, hour)
                 cal.set(Calendar.MINUTE, minute)
                 cal.set(Calendar.SECOND, 0)
                 val ts = cal.timeInMillis
                 if (ts > System.currentTimeMillis()) reminders.add(buildReminder(ts, m.value.trim()))
             }

        } catch (ex: Exception) {
            Log.e(TAG, "❌ Error in regexFallbackForReminders", ex)
        }

        // Bucket by minute so overlapping patterns (e.g. "in 2 min" hit by both
        // `minuteVariants` and `inMinutesPattern`) collapse to a single reminder.
        return reminders
            .distinctBy { it.reminderDateTime / 60_000L }
            .sortedBy { it.reminderDateTime }
    }

    private fun isLikelyNumericDateMatch(text: String, match: MatchResult): Boolean {
        val first = match.groupValues[1].toIntOrNull() ?: return false
        val second = match.groupValues[2].toIntOrNull() ?: return false
        val third = match.groupValues.getOrNull(3).orEmpty()

        if (third.isNotBlank()) return true

        val contextStart = (match.range.first - 20).coerceAtLeast(0)
        val contextEnd = (match.range.last + 21).coerceAtMost(text.length)
        val context = text.substring(contextStart, contextEnd)

        val dateCuePattern = Regex(
            "\\b(?:on|by|due|before|after|until|till|from|starting|scheduled?|appointment|meeting|deadline|birthday|anniversary|flight|exam|doctor|dentist|visit|renew|renewal|submit|pay|bill)\\b",
            RegexOption.IGNORE_CASE
        )

        return when {
            first > 12 || second > 12 -> true
            else -> dateCuePattern.containsMatchIn(context)
        }
    }

    // Parse time strings like "10am", "10:30 pm", "7:15PM" into hour and minute
    private fun parseTimeStringToHourMinute(timeStr: String): Pair<Int, Int>? {
        try {
            var s = timeStr.lowercase().trim()
            val ampm = when {
                s.endsWith("am") -> "am"
                s.endsWith("pm") -> "pm"
                else -> null
            }
            s = s.replace("am", "").replace("pm", "").trim()
            val parts = s.split(":")
            var hour = parts[0].toIntOrNull() ?: return null
            val minute = if (parts.size > 1) parts[1].toIntOrNull() ?: 0 else 0
            if (ampm != null) {
                if (ampm == "pm" && hour < 12) hour += 12
                if (ampm == "am" && hour == 12) hour = 0
            }
            if (hour in 0..23 && minute in 0..59) return Pair(hour, minute)
        } catch (_: Exception) {
            // ignore
        }
        return null
    }

    /**
     * Phase 2: Detect recurrence patterns from text.
     * Recognizes English and Hinglish phrases like "every day", "daily", "weekly",
     * "every Monday", "har hafte", "roz", etc.
     *
     * @return DetectedRecurrence if a recurrence pattern is found with confidence >= 0.7, null otherwise
     */
    fun detectRecurrence(text: String): DetectedRecurrence? {

        // "every day" / "daily" / "roz" / "har din" / "har roz"
        val dailyPatterns = listOf(
            Regex("\\bevery\\s+day\\b", RegexOption.IGNORE_CASE),
            Regex("\\bdaily\\b", RegexOption.IGNORE_CASE),
            Regex("\\broz\\b", RegexOption.IGNORE_CASE),
            Regex("\\bhar\\s+din\\b", RegexOption.IGNORE_CASE),
            Regex("\\bhar\\s+roz\\b", RegexOption.IGNORE_CASE),
            Regex("\\bdaily\\s+at\\b", RegexOption.IGNORE_CASE),
            Regex("\\beveryday\\b", RegexOption.IGNORE_CASE)
        )
        if (dailyPatterns.any { it.containsMatchIn(text) }) {
            return DetectedRecurrence(type = "DAILY", interval = 1)
        }

        // "every N days"
        val everyNDays = Regex("\\bevery\\s+(\\d+)\\s+days?\\b", RegexOption.IGNORE_CASE)
        everyNDays.find(text)?.let { m ->
            val n = m.groupValues[1].toIntOrNull() ?: 1
            return DetectedRecurrence(type = "DAILY", interval = n)
        }

        // Specific weekday recurrence: "every Monday", "every mon/wed/fri", "har Monday"
        val weekdays = mapOf(
            "sunday" to 1, "sun" to 1,
            "monday" to 2, "mon" to 2,
            "tuesday" to 3, "tue" to 3,
            "wednesday" to 4, "wed" to 4,
            "thursday" to 5, "thu" to 5,
            "friday" to 6, "fri" to 6,
            "saturday" to 7, "sat" to 7
        )
        val weekdayNames = weekdays.keys.joinToString("|")
        val everyWeekdayPattern = Regex(
            "\\b(?:every|har)\\s+($weekdayNames)(?:\\s*(?:,|and|aur|&)\\s*($weekdayNames))*\\b",
            RegexOption.IGNORE_CASE
        )
        everyWeekdayPattern.find(text)?.let { m ->
            // Extract all mentioned days from the matched region
            val matchedText = m.value.lowercase()
            val days = weekdays.filter { matchedText.contains(it.key) }.values.toSortedSet()
            if (days.isNotEmpty()) {
                return DetectedRecurrence(
                    type = "WEEKLY",
                    interval = 1,
                    daysOfWeek = days.joinToString(",")
                )
            }
        }

        // "weekdays" / "weekday" / "on weekdays"
        val weekdaysPattern = Regex("\\b(?:every\\s+)?weekdays?\\b", RegexOption.IGNORE_CASE)
        if (weekdaysPattern.containsMatchIn(text)) {
            return DetectedRecurrence(type = "WEEKLY", interval = 1, daysOfWeek = "2,3,4,5,6") // Mon-Fri
        }

        // "weekly" / "every week" / "har hafte" / "har hafta"
        val weeklyPatterns = listOf(
            Regex("\\bweekly\\b", RegexOption.IGNORE_CASE),
            Regex("\\bevery\\s+week\\b", RegexOption.IGNORE_CASE),
            Regex("\\bhar\\s+haft[ea]\\b", RegexOption.IGNORE_CASE)
        )
        if (weeklyPatterns.any { it.containsMatchIn(text) }) {
            return DetectedRecurrence(type = "WEEKLY", interval = 1)
        }

        // "every N weeks"
        val everyNWeeks = Regex("\\bevery\\s+(\\d+)\\s+weeks?\\b", RegexOption.IGNORE_CASE)
        everyNWeeks.find(text)?.let { m ->
            val n = m.groupValues[1].toIntOrNull() ?: 1
            return DetectedRecurrence(type = "WEEKLY", interval = n)
        }

        // "monthly" / "every month" / "har mahine" / "har mahiney"
        val monthlyPatterns = listOf(
            Regex("\\bmonthly\\b", RegexOption.IGNORE_CASE),
            Regex("\\bevery\\s+month\\b", RegexOption.IGNORE_CASE),
            Regex("\\bhar\\s+mahin[ey]+\\b", RegexOption.IGNORE_CASE)
        )
        if (monthlyPatterns.any { it.containsMatchIn(text) }) {
            return DetectedRecurrence(type = "MONTHLY", interval = 1)
        }

        // "every N months"
        val everyNMonths = Regex("\\bevery\\s+(\\d+)\\s+months?\\b", RegexOption.IGNORE_CASE)
        everyNMonths.find(text)?.let { m ->
            val n = m.groupValues[1].toIntOrNull() ?: 1
            return DetectedRecurrence(type = "MONTHLY", interval = n)
        }

        // "yearly" / "every year" / "annually" / "har saal"
        val yearlyPatterns = listOf(
            Regex("\\byearly\\b", RegexOption.IGNORE_CASE),
            Regex("\\bevery\\s+year\\b", RegexOption.IGNORE_CASE),
            Regex("\\bannually\\b", RegexOption.IGNORE_CASE),
            Regex("\\bhar\\s+saal\\b", RegexOption.IGNORE_CASE)
        )
        if (yearlyPatterns.any { it.containsMatchIn(text) }) {
            return DetectedRecurrence(type = "YEARLY", interval = 1)
        }

        return null
    }

}

/**
 * Data class representing a detected reminder
 */
data class DetectedReminder(
    val id: String,
    val title: String,
    val description: String,
    val extractedText: String,
    val reminderDateTime: Long,
    val confidence: Float,
    val entityType: String,
    val originalNoteTitle: String,
    val isConfirmed: Boolean = false,
    // Phase 2: Detected recurrence info from AI
    val detectedRecurrence: DetectedRecurrence? = null
)

/**
 * Phase 2: Data class representing detected recurrence from text analysis.
 * Returned alongside a DetectedReminder when patterns like "every day", "daily", "weekly" etc. are found.
 */
data class DetectedRecurrence(
    val type: String,       // RecurrenceType constant: DAILY, WEEKLY, MONTHLY, YEARLY
    val interval: Int = 1,  // e.g., "every 2 weeks" → 2
    val daysOfWeek: String? = null  // CSV for specific days, e.g. "2,3,4,5,6" for weekdays
)
