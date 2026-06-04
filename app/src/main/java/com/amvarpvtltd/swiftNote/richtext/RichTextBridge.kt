package com.amvarpvtltd.swiftNote.richtext

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

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
            Jsoup.parseBodyFragment(html).text()
        } catch (e: Exception) {
            // Fallback: strip tags with regex if Jsoup fails (should never happen)
            html.replace(Regex("<[^>]+>"), "")
        }
    }

    /**
     * Convert HTML to well-formatted plain text that preserves structural
     * formatting (paragraphs, headings, lists, code blocks, links).
     * Also strips any remaining Markdown-style formatting markers (e.g. *bold*).
     *
     * Used by: ShareUtils for copy-to-clipboard and share operations so the
     * recipient sees a readable, structured note — not a flat wall of text.
     */
    fun htmlToFormattedPlainText(html: String): String {
        if (html.isBlank()) return html

        // If no HTML tags, check if it's markdown — convert to HTML first for proper structure
        val effectiveHtml = if ('<' !in html) {
            if (MarkdownToHtmlConverter.containsMarkdown(html)) {
                // Convert markdown → HTML so we can walk the DOM tree for structure
                MarkdownToHtmlConverter.convert(html) ?: html
            } else {
                // Plain text with no formatting — just return as-is preserving line breaks
                return html
            }
        } else {
            html
        }

        // If still no HTML after markdown conversion attempt, return stripped
        if ('<' !in effectiveHtml) return stripMarkdownFormatting(effectiveHtml)

        return try {
            val doc = Jsoup.parseBodyFragment(effectiveHtml)
            val sb = StringBuilder()
            renderNode(doc.body(), sb, listDepth = 0, orderedIndex = null)
            // Clean up excessive blank lines then strip markdown markers
            val structured = sb.toString()
                .replace(Regex("[ \\t]+\\n"), "\n")       // trailing spaces
                .replace(Regex("\\n{3,}"), "\n\n")          // max 2 consecutive newlines
                .trim()
            stripMarkdownFormatting(structured)
        } catch (e: Exception) {
            stripMarkdownFormatting(stripHtmlToPlainText(effectiveHtml))
        }
    }

    /**
     * Strip Markdown-style formatting markers from plain text:
     * - **bold** or *bold* → bold (remove asterisks)
     * - __bold__ or _italic_ → text (remove underscores)
     * - Lines starting with "- " → "• " (bullet points)
     * - Lines starting with "* " (list item) → "• "
     * - Heading markers like "# ", "## " → removed
     */
    private fun stripMarkdownFormatting(text: String): String {
        if (text.isBlank()) return text

        var result = text

        // Strip bold/italic markers: **text** → text, *text* → text
        // Handle double asterisks first, then single
        result = result.replace(Regex("\\*\\*(.+?)\\*\\*")) { it.groupValues[1] }

        // Single *text* — match asterisks that wrap words/phrases (not math like 2*3)
        // Handles: *bold text*, beginning of line, after space/punctuation
        result = result.replace(Regex("(^|[\\s(\\[])\\*([^*\\n]+?)\\*([\\s),.;:!?\\]\\-]|$)", RegexOption.MULTILINE)) { 
            "${it.groupValues[1]}${it.groupValues[2]}${it.groupValues[3]}" 
        }

        // Strip __text__ and _text_ (underscore bold/italic)
        result = result.replace(Regex("__(.+?)__")) { it.groupValues[1] }
        result = result.replace(Regex("(^|[\\s(\\[])_([^_\\n]+?)_([\\s),.;:!?\\]\\-]|$)", RegexOption.MULTILINE)) {
            "${it.groupValues[1]}${it.groupValues[2]}${it.groupValues[3]}"
        }

        // Convert "- " at line start to "• " (markdown unordered list)
        result = result.replace(Regex("^- ", RegexOption.MULTILINE), "• ")

        // Convert "* " at line start to "• " (markdown unordered list alternative)
        result = result.replace(Regex("^\\* ", RegexOption.MULTILINE), "• ")

        // Strip heading markers "# ", "## ", "### " etc. at line start
        result = result.replace(Regex("^#{1,6}\\s+", RegexOption.MULTILINE), "")

        return result
    }

    private fun renderNode(
        node: Node,
        sb: StringBuilder,
        listDepth: Int,
        orderedIndex: MutableList<Int>?
    ) {
        when (node) {
            is TextNode -> {
                val text = node.wholeText
                // Skip pure-whitespace nodes between block elements
                if (text.isNotBlank()) {
                    sb.append(text)
                }
            }

            is Element -> {
                val tag = node.tagName().lowercase()
                when (tag) {
                    "br" -> sb.append("\n")

                    "p", "div" -> {
                        ensureNewline(sb)
                        renderChildren(node, sb, listDepth, orderedIndex)
                        sb.append("\n")
                    }

                    "h1", "h2", "h3", "h4", "h5", "h6" -> {
                        ensureNewline(sb)
                        renderChildren(node, sb, listDepth, orderedIndex)
                        sb.append("\n")
                    }

                    "ul" -> {
                        ensureNewline(sb)
                        for (child in node.childNodes()) {
                            renderNode(child, sb, listDepth + 1, null)
                        }
                    }

                    "ol" -> {
                        ensureNewline(sb)
                        val idx = mutableListOf(0)
                        for (child in node.childNodes()) {
                            renderNode(child, sb, listDepth + 1, idx)
                        }
                    }

                    "li" -> {
                        val indent = "  ".repeat((listDepth - 1).coerceAtLeast(0))
                        if (orderedIndex != null) {
                            orderedIndex[0]++
                            sb.append("$indent${orderedIndex[0]}. ")
                        } else {
                            sb.append("$indent• ")
                        }
                        renderChildren(node, sb, listDepth, orderedIndex)
                        sb.append("\n")
                    }

                    "pre", "code" -> {
                        val isBlock = tag == "pre" || node.parent()?.tagName()?.lowercase() != "pre"
                                && node.children().isNotEmpty()
                        if (isBlock && tag == "pre") {
                            ensureNewline(sb)
                        }
                        renderChildren(node, sb, listDepth, orderedIndex)
                        if (isBlock && tag == "pre") {
                            sb.append("\n")
                        }
                    }

                    "a" -> {
                        val linkText = node.text()
                        val href = node.attr("href")
                        if (href.isNotBlank() && linkText != href) {
                            sb.append("$linkText ($href)")
                        } else {
                            sb.append(linkText)
                        }
                    }

                    "input" -> {
                        // Checklist items
                        val isChecked = node.hasAttr("checked")
                        sb.append(if (isChecked) "☑ " else "☐ ")
                    }

                    // Inline formatting tags — just render children (no markdown wrappers needed for plain text)
                    "b", "strong", "i", "em", "u" -> {
                        renderChildren(node, sb, listDepth, orderedIndex)
                    }

                    // body or unknown — just recurse
                    else -> {
                        renderChildren(node, sb, listDepth, orderedIndex)
                    }
                }
            }

            else -> {
                // Other node types (comments, etc.) — ignore
            }
        }
    }

    private fun renderChildren(
        element: Element,
        sb: StringBuilder,
        listDepth: Int,
        orderedIndex: MutableList<Int>?
    ) {
        for (child in element.childNodes()) {
            renderNode(child, sb, listDepth, orderedIndex)
        }
    }

    /** Ensure we're on a fresh line without adding excessive blank lines. */
    private fun ensureNewline(sb: StringBuilder) {
        if (sb.isNotEmpty() && sb.last() != '\n') {
            sb.append("\n")
        }
    }

    /**
     * Pre-process HTML (or plain text) so that leading whitespace (spaces/tabs
     * used for indentation) is preserved when rendered by an HTML engine.
     *
     * HTML renderers collapse consecutive whitespace to a single space by
     * default, so "    word" inside a <p> loses its indentation.  This
     * function converts those leading spaces/tabs to non-breaking-space
     * entities (&nbsp;) so the visual indentation survives rendering.
     *
     * - Plain text (no '<'): converts to HTML, turning '\n' into <br> and
     *   leading spaces/tabs on each line into &nbsp; sequences.
     * - HTML content: replaces space/tab runs that appear immediately after
     *   a '>' boundary (i.e. at the start of a text node) with &nbsp;.
     */
    fun preserveLeadingWhitespace(html: String): String {
        if (html.isBlank()) return html

        if ('<' !in html) {
            // Pure plain text — convert to displayable HTML preserving indentation
            return html
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .split("\n")
                .joinToString("<br>") { line ->
                    val leading = line.length - line.trimStart(' ', '\t').length
                    if (leading > 0) {
                        line.substring(0, leading)
                            .replace(" ", "&nbsp;")
                            .replace("\t", "&nbsp;&nbsp;&nbsp;&nbsp;") +
                                line.substring(leading)
                    } else {
                        line
                    }
                }
        }

        // HTML content — replace space/tab runs that directly follow a '>'
        // (i.e. at the start of a text node inside a block element).
        return html
            .replace(Regex("(?<=>)([ ]+)")) { match ->
                "&nbsp;".repeat(match.value.length)
            }
            .replace(Regex("(?<=>)(\\t+)")) { match ->
                "&nbsp;&nbsp;&nbsp;&nbsp;".repeat(match.value.length)
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
            "<(/?)(b|strong|i|em|u|h[1-6]|p|br|ul|ol|li|a|code|pre|input|div|span|s)\\b[^>]*>",
            RegexOption.IGNORE_CASE
        ).containsMatchIn(text)
    }
}
