package com.amvarpvtltd.swiftNote.utils

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import com.amvarpvtltd.swiftNote.offline.OfflineNoteManager
import com.amvarpvtltd.swiftNote.repository.NoteRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AutoSyncManager(
    private val context: Context,
    private val noteRepository: NoteRepository
) {
    private val networkManager = NetworkManager.getInstance(context)
    private val offlineManager = OfflineNoteManager(context)

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _lastSyncStatus = MutableStateFlow<SyncStatus>(SyncStatus.None)
    val lastSyncStatus: StateFlow<SyncStatus> = _lastSyncStatus.asStateFlow()

    private val _hasPendingSync = MutableStateFlow(false)
    val hasPendingSync: StateFlow<Boolean> = _hasPendingSync.asStateFlow()

    private var syncJob: Job? = null
    private var networkMonitorJob: Job? = null

    private val syncScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    sealed class SyncStatus {
        object None : SyncStatus()
        object Success : SyncStatus()
        object Failed : SyncStatus()
        object InProgress : SyncStatus()
    }

    companion object {
        private const val TAG = "AutoSyncManager"
        private const val SYNC_RETRY_DELAY = 30_000L // 30 seconds
        private const val MAX_RETRY_ATTEMPTS = 3

        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var INSTANCE: AutoSyncManager? = null

        fun getInstance(context: Context, noteRepository: NoteRepository): AutoSyncManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AutoSyncManager(context.applicationContext, noteRepository).also { INSTANCE = it }
            }
        }
    }

    fun startAutoSync() {
        Log.d(TAG, "Starting auto sync monitoring")

        // Monitor network changes and trigger sync when online
        networkMonitorJob = syncScope.launch {
            networkManager.isOnline
                .collect { isOnline ->
                    Log.d(TAG, "Network status changed: $isOnline")
                    if (isOnline && offlineManager.hasPendingSync()) {
                        Log.d(TAG, "Network available and has pending sync - starting sync")
                        delay(1000) // Small delay to ensure stable connection
                        performSync()
                    }
                    updatePendingSyncStatus()
                }
        }

        // Initial sync check
        syncScope.launch {
            updatePendingSyncStatus()
            if (networkManager.isConnected() && offlineManager.hasPendingSync()) {
                delay(2000) // Wait for app to stabilize
                performSync()
            }
        }
    }

    fun stopAutoSync() {
        Log.d(TAG, "Stopping auto sync")
        syncJob?.cancel()
        networkMonitorJob?.cancel()
        syncJob = null
        networkMonitorJob = null
    }

    private fun updatePendingSyncStatus() {
        _hasPendingSync.value = offlineManager.hasPendingSync()
    }

    private suspend fun performSync() {
        if (_isSyncing.value) {
            Log.d(TAG, "Sync already in progress, skipping")
            return
        }

        if (!networkManager.isConnected()) {
            Log.d(TAG, "No network connection, skipping sync")
            return
        }

        Log.d(TAG, "Starting sync operation")
        _isSyncing.value = true
        _lastSyncStatus.value = SyncStatus.InProgress

        var attempts = 0
        var success = false

        while (attempts < MAX_RETRY_ATTEMPTS && !success) {
            attempts++
            Log.d(TAG, "Sync attempt $attempts/$MAX_RETRY_ATTEMPTS")

            try {
                val result = noteRepository.syncOfflineNotes(context)

                if (result.isSuccess) {
                    Log.d(TAG, "Sync completed successfully")
                    _lastSyncStatus.value = SyncStatus.Success
                    success = true
                    updatePendingSyncStatus()
                } else {
                    Log.w(TAG, "Sync failed: ${result.exceptionOrNull()?.message}")
                    if (attempts < MAX_RETRY_ATTEMPTS) {
                        Log.d(TAG, "Retrying sync in ${SYNC_RETRY_DELAY}ms")
                        delay(SYNC_RETRY_DELAY)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Sync error on attempt $attempts", e)
                if (attempts < MAX_RETRY_ATTEMPTS) {
                    delay(SYNC_RETRY_DELAY)
                }
            }
        }

        if (!success) {
            Log.e(TAG, "Sync failed after $MAX_RETRY_ATTEMPTS attempts")
            _lastSyncStatus.value = SyncStatus.Failed
        }

        _isSyncing.value = false
    }


}
