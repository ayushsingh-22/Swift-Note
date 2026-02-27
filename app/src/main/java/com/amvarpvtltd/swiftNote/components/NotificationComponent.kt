package com.amvarpvtltd.swiftNote.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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