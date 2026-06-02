@file:Suppress("DEPRECATION")

package com.amvarpvtltd.swiftNote.design

import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.amvarpvtltd.swiftNote.auth.PassphraseManager
import com.amvarpvtltd.swiftNote.auth.DeviceManager
import com.amvarpvtltd.swiftNote.auth.SyncMode
import com.amvarpvtltd.swiftNote.DeviceIdentity
import com.amvarpvtltd.swiftNote.sync.SyncManager
import com.amvarpvtltd.swiftNote.room.AppDatabase
import com.amvarpvtltd.swiftNote.repository.NoteRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import com.amvarpvtltd.swiftNote.components.IconActionButton
import com.amvarpvtltd.swiftNote.components.NoteScreenBackground
import com.amvarpvtltd.swiftNote.components.DisconnectSyncDialog
import com.amvarpvtltd.swiftNote.components.InAppNotificationBanner
import com.amvarpvtltd.swiftNote.components.NotificationHelper
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncSettingsScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val hapticFeedback = LocalHapticFeedback.current
    @Suppress("DEPRECATION")
    val clipboardManager = LocalClipboardManager.current

    var currentPassphrase by remember { mutableStateOf("") }
    var qrCodeBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var showQRCode by remember { mutableStateOf(false) }
    var showSyncDialog by remember { mutableStateOf(false) }
    var showQRScanner by remember { mutableStateOf(false) }
    var inputPassphrase by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var syncStats by remember { mutableStateOf<com.amvarpvtltd.swiftNote.sync.SyncStats?>(null) }

    // Phase 5 — Disconnect from Continuous Sync
    var syncMode by remember { mutableStateOf(SyncMode.LOCAL_ONLY) }
    var showDisconnectDialog by remember { mutableStateOf(false) }
    var disconnectIsLoading by remember { mutableStateOf(false) }

    // Animation states for enhanced UX
    var statsVisible by remember { mutableStateOf(false) }
    var cardsVisible by remember { mutableStateOf(false) }

    // Performance: remember brush to avoid recreating gradient on every recomposition
    // NoteScreenBackground is now the primary wrapper; backgroundBrush kept for reference only

    // Load current passphrase on screen load
    LaunchedEffect(Unit) {
        // BUG-026 FIX: Never use empty string as Firebase path — always fallback to deviceId
        val stored = PassphraseManager.getStoredPassphrase(context)
        val deviceId = DeviceManager.getOrCreateDeviceId(context)
        currentPassphrase = stored ?: deviceId
        DeviceIdentity.setIfEmpty(currentPassphrase, "SyncSettingsScreen")
        // Phase 5: read sync mode so Disconnect card renders correctly
        syncMode = PassphraseManager.getSyncMode(context)
        if (currentPassphrase.isNotEmpty()) {
            // Load sync stats with animation
            scope.launch {
                val statsResult = SyncManager.getSyncStats(currentPassphrase)
                if (statsResult.isSuccess) {
                    syncStats = statsResult.getOrNull()
                    delay(300) // Small delay for smooth animation
                    statsVisible = true
                }
            }
        }
        // Animate cards appearance
        delay(100)
        cardsVisible = true
    }

    NoteScreenBackground {
    Scaffold(
        topBar = {
            Column {
            TopAppBar(
                title = {
                    Text(
                        "Sync Settings",
                        color = NoteTheme.OnSurface,
                        fontWeight = FontWeight.Bold
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
            Spacer(modifier = Modifier.height(3.dp))
            }
        },
        // make scaffold transparent and use NoteScreenBackground wrapper
        containerColor = Color.Transparent
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Enhanced animated layout with improved visual hierarchy
            AnimatedVisibility(
                visible = cardsVisible,
                enter = slideInVertically(
                    initialOffsetY = { it / 2 },
                    animationSpec = tween(600, easing = EaseOutCubic)
                ) + fadeIn(animationSpec = tween(600))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    // Enhanced Device Info Card
                    AnimatedDeviceInfoCard(
                        currentPassphrase = currentPassphrase,
                        syncStats = syncStats,
                        statsVisible = statsVisible,
                        syncMode = syncMode,
                        onCopyPassphrase = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                            clipboardManager.setText(AnnotatedString(currentPassphrase))
                            Toast.makeText(context, "Passphrase copied!", Toast.LENGTH_SHORT).show()
                        }
                    )

                    // Enhanced Share & Sync Actions Card
                    AnimatedActionsCard(
                        currentPassphrase = currentPassphrase,
                        onShowQR = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                            if (currentPassphrase.isNotEmpty()) {
                                qrCodeBitmap = PassphraseManager.generateQRCode(currentPassphrase)
                                showQRCode = true
                            }
                        },
                        onCopyPassphrase = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                            clipboardManager.setText(AnnotatedString(currentPassphrase))
                            Toast.makeText(context, "Passphrase copied to clipboard!", Toast.LENGTH_LONG).show()
                        },
                        onScanQR = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                            showQRScanner = true
                        },
                        onManualSync = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                            showSyncDialog = true
                        }
                    )

                    // ── Continuous Sync Active card (Phase 5) ──────────────────
                    // Visible ONLY when this device has joined a shared account via
                    // Continuous Restore. Hidden for LOCAL_ONLY and ONE_TIME_IMPORTED.
                    AnimatedVisibility(visible = syncMode == SyncMode.CONTINUOUS) {
                        ContinuousSyncActiveCard(
                            passphrase = currentPassphrase,
                            onDisconnect = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                showDisconnectDialog = true
                            }
                        )
                    }

                    // Enhanced Info Card
                    AnimatedInfoCard()
                }
            }

            // ── In-app notification banner overlay (Phase 5) ──────────────
            // Slides in from the top; displaces nothing; auto-dismisses.
            InAppNotificationBanner(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
    }

    // Enhanced QR Code Dialog
    val bitmap = qrCodeBitmap
    if (showQRCode && bitmap != null) {
        EnhancedQRCodeDialog(
            bitmap = bitmap,
            passphrase = currentPassphrase,
            onDismiss = { showQRCode = false }
        )
    }

    // QR Scanner with enhanced feedback
    if (showQRScanner) {
        val scannerContext = LocalContext.current
        QRScannerSection(
            onQRScanned = { qrContent ->
                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                val extracted = PassphraseManager.extractPassphraseFromQR(qrContent)
                if (extracted == null) {
                    Toast.makeText(scannerContext, "Invalid QR code format", Toast.LENGTH_SHORT).show()
                    showQRScanner = false
                    return@QRScannerSection
                }

                scope.launch {
                    isLoading = true
                    errorMessage = ""
                    try {
                        val passphrase = extracted
                        val verifyResult = PassphraseManager.verifyPassphrase(passphrase)
                        if (verifyResult.isSuccess && (verifyResult.getOrNull() == true)) {
                            val syncResult = SyncManager.syncDataFromPassphrase(scannerContext, passphrase, currentPassphrase)
                            if (syncResult.isSuccess) {
                                val result = syncResult.getOrNull()
                                Toast.makeText(
                                    scannerContext,
                                    "✅ Synced ${result?.syncedNotesCount ?: 0} notes!",
                                    Toast.LENGTH_LONG
                                ).show()

                                val statsResult = SyncManager.getSyncStats(currentPassphrase)
                                if (statsResult.isSuccess) syncStats = statsResult.getOrNull()
                            } else {
                                errorMessage = syncResult.exceptionOrNull()?.message ?: "Sync failed"
                            }
                        } else {
                            errorMessage = verifyResult.exceptionOrNull()?.message ?: "Passphrase not found. Please check and try again."
                        }
                    } catch (e: Exception) {
                        errorMessage = "Error: ${e.message}"
                    } finally {
                        isLoading = false
                        showQRScanner = false
                    }
                }
            },
            onCancel = {
                showQRScanner = false
            }
        )
    }

    // Enhanced Sync Dialog
    if (showSyncDialog) {
        EnhancedSyncFromDeviceDialog(
            inputPassphrase = inputPassphrase,
            onPassphraseChange = {
                inputPassphrase = it.lowercase()
                errorMessage = ""
            },
            errorMessage = errorMessage,
            isLoading = isLoading,
            onSync = {
                scope.launch {
                    if (inputPassphrase.isBlank()) {
                        errorMessage = "Please enter a passphrase"
                        return@launch
                    }

                    isLoading = true
                    errorMessage = ""

                    try {
                        val verifyResult = PassphraseManager.verifyPassphrase(inputPassphrase)
                        if (verifyResult.isSuccess) {
                            val exists = verifyResult.getOrNull() ?: false
                            if (exists) {
                                val syncResult = SyncManager.syncDataFromPassphrase(
                                    context, inputPassphrase, currentPassphrase
                                )

                                if (syncResult.isSuccess) {
                                    val result = syncResult.getOrNull()
                                    Toast.makeText(
                                        context,
                                        "✅ Synced ${result?.syncedNotesCount ?: 0} notes!",
                                        Toast.LENGTH_LONG
                                    ).show()

                                    showSyncDialog = false
                                    inputPassphrase = ""

                                    val statsResult = SyncManager.getSyncStats(currentPassphrase)
                                    if (statsResult.isSuccess) {
                                        syncStats = statsResult.getOrNull()
                                    }
                                } else {
                                    errorMessage = "Sync failed: ${syncResult.exceptionOrNull()?.message ?: "Unknown error"}"
                                }
                            } else {
                                errorMessage = "Passphrase not found. Please check and try again."
                            }
                        } else {
                            errorMessage = verifyResult.exceptionOrNull()?.message ?: "Failed to verify passphrase. Please try again."
                        }
                    } catch (e: Exception) {
                        errorMessage = "Error: ${e.message}"
                    } finally {
                        isLoading = false
                    }
                }
            },
            onCancel = {
                showSyncDialog = false
                inputPassphrase = ""
                errorMessage = ""
            }
        )
    }
    } // end NoteScreenBackground

    // ── Disconnect from Continuous Sync dialog (Phase 5) ──────────────────────
    if (showDisconnectDialog) {
        DisconnectSyncDialog(
            isLoading = disconnectIsLoading,
            onKeepNotes = {
                scope.launch {
                    disconnectIsLoading = true
                    try {
                        val result = disconnectKeepNotes(context)
                        if (result.isSuccess) {
                            syncMode = SyncMode.LOCAL_ONLY
                            currentPassphrase = DeviceManager.getOrCreateDeviceId(context)
                            showDisconnectDialog = false
                            Toast.makeText(context, "✅ Disconnected. Notes kept on this device.", Toast.LENGTH_LONG).show()
                        } else {
                            // Local part succeeds; Firebase upload may have failed.
                            // We still update UI since the identity switch is done.
                            syncMode = PassphraseManager.getSyncMode(context)
                            if (syncMode == SyncMode.LOCAL_ONLY) {
                                currentPassphrase = DeviceManager.getOrCreateDeviceId(context)
                                showDisconnectDialog = false
                            }
                            Toast.makeText(
                                context,
                                "Disconnected (notes kept), but upload to new account failed: ${result.exceptionOrNull()?.message}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    } finally {
                        disconnectIsLoading = false
                    }
                }
            },
            onRemoveNotes = {
                scope.launch {
                    disconnectIsLoading = true
                    try {
                        val result = disconnectRemoveNotes(context)
                        syncMode = SyncMode.LOCAL_ONLY
                        currentPassphrase = DeviceManager.getOrCreateDeviceId(context)
                        showDisconnectDialog = false
                        if (result.isSuccess) {
                            Toast.makeText(context, "✅ Disconnected. Notes removed from this device.", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(
                                context,
                                "Disconnected, but some cleanup failed: ${result.exceptionOrNull()?.message}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    } finally {
                        disconnectIsLoading = false
                    }
                }
            },
            onCancel = { showDisconnectDialog = false }
        )
    }
}

// ─── Phase 5: Disconnect helpers ─────────────────────────────────────────────

/**
 * Disconnects from Continuous Sync while KEEPING all local notes.
 *
 * Steps:
 *  1. Switch identity to deviceId (storePassphrase + setSyncMode LOCAL_ONLY).
 *  2. Mark all local notes as unsynced so the upload cycle re-syncs them to the
 *     new deviceId-rooted path (not the old shared account path).
 *  3. Upload to users/{deviceId}/notes/{deviceId}/.
 *
 * ⚠️  Deliberately does NOT touch users/{sourcePassphrase}/ — other devices
 *      in the shared account keep their data.
 */
private suspend fun disconnectKeepNotes(context: android.content.Context): Result<Unit> =
    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val deviceId = DeviceManager.getOrCreateDeviceId(context)
            // 1 — Switch identity back to deviceId
            PassphraseManager.storePassphrase(context, deviceId).getOrThrow()
            PassphraseManager.setSyncMode(context, SyncMode.LOCAL_ONLY)
            // 2 — Force re-upload: all notes marked unsynced go to users/{deviceId}/
            AppDatabase.getInstance(context).noteDao().markAllUnsynced()
            // 3 — Upload to the new identity path
            SyncManager.uploadLocalDataToFirebase(context, deviceId).getOrThrow()
            android.util.Log.d("Disconnect", "keepNotes complete — identity now $deviceId")
            Result.success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("Disconnect", "keepNotes failed", e)
            Result.failure(e)
        }
    }

/**
 * Disconnects from Continuous Sync and REMOVES all local notes.
 *
 * Steps:
 *  1. Delete local notes, reminders, pending-deletions from Room.
 *  2. Switch identity to deviceId (storePassphrase + setSyncMode LOCAL_ONLY).
 *  3. Skip the next cloud-pull so the sync cycle doesn't re-fetch the old notes.
 *
 * ⚠️  Deliberately does NOT touch users/{sourcePassphrase}/ — other devices
 *      in the shared account keep their data.
 */
private suspend fun disconnectRemoveNotes(context: android.content.Context): Result<Unit> =
    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val deviceId = DeviceManager.getOrCreateDeviceId(context)
            val db = AppDatabase.getInstance(context)
            // 1 — Wipe local data ONLY (NOT the shared Firebase account)
            db.noteDao().deleteAllNotes()
            db.pendingDeletionDao().clearAllPendingDeletions()
            db.reminderDao().clearAll()
            // 2 — Restore device identity
            PassphraseManager.storePassphrase(context, deviceId).getOrThrow()
            PassphraseManager.setSyncMode(context, SyncMode.LOCAL_ONLY)
            // 3 — Prevent next sync cycle from re-pulling the shared account's notes
            NoteRepository.markSkipNextCloudPull(context)
            android.util.Log.d("Disconnect", "removeNotes complete — identity now $deviceId")
            Result.success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("Disconnect", "removeNotes failed", e)
            Result.failure(e)
        }
    }

// ─── Phase 5: Continuous Sync Active card ────────────────────────────────────

/**
 * Shown in SyncSettingsScreen when [SyncMode.CONTINUOUS] is active.
 *
 * Design language mirrors the other [ElevatedCard]s on this screen:
 *  – [NoteTheme.Surface] container (not a coloured tint)
 *  – [EnhancedSectionHeader]-style icon badge (Card + Icon)
 *  – Pulsing "● LIVE" badge to signal an active connection
 *  – Account info row styled like the passphrase card in [AnimatedDeviceInfoCard]
 *  – [FilledTonalButton] with error-tint and spring press-scale for the disconnect CTA
 */
@Composable
private fun ContinuousSyncActiveCard(
    passphrase: String,
    onDisconnect: () -> Unit
) {
    val preview = if (passphrase.length > 14) passphrase.take(14) + "…" else passphrase

    // ── Pulsing glow for the LIVE badge ──────────────────────────────────
    val infiniteTransition = rememberInfiniteTransition(label = "sync_live")
    val liveAlpha by infiniteTransition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(950, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "live_alpha"
    )

    // ── Press animation for disconnect button ─────────────────────────────
    var disconnectPressed by remember { mutableStateOf(false) }
    val disconnectBtnScale by animateFloatAsState(
        targetValue = if (disconnectPressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = 600f),
        label = "disconnect_btn_scale"
    )

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = NoteTheme.Surface,
            contentColor = NoteTheme.OnSurface
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Header — mirrors EnhancedSectionHeader ────────────────────
            Row(verticalAlignment = Alignment.CenterVertically) {
                Card(
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = NoteTheme.SuccessContainer.copy(alpha = 0.5f)
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Sync,
                        contentDescription = null,
                        tint = NoteTheme.Success,
                        modifier = Modifier
                            .size(24.dp)
                            .padding(4.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Continuous Sync Active",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = NoteTheme.OnSurface
                    )
                    Text(
                        text = "Sharing notes across devices",
                        style = MaterialTheme.typography.bodySmall,
                        color = NoteTheme.OnSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                }
                // ── Pulsing LIVE pill ─────────────────────────────────────
                Box(
                    modifier = Modifier
                        .background(
                            color = NoteTheme.SuccessContainer.copy(alpha = liveAlpha * 0.75f),
                            shape = RoundedCornerShape(20.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(
                                    NoteTheme.Success.copy(alpha = liveAlpha),
                                    shape = RoundedCornerShape(50)
                                )
                        )
                        Text(
                            text = "LIVE",
                            style = MaterialTheme.typography.labelSmall,
                            color = NoteTheme.OnSuccessContainer,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.8.sp
                        )
                    }
                }
            }

            // ── Linked account info — matches passphrase card style ────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(
                    containerColor = NoteTheme.PrimaryContainer.copy(alpha = 0.3f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Link,
                        contentDescription = null,
                        tint = NoteTheme.Primary.copy(alpha = 0.7f),
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Shared account  ",
                        style = MaterialTheme.typography.bodySmall,
                        color = NoteTheme.OnSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = preview,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = NoteTheme.OnSurface,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            HorizontalDivider(
                thickness = 1.dp,
                color = NoteTheme.Outline.copy(alpha = 0.3f)
            )

            // ── Description ───────────────────────────────────────────────
            Text(
                text = "Note changes on this device are synced to the shared account. To switch to a private account, disconnect below.",
                style = MaterialTheme.typography.bodySmall,
                color = NoteTheme.OnSurfaceVariant,
                lineHeight = 18.sp
            )

            // ── Disconnect CTA ────────────────────────────────────────────
            FilledTonalButton(
                onClick = {
                    disconnectPressed = true
                    onDisconnect()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .scale(disconnectBtnScale),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = NoteTheme.ErrorContainer.copy(alpha = 0.6f),
                    contentColor = NoteTheme.Error
                )
            ) {
                Icon(
                    imageVector = Icons.Default.LinkOff,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Disconnect from Shared Account",
                    fontWeight = FontWeight.SemiBold
                )
            }

            LaunchedEffect(disconnectPressed) {
                if (disconnectPressed) {
                    delay(150)
                    disconnectPressed = false
                }
            }
        }
    }
}

// ─── Phase 6: Sync Mode status chip ─────────────────────────────────────────

/**
 * A compact chip displayed in [AnimatedDeviceInfoCard] that shows the current [SyncMode]
 * with a matching icon and colour so the user always knows at a glance how their
 * identity is configured.
 *
 *  LOCAL_ONLY        → neutral phone icon  — "Local only"
 *  CONTINUOUS        → green sync icon     — "Continuous Sync"
 *  ONE_TIME_IMPORTED → indigo cloud↓ icon  — "Imported (one-time)"
 */
@Composable
private fun SyncModeStatusChip(syncMode: SyncMode) {
    val chipIcon = when (syncMode) {
        SyncMode.CONTINUOUS      -> Icons.Default.Sync
        SyncMode.ONE_TIME_IMPORTED -> Icons.Default.CloudDownload
        SyncMode.LOCAL_ONLY      -> Icons.Default.PhoneAndroid
    }
    val chipLabel = when (syncMode) {
        SyncMode.CONTINUOUS      -> "Continuous Sync"
        SyncMode.ONE_TIME_IMPORTED -> "Imported (one-time)"
        SyncMode.LOCAL_ONLY      -> "Local only"
    }
    val containerColor = when (syncMode) {
        SyncMode.CONTINUOUS      -> NoteTheme.SuccessContainer.copy(alpha = 0.8f)
        SyncMode.ONE_TIME_IMPORTED -> NoteTheme.PrimaryContainer.copy(alpha = 0.8f)
        SyncMode.LOCAL_ONLY      -> NoteTheme.SecondaryContainer.copy(alpha = 0.5f)
    }
    val contentColor = when (syncMode) {
        SyncMode.CONTINUOUS      -> NoteTheme.OnSuccessContainer
        SyncMode.ONE_TIME_IMPORTED -> NoteTheme.OnPrimaryContainer
        SyncMode.LOCAL_ONLY      -> NoteTheme.OnSecondaryContainer
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(color = containerColor, shape = RoundedCornerShape(20.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Icon(
            imageVector = chipIcon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = chipLabel,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            fontWeight = FontWeight.SemiBold
        )
    }
}

// Enhanced animated device info card
@Composable
private fun AnimatedDeviceInfoCard(
    currentPassphrase: String,
    syncStats: com.amvarpvtltd.swiftNote.sync.SyncStats?,
    statsVisible: Boolean,
    syncMode: SyncMode,
    onCopyPassphrase: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
//        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 8.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = NoteTheme.Surface,
            contentColor = NoteTheme.OnSurface
        )
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            EnhancedSectionHeader(
                icon = Icons.Default.Smartphone,
                title = "This Device",
                subtitle = "Your sync identity"
            )

            // Phase 6 — Sync mode indicator chip
            Spacer(modifier = Modifier.height(8.dp))
            SyncModeStatusChip(syncMode = syncMode)

            Spacer(modifier = Modifier.height(12.dp))

            if (currentPassphrase.isNotEmpty()) {
                Text(
                    text = "Passphrase",
                    style = MaterialTheme.typography.labelMedium,
                    color = NoteTheme.OnSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(8.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        // Use full PrimaryContainer (not alpha-blended) so both
                        // themes get equivalent contrast against the parent card:
                        // pale indigo on white (light) and dark indigo on dark
                        // surface — instead of the previous 0.3α which rendered
                        // nearly invisible in light mode.
                        containerColor = NoteTheme.PrimaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SelectionContainer(modifier = Modifier.weight(1f)) {
                            Text(
                                text = currentPassphrase,
                                style = MaterialTheme.typography.bodyLarge,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = NoteTheme.OnPrimaryContainer
                            )
                        }

                        var buttonScale by remember { mutableStateOf(1f) }
                        val animatedScale by animateFloatAsState(
                            targetValue = buttonScale,
                            animationSpec = spring(dampingRatio = 0.3f)
                        )

                        FilledTonalIconButton(
                            onClick = {
                                buttonScale = 0.8f
                                onCopyPassphrase()
                            },
                            modifier = Modifier
                                .padding(start = 12.dp)
                                .scale(animatedScale),
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = NoteTheme.Primary,
                                contentColor = NoteTheme.OnPrimary
                            )
                        ) {
                            Icon(
                                Icons.Default.ContentCopy,
                                contentDescription = "Copy",
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        LaunchedEffect(buttonScale) {
                            if (buttonScale != 1f) {
                                delay(150)
                                buttonScale = 1f
                            }
                        }
                    }
                }

                // Enhanced animated stats section
                AnimatedVisibility(
                    visible = statsVisible && syncStats != null,
                    enter = slideInVertically(
                        initialOffsetY = { it / 3 },
                        animationSpec = tween(500, delayMillis = 200)
                    ) + fadeIn(animationSpec = tween(500, delayMillis = 200))
                ) {
                    syncStats?.let { stats ->
                        Column {
                            Spacer(modifier = Modifier.height(16.dp))
                            HorizontalDivider(
                                thickness = 1.dp,
                                color = NoteTheme.Outline.copy(alpha = 0.3f)
                            )
                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = "Sync Statistics",
                                style = MaterialTheme.typography.labelMedium,
                                color = NoteTheme.OnSurfaceVariant,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                EnhancedStatItem(
                                    value = stats.totalNotes.toString(),
                                    label = "Notes",
                                    icon = Icons.Default.Description,
                                    color = NoteTheme.Primary
                                )

                                val lastSync = if (stats.lastSyncAt > 0)
                                    SimpleDateFormat("MMM dd", Locale.getDefault()).format(Date(stats.lastSyncAt))
                                else "Never"
                                EnhancedStatItem(
                                    value = lastSync,
                                    label = "Last Sync",
                                    icon = Icons.Default.Sync,
                                    color = NoteTheme.Success
                                )
                            }
                        }
                    }
                }
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = NoteTheme.WarningContainer.copy(alpha = 0.3f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = NoteTheme.Warning,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "No passphrase found for this device.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = NoteTheme.OnSurface,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

// Enhanced animated actions card
@Composable
private fun AnimatedActionsCard(
    currentPassphrase: String,
    onShowQR: () -> Unit,
    onCopyPassphrase: () -> Unit,
    onScanQR: () -> Unit,
    onManualSync: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 8.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = NoteTheme.Surface,
            contentColor = NoteTheme.OnSurface
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            EnhancedActionSection(
                icon = Icons.Default.Share,
                title = "Share with Another Device",
                description = "Securely transfer your passphrase using QR code or clipboard."
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    var qrButtonPressed by remember { mutableStateOf(false) }
                    var copyButtonPressed by remember { mutableStateOf(false) }

                    FilledTonalButton(
                        onClick = {
                            qrButtonPressed = true
                            onShowQR()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .scale(if (qrButtonPressed) 0.95f else 1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = NoteTheme.PrimaryContainer,
                            contentColor = NoteTheme.OnPrimaryContainer
                        )
                    ) {
                        Icon(
                            Icons.Default.QrCode,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Show QR", fontWeight = FontWeight.Medium)
                    }

                    OutlinedButton(
                        onClick = {
                            copyButtonPressed = true
                            onCopyPassphrase()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .scale(if (copyButtonPressed) 0.95f else 1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = NoteTheme.OnSurface
                        )
                    ) {
                        Icon(
                            Icons.Default.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Copy", fontWeight = FontWeight.Medium)
                    }

                    // Reset button states
                    LaunchedEffect(qrButtonPressed) {
                        if (qrButtonPressed) {
                            delay(150)
                            qrButtonPressed = false
                        }
                    }

                    LaunchedEffect(copyButtonPressed) {
                        if (copyButtonPressed) {
                            delay(150)
                            copyButtonPressed = false
                        }
                    }
                }
            }

            HorizontalDivider(
                thickness = 1.dp,
                color = NoteTheme.Outline.copy(alpha = 0.3f)
            )

            EnhancedActionSection(
                icon = Icons.Default.CloudDownload,
                title = "Import from Another Device",
                description = "Scan QR code or enter passphrase to import notes and reminders."
            ) {
                var scanButtonPressed by remember { mutableStateOf(false) }

                FilledTonalButton(
                    onClick = {
                        scanButtonPressed = true
                        onScanQR()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .scale(if (scanButtonPressed) 0.98f else 1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = NoteTheme.SecondaryContainer,
                        contentColor = NoteTheme.OnSecondaryContainer
                    )
                ) {
                    Icon(
                        Icons.Default.QrCodeScanner,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Scan and Sync", fontWeight = FontWeight.Medium)
                }

                TextButton(
                    onClick = onManualSync,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.textButtonColors(contentColor = NoteTheme.Primary)
                ) {
                    Text("Enter passphrase manually", fontWeight = FontWeight.Medium)
                }

                LaunchedEffect(scanButtonPressed) {
                    if (scanButtonPressed) {
                        delay(150)
                        scanButtonPressed = false
                    }
                }
            }
        }
    }
}

// Enhanced animated info card
@Composable
private fun AnimatedInfoCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = NoteTheme.SurfaceVariant.copy(alpha = 0.7f),
            contentColor = NoteTheme.OnSurface
        ),
//        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    tint = NoteTheme.Primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "How Sync Works",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = NoteTheme.OnSurface
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                EnhancedInfoItem("Each device has a unique passphrase", Icons.Default.Smartphone)
                EnhancedInfoItem("Sync copies data from one device to another", Icons.Default.SyncAlt)
                EnhancedInfoItem("After sync, devices remain independent", Icons.Default.DevicesOther)
                EnhancedInfoItem("Your data is encrypted and secure", Icons.Default.Security)
            }
        }
    }
}

// Enhanced helper composables with better visual hierarchy and animations
@Composable
private fun EnhancedSectionHeader(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String? = null
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Card(
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(
                containerColor = NoteTheme.PrimaryContainer.copy(alpha = 0.5f)
            )
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = NoteTheme.Primary,
                modifier = Modifier
                    .size(24.dp)
                    .padding(4.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = NoteTheme.OnSurface
            )
            subtitle?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = NoteTheme.OnSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun EnhancedStatItem(
    value: String,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Card(
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(
                containerColor = color.copy(alpha = 0.1f)
            )
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier
                    .size(32.dp)
                    .padding(8.dp)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = NoteTheme.OnSurface
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = NoteTheme.OnSurfaceVariant,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun EnhancedActionSection(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Card(
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = NoteTheme.SecondaryContainer.copy(alpha = 0.5f)
                )
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = NoteTheme.Secondary,
                    modifier = Modifier
                        .size(24.dp)
                        .padding(4.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = NoteTheme.OnSurface
                )
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = NoteTheme.OnSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
            }
        }
        content()
    }
}

@Composable
private fun EnhancedInfoItem(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = NoteTheme.Surface.copy(alpha = 0.5f)
        ),
//        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = NoteTheme.Primary,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = NoteTheme.OnSurface,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

// Enhanced QR Code Dialog with better visual presentation
@Composable
fun EnhancedQRCodeDialog(
    bitmap: Bitmap,
    passphrase: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        containerColor = NoteTheme.Surface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Card(
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = NoteTheme.PrimaryContainer.copy(alpha = 0.5f)
                    )
                ) {
                    Icon(
                        Icons.Default.QrCode,
                        contentDescription = null,
                        tint = NoteTheme.Primary,
                        modifier = Modifier
                            .size(28.dp)
                            .padding(6.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "QR Code for Sync",
                    fontWeight = FontWeight.Bold,
                    color = NoteTheme.OnSurface
                )
            }
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                ) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "QR Code",
                        modifier = Modifier
                            .size(220.dp)
                            .padding(16.dp)
                    )
                }

                Text(
                    text = "Scan this QR code on another device to sync your data",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = NoteTheme.OnSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )

                Card(
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = NoteTheme.SurfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Text(
                        text = passphrase,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = NoteTheme.OnSurface.copy(alpha = 0.8f),
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        },
        confirmButton = {
            FilledTonalButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = NoteTheme.PrimaryContainer,
                    contentColor = NoteTheme.OnPrimaryContainer
                )
            ) {
                Text("Close", fontWeight = FontWeight.Medium)
            }
        }
    )
}

// Enhanced Sync Dialog with better UX
@Composable
fun EnhancedSyncFromDeviceDialog(
    inputPassphrase: String,
    onPassphraseChange: (String) -> Unit,
    errorMessage: String,
    isLoading: Boolean,
    onSync: () -> Unit,
    onCancel: () -> Unit
) {
    var showQRScanner by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onCancel,
        shape = RoundedCornerShape(20.dp),
        containerColor = NoteTheme.Surface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Card(
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = NoteTheme.SecondaryContainer.copy(alpha = 0.5f)
                    )
                ) {
                    Icon(
                        Icons.Default.CloudDownload,
                        contentDescription = null,
                        tint = NoteTheme.Secondary,
                        modifier = Modifier
                            .size(28.dp)
                            .padding(6.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Sync from Another Device",
                    fontWeight = FontWeight.Bold,
                    color = NoteTheme.OnSurface
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = "Enter the passphrase from the other device:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = NoteTheme.OnSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )

                OutlinedTextField(
                    value = inputPassphrase,
                    onValueChange = onPassphraseChange,
                    placeholder = {
                        Text(
                            "Enter passphrase",
                            color = NoteTheme.OnSurfaceVariant.copy(alpha = 0.6f)
                        )
                    },
                    isError = errorMessage.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = NoteTheme.SurfaceVariant.copy(alpha = 0.3f),
                        unfocusedContainerColor = NoteTheme.SurfaceVariant.copy(alpha = 0.1f),
                        focusedTextColor = NoteTheme.OnSurface,
                        unfocusedTextColor = NoteTheme.OnSurface,
                        cursorColor = NoteTheme.Primary,
                        focusedBorderColor = NoteTheme.Primary,
                        unfocusedBorderColor = NoteTheme.Outline.copy(alpha = 0.5f)
                    )
                )

                // Enhanced error message display
                AnimatedVisibility(
                    visible = errorMessage.isNotEmpty(),
                    enter = slideInVertically(initialOffsetY = { -it/2 }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { -it/2 }) + fadeOut()
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = NoteTheme.ErrorContainer.copy(alpha = 0.3f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Error,
                                contentDescription = null,
                                tint = NoteTheme.Error,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = errorMessage,
                                color = NoteTheme.Error,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // Enhanced action buttons
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onSync,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isLoading,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NoteTheme.Primary,
                            contentColor = NoteTheme.OnPrimary
                        )
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = NoteTheme.OnPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Syncing...", fontWeight = FontWeight.Medium)
                        } else {
                            Icon(
                                Icons.Default.Sync,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Sync Data", fontWeight = FontWeight.Medium)
                        }
                    }

                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = NoteTheme.OnSurface
                        )
                    ) {
                        Text("Cancel", fontWeight = FontWeight.Medium)
                    }

                    FilledTonalButton(
                        onClick = { showQRScanner = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = NoteTheme.SecondaryContainer,
                            contentColor = NoteTheme.OnSecondaryContainer
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.QrCodeScanner,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Scan QR Code", fontWeight = FontWeight.Medium)
                    }
                }
            }
        },
        confirmButton = { /* Empty to avoid duplicate buttons */ },
        dismissButton = { /* Empty to avoid duplicate buttons */ }
    )

    // Enhanced QR Scanner with better feedback
    if (showQRScanner) {
        val scannerContext = LocalContext.current
        QRScannerSection(
            onQRScanned = { qrContent ->
                val extracted = PassphraseManager.extractPassphraseFromQR(qrContent)
                if (extracted == null) {
                    Toast.makeText(scannerContext, "❌ Invalid QR code format", Toast.LENGTH_SHORT).show()
                    showQRScanner = false
                    return@QRScannerSection
                }
                onPassphraseChange(extracted)
                showQRScanner = false
                onSync()
            },
            onCancel = {
                showQRScanner = false
            }
        )
    }
}
