package com.amvarpvtltd.swiftNote.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.FormatBold
import androidx.compose.material.icons.outlined.FormatItalic
import androidx.compose.material.icons.outlined.FormatListNumbered
import androidx.compose.material.icons.outlined.FormatUnderlined
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Title
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import com.amvarpvtltd.swiftNote.design.NoteTheme
import com.amvarpvtltd.swiftNote.richtext.MarkdownToHtmlConverter
import com.amvarpvtltd.swiftNote.richtext.RichTextSanitizer
import com.mohamedrejeb.richeditor.model.RichTextState

/**
 * Horizontal scrollable formatting toolbar for the rich text editor.
 *
 * Groups:
 * 1. Bold · Italic · Underline
 * 2. Heading ▾ · Bullet list · Numbered list
 * 3. Code · Link
 *
 * @param state    The [RichTextState] from compose-rich-editor library.
 * @param modifier Optional modifier (e.g. padding / background from the parent).
 */
@Composable
fun RichTextToolbar(
    state: RichTextState,
    modifier: Modifier = Modifier
) {
    var showHeadingMenu by remember { mutableStateOf(false) }
    var showLinkDialog  by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // Detect active formats from library state
    val isBold by remember { derivedStateOf { state.currentSpanStyle.fontWeight == FontWeight.Bold } }
    val isItalic by remember { derivedStateOf { state.currentSpanStyle.fontStyle == FontStyle.Italic } }
    val isUnderline by remember { derivedStateOf {
        state.currentSpanStyle.textDecoration?.contains(TextDecoration.Underline) == true
    } }
    val isHeading by remember { derivedStateOf { (state.currentSpanStyle.fontSize.value) >= 18f } }
    val isBulletList = state.isUnorderedList
    val isNumberedList = state.isOrderedList
    val isCode = state.isCodeSpan
    val isLink = state.isLink

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(NoteTheme.SurfaceVariant.copy(alpha = 0.85f)),
        contentAlignment = Alignment.Center
    ) {
        LazyRow(
            modifier = Modifier
                .padding(horizontal = 6.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment     = Alignment.CenterVertically
        ) {
            // Group 1 — Inline styles
            item {
                ToolbarIconBtn(Icons.Outlined.FormatBold, "Bold", isActive = isBold) {
                    state.toggleSpanStyle(SpanStyle(fontWeight = FontWeight.Bold))
                }
            }
            item {
                ToolbarIconBtn(Icons.Outlined.FormatItalic, "Italic", isActive = isItalic) {
                    state.toggleSpanStyle(SpanStyle(
                        fontStyle = FontStyle.Italic,
                        fontFamily = FontFamily.Serif
                    ))
                }
            }
            item {
                ToolbarIconBtn(Icons.Outlined.FormatUnderlined, "Underline", isActive = isUnderline) {
                    state.toggleSpanStyle(SpanStyle(textDecoration = TextDecoration.Underline))
                }
            }

            // Divider
            item { ToolbarDividerLine() }

            // Group 2 — Block / list
            item {
                // Heading with drop-down
                Box(modifier = Modifier.wrapContentSize(Alignment.TopStart)) {
                    ToolbarIconBtn(Icons.Outlined.Title, "Heading", isActive = isHeading) {
                        showHeadingMenu = true
                    }
                    DropdownMenu(
                        expanded    = showHeadingMenu,
                        onDismissRequest = { showHeadingMenu = false },
                        modifier = Modifier.background(NoteTheme.Surface)
                    ) {
                        DropdownMenuItem(
                            text = {
                                Text("H1", style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold, color = NoteTheme.OnSurface)
                            },
                            onClick = {
                                state.toggleSpanStyle(SpanStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold))
                                showHeadingMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Text("H2", style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold, color = NoteTheme.OnSurface)
                            },
                            onClick = {
                                state.toggleSpanStyle(SpanStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold))
                                showHeadingMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Text("Normal", style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Normal, color = NoteTheme.OnSurface)
                            },
                            onClick = {
                                state.removeSpanStyle(SpanStyle(fontSize = 24.sp))
                                state.removeSpanStyle(SpanStyle(fontSize = 20.sp))
                                showHeadingMenu = false
                            }
                        )
                    }
                }
            }
            item {
                ToolbarIconBtn(Icons.AutoMirrored.Outlined.FormatListBulleted, "Bullet list", isActive = isBulletList) {
                    state.toggleUnorderedList()
                }
            }
            item {
                ToolbarIconBtn(Icons.Outlined.FormatListNumbered, "Numbered list", isActive = isNumberedList) {
                    state.toggleOrderedList()
                }
            }

            // Divider
            item { ToolbarDividerLine() }

            // Group 3 — Code / link
            item {
                ToolbarIconBtn(Icons.Outlined.Code, "Code", isActive = isCode) {
                    state.toggleSpanStyle(SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        background = NoteTheme.OnSurfaceVariant.copy(alpha = 0.15f)
                    ))
                    state.toggleCodeSpan()
                }
            }
            item {
                ToolbarIconBtn(Icons.Outlined.Link, "Insert link", isActive = isLink) {
                    showLinkDialog = true
                }
            }

            // Divider
            item { ToolbarDividerLine() }

            // Group 4 — Paste with formatting
            item {
                ToolbarIconBtn(Icons.Outlined.ContentPaste, "Paste formatted", isActive = false) {
                    pasteFormattedFromClipboard(context, state)
                }
            }
        }
    }

    // ── Link insert dialog ────────────────────────────────────────────────────
    if (showLinkDialog) {
        val selectedText = state.selectedLinkText.orEmpty()
        LinkInsertDialog(
            initialDisplayText = selectedText,
            onConfirm = { displayText, url ->
                val finalUrl = if (!url.startsWith("http://") && !url.startsWith("https://"))
                    "https://$url" else url
                state.addLink(text = displayText.ifBlank { finalUrl }, url = finalUrl)
                showLinkDialog = false
            },
            onDismiss = { showLinkDialog = false }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Private sub-composables
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ToolbarIconBtn(
    icon: ImageVector,
    contentDescription: String,
    isActive: Boolean = false,
    onClick: () -> Unit
) {
    val bgColor by animateColorAsState(
        targetValue = if (isActive) NoteTheme.Primary.copy(alpha = 0.15f) else Color.Transparent,
        animationSpec = tween(200),
        label = "toolbar_btn_bg"
    )
    val iconTint by animateColorAsState(
        targetValue = if (isActive) NoteTheme.Primary else NoteTheme.OnSurfaceVariant,
        animationSpec = tween(200),
        label = "toolbar_btn_tint"
    )

    IconButton(
        onClick   = onClick,
        modifier  = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
    ) {
        Icon(
            imageVector        = icon,
            contentDescription = contentDescription,
            tint               = iconTint,
            modifier           = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun ToolbarDividerLine() {
    Box(
        modifier = Modifier
            .padding(horizontal = 2.dp)
            .width(1.dp)
            .height(20.dp)
            .background(NoteTheme.OnSurface.copy(alpha = 0.12f))
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Link Insert Dialog
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Dialog for inserting a hyperlink.
 *
 * Pre-fills [initialDisplayText] when the user had a selection in the editor.
 * Validates the URL and auto-prepends `"https://"` if missing.
 */
@Composable
fun LinkInsertDialog(
    initialDisplayText: String = "",
    onConfirm: (displayText: String, url: String) -> Unit,
    onDismiss: () -> Unit
) {
    var displayText by remember { mutableStateOf(initialDisplayText) }
    var urlText     by remember { mutableStateOf("") }

    val urlHasScheme   = urlText.startsWith("http://") || urlText.startsWith("https://")
    val showSchemeTip  = urlText.isNotBlank() && !urlHasScheme
    val canInsert      = urlText.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        shape            = RoundedCornerShape(20.dp),
        containerColor   = NoteTheme.Surface,
        title = {
            Text(
                "Insert link",
                style      = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color      = NoteTheme.OnSurface
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Display text field
                OutlinedTextField(
                    value         = displayText,
                    onValueChange = { displayText = it },
                    label         = { Text("Display text") },
                    placeholder   = { Text("Optional — defaults to URL") },
                    singleLine    = true,
                    modifier      = Modifier.fillMaxWidth(),
                    colors        = linkDialogFieldColors()
                )

                // URL field
                OutlinedTextField(
                    value         = urlText,
                    onValueChange = { urlText = it },
                    label         = { Text("URL") },
                    placeholder   = { Text("https://") },
                    singleLine    = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier      = Modifier.fillMaxWidth(),
                    colors        = linkDialogFieldColors()
                )

                // Warning chip when scheme is missing
                if (showSchemeTip) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(NoteTheme.WarningContainer.copy(alpha = 0.6f))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            Icons.Outlined.Warning,
                            contentDescription = null,
                            tint     = NoteTheme.Warning,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Will be saved as https://$urlText",
                            style = MaterialTheme.typography.labelSmall,
                            color = NoteTheme.OnWarningContainer
                        )
                    }
                }

                // Helper tip
                Text(
                    "Tip: tap a link in the note viewer to open it in your browser.",
                    style = MaterialTheme.typography.labelSmall,
                    color = NoteTheme.OnSurfaceVariant
                )
            }
        },
        confirmButton = {
            FilledTonalButton(
                onClick = { onConfirm(displayText, urlText) },
                enabled = canInsert
            ) {
                Text("Insert link", fontWeight = FontWeight.Medium, fontSize = 14.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = NoteTheme.OnSurfaceVariant)
            }
        }
    )
}

@Composable
private fun linkDialogFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor   = NoteTheme.Primary,
    unfocusedBorderColor = NoteTheme.OnSurfaceVariant.copy(alpha = 0.4f),
    focusedLabelColor    = NoteTheme.Primary,
    focusedTextColor     = NoteTheme.OnSurface,
    unfocusedTextColor   = NoteTheme.OnSurface,
    cursorColor          = NoteTheme.Primary
)

// ─────────────────────────────────────────────────────────────────────────────
// Paste with Formatting
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Reads HTML from the system clipboard, sanitizes it through [RichTextSanitizer],
 * and appends it to the current [RichTextState] content.
 *
 * If the clipboard has no HTML, falls back to pasting plain text.
 * If clipboard is empty, shows a toast.
 */
private fun pasteFormattedFromClipboard(context: Context, state: RichTextState) {
    try {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip
        if (clip == null || clip.itemCount == 0) {
            Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
            return
        }

        val item = clip.getItemAt(0)
        val htmlText = item.htmlText
        val plainText = item.coerceToText(context)?.toString().orEmpty()

        if (htmlText.isNullOrBlank() && plainText.isBlank()) {
            Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
            return
        }

        if (!htmlText.isNullOrBlank()) {
            // Sanitize the HTML to our supported subset
            val sanitized = RichTextSanitizer.sanitize(htmlText)
            if (sanitized.isNotBlank()) {
                // Get current content, append sanitized HTML
                val currentHtml = state.toHtml()
                val combined = if (currentHtml.isBlank() || currentHtml == "<p><br></p>" || currentHtml == "<br>") {
                    sanitized
                } else {
                    "$currentHtml$sanitized"
                }
                state.setHtml(combined)
                Toast.makeText(context, "Pasted with formatting", Toast.LENGTH_SHORT).show()
            } else {
                // Sanitizer stripped everything — fall back to plain text
                insertPlainText(state, plainText, context)
            }
        } else {
            // No HTML available — paste as plain text
            insertPlainText(state, plainText, context)
        }
    } catch (e: Exception) {
        Toast.makeText(context, "Paste failed: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

/**
 * Inserts plain text by detecting markdown formatting and converting to HTML,
 * or appending it as a paragraph if no markdown is detected.
 */
private fun insertPlainText(state: RichTextState, text: String, context: Context) {
    if (text.isBlank()) {
        Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
        return
    }

    val currentHtml = state.toHtml()

    // Try to detect and convert markdown formatting
    val convertedHtml = MarkdownToHtmlConverter.convert(text)
    val htmlToInsert = if (convertedHtml != null) {
        convertedHtml
    } else {
        // No markdown detected — wrap plain text in <p> for consistent block structure
        "<p>${text.replace("\n", "<br>")}</p>"
    }

    val combined = if (currentHtml.isBlank() || currentHtml == "<p><br></p>" || currentHtml == "<br>") {
        htmlToInsert
    } else {
        "$currentHtml$htmlToInsert"
    }
    state.setHtml(combined)

    val msg = if (convertedHtml != null) "Pasted with formatting" else "Pasted as plain text"
    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
}
