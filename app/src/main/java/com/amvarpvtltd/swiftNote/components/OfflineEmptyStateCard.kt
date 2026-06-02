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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amvarpvtltd.swiftNote.design.NoteTheme
import com.amvarpvtltd.swiftNote.utils.Constants
import kotlinx.coroutines.delay
import com.amvarpvtltd.swiftNote.utils.rememberResponsiveDimensions

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun OfflineEmptyStateCard(
    isOnline: Boolean,
    hasPendingSync: Boolean,
    onCreateNoteClick: () -> Unit,
    onSeedDemoClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val dims = rememberResponsiveDimensions()
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

    // Derived responsive sizes — keep card components visually consistent
    // across width buckets (COMPACT / MEDIUM / EXPANDED) AND across phones of
    // different physical heights within the same bucket. All hardcoded dp/sp
    // values below were causing layout overflow on shorter screens (e.g.
    // 384x854 vs 412x912 — both fall in MEDIUM so they used identical dims,
    // but the fixed 120/48/56/32 values left no room for the status row).
    val statusIndicatorSize = dims.iconLarge * 5       // 100 / 120 / 140
    val statusIndicatorIcon = dims.iconLarge * 2       //  40 /  48 /  56
    val primaryButtonHeight = dims.fabSize             //  56 /  64 /  72
    val secondaryButtonHeight = dims.fabSize - 8.dp    //  48 /  56 /  64
    val cardElevation = dims.paddingSmall              //   6 /   8 /  10
    val cardInnerPadding = dims.paddingLarge           //  18 /  24 /  32 (was paddingXL — too generous on compact)
    val sectionSpacing = dims.paddingMedium            //  12 /  15 /  20 (was paddingLarge — gave bottom row no room)

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(dims.paddingMedium),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(dims.paddingSmall),
            colors = CardDefaults.cardColors(
                containerColor = if (isOnline) NoteTheme.Surface else NoteTheme.Surface.copy(alpha = 0.95f)
            ),
            shape = RoundedCornerShape(dims.cornerXL),
            elevation = CardDefaults.cardElevation(defaultElevation = cardElevation)
        ) {
            Column(
                modifier = Modifier
                    .padding(cardInnerPadding)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(sectionSpacing)
            ) {
                // Status indicator with icon
                Box(
                    modifier = Modifier
                        .size(statusIndicatorSize)
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
                        modifier = Modifier.size(statusIndicatorIcon)
                    )
                }

                // Title and status message
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(dims.paddingSmall)
                ) {
                    Text(
                        text = if (isOnline) {
                            if (hasPendingSync) "Syncing Notes" else "No Notes Yet"
                        } else {
                            "You're Offline"
                        },
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontSize = dims.scaledSp(24)
                        ),
                        fontWeight = FontWeight.Bold,
                        color = NoteTheme.OnSurface,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
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
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontSize = dims.scaledSp(15)
                            ),
                            color = NoteTheme.OnSurfaceVariant,
                            textAlign = TextAlign.Center,
                            lineHeight = dims.scaledSp(22)
                        )
                    }
                }

                // Action buttons
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(dims.paddingSmall)
                ) {
                    // Primary action button
                    Button(
                        onClick = onCreateNoteClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = primaryButtonHeight),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NoteTheme.Primary,
                            contentColor = NoteTheme.OnPrimary
                        ),
                        shape = RoundedCornerShape(dims.cornerLarge),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Add,
                            contentDescription = null,
                            modifier = Modifier.size(dims.iconLarge)
                        )
                        Spacer(modifier = Modifier.width(dims.paddingSmall))
                        Text(
                            text = if (isOnline) "Create First Note" else "Create Note Offline",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = dims.scaledSp(16)
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Demo data button – only shown when there are no notes
                    if (onSeedDemoClick != null) {
                        OutlinedButton(
                            onClick = onSeedDemoClick,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = secondaryButtonHeight),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = NoteTheme.Primary
                            ),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                NoteTheme.Primary.copy(alpha = 0.5f)
                            ),
                            shape = RoundedCornerShape(dims.cornerLarge)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.AutoAwesome,
                                contentDescription = null,
                                modifier = Modifier.size(dims.iconMedium)
                            )
                            Spacer(modifier = Modifier.width(dims.paddingSmall))
                            Text(
                                text = "Load Demo Notes",
                                fontWeight = FontWeight.Medium,
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontSize = dims.scaledSp(14)
                                )
                            )
                        }
                    }

                    // Status information. Always shown on the empty state so
                    // the card layout stays stable across sync states — only
                    // the icon/colour/label vary. Avoids the jarring "shape
                    // shift" between "Syncing Notes" and "No Notes Yet".
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(dims.paddingSmall),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val statusIcon = when {
                            !isOnline -> Icons.Outlined.WifiOff
                            hasPendingSync -> Icons.Outlined.CloudSync
                            else -> Icons.Outlined.CloudDone
                        }
                        val statusTint = when {
                            !isOnline -> NoteTheme.Warning
                            hasPendingSync -> NoteTheme.Primary
                            else -> NoteTheme.Success
                        }
                        val statusLabel = when {
                            !isOnline -> "Working offline"
                            hasPendingSync -> "Syncing in background"
                            else -> "Auto-sync is active"
                        }
                        Icon(
                            imageVector = statusIcon,
                            contentDescription = null,
                            tint = statusTint,
                            modifier = Modifier.size(dims.iconSmall)
                        )
                        Text(
                            text = statusLabel,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontSize = dims.scaledSp(12)
                            ),
                            color = statusTint,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Offline features info
                if (!isOnline) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = NoteTheme.SurfaceVariant.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(dims.cornerMedium)
                    ) {
                        Column(
                            modifier = Modifier.padding(dims.paddingMedium),
                            verticalArrangement = Arrangement.spacedBy(dims.paddingSmall)
                        ) {
                            Text(
                                text = "Offline Features Available:",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontSize = dims.scaledSp(14)
                                ),
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
                                    horizontalArrangement = Arrangement.spacedBy(dims.paddingSmall)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Check,
                                        contentDescription = null,
                                        tint = NoteTheme.Success,
                                        modifier = Modifier.size(dims.iconSmall)
                                    )
                                    Text(
                                        text = feature,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontSize = dims.scaledSp(12)
                                        ),
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
