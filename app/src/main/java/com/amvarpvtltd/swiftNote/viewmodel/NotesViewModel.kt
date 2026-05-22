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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ViewModel for the Notes list screen.
 * Uses Flow-based reactive observation from Room (Phase 0).
 * Supports undo-delete via pendingDelete state (Phase 0).
 */
class NotesViewModel(application: Application) : AndroidViewModel(application) {

    private val TAG = "NotesViewModel"
    private val context = application.applicationContext

    val noteRepository = NoteRepository.getInstance(context)
    val networkManager = NetworkManager.getInstance(context)
    val autoSyncManager = AutoSyncManager.getInstance(context, noteRepository)

    // Phase 0.3: Flow-based reactive notes from Room — always active (Eagerly) so cached
    // data is available instantly on navigation back without Room requery lag
    val notes: StateFlow<List<dataclass>> = noteRepository.observeNotes()
        .catch { e ->
            Log.e(TAG, "Error observing notes", e)
            emit(emptyList())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly, // never stops — keeps data hot for instant nav-back
            initialValue = emptyList()
        )

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    // Phase 0.4: Undo-delete support
    private val _pendingDelete = MutableStateFlow<dataclass?>(null)
    val pendingDelete: StateFlow<dataclass?> = _pendingDelete.asStateFlow()
    private var undoDeleteJob: Job? = null

    // One-shot UI events (toasts, navigation) — collected once, never replayed
    private val _uiEvent = MutableSharedFlow<UiEvent>()
    val uiEvent: SharedFlow<UiEvent> = _uiEvent.asSharedFlow()

    sealed class UiEvent {
        data class ShowToast(val message: String) : UiEvent()
        object NavigateToMain : UiEvent()
    }

    init {
        // Dismiss loading as soon as Room emits its first value (fast with Eagerly — typically < 60ms).
        // This replaces the fixed delay(300ms) which was causing a guaranteed loading flash on every launch.
        viewModelScope.launch {
            notes.first()          // suspends until Room gives ANY data (empty list or populated)
            _isLoading.value = false
        }
        startAutoSync()
        attemptOneTimeRemoteImport()
    }

    /**
     * Manually refresh notes from the repository (triggers background cloud sync).
     * The Flow will automatically pick up any changes written to the DB.
     */
    fun refreshNotes() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                // Trigger a fetch which syncs from cloud to local DB
                withContext(Dispatchers.IO) { noteRepository.fetchNotes() }
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    /**
     * Phase 0.4: Delete with undo support.
     * Holds the note for 4 seconds before permanent deletion.
     */
    fun deleteNote(noteId: String) {
        // Find the note in current list to hold for undo
        val noteToDelete = notes.value.find { it.id == noteId }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val result = noteRepository.deleteNote(noteId, context)
                if (result.isSuccess) {
                    if (noteToDelete != null) {
                        // Set pending delete for undo
                        _pendingDelete.value = noteToDelete
                        // Cancel any previous undo timer
                        undoDeleteJob?.cancel()
                        // Start timer to clear pending state
                        undoDeleteJob = viewModelScope.launch {
                            delay(4000L) // 4 second undo window
                            _pendingDelete.value = null
                        }
                    }
                    if (!networkManager.isConnected()) {
                        _uiEvent.emit(UiEvent.ShowToast("📱 Note deleted offline. Will sync when online."))
                    }
                }
            } catch (e: Exception) {
                _uiEvent.emit(UiEvent.ShowToast(Constants.ERROR_DELETING_MESSAGE))
            }
        }
    }

    /**
     * Phase 0.4: Undo the last delete.
     * Re-saves the note that was pending deletion.
     */
    fun undoDelete() {
        val note = _pendingDelete.value ?: return
        undoDeleteJob?.cancel()
        _pendingDelete.value = null

        viewModelScope.launch(Dispatchers.IO) {
            try {
                noteRepository.saveNote(
                    title = note.title,
                    description = note.description,
                    noteId = note.id,
                    context = context
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to undo delete", e)
                _uiEvent.emit(UiEvent.ShowToast("❌ Failed to restore note"))
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
                    // Flow will auto-update, no manual refresh needed
                    Log.d(TAG, "One-time remote import succeeded")
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
