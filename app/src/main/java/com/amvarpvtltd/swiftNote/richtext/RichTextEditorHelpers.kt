package com.amvarpvtltd.swiftNote.richtext

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

/**
 * Pure, stateless helper functions for HTML-based rich text editing.
 *
 * All functions take a [TextFieldValue] (text + cursor/selection) and return a new
 * [TextFieldValue] with the formatting applied.  They never touch Compose state or the
 * UI layer — they are trivially unit-testable.
 *
 * Phase 2 implementation.
 */
object RichTextEditorHelpers {

    // ─── Inline tags ──────────────────────────────────────────────────────────

    /**
     * Toggle an inline HTML tag around the current selection.
     *
     * Behaviour:
     * - **No selection (collapsed cursor):** inserts `<tag></tag>` and places cursor between
     *   the opening and closing tags.
     * - **Selection already wrapped:** unwraps — removes the surrounding tags.
     * - **New selection:** wraps the selected text with `<tag>…</tag>`.
     */
    fun toggleInlineTag(value: TextFieldValue, tag: String): TextFieldValue {
        val openTag  = "<$tag>"
        val closeTag = "</$tag>"
        val sel  = value.selection
        val text = value.text

        if (sel.collapsed) {
            val newText = text.substring(0, sel.start) + openTag + closeTag + text.substring(sel.start)
            return value.copy(
                text      = newText,
                selection = TextRange(sel.start + openTag.length)
            )
        }

        val before = text.substring(0, sel.start)
        val after  = text.substring(sel.end)

        // Already wrapped → unwrap
        if (before.endsWith(openTag) && after.startsWith(closeTag)) {
            val newText = before.dropLast(openTag.length) +
                          text.substring(sel.start, sel.end) +
                          after.drop(closeTag.length)
            return value.copy(
                text      = newText,
                selection = TextRange(sel.start - openTag.length, sel.end - openTag.length)
            )
        }

        // Wrap selection
        val selected = text.substring(sel.start, sel.end)
        val newText  = before + openTag + selected + closeTag + after
        return value.copy(
            text      = newText,
            selection = TextRange(sel.start + openTag.length, sel.end + openTag.length)
        )
    }

    /** Bold — toggles `<b>…</b>` around selection. */
    fun toggleBold(value: TextFieldValue)      = toggleInlineTag(value, "b")

    /** Italic — toggles `<i>…</i>` around selection. */
    fun toggleItalic(value: TextFieldValue)    = toggleInlineTag(value, "i")

    /** Underline — toggles `<u>…</u>` around selection. */
    fun toggleUnderline(value: TextFieldValue) = toggleInlineTag(value, "u")

    /** Inline code — toggles `<code>…</code>` around selection. */
    fun toggleCode(value: TextFieldValue)      = toggleInlineTag(value, "code")

    // ─── Headings ─────────────────────────────────────────────────────────────

    /**
     * Apply (or remove) a heading to the line containing the cursor.
     *
     * - [level] 1 → `<h1>`, 2 → `<h2>`, 0 → removes any existing heading.
     * - If the line already has the requested heading, it is removed (toggle off).
     */
    fun applyHeading(value: TextFieldValue, level: Int): TextFieldValue {
        val text     = value.text
        val cursor   = value.selection.start.coerceIn(0, text.length)
        val lineStart = text.lastIndexOf('\n', cursor - 1) + 1   // 0 if no newline found
        val lineEnd   = text.indexOf('\n', cursor).let { if (it == -1) text.length else it }
        val line      = text.substring(lineStart, lineEnd)

        val existingHeadingRegex = Regex("^<h([12])>(.*)</h([12])>$", RegexOption.DOT_MATCHES_ALL)
        val match = existingHeadingRegex.matchEntire(line.trim())

        val newLine: String = when {
            // Toggle off: same level already applied
            match != null && match.groupValues[1] == level.toString() -> match.groupValues[2]
            // Apply heading (replacing any existing heading)
            level in 1..2 -> {
                val plainContent = if (match != null) match.groupValues[2] else line
                "<h$level>$plainContent</h$level>"
            }
            // level == 0 → strip heading
            match != null -> match.groupValues[2]
            else          -> line
        }

        val newText = text.substring(0, lineStart) + newLine + text.substring(lineEnd)
        // Keep cursor inside the new content
        val newCursor = (lineStart + newLine.length).coerceAtMost(newText.length)
        return value.copy(text = newText, selection = TextRange(newCursor))
    }

    // ─── Lists ────────────────────────────────────────────────────────────────

    /**
     * Toggle unordered list formatting for the current line.
     *
     * - If the line already has a `<li>` wrapper (inside `<ul>`), strip it.
     * - Otherwise wrap the line content in `<ul><li>...</li></ul>`.
     */
    fun toggleBulletList(value: TextFieldValue): TextFieldValue {
        val text   = value.text
        val cursor = value.selection.start.coerceIn(0, text.length)
        val lineStart = text.lastIndexOf('\n', cursor - 1) + 1
        val lineEnd   = text.indexOf('\n', cursor).let { if (it == -1) text.length else it }
        val line      = text.substring(lineStart, lineEnd)

        val ulRegex = Regex("""^<ul><li>(.*)</li></ul>$""")
        val bulletPrefix = Regex("""^•\s?""")
        val newLine = when {
            ulRegex.matches(line) -> ulRegex.find(line)!!.groupValues[1]
            bulletPrefix.containsMatchIn(line) -> line.replace(bulletPrefix, "")
            else -> "<ul><li>$line</li></ul>"
        }
        val newText = text.substring(0, lineStart) + newLine + text.substring(lineEnd)
        val newCursor = (lineStart + newLine.length).coerceAtMost(newText.length)
        return value.copy(text = newText, selection = TextRange(newCursor))
    }

    /**
     * Toggle ordered (numbered) list formatting for the current line.
     *
     * - If the line already has a `<li>` wrapper (inside `<ol>`), strip it.
     * - Otherwise wrap the line content in `<ol><li>...</li></ol>`.
     */
    fun toggleNumberedList(value: TextFieldValue): TextFieldValue {
        val text   = value.text
        val cursor = value.selection.start.coerceIn(0, text.length)
        val lineStart = text.lastIndexOf('\n', cursor - 1) + 1
        val lineEnd   = text.indexOf('\n', cursor).let { if (it == -1) text.length else it }
        val line      = text.substring(lineStart, lineEnd)

        val olRegex = Regex("""^<ol><li>(.*)</li></ol>$""")
        val numPrefix = Regex("""^\d+\.\s?""")
        val newLine = when {
            olRegex.matches(line) -> olRegex.find(line)!!.groupValues[1]
            numPrefix.containsMatchIn(line) -> line.replace(numPrefix, "")
            else -> "<ol><li>$line</li></ol>"
        }
        val newText = text.substring(0, lineStart) + newLine + text.substring(lineEnd)
        val newCursor = (lineStart + newLine.length).coerceAtMost(newText.length)
        return value.copy(text = newText, selection = TextRange(newCursor))
    }

    /** Insert an unchecked checkbox (`<input type="checkbox">`) at the cursor. */
    fun insertCheckbox(value: TextFieldValue, checked: Boolean = false): TextFieldValue {
        val marker  = if (checked) "<input type=\"checkbox\" checked>" else "<input type=\"checkbox\">"
        val newText = value.text.substring(0, value.selection.start) +
                      marker + " " +
                      value.text.substring(value.selection.start)
        return value.copy(
            text      = newText,
            selection = TextRange(value.selection.start + marker.length + 1)
        )
    }

    // ─── Links ────────────────────────────────────────────────────────────────

    /**
     * Insert a hyperlink at the cursor.
     *
     * - If [displayText] is provided (or text was selected), uses it as link label.
     * - Otherwise uses the URL itself as label.
     * - Ensures the URL starts with `"https://"` if it has no scheme.
     */
    fun insertLink(
        value: TextFieldValue,
        url: String,
        displayText: String?
    ): TextFieldValue {
        val safeUrl  = if (url.startsWith("http://") || url.startsWith("https://")) url else "https://$url"
        val label    = displayText?.takeIf { it.isNotBlank() } ?: safeUrl
        val linkHtml = "<a href=\"$safeUrl\">$label</a>"

        val sel     = value.selection
        val before  = value.text.substring(0, sel.start)
        val after   = value.text.substring(sel.end)   // replaces any selected text
        val newText = before + linkHtml + after
        return value.copy(
            text      = newText,
            selection = TextRange(before.length + linkHtml.length)
        )
    }

    // ─── Code block ───────────────────────────────────────────────────────────

    /** Insert a `<pre><code>` block wrapping the selection (or at cursor). */
    fun insertCodeBlock(value: TextFieldValue): TextFieldValue {
        val sel      = value.selection
        val selected = value.text.substring(sel.start, sel.end)
        val block    = "\n<pre><code>$selected</code></pre>\n"
        val before   = value.text.substring(0, sel.start)
        val after    = value.text.substring(sel.end)
        val newText  = before + block + after
        return value.copy(
            text      = newText,
            selection = TextRange(before.length + block.length)
        )
    }

    // ─── Internal helpers ─────────────────────────────────────────────────────

    /**
     * Toggle a text prefix on/off for the line containing the cursor.
     * Used by [toggleBulletList] and [toggleNumberedList].
     */
    private fun toggleLinePrefix(value: TextFieldValue, prefix: String): TextFieldValue {
        val text      = value.text
        val cursor    = value.selection.start.coerceIn(0, text.length)
        val lineStart = text.lastIndexOf('\n', cursor - 1) + 1
        val lineEnd   = text.indexOf('\n', cursor).let { if (it == -1) text.length else it }
        val line      = text.substring(lineStart, lineEnd)

        val (newLine, cursorDelta) = if (line.startsWith(prefix)) {
            Pair(line.removePrefix(prefix), -prefix.length)
        } else {
            Pair(prefix + line, prefix.length)
        }

        val newText      = text.substring(0, lineStart) + newLine + text.substring(lineEnd)
        val newCursorPos = (cursor + cursorDelta).coerceIn(0, newText.length)
        return value.copy(text = newText, selection = TextRange(newCursorPos))
    }
}

