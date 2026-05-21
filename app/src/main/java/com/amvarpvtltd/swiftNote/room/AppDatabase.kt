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
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
    abstract fun pendingDeletionDao(): PendingDeletionDao
    abstract fun reminderDao(): ReminderDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        // BUG-010 FIX: Migration from version 1 to 2 (adding synced column and mymobiledeviceid)
        // Prevents data loss for users upgrading from original DB schema
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add synced column with default false (all existing notes treated as unsynced)
                database.execSQL("ALTER TABLE notes ADD COLUMN synced INTEGER NOT NULL DEFAULT 0")
                // Add mymobiledeviceid if missing
                try {
                    database.execSQL("ALTER TABLE notes ADD COLUMN mymobiledeviceid TEXT NOT NULL DEFAULT ''")
                } catch (_: Exception) {
                    // Column may already exist in some v1 schemas
                }
            }
        }

        // Migration from version 2 to version 3 (adding pending_deletions table)
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
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

                database.execSQL("CREATE INDEX IF NOT EXISTS `index_reminders_noteId` ON `reminders` (`noteId`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_reminders_reminderTime` ON `reminders` (`reminderTime`)")
            }
        }

        // BUG-013 FIX: Migration from version 4 to 5 — recreate reminders table WITHOUT foreign key
        // Foreign key caused crash when syncing reminders before their parent notes
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Recreate reminders table without foreign key constraint
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `reminders_new` (
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
                database.execSQL("INSERT OR IGNORE INTO reminders_new SELECT * FROM reminders")
                database.execSQL("DROP TABLE reminders")
                database.execSQL("ALTER TABLE reminders_new RENAME TO reminders")
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
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                // BUG-010 FIX: Removed fallbackToDestructiveMigration() to prevent silent data loss
                .build()
                INSTANCE = instance
                instance
            }
        }

        fun resetInstance() {
            synchronized(this) {
                INSTANCE = null
            }
        }
    }
}
