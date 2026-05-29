package com.amvarpvtltd.swiftNote.richtext

/**
 * Facade over the MohamedRejeb compose-rich-editor library.
 *
 * All external callers that previously used RichTextRenderer go through here.
 * Keeping library calls in one place makes future swaps a single-file change.
 */
object RichTextBridge {

    /**
     * Strip all HTML tags and return plain text.
     * Used by: SmartEntityDetector, SearchAndSortManager, AutoTitleGenerator.
     *
     * Replaces RichTextRenderer.stripHtmlToPlainText().
     */
    fun stripHtmlToPlainText(html: String): String {
        if (html.isBlank() || '<' !in html) return html
        return try {
            org.jsoup.Jsoup.parseBodyFragment(html).text()
        } catch (e: Exception) {
            // Fallback: strip tags with regex if Jsoup fails (should never happen)
            html.replace(Regex("<[^>]+>"), "")
        }
    }

    /**
     * Returns true if the string contains any HTML we recognise.
     * Used in save/load paths to decide rendering mode.
     *
     * Replaces RichTextRenderer.containsHtml().
     */
    fun containsHtml(text: String): Boolean {
        if (text.isBlank() || '<' !in text) return false
        return Regex(
            "<(/?)(b|strong|i|em|u|h1|h2|p|br|ul|ol|li|a|code|pre|input)\\b[^>]*>",
            RegexOption.IGNORE_CASE
        ).containsMatchIn(text)
    }
}
