package com.amvarpvtltd.swiftNote.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.rememberNavController
import com.amvarpvtltd.swiftNote.Note
import com.amvarpvtltd.swiftNote.settings.SettingsScreen
import com.amvarpvtltd.swiftNote.ui.theme.SelfNoteTheme

// Preview fixtures live only in the debug source set and never enter production screens.
@Preview(name = "Settings light", widthDp = 360, heightDp = 900)
@Preview(name = "Settings large text", widthDp = 320, heightDp = 640, fontScale = 1.5f)
@Preview(name = "Settings tablet", widthDp = 840, heightDp = 1180)
@Composable
private fun SettingsLightPreview() {
    SelfNoteTheme(darkTheme = false) { SettingsScreen(rememberNavController()) }
}

@Preview(name = "Settings dark", widthDp = 412, heightDp = 900)
@Composable
private fun SettingsDarkPreview() {
    SelfNoteTheme(darkTheme = true) { SettingsScreen(rememberNavController()) }
}

@Composable
private fun NoteCardSamples(dark: Boolean) {
    SelfNoteTheme(darkTheme = dark) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                SwiftNoteFeatureHeader(Icons.Outlined.EditNote, "A SPACE FOR YOUR IDEAS",
                    "Good morning", "Keep a little inspiration close.")
                SwiftNoteSectionHeader("Pinned")
                SwiftNoteNoteCard(
                    note = Note(id = "preview", mymobiledeviceid = "preview",
                        title = "Every thought deserves a place.",
                        description = "<p>Capture <b>something worth remembering</b>. Make room for a <i>new idea</i>.</p>",
                        category = "Ideas", isPinned = true, timestamp = 1793577600000L),
                    onView = {}, onEdit = {}, onDelete = {}, onShare = {}, onReminder = {},
                    onPin = {}, onArchive = {}
                )
                SwiftNoteSectionHeader("Recent notes")
                SwiftNoteNoteCard(
                    note = Note(id = "preview-two", mymobiledeviceid = "preview",
                        title = "Weekend checklist", description = com.amvarpvtltd.swiftNote.checklist.ChecklistParser.serializeItems(listOf(
                            com.amvarpvtltd.swiftNote.checklist.ChecklistItem(text = "Visit the farmers market", isChecked = true),
                            com.amvarpvtltd.swiftNote.checklist.ChecklistItem(text = "Try a <b>new recipe</b>"),
                            com.amvarpvtltd.swiftNote.checklist.ChecklistItem(text = "Make time for a walk")
                        )),
                        timestamp = 1793577600000L),
                    onView = {}, onEdit = {}, onDelete = {}, onShare = {}, onReminder = {}
                )
            }
        }
    }
}

@Preview(name = "Cards light", widthDp = 360, heightDp = 900)
@Preview(name = "Cards compact large text", widthDp = 320, heightDp = 1000, fontScale = 1.5f)
@Composable
private fun CardsLightPreview() = NoteCardSamples(false)

@Preview(name = "Cards dark", widthDp = 412, heightDp = 900)
@Composable
private fun CardsDarkPreview() = NoteCardSamples(true)
