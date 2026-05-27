package com.amvarpvtltd.swiftNote.richtext

import android.text.Editable
import android.text.Html
import android.text.Spannable
import android.text.Spanned
import android.text.style.TypefaceSpan
import android.util.Log
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp
import org.xml.sax.XMLReader

/**
 * Central HTML ↔ AnnotatedString conversion utilities.
 *
 * Phase 0: extracted from add_Note.kt's spannedToAnnotatedString.
 * Phase 1: extended with full HTML support —
 *   headings (h1/h2), bullet/ordered lists, hyperlinks, code/pre blocks,
 *   and inline checkboxes (<input type="checkbox">).
 */
object RichTextRenderer {

    // ─── Regex & private constants ────────────────────────────────────────────

    private val HTML_TAG_REGEX = Regex(
        "<(/?)(b|strong|i|em|u|s|h1|h2|p|br|ul|ol|li|a|code|pre|input)\\b[^>]*>",
        RegexOption.IGNORE_CASE
    )

    /**
     * Private-Use-Area Unicode chars that act as in-band markers for checkboxes
     * during the HTML pre-processing step.  They survive [Html.fromHtml] unchanged.
     */
    internal const val CHECKBOX_UNCHECKED = "\uE001"
    internal const val CHECKBOX_CHECKED   = "\uE002"
    private  const val CHECKBOX_UNCHECKED_CHAR = '\uE001'
    private  const val CHECKBOX_CHECKED_CHAR   = '\uE002'

    /** Default link colour — matches NoteTheme.Primary; callers may override. */
    val DEFAULT_LINK_COLOR = Color(0xFF6366F1)

    // ─── Public data classes ──────────────────────────────────────────────────

    /**
     * Metadata for a single inline checkbox detected in the HTML.
     *
     * @param id       Unique key used in the [androidx.compose.foundation.text.InlineTextContent] map.
     * @param checked  Whether the checkbox is checked.
     * @param index    Zero-based index within this note's checkbox list — used to locate
     *                 the checkbox in the original HTML for toggling.
     */
    data class CheckboxInfo(
        val id: String,
        val checked: Boolean,
        val index: Int
    )

    /**
     * Full result of converting an HTML string.
     *
     * @param annotated  Compose [AnnotatedString] ready for [androidx.compose.material3.Text].
     * @param checkboxes Ordered list of checkboxes found; each carries an [InlineTextContent] key.
     */
    data class RenderedContent(
        val annotated: AnnotatedString,
        val checkboxes: List<CheckboxInfo>
    )

    // ─── Public API ───────────────────────────────────────────────────────────

    /** Returns true if the string appears to contain HTML markup. */
    fun containsHtml(text: String): Boolean = HTML_TAG_REGEX.containsMatchIn(text)

    /**
     * Strip HTML tags and return plain text — suitable for search or entity detection.
     */
    fun stripHtmlToPlainText(html: String): String {
        if (!containsHtml(html)) return html
        return try {
            Html.fromHtml(html, Html.FROM_HTML_MODE_COMPACT).toString()
        } catch (e: Exception) {
            Log.d("RichTextRenderer", "stripHtmlToPlainText: ${e.message}")
            html
        }
    }

    /**
     * Convenience overload — converts HTML to [AnnotatedString] only.
     * For notes that may contain checkboxes, prefer [htmlToAnnotatedFull].
     */
    fun htmlToAnnotated(html: String): AnnotatedString = htmlToAnnotatedFull(html).annotated

    /**
     * Full conversion: HTML → [RenderedContent] (annotated text + checkbox list).
     *
     * Supported features:
     * - **Bold / italic / underline / strikethrough** (b/i/u/s and their semantic aliases)
     * - **H1 / H2 headings** — rendered larger and bold (handled by Android's Html.fromHtml)
     * - **Unordered & ordered lists** — converted to "• " / "N. " bullet text in pre-processing
     * - **Hyperlinks** — coloured, underlined, tap-to-open via [RichTextDisplay]
     * - **`<code>` / `<pre>`** — monospace font family
     * - **`<input type="checkbox">`** — replaced with [appendInlineContent] placeholders
     *
     * @param html      Raw HTML string from Room / clipboard.
     * @param linkColor Colour applied to hyperlink text; defaults to [DEFAULT_LINK_COLOR].
     */
    fun htmlToAnnotatedFull(
        html: String,
        linkColor: Color = DEFAULT_LINK_COLOR
    ): RenderedContent {
        if (!containsHtml(html)) {
            return RenderedContent(AnnotatedString(html), emptyList())
        }
        return try {
            val preprocessed = preprocessHtml(html)
            val spanned: Spanned = Html.fromHtml(
                preprocessed,
                Html.FROM_HTML_MODE_COMPACT,
                null,
                CodeTagHandler()
            )

            val checkboxes = mutableListOf<CheckboxInfo>()
            val spannedText = spanned.toString()
            var checkboxIndex = 0

            val annotated = buildAnnotatedString {
                // ── Step 1: walk char-by-char, replace private checkbox markers ──────
                for (i in spannedText.indices) {
                    when (spannedText[i]) {
                        CHECKBOX_UNCHECKED_CHAR -> {
                            val id  = "checkbox_$checkboxIndex"
                            val pos = length
                            appendInlineContent(id, "☐")          // 1 char added
                            addStringAnnotation("CHECKBOX", checkboxIndex.toString(), pos, pos + 1)
                            checkboxes.add(CheckboxInfo(id, false, checkboxIndex++))
                        }
                        CHECKBOX_CHECKED_CHAR -> {
                            val id  = "checkbox_$checkboxIndex"
                            val pos = length
                            appendInlineContent(id, "☑")           // 1 char added
                            addStringAnnotation("CHECKBOX", checkboxIndex.toString(), pos, pos + 1)
                            checkboxes.add(CheckboxInfo(id, true, checkboxIndex++))
                        }
                        else -> append(spannedText[i])
                    }
                }

                // ── Step 2: apply all Android spans (positions 1-to-1 with spannedText) ─
                val allSpans = spanned.getSpans(0, spannedText.length, Any::class.java)
                for (span in allSpans) {
                    val start = spanned.getSpanStart(span).coerceIn(0, spannedText.length)
                    val end   = spanned.getSpanEnd(span).coerceIn(0, spannedText.length)
                    if (start >= end) continue

                    when (span) {
                        is android.text.style.StyleSpan -> {
                            val s = when (span.style) {
                                android.graphics.Typeface.BOLD ->
                                    SpanStyle(fontWeight = FontWeight.Bold)
                                android.graphics.Typeface.ITALIC ->
                                    SpanStyle(fontStyle = FontStyle.Italic)
                                android.graphics.Typeface.BOLD_ITALIC ->
                                    SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)
                                else -> null
                            }
                            s?.let { addStyle(it, start, end) }
                        }
                        is android.text.style.UnderlineSpan ->
                            addStyle(SpanStyle(textDecoration = TextDecoration.Underline), start, end)
                        is android.text.style.StrikethroughSpan ->
                            addStyle(SpanStyle(textDecoration = TextDecoration.LineThrough), start, end)
                        is android.text.style.ForegroundColorSpan ->
                            addStyle(SpanStyle(color = Color(span.foregroundColor)), start, end)
                        is android.text.style.RelativeSizeSpan ->
                            // Use 16sp as base (bodyLarge) so h1 ≈ 24sp, h2 ≈ 22sp
                            addStyle(SpanStyle(fontSize = (16f * span.sizeChange).sp), start, end)
                        is android.text.style.URLSpan -> {
                            // Colour + underline + annotation for tap detection
                            addStyle(
                                SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline),
                                start, end
                            )
                            addStringAnnotation("URL", span.url ?: "", start, end)
                        }
                        is TypefaceSpan -> {
                            if (span.family == "monospace") {
                                addStyle(SpanStyle(fontFamily = FontFamily.Monospace), start, end)
                            }
                        }
                        // BulletSpan / LeadingMarginSpan are dropped; lists are already
                        // pre-processed to "• " text before Html.fromHtml runs.
                    }
                }
            }

            RenderedContent(annotated, checkboxes)
        } catch (e: Exception) {
            Log.d("RichTextRenderer", "htmlToAnnotatedFull error: ${e.message}")
            RenderedContent(AnnotatedString(stripHtmlToPlainText(html)), emptyList())
        }
    }

    /**
     * Convert an Android [Spanned] (from [Html.fromHtml]) to a Compose [AnnotatedString].
     *
     * Kept for backward-compatibility with add_Note.kt's clipboard paste flow.
     * New code should use [htmlToAnnotated] or [htmlToAnnotatedFull].
     */
    fun spannedToAnnotatedString(spanned: Spanned): AnnotatedString {
        val plain = spanned.toString()
        return buildAnnotatedString {
            append(plain)
            try {
                val spans = spanned.getSpans(0, spanned.length, Any::class.java)
                for (span in spans) {
                    val start = spanned.getSpanStart(span).coerceIn(0, plain.length)
                    val end   = spanned.getSpanEnd(span).coerceIn(0, plain.length)
                    val style: SpanStyle? = when (span) {
                        is android.text.style.StyleSpan -> when (span.style) {
                            android.graphics.Typeface.BOLD ->
                                SpanStyle(fontWeight = FontWeight.Bold)
                            android.graphics.Typeface.ITALIC ->
                                SpanStyle(fontStyle = FontStyle.Italic)
                            android.graphics.Typeface.BOLD_ITALIC ->
                                SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)
                            else -> SpanStyle()
                        }
                        is android.text.style.UnderlineSpan ->
                            SpanStyle(textDecoration = TextDecoration.Underline)
                        is android.text.style.StrikethroughSpan ->
                            SpanStyle(textDecoration = TextDecoration.LineThrough)
                        is android.text.style.ForegroundColorSpan ->
                            SpanStyle(color = Color(span.foregroundColor))
                        is android.text.style.RelativeSizeSpan ->
                            SpanStyle(fontSize = (14 * span.sizeChange).sp)
                        else -> null
                    }
                    if (style != null && start < end) addStyle(style, start, end)
                }
            } catch (e: Exception) {
                Log.d("RichTextRenderer", "spannedToAnnotatedString: ${e.message}")
            }
        }
    }

    // ─── Internal helpers ─────────────────────────────────────────────────────

    /**
     * Pre-process HTML before passing to [Html.fromHtml].
     *
     * 1. Replace `<input type="checkbox" [checked]>` with [CHECKBOX_CHECKED] / [CHECKBOX_UNCHECKED]
     *    private-use characters that survive [Html.fromHtml] unchanged.
     * 2. Strip `<ul>` / `<ol>` wrapper tags.
     * 3. Replace `<li>…</li>` with `"• …<br>"` so list items render as bullet paragraphs
     *    without needing BulletSpan / LeadingMarginSpan support in Compose.
     */
    private fun preprocessHtml(html: String): String {
        var result = html

        // ── Newlines → <br> so Html.fromHtml renders them ─────────────────────
        // Replace literal \n with <br> (but not \n that appear inside tags)
        result = result.replace("\n", "<br>")

        // ── Checkboxes ────────────────────────────────────────────────────────
        // Process "checked" variants first to avoid partial matches
        result = result.replace(
            Regex("""<input\b[^>]*\bchecked\b[^>]*>""", RegexOption.IGNORE_CASE),
            CHECKBOX_CHECKED
        )
        result = result.replace(
            Regex("""<input\b[^>]*type=["']checkbox["'][^>]*>""", RegexOption.IGNORE_CASE),
            CHECKBOX_UNCHECKED
        )

        // ── Lists ─────────────────────────────────────────────────────────────
        // Render <ol> items with sequential numbers; <ul> items with bullets.
        // Process <ol>...</ol> blocks first, numbering each <li>.
        result = result.replace(Regex("<ol[^>]*>(.*?)</ol>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))) { match ->
            var idx = 0
            match.groupValues[1]
                .replace(Regex("<li[^>]*>", RegexOption.IGNORE_CASE)) { idx++; "$idx. " }
                .replace(Regex("</li>", RegexOption.IGNORE_CASE), "<br>")
        }
        // Process <ul>...</ul> blocks with bullet prefix.
        result = result.replace(Regex("<ul[^>]*>(.*?)</ul>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))) { match ->
            match.groupValues[1]
                .replace(Regex("<li[^>]*>", RegexOption.IGNORE_CASE), "• ")
                .replace(Regex("</li>", RegexOption.IGNORE_CASE), "<br>")
        }
        // Fallback: any remaining bare <li> (shouldn't happen, but just in case)
        result = result.replace(Regex("<li[^>]*>", RegexOption.IGNORE_CASE), "• ")
        result = result.replace(Regex("</li>", RegexOption.IGNORE_CASE), "<br>")

        return result
    }

    // ─── Inner classes ────────────────────────────────────────────────────────

    /**
     * [Html.TagHandler] that adds `<code>` and `<pre>` monospace support.
     *
     * Android's [Html.fromHtml] ignores these tags by default; we intercept them,
     * record the start position with a [CodeMarker] span, and then on the closing
     * tag apply a [TypefaceSpan] over the enclosed range.
     */
    private class CodeTagHandler : Html.TagHandler {
        override fun handleTag(
            opening: Boolean,
            tag: String,
            output: Editable,
            xmlReader: XMLReader
        ) {
            when (tag.lowercase()) {
                "code", "pre" -> {
                    if (opening) {
                        // Mark start position
                        output.setSpan(
                            CodeMarker(),
                            output.length,
                            output.length,
                            Spannable.SPAN_MARK_MARK
                        )
                    } else {
                        // Find the matching start marker and apply monospace
                        val markers = output.getSpans(0, output.length, CodeMarker::class.java)
                        val marker  = markers.lastOrNull() ?: return
                        val start   = output.getSpanStart(marker)
                        output.removeSpan(marker)
                        if (start < output.length) {
                            output.setSpan(
                                TypefaceSpan("monospace"),
                                start,
                                output.length,
                                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                            )
                        }
                        // Ensure a trailing newline after <pre> blocks
                        if (tag.lowercase() == "pre" &&
                            output.isNotEmpty() &&
                            output.last() != '\n'
                        ) {
                            output.append('\n')
                        }
                    }
                }
            }
        }

        /** Zero-length marker span used to record the start of a code/pre block. */
        private class CodeMarker
    }
}
