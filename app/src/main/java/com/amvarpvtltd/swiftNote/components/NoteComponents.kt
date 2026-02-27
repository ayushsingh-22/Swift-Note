package com.amvarpvtltd.swiftNote.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.amvarpvtltd.swiftNote.design.NoteTheme
import com.amvarpvtltd.swiftNote.utils.Constants
import com.amvarpvtltd.swiftNote.utils.UIUtils
import androidx.compose.ui.graphics.luminance

@Composable
fun ActionButton(
    onClick: () -> Unit,
    text: String,
    icon: ImageVector,
    isLoading: Boolean = false,
    loadingText: String = "Loading...",
    modifier: Modifier = Modifier,
    containerColor: Color = NoteTheme.Primary,
    contentColor: Color = NoteTheme.OnPrimary
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.9f else 1f,
        animationSpec = UIUtils.getSpringAnimationSpec(),
        label = "button_scale"
    )

    ExtendedFloatingActionButton(
        onClick = {
            isPressed = true
            onClick()
        },
        modifier = modifier.scale(scale),
        containerColor = containerColor,
        contentColor = contentColor,
        shape = RoundedCornerShape(Constants.CORNER_RADIUS_LARGE.dp)
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(Constants.ICON_SIZE_MEDIUM.dp),
                strokeWidth = 2.dp,
                color = contentColor
            )
            Spacer(modifier = Modifier.width(Constants.CORNER_RADIUS_SMALL.dp))
            Text(loadingText, fontWeight = FontWeight.Bold)
        } else {
            Icon(icon, contentDescription = null, modifier = Modifier.size(Constants.ICON_SIZE_LARGE.dp))
            Spacer(modifier = Modifier.width(Constants.CORNER_RADIUS_SMALL.dp))
            Text(text, fontWeight = FontWeight.Bold)
        }
    }

    LaunchedEffect(isPressed) {
        if (isPressed) {
            kotlinx.coroutines.delay(Constants.SPRING_ANIMATION_DELAY.toLong())
            isPressed = false
        }
    }
}

fun adaptiveIconColors(background: Color, accent: Color): Pair<Color, Color> {
    val bgIsLight = background.luminance() > 0.5f
    // Slightly stronger container on dark backgrounds to improve visibility
    val containerAlpha = if (bgIsLight) 0.10f else 0.16f
    val container = accent.copy(alpha = containerAlpha)

    // Content color: prefer accent when it contrasts enough, otherwise use white/black
    val accentIsContrasting = if (bgIsLight) accent.luminance() < 0.6f else accent.luminance() > 0.4f
    val content = when {
        accentIsContrasting -> accent
        bgIsLight -> Color.Black.copy(alpha = 0.9f)
        else -> Color.White.copy(alpha = 0.95f)
    }

    return Pair(container, content)
}

@Composable
fun IconActionButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null
        ) { onClick() },
        shape = CircleShape,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp) // Removed shadow
    ) {
        Box(
            modifier = Modifier.padding(Constants.PADDING_SMALL.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = contentDescription,
                tint = Color.Black,
                modifier = Modifier.size(Constants.ICON_SIZE_LARGE.dp)
            )
        }
    }
}
