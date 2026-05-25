package com.amvarpvtltd.swiftNote.share

/**
 * Phase 5: Simple in-memory holder for passing shared content to the AddScreen.
 * Consumed once on read (values are cleared after retrieval).
 */
object SharedNoteData {
    var title: String = ""
    var description: String = ""
    var startAsChecklist: Boolean = false

    /**
     * Consume and clear all pending shared data.
     * Returns triple of (title, description, isChecklist).
     */
    fun consume(): Triple<String, String, Boolean> {
        val result = Triple(title, description, startAsChecklist)
        title = ""
        description = ""
        startAsChecklist = false
        return result
    }

    val hasPendingData: Boolean
        get() = title.isNotEmpty() || description.isNotEmpty() || startAsChecklist
}

