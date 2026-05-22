package com.amvarpvtltd.swiftNote.design

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import com.amvarpvtltd.swiftNote.theme.ProvideNoteTheme
import com.amvarpvtltd.swiftNote.utils.AutoSyncManager
import com.amvarpvtltd.swiftNote.utils.Constants
import com.amvarpvtltd.swiftNote.utils.ShareUtils
import com.amvarpvtltd.swiftNote.viewmodel.NotesViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    // With SharingStarted.Eagerly on NotesViewModel.notes, `notes` already has cached data
    // at first composition after navigation back.
    val searchAndSortManager = rememberSearchAndSortManager(initialNotes = notes)
    val searchAndSortState by searchAndSortManager.searchAndSortState.collectAsStateWithLifecycle()
    var showSortSheet by remember { mutableStateOf(false) }

    // Local search state for immediate UI updates
    var localSearchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }

    // Theme — observe the global AppThemeState singleton.
    // This is the same StateFlow that MyApp's ProvideNoteTheme observes, so toggling here
    // immediately updates ProvideNoteTheme and NoteTheme colors everywhere in the app.
    val currentTheme by com.amvarpvtltd.swiftNote.theme.AppThemeState.themeMode.collectAsStateWithLifecycle()

    // View mode management
    val viewModeState = rememberViewModeState()
    var currentViewMode by viewModeState

    // Adaptive icon colors — computed once, reused for both action buttons
    // (was inside run{} blocks, recomputed on every recomposition — now memoized)
    val (adaptiveIconContainer, adaptiveIconContent) = remember(NoteTheme.Background, NoteTheme.Secondary) {
        com.amvarpvtltd.swiftNote.components.adaptiveIconColors(NoteTheme.Background, NoteTheme.Secondary)
    }

    // Offline banner state
    var showOfflineBanner by remember { mutableStateOf(false) }

    // Reminder functionality state
    var selectedNoteForReminder by remember { mutableStateOf<dataclass?>(null) }
    var showReminderSheet by remember { mutableStateOf(false) }
    var showPermissionRationale by remember { mutableStateOf(false) }
    var missingPermissionType by remember { mutableStateOf<com.amvarpvtltd.swiftNote.components.PermissionType?>(null) }
    val reminderRepository = remember { com.amvarpvtltd.swiftNote.reminders.ReminderRepository(context) }

    // Phase 0.4: Undo-delete Snackbar
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

    // Monitor offline state — only key on isOnline, NOT on notes
    // (was: LaunchedEffect(isOnline, notes) which restarted the 2s delay on every DB change)
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

    // Save view mode when it changes — on IO to avoid main-thread SharedPreferences write
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

    // Share note function (UI-only — no business logic)
    fun shareNote(note: dataclass) {
        ShareUtils.shareNote(context, note)
    }

    // Sync function delegates to ViewModel
    fun syncNotes() { viewModel.syncNotes() }

    // Initial load handled by ViewModel init{}

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

    // Theme persistence is handled inside AppThemeState.setTheme() — no LaunchedEffect needed here

    // Theme already provided by MyApp's ProvideNoteTheme — no double-wrap needed
    NoteScreenBackground {
            Scaffold(
                containerColor = Color.Transparent,
                snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                topBar = {
                    Column {

                        // Add offline banner at the top
                        OfflineBanner(
                            isVisible = showOfflineBanner && !isOnline,
                            message = "You're offline. Notes are cached locally and will sync when connected.",
                            onDismiss = { showOfflineBanner = false }
                        )

                        // Main top bar with only title and theme toggle
                        TopAppBar(
                            title = {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Column {
                                            Text(
                                                text = "My Notes",
                                                fontWeight = FontWeight.ExtraBold,
                                                color = NoteTheme.OnSurface,
                                                style = MaterialTheme.typography.titleLarge,
                                                fontSize = 30.sp,
                                                letterSpacing = (-0.8).sp
                                            )
                                            if (notes.isNotEmpty()) {
                                                Text(
                                                    text = "${notes.size} note${if (notes.size != 1) "s" else ""}",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = NoteTheme.OnSurfaceVariant,
                                                    fontWeight = FontWeight.Normal
                                                )
                                            }
                                        }

                                        if (isRefreshingState) {
                                            Spacer(modifier = Modifier.width(Constants.CORNER_RADIUS_SMALL.dp))
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(Constants.ICON_SIZE_MEDIUM.dp),
                                                strokeWidth = 2.dp,
                                                color = NoteTheme.Primary
                                            )
                                        }
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.offset(x = (-15).dp)) {
                                        // Only keep sync status and theme toggle in top bar
                                        SyncStatusIndicator(
                                            isOnline = isOnline,
                                            hasPendingSync = hasPendingSync,
                                            isSyncing = isSyncing,
                                            onSyncClick = { syncNotes() }
                                        )

                                        Spacer(modifier = Modifier.width(12.dp))

                                        // Sync settings button (adaptive colors)
                                        IconActionButton(
                                            onClick = { navController.navigate("syncSettings") },
                                            icon = Icons.Outlined.Person,
                                            contentDescription = "Sync Settings",
                                            containerColor = adaptiveIconContainer,
                                            contentColor = adaptiveIconContent
                                        )

                                        Spacer(modifier = Modifier.width(12.dp))

                                        // Theme toggle button — updates global AppThemeState
                                        ThemeToggleButton(
                                            currentTheme = currentTheme,
                                            onThemeChange = { newTheme ->
                                                com.amvarpvtltd.swiftNote.theme.AppThemeState.setTheme(context, newTheme)
                                            }
                                        )
                                    }
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                        )

                        // Search bar
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

                        // Move view mode and sort buttons below search bar
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = Constants.PADDING_MEDIUM.dp)
                                .padding(top = 8.dp),
                            horizontalArrangement = Arrangement.End, // Changed to Arrangement.End to move buttons to right
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Show search results count when active - moved to left side
                            if (searchAndSortState.isSearchActive) {
                                Text(
                                    text = "${searchAndSortState.filteredNotes.size} result${if (searchAndSortState.filteredNotes.size != 1) "s" else ""}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = NoteTheme.Warning,
                                    fontWeight = FontWeight.Medium
                                )

                                Spacer(modifier = Modifier.weight(1f)) // Push buttons to right
                            } else {
                                Spacer(modifier = Modifier.weight(1f)) // Take up space when no search results
                            }

                            // View mode toggle button
                            ViewModeToggleButton(
                                currentViewMode = currentViewMode,
                                onViewModeChange = { newViewMode ->
                                    currentViewMode = newViewMode
                                }
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                                // Sort button (adaptive colors)
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

                        Spacer(modifier = Modifier.height(8.dp))
                    }
                },
                floatingActionButton = {
                    AnimatedFloatingActionButton(
                        onClick = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                            navController.navigate("addscreen")
                        }
                    )
                }
            ) { paddingValues ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                    when {
                        isLoadingState -> {
                            LoadingCard("Loading your notes...", "Please wait a moment")
                        }

                        searchAndSortState.isSearchActive && searchAndSortState.filteredNotes.isEmpty() -> {
                            EmptyStateCard(
                                icon = Icons.Outlined.SearchOff,
                                title = "No results found",
                                description = "Try adjusting your search query or filters.",
                                buttonText = "Clear Filters",
                                onButtonClick = {
                                    localSearchQuery = ""
                                    searchAndSortManager.clearSearch()
                                }
                            )
                        }

                        searchAndSortState.filteredNotes.isEmpty() -> {
                            // Use the new OfflineEmptyStateCard for better offline handling
                            OfflineEmptyStateCard(
                                isOnline = isOnline,
                                hasPendingSync = hasPendingSync,
                                onCreateNoteClick = {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                    navController.navigate("addscreen")
                                }
                            )
                        }

                        else -> {
                            Column {
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
                                    onReminder = { note ->
                                        selectedNoteForReminder = note
                                        showReminderSheet = true
                                    }
                                )
                            }
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

        // Permission rationale sheet - shown before reminder sheet when permissions are missing
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
                    // Re-check: if there's another missing permission, show it; otherwise open reminder
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
