package com.amvarpvtltd.swiftNote.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amvarpvtltd.swiftNote.design.NoteTheme
import com.amvarpvtltd.swiftNote.richtext.RichTextRenderer

/**
 * A Composable that renders HTML note content with full formatting support.
 *
 * Handles:
 * - **Bold / italic / underline / strikethrough**
 * - **H1 / H2 headings** (larger + bold)
 * - **Bullet lists** (rendered as "• " prefixed lines)
 * - **Hyperlinks** — tinted, underlined, tap-to-open in browser (or [onLinkClick] callback)
 * - **`<code>` / `<pre>` blocks** — rendered in a monospace font family
 * - **Inline checkboxes** (`<input type="checkbox">`) — shown as [Checkbox] composables;
 *   optionally interactive via [onCheckboxToggle]
 * - **Plain text** — passed through unchanged
 *
 * Phase 1 implementation. Toggling checkboxes from this composable requires the caller
 * to update the source HTML and re-compose (see [onCheckboxToggle]).
 *
 * @param html             Raw HTML or plain text string.
 * @param modifier         Layout modifier applied to the underlying [Text].
 * @param style            Text style (defaults to [MaterialTheme.typography.bodyLarge]).
 * @param color            Default text colour (defaults to [NoteTheme.OnSurface]).
 * @param onCheckboxToggle Called when the user taps a checkbox. Args: (checkboxIndex, newChecked).
 *                         When null, checkboxes are rendered as read-only (disabled).
 * @param onLinkClick      Called when the user taps a hyperlink. When null, the default
 *                         [LocalUriHandler] opens the URL in the system browser.
 */
@Composable
fun RichTextDisplay(
    html: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    color: Color = NoteTheme.OnSurface,
    onCheckboxToggle: ((index: Int, newChecked: Boolean) -> Unit)? = null,
    onLinkClick: ((url: String) -> Unit)? = null
) {
    val linkColor = NoteTheme.Primary
    val uriHandler = LocalUriHandler.current

    // Parse once; re-parse only if html content or link colour changes.
    val content = remember(html, linkColor) {
        RichTextRenderer.htmlToAnnotatedFull(html, linkColor)
    }

    // Detect whether any interactions (links / checkboxes) exist so we only
    // attach the expensive pointerInput modifier when actually needed.
    val hasLinks = remember(content) {
        content.annotated.getStringAnnotations("URL", 0, content.annotated.length).isNotEmpty()
    }
    val needsPointerInput = hasLinks || content.checkboxes.isNotEmpty()

    // Build inline-content map for checkbox placeholders.
    val inlineContent = remember(content.checkboxes, onCheckboxToggle) {
        if (content.checkboxes.isEmpty()) {
            emptyMap()
        } else {
            content.checkboxes.associate { cb ->
                cb.id to InlineTextContent(
                    placeholder = Placeholder(
                        width  = 20.sp,
                        height = 20.sp,
                        placeholderVerticalAlign = PlaceholderVerticalAlign.Center
                    )
                ) {
                    Checkbox(
                        checked = cb.checked,
                        onCheckedChange = if (onCheckboxToggle != null) {
                            { newChecked -> onCheckboxToggle(cb.index, newChecked) }
                        } else {
                            null   // null → disabled / read-only visual
                        },
                        colors = CheckboxDefaults.colors(
                            checkedColor           = NoteTheme.Primary,
                            uncheckedColor         = NoteTheme.OnSurfaceVariant,
                            disabledCheckedColor   = NoteTheme.Primary.copy(alpha = 0.5f),
                            disabledUncheckedColor = NoteTheme.OnSurfaceVariant.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }

    // Capture TextLayoutResult for offset-to-character mapping on tap.
    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

    Text(
        text          = content.annotated,
        inlineContent = inlineContent,
        style         = style,
        color         = color,
        onTextLayout  = { textLayoutResult = it },
        modifier      = modifier.then(
            if (needsPointerInput) {
                Modifier.pointerInput(content, onCheckboxToggle, onLinkClick) {
                    detectTapGestures { tapOffset ->
                        val layout    = textLayoutResult ?: return@detectTapGestures
                        val charIndex = layout.getOffsetForPosition(tapOffset)

                        // ── Checkbox tap ─────────────────────────────────────────────
                        content.annotated
                            .getStringAnnotations("CHECKBOX", charIndex, charIndex)
                            .firstOrNull()
                            ?.let { ann ->
                                val idx = ann.item.toIntOrNull() ?: return@let
                                val cb  = content.checkboxes.getOrNull(idx) ?: return@let
                                onCheckboxToggle?.invoke(idx, !cb.checked)
                            }

                        // ── Link tap ─────────────────────────────────────────────────
                        content.annotated
                            .getStringAnnotations("URL", charIndex, charIndex)
                            .firstOrNull()
                            ?.let { ann ->
                                val url = ann.item
                                if (onLinkClick != null) {
                                    onLinkClick(url)
                                } else {
                                    try {
                                        uriHandler.openUri(url)
                                    } catch (_: Exception) {
                                        // ignore malformed / unhandled URIs silently
                                    }
                                }
                            }
                    }
                }
            } else {
                Modifier
            }
        )
    )
}

