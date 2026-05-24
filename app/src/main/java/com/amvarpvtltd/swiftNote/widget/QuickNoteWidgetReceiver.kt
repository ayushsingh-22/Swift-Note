package com.amvarpvtltd.swiftNote.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/**
 * Phase 5B: Widget receiver — required entry point for Glance widgets.
 * The system uses this receiver to manage widget lifecycle events.
 */
class QuickNoteWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QuickNoteWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        // Start periodic updates when first widget is added
        WidgetUpdateWorker.enqueue(context)
    }
}

