package com.amvarpvtltd.swiftNote.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.amvarpvtltd.swiftNote.checklist.ChecklistItem
import com.amvarpvtltd.swiftNote.design.NoteTheme
import com.amvarpvtltd.swiftNote.utils.Constants

/**
 * A single row in the checklist editor.
 * - Checkbox toggles checked state
 * - TextField for editing item text
 * - Delete icon shown always (small X)
 * - Enter key: inserts a new item below
 * - Backspace on empty: removes current item
 */
@Composable
fun ChecklistItemRow(
    item: ChecklistItem,
    onCheckedChange: (Boolean) -> Unit,
    onTextChange: (String) -> Unit,
    onDelete: () -> Unit,
    onEnterPressed: () -> Unit,
    onBackspaceOnEmpty: () -> Unit,
    requestFocus: Boolean = false,
    readOnly: Boolean = false,
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }

    val textColor by animateColorAsState(
        targetValue = if (item.isChecked) NoteTheme.OnSurfaceVariant.copy(alpha = 0.5f)
        else NoteTheme.OnSurface,
        animationSpec = tween(200),
        label = "checklist_text_color"
    )

    val bgColor by animateColorAsState(
        targetValue = if (item.isChecked) NoteTheme.SurfaceVariant.copy(alpha = 0.2f)
        else NoteTheme.Surface.copy(alpha = 0.01f),
        animationSpec = tween(200),
        label = "checklist_bg_color"
    )

    LaunchedEffect(requestFocus) {
        if (requestFocus) {
            try { focusRequester.requestFocus() } catch (_: Exception) {}
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Constants.CORNER_RADIUS_SMALL.dp))
            .background(bgColor)
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Drag handle (visual only in editor — functional reorder deferred)
        if (!readOnly) {
            Icon(
                Icons.Outlined.DragHandle,
                contentDescription = "Reorder",
                tint = NoteTheme.OnSurfaceVariant.copy(alpha = 0.3f),
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
        }

        // Checkbox
        Checkbox(
            checked = item.isChecked,
            onCheckedChange = onCheckedChange,
            colors = CheckboxDefaults.colors(
                checkedColor = NoteTheme.Primary,
                uncheckedColor = NoteTheme.OnSurfaceVariant.copy(alpha = 0.6f),
                checkmarkColor = NoteTheme.OnPrimary
            ),
            modifier = Modifier.size(24.dp)
        )

        Spacer(modifier = Modifier.width(8.dp))

        // Text field
        if (readOnly) {
            Text(
                text = item.text.ifEmpty { " " },
                style = MaterialTheme.typography.bodyLarge.copy(
                    textDecoration = if (item.isChecked) TextDecoration.LineThrough else TextDecoration.None
                ),
                color = textColor,
                modifier = Modifier.weight(1f)
            )
        } else {
            BasicTextField(
                value = item.text,
                onValueChange = onTextChange,
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester)
                    .onKeyEvent { event ->
                        if (event.key == Key.Backspace && item.text.isEmpty()) {
                            onBackspaceOnEmpty()
                            true
                        } else {
                            false
                        }
                    },
                textStyle = TextStyle(
                    color = textColor,
                    fontSize = MaterialTheme.typography.bodyLarge.fontSize,
                    textDecoration = if (item.isChecked) TextDecoration.LineThrough else TextDecoration.None
                ),
                cursorBrush = SolidColor(NoteTheme.Primary),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(
                    onNext = { onEnterPressed() }
                ),
                decorationBox = { innerTextField ->
                    Box {
                        if (item.text.isEmpty()) {
                            Text(
                                "Add item...",
                                style = MaterialTheme.typography.bodyLarge,
                                color = NoteTheme.OnSurfaceVariant.copy(alpha = 0.4f)
                            )
                        }
                        innerTextField()
                    }
                }
            )
        }

        // Delete button (editor only)
        if (!readOnly) {
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    Icons.Outlined.Close,
                    contentDescription = "Remove item",
                    tint = NoteTheme.OnSurfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/**
 * Progress bar display for checklist notes in list cards.
 */
@Composable
fun ChecklistProgressIndicator(
    checked: Int,
    total: Int,
    modifier: Modifier = Modifier
) {
    if (total <= 0) return

    val fraction = checked.toFloat() / total.toFloat()
    val progressColor by animateColorAsState(
        targetValue = if (fraction >= 1f) NoteTheme.Primary
        else NoteTheme.Secondary,
        animationSpec = tween(300),
        label = "progress_color"
    )

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Progress bar
        Box(
            modifier = Modifier
                .weight(1f)
                .size(height = 4.dp, width = 0.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(NoteTheme.OnSurfaceVariant.copy(alpha = 0.1f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .size(height = 4.dp, width = 0.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(progressColor)
            )
        }

        // Count text
        Text(
            text = "$checked/$total",
            style = MaterialTheme.typography.labelSmall,
            color = progressColor
        )
    }
}

