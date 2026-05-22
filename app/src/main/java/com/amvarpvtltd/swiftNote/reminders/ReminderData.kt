package com.amvarpvtltd.swiftNote.reminders

enum class ReminderPreset(val label: String, val minutes: Int) {
    TEN_MINUTES("10 minutes", 10),
    THIRTY_MINUTES("30 minutes", 30),
    ONE_HOUR("1 hour", 60),
    ONE_DAY("1 day", 1440),
    CUSTOM("Custom", -1)
}

data class ReminderRequest(
    val noteId: String,
    val noteTitle: String,
    val noteDescription: String = "",
    val preset: ReminderPreset,
    val customDateTime: Long? = null, // Only used for CUSTOM preset
    // Phase 2: Recurrence settings
    val recurrenceType: String = RecurrenceType.NONE,
    val recurrenceInterval: Int = 1,
    val recurrenceDaysOfWeek: String? = null,
    val recurrenceEndDate: Long? = null
) {
    fun getReminderTime(): Long {
        return if (preset == ReminderPreset.CUSTOM && customDateTime != null) {
            customDateTime
        } else {
            System.currentTimeMillis() + (preset.minutes * 60 * 1000L)
        }
    }

    val isRecurring: Boolean get() = recurrenceType != RecurrenceType.NONE
}

/**
 * Phase 2: Recurrence option for the reminder picker UI.
 */
enum class RecurrenceOption(val label: String, val type: String) {
    NEVER("Never", RecurrenceType.NONE),
    DAILY("Daily", RecurrenceType.DAILY),
    WEEKDAYS("Weekdays", RecurrenceType.WEEKLY), // Mon-Fri special case
    WEEKLY("Weekly", RecurrenceType.WEEKLY),
    MONTHLY("Monthly", RecurrenceType.MONTHLY),
    YEARLY("Yearly", RecurrenceType.YEARLY);

    companion object {
        fun fromType(type: String): RecurrenceOption {
            return entries.find { it.type == type } ?: NEVER
        }
    }
}
