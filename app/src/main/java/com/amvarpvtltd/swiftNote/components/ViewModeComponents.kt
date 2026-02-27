package com.amvarpvtltd.swiftNote.components

import android.content.res.Configuration
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
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
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amvarpvtltd.swiftNote.dataclass
import com.amvarpvtltd.swiftNote.design.NoteTheme
import com.amvarpvtltd.swiftNote.utils.Constants
import kotlinx.coroutines.delay

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
    val hapticFeedback = LocalHapticFeedback.current
    val context = LocalContext.current

    // Compute adaptive colors based on current background and secondary accent
    val (containerColor, contentColor) = adaptiveIconColors(NoteTheme.Background, NoteTheme.Secondary)

    Card(
        modifier = modifier
            .clickable {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                val nextMode = when (currentViewMode) {
                    ViewMode.CARD -> ViewMode.LIST
                    ViewMode.LIST -> ViewMode.GRID
                    ViewMode.GRID -> ViewMode.CARD
                }

                val toastMessage = "📱 ${ViewModeManager.getViewModeLabel(nextMode)} activated"
                Toast.makeText(context, toastMessage, Toast.LENGTH_SHORT).show()
                onViewModeChange(nextMode)
            },
        shape = CircleShape,
        colors = CardDefaults.cardColors(
            containerColor = containerColor
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(
            modifier = Modifier.padding(Constants.PADDING_SMALL.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = ViewModeManager.getViewModeIcon(currentViewMode),
                contentDescription = ViewModeManager.getViewModeLabel(currentViewMode),
                tint = Color.Black,
                modifier = Modifier.size(Constants.ICON_SIZE_LARGE.dp)
            )
        }
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
    onReminder: (dataclass) -> Unit, // Added reminder action
    modifier: Modifier = Modifier
) {
    when (viewMode) {
        ViewMode.CARD -> NotesCardView(
            notes = notes,
            onView = onView,
            onEdit = onEdit,
            onDelete = onDelete,
            onShare = onShare,
            onReminder = onReminder, // Pass reminder action
            modifier = modifier
        )
        ViewMode.LIST -> NotesListView(
            notes = notes,
            onView = onView,
            onEdit = onEdit,
            onDelete = onDelete,
            onShare = onShare,
            onReminder = onReminder, // Pass reminder action
            modifier = modifier
        )
        ViewMode.GRID -> NotesGridView(
            notes = notes,
            onView = onView,
            onEdit = onEdit,
            onDelete = onDelete,
            onShare = onShare,
            onReminder = onReminder, // Pass reminder action
            modifier = modifier
        )
    }
}

// Card View (existing style)
@Composable
private fun NotesCardView(
    notes: List<dataclass>,
    onView: (dataclass) -> Unit,
    onEdit: (dataclass) -> Unit,
    onDelete: (dataclass) -> Unit,
    onShare: (dataclass) -> Unit,
    onReminder: (dataclass) -> Unit, // Added reminder action
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(
            start = Constants.PADDING_MEDIUM.dp,
            top = Constants.PADDING_SMALL.dp,
            end = Constants.PADDING_MEDIUM.dp,
            bottom = 100.dp
        ),
        verticalArrangement = Arrangement.spacedBy(Constants.PADDING_MEDIUM.dp)
    ) {
        itemsIndexed(
            items = notes,
            key = { _, note -> note.id }
        ) { index, note ->
            NoteCardItem(
                note = note,
                index = index,
                onView = onView,
                onEdit = onEdit,
                onDelete = onDelete,
                onShare = onShare,
                onReminder = onReminder, // Pass reminder action
                modifier = modifier
            )
        }
    }
}

// List View (compact horizontal layout)
@Composable
private fun NotesListView(
    notes: List<dataclass>,
    onView: (dataclass) -> Unit,
    onEdit: (dataclass) -> Unit,
    onDelete: (dataclass) -> Unit,
    onShare: (dataclass) -> Unit,
    onReminder: (dataclass) -> Unit, // Added reminder action
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(
            start = Constants.PADDING_MEDIUM.dp,
            top = Constants.PADDING_SMALL.dp,
            end = Constants.PADDING_MEDIUM.dp,
            bottom = 100.dp
        ),
        verticalArrangement = Arrangement.spacedBy(Constants.PADDING_SMALL.dp)
    ) {
        itemsIndexed(
            items = notes,
            key = { _, note -> note.id }
        ) { index, note ->
            NoteListItem(
                note = note,
                index = index,
                onView = onView,
                onEdit = onEdit,
                onDelete = onDelete,
                onShare = onShare,
                onReminder = onReminder, // Pass reminder action
            )
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
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val columns = if (isLandscape) Constants.GRID_COLUMNS_LANDSCAPE else Constants.GRID_COLUMNS_PORTRAIT

    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = modifier,
        contentPadding = PaddingValues(
            start = Constants.PADDING_MEDIUM.dp,
            top = Constants.PADDING_SMALL.dp,
            end = Constants.PADDING_MEDIUM.dp,
            bottom = 100.dp
        ),
        verticalArrangement = Arrangement.spacedBy(Constants.PADDING_SMALL.dp),
        horizontalArrangement = Arrangement.spacedBy(Constants.PADDING_SMALL.dp)
    ) {
        itemsIndexed(
            items = notes,
            key = { _, note -> note.id }
        ) { index, note ->
            NoteGridItem(
                note = note,
                index = index,
                onView = onView,
                onEdit = onEdit,
                onDelete = onDelete,
                onShare = onShare,
                onReminder = onReminder,

            )
        }
    }
}

// Card Item Component (enhanced with share)
@Composable
private fun NoteCardItem(
    note: dataclass,
    index: Int,
    onView: (dataclass) -> Unit,
    onEdit: (dataclass) -> Unit,
    onDelete: (dataclass) -> Unit,
    onShare: (dataclass) -> Unit,
    onReminder: (dataclass) -> Unit, // Added reminder action
    modifier: Modifier = Modifier
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    var isPressed by remember { mutableStateOf(false) }
    val hapticFeedback = LocalHapticFeedback.current

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "card_scale"
    )

    val cardColorPair = noteCardColors[index % noteCardColors.size]
    val backgroundColor = cardColorPair.first
    val accentColor = cardColorPair.second

    AnimatedVisibility(
        visible = true,
        enter = slideInVertically(
            initialOffsetY = { it },
            animationSpec = tween(durationMillis = 600, delayMillis = index * 100)
        ) + scaleIn(
            initialScale = 0.9f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
        ) + fadeIn(animationSpec = tween(durationMillis = 600, delayMillis = index * 100))
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .scale(scale)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    isPressed = true
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    onView(note)
                },
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = backgroundColor),
            elevation = CardDefaults.cardElevation(
                defaultElevation = if (isPressed) 8.dp else 4.dp
            )
        ) {
            Box {
                // Accent gradient overlay
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .background(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    accentColor.copy(alpha = 0.8f),
                                    accentColor.copy(alpha = 0.4f),
                                    Color.Transparent
                                )
                            )
                        )
                )

                Column(modifier = Modifier.padding(24.dp)) {
                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = note.title,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontSize = 20.sp,
                            lineHeight = 28.sp
                        ),
                        fontWeight = FontWeight.Bold,
                        color = Color.Black,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (note.description.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = note.description,
                            style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 24.sp),
                            color = Color.Black.copy(alpha = 0.7f),
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconActionButton(
                            onClick = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                onShare(note)
                            },
                            icon = Icons.Outlined.Share,
                            contentDescription = "Share",
                            containerColor = NoteTheme.Primary.copy(alpha = 0.1f),
                            contentColor = NoteTheme.Primary
                        )

                        Spacer(modifier = Modifier.width(12.dp))

                        IconActionButton(
                            onClick = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                showDeleteDialog = true
                            },
                            icon = Icons.Outlined.Delete,
                            contentDescription = "Delete",
                            containerColor = NoteTheme.Error.copy(alpha = 0.1f),
                            contentColor = NoteTheme.Error
                        )

                        Spacer(modifier = Modifier.width(12.dp))

                        IconActionButton(
                            onClick = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                onEdit(note)
                            },
                            icon = Icons.Outlined.Edit,
                            contentDescription = "Edit",
                            containerColor = accentColor.copy(alpha = 0.1f),
                            contentColor = accentColor
                        )

                        // Reminder button
                        Spacer(modifier = Modifier.width(12.dp))
                        IconActionButton(
                            onClick = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                onReminder(note)
                            },
                            icon = Icons.Outlined.Alarm,
                            contentDescription = "Set Reminder",
                            containerColor = NoteTheme.Secondary.copy(alpha = 0.1f),
                            contentColor = NoteTheme.Secondary
                        )
                    }
                }
            }
        }
    }

    DeleteConfirmationDialog(
        showDialog = showDeleteDialog,
        onDismiss = { showDeleteDialog = false },
        onConfirm = {
            showDeleteDialog = false
            onDelete(note)
        },
        title = note.title,
        message = "Are you sure you want to delete:"
    )

    LaunchedEffect(isPressed) {
        if (isPressed) {
            delay(100)
            isPressed = false
        }
    }
}

// List Item Component (compact horizontal layout)
@Composable
private fun NoteListItem(
    note: dataclass,
    index: Int,
    onView: (dataclass) -> Unit,
    onEdit: (dataclass) -> Unit,
    onDelete: (dataclass) -> Unit,
    onShare: (dataclass) -> Unit,
    onReminder: (dataclass) -> Unit, // Added reminder action
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    var isPressed by remember { mutableStateOf(false) }
    val hapticFeedback = LocalHapticFeedback.current

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "list_scale"
    )

    AnimatedVisibility(
        visible = true,
        enter = slideInVertically(
            initialOffsetY = { it / 4 },
            animationSpec = tween(durationMillis = 400, delayMillis = index * 50)
        ) + fadeIn(animationSpec = tween(durationMillis = 400, delayMillis = index * 50))
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .scale(scale)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    isPressed = true
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onView(note)
                },
            shape = RoundedCornerShape(Constants.CORNER_RADIUS_MEDIUM.dp),
            colors = CardDefaults.cardColors(containerColor = NoteTheme.Surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Constants.PADDING_MEDIUM.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Content section
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = note.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = NoteTheme.OnSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (note.description.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = note.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = NoteTheme.OnSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Action buttons
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onShare(note)
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Outlined.Share,
                            contentDescription = "Share",
                            tint = NoteTheme.Primary,
                            modifier = Modifier.size(Constants.ICON_SIZE_MEDIUM.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onEdit(note)
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Outlined.Edit,
                            contentDescription = "Edit",
                            tint = NoteTheme.Secondary,
                            modifier = Modifier.size(Constants.ICON_SIZE_MEDIUM.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                            showDeleteDialog = true
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Outlined.Delete,
                            contentDescription = "Delete",
                            tint = NoteTheme.Error,
                            modifier = Modifier.size(Constants.ICON_SIZE_MEDIUM.dp)
                        )
                    }

                    // Reminder button
                    IconButton(
                        onClick = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                            onReminder(note)
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Outlined.Alarm,
                            contentDescription = "Set Reminder",
                            tint = NoteTheme.Secondary,
                            modifier = Modifier.size(Constants.ICON_SIZE_MEDIUM.dp)
                        )
                    }
                }
            }
        }
    }

    DeleteConfirmationDialog(
        showDialog = showDeleteDialog,
        onDismiss = { showDeleteDialog = false },
        onConfirm = {
            showDeleteDialog = false
            onDelete(note)
        },
        title = note.title,
        message = "Are you sure you want to delete:"
    )

    LaunchedEffect(isPressed) {
        if (isPressed) {
            delay(100)
            isPressed = false
        }
    }
}

// Grid Item Component (compact card layout)
@Composable
private fun NoteGridItem(
    note: dataclass,
    index: Int,
    onView: (dataclass) -> Unit,
    onEdit: (dataclass) -> Unit,
    onDelete: (dataclass) -> Unit,
    onShare: (dataclass) -> Unit,
    onReminder: (dataclass) -> Unit, // Added reminder action
    onCopy: ((dataclass) -> Unit)? = null
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    var isPressed by remember { mutableStateOf(false) }
    val hapticFeedback = LocalHapticFeedback.current

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "grid_scale"
    )

    val cardColorPair = noteCardColors[index % noteCardColors.size]
    val backgroundColor = cardColorPair.first
    val accentColor = cardColorPair.second

    AnimatedVisibility(
        visible = true,
        enter = scaleIn(
            initialScale = 0.8f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
        ) + fadeIn(animationSpec = tween(durationMillis = 400, delayMillis = index * 100))
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(Constants.GRID_ITEM_MIN_HEIGHT.dp)
                .scale(scale)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    isPressed = true
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onView(note)
                },
            shape = RoundedCornerShape(Constants.CORNER_RADIUS_MEDIUM.dp),
            colors = CardDefaults.cardColors(containerColor = backgroundColor),
            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
        ) {
            Box {
                // Accent line at top
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .background(accentColor)
                )

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(Constants.PADDING_SMALL.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Content section
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = note.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        if (note.description.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = note.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Black.copy(alpha = 0.7f),
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Action buttons row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onShare(note)
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                Icons.Outlined.Share,
                                contentDescription = "Share",
                                tint = accentColor,
                                modifier = Modifier.size(Constants.ICON_SIZE_SMALL.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onEdit(note)
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                Icons.Outlined.Edit,
                                contentDescription = "Edit",
                                tint = accentColor,
                                modifier = Modifier.size(Constants.ICON_SIZE_SMALL.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                showDeleteDialog = true
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                Icons.Outlined.Delete,
                                contentDescription = "Delete",
                                tint = Color.Red.copy(alpha = 0.8f),
                                modifier = Modifier.size(Constants.ICON_SIZE_SMALL.dp)
                            )
                        }

                        // Reminder button
                        IconButton(
                            onClick = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                onReminder(note)
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                Icons.Outlined.Alarm,
                                contentDescription = "Set Reminder",
                                tint = accentColor,
                                modifier = Modifier.size(Constants.ICON_SIZE_SMALL.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    DeleteConfirmationDialog(
        showDialog = showDeleteDialog,
        onDismiss = { showDeleteDialog = false },
        onConfirm = {
            showDeleteDialog = false
            onDelete(note)
        },
        title = note.title,
        message = "Are you sure you want to delete:"
    )

    LaunchedEffect(isPressed) {
        if (isPressed) {
            delay(100)
            isPressed = false
        }
    }
}

// Define note card colors for compatibility
val noteCardColors = listOf(
    Color(0xFFFFFBEB) to Color(0xFFF59E0B), // Soft Amber
    Color(0xFFF0F9FF) to Color(0xFF0284C7), // Soft Blue
    Color(0xFFF0FDF4) to Color(0xFF059669), // Soft Green
    Color(0xFFFDF2F8) to Color(0xFFDB2777), // Soft Pink
    Color(0xFFF3E8FF) to Color(0xFF7C3AED), // Soft Purple
    Color(0xFFFFF1F2) to Color(0xFFDC2626), // Soft Red
    Color(0xFFF0F4FF) to Color(0xFF4F46E5), // Soft Indigo
    Color(0xFFECFDF5) to Color(0xFF047857), // Soft Emerald
    Color(0xFFFFF7ED) to Color(0xFFEA580C), // Soft Orange
    Color(0xFFEFFAFD) to Color(0xFF0EA5E9)  // Soft Cyan
)
