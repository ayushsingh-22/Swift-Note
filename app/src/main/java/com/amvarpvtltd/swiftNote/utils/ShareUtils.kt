package com.amvarpvtltd.swiftNote.utils

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.amvarpvtltd.swiftNote.dataclass
import com.amvarpvtltd.swiftNote.richtext.RichTextBridge

object ShareUtils {

    /**
     * Share a single note to other apps
     */
    fun shareNote(context: Context, note: dataclass) {
        try {
            val shareText = formatNoteForSharing(note)
            val shareIntent = Intent().apply {
                action = Intent.ACTION_SEND
                type = Constants.SHARE_MIME_TYPE
                putExtra(Intent.EXTRA_SUBJECT, Constants.SHARE_SUBJECT)
                putExtra(Intent.EXTRA_TEXT, shareText)
            }

            val chooserIntent = Intent.createChooser(shareIntent, "Share note via...")
            chooserIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(chooserIntent)

        } catch (e: Exception) {
            Toast.makeText(context, "❌ Error sharing note", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Copy note content to clipboard
     */
    fun copyNoteToClipboard(context: Context, note: dataclass) {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val noteText = formatNoteForSharing(note)
            val clip = ClipData.newPlainText("SwiftNote - ${note.title}", noteText)
            clipboard.setPrimaryClip(clip)

            Toast.makeText(context, "📋 Note copied to clipboard", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "❌ Error copying note", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Format a single note for sharing with enhanced formatting.
     * Strips HTML tags so the shared/copied text is clean plain text.
     */
    private fun formatNoteForSharing(note: dataclass): String {
        return buildString {
            // Title
            val plainTitle = RichTextBridge.stripHtmlToPlainText(note.title)
            if (plainTitle.isNotBlank()) {
                appendLine(plainTitle)
                appendLine()
            }

            // Description — strip HTML so recipients see formatted text, not tags
            if (note.description.isNotEmpty()) {
                val plainDescription = RichTextBridge.stripHtmlToPlainText(note.description)
                appendLine(plainDescription.trim())
            }
        }.trim()
    }

}
