package com.amvarpvtltd.swiftNote.design

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.amvarpvtltd.swiftNote.auth.DeviceManager
import com.amvarpvtltd.swiftNote.auth.PassphraseManager
import com.amvarpvtltd.swiftNote.sync.SyncManager
import kotlinx.coroutines.launch
import com.amvarpvtltd.swiftNote.components.BackgroundProvider
import androidx.compose.ui.res.painterResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    navController: NavController
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showQRScanner by remember { mutableStateOf(false) }
    var showManualDialog by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var inputPassphrase by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        // Mark first launch as complete
        DeviceManager.markFirstLaunchComplete(context)
    }

    // Background gradient consistent with other screens (shared random palette)
    val backgroundBrush = BackgroundProvider.getBrush()

    Scaffold { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(brush = backgroundBrush)
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Welcome Header with better spacing and visual hierarchy
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(bottom = 40.dp)
                ) {
                    // App icon/logo placeholder
                    Card(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(RoundedCornerShape(20.dp)),
                        colors = CardDefaults.cardColors(
                            containerColor = NoteTheme.Primary.copy(alpha = 0.1f)
                        ),
//
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(id = com.amvarpvtltd.swiftNote.R.drawable.logo2),
                                contentDescription = "Onboarding Image",
                                modifier = Modifier.size(48.dp)
//                                colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(NoteTheme.Primary),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = "Welcome to SwiftNote",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = NoteTheme.OnBackground,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Your secure, private note-taking companion.\nChoose how you'd like to get started:",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        color = NoteTheme.OnSurface.copy(alpha = 0.8f),
                        lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * 1.2
                    )
                }

                // Options container with better spacing
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Start Fresh card - enhanced design
                    ElevatedCard(
                        onClick = {
                            scope.launch {
                                isLoading = true
                                try {
                                    val deviceId = DeviceManager.getOrCreateDeviceId(context)
                                    PassphraseManager.storePassphrase(context, deviceId).getOrThrow()

                                    try {
                                        val syncResult = SyncManager.syncDataFromPassphrase(context, deviceId, deviceId)
                                        if (syncResult.isSuccess) {
                                            android.util.Log.d("OnboardingScreen", "Imported remote notes for device: $deviceId")
                                        } else {
                                            android.util.Log.w("OnboardingScreen", "No remote notes or import failed for device: $deviceId - ${syncResult.exceptionOrNull()?.message}")
                                        }
                                    } catch (e: Exception) {
                                        android.util.Log.w("OnboardingScreen", "Remote import check failed for device: $deviceId", e)
                                    }

                                    navController.navigate("main") {
                                        popUpTo("onboarding") { inclusive = true }
                                    }
                                } catch (e: Exception) {
                                    Toast.makeText(
                                        context,
                                        "Setup failed: ${e.message}",
                                        Toast.LENGTH_LONG
                                    ).show()
                                } finally {
                                    isLoading = false
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp)),
                        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp),
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = NoteTheme.Surface
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalArrangement = Arrangement.spacedBy(20.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Enhanced icon container
                            Card(
                                modifier = Modifier.size(56.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = NoteTheme.Primary.copy(alpha = 0.15f)
                                )
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = null,
                                        modifier = Modifier.size(28.dp),
                                        tint = NoteTheme.Primary
                                    )
                                }
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Start Fresh",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = NoteTheme.OnSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Begin with a clean slate and create your first notes",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = NoteTheme.OnSurface.copy(alpha = 0.7f),
                                    lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.3
                                )
                            }

                            // Arrow indicator
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = NoteTheme.OnSurface.copy(alpha = 0.4f)
                            )
                        }
                    }

                    // Restore Existing (QR) - enhanced design
                    ElevatedCard(
                        onClick = { showQRScanner = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp)),
                        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp),
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = NoteTheme.Surface
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalArrangement = Arrangement.spacedBy(20.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Enhanced icon container
                            Card(
                                modifier = Modifier.size(56.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = NoteTheme.Secondary.copy(alpha = 0.15f)
                                )
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.QrCodeScanner,
                                        contentDescription = null,
                                        modifier = Modifier.size(28.dp),
                                        tint = NoteTheme.Secondary
                                    )
                                }
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Scan QR Code",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = NoteTheme.OnSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Restore your notes by scanning a QR code from another device",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = NoteTheme.OnSurface.copy(alpha = 0.7f),
                                    lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.3
                                )
                            }

                            // Arrow indicator
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = NoteTheme.OnSurface.copy(alpha = 0.4f)
                            )
                        }
                    }

                    // Manual entry option - redesigned as a subtle button
                    OutlinedCard(
                        onClick = { showManualDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp)),
                        border = CardDefaults.outlinedCardBorder().copy(
                            width = 1.dp,
                            brush = androidx.compose.ui.graphics.SolidColor(NoteTheme.Primary.copy(alpha = 0.3f))
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Key,
                                contentDescription = null,
                                tint = NoteTheme.Primary,
                                modifier = Modifier.size(24.dp)
                            )

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Enter Passphrase Manually",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = NoteTheme.Primary
                                )
                                Text(
                                    text = "Have a passphrase? Enter it directly",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = NoteTheme.OnSurface.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }
                }

                // Loading indicator with better positioning and styling
                if (isLoading) {
                    Spacer(modifier = Modifier.height(32.dp))
                    Card(
                        modifier = Modifier.padding(16.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = NoteTheme.Surface.copy(alpha = 0.9f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(
                                color = NoteTheme.Primary,
                                strokeWidth = 3.dp
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Setting up your notes...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = NoteTheme.OnSurface.copy(alpha = 0.7f)
                            )
                        }
                    }
                }

                // Bottom spacing
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    // QR Scanner for restore (unchanged functionality)
    if (showQRScanner) {
        QRScannerSection(
            onQRScanned = { qrContent ->
                scope.launch {
                    isLoading = true
                    try {
                        val sourcePassphrase = PassphraseManager.extractPassphraseFromQR(qrContent)
                        if (sourcePassphrase == null) {
                            Toast.makeText(
                                context,
                                "Invalid QR code format",
                                Toast.LENGTH_SHORT
                            ).show()
                            return@launch
                        }

                        val currentPassphrase = DeviceManager.getOrCreateDeviceId(context)
                        PassphraseManager.storePassphrase(context, currentPassphrase).getOrThrow()

                        val result = SyncManager.syncDataFromPassphrase(
                            context,
                            sourcePassphrase,
                            currentPassphrase
                        )
                        if (result.isFailure) {
                            Toast.makeText(
                                context,
                                "Restore failed: ${result.exceptionOrNull()?.message ?: "Unknown error"}",
                                Toast.LENGTH_LONG
                            ).show()
                        }

                        navController.navigate("main") {
                            popUpTo("onboarding") { inclusive = true }
                        }
                    } catch (e: Exception) {
                        Toast.makeText(
                            context,
                            "Restore failed: ${e.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    } finally {
                        isLoading = false
                        showQRScanner = false
                    }
                }
            },
            onCancel = { showQRScanner = false }
        )
    }

    // Manual passphrase dialog using shared EnhancedSyncFromDeviceDialog for consistent UI (unchanged functionality)
    if (showManualDialog) {
        EnhancedSyncFromDeviceDialog(
            inputPassphrase = inputPassphrase,
            onPassphraseChange = { inputPassphrase = it },
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
                        val sourcePassphrase = inputPassphrase

                        val currentPassphrase = DeviceManager.getOrCreateDeviceId(context)
                        PassphraseManager.storePassphrase(context, currentPassphrase).getOrThrow()

                        val result = SyncManager.syncDataFromPassphrase(
                            context,
                            sourcePassphrase,
                            currentPassphrase
                        )
                        if (result.isFailure) {
                            Toast.makeText(
                                context,
                                "Restore failed: ${result.exceptionOrNull()?.message ?: "Unknown error"}",
                                Toast.LENGTH_LONG
                            ).show()
                        }

                        navController.navigate("main") {
                            popUpTo("onboarding") { inclusive = true }
                        }
                    } catch (e: Exception) {
                        Toast.makeText(
                            context,
                            "Restore failed: ${e.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    } finally {
                        isLoading = false
                        showManualDialog = false
                        inputPassphrase = ""
                        errorMessage = ""
                    }
                }
            },
            onCancel = {
                showManualDialog = false
                inputPassphrase = ""
                errorMessage = ""
            }
        )
    }
}