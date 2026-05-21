package com.amvarpvtltd.swiftNote.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.amvarpvtltd.swiftNote.dataclass
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
 * ViewModel for the View Note screen.
 * Extracts note loading and deletion logic from the Composable.
 */
class ViewNoteViewModel(application: Application) : AndroidViewModel(application) {

    private val TAG = "ViewNoteViewModel"
    private val context = application.applicationContext
    private val noteRepository = NoteRepository.getInstance(context)
    private val networkManager = NetworkManager.getInstance(context)

    private val _note = MutableStateFlow<dataclass?>(null)
    val note: StateFlow<dataclass?> = _note.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // One-shot UI events
    private val _uiEvent = MutableSharedFlow<UiEvent>()
    val uiEvent: SharedFlow<UiEvent> = _uiEvent.asSharedFlow()

    sealed class UiEvent {
        data class ShowToast(val message: String) : UiEvent()
        object NavigateToMain : UiEvent()
    }

    fun loadNote(noteId: String?) {
        if (noteId == null) {
            _isLoading.value = false
            _errorMessage.value = "No note ID provided"
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            try {
                val result = withContext(Dispatchers.IO) {
                    noteRepository.loadNote(noteId, context)
                }

                if (result.isSuccess) {
                    val loadedNote = result.getOrNull()
                    if (loadedNote != null) {
                        _note.value = loadedNote
                        Log.d(TAG, "Note loaded: ${loadedNote.title}")
                    } else {
                        _errorMessage.value = "Note data is empty"
                    }
                } else {
                    val error = result.exceptionOrNull()?.message ?: "Unknown error"
                    _errorMessage.value = "Failed to load note: $error"
                    Log.e(TAG, "Failed to load note: $error")
                }
            } catch (e: Exception) {
                _errorMessage.value = "Error loading note: ${e.message}"
                Log.e(TAG, "Exception loading note", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun deleteNote(noteId: String?) {
        if (noteId == null) return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val result = noteRepository.deleteNote(noteId, context)
                if (result.isSuccess) {
                    val message = if (!networkManager.isConnected()) {
                        "📱 Note deleted offline. Will sync when online."
                    } else {
                        "✅ Note deleted successfully"
                    }
                    _uiEvent.emit(UiEvent.ShowToast(message))
                    _uiEvent.emit(UiEvent.NavigateToMain)
                } else {
                    _uiEvent.emit(UiEvent.ShowToast("❌ Error deleting note"))
                }
            } catch (e: Exception) {
                _uiEvent.emit(UiEvent.ShowToast("❌ Error deleting note: ${e.message}"))
                Log.e(TAG, "Error deleting note", e)
            }
        }
    }
}


