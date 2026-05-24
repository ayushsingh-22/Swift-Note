package com.amvarpvtltd.swiftNote.test

import android.content.Context
import android.util.Log
import com.amvarpvtltd.swiftNote.Note
import com.amvarpvtltd.swiftNote.repository.NoteRepository
import com.amvarpvtltd.swiftNote.security.EncryptionUtil
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Call this from any Activity/Composable to seed test data that exercises Smart Action Chips.
 * The notes will be saved locally AND synced to Firebase under device ID "2ae571a095343c67".
 *
 * Usage: SmartChipsTestDataSeeder.seedTestData(context)
 *
 * This seeds data both locally (via NoteRepository) and directly to Firebase under the
 * target device ID path so that the Smart Action Chips feature can be verified.
 */
object SmartChipsTestDataSeeder {
    private const val TAG = "TestDataSeeder"
    private const val TARGET_DEVICE_ID = "2ae571a095343c67"

    private val testNotes = listOf(
        "Smart Chips: Phone Test" to
            "Call John at 9876543210 for the meeting. His office number is +91-11-2345-6789. Also reach out to Sarah at (555) 123-4567.",

        "Smart Chips: Email Test" to
            "Send the report to john.doe@example.com by Friday. CC marketing@company.org and dev-team@startup.io for review.",

        "Smart Chips: URL Test" to
            "Check the docs at https://developer.android.com/jetpack/compose and the repo at https://github.com/example/project. Also see http://stackoverflow.com/questions/123456.",

        "Smart Chips: Address Test" to
            "Meeting location: 221B Baker Street, London. Backup venue: 1600 Amphitheatre Parkway, Mountain View, CA 94043.",

        "Smart Chips: Mixed Entities" to
            "Call Dr. Sharma at 9988776655 tomorrow at 5 PM. Email confirmation to appointments@clinic.com. Clinic address: 42 M.G. Road, Bangalore 560001. Website: https://drsharma-clinic.com/book"
    )

    /**
     * Seeds test notes both locally and to Firebase under device "2ae571a095343c67".
     * Call once to populate test data that exercises the Smart Action Chips feature.
     */
    fun seedTestData(context: Context) {
        val repository = NoteRepository.getInstance(context)
        val scope = CoroutineScope(Dispatchers.IO)

        scope.launch {
            // Save locally via standard repository (uses current device identity)
            testNotes.forEach { (title, description) ->
                try {
                    repository.saveNote(
                        title = title,
                        description = description,
                        noteId = null,
                        context = context
                    )
                    Log.d(TAG, "✅ Seeded locally: $title")
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Failed to seed locally: $title", e)
                }
            }

            // Also push directly to Firebase under the target device ID path
            pushToFirebaseForDevice()

            Log.d(TAG, "🎉 All test notes seeded! Check Firebase path: users/$TARGET_DEVICE_ID/notes/$TARGET_DEVICE_ID")
        }
    }

    /**
     * Push encrypted test notes directly to Firebase under the target device ID.
     * This ensures data exists on Firebase for device "2ae571a095343c67" regardless
     * of what the current device's identity is.
     */
    private suspend fun pushToFirebaseForDevice() {
        val db = FirebaseDatabase.getInstance()
        val basePath = "users/$TARGET_DEVICE_ID/notes/$TARGET_DEVICE_ID"

        testNotes.forEach { (title, description) ->
            try {
                val note = Note(
                    title = title,
                    description = description,
                    mymobiledeviceid = TARGET_DEVICE_ID
                )
                // Encrypt using the target device ID as key
                val encTitle = EncryptionUtil.encrypt(title, TARGET_DEVICE_ID)
                val encDesc = EncryptionUtil.encrypt(description, TARGET_DEVICE_ID)

                val noteData = mapOf(
                    "title" to encTitle,
                    "description" to encDesc,
                    "id" to note.id,
                    "mymobiledeviceid" to TARGET_DEVICE_ID,
                    "timestamp" to note.timestamp,
                    "updatedAt" to note.updatedAt
                )

                db.getReference(basePath).child(note.id).setValue(noteData).await()
                Log.d(TAG, "✅ Pushed to Firebase: $title (id=${note.id})")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Firebase push failed: $title", e)
            }
        }
    }
}


