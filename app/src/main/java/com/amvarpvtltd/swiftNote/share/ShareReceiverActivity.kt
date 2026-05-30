package com.amvarpvtltd.swiftNote.share

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.amvarpvtltd.swiftNote.richtext.RichTextSanitizer
import com.amvarpvtltd.swiftNote.ui.theme.SelfNoteTheme

/**
 * Phase 5A: Share-to-SwiftNote
 * Receives ACTION_SEND intents from other apps and shows the QuickCaptureSheet.
 * Themed as a dialog overlay so the source app remains visible beneath.
 */
class ShareReceiverActivity : ComponentActivity() {

    companion object {
        private const val TAG = "ShareReceiver"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val sharedText = intent?.getStringExtra(Intent.EXTRA_TEXT) ?: ""
        val sharedSubject = intent?.getStringExtra(Intent.EXTRA_SUBJECT) ?: ""
        val sharedHtml = intent?.getStringExtra(Intent.EXTRA_HTML_TEXT)

        Log.d(TAG, "Received share: subject='$sharedSubject', text length=${sharedText.length}, hasHtml=${!sharedHtml.isNullOrBlank()}")

        // Determine smart title from shared content
        val title = when {
            sharedSubject.isNotBlank() -> sharedSubject
            sharedText.matches(Regex("^https?://\\S+$")) -> "Saved Link"
            sharedText.length <= 50 -> sharedText.take(50)
            else -> ""
        }

        // Phase 3: Use sanitized HTML if available, otherwise fall back to plain text
        val description = if (!sharedHtml.isNullOrBlank()) {
            RichTextSanitizer.sanitize(sharedHtml)
        } else {
            sharedText
        }

        setContent {
            SelfNoteTheme {
                QuickCaptureSheet(
                    initialTitle = title,
                    initialDescription = description,
                    onSave = { savedTitle, savedDescription, category ->
                        saveAndFinish(savedTitle, savedDescription, category)
                    },
                    onEdit = { editTitle, editDescription, category ->
                        openFullEditor(editTitle, editDescription, category)
                    },
                    onDismiss = { finish() }
                )
            }
        }
    }

    private fun saveAndFinish(title: String, description: String, category: String) {
        val intent = Intent(this, com.amvarpvtltd.swiftNote.MainActivity::class.java).apply {
            action = "com.amvarpvtltd.swiftNote.ACTION_QUICK_SAVE"
            putExtra("quick_title", title)
            putExtra("quick_description", description)
            putExtra("quick_category", category)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        startActivity(intent)
        finish()
    }

    private fun openFullEditor(title: String, description: String, category: String) {
        val intent = Intent(this, com.amvarpvtltd.swiftNote.MainActivity::class.java).apply {
            action = "com.amvarpvtltd.swiftNote.ACTION_OPEN_EDITOR"
            putExtra("shared_title", title)
            putExtra("shared_description", description)
            putExtra("shared_category", category)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        startActivity(intent)
        finish()
    }
}

