package com.amvarpvtltd.swiftNote.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amvarpvtltd.swiftNote.design.NoteTheme
import com.amvarpvtltd.swiftNote.utils.Constants
import kotlinx.coroutines.delay

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun OfflineEmptyStateCard(
    isOnline: Boolean,
    hasPendingSync: Boolean,
    onCreateNoteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showPulse by remember { mutableStateOf(true) }

    // Animated pulse effect for offline indicator
    val pulseAlpha by animateFloatAsState(
        targetValue = if (showPulse && !isOnline) 0.3f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    LaunchedEffect(isOnline) {
        if (!isOnline) {
            while (!isOnline) {
                showPulse = true
                delay(1000)
                showPulse = false
                delay(1000)
            }
        } else {
            showPulse = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(Constants.PADDING_LARGE.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Constants.PADDING_MEDIUM.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isOnline) NoteTheme.Surface else NoteTheme.Surface.copy(alpha = 0.95f)
            ),
            shape = RoundedCornerShape(Constants.CORNER_RADIUS_XL.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(Constants.PADDING_XL.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Constants.PADDING_LARGE.dp)
            ) {
                // Status indicator with icon
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .background(
                            brush = Brush.radialGradient(
                                colors = if (isOnline) {
                                    listOf(
                                        NoteTheme.Success.copy(alpha = 0.2f),
                                        NoteTheme.Success.copy(alpha = 0.05f)
                                    )
                                } else {
                                    listOf(
                                        NoteTheme.Warning.copy(alpha = pulseAlpha),
                                        NoteTheme.Warning.copy(alpha = 0.05f)
                                    )
                                }
                            ),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isOnline) {
                            if (hasPendingSync) Icons.Outlined.CloudSync else Icons.Outlined.CloudDone
                        } else {
                            Icons.Outlined.CloudOff
                        },
                        contentDescription = null,
                        tint = if (isOnline) {
                            if (hasPendingSync) NoteTheme.Primary else NoteTheme.Success
                        } else {
                            NoteTheme.Warning
                        },
                        modifier = Modifier.size(48.dp)
                    )
                }

                // Title and status message
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Constants.PADDING_SMALL.dp)
                ) {
                    Text(
                        text = if (isOnline) {
                            if (hasPendingSync) "Syncing Notes" else "No Notes Yet"
                        } else {
                            "You're Offline"
                        },
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = NoteTheme.OnSurface,
                        textAlign = TextAlign.Center
                    )

                    AnimatedContent(
                        targetState = Pair(isOnline, hasPendingSync),
                        transitionSpec = {
                            (slideInVertically { it } + fadeIn()).togetherWith(slideOutVertically { -it } + fadeOut())
                        },
                        label = "status_message"
                    ) { (online, syncing) ->
                        Text(
                            text = when {
                                !online -> "Your notes are saved locally and will sync automatically when you're back online"
                                syncing -> "Your notes are being synchronized with the cloud"
                                else -> "Create your first note to capture your thoughts and ideas"
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            color = NoteTheme.OnSurfaceVariant,
                            textAlign = TextAlign.Center,
                            lineHeight = 24.sp
                        )
                    }
                }

                // Action buttons
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Constants.PADDING_MEDIUM.dp)
                ) {
                    // Primary action button
                    Button(
                        onClick = onCreateNoteClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NoteTheme.Primary,
                            contentColor = NoteTheme.OnPrimary
                        ),
                        shape = RoundedCornerShape(Constants.CORNER_RADIUS_LARGE.dp),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Add,
                            contentDescription = null,
                            modifier = Modifier.size(Constants.ICON_SIZE_LARGE.dp)
                        )
                        Spacer(modifier = Modifier.width(Constants.PADDING_SMALL.dp))
                        Text(
                            text = if (isOnline) "Create Your First Note" else "Create Note Offline",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }

                    // Status information
                    if (!isOnline || hasPendingSync) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(Constants.PADDING_SMALL.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isOnline) Icons.Outlined.Info else Icons.Outlined.WifiOff,
                                contentDescription = null,
                                tint = if (isOnline) NoteTheme.Primary else NoteTheme.Warning,
                                modifier = Modifier.size(Constants.ICON_SIZE_SMALL.dp)
                            )
                            Text(
                                text = if (isOnline) {
                                    "Auto-sync is active"
                                } else {
                                    "Working offline"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isOnline) NoteTheme.Primary else NoteTheme.Warning,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // Offline features info
                if (!isOnline) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = NoteTheme.SurfaceVariant.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(Constants.CORNER_RADIUS_MEDIUM.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(Constants.PADDING_MEDIUM.dp),
                            verticalArrangement = Arrangement.spacedBy(Constants.PADDING_SMALL.dp)
                        ) {
                            Text(
                                text = "Offline Features Available:",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = NoteTheme.OnSurface
                            )

                            listOf(
                                "Create and edit notes",
                                "Delete existing notes",
                                "Search through notes",
                                "Auto-sync when online"
                            ).forEach { feature ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(Constants.PADDING_SMALL.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Check,
                                        contentDescription = null,
                                        tint = NoteTheme.Success,
                                        modifier = Modifier.size(Constants.ICON_SIZE_SMALL.dp)
                                    )
                                    Text(
                                        text = feature,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = NoteTheme.OnSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
