package com.amvarpvtltd.swiftNote.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AttachMoney
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Message
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amvarpvtltd.swiftNote.ai.DetectedEntity
import com.amvarpvtltd.swiftNote.design.NoteTheme
import com.google.firebase.analytics.FirebaseAnalytics

/**
 * Renders a horizontal scrollable row of action chips for detected entities in a note.
 * Each entity type gets contextual action chips (Call, WhatsApp, SMS for phones; Open, Copy for URLs, etc.)
 *
 * @param onAddReminderClick Callback when "Add Reminder" chip is tapped for a DateTime entity.
 *        Parent composable should open the ReminderBottomSheet with the detected date text.
 */
@Composable
fun SmartActionChipRow(
    entities: List<DetectedEntity>,
    modifier: Modifier = Modifier,
    visible: Boolean = true,
    onAddReminderClick: ((dateTimeText: String) -> Unit)? = null
) {
    if (entities.isEmpty()) return

    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(400)) + slideInVertically(
            animationSpec = tween(400),
            initialOffsetY = { it / 2 }
        )
    ) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        ) {
            Text(
                text = "Smart Actions",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = NoteTheme.OnSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                entities.forEach { entity ->
                    when (entity) {
                        is DetectedEntity.PhoneNumber -> {
                            ActionChip(
                                label = "Call ${entity.raw.take(12)}",
                                icon = Icons.Outlined.Call,
                                onClick = {
                                    logSmartChipTapped(context, "phone_call")
                                    launchDial(context, entity.normalized)
                                }
                            )
                            ActionChip(
                                label = "WhatsApp",
                                icon = Icons.Outlined.Message,
                                onClick = {
                                    logSmartChipTapped(context, "phone_whatsapp")
                                    launchWhatsApp(context, entity.normalized)
                                }
                            )
                            ActionChip(
                                label = "SMS",
                                icon = Icons.Outlined.Phone,
                                onClick = {
                                    logSmartChipTapped(context, "phone_sms")
                                    launchSms(context, entity.normalized)
                                }
                            )
                        }

                        is DetectedEntity.Email -> {
                            ActionChip(
                                label = entity.address.take(20),
                                icon = Icons.Outlined.Email,
                                onClick = {
                                    logSmartChipTapped(context, "email")
                                    launchEmail(context, entity.address)
                                }
                            )
                            ActionChip(
                                label = "Copy",
                                icon = Icons.Outlined.ContentCopy,
                                onClick = {
                                    logSmartChipTapped(context, "email_copy")
                                    clipboardManager.setText(AnnotatedString(entity.address))
                                }
                            )
                        }

                        is DetectedEntity.Url -> {
                            ActionChip(
                                label = entity.raw.take(25),
                                icon = Icons.Outlined.Language,
                                onClick = {
                                    logSmartChipTapped(context, "url_open")
                                    launchUrl(context, entity.url)
                                }
                            )
                            ActionChip(
                                label = "Copy",
                                icon = Icons.Outlined.ContentCopy,
                                onClick = {
                                    logSmartChipTapped(context, "url_copy")
                                    clipboardManager.setText(AnnotatedString(entity.url))
                                }
                            )
                        }

                        is DetectedEntity.Address -> {
                            ActionChip(
                                label = "Maps: ${entity.text.take(18)}",
                                icon = Icons.Outlined.LocationOn,
                                onClick = {
                                    logSmartChipTapped(context, "address_maps")
                                    launchMaps(context, entity.text)
                                }
                            )
                        }

                        is DetectedEntity.DateTime -> {
                            ActionChip(
                                label = "Remind: ${entity.text.take(15)}",
                                icon = Icons.Outlined.Notifications,
                                onClick = {
                                    logSmartChipTapped(context, "datetime_reminder")
                                    onAddReminderClick?.invoke(entity.text)
                                }
                            )
                            ActionChip(
                                label = "Calendar",
                                icon = Icons.Outlined.Event,
                                onClick = {
                                    logSmartChipTapped(context, "datetime_calendar")
                                    launchCalendar(context, entity.text)
                                }
                            )
                        }

                        is DetectedEntity.Amount -> {
                            ActionChip(
                                label = entity.raw.take(15),
                                icon = Icons.Outlined.AttachMoney,
                                onClick = {
                                    logSmartChipTapped(context, "amount_copy")
                                    clipboardManager.setText(AnnotatedString(entity.raw))
                                }
                            )
                        }

                        is DetectedEntity.TrackingNumber -> {
                            ActionChip(
                                label = "Track: ${entity.raw.take(12)}",
                                icon = Icons.Outlined.LocalShipping,
                                onClick = {
                                    logSmartChipTapped(context, "tracking_copy")
                                    clipboardManager.setText(AnnotatedString(entity.raw))
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionChip(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    AssistChip(
        onClick = onClick,
        label = {
            Text(
                text = label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 12.sp
            )
        },
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(16.dp)
            )
        },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = NoteTheme.PrimaryContainer,
            labelColor = NoteTheme.OnPrimaryContainer,
            leadingIconContentColor = NoteTheme.Primary
        ),
        border = BorderStroke(1.dp, NoteTheme.Primary.copy(alpha = 0.25f))
    )
}

// ─── Analytics ───────────────────────────────────────────────────────────────

/**
 * Logs phase3_smart_chip_tapped event to Firebase Analytics.
 * Only logs entity type, never the content itself (privacy boundary).
 */
private fun logSmartChipTapped(context: Context, entityType: String) {
    try {
        val analytics = FirebaseAnalytics.getInstance(context)
        val params = Bundle().apply {
            putString("entity_type", entityType)
        }
        analytics.logEvent("phase3_smart_chip_tapped", params)
    } catch (_: Exception) {
        // Analytics not critical — silent fail
    }
}

// ─── Intent Launchers ────────────────────────────────────────────────────────

private fun launchDial(context: Context, phone: String) {
    try {
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
        context.startActivity(intent)
    } catch (_: Exception) { }
}

private fun launchWhatsApp(context: Context, phone: String) {
    try {
        val cleanPhone = phone.removePrefix("+").trimStart('0')
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$cleanPhone"))
        context.startActivity(intent)
    } catch (_: Exception) {
        launchDial(context, phone)
    }
}

private fun launchSms(context: Context, phone: String) {
    try {
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$phone"))
        context.startActivity(intent)
    } catch (_: Exception) { }
}

private fun launchEmail(context: Context, email: String) {
    try {
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$email"))
        context.startActivity(intent)
    } catch (_: Exception) { }
}

private fun launchUrl(context: Context, url: String) {
    try {
        val customTabsIntent = CustomTabsIntent.Builder()
            .setShowTitle(true)
            .build()
        customTabsIntent.launchUrl(context, Uri.parse(url))
    } catch (_: Exception) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            context.startActivity(intent)
        } catch (_: Exception) { }
    }
}

private fun launchMaps(context: Context, address: String) {
    try {
        val encodedAddress = Uri.encode(address)
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=$encodedAddress"))
        context.startActivity(intent)
    } catch (_: Exception) { }
}

private fun launchCalendar(context: Context, dateText: String) {
    try {
        val intent = Intent(Intent.ACTION_INSERT).apply {
            data = android.provider.CalendarContract.Events.CONTENT_URI
            putExtra(android.provider.CalendarContract.Events.TITLE, "SwiftNote Reminder")
            putExtra(android.provider.CalendarContract.Events.DESCRIPTION, dateText)
        }
        context.startActivity(intent)
    } catch (_: Exception) { }
}
