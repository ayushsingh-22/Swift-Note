package com.amvarpvtltd.swiftNote.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** Calm first-note state; network availability never prevents local capture. */
@Composable
fun OfflineEmptyStateCard(
    isOnline: Boolean,
    hasPendingSync: Boolean,
    onCreateNoteClick: () -> Unit,
    onSeedDemoClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 440.dp).verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Box(
                Modifier.size(116.dp).background(
                    Brush.radialGradient(listOf(colors.primaryContainer, colors.background)), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.EditNote, null, tint = colors.primary, modifier = Modifier.size(48.dp))
            }
            Text("Every thought starts here.", style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold, color = colors.onSurface, textAlign = TextAlign.Center)
            Text(
                if (isOnline) "Capture an idea, make a list, or save something for later."
                else "Create, edit, delete and search your notes on this device, even without internet.",
                style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            SwiftNotePrimaryButton(
                if (isOnline) "Create your first note" else "Create a note offline",
                onClick = onCreateNoteClick,
                modifier = Modifier.fillMaxWidth()
            )
            onSeedDemoClick?.let {
                TextButton(onClick = it) { Text("Load demo notes") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                if (!isOnline) {
                    Icon(Icons.Outlined.WifiOff, null, modifier = Modifier.size(16.dp), tint = colors.primary)
                }
                Text(
                    when {
                        !isOnline -> "Available offline"
                        hasPendingSync -> "Changes waiting to sync"
                        else -> "Sync is optional"
                    },
                    style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant
                )
            }
        }
    }
}
