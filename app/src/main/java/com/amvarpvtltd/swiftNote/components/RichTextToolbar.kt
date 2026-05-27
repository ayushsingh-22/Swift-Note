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
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.DataObject
import androidx.compose.material.icons.outlined.FormatBold
import androidx.compose.material.icons.outlined.FormatItalic
import androidx.compose.material.icons.outlined.FormatListBulleted
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amvarpvtltd.swiftNote.design.NoteTheme
import com.amvarpvtltd.swiftNote.richtext.RichTextEditorHelpers

/**
 * Horizontal scrollable formatting toolbar shown above the description text field.
 *
 * Groups:
 * 1. Bold · Italic · Underline
 * 2. Heading ▾ · Bullet list · Numbered list · Checkbox
 * 3. Code · Link
 *
 * The toolbar is hidden when the description field is not focused or when the note
 * is in checklist mode — the caller controls visibility via [AnimatedVisibility].
 *
 * @param value         Current [TextFieldValue] of the description field.
 * @param onValueChange Callback invoked with the updated [TextFieldValue] after a formatting action.
 * @param modifier      Optional modifier (e.g. padding / background from the parent).
 */
@Composable
fun RichTextToolbar(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier
) {
    var showHeadingMenu by remember { mutableStateOf(false) }
    var showLinkDialog  by remember { mutableStateOf(false) }

    // Detect active formats at cursor position
    val activeFormats = remember(value.text, value.selection) {
        detectActiveFormats(value.text, value.selection.start)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(NoteTheme.SurfaceVariant.copy(alpha = 0.85f))
    ) {
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(0.dp),
            verticalAlignment     = Alignment.CenterVertically
        ) {
            // Group 1 — Inline styles
            item {
                ToolbarIconBtn(Icons.Outlined.FormatBold, "Bold", isActive = activeFormats.bold) {
                    onValueChange(RichTextEditorHelpers.toggleBold(value))
                }
            }
            item {
                ToolbarIconBtn(Icons.Outlined.FormatItalic, "Italic", isActive = activeFormats.italic) {
                    onValueChange(RichTextEditorHelpers.toggleItalic(value))
                }
            }
            item {
                ToolbarIconBtn(Icons.Outlined.FormatUnderlined, "Underline", isActive = activeFormats.underline) {
                    onValueChange(RichTextEditorHelpers.toggleUnderline(value))
                }
            }

            // Divider
            item { ToolbarDividerLine() }

            // Group 2 — Block / list
            item {
                // Heading with drop-down
                Box(modifier = Modifier.wrapContentSize(Alignment.TopStart)) {
                    ToolbarIconBtn(Icons.Outlined.Title, "Heading", isActive = activeFormats.heading) {
                        showHeadingMenu = true
                    }
                    DropdownMenu(
                        expanded    = showHeadingMenu,
                        onDismissRequest = { showHeadingMenu = false }
                    ) {
                        listOf("H1" to 1, "H2" to 2, "Normal" to 0).forEach { (label, level) ->
                            DropdownMenuItem(
                                text    = {
                                    Text(
                                        label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = NoteTheme.OnSurface
                                    )
                                },
                                onClick = {
                                    onValueChange(RichTextEditorHelpers.applyHeading(value, level))
                                    showHeadingMenu = false
                                }
                            )
                        }
                    }
                }
            }
            item {
                ToolbarIconBtn(Icons.AutoMirrored.Outlined.FormatListBulleted, "Bullet list", isActive = activeFormats.bulletList) {
                    onValueChange(RichTextEditorHelpers.toggleBulletList(value))
                }
            }
            item {
                ToolbarIconBtn(Icons.Outlined.FormatListNumbered, "Numbered list", isActive = activeFormats.numberedList) {
                    onValueChange(RichTextEditorHelpers.toggleNumberedList(value))
                }
            }
            item {
                ToolbarIconBtn(Icons.Outlined.CheckBoxOutlineBlank, "Checkbox") {
                    onValueChange(RichTextEditorHelpers.insertCheckbox(value))
                }
            }

            // Divider
            item { ToolbarDividerLine() }

            // Group 3 — Code / link
            item {
                ToolbarIconBtn(Icons.Outlined.Code, "Inline code", isActive = activeFormats.code) {
                    onValueChange(RichTextEditorHelpers.toggleCode(value))
                }
            }
            item {
                ToolbarIconBtn(Icons.Outlined.DataObject, "Code block", isActive = activeFormats.codeBlock) {
                    onValueChange(RichTextEditorHelpers.insertCodeBlock(value))
                }
            }
            item {
                ToolbarIconBtn(Icons.Outlined.Link, "Insert link", isActive = activeFormats.link) {
                    showLinkDialog = true
                }
            }
        }
    }


    // ── Link insert dialog ────────────────────────────────────────────────────
    if (showLinkDialog) {
        val selectedText = value.text.substring(
            value.selection.min,
            value.selection.max
        )
        LinkInsertDialog(
            initialDisplayText = selectedText,
            onConfirm = { displayText, url ->
                onValueChange(RichTextEditorHelpers.insertLink(value, url, displayText))
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
        targetValue = if (isActive) Color(0xFF6366F1).copy(alpha = 0.15f) else Color.Transparent,
        animationSpec = tween(200),
        label = "toolbar_btn_bg"
    )
    val iconTint by animateColorAsState(
        targetValue = if (isActive) Color(0xFF6366F1) else NoteTheme.OnSurfaceVariant,
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
 *
 * @param initialDisplayText  Pre-filled display text (the selected text in the editor, if any).
 * @param onConfirm           Called with (displayText, url) when the user taps "Insert link".
 * @param onDismiss           Called when the dialog is cancelled.
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
// Active format detection
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Holds which formatting styles are currently active at the cursor position.
 */
private data class ActiveFormats(
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val heading: Boolean = false,
    val bulletList: Boolean = false,
    val numberedList: Boolean = false,
    val code: Boolean = false,
    val codeBlock: Boolean = false,
    val link: Boolean = false
)

/**
 * Detects which HTML formatting tags surround the given cursor position.
 * Checks if the cursor is inside open/close tag pairs.
 */
private fun detectActiveFormats(text: String, cursorPos: Int): ActiveFormats {
    if (text.isEmpty()) return ActiveFormats()

    val pos = cursorPos.coerceIn(0, text.length)

    // Get the text before cursor to check for open tags without matching close tags
    val before = text.substring(0, pos)
    val after = text.substring(pos)

    fun isInsideTag(openTag: String, closeTag: String): Boolean {
        // Find the last occurrence of open tag before cursor
        val lastOpen = before.lastIndexOf(openTag, ignoreCase = true)
        if (lastOpen == -1) return false
        // Check if there's a close tag between the open tag and cursor
        val closeBeforeCursor = before.indexOf(closeTag, startIndex = lastOpen + openTag.length, ignoreCase = true)
        return closeBeforeCursor == -1  // No close tag found before cursor = we're inside
    }

    // For line-level formats, get the current line
    val lineStart = before.lastIndexOf('\n') + 1
    val lineEnd = after.indexOf('\n').let { if (it == -1) text.length else pos + it }
    val currentLine = text.substring(lineStart, lineEnd)

    return ActiveFormats(
        bold = isInsideTag("<b>", "</b>") || isInsideTag("<strong>", "</strong>"),
        italic = isInsideTag("<i>", "</i>") || isInsideTag("<em>", "</em>"),
        underline = isInsideTag("<u>", "</u>"),
        heading = currentLine.trimStart().startsWith("<h1>", ignoreCase = true) ||
                  currentLine.trimStart().startsWith("<h2>", ignoreCase = true),
        bulletList = currentLine.trimStart().startsWith("<ul>", ignoreCase = true),
        numberedList = currentLine.trimStart().startsWith("<ol>", ignoreCase = true),
        code = isInsideTag("<code>", "</code>") && !isInsideTag("<pre>", "</pre>"),
        codeBlock = isInsideTag("<pre>", "</pre>"),
        link = isInsideTag("<a ", "</a>")
    )
}



