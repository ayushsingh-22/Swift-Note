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

    /** Phase 5: All reminders (active + inactive) for Today screen history view */
    @Query("SELECT * FROM reminders ORDER BY reminderTime DESC")
    fun observeAllReminders(): Flow<List<ReminderEntity>>

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

    // Phase 2: Recurring reminder queries

    /** Get all active recurring reminders (for rescheduling after boot) */
    @Query("SELECT * FROM reminders WHERE isActive = 1 AND recurrenceType != 'NONE'")
    suspend fun getActiveRecurringReminders(): List<ReminderEntity>

    /** Deactivate all reminders in a recurring chain (by parentReminderId or own id) */
    @Query("UPDATE reminders SET isActive = 0 WHERE parentReminderId = :parentId OR id = :parentId")
    suspend fun deactivateRecurringChain(parentId: String)

    /** Get the parent reminder of a recurring chain */
    @Query("SELECT * FROM reminders WHERE id = :parentId")
    suspend fun getParentReminder(parentId: String): ReminderEntity?

    /** Wipe all reminders — used during onboarding identity reset */
    @Query("DELETE FROM reminders")
    fun clearAll()
}
