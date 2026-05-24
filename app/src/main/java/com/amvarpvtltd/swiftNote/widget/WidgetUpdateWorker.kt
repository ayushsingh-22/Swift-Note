package com.amvarpvtltd.swiftNote.widget

import android.content.Context
import android.util.Log
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Phase 5B: Periodic worker to refresh widget content.
 * Runs every 15 minutes to keep pinned notes up-to-date.
 */
class WidgetUpdateWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "WidgetUpdateWorker"
        private const val WORK_NAME = "quick_note_widget_update"

        fun enqueue(context: Context) {
            val request = PeriodicWorkRequestBuilder<WidgetUpdateWorker>(
                15, TimeUnit.MINUTES
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
            Log.d(TAG, "Widget update worker enqueued (every 15 min)")
        }

        /**
         * Trigger an immediate widget refresh (e.g., after pinning/unpinning a note).
         */
        suspend fun refreshNow(context: Context) {
            try {
                QuickNoteWidget().updateAll(context)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to refresh widget", e)
            }
        }
    }

    override suspend fun doWork(): Result {
        return try {
            QuickNoteWidget().updateAll(context)
            Log.d(TAG, "Widget updated successfully")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Widget update failed", e)
            Result.retry()
        }
    }
}

