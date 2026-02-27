package com.amvarpvtltd.swiftNote.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.amvarpvtltd.swiftNote.design.NoteTheme
import com.amvarpvtltd.swiftNote.utils.Constants
import kotlinx.coroutines.delay

@Composable
fun OfflineBanner(
    isVisible: Boolean,
    message: String = "You're offline. Changes will sync when connected.",
    onDismiss: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var showPulse by remember { mutableStateOf(true) }

    val pulseAlpha by animateFloatAsState(
        targetValue = if (showPulse) 0.3f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    LaunchedEffect(isVisible) {
        if (isVisible) {
            while (isVisible) {
                showPulse = true
                delay(1500)
                showPulse = false
                delay(1500)
            }
        }
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically { -it } + fadeIn(),
        exit = slideOutVertically { -it } + fadeOut(),
        modifier = modifier
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Constants.PADDING_MEDIUM.dp),
            colors = CardDefaults.cardColors(
                containerColor = NoteTheme.Warning.copy(alpha = pulseAlpha)
            ),
            shape = RoundedCornerShape(Constants.CORNER_RADIUS_MEDIUM.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Constants.PADDING_MEDIUM.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CloudOff,
                        contentDescription = null,
                        tint = NoteTheme.OnSurface,
                        modifier = Modifier.size(Constants.ICON_SIZE_MEDIUM.dp)
                    )
                    Spacer(modifier = Modifier.width(Constants.PADDING_SMALL.dp))
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = NoteTheme.OnSurface,
                        fontWeight = FontWeight.Medium
                    )
                }

                onDismiss?.let {
                    IconButton(onClick = it) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "Dismiss",
                            tint = NoteTheme.OnSurface,
                            modifier = Modifier.size(Constants.ICON_SIZE_SMALL.dp)
                        )
                    }
                }
            }
        }
    }
}


