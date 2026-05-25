package com.amvarpvtltd.swiftNote.design

import android.util.Log
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.amvarpvtltd.swiftNote.auth.DeviceManager
import com.amvarpvtltd.swiftNote.auth.PassphraseManager
import com.amvarpvtltd.swiftNote.cleanup.DataCleanupManager
import com.amvarpvtltd.swiftNote.sync.SyncManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ─────────────────────────────────────────────────────────────────────────────
// Shared restore helper — called by both QR and Manual Passphrase paths so the
// two flows can never drift out of sync.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Unified restore flow:
 *  1. Wipe local notes + reminders + pending-deletions
 *     AND best-effort delete Firebase data from the previous identity.
 *  2. Store new identity (= this device's hardware ID).
 *  3. Sync notes FROM sourcePassphrase INTO the new identity, cleaning the
 *     destination Firebase path first (cleanTargetBeforeUpload = true) to
 *     prevent old data from mixing with restored notes.
 *
 * [onError] is called with a user-visible message if any step fails.
 * [onSuccess] is called when the user should be navigated to "main".
 * Neither callback throws — the caller's finally{} block always runs.
 */
private suspend fun performRestore(
    context: android.content.Context,
    sourcePassphrase: String,
    onError: (String) -> Unit,
    onSuccess: () -> Unit
) {
    // Step 1 — Unified local + remote wipe.
    val cleanupResult = DataCleanupManager.wipeLocalAndPreviousRemoteNotes(context)
    if (cleanupResult.isFailure) {
        Log.e("Onboarding/Restore", "Cleanup failed", cleanupResult.exceptionOrNull())
        onError("Couldn't clear existing data. Please try again.")
        return
    }
    Log.d("Onboarding/Restore", "Cleanup summary: ${cleanupResult.getOrNull()}")

    // Step 2 — Adopt new identity (deviceId becomes the new passphrase).
    val currentPassphrase = DeviceManager.getOrCreateDeviceId(context)
    val storeResult = PassphraseManager.storePassphrase(context, currentPassphrase)
    if (storeResult.isFailure) {
        Log.e("Onboarding/Restore", "storePassphrase failed", storeResult.exceptionOrNull())
        onError("Failed to create new identity: ${storeResult.exceptionOrNull()?.message}")
        return
    }

    // Step 3 — Sync from source; clean destination first to avoid cross-contamination.
    val syncResult = SyncManager.syncDataFromPassphrase(
        context = context,
        sourcePassphrase = sourcePassphrase,
        currentPassphrase = currentPassphrase,
        cleanTargetBeforeUpload = true
    )
    if (syncResult.isFailure) {
        onError("Restore failed: ${syncResult.exceptionOrNull()?.message ?: "Unknown error"}")
        return
    }

    Log.d("Onboarding/Restore", "Restore complete: ${syncResult.getOrNull()}")
    onSuccess()
}

// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showQRScanner by remember { mutableStateOf(false) }
    var showManualDialog by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var inputPassphrase by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }

    // Staggered animation states
    var heroVisible by remember { mutableStateOf(false) }
    var cardsVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        DeviceManager.markFirstLaunchComplete(context)
        delay(100)
        heroVisible = true
        delay(300)
        cardsVisible = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NoteTheme.Background)
    ) {
        // Gradient hero background — drawn first, visible through the transparent hero section
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.52f)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            NoteTheme.Primary,
                            NoteTheme.PrimaryVariant,
                            NoteTheme.Background   // smooth fade into background
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ── Hero Section ────────────────────────────────────────────────
            AnimatedVisibility(
                visible = heroVisible,
                enter = fadeIn(tween(600)) + slideInVertically(
                    spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                    initialOffsetY = { -it / 3 }
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp)
                        .padding(top = 64.dp, bottom = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Floating logo circle
                    Box(
                        modifier = Modifier
                            .size(96.dp)
                            .background(
                                color = Color.White.copy(alpha = 0.15f),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .background(Color.White.copy(alpha = 0.9f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(id = com.amvarpvtltd.swiftNote.R.drawable.logo2),
                                contentDescription = "SwiftNote Logo",
                                modifier = Modifier.size(44.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = "SwiftNote",
                        fontSize = 36.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        letterSpacing = (-1).sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Secure. Private. Always with you.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White.copy(alpha = 0.82f),
                        textAlign = TextAlign.Center,
                        lineHeight = 24.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Feature chips row
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("✦ Offline-first", "✦ Encrypted", "✦ AI Reminders").forEach { label ->
                            Surface(
                                shape = RoundedCornerShape(100.dp),
                                color = Color.White.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = label,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.9f),
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }

            // ── Options Card Sheet ───────────────────────────────────────────
            AnimatedVisibility(
                visible = cardsVisible,
                enter = fadeIn(tween(500)) + slideInVertically(
                    spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                    initialOffsetY = { it / 2 }
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                        .background(NoteTheme.Background)
                        .padding(horizontal = 20.dp)
                        .padding(top = 28.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "How would you like to start?",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = NoteTheme.OnBackground
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // ── Start Fresh Card ─────────────────────────────
                    OnboardingOptionCard(
                        icon = Icons.Default.AutoAwesome,
                        iconTint = NoteTheme.Primary,
                        iconBg = NoteTheme.PrimaryContainer,
                        title = "Start Fresh",
                        subtitle = "Begin with a clean slate. Your notes stay private on this device.",
                        accentColor = NoteTheme.Primary,
                        onClick = {
                            scope.launch {
                                isLoading = true
                                try {
                                    // Unified wipe: local notes/reminders/pending-deletions
                                    // + best-effort Firebase delete of the previous identity.
                                    val cleanupResult =
                                        DataCleanupManager.wipeLocalAndPreviousRemoteNotes(context)
                                    if (cleanupResult.isFailure) {
                                        Toast.makeText(
                                            context,
                                            "Couldn't clear existing data. Please try again.",
                                            Toast.LENGTH_LONG
                                        ).show()
                                        Log.e("Onboarding/StartFresh", "Cleanup failed",
                                            cleanupResult.exceptionOrNull())
                                        return@launch
                                    }
                                    Log.d("Onboarding/StartFresh",
                                        "Cleanup summary: ${cleanupResult.getOrNull()}")

                                    // Fresh UUID passphrase → brand-new empty Firebase path.
                                    val freshPassphrase = java.util.UUID.randomUUID().toString()
                                    PassphraseManager.storePassphrase(context, freshPassphrase).getOrThrow()

                                    navController.navigate("main") {
                                        popUpTo("onboarding") { inclusive = true }
                                    }
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Setup failed: ${e.message}", Toast.LENGTH_LONG).show()
                                    Log.e("Onboarding/StartFresh", "Unexpected error", e)
                                } finally {
                                    isLoading = false
                                }
                            }
                        }
                    )

                    // ── Scan QR Card ──────────────────────────────────
                    OnboardingOptionCard(
                        icon = Icons.Default.QrCodeScanner,
                        iconTint = Color(0xFF8B5CF6),
                        iconBg = Color(0xFFF3E8FF),
                        title = "Restore via QR Code",
                        subtitle = "Scan a QR from another device to import your notes securely.",
                        accentColor = Color(0xFF8B5CF6),
                        onClick = { showQRScanner = true }
                    )

                    // ── Manual Passphrase Card ────────────────────────
                    OutlinedCard(
                        onClick = { showManualDialog = true },
                        shape = RoundedCornerShape(16.dp),
                        border = CardDefaults.outlinedCardBorder().copy(
                            width = 1.dp,
                            brush = androidx.compose.ui.graphics.SolidColor(
                                NoteTheme.Outline
                            )
                        ),
                        colors = CardDefaults.outlinedCardColors(
                            containerColor = NoteTheme.Surface
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Key,
                                contentDescription = null,
                                tint = NoteTheme.Primary,
                                modifier = Modifier.size(22.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Enter Passphrase Manually",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = NoteTheme.Primary
                                )
                                Text(
                                    text = "Already have a passphrase? Enter it directly.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = NoteTheme.OnSurfaceVariant
                                )
                            }
                            Icon(
                                Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = NoteTheme.Primary.copy(alpha = 0.5f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // Loading feedback
                    if (isLoading) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = NoteTheme.PrimaryContainer
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = NoteTheme.Primary,
                                    strokeWidth = 2.5.dp
                                )
                                Text(
                                    text = "Setting up your workspace…",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = NoteTheme.OnPrimaryContainer,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showQRScanner) {
        QRScannerSection(
            onQRScanned = { qrContent ->
                scope.launch {
                    isLoading = true
                    try {
                        val sourcePassphrase = PassphraseManager.extractPassphraseFromQR(qrContent)
                        if (sourcePassphrase == null) {
                            Toast.makeText(context, "Invalid QR code format", Toast.LENGTH_SHORT).show()
                            return@launch
                        }
                        performRestore(
                            context = context,
                            sourcePassphrase = sourcePassphrase,
                            onError = { msg ->
                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                            },
                            onSuccess = {
                                navController.navigate("main") {
                                    popUpTo("onboarding") { inclusive = true }
                                }
                            }
                        )
                    } finally {
                        isLoading = false
                        showQRScanner = false
                    }
                }
            },
            onCancel = { showQRScanner = false }
        )
    }

    if (showManualDialog) {
        EnhancedSyncFromDeviceDialog(
            inputPassphrase = inputPassphrase,
            onPassphraseChange = { inputPassphrase = it },
            errorMessage = errorMessage,
            isLoading = isLoading,
            onSync = {
                scope.launch {
                    if (inputPassphrase.isBlank()) { errorMessage = "Please enter a passphrase"; return@launch }
                    isLoading = true; errorMessage = ""
                    try {
                        performRestore(
                            context = context,
                            sourcePassphrase = inputPassphrase.trim(),
                            onError = { msg ->
                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                            },
                            onSuccess = {
                                navController.navigate("main") {
                                    popUpTo("onboarding") { inclusive = true }
                                }
                            }
                        )
                    } finally {
                        isLoading = false; showManualDialog = false
                        inputPassphrase = ""; errorMessage = ""
                    }
                }
            },
            onCancel = { showManualDialog = false; inputPassphrase = ""; errorMessage = "" }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OnboardingOptionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    iconBg: Color,
    title: String,
    subtitle: String,
    accentColor: Color,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = NoteTheme.Surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        0f to accentColor.copy(alpha = 0.06f),
                        1f to Color.Transparent
                    )
                )
                .padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon container
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .background(iconBg, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(26.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = NoteTheme.OnSurface
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = NoteTheme.OnSurfaceVariant,
                    lineHeight = 18.sp
                )
            }

            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = accentColor.copy(alpha = 0.6f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}