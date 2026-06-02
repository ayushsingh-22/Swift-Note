package com.amvarpvtltd.swiftNote.design

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.amvarpvtltd.swiftNote.components.*
import com.amvarpvtltd.swiftNote.search.SortOption
import com.amvarpvtltd.swiftNote.utils.Constants
import com.amvarpvtltd.swiftNote.utils.ShareUtils
import com.amvarpvtltd.swiftNote.viewmodel.NotesViewModel
import kotlinx.coroutines.launch
import com.amvarpvtltd.swiftNote.utils.rememberResponsiveDimensions

/**
 * Archive Screen — full-featured screen for archived notes with search, sort,
 * view mode toggle, checklist rendering, and all note actions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchiveScreen(navController: NavHostController) {
    val dims = rememberResponsiveDimensions()
    val viewModel: NotesViewModel = viewModel()
    val context = LocalContext.current
    val hapticFeedback = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    val archivedNotes by remember(viewModel) { viewModel.noteRepository.observeArchivedNotes() }
        .collectAsStateWithLifecycle(initialValue = emptyList())

    // Search state
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }

    // Sort state
    var showSortSheet by remember { mutableStateOf(false) }
    var sortOption by remember { mutableStateOf(SortOption.DATE_CREATED_DESC) }

    // View mode
    val viewModeState = rememberViewModeState()
    var currentViewMode by viewModeState

    // Adaptive icon colors
    val (adaptiveIconContainer, adaptiveIconContent) = remember(NoteTheme.Background, NoteTheme.Secondary) {
        adaptiveIconColors(NoteTheme.Background, NoteTheme.Secondary)
    }

    // Snackbar for undo
    val snackbarHostState = remember { SnackbarHostState() }

    // Filter and sort notes
    val filteredNotes = remember(archivedNotes, searchQuery, sortOption) {
        var result = archivedNotes
        if (searchQuery.isNotBlank()) {
            val query = searchQuery.lowercase()
            result = result.filter {
                it.title.lowercase().contains(query) ||
                        it.description.lowercase().contains(query) ||
                        it.category.lowercase().contains(query)
            }
        }
        when (sortOption) {
            SortOption.DATE_CREATED_DESC -> result.sortedByDescending { it.timestamp }
            SortOption.DATE_CREATED_ASC -> result.sortedBy { it.timestamp }
            SortOption.TITLE_ASC -> result.sortedBy { it.title.lowercase() }
            SortOption.TITLE_DESC -> result.sortedByDescending { it.title.lowercase() }
            else -> result
        }
    }

    NoteScreenBackground {
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                Column {
                    TopAppBar(
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column {
                                    Text(
                                        text = "Archive",
                                        fontWeight = FontWeight.ExtraBold,
                                        color = NoteTheme.OnSurface,
                                        style = MaterialTheme.typography.titleLarge,
                                        fontSize = 26.sp,
                                        letterSpacing = (-0.5).sp
                                    )
                                    Text(
                                        text = "${archivedNotes.size} note${if (archivedNotes.size != 1) "s" else ""}" +
                                                if (searchQuery.isNotBlank()) " • ${filteredNotes.size} matched" else "",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = NoteTheme.OnSurfaceVariant,
                                        fontWeight = FontWeight.Normal
                                    )
                                }
                            }
                        },
                        navigationIcon = {
                            IconActionButton(
                                onClick = { navController.navigateUp() },
                                icon = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                containerColor = NoteTheme.Primary.copy(alpha = 0.1f),
                                contentColor = NoteTheme.Primary,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                    )

                    // Search bar
                    SearchBar(
                        searchQuery = searchQuery,
                        onSearchQueryChange = { searchQuery = it },
                        isSearchActive = isSearchActive,
                        onSearchActiveChange = { isSearchActive = it },
                        onClearSearch = {
                            searchQuery = ""
                            isSearchActive = false
                        },
                        modifier = Modifier.padding(horizontal = dims.paddingMedium)
                    )

                    // Action row: view mode + sort
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = dims.paddingMedium, vertical = 8.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (searchQuery.isNotBlank()) {
                            Text(
                                text = "${filteredNotes.size} result${if (filteredNotes.size != 1) "s" else ""}",
                                style = MaterialTheme.typography.labelMedium,
                                color = NoteTheme.Primary,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.weight(1f))
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }

                        ViewModeToggleButton(
                            currentViewMode = currentViewMode,
                            onViewModeChange = { currentViewMode = it }
                        )

                        Spacer(modifier = Modifier.width(4.dp))

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
            }
        ) { paddingValues ->
            if (filteredNotes.isEmpty()) {
                // Empty state
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = NoteTheme.Spacing.xxl.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .background(
                                    color = NoteTheme.Primary.copy(alpha = 0.08f),
                                    shape = RoundedCornerShape(NoteTheme.Radius.lg.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Outlined.Archive,
                                contentDescription = null,
                                modifier = Modifier.size(36.dp),
                                tint = NoteTheme.Primary
                            )
                        }
                        Spacer(modifier = Modifier.height(NoteTheme.Spacing.xl.dp))
                        Text(
                            text = if (searchQuery.isNotBlank()) "No matching notes" else "No archived notes",
                            style = MaterialTheme.typography.titleLarge,
                            color = NoteTheme.OnSurface,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 22.sp
                        )
                        Spacer(modifier = Modifier.height(NoteTheme.Spacing.sm.dp))
                        Text(
                            text = if (searchQuery.isNotBlank()) "Try a different search term"
                            else "Notes you archive will appear here",
                            style = MaterialTheme.typography.bodyLarge,
                            color = NoteTheme.OnSurfaceVariant,
                            fontWeight = FontWeight.Normal,
                            fontSize = 16.sp
                        )
                    }
                }
            } else {
                // Use NotesDisplay — same as home screen with all features
                Box(modifier = Modifier.padding(paddingValues)) {
                    NotesDisplay(
                        notes = filteredNotes,
                        viewMode = currentViewMode,
                        onView = { note ->
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                            navController.navigate("viewnote/${note.id}")
                        },
                        onEdit = { note ->
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                            navController.navigate("addscreen/${note.id}")
                        },
                        onDelete = { note ->
                            viewModel.deleteNote(note.id)
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    message = "Note deleted",
                                    actionLabel = "Undo",
                                    duration = SnackbarDuration.Short
                                ).let { result ->
                                    if (result == SnackbarResult.ActionPerformed) {
                                        viewModel.undoDelete()
                                    }
                                }
                            }
                        },
                        onShare = { note -> ShareUtils.shareNote(context, note) },
                        onPin = { note -> viewModel.togglePin(note.id, note.isPinned) },
                        onArchive = { note ->
                            // In archive screen, this acts as "Unarchive"
                            viewModel.unarchiveNote(note.id)
                            Toast.makeText(context, "Note restored to main", Toast.LENGTH_SHORT).show()
                        },
                        onReminder = { note ->
                            navController.navigate("viewnote/${note.id}")
                        }
                    )
                }
            }
        }

        // Sort options sheet
        if (showSortSheet) {
            SortOptionsSheet(
                currentSort = sortOption,
                onSortChange = { sortOption = it },
                onDismiss = { showSortSheet = false }
            )
        }
    }
}



