package com.amvarpvtltd.swiftNote.design

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.ripple
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOn
import com.amvarpvtltd.swiftNote.richtext.NotePreviewFormatter
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.amvarpvtltd.swiftNote.components.NoteScreenBackground
import com.amvarpvtltd.swiftNote.reminders.ReminderEntity
import com.amvarpvtltd.swiftNote.room.AppDatabase
import com.amvarpvtltd.swiftNote.room.NoteEntityMapper
import com.amvarpvtltd.swiftNote.utils.rememberResponsiveDimensions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Today / Daily Review Screen — redesigned with minimal, clean aesthetic
 * matching the app's premium design language.
 */

// ─── Filter Enum ───────────────────────────────────────────────────────────────

enum class ReminderFilter(val label: String, val icon: ImageVector) {
    TODAY("Today", Icons.Outlined.Today),
    UPCOMING("Upcoming", Icons.Outlined.Upcoming),
    PAST("Past", Icons.Outlined.History)
}

// ─── Display Models ────────────────────────────────────────────────────────────

private data class ReminderDisplayItem(
    val reminder: ReminderEntity,
    val noteTitle: String,
    val noteSnippet: String,
    val isOverdue: Boolean
)

private data class PinnedNoteItem(
    val id: String,
    val title: String,
    val snippet: String,
    val category: String,
    val colorKey: String?
)

// ─── Main Screen ───────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(navController: NavHostController) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getInstance(context) }

    // Reactive `now` — updated every 30s for live filter accuracy
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000L)
            now = System.currentTimeMillis()
        }
    }

    val todayStart = remember(now) {
        Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
    val todayEnd = remember(todayStart) { todayStart + 24 * 60 * 60 * 1000 }

    // Reactive data from Room
    val activeFlow = remember(db) { db.reminderDao().getAllActiveReminders() }
    val allFlow = remember(db) { db.reminderDao().observeAllReminders() }
    val activeReminders by activeFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val previewFlow = remember(allFlow) {
        allFlow.map { reminders ->
            reminders to reminders.associate {
                it.id to NotePreviewFormatter.create(it.noteDescription).plainText.take(100)
            }
        }.flowOn(Dispatchers.Default)
    }
    val reminderSnapshot by previewFlow.collectAsStateWithLifecycle(
        initialValue = emptyList<ReminderEntity>() to emptyMap<String, String>()
    )
    val allReminders = reminderSnapshot.first
    val snippets = reminderSnapshot.second
    val scope = rememberCoroutineScope()
    var showPinned by rememberSaveable { mutableStateOf(false) }


    // Filter state
    var selectedFilter by rememberSaveable { mutableStateOf(ReminderFilter.TODAY) }

    // Compute filtered reminders
    val filteredReminders = remember(activeReminders, allReminders, snippets, selectedFilter, now, todayStart, todayEnd) {
        when (selectedFilter) {
            ReminderFilter.TODAY -> allReminders.filter {
                it.reminderTime in todayStart..todayEnd
            }
            ReminderFilter.UPCOMING -> activeReminders.filter {
                it.reminderTime > todayEnd
            }
            ReminderFilter.PAST -> allReminders.filter {
                it.reminderTime < todayStart ||
                (it.reminderTime < now && !it.isActive)
            }
        }.sortedByDescending { it.reminderTime }
            .map { reminder ->
                ReminderDisplayItem(
                    reminder = reminder,
                    noteTitle = reminder.noteTitle.ifBlank { "Untitled" },
                    noteSnippet = snippets[reminder.id].orEmpty(),
                    isOverdue = reminder.reminderTime < now
                )
            }
    }

    // Reminder counts for badges
    val reminderCounts = remember(activeReminders, allReminders, now, todayStart, todayEnd) {
        mapOf(
            ReminderFilter.TODAY to allReminders.count { it.reminderTime in todayStart..todayEnd },
            ReminderFilter.UPCOMING to activeReminders.count { it.reminderTime > todayEnd },
            ReminderFilter.PAST to allReminders.count {
                it.reminderTime < todayStart || (it.reminderTime < now && !it.isActive)
            }
        )
    }

    // Decrypt and project pinned previews off the UI thread, once per Room emission.
    val pinnedFlow = remember(db) {
        db.noteDao().observePinnedNotes().map { entities ->
            entities.take(8).map { entity ->
                val domain = NoteEntityMapper.toDomain(entity)
                PinnedNoteItem(
                    id = domain.id,
                    title = domain.title.ifBlank { "Untitled" },
                    snippet = NotePreviewFormatter.create(domain.description).plainText.take(100),
                    category = domain.category,
                    colorKey = domain.colorKey
                )
            }
        }.flowOn(Dispatchers.IO)
    }
    val pinnedNotes by pinnedFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    NoteScreenBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                com.amvarpvtltd.swiftNote.components.SwiftNoteTopBar(
                    title = "Reminders",
                    subtitle = getFormattedDate(),
                    onBack = { navController.popBackStack() }
                )
            }
        ) { paddingValues ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // ─── Filter Tabs ─────────────────────────────────────────────
                item {
                    FilterTabRow(
                        selectedFilter = selectedFilter,
                        onFilterChange = { selectedFilter = it },
                        counts = reminderCounts
                    )
                }

                // ─── Reminders ───────────────────────────────────────────────
                if (filteredReminders.isEmpty()) {
                    item {
                        EmptyReminderState(filter = selectedFilter)
                    }
                } else {
                    items(filteredReminders, key = { it.reminder.id }, contentType = { "reminder" }) { item ->
                        ReminderRow(
                            item = item,
                            selectedFilter = selectedFilter,
                            onClick = {
                                navController.navigate("viewnote/${item.reminder.noteId}")
                            },
                            onMarkDone = {
                                scope.launch(Dispatchers.IO) {
                                    db.reminderDao().deactivateReminder(item.reminder.id)
                                }
                            }
                        )
                    }
                }

                // ─── Pinned Notes ────────────────────────────────────────────
                if (pinnedNotes.isNotEmpty()) {
                    item(key = "pinned-toggle") {
                        TextButton(onClick = { showPinned = !showPinned }) {
                            Icon(Icons.Outlined.PushPin, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(if (showPinned) "Hide pinned notes" else "Pinned notes")
                            Icon(if (showPinned) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null)
                        }
                    }

                    if (showPinned) item(key = "pinned-notes") {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(horizontal = 2.dp)
                        ) {
                            items(pinnedNotes, key = { it.id }) { note ->
                                PinnedNoteChip(
                                    note = note,
                                    onClick = { navController.navigate("viewnote/${note.id}") }
                                )
                            }
                        }
                    }
                }

                // Bottom spacer
                item { Spacer(modifier = Modifier.height(32.dp)) }
            }
        }
    }
}

// ─── Greeting Banner ───────────────────────────────────────────────────────────

// ─── Filter Tab Row ────────────────────────────────────────────────────────────

@Composable
private fun FilterTabRow(
    selectedFilter: ReminderFilter,
    onFilterChange: (ReminderFilter) -> Unit,
    counts: Map<ReminderFilter, Int>
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.2f

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ReminderFilter.entries.forEach { filter ->
            val isSelected = selectedFilter == filter
            val count = counts[filter] ?: 0

            Surface(
                onClick = { onFilterChange(filter) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                color = when {
                    isSelected -> MaterialTheme.colorScheme.primary
                    isDark -> MaterialTheme.colorScheme.surfaceVariant
                    else -> MaterialTheme.colorScheme.surface
                },
                shadowElevation = 0.dp,
                // Stronger border — visible in both light and dark mode
                border = when {
                    isSelected -> null
                    isDark -> BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
                    else -> BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f))
                }
            ) {
                Row(
                    modifier = Modifier.heightIn(min = 48.dp).padding(horizontal = 6.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (count > 0) "${filter.label} $count" else filter.label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

// ─── Reminder Row (compact, minimal) ──────────────────────────────────────────

@Composable
private fun ReminderRow(
    item: ReminderDisplayItem,
    selectedFilter: ReminderFilter,
    onClick: () -> Unit,
    onMarkDone: () -> Unit
) {
    val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    val dateFormat = remember { SimpleDateFormat("MMM d", Locale.getDefault()) }

    val accentColor = when {
        item.isOverdue && selectedFilter == ReminderFilter.TODAY -> MaterialTheme.colorScheme.error
        selectedFilter == ReminderFilter.PAST -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.primary
    }

    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.2f
    val rowBackground = MaterialTheme.colorScheme.surface

    // Border is more visible in light mode; dark mode uses a slightly brighter stroke
    val rowBorderColor = if (isDark)
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
    else
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .7f), RoundedCornerShape(18.dp))
            .clip(RoundedCornerShape(18.dp))
            .background(rowBackground)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true)
            ) { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Left accent indicator
        Box(
            modifier = Modifier
                .padding(top = 4.dp)
                .size(8.dp)
                .clip(CircleShape)
                .background(accentColor.copy(alpha = if (selectedFilter == ReminderFilter.PAST) 0.4f else 0.8f))
        )

        Spacer(modifier = Modifier.width(12.dp))

        // Content
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.noteTitle,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (selectedFilter == ReminderFilter.PAST)
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                letterSpacing = (-0.2).sp
            )

            if (item.noteSnippet.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = item.noteSnippet,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 16.sp
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Time row
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.Schedule,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = accentColor.copy(alpha = 0.8f)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = when (selectedFilter) {
                        ReminderFilter.TODAY -> timeFormat.format(item.reminder.reminderTime)
                        ReminderFilter.UPCOMING -> "${dateFormat.format(item.reminder.reminderTime)} · ${timeFormat.format(item.reminder.reminderTime)}"
                        ReminderFilter.PAST -> {
                            val ago = getRelativeTimeString(item.reminder.reminderTime)
                            "${dateFormat.format(item.reminder.reminderTime)} · $ago"
                        }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = accentColor.copy(alpha = 0.8f),
                    fontWeight = FontWeight.Medium,
                    fontSize = 11.sp
                )

                if (item.reminder.isRecurring) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        Icons.Outlined.EventRepeat,
                        contentDescription = "Recurring",
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }

                if (item.isOverdue && selectedFilter == ReminderFilter.TODAY) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Overdue",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 10.sp
                    )
                }
            }
        }

        // Right action
        if (selectedFilter != ReminderFilter.PAST && item.reminder.isActive) {
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = onMarkDone,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    Icons.Outlined.CheckCircleOutline,
                    contentDescription = "Mark done",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        } else if (selectedFilter == ReminderFilter.PAST) {
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                Icons.Outlined.CheckCircle,
                contentDescription = "Done",
                modifier = Modifier
                    .size(18.dp)
                    .alpha(0.4f),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ─── Pinned Note Chip (compact horizontal cards) ──────────────────────────────

@Composable
private fun PinnedNoteChip(
    note: PinnedNoteItem,
    onClick: () -> Unit
) {
    val dims = rememberResponsiveDimensions()
    val chipWidth = when (dims.bucket) {
        com.amvarpvtltd.swiftNote.utils.WidthBucket.COMPACT -> 130.dp
        com.amvarpvtltd.swiftNote.utils.WidthBucket.MEDIUM -> 150.dp
        com.amvarpvtltd.swiftNote.utils.WidthBucket.EXPANDED -> 180.dp
    }
    Surface(
        onClick = onClick,
        modifier = Modifier.width(chipWidth),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 0.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            // Category dot
            if (note.category.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            Text(
                text = note.title,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 16.sp,
                letterSpacing = (-0.1).sp
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = note.snippet,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 14.sp,
                fontSize = 11.sp
            )
        }
    }
}

// ─── Empty State ───────────────────────────────────────────────────────────────

@Composable
private fun EmptyReminderState(filter: ReminderFilter) {
    val (message, subtitle, icon) = when (filter) {
        ReminderFilter.TODAY -> Triple(
            "All clear today",
            "No reminders scheduled for today",
            Icons.Outlined.Today
        )
        ReminderFilter.UPCOMING -> Triple(
            "Nothing upcoming",
            "Future reminders will appear here",
            Icons.Outlined.Upcoming
        )
        ReminderFilter.PAST -> Triple(
            "No past reminders",
            "Fired reminders will show up here",
            Icons.Outlined.History
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "Reminders empty light", widthDp = 360, heightDp = 640)
@androidx.compose.ui.tooling.preview.Preview(name = "Reminders empty dark", widthDp = 320, heightDp = 640,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES, fontScale = 1.3f)
@Composable
private fun ReminderEmptyPreview() {
    com.amvarpvtltd.swiftNote.ui.theme.SelfNoteTheme(
        darkTheme = androidx.compose.foundation.isSystemInDarkTheme()
    ) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize()) {
                com.amvarpvtltd.swiftNote.components.SwiftNoteTopBar(
                    title = "Reminders", subtitle = "Tuesday, Nov 3", onBack = {})
                Column(Modifier.padding(16.dp)) {
                    FilterTabRow(ReminderFilter.TODAY, {}, emptyMap())
                    EmptyReminderState(ReminderFilter.TODAY)
                }
            }
        }
    }
}

// ─── Helpers ───────────────────────────────────────────────────────────────────

private fun getRelativeTimeString(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
    val hours = TimeUnit.MILLISECONDS.toHours(diff)
    val days = TimeUnit.MILLISECONDS.toDays(diff)
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "${minutes}m ago"
        hours < 24 -> "${hours}h ago"
        days < 7 -> "${days}d ago"
        days < 30 -> "${days / 7}w ago"
        else -> "${days / 30}mo ago"
    }
}

private fun getFormattedDate(): String {
    return SimpleDateFormat("EEEE, MMM d", Locale.getDefault())
        .format(System.currentTimeMillis())
}





