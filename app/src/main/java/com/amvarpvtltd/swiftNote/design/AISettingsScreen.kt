package com.amvarpvtltd.swiftNote.design

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.amvarpvtltd.swiftNote.ai.GeminiReminderParser
import com.amvarpvtltd.swiftNote.ai.KeyValidationResult
import com.amvarpvtltd.swiftNote.ai.LlmProvider
import com.amvarpvtltd.swiftNote.ai.LlmService
import com.amvarpvtltd.swiftNote.components.IconActionButton
import com.amvarpvtltd.swiftNote.components.NoteScreenBackground
import com.amvarpvtltd.swiftNote.security.GeminiApiKey
import com.amvarpvtltd.swiftNote.security.GeminiKeyManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AISettingsScreen(navController: NavHostController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val hapticFeedback = LocalHapticFeedback.current

    var keys by remember { mutableStateOf(GeminiKeyManager.getAllKeys(context)) }
    var activeKeyId by remember { mutableStateOf(GeminiKeyManager.getActiveKeyId(context)) }
    var isEnabled by remember { mutableStateOf(GeminiKeyManager.isEnabled(context)) }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingKey by remember { mutableStateOf<GeminiApiKey?>(null) }
    var deletingKey by remember { mutableStateOf<GeminiApiKey?>(null) }

    // Animation states
    var headerVisible by remember { mutableStateOf(false) }
    var contentVisible by remember { mutableStateOf(false) }

    // Pulsing animation for the AI icon
    val infiniteTransition = rememberInfiniteTransition(label = "ai_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )

    LaunchedEffect(Unit) {
        delay(100)
        headerVisible = true
        delay(200)
        contentVisible = true
    }

    fun refreshState() {
        keys = GeminiKeyManager.getAllKeys(context)
        activeKeyId = GeminiKeyManager.getActiveKeyId(context)
        isEnabled = GeminiKeyManager.isEnabled(context)
    }

    NoteScreenBackground {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            "AI Settings",
                            color = NoteTheme.OnSurface,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconActionButton(
                            onClick = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                navController.popBackStack()
                            },
                            icon = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            containerColor = NoteTheme.Primary.copy(alpha = 0.1f),
                            contentColor = NoteTheme.Primary,
                            modifier = Modifier.padding(start = 12.dp)
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            },
            containerColor = Color.Transparent
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // ──────────── Animated Hero Section ────────────
                AnimatedVisibility(
                    visible = headerVisible,
                    enter = slideInVertically(
                        initialOffsetY = { -it / 3 },
                        animationSpec = tween(500, easing = EaseOutCubic)
                    ) + fadeIn(tween(500))
                ) {
                    AIHeroCard(pulseScale = pulseScale, glowAlpha = glowAlpha, isEnabled = isEnabled)
                }

                // ──────────── Content ────────────
                AnimatedVisibility(
                    visible = contentVisible,
                    enter = slideInVertically(
                        initialOffsetY = { it / 2 },
                        animationSpec = tween(600, easing = EaseOutCubic)
                    ) + fadeIn(tween(600))
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        // Enable/Disable Card
                        if (keys.isNotEmpty()) {
                            AIToggleCard(
                                isEnabled = isEnabled,
                                onToggle = {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                    GeminiKeyManager.setEnabled(context, it)
                                    isEnabled = it
                                    GeminiReminderParser.invalidate()
                                }
                            )
                        }

                        // API Keys Section
                        APIKeysSection(
                            keys = keys,
                            activeKeyId = activeKeyId,
                            onAddKey = { showAddDialog = true },
                            onSetActive = { keyId ->
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                GeminiKeyManager.setActiveKeyId(context, keyId)
                                GeminiReminderParser.invalidate()
                                refreshState()
                            },
                            onEdit = { editingKey = it },
                            onDelete = { deletingKey = it }
                        )

                        // How-to Card
                        HowToGetKeyCard(
                            onOpenGeminiLink = {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/apikey"))
                                context.startActivity(intent)
                            },
                            onOpenGroqLink = {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://console.groq.com/keys"))
                                context.startActivity(intent)
                            }
                        )

                        // Privacy Card
                        PrivacyInfoCard()

                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }

    // ──────────── Dialogs ────────────
    if (showAddDialog) {
        AddApiKeyDialog(
            onDismiss = { showAddDialog = false },
            onSave = { label, apiKey, provider ->
                scope.launch {
                    GeminiKeyManager.addApiKey(context, label, apiKey, provider.name)
                    GeminiReminderParser.invalidate()
                    LlmService.invalidate()
                    refreshState()
                    showAddDialog = false
                }
            }
        )
    }

    editingKey?.let { key ->
        EditKeyLabelDialog(
            currentLabel = key.label,
            onDismiss = { editingKey = null },
            onSave = { newLabel ->
                GeminiKeyManager.updateKeyLabel(context, key.id, newLabel)
                refreshState()
                editingKey = null
            }
        )
    }

    deletingKey?.let { key ->
        AlertDialog(
            onDismissRequest = { deletingKey = null },
            title = { Text("Remove API Key?", color = NoteTheme.OnSurface) },
            text = {
                Text(
                    "Remove \"${key.label}\"? You can always add it back later.",
                    color = NoteTheme.OnSurfaceVariant
                )
            },
            containerColor = NoteTheme.Surface,
            confirmButton = {
                TextButton(onClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    GeminiKeyManager.removeApiKey(context, key.id)
                    GeminiReminderParser.invalidate()
                    refreshState()
                    deletingKey = null
                }) {
                    Text("Remove", color = NoteTheme.Error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingKey = null }) {
                    Text("Cancel", color = NoteTheme.OnSurfaceVariant)
                }
            }
        )
    }
}

// ═══════════════════════════════════════════════════════════════════
// HERO CARD — Animated gradient header with pulsing AI icon
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun AIHeroCard(pulseScale: Float, glowAlpha: Float, isEnabled: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(NoteTheme.Radius.xl.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            NoteTheme.Primary.copy(alpha = 0.15f),
                            NoteTheme.PrimaryContainer.copy(alpha = 0.6f),
                            NoteTheme.Primary.copy(alpha = 0.08f)
                        )
                    ),
                    shape = RoundedCornerShape(NoteTheme.Radius.xl.dp)
                )
                .padding(24.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Pulsing AI orb
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(64.dp)
                ) {
                    // Outer glow
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .scale(pulseScale)
                            .background(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        NoteTheme.Primary.copy(alpha = glowAlpha * 0.4f),
                                        Color.Transparent
                                    )
                                ),
                                shape = CircleShape
                            )
                    )
                    // Inner icon circle
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(48.dp)
                            .background(
                                color = NoteTheme.Primary.copy(alpha = 0.15f),
                                shape = CircleShape
                            )
                    ) {
                        Text("✨", fontSize = 24.sp)
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Smart AI",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = NoteTheme.OnSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Gemini & Groq — understands Hindi, Hinglish & complex reminders",
                        style = MaterialTheme.typography.bodySmall,
                        color = NoteTheme.OnSurfaceVariant,
                        lineHeight = 16.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    // Status pill
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (isEnabled) NoteTheme.Success.copy(alpha = 0.15f)
                        else NoteTheme.OnSurfaceVariant.copy(alpha = 0.1f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(
                                        if (isEnabled) NoteTheme.Success else NoteTheme.OnSurfaceVariant,
                                        CircleShape
                                    )
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                if (isEnabled) "Active" else "Inactive",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isEnabled) NoteTheme.Success else NoteTheme.OnSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// TOGGLE CARD
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun AIToggleCard(isEnabled: Boolean, onToggle: (Boolean) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(NoteTheme.Radius.lg.dp),
        colors = CardDefaults.cardColors(containerColor = NoteTheme.Surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        color = if (isEnabled) NoteTheme.Primary.copy(alpha = 0.12f)
                        else NoteTheme.OnSurfaceVariant.copy(alpha = 0.08f),
                        shape = RoundedCornerShape(12.dp)
                    )
            ) {
                Icon(
                    Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = if (isEnabled) NoteTheme.Primary else NoteTheme.OnSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Enable AI",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = NoteTheme.OnSurface
                )
                Text(
                    if (isEnabled) "AI analyzes complex text for reminders & titles"
                    else "Using on-device detection only",
                    style = MaterialTheme.typography.bodySmall,
                    color = NoteTheme.OnSurfaceVariant
                )
            }

            Switch(
                checked = isEnabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedTrackColor = NoteTheme.Primary,
                    checkedThumbColor = NoteTheme.OnPrimary,
                    uncheckedTrackColor = NoteTheme.Outline,
                    uncheckedThumbColor = NoteTheme.OnSurfaceVariant
                )
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// API KEYS SECTION
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun APIKeysSection(
    keys: List<GeminiApiKey>,
    activeKeyId: String?,
    onAddKey: () -> Unit,
    onSetActive: (String) -> Unit,
    onEdit: (GeminiApiKey) -> Unit,
    onDelete: (GeminiApiKey) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(NoteTheme.Radius.lg.dp),
        colors = CardDefaults.cardColors(containerColor = NoteTheme.Surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Key,
                    null,
                    tint = NoteTheme.Primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "API Keys",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = NoteTheme.OnSurface,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    onClick = onAddKey,
                    shape = RoundedCornerShape(20.dp),
                    color = NoteTheme.Primary.copy(alpha = 0.1f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Add, null,
                            tint = NoteTheme.Primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "Add",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = NoteTheme.Primary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (keys.isEmpty()) {
                EmptyKeysState(onAddKey = onAddKey)
            } else {
                keys.forEachIndexed { index, key ->
                    if (index > 0) Spacer(modifier = Modifier.height(10.dp))
                    AnimatedApiKeyItem(
                        key = key,
                        isActive = key.id == activeKeyId,
                        onSetActive = { onSetActive(key.id) },
                        onEdit = { onEdit(key) },
                        onDelete = { onDelete(key) }
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyKeysState(onAddKey: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (isPressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.7f),
        label = "press"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(RoundedCornerShape(NoteTheme.Radius.md.dp))
            .background(NoteTheme.SurfaceVariant.copy(alpha = 0.5f))
            .clickable(interactionSource = interactionSource, indication = null) { onAddKey() }
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(56.dp)
                    .background(NoteTheme.Primary.copy(alpha = 0.1f), CircleShape)
            ) {
                Icon(
                    Icons.Default.Key, null,
                    modifier = Modifier.size(28.dp),
                    tint = NoteTheme.Primary
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                "No API keys added yet",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = NoteTheme.OnSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Tap to add your free Gemini or Groq API key",
                style = MaterialTheme.typography.bodySmall,
                color = NoteTheme.OnSurfaceVariant
            )
        }
    }
}

@Composable
private fun AnimatedApiKeyItem(
    key: GeminiApiKey,
    isActive: Boolean,
    onSetActive: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (isPressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = 0.7f),
        label = "card_press"
    )
    val borderColor by animateColorAsState(
        if (isActive) NoteTheme.Primary.copy(alpha = 0.4f) else NoteTheme.Outline.copy(alpha = 0.5f),
        animationSpec = tween(300),
        label = "border"
    )
    val bgColor by animateColorAsState(
        if (isActive) NoteTheme.Primary.copy(alpha = 0.06f) else NoteTheme.SurfaceVariant.copy(alpha = 0.3f),
        animationSpec = tween(300),
        label = "bg"
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .animateContentSize(),
        shape = RoundedCornerShape(NoteTheme.Radius.md.dp),
        color = bgColor,
        border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(
            width = 1.dp,
            brush = Brush.linearGradient(listOf(borderColor, borderColor))
        ),
        onClick = onSetActive,
        interactionSource = interactionSource
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (isActive) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
                    contentDescription = if (isActive) "Active" else "Tap to activate",
                    tint = if (isActive) NoteTheme.Primary else NoteTheme.OnSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            key.label,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = NoteTheme.OnSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (isActive) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = NoteTheme.Primary.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    "ACTIVE",
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = NoteTheme.Primary,
                                    fontSize = 9.sp
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Provider badge
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = if (key.llmProvider == LlmProvider.GEMINI)
                                Color(0xFF1A73E8).copy(alpha = 0.1f)
                            else Color(0xFFF97316).copy(alpha = 0.1f)
                        ) {
                            Text(
                                key.llmProvider.displayName,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                color = if (key.llmProvider == LlmProvider.GEMINI)
                                    Color(0xFF1A73E8) else Color(0xFFF97316),
                                fontSize = 9.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            key.maskedKey,
                            style = MaterialTheme.typography.bodySmall,
                            color = NoteTheme.OnSurfaceVariant,
                            fontSize = 12.sp
                        )
                    }
                }

                IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.Edit, "Edit",
                        modifier = Modifier.size(16.dp),
                        tint = NoteTheme.OnSurfaceVariant
                    )
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.Delete, "Delete",
                        modifier = Modifier.size(16.dp),
                        tint = NoteTheme.Error.copy(alpha = 0.7f)
                    )
                }
            }

            if (key.usageCount > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.padding(start = 32.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.BarChart, null,
                        modifier = Modifier.size(12.dp),
                        tint = NoteTheme.OnSurfaceVariant.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    val lastUsedStr = if (key.lastUsed > 0) {
                        SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(key.lastUsed))
                    } else "Never"
                    Text(
                        "${key.usageCount} calls · Last: $lastUsedStr",
                        style = MaterialTheme.typography.labelSmall,
                        color = NoteTheme.OnSurfaceVariant.copy(alpha = 0.7f),
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// HOW-TO CARD
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun HowToGetKeyCard(onOpenGeminiLink: () -> Unit, onOpenGroqLink: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(NoteTheme.Radius.lg.dp),
        colors = CardDefaults.cardColors(containerColor = NoteTheme.Surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(36.dp)
                        .background(NoteTheme.Warning.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
                ) {
                    Text("💡", fontSize = 18.sp)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    "How to get a free API key",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = NoteTheme.OnSurface
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                "Gemini (Google):",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = NoteTheme.OnSurface
            )
            val geminiSteps = listOf(
                "Open Google AI Studio",
                "Sign in with your Google account",
                "Click \"Create API Key\" → Copy & paste here"
            )
            geminiSteps.forEachIndexed { index, step ->
                Row(
                    modifier = Modifier.padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(20.dp)
                            .background(NoteTheme.Primary.copy(alpha = 0.1f), CircleShape)
                    ) {
                        Text(
                            "${index + 1}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = NoteTheme.Primary,
                            fontSize = 9.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(step, style = MaterialTheme.typography.bodySmall, color = NoteTheme.OnSurfaceVariant)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                "Groq:",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = NoteTheme.OnSurface
            )
            val groqSteps = listOf(
                "Open console.groq.com",
                "Sign up / Sign in",
                "Go to API Keys → Create → Copy & paste here"
            )
            groqSteps.forEachIndexed { index, step ->
                Row(
                    modifier = Modifier.padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(20.dp)
                            .background(Color(0xFFF97316).copy(alpha = 0.1f), CircleShape)
                    ) {
                        Text(
                            "${index + 1}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFF97316),
                            fontSize = 9.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(step, style = MaterialTheme.typography.bodySmall, color = NoteTheme.OnSurfaceVariant)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = NoteTheme.Success.copy(alpha = 0.08f)
            ) {
                Text(
                    "✨ Both are free — Gemini: 15 req/min · Groq: 30 req/min",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = NoteTheme.Success
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    onClick = onOpenGeminiLink,
                    shape = RoundedCornerShape(12.dp),
                    color = NoteTheme.Primary.copy(alpha = 0.1f),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.OpenInNew, null,
                            modifier = Modifier.size(14.dp),
                            tint = NoteTheme.Primary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "Gemini",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = NoteTheme.Primary
                        )
                    }
                }
                Surface(
                    onClick = onOpenGroqLink,
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFF97316).copy(alpha = 0.1f),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.OpenInNew, null,
                            modifier = Modifier.size(14.dp),
                            tint = Color(0xFFF97316)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "Groq",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFF97316)
                        )
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// PRIVACY CARD
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun PrivacyInfoCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(NoteTheme.Radius.lg.dp),
        colors = CardDefaults.cardColors(containerColor = NoteTheme.SurfaceVariant.copy(alpha = 0.4f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                Icons.Default.Lock, null,
                modifier = Modifier.size(18.dp),
                tint = NoteTheme.OnSurfaceVariant
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                "Your API key is stored encrypted on-device only. " +
                        "Note text is sent to the AI provider only when on-device detection fails. " +
                        "No note content is stored by providers for training.",
                style = MaterialTheme.typography.bodySmall,
                color = NoteTheme.OnSurfaceVariant,
                lineHeight = 16.sp
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// ADD API KEY DIALOG — with provider selection, inline errors,
// confirmation popup, no toast, no spinner on error
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun AddApiKeyDialog(
    onDismiss: () -> Unit,
    onSave: (label: String, apiKey: String, provider: LlmProvider) -> Unit
) {
    var label by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var keyVisible by remember { mutableStateOf(false) }
    var selectedProvider by remember { mutableStateOf(LlmProvider.GEMINI) }
    var isValidating by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showConfirmation by remember { mutableStateOf(false) }
    var providerDropdownExpanded by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Warning dialog
    if (showConfirmation) {
        AlertDialog(
            onDismissRequest = { showConfirmation = false },
            containerColor = NoteTheme.Surface,
            icon = {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = NoteTheme.Warning,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text("AI Content Warning", color = NoteTheme.OnSurface, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    "Once enabled, your note content will be sent to ${selectedProvider.displayName} AI for analysis — including title generation, reminder detection, and smart suggestions.\n\nNo data is stored by the provider for training. If you agree, tap Continue to validate and save your key.",
                    color = NoteTheme.OnSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showConfirmation = false
                    isValidating = true
                    errorMessage = null
                    scope.launch {
                        val result = LlmService.getInstance(context).validateKey(apiKey.trim(), selectedProvider)
                        isValidating = false
                        when (result) {
                            is KeyValidationResult.Success -> {
                                onSave(label, apiKey.trim(), selectedProvider)
                            }
                            is KeyValidationResult.Failed -> {
                                errorMessage = result.errorMessage
                            }
                        }
                    }
                }) {
                    Text("Continue", color = NoteTheme.Primary, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmation = false }) {
                    Text("Cancel", color = NoteTheme.OnSurfaceVariant)
                }
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NoteTheme.Surface,
        title = {
            Text("Add API Key", color = NoteTheme.OnSurface, fontWeight = FontWeight.Bold)
        },
        text = {
            Column {
                // ── LLM Provider Selector ──
                Text(
                    "LLM Provider",
                    style = MaterialTheme.typography.labelMedium,
                    color = NoteTheme.OnSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(6.dp))
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = selectedProvider.displayName,
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = {
                            Icon(
                                if (providerDropdownExpanded) Icons.Default.ArrowDropUp
                                else Icons.Default.ArrowDropDown,
                                contentDescription = "Select provider",
                                tint = NoteTheme.OnSurfaceVariant
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = NoteTheme.OnSurface,
                            unfocusedTextColor = NoteTheme.OnSurface,
                            focusedBorderColor = NoteTheme.Primary,
                            unfocusedBorderColor = NoteTheme.Outline,
                            cursorColor = NoteTheme.Primary
                        )
                    )
                    // Invisible clickable overlay to toggle dropdown
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { providerDropdownExpanded = !providerDropdownExpanded }
                    )
                    DropdownMenu(
                        expanded = providerDropdownExpanded,
                        onDismissRequest = { providerDropdownExpanded = false },
                        modifier = Modifier.background(NoteTheme.Surface)
                    ) {
                        LlmProvider.entries.forEach { provider ->
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            provider.displayName,
                                            color = NoteTheme.OnSurface,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            if (provider == LlmProvider.GEMINI) "Google" else "Fast & Free",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = NoteTheme.OnSurfaceVariant
                                        )
                                    }
                                },
                                modifier = Modifier.background(
                                    if (provider == selectedProvider) NoteTheme.Primary.copy(alpha = 0.08f)
                                    else Color.Transparent
                                ),
                                onClick = {
                                    selectedProvider = provider
                                    providerDropdownExpanded = false
                                    errorMessage = null
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // ── Label (optional) ──
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Label (optional)", color = NoteTheme.OnSurfaceVariant) },
                    placeholder = { Text("e.g., Personal, Work, Backup") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = NoteTheme.OnSurface,
                        unfocusedTextColor = NoteTheme.OnSurface,
                        focusedBorderColor = NoteTheme.Primary,
                        unfocusedBorderColor = NoteTheme.Outline,
                        focusedLabelColor = NoteTheme.Primary,
                        cursorColor = NoteTheme.Primary,
                        focusedPlaceholderColor = NoteTheme.OnSurfaceVariant,
                        unfocusedPlaceholderColor = NoteTheme.OnSurfaceVariant
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                // ── API Key ──
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = {
                        apiKey = it
                        errorMessage = null // Clear error on edit
                    },
                    label = { Text("API Key", color = NoteTheme.OnSurfaceVariant) },
                    placeholder = { Text(selectedProvider.keyHint) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { keyVisible = !keyVisible }) {
                            Icon(
                                if (keyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = "Toggle visibility",
                                tint = NoteTheme.OnSurfaceVariant
                            )
                        }
                    },
                    isError = errorMessage != null,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = NoteTheme.OnSurface,
                        unfocusedTextColor = NoteTheme.OnSurface,
                        focusedBorderColor = NoteTheme.Primary,
                        unfocusedBorderColor = NoteTheme.Outline,
                        errorBorderColor = NoteTheme.Error,
                        focusedLabelColor = NoteTheme.Primary,
                        cursorColor = NoteTheme.Primary,
                        focusedPlaceholderColor = NoteTheme.OnSurfaceVariant,
                        unfocusedPlaceholderColor = NoteTheme.OnSurfaceVariant
                    )
                )

                // ── Inline Error Message ──
                AnimatedVisibility(visible = errorMessage != null) {
                    Column {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = NoteTheme.Error.copy(alpha = 0.08f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(
                                    Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    tint = NoteTheme.Error,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    errorMessage ?: "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = NoteTheme.Error,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }
                }

                // ── Validating indicator (only while actively validating) ──
                AnimatedVisibility(visible = isValidating) {
                    Column {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = NoteTheme.Primary
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                "Validating with ${selectedProvider.displayName}...",
                                style = MaterialTheme.typography.bodySmall,
                                color = NoteTheme.OnSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "Get your key from ${selectedProvider.keyGetUrl}",
                    style = MaterialTheme.typography.bodySmall,
                    color = NoteTheme.Primary,
                    fontSize = 11.sp
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    // Show confirmation before validating
                    showConfirmation = true
                },
                enabled = apiKey.length >= 10 && !isValidating
            ) {
                Text(
                    "Validate & Save",
                    color = if (apiKey.length >= 10 && !isValidating) NoteTheme.Primary else NoteTheme.OnSurfaceVariant,
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = NoteTheme.OnSurfaceVariant)
            }
        }
    )
}

@Composable
private fun EditKeyLabelDialog(
    currentLabel: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var newLabel by remember { mutableStateOf(currentLabel) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NoteTheme.Surface,
        title = { Text("Edit Key Label", color = NoteTheme.OnSurface, fontWeight = FontWeight.Bold) },
        text = {
            OutlinedTextField(
                value = newLabel,
                onValueChange = { newLabel = it },
                label = { Text("Label", color = NoteTheme.OnSurfaceVariant) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = NoteTheme.OnSurface,
                    unfocusedTextColor = NoteTheme.OnSurface,
                    focusedBorderColor = NoteTheme.Primary,
                    unfocusedBorderColor = NoteTheme.Outline,
                    focusedLabelColor = NoteTheme.Primary,
                    cursorColor = NoteTheme.Primary,
                    focusedPlaceholderColor = NoteTheme.OnSurfaceVariant,
                    unfocusedPlaceholderColor = NoteTheme.OnSurfaceVariant
                )
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(newLabel) },
                enabled = newLabel.isNotBlank()
            ) { Text("Save", color = NoteTheme.Primary, fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = NoteTheme.OnSurfaceVariant) }
        }
    )
}


