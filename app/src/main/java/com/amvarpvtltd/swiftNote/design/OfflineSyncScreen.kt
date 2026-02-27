package com.amvarpvtltd.swiftNote.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Note
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.amvarpvtltd.swiftNote.components.IconActionButton
import com.amvarpvtltd.swiftNote.components.NoteScreenBackground
import com.amvarpvtltd.swiftNote.offline.OfflineNoteManager
import com.amvarpvtltd.swiftNote.repository.NoteRepository
import com.amvarpvtltd.swiftNote.utils.AutoSyncManager
import com.amvarpvtltd.swiftNote.utils.Constants
import com.amvarpvtltd.swiftNote.utils.NetworkManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfflineSyncScreen(
    navController: NavHostController,
    showBackButton: Boolean = true
) {
    val context = LocalContext.current
    val hapticFeedback = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    val networkManager = remember { NetworkManager.getInstance(context) }
    val offlineManager = remember { OfflineNoteManager(context) }
    val noteRepository = remember { NoteRepository(context) }
    val autoSyncManager = remember { AutoSyncManager.getInstance(context, noteRepository) }

    val isOnline by networkManager.isOnline.collectAsState()
    val isSyncing by autoSyncManager.isSyncing.collectAsState()
    val syncStatus by autoSyncManager.lastSyncStatus.collectAsState()
    val pendingNotes by offlineManager.pendingSyncNotes.collectAsState()

    // Manual sync function
    fun startManualSync() {
        if (!isOnline) return

        scope.launch {
            try {
                val result = noteRepository.syncOfflineNotes(context)
                if (result.isSuccess) {
                    // Success handled by AutoSyncManager status updates
                }
            } catch (e: Exception) {
                // Error handled by AutoSyncManager status updates
            }
        }
    }

    NoteScreenBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                if (showBackButton) {
                    Column {
                        Spacer(modifier = Modifier.height(16.dp))

                        TopAppBar(
                            title = {
                                Text(
                                    text = "Sync Status",
                                    fontWeight = FontWeight.Bold,
                                    color = NoteTheme.OnSurface,
                                    style = MaterialTheme.typography.titleLarge
                                )
                            },
                            navigationIcon = {
                                IconActionButton(
                                    onClick = {
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                        navController.navigateUp()
                                    },
                                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    containerColor = NoteTheme.Primary.copy(alpha = 0.1f),
                                    contentColor = NoteTheme.Primary,
                                    modifier = Modifier.padding(start = 12.dp)
                                )
                            },
                            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                        )

                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(if (showBackButton) paddingValues else PaddingValues(top = 32.dp))
                    .padding(Constants.PADDING_LARGE.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Constants.PADDING_LARGE.dp)
            ) {
                // Connection Status Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = NoteTheme.Surface
                    ),
                    shape = RoundedCornerShape(Constants.CORNER_RADIUS_XL.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(Constants.PADDING_XL.dp)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(Constants.PADDING_MEDIUM.dp)
                    ) {
                        // Connection status icon
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .background(
                                    brush = Brush.radialGradient(
                                        colors = if (isOnline) {
                                            listOf(
                                                NoteTheme.Success.copy(alpha = 0.3f),
                                                NoteTheme.Success.copy(alpha = 0.05f)
                                            )
                                        } else {
                                            listOf(
                                                NoteTheme.Error.copy(alpha = 0.3f),
                                                NoteTheme.Error.copy(alpha = 0.05f)
                                            )
                                        }
                                    ),
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isOnline) Icons.Outlined.Wifi else Icons.Outlined.WifiOff,
                                contentDescription = null,
                                tint = if (isOnline) NoteTheme.Success else NoteTheme.Error,
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        Text(
                            text = if (isOnline) "Connected" else "Disconnected",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isOnline) NoteTheme.Success else NoteTheme.Error
                        )

                        Text(
                            text = if (isOnline)
                                "Your device is connected to the internet"
                            else
                                "No internet connection available",
                            style = MaterialTheme.typography.bodyMedium,
                            color = NoteTheme.OnSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // Sync Status Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = NoteTheme.Surface
                    ),
                    shape = RoundedCornerShape(Constants.CORNER_RADIUS_XL.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(Constants.PADDING_XL.dp)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(Constants.PADDING_MEDIUM.dp)
                    ) {
                        // Sync status icon with animation
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .background(
                                    brush = Brush.radialGradient(
                                        colors = when (syncStatus) {
                                            is AutoSyncManager.SyncStatus.Success -> listOf(
                                                NoteTheme.Success.copy(alpha = 0.3f),
                                                NoteTheme.Success.copy(alpha = 0.05f)
                                            )
                                            is AutoSyncManager.SyncStatus.Failed -> listOf(
                                                NoteTheme.Error.copy(alpha = 0.3f),
                                                NoteTheme.Error.copy(alpha = 0.05f)
                                            )
                                            is AutoSyncManager.SyncStatus.InProgress -> listOf(
                                                NoteTheme.Primary.copy(alpha = 0.3f),
                                                NoteTheme.Primary.copy(alpha = 0.05f)
                                            )
                                            else -> listOf(
                                                NoteTheme.OnSurfaceVariant.copy(alpha = 0.3f),
                                                NoteTheme.OnSurfaceVariant.copy(alpha = 0.05f)
                                            )
                                        }
                                    ),
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSyncing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(32.dp),
                                    strokeWidth = 3.dp,
                                    color = NoteTheme.Primary
                                )
                            } else {
                                Icon(
                                    imageVector = when (syncStatus) {
                                        is AutoSyncManager.SyncStatus.Success -> Icons.Outlined.CheckCircle
                                        is AutoSyncManager.SyncStatus.Failed -> Icons.Outlined.Error
                                        else -> Icons.Outlined.Sync
                                    },
                                    contentDescription = null,
                                    tint = when (syncStatus) {
                                        is AutoSyncManager.SyncStatus.Success -> NoteTheme.Success
                                        is AutoSyncManager.SyncStatus.Failed -> NoteTheme.Error
                                        else -> NoteTheme.OnSurfaceVariant
                                    },
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }

                        Text(
                            text = when {
                                isSyncing -> "Syncing..."
                                syncStatus is AutoSyncManager.SyncStatus.Success -> "Sync Complete"
                                syncStatus is AutoSyncManager.SyncStatus.Failed -> "Sync Failed"
                                else -> "Ready to Sync"
                            },
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                isSyncing -> NoteTheme.Primary
                                syncStatus is AutoSyncManager.SyncStatus.Success -> NoteTheme.Success
                                syncStatus is AutoSyncManager.SyncStatus.Failed -> NoteTheme.Error
                                else -> NoteTheme.OnSurface
                            }
                        )

                        Text(
                            text = when {
                                isSyncing -> "Uploading your notes to the cloud..."
                                syncStatus is AutoSyncManager.SyncStatus.Success -> "All notes are synchronized"
                                syncStatus is AutoSyncManager.SyncStatus.Failed -> "Failed to sync some notes"
                                pendingNotes.isNotEmpty() -> "${pendingNotes.size} notes waiting to sync"
                                else -> "All notes are up to date"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = NoteTheme.OnSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // Pending Notes Card
                if (pendingNotes.isNotEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = NoteTheme.Warning.copy(alpha = 0.1f)
                        ),
                        shape = RoundedCornerShape(Constants.CORNER_RADIUS_LARGE.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(Constants.PADDING_LARGE.dp)
                                .fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Constants.PADDING_SMALL.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Schedule,
                                    contentDescription = null,
                                    tint = NoteTheme.Warning,
                                    modifier = Modifier.size(Constants.ICON_SIZE_MEDIUM.dp)
                                )
                                Text(
                                    text = "Pending Sync",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = NoteTheme.Warning
                                )
                            }

                            Spacer(modifier = Modifier.height(Constants.PADDING_SMALL.dp))

                            Text(
                                text = "${pendingNotes.size} note${if (pendingNotes.size != 1) "s" else ""} waiting to be synchronized with the cloud",
                                style = MaterialTheme.typography.bodyMedium,
                                color = NoteTheme.OnSurfaceVariant
                            )
                        }
                    }
                }

                // Action Buttons
                Column(
                    verticalArrangement = Arrangement.spacedBy(Constants.PADDING_MEDIUM.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Manual sync button
                    Button(
                        onClick = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                            startManualSync()
                        },
                        enabled = isOnline && !isSyncing && pendingNotes.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NoteTheme.Primary
                        ),
                        shape = RoundedCornerShape(Constants.CORNER_RADIUS_MEDIUM.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Outlined.Sync,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isSyncing) "Syncing..." else "Sync Now",
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Go to notes button
                    OutlinedButton(
                        onClick = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                            navController.navigate("main") {
                                popUpTo("offlineSyncScreen") { inclusive = true }
                            }
                        },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = NoteTheme.OnSurfaceVariant
                        ),
                        shape = RoundedCornerShape(Constants.CORNER_RADIUS_MEDIUM.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.AutoMirrored.Outlined.Note,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("View Notes", fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}
