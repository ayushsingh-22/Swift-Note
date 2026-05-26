package com.amvarpvtltd.swiftNote.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amvarpvtltd.swiftNote.design.NoteTheme

/**
 * Dialog shown after the user provides a passphrase (via QR or manual entry).
 * Lets the user choose between two sync strategies before any network call is made:
 *
 *  • One-Time Copy  — imports a snapshot, then this device keeps its own identity.
 *  • Continuous Sync — joins the source account for ongoing multi-device sync.
 *
 * @param sourcePassphrasePreview A short, safe preview of the passphrase (first 8 chars + "…").
 * @param isLoading               True while the sync operation is in progress. Dims cards and
 *                                shows a progress indicator; prevents dismiss/option taps.
 * @param onOneTimePicked         Called when the user taps "One-Time Copy".
 * @param onContinuousPicked      Called when the user taps "Continuous Sync".
 * @param onCancel                Called when the user taps Cancel or dismisses the dialog.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncModeDialog(
    sourcePassphrasePreview: String,
    isLoading: Boolean,
    onOneTimePicked: () -> Unit,
    onContinuousPicked: () -> Unit,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!isLoading) onCancel() },
        shape = RoundedCornerShape(20.dp),
        containerColor = NoteTheme.Surface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Icon badge — mirrors EnhancedSyncFromDeviceDialog style
                Card(
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = NoteTheme.SecondaryContainer.copy(alpha = 0.5f)
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Sync,
                        contentDescription = null,
                        tint = NoteTheme.Primary,
                        modifier = Modifier
                            .size(28.dp)
                            .padding(6.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Choose sync mode",
                    fontWeight = FontWeight.Bold,
                    color = NoteTheme.OnSurface
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {

                // Helper text
                Text(
                    text = "How would you like to use notes from \"$sourcePassphrasePreview\"?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = NoteTheme.OnSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )

                // ── Option 1: One-Time Copy ──────────────────────────────────
                ElevatedCard(
                    onClick = { if (!isLoading) onOneTimePicked() },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = NoteTheme.SurfaceVariant
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .alpha(if (isLoading) 0.45f else 1f)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Icon container
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .background(
                                    NoteTheme.PrimaryContainer,
                                    RoundedCornerShape(12.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudDownload,
                                contentDescription = null,
                                tint = NoteTheme.Primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "One-Time Copy",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = NoteTheme.OnSurface
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = "Import notes once. Future changes won't sync between devices. This device keeps its own account.",
                                style = MaterialTheme.typography.bodySmall,
                                color = NoteTheme.OnSurfaceVariant,
                                lineHeight = 17.sp
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = NoteTheme.Primary.copy(alpha = 0.5f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // ── Option 2: Continuous Sync ────────────────────────────────
                ElevatedCard(
                    onClick = { if (!isLoading) onContinuousPicked() },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = NoteTheme.SurfaceVariant
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .alpha(if (isLoading) 0.45f else 1f)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Icon container
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .background(
                                    NoteTheme.SecondaryContainer,
                                    RoundedCornerShape(12.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Sync,
                                contentDescription = null,
                                tint = NoteTheme.Secondary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Continuous Sync",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = NoteTheme.OnSurface
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = "Join the source account. Changes on either device will sync to both. Same account, multiple devices.",
                                style = MaterialTheme.typography.bodySmall,
                                color = NoteTheme.OnSurfaceVariant,
                                lineHeight = 17.sp
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = NoteTheme.Secondary.copy(alpha = 0.5f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // ── Loading indicator (shown during sync) ────────────────────
                AnimatedVisibility(
                    visible = isLoading,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = NoteTheme.Primary,
                            strokeWidth = 2.5.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Syncing your notes…",
                            style = MaterialTheme.typography.bodySmall,
                            color = NoteTheme.OnSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        },
        confirmButton = { /* no confirm button — options are the cards */ },
        dismissButton = {
            TextButton(
                onClick = { if (!isLoading) onCancel() },
                enabled = !isLoading
            ) {
                Text(
                    text = "Cancel",
                    color = if (!isLoading) NoteTheme.OnSurfaceVariant
                    else NoteTheme.OnSurfaceVariant.copy(alpha = 0.4f)
                )
            }
        }
    )
}

