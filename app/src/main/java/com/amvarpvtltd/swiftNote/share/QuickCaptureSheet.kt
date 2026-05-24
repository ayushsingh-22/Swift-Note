package com.amvarpvtltd.swiftNote.share

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Notes
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Phase 5A: Quick Capture Sheet — premium redesign.
 *
 * Design:
 * - Drag handle + branded header with app identity
 * - Content-type auto-detection badge (URL / Text)
 * - Filled card-style text fields with focus rings
 * - Character count on content field
 * - Full-width indigo Save button + ghost Edit button
 */
@Composable
fun QuickCaptureSheet(
    initialTitle: String,
    initialDescription: String,
    onSave: (String, String) -> Unit,
    onEdit: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf(initialTitle) }
    var description by remember { mutableStateOf(initialDescription) }
    var titleFocused by remember { mutableStateOf(false) }
    var descFocused by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // Detect content type
    val isUrl = remember(initialDescription) {
        initialDescription.matches(Regex("^https?://\\S+.*"))
    }

    val primary = Color(0xFF6366F1)
    val primaryContainer = Color(0xFFEEF2FF)
    val onPrimary = Color(0xFFFFFFFF)
    val surfaceColor = MaterialTheme.colorScheme.surface
    val bgVariant = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val outline = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF000000).copy(alpha = 0.55f))
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
            ) + fadeIn(tween(180)),
            exit = slideOutVertically(
                animationSpec = tween(220),
                targetOffsetY = { it }
            ) + fadeOut(tween(180))
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) {},
                color = surfaceColor,
                tonalElevation = 6.dp,
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
            ) {
                Column {
                    // ─── Drag Handle ──────────────────────────────────────
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .width(36.dp)
                                .height(4.dp)
                                .clip(CircleShape)
                                .background(onSurfaceVariant.copy(alpha = 0.3f))
                        )
                    }

                    Column(
                        modifier = Modifier
                            .padding(horizontal = 24.dp)
                            .padding(top = 16.dp, bottom = 24.dp)
                    ) {
                        // ─── Header ───────────────────────────────────────
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // App icon badge
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(primaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Notes,
                                        contentDescription = null,
                                        tint = primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column {
                                    Text(
                                        text = "Save to SwiftNote",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = onSurface
                                    )
                                    // Content type badge
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(top = 2.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isUrl) Icons.Outlined.Link else Icons.Outlined.Notes,
                                            contentDescription = null,
                                            tint = onSurfaceVariant,
                                            modifier = Modifier.size(11.dp)
                                        )
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text(
                                            text = if (isUrl) "Link detected" else "Text captured",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            // Close button
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable { onDismiss() },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Close,
                                    contentDescription = "Dismiss",
                                    tint = onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // ─── Title Field ──────────────────────────────────
                        Text(
                            text = "TITLE",
                            style = MaterialTheme.typography.labelSmall.copy(
                                letterSpacing = 1.sp
                            ),
                            fontWeight = FontWeight.SemiBold,
                            color = onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(bgVariant)
                                .then(
                                    if (titleFocused)
                                        Modifier.border(
                                            1.5.dp,
                                            primary.copy(alpha = 0.6f),
                                            RoundedCornerShape(12.dp)
                                        )
                                    else
                                        Modifier.border(
                                            1.dp,
                                            outline,
                                            RoundedCornerShape(12.dp)
                                        )
                                )
                                .padding(horizontal = 14.dp, vertical = 12.dp)
                        ) {
                            if (title.isEmpty()) {
                                Text(
                                    "Note title (optional)",
                                    style = TextStyle(
                                        fontSize = 15.sp,
                                        color = onSurfaceVariant.copy(alpha = 0.5f)
                                    )
                                )
                            }
                            BasicTextField(
                                value = title,
                                onValueChange = { if (it.length <= 120) title = it },
                                textStyle = TextStyle(
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = onSurface
                                ),
                                singleLine = true,
                                cursorBrush = SolidColor(primary),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onFocusChanged { titleFocused = it.isFocused }
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // ─── Content Field ────────────────────────────────
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "CONTENT",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    letterSpacing = 1.sp
                                ),
                                fontWeight = FontWeight.SemiBold,
                                color = onSurfaceVariant
                            )
                            Text(
                                text = "${description.length}/2000",
                                style = MaterialTheme.typography.labelSmall,
                                color = onSurfaceVariant.copy(
                                    alpha = if (description.length > 1800) 1f else 0.5f
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 110.dp, max = 200.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(bgVariant)
                                .then(
                                    if (descFocused)
                                        Modifier.border(
                                            1.5.dp,
                                            primary.copy(alpha = 0.6f),
                                            RoundedCornerShape(12.dp)
                                        )
                                    else
                                        Modifier.border(
                                            1.dp,
                                            outline,
                                            RoundedCornerShape(12.dp)
                                        )
                                )
                                .padding(horizontal = 14.dp, vertical = 12.dp)
                        ) {
                            if (description.isEmpty()) {
                                Text(
                                    "Note content…",
                                    style = TextStyle(
                                        fontSize = 14.sp,
                                        color = onSurfaceVariant.copy(alpha = 0.5f)
                                    )
                                )
                            }
                            BasicTextField(
                                value = description,
                                onValueChange = { if (it.length <= 2000) description = it },
                                textStyle = TextStyle(
                                    fontSize = 14.sp,
                                    color = onSurface,
                                    lineHeight = 21.sp
                                ),
                                maxLines = 10,
                                cursorBrush = SolidColor(primary),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onFocusChanged { descFocused = it.isFocused }
                            )
                        }

                        Spacer(modifier = Modifier.height(22.dp))

                        // ─── Action Buttons ───────────────────────────────
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Edit button — ghost style
                            OutlinedButton(
                                onClick = { onEdit(title, description) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = primary
                                ),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.5.dp, primary.copy(alpha = 0.35f)
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Edit,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "Edit",
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 14.sp
                                )
                            }

                            // Save button — filled indigo
                            Button(
                                onClick = {
                                    if (title.isBlank() && description.isBlank()) {
                                        Toast.makeText(context, "Nothing to save", Toast.LENGTH_SHORT).show()
                                    } else {
                                        onSave(title, description)
                                    }
                                },
                                modifier = Modifier
                                    .weight(2f)
                                    .height(48.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = primary,
                                    contentColor = onPrimary
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Save,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(7.dp))
                                Text(
                                    "Save Note",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
