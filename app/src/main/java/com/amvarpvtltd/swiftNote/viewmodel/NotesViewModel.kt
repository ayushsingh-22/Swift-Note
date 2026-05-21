package com.amvarpvtltd.swiftNote.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.amvarpvtltd.swiftNote.auth.DeviceManager
import com.amvarpvtltd.swiftNote.auth.PassphraseManager
import com.amvarpvtltd.swiftNote.dataclass
import com.amvarpvtltd.swiftNote.repository.NoteRepository
import com.amvarpvtltd.swiftNote.sync.SyncManager
import com.amvarpvtltd.swiftNote.utils.AutoSyncManager
import com.amvarpvtltd.swiftNote.utils.Constants
import com.amvarpvtltd.swiftNote.utils.NetworkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ViewModel for the Notes list screen.
 * Extracts business logic (fetching, deleting, syncing) from the Composable.
 */
class NotesViewModel(application: Application) : AndroidViewModel(application) {

    private val TAG = "NotesViewModel"
    private val context = application.applicationContext

    val noteRepository = NoteRepository.getInstance(context)
    val networkManager = NetworkManager.getInstance(context)
    val autoSyncManager = AutoSyncManager.getInstance(context, noteRepository)

    private val _notes = MutableStateFlow<List<dataclass>>(emptyList())
    val notes: StateFlow<List<dataclass>> = _notes.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    // One-shot UI events (toasts, navigation) — collected once, never replayed
    private val _uiEvent = MutableSharedFlow<UiEvent>()
    val uiEvent: SharedFlow<UiEvent> = _uiEvent.asSharedFlow()

    sealed class UiEvent {
        data class ShowToast(val message: String) : UiEvent()
        object NavigateToMain : UiEvent()
    }

    init {
        refreshNotes()
        startAutoSync()
        attemptOneTimeRemoteImport()
    }

    fun refreshNotes() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                withContext(Dispatchers.IO) { delay(Constants.REFRESH_DELAY) }
                val result = withContext(Dispatchers.IO) { noteRepository.fetchNotes() }
                if (result.isSuccess) {
                    _notes.value = result.getOrNull() ?: emptyList()
                }
            } finally {
                _isRefreshing.value = false
                if (_isLoading.value) _isLoading.value = false
            }
        }
    }

    fun deleteNote(noteId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val result = noteRepository.deleteNote(noteId, context)
                if (result.isSuccess) {
                    withContext(Dispatchers.Main) { refreshNotes() }
                    if (!networkManager.isConnected()) {
                        _uiEvent.emit(UiEvent.ShowToast("📱 Note deleted offline. Will sync when online."))
                    }
                }
            } catch (e: Exception) {
                _uiEvent.emit(UiEvent.ShowToast(Constants.ERROR_DELETING_MESSAGE))
            }
        }
    }

    fun syncNotes() {
        if (!networkManager.isConnected()) {
            viewModelScope.launch { _uiEvent.emit(UiEvent.ShowToast("❌ Can't sync while offline")) }
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val result = noteRepository.syncOfflineNotes(context)
                if (result.isSuccess) {
                    withContext(Dispatchers.Main) { refreshNotes() }
                    _uiEvent.emit(UiEvent.ShowToast("✅ Sync completed"))
                }
            } catch (e: Exception) {
                _uiEvent.emit(UiEvent.ShowToast("❌ Sync failed"))
            }
        }
    }

    private fun startAutoSync() {
        autoSyncManager.startAutoSync()
    }

    fun stopAutoSync() {
        autoSyncManager.stopAutoSync()
    }

    private fun attemptOneTimeRemoteImport() {
        if (!networkManager.isConnected()) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val accountId = PassphraseManager.getStoredPassphrase(context)
                    ?: DeviceManager.getOrCreateDeviceId(context)
                val res = SyncManager.syncDataFromPassphrase(context, accountId, accountId)
                if (res.isSuccess) {
                    withContext(Dispatchers.Main) { refreshNotes() }
                } else {
                    Log.d(TAG, "One-time remote import failed: ${res.exceptionOrNull()?.message}")
                }
            } catch (e: Exception) {
                Log.d(TAG, "One-time remote import failed", e)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        autoSyncManager.stopAutoSync()
    }
}


