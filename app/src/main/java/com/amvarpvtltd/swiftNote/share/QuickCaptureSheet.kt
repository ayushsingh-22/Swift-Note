package com.amvarpvtltd.swiftNote.share

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amvarpvtltd.swiftNote.R
import com.amvarpvtltd.swiftNote.ai.AITitleGenerator
import com.amvarpvtltd.swiftNote.ai.DetectedEntity
import com.amvarpvtltd.swiftNote.ai.DetectedReminder
import com.amvarpvtltd.swiftNote.ai.SmartEntityDetector
import com.amvarpvtltd.swiftNote.ai.SmartReminderAI
import com.amvarpvtltd.swiftNote.categories.Category
import com.amvarpvtltd.swiftNote.categories.CategoryManager
import com.amvarpvtltd.swiftNote.components.SmartActionChipRow
import com.amvarpvtltd.swiftNote.utils.AutoTitleGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ─── State Holder ──────────────────────────────────────────────────────────────

@Stable
private class QuickCaptureState(
    initialTitle: String,
    initialDescription: String,
    context: Context
) {
    var title by mutableStateOf(initialTitle)
    var description by mutableStateOf(initialDescription)
    var selectedCategory by mutableStateOf("")
    var titleFocused by mutableStateOf(false)
    var descFocused by mutableStateOf(false)
    var detectedEntities by mutableStateOf<List<DetectedEntity>>(emptyList())
    var detectedReminders by mutableStateOf<List<DetectedReminder>>(emptyList())
    var isAnalyzing by mutableStateOf(false)
    var hasAnalyzed by mutableStateOf(false)

    // AI title suggestion (LLM-powered, same key as AI Settings)
    var aiSuggestedTitle by mutableStateOf("")
    var isAiTitleLoading by mutableStateOf(false)

    val allCategories: List<Category> = CategoryManager.getAll(context)
    private val urlRegex = Regex("^https?://\\S+.*")

    val isUrl: Boolean get() = description.matches(urlRegex)
}

// ─── Color Palette ─────────────────────────────────────────────────────────────

@Stable
private data class CaptureColors(
    val primary: Color = Color(0xFF6366F1),
    val primaryLight: Color = Color(0xFF818CF8),
    val primaryContainer: Color = Color(0xFFEEF2FF),
    val onPrimary: Color = Color(0xFFFFFFFF),
    val successColor: Color = Color(0xFF10B981),
    val warningColor: Color = Color(0xFFF59E0B),
    val surfaceColor: Color,
    val bgVariant: Color,
    val onSurface: Color,
    val onSurfaceVariant: Color,
    val outline: Color
)

@Composable
private fun rememberCaptureColors(): CaptureColors {
    return CaptureColors(
        surfaceColor = MaterialTheme.colorScheme.surface,
        bgVariant = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        onSurface = MaterialTheme.colorScheme.onSurface,
        onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant,
        outline = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    )
}

// ─── Main Composable ───────────────────────────────────────────────────────────

@Composable
fun QuickCaptureSheet(
    initialTitle: String,
    initialDescription: String,
    onSave: (String, String, String) -> Unit,
    onEdit: (String, String, String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val state = remember { QuickCaptureState(initialTitle, initialDescription, context) }
    val colors = rememberCaptureColors()

    val reminderAnalysisInput = remember(state.title, state.description) {
        listOf(state.title, state.description)
            .filter { it.isNotBlank() }
            .joinToString(". ")
    }

    val hasReminderKeywords = remember(reminderAnalysisInput) {
        SmartReminderAI.getInstance(context).hasReminderKeywords(reminderAnalysisInput)
    }

    // Smart Analysis
    LaunchedEffect(state.description) {
        if (state.description.isNotBlank() && !state.hasAnalyzed) {
            state.isAnalyzing = true
            delay(300)
            state.detectedEntities = SmartEntityDetector.analyze(
                context = context,
                text = state.description,
                noteId = "share_preview_${System.currentTimeMillis()}"
            )
            if (hasReminderKeywords) {
                val result = SmartReminderAI.getInstance(context)
                    .analyzeTextForReminders(state.description, state.title.ifBlank { "Shared Note" })
                if (result.isSuccess) {
                    state.detectedReminders = result.getOrDefault(emptyList())
                }
            } else {
                state.detectedReminders = emptyList()
            }
            state.isAnalyzing = false
            state.hasAnalyzed = true
        }
    }

    val sheetScale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "scale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { onDismiss() },
        contentAlignment = Alignment.BottomCenter
    ) {
        AnimatedVisibility(
            visible = true,
            enter = slideInVertically(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow
                ),
                initialOffsetY = { it }
            ) + fadeIn(tween(200)),
            exit = slideOutVertically(tween(200)) { it } + fadeOut(tween(150))
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .scale(sheetScale)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) {},
                color = colors.surfaceColor,
                tonalElevation = 8.dp,
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
            ) {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    DragHandle(colors)
                    SheetContent(state, colors, hasReminderKeywords, context, onSave, onEdit, onDismiss)
                }
            }
        }
    }
}

// ─── Drag Handle ───────────────────────────────────────────────────────────────

@Composable
private fun DragHandle(colors: CaptureColors) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(40.dp)
                .height(4.dp)
                .clip(CircleShape)
                .background(colors.onSurfaceVariant.copy(alpha = 0.25f))
        )
    }
}

// ─── Sheet Content ─────────────────────────────────────────────────────────────

@Composable
private fun SheetContent(
    state: QuickCaptureState,
    colors: CaptureColors,
    hasReminderKeywords: Boolean,
    context: Context,
    onSave: (String, String, String) -> Unit,
    onEdit: (String, String, String) -> Unit,
    onDismiss: () -> Unit
) {
    // ── AI title suggestion (same LLM key as AI Settings / AddScreen) ──────────
    LaunchedEffect(state.description) {
        state.aiSuggestedTitle = ""
        // Only trigger when there's content to generate from
        if (state.description.isBlank()) return@LaunchedEffect

        // Shorter debounce than AddScreen (800ms) — share sheet is transient
        delay(800)

        val aiGen = AITitleGenerator.getInstance(context)
        if (!aiGen.isAvailable()) return@LaunchedEffect

        state.isAiTitleLoading = true
        try {
            val result = withContext(Dispatchers.IO) { aiGen.generate(state.description) }
            // Only surface the suggestion if it's non-empty and different from what's in the field
            if (result.isNotBlank() && result != state.title) {
                state.aiSuggestedTitle = result
            }
        } catch (_: Exception) {
            // Silent fallback — rule-based title used at save time
        } finally {
            state.isAiTitleLoading = false
        }
    }
    // ──────────────────────────────────────────────────────────────────────────

    Column(
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .padding(top = 14.dp, bottom = 24.dp)
    ) {
        SheetHeader(state, colors, hasReminderKeywords, onDismiss)
        DetectedRemindersSection(state, colors)
        SmartEntitiesSection(state, context)
        Spacer(modifier = Modifier.height(18.dp))
        TitleField(state, colors)
        AiTitleSuggestionChip(state, colors)
        Spacer(modifier = Modifier.height(16.dp))
        CategorySelector(state, colors)
        Spacer(modifier = Modifier.height(16.dp))
        ContentField(state, colors)
        Spacer(modifier = Modifier.height(24.dp))
        ActionButtons(state, colors, context, onSave, onEdit)
        SmartFeaturesHint(state, colors)
    }
}

// ─── Header ────────────────────────────────────────────────────────────────────

@Composable
private fun SheetHeader(
    state: QuickCaptureState,
    colors: CaptureColors,
    hasReminderKeywords: Boolean,
    onDismiss: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Brush.linearGradient(listOf(colors.primary, colors.primaryLight))),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.logo2),
                    contentDescription = "SwiftNote",
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "Save to SwiftNote",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = colors.onSurface
                )
                ContentTypeBadge(state, colors, hasReminderKeywords)
            }
        }
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.Close,
                contentDescription = "Dismiss",
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun ContentTypeBadge(
    state: QuickCaptureState,
    colors: CaptureColors,
    hasReminderKeywords: Boolean
) {
    val (typeIcon, typeText, typeColor) = when {
        state.detectedReminders.isNotEmpty() -> Triple(
            Icons.Outlined.Schedule, "Reminder detected", colors.successColor
        )
        state.isUrl -> Triple(
            Icons.Outlined.Link, "Link captured", colors.primary
        )
        hasReminderKeywords -> Triple(
            Icons.Outlined.Notifications, "May contain reminder", colors.warningColor
        )
        else -> Triple(
            Icons.AutoMirrored.Outlined.Notes, "Text captured", colors.onSurfaceVariant
        )
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 3.dp)
    ) {
        Icon(typeIcon, contentDescription = null, tint = typeColor, modifier = Modifier.size(12.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = typeText,
            style = MaterialTheme.typography.labelSmall,
            color = typeColor,
            fontWeight = FontWeight.Medium
        )
        if (state.isAnalyzing) {
            Spacer(modifier = Modifier.width(8.dp))
            CircularProgressIndicator(
                modifier = Modifier.size(10.dp),
                strokeWidth = 1.5.dp,
                color = colors.primary
            )
        }
    }
}

// ─── Detected Reminders Section ────────────────────────────────────────────────

@Composable
private fun DetectedRemindersSection(state: QuickCaptureState, colors: CaptureColors) {
    AnimatedVisibility(
        visible = state.detectedReminders.isNotEmpty(),
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut()
    ) {
        Column(modifier = Modifier.padding(top = 16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    tint = colors.successColor,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "SMART REMINDERS",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
                    fontWeight = FontWeight.Bold,
                    color = colors.successColor
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                state.detectedReminders.take(3).forEach { reminder ->
                    ReminderChip(reminder, colors.primary, colors.primaryContainer)
                }
            }
        }
    }
}

// ─── Smart Entities Section ────────────────────────────────────────────────────

@Composable
private fun SmartEntitiesSection(state: QuickCaptureState, context: Context) {
    AnimatedVisibility(
        visible = state.detectedEntities.isNotEmpty(),
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut()
    ) {
        SmartActionChipRow(
            entities = state.detectedEntities,
            modifier = Modifier.padding(top = 12.dp),
            onAddReminderClick = { _ ->
                Toast.makeText(context, "Reminder will be set after saving", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

// ─── AI Title Suggestion Chip ──────────────────────────────────────────────────

/**
 * Mirrors the suggestion chip from AddScreen — shows an LLM-generated title
 * below the title field when the title is blank. Tapping it fills the title.
 */
@Composable
private fun AiTitleSuggestionChip(state: QuickCaptureState, colors: CaptureColors) {
    // Show chip when:
    //  • AI is loading, OR
    //  • AI suggestion exists AND is different from what's currently in the title field
    //    (hides itself once the user taps it so the field matches the suggestion)
    val showChip = state.isAiTitleLoading ||
            (state.aiSuggestedTitle.isNotBlank() && state.aiSuggestedTitle != state.title)

    AnimatedVisibility(
        visible = showChip,
        enter = fadeIn(tween(200)) + slideInVertically(initialOffsetY = { -it / 4 }),
        exit = fadeOut(tween(150)) + slideOutVertically(targetOffsetY = { -it / 4 })
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp, bottom = 2.dp)
                .then(
                    if (state.aiSuggestedTitle.isNotBlank()) {
                        Modifier.clickable { state.title = state.aiSuggestedTitle }
                    } else Modifier
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (state.isAiTitleLoading && state.aiSuggestedTitle.isBlank()) {
                CircularProgressIndicator(
                    modifier = Modifier.size(12.dp),
                    strokeWidth = 1.5.dp,
                    color = colors.primary
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(13.dp)
                )
            }
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = when {
                    state.isAiTitleLoading && state.aiSuggestedTitle.isBlank() ->
                        "✨ Generating AI title…"
                    state.aiSuggestedTitle.isNotBlank() ->
                        "✨ Use \"${state.aiSuggestedTitle}\""
                    else -> ""
                },
                style = MaterialTheme.typography.labelSmall,
                color = colors.primary,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ─── Title Field ───────────────────────────────────────────────────────────────

@Composable
private fun TitleField(state: QuickCaptureState, colors: CaptureColors) {
    Text(
        text = "TITLE",
        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp),
        fontWeight = FontWeight.SemiBold,
        color = colors.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 8.dp)
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.bgVariant)
            .border(
                width = if (state.titleFocused) 1.5.dp else 1.dp,
                color = if (state.titleFocused) colors.primary else colors.outline,
                shape = RoundedCornerShape(14.dp)
            )
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        if (state.title.isEmpty()) {
            Text(
                "Note title (Mandatory)",
                style = TextStyle(fontSize = 15.sp, color = colors.onSurfaceVariant.copy(alpha = 0.5f))
            )
        }
        BasicTextField(
            value = state.title,
            onValueChange = { if (it.length <= 120) state.title = it },
            textStyle = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium, color = colors.onSurface),
            singleLine = true,
            cursorBrush = SolidColor(colors.primary),
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { state.titleFocused = it.isFocused }
        )
    }
}

// ─── Category Selector ─────────────────────────────────────────────────────────

@Composable
private fun CategorySelector(state: QuickCaptureState, colors: CaptureColors) {
    Text(
        text = "CATEGORY",
        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp),
        fontWeight = FontWeight.SemiBold,
        color = colors.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 8.dp)
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CategoryChip(
            label = "None",
            isSelected = state.selectedCategory.isBlank(),
            chipColor = colors.primary,
            bgVariant = colors.bgVariant,
            outline = colors.outline,
            onSurfaceVariant = colors.onSurfaceVariant,
            onClick = { state.selectedCategory = "" }
        )
        state.allCategories.forEach { category ->
            CategoryChipWithDot(
                label = category.name,
                isSelected = state.selectedCategory == category.name,
                chipColor = Color(category.colorHex),
                bgVariant = colors.bgVariant,
                outline = colors.outline,
                onSurfaceVariant = colors.onSurfaceVariant,
                onClick = { state.selectedCategory = category.name }
            )
        }
    }
}

@Composable
private fun CategoryChip(
    label: String,
    isSelected: Boolean,
    chipColor: Color,
    bgVariant: Color,
    outline: Color,
    onSurfaceVariant: Color,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (isSelected) chipColor.copy(alpha = 0.12f) else bgVariant,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = if (isSelected) chipColor else outline,
                shape = RoundedCornerShape(10.dp)
            )
            .clickable(onClick = onClick)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) chipColor else onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun CategoryChipWithDot(
    label: String,
    isSelected: Boolean,
    chipColor: Color,
    bgVariant: Color,
    outline: Color,
    onSurfaceVariant: Color,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (isSelected) chipColor.copy(alpha = 0.12f) else bgVariant,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = if (isSelected) chipColor else outline,
                shape = RoundedCornerShape(10.dp)
            )
            .clickable(onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(chipColor)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) chipColor else onSurfaceVariant
            )
        }
    }
}

// ─── Content Field ─────────────────────────────────────────────────────────────

@Composable
private fun ContentField(state: QuickCaptureState, colors: CaptureColors) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "CONTENT",
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp),
            fontWeight = FontWeight.SemiBold,
            color = colors.onSurfaceVariant
        )
        Text(
            text = "${state.description.length}/2000",
            style = MaterialTheme.typography.labelSmall,
            color = if (state.description.length > 1800) colors.warningColor
            else colors.onSurfaceVariant.copy(alpha = 0.5f),
            fontWeight = if (state.description.length > 1800) FontWeight.Medium else FontWeight.Normal
        )
    }
    Spacer(modifier = Modifier.height(8.dp))
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 100.dp, max = 180.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(colors.bgVariant)
            .border(
                width = if (state.descFocused) 1.5.dp else 1.dp,
                color = if (state.descFocused) colors.primary else colors.outline,
                shape = RoundedCornerShape(14.dp)
            )
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        if (state.description.isEmpty()) {
            Text(
                "Note content…",
                style = TextStyle(fontSize = 14.sp, color = colors.onSurfaceVariant.copy(alpha = 0.5f))
            )
        }
        BasicTextField(
            value = state.description,
            onValueChange = { if (it.length <= 2000) state.description = it },
            textStyle = TextStyle(fontSize = 14.sp, color = colors.onSurface, lineHeight = 22.sp),
            maxLines = 8,
            cursorBrush = SolidColor(colors.primary),
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { state.descFocused = it.isFocused }
        )
    }
}

// ─── Action Buttons ────────────────────────────────────────────────────────────

@Composable
private fun ActionButtons(
    state: QuickCaptureState,
    colors: CaptureColors,
    context: Context,
    onSave: (String, String, String) -> Unit,
    onEdit: (String, String, String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedButton(
            onClick = { onEdit(state.title, state.description, state.selectedCategory) },
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 52.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.primary),
            border = androidx.compose.foundation.BorderStroke(1.5.dp, colors.primary.copy(alpha = 0.4f))
        ) {
            Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Edit", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        }

        Button(
            onClick = {
                // Priority: user-typed title → AI-suggested → rule-based → error
                val effectiveTitle = when {
                    state.title.isNotBlank() -> state.title
                    state.aiSuggestedTitle.isNotBlank() -> state.aiSuggestedTitle
                    else -> AutoTitleGenerator.generate(state.description)
                }
                if (effectiveTitle.isBlank()) {
                    Toast.makeText(context, "Please add a title or some content", Toast.LENGTH_SHORT).show()
                } else {
                    onSave(effectiveTitle, state.description, state.selectedCategory)
                }
            },
            modifier = Modifier
                .weight(2f)
                .heightIn(min = 52.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.primary,
                contentColor = colors.onPrimary
            )
        ) {
            Icon(Icons.Outlined.Save, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Save Note", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
    }
}

// ─── Smart Features Hint ───────────────────────────────────────────────────────

@Composable
private fun SmartFeaturesHint(state: QuickCaptureState, colors: CaptureColors) {
    if (state.detectedReminders.isNotEmpty() || state.detectedEntities.isNotEmpty()) {
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "\uD83D\uDCA1 Smart actions will be available after saving",
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

// ─── Reminder Chip ─────────────────────────────────────────────────────────────

@Composable
private fun ReminderChip(
    reminder: DetectedReminder,
    primaryColor: Color,
    primaryContainer: Color
) {
    val dateFormat = remember { SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()) }
    val timeText = remember(reminder.reminderDateTime) {
        dateFormat.format(Date(reminder.reminderDateTime))
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = primaryContainer,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.Schedule,
                contentDescription = null,
                tint = primaryColor,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = reminder.extractedText.take(20),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = primaryColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = timeText,
                    style = MaterialTheme.typography.labelSmall,
                    color = primaryColor.copy(alpha = 0.7f)
                )
            }
        }
    }
}
