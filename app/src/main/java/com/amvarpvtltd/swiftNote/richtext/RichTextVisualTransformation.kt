package com.amvarpvtltd.swiftNote.richtext

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp

/**
 * A [VisualTransformation] that renders HTML tags as styled text instead of
 * showing the raw tags to the user.
 *
 * Supported tags: <b>, <i>, <u>, <code>, <h1>, <h2>, <pre>, <s>,
 * <ul><li>...</li></ul>, <ol><li>...</li></ol>, <a href="...">...</a>,
 * <input type="checkbox" [checked]>
 *
 * The underlying [TextFieldValue.text] still contains raw HTML — this transformation
 * only affects what is *displayed* in the text field. Cursor positioning is mapped
 * correctly between the raw and visual representations.
 */
class RichTextVisualTransformation(
    private val linkColor: Color = Color(0xFF6366F1)
) : VisualTransformation {

    // Regex to match HTML tags
    private val tagRegex = Regex("""</?[a-zA-Z][a-zA-Z0-9]*\b[^>]*>""")

    override fun filter(text: AnnotatedString): TransformedText {
        val rawText = text.text
        if (!rawText.contains('<')) {
            // No HTML at all — pass through unchanged
            return TransformedText(text, OffsetMapping.Identity)
        }

        val result = parseAndTransform(rawText)
        return TransformedText(result.annotatedString, result.offsetMapping)
    }

    private fun parseAndTransform(raw: String): ParseResult {
        // PRE-PASS: compute the displayed number for each <ol><li> line,
        // grouping contiguous <ol> lines as one logical numbered list.
        val olLineNumbers = computeOrderedListNumbers(raw)

        // Build the visual string by walking through the raw text and removing tags
        val visualBuilder = StringBuilder()
        val spans = mutableListOf<SpanRange>()
        // Maps: rawOffset -> visualOffset for every character position
        val rawToVisual = IntArray(raw.length + 1)
        val visualToRaw = mutableListOf<Int>()

        // Track open tags using a stack
        val tagStack = mutableListOf<OpenTag>()


        var i = 0
        while (i < raw.length) {
            if (raw[i] == '<') {
                val tagEnd = raw.indexOf('>', i)
                if (tagEnd == -1) {
                    // Not a valid tag, treat as text
                    rawToVisual[i] = visualBuilder.length
                    visualToRaw.add(i)
                    visualBuilder.append(raw[i])
                    i++
                    continue
                }

                val fullTag = raw.substring(i, tagEnd + 1)
                val tagContent = fullTag.substring(1, fullTag.length - 1).trim()

                // Determine tag type
                val isClosing = tagContent.startsWith("/")
                val tagName = if (isClosing) {
                    tagContent.substring(1).split("\\s".toRegex()).firstOrNull()?.lowercase() ?: ""
                } else {
                    tagContent.split("\\s".toRegex()).firstOrNull()?.lowercase() ?: ""
                }

                // Handle self-closing input (checkbox)
                if (tagName == "input" && fullTag.contains("checkbox", ignoreCase = true)) {
                    val isChecked = fullTag.contains("checked", ignoreCase = true)
                    val symbol = if (isChecked) "☑ " else "☐ "
                    // Map all raw chars in the tag to the visual symbol position
                    for (j in i..tagEnd) {
                        rawToVisual[j] = visualBuilder.length
                    }
                    for (ch in symbol) {
                        visualToRaw.add(i)
                        visualBuilder.append(ch)
                    }
                    i = tagEnd + 1
                    continue
                }

                // Handle <br> and <br/>
                if (tagName == "br") {
                    for (j in i..tagEnd) {
                        rawToVisual[j] = visualBuilder.length
                    }
                    visualToRaw.add(i)
                    visualBuilder.append('\n')
                    i = tagEnd + 1
                    continue
                }

                if (isClosing) {
                    // Closing tag: find matching open tag, record span
                    val openIdx = tagStack.indexOfLast { it.tagName == tagName }
                    if (openIdx >= 0) {
                        val openTag = tagStack.removeAt(openIdx)
                        val spanStyle = getSpanStyleForTag(tagName, openTag.attributes)
                        if (spanStyle != null) {
                            spans.add(SpanRange(spanStyle, openTag.visualStart, visualBuilder.length))
                        }
                    }
                    // Skip all chars in the closing tag
                    for (j in i..tagEnd) {
                        rawToVisual[j] = visualBuilder.length
                    }
                    i = tagEnd + 1
                } else {
                    // Opening tag: push to stack
                    // Extract attributes for <a href="...">
                    val attributes = extractAttributes(fullTag)

                    // For list wrappers <ul>, <ol> — skip visually but push to stack
                    if (tagName == "ul" || tagName == "ol") {
                        for (j in i..tagEnd) {
                            rawToVisual[j] = visualBuilder.length
                        }
                        // Attach the precomputed number to the OpenTag so <li> can read it.
                        val attrsWithNumber = if (tagName == "ol") {
                            attributes + ("_displayNumber" to (olLineNumbers[i] ?: 1).toString())
                        } else attributes
                        tagStack.add(OpenTag(tagName, visualBuilder.length, attrsWithNumber))
                        i = tagEnd + 1
                        continue
                    }

                    // For <li> — insert bullet/number prefix
                    if (tagName == "li") {
                        // Determine if inside <ol> or <ul>
                        val parentList = tagStack.lastOrNull { it.tagName == "ol" || it.tagName == "ul" }
                        val prefix = if (parentList?.tagName == "ol") {
                            val n = parentList.attributes["_displayNumber"] ?: "1"
                            "$n. "
                        } else {
                            "• "
                        }
                        for (j in i..tagEnd) {
                            rawToVisual[j] = visualBuilder.length
                        }
                        for (ch in prefix) {
                            visualToRaw.add(i)
                            visualBuilder.append(ch)
                        }
                        tagStack.add(OpenTag(tagName, visualBuilder.length, attributes))
                        i = tagEnd + 1
                        continue
                    }

                    // Skip tag chars from visual
                    for (j in i..tagEnd) {
                        rawToVisual[j] = visualBuilder.length
                    }
                    tagStack.add(OpenTag(tagName, visualBuilder.length, attributes))
                    i = tagEnd + 1
                }
            } else {
                // Regular character — add to visual
                rawToVisual[i] = visualBuilder.length
                visualToRaw.add(i)
                visualBuilder.append(raw[i])
                i++
            }
        }
        // Final position
        rawToVisual[raw.length] = visualBuilder.length

        // Build the AnnotatedString
        val annotatedString = buildAnnotatedString {
            append(visualBuilder.toString())
            for (span in spans) {
                if (span.start < span.end && span.end <= length) {
                    addStyle(span.style, span.start, span.end)
                }
            }
        }

        // Build offset mapping
        val finalRawToVisual = rawToVisual.copyOf()
        val finalVisualToRaw = visualToRaw.toIntArray()

        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                return finalRawToVisual[offset.coerceIn(0, finalRawToVisual.size - 1)]
            }

            override fun transformedToOriginal(offset: Int): Int {
                val coerced = offset.coerceIn(0, visualBuilder.length)
                
                // Find the first raw index that maps to this visual offset
                val firstRaw = finalRawToVisual.indexOfFirst { it == coerced }
                if (firstRaw != -1) return firstRaw
                
                // Fallback: use visualToRaw exact character mapping
                if (finalVisualToRaw.isEmpty()) return 0
                return if (coerced < finalVisualToRaw.size) {
                    finalVisualToRaw[coerced]
                } else {
                    raw.length
                }
            }
        }

        return ParseResult(annotatedString, offsetMapping)
    }

    private fun getSpanStyleForTag(tagName: String, attributes: Map<String, String>): SpanStyle? {
        return when (tagName) {
            "b", "strong" -> SpanStyle(fontWeight = FontWeight.Bold)
            "i", "em" -> SpanStyle(
                fontStyle = FontStyle.Italic,
                fontFamily = FontFamily.Serif,
                letterSpacing = 0.3.sp
            )
            "u" -> SpanStyle(textDecoration = TextDecoration.Underline)
            "s", "strike", "del" -> SpanStyle(textDecoration = TextDecoration.LineThrough)
            "code" -> SpanStyle(
                fontFamily = FontFamily.Monospace,
                background = Color(0x2664748B)
            )
            "pre" -> SpanStyle(
                fontFamily = FontFamily.Monospace,
                background = Color(0x2664748B)
            )
            "h1" -> SpanStyle(fontWeight = FontWeight.Bold, fontSize = 24.sp)
            "h2" -> SpanStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp)
            "a" -> SpanStyle(
                color = linkColor,
                textDecoration = TextDecoration.Underline
            )
            else -> null
        }
    }

    private fun extractAttributes(tag: String): Map<String, String> {
        val attrs = mutableMapOf<String, String>()
        val attrRegex = Regex("""(\w+)\s*=\s*["']([^"']*)["']""")
        attrRegex.findAll(tag).forEach { match ->
            attrs[match.groupValues[1].lowercase()] = match.groupValues[2]
        }
        return attrs
    }

    /**
     * Walks the raw text line by line and assigns a displayed number to each
     * line that starts with `<ol><li>`. Contiguous OL lines share an incrementing
     * counter; any non-OL line resets the counter.
     *
     * Returns a map from the raw character index of `<ol>` opening tag → display number.
     */
    private fun computeOrderedListNumbers(raw: String): Map<Int, Int> {
        val result = mutableMapOf<Int, Int>()
        var counter = 0
        var i = 0

        while (i < raw.length) {
            // Find end of this line
            val lineEnd = raw.indexOf('\n', i).let { if (it == -1) raw.length else it }
            val line = raw.substring(i, lineEnd)

            if (line.trimStart().startsWith("<ol><li>")) {
                counter++
                // Record the position of the <ol> opening
                val olPos = i + line.indexOf("<ol>")
                result[olPos] = counter
            } else if (line.isNotBlank()) {
                // Non-empty, non-OL line — reset
                counter = 0
            }
            // Empty lines neither increment nor reset (allow blank lines between items)

            i = lineEnd + 1
        }
        return result
    }

    private data class OpenTag(
        val tagName: String,
        val visualStart: Int,
        val attributes: Map<String, String>
    )

    private data class SpanRange(
        val style: SpanStyle,
        val start: Int,
        val end: Int
    )

    private data class ParseResult(
        val annotatedString: AnnotatedString,
        val offsetMapping: OffsetMapping
    )
}

