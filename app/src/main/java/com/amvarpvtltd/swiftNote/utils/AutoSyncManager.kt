package com.amvarpvtltd.swiftNote.utils

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.amvarpvtltd.swiftNote.offline.OfflineNoteManager
import com.amvarpvtltd.swiftNote.repository.NoteRepository
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.TimeUnit

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

    private var networkMonitorJob: Job? = null

    // Mutex to prevent overlapping performSync calls from network changes
    private val syncGuard = Mutex()

    private val exceptionHandler = CoroutineExceptionHandler { _, exception ->
        Log.e(TAG, "Uncaught exception in syncScope", exception)
    }
    private val syncScope = CoroutineScope(Dispatchers.IO + SupervisorJob() + exceptionHandler)

    sealed class SyncStatus {
        object None : SyncStatus()
        object Success : SyncStatus()
        object Failed : SyncStatus()
        object InProgress : SyncStatus()
    }

    companion object {
        private const val TAG = "AutoSyncManager"
        private const val INITIAL_BACKOFF_MS = 5_000L // 5 seconds initial backoff
        private const val MAX_RETRY_ATTEMPTS = 4
        private const val PERIODIC_SYNC_WORK_NAME = "swiftnote_periodic_sync"

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

        // Schedule a periodic WorkManager job as a safety net — survives app kills
        schedulePeriodicSyncWork()

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
        networkMonitorJob?.cancel()
        networkMonitorJob = null
    }

    /**
     * Schedule a periodic WorkManager job to sync notes even if the app is killed.
     * Respects battery optimization and network constraints.
     */
    private fun schedulePeriodicSyncWork() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)      // Only sync when network available
            .setRequiresBatteryNotLow(true)                     // Don't sync on low battery
            .build()

        val syncRequest = PeriodicWorkRequestBuilder<SyncWorker>(
            15, TimeUnit.MINUTES // Minimum periodic interval
        )
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                30, TimeUnit.SECONDS // Initial backoff for retries
            )
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_SYNC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP, // Don't replace if already scheduled
            syncRequest
        )

        Log.d(TAG, "Periodic sync WorkManager job scheduled")
    }

    private fun updatePendingSyncStatus() {
        _hasPendingSync.value = offlineManager.hasPendingSync()
    }

    /**
     * Perform sync with exponential backoff retry.
     * Protected by mutex so concurrent calls are serialized (no overlapping syncs).
     */
    private suspend fun performSync() {
        // tryLock: if another sync is already in progress, skip silently
        if (!syncGuard.tryLock()) {
            Log.d(TAG, "Sync already in progress, skipping")
            return
        }

        try {
            if (!networkManager.isConnected()) {
                Log.d(TAG, "No network connection, skipping sync")
                return
            }

            Log.d(TAG, "Starting sync operation")
            _isSyncing.value = true
            _lastSyncStatus.value = SyncStatus.InProgress

            var attempts = 0
            var success = false
            var backoffMs = INITIAL_BACKOFF_MS

            while (attempts < MAX_RETRY_ATTEMPTS && !success) {
                attempts++
                Log.d(TAG, "Sync attempt $attempts/$MAX_RETRY_ATTEMPTS (backoff: ${backoffMs}ms)")

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
                            // Exponential backoff: 5s, 10s, 20s, 40s...
                            delay(backoffMs)
                            backoffMs *= 2
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Sync error on attempt $attempts", e)
                    if (attempts < MAX_RETRY_ATTEMPTS) {
                        delay(backoffMs)
                        backoffMs *= 2
                    }
                }
            }

            if (!success) {
                Log.e(TAG, "Sync failed after $MAX_RETRY_ATTEMPTS attempts")
                _lastSyncStatus.value = SyncStatus.Failed
            }

            _isSyncing.value = false
        } finally {
            syncGuard.unlock()
        }
    }
}

/**
 * WorkManager-based sync worker — survives app kills, respects system constraints.
 * Runs with exponential backoff configured by WorkManager itself on failure.
 */
class SyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val TAG = "SyncWorker"
        Log.d(TAG, "Periodic sync worker executing (attempt: $runAttemptCount)")

        return try {
            val offlineManager = OfflineNoteManager(applicationContext)

            if (!offlineManager.hasPendingSync()) {
                Log.d(TAG, "No pending sync — nothing to do")
                return Result.success()
            }

            val noteRepository = NoteRepository.getInstance(applicationContext)
            val result = noteRepository.syncOfflineNotes(applicationContext)

            if (result.isSuccess) {
                Log.d(TAG, "Periodic sync completed successfully")
                Result.success()
            } else {
                Log.w(TAG, "Periodic sync failed: ${result.exceptionOrNull()?.message}")
                // Return retry — WorkManager will apply exponential backoff
                Result.retry()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Periodic sync worker error", e)
            // Return retry with exponential backoff
            Result.retry()
        }
    }
}
