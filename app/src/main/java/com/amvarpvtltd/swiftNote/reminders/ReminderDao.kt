package com.amvarpvtltd.swiftNote.reminders

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders WHERE noteId = :noteId AND isActive = 1")
    suspend fun getRemindersForNote(noteId: String): List<ReminderEntity>

    @Query("SELECT * FROM reminders WHERE isActive = 1 ORDER BY reminderTime ASC")
    fun getAllActiveReminders(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders WHERE id = :reminderId")
    suspend fun getReminderById(reminderId: String): ReminderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReminder(reminder: ReminderEntity): Long

    @Update
    suspend fun updateReminder(reminder: ReminderEntity)

    @Delete
    suspend fun deleteReminder(reminder: ReminderEntity)

    @Query("DELETE FROM reminders WHERE noteId = :noteId")
    suspend fun deleteRemindersForNote(noteId: String)

    @Query("SELECT * FROM reminders ORDER BY createdAt DESC")
    suspend fun getAllReminders(): List<ReminderEntity>

    @Query("UPDATE reminders SET isActive = 0 WHERE id = :reminderId")
    suspend fun deactivateReminder(reminderId: String)

    @Query("SELECT * FROM reminders WHERE reminderTime <= :currentTime AND isActive = 1")
    suspend fun getDueReminders(currentTime: Long): List<ReminderEntity>

    @Query("SELECT COUNT(*) FROM reminders WHERE isActive = 1")
    suspend fun getActiveReminderCount(): Int

    @Query("DELETE FROM reminders WHERE reminderTime < :currentTime AND isActive = 0")
    suspend fun cleanupOldReminders(currentTime: Long)
}
