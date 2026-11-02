package com.amvarpvtltd.swiftNote.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.flow.StateFlow

/** Retains a notification target until initialization and the navigation graph are ready. */
class NotificationNavigationViewModel(
    application: Application,
    private val savedState: SavedStateHandle
) : AndroidViewModel(application) {
    val pendingNoteId: StateFlow<String?> = savedState.getStateFlow(KEY_PENDING_NOTE, null)

    fun openNote(noteId: String?) {
        if (!noteId.isNullOrBlank()) savedState[KEY_PENDING_NOTE] = noteId
    }

    fun markOpened(noteId: String) {
        if (pendingNoteId.value == noteId) savedState[KEY_PENDING_NOTE] = null
    }

    private companion object {
        const val KEY_PENDING_NOTE = "notification_pending_note"
    }
}
