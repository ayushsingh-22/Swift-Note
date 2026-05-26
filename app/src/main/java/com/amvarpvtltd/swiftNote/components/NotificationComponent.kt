package com.amvarpvtltd.swiftNote.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.amvarpvtltd.swiftNote.design.NoteTheme
import com.amvarpvtltd.swiftNote.utils.Constants
import kotlinx.coroutines.delay

enum class NotificationType {
    SUCCESS, ERROR, WARNING, INFO
}

data class NotificationData(
    val title: String,
    val message: String,
    val type: NotificationType = NotificationType.INFO,
    val duration: Long = 3000 // Default duration in milliseconds
)

object NotificationManager {
    private val _currentNotification = mutableStateOf<NotificationData?>(null)

    /** Public read-only access for composables to observe */
    val currentNotification: State<NotificationData?> = _currentNotification

    fun showNotification(
        title: String,
        message: String,
        type: NotificationType = NotificationType.INFO,
        duration: Long = 3000
    ) {
        _currentNotification.value = NotificationData(
            title = title,
            message = message,
            type = type,
            duration = duration
        )
    }

    fun clearNotification() {
        _currentNotification.value = null
    }
}

object NotificationHelper {
    fun showSuccess(
        title: String,
        message: String,
        duration: Long = 3000
    ) {
        NotificationManager.showNotification(
            title = title,
            message = message,
            type = NotificationType.SUCCESS,
            duration = duration
        )
    }

    fun showError(
        title: String,
        message: String,
        duration: Long = 4000
    ) {
        NotificationManager.showNotification(
            title = title,
            message = message,
            type = NotificationType.ERROR,
            duration = duration
        )
    }

    fun showWarning(
        title: String,
        message: String,
        duration: Long = 3500
    ) {
        NotificationManager.showNotification(
            title = title,
            message = message,
            type = NotificationType.WARNING,
            duration = duration
        )
    }

    fun showInfo(
        title: String,
        message: String,
        duration: Long = 3000
    ) {
        NotificationManager.showNotification(
            title = title,
            message = message,
            type = NotificationType.INFO,
            duration = duration
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// InAppNotificationBanner — the composable UI overlay that renders notifications
// Place this composable inside a Box with fillMaxSize so it can overlay content.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Renders the global in-app notification banner.
 *
 * This composable observes [NotificationManager.currentNotification] and slides
 * a pill-shaped banner in from the top of the screen. It auto-dismisses after
 * the notification's [NotificationData.duration] and can also be dismissed by
 * tapping the ✕ button.
 *
 * **Placement:** add this inside a top-level `Box(Modifier.fillMaxSize())` in
 * every screen that should display in-app notifications:
 * ```kotlin
 * Box(Modifier.fillMaxSize()) {
 *     // screen content
 *     InAppNotificationBanner(modifier = Modifier.align(Alignment.TopCenter))
 * }
 * ```
 */
@Composable
fun InAppNotificationBanner(
    modifier: Modifier = Modifier
) {
    val notification by NotificationManager.currentNotification

    // Separate "should be shown" state so we can drive the exit animation before clearing.
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(notification) {
        if (notification != null) {
            visible = true
            delay(notification!!.duration)
            visible = false
            delay(350L) // let the exit animation finish
            NotificationManager.clearNotification()
        } else {
            visible = false
        }
    }

    AnimatedVisibility(
        visible = visible && notification != null,
        modifier = modifier,
        enter = slideInVertically(
            initialOffsetY = { -it - 40 },
            animationSpec = tween(durationMillis = 380)
        ) + fadeIn(animationSpec = tween(300)),
        exit = slideOutVertically(
            targetOffsetY = { -it - 40 },
            animationSpec = tween(durationMillis = 300)
        ) + fadeOut(animationSpec = tween(250))
    ) {
        notification?.let { data ->
            val config = notificationConfig(data.type)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(
                        elevation = 8.dp,
                        shape = RoundedCornerShape(14.dp),
                        ambientColor = config.iconTint.copy(alpha = 0.15f),
                        spotColor = config.iconTint.copy(alpha = 0.25f)
                    ),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = config.containerColor),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    config.iconTint.copy(alpha = 0.25f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Type icon
                    Icon(
                        imageVector = config.icon,
                        contentDescription = null,
                        tint = config.iconTint,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    // Title + message
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = data.title,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = config.contentColor
                        )
                        if (data.message.isNotBlank()) {
                            Text(
                                text = data.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = config.contentColor.copy(alpha = 0.8f),
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                    // Dismiss button
                    IconButton(
                        onClick = {
                            visible = false
                            NotificationManager.clearNotification()
                        },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Dismiss",
                            tint = config.contentColor.copy(alpha = 0.55f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

/** Internal config bundle for [InAppNotificationBanner] colors/icons */
private data class NotifConfig(
    val icon: ImageVector,
    val iconTint: Color,
    val containerColor: Color,
    val contentColor: Color
)

private fun notificationConfig(type: NotificationType): NotifConfig = when (type) {
    NotificationType.SUCCESS -> NotifConfig(
        icon = Icons.Default.CheckCircle,
        iconTint = NoteTheme.Success,
        containerColor = NoteTheme.SuccessContainer,
        contentColor = NoteTheme.OnSuccessContainer
    )
    NotificationType.ERROR -> NotifConfig(
        icon = Icons.Default.ErrorOutline,
        iconTint = NoteTheme.Error,
        containerColor = NoteTheme.ErrorContainer,
        contentColor = NoteTheme.OnErrorContainer
    )
    NotificationType.WARNING -> NotifConfig(
        icon = Icons.Default.Warning,
        iconTint = NoteTheme.Warning,
        containerColor = NoteTheme.WarningContainer,
        contentColor = NoteTheme.OnWarningContainer
    )
    NotificationType.INFO -> NotifConfig(
        icon = Icons.Default.Info,
        iconTint = NoteTheme.Primary,
        containerColor = NoteTheme.PrimaryContainer,
        contentColor = NoteTheme.OnPrimaryContainer
    )
}
