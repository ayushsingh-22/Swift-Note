package com.amvarpvtltd.swiftNote.room

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pending_deletions")
data class PendingDeletionEntity(
    @PrimaryKey val noteId: String,
    val mymobiledeviceid: String,
    val deletionTimestamp: Long = System.currentTimeMillis()
)
