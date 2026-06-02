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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.amvarpvtltd.swiftNote.auth.DeviceManager
import com.amvarpvtltd.swiftNote.utils.rememberResponsiveDimensions
import com.amvarpvtltd.swiftNote.auth.PassphraseManager
import com.amvarpvtltd.swiftNote.auth.SyncMode
import com.amvarpvtltd.swiftNote.cleanup.DataCleanupManager
import com.amvarpvtltd.swiftNote.components.SyncModeDialog
import com.amvarpvtltd.swiftNote.sync.SyncManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ─────────────────────────────────────────────────────────────────────────────
// Restore helpers — split by sync mode so Phase 3's dialog can call each path
// independently. Both helpers share the same contract:
//   [onError]   → called with a user-visible message if any step fails (no throw)
//   [onSuccess] → called when navigation to "main" should happen
//   The caller's finally{} block always runs regardless.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Continuous Sync restore (QR + Manual legacy path, and what the user picks after Phase 3).
 *
 * Adopts [sourcePassphrase] as the local identity so both devices share the same
 * users/{sourcePassphrase}/ Firebase account root. Future reads/writes on both
 * devices go through that shared account — true multi-device sync.
 *
 * ⚠️  No local wipe is performed — this is intentional. Restore is additive.
 *      Start Fresh is the only flow that wipes.
 */
private suspend fun performContinuousRestore(
    context: android.content.Context,
    sourcePassphrase: String,
    onError: (String) -> Unit,
    onSuccess: () -> Unit
) {
    // Step 0 — Safety net: confirm the source account exists BEFORE adopting its
    // passphrase. storePassphrase() writes to Firebase (updateChildren), which would
    // silently CREATE a new node for a non-existent account — this is the reported bug.
    // Primary validation happens in onSync/onQRScanned (better UX); this guard prevents
    // the bug even if the UI check is bypassed or a race condition occurs.
    val verifyResult = PassphraseManager.verifyPassphrase(sourcePassphrase)
    if (verifyResult.isFailure) {
        val errMsg = verifyResult.exceptionOrNull()?.message ?: "Network error"
        Log.e("Onboarding/Continuous", "verifyPassphrase failed: $errMsg")
        onError("Could not verify account: $errMsg")
        return
    }
    if (verifyResult.getOrDefault(false) == false) {
        // SECURITY: Log.w survives release builds — do NOT log passphrase length (fingerprints user).
        Log.w("Onboarding/Continuous", "Account not found for passphrase")
        onError("Account not found. Please double-check the passphrase and try again.")
        return
    }

    // Step 1 — Adopt the source passphrase as the local identity.
    val storeResult = PassphraseManager.storePassphrase(context, sourcePassphrase)
    if (storeResult.isFailure) {
        Log.e("Onboarding/Continuous", "storePassphrase failed", storeResult.exceptionOrNull())
        onError("Failed to connect to account: ${storeResult.exceptionOrNull()?.message}")
        return
    }
    // Mark device as CONTINUOUS so SyncSettingsScreen can show the Disconnect card.
    PassphraseManager.setSyncMode(context, SyncMode.CONTINUOUS)

    // Step 2 — Merge all sub-device folders from users/{sourcePassphrase}/notes/ into
    // local Room. Timestamp-based conflict resolution: local wins if newer.
    // After merging, this device's full note set is uploaded back to
    // users/{sourcePassphrase}/notes/{thisDeviceId}/.
    val syncResult = SyncManager.syncDataFromPassphrase(
        context = context,
        sourcePassphrase = sourcePassphrase,
        currentPassphrase = sourcePassphrase   // same account — multi-device sharing
    )
    if (syncResult.isFailure) {
        onError("Sync failed: ${syncResult.exceptionOrNull()?.message ?: "Unknown error"}")
        return
    }

    Log.d("Onboarding/Continuous", "Continuous restore complete: ${syncResult.getOrNull()}")
    onSuccess()
}

/**
 * One-Time Sync restore — imports a snapshot of the source account then severs the link.
 *
 * This device KEEPS its own identity (deviceId). The source device is never
 * affected after this call — future edits on either device do NOT propagate.
 *
 * Steps:
 *  1. Ensure this device's passphrase is set to its own deviceId (not the source).
 *  2. Pull notes from all sub-device folders under users/{sourcePassphrase}/notes/.
 *  3. Merge them into local Room (additive — existing local notes are preserved).
 *  4. Re-upload the merged set to users/{deviceId}/notes/{deviceId}/
 *     (the device's own account, not the source account).
 *
 * ⚠️  No local wipe is performed — the import is strictly additive.
 */
private suspend fun performOneTimeRestore(
    context: android.content.Context,
    sourcePassphrase: String,
    onError: (String) -> Unit,
    onSuccess: () -> Unit
) {
    val deviceId = DeviceManager.getOrCreateDeviceId(context)

    // Step 1 — Ensure the local identity is set to the device's OWN id, NOT the source.
    // (If the user was previously in Continuous mode this overwrites back to deviceId.)
    val storeResult = PassphraseManager.storePassphrase(context, deviceId)
    if (storeResult.isFailure) {
        Log.e("Onboarding/OneTime", "storePassphrase failed", storeResult.exceptionOrNull())
        onError("Failed to set up local account: ${storeResult.exceptionOrNull()?.message}")
        return
    }
    // Mark as ONE_TIME_IMPORTED — no shared account, no Disconnect card needed.
    PassphraseManager.setSyncMode(context, SyncMode.ONE_TIME_IMPORTED)

    // Step 2 & 3 — Pull from source, keep own identity as the upload target.
    //   source != current → SyncManager reads all sub-folders under
    //                        users/{sourcePassphrase}/notes/ and uploads merged notes
    //                        to users/{deviceId}/notes/{deviceId}/
    //   cleanTargetBeforeUpload = false → additive merge (existing local notes are kept)
    //
    // Decryption still works: SyncManager.tryDecryptWithCandidates includes sourcePassphrase
    // in its candidate list, so source-encrypted notes decrypt successfully even though
    // currentPassphrase is now deviceId.
    val syncResult = SyncManager.syncDataFromPassphrase(
        context = context,
        sourcePassphrase = sourcePassphrase,
        currentPassphrase = deviceId,
        cleanTargetBeforeUpload = false
    )
    if (syncResult.isFailure) {
        onError("Import failed: ${syncResult.exceptionOrNull()?.message ?: "Unknown error"}")
        return
    }

    Log.d("Onboarding/OneTime", "One-time import complete: ${syncResult.getOrNull()}")
    onSuccess()
}

// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun OnboardingScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showQRScanner by remember { mutableStateOf(false) }
    var showManualDialog by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var inputPassphrase by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }
    // Non-null when a passphrase has been captured (QR or manual) and the user
    // needs to choose a sync mode before the actual sync begins.
    var pendingSyncSourcePassphrase by remember { mutableStateOf<String?>(null) }

    val dims = rememberResponsiveDimensions()

    // Derived responsive sizes — every hardcoded dp value in the hero / options
    // sheet has been replaced with one of these so a 384x854 phone, a 412x912
    // phone, and a 720dp tablet all render consistent proportions. Compact-width
    // devices (<360 dp) get the same scaling treatment as the empty-state card.
    val heroLogoOuter = dims.iconLarge * 4              // 80 /  96 / 112
    val heroLogoInner = dims.iconLarge * 3              // 60 /  72 /  84
    val heroLogoImage = dims.iconLarge * 1.8f           // 36 /  43 /  50
    val heroHorizontalPadding = dims.paddingXL          // 24 /  32 /  40
    val heroTopPadding = dims.paddingXL * 2             // 48 /  64 /  80
    val heroBottomPadding = dims.paddingXL              // 24 /  32 /  40
    val optionsHorizontalPadding = dims.paddingLarge    // 18 /  24 /  32
    val optionCardCornerLarge = dims.cornerXL           // 20 /  24 /  28
    val sheetTopCorner = dims.cornerXL + 8.dp           // 28 /  32 /  36

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
                .heightIn(max = 360.dp)
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
                        .padding(horizontal = heroHorizontalPadding)
                        .padding(top = heroTopPadding, bottom = heroBottomPadding),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Floating logo circle
                    Box(
                        modifier = Modifier
                            .size(heroLogoOuter)
                            .background(
                                color = Color.White.copy(alpha = 0.15f),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(heroLogoInner)
                                .background(Color.White.copy(alpha = 0.9f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(id = com.amvarpvtltd.swiftNote.R.drawable.logo2),
                                contentDescription = "SwiftNote Logo",
                                modifier = Modifier.size(heroLogoImage)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(dims.paddingLarge))

                    Text(
                        text = "SwiftNote",
                        fontSize = dims.scaledSp(36),
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        letterSpacing = (-1).sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(dims.paddingSmall))

                    Text(
                        text = "Secure. Private. Always with you.",
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = dims.scaledSp(15)
                        ),
                        color = Color.White.copy(alpha = 0.82f),
                        textAlign = TextAlign.Center,
                        lineHeight = dims.scaledSp(22)
                    )

                    Spacer(modifier = Modifier.height(dims.paddingMedium))

                    // Feature chips row — uses FlowRow so chips wrap onto a new
                    // line on narrow screens instead of getting their text
                    // squeezed onto 2–3 lines (the original bug on 384dp where
                    // "AI Reminders" rendered as "AI / Remin / ders").
                    androidx.compose.foundation.layout.FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(
                            dims.paddingSmall,
                            Alignment.CenterHorizontally
                        ),
                        verticalArrangement = Arrangement.spacedBy(dims.paddingSmall)
                    ) {
                        listOf("✦ Offline-first", "✦ Encrypted", "✦ AI Reminders").forEach { label ->
                            Surface(
                                shape = RoundedCornerShape(100.dp),
                                color = Color.White.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = label,
                                    modifier = Modifier.padding(
                                        horizontal = dims.paddingMedium,
                                        vertical = dims.paddingSmall / 2
                                    ),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = dims.scaledSp(11)
                                    ),
                                    color = Color.White.copy(alpha = 0.9f),
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Visible
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
                        .clip(RoundedCornerShape(topStart = sheetTopCorner, topEnd = sheetTopCorner))
                        .background(NoteTheme.Background)
                        .padding(horizontal = optionsHorizontalPadding)
                        .padding(top = dims.paddingLarge + 4.dp, bottom = dims.paddingXL),
                    verticalArrangement = Arrangement.spacedBy(dims.paddingMedium)
                ) {
                    Text(
                        text = "How would you like to start?",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontSize = dims.scaledSp(22)
                        ),
                        fontWeight = FontWeight.Bold,
                        color = NoteTheme.OnBackground
                    )

                    Spacer(modifier = Modifier.height(dims.paddingSmall / 2))

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
                                    // Step 1 — Unified wipe: local notes/reminders/pending-deletions
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

                                    // Step 2 — Resolve the incoming identity (device ID).
                                    // Use the stable device ID as the passphrase so that
                                    // after reinstall the navbar.kt recovery probe at
                                    // users/{deviceId}/ can auto-restore the user's notes.
                                    // UUID.randomUUID() was the old approach — it created an
                                    // unrecoverable orphan account on every reinstall.
                                    val freshPassphrase = DeviceManager.getOrCreateDeviceId(context)

                                    // Step 3 — Force-delete the entire users/{deviceId} Firebase
                                    // node and WAIT for it to complete before navigating.
                                    //
                                    // WHY: wipeLocalAndPreviousRemoteNotes caps its remote wipe at
                                    // 3 s (best-effort). On a slow connection the delete may not
                                    // have finished when NotesScreen loads and calls fetchNotes()
                                    // → syncFromCloudInBackground() reads from Firebase and
                                    // re-fetches the old notes back into local Room storage —
                                    // defeating the whole wipe.  Waiting here (up to 10 s) for the
                                    // full node removal guarantees Firebase is clean before the
                                    // main screen starts its background sync.
                                    val firebaseWipeResult =
                                        DataCleanupManager.forceWipeFirebaseAccount(freshPassphrase)
                                    if (firebaseWipeResult.isFailure) {
                                        Log.w(
                                            "Onboarding/StartFresh",
                                            "Firebase wipe incomplete — proceeding anyway: " +
                                                "${firebaseWipeResult.exceptionOrNull()?.message}"
                                        )
                                        // Non-fatal: we still navigate. Local notes are already
                                        // wiped; any remaining remote notes will eventually be
                                        // overwritten when the user adds new content.
                                    }

                                    // Step 4 — Adopt the fresh identity and navigate.
                                    PassphraseManager.storePassphrase(context, freshPassphrase)
                                        .getOrThrow()
                                    // This device has no shared account — mark it as local-only.
                                    PassphraseManager.setSyncMode(context, SyncMode.LOCAL_ONLY)

                                    // Belt-and-suspenders: even if forceWipeFirebaseAccount above
                                    // didn't fully complete (e.g. no network), tell NoteRepository
                                    // to skip its cloud-download pass on the very first fetchNotes()
                                    // call so it never pulls stale remote notes into local storage.
                                    com.amvarpvtltd.swiftNote.repository.NoteRepository
                                        .markSkipNextCloudPull(context)

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
                        iconTint = NoteTheme.Tertiary,
                        iconBg = NoteTheme.TertiaryContainer,
                        title = "Restore via QR Code",
                        subtitle = "Scan a QR from another device to import your notes securely.",
                        accentColor = NoteTheme.Tertiary,
                        onClick = { showQRScanner = true }
                    )

                    // ── Manual Passphrase Card ────────────────────────
                    OutlinedCard(
                        onClick = { showManualDialog = true },
                        shape = RoundedCornerShape(optionCardCornerLarge - 4.dp),
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
                                .padding(horizontal = dims.paddingLarge, vertical = dims.paddingMedium),
                            horizontalArrangement = Arrangement.spacedBy(dims.paddingMedium),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Key,
                                contentDescription = null,
                                tint = NoteTheme.Primary,
                                modifier = Modifier.size(dims.iconMedium)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Enter Passphrase Manually",
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontSize = dims.scaledSp(14)
                                    ),
                                    fontWeight = FontWeight.SemiBold,
                                    color = NoteTheme.Primary
                                )
                                Text(
                                    text = "Already have a passphrase? Enter it directly.",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = dims.scaledSp(12)
                                    ),
                                    color = NoteTheme.OnSurfaceVariant
                                )
                            }
                            Icon(
                                Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = NoteTheme.Primary.copy(alpha = 0.5f),
                                modifier = Modifier.size(dims.iconSmall)
                            )
                        }
                    }

                    // Loading feedback
                    if (isLoading) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(dims.cornerLarge),
                            colors = CardDefaults.cardColors(
                                containerColor = NoteTheme.PrimaryContainer
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(dims.paddingMedium),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(dims.paddingMedium)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(dims.iconMedium),
                                    color = NoteTheme.Primary,
                                    strokeWidth = 2.5.dp
                                )
                                Text(
                                    text = "Setting up your workspace…",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontSize = dims.scaledSp(14)
                                    ),
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
                    val sourcePassphrase = PassphraseManager.extractPassphraseFromQR(qrContent)
                    if (sourcePassphrase == null) {
                        Toast.makeText(context, "Invalid QR code format", Toast.LENGTH_SHORT).show()
                        return@launch
                    }
                    // Validate the account exists before closing the scanner.
                    isLoading = true
                    try {
                        val verifyResult = PassphraseManager.verifyPassphrase(sourcePassphrase)
                        when {
                            verifyResult.isFailure -> {
                                showQRScanner = false
                                Toast.makeText(
                                    context,
                                    "Could not verify QR account. Check your connection and try again.",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                            verifyResult.getOrDefault(false) == false -> {
                                showQRScanner = false
                                Toast.makeText(
                                    context,
                                    "QR code account not found. Please scan a valid SwiftNote QR code.",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                            else -> {
                                // ✅ Valid account — proceed to mode selection
                                showQRScanner = false
                                pendingSyncSourcePassphrase = sourcePassphrase
                            }
                        }
                    } finally {
                        isLoading = false
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
                    if (inputPassphrase.isBlank()) {
                        errorMessage = "Please enter a passphrase"
                        return@launch
                    }
                    val trimmed = inputPassphrase.trim()
                    isLoading = true
                    errorMessage = ""
                    try {
                        // Validate the account exists BEFORE closing the dialog.
                        // Error renders inside the dialog (errorMessage field) so
                        // the user can correct the passphrase without starting over.
                        val verifyResult = PassphraseManager.verifyPassphrase(trimmed)
                        when {
                            verifyResult.isFailure -> {
                                errorMessage = "Connection error: ${verifyResult.exceptionOrNull()?.message ?: "Could not reach server"}"
                            }
                            verifyResult.getOrDefault(false) == false -> {
                                errorMessage = "Account not found. Please check the passphrase."
                            }
                            else -> {
                                // ✅ Valid — dismiss dialog and show SyncModeDialog
                                showManualDialog = false
                                inputPassphrase = ""
                                errorMessage = ""
                                pendingSyncSourcePassphrase = trimmed
                            }
                        }
                    } finally {
                        isLoading = false
                    }
                }
            },
            onCancel = { showManualDialog = false; inputPassphrase = ""; errorMessage = "" }
        )
    }

    // ── Sync Mode Dialog ─────────────────────────────────────────────────────
    // Shown after the user provides a passphrase via QR or manual entry.
    // Intercepts the flow so the user can choose One-Time vs Continuous before
    // any network call is made.
    //
    // Use a local val snapshot so Kotlin can smart-cast the nullable String
    // inside the if-block (Compose 'var' delegates aren't smart-castable directly).
    val pendingPassphrase = pendingSyncSourcePassphrase
    if (pendingPassphrase != null) {
        SyncModeDialog(
            sourcePassphrasePreview = pendingPassphrase.take(8) + "…",
            isLoading = isLoading,
            onOneTimePicked = {
                scope.launch {
                    isLoading = true
                    try {
                        performOneTimeRestore(
                            context = context,
                            sourcePassphrase = pendingPassphrase,
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
                        pendingSyncSourcePassphrase = null
                    }
                }
            },
            onContinuousPicked = {
                scope.launch {
                    isLoading = true
                    try {
                        performContinuousRestore(
                            context = context,
                            sourcePassphrase = pendingPassphrase,
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
                        pendingSyncSourcePassphrase = null
                    }
                }
            },
            onCancel = { pendingSyncSourcePassphrase = null }
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
    val dims = rememberResponsiveDimensions()
    // Derived sizes — keep the option card visually consistent across width
    // buckets. The icon container scales with iconLarge so a Compact phone
    // doesn't waste 52dp on a single icon while crushing the title/subtitle
    // to the right of it.
    val iconContainer = dims.iconLarge * 2.2f           // 44 /  52 /  62
    val iconInside = dims.iconLarge * 1.1f              // 22 /  26 /  31
    val cardCorner = dims.cornerXL                      // 20 /  24 /  28
    val iconCorner = dims.cornerMedium - 2.dp           // 12 /  14 /  16

    Card(
        onClick = onClick,
        shape = RoundedCornerShape(cardCorner),
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
                .padding(dims.paddingLarge),
            horizontalArrangement = Arrangement.spacedBy(dims.paddingMedium),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon container
            Box(
                modifier = Modifier
                    .size(iconContainer)
                    .background(iconBg, RoundedCornerShape(iconCorner)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(iconInside)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = dims.scaledSp(16)
                    ),
                    fontWeight = FontWeight.Bold,
                    color = NoteTheme.OnSurface
                )
                Spacer(modifier = Modifier.height(dims.paddingSmall / 2))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = dims.scaledSp(12)
                    ),
                    color = NoteTheme.OnSurfaceVariant,
                    lineHeight = dims.scaledSp(17)
                )
            }

            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = accentColor.copy(alpha = 0.6f),
                modifier = Modifier.size(dims.iconSmall + 2.dp)
            )
        }
    }
}