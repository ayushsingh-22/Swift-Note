package com.amvarpvtltd.swiftNote.utils

object ValidationUtils {

    /**
     * Check if title is valid (meets minimum requirements)
     */
    fun isValidTitle(title: String): Boolean {
        return title.trim().length >= Constants.MIN_CONTENT_LENGTH
    }
    /**
     * Check if a note can be saved (title is valid)
     */
    fun canSaveNote(title: String, description: String): Boolean {
        return isValidTitle(title)
    }

    /**
     * Get validation message for title
     */
    fun getTitleValidationMessage(): String {
        return "Title must be at least ${Constants.MIN_CONTENT_LENGTH} characters"
    }

    /**
     * Get validation message for save requirements
     */
    fun getSaveValidationMessage(): String {
        return "Please enter a title with at least ${Constants.MIN_CONTENT_LENGTH} characters to save your note"
    }

}
