package com.amvarpvtltd.swiftNote.reminders

import androidx.room.*

/**
 * Room entity for storing reminder data
 */
@Entity(
    tableName = "reminders",
    foreignKeys = [
        ForeignKey(
            entity = com.amvarpvtltd.swiftNote.room.NoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["noteId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
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
