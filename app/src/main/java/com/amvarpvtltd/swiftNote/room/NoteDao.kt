package com.amvarpvtltd.swiftNote.room

import androidx.room.*

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

    @Query("SELECT * FROM notes ORDER BY timestamp DESC")
    fun getAllNotes(): List<NoteEntity>

    @Query("SELECT * FROM notes WHERE synced = 0 ORDER BY timestamp ASC")
    fun getPendingNotes(): List<NoteEntity>
}
