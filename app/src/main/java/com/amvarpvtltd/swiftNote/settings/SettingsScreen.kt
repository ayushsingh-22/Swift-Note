package com.amvarpvtltd.swiftNote.settings

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.amvarpvtltd.swiftNote.BuildConfig
import com.amvarpvtltd.swiftNote.components.*
import com.amvarpvtltd.swiftNote.theme.AppThemeState
import com.amvarpvtltd.swiftNote.theme.ThemeMode
import com.amvarpvtltd.swiftNote.utils.rememberResponsiveDimensions
import com.amvarpvtltd.swiftNote.utils.responsiveContentWidth

@Composable
fun SettingsScreen(navController: NavController) {
    val context = LocalContext.current
    val dims = rememberResponsiveDimensions()
    val mode by AppThemeState.themeMode.collectAsState()
    var showPrivacy by rememberSaveable { mutableStateOf(false) }
    var showAbout by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { SwiftNoteTopBar("Settings", onBack = { navController.navigateUp() }) }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).responsiveContentWidth(dims)
                .verticalScroll(rememberScrollState()).padding(horizontal = dims.paddingLarge)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SwiftNoteFeatureHeader(Icons.Outlined.Tune, "MAKE IT YOURS", "A space that feels like you.",
                "Your appearance, reminders and connected devices, all in one place.")
            SwiftNoteSectionHeader("Appearance", "Choose what feels comfortable.")
            SwiftNoteCard {
                listOf(
                    Triple(ThemeMode.LIGHT, "Light", Icons.Outlined.LightMode),
                    Triple(ThemeMode.DARK, "Dark", Icons.Outlined.DarkMode),
                    Triple(ThemeMode.SYSTEM, "Follow system", Icons.Outlined.SettingsBrightness)
                ).forEach { (value, label, icon) ->
                    SwiftNoteSettingRow(
                        icon, label,
                        when (value) {
                            ThemeMode.LIGHT -> "Soft lavender and off-white"
                            ThemeMode.DARK -> "Charcoal and lavender"
                            ThemeMode.SYSTEM -> "Match your device appearance"
                        },
                        modifier = Modifier.semantics {
                            selected = mode == value
                            role = Role.RadioButton
                        },
                        onClick = { AppThemeState.setTheme(context, value) },
                        trailing = { RadioButton(selected = mode == value, onClick = null) }
                    )
                }
            }
            SwiftNoteSectionHeader("Your workspace")
            SwiftNoteCard {
                SwiftNoteSettingRow(Icons.Outlined.NotificationsNone, "Notifications",
                    "Manage reminder alerts in Android settings",
                    onClick = {
                        context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                    })
                SwiftNoteSettingRow(Icons.Outlined.AutoAwesome, "AI assistance",
                    "Providers, API keys and reminder preferences",
                    onClick = { navController.navigate("aiSettings") })
                SwiftNoteSettingRow(Icons.Outlined.Sync, "Synchronization",
                    "Pair devices, restore notes and manage sync",
                    onClick = { navController.navigate("syncSettings") })
            }
            SwiftNoteSectionHeader("Privacy and storage")
            SwiftNoteCard {
                SwiftNoteSettingRow(Icons.Outlined.Lock, "Privacy and security",
                    "How your notes and API keys are stored",
                    onClick = { showPrivacy = true })
                SwiftNoteSettingRow(Icons.Outlined.Storage, "Data management",
                    "Manage the data stored by SwiftNote",
                    onClick = { navController.navigate("dataManagement") })
            }
            SwiftNoteSectionHeader("About")
            SwiftNoteCard {
                SwiftNoteSettingRow(Icons.Outlined.Info, "SwiftNote",
                    "Version ${BuildConfig.VERSION_NAME}",
                    onClick = { showAbout = true })
            }
        }
    }
    if (showPrivacy) {
        AlertDialog(
            onDismissRequest = { showPrivacy = false },
            icon = { Icon(Icons.Outlined.Lock, null) },
            title = { Text("Your private space") },
            text = { Text("Notes are stored locally with encryption. Synchronization is optional and uses your existing QR or passphrase setup. API keys stay in encrypted storage on this device. When you use cloud AI, note text is sent to the configured provider.") },
            confirmButton = { TextButton(onClick = { showPrivacy = false }) { Text("Got it") } }
        )
    }
    if (showAbout) {
        AlertDialog(
            onDismissRequest = { showAbout = false },
            icon = { Icon(Icons.Outlined.EditNote, null) },
            title = { Text("SwiftNote ${BuildConfig.VERSION_NAME}") },
            text = { Text("A smarter space for everything on your mind. Capture notes, keep reminders close, and connect your devices on your terms.") },
            confirmButton = { TextButton(onClick = { showAbout = false }) { Text("Done") } }
        )
    }
}
