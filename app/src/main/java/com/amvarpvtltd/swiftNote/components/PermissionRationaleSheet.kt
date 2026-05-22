package com.amvarpvtltd.swiftNote.components

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.amvarpvtltd.swiftNote.design.NoteTheme
import com.amvarpvtltd.swiftNote.utils.Constants

/**
 * Enum representing which permission is missing and needs rationale.
 */
enum class PermissionType {
    NOTIFICATION,
    EXACT_ALARM
}

/**
 * Checks what permissions are missing for reminders.
 * Returns null if all permissions are granted.
 */
fun checkReminderPermissions(context: Context): PermissionType? {
    // Check notification permission (Android 13+)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) return PermissionType.NOTIFICATION
    }

    // Check exact alarm permission (Android 12+)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (!alarmManager.canScheduleExactAlarms()) return PermissionType.EXACT_ALARM
    }

    return null
}

/**
 * A bottom sheet that explains why notification or exact-alarm permission is needed
 * and provides an action to grant it.
 *
 * Usage: Show this BEFORE opening the ReminderBottomSheet when permissions are missing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionRationaleSheet(
    permissionType: PermissionType,
    onDismiss: () -> Unit,
    onPermissionGranted: () -> Unit
) {
    val context = LocalContext.current

    // Launcher for notification permission (Android 13+)
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            onPermissionGranted()
        }
        // If denied, stay on sheet so user can see the explanation
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = NoteTheme.Surface,
        shape = RoundedCornerShape(
            topStart = Constants.CORNER_RADIUS_XL.dp,
            topEnd = Constants.CORNER_RADIUS_XL.dp
        ),
        dragHandle = {
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(NoteTheme.OnSurfaceVariant.copy(alpha = 0.3f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Constants.PADDING_LARGE.dp)
                .padding(bottom = Constants.PADDING_XL.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header with close button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(NoteTheme.OnSurfaceVariant.copy(alpha = 0.1f))
                ) {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = "Close",
                        tint = NoteTheme.OnSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(Constants.PADDING_MEDIUM.dp))

            // Icon
            val (icon, title, description) = when (permissionType) {
                PermissionType.NOTIFICATION -> Triple(
                    Icons.Outlined.NotificationsActive,
                    "Enable Notifications",
                    "SwiftNote needs notification permission to deliver your reminders on time. Without it, reminders will be set but you won't see them."
                )
                PermissionType.EXACT_ALARM -> Triple(
                    Icons.Outlined.Alarm,
                    "Allow Exact Alarms",
                    "To fire reminders at the exact time you choose, SwiftNote needs the \"Alarms & Reminders\" permission. This ensures your reminders aren't delayed by battery optimization."
                )
            }

            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                NoteTheme.Primary.copy(alpha = 0.2f),
                                NoteTheme.Primary.copy(alpha = 0.05f)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = NoteTheme.Primary,
                    modifier = Modifier.size(36.dp)
                )
            }

            Spacer(modifier = Modifier.height(Constants.PADDING_LARGE.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = NoteTheme.OnSurface,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(Constants.PADDING_SMALL.dp))

            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = NoteTheme.OnSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = Constants.PADDING_MEDIUM.dp)
            )

            Spacer(modifier = Modifier.height(Constants.PADDING_XL.dp))

            // Grant permission button
            Button(
                onClick = {
                    when (permissionType) {
                        PermissionType.NOTIFICATION -> {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notificationPermissionLauncher.launch(
                                    Manifest.permission.POST_NOTIFICATIONS
                                )
                            }
                        }
                        PermissionType.EXACT_ALARM -> {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                try {
                                    val intent = Intent(
                                        Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                        Uri.parse("package:${context.packageName}")
                                    )
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    val fallbackIntent = Intent(
                                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                                    ).apply {
                                        data = Uri.fromParts("package", context.packageName, null)
                                    }
                                    context.startActivity(fallbackIntent)
                                }
                            }
                            // For exact alarm, user must toggle in settings; dismiss sheet
                            onDismiss()
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = NoteTheme.Primary
                ),
                shape = RoundedCornerShape(Constants.CORNER_RADIUS_LARGE.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Constants.PADDING_SMALL.dp)
                ) {
                    Icon(
                        if (permissionType == PermissionType.EXACT_ALARM) Icons.Outlined.Settings
                        else icon,
                        contentDescription = null,
                        modifier = Modifier.size(Constants.ICON_SIZE_MEDIUM.dp)
                    )
                    Text(
                        text = if (permissionType == PermissionType.EXACT_ALARM)
                            "Open Settings" else "Grant Permission",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }

            Spacer(modifier = Modifier.height(Constants.PADDING_MEDIUM.dp))

            // Skip button
            OutlinedButton(
                onClick = {
                    // Allow user to proceed without granting (reminders may not work)
                    onPermissionGranted()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(Constants.CORNER_RADIUS_LARGE.dp)
            ) {
                Text(
                    text = "Skip for now",
                    style = MaterialTheme.typography.bodyMedium,
                    color = NoteTheme.OnSurfaceVariant
                )
            }
        }
    }
}

