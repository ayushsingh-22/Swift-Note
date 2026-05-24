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
import com.amvarpvtltd.swiftNote.room.AppDatabase
import com.amvarpvtltd.swiftNote.room.NoteEntityMapper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.map as flowMap

/**
 * Phase 5B: SwiftNote Widget — size-adaptive Glance UI.
 *
 * Size tiers (based on LocalSize.current.height):
 *  • SMALL  (< 130dp) — header + action buttons only, compact spacing/fonts
 *  • LARGE  (≥ 130dp) — header + action buttons + pinned notes section
 *
 * Switching from SizeMode.Single → SizeMode.Exact so the widget receives its
 * real on-screen bounds and LocalSize works correctly.
 */
class QuickNoteWidget : GlanceAppWidget() {

    companion object {
        private const val TAG = "QuickNoteWidget"
        val ACTION_KEY    = ActionParameters.Key<String>("widget_action")
        val NOTE_ID_KEY   = ActionParameters.Key<String>("widget_note_id")

        const val ACTION_CREATE_NOTE      = "com.amvarpvtltd.swiftNote.ACTION_CREATE_NOTE"
        const val ACTION_CREATE_CHECKLIST = "com.amvarpvtltd.swiftNote.ACTION_CREATE_CHECKLIST"
        const val ACTION_OPEN_NOTE        = "com.amvarpvtltd.swiftNote.ACTION_OPEN_NOTE"
        const val ACTION_OPEN_APP         = "com.amvarpvtltd.swiftNote.ACTION_OPEN_APP"

        // Height threshold that separates small ↔ large
        val LARGE_THRESHOLD = 130.dp

        // Accent colours for note cards
        private val ACCENT_COLORS = listOf(
            Color(0xFF6366F1), Color(0xFF8B5CF6),
            Color(0xFF06B6D4), Color(0xFF10B981), Color(0xFFF59E0B),
        )

        // ─── Colour palette (day / night) ──────────────────────────────
        val BgColor        = ColorProvider(Color(0xFFF3F4FF), Color(0xFF0F1117))
        val CardColor      = ColorProvider(Color(0xFFFFFFFF), Color(0xFF1A1F2E))
        val CardAltColor   = ColorProvider(Color(0xFFF8F8FF), Color(0xFF1E2433))
        val DividerColor   = ColorProvider(Color(0xFFE8E8F0), Color(0xFF2A2F3E))
        val PrimaryColor   = ColorProvider(Color(0xFF6366F1), Color(0xFF818CF8))
        val PrimaryBtnBg   = ColorProvider(Color(0xFF6366F1), Color(0xFF3730A3))
        val PrimaryBtnText = ColorProvider(Color(0xFFFFFFFF), Color(0xFFE0E7FF))
        val SecBtnBg       = ColorProvider(Color(0xFFEEF2FF), Color(0xFF1E2040))
        val SecBtnText     = ColorProvider(Color(0xFF4F46E5), Color(0xFFA5B4FC))
        val TitleColor     = ColorProvider(Color(0xFF0F172A), Color(0xFFECF0F7))
        val SubtitleColor  = ColorProvider(Color(0xFF64748B), Color(0xFF8B949E))
        val MutedColor     = ColorProvider(Color(0xFF94A3B8), Color(0xFF6B7280))
    }

    // Exact mode → widget passes real on-screen size into LocalSize
    override val sizeMode = SizeMode.Exact

    // ─── Data loading — reactive via Room Flow ────────────────────────────
    //
    // KEY FIX: Data is subscribed INSIDE provideContent { }.
    // Glance keeps the composition alive, so collectAsState() re-renders the
    // widget automatically every time pinned notes or the note count changes —
    // no manual updateAll() call needed from the app side.

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val ctx = LocalContext.current
            val db = remember { AppDatabase.getInstance(ctx) }

            // Live pinned notes — decrypted via NoteEntityMapper so widget shows
            // real title/content instead of the raw AES-GCM ciphertext stored in Room.
            val pinnedNotes by db.noteDao()
                .observePinnedNotes()
                .flowMap { entities ->
                    entities.map { NoteEntityMapper.toDomain(it) }
                }
                .collectAsState(initial = emptyList())

            // Live total count for the header subtitle
            val totalNotes by db.noteDao()
                .observeNoteCount()
                .collectAsState(initial = 0)

            WidgetRoot(pinnedNotes.take(3), totalNotes)
        }
    }

    // ─── Root — picks layout tier from real widget height ─────────────────

    @Composable
    private fun WidgetRoot(pinnedNotes: List<dataclass>, totalNotes: Int) {
        val widgetHeight = LocalSize.current.height
        val isLarge = widgetHeight >= LARGE_THRESHOLD

        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .cornerRadius(24.dp)
                .background(BgColor),
            contentAlignment = Alignment.TopStart
        ) {
            if (isLarge) {
                LargeLayout(pinnedNotes, totalNotes)
            } else {
                SmallLayout(totalNotes)
            }
        }
    }

    // ─── SMALL layout (< 130dp tall) ──────────────────────────────────────
    //  Contains: compact header row + action buttons, tight padding & fonts

    @Composable
    private fun SmallLayout(totalNotes: Int) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            // Compact header
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = GlanceModifier
                        .size(24.dp)
                        .cornerRadius(8.dp)
                        .background(PrimaryColor),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        provider = ImageProvider(R.drawable.logo2),
                        contentDescription = "SwiftNote",
                        modifier = GlanceModifier.size(16.dp)
                    )
                }
                Spacer(modifier = GlanceModifier.width(8.dp))
                Text(
                    "SwiftNote",
                    style = TextStyle(
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = TitleColor
                    )
                )
                Spacer(modifier = GlanceModifier.defaultWeight())
                if (totalNotes > 0) {
                    Text(
                        "$totalNotes notes",
                        style = TextStyle(fontSize = 10.sp, color = MutedColor)
                    )
                }
            }

            Spacer(modifier = GlanceModifier.height(8.dp))

            // Thin divider
            Spacer(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(DividerColor)
            )

            Spacer(modifier = GlanceModifier.height(8.dp))

            // Compact action buttons — equal weight, smaller padding
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = GlanceModifier
                        .defaultWeight()
                        .cornerRadius(14.dp)
                        .background(PrimaryBtnBg)
                        .padding(vertical = 9.dp, horizontal = 4.dp)
                        .clickable(actionRunCallback<WidgetActionCallback>(
                            actionParametersOf(ACTION_KEY to ACTION_CREATE_NOTE)
                        )),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("✏️", style = TextStyle(fontSize = 13.sp))
                        Spacer(modifier = GlanceModifier.width(4.dp))
                        Text(
                            "Note",
                            style = TextStyle(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryBtnText
                            )
                        )
                    }
                }

                Spacer(modifier = GlanceModifier.width(8.dp))

                Box(
                    modifier = GlanceModifier
                        .defaultWeight()
                        .cornerRadius(14.dp)
                        .background(SecBtnBg)
                        .padding(vertical = 9.dp, horizontal = 4.dp)
                        .clickable(actionRunCallback<WidgetActionCallback>(
                            actionParametersOf(ACTION_KEY to ACTION_CREATE_CHECKLIST)
                        )),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("☑️", style = TextStyle(fontSize = 13.sp))
                        Spacer(modifier = GlanceModifier.width(4.dp))
                        Text(
                            "List",
                            style = TextStyle(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = SecBtnText
                            )
                        )
                    }
                }
            }
        }
    }

    // ─── LARGE layout (≥ 130dp tall) ──────────────────────────────────────
    //  Contains: full header + action buttons + pinned notes / empty state

    @Composable
    private fun LargeLayout(pinnedNotes: List<dataclass>, totalNotes: Int) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 14.dp)
        ) {
            FullHeader(totalNotes)

            Spacer(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(DividerColor)
            )

            Spacer(modifier = GlanceModifier.height(12.dp))

            FullActionButtons()

            when {
                pinnedNotes.isNotEmpty() -> {
                    Spacer(modifier = GlanceModifier.height(14.dp))
                    NotesSection(pinnedNotes)
                }
                totalNotes == 0 -> {
                    Spacer(modifier = GlanceModifier.height(12.dp))
                    EmptyState()
                }
            }
        }
    }

    // ─── Full header (large layout) ────────────────────────────────────────

    @Composable
    private fun FullHeader(totalNotes: Int) {
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
                .clickable(actionRunCallback<WidgetActionCallback>(
                    actionParametersOf(ACTION_KEY to ACTION_OPEN_APP)
                )),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = GlanceModifier
                    .size(32.dp)
                    .cornerRadius(10.dp)
                    .background(PrimaryColor),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    provider = ImageProvider(R.drawable.logo2),
                    contentDescription = "SwiftNote",
                    modifier = GlanceModifier.size(20.dp)
                )
            }

            Spacer(modifier = GlanceModifier.width(10.dp))

            Column {
                Text(
                    "SwiftNote",
                    style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TitleColor)
                )
                if (totalNotes > 0) {
                    Text(
                        "$totalNotes notes",
                        style = TextStyle(fontSize = 10.sp, color = SubtitleColor)
                    )
                }
            }

            Spacer(modifier = GlanceModifier.defaultWeight())

            Box(
                modifier = GlanceModifier
                    .cornerRadius(20.dp)
                    .background(SecBtnBg)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
                    .clickable(actionRunCallback<WidgetActionCallback>(
                        actionParametersOf(ACTION_KEY to ACTION_OPEN_APP)
                    ))
            ) {
                Text(
                    "Open",
                    style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, color = SecBtnText)
                )
            }
        }
    }

    // ─── Full action buttons (large layout) ────────────────────────────────

    @Composable
    private fun FullActionButtons() {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = GlanceModifier
                    .defaultWeight()
                    .cornerRadius(16.dp)
                    .background(PrimaryBtnBg)
                    .padding(vertical = 11.dp, horizontal = 6.dp)
                    .clickable(actionRunCallback<WidgetActionCallback>(
                        actionParametersOf(ACTION_KEY to ACTION_CREATE_NOTE)
                    )),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("✏️", style = TextStyle(fontSize = 15.sp))
                    Spacer(modifier = GlanceModifier.width(5.dp))
                    Text(
                        "New Note",
                        style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = PrimaryBtnText)
                    )
                }
            }

            Spacer(modifier = GlanceModifier.width(10.dp))

            Box(
                modifier = GlanceModifier
                    .defaultWeight()
                    .cornerRadius(16.dp)
                    .background(SecBtnBg)
                    .padding(vertical = 11.dp, horizontal = 6.dp)
                    .clickable(actionRunCallback<WidgetActionCallback>(
                        actionParametersOf(ACTION_KEY to ACTION_CREATE_CHECKLIST)
                    )),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("☑️", style = TextStyle(fontSize = 15.sp))
                    Spacer(modifier = GlanceModifier.width(5.dp))
                    Text(
                        "Checklist",
                        style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = SecBtnText)
                    )
                }
            }
        }
    }

    // ─── Pinned notes section ──────────────────────────────────────────────

    @Composable
    private fun NotesSection(notes: List<dataclass>) {
        Column(modifier = GlanceModifier.fillMaxWidth()) {
            Row(
                modifier = GlanceModifier.fillMaxWidth().padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "PINNED",
                    style = TextStyle(
                        fontSize = 10.sp, fontWeight = FontWeight.Bold,
                        color = MutedColor, textAlign = TextAlign.Start
                    )
                )
                Spacer(modifier = GlanceModifier.width(6.dp))
                Box(
                    modifier = GlanceModifier
                        .cornerRadius(10.dp)
                        .background(SecBtnBg)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        "${notes.size}",
                        style = TextStyle(fontSize = 9.sp, fontWeight = FontWeight.Bold, color = SecBtnText)
                    )
                }
            }

            notes.forEachIndexed { index, note ->
                NoteCard(note, index)
                if (index < notes.lastIndex) Spacer(modifier = GlanceModifier.height(6.dp))
            }
        }
    }

    // ─── Single note card ──────────────────────────────────────────────────

    @Composable
    private fun NoteCard(note: dataclass, index: Int) {
        val accentColor = ColorProvider(
            ACCENT_COLORS[index % ACCENT_COLORS.size],
            ACCENT_COLORS[index % ACCENT_COLORS.size]
        )
        val timeStr = formatRelativeTime(note.updatedAt)
        val hasChecklist = note.description.startsWith("[[CHECKLIST_V1]]")
        val typeEmoji = if (hasChecklist) "☑ " else ""

        Box(
            modifier = GlanceModifier
                .fillMaxWidth()
                .cornerRadius(14.dp)
                .background(CardColor)
                .clickable(actionRunCallback<WidgetActionCallback>(
                    actionParametersOf(ACTION_KEY to ACTION_OPEN_NOTE, NOTE_ID_KEY to note.id)
                ))
        ) {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = GlanceModifier
                        .padding(start = 12.dp)
                        .size(8.dp)
                        .cornerRadius(4.dp)
                        .background(accentColor)
                ) {}

                Column(
                    modifier = GlanceModifier
                        .defaultWeight()
                        .padding(start = 10.dp, top = 10.dp, bottom = 10.dp, end = 8.dp)
                ) {
                    Text(
                        text = "$typeEmoji${note.title.take(32).ifEmpty { "Untitled" }}",
                        style = TextStyle(
                            fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TitleColor
                        ),
                        maxLines = 1
                    )
                    if (!hasChecklist && note.description.isNotBlank()) {
                        Spacer(modifier = GlanceModifier.height(2.dp))
                        Text(
                            text = note.description.take(40).replace("\n", " "),
                            style = TextStyle(fontSize = 11.sp, color = SubtitleColor),
                            maxLines = 1
                        )
                    }
                    if (timeStr.isNotEmpty()) {
                        Spacer(modifier = GlanceModifier.height(3.dp))
                        Text(
                            text = timeStr,
                            style = TextStyle(fontSize = 10.sp, color = MutedColor)
                        )
                    }
                }

                Text(
                    "›",
                    style = TextStyle(
                        fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MutedColor
                    ),
                    modifier = GlanceModifier.padding(end = 12.dp)
                )
            }
        }
    }

    // ─── Empty state ───────────────────────────────────────────────────────

    @Composable
    private fun EmptyState() {
        Box(
            modifier = GlanceModifier
                .fillMaxWidth()
                .cornerRadius(16.dp)
                .background(CardAltColor)
                .padding(vertical = 18.dp, horizontal = 16.dp)
                .clickable(actionRunCallback<WidgetActionCallback>(
                    actionParametersOf(ACTION_KEY to ACTION_CREATE_NOTE)
                )),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("📝", style = TextStyle(fontSize = 22.sp))
                Spacer(modifier = GlanceModifier.height(5.dp))
                Text(
                    "No notes yet",
                    style = TextStyle(
                        fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TitleColor
                    )
                )
                Spacer(modifier = GlanceModifier.height(2.dp))
                Text(
                    "Tap New Note to get started",
                    style = TextStyle(
                        fontSize = 11.sp, color = SubtitleColor, textAlign = TextAlign.Center
                    )
                )
            }
        }
    }

    // ─── Helpers ───────────────────────────────────────────────────────────

    private fun formatRelativeTime(timestamp: Long): String {
        if (timestamp <= 0) return ""
        val diff = System.currentTimeMillis() - timestamp
        return when {
            diff < 60_000      -> "just now"
            diff < 3_600_000   -> "${diff / 60_000}m ago"
            diff < 86_400_000  -> "${diff / 3_600_000}h ago"
            diff < 604_800_000 -> "${diff / 86_400_000}d ago"
            else               -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(timestamp))
        }
    }
}

/**
 * ActionCallback — launches MainActivity with the correct action + optional noteId.
 */
class WidgetActionCallback : ActionCallback {
    override suspend fun onAction(
        context: Context, glanceId: GlanceId, parameters: ActionParameters
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
