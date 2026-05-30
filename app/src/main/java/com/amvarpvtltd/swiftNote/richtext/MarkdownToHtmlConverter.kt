package com.amvarpvtltd.swiftNote.richtext

/**
 * Converts common Markdown patterns to HTML for display in the rich text editor.
 *
 * Handles:
 * - **bold** / __bold__
 * - *italic* / _italic_
 * - ~~strikethrough~~
 * - # H1, ## H2 headings
 * - Numbered lists (1. 2. 3.)
 * - Bullet lists (- or * or •)
 * - `inline code`
 * - [link text](url)
 * - Line breaks
 *
 * This is a best-effort converter for pasted content — not a full markdown spec implementation.
 */
object MarkdownToHtmlConverter {

    /**
     * Returns true if the text appears to contain markdown formatting.
     */
    fun containsMarkdown(text: String): Boolean {
        if (text.isBlank()) return false
        return MARKDOWN_INDICATORS.any { it.containsMatchIn(text) }
    }

    /**
     * Convert markdown-formatted plain text to HTML.
     * If the text doesn't appear to contain markdown, returns null (caller should use plain text).
     */
    fun convert(text: String): String? {
        if (text.isBlank()) return null
        if (!containsMarkdown(text)) return null

        val lines = text.lines()
        val htmlLines = mutableListOf<String>()
        var inOrderedList = false
        var inUnorderedList = false

        for (line in lines) {
            val trimmed = line.trim()

            // Close lists if current line is not a list item
            if (inOrderedList && !ORDERED_LIST_REGEX.matches(trimmed) && trimmed.isNotBlank()) {
                htmlLines.add("</ol>")
                inOrderedList = false
            }
            if (inUnorderedList && !UNORDERED_LIST_REGEX.matches(trimmed) && !isAsteriskListItem(trimmed) && trimmed.isNotBlank()) {
                htmlLines.add("</ul>")
                inUnorderedList = false
            }

            when {
                trimmed.isBlank() -> {
                    // Close any open lists on blank line
                    if (inOrderedList) { htmlLines.add("</ol>"); inOrderedList = false }
                    if (inUnorderedList) { htmlLines.add("</ul>"); inUnorderedList = false }
                    // Don't add empty paragraphs between list transitions
                    if (htmlLines.lastOrNull()?.let { it != "</ol>" && it != "</ul>" } != false) {
                        htmlLines.add("<br>")
                    }
                }

                // Headings: longest match first (######, then #####, ..., then #)
                trimmed.startsWith("###### ") -> {
                    htmlLines.add("<h2>${convertInlineFormatting(trimmed.removePrefix("###### "))}</h2>")
                }
                trimmed.startsWith("##### ") -> {
                    htmlLines.add("<h2>${convertInlineFormatting(trimmed.removePrefix("##### "))}</h2>")
                }
                trimmed.startsWith("#### ") -> {
                    htmlLines.add("<h2>${convertInlineFormatting(trimmed.removePrefix("#### "))}</h2>")
                }
                trimmed.startsWith("### ") -> {
                    htmlLines.add("<h2>${convertInlineFormatting(trimmed.removePrefix("### "))}</h2>")
                }
                trimmed.startsWith("## ") -> {
                    htmlLines.add("<h2>${convertInlineFormatting(trimmed.removePrefix("## "))}</h2>")
                }
                trimmed.startsWith("# ") -> {
                    htmlLines.add("<h1>${convertInlineFormatting(trimmed.removePrefix("# "))}</h1>")
                }

                // Ordered list: 1. item, 2. item, etc.
                ORDERED_LIST_REGEX.matches(trimmed) -> {
                    if (!inOrderedList) {
                        htmlLines.add("<ol>")
                        inOrderedList = true
                    }
                    val content = ORDERED_LIST_REGEX.find(trimmed)!!.groupValues[1]
                    htmlLines.add("<li>${convertInlineFormatting(content)}</li>")
                }

                // Unordered list: - item, • item, or * item (when not italic wrap)
                UNORDERED_LIST_REGEX.matches(trimmed) || isAsteriskListItem(trimmed) -> {
                    if (!inUnorderedList) {
                        htmlLines.add("<ul>")
                        inUnorderedList = true
                    }
                    val content = if (UNORDERED_LIST_REGEX.matches(trimmed)) {
                        UNORDERED_LIST_REGEX.find(trimmed)!!.groupValues[1]
                    } else {
                        ASTERISK_LIST_REGEX.find(trimmed)!!.groupValues[1]
                    }
                    htmlLines.add("<li>${convertInlineFormatting(content)}</li>")
                }

                // Regular paragraph
                else -> {
                    htmlLines.add("<p>${convertInlineFormatting(trimmed)}</p>")
                }
            }
        }

        // Close any remaining open lists
        if (inOrderedList) htmlLines.add("</ol>")
        if (inUnorderedList) htmlLines.add("</ul>")

        return htmlLines.joinToString("\n")
    }

    /**
     * Convert inline markdown formatting to HTML within a single line.
     */
    private fun convertInlineFormatting(text: String): String {
        var result = text

        // Escape HTML entities first (avoid double-encoding)
        // Skip this for text that might already have HTML
        if (!RichTextBridge.containsHtml(result)) {
            result = result
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
        }

        // Bold: **text** or __text__
        result = BOLD_REGEX.replace(result) { "<b>${it.groupValues[1]}</b>" }
        result = BOLD_UNDERSCORE_REGEX.replace(result) { "<b>${it.groupValues[1]}</b>" }

        // Italic: *text* or _text_ (but not inside words for underscore)
        result = ITALIC_REGEX.replace(result) { "<i>${it.groupValues[1]}</i>" }
        result = ITALIC_UNDERSCORE_REGEX.replace(result) { "<i>${it.groupValues[1]}</i>" }

        // Strikethrough: ~~text~~
        result = STRIKETHROUGH_REGEX.replace(result) { "<s>${it.groupValues[1]}</s>" }

        // Inline code: `code`
        result = INLINE_CODE_REGEX.replace(result) { "<code>${it.groupValues[1]}</code>" }

        // Links: [text](url)
        result = LINK_REGEX.replace(result) { "<a href=\"${it.groupValues[2]}\">${it.groupValues[1]}</a>" }

        return result
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Determines if a line starting with "* " is a list item (not italic wrapping).
     * "* item text" is a list item, but "*italic text*" is inline formatting.
     * Key heuristic: if line starts with "* " and the content doesn't end with "*", it's a list item.
     */
    private fun isAsteriskListItem(line: String): Boolean {
        if (!ASTERISK_LIST_REGEX.matches(line)) return false
        val content = ASTERISK_LIST_REGEX.find(line)!!.groupValues[1]
        // If the remaining content ends with * it's likely wrapping italic
        return !content.endsWith("*")
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Regex patterns
    // ─────────────────────────────────────────────────────────────────────────

    private val BOLD_REGEX = Regex("\\*\\*(.+?)\\*\\*")
    private val BOLD_UNDERSCORE_REGEX = Regex("__(.+?)__")
    private val ITALIC_REGEX = Regex("(?<!\\*)\\*(?!\\*)(.+?)(?<!\\*)\\*(?!\\*)")
    private val ITALIC_UNDERSCORE_REGEX = Regex("(?<![\\w_])_(?!_)(.+?)(?<!_)_(?![\\w_])")
    private val STRIKETHROUGH_REGEX = Regex("~~(.+?)~~")
    private val INLINE_CODE_REGEX = Regex("`([^`]+)`")
    private val LINK_REGEX = Regex("\\[([^\\]]+)]\\(([^)]+)\\)")

    private val ORDERED_LIST_REGEX = Regex("^\\d+\\.\\s+(.+)$")
    // Unordered list: "- item" or "• item"
    // Note: "* item" is ambiguous (could be italic), so we only treat "- " and "• " as bullet markers.
    // If user writes "* text" where text doesn't end with *, treat as list; otherwise italic.
    private val UNORDERED_LIST_REGEX = Regex("^[-•]\\s+(.+)$")
    // Asterisk list: "* item" — only if the line doesn't look like *italic wrapping*
    private val ASTERISK_LIST_REGEX = Regex("^\\*\\s+(.+)$")

    /** Quick checks to see if text likely contains markdown */
    private val MARKDOWN_INDICATORS = listOf(
        Regex("\\*\\*.+?\\*\\*"),           // **bold**
        Regex("__.+?__"),                   // __bold__
        Regex("(?<!\\*)\\*(?!\\*).+?(?<!\\*)\\*(?!\\*)"), // *italic*
        Regex("^#{1,6}\\s+", RegexOption.MULTILINE),     // # heading
        Regex("^\\d+\\.\\s+", RegexOption.MULTILINE),    // 1. list
        Regex("^[-•]\\s+", RegexOption.MULTILINE),       // - bullet
        Regex("^\\*\\s+[^*]", RegexOption.MULTILINE),    // * bullet (not italic)
        Regex("`[^`]+`"),                   // `code`
        Regex("\\[.+?]\\(.+?\\)"),          // [link](url)
        Regex("~~.+?~~")                    // ~~strike~~
    )
}







