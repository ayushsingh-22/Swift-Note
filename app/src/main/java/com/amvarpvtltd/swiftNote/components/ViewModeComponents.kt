package com.amvarpvtltd.swiftNote.components

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amvarpvtltd.swiftNote.dataclass
import com.amvarpvtltd.swiftNote.design.NoteTheme
import com.amvarpvtltd.swiftNote.richtext.RichTextBridge
import com.amvarpvtltd.swiftNote.utils.Constants
import kotlinx.coroutines.delay
import com.amvarpvtltd.swiftNote.utils.rememberResponsiveDimensions

// View Mode enum
enum class ViewMode {
    LIST, GRID, CARD
}

// View Mode Manager
object ViewModeManager {
    fun getViewMode(context: android.content.Context): ViewMode {
        val prefs = context.getSharedPreferences(Constants.VIEW_MODE_PREFERENCES, android.content.Context.MODE_PRIVATE)
        val viewModeName = prefs.getString(Constants.VIEW_MODE_KEY, Constants.DEFAULT_VIEW_MODE)
        return when (viewModeName) {
            Constants.VIEW_MODE_LIST -> ViewMode.LIST
            Constants.VIEW_MODE_GRID -> ViewMode.GRID
            else -> ViewMode.CARD
        }
    }

    fun setViewMode(context: android.content.Context, viewMode: ViewMode) {
        val prefs = context.getSharedPreferences(Constants.VIEW_MODE_PREFERENCES, android.content.Context.MODE_PRIVATE)
        val viewModeName = when (viewMode) {
            ViewMode.LIST -> Constants.VIEW_MODE_LIST
            ViewMode.GRID -> Constants.VIEW_MODE_GRID
            ViewMode.CARD -> Constants.VIEW_MODE_CARD
        }
        prefs.edit().putString(Constants.VIEW_MODE_KEY, viewModeName).apply()
    }

    fun getViewModeIcon(viewMode: ViewMode): androidx.compose.ui.graphics.vector.ImageVector {
        return when (viewMode) {
            ViewMode.LIST -> Icons.AutoMirrored.Outlined.ViewList
            ViewMode.GRID -> Icons.Outlined.GridView
            ViewMode.CARD -> Icons.Outlined.ViewModule
        }
    }

    fun getViewModeLabel(viewMode: ViewMode): String {
        return when (viewMode) {
            ViewMode.LIST -> "List View"
            ViewMode.GRID -> "Grid View"
            ViewMode.CARD -> "Card View"
        }
    }
}

@Composable
fun rememberViewModeState(): MutableState<ViewMode> {
    val context = LocalContext.current
    return remember {
        mutableStateOf(ViewModeManager.getViewMode(context))
    }
}

// View Mode Toggle Button
@Composable
fun ViewModeToggleButton(
    currentViewMode: ViewMode,
    onViewModeChange: (ViewMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val dims = rememberResponsiveDimensions()
    val hapticFeedback = LocalHapticFeedback.current
    val context = LocalContext.current

    // Compute adaptive colors based on current background and secondary accent
    val (containerColor, contentColor) = adaptiveIconColors(NoteTheme.Background, NoteTheme.Secondary)

    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(containerColor)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                val nextMode = when (currentViewMode) {
                    ViewMode.CARD -> ViewMode.LIST
                    ViewMode.LIST -> ViewMode.GRID
                    ViewMode.GRID -> ViewMode.CARD
                }
                val toastMessage = "📱 ${ViewModeManager.getViewModeLabel(nextMode)} activated"
                Toast.makeText(context, toastMessage, Toast.LENGTH_SHORT).show()
                onViewModeChange(nextMode)
            }
            .padding(dims.paddingSmall),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = ViewModeManager.getViewModeIcon(currentViewMode),
            contentDescription = ViewModeManager.getViewModeLabel(currentViewMode),
            tint = contentColor,
            modifier = Modifier.size(dims.iconLarge)
        )
    }
}

// Notes Display with different view modes
@Composable
fun NotesDisplay(
    notes: List<dataclass>,
    viewMode: ViewMode,
    onView: (dataclass) -> Unit,
    onEdit: (dataclass) -> Unit,
    onDelete: (dataclass) -> Unit,
    onShare: (dataclass) -> Unit,
    onReminder: (dataclass) -> Unit,
    onPin: (dataclass) -> Unit = {},
    onArchive: (dataclass) -> Unit = {},
    modifier: Modifier = Modifier,
    showSections: Boolean = false
) {
    val displayNotes = remember(notes, showSections) {
        if (showSections) notes.sortedByDescending { it.isPinned } else notes
    }
    when (viewMode) {
        ViewMode.CARD -> NotesCardView(
            notes = displayNotes,
            onView = onView,
            onEdit = onEdit,
            onDelete = onDelete,
            onShare = onShare,
            onReminder = onReminder,
            onPin = onPin,
            onArchive = onArchive,
            modifier = modifier,
            showSections = showSections
        )
        ViewMode.LIST -> NotesListView(
            notes = displayNotes,
            onView = onView,
            onEdit = onEdit,
            onDelete = onDelete,
            onShare = onShare,
            onReminder = onReminder,
            onPin = onPin,
            onArchive = onArchive,
            modifier = modifier,
            showSections = showSections
        )
        ViewMode.GRID -> NotesGridView(
            notes = displayNotes,
            onView = onView,
            onEdit = onEdit,
            onDelete = onDelete,
            onShare = onShare,
            onReminder = onReminder,
            onPin = onPin,
            onArchive = onArchive,
            modifier = modifier,
            showSections = showSections
        )
    }
}

// Card View (existing style with swipe-to-action)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotesCardView(
    notes: List<dataclass>,
    onView: (dataclass) -> Unit,
    onEdit: (dataclass) -> Unit,
    onDelete: (dataclass) -> Unit,
    onShare: (dataclass) -> Unit,
    onReminder: (dataclass) -> Unit,
    onPin: (dataclass) -> Unit,
    onArchive: (dataclass) -> Unit,
    modifier: Modifier = Modifier,
    showSections: Boolean = false
) {
    val dims = rememberResponsiveDimensions()
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(
            start = dims.paddingMedium,
            top = dims.paddingSmall,
            end = dims.paddingMedium,
            bottom = 100.dp
        ),
        verticalArrangement = Arrangement.spacedBy(dims.paddingSmall)
    ) {
        itemsIndexed(
            items = notes,
            key = { _, note -> note.id },
            contentType = { _, _ -> "note" }
        ) { index, note ->
            if (showSections && (index == 0 || notes[index - 1].isPinned != note.isPinned)) {
                SwiftNoteSectionHeader(if (note.isPinned) "Pinned" else "Recent notes")
            }
            val dismissState = rememberSwipeToDismissBoxState(
                confirmValueChange = { dismissValue ->
                    when (dismissValue) {
                        SwipeToDismissBoxValue.EndToStart -> {
                            onDelete(note)
                            true
                        }
                        else -> false
                    }
                }
            )

            SwipeToDismissBox(
                state = dismissState,
                enableDismissFromStartToEnd = false,
                backgroundContent = {
                    val color by animateColorAsState(
                        when (dismissState.targetValue) {
                            SwipeToDismissBoxValue.EndToStart -> NoteTheme.Error.copy(alpha = 0.15f)
                            else -> Color.Transparent
                        },
                        label = "swipe_bg"
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(16.dp))
                            .background(color)
                            .padding(horizontal = 20.dp),
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        if (dismissState.targetValue == SwipeToDismissBoxValue.EndToStart) {
                            Icon(
                                imageVector = Icons.Outlined.Delete,
                                contentDescription = null,
                                tint = NoteTheme.Error,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                },
                modifier = Modifier.animateItem()
            ) {
                NoteCardItem(
                    note = note,
                    index = index,
                    onView = onView,
                    onEdit = onEdit,
                    onDelete = onDelete,
                    onShare = onShare,
                    onReminder = onReminder,
                    onPin = onPin,
                    onArchive = onArchive,
                    modifier = modifier
                )
            }
        }
    }
}

// List View (compact horizontal layout with swipe-to-action)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotesListView(
    notes: List<dataclass>,
    onView: (dataclass) -> Unit,
    onEdit: (dataclass) -> Unit,
    onDelete: (dataclass) -> Unit,
    onShare: (dataclass) -> Unit,
    onReminder: (dataclass) -> Unit,
    onPin: (dataclass) -> Unit,
    onArchive: (dataclass) -> Unit,
    modifier: Modifier = Modifier,
    showSections: Boolean = false
) {
    val dims = rememberResponsiveDimensions()
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(
            start = dims.paddingMedium,
            top = dims.paddingSmall,
            end = dims.paddingMedium,
            bottom = 100.dp
        ),
        verticalArrangement = Arrangement.spacedBy(dims.paddingSmall)
    ) {
        itemsIndexed(
            items = notes,
            key = { _, note -> note.id },
            contentType = { _, _ -> "note" }
        ) { index, note ->
            if (showSections && (index == 0 || notes[index - 1].isPinned != note.isPinned)) {
                SwiftNoteSectionHeader(if (note.isPinned) "Pinned" else "Recent notes")
            }
            val dismissState = rememberSwipeToDismissBoxState(
                confirmValueChange = { dismissValue ->
                    when (dismissValue) {
                        SwipeToDismissBoxValue.EndToStart -> {
                            onDelete(note)
                            true
                        }
                        else -> false
                    }
                }
            )

            SwipeToDismissBox(
                state = dismissState,
                enableDismissFromStartToEnd = false,
                backgroundContent = {
                    val color by animateColorAsState(
                        when (dismissState.targetValue) {
                            SwipeToDismissBoxValue.EndToStart -> NoteTheme.Error.copy(alpha = 0.15f)
                            else -> Color.Transparent
                        },
                        label = "swipe_bg_list"
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(NoteTheme.Radius.md.dp))
                            .background(color)
                            .padding(horizontal = 20.dp),
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        if (dismissState.targetValue == SwipeToDismissBoxValue.EndToStart) {
                            Icon(
                                imageVector = Icons.Outlined.Delete,
                                contentDescription = null,
                                tint = NoteTheme.Error,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                },
                modifier = Modifier.animateItem()
            ) {
                NoteListItem(
                    note = note,
                    index = index,
                    onView = onView,
                    onEdit = onEdit,
                    onDelete = onDelete,
                    onShare = onShare,
                    onReminder = onReminder,
                    onPin = onPin,
                    onArchive = onArchive,
                )
            }
        }
    }
}

// Grid View (2-3 columns)
@Composable
private fun NotesGridView(
    notes: List<dataclass>,
    onView: (dataclass) -> Unit,
    onEdit: (dataclass) -> Unit,
    onDelete: (dataclass) -> Unit,
    onShare: (dataclass) -> Unit,
    onReminder: (dataclass) -> Unit,
    onPin: (dataclass) -> Unit,
    onArchive: (dataclass) -> Unit,
    modifier: Modifier = Modifier,
    showSections: Boolean = false
) {
    val dims = rememberResponsiveDimensions()
    // Adaptive columns: fits as many ~150dp-wide cards as possible.
    // - 320dp compact phone → 2 columns (still readable, no cramped 1-col)
    // - 360-410dp normal phone → 2 columns
    // - 600dp+ foldable/tablet portrait → 3-4 columns
    // - landscape → automatically more columns based on width
    // Replaces orientation branching with width-based adaptation.
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 150.dp),
        modifier = modifier,
        contentPadding = PaddingValues(
            start = dims.paddingMedium,
            top = dims.paddingSmall,
            end = dims.paddingMedium,
            bottom = 100.dp
        ),
        verticalArrangement = Arrangement.spacedBy(dims.paddingSmall),
        horizontalArrangement = Arrangement.spacedBy(dims.paddingSmall)
    ) {
        if (showSections) {
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                SwiftNoteSectionHeader(if (notes.any { it.isPinned }) "Pinned & recent" else "Recent notes")
            }
        }
        itemsIndexed(
            items = notes,
            key = { _, note -> note.id },
            contentType = { _, _ -> "note" }
        ) { index, note ->
            NoteGridItem(
                note = note,
                index = index,
                onView = onView,
                onEdit = onEdit,
                onDelete = onDelete,
                onShare = onShare,
                onReminder = onReminder,
                onPin = onPin,
                onArchive = onArchive,
            )
        }
    }
}

// Card Item Component (enhanced with pin/archive)
@Composable
private fun NoteCardItem(
    note: dataclass, index: Int,
    onView: (dataclass) -> Unit, onEdit: (dataclass) -> Unit,
    onDelete: (dataclass) -> Unit, onShare: (dataclass) -> Unit,
    onReminder: (dataclass) -> Unit, onPin: (dataclass) -> Unit,
    onArchive: (dataclass) -> Unit, modifier: Modifier = Modifier
) {
    SwiftNoteNoteCard(note, onView, onEdit, onDelete, onShare, onReminder,
        modifier = Modifier, onPin = onPin, onArchive = onArchive)
}

@Composable
private fun NoteListItem(
    note: dataclass, index: Int,
    onView: (dataclass) -> Unit, onEdit: (dataclass) -> Unit,
    onDelete: (dataclass) -> Unit, onShare: (dataclass) -> Unit,
    onReminder: (dataclass) -> Unit,
    onPin: (dataclass) -> Unit, onArchive: (dataclass) -> Unit
) {
    SwiftNoteNoteCard(note, onView, onEdit, onDelete, onShare, onReminder,
        compact = true, onPin = onPin, onArchive = onArchive)
}

@Composable
private fun NoteGridItem(
    note: dataclass, index: Int,
    onView: (dataclass) -> Unit, onEdit: (dataclass) -> Unit,
    onDelete: (dataclass) -> Unit, onShare: (dataclass) -> Unit,
    onReminder: (dataclass) -> Unit,
    onPin: (dataclass) -> Unit, onArchive: (dataclass) -> Unit,
    onCopy: ((dataclass) -> Unit)? = null
) {
    SwiftNoteNoteCard(note, onView, onEdit, onDelete, onShare, onReminder,
        compact = true, onPin = onPin, onArchive = onArchive, onCopy = onCopy)
}

@Composable
fun noteCardColors(): List<Pair<Color, Color>> = NoteTheme.NoteAccentColors.map { NoteTheme.Surface to it }
