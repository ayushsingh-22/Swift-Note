package com.amvarpvtltd.swiftNote.design

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amvarpvtltd.swiftNote.R
import com.amvarpvtltd.swiftNote.onboarding.OnboardingIllustration
import com.amvarpvtltd.swiftNote.onboarding.TourTheme
import com.amvarpvtltd.swiftNote.utils.rememberResponsiveDimensions
import com.amvarpvtltd.swiftNote.utils.responsiveContentWidth
import kotlinx.coroutines.launch

private data class WelcomePage(
    @param:StringRes val label: Int,
    @param:StringRes val title: Int,
    @param:StringRes val description: Int,
    @param:StringRes val imageDescription: Int,
    @param:StringRes val features: Int
)

private val welcomePages = listOf(
    WelcomePage(R.string.welcome_notes_label, R.string.welcome_capture_title,
        R.string.welcome_capture_body, R.string.welcome_capture_image, R.string.welcome_notes_features),
    WelcomePage(R.string.welcome_reminders_label, R.string.welcome_reminders_title,
        R.string.welcome_reminders_body, R.string.welcome_reminders_image, R.string.welcome_reminders_features),
    WelcomePage(R.string.welcome_offline_label, R.string.welcome_offline_title,
        R.string.welcome_offline_body, R.string.welcome_offline_image, R.string.welcome_offline_features),
    WelcomePage(R.string.welcome_sync_label, R.string.welcome_sync_title,
        R.string.welcome_sync_body, R.string.welcome_sync_image, R.string.welcome_sync_features)
)

/** Pure tour UI. Only the final welcome action delegates persistence to the host ViewModel. */
@Composable
fun WelcomeTourScreen(
    onComplete: () -> Unit,
    isCompleting: Boolean = false,
    saveFailed: Boolean = false
) {
    TourTheme {
        WelcomeTourContent(onComplete, isCompleting, saveFailed)
    }
}

@Composable
private fun WelcomeTourContent(
    onComplete: () -> Unit,
    isCompleting: Boolean,
    saveFailed: Boolean
) {
    val dims = rememberResponsiveDimensions()
    val colors = MaterialTheme.colorScheme
    // Both states have Savers: rotation and process recreation retain the current step.
    val pager = rememberPagerState(pageCount = { welcomePages.size })
    var showingWelcome by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun previous() {
        if (showingWelcome) showingWelcome = false
        else if (!pager.isScrollInProgress && pager.currentPage > 0) {
            scope.launch { pager.animateScrollToPage(pager.currentPage - 1, animationSpec = tween(280)) }
        }
    }

    BackHandler(enabled = showingWelcome || pager.currentPage > 0) {
        if (!isCompleting) previous()
    }

    Surface(Modifier.fillMaxSize(), color = colors.background) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().responsiveContentWidth(dims),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = dims.paddingLarge).heightIn(min = 56.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (showingWelcome || pager.currentPage > 0) {
                    IconButton(onClick = ::previous, enabled = !isCompleting && !pager.isScrollInProgress) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack,
                            stringResource(R.string.welcome_back), tint = colors.onSurface)
                    }
                } else {
                    Icon(Icons.Outlined.EditNote, null, tint = colors.primary,
                        modifier = Modifier.padding(end = 8.dp).size(26.dp))
                }
                Text(stringResource(R.string.welcome_brand),
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                    color = colors.onSurface)
                Spacer(Modifier.weight(1f))
                if (!showingWelcome) {
                    TextButton(onClick = { showingWelcome = true }, enabled = !pager.isScrollInProgress) {
                        Text(stringResource(R.string.welcome_skip))
                    }
                }
            }

            AnimatedContent(
                targetState = showingWelcome,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                transitionSpec = { fadeIn(tween(240)) togetherWith fadeOut(tween(160)) },
                label = "tour welcome"
            ) { welcome ->
                if (welcome) {
                    FinalWelcome()
                } else {
                    HorizontalPager(
                        state = pager,
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically
                    ) { index ->
                        TourPage(welcomePages[index], index)
                    }
                }
            }

            Column(
                Modifier.widthIn(max = 520.dp).fillMaxWidth().padding(horizontal = dims.paddingLarge)
                    .padding(top = 12.dp, bottom = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (!showingWelcome) {
                    val progress = stringResource(R.string.welcome_progress,
                        pager.currentPage + 1, welcomePages.size)
                    Row(
                        Modifier.clearAndSetSemantics { stateDescription = progress },
                        horizontalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        repeat(welcomePages.size) { index ->
                            val selected = pager.currentPage == index
                            val width by animateDpAsState(if (selected) 28.dp else 7.dp,
                                tween(220), label = "page indicator")
                            Box(Modifier.size(width, 7.dp)
                                .background(if (selected) colors.primary else colors.outlineVariant, CircleShape))
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(progress, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                    Spacer(Modifier.height(16.dp))
                } else {
                    Text(stringResource(R.string.welcome_final_hint),
                        style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant,
                        textAlign = TextAlign.Center)
                    Spacer(Modifier.height(14.dp))
                }
                if (saveFailed) {
                    Text(stringResource(R.string.welcome_save_error),
                        color = colors.error, style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 12.dp).semantics { liveRegion = LiveRegionMode.Polite })
                }
                Button(
                    onClick = {
                        if (showingWelcome) onComplete()
                        else if (pager.currentPage == welcomePages.lastIndex) showingWelcome = true
                        else scope.launch {
                            pager.animateScrollToPage(pager.currentPage + 1, animationSpec = tween(300))
                        }
                    },
                    enabled = !pager.isScrollInProgress && !isCompleting,
                    shape = RoundedCornerShape(18.dp),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 17.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                ) {
                    Text(
                        stringResource(
                            if (isCompleting) R.string.welcome_saving
                            else if (showingWelcome || pager.currentPage == welcomePages.lastIndex)
                                R.string.welcome_get_started else R.string.welcome_continue
                        ),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.width(12.dp))
                    Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, modifier = Modifier.size(19.dp))
                }
            }
        }
    }
}

@Composable
private fun TourPage(page: WelcomePage, index: Int) {
    val dims = rememberResponsiveDimensions()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val landscape = maxWidth > 580.dp && maxWidth > maxHeight
        if (landscape) {
            Row(
                Modifier.fillMaxSize().padding(horizontal = dims.paddingLarge),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                OnboardingIllustration(index, stringResource(page.imageDescription),
                    Modifier.weight(1f).fillMaxHeight())
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(vertical = 12.dp)) {
                    WelcomeMessage(page, index, centered = false)
                }
            }
        } else {
            val illustrationHeight = (maxHeight * .48f).coerceIn(190.dp, 340.dp)
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(horizontal = dims.paddingLarge, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                OnboardingIllustration(index, stringResource(page.imageDescription),
                    Modifier.fillMaxWidth().height(illustrationHeight))
                Spacer(Modifier.height(20.dp))
                WelcomeMessage(page, index, centered = true)
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun WelcomeMessage(page: WelcomePage, index: Int, centered: Boolean) {
    val colors = MaterialTheme.colorScheme
    val alignment = if (centered) Alignment.CenterHorizontally else Alignment.Start
    val textAlign = if (centered) TextAlign.Center else TextAlign.Start
    Column(Modifier.widthIn(max = 440.dp), horizontalAlignment = alignment) {
        Text(stringResource(page.label), color = colors.primary,
            style = MaterialTheme.typography.labelSmall, letterSpacing = 1.6.sp,
            fontWeight = FontWeight.Bold, textAlign = textAlign)
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(page.title), color = colors.onSurface,
            style = MaterialTheme.typography.headlineLarge.copy(fontSize = 30.sp, lineHeight = 36.sp),
            fontWeight = FontWeight.Bold, textAlign = textAlign,
            modifier = Modifier.semantics { heading() }
        )
        Spacer(Modifier.height(14.dp))
        Text(stringResource(page.description), color = colors.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 23.sp), textAlign = textAlign)
        Spacer(Modifier.height(18.dp))
        Text(stringResource(page.features), color = colors.onPrimaryContainer,
            style = MaterialTheme.typography.labelMedium.copy(lineHeight = 22.sp), textAlign = textAlign)
        if (index == 1) {
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.welcome_ai_optional), color = colors.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall, textAlign = textAlign)
        }
    }
}

@Composable
private fun FinalWelcome() {
    val colors = MaterialTheme.colorScheme
    val dims = rememberResponsiveDimensions()
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = dims.paddingLarge, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier.size(144.dp).background(
                Brush.radialGradient(listOf(colors.primaryContainer, colors.background)), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Surface(shape = RoundedCornerShape(28.dp), color = colors.primary,
                shadowElevation = 6.dp, modifier = Modifier.size(84.dp)) {
                Icon(Icons.Outlined.EditNote, null, tint = colors.onPrimary, modifier = Modifier.padding(20.dp))
            }
        }
        Spacer(Modifier.height(24.dp))
        Text(stringResource(R.string.welcome_final_title),
            style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold,
            color = colors.onSurface, textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(14.dp))
        Text(stringResource(R.string.welcome_final_body),
            style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center)
        Spacer(Modifier.height(32.dp))
        Column(Modifier.widthIn(max = 370.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
            WelcomeFeature(Icons.Outlined.Checklist, R.string.welcome_final_notes)
            WelcomeFeature(Icons.Outlined.WifiOff, R.string.welcome_final_offline)
            WelcomeFeature(Icons.Outlined.Lock, R.string.welcome_final_privacy)
        }
    }
}

@Composable
private fun WelcomeFeature(icon: ImageVector, @StringRes label: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.size(42.dp).background(MaterialTheme.colorScheme.primaryContainer,
            RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(21.dp))
        }
        Text(stringResource(label), style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Preview(name = "Phone", widthDp = 360, heightDp = 800)
@Preview(name = "Large text", widthDp = 320, heightDp = 640, fontScale = 1.5f)
@Preview(name = "Landscape", widthDp = 800, heightDp = 360)
@Preview(name = "Tablet", widthDp = 840, heightDp = 1180)
@Composable
private fun WelcomeTourPreview() {
    TourTheme(darkOverride = false) { WelcomeTourContent({}, false, false) }
}

@Preview(name = "Dark", widthDp = 412, heightDp = 850)
@Composable
private fun WelcomeTourDarkPreview() {
    TourTheme(darkOverride = true) { WelcomeTourContent({}, false, false) }
}

class TourPagePreviewProvider :
    androidx.compose.ui.tooling.preview.PreviewParameterProvider<Int> {
    override val values = sequenceOf(1, 2, 3)
}

@Preview(name = "Feature details", widthDp = 360, heightDp = 620)
@Composable
private fun FeaturePagePreview(
    @androidx.compose.ui.tooling.preview.PreviewParameter(TourPagePreviewProvider::class) index: Int
) {
    TourTheme(darkOverride = false) {
        Surface(color = MaterialTheme.colorScheme.background) { TourPage(welcomePages[index], index) }
    }
}

@Preview(name = "Welcome", widthDp = 360, heightDp = 700)
@Composable
private fun FinalWelcomePreview() {
    TourTheme(darkOverride = false) { Surface(color = MaterialTheme.colorScheme.background) { FinalWelcome() } }
}
