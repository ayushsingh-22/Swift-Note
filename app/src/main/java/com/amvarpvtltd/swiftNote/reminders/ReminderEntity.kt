package com.amvarpvtltd.swiftNote.reminders

import androidx.room.*
import com.google.firebase.database.IgnoreExtraProperties

/**
 * Room entity for storing reminder data.
 * BUG-013 FIX: Removed ForeignKey constraint on noteId.
 * Phase 2: Added recurrence fields for recurring reminders.
 *
 * Firebase RTDB compatibility rules:
 *  - @IgnoreExtraProperties: suppress "No setter/field for X" warnings on unknown keys.
 *  - All fields have default values → Kotlin compiler generates a no-arg constructor
 *    (required by Firebase for reflection-based deserialization).
 *  - Fields are `var` so Firebase can set individual properties after construction.
 */
@IgnoreExtraProperties
@Entity(
    tableName = "reminders",
    indices = [Index(value = ["noteId"]), Index(value = ["reminderTime"])]
)
data class ReminderEntity(
    @PrimaryKey
    var id: String = "",

    @ColumnInfo(name = "noteId")
    var noteId: String = "",

    @ColumnInfo(name = "noteTitle")
    var noteTitle: String = "",

    @ColumnInfo(name = "noteDescription")
    var noteDescription: String = "",

    @ColumnInfo(name = "reminderTime")
    var reminderTime: Long = 0L,

    @ColumnInfo(name = "isActive")
    var isActive: Boolean = true,

    @ColumnInfo(name = "createdAt")
    var createdAt: Long = System.currentTimeMillis(),

    // Phase 2: Recurrence fields — all nullable/defaulted for safe migration
    @ColumnInfo(name = "recurrenceType", defaultValue = "NONE")
    var recurrenceType: String = RecurrenceType.NONE,

    @ColumnInfo(name = "recurrenceInterval", defaultValue = "1")
    var recurrenceInterval: Int = 1,

    @ColumnInfo(name = "recurrenceDaysOfWeek")
    var recurrenceDaysOfWeek: String? = null,

    @ColumnInfo(name = "recurrenceEndDate")
    var recurrenceEndDate: Long? = null,

    @ColumnInfo(name = "parentReminderId")
    var parentReminderId: String? = null
) {
    /** Returns true if this reminder is recurring */
    val isRecurring: Boolean get() = recurrenceType != RecurrenceType.NONE
}
