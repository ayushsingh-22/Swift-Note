package com.amvarpvtltd.swiftNote.design

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.SearchOff

import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.amvarpvtltd.swiftNote.components.AnimatedFloatingActionButton
import com.amvarpvtltd.swiftNote.components.FloatingToolbar
import com.amvarpvtltd.swiftNote.components.EmptyStateCard
import com.amvarpvtltd.swiftNote.components.IconActionButton
import com.amvarpvtltd.swiftNote.components.LoadingCard
import com.amvarpvtltd.swiftNote.components.NoteScreenBackground
import com.amvarpvtltd.swiftNote.components.NotesDisplay
import com.amvarpvtltd.swiftNote.components.OfflineBanner
import com.amvarpvtltd.swiftNote.components.OfflineEmptyStateCard
import com.amvarpvtltd.swiftNote.components.SearchBar
import com.amvarpvtltd.swiftNote.components.SortOptionsSheet
import com.amvarpvtltd.swiftNote.components.SyncStatusIndicator
import com.amvarpvtltd.swiftNote.components.ThemeToggleButton
import com.amvarpvtltd.swiftNote.components.ViewModeToggleButton
import com.amvarpvtltd.swiftNote.components.rememberViewModeState
import com.amvarpvtltd.swiftNote.dataclass
import com.amvarpvtltd.swiftNote.search.rememberSearchAndSortManager
import com.amvarpvtltd.swiftNote.auth.PassphraseManager
import com.amvarpvtltd.swiftNote.auth.SyncMode
import com.amvarpvtltd.swiftNote.utils.AutoSyncManager
import com.amvarpvtltd.swiftNote.utils.Constants
import com.amvarpvtltd.swiftNote.utils.ShareUtils
import com.amvarpvtltd.swiftNote.viewmodel.NotesViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

/**
 * Dynamic greeting based on time of day with varied messages
 */
private fun getGreeting(): String {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val dayOfWeek = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)

    // Weekend special greetings
    val isWeekend = dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY

    return when {
        // Early morning (5-7)
        hour in 5..6 -> listOf(
            "Rise and shine! ✨",
            "Early bird! 🐦",
            "Fresh start! 🌅"
        ).random()

        // Morning (7-11)
        hour in 7..11 -> if (isWeekend) {
            listOf(
                "Lazy morning? ☕",
                "Weekend vibes! 🎉",
                "Relax mode on! 😌"
            ).random()
        } else {
            listOf(
                "Good morning! ☀️",
                "Ready to conquer! 💪",
                "Let's do this! 🚀",
                "Morning sunshine! 🌻"
            ).random()
        }

        // Afternoon (12-16)
        hour in 12..16 -> listOf(
            "Good afternoon! 🌤️",
            "Crushing it! 🔥",
            "Keep going! 💫",
            "Halfway there! ⚡"
        ).random()

        // Evening (17-20)
        hour in 17..20 -> listOf(
            "Good evening! 🌆",
            "Winding down? 🎯",
            "Evening thoughts! 💭",
            "Golden hour! ✨"
        ).random()

        // Night (21-23)
        hour in 21..23 -> listOf(
            "Night owl! 🦉",
            "Burning midnight oil? 🔮",
            "Late thoughts! 🌙",
            "Still creating! ⭐"
        ).random()

        // Late night / very early (0-4)
        else -> listOf(
            "Can't sleep? 🌌",
            "Night thinker! 💫",
            "Stargazing? ✨",
            "Midnight magic! 🔮"
        ).random()
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun NotesScreen(navController: NavHostController) {
    // ViewModel handles business logic (fetch, delete, sync)
    val viewModel: NotesViewModel = viewModel()

    val notes by viewModel.notes.collectAsStateWithLifecycle()
    val isLoadingState by viewModel.isLoading.collectAsStateWithLifecycle()
    val isRefreshingState by viewModel.isRefreshing.collectAsStateWithLifecycle()

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val hapticFeedback = LocalHapticFeedback.current

    // Network and sync state from ViewModel
    val isOnline by viewModel.networkManager.isOnline.collectAsStateWithLifecycle()
    val isSyncing by viewModel.autoSyncManager.isSyncing.collectAsStateWithLifecycle()
    val syncStatus by viewModel.autoSyncManager.lastSyncStatus.collectAsStateWithLifecycle()
    val hasPendingSync by viewModel.autoSyncManager.hasPendingSync.collectAsStateWithLifecycle()

    // Search and Sort — pre-seeded with current notes so no empty-state flash on nav-back.
    val searchAndSortManager = rememberSearchAndSortManager(initialNotes = notes)
    val searchAndSortState by searchAndSortManager.searchAndSortState.collectAsStateWithLifecycle()
    var showSortSheet by remember { mutableStateOf(false) }

    // Local search state for immediate UI updates
    var localSearchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }

    // Theme — observe the global AppThemeState singleton.
    val currentTheme by com.amvarpvtltd.swiftNote.theme.AppThemeState.themeMode.collectAsStateWithLifecycle()

    // View mode management
    val viewModeState = rememberViewModeState()
    var currentViewMode by viewModeState

    // Adaptive icon colors
    val (adaptiveIconContainer, adaptiveIconContent) = remember(NoteTheme.Background, NoteTheme.Secondary) {
        com.amvarpvtltd.swiftNote.components.adaptiveIconColors(NoteTheme.Background, NoteTheme.Secondary)
    }

    // Offline banner state
    var showOfflineBanner by remember { mutableStateOf(false) }

    // Phase 6 — Sync mode for top-bar icon indicator
    var syncMode by remember { mutableStateOf(SyncMode.LOCAL_ONLY) }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            syncMode = PassphraseManager.getSyncMode(context)
        }
    }
    // Re-read sync mode when the user navigates back from SyncSettingsScreen
    DisposableEffect(navController) {
        val listener = androidx.navigation.NavController.OnDestinationChangedListener { _, destination, _ ->
            if (destination.route == "main") {
                scope.launch {
                    withContext(Dispatchers.IO) {
                        syncMode = PassphraseManager.getSyncMode(context)
                    }
                }
            }
        }
        navController.addOnDestinationChangedListener(listener)
        onDispose { navController.removeOnDestinationChangedListener(listener) }
    }

    // Reminder functionality state
    var selectedNoteForReminder by remember { mutableStateOf<dataclass?>(null) }
    var showReminderSheet by remember { mutableStateOf(false) }
    var showPermissionRationale by remember { mutableStateOf(false) }
    var missingPermissionType by remember { mutableStateOf<com.amvarpvtltd.swiftNote.components.PermissionType?>(null) }
    val reminderRepository = remember { com.amvarpvtltd.swiftNote.reminders.ReminderRepository(context) }

    // Quick stats (derived state — no extra recomposition)
    val pinnedCount by remember { derivedStateOf { notes.count { it.isPinned } } }

    // Pull-to-refresh state
    val pullToRefreshState = rememberPullToRefreshState()

    // Undo-delete Snackbar
    val snackbarHostState = remember { SnackbarHostState() }
    val pendingDelete by viewModel.pendingDelete.collectAsStateWithLifecycle()

    // Show snackbar when a note is pending deletion
    LaunchedEffect(pendingDelete) {
        val deletedNote = pendingDelete
        if (deletedNote != null) {
            val result = snackbarHostState.showSnackbar(
                message = "Note deleted",
                actionLabel = "Undo",
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.undoDelete()
            }
        }
    }

    // Monitor offline state
    LaunchedEffect(isOnline) {
        if (!isOnline) {
            showOfflineBanner = true
        } else {
            delay(2000)
            showOfflineBanner = false
        }
    }

    // Update search manager when local search changes
    LaunchedEffect(localSearchQuery) {
        searchAndSortManager.updateSearchQuery(localSearchQuery)
    }

    // Update notes in search manager when notes change
    LaunchedEffect(notes) {
        searchAndSortManager.updateNotes(notes)
    }

    // Save view mode when it changes
    LaunchedEffect(currentViewMode) {
        withContext(Dispatchers.IO) {
            com.amvarpvtltd.swiftNote.components.ViewModeManager.setViewMode(context, currentViewMode)
        }
    }

    // Collect one-shot UI events from ViewModel
    LaunchedEffect(Unit) {
        viewModel.uiEvent.collect { event ->
            when (event) {
                is NotesViewModel.UiEvent.ShowToast -> {
                    Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                }
                is NotesViewModel.UiEvent.NavigateToMain -> { /* already on main */ }
            }
        }
    }

    // Refresh functionality delegates to ViewModel
    fun refreshNotes() { viewModel.refreshNotes() }

    // Delete note delegates to ViewModel
    fun deleteNote(noteId: String) { viewModel.deleteNote(noteId) }

    // Share note function
    fun shareNote(note: dataclass) {
        ShareUtils.shareNote(context, note)
    }

    // Sync function delegates to ViewModel
    fun syncNotes() { viewModel.syncNotes() }

    // Cleanup when screen is disposed
    DisposableEffect(Unit) {
        onDispose {
            viewModel.stopAutoSync()
        }
    }

    // Handle sync status changes
    LaunchedEffect(syncStatus) {
        when (syncStatus) {
            is AutoSyncManager.SyncStatus.Success -> {
                refreshNotes()
                Toast.makeText(context, Constants.SYNC_SUCCESS_MESSAGE, Toast.LENGTH_SHORT).show()
            }
            is AutoSyncManager.SyncStatus.Failed -> {
                Toast.makeText(context, Constants.SYNC_FAILED_MESSAGE, Toast.LENGTH_SHORT).show()
            }
            else -> { /* No action needed for None and InProgress */ }
        }
    }

    NoteScreenBackground {
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                Column(
                    modifier = Modifier.animateContentSize(
                        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy)
                    )
                ) {
                    // Offline banner at the top
                    OfflineBanner(
                        isVisible = showOfflineBanner && !isOnline,
                        message = "You're offline. Notes are cached locally and will sync when connected.",
                        onDismiss = { showOfflineBanner = false }
                    )

                    // ─── Premium Top Bar ─────────────────────────────────────
                    TopAppBar(
                        title = {
                            Column {
                                // Greeting text
                                Text(
                                    text = getGreeting(),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = NoteTheme.OnSurfaceVariant,
                                    fontWeight = FontWeight.Medium,
                                    letterSpacing = 0.3.sp
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                // Title with note count
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "My Notes",
                                        fontWeight = FontWeight.Black,
                                        color = NoteTheme.OnSurface,
                                        style = MaterialTheme.typography.headlineMedium,
                                        fontSize = 28.sp,
                                        letterSpacing = (-0.8).sp
                                    )

                                    if (notes.isNotEmpty()) {
                                        Spacer(modifier = Modifier.width(10.dp))
                                        // Animated note count badge
                                        Box(
                                            modifier = Modifier
                                                .background(
                                                    color = NoteTheme.Primary.copy(alpha = 0.1f),
                                                    shape = RoundedCornerShape(12.dp)
                                                )
                                                .padding(horizontal = 10.dp, vertical = 4.dp)
                                        ) {
                                            AnimatedContent(
                                                targetState = notes.size,
                                                transitionSpec = {
                                                    (slideInVertically { -it } + fadeIn()).togetherWith(
                                                        slideOutVertically { it } + fadeOut()
                                                    )
                                                },
                                                label = "note_count"
                                            ) { count ->
                                                Text(
                                                    text = "$count",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = NoteTheme.Primary,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }

                                    if (isRefreshingState) {
                                        Spacer(modifier = Modifier.width(12.dp))
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp,
                                            color = NoteTheme.Primary
                                        )
                                    }
                                }
                            }
                        },
                        actions = {
                            // Sync status
                            SyncStatusIndicator(
                                isOnline = isOnline,
                                hasPendingSync = hasPendingSync,
                                isSyncing = isSyncing,
                                onSyncClick = { syncNotes() }
                            )

                            Spacer(modifier = Modifier.width(6.dp))

                            // Sync settings — icon/colour changes with SyncMode (Phase 6)
                            val (syncSettingsIcon, syncSettingsContainer, syncSettingsContent) =
                                when (syncMode) {
                                    SyncMode.CONTINUOUS -> Triple(
                                        Icons.Filled.Sync,
                                        NoteTheme.SuccessContainer.copy(alpha = 0.8f),
                                        NoteTheme.OnSuccessContainer
                                    )
                                    SyncMode.ONE_TIME_IMPORTED -> Triple(
                                        Icons.Filled.CloudDownload,
                                        NoteTheme.PrimaryContainer.copy(alpha = 0.8f),
                                        NoteTheme.OnPrimaryContainer
                                    )
                                    SyncMode.LOCAL_ONLY -> Triple(
                                        Icons.Outlined.Person,
                                        adaptiveIconContainer,
                                        adaptiveIconContent
                                    )
                                }
                            IconActionButton(
                                onClick = { navController.navigate("syncSettings") },
                                icon = syncSettingsIcon,
                                contentDescription = "Sync Settings",
                                containerColor = syncSettingsContainer,
                                contentColor = syncSettingsContent
                            )

                            Spacer(modifier = Modifier.width(6.dp))

                            // AI Settings
                            IconActionButton(
                                onClick = { navController.navigate("aiSettings") },
                                icon = Icons.Outlined.AutoAwesome,
                                contentDescription = "AI Settings",
                                containerColor = adaptiveIconContainer,
                                contentColor = adaptiveIconContent
                            )

                            Spacer(modifier = Modifier.width(6.dp))

                            // Theme toggle
                            ThemeToggleButton(
                                currentTheme = currentTheme,
                                onThemeChange = { newTheme ->
                                    com.amvarpvtltd.swiftNote.theme.AppThemeState.setTheme(context, newTheme)
                                }
                            )

                            Spacer(modifier = Modifier.width(4.dp))
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                    )

                    // ─── Search bar ──────────────────────────────────────────
                    SearchBar(
                        searchQuery = searchAndSortState.searchQuery,
                        onSearchQueryChange = { localSearchQuery = it },
                        isSearchActive = isSearchActive,
                        onSearchActiveChange = { isSearchActive = it },
                        onClearSearch = {
                            searchAndSortManager.clearSearch()
                            isSearchActive = false
                        },
                        modifier = Modifier.padding(horizontal = Constants.PADDING_MEDIUM.dp)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // ─── Quick Stats + Action Row ─────────────────────────────
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Constants.PADDING_MEDIUM.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left: Pinned count + search results
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Pinned count indicator (only show when pinned notes exist)
                            AnimatedVisibility(
                                visible = pinnedCount > 0,
                                enter = scaleIn() + fadeIn(),
                                exit = scaleOut() + fadeOut()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .background(
                                            color = NoteTheme.Primary.copy(alpha = 0.08f),
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Icon(
                                        Icons.Outlined.PushPin,
                                        contentDescription = null,
                                        modifier = Modifier.size(13.dp),
                                        tint = NoteTheme.Primary
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        "$pinnedCount",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = NoteTheme.Primary
                                    )
                                }
                            }

                            // Search results count
                            AnimatedVisibility(
                                visible = searchAndSortState.isSearchActive,
                                enter = slideInVertically { -it } + fadeIn(),
                                exit = slideOutVertically { -it } + fadeOut()
                            ) {
                                Text(
                                    text = "${searchAndSortState.filteredNotes.size} result${if (searchAndSortState.filteredNotes.size != 1) "s" else ""}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = NoteTheme.Primary,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 6.dp)
                                )
                            }
                        }

                        // Right: View mode + Sort
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            ViewModeToggleButton(
                                currentViewMode = currentViewMode,
                                onViewModeChange = { newViewMode ->
                                    currentViewMode = newViewMode
                                }
                            )

                            IconActionButton(
                                onClick = {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                    showSortSheet = true
                                },
                                icon = Icons.AutoMirrored.Outlined.Sort,
                                contentDescription = "Sort",
                                containerColor = adaptiveIconContainer,
                                contentColor = adaptiveIconContent
                            )
                        }
                    }

                    // ─── Category filter chips ────────────────────────────────
                    val availableCategories by searchAndSortManager.availableCategories.collectAsStateWithLifecycle()
                    if (availableCategories.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        LazyRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = Constants.PADDING_MEDIUM.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            item {
                                FilterChip(
                                    selected = searchAndSortState.categoryFilter.isBlank(),
                                    onClick = { searchAndSortManager.updateCategoryFilter("") },
                                    label = { Text("All", fontSize = 12.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = NoteTheme.Primary.copy(alpha = 0.18f),
                                        selectedLabelColor = NoteTheme.Primary,
                                        selectedLeadingIconColor = NoteTheme.Primary
                                    ),
                                    border = FilterChipDefaults.filterChipBorder(
                                        enabled = true,
                                        selected = searchAndSortState.categoryFilter.isBlank(),
                                        borderColor = NoteTheme.Outline,
                                        selectedBorderColor = NoteTheme.Primary.copy(alpha = 0.6f),
                                        selectedBorderWidth = 1.5.dp
                                    )
                                )
                            }
                            items(availableCategories.size) { idx ->
                                val cat = availableCategories[idx]
                                val catColor = com.amvarpvtltd.swiftNote.categories.CategoryManager.getCategoryColor(context, cat)
                                val isSelected = searchAndSortState.categoryFilter.equals(cat, ignoreCase = true)
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { searchAndSortManager.updateCategoryFilter(cat) },
                                    label = { Text(cat, fontSize = 12.sp) },
                                    leadingIcon = {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .background(
                                                    catColor ?: NoteTheme.Primary,
                                                    CircleShape
                                                )
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        containerColor = (catColor ?: NoteTheme.Primary).copy(alpha = 0.06f),
                                        labelColor = NoteTheme.OnSurface,
                                        iconColor = catColor ?: NoteTheme.Primary,
                                        selectedContainerColor = (catColor ?: NoteTheme.Primary).copy(alpha = 0.20f),
                                        selectedLabelColor = catColor ?: NoteTheme.Primary,
                                        selectedLeadingIconColor = catColor ?: NoteTheme.Primary
                                    ),
                                    border = FilterChipDefaults.filterChipBorder(
                                        enabled = true,
                                        selected = isSelected,
                                        borderColor = (catColor ?: NoteTheme.Primary).copy(alpha = 0.4f),
                                        selectedBorderColor = catColor ?: NoteTheme.Primary,
                                        borderWidth = 1.dp,
                                        selectedBorderWidth = 1.5.dp
                                    )
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            },
            bottomBar = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 35.dp),
                    contentAlignment = Alignment.Center
                ) {
                    FloatingToolbar(
                        onNewNote = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                            navController.navigate("addscreen")
                        },
                        onArchive = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                            navController.navigate("archive")
                        },
                        onToday = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                            navController.navigate("today")
                        }
                    )
                }
            }
        ) { paddingValues ->
            // ─── Pull to Refresh Wrapper ─────────────────────────────────
            PullToRefreshBox(
                isRefreshing = isRefreshingState,
                onRefresh = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    refreshNotes()
                },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                state = pullToRefreshState
            ) {
                when {
                    isLoadingState -> {
                        LoadingCard("Loading your notes...", "Please wait a moment")
                    }

                    searchAndSortState.isSearchActive && searchAndSortState.filteredNotes.isEmpty() -> {
                        EmptyStateCard(
                            icon = Icons.Outlined.SearchOff,
                            title = "No results found",
                            description = "Try a different search term",
                            buttonText = "Clear Search",
                            onButtonClick = {
                                localSearchQuery = ""
                                searchAndSortManager.clearSearch()
                            }
                        )
                    }

                    searchAndSortState.filteredNotes.isEmpty() -> {
                        OfflineEmptyStateCard(
                            isOnline = isOnline,
                            hasPendingSync = hasPendingSync,
                            onCreateNoteClick = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                navController.navigate("addscreen")
                            },
                            onSeedDemoClick = { viewModel.seedDemoNotes() }
                        )
                    }

                    else -> {
                        // Notes display with different view modes
                        NotesDisplay(
                            notes = searchAndSortState.filteredNotes,
                            viewMode = currentViewMode,
                            onView = { note ->
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                navController.navigate("viewnote/${note.id}")
                            },
                            onEdit = { note ->
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                navController.navigate("addscreen/${note.id}")
                            },
                            onDelete = { note -> deleteNote(note.id) },
                            onShare = { note -> shareNote(note) },
                            onPin = { note -> viewModel.togglePin(note.id, note.isPinned) },
                            onArchive = { note -> viewModel.archiveNote(note.id) },
                            onReminder = { note ->
                                selectedNoteForReminder = note
                                val missing = com.amvarpvtltd.swiftNote.components.checkReminderPermissions(context)
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
        }
    }

    // Sort options sheet
    if (showSortSheet) {
        SortOptionsSheet(
            currentSort = searchAndSortState.sortOption,
            onSortChange = { sortOption ->
                searchAndSortManager.updateSortOption(sortOption)
            },
            onDismiss = { showSortSheet = false }
        )
    }

    // Permission rationale sheet
    if (showPermissionRationale && missingPermissionType != null) {
        com.amvarpvtltd.swiftNote.components.PermissionRationaleSheet(
            permissionType = missingPermissionType!!,
            onDismiss = {
                showPermissionRationale = false
                missingPermissionType = null
                selectedNoteForReminder = null
            },
            onPermissionGranted = {
                showPermissionRationale = false
                missingPermissionType = null
                val nextMissing = com.amvarpvtltd.swiftNote.components.checkReminderPermissions(context)
                if (nextMissing != null) {
                    missingPermissionType = nextMissing
                    showPermissionRationale = true
                } else {
                    showReminderSheet = true
                }
            }
        )
    }

    // Reminder sheet
    val noteForReminder = selectedNoteForReminder
    if (showReminderSheet && noteForReminder != null) {
        com.amvarpvtltd.swiftNote.components.ReminderBottomSheet(
            isVisible = showReminderSheet,
            noteId = noteForReminder.id,
            noteTitle = noteForReminder.title,
            noteDescription = noteForReminder.description,
            onDismiss = {
                showReminderSheet = false
                selectedNoteForReminder = null
            },
            onReminderSet = { reminderRequest ->
                scope.launch(Dispatchers.IO) {
                    try {
                        val result = reminderRepository.createReminder(reminderRequest)
                        withContext(Dispatchers.Main) {
                            if (result.isSuccess) {
                                Toast.makeText(context, "⏰ Reminder set successfully!", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "❌ Failed to set reminder", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "❌ Error setting reminder", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                showReminderSheet = false
                selectedNoteForReminder = null
            }
        )
    }
}
