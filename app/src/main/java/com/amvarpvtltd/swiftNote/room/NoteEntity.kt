package com.amvarpvtltd.swiftNote.room

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey val id: String,
    val title: String,            // Encrypted title
    val description: String,      // Encrypted description
    val mymobiledeviceid: String,
    val timestamp: Long,
    val synced: Boolean           // false = pending sync, true = synced
)
