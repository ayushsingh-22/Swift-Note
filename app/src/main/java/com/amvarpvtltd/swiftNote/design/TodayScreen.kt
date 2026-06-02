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
import com.amvarpvtltd.swiftNote.richtext.RichTextBridge
import com.amvarpvtltd.swiftNote.room.AppDatabase
import com.amvarpvtltd.swiftNote.room.NoteEntityMapper
import com.amvarpvtltd.swiftNote.utils.rememberResponsiveDimensions
import kotlinx.coroutines.CoroutineScope
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
    val activeReminders by db.reminderDao().getAllActiveReminders()
        .collectAsState(initial = emptyList())
    val allReminders by db.reminderDao().observeAllReminders()
        .collectAsState(initial = emptyList())
    val pinnedEntities by db.noteDao().observePinnedNotes()
        .collectAsState(initial = emptyList())


    // Filter state
    var selectedFilter by remember { mutableStateOf(ReminderFilter.TODAY) }

    // Compute filtered reminders
    val filteredReminders = remember(activeReminders, allReminders, selectedFilter, now, todayStart, todayEnd) {
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
                    noteSnippet = RichTextBridge.stripHtmlToPlainText(reminder.noteDescription).take(100),
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

    // Pinned notes
    val pinnedNotes = remember(pinnedEntities) {
        pinnedEntities.take(8).map { entity ->
            val domain = NoteEntityMapper.toDomain(entity)
            PinnedNoteItem(
                id = domain.id,
                title = domain.title.ifBlank { "Untitled" },
                snippet = RichTextBridge.stripHtmlToPlainText(domain.description).take(100),
                category = domain.category,
                colorKey = domain.colorKey
            )
        }
    }

    NoteScreenBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = "Today",
                                fontWeight = FontWeight.ExtraBold,
                                color = NoteTheme.OnSurface,
                                style = MaterialTheme.typography.titleLarge,
                                fontSize = 26.sp,
                                letterSpacing = (-0.5).sp
                            )
                            Text(
                                text = getFormattedDate(),
                                style = MaterialTheme.typography.labelMedium,
                                color = NoteTheme.OnSurfaceVariant,
                                fontWeight = FontWeight.Normal
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = { navController.popBackStack() },
                            modifier = Modifier.padding(start = 4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(NoteTheme.Primary.copy(alpha = 0.1f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = NoteTheme.Primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    },
                    actions = {},
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
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
                // ─── Greeting Banner ─────────────────────────────────────────
                item {
                    GreetingBanner(
                        todayCount = reminderCounts[ReminderFilter.TODAY] ?: 0,
                        overdueCount = allReminders.count {
                            it.reminderTime < now && it.isActive && it.reminderTime in todayStart..todayEnd
                        }
                    )
                }

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
                    items(filteredReminders, key = { it.reminder.id }) { item ->
                        ReminderRow(
                            item = item,
                            selectedFilter = selectedFilter,
                            onClick = {
                                navController.navigate("viewnote/${item.reminder.noteId}")
                            },
                            onMarkDone = {
                                CoroutineScope(Dispatchers.IO).launch {
                                    db.reminderDao().deactivateReminder(item.reminder.id)
                                }
                            }
                        )
                    }
                }

                // ─── Pinned Notes ────────────────────────────────────────────
                if (pinnedNotes.isNotEmpty()) {
                    item { Spacer(modifier = Modifier.height(4.dp)) }

                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 4.dp, horizontal = 4.dp)
                        ) {
                            Icon(
                                Icons.Outlined.PushPin,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp),
                                tint = NoteTheme.Secondary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Pinned",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = NoteTheme.OnSurface,
                                letterSpacing = (-0.2).sp
                            )
                        }
                    }

                    item {
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

@Composable
private fun GreetingBanner(todayCount: Int, overdueCount: Int) {
    val greeting = remember { getScreenGreeting() }

    // In dark mode PrimaryContainer is very dark — use higher alpha for visibility
    val isDark = NoteTheme.Background.luminance() < 0.2f
    val bannerBackground = if (isDark)
        NoteTheme.Primary.copy(alpha = 0.12f)
    else
        NoteTheme.PrimaryContainer.copy(alpha = 0.4f)
    val bannerBorder = if (isDark)
        NoteTheme.Primary.copy(alpha = 0.25f)
    else
        Color.Transparent

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(bannerBackground)
            .then(
                if (isDark) Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Transparent)
                    .padding(0.dp) // placeholder for border below
                else Modifier
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = greeting,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (isDark) NoteTheme.Primary else NoteTheme.OnPrimaryContainer,
                letterSpacing = (-0.2).sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = when {
                    overdueCount > 0 -> "$overdueCount overdue · $todayCount today"
                    todayCount > 0 -> "$todayCount reminder${if (todayCount > 1) "s" else ""} today"
                    else -> "You're all caught up ✓"
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (isDark)
                    NoteTheme.OnSurface.copy(alpha = 0.7f)
                else
                    NoteTheme.OnPrimaryContainer.copy(alpha = 0.7f)
            )
        }

        // Summary count pill
        if (todayCount > 0) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        if (isDark) NoteTheme.Primary.copy(alpha = 0.2f)
                        else NoteTheme.Primary.copy(alpha = 0.15f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "$todayCount",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = NoteTheme.Primary
                )
            }
        }
    }
}

// ─── Filter Tab Row ────────────────────────────────────────────────────────────

@Composable
private fun FilterTabRow(
    selectedFilter: ReminderFilter,
    onFilterChange: (ReminderFilter) -> Unit,
    counts: Map<ReminderFilter, Int>
) {
    val isDark = NoteTheme.Background.luminance() < 0.2f

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
                    isSelected -> NoteTheme.Primary
                    isDark -> NoteTheme.SurfaceVariant
                    else -> NoteTheme.Surface
                },
                shadowElevation = 0.dp,
                // Stronger border — visible in both light and dark mode
                border = when {
                    isSelected -> null
                    isDark -> BorderStroke(1.dp, NoteTheme.OnSurface.copy(alpha = 0.2f))
                    else -> BorderStroke(1.dp, NoteTheme.OnSurface.copy(alpha = 0.15f))
                }
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = filter.icon,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = if (isSelected) NoteTheme.OnPrimary else NoteTheme.OnSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = filter.label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) NoteTheme.OnPrimary else NoteTheme.OnSurface,
                        letterSpacing = 0.sp
                    )
                    // Always reserve the count row height so all tabs are equal size.
                    // Render the number when > 0, otherwise an invisible placeholder.
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (count > 0) "$count" else " ",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (!isSelected && count == 0) Color.Transparent
                               else if (isSelected) NoteTheme.OnPrimary.copy(alpha = 0.8f)
                               else NoteTheme.Primary,
                        fontSize = 11.sp
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
        item.isOverdue && selectedFilter == ReminderFilter.TODAY -> NoteTheme.Error
        selectedFilter == ReminderFilter.PAST -> NoteTheme.OnSurfaceVariant
        else -> NoteTheme.Primary
    }

    val isDark = NoteTheme.Background.luminance() < 0.2f
    val rowBackground = if (isDark) NoteTheme.SurfaceVariant else NoteTheme.Surface

    // Border is more visible in light mode; dark mode uses a slightly brighter stroke
    val rowBorderColor = if (isDark)
        NoteTheme.OnSurface.copy(alpha = 0.15f)
    else
        NoteTheme.OnSurface.copy(alpha = 0.12f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, rowBorderColor, RoundedCornerShape(14.dp))
            .clip(RoundedCornerShape(14.dp))
            .background(rowBackground)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true)
            ) { onClick() }
            .padding(14.dp),
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
                    NoteTheme.OnSurface.copy(alpha = 0.7f)
                else NoteTheme.OnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                letterSpacing = (-0.2).sp
            )

            if (item.noteSnippet.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = item.noteSnippet,
                    style = MaterialTheme.typography.bodySmall,
                    color = NoteTheme.OnSurfaceVariant,
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
                        tint = NoteTheme.OnSurfaceVariant.copy(alpha = 0.6f)
                    )
                }

                if (item.isOverdue && selectedFilter == ReminderFilter.TODAY) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Overdue",
                        style = MaterialTheme.typography.labelSmall,
                        color = NoteTheme.Error,
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
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Outlined.CheckCircleOutline,
                    contentDescription = "Mark done",
                    modifier = Modifier.size(20.dp),
                    tint = NoteTheme.Success
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
                tint = NoteTheme.OnSurfaceVariant
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
        color = NoteTheme.Surface,
        shadowElevation = 0.dp,
        border = BorderStroke(1.dp, NoteTheme.OnSurface.copy(alpha = 0.12f))
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
                        .background(NoteTheme.Primary.copy(alpha = 0.6f))
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            Text(
                text = note.title,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = NoteTheme.OnSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 16.sp,
                letterSpacing = (-0.1).sp
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = note.snippet,
                style = MaterialTheme.typography.bodySmall,
                color = NoteTheme.OnSurfaceVariant.copy(alpha = 0.7f),
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
                .background(NoteTheme.SurfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = NoteTheme.OnSurfaceVariant.copy(alpha = 0.6f)
            )
        }
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = NoteTheme.OnSurface.copy(alpha = 0.7f)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = NoteTheme.OnSurfaceVariant.copy(alpha = 0.6f)
        )
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

private fun getScreenGreeting(): String {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when {
        hour < 12 -> "Good morning ☀️"
        hour < 17 -> "Good afternoon 👋"
        hour < 21 -> "Good evening 🌆"
        else -> "Good night 🌙"
    }
}

private fun getFormattedDate(): String {
    return SimpleDateFormat("EEEE, MMM d", Locale.getDefault())
        .format(System.currentTimeMillis())
}





