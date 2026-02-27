package com.amvarpvtltd.swiftNote.components

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.view.ContextThemeWrapper
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.amvarpvtltd.swiftNote.design.NoteTheme
import com.amvarpvtltd.swiftNote.reminders.ReminderPreset
import com.amvarpvtltd.swiftNote.reminders.ReminderRequest
import com.amvarpvtltd.swiftNote.utils.Constants
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(
    ExperimentalMaterial3Api::class
)
@Composable
fun ReminderBottomSheet(
    isVisible: Boolean,
    noteId: String,
    noteTitle: String,
    noteDescription: String = "",
    onDismiss: () -> Unit,
    onReminderSet: (ReminderRequest) -> Unit
) {
    val context =
        LocalContext.current
    var selectedPreset by remember {
        mutableStateOf<ReminderPreset?>(
            null
        )
    }
    var customDateTime by remember {
        mutableStateOf<Long?>(
            null
        )
    }
    var showDatePicker by remember {
        mutableStateOf(
            false
        )
    }
    var showTimePicker by remember {
        mutableStateOf(
            false
        )
    }
    var selectedDate by remember {
        mutableStateOf<Calendar?>(
            null
        )
    }

    if (!isVisible) return

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
                    .width(
                        40.dp
                    )
                    .height(
                        4.dp
                    )
                    .clip(
                        RoundedCornerShape(
                            2.dp
                        )
                    )
                    .background(
                        NoteTheme.OnSurfaceVariant.copy(
                            alpha = 0.3f
                        )
                    )
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = Constants.PADDING_LARGE.dp
                )
                .padding(
                    bottom = Constants.PADDING_LARGE.dp
                )
        ) {
            // Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(
                        RoundedCornerShape(
                            Constants.CORNER_RADIUS_LARGE.dp
                        )
                    )
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                NoteTheme.Primary.copy(
                                    alpha = 0.1f
                                ),
                                NoteTheme.Secondary.copy(
                                    alpha = 0.05f
                                )
                            )
                        )
                    )
                    .padding(
                        Constants.PADDING_MEDIUM.dp
                    )
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(
                                    40.dp
                                )
                                .clip(
                                    CircleShape
                                )
                                .background(
                                    NoteTheme.Primary.copy(
                                        alpha = 0.15f
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Outlined.NotificationsActive,
                                contentDescription = null,
                                tint = NoteTheme.Primary,
                                modifier = Modifier.size(
                                    20.dp
                                )
                            )
                        }
                        Spacer(
                            modifier = Modifier.width(
                                Constants.PADDING_MEDIUM.dp
                            )
                        )
                        Column {
                            Text(
                                text = "Set Reminder",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = NoteTheme.OnSurface
                            )
                            Text(
                                text = "Never miss important notes",
                                style = MaterialTheme.typography.bodySmall,
                                color = NoteTheme.OnSurfaceVariant
                            )
                        }
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .clip(
                                CircleShape
                            )
                            .background(
                                NoteTheme.OnSurfaceVariant.copy(
                                    alpha = 0.1f
                                )
                            )
                    ) {
                        Icon(
                            Icons.Outlined.Close,
                            contentDescription = "Close",
                            tint = NoteTheme.OnSurfaceVariant,
                            modifier = Modifier.size(
                                20.dp
                            )
                        )
                    }
                }
            }

            Spacer(
                modifier = Modifier.height(
                    Constants.PADDING_LARGE.dp
                )
            )

            // Note preview
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = NoteTheme.PrimaryContainer.copy(
                        alpha = 0.08f
                    )
                ),
                shape = RoundedCornerShape(
                    Constants.CORNER_RADIUS_LARGE.dp
                ),
                border = BorderStroke(
                    1.dp,
                    NoteTheme.Primary.copy(
                        alpha = 0.1f
                    )
                )
            ) {
                Column(
                    modifier = Modifier.padding(
                        Constants.PADDING_LARGE.dp
                    )
                ) {
                    Row {
                        Box(
                            modifier = Modifier
                                .size(
                                    8.dp
                                )
                                .clip(
                                    CircleShape
                                )
                                .background(
                                    NoteTheme.Primary
                                )
                        )
                        Spacer(
                            modifier = Modifier.width(
                                Constants.PADDING_MEDIUM.dp
                            )
                        )
                        Column(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = noteTitle,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = NoteTheme.OnSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (noteDescription.isNotEmpty()) {
                                Spacer(
                                    modifier = Modifier.height(
                                        6.dp
                                    )
                                )
                                Text(
                                    text = noteDescription.take(
                                        100
                                    ) + if (noteDescription.length > 100) "..." else "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = NoteTheme.OnSurfaceVariant,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            Spacer(
                modifier = Modifier.height(
                    Constants.PADDING_LARGE.dp
                )
            )

            // Quick options header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Quick Options",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = NoteTheme.OnSurface
                )
                Spacer(
                    modifier = Modifier.width(
                        Constants.PADDING_SMALL.dp
                    )
                )
                // Use a full-width divider instead of weight to avoid internal API access
                Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(
                            1.dp
                        )
                        .background(
                            NoteTheme.OnSurfaceVariant.copy(
                                alpha = 0.3f
                            )
                        )
                )
            }

            Spacer(
                modifier = Modifier.height(
                    Constants.PADDING_LARGE.dp
                )
            )

            // Presets
            val presets =
                listOf(
                    ReminderPreset.TEN_MINUTES,
                    ReminderPreset.THIRTY_MINUTES,
                    ReminderPreset.ONE_HOUR,
                    ReminderPreset.ONE_DAY
                )
            presets.chunked(
                2
            )
                .forEach { rowPresets ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(
                            Constants.PADDING_MEDIUM.dp
                        )
                    ) {
                        rowPresets.forEach { preset ->
                            Box(
                                modifier = Modifier.weight(
                                    1f
                                )
                            ) {
                                PresetButton(
                                    preset = preset,
                                    isSelected = selectedPreset == preset,
                                    onClick = {
                                        selectedPreset =
                                            preset; customDateTime =
                                        null
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                        if (rowPresets.size == 1) Spacer(
                            modifier = Modifier.weight(
                                1f
                            )
                        )
                    }
                    Spacer(
                        modifier = Modifier.height(
                            Constants.PADDING_MEDIUM.dp
                        )
                    )
                }

            // Custom option
            val isCustomSelected =
                selectedPreset == ReminderPreset.CUSTOM
            val customButtonScale by animateFloatAsState(
                targetValue = if (isCustomSelected) 1.02f else 1f,
                animationSpec = spring(
                    dampingRatio = 0.7f
                )
            )

            OutlinedButton(
                onClick = {
                    selectedPreset =
                        ReminderPreset.CUSTOM; showDatePicker =
                    true
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .scale(
                        customButtonScale
                    )
                    .height(
                        64.dp
                    ),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = if (isCustomSelected) NoteTheme.Secondary.copy(
                        alpha = 0.15f
                    ) else NoteTheme.Surface
                ),
                border = BorderStroke(
                    if (isCustomSelected) 2.dp else 1.dp,
                    if (isCustomSelected) NoteTheme.Secondary
                    else NoteTheme.OnSurfaceVariant.copy(
                        alpha = 0.3f
                    )
                ),
                shape = RoundedCornerShape(
                    Constants.CORNER_RADIUS_LARGE.dp
                )
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(
                        Constants.PADDING_MEDIUM.dp
                    )
                ) {
                    Box(
                        modifier = Modifier
                            .size(
                                36.dp
                            )
                            .clip(
                                CircleShape
                            )
                            .background(
                                if (isCustomSelected) NoteTheme.Secondary.copy(
                                    alpha = 0.2f
                                ) else NoteTheme.OnSurfaceVariant.copy(
                                    alpha = 0.1f
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Outlined.Schedule,
                            contentDescription = null,
                            modifier = Modifier.size(
                                20.dp
                            )
                        )
                    }
                    // Use fillMaxWidth with a fraction to approximate remaining space without weight
                    Column(
                        modifier = Modifier.fillMaxWidth(
                            0.75f
                        )
                    ) {
                        if (customDateTime != null) {
                            Text(
                                text = "Custom Time Set",
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = formatDateTime(
                                    customDateTime!!
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = NoteTheme.Secondary
                            )
                        } else {
                            Text(
                                text = "Custom Date & Time",
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = "Select specific date and time",
                                style = MaterialTheme.typography.bodySmall,
                                color = NoteTheme.OnSurfaceVariant
                            )
                        }
                    }
                    AnimatedVisibility(
                        visible = customDateTime != null,
                        enter = scaleIn() + fadeIn(),
                        exit = scaleOut() + fadeOut()
                    ) {
                        Icon(
                            Icons.Outlined.CheckCircle,
                            contentDescription = null,
                            tint = NoteTheme.Secondary,
                            modifier = Modifier.size(
                                20.dp
                            )
                        )
                    }
                }
            }

            Spacer(
                modifier = Modifier.height(
                    Constants.PADDING_XL.dp
                )
            )

            // Set reminder button
            val isButtonEnabled =
                selectedPreset != null && (selectedPreset != ReminderPreset.CUSTOM || customDateTime != null)
            val buttonColor by animateColorAsState(
                targetValue = if (isButtonEnabled) NoteTheme.Primary
                else NoteTheme.OnSurfaceVariant.copy(
                    alpha = 0.3f
                ),
                animationSpec = tween(
                    300
                )
            )

            Button(
                onClick = {
                    // Get the reminder time either from preset or custom date/time
                    val reminderTime = when {
                        selectedPreset != null -> {
                            val calendar = Calendar.getInstance()
                            when (selectedPreset) {
                                ReminderPreset.TEN_MINUTES -> calendar.apply { add(Calendar.MINUTE, 10) }
                                ReminderPreset.THIRTY_MINUTES -> calendar.apply { add(Calendar.MINUTE, 30) }
                                ReminderPreset.ONE_HOUR -> calendar.apply { add(Calendar.HOUR, 1) }
                                ReminderPreset.ONE_DAY -> calendar.apply { add(Calendar.DAY_OF_MONTH, 1) }
                                ReminderPreset.CUSTOM -> calendar.apply {
                                    if (customDateTime != null) {
                                        timeInMillis = customDateTime!!
                                    }
                                }
                                 else -> calendar // This branch is needed for exhaustive when expression
                            }.timeInMillis
                        }
                        customDateTime != null -> customDateTime!!
                        else -> null
                    }

                    // Create and set reminder if time is valid
                    if (reminderTime != null && reminderTime > System.currentTimeMillis()) {
                        val preset = selectedPreset ?: ReminderPreset.CUSTOM
                        val request = ReminderRequest(
                            noteId = noteId,
                            noteTitle = noteTitle,
                            noteDescription = noteDescription,
                            preset = preset,
                            customDateTime = if (preset == ReminderPreset.CUSTOM) reminderTime else null
                        )
                        onReminderSet(request)

                        // Show notification feedback using our new in-app notification component
                        NotificationHelper.showSuccess(
                            title = "Reminder Set",
                            message = "Reminder for \"${noteTitle.take(20)}${if (noteTitle.length > 20) "..." else ""}\" is set"
                        )

                        onDismiss()
                    } else {
                        // Show error notification using our new in-app notification component
                        NotificationHelper.showError(
                            title = "Invalid Time",
                            message = "Please select a valid future time for the reminder"
                        )
                    }
                },
                enabled = isButtonEnabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(
                        56.dp
                    ),
                colors = ButtonDefaults.buttonColors(
                    containerColor = buttonColor
                ),
                shape = RoundedCornerShape(
                    Constants.CORNER_RADIUS_LARGE.dp
                )
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(
                        Constants.PADDING_MEDIUM.dp
                    )
                ) {
                    Icon(
                        Icons.Outlined.NotificationsActive,
                        contentDescription = null,
                        modifier = Modifier.size(
                            Constants.ICON_SIZE_MEDIUM.dp
                        )
                    )
                    Text(
                        text = "Set Reminder",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }

            Spacer(
                modifier = Modifier.height(
                    Constants.PADDING_MEDIUM.dp
                )
            )
        }
    }

    // DatePicker using themed context
    if (showDatePicker) {
        val calendar =
            Calendar.getInstance()
        val isDark =
            isSystemInDarkTheme()
        val datePickerTheme =
            if (isDark) com.amvarpvtltd.swiftNote.R.style.ThemeOverlay_SwiftNote_DateTimePicker_Dark
            else com.amvarpvtltd.swiftNote.R.style.ThemeOverlay_SwiftNote_DateTimePicker_Light
        val themedContext =
            ContextThemeWrapper(
                context,
                datePickerTheme
            )
        val datePickerDialog =
            DatePickerDialog(
                themedContext,
                { _, year, month, dayOfMonth ->
                    val selectedCalendar =
                        Calendar.getInstance()
                            .apply {
                                set(
                                    year,
                                    month,
                                    dayOfMonth
                                )
                            }
                    selectedDate =
                        selectedCalendar
                    showDatePicker =
                        false
                    showTimePicker =
                        true
                },
                calendar.get(
                    Calendar.YEAR
                ),
                calendar.get(
                    Calendar.MONTH
                ),
                calendar.get(
                    Calendar.DAY_OF_MONTH
                )
            )
        datePickerDialog.datePicker.minDate =
            System.currentTimeMillis()
        datePickerDialog.setOnDismissListener {
            showDatePicker =
                false
        }
        LaunchedEffect(
            showDatePicker
        ) { if (showDatePicker) datePickerDialog.show() }
    }

    // TimePicker using themed context
    if (showTimePicker && selectedDate != null) {
        val calendar =
            Calendar.getInstance()
        val isDarkTime =
            isSystemInDarkTheme()
        val timePickerTheme =
            if (isDarkTime) com.amvarpvtltd.swiftNote.R.style.ThemeOverlay_SwiftNote_DateTimePicker_Dark
            else com.amvarpvtltd.swiftNote.R.style.ThemeOverlay_SwiftNote_DateTimePicker_Light
        val themedTimeContext =
            ContextThemeWrapper(
                context,
                timePickerTheme
            )
        val timePickerDialog =
            TimePickerDialog(
                themedTimeContext,
                { _, hourOfDay, minute ->
                    selectedDate?.let { date ->
                        date.set(
                            Calendar.HOUR_OF_DAY,
                            hourOfDay
                        )
                        date.set(
                            Calendar.MINUTE,
                            minute
                        )
                        date.set(
                            Calendar.SECOND,
                            0
                        )
                        customDateTime =
                            date.timeInMillis
                    }
                    showTimePicker =
                        false
                },
                calendar.get(
                    Calendar.HOUR_OF_DAY
                ),
                calendar.get(
                    Calendar.MINUTE
                ),
                false
            )
        timePickerDialog.setOnDismissListener {
            showTimePicker =
                false
        }
        LaunchedEffect(
            showTimePicker
        ) { if (showTimePicker) timePickerDialog.show() }
    }
}

@Composable
private fun PresetButton(
    preset: ReminderPreset,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val icon =
        when (preset) {
            ReminderPreset.TEN_MINUTES -> Icons.Outlined.Timer
            ReminderPreset.THIRTY_MINUTES -> Icons.Outlined.AccessTime
            ReminderPreset.ONE_HOUR -> Icons.Outlined.HourglassEmpty
            ReminderPreset.ONE_DAY -> Icons.Outlined.CalendarToday
            else -> Icons.Outlined.Schedule
        }

    val buttonScale by animateFloatAsState(
        targetValue = if (isSelected) 1.05f else 1f,
        animationSpec = spring(
            dampingRatio = 0.7f
        )
    )
    val containerColor by animateColorAsState(
        targetValue = if (isSelected) NoteTheme.Primary.copy(
            alpha = 0.15f
        ) else NoteTheme.Surface,
        animationSpec = tween(
            200
        )
    )

    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .scale(
                buttonScale
            )
            .height(84.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = containerColor
        )
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(
                6.dp
            ),
            modifier = Modifier.padding(
                vertical = Constants.PADDING_SMALL.dp
            )
        ) {
            Box(
                modifier = Modifier
                    .size(
                        if (isSelected) 32.dp else 28.dp
                    )
                    .clip(
                        CircleShape
                    )
                    .background(
                        if (isSelected) NoteTheme.Primary.copy(
                            alpha = 0.2f
                        ) else NoteTheme.OnSurfaceVariant.copy(
                            alpha = 0.1f
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(
                        if (isSelected) 18.dp else 16.dp
                    )
                )
            }
            Text(
                text = preset.label,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun formatDateTime(
    timestamp: Long
): String {
    val formatter =
        SimpleDateFormat(
            "MMM dd, HH:mm",
            Locale.getDefault()
        )
    return formatter.format(
        Date(timestamp)
    )
}