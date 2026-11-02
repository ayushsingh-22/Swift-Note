package com.amvarpvtltd.swiftNote.onboarding

import androidx.compose.runtime.Composable
import com.amvarpvtltd.swiftNote.ui.theme.SelfNoteTheme

/** The welcome tour and application share one palette, typography, shapes and theme preference. */
@Composable
internal fun TourTheme(darkOverride: Boolean? = null, content: @Composable () -> Unit) {
    SelfNoteTheme(darkTheme = darkOverride, content = content)
}
