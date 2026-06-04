package com.amvarpvtltd.swiftNote.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import com.amvarpvtltd.swiftNote.design.NoteTheme
import com.amvarpvtltd.swiftNote.richtext.MarkdownToHtmlConverter
import com.amvarpvtltd.swiftNote.richtext.RichTextBridge
import com.mohamedrejeb.richeditor.model.rememberRichTextState
import com.mohamedrejeb.richeditor.ui.material3.RichText

/**
 * Read-only renderer for note descriptions.
 *
 * Handles both HTML and Markdown content:
 * - If content contains HTML tags, renders as HTML directly.
 * - If content contains Markdown formatting (e.g. **bold**, # headings, numbered lists),
 *   converts to HTML first then renders.
 * - Plain text is rendered as-is.
 */
@Composable
fun RichTextDisplay(
    html: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    color: Color = NoteTheme.OnSurface,
    @Suppress("UNUSED_PARAMETER")
    onCheckboxToggle: ((index: Int, newChecked: Boolean) -> Unit)? = null,
    @Suppress("UNUSED_PARAMETER")
    onLinkClick: ((url: String) -> Unit)? = null
) {
    val state = rememberRichTextState()

    // Determine the actual HTML to render: convert markdown if needed,
    // then preserve leading whitespace (indentation) that HTML would otherwise collapse.
    val resolvedHtml = remember(html) {
        val base = when {
            html.isBlank() -> ""
            RichTextBridge.containsHtml(html) -> html // Already HTML
            MarkdownToHtmlConverter.containsMarkdown(html) -> {
                // Contains markdown — convert to HTML for proper rendering
                MarkdownToHtmlConverter.convert(html) ?: html
            }
            else -> html // Plain text
        }
        // Preserve leading spaces/tabs so they survive HTML rendering
        if (base.isBlank()) base else RichTextBridge.preserveLeadingWhitespace(base)
    }

    LaunchedEffect(resolvedHtml) {
        state.setHtml(resolvedHtml)
    }
    RichText(
        state = state,
        modifier = modifier,
        style = style.copy(color = color)
    )
}
