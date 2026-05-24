package com.amvarpvtltd.swiftNote.ai

import android.content.Context
import android.view.textclassifier.TextClassifier
import android.view.textclassifier.TextLinks
import android.view.textclassifier.TextClassificationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Unified entity detector that combines Android TextClassifier with regex fallbacks.
 * Returns structured [DetectedEntity] objects ready for chip rendering.
 *
 * Results are cached per noteId to avoid re-running on every recomposition.
 */
object SmartEntityDetector {

    private const val CONFIDENCE_THRESHOLD = 0.5f
    private const val MIN_PHONE_LENGTH = 8

    // Simple LRU-like cache: noteId → detected entities
    private val cache = LinkedHashMap<String, List<DetectedEntity>>(16, 0.75f, true)
    private const val MAX_CACHE_SIZE = 50

    /**
     * Analyze text and return all detected entities.
     * Cached by [noteId] — returns instantly on cache hit.
     */
    suspend fun analyze(
        context: Context,
        text: String,
        noteId: String
    ): List<DetectedEntity> {
        // Return from cache if available
        cache[noteId]?.let { return it }

        val results = withContext(Dispatchers.Default) {
            val entities = mutableListOf<DetectedEntity>()

            // 1. Try Android TextClassifier
            try {
                val tcm = context.getSystemService(Context.TEXT_CLASSIFICATION_SERVICE) as? TextClassificationManager
                val classifier = tcm?.textClassifier ?: TextClassifier.NO_OP
                if (classifier != TextClassifier.NO_OP) {
                    val request = TextLinks.Request.Builder(text).build()
                    val links = classifier.generateLinks(request)
                    for (link in links.links) {
                        val entityText = text.substring(link.start, link.end)
                        processTextClassifierLink(link, entityText, entities)
                    }
                }
            } catch (_: Exception) {
                // TextClassifier might not be available; fall through to regex
            }

            // 2. Regex fallback for entities TextClassifier may miss
            addRegexEntities(text, entities)

            // Deduplicate by raw text
            entities.distinctBy { it.raw.trim().lowercase() }
        }

        // Cache the result
        synchronized(cache) {
            if (cache.size >= MAX_CACHE_SIZE) {
                val oldest = cache.keys.firstOrNull()
                if (oldest != null) cache.remove(oldest)
            }
            cache[noteId] = results
        }

        return results
    }

    /**
     * Invalidate cached results for a given noteId (call when note is edited).
     */
    fun invalidateCache(noteId: String) {
        synchronized(cache) { cache.remove(noteId) }
    }

    // ─── Private Helpers ─────────────────────────────────────────────────

    private fun processTextClassifierLink(
        link: TextLinks.TextLink,
        entityText: String,
        results: MutableList<DetectedEntity>
    ) {
        val types = listOf(
            TextClassifier.TYPE_PHONE,
            TextClassifier.TYPE_EMAIL,
            TextClassifier.TYPE_URL,
            TextClassifier.TYPE_ADDRESS,
            TextClassifier.TYPE_DATE,
            TextClassifier.TYPE_DATE_TIME
        )

        for (type in types) {
            val score = link.getConfidenceScore(type)
            if (score < CONFIDENCE_THRESHOLD) continue

            when (type) {
                TextClassifier.TYPE_PHONE -> {
                    val normalized = entityText.replace(Regex("[^+\\d]"), "")
                    if (normalized.length >= MIN_PHONE_LENGTH) {
                        results.add(DetectedEntity.PhoneNumber(raw = entityText, normalized = normalized))
                    }
                }
                TextClassifier.TYPE_EMAIL -> {
                    results.add(DetectedEntity.Email(raw = entityText, address = entityText.trim()))
                }
                TextClassifier.TYPE_URL -> {
                    val url = if (entityText.startsWith("http")) entityText.trim()
                    else "https://${entityText.trim()}"
                    results.add(DetectedEntity.Url(raw = entityText, url = url))
                }
                TextClassifier.TYPE_ADDRESS -> {
                    results.add(DetectedEntity.Address(raw = entityText, text = entityText.trim()))
                }
                TextClassifier.TYPE_DATE, TextClassifier.TYPE_DATE_TIME -> {
                    results.add(DetectedEntity.DateTime(raw = entityText, text = entityText.trim()))
                }
            }
            break // Take the highest-confidence type per link
        }
    }

    private fun addRegexEntities(text: String, results: MutableList<DetectedEntity>) {
        val existingPhones = results.filterIsInstance<DetectedEntity.PhoneNumber>().map { it.normalized }.toSet()
        val existingEmails = results.filterIsInstance<DetectedEntity.Email>().map { it.address.lowercase() }.toSet()
        val existingUrls = results.filterIsInstance<DetectedEntity.Url>().map { it.url.lowercase() }.toSet()
        val existingDateTimes = results.filterIsInstance<DetectedEntity.DateTime>().map { it.text.lowercase() }.toSet()

        // Phone number patterns (Indian + international)
        val phoneRegex = Regex("""(?:\+?\d{1,3}[-.\s]?)?\(?\d{2,4}\)?[-.\s]?\d{3,4}[-.\s]?\d{3,4}""")
        for (match in phoneRegex.findAll(text)) {
            val raw = match.value
            val normalized = raw.replace(Regex("[^+\\d]"), "")
            if (normalized.length >= MIN_PHONE_LENGTH && normalized.length <= 15 && normalized !in existingPhones) {
                // Filter out things that look like order numbers / tracking numbers
                if (!isLikelyOrderNumber(text, match.range)) {
                    results.add(DetectedEntity.PhoneNumber(raw = raw, normalized = normalized))
                }
            }
        }

        // Email pattern
        val emailRegex = Regex("""[a-zA-Z0-9._%+\-]+@[a-zA-Z0-9.\-]+\.[a-zA-Z]{2,}""")
        for (match in emailRegex.findAll(text)) {
            val address = match.value.trim()
            if (address.lowercase() !in existingEmails) {
                results.add(DetectedEntity.Email(raw = match.value, address = address))
            }
        }

        // URL pattern
        val urlRegex = Regex("""https?://[^\s<>"{}|\\^`\[\]]+""", RegexOption.IGNORE_CASE)
        for (match in urlRegex.findAll(text)) {
            val url = match.value.trimEnd('.', ',', ')', ']')
            if (url.lowercase() !in existingUrls) {
                results.add(DetectedEntity.Url(raw = match.value, url = url))
            }
        }

        // ─── Hinglish + English Date/Time patterns ───────────────────────────
        // English: "tomorrow at 5 PM", "next Monday", "today 3pm", "on 25th Dec", "at 10:30 AM"
        // Hinglish: "kal 5 baje", "aaj shaam ko", "parso subah", "raat 9 baje"
        addDateTimeRegexEntities(text, results, existingDateTimes)
    }

    /**
     * Detect date/time expressions using regex patterns for both English and Hinglish.
     */
    private fun addDateTimeRegexEntities(
        text: String,
        results: MutableList<DetectedEntity>,
        existingDateTimes: Set<String>
    ) {
        val dateTimePatterns = listOf(
            // English patterns
            Regex("""(?i)\b(tomorrow|today|tonight)\s*(at\s*)?\d{1,2}(:\d{2})?\s*(am|pm|AM|PM)?\b"""),
            Regex("""(?i)\b(next|this)\s+(monday|tuesday|wednesday|thursday|friday|saturday|sunday)\b"""),
            Regex("""(?i)\bat\s+\d{1,2}(:\d{2})?\s*(am|pm|AM|PM)\b"""),
            Regex("""(?i)\b(on\s+)?\d{1,2}(st|nd|rd|th)?\s+(jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)\w*\b"""),
            Regex("""(?i)\b(in\s+)\d+\s+(minutes?|hours?|days?|weeks?)\b"""),
            // Hinglish patterns — "kal", "aaj", "parso", "subah", "shaam", "raat", "dopahar", "baje"
            Regex("""(?i)\b(kal|aaj|parso)\s*(subah|shaam|dopahar|raat)?\s*(\d{1,2}\s*baje)?\b"""),
            Regex("""(?i)\b(subah|shaam|dopahar|raat)\s*(\d{1,2}\s*baje)\b"""),
            Regex("""(?i)\b\d{1,2}\s*baje\b""")
        )

        for (pattern in dateTimePatterns) {
            for (match in pattern.findAll(text)) {
                val detected = match.value.trim()
                if (detected.length >= 3 && detected.lowercase() !in existingDateTimes) {
                    results.add(DetectedEntity.DateTime(raw = detected, text = detected))
                }
            }
        }
    }

    /**
     * Heuristic to avoid false-positive phone detection on order/tracking numbers.
     */
    private fun isLikelyOrderNumber(text: String, range: IntRange): Boolean {
        val prefix = text.substring(maxOf(0, range.first - 15), range.first).lowercase()
        return prefix.contains("order") || prefix.contains("#") || prefix.contains("tracking")
    }
}

