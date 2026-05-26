package com.amvarpvtltd.swiftNote.test

import android.content.Context
import android.util.Log
import com.amvarpvtltd.swiftNote.repository.NoteRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Call this from any Activity/Composable (DEBUG builds only) to seed test data
 * that exercises Smart Action Chips.
 *
 * Notes are saved locally via the standard NoteRepository and will sync to
 * whatever Firebase identity this device is currently using. No hardcoded paths.
 *
 * Usage: SmartChipsTestDataSeeder.seedTestData(context)
 */
object SmartChipsTestDataSeeder {
    private const val TAG = "TestDataSeeder"

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
     * Seeds test notes locally via NoteRepository.
     * Notes will sync to Firebase under the current device identity — no hardcoded paths.
     */
    fun seedTestData(context: Context) {
        val repository = NoteRepository.getInstance(context)
        val scope = CoroutineScope(Dispatchers.IO)

        scope.launch {
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
            Log.d(TAG, "🎉 Test notes seeded locally — they will sync to the current device's Firebase identity.")
        }
    }
}
