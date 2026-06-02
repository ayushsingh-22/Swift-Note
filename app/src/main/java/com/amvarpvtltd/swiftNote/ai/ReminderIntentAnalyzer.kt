package com.amvarpvtltd.swiftNote.ai

object ReminderIntentAnalyzer {

    data class CandidateContext(
        val text: String,
        val score: Int,
        val hasTemporalSignal: Boolean,
        val hasIntentSignal: Boolean
    )

    private data class SignalSummary(
        val hasExplicitReminder: Boolean,
        val hasActionSignal: Boolean,
        val hasTemporalSignal: Boolean,
        val score: Int
    ) {
        val hasIntentSignal: Boolean
            get() = hasExplicitReminder || hasActionSignal
    }

    private val explicitReminderPatterns = listOf(
        Regex("\\bremind(?: me)?\\b", RegexOption.IGNORE_CASE),
        Regex("\\bset (?:a )?reminder\\b", RegexOption.IGNORE_CASE),
        Regex("\\bremember to\\b", RegexOption.IGNORE_CASE),
        Regex("\\bdon'?t forget(?: to)?\\b", RegexOption.IGNORE_CASE),
        Regex("\\byaad\\s+dil(?:a|ana)?\\b", RegexOption.IGNORE_CASE),
        Regex("\\byaad\\s+rakh(?:na)?\\b", RegexOption.IGNORE_CASE),
        Regex("\\bremind\\s+kar\\b", RegexOption.IGNORE_CASE)
    )

    private val actionSignalPatterns = listOf(
        Regex(
            "\\b(?:meeting|call|appointment|deadline|alarm|submit|submission|pay|payment|buy|pickup|pick\\s*up|drop|renew|renewal|send|reply|visit|attend|doctor|dentist|exam|interview|flight|birthday|anniversary|medicine|medication|dawai|class|event|task|report|bill|follow\\s*up|assignment)\\b",
            RegexOption.IGNORE_CASE
        ),
        Regex(
            "\\b(?:need to|have to|must|should|plan to|want to|make sure to|check on|follow up on|karna hai|krna hai|jana hai|bhejna hai|lena hai|dena hai|submit karna|pay karna|call karna|milna hai|meeting hai|appointment hai|deadline hai)\\b",
            RegexOption.IGNORE_CASE
        )
    )

    private val temporalPatterns = listOf(
        Regex("\\b(?:today|tomorrow|tonight|this morning|this evening|this afternoon|next week|next month|next year)\\b", RegexOption.IGNORE_CASE),
        Regex("\\b(?:kal|aaj|parso|subah|subh|shaam|raat|dopahar|abhi|baad mein)\\b", RegexOption.IGNORE_CASE),
        Regex("\\b(?:monday|tuesday|wednesday|thursday|friday|saturday|sunday|mon|tue|wed|thu|fri|sat|sun)\\b", RegexOption.IGNORE_CASE),
        Regex("\\b(?:jan|feb|mar|apr|may|jun|jul|aug|sep|sept|oct|nov|dec|january|february|march|april|june|july|august|september|october|november|december)\\b", RegexOption.IGNORE_CASE),
        Regex("\\b\\d{1,2}(:\\d{2})?\\s?(?:am|pm)\\b", RegexOption.IGNORE_CASE),
        Regex("\\b([01]?\\d|2[0-3]):[0-5]\\d\\b"),
        Regex("\\b\\d{1,2}\\s*(?:baje|bje|bjey|bajke)\\b", RegexOption.IGNORE_CASE),
        Regex("\\b(?:in|after)\\s+\\d{1,3}\\s+(?:minutes?|mins?|hours?|hrs?)\\b", RegexOption.IGNORE_CASE),
        Regex("\\b\\d{1,3}\\s*(?:min|mins|minm|minute|minutes)\\s*(?:mai|mein)?\\b", RegexOption.IGNORE_CASE),
        Regex("\\b(?:every\\s+day|daily|weekly|monthly|yearly|annually|weekdays?|every\\s+week|every\\s+month|every\\s+year|roz|har\\s+din|har\\s+roz|har\\s+hafte|har\\s+hafta|har\\s+mahine|har\\s+saal)\\b", RegexOption.IGNORE_CASE)
    )

    private val numericDatePattern = Regex("\\b(\\d{1,4})[/-](\\d{1,2})(?:[/-](\\d{2,4}))?\\b")
    private val numericDateCuePattern = Regex(
        "\\b(?:on|by|due|before|after|until|till|from|starting|scheduled?|appointment|meeting|deadline|birthday|anniversary|flight|exam|doctor|dentist|visit|renew|renewal|submit|pay|bill)\\b",
        RegexOption.IGNORE_CASE
    )

    fun hasReminderIntent(noteBody: String, noteTitle: String = ""): Boolean {
        return extractCandidateContexts(noteBody = noteBody, noteTitle = noteTitle).isNotEmpty()
    }

    fun buildAnalysisText(noteBody: String, noteTitle: String = ""): String? {
        val merged = extractCandidateContexts(noteBody = noteBody, noteTitle = noteTitle)
            .map { it.text }
            .distinct()
            .joinToString(". ")
            .trim()
        return merged.ifBlank { null }
    }

    fun extractCandidateContexts(noteBody: String, noteTitle: String = ""): List<CandidateContext> {
        val sentences = splitIntoSentences(prepareText(noteBody, noteTitle))
        if (sentences.isEmpty()) return emptyList()

        val candidates = mutableListOf<CandidateContext>()

        sentences.forEachIndexed { index, sentence ->
            val window = buildWindow(sentences, index)
            val currentSignals = evaluateSignals(sentence)
            val windowSignals = evaluateSignals(window)

            val shouldAnalyze = windowSignals.hasTemporalSignal &&
                windowSignals.hasIntentSignal &&
                (currentSignals.hasTemporalSignal || currentSignals.hasIntentSignal)

            if (shouldAnalyze) {
                candidates.add(
                    CandidateContext(
                        text = window,
                        score = windowSignals.score,
                        hasTemporalSignal = windowSignals.hasTemporalSignal,
                        hasIntentSignal = windowSignals.hasIntentSignal
                    )
                )
            }
        }

        return candidates.distinctBy { it.text }
    }

    private fun prepareText(noteBody: String, noteTitle: String): String {
        val cleanedTitle = noteTitle.trim().takeUnless {
            it.isBlank() || it.equals("Untitled", ignoreCase = true)
        }
        return listOfNotNull(cleanedTitle, noteBody)
            .map(::sanitizeText)
            .filter { it.isNotBlank() }
            .joinToString(". ")
    }

    private fun sanitizeText(text: String): String {
        return text
            .replace(Regex("https?://\\S+"), " ")
            .replace(Regex("\\[[^\\]]+\\]\\([^)]*\\)"), " ")
            .replace(Regex("\\[[0-9]+]"), " ")
            .replace(Regex("\\[[0-9]+]:\\s*\\S+"), " ")
            .replace("\r", " ")
            .replace("\n", ". ")
            .replace(Regex("[*\\-]\\s+"), ". ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun splitIntoSentences(text: String): List<String> {
        if (text.isBlank()) return emptyList()
        return text
            .split(Regex("(?<=[.!?])\\s+|\\s*[;]+\\s+"))
            .map { it.trim().trim('.', '!', '?', ';', ':', '"', '\'') }
            .filter { it.isNotBlank() }
    }

    private fun buildWindow(sentences: List<String>, index: Int): String {
        val start = (index - 1).coerceAtLeast(0)
        val end = (index + 1).coerceAtMost(sentences.lastIndex)
        return sentences.subList(start, end + 1).joinToString(". ").trim()
    }

    private fun evaluateSignals(text: String): SignalSummary {
        val explicitReminder = explicitReminderPatterns.any { it.containsMatchIn(text) }
        val actionSignal = actionSignalPatterns.any { it.containsMatchIn(text) }
        val temporalSignal = temporalPatterns.any { it.containsMatchIn(text) } ||
            hasLikelyNumericDate(text, hasIntentSignal = explicitReminder || actionSignal)

        val score = (if (explicitReminder) 4 else 0) +
            (if (actionSignal) 3 else 0) +
            (if (temporalSignal) 3 else 0)

        return SignalSummary(
            hasExplicitReminder = explicitReminder,
            hasActionSignal = actionSignal,
            hasTemporalSignal = temporalSignal,
            score = score
        )
    }

    private fun hasLikelyNumericDate(text: String, hasIntentSignal: Boolean): Boolean {
        return numericDatePattern.findAll(text).any { match ->
            val first = match.groupValues[1].toIntOrNull() ?: return@any false
            val second = match.groupValues[2].toIntOrNull() ?: return@any false
            val third = match.groupValues.getOrNull(3).orEmpty()
            val hasCueWord = numericDateCuePattern.containsMatchIn(text)

            when {
                third.isNotBlank() -> true
                !hasIntentSignal && !hasCueWord -> false
                first > 12 || second > 12 -> true
                else -> hasIntentSignal || hasCueWord
            }
        }
    }
}
