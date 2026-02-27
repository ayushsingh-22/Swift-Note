package com.amvarpvtltd.swiftNote.room

import androidx.room.*

@Dao
interface PendingDeletionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(pendingDeletion: PendingDeletionEntity): Long

    @Query("SELECT * FROM pending_deletions ORDER BY deletionTimestamp ASC")
    fun getAllPendingDeletions(): List<PendingDeletionEntity>

    @Query("DELETE FROM pending_deletions WHERE noteId = :noteId")
    fun removePendingDeletion(noteId: String): Int

    @Query("DELETE FROM pending_deletions")
    fun clearAllPendingDeletions(): Int
}
