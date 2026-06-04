package com.amvarpvtltd.swiftNote.design

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.amvarpvtltd.swiftNote.components.EmptyStateCard
import com.amvarpvtltd.swiftNote.components.IconActionButton
import com.amvarpvtltd.swiftNote.components.NoteScreenBackground
import com.amvarpvtltd.swiftNote.components.NotesDisplay
import com.amvarpvtltd.swiftNote.components.SearchBar
import com.amvarpvtltd.swiftNote.components.SortOptionsSheet
import com.amvarpvtltd.swiftNote.components.ViewModeToggleButton
import com.amvarpvtltd.swiftNote.components.adaptiveIconColors
import com.amvarpvtltd.swiftNote.components.rememberViewModeState
import com.amvarpvtltd.swiftNote.search.SortOption
import com.amvarpvtltd.swiftNote.utils.ShareUtils
import com.amvarpvtltd.swiftNote.utils.rememberResponsiveDimensions
import com.amvarpvtltd.swiftNote.viewmodel.NotesViewModel
import kotlinx.coroutines.launch

/**
 * Archive Screen — consistent with the home screen design language.
 * Custom header (no TopAppBar), large title with count badge,
 * inline search + ViewMode + Sort pill, animated results count.
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

    // Adaptive icon colors — same as home screen
    val (adaptiveIconContainer, adaptiveIconContent) = remember(NoteTheme.Background, NoteTheme.Secondary) {
        adaptiveIconColors(NoteTheme.Background, NoteTheme.Secondary)
    }

    // Snackbar for undo-delete
    val snackbarHostState = remember { SnackbarHostState() }

    // Filter + sort
    val filteredNotes = remember(archivedNotes, searchQuery, sortOption) {
        var result = archivedNotes
        if (searchQuery.isNotBlank()) {
            val q = searchQuery.lowercase()
            result = result.filter {
                it.title.lowercase().contains(q) ||
                        it.description.lowercase().contains(q) ||
                        it.category.lowercase().contains(q)
            }
        }
        when (sortOption) {
            SortOption.DATE_CREATED_DESC  -> result.sortedByDescending { it.timestamp }
            SortOption.DATE_CREATED_ASC   -> result.sortedBy { it.timestamp }
            SortOption.TITLE_ASC          -> result.sortedBy { it.title.lowercase() }
            SortOption.TITLE_DESC         -> result.sortedByDescending { it.title.lowercase() }
            else                          -> result
        }
    }

    NoteScreenBackground {
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                Column(
                    modifier = Modifier
                        .animateContentSize(
                            animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy)
                        )
                        .background(NoteTheme.Background)
                ) {
                    // ─── Status bar spacer ────────────────────────────────────
                    Spacer(modifier = Modifier.statusBarsPadding())

                    // ─── Subtitle row ─────────────────────────────────────────
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = dims.paddingMedium)
                            .padding(top = 6.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Archived notes",
                            style = MaterialTheme.typography.labelLarge,
                            color = NoteTheme.OnSurfaceVariant,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 0.3.sp
                        )
                    }

                    // ─── Title row ────────────────────────────────────────────
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = dims.paddingMedium)
                            .padding(top = 6.dp, bottom = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Archive",
                            fontWeight = FontWeight.Black,
                            color = NoteTheme.OnSurface,
                            style = MaterialTheme.typography.headlineMedium,
                            fontSize = 28.sp,
                            letterSpacing = (-0.8).sp
                        )

                        if (archivedNotes.isNotEmpty()) {
                            Spacer(modifier = Modifier.width(10.dp))
                            Box(
                                modifier = Modifier
                                    .background(
                                        color = NoteTheme.Primary.copy(alpha = 0.1f),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                AnimatedContent(
                                    targetState = archivedNotes.size,
                                    transitionSpec = {
                                        (slideInVertically { -it } + fadeIn()).togetherWith(
                                            slideOutVertically { it } + fadeOut()
                                        )
                                    },
                                    label = "archive_count"
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
                    }

                    // ─── Search bar + inline view/sort controls ───────────────
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = dims.paddingMedium),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SearchBar(
                            searchQuery = searchQuery,
                            onSearchQueryChange = { searchQuery = it },
                            isSearchActive = isSearchActive,
                            onSearchActiveChange = { isSearchActive = it },
                            onClearSearch = {
                                searchQuery = ""
                                isSearchActive = false
                            },
                            modifier = Modifier.weight(1f)
                        )

                        ViewModeToggleButton(
                            currentViewMode = currentViewMode,
                            onViewModeChange = { currentViewMode = it }
                        )

                        // Sort pill — same style as home screen
                        val isSortActive = sortOption != SortOption.DATE_CREATED_DESC
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(
                                    if (isSortActive) NoteTheme.Primary.copy(alpha = 0.14f)
                                    else adaptiveIconContainer
                                )
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                    showSortSheet = true
                                }
                                .padding(horizontal = 14.dp, vertical = 9.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Tune,
                                    contentDescription = "Sort",
                                    tint = if (isSortActive) NoteTheme.Primary else adaptiveIconContent,
                                    modifier = Modifier.size(17.dp)
                                )

                            }
                        }
                    }

                    // Search results count — only shown while searching
                    AnimatedVisibility(
                        visible = isSearchActive && searchQuery.isNotBlank(),
                        enter = slideInVertically { -it } + fadeIn(),
                        exit = slideOutVertically { -it } + fadeOut()
                    ) {
                        Text(
                            text = "${filteredNotes.size} result${if (filteredNotes.size != 1) "s" else ""}",
                            style = MaterialTheme.typography.labelMedium,
                            color = NoteTheme.OnSurfaceVariant,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .padding(horizontal = dims.paddingMedium)
                                .padding(top = 6.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                }
            }
        ) { paddingValues ->
            if (filteredNotes.isEmpty()) {
                EmptyStateCard(
                    icon = Icons.Outlined.Archive,
                    title = if (searchQuery.isNotBlank()) "No matching notes" else "No archived notes",
                    description = if (searchQuery.isNotBlank()) "Try a different search term"
                                  else "Notes you archive will appear here",
                    buttonText = if (searchQuery.isNotBlank()) "Clear Search" else "Go Back",
                    onButtonClick = {
                        if (searchQuery.isNotBlank()) {
                            searchQuery = ""
                            isSearchActive = false
                        } else {
                            navController.navigateUp()
                        }
                    },
                    modifier = Modifier.padding(paddingValues)
                )
            } else {
                Box(modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                ) {
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
                                val result = snackbarHostState.showSnackbar(
                                    message = "Note deleted",
                                    actionLabel = "Undo",
                                    duration = SnackbarDuration.Short
                                )
                                if (result == SnackbarResult.ActionPerformed) {
                                    viewModel.undoDelete()
                                }
                            }
                        },
                        onShare = { note -> ShareUtils.shareNote(context, note) },
                        onPin = { note -> viewModel.togglePin(note.id, note.isPinned) },
                        onArchive = { note ->
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



