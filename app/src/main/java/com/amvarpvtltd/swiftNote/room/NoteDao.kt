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

    @Query("SELECT * FROM notes ORDER BY updatedAt DESC")
    fun getAllNotes(): List<NoteEntity>

    /** Reactive Flow of all notes, sorted by last modified descending */
    @Query("SELECT * FROM notes ORDER BY updatedAt DESC")
    fun observeAllNotes(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE synced = 0 ORDER BY timestamp ASC")
    fun getPendingNotes(): List<NoteEntity>
}
