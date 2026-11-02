package com.amvarpvtltd.swiftNote.onboarding

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amvarpvtltd.swiftNote.R

/** Vector-only product vignettes: no network, bitmap allocations, or animation libraries. */
@Composable
internal fun OnboardingIllustration(page: Int, description: String, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    // Finite entrance animation honors the system animator scale and runs only on entry.
    val inspectionMode = LocalInspectionMode.current
    val reveal = remember(page, inspectionMode) { Animatable(if (inspectionMode) 1f else 0f) }
    LaunchedEffect(page) { reveal.animateTo(1f, tween(650, easing = FastOutSlowInEasing)) }
    BoxWithConstraints(
        modifier.clearAndSetSemantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        val scale = minOf(maxWidth.value / 320f, maxHeight.value / 310f, 1.3f)
        Box(
            Modifier.requiredSize(320.dp, 310.dp).graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
            contentAlignment = Alignment.Center
        ) {
            Box(Modifier.size(276.dp).background(
                Brush.radialGradient(listOf(colors.primaryContainer, colors.background)),
                CircleShape
            ))
            if (page == 3) {
                Phone(Modifier.offset(x = (-68).dp, y = 10.dp).graphicsLayer { rotationZ = -8f }, compact = true)
                Phone(Modifier.offset(x = 68.dp, y = (-9).dp).graphicsLayer { rotationZ = 8f }, compact = true)
                Surface(
                    modifier = Modifier.size(82.dp),
                    shape = RoundedCornerShape(22.dp),
                    color = colors.surface, shadowElevation = 8.dp
                ) {
                    Icon(Icons.Outlined.QrCode2, null, tint = colors.primary, modifier = Modifier.padding(13.dp))
                }
                FloatingLabel(Icons.Outlined.Lock, R.string.welcome_sample_encrypted,
                    Modifier.align(Alignment.BottomCenter).offset(y = (-5).dp)
                        .graphicsLayer { alpha = reveal.value })
            } else {
                Phone(Modifier.graphicsLayer { rotationZ = -4f + reveal.value * 2f })
                when (page) {
                    0 -> {
                        MiniCard(
                            R.string.welcome_sample_ideas, R.string.welcome_sample_note,
                            Icons.Outlined.PushPin,
                            Modifier.align(Alignment.CenterStart).offset(x = 2.dp, y = (-42).dp)
                                .graphicsLayer { translationY = (1f - reveal.value) * 35f }
                        )
                        MiniCard(
                            R.string.welcome_sample_checklist, R.string.welcome_sample_task_two,
                            Icons.Outlined.CheckBox,
                            Modifier.align(Alignment.BottomEnd).offset(x = (-2).dp, y = (-22).dp)
                                .graphicsLayer { translationY = (1f - reveal.value) * -35f }
                        )
                    }
                    1 -> {
                        MiniCard(
                            R.string.welcome_sample_meeting, R.string.welcome_sample_category,
                            Icons.Outlined.EditNote,
                            Modifier.align(Alignment.TopStart).offset(y = 28.dp)
                        )
                        Column(Modifier.align(Alignment.BottomEnd).offset(y = (-16).dp),
                            horizontalAlignment = Alignment.End) {
                            Icon(Icons.Outlined.AutoAwesome, null, tint = colors.primary,
                                modifier = Modifier.padding(12.dp).size(30.dp)
                                    .graphicsLayer { alpha = reveal.value })
                            MiniCard(
                                R.string.welcome_sample_detected, R.string.welcome_sample_time,
                                Icons.Outlined.NotificationsActive,
                                Modifier.graphicsLayer {
                                    alpha = reveal.value
                                    translationY = (1f - reveal.value) * 45f
                                }
                            )
                        }
                    }
                    2 -> {
                        Surface(
                            Modifier.align(Alignment.TopEnd).offset(x = (-14).dp, y = 28.dp).size(62.dp),
                            shape = CircleShape, color = colors.primaryContainer
                        ) {
                            Icon(Icons.Outlined.WifiOff, null, tint = colors.primary, modifier = Modifier.padding(16.dp))
                        }
                        MiniCard(
                            R.string.welcome_sample_saved, R.string.welcome_sample_offline,
                            Icons.Outlined.Storage,
                            Modifier.align(Alignment.BottomCenter).offset(y = (-15).dp)
                                .graphicsLayer { alpha = reveal.value }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Phone(modifier: Modifier, compact: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier.width(if (compact) 146.dp else 180.dp).height(if (compact) 240.dp else 278.dp)
            .border(2.dp, colors.outlineVariant, RoundedCornerShape(28.dp))
            .clip(RoundedCornerShape(28.dp))
            .background(colors.surface).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(Modifier.align(Alignment.CenterHorizontally).size(42.dp, 5.dp)
            .background(colors.outlineVariant, CircleShape))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Notes, null, tint = colors.primary, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(5.dp))
            Text(stringResource(R.string.welcome_sample_notes), fontSize = 13.sp,
                fontWeight = FontWeight.Bold, color = colors.onSurface)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(3) {
                Box(Modifier.size(if (it == 0) 45.dp else 25.dp, 7.dp)
                    .background(if (it == 0) colors.primary else colors.primaryContainer, CircleShape))
            }
        }
        repeat(3) { index ->
            Column(Modifier.fillMaxWidth()
                .background(if (index == 0) colors.primaryContainer else colors.surfaceVariant,
                    RoundedCornerShape(12.dp)).padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Icon(if (index == 1) Icons.Outlined.CheckBox else Icons.Outlined.ShortText,
                        null, tint = colors.primary, modifier = Modifier.size(13.dp))
                    Box(Modifier.padding(top = 4.dp).size(58.dp, 4.dp)
                        .background(colors.onSurface.copy(alpha = .4f), CircleShape))
                }
                Box(Modifier.fillMaxWidth(.85f).height(3.dp)
                    .background(colors.onSurface.copy(alpha = .16f), CircleShape))
                Box(Modifier.fillMaxWidth(.6f).height(3.dp)
                    .background(colors.onSurface.copy(alpha = .12f), CircleShape))
            }
        }
    }
}

@Composable
private fun MiniCard(title: Int, body: Int, icon: ImageVector, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    Surface(modifier.width(184.dp), shape = RoundedCornerShape(18.dp),
        color = colors.surface, shadowElevation = 6.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.outlineVariant.copy(alpha = .6f))) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Icon(icon, null, tint = colors.primary, modifier = Modifier.size(20.dp))
            Text(stringResource(title), color = colors.onSurface, fontSize = 13.sp,
                lineHeight = 17.sp, fontWeight = FontWeight.SemiBold)
            Text(stringResource(body), color = colors.onSurfaceVariant, fontSize = 10.sp, lineHeight = 14.sp)
        }
    }
}

@Composable
private fun FloatingLabel(icon: ImageVector, label: Int, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    Surface(modifier, shape = RoundedCornerShape(30.dp), color = colors.primaryContainer) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, null, tint = colors.primary, modifier = Modifier.size(18.dp))
            Text(stringResource(label), color = colors.onPrimaryContainer, fontSize = 12.sp,
                fontWeight = FontWeight.Medium)
        }
    }
}
