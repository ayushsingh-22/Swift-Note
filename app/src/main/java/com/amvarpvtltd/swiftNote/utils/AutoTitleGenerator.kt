package com.amvarpvtltd.swiftNote.utils

import com.amvarpvtltd.swiftNote.checklist.ChecklistParser
import com.amvarpvtltd.swiftNote.richtext.RichTextBridge

/**
 * Phase 4: Auto-Title Generator (Rule-Based Engine)
 *
 * Two-tier architecture:
 *   Tier 1 → LLM-powered via [com.amvarpvtltd.swiftNote.ai.AITitleGenerator]
 *   Tier 2 → This class (offline, instant, 8-strategy pipeline)
 *
 * This class is synchronous and used for:
 *   - Live suggestion chip (instant feedback as user types)
 *   - Offline saves (no network)
 *   - Fallback when all LLM providers fail
 *
 * Used by: AddScreen save path, ShareReceiverActivity, QuickCaptureSheet, AITitleGenerator.
 */
object AutoTitleGenerator {

    private const val MAX_TITLE_LENGTH = 40
    private const val IDEAL_TITLE_LENGTH = 25

    // ──────────────────── PUBLIC API ────────────────────

    /**
     * Synchronous rule-based title generation (8-strategy pipeline).
     */
    fun generate(description: String): String {
        if (description.isBlank()) return ""

        if (ChecklistParser.isChecklistContent(description)) {
            return generateFromChecklist(description)
        }

        val plainText = toPlainText(description)
        if (plainText.isBlank()) return ""

        return applyStrategies(plainText)
    }

    /**
     * Check if description has enough content to auto-generate a valid title.
     */
    fun canGenerateTitle(description: String): Boolean {
        return generate(description).length >= Constants.MIN_CONTENT_LENGTH
    }

    /**
     * Truncate text to max length at word boundary. Public for AITitleGenerator reuse.
     */
    fun truncate(text: String, maxLen: Int = MAX_TITLE_LENGTH): String {
        if (text.length <= maxLen) return text
        val t = text.take(maxLen)
        val lastSpace = t.lastIndexOf(' ')
        return if (lastSpace > maxLen / 3) {
            t.substring(0, lastSpace) + "…"
        } else {
            t.trimEnd() + "…"
        }
    }

    // ──────────────────── STRATEGY PIPELINE ────────────────────

    private fun applyStrategies(plainText: String): String {
        return tryUrlTitle(plainText)
            ?: tryQuestionTitle(plainText)
            ?: tryActionTitle(plainText)
            ?: tryListTitle(plainText)
            ?: tryDateEventTitle(plainText)
            ?: tryCompoundSentenceTitle(plainText)
            ?: tryKeyPhraseTitle(plainText)
            ?: fallbackTitle(plainText)
    }

    // ─── Strategy 1: URL → extract domain ───

    private val URL_REGEX = Regex("^https?://\\S+$")

    private fun tryUrlTitle(text: String): String? {
        val firstLine = text.lines().first().trim()
        if (!URL_REGEX.matches(firstLine)) return null
        return try {
            val domain = firstLine
                .removePrefix("https://").removePrefix("http://")
                .removePrefix("www.")
                .substringBefore("/")
                .substringBefore("?")
            "Link: $domain"
        } catch (_: Exception) { "Saved Link" }
    }

    // ─── Strategy 2: Question → preserve up to "?" ───

    private val QUESTION_START = Regex(
        "^(what|how|why|when|where|who|which|can|should|is|are|do|does|will|would|could|shall|" +
            "kya|kaise|kyun|kab|kahan|konsa)\\b",
        RegexOption.IGNORE_CASE
    )

    private fun tryQuestionTitle(text: String): String? {
        val firstLine = text.lines().firstOrNull { it.isNotBlank() }?.trim() ?: return null
        if (!QUESTION_START.containsMatchIn(firstLine)) return null
        val qEnd = firstLine.indexOf('?')
        val question = if (qEnd in 1..MAX_TITLE_LENGTH) firstLine.substring(0, qEnd + 1) else firstLine
        return truncate(question)
    }

    // ─── Strategy 3: Action verb → extract verb phrase ───

    private val ACTION_VERBS = setOf(
        // Common English
        "buy", "call", "email", "send", "fix", "update", "check", "review",
        "finish", "complete", "submit", "book", "schedule", "meet", "pick",
        "drop", "pay", "cancel", "renew", "return", "clean", "prepare",
        "write", "read", "watch", "download", "install", "setup", "create",
        "delete", "move", "copy", "share", "remind", "remember", "note",
        "order", "plan", "confirm", "reserve", "deliver", "collect", "apply",
        "upload", "backup", "print", "sign", "approve", "reject", "test",
        "deploy", "release", "merge", "push", "pull", "commit", "transfer",
        "discuss", "attend", "organize", "arrange", "visit", "learn", "study",
        "practice", "cook", "eat", "drink", "exercise", "run", "walk",
        "register", "login", "logout", "configure", "debug", "refactor",
        // Hinglish
        "kharido", "bhejo", "likho", "padho", "dekho", "suno", "karo",
        "banao", "bolo", "jao", "aao", "kholo", "band", "bhulo"
    )

    private fun tryActionTitle(text: String): String? {
        val firstLine = text.lines().firstOrNull { it.isNotBlank() }?.trim() ?: return null
        val words = firstLine.split(Regex("\\s+"))
        val lowerWords = words.map { it.lowercase().replace(Regex("[^a-z]"), "") }

        val actionIdx = lowerWords.indexOfFirst { it in ACTION_VERBS }
        if (actionIdx < 0 || actionIdx > 2) return null

        // verb + up to 4 following words, trim trailing punctuation
        val endIdx = (actionIdx + 5).coerceAtMost(words.size)
        val phrase = words.subList(actionIdx, endIdx).joinToString(" ")
            .replace(Regex("[.,;:!]+$"), "")

        return truncate(phrase.replaceFirstChar { it.uppercase() })
    }

    // ─── Strategy 4: Bullet/numbered list → summarize ───

    private val LIST_BULLET = Regex("^\\s*[-•*▪▸►]\\s+|^\\s*\\d+[.):]\\s+")

    private fun tryListTitle(text: String): String? {
        val lines = text.lines().filter { it.isNotBlank() }
        val listLines = lines.filter { LIST_BULLET.containsMatchIn(it) }

        if (listLines.size < 3) return null

        val cleanItems = listLines.map { it.replace(LIST_BULLET, "").trim() }
        val theme = findCommonTheme(cleanItems)
        if (theme.isNotBlank()) return truncate(theme)

        val first = cleanItems.first().split(" ").take(2).joinToString(" ")
        return truncate("$first & ${listLines.size - 1} more")
    }

    // ─── Strategy 5: Date/event/time markers → contextual title ───

    private val DATE_MARKERS = Regex(
        "(today|tomorrow|tonight|monday|tuesday|wednesday|thursday|friday|saturday|sunday|" +
            "morning|evening|afternoon|meeting|appointment|deadline|due|event|birthday|anniversary|" +
            "interview|exam|flight|train|doctor|dentist|" +
            "kal|aaj|parso|subah|shaam|raat|dopahar)",
        RegexOption.IGNORE_CASE
    )

    private fun tryDateEventTitle(text: String): String? {
        val firstLine = text.lines().firstOrNull { it.isNotBlank() }?.trim() ?: return null
        if (!DATE_MARKERS.containsMatchIn(firstLine)) return null

        if (firstLine.length <= MAX_TITLE_LENGTH) {
            return firstLine.replaceFirstChar { it.uppercase() }
        }

        // Extract a window around the date marker
        val match = DATE_MARKERS.find(firstLine) ?: return null
        val start = (match.range.first - 15).coerceAtLeast(0)
        val end = (match.range.last + 20).coerceAtMost(firstLine.length)
        val segment = firstLine.substring(start, end).trim()

        return truncate(segment.replaceFirstChar { it.uppercase() })
    }

    // ─── Strategy 6: Compound sentence → first clause ───

    private val CLAUSE_SEPARATORS = Regex(
        "\\s+(and then|and|but|then|so that|so|because|since|after|before|while|or|hence|therefore)\\s+",
        RegexOption.IGNORE_CASE
    )

    private fun tryCompoundSentenceTitle(text: String): String? {
        val firstLine = text.lines().firstOrNull { it.isNotBlank() }?.trim() ?: return null
        if (firstLine.length <= MAX_TITLE_LENGTH) return null // not needed, fallback handles it

        val match = CLAUSE_SEPARATORS.find(firstLine) ?: return null
        val firstClause = firstLine.substring(0, match.range.first).trim()

        return if (firstClause.length in Constants.MIN_CONTENT_LENGTH..MAX_TITLE_LENGTH) {
            firstClause.replaceFirstChar { it.uppercase() }
        } else null
    }

    // ─── Strategy 7: Key phrase extraction (NLP-lite) ───

    private fun tryKeyPhraseTitle(text: String): String? {
        val lines = text.lines().filter { it.isNotBlank() }
        val firstLine = lines.firstOrNull()?.trim() ?: return null

        // Short first line → use as-is
        if (firstLine.length <= IDEAL_TITLE_LENGTH && firstLine.split(" ").size >= 2) {
            return firstLine.replaceFirstChar { it.uppercase() }
        }

        // Extract keywords from first 2 lines
        val sourceText = lines.take(2).joinToString(" ")
        val words = sourceText.split(Regex("[\\s,;:()\\[\\]{}\"]+"))
            .map { it.replace(Regex("[^\\w'-]"), "") }
            .filter { it.length > 2 && it.lowercase() !in STOP_WORDS }
            .distinctBy { it.lowercase() }

        if (words.isEmpty()) return null

        val keyWords = words.take(4)
        val title = keyWords.joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }

        return if (title.length >= Constants.MIN_CONTENT_LENGTH) truncate(title) else null
    }

    // ─── Strategy 8: Fallback (natural break or first line) ───

    private fun fallbackTitle(text: String): String {
        val firstLine = text.lines().firstOrNull { it.isNotBlank() }?.trim() ?: return ""
        if (firstLine.length <= MAX_TITLE_LENGTH) {
            return firstLine.replaceFirstChar { it.uppercase() }
        }

        // Natural break points
        val breaks = listOf(". ", ", ", " - ", " — ", "; ")
        for (bp in breaks) {
            val idx = firstLine.indexOf(bp)
            if (idx in Constants.MIN_CONTENT_LENGTH..MAX_TITLE_LENGTH) {
                return firstLine.substring(0, idx).replaceFirstChar { it.uppercase() }
            }
        }
        return truncate(firstLine)
    }

    // ──────────────────── CHECKLIST HANDLER ────────────────────

    private fun generateFromChecklist(description: String): String {
        val items = ChecklistParser.parseItems(description)
        if (items.isEmpty()) return ""

        val unchecked = items.filter { !it.isChecked && it.text.isNotBlank() }
        val allItems = items.filter { it.text.isNotBlank() }
        val relevantItems = unchecked.ifEmpty { allItems }
        if (relevantItems.isEmpty()) return ""

        if (relevantItems.size <= 2) {
            return truncate(relevantItems.first().text.trim())
        }

        val theme = findCommonTheme(relevantItems.map { it.text })
        if (theme.isNotBlank()) return truncate(theme)

        val first = relevantItems.first().text.trim().split(" ").take(3).joinToString(" ")
        return truncate("$first +${relevantItems.size - 1} more")
    }

    // ──────────────────── UTILITIES ────────────────────

    private fun toPlainText(description: String): String {
        return if (RichTextBridge.containsHtml(description)) {
            RichTextBridge.stripHtmlToPlainText(description)
        } else {
            description
        }.trim()
    }

    private fun findCommonTheme(items: List<String>): String {
        if (items.size < 3) return ""

        val wordFreq = mutableMapOf<String, Int>()
        items.forEach { item ->
            item.lowercase().split(Regex("\\s+"))
                .map { it.replace(Regex("[^\\w]"), "") }
                .filter { it.length > 2 && it !in STOP_WORDS }
                .toSet()
                .forEach { word -> wordFreq[word] = (wordFreq[word] ?: 0) + 1 }
        }

        val threshold = (items.size * 0.5).toInt().coerceAtLeast(2)
        val themeWords = wordFreq.entries
            .filter { it.value >= threshold }
            .sortedByDescending { it.value }
            .take(2)
            .map { it.key.replaceFirstChar { c -> c.uppercase() } }

        return if (themeWords.isNotEmpty()) "${themeWords.joinToString(" ")} List" else ""
    }

    // Expanded stop words for better keyword extraction
    private val STOP_WORDS = setOf(
        "the", "a", "an", "is", "are", "was", "were", "be", "been", "being",
        "have", "has", "had", "do", "does", "did", "will", "would", "could",
        "should", "may", "might", "shall", "can", "need", "dare", "ought",
        "used", "to", "of", "in", "for", "on", "with", "at", "by", "from",
        "as", "into", "through", "during", "before", "after", "above", "below",
        "between", "out", "off", "over", "under", "again", "further", "then",
        "once", "here", "there", "when", "where", "why", "how", "all", "each",
        "every", "both", "few", "more", "most", "other", "some", "such", "no",
        "nor", "not", "only", "own", "same", "so", "than", "too", "very",
        "just", "because", "but", "and", "or", "if", "while", "that", "this",
        "these", "those", "it", "its", "i", "me", "my", "we", "our", "you",
        "your", "he", "him", "his", "she", "her", "they", "them", "their",
        "what", "which", "who", "whom", "am", "about", "also", "get", "got",
        "really", "actually", "basically", "literally", "like", "thing", "things",
        "going", "want", "know", "think", "make", "take", "come", "see",
        "look", "give", "use", "find", "tell", "new", "good", "first", "last",
        "long", "great", "little", "much", "many", "well", "back", "even", "still",
        // Hindi/Hinglish stop words
        "hai", "hain", "tha", "thi", "the", "kar", "karna", "mein", "se",
        "ko", "ka", "ki", "ke", "par", "bhi", "nahi", "yeh", "woh", "kuch",
        "apna", "apni", "apne", "ek", "aur", "ya", "mujhe", "tum", "hum"
    )
}

