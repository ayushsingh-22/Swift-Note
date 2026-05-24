package com.amvarpvtltd.swiftNote.room

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey val id: String,
    val title: String,            // Encrypted title
    val description: String,      // Encrypted description
    val mymobiledeviceid: String,
    val timestamp: Long,
    val synced: Boolean,          // false = pending sync, true = synced
    @ColumnInfo(defaultValue = "0")
    val updatedAt: Long = 0L,    // Last modified timestamp
    @ColumnInfo(defaultValue = "0")
    val isPinned: Boolean = false,
    @ColumnInfo(defaultValue = "0")
    val isArchived: Boolean = false,
    @ColumnInfo(defaultValue = "")
    val category: String = "",
    @ColumnInfo(defaultValue = "")
    val colorKey: String? = null
)
