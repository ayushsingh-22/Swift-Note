package com.amvarpvtltd.swiftNote.components

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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.amvarpvtltd.swiftNote.design.NoteTheme
import com.amvarpvtltd.swiftNote.utils.Constants

@Composable
fun DeleteConfirmationDialog(
    showDialog: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    title: String,
    message: String = "Are you sure you want to delete this item?"
) {
    val hapticFeedback = LocalHapticFeedback.current

    if (showDialog) {
        AlertDialog(
            onDismissRequest = onDismiss,
            icon = {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    NoteTheme.Error.copy(alpha = 0.2f),
                                    NoteTheme.Error.copy(alpha = 0.05f)
                                )
                            ),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.DeleteOutline,
                        contentDescription = null,
                        tint = NoteTheme.Error,
                        modifier = Modifier.size(32.dp)
                    )
                }
            },
            title = {
                Text(
                    "Delete Note?",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = NoteTheme.OnSurface
                )
            },
            text = {
                Column {
                    Text(
                        message,
                        style = MaterialTheme.typography.bodyLarge,
                        color = NoteTheme.OnSurfaceVariant
                    )
                    if (title.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = NoteTheme.ErrorContainer.copy(alpha = 0.3f)
                            ),
                            shape = RoundedCornerShape(Constants.CORNER_RADIUS_SMALL.dp)
                        ) {
                            Text(
                                text = "\"$title\"",
                                style = MaterialTheme.typography.bodyMedium,
                                color = NoteTheme.OnSurface,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(Constants.CORNER_RADIUS_SMALL.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "This action cannot be undone.",
                        style = MaterialTheme.typography.bodySmall,
                        color = NoteTheme.Error,
                        fontWeight = FontWeight.Medium
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        onConfirm()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NoteTheme.Error,
                        contentColor = NoteTheme.OnPrimary
                    ),
                    shape = RoundedCornerShape(Constants.CORNER_RADIUS_SMALL.dp)
                ) {
                    Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Delete", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = onDismiss,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = NoteTheme.OnSurfaceVariant
                    ),
                    shape = RoundedCornerShape(Constants.CORNER_RADIUS_SMALL.dp)
                ) {
                    Text("Cancel", fontWeight = FontWeight.Medium)
                }
            },
            shape = RoundedCornerShape(Constants.CORNER_RADIUS_XL.dp),
            containerColor = NoteTheme.Surface
        )
    }
}

@Composable
fun LoadingCard(
    message: String = "Loading...",
    subMessage: String = "Please wait a moment"
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(Constants.CORNER_RADIUS_LARGE.dp),
            colors = CardDefaults.cardColors(containerColor = NoteTheme.Surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(Constants.PADDING_XL.dp)
            ) {
                CircularProgressIndicator(
                    color = NoteTheme.Primary,
                    strokeWidth = 4.dp
                )
                Spacer(modifier = Modifier.height(Constants.PADDING_MEDIUM.dp))
                Text(
                    message,
                    style = MaterialTheme.typography.titleMedium,
                    color = NoteTheme.OnSurface,
                    fontWeight = FontWeight.Medium
                )
                if (subMessage.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        subMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = NoteTheme.OnSurfaceVariant
                    )
                }
            }
        }
    }
}
