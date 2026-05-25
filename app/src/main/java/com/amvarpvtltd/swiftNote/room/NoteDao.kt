package com.amvarpvtltd.swiftNote.room

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(note: NoteEntity): Long

    @Query("SELECT * FROM notes WHERE id = :id LIMIT 1")
    fun getNoteById(id: String): NoteEntity?

    @Delete
    fun delete(note: NoteEntity): Int

    @Update
    fun update(note: NoteEntity): Int

    @Query("SELECT * FROM notes WHERE isArchived = 0 ORDER BY isPinned DESC, updatedAt DESC")
    fun getAllNotes(): List<NoteEntity>

    /** All notes including archived — used for sync conflict resolution */
    @Query("SELECT * FROM notes ORDER BY updatedAt DESC")
    fun getAllNotesIncludingArchived(): List<NoteEntity>

    /** Reactive Flow of all non-archived notes, pinned first then by last modified */
    @Query("SELECT * FROM notes WHERE isArchived = 0 ORDER BY isPinned DESC, updatedAt DESC")
    fun observeAllNotes(): Flow<List<NoteEntity>>

    /** Reactive Flow of archived notes */
    @Query("SELECT * FROM notes WHERE isArchived = 1 ORDER BY updatedAt DESC")
    fun observeArchivedNotes(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE isArchived = 1 ORDER BY updatedAt DESC")
    fun getArchivedNotes(): List<NoteEntity>

    @Query("SELECT * FROM notes WHERE synced = 0 ORDER BY timestamp ASC")
    fun getPendingNotes(): List<NoteEntity>

    @Query("UPDATE notes SET isPinned = :isPinned, synced = 0, updatedAt = :updatedAt WHERE id = :noteId")
    fun updatePinStatus(noteId: String, isPinned: Boolean, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE notes SET isArchived = :isArchived, synced = 0, updatedAt = :updatedAt WHERE id = :noteId")
    fun updateArchiveStatus(noteId: String, isArchived: Boolean, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE notes SET category = :category, synced = 0, updatedAt = :updatedAt WHERE id = :noteId")
    fun updateCategory(noteId: String, category: String, updatedAt: Long = System.currentTimeMillis())

    /** Synchronous query for pinned notes — kept for fallback/testing */
    @Query("SELECT * FROM notes WHERE isPinned = 1 AND isArchived = 0 ORDER BY updatedAt DESC")
    fun getPinnedNotesSync(): List<NoteEntity>

    /**
     * Reactive Flow of pinned notes — used by widget so it auto-refreshes
     * whenever a note is pinned/unpinned without any manual trigger.
     */
    @Query("SELECT * FROM notes WHERE isPinned = 1 AND isArchived = 0 ORDER BY updatedAt DESC")
    fun observePinnedNotes(): Flow<List<NoteEntity>>

    /** Reactive count of all non-archived notes — used by widget header */
    @Query("SELECT COUNT(*) FROM notes WHERE isArchived = 0")
    fun observeNoteCount(): Flow<Int>

    /** Delete every note row — used by onboarding reset and full data wipe */
    @Query("DELETE FROM notes")
    fun deleteAllNotes()
}
