package com.amvarpvtltd.swiftNote.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.itemsIndexed
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import com.amvarpvtltd.swiftNote.MainActivity
import com.amvarpvtltd.swiftNote.R
import com.amvarpvtltd.swiftNote.dataclass
import com.amvarpvtltd.swiftNote.richtext.RichTextBridge
import com.amvarpvtltd.swiftNote.room.AppDatabase
import com.amvarpvtltd.swiftNote.room.NoteEntityMapper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.map as flowMap

/**
 * SwiftNote Home Screen Widget
 *
 * Design principles:
 *  • ADAPTIVE layout — scales proportionally based on actual widget dimensions
 *  • CONSISTENT spacing — gaps scale with widget size
 *  • CLEAN typography — sizes adapt to available space
 *  • MODERN cards — rounded corners, subtle surfaces
 *  • RESPONSIVE — shows pinned notes when there's enough vertical space
 */
class QuickNoteWidget : GlanceAppWidget() {

    companion object {
        val ACTION_KEY  = ActionParameters.Key<String>("widget_action")
        val NOTE_ID_KEY = ActionParameters.Key<String>("widget_note_id")

        const val ACTION_CREATE_NOTE      = "com.amvarpvtltd.swiftNote.ACTION_CREATE_NOTE"
        const val ACTION_CREATE_CHECKLIST = "com.amvarpvtltd.swiftNote.ACTION_CREATE_CHECKLIST"
        const val ACTION_OPEN_NOTE        = "com.amvarpvtltd.swiftNote.ACTION_OPEN_NOTE"
        const val ACTION_OPEN_APP         = "com.amvarpvtltd.swiftNote.ACTION_OPEN_APP"

        // Accent colors for note cards (rotating)
        private val ACCENT_COLORS = listOf(
            Color(0xFF6366F1), // Indigo
            Color(0xFF8B5CF6), // Purple
            Color(0xFF06B6D4), // Cyan
            Color(0xFF10B981), // Emerald
            Color(0xFFF59E0B), // Amber
        )

        // ─── Color palette (light / dark) ──────────────────────────────────
        private val BgColor        = ColorProvider(Color(0xFFF8FAFC), Color(0xFF0F1419))
        private val SurfaceColor   = ColorProvider(Color(0xFFFFFFFF), Color(0xFF1C2128))
        private val SurfaceAltColor= ColorProvider(Color(0xFFF1F5F9), Color(0xFF21262D))
        private val PrimaryColor   = ColorProvider(Color(0xFF6366F1), Color(0xFF818CF8))
        private val PrimaryBtnBg   = ColorProvider(Color(0xFF6366F1), Color(0xFF4F46E5))
        private val PrimaryBtnText = ColorProvider(Color(0xFFFFFFFF), Color(0xFFFFFFFF))
        private val SecBtnBg       = ColorProvider(Color(0xFFE0E7FF), Color(0xFF312E81))
        private val SecBtnText     = ColorProvider(Color(0xFF4338CA), Color(0xFFC7D2FE))
        private val TitleColor     = ColorProvider(Color(0xFF0F172A), Color(0xFFF0F6FC))
        private val BodyColor      = ColorProvider(Color(0xFF475569), Color(0xFF8B949E))
        private val MutedColor     = ColorProvider(Color(0xFF94A3B8), Color(0xFF6E7681))
    }

    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val ctx = LocalContext.current
            val db = remember { AppDatabase.getInstance(ctx) }

            val pinnedFlow = remember(db) {
                db.noteDao()
                    .observePinnedNotes()
                    .flowMap { entities -> entities.map { NoteEntityMapper.toDomain(it) } }
            }
            val pinnedNotes by pinnedFlow.collectAsState(initial = emptyList())

            val countFlow = remember(db) { db.noteDao().observeNoteCount() }
            val totalNotes by countFlow.collectAsState(initial = 0)

            WidgetContent(pinnedNotes, totalNotes)
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // SIZE TIER CALCULATION — Adaptive layout based on actual dimensions
    // ═══════════════════════════════════════════════════════════════════════

    private enum class SizeTier { COMPACT, MEDIUM, LARGE }

    @Composable
    private fun calculateSizeTier(): SizeTier {
        val size = LocalSize.current
        val width = size.width
        val height = size.height

        return when {
            height < 120.dp || width < 180.dp -> SizeTier.COMPACT
            height < 180.dp -> SizeTier.MEDIUM
            else -> SizeTier.LARGE
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // MAIN CONTENT — Unified layout that adapts to size tier
    // ═══════════════════════════════════════════════════════════════════════

    @Composable
    private fun WidgetContent(pinnedNotes: List<dataclass>, totalNotes: Int) {
        val tier = calculateSizeTier()

        // Adaptive padding based on widget size
        val padding = when (tier) {
            SizeTier.COMPACT -> 10.dp
            SizeTier.MEDIUM -> 12.dp
            SizeTier.LARGE -> 14.dp
        }

        val gap = when (tier) {
            SizeTier.COMPACT -> 8.dp
            SizeTier.MEDIUM -> 10.dp
            SizeTier.LARGE -> 12.dp
        }

        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .cornerRadius(16.dp)
                .background(BgColor)
                .padding(padding)
        ) {
            Column(modifier = GlanceModifier.fillMaxSize()) {
                // ─── Header ────────────────────────────────────────────────
                Header(totalNotes, tier)

                Spacer(modifier = GlanceModifier.height(gap))

                // ─── Action Buttons ────────────────────────────────────────
                ActionButtons(tier)

                // ─── Pinned Notes (only for LARGE tier with space) ─────────
                if (tier == SizeTier.LARGE && pinnedNotes.isNotEmpty()) {
                    Spacer(modifier = GlanceModifier.height(gap))
                    PinnedNotesSection(pinnedNotes, tier)
                } else if (tier == SizeTier.LARGE && totalNotes == 0) {
                    Spacer(modifier = GlanceModifier.height(gap))
                    EmptyState(tier)
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // HEADER — Adapts icon/text sizes based on tier
    // ═══════════════════════════════════════════════════════════════════════

    @Composable
    private fun Header(totalNotes: Int, tier: SizeTier) {
        val iconSize = when (tier) {
            SizeTier.COMPACT -> 28.dp
            SizeTier.MEDIUM -> 32.dp
            SizeTier.LARGE -> 36.dp
        }
        val logoSize = when (tier) {
            SizeTier.COMPACT -> 18.dp
            SizeTier.MEDIUM -> 20.dp
            SizeTier.LARGE -> 22.dp
        }
        val titleSize = when (tier) {
            SizeTier.COMPACT -> 13.sp
            SizeTier.MEDIUM -> 14.sp
            SizeTier.LARGE -> 15.sp
        }

        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .clickable(actionRunCallback<WidgetActionCallback>(
                    actionParametersOf(ACTION_KEY to ACTION_OPEN_APP)
                )),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // App icon
            Box(
                modifier = GlanceModifier
                    .size(iconSize)
                    .cornerRadius(8.dp)
                    .background(PrimaryColor),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    provider = ImageProvider(R.drawable.logo2),
                    contentDescription = "SwiftNote",
                    modifier = GlanceModifier.size(logoSize)
                )
            }

            Spacer(modifier = GlanceModifier.width(8.dp))

            // Title + count
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    "SwiftNote",
                    style = TextStyle(
                        fontWeight = FontWeight.Bold,
                        fontSize = titleSize,
                        color = TitleColor
                    )
                )
                if (totalNotes > 0 && tier != SizeTier.COMPACT) {
                    Text(
                        "$totalNotes notes",
                        style = TextStyle(fontSize = 10.sp, color = MutedColor)
                    )
                }
            }

            // Open button (hide in compact)
            if (tier != SizeTier.COMPACT) {
                Box(
                    modifier = GlanceModifier
                        .cornerRadius(10.dp)
                        .background(SurfaceAltColor)
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                        .clickable(actionRunCallback<WidgetActionCallback>(
                            actionParametersOf(ACTION_KEY to ACTION_OPEN_APP)
                        ))
                ) {
                    Text(
                        "Open",
                        style = TextStyle(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = BodyColor
                        )
                    )
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ACTION BUTTONS — Adapts based on tier
    // ═══════════════════════════════════════════════════════════════════════

    @Composable
    private fun ActionButtons(tier: SizeTier) {
        val buttonPadding = when (tier) {
            SizeTier.COMPACT -> 8.dp
            SizeTier.MEDIUM -> 10.dp
            SizeTier.LARGE -> 12.dp
        }
        val fontSize = when (tier) {
            SizeTier.COMPACT -> 11.sp
            SizeTier.MEDIUM -> 12.sp
            SizeTier.LARGE -> 13.sp
        }
        val iconSize = when (tier) {
            SizeTier.COMPACT -> 12.sp
            SizeTier.MEDIUM -> 13.sp
            SizeTier.LARGE -> 14.sp
        }

        // Use short labels for compact/medium
        val noteLabel = if (tier == SizeTier.LARGE) "New Note" else "Note"
        val listLabel = if (tier == SizeTier.LARGE) "Checklist" else "List"

        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // New Note button
            Box(
                modifier = GlanceModifier
                    .defaultWeight()
                    .cornerRadius(10.dp)
                    .background(PrimaryBtnBg)
                    .padding(vertical = buttonPadding, horizontal = 8.dp)
                    .clickable(actionRunCallback<WidgetActionCallback>(
                        actionParametersOf(ACTION_KEY to ACTION_CREATE_NOTE)
                    )),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("✏️", style = TextStyle(fontSize = iconSize))
                    Spacer(modifier = GlanceModifier.width(4.dp))
                    Text(
                        noteLabel,
                        style = TextStyle(
                            fontSize = fontSize,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryBtnText
                        )
                    )
                }
            }

            Spacer(modifier = GlanceModifier.width(8.dp))

            // Checklist button
            Box(
                modifier = GlanceModifier
                    .defaultWeight()
                    .cornerRadius(10.dp)
                    .background(SecBtnBg)
                    .padding(vertical = buttonPadding, horizontal = 8.dp)
                    .clickable(actionRunCallback<WidgetActionCallback>(
                        actionParametersOf(ACTION_KEY to ACTION_CREATE_CHECKLIST)
                    )),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("☑️", style = TextStyle(fontSize = iconSize))
                    Spacer(modifier = GlanceModifier.width(4.dp))
                    Text(
                        listLabel,
                        style = TextStyle(
                            fontSize = fontSize,
                            fontWeight = FontWeight.Bold,
                            color = SecBtnText
                        )
                    )
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // PINNED NOTES SECTION
    // ═══════════════════════════════════════════════════════════════════════

    @Composable
    private fun PinnedNotesSection(notes: List<dataclass>, tier: SizeTier) {
        Column(modifier = GlanceModifier.fillMaxSize()) {
            // Section header
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "📌 PINNED",
                    style = TextStyle(
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MutedColor
                    )
                )
                Spacer(modifier = GlanceModifier.width(6.dp))
                Box(
                    modifier = GlanceModifier
                        .cornerRadius(6.dp)
                        .background(SecBtnBg)
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text(
                        "${notes.size}",
                        style = TextStyle(
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = SecBtnText
                        )
                    )
                }
            }

            Spacer(modifier = GlanceModifier.height(8.dp))

            // Scrollable notes list
            LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                itemsIndexed(notes) { index, note ->
                    NoteCard(note, index, isLast = index == notes.lastIndex, tier = tier)
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // NOTE CARD
    // ═══════════════════════════════════════════════════════════════════════

    @Composable
    private fun NoteCard(note: dataclass, index: Int, isLast: Boolean, tier: SizeTier) {
        val accentColor = ColorProvider(
            ACCENT_COLORS[index % ACCENT_COLORS.size],
            ACCENT_COLORS[index % ACCENT_COLORS.size]
        )
        val timeStr = formatRelativeTime(note.updatedAt)
        val hasChecklist = note.description.startsWith("[[CHECKLIST_V1]]")

        // Strip HTML tags so rich-text markup (<p>, <b>, etc.) doesn't show as raw text in the widget.
        val plainTitle = stripHtmlSafe(note.title)
        val plainDescription = if (hasChecklist) note.description else stripHtmlSafe(note.description)

        val cardPadding = if (tier == SizeTier.LARGE) 10.dp else 8.dp
        val cardGap = if (tier == SizeTier.LARGE) 8.dp else 6.dp

        Box(
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(bottom = if (isLast) 0.dp else cardGap)
        ) {
            Box(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .cornerRadius(12.dp)
                    .background(SurfaceColor)
                    .clickable(actionRunCallback<WidgetActionCallback>(
                        actionParametersOf(ACTION_KEY to ACTION_OPEN_NOTE, NOTE_ID_KEY to note.id)
                    ))
            ) {
                Row(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .padding(cardPadding),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Accent indicator
                    Box(
                        modifier = GlanceModifier
                            .size(width = 3.dp, height = 32.dp)
                            .cornerRadius(2.dp)
                            .background(accentColor)
                    ) {}

                    Spacer(modifier = GlanceModifier.width(10.dp))

                    // Content
                    Column(modifier = GlanceModifier.defaultWeight()) {
                        Text(
                            text = if (hasChecklist) "☑ ${plainTitle.take(28).ifEmpty { "Untitled" }}"
                                   else plainTitle.take(32).ifEmpty { "Untitled" },
                            style = TextStyle(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = TitleColor
                            ),
                            maxLines = 1
                        )

                        if (!hasChecklist && plainDescription.isNotBlank()) {
                            Spacer(modifier = GlanceModifier.height(2.dp))
                            Text(
                                text = plainDescription.take(36).replace("\n", " "),
                                style = TextStyle(fontSize = 10.sp, color = BodyColor),
                                maxLines = 1
                            )
                        }

                        if (timeStr.isNotEmpty()) {
                            Spacer(modifier = GlanceModifier.height(3.dp))
                            Text(
                                text = timeStr,
                                style = TextStyle(fontSize = 9.sp, color = MutedColor)
                            )
                        }
                    }

                    Text(
                        "›",
                        style = TextStyle(
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = MutedColor
                        )
                    )
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // EMPTY STATE
    // ═══════════════════════════════════════════════════════════════════════

    @Composable
    private fun EmptyState(tier: SizeTier) {
        Box(
            modifier = GlanceModifier
                .fillMaxWidth()
                .cornerRadius(12.dp)
                .background(SurfaceAltColor)
                .padding(16.dp)
                .clickable(actionRunCallback<WidgetActionCallback>(
                    actionParametersOf(ACTION_KEY to ACTION_CREATE_NOTE)
                )),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("📝", style = TextStyle(fontSize = 20.sp))
                Spacer(modifier = GlanceModifier.height(6.dp))
                Text(
                    "No notes yet",
                    style = TextStyle(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = TitleColor
                    )
                )
                Spacer(modifier = GlanceModifier.height(3.dp))
                Text(
                    "Tap to create your first note",
                    style = TextStyle(
                        fontSize = 11.sp,
                        color = BodyColor,
                        textAlign = TextAlign.Center
                    )
                )
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════════════════════════════════════

    /** Strip rich-text HTML to plain text so widget cells don't render <p>/<b>/etc as literal markup. */
    private fun stripHtmlSafe(raw: String): String {
        if (raw.isBlank()) return raw
        return try {
            RichTextBridge.stripHtmlToPlainText(raw).trim()
        } catch (_: Throwable) {
            // Fallback: regex strip + entity decode, identical to Note.isBlank()'s cheap path.
            raw.replace(Regex("<[^>]+>"), "")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .trim()
        }
    }

    private fun formatRelativeTime(timestamp: Long): String {
        if (timestamp <= 0) return ""
        val diff = System.currentTimeMillis() - timestamp
        return when {
            diff < 60_000       -> "just now"
            diff < 3_600_000    -> "${diff / 60_000}m ago"
            diff < 86_400_000   -> "${diff / 3_600_000}h ago"
            diff < 604_800_000  -> "${diff / 86_400_000}d ago"
            else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(timestamp))
        }
    }
}

/**
 * ActionCallback — launches MainActivity with the correct action + optional noteId.
 */
class WidgetActionCallback : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val action = parameters[QuickNoteWidget.ACTION_KEY] ?: return
        val noteId = parameters[QuickNoteWidget.NOTE_ID_KEY]
        context.startActivity(
            Intent(context, MainActivity::class.java).apply {
                this.action = action
                noteId?.let { putExtra("noteId", it) }
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
        )
    }
}
