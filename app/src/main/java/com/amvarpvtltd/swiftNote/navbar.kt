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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.amvarpvtltd.swiftNote.auth.DeviceManager
import com.amvarpvtltd.swiftNote.design.AddScreen
import com.amvarpvtltd.swiftNote.design.NoteTheme
import com.amvarpvtltd.swiftNote.design.NotesScreen
import com.amvarpvtltd.swiftNote.design.ViewNoteScreen
import com.amvarpvtltd.swiftNote.offline.OfflineNoteManager
import com.amvarpvtltd.swiftNote.sync.SyncManager
import com.amvarpvtltd.swiftNote.theme.ProvideNoteTheme
import com.amvarpvtltd.swiftNote.theme.rememberThemeState
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

@Composable
fun MyApp(modifier: Modifier = Modifier) {
    val navController = rememberNavController()

    // Initialize theme management
    val themeState = rememberThemeState()
    var currentTheme by themeState

    // Capture context once in composable scope
    val context = LocalContext.current

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

    // Initialize app and determine start destination
    LaunchedEffect(Unit) {
        try {
            Log.d("MyApp", "🚀 Starting app initialization...")

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
            color = MaterialTheme.colorScheme.surfaceTint,
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
    NavHost(navController = navController, startDestination = startDestination) {
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
    }
}
