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
     * - **No selection (collapsed cursor) & already inside tag:** splits the tag at cursor
     *   so text before cursor stays formatted, text after cursor loses formatting.
     * - **No selection (collapsed cursor) & not inside tag:** inserts `<tag></tag>` and places cursor between.
     * - **Selection already wrapped:** unwraps — removes the surrounding tags.
     * - **New selection:** wraps the selected text with `<tag>…</tag>`.
     */
    fun toggleInlineTag(value: TextFieldValue, tag: String): TextFieldValue {
        val openTag  = "<$tag>"
        val closeTag = "</$tag>"
        val sel  = value.selection
        val text = value.text

        if (sel.collapsed) {
            // Check if cursor is currently inside an existing <tag>...</tag> pair
            val before = text.substring(0, sel.start)
            val after  = text.substring(sel.start)

            // Use regex for exact tag matching (avoids <li> matching <i>)
            val openRegex = Regex("<$tag(\\s[^>]*)?>", RegexOption.IGNORE_CASE)
            val closeRegex = Regex("</$tag>", RegexOption.IGNORE_CASE)

            val openMatches = openRegex.findAll(before).toList()
            if (openMatches.isNotEmpty()) {
                val lastOpen = openMatches.last()
                val afterOpenTag = lastOpen.range.last + 1
                // Check there's no close tag between open tag and cursor
                val closeInBetween = closeRegex.find(before, startIndex = afterOpenTag)
                if (closeInBetween == null) {
                    // We ARE inside the tag — find the close tag after cursor
                    val closeAfterCursor = closeRegex.find(after)
                    if (closeAfterCursor != null) {
                        // FIX BUG 1: Instead of removing the whole tag pair, SPLIT at cursor.
                        // Text before cursor keeps formatting, text after cursor loses it.
                        val contentBefore = before.substring(afterOpenTag)
                        val contentAfter = after.substring(0, closeAfterCursor.range.first)
                        val remainingAfter = after.substring(closeAfterCursor.range.last + 1)

                        if (contentBefore.isEmpty() && contentAfter.isEmpty()) {
                            // Empty tags — just remove both tags entirely
                            val beforeWithoutOpen = before.substring(0, lastOpen.range.first)
                            val newText = beforeWithoutOpen + remainingAfter
                            return value.copy(text = newText, selection = TextRange(beforeWithoutOpen.length))
                        } else if (contentBefore.isEmpty()) {
                            // Cursor is right after open tag — remove open tag, keep content unformatted
                            val beforeWithoutOpen = before.substring(0, lastOpen.range.first)
                            val newText = beforeWithoutOpen + contentAfter + remainingAfter
                            return value.copy(text = newText, selection = TextRange(beforeWithoutOpen.length))
                        } else if (contentAfter.isEmpty()) {
                            // Cursor is right before close tag — close tag here, done
                            val beforeOpen = before.substring(0, lastOpen.range.first)
                            val newText = beforeOpen + openTag + contentBefore + closeTag + remainingAfter
                            return value.copy(text = newText, selection = TextRange(beforeOpen.length + openTag.length + contentBefore.length + closeTag.length))
                        } else {
                            // Split: close tag before cursor, leave after-content unformatted
                            val beforeOpen = before.substring(0, lastOpen.range.first)
                            val newText = beforeOpen + openTag + contentBefore + closeTag + contentAfter + remainingAfter
                            val newCursor = beforeOpen.length + openTag.length + contentBefore.length + closeTag.length
                            return value.copy(text = newText, selection = TextRange(newCursor))
                        }
                    }
                }
            }

            // Not inside existing tag — insert empty tag pair
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

        // Check if selection is fully inside existing tags — unwrap those tags
        val openRegex = Regex("<$tag(\\s[^>]*)?>", RegexOption.IGNORE_CASE)
        val closeRegex = Regex("</$tag>", RegexOption.IGNORE_CASE)
        val openMatches = openRegex.findAll(before).toList()
        if (openMatches.isNotEmpty()) {
            val lastOpen = openMatches.last()
            val afterOpenTag = lastOpen.range.last + 1
            val closeInBetween = closeRegex.find(before, startIndex = afterOpenTag)
            if (closeInBetween == null) {
                val closeAfterSelection = closeRegex.find(after)
                if (closeAfterSelection != null) {
                    // Remove surrounding tags
                    val afterWithoutClose = after.removeRange(
                        closeAfterSelection.range.first,
                        closeAfterSelection.range.last + 1
                    )
                    val beforeWithoutOpen = before.removeRange(
                        lastOpen.range.first,
                        lastOpen.range.last + 1
                    )
                    val selected = text.substring(sel.start, sel.end)
                    val newText = beforeWithoutOpen + selected + afterWithoutClose
                    val openLen = lastOpen.range.last - lastOpen.range.first + 1
                    return value.copy(
                        text = newText,
                        selection = TextRange(sel.start - openLen, sel.end - openLen)
                    )
                }
            }
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
     *
     * Robust: works regardless of where cursor is on the line (inside tags, at start/end).
     */
    fun applyHeading(value: TextFieldValue, level: Int): TextFieldValue {
        val text   = value.text
        val cursor = value.selection.start.coerceIn(0, text.length)

        // Find line boundaries — search backwards and forwards for newline
        val lineStart = if (cursor == 0) 0 else (text.lastIndexOf('\n', cursor - 1) + 1)
        val lineEnd = text.indexOf('\n', cursor).let { if (it == -1) text.length else it }

        // Also check: if cursor is exactly at a \n position, we want the line BEFORE it
        // (unless cursor is at position 0)
        val line = text.substring(lineStart, lineEnd)

        // Try to match heading tags anywhere in the line
        val existingHeadingRegex = Regex("""<h([12])>(.*?)</h\1>""", RegexOption.DOT_MATCHES_ALL)
        val match = existingHeadingRegex.find(line)

        val newLine: String = when {
            // Toggle off: same level already applied
            match != null && match.groupValues[1] == level.toString() -> {
                // Remove heading tags, keep content
                line.substring(0, match.range.first) +
                        match.groupValues[2] +
                        line.substring(match.range.last + 1)
            }
            // Apply heading (replacing any existing heading of different level)
            level in 1..2 -> {
                if (match != null) {
                    // Replace existing heading with new level
                    line.substring(0, match.range.first) +
                            "<h$level>${match.groupValues[2]}</h$level>" +
                            line.substring(match.range.last + 1)
                } else {
                    // Wrap entire line content
                    "<h$level>$line</h$level>"
                }
            }
            // level == 0 → strip heading (Normal)
            match != null -> {
                line.substring(0, match.range.first) +
                        match.groupValues[2] +
                        line.substring(match.range.last + 1)
            }
            else -> line
        }

        val newText = text.substring(0, lineStart) + newLine + text.substring(lineEnd)
        // Place cursor at a sensible position within the new content
        val newCursor = when {
            level in 1..2 && match == null -> {
                // Just wrapped: cursor at end of content (before closing tag)
                (lineStart + "<h$level>".length + line.length).coerceAtMost(newText.length)
            }
            else -> {
                // Keep cursor relative position sensible — place at end of new line
                (lineStart + newLine.length).coerceAtMost(newText.length)
            }
        }
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

    // ─── Smart text change handler ──────────────────────────────────────────────

    /**
     * Master handler for all text changes. Call this from onValueChange.
     * Handles:
     * 1. Auto-continue lists/checkboxes on Enter
     * 2. Smart deletion — removes entire HTML tags on backspace instead of
     *    letting the user see partially-broken tags
     */
    fun handleTextChange(oldValue: TextFieldValue, newValue: TextFieldValue): TextFieldValue {
        val oldText = oldValue.text
        val newText = newValue.text
        val diff = newText.length - oldText.length

        // --- ENTER / NEWLINE handling ---
        if (diff > 0) {
            val insertPos = newValue.selection.start - 1
            if (insertPos >= 0 && newText[insertPos] == '\n') {
                val result = handleNewLineInternal(newValue, insertPos)
                if (result != null) return result
            }
        }

        // --- DELETION handling ---
        if (diff < 0) {
            val result = handleDeletion(oldValue, newValue)
            if (result != null) return result
        }

        return newValue
    }

    /**
     * Handles newline auto-continuation for lists and checkboxes.
     * Detects what was on the line BEFORE the newline and continues the pattern.
     *
     * Handles two scenarios:
     * A) Enter pressed at end of line: "…</li></ul>\n" → prev line matches full pattern
     * B) Enter pressed inside tags: "<ul><li>content\n</li></ul>" → prev line is partial,
     *    we need to fix the split and re-wrap both lines properly.
     */
    private fun handleNewLineInternal(newValue: TextFieldValue, insertPos: Int): TextFieldValue? {
        val text = newValue.text
        val prevLineEnd = insertPos
        val prevLineStart = text.lastIndexOf('\n', prevLineEnd - 1) + 1
        val prevLine = text.substring(prevLineStart, prevLineEnd)

        // After the newline
        val nextLineStart = insertPos + 1
        val nextLineEnd = text.indexOf('\n', nextLineStart).let { if (it == -1) text.length else it }
        val nextLine = if (nextLineStart <= text.length) text.substring(nextLineStart, nextLineEnd) else ""

        val ulRegex = Regex("""^<ul><li>(.*)</li></ul>$""")
        val olRegex = Regex("""^<ol><li>(.*)</li></ol>$""")
        val checkboxRegex = Regex("""^<input type="checkbox"[^>]*>\s?(.*)$""")

        // ─── Scenario A: prev line is a complete list/checkbox pattern ───
        val ulMatch = ulRegex.find(prevLine)
        val olMatch = olRegex.find(prevLine)
        val cbMatch = checkboxRegex.find(prevLine)

        when {
            ulMatch != null -> {
                // Persist bullet mode — always create a new bullet on Enter.
                // User must toggle the bullet button off to exit.
                val continuation = "<ul><li></li></ul>"
                val cursorInside = "<ul><li>".length
                val before = text.substring(0, insertPos + 1)
                val after = text.substring(insertPos + 1)
                val newText = before + continuation + after
                return newValue.copy(text = newText, selection = TextRange(insertPos + 1 + cursorInside))
            }
            olMatch != null -> {
                // Persist numbered list mode — always create a new item on Enter.
                // User must toggle the numbered list button off to exit.
                val continuation = "<ol><li></li></ol>"
                val cursorInside = "<ol><li>".length
                val before = text.substring(0, insertPos + 1)
                val after = text.substring(insertPos + 1)
                val newText = before + continuation + after
                return newValue.copy(text = newText, selection = TextRange(insertPos + 1 + cursorInside))
            }
            cbMatch != null -> {
                // Persist checkbox mode — always create a new checkbox on Enter.
                // User must toggle the checkbox button off to exit.
                val continuation = "<input type=\"checkbox\"> "
                val before = text.substring(0, insertPos + 1)
                val after = text.substring(insertPos + 1)
                val newText = before + continuation + after
                return newValue.copy(text = newText, selection = TextRange(insertPos + 1 + continuation.length))
            }
        }

        // ─── Scenario B: Enter was pressed INSIDE a tag structure ───
        // When VisualTransformation maps cursor to content area inside tags,
        // Enter splits the raw text. Examples:
        //   "<ul><li>content" + newline + "</li></ul>"
        //   "<ol><li>content" + newline + "</li></ol>"
        //   "<h1>content" + newline + "</h1>"

        // UL list split detection
        if (prevLine.startsWith("<ul><li>")) {
            val closingIdx = nextLine.indexOf("</li></ul>")
            if (closingIdx != -1) {
                val contentBefore = prevLine.substring("<ul><li>".length)
                val contentAfter = nextLine.substring(0, closingIdx)
                val afterClosing = nextLine.substring(closingIdx + "</li></ul>".length)


                val rebuiltText = text.substring(0, prevLineStart) +
                        "<ul><li>$contentBefore</li></ul>" +
                        "\n<ul><li>$contentAfter</li></ul>" +
                        afterClosing +
                        (if (nextLineEnd < text.length) text.substring(nextLineEnd) else "")
                val cursorPos = prevLineStart + "<ul><li>$contentBefore</li></ul>\n<ul><li>".length
                return newValue.copy(text = rebuiltText, selection = TextRange(cursorPos))
            }
        }

        // OL list split detection
        if (prevLine.startsWith("<ol><li>")) {
            val closingIdx = nextLine.indexOf("</li></ol>")
            if (closingIdx != -1) {
                val contentBefore = prevLine.substring("<ol><li>".length)
                val contentAfter = nextLine.substring(0, closingIdx)
                val afterClosing = nextLine.substring(closingIdx + "</li></ol>".length)


                val rebuiltText = text.substring(0, prevLineStart) +
                        "<ol><li>$contentBefore</li></ol>" +
                        "\n<ol><li>$contentAfter</li></ol>" +
                        afterClosing +
                        (if (nextLineEnd < text.length) text.substring(nextLineEnd) else "")
                val cursorPos = prevLineStart + "<ol><li>$contentBefore</li></ol>\n<ol><li>".length
                return newValue.copy(text = rebuiltText, selection = TextRange(cursorPos))
            }
        }

        // Heading split detection — when Enter is pressed inside <h1>content</h1>
        // producing "<h1>content" + "\n" + "</h1>", close the heading and start a normal line,
        // or split the heading. Usually, Enter in a heading starts a normal paragraph.
        val headingSplitRegex = Regex("""^<h([12])>(.*)$""")
        val headingSplitMatch = headingSplitRegex.find(prevLine)

        if (headingSplitMatch != null) {
            val headingLevel = headingSplitMatch.groupValues[1]
            val contentBefore = headingSplitMatch.groupValues[2]
            
            val closingIdx = nextLine.indexOf("</h$headingLevel>")
            if (closingIdx != -1) {
                val contentAfter = nextLine.substring(0, closingIdx)
                val afterClosing = nextLine.substring(closingIdx + "</h$headingLevel>".length)
                
                // Usually, pressing Enter in a heading ends the heading and starts normal text
                // So we close the heading with contentBefore, and start a normal line with contentAfter
                val rebuiltText = text.substring(0, prevLineStart) +
                        "<h$headingLevel>$contentBefore</h$headingLevel>" +
                        "\n" +
                        contentAfter + afterClosing +
                        (if (nextLineEnd < text.length) text.substring(nextLineEnd) else "")
                val cursorPos = prevLineStart + "<h$headingLevel>$contentBefore</h$headingLevel>\n".length
                return newValue.copy(text = rebuiltText, selection = TextRange(cursorPos))
            }
        }

        return null
    }

    /**
     * Smart deletion handler.
     *
     * When the user presses backspace and removes character(s) that are part of
     * an HTML tag, this function detects the broken state and removes the entire
     * tag (or the whole formatted block) cleanly — preventing partial HTML tags
     * from being shown to the user.
     */
    private fun handleDeletion(oldValue: TextFieldValue, newValue: TextFieldValue): TextFieldValue? {
        val oldText = oldValue.text
        val newText = newValue.text
        val cursorPos = newValue.selection.start
        val deletedCount = oldText.length - newText.length

        // Only handle single-char deletions (backspace / delete key)
        if (deletedCount != 1) return null

        // Determine position of deleted char in oldText
        val deletedPos = if (oldValue.selection.start > cursorPos) {
            // Backspace
            cursorPos
        } else {
            // Forward delete
            oldValue.selection.start
        }

        // Check if the deleted character was part of an HTML tag in the old text
        // If the deleted char is '<' or '>' or was between them, the tag is now broken
        // Find if deletedPos was inside a tag in oldText
        if (isInsideTag(oldText, deletedPos)) {
            // Find the full tag boundaries in oldText
            val tagStart = oldText.lastIndexOf('<', deletedPos)
            val tagEnd = oldText.indexOf('>', deletedPos)

            if (tagStart >= 0 && tagEnd >= 0) {
                val fullTag = oldText.substring(tagStart, tagEnd + 1)

                // Determine what kind of tag and handle accordingly
                return handleTagDeletion(oldText, tagStart, tagEnd, fullTag, cursorPos, newText)
            } else if (tagStart >= 0 && tagEnd == -1) {
                // The '>' was what got deleted — remove the whole broken tag
                // In newText, the tag starts at tagStart, but '>' is gone
                // Find where the tag fragment ends in newText
                val fragEnd = findTagFragmentEnd(newText, tagStart)
                val cleanedText = newText.substring(0, tagStart) + newText.substring(fragEnd)
                return TextFieldValue(
                    text = cleanedText,
                    selection = TextRange(tagStart.coerceAtMost(cleanedText.length))
                )
            }
        }

        // Check if deletion resulted in a broken tag in the new text
        val brokenResult = fixBrokenTagsAroundCursor(newText, cursorPos)
        if (brokenResult != null) return brokenResult

        return null
    }

    /**
     * Checks if a position in the text is inside an HTML tag (between < and >).
     */
    private fun isInsideTag(text: String, pos: Int): Boolean {
        // The char at pos could be '<', '>', or between them
        if (text[pos] == '<' || text[pos] == '>') return true

        // Look backwards for '<' without hitting '>'
        var i = pos - 1
        while (i >= 0) {
            if (text[i] == '>') return false  // hit a close of a different tag
            if (text[i] == '<') return true   // found opening bracket — we're inside
            i--
        }
        return false
    }

    /**
     * Handle deletion of a character that was part of an HTML tag.
     * Removes the entire tag and its matching counterpart if applicable.
     */
    private fun handleTagDeletion(
        oldText: String,
        tagStart: Int,
        tagEnd: Int,
        fullTag: String,
        cursorPos: Int,
        newValueText: String
    ): TextFieldValue {
        val tagContent = fullTag.substring(1, fullTag.length - 1).trim()
        val isClosing = tagContent.startsWith("/")
        val tagName = if (isClosing) {
            tagContent.substring(1).split("\\s".toRegex()).firstOrNull()?.lowercase() ?: ""
        } else {
            tagContent.split("\\s".toRegex()).firstOrNull()?.lowercase() ?: ""
        }

        // For checkbox input tag — just remove the whole checkbox
        if (tagName == "input" && fullTag.contains("checkbox", ignoreCase = true)) {
            // Remove the checkbox tag and any trailing space
            var removeEnd = tagEnd + 1
            if (removeEnd < oldText.length && oldText[removeEnd] == ' ') removeEnd++
            val newText = oldText.substring(0, tagStart) + oldText.substring(removeEnd)
            return TextFieldValue(
                text = newText,
                selection = TextRange(tagStart.coerceAtMost(newText.length))
            )
        }

        // For list container tags <ul>, <ol>, </ul>, </ol>
        if (tagName == "ul" || tagName == "ol") {
            // Remove the complete list wrapper from the line
            val lineStart = oldText.lastIndexOf('\n', tagStart - 1) + 1
            val lineEnd = oldText.indexOf('\n', tagEnd).let { if (it == -1) oldText.length else it }
            val line = oldText.substring(lineStart, lineEnd)

            val listRegex = Regex("""<(ul|ol)><li>(.*)</li></(ul|ol)>""")
            val match = listRegex.find(line)
            if (match != null) {
                // Extract just the content, remove list wrapping
                val content = match.groupValues[2]
                val newText = oldText.substring(0, lineStart) + content + oldText.substring(lineEnd)
                return TextFieldValue(
                    text = newText,
                    selection = TextRange((lineStart + content.length).coerceAtMost(newText.length))
                )
            }
        }

        // For <li> or </li> — also remove the list wrapper
        if (tagName == "li") {
            val lineStart = oldText.lastIndexOf('\n', tagStart - 1) + 1
            val lineEnd = oldText.indexOf('\n', tagEnd).let { if (it == -1) oldText.length else it }
            val line = oldText.substring(lineStart, lineEnd)

            val listRegex = Regex("""<(ul|ol)><li>(.*)</li></(ul|ol)>""")
            val match = listRegex.find(line)
            if (match != null) {
                val content = match.groupValues[2]
                val newText = oldText.substring(0, lineStart) + content + oldText.substring(lineEnd)
                return TextFieldValue(
                    text = newText,
                    selection = TextRange((lineStart + content.length).coerceAtMost(newText.length))
                )
            }
        }

        // For inline tags like <b>, </b>, <i>, </i>, <u>, </u>, <code>, </code>
        if (tagName in listOf("b", "i", "u", "code", "strong", "em", "s", "del", "strike")) {
            if (isClosing) {
                // Closing tag deleted — find matching open tag and remove both
                val openTagRegex = Regex("<$tagName(\\s[^>]*)?>", RegexOption.IGNORE_CASE)
                val beforeTag = oldText.substring(0, tagStart)
                val matchResult = openTagRegex.findAll(beforeTag).lastOrNull()
                if (matchResult != null) {
                    // Remove both open and close tags, keep content
                    val content = oldText.substring(matchResult.range.last + 1, tagStart)
                    val newText = oldText.substring(0, matchResult.range.first) +
                            content +
                            oldText.substring(tagEnd + 1)
                    val newCursor = matchResult.range.first + content.length
                    return TextFieldValue(
                        text = newText,
                        selection = TextRange(newCursor.coerceAtMost(newText.length))
                    )
                }
            } else {
                // Opening tag deleted — find matching close tag and remove both
                val closeTagStr = "</$tagName>"
                val afterTag = oldText.substring(tagEnd + 1)
                val closeIdx = afterTag.indexOf(closeTagStr, ignoreCase = true)
                if (closeIdx >= 0) {
                    val absCloseStart = tagEnd + 1 + closeIdx
                    val absCloseEnd = absCloseStart + closeTagStr.length
                    // Remove both tags, keep content
                    val content = oldText.substring(tagEnd + 1, absCloseStart)
                    val newText = oldText.substring(0, tagStart) +
                            content +
                            oldText.substring(absCloseEnd)
                    return TextFieldValue(
                        text = newText,
                        selection = TextRange(tagStart.coerceAtMost(newText.length))
                    )
                }
            }
        }

        // For heading tags <h1>, <h2>, </h1>, </h2>
        if (tagName in listOf("h1", "h2")) {
            val lineStart = oldText.lastIndexOf('\n', tagStart - 1) + 1
            val lineEnd = oldText.indexOf('\n', tagEnd).let { if (it == -1) oldText.length else it }
            val line = oldText.substring(lineStart, lineEnd)

            val headingRegex = Regex("""<h([12])>(.*)</h\1>""")
            val match = headingRegex.find(line)
            if (match != null) {
                val content = match.groupValues[2]
                val newText = oldText.substring(0, lineStart) + content + oldText.substring(lineEnd)
                return TextFieldValue(
                    text = newText,
                    selection = TextRange((lineStart + content.length).coerceAtMost(newText.length))
                )
            }
        }

        // For <a>, </a> — remove the link but keep the label text
        if (tagName == "a") {
            val lineStart = oldText.lastIndexOf('\n', tagStart - 1) + 1
            val lineEnd = oldText.indexOf('\n', tagEnd).let { if (it == -1) oldText.length else it }
            val line = oldText.substring(lineStart, lineEnd)

            val linkRegex = Regex("""<a\s[^>]*>(.*?)</a>""", RegexOption.IGNORE_CASE)
            val match = linkRegex.find(line)
            if (match != null) {
                val label = match.groupValues[1]
                val absMatchStart = lineStart + match.range.first
                val absMatchEnd = lineStart + match.range.last + 1
                val newText = oldText.substring(0, absMatchStart) + label + oldText.substring(absMatchEnd)
                return TextFieldValue(
                    text = newText,
                    selection = TextRange((absMatchStart + label.length).coerceAtMost(newText.length))
                )
            }
        }

        // For <pre>, <code> blocks
        if (tagName == "pre") {
            val preRegex = Regex("""<pre><code>(.*?)</code></pre>""", RegexOption.DOT_MATCHES_ALL)
            val match = preRegex.find(oldText)
            if (match != null && tagStart >= match.range.first && tagStart <= match.range.last) {
                val content = match.groupValues[1]
                val newText = oldText.substring(0, match.range.first) +
                        content +
                        oldText.substring(match.range.last + 1)
                return TextFieldValue(
                    text = newText,
                    selection = TextRange((match.range.first + content.length).coerceAtMost(newText.length))
                )
            }
        }

        // Fallback: just remove the entire broken tag from newText
        // Map tagStart position to newText (1 char was already deleted)
        val newTagStart = if (tagStart >= cursorPos) tagStart - 1 else tagStart
        val newTagEnd = (tagEnd - 1).coerceAtMost(newValueText.length)
        // Find the broken tag fragment in newText and remove it
        return fixBrokenTagsAroundCursor(newValueText, cursorPos)
            ?: TextFieldValue(text = newValueText, selection = TextRange(cursorPos))
    }

    /**
     * Finds the end of a tag fragment (a `<` without matching `>`).
     */
    private fun findTagFragmentEnd(text: String, tagStart: Int): Int {
        var i = tagStart + 1
        while (i < text.length) {
            if (text[i] == '<' || text[i] == '\n') return i
            if (text[i] == '>') return i + 1
            i++
        }
        return i
    }

    /**
     * Scans around the cursor for broken/partial HTML tag fragments and removes them.
     */
    private fun fixBrokenTagsAroundCursor(text: String, cursorPos: Int): TextFieldValue? {
        // Look backwards from cursor for an unmatched '<'
        var openBracket = -1
        for (i in (cursorPos - 1).coerceAtLeast(0) downTo (cursorPos - 60).coerceAtLeast(0)) {
            if (text[i] == '>') break
            if (text[i] == '<') {
                openBracket = i
                break
            }
        }

        if (openBracket != -1) {
            // Found '<' before cursor without '>' in between — check if tag is broken
            var closeBracket = -1
            for (i in cursorPos until (cursorPos + 60).coerceAtMost(text.length)) {
                if (text[i] == '<') break
                if (text[i] == '>') {
                    closeBracket = i
                    break
                }
            }

            if (closeBracket == -1) {
                // No matching '>' — broken tag fragment. Remove it.
                var fragEnd = cursorPos
                for (i in cursorPos until text.length) {
                    if (text[i] == '<' || text[i] == '\n') break
                    fragEnd = i + 1
                }
                val newText = text.substring(0, openBracket) + text.substring(fragEnd)
                return TextFieldValue(
                    text = newText,
                    selection = TextRange(openBracket.coerceAtMost(newText.length))
                )
            }
            // If we found both '<' before cursor and '>' after — the tag may have valid structure
            // but with missing content. Check if it forms a valid tag:
            val possibleTag = text.substring(openBracket, closeBracket + 1)
            val validTagRegex = Regex("""^</?[a-zA-Z][a-zA-Z0-9]*(\s[^>]*)?>$""")
            if (!validTagRegex.matches(possibleTag)) {
                // Invalid tag — remove it
                val newText = text.substring(0, openBracket) + text.substring(closeBracket + 1)
                return TextFieldValue(
                    text = newText,
                    selection = TextRange(openBracket.coerceAtMost(newText.length))
                )
            }
        }

        // Check for orphaned '>' after cursor with no '<' before it
        for (i in cursorPos until (cursorPos + 10).coerceAtMost(text.length)) {
            if (text[i] == '<') break
            if (text[i] == '>') {
                // Check no '<' between cursor and this '>'
                var hasOpen = false
                for (j in cursorPos until i) {
                    if (text[j] == '<') { hasOpen = true; break }
                }
                if (!hasOpen) {
                    val newText = text.substring(0, cursorPos) + text.substring(i + 1)
                    return TextFieldValue(
                        text = newText,
                        selection = TextRange(cursorPos.coerceAtMost(newText.length))
                    )
                }
                break
            }
        }

        return null
    }

    /**
     * Legacy name — delegates to handleTextChange.
     * Kept for backward compatibility.
     */
    fun handleNewLine(oldValue: TextFieldValue, newValue: TextFieldValue): TextFieldValue {
        return handleTextChange(oldValue, newValue)
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

