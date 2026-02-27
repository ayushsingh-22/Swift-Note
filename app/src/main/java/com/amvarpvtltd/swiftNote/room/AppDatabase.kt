package com.amvarpvtltd.swiftNote.room

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.amvarpvtltd.swiftNote.reminders.ReminderDao
import com.amvarpvtltd.swiftNote.reminders.ReminderEntity

@Database(
    entities = [NoteEntity::class, PendingDeletionEntity::class, ReminderEntity::class],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
    abstract fun pendingDeletionDao(): PendingDeletionDao
    abstract fun reminderDao(): ReminderDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        // Migration from version 2 to version 3 (adding pending_deletions table)
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Create the pending_deletions table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `pending_deletions` (
                        `noteId` TEXT NOT NULL,
                        `mymobiledeviceid` TEXT NOT NULL,
                        `deletionTimestamp` INTEGER NOT NULL,
                        PRIMARY KEY(`noteId`)
                    )
                """.trimIndent())
            }
        }

        // Migration from version 3 to version 4 (adding reminders table)
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Create the reminders table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `reminders` (
                        `id` TEXT NOT NULL,
                        `noteId` TEXT NOT NULL,
                        `noteTitle` TEXT NOT NULL,
                        `noteDescription` TEXT NOT NULL,
                        `reminderTime` INTEGER NOT NULL,
                        `isActive` INTEGER NOT NULL DEFAULT 1,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())

                // Create indices for better performance
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_reminders_noteId` ON `reminders` (`noteId`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_reminders_reminderTime` ON `reminders` (`reminderTime`)")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "notes_database"
                )
                .addMigrations(MIGRATION_2_3, MIGRATION_3_4)
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
