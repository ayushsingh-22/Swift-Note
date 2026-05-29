package com.amvarpvtltd.swiftNote.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import com.amvarpvtltd.swiftNote.design.NoteTheme
import com.mohamedrejeb.richeditor.model.rememberRichTextState
import com.mohamedrejeb.richeditor.ui.material3.RichText

/**
 * Read-only renderer for note descriptions.
 *
 * Migration note: Previously wrapped RichTextRenderer.htmlToAnnotatedFull().
 * Now wraps the MohamedRejeb library's RichText composable.
 * Inline checkbox toggling and link clicks are handled by the library natively.
 *
 * The deprecated parameters remain to avoid breaking the six call sites in
 * ViewModeComponents.kt — they are no-ops and can be cleaned up separately.
 */
@Composable
fun RichTextDisplay(
    html: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    color: Color = NoteTheme.OnSurface,
    @Suppress("UNUSED_PARAMETER")
    onCheckboxToggle: ((index: Int, newChecked: Boolean) -> Unit)? = null,
    @Suppress("UNUSED_PARAMETER")
    onLinkClick: ((url: String) -> Unit)? = null
) {
    val state = rememberRichTextState()
    LaunchedEffect(html) {
        state.setHtml(html)
    }
    RichText(
        state = state,
        modifier = modifier,
        style = style.copy(color = color)
    )
}
