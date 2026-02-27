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
    val customDateTime: Long? = null // Only used for CUSTOM preset
) {
    fun getReminderTime(): Long {
        return if (preset == ReminderPreset.CUSTOM && customDateTime != null) {
            customDateTime
        } else {
            System.currentTimeMillis() + (preset.minutes * 60 * 1000L)
        }
    }
}
