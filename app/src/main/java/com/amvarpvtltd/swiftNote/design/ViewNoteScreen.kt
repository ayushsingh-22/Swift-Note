package com.amvarpvtltd.swiftNote.design

import android.util.Log
import android.widget.Toast
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.amvarpvtltd.swiftNote.components.ActionButton
import com.amvarpvtltd.swiftNote.components.DeleteConfirmationDialog
import com.amvarpvtltd.swiftNote.components.EmptyStateCard
import com.amvarpvtltd.swiftNote.components.IconActionButton
import com.amvarpvtltd.swiftNote.components.LoadingCard
import com.amvarpvtltd.swiftNote.components.NoteScreenBackground
import com.amvarpvtltd.swiftNote.dataclass
import com.amvarpvtltd.swiftNote.repository.NoteRepository
import com.amvarpvtltd.swiftNote.utils.Constants
import com.amvarpvtltd.swiftNote.utils.NetworkManager
import com.amvarpvtltd.swiftNote.utils.ShareUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewNoteScreen(navController: NavHostController, noteId: String?) {
    var note by remember { mutableStateOf<dataclass?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current
    val noteRepository = remember { NoteRepository(context) }
    val scope = rememberCoroutineScope()
    val hapticFeedback = LocalHapticFeedback.current
    val scrollState = rememberScrollState()

    // Network management
    val networkManager = remember { NetworkManager.getInstance(context) }
    val isOnline by networkManager.isOnline.collectAsState()

    // OFFLINE FIRST: Load note data from Room database first
    LaunchedEffect(noteId) {
        if (noteId != null) {
            isLoading = true
            errorMessage = null

            try {
                Log.d("ViewNoteScreen", "Loading note: $noteId (Online: $isOnline)")

                // ALWAYS try to load from offline storage first (Room database)
                val result = noteRepository.loadNote(noteId, context)

                if (result.isSuccess) {
                    val loadedNote = result.getOrNull()
                    if (loadedNote != null) {
                        note = loadedNote
                        Log.d("ViewNoteScreen", "Note loaded successfully from offline storage: ${loadedNote.title}")
                    } else {
                        errorMessage = "Note data is empty"
                        Log.w("ViewNoteScreen", "Note loaded but data is null")
                    }
                } else {
                    val error = result.exceptionOrNull()?.message ?: "Unknown error"
                    errorMessage = "Failed to load note: $error"
                    Log.e("ViewNoteScreen", "Failed to load note: $error")
                }
            } catch (e: Exception) {
                errorMessage = "Error loading note: ${e.message}"
                Log.e("ViewNoteScreen", "Exception loading note", e)
            } finally {
                isLoading = false
            }
        } else {
            Log.w("ViewNoteScreen", "No noteId provided")
            isLoading = false
            errorMessage = "No note ID provided"
        }
    }

    // Delete note function - OFFLINE FIRST
    fun deleteNote() {
        if (noteId == null) return
        scope.launch(Dispatchers.IO) {
            try {
                Log.d("ViewNoteScreen", "Deleting note: $noteId (Online: $isOnline)")
                val result = noteRepository.deleteNote(noteId, context)
                if (result.isSuccess) {
                    withContext(Dispatchers.Main) {
                        val message = if (!isOnline) {
                            "📱 Note deleted offline. Will sync when online."
                        } else {
                            "✅ Note deleted successfully"
                        }
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                        navController.navigate("main") {
                            popUpTo("main") { inclusive = false }
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "❌ Error deleting note", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "❌ Error deleting note: ${e.message}", Toast.LENGTH_SHORT).show()
                }
                Log.e("ViewNoteScreen", "Error deleting note", e)
            }
        }
    }

    // Share note function
    fun shareNote() {
        note?.let { currentNote ->
            ShareUtils.shareNote(context, currentNote)
        }
    }

    // Copy note function
    fun copyNote() {
        note?.let { currentNote ->
            ShareUtils.copyNoteToClipboard(context, currentNote)
        }
    }

    DeleteConfirmationDialog(
        showDialog = showDeleteDialog,
        onDismiss = { showDeleteDialog = false },
        onConfirm = {
            showDeleteDialog = false
            deleteNote()
        },
        title = note?.title ?: "",
        message = "Are you sure you want to delete this note? This action cannot be undone."
    )

    NoteScreenBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                Column {
                    Spacer(modifier = Modifier.height(16.dp))

                    TopAppBar(
                        title = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = when {
                                            isLoading -> "Loading..."
                                            errorMessage != null -> "Error"
                                            note != null -> {
                                                val title = note!!.title
                                                if (title.length > 25) "${title.take(25)}..." else title
                                            }
                                            else -> "Note"
                                        },
                                        fontWeight = FontWeight.Bold,
                                        color = NoteTheme.OnSurface,
                                        style = MaterialTheme.typography.titleLarge,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    // Show offline indicator in title area
                                    if (!isOnline) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(top = 2.dp)
                                        ) {
                                            Icon(
                                                Icons.Outlined.CloudOff,
                                                contentDescription = "Offline",
                                                tint = NoteTheme.Warning,
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "Offline Mode",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = NoteTheme.Warning,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }

                                if (isLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
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
                               // Copy action
                               if (note != null) {
                                   IconActionButton(
                                       onClick = {
                                           hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                           copyNote()
                                       },
                                       icon = Icons.Outlined.ContentCopy,
                                       contentDescription = "Copy note",
                                       containerColor = NoteTheme.Secondary.copy(alpha = 0.1f),
                                       contentColor = NoteTheme.Secondary
                                   )
                               }

                               // Share action
                               if (note != null) {
                                   IconActionButton(
                                       onClick = {
                                           hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                           shareNote()
                                       },
                                       icon = Icons.Outlined.Share,
                                       contentDescription = "Share note",
                                       containerColor = NoteTheme.Primary.copy(alpha = 0.1f),
                                       contentColor = NoteTheme.Primary
                                   )
                               }

                               // Delete action
                               if (note != null) {
                                   IconActionButton(
                                       onClick = {
                                           hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                           showDeleteDialog = true
                                       },
                                       icon = Icons.Outlined.Delete,
                                       contentDescription = "Delete note",
                                       containerColor = NoteTheme.Error.copy(alpha = 0.1f),
                                       contentColor = NoteTheme.Error
                                   )
                               }
                           }
                       },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                }
            },
            floatingActionButton = {
                if (note != null) {
                    ActionButton(
                        onClick = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                            // Allow editing in both online and offline modes
                            navController.navigate("addscreen/${note?.id}")
                        },
                        text = if (isOnline) "Edit Note" else "Edit Offline",
                        icon = Icons.Outlined.Edit,
                        modifier = Modifier
                    )
                }
            }
        ) { paddingValues ->
            when {
                isLoading -> {
                    LoadingCard("Loading your note...", "Fetching from local storage...")
                }

                errorMessage != null -> {
                    // Error state
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                            .padding(24.dp),
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
                }

                note == null -> {
                    // No note found state
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        EmptyStateCard(
                            icon = Icons.Outlined.ErrorOutline,
                            title = "Note Not Available",
                            description = "This note could not be loaded. It may not exist in your local storage.",
                            buttonText = "Go Back to Notes",
                            onButtonClick = { navController.navigateUp() }
                        )
                    }
                }

                else -> {
                    // Display note content - WORKS IN BOTH ONLINE AND OFFLINE MODE
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                            .padding(horizontal = Constants.PADDING_MEDIUM.dp)
                            .verticalScroll(scrollState),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Spacer(modifier = Modifier.height(8.dp))

                        // Show offline message if needed
                        if (!isOnline) {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = NoteTheme.Warning.copy(alpha = 0.1f)
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Outlined.CloudOff,
                                        contentDescription = "Offline",
                                        tint = NoteTheme.Warning,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = "Viewing Offline",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = NoteTheme.Warning,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "This note is loaded from your device storage",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = NoteTheme.Warning
                                        )
                                    }
                                }
                            }
                        }

                        // Description Card
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = NoteTheme.Surface
                            ),
                            shape = RoundedCornerShape(Constants.CORNER_RADIUS_LARGE.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(Constants.CORNER_RADIUS_LARGE.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(
                                        Icons.Outlined.Description,
                                        contentDescription = "Description",
                                        tint = NoteTheme.Secondary,
                                        modifier = Modifier.size(Constants.ICON_SIZE_MEDIUM.dp)
                                    )
                                    Spacer(modifier = Modifier.width(Constants.PADDING_SMALL.dp))
                                    Text(
                                        text = "Description",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = NoteTheme.OnSurface,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }

                                Spacer(modifier = Modifier.height(Constants.PADDING_MEDIUM.dp))

                                Text(
                                    text = note?.description ?: "",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = NoteTheme.OnSurface,
                                    lineHeight = 24.sp,
                                    textAlign = TextAlign.Start
                                )
                            }
                        }

                        // Metadata Card
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = NoteTheme.SurfaceVariant.copy(alpha = 0.3f)
                            ),
                            shape = RoundedCornerShape(Constants.CORNER_RADIUS_MEDIUM.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(Constants.PADDING_SMALL.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        text = "Created",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = NoteTheme.OnSurfaceVariant,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = note?.let {
                                            java.text.SimpleDateFormat("MMM dd, yyyy 'at' hh:mm a", java.util.Locale.getDefault())
                                                .format(java.util.Date(it.timestamp))
                                        } ?: "",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = NoteTheme.OnSurface,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }

                                if (!isOnline) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Outlined.CloudOff,
                                            contentDescription = "Offline",
                                            tint = NoteTheme.Warning,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Offline",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = NoteTheme.Warning,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }

                        // Add bottom spacing for FAB
                        Spacer(modifier = Modifier.height(80.dp))
                    }
                }
            }
        }
    }
}
