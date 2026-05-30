package com.amvarpvtltd.swiftNote.design

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.amvarpvtltd.swiftNote.ai.DetectedEntity
import com.amvarpvtltd.swiftNote.ai.SmartEntityDetector
import com.amvarpvtltd.swiftNote.checklist.ChecklistParser
import com.amvarpvtltd.swiftNote.components.ChecklistItemRow
import com.amvarpvtltd.swiftNote.components.ChecklistProgressIndicator
import com.amvarpvtltd.swiftNote.components.DeleteConfirmationDialog
import com.amvarpvtltd.swiftNote.components.EmptyStateCard
import com.amvarpvtltd.swiftNote.components.IconActionButton
import com.amvarpvtltd.swiftNote.components.LoadingCard
import com.amvarpvtltd.swiftNote.components.NoteScreenBackground
import com.amvarpvtltd.swiftNote.components.ReminderBottomSheet
import com.amvarpvtltd.swiftNote.components.PermissionRationaleSheet
import com.amvarpvtltd.swiftNote.components.PermissionType
import com.amvarpvtltd.swiftNote.components.checkReminderPermissions
import com.amvarpvtltd.swiftNote.components.SmartActionChipRow
import com.amvarpvtltd.swiftNote.reminders.ReminderRepository
import com.amvarpvtltd.swiftNote.utils.Constants
import com.amvarpvtltd.swiftNote.utils.NetworkManager
import com.amvarpvtltd.swiftNote.utils.ShareUtils
import com.amvarpvtltd.swiftNote.repository.NoteRepository
import com.amvarpvtltd.swiftNote.categories.CategoryManager
import com.amvarpvtltd.swiftNote.components.RichTextDisplay
import com.amvarpvtltd.swiftNote.richtext.RichTextBridge
import com.amvarpvtltd.swiftNote.viewmodel.ViewNoteViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.absoluteValue
import kotlin.math.max

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewNoteScreen(navController: NavHostController, noteId: String?) {
    val viewModel: ViewNoteViewModel = viewModel()

    val note by viewModel.note.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()

    var showDeleteDialog by remember { mutableStateOf(false) }
    var copyConfirmed by remember { mutableStateOf(false) }
    var showReminderSheet by remember { mutableStateOf(false) }
    var showPermissionRationale by remember { mutableStateOf(false) }
    var missingPermissionType by remember { mutableStateOf<PermissionType?>(null) }

    val context = LocalContext.current
    val hapticFeedback = LocalHapticFeedback.current
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val noteRepository = remember { NoteRepository(context) }
    val reminderRepository = remember { ReminderRepository(context) }

    val networkManager = remember { NetworkManager.getInstance(context) }
    val isOnline by networkManager.isOnline.collectAsStateWithLifecycle()

    // Scroll progress fraction for the reading progress bar
    val scrollFraction = if (scrollState.maxValue > 0)
        scrollState.value.toFloat() / scrollState.maxValue.toFloat()
    else 0f
    val animatedProgress by animateFloatAsState(
        targetValue = scrollFraction,
        animationSpec = tween(150),
        label = "scroll_progress"
    )

    // Note accent color derived from note ID for visual identity
    val accentColor = remember(note?.id) {
        val idx = (note?.id?.hashCode() ?: 0).absoluteValue
        NoteTheme.getNoteAccentColor(idx)
    }

    // Stats derived from note content
    val wordCount = remember(note?.description) {
        note?.description?.trim()?.split("\\s+".toRegex())?.filter { it.isNotBlank() }?.size ?: 0
    }
    val readingMinutes = remember(wordCount) { max(1, wordCount / 200) }

    // Staggered visibility states
    var heroVisible by remember { mutableStateOf(false) }
    var contentVisible by remember { mutableStateOf(false) }
    var metaVisible by remember { mutableStateOf(false) }

    // Smart Action Chips — detected entities
    var detectedEntities by remember { mutableStateOf<List<DetectedEntity>>(emptyList()) }

    // Debounced checklist sync
    var pendingDescription by remember { mutableStateOf<String?>(null) }
    var syncJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    val dateFormatter = remember {
        java.text.SimpleDateFormat("MMM dd, yyyy · hh:mm a", java.util.Locale.getDefault())
    }

    LaunchedEffect(noteId) { viewModel.loadNote(noteId) }

    LaunchedEffect(note) {
        val currentNote = note ?: return@LaunchedEffect
        heroVisible = true
        delay(120)
        contentVisible = true
        delay(80)
        metaVisible = true

        // Detect entities for Smart Action Chips
        // Strip HTML tags first so detectors see clean prose (e.g. <b>9876543210</b> → 9876543210)
        val rawText = "${currentNote.title} ${currentNote.description}"
        val textToAnalyze = RichTextBridge.stripHtmlToPlainText(rawText)
        if (textToAnalyze.isNotBlank()) {
            detectedEntities = SmartEntityDetector.analyze(
                context = context,
                text = textToAnalyze,
                noteId = currentNote.id
            )
        }
    }

    LaunchedEffect(Unit) {
        viewModel.uiEvent.collect { event ->
            when (event) {
                is ViewNoteViewModel.UiEvent.ShowToast ->
                    Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                is ViewNoteViewModel.UiEvent.NavigateToMain ->
                    navController.navigate("main") { popUpTo("main") { inclusive = false } }
            }
        }
    }

    // Copy with confirmation animation
    fun copyNote() {
        note?.let {
            ShareUtils.copyNoteToClipboard(context, it)
            scope.launch {
                copyConfirmed = true
                delay(2000)
                copyConfirmed = false
            }
        }
    }
    fun shareNote() { note?.let { ShareUtils.shareNote(context, it) } }
    fun deleteNote() { viewModel.deleteNote(noteId) }

    DeleteConfirmationDialog(
        showDialog = showDeleteDialog,
        onDismiss = { showDeleteDialog = false },
        onConfirm = { showDeleteDialog = false; deleteNote() },
        title = note?.title ?: "",
        message = "Are you sure you want to delete this note? This action cannot be undone."
    )

    // ── Permission Rationale Sheet (shown when notification/alarm permission is missing)
    if (showPermissionRationale && missingPermissionType != null) {
        PermissionRationaleSheet(
            permissionType = missingPermissionType!!,
            onDismiss = {
                showPermissionRationale = false
                missingPermissionType = null
            },
            onPermissionGranted = {
                showPermissionRationale = false
                missingPermissionType = null
                val nextMissing = checkReminderPermissions(context)
                if (nextMissing != null) {
                    missingPermissionType = nextMissing
                    showPermissionRationale = true
                } else {
                    showReminderSheet = true
                }
            }
        )
    }

    // ── Reminder Bottom Sheet (triggered by Smart Action Chips DateTime → Add Reminder)
    if (showReminderSheet && note != null) {
        ReminderBottomSheet(
            isVisible = showReminderSheet,
            noteId = note!!.id,
            noteTitle = note!!.title,
            noteDescription = note!!.description,
            onDismiss = { showReminderSheet = false },
            onReminderSet = { reminderRequest ->
                scope.launch(Dispatchers.IO) {
                    try {
                        val result = reminderRepository.createReminder(reminderRequest)
                        withContext(Dispatchers.Main) {
                            if (result.isSuccess) {
                                Toast.makeText(context, "⏰ Reminder set!", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "❌ Failed to set reminder", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } catch (_: Exception) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "❌ Error setting reminder", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                showReminderSheet = false
            }
        )
    }

    NoteScreenBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                Column {
                    TopAppBar(
                        title = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = when {
                                        isLoading -> "Loading…"
                                        errorMessage != null -> "Error"
                                        else -> "Note"
                                    },
                                    fontWeight = FontWeight.SemiBold,
                                    color = NoteTheme.OnSurfaceVariant,
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                if (isLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = NoteTheme.Primary
                                    )
                                }
                            }
                        },
                        navigationIcon = {
                            IconActionButton(
                                onClick = {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                    navController.navigateUp()
                                },
                                icon = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back to notes",
                                containerColor = NoteTheme.Primary.copy(alpha = 0.1f),
                                contentColor = NoteTheme.Primary,
                                modifier = Modifier.padding(start = 12.dp)
                            )
                        },
                        actions = {
                            Row(
                                modifier = Modifier.padding(end = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Phase 4: Pin/Unpin button
                                if (note != null) {
                                    IconActionButton(
                                        onClick = {
                                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                            scope.launch(Dispatchers.IO) {
                                                noteRepository.togglePin(note!!.id, !note!!.isPinned)
                                                viewModel.loadNote(noteId) // reload
                                            }
                                            val msg = if (note!!.isPinned) "Unpinned" else "Pinned"
                                            Toast.makeText(context, "📌 $msg", Toast.LENGTH_SHORT).show()
                                        },
                                        icon = Icons.Outlined.PushPin,
                                        contentDescription = if (note!!.isPinned) "Unpin" else "Pin",
                                        containerColor = if (note!!.isPinned) NoteTheme.Primary.copy(alpha = 0.15f) else NoteTheme.OnSurfaceVariant.copy(alpha = 0.08f),
                                        contentColor = if (note!!.isPinned) NoteTheme.Primary else NoteTheme.OnSurfaceVariant
                                    )

                                    // Phase 4: Archive/Unarchive button
                                    IconActionButton(
                                        onClick = {
                                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                            val isCurrentlyArchived = note!!.isArchived
                                            scope.launch(Dispatchers.IO) {
                                                noteRepository.toggleArchive(note!!.id, !isCurrentlyArchived)
                                            }
                                            val msg = if (isCurrentlyArchived) "📤 Note unarchived" else "📦 Note archived"
                                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                            navController.navigateUp()
                                        },
                                        icon = if (note!!.isArchived) Icons.Outlined.Unarchive else Icons.Outlined.Archive,
                                        contentDescription = if (note!!.isArchived) "Unarchive" else "Archive",
                                        containerColor = if (note!!.isArchived) NoteTheme.Primary.copy(alpha = 0.12f) else NoteTheme.OnSurfaceVariant.copy(alpha = 0.08f),
                                        contentColor = if (note!!.isArchived) NoteTheme.Primary else NoteTheme.OnSurfaceVariant
                                    )
                                }

                                if (!isOnline) {
                                    Surface(
                                        shape = RoundedCornerShape(100.dp),
                                        color = NoteTheme.Warning.copy(alpha = 0.12f)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                Icons.Outlined.CloudOff,
                                                contentDescription = null,
                                                tint = NoteTheme.Warning,
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Text(
                                                text = "Offline",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = NoteTheme.Warning,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                    )

                    // ── Reading Progress Bar ─────────────────────────────────
                    if (note != null && scrollState.maxValue > 0) {
                        LinearProgressIndicator(
                            progress = { animatedProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp),
                            color = accentColor,
                            trackColor = accentColor.copy(alpha = 0.12f),
                            strokeCap = StrokeCap.Round
                        )
                    } else {
                        Spacer(modifier = Modifier.height(3.dp))
                    }
                }
            }
        ) { paddingValues ->
            when {
                isLoading -> LoadingCard("Loading your note...", "Fetching from local storage...")

                errorMessage != null -> Box(
                    modifier = Modifier.fillMaxSize().padding(paddingValues).padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    EmptyStateCard(
                        icon = Icons.Outlined.ErrorOutline,
                        title = "Note Not Found",
                        description = "The note you're looking for might have been deleted or doesn't exist.\n\nError: $errorMessage",
                        buttonText = "Go Back to Notes",
                        onButtonClick = { navController.navigateUp() }
                    )
                }

                note == null -> Box(
                    modifier = Modifier.fillMaxSize().padding(paddingValues).padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    EmptyStateCard(
                        icon = Icons.Outlined.ErrorOutline,
                        title = "Note Not Available",
                        description = "This note could not be loaded.",
                        buttonText = "Go Back to Notes",
                        onButtonClick = { navController.navigateUp() }
                    )
                }

                else -> {
                    val noteDescription = note?.description ?: ""
                    val isChecklist = ChecklistParser.isChecklistContent(noteDescription)
                    val checklistItems = if (isChecklist) ChecklistParser.parseItems(noteDescription) else emptyList()
                    val isValidChecklist = isChecklist && checklistItems.isNotEmpty()

                    Box(modifier = Modifier.fillMaxSize()) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(paddingValues)
                                .verticalScroll(scrollState)
                                .navigationBarsPadding()   // sync with action bar's nav inset
                                .padding(horizontal = 16.dp)
                                .padding(bottom = 110.dp), // action card (~73dp) + box padding (40dp) - navBar already consumed
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Spacer(modifier = Modifier.height(4.dp))

                            // ── Hero Title Card ─────────────────────────────────
                            AnimatedVisibility(
                                visible = heroVisible,
                                enter = fadeIn(tween(400)) + slideInVertically(
                                    spring(Spring.DampingRatioMediumBouncy),
                                    initialOffsetY = { it / 4 }
                                )
                            ) {
                                Card(
                                    // SurfaceVariant gives a solid tinted base in both modes:
                                    // Light → #F1F5F9 (cool blue-gray), Dark → #2A2F3E (deep navy)
                                    colors = CardDefaults.cardColors(containerColor = NoteTheme.SurfaceVariant),
                                    shape = RoundedCornerShape(24.dp),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                                    border = BorderStroke(1.5.dp, accentColor.copy(alpha = 0.40f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    // IntrinsicSize.Min lets the stripe Box use fillMaxHeight()
                                    // matching the Column's intrinsic height without hardcoding dp
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(IntrinsicSize.Min)
                                            // Layer 1 (already from card): SurfaceVariant solid base
                                            // Layer 2: Strong horizontal accent wash — left vivid, right fades
                                            .background(
                                                Brush.horizontalGradient(
                                                    colorStops = arrayOf(
                                                        0.00f to accentColor.copy(alpha = 0.52f),
                                                        0.38f to accentColor.copy(alpha = 0.20f),
                                                        0.75f to accentColor.copy(alpha = 0.06f),
                                                        1.00f to Color.Transparent
                                                    )
                                                )
                                            )
                                    ) {
                                        // Full-height accent stripe — bold left edge
                                        Box(
                                            modifier = Modifier
                                                .width(6.dp)
                                                .fillMaxHeight()
                                                .align(Alignment.CenterStart)
                                                .clip(RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp))
                                                .background(
                                                    Brush.verticalGradient(
                                                        colors = listOf(
                                                            accentColor,
                                                            accentColor.copy(alpha = 0.55f)
                                                        )
                                                    )
                                                )
                                        )
                                        Column(
                                            modifier = Modifier.padding(
                                                start = 22.dp, end = 16.dp,
                                                top = 20.dp, bottom = 18.dp
                                            )
                                        ) {
                                            Text(
                                                text = note?.title ?: "",
                                                fontSize = 26.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = NoteTheme.OnSurface,
                                                lineHeight = 34.sp,
                                                letterSpacing = (-0.5).sp
                                            )

                                            Spacer(modifier = Modifier.height(12.dp))

                                            // ── Metadata chips row ────────────────────────────
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                // Date chip
                                                MetaChip(
                                                    icon = { Icon(Icons.Outlined.CalendarToday, contentDescription = null, modifier = Modifier.size(11.dp), tint = NoteTheme.OnSurfaceVariant) },
                                                    label = note?.let { dateFormatter.format(java.util.Date(it.timestamp)) } ?: ""
                                                )
                                            }

                                            // ── Category chip ─────────────────────────────────
                                            run {
                                                val categoryName = note?.category?.takeIf { it.isNotBlank() } ?: "General"
                                                val catColorHex = note?.category?.takeIf { it.isNotBlank() }?.let {
                                                    CategoryManager.getColor(context, it)
                                                }
                                                val catColor = if (catColorHex != null) Color(catColorHex) else NoteTheme.OnSurfaceVariant
                                                Spacer(modifier = Modifier.height(6.dp))
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(8.dp)
                                                            .clip(CircleShape)
                                                            .background(catColor)
                                                    )
                                                    Text(
                                                        text = categoryName,
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Medium,
                                                        color = catColor,
                                                        letterSpacing = 0.3.sp
                                                    )
                                                }
                                            }

                                            if (!isValidChecklist && wordCount > 0) {
                                                Spacer(modifier = Modifier.height(6.dp))
                                                Row(
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    // Word count chip
                                                    MetaChip(
                                                        icon = { Icon(Icons.Outlined.TextFields, contentDescription = null, modifier = Modifier.size(11.dp), tint = NoteTheme.OnSurfaceVariant) },
                                                        label = "$wordCount words"
                                                    )
                                                    // Reading time chip
                                                    MetaChip(
                                                        icon = { Icon(Icons.Outlined.AccessTime, contentDescription = null, modifier = Modifier.size(11.dp), tint = NoteTheme.OnSurfaceVariant) },
                                                        label = "$readingMinutes min read"
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // ── Content ─────────────────────────────────────────
                            AnimatedVisibility(
                                visible = contentVisible,
                                enter = fadeIn(tween(400)) + slideInVertically(
                                    spring(Spring.DampingRatioMediumBouncy),
                                    initialOffsetY = { it / 3 }
                                )
                            ) {
                                if (isValidChecklist) {
                                    // Interactive Checklist Card
                                    val (checked, total) = ChecklistParser.progress(noteDescription)
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = NoteTheme.Surface),
                                        shape = RoundedCornerShape(20.dp),
                                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                                        border = BorderStroke(1.dp, NoteTheme.OnSurface.copy(alpha = 0.07f)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(20.dp)) {
                                            // Header row with progress ring
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(36.dp)
                                                            .background(
                                                                accentColor.copy(alpha = 0.12f),
                                                                CircleShape
                                                            ),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(
                                                            Icons.Outlined.CheckCircle,
                                                            contentDescription = null,
                                                            tint = accentColor,
                                                            modifier = Modifier.size(20.dp)
                                                        )
                                                    }
                                                    Column {
                                                        Text(
                                                            text = "Checklist",
                                                            style = MaterialTheme.typography.titleMedium,
                                                            fontWeight = FontWeight.Bold,
                                                            color = NoteTheme.OnSurface
                                                        )
                                                        if (total > 0) {
                                                            Text(
                                                                text = "$checked of $total completed",
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = NoteTheme.OnSurfaceVariant
                                                            )
                                                        }
                                                    }
                                                }
                                                // Circular progress micro-indicator
                                                if (total > 0) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        CircularProgressIndicator(
                                                            progress = { checked.toFloat() / total.toFloat() },
                                                            modifier = Modifier.size(40.dp),
                                                            color = if (checked == total) NoteTheme.Success else accentColor,
                                                            trackColor = accentColor.copy(alpha = 0.12f),
                                                            strokeCap = StrokeCap.Round,
                                                            strokeWidth = 4.dp
                                                        )
                                                        Text(
                                                            text = "${((checked.toFloat() / total.toFloat()) * 100).toInt()}%",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            fontWeight = FontWeight.Bold,
                                                            color = if (checked == total) NoteTheme.Success else accentColor,
                                                            fontSize = 10.sp
                                                        )
                                                    }
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(14.dp))
                                            ChecklistProgressIndicator(checked = checked, total = total)
                                            Spacer(modifier = Modifier.height(16.dp))

                                            checklistItems.forEach { item ->
                                                ChecklistItemRow(
                                                    item = item,
                                                    onCheckedChange = { _ ->
                                                        val newDesc = ChecklistParser.toggleItem(
                                                            pendingDescription ?: noteDescription, item.id
                                                        )
                                                        pendingDescription = newDesc
                                                        syncJob?.cancel()
                                                        syncJob = scope.launch(Dispatchers.IO) {
                                                            kotlinx.coroutines.delay(500)
                                                            noteRepository.saveNote(
                                                                title = note?.title ?: "",
                                                                description = newDesc,
                                                                noteId = note?.id,
                                                                context = context
                                                            )
                                                            pendingDescription = null
                                                            viewModel.loadNote(noteId)
                                                        }
                                                    },
                                                    onTextChange = {},
                                                    onDelete = {},
                                                    onEnterPressed = {},
                                                    onBackspaceOnEmpty = {},
                                                    readOnly = true,
                                                    modifier = Modifier.padding(vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    // Text Description Card with SelectionContainer
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = NoteTheme.Surface),
                                        shape = RoundedCornerShape(20.dp),
                                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                                        border = BorderStroke(1.dp, NoteTheme.OnSurface.copy(alpha = 0.07f)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp)) {
                                            // Phase 1: RichTextDisplay handles HTML headings, lists,
                                            // links, code blocks and checkboxes natively.
                                            // SelectionContainer removed — conflicts with tap-to-open links.
                                            RichTextDisplay(
                                                html = note?.description ?: "",
                                                style = MaterialTheme.typography.bodyLarge.copy(
                                                    lineHeight   = 28.sp,
                                                    textAlign    = TextAlign.Start,
                                                    letterSpacing = 0.2.sp
                                                ),
                                                color    = NoteTheme.OnSurface,
                                                modifier = Modifier.fillMaxWidth(),
                                                onCheckboxToggle = { index, newChecked ->
                                                    viewModel.toggleCheckbox(noteId, index, newChecked)
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            // ── Smart Action Chips ────────────────────────────────
                            AnimatedVisibility(
                                visible = metaVisible && detectedEntities.isNotEmpty(),
                                enter = fadeIn(tween(400)) + slideInVertically(
                                    animationSpec = tween(400),
                                    initialOffsetY = { it / 3 }
                                )
                            ) {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = NoteTheme.Surface),
                                    shape = RoundedCornerShape(20.dp),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                                    border = BorderStroke(1.dp, NoteTheme.OnSurface.copy(alpha = 0.07f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    SmartActionChipRow(
                                        entities = detectedEntities,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                        onAddReminderClick = { _ ->
                                            val missing = checkReminderPermissions(context)
                                            if (missing != null) {
                                                missingPermissionType = missing
                                                showPermissionRationale = true
                                            } else {
                                                showReminderSheet = true
                                            }
                                        }
                                    )
                                }
                            }
                        }

                        // ── Bottom Action Bar ────────────────────────────────────
                        AnimatedVisibility(
                            visible = contentVisible,
                            modifier = Modifier.align(Alignment.BottomCenter),
                            enter = fadeIn(tween(500)) + slideInVertically(
                                spring(Spring.DampingRatioMediumBouncy),
                                initialOffsetY = { it }
                            )
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                Color.Transparent,
                                                NoteTheme.Background.copy(alpha = 0.95f),
                                                NoteTheme.Background
                                            )
                                        )
                                    )
                                    .padding(horizontal = 16.dp)
                                    .padding(top = 16.dp, bottom = 24.dp)
                                    .navigationBarsPadding()
                            ) {
                                Card(
                                    shape = RoundedCornerShape(22.dp),
                                    colors = CardDefaults.cardColors(containerColor = NoteTheme.Surface),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        horizontalArrangement = Arrangement.SpaceAround,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Edit (primary action)
                                        BottomActionItem(
                                            icon = Icons.Outlined.Edit,
                                            label = "Edit",
                                            tint = NoteTheme.Primary,
                                            bg = NoteTheme.Primary.copy(alpha = 0.13f),
                                            isPrimary = true,
                                            onClick = {
                                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                                navController.navigate("addscreen/${note?.id}")
                                            }
                                        )
                                        // Share
                                        BottomActionItem(
                                            icon = Icons.Outlined.Share,
                                            label = "Share",
                                            tint = NoteTheme.Primary.copy(alpha = 0.85f),
                                            bg = NoteTheme.Primary.copy(alpha = 0.08f),
                                            onClick = {
                                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                                shareNote()
                                            }
                                        )
                                        // Copy (with confirmation)
                                        val copyTint by animateColorAsState(
                                            targetValue = if (copyConfirmed) NoteTheme.Success else NoteTheme.OnSurfaceVariant,
                                            label = "copy_tint"
                                        )
                                        BottomActionItem(
                                            icon = if (copyConfirmed) Icons.Outlined.CheckCircle else Icons.Outlined.ContentCopy,
                                            label = if (copyConfirmed) "Copied!" else "Copy",
                                            tint = copyTint,
                                            bg = if (copyConfirmed) NoteTheme.Success.copy(alpha = 0.1f)
                                                 else NoteTheme.OnSurfaceVariant.copy(alpha = 0.08f),
                                            onClick = {
                                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                                copyNote()
                                            }
                                        )
                                        // Delete
                                        BottomActionItem(
                                            icon = Icons.Outlined.Delete,
                                            label = "Delete",
                                            tint = NoteTheme.Error,
                                            bg = NoteTheme.Error.copy(alpha = 0.08f),
                                            onClick = {
                                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                                showDeleteDialog = true
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// Small metadata chip composable
@Composable
private fun MetaChip(
    icon: @Composable () -> Unit,
    label: String
) {
    Surface(
        shape = RoundedCornerShape(100.dp),
        color = NoteTheme.SurfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            icon()
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = NoteTheme.OnSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

// Bottom action bar item composable
@Composable
private fun BottomActionItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color,
    bg: Color,
    isPrimary: Boolean = false,
    onClick: () -> Unit
) {
    val iconBoxSize = if (isPrimary) 36.dp else 30.dp
    val iconSize    = if (isPrimary) 17.dp else 15.dp
    val cornerSize  = if (isPrimary) 11.dp else 9.dp

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 7.dp)
    ) {
        Box(
            modifier = Modifier
                .size(iconBoxSize)
                .background(bg, RoundedCornerShape(cornerSize)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(iconSize)
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            fontWeight = if (isPrimary) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            fontSize = 10.sp
        )
    }
}
