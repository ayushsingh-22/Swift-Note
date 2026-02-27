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
import com.amvarpvtltd.swiftNote.myGlobalMobileDeviceId
import com.amvarpvtltd.swiftNote.sync.SyncManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import com.amvarpvtltd.swiftNote.components.BackgroundProvider

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

    // Animation states for enhanced UX
    var statsVisible by remember { mutableStateOf(false) }
    var cardsVisible by remember { mutableStateOf(false) }

    // Load current passphrase on screen load
    LaunchedEffect(Unit) {
        currentPassphrase = PassphraseManager.getStoredPassphrase(context) ?: myGlobalMobileDeviceId
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Sync Settings",
                        color = NoteTheme.OnSurface,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                            navController.navigateUp()
                        }
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = NoteTheme.OnSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = NoteTheme.SurfaceVariant
                )
            )
        },
        // make scaffold transparent and use shared background so gradient matches other screens
        containerColor = Color.Transparent
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(brush = BackgroundProvider.getBrush())
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

                    // Enhanced Info Card
                    AnimatedInfoCard()
                }
            }
        }
    }

    // Enhanced QR Code Dialog
    if (showQRCode && qrCodeBitmap != null) {
        EnhancedQRCodeDialog(
            bitmap = qrCodeBitmap!!,
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
}

// Enhanced animated device info card
@Composable
private fun AnimatedDeviceInfoCard(
    currentPassphrase: String,
    syncStats: com.amvarpvtltd.swiftNote.sync.SyncStats?,
    statsVisible: Boolean,
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
                        containerColor = NoteTheme.PrimaryContainer.copy(alpha = 0.3f)
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
                                color = NoteTheme.OnSurface
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
