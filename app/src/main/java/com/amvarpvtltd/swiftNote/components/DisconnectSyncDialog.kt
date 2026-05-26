package com.amvarpvtltd.swiftNote.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amvarpvtltd.swiftNote.design.NoteTheme
import kotlinx.coroutines.delay

/**
 * Confirmation dialog shown when the user taps "Disconnect" from Continuous Sync.
 *
 * Design follows the same token set as the dialogs in SyncSettingsScreen:
 *  – [AlertDialog] with [NoteTheme.Surface] container + RoundedCornerShape(20.dp)
 *  – Title icon badge: [Card] RoundedCornerShape(8.dp) wrapping a 28dp icon with 6dp padding
 *  – Option cards: [ElevatedCard] + [NoteTheme.Surface] container + matching [Card] icon badges
 *  – Press-spring scale animation on both option cards
 *  – Loading state: centred [CircularProgressIndicator] with fade-in/out
 *
 * @param isLoading      True while a disconnect operation is in progress. Dims both option
 *                       cards and shows a progress indicator; blocks dismiss/taps.
 * @param onKeepNotes    Called when the user picks "Keep my notes".
 * @param onRemoveNotes  Called when the user picks "Remove notes from this device".
 * @param onCancel       Called when the user dismisses or taps Cancel.
 */
@Composable
fun DisconnectSyncDialog(
    isLoading: Boolean,
    onKeepNotes: () -> Unit,
    onRemoveNotes: () -> Unit,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!isLoading) onCancel() },
        shape = RoundedCornerShape(20.dp),
        containerColor = NoteTheme.Surface,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // ── Icon badge (slightly smaller to save horizontal space) ──
                Card(
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = NoteTheme.ErrorContainer.copy(alpha = 0.6f)
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.LinkOff,
                        contentDescription = null,
                        tint = NoteTheme.Error,
                        modifier = Modifier
                            .size(24.dp)   // 28→24 dp
                            .padding(5.dp) // 6→5 dp
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                // weight(1f) makes the text wrap instead of pushing the row wider
                Text(
                    text = "Disconnect Shared Sync",
                    fontWeight = FontWeight.Bold,
                    color = NoteTheme.OnSurface,
                    modifier = Modifier.weight(1f)
                )
            }
        },
        text = {
            // verticalScroll ensures nothing is clipped on short/small screens
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                // ── Explanation ───────────────────────────────────────────────
                Text(
                    text = "After disconnecting, this device will stop syncing with the shared account. What should happen to your notes?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = NoteTheme.OnSurfaceVariant,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 20.sp
                )

                // ── Option 1: Keep notes ──────────────────────────────────────
                var keepPressed by remember { mutableStateOf(false) }
                val keepScale by animateFloatAsState(
                    targetValue = if (keepPressed) 0.97f else 1f,
                    animationSpec = spring(dampingRatio = 0.45f, stiffness = 600f),
                    label = "keep_scale"
                )

                ElevatedCard(
                    onClick = {
                        if (!isLoading) {
                            keepPressed = true
                            onKeepNotes()
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .scale(keepScale)
                        .alpha(if (isLoading) 0.45f else 1f),
                    colors = CardDefaults.elevatedCardColors(containerColor = NoteTheme.Surface),
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        // Alignment.Top so a tall description column doesn't
                        // push the icon & chevron to an awkward vertical centre
                        verticalAlignment = Alignment.Top
                    ) {
                        // Smaller badge: 22dp icon / 4dp padding
                        Card(
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = NoteTheme.SuccessContainer.copy(alpha = 0.5f)
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Save,
                                contentDescription = null,
                                tint = NoteTheme.Success,
                                modifier = Modifier
                                    .size(22.dp)
                                    .padding(4.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Keep my notes",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = NoteTheme.OnSurface
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = "Notes stay on this device. Switches to its own private account for future sync.",
                                style = MaterialTheme.typography.bodySmall,
                                color = NoteTheme.OnSurfaceVariant,
                                lineHeight = 17.sp
                            )
                        }
                        // Nudge chevron down slightly to align with the title text
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = NoteTheme.Success.copy(alpha = 0.6f),
                            modifier = Modifier
                                .size(16.dp)
                                .padding(top = 2.dp)
                        )
                    }
                }

                LaunchedEffect(keepPressed) {
                    if (keepPressed) { delay(150); keepPressed = false }
                }

                // ── Option 2: Remove notes ────────────────────────────────────
                var removePressed by remember { mutableStateOf(false) }
                val removeScale by animateFloatAsState(
                    targetValue = if (removePressed) 0.97f else 1f,
                    animationSpec = spring(dampingRatio = 0.45f, stiffness = 600f),
                    label = "remove_scale"
                )

                ElevatedCard(
                    onClick = {
                        if (!isLoading) {
                            removePressed = true
                            onRemoveNotes()
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .scale(removeScale)
                        .alpha(if (isLoading) 0.45f else 1f),
                    colors = CardDefaults.elevatedCardColors(containerColor = NoteTheme.Surface),
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Card(
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = NoteTheme.ErrorContainer.copy(alpha = 0.5f)
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteForever,
                                contentDescription = null,
                                tint = NoteTheme.Error,
                                modifier = Modifier
                                    .size(22.dp)
                                    .padding(4.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Remove from this device",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = NoteTheme.Error
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = "All notes are deleted. The other device keeps its data.",
                                style = MaterialTheme.typography.bodySmall,
                                color = NoteTheme.OnSurfaceVariant,
                                lineHeight = 17.sp
                            )
                            Text(
                                text = "This action cannot be undone.",
                                style = MaterialTheme.typography.labelSmall,
                                color = NoteTheme.Error,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = NoteTheme.Error.copy(alpha = 0.6f),
                            modifier = Modifier
                                .size(16.dp)
                                .padding(top = 2.dp)
                        )
                    }
                }

                LaunchedEffect(removePressed) {
                    if (removePressed) { delay(150); removePressed = false }
                }

                // ── Loading indicator ─────────────────────────────────────────
                AnimatedVisibility(
                    visible = isLoading,
                    enter = fadeIn() + slideInVertically { it / 2 },
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
                            modifier = Modifier.size(18.dp),
                            color = NoteTheme.Primary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Disconnecting…",
                            style = MaterialTheme.typography.bodySmall,
                            color = NoteTheme.OnSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        },
        confirmButton = { /* options are the tappable cards above */ },
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
