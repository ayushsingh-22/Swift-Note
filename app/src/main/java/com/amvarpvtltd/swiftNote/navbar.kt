package com.amvarpvtltd.swiftNote

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.amvarpvtltd.swiftNote.auth.DeviceManager
import com.amvarpvtltd.swiftNote.design.AddScreen
import com.amvarpvtltd.swiftNote.design.NoteTheme
import com.amvarpvtltd.swiftNote.design.NotesScreen
import com.amvarpvtltd.swiftNote.design.TodayScreen
import com.amvarpvtltd.swiftNote.design.ViewNoteScreen
import com.amvarpvtltd.swiftNote.offline.OfflineNoteManager
import com.amvarpvtltd.swiftNote.share.SharedNoteData
import com.amvarpvtltd.swiftNote.sync.SyncManager
import com.amvarpvtltd.swiftNote.auth.SyncMode
import com.amvarpvtltd.swiftNote.theme.AppThemeState
import com.amvarpvtltd.swiftNote.theme.ProvideNoteTheme
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import androidx.core.content.edit

@Composable
fun MyApp(modifier: Modifier = Modifier) {
    val navController = rememberNavController()

    // Capture context once in composable scope
    val context = LocalContext.current

    // Initialize AppThemeState from SharedPrefs once, then observe reactively.
    // AppThemeState is a global StateFlow — any screen that calls AppThemeState.setTheme()
    // will immediately trigger recomposition here and update ProvideNoteTheme.
    LaunchedEffect(Unit) {
        AppThemeState.initialize(context)
    }
    val currentTheme by AppThemeState.themeMode.collectAsState()

    // Initialize managers using captured context
    val offlineManager = remember(context) { OfflineNoteManager(context) }

    // State to track initialization
    var isInitializing by remember { mutableStateOf(true) }
    var startDestination by remember { mutableStateOf("onboarding") }

    // Observe the noteId from notification to navigate directly to a specific note
    // Use a state variable to track the noteId from notification
    val noteIdToOpen = remember { mutableStateOf<String?>(null) }

    // Effect to observe the LiveData and update our local state
    DisposableEffect(Unit) {
        val observer = androidx.lifecycle.Observer<String?> { newNoteId ->
            noteIdToOpen.value = newNoteId
        }

        MainActivity.noteIdToOpen.observeForever(observer)

        onDispose {
            MainActivity.noteIdToOpen.removeObserver(observer)
        }
    }

    // Navigate to the specific note when noteId changes
    LaunchedEffect(noteIdToOpen.value) {
        val noteId = noteIdToOpen.value
        if (!noteId.isNullOrEmpty() && !isInitializing) {
            Log.d("MyApp", "🧭 Navigating to note from notification: $noteId")
            // Navigate to the ViewNoteScreen with the specified noteId
            navController.navigate("viewnote/$noteId") {
                // Pop up to main screen to avoid stack build-up
                popUpTo("main") { inclusive = false }
            }
            // Reset the noteId after navigation
            noteIdToOpen.value = null
            MainActivity.noteIdToOpen.value = null
        }
    }

    // Phase 5: Handle pending actions from share/widget
    val pendingAction = remember { mutableStateOf<MainActivity.QuickAction?>(null) }

    DisposableEffect(Unit) {
        val observer = androidx.lifecycle.Observer<MainActivity.QuickAction?> { action ->
            pendingAction.value = action
        }
        MainActivity.pendingAction.observeForever(observer)
        onDispose {
            MainActivity.pendingAction.removeObserver(observer)
        }
    }

    LaunchedEffect(pendingAction.value) {
        val action = pendingAction.value
        if (action != null && !isInitializing) {
            when (action) {
                is MainActivity.QuickAction.QuickSave -> {
                    // Save the note directly via repository
                    Log.d("MyApp", "💾 Quick saving note from share")
                    try {
                        val repo = com.amvarpvtltd.swiftNote.repository.NoteRepository.getInstance(context)
                        repo.saveNote(
                            title = action.title,
                            description = action.description,
                            context = context
                        )
                        android.widget.Toast.makeText(context, "Note saved!", android.widget.Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Log.e("MyApp", "Failed to quick save", e)
                        android.widget.Toast.makeText(context, "Failed to save note", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
                is MainActivity.QuickAction.OpenEditor -> {
                    navController.navigate("addscreen") {
                        popUpTo("main") { inclusive = false }
                    }
                    // The shared content will be passed via SharedNoteData
                    SharedNoteData.title = action.title
                    SharedNoteData.description = action.description
                }
                is MainActivity.QuickAction.CreateNote -> {
                    navController.navigate("addscreen") {
                        popUpTo("main") { inclusive = false }
                    }
                }
                is MainActivity.QuickAction.CreateChecklist -> {
                    // Navigate to add screen — checklist mode will be activated
                    SharedNoteData.startAsChecklist = true
                    navController.navigate("addscreen") {
                        popUpTo("main") { inclusive = false }
                    }
                }
            }
            pendingAction.value = null
            MainActivity.pendingAction.value = null
        }
    }

    // Initialize app and determine start destination
    LaunchedEffect(Unit) {
        try {
            Log.d("MyApp", "🚀 Starting app initialization...")

            // ── One-time SyncMode migration ──────────────────────────────────────
            // Existing installs have no KEY_SYNC_MODE stored. We infer the correct
            // mode from whether the stored passphrase equals the device ID:
            //   • equal (or empty) → LOCAL_ONLY   (never joined a shared account)
            //   • different        → CONTINUOUS    (user previously did a Restore)
            // This runs exactly once per install and is a no-op on fresh installs.
            withContext(Dispatchers.IO) {
                val migrationPrefs = context.getSharedPreferences("auth_migration", android.content.Context.MODE_PRIVATE)
                val alreadyMigrated = migrationPrefs.getBoolean("synced_mode_v1", false)
                if (!alreadyMigrated) {
                    val storedPass = com.amvarpvtltd.swiftNote.auth.PassphraseManager.getStoredPassphrase(context).orEmpty()
                    val deviceId = DeviceManager.getOrCreateDeviceId(context)
                    // Only infer CONTINUOUS if the stored passphrase is non-empty and doesn't
                    // match the device ID — that means the user previously did a Continuous Restore.
                    val inferredMode = if (storedPass.isNotEmpty() && storedPass != deviceId) {
                        SyncMode.CONTINUOUS
                    } else {
                        SyncMode.LOCAL_ONLY
                    }
                    com.amvarpvtltd.swiftNote.auth.PassphraseManager.setSyncMode(context, inferredMode)
                    migrationPrefs.edit { putBoolean("synced_mode_v1", true) }
                    Log.d("MyApp", "SyncMode migration complete — inferred: $inferredMode")
                }
            }
            // ── End SyncMode migration ────────────────────────────────────────────

            // Check if we have a stored passphrase (new system)
            val storedPassphrase = com.amvarpvtltd.swiftNote.auth.PassphraseManager.getStoredPassphrase(context)
            if (!storedPassphrase.isNullOrEmpty()) {
                DeviceIdentity.set(storedPassphrase, "NavHost.storedPassphrase")
                Log.d("MyApp", "✅ Found stored passphrase, going to main screen")
                startDestination = "main"
                isInitializing = false
                return@LaunchedEffect
            }

            // No local data and no stored credentials: check if this device id has data on Firebase (reinstall case)
            // BUG-004 FIX: Wrap in withTimeoutOrNull to prevent indefinite blocking on slow/unreachable networks
            try {
                val deviceId = DeviceManager.getOrCreateDeviceId(context)
                Log.d("MyApp", "No local notes — checking remote for deviceId: $deviceId")

                val remoteImportSuccess = withContext(Dispatchers.IO) {
                    withTimeoutOrNull(5_000L) { // 5-second timeout — never block app startup
                        val db = FirebaseDatabase.getInstance()
                        val userRef = db.getReference("users").child(deviceId)
                        val snapshot = userRef.get().await()
                        if (snapshot.exists()) {
                            Log.d("MyApp", "Remote data found for deviceId: $deviceId — importing to local DB")
                            val syncResult = SyncManager.syncDataFromPassphrase(context, deviceId, deviceId)
                            syncResult.isSuccess
                        } else {
                            false
                        }
                    }
                } ?: false // null means timeout — treat as no remote data

                if (remoteImportSuccess) {
                    val deviceId2 = DeviceManager.getOrCreateDeviceId(context)
                    Log.d("MyApp", "Imported remote notes for deviceId: $deviceId2")
                    DeviceIdentity.set(deviceId2, "NavHost.remoteImport")
                    startDestination = "main"
                    isInitializing = false
                    return@LaunchedEffect
                }
            } catch (e: Exception) {
                Log.d("MyApp", "Remote device check failed or timed out", e)
            }

            // No data found anywhere: show onboarding
            Log.d("MyApp", "🆕 New user, showing onboarding")
            startDestination = "onboarding"

        } catch (e: Exception) {
            Log.e("MyApp", "❌ Error during app initialization", e)
            startDestination = "onboarding"
        } finally {
            isInitializing = false
        }
    }

    // Apply theme to entire app
    ProvideNoteTheme(themeMode = currentTheme) {
        Surface(
            color = MaterialTheme.colorScheme.background,
            modifier = modifier
        ) {
            if (isInitializing) {
                LoadingScreen()
            } else {
                NavigationComponent(navController, startDestination)
            }
        }
    }
}

@Composable
fun LoadingScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator(
                color = NoteTheme.Primary,
                strokeWidth = 4.dp
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Loading SwiftNote...",
                style = MaterialTheme.typography.titleMedium,
                color = NoteTheme.OnSurface,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun NavigationComponent(navController: NavHostController, startDestination: String) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = {
            slideInHorizontally(
                initialOffsetX = { it },
                animationSpec = tween(280, easing = FastOutSlowInEasing)
            ) + fadeIn(animationSpec = tween(220))
        },
        exitTransition = {
            slideOutHorizontally(
                targetOffsetX = { -it / 4 },
                animationSpec = tween(280, easing = FastOutSlowInEasing)
            ) + fadeOut(animationSpec = tween(220))
        },
        popEnterTransition = {
            slideInHorizontally(
                initialOffsetX = { -it / 4 },
                animationSpec = tween(280, easing = FastOutSlowInEasing)
            ) + fadeIn(animationSpec = tween(220))
        },
        popExitTransition = {
            slideOutHorizontally(
                targetOffsetX = { it },
                animationSpec = tween(280, easing = FastOutSlowInEasing)
            ) + fadeOut(animationSpec = tween(220))
        }
    ) {
        // Onboarding & Auth
        composable("onboarding") {
            com.amvarpvtltd.swiftNote.design.OnboardingScreen(navController)
        }

        // Main notes list
        composable("main") {
            NotesScreen(navController)
        }

        // Note Management
        composable("addscreen") {
            AddScreen(navController, noteId = null)
        }

        composable("addscreen/{noteId}") { backStackEntry ->
            val noteId = backStackEntry.arguments?.getString("noteId")
            AddScreen(navController = navController, noteId = noteId)
        }

        composable("viewnote/{noteId}") { backStackEntry ->
            val noteId = backStackEntry.arguments?.getString("noteId")
            ViewNoteScreen(navController = navController, noteId = noteId)
        }

        // Sync & Settings
        composable("syncSettings") {
            com.amvarpvtltd.swiftNote.design.SyncSettingsScreen(navController)
        }

        composable("offlineSyncScreen") {
            com.amvarpvtltd.swiftNote.design.OfflineSyncScreen(navController)
        }

        // AI Settings (Gemini API Key management)
        composable("aiSettings") {
            com.amvarpvtltd.swiftNote.design.AISettingsScreen(navController)
        }

        // Phase 4: Archive screen
        composable("archive") {
            com.amvarpvtltd.swiftNote.design.ArchiveScreen(navController)
        }

        // Phase 5: Today / Daily Review screen
        composable("today") {
            TodayScreen(navController)
        }
    }
}

