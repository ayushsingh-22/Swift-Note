package com.amvarpvtltd.swiftNote.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amvarpvtltd.swiftNote.design.NoteTheme
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * Premium background surface — clean solid canvas, no distracting gradients.
 */
@Composable
fun NoteScreenBackground(
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NoteTheme.Background)
    ) {
        content()
    }
}

/**
 * Premium empty state — centered composition with illustration icon,
 * descriptive headline, helper body text, and single CTA.
 */
@Composable
fun EmptyStateCard(
    icon: ImageVector,
    title: String,
    description: String,
    buttonText: String,
    onButtonClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = NoteTheme.Spacing.xxl.dp)
        ) {
            // Illustration icon with teal tint background
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(
                        color = NoteTheme.Primary.copy(alpha = 0.08f),
                        shape = RoundedCornerShape(NoteTheme.Radius.lg.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = NoteTheme.Primary,
                    modifier = Modifier.size(36.dp)
                )
            }

            Spacer(modifier = Modifier.height(NoteTheme.Spacing.xl.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = NoteTheme.OnSurface,
                fontWeight = FontWeight.SemiBold,
                fontSize = 22.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(NoteTheme.Spacing.sm.dp))

            Text(
                text = description,
                style = MaterialTheme.typography.bodyLarge,
                color = NoteTheme.OnSurfaceVariant,
                fontWeight = FontWeight.Normal,
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
                lineHeight = 24.sp
            )

            Spacer(modifier = Modifier.height(NoteTheme.Spacing.xl.dp))

            Button(
                onClick = onButtonClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = NoteTheme.Primary,
                    contentColor = NoteTheme.OnPrimary
                ),
                shape = RoundedCornerShape(NoteTheme.Radius.md.dp),
                elevation = ButtonDefaults.buttonElevation(
                    defaultElevation = 0.dp,
                    pressedElevation = 0.dp
                )
            ) {
                Text(
                    text = buttonText,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

/**
 * Premium FAB with perpetual float micro-animation and spring-physics press feedback.
 * Pill-shaped, teal accent, whisper shadow tinted to accent color.
 */
@Composable
fun AnimatedFloatingActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hapticFeedback = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // Tactile spring press scale
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(
            dampingRatio = 0.6f,
            stiffness = Spring.StiffnessMedium
        ),
        label = "fab_press_scale"
    )

    // Perpetual float micro-animation (translateY ±2dp, 3s infinite)
    val infiniteTransition = rememberInfiniteTransition(label = "fab_float")
    val floatOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -4f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "fab_float_offset"
    )

    FloatingActionButton(
        onClick = {
            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
            onClick()
        },
        modifier = modifier
            .offset { IntOffset(0, floatOffset.roundToInt()) }
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .shadow(
                elevation = 12.dp,
                shape = RoundedCornerShape(NoteTheme.Radius.lg.dp),
                ambientColor = NoteTheme.Primary.copy(alpha = 0.2f),
                spotColor = NoteTheme.Primary.copy(alpha = 0.25f)
            ),
        containerColor = NoteTheme.Primary,
        contentColor = NoteTheme.OnPrimary,
        shape = RoundedCornerShape(NoteTheme.Radius.lg.dp),
        elevation = FloatingActionButtonDefaults.elevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp
        ),
        interactionSource = interactionSource
    ) {
        Icon(
            Icons.Outlined.Add,
            contentDescription = "Add Note",
            modifier = Modifier.size(24.dp)
        )
    }
}

/**
 * Floating toolbar — horizontal pill-shaped bar with icon+label action items.
 * Styled like the iOS-inspired floating action bar shown in the design reference.
 */
@Composable
fun FloatingToolbar(
    onNewNote: () -> Unit,
    onArchive: () -> Unit,
    onToday: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hapticFeedback = LocalHapticFeedback.current

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = NoteTheme.Surface,
        shadowElevation = 8.dp,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FloatingToolbarItem(
                icon = Icons.Outlined.EditNote,
                label = "New",
                tint = NoteTheme.Primary,
                onClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    onNewNote()
                }
            )
            FloatingToolbarItem(
                icon = Icons.Outlined.Today,
                label = "Today",
                tint = NoteTheme.Primary,
                onClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    onToday()
                }
            )
            FloatingToolbarItem(
                icon = Icons.Outlined.Archive,
                label = "Archive",
                tint = NoteTheme.Secondary,
                onClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    onArchive()
                }
            )
        }
    }
}

@Composable
private fun FloatingToolbarItem(
    icon: ImageVector,
    label: String,
    tint: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() }
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(tint.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(20.dp),
                tint = tint
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = NoteTheme.OnSurfaceVariant,
            fontSize = 11.sp
        )
    }
}

/**
 * Reusable icon action button with minimum 44dp touch target.
 * Circular container with surface tint on press.
 */
@Composable
fun IconActionButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: androidx.compose.ui.graphics.Color = NoteTheme.OnSurfaceVariant
) {
    val hapticFeedback = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.9f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessHigh),
        label = "icon_btn_scale"
    )

    androidx.compose.material3.IconButton(
        onClick = {
            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
            onClick()
        },
        modifier = modifier
            .size(44.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        interactionSource = interactionSource
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(22.dp)
        )
    }
}

/**
 * Loading card placeholder with shimmer effect dimensions matching note cards.
 */
@Composable
fun LoadingCard(
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val shimmerAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmer_alpha"
    )

    Card(
        shape = RoundedCornerShape(NoteTheme.Radius.lg.dp),
        colors = CardDefaults.cardColors(
            containerColor = NoteTheme.SurfaceVariant.copy(alpha = shimmerAlpha)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier
            .height(80.dp)
            .padding(horizontal = NoteTheme.Spacing.lg.dp, vertical = NoteTheme.Spacing.sm.dp)
    ) {
        // Empty — pure skeletal shimmer matching note card shape
    }
}
