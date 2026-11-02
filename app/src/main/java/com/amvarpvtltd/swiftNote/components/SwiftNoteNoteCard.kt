package com.amvarpvtltd.swiftNote.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.amvarpvtltd.swiftNote.categories.CategoryManager
import com.amvarpvtltd.swiftNote.richtext.NotePreviewFormatter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.style.TextDecoration
import com.amvarpvtltd.swiftNote.dataclass
import com.amvarpvtltd.swiftNote.design.NoteTheme
import com.amvarpvtltd.swiftNote.richtext.RichTextBridge
import java.text.DateFormat
import java.util.Date

/** One note presentation across home, archive, grid and list. Every existing action remains available. */
@Composable
fun SwiftNoteNoteCard(
    note: dataclass,
    onView: (dataclass) -> Unit,
    onEdit: (dataclass) -> Unit,
    onDelete: (dataclass) -> Unit,
    onShare: (dataclass) -> Unit,
    onReminder: (dataclass) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    onPin: ((dataclass) -> Unit)? = null,
    onArchive: ((dataclass) -> Unit)? = null,
    onCopy: ((dataclass) -> Unit)? = null
) {
    var menuOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val categoryColor = remember(context, note.category) {
        CategoryManager.getCategoryColor(context, note.category)
    } ?: colors.primary
    val title = remember(note.title) { RichTextBridge.stripHtmlToPlainText(note.title) }
    val preview = remember(note.description) { NotePreviewFormatter.create(note.description) }
    val date = remember(note.timestamp) { DateFormat.getDateInstance(DateFormat.SHORT).format(Date(note.timestamp)) }

    Card(
        onClick = { onView(note) },
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = lerp(colors.surface, categoryColor, if (note.isPinned) .09f else .035f)
        ),
        border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = .65f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(title.ifBlank { "Untitled" }, style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold, color = colors.onSurface,
                    maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (note.isPinned) {
                    Icon(Icons.Outlined.PushPin, "Pinned", tint = colors.primary, modifier = Modifier.size(15.dp))
                }
                Box {
                    IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Outlined.MoreHoriz, "Note actions", tint = colors.onSurfaceVariant)
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(text = { Text("Edit") }, leadingIcon = { Icon(Icons.Outlined.Edit, null) },
                            onClick = { menuOpen = false; onEdit(note) })
                        onPin?.let { action ->
                            DropdownMenuItem(text = { Text(if (note.isPinned) "Unpin" else "Pin") },
                                leadingIcon = { Icon(Icons.Outlined.PushPin, null) },
                                onClick = { menuOpen = false; action(note) })
                        }
                        onArchive?.let { action ->
                            DropdownMenuItem(text = { Text(if (note.isArchived) "Restore" else "Archive") },
                                leadingIcon = { Icon(if (note.isArchived) Icons.Outlined.Unarchive else Icons.Outlined.Archive, null) },
                                onClick = { menuOpen = false; action(note) })
                        }
                        DropdownMenuItem(text = { Text("Set reminder") }, leadingIcon = { Icon(Icons.Outlined.Alarm, null) },
                            onClick = { menuOpen = false; onReminder(note) })
                        DropdownMenuItem(text = { Text("Share") }, leadingIcon = { Icon(Icons.Outlined.Share, null) },
                            onClick = { menuOpen = false; onShare(note) })
                        onCopy?.let { action ->
                            DropdownMenuItem(text = { Text("Copy") }, leadingIcon = { Icon(Icons.Outlined.ContentCopy, null) },
                                onClick = { menuOpen = false; action(note) })
                        }
                        DropdownMenuItem(text = { Text("Delete", color = colors.error) },
                            leadingIcon = { Icon(Icons.Outlined.DeleteOutline, null, tint = colors.error) },
                            onClick = { menuOpen = false; confirmDelete = true })
                    }
                }
            }
            if (preview.checklist && preview.lines.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    preview.lines.take(if (compact) 2 else 3).forEach { line ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(
                                if (line.checked) Icons.Outlined.CheckBox else Icons.Outlined.CheckBoxOutlineBlank,
                                null, tint = if (line.checked) colors.primary else colors.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(line.text, style = MaterialTheme.typography.bodySmall.copy(
                                textDecoration = if (line.checked) TextDecoration.LineThrough else TextDecoration.None),
                                color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            } else if (preview.body.isNotBlank()) {
                Text(preview.body, style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant, maxLines = if (compact) 2 else 3,
                    overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (note.category.isNotBlank()) {
                    Box(Modifier.size(6.dp).background(categoryColor, CircleShape))
                    Text(note.category, style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f))
                } else Spacer(Modifier.weight(1f))
                if (preview.checklist && preview.total > 0) {
                    Text("${preview.checked}/${preview.total} done",
                        style = MaterialTheme.typography.labelSmall, color = colors.primary)
                }
                Text(date, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            icon = { Icon(Icons.Outlined.DeleteOutline, null, tint = colors.error) },
            title = { Text("Delete note?") },
            text = { Text("Delete “${title.ifBlank { "Untitled" }}”? This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete(note) }) {
                    Text("Delete", color = colors.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
        )
    }
}
