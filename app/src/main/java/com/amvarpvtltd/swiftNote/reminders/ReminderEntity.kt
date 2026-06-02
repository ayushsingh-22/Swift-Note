package com.amvarpvtltd.swiftNote.reminders

import android.util.Log
import androidx.room.*
import com.amvarpvtltd.swiftNote.security.EncryptionUtil
import com.google.firebase.database.IgnoreExtraProperties

/**
 * Room entity for storing reminder data.
 * BUG-013 FIX: Removed ForeignKey constraint on noteId.
 * Phase 2: Added recurrence fields for recurring reminders.
 *
 * Firebase RTDB compatibility rules:
 *  - @IgnoreExtraProperties: suppress "No setter/field for X" warnings on unknown keys.
 *  - All fields have default values → Kotlin compiler generates a no-arg constructor
 *    (required by Firebase for reflection-based deserialization).
 *  - Fields are `var` so Firebase can set individual properties after construction.
 */
@IgnoreExtraProperties
@Entity(
    tableName = "reminders",
    indices = [Index(value = ["noteId"]), Index(value = ["reminderTime"])]
)
data class ReminderEntity(
    @PrimaryKey
    var id: String = "",

    @ColumnInfo(name = "noteId")
    var noteId: String = "",

    @ColumnInfo(name = "noteTitle")
    var noteTitle: String = "",

    @ColumnInfo(name = "noteDescription")
    var noteDescription: String = "",

    @ColumnInfo(name = "reminderTime")
    var reminderTime: Long = 0L,

    @ColumnInfo(name = "isActive")
    var isActive: Boolean = true,

    @ColumnInfo(name = "createdAt")
    var createdAt: Long = System.currentTimeMillis(),

    // Phase 2: Recurrence fields — all nullable/defaulted for safe migration
    @ColumnInfo(name = "recurrenceType", defaultValue = "NONE")
    var recurrenceType: String = RecurrenceType.NONE,

    @ColumnInfo(name = "recurrenceInterval", defaultValue = "1")
    var recurrenceInterval: Int = 1,

    @ColumnInfo(name = "recurrenceDaysOfWeek")
    var recurrenceDaysOfWeek: String? = null,

    @ColumnInfo(name = "recurrenceEndDate")
    var recurrenceEndDate: Long? = null,

    @ColumnInfo(name = "parentReminderId")
    var parentReminderId: String? = null
) {
    /** Returns true if this reminder is recurring */
    val isRecurring: Boolean get() = recurrenceType != RecurrenceType.NONE

    /**
     * Encrypted copy for Firebase storage. Mirrors [com.amvarpvtltd.swiftNote.Note.toEncryptedData].
     * Only the user-visible text fields ([noteTitle], [noteDescription]) are encrypted; timestamps,
     * IDs, and recurrence rules stay plaintext (same trade-off the Note model makes).
     *
     * @param key Symmetric key — pass the device passphrase / accountId used for note encryption
     *            so the same key candidate list decrypts both notes and reminders during sync.
     */
    fun toEncryptedData(key: String): ReminderEntity {
        require(key.isNotEmpty()) { "Cannot encrypt reminder $id: key is empty" }
        return copy(
            noteTitle = EncryptionUtil.encrypt(noteTitle, key),
            noteDescription = EncryptionUtil.encrypt(noteDescription, key)
        )
    }

    companion object {
        private const val TAG = "ReminderEntity"

        /** Sentinel id-prefix used when [fromEncryptedData] fails so the sync layer can drop it. */
        const val DECRYPT_FAILED_MARKER = "__SN_REM_DECRYPT_FAILED__"

        /**
         * Decrypt a reminder fetched from Firebase using candidate keys.
         * Mirrors [com.amvarpvtltd.swiftNote.Note.fromEncryptedData] including legacy plaintext
         * fallback — if neither field looks encrypted (legacy data uploaded before this change),
         * the input is returned unchanged.
         */
        fun fromEncryptedData(encrypted: ReminderEntity, candidateKeys: List<String>): ReminderEntity {
            val titleLooksEncrypted = EncryptionUtil.isPotentiallyEncrypted(encrypted.noteTitle)
            val descLooksEncrypted = EncryptionUtil.isPotentiallyEncrypted(encrypted.noteDescription)

            // Legacy plaintext (pre-encryption upload) → return as-is for backward compatibility.
            if (!titleLooksEncrypted && !descLooksEncrypted) return encrypted

            val keys = candidateKeys.filter { it.isNotEmpty() }.distinct()
            for (key in keys) {
                try {
                    val t = if (titleLooksEncrypted) EncryptionUtil.decrypt(encrypted.noteTitle, key) else encrypted.noteTitle
                    val d = if (descLooksEncrypted) EncryptionUtil.decrypt(encrypted.noteDescription, key) else encrypted.noteDescription
                    if (t != null && d != null) {
                        return encrypted.copy(noteTitle = t, noteDescription = d)
                    }
                } catch (_: Exception) {
                    // Try next key candidate — don't log key material.
                }
            }

            Log.w(TAG, "All decryption attempts failed for reminder ${encrypted.id}")
            return encrypted.copy(
                id = DECRYPT_FAILED_MARKER + encrypted.id,
                noteTitle = "",
                noteDescription = ""
            )
        }
    }
}

/** True when this reminder is the sentinel returned by [ReminderEntity.fromEncryptedData] on failure. */
fun ReminderEntity.isDecryptFailed(): Boolean = id.startsWith(ReminderEntity.DECRYPT_FAILED_MARKER)
