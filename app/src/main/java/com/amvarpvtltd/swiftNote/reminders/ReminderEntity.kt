package com.amvarpvtltd.swiftNote.reminders

import androidx.room.*

/**
 * Room entity for storing reminder data.
 * BUG-013 FIX: Removed ForeignKey constraint on noteId.
 * FK caused SQLiteConstraintException when syncing reminders before their parent notes.
 * Orphan cleanup is handled in deleteNoteOffline() instead.
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
    val createdAt: Long = System.currentTimeMillis()
)
