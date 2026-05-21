package com.amvarpvtltd.swiftNote.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.amvarpvtltd.swiftNote.ai.DetectedReminder
import com.amvarpvtltd.swiftNote.dataclass
import com.amvarpvtltd.swiftNote.reminders.ReminderManager
import com.amvarpvtltd.swiftNote.repository.NoteRepository
import com.amvarpvtltd.swiftNote.utils.NetworkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ViewModel for the Add/Edit Note screen.
 * Handles I/O operations (save, load, delete) leaving UI-only state in the Composable.
 */
class AddNoteViewModel(application: Application) : AndroidViewModel(application) {

    private val TAG = "AddNoteViewModel"
    private val context = application.applicationContext
    private val noteRepository = NoteRepository.getInstance(context)
    private val reminderManager = ReminderManager.getInstance(context)
    private val networkManager = NetworkManager.getInstance(context)

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _loadedNote = MutableStateFlow<dataclass?>(null)
    val loadedNote: StateFlow<dataclass?> = _loadedNote.asStateFlow()

    // One-shot UI events
    private val _uiEvent = MutableSharedFlow<UiEvent>()
    val uiEvent: SharedFlow<UiEvent> = _uiEvent.asSharedFlow()

    sealed class UiEvent {
        data class ShowToast(val message: String) : UiEvent()
        object NavigateToMain : UiEvent()
        data class NoteSaved(val noteId: String) : UiEvent()
    }

    /**
     * Load an existing note for editing.
     */
    fun loadNote(noteId: String?) {
        if (noteId == null) return

        viewModelScope.launch {
            _isLoading.value = true
            try {
                val result = withContext(Dispatchers.IO) {
                    noteRepository.loadNote(noteId, context)
                }
                if (result.isSuccess) {
                    _loadedNote.value = result.getOrNull()
                } else {
                    _uiEvent.emit(UiEvent.ShowToast("Error loading note: ${result.exceptionOrNull()?.message}"))
                }
            } catch (e: Exception) {
                _uiEvent.emit(UiEvent.ShowToast("Error loading note: ${e.message}"))
                Log.e(TAG, "Error loading note", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Save a note (create new or update existing). Handles:
     * - Offline-first save to Room
     * - Pending reminder creation
     * - Network status-aware toast messages
     */
    fun saveNote(
        title: String,
        description: String,
        noteId: String?,
        pendingReminders: List<DetectedReminder>,
        onRemindersCreated: (Int) -> Unit = {}
    ) {
        _isSaving.value = true

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val result = noteRepository.saveNote(title, description, noteId, context)

                if (result.isSuccess) {
                    val savedNoteId = result.getOrNull() ?: noteId

                    // Create any pending smart reminders
                    if (savedNoteId != null && pendingReminders.isNotEmpty()) {
                        var createdCount = 0
                        pendingReminders.forEach { reminder ->
                            if (reminder.confidence >= 0.6f) {
                                try {
                                    if (reminderManager.createReminderFromDetection(reminder, savedNoteId)) {
                                        createdCount++
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error creating pending reminder", e)
                                }
                            }
                        }
                        if (createdCount > 0) {
                            withContext(Dispatchers.Main) { onRemindersCreated(createdCount) }
                            _uiEvent.emit(UiEvent.ShowToast(
                                "🤖 Auto-created $createdCount smart reminder${if (createdCount > 1) "s" else ""}"
                            ))
                        }
                    }

                    // Show save message based on network status
                    val message = if (networkManager.isConnected()) {
                        "✅ Note saved"
                    } else {
                        "📱 Note saved offline. Will sync when online."
                    }
                    _uiEvent.emit(UiEvent.ShowToast(message))
                    _uiEvent.emit(UiEvent.NavigateToMain)
                } else {
                    _uiEvent.emit(UiEvent.ShowToast("Error saving note"))
                }
            } catch (e: Exception) {
                _uiEvent.emit(UiEvent.ShowToast("Error saving note: ${e.message}"))
                Log.e(TAG, "Error saving note", e)
            } finally {
                _isSaving.value = false
            }
        }
    }

    /**
     * Delete a note by ID.
     */
    fun deleteNote(noteId: String?) {
        if (noteId == null) return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val result = noteRepository.deleteNote(noteId, context)
                if (result.isSuccess) {
                    if (!networkManager.isConnected()) {
                        _uiEvent.emit(UiEvent.ShowToast("📱 Note deleted offline. Will sync when online."))
                    }
                    _uiEvent.emit(UiEvent.NavigateToMain)
                } else {
                    _uiEvent.emit(UiEvent.ShowToast("Error deleting note"))
                }
            } catch (e: Exception) {
                _uiEvent.emit(UiEvent.ShowToast("Error deleting note: ${e.message}"))
                Log.e(TAG, "Error deleting note", e)
            }
        }
    }
}


