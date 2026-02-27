package com.amvarpvtltd.swiftNote.utils

object Constants {
    // Character limits
    const val TITLE_MAX_LENGTH = 50
    const val DESCRIPTION_MAX_LENGTH = 10000
    const val MIN_CONTENT_LENGTH = 5
    const val WARNING_LENGTH = 3

    // Animation durations
    const val COLOR_ANIMATION_DURATION = 300
    const val SPRING_ANIMATION_DELAY = 150
    const val LOADING_DELAY = 300L
    const val REFRESH_DELAY = 500L

    // UI Messages
    const val SAVE_SUCCESS_MESSAGE = "✅ Note saved successfully!"
    const val UPDATE_SUCCESS_MESSAGE = "✅ Note updated successfully!"
    const val DELETE_SUCCESS_MESSAGE = "🗑️ Note deleted successfully"
    const val ERROR_LOADING_MESSAGE = "❌ Error loading note"
    const val ERROR_DELETING_MESSAGE = "❌ Error deleting note"
    const val VALIDATION_WARNING_MESSAGE = "⚠️ Title must be at least 5 characters"

    // Offline messages
    const val OFFLINE_SAVE_MESSAGE = "📱 Note saved offline! Will sync when online"
    const val OFFLINE_MODE_MESSAGE = "You're currently offline"
    const val AUTO_SYNC_MESSAGE = "🔄 Auto-syncing your notes..."
    const val SYNC_SUCCESS_MESSAGE = "✅ All notes synced successfully"
    const val SYNC_FAILED_MESSAGE = "❌ Sync failed. Will retry automatically"

    // UI Sizes
    const val FAB_SIZE = 64
    const val ICON_SIZE_SMALL = 16
    const val ICON_SIZE_MEDIUM = 20
    const val ICON_SIZE_LARGE = 24
    const val PROGRESS_INDICATOR_SIZE = 24

    // Padding and spacing
    const val PADDING_SMALL = 8
    const val PADDING_MEDIUM = 15
    const val PADDING_LARGE = 24
    const val PADDING_XL = 32

    // Corner radius
    const val CORNER_RADIUS_SMALL = 12
    const val CORNER_RADIUS_MEDIUM = 16
    const val CORNER_RADIUS_LARGE = 20
    const val CORNER_RADIUS_XL = 24

    // Theme preferences
    const val THEME_PREFERENCES = "theme_preferences"
    const val THEME_MODE_KEY = "theme_mode"
    const val DEFAULT_THEME_MODE = "system"

    // Search and sorting
    const val SEARCH_DEBOUNCE_DELAY = 300L


    // View mode preferences
    const val VIEW_MODE_PREFERENCES = "view_mode_preferences"
    const val VIEW_MODE_KEY = "view_mode"
    const val DEFAULT_VIEW_MODE = "grid"
    const val VIEW_MODE_LIST = "list"
    const val VIEW_MODE_GRID = "grid"
    const val VIEW_MODE_CARD = "card"

    // Grid layout
    const val GRID_COLUMNS_PORTRAIT = 2
    const val GRID_COLUMNS_LANDSCAPE = 3
    const val GRID_ITEM_MIN_HEIGHT = 120

    // Share functionality
    const val SHARE_MIME_TYPE = "text/plain"
    const val SHARE_SUBJECT = "Note from SwiftNote"
}
