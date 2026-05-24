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

    /**
     * Phase 4: Toggle pin status.
     */
    fun togglePin(noteId: String, currentlyPinned: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                noteRepository.togglePin(noteId, !currentlyPinned)
                val action = if (!currentlyPinned) "📌 Note pinned" else "📌 Note unpinned"
                _uiEvent.emit(UiEvent.ShowToast(action))
            } catch (e: Exception) {
                _uiEvent.emit(UiEvent.ShowToast("❌ Failed to update pin status"))
            }
        }
    }

    /**
     * Phase 4: Archive a note.
     */
    fun archiveNote(noteId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                noteRepository.toggleArchive(noteId, true)
                _uiEvent.emit(UiEvent.ShowToast("📦 Note archived"))
            } catch (e: Exception) {
                _uiEvent.emit(UiEvent.ShowToast("❌ Failed to archive note"))
            }
        }
    }

    /**
     * Phase 4: Unarchive a note.
     */
    fun unarchiveNote(noteId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                noteRepository.toggleArchive(noteId, false)
                _uiEvent.emit(UiEvent.ShowToast("📦 Note restored from archive"))
            } catch (e: Exception) {
                _uiEvent.emit(UiEvent.ShowToast("❌ Failed to unarchive note"))
            }
        }
    }

    /**
     * Phase 4: Update note category.
     */
    fun updateCategory(noteId: String, category: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                noteRepository.updateCategory(noteId, category)
            } catch (e: Exception) {
                _uiEvent.emit(UiEvent.ShowToast("❌ Failed to update category"))
            }
        }
    }

    /**
     * Seeds one sample note per default category so the UI has data to display.
     * Only runs when there are no existing notes.
     */
    fun seedDemoNotes() {
        viewModelScope.launch(Dispatchers.IO) {
            if (notes.value.isNotEmpty()) return@launch

            data class DemoNote(val title: String, val description: String, val category: String)

            val checklistV1 = "[[CHECKLIST_V1]]"
            val demos = listOf(
                DemoNote(
                    title = "Weekend Plans",
                    description = "• Visit the local farmers market in the morning\n• Call parents in the afternoon\n• Movie night with friends – don't forget snacks!\n• Prep clothes for the week",
                    category = "Personal"
                ),
                DemoNote(
                    title = "Q2 Project Goals",
                    description = "• Launch redesigned onboarding flow by April 15\n• Reduce user drop-off by 20%\n• Conduct 5 user-research interviews\n• Align with design team on component library\n• Weekly stand-ups every Monday 10 AM",
                    category = "Work"
                ),
                DemoNote(
                    title = "Grocery Checklist",
                    description = "$checklistV1[{\"id\":\"1\",\"text\":\"Milk (2 litres)\",\"isChecked\":false,\"order\":0},{\"id\":\"2\",\"text\":\"Eggs (12 pack)\",\"isChecked\":true,\"order\":1},{\"id\":\"3\",\"text\":\"Bread\",\"isChecked\":false,\"order\":2},{\"id\":\"4\",\"text\":\"Olive oil\",\"isChecked\":false,\"order\":3},{\"id\":\"5\",\"text\":\"Fresh vegetables\",\"isChecked\":false,\"order\":4},{\"id\":\"6\",\"text\":\"Greek yogurt\",\"isChecked\":true,\"order\":5}]",
                    category = "Shopping"
                ),
                DemoNote(
                    title = "Morning Routine",
                    description = "$checklistV1[{\"id\":\"1\",\"text\":\"Wake up at 6:30 AM\",\"isChecked\":false,\"order\":0},{\"id\":\"2\",\"text\":\"10 min meditation\",\"isChecked\":false,\"order\":1},{\"id\":\"3\",\"text\":\"30 min workout\",\"isChecked\":false,\"order\":2},{\"id\":\"4\",\"text\":\"Healthy breakfast\",\"isChecked\":false,\"order\":3},{\"id\":\"5\",\"text\":\"Review daily goals\",\"isChecked\":false,\"order\":4}]",
                    category = "Health"
                ),
                DemoNote(
                    title = "Monthly Budget",
                    description = "💰 Income: ₹85,000\n\nFixed Expenses:\n  • Rent – ₹22,000\n  • EMI – ₹12,500\n  • Utilities – ₹3,200\n\nVariable Expenses:\n  • Groceries – ₹8,000\n  • Dining out – ₹4,000\n  • Entertainment – ₹2,500\n\nSavings goal: ₹15,000\n📈 Investment SIP: ₹10,000",
                    category = "Finance"
                ),
                DemoNote(
                    title = "Goa Trip Plans 🌊",
                    description = "Dates: 15–20 December\nBudget: ₹25,000 per person\n\nTo-do:\n• Book flights (IndiGo / Air India)\n• Reserve beachside hotel at Calangute\n• Rent a scooter for sightseeing\n\nMust visit:\n• Dudhsagar Falls\n• Basilica of Bom Jesus\n• Anjuna Flea Market\n• Spice plantation tour",
                    category = "Travel"
                ),
                DemoNote(
                    title = "App Feature Ideas 💡",
                    description = "1. Collaborative notes with real-time editing\n2. Voice-to-note transcription using on-device AI\n3. Smart auto-tagging from note content\n4. Handwriting-to-text conversion\n5. Weekly digest email of top notes\n6. Note graph – visualise connections\n7. Dark/Light/AMOLED themes\n8. Custom note templates",
                    category = "Ideas"
                ),
                DemoNote(
                    title = "Books to Read 📚",
                    description = "$checklistV1[{\"id\":\"1\",\"text\":\"Atomic Habits – James Clear\",\"isChecked\":true,\"order\":0},{\"id\":\"2\",\"text\":\"Deep Work – Cal Newport\",\"isChecked\":false,\"order\":1},{\"id\":\"3\",\"text\":\"The Psychology of Money – Morgan Housel\",\"isChecked\":false,\"order\":2},{\"id\":\"4\",\"text\":\"Zero to One – Peter Thiel\",\"isChecked\":false,\"order\":3},{\"id\":\"5\",\"text\":\"Sapiens – Yuval Noah Harari\",\"isChecked\":false,\"order\":4},{\"id\":\"6\",\"text\":\"The Lean Startup – Eric Ries\",\"isChecked\":false,\"order\":5}]",
                    category = "Learning"
                )
            )

            try {
                demos.forEach { demo ->
                    noteRepository.saveNote(
                        title = demo.title,
                        description = demo.description,
                        context = context,
                        category = demo.category
                    )
                    // small delay so timestamps are distinct
                    delay(5)
                }
                _uiEvent.emit(UiEvent.ShowToast("🎉 Demo notes loaded! One for each category."))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to seed demo notes", e)
                _uiEvent.emit(UiEvent.ShowToast("❌ Could not load demo notes"))
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
