package com.amvarpvtltd.swiftNote.reminders

import androidx.room.*

/**
 * Room entity for storing reminder data.
 * BUG-013 FIX: Removed ForeignKey constraint on noteId.
 * Phase 2: Added recurrence fields for recurring reminders.
 */
@Entity(
    tableName = "reminders",
    indices = [Index(value = ["noteId"]), Index(value = ["reminderTime"])]
)
data class ReminderEntity(
    @PrimaryKey
    val id: String,

    @ColumnInfo(name = "noteId")
    val noteId: String,

    @ColumnInfo(name = "noteTitle")
    val noteTitle: String,

    @ColumnInfo(name = "noteDescription")
    val noteDescription: String,

    @ColumnInfo(name = "reminderTime")
    val reminderTime: Long,

    @ColumnInfo(name = "isActive")
    val isActive: Boolean = true,

    @ColumnInfo(name = "createdAt")
    val createdAt: Long = System.currentTimeMillis(),

    // Phase 2: Recurrence fields — all nullable/defaulted for safe migration
    @ColumnInfo(name = "recurrenceType", defaultValue = "NONE")
    val recurrenceType: String = RecurrenceType.NONE,

    @ColumnInfo(name = "recurrenceInterval", defaultValue = "1")
    val recurrenceInterval: Int = 1,

    @ColumnInfo(name = "recurrenceDaysOfWeek")
    val recurrenceDaysOfWeek: String? = null,

    @ColumnInfo(name = "recurrenceEndDate")
    val recurrenceEndDate: Long? = null,

    @ColumnInfo(name = "parentReminderId")
    val parentReminderId: String? = null
) {
    /** Returns true if this reminder is recurring */
    val isRecurring: Boolean get() = recurrenceType != RecurrenceType.NONE
}
