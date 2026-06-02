package com.amvarpvtltd.swiftNote

import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import com.amvarpvtltd.swiftNote.security.EncryptionUtil
import android.util.Log
import androidx.compose.runtime.Stable

/**
 * Centralized, thread-safe holder for the device/account identity used in encryption.
 *
 * Only authorized call-sites (Application init, nav init, sync settings) should call
 * [DeviceIdentity.set]. All other code should read via [DeviceIdentity.id] or the
 * legacy [myGlobalMobileDeviceId] read-only accessor.
 */
object DeviceIdentity {
    private val _id = AtomicReference("")

    /** Current device identity (passphrase or device-ID). Never empty after app init. */
    val id: String get() = _id.get()

    /**
     * Set the device identity. Should only be called during:
     * - Application.onCreate (initial bootstrap)
     * - Navigation init (passphrase/device lookup)
     * - SyncSettingsScreen (when identity was empty)
     *
     * Logs a warning including the call-site so accidental mutations are easy to spot.
     */
    fun set(value: String, caller: String = inferCaller()) {
        require(value.isNotEmpty()) { "DeviceIdentity cannot be set to an empty string (caller=$caller)" }
        val old = _id.getAndSet(value)
        if (old != value) {
            // SECURITY: do NOT log identity contents or lengths — both fingerprint the user.
            Log.w("DeviceIdentity", "Identity changed by [$caller]")
        }
    }

    /**
     * Set only if currently empty. Safe for fallback paths.
     */
    fun setIfEmpty(value: String, caller: String = inferCaller()) {
        if (_id.get().isEmpty() && value.isNotEmpty()) {
            set(value, caller)
        }
    }

    /** For tests only — resets identity to empty. */
    @VisibleForTestingOnly
    fun resetForTesting(value: String = "") {
        _id.set(value)
        Log.d("DeviceIdentity", "RESET for testing")
    }

    private fun inferCaller(): String {
        return Thread.currentThread().stackTrace
            .drop(4) // skip getStackTrace, inferCaller, set/setIfEmpty, actual caller
            .firstOrNull()
            ?.let { "${it.className.substringAfterLast('.')}.${it.methodName}" }
            ?: "unknown"
    }
}

/** Annotation marking test-only APIs. */
@RequiresOptIn(message = "This API is for testing only.")
@Retention(AnnotationRetention.BINARY)
annotation class VisibleForTestingOnly

/**
 * Legacy read-only accessor. Existing code can still read [myGlobalMobileDeviceId] without changes.
 * Writes must go through [DeviceIdentity.set] or [DeviceIdentity.setIfEmpty].
 */
val myGlobalMobileDeviceId: String
    get() = DeviceIdentity.id

// @Stable: Tells Compose this class won't change unexpectedly, reducing unnecessary recompositions
@Stable
data class Note(
    val title: String = "",
    val description: String = "",
    var id: String = UUID.randomUUID().toString(),
    var mymobiledeviceid: String = myGlobalMobileDeviceId,
    var timestamp: Long = System.currentTimeMillis(),
    var updatedAt: Long = System.currentTimeMillis(),
    val isPinned: Boolean = false,
    val isArchived: Boolean = false,
    val category: String = "",
    val colorKey: String? = null
) {
    // Encrypted versions for Firebase storage
    fun getEncryptedTitle(): String {
        return EncryptionUtil.encrypt(title, mymobiledeviceid)
    }

    fun getEncryptedDescription(): String {
        return EncryptionUtil.encrypt(description, mymobiledeviceid)
    }

    // Create encrypted version for Firebase with preserved timestamp
    // SECURITY: Throws on failure — NEVER sends plaintext to Firebase
    fun toEncryptedData(): Note {
        require(mymobiledeviceid.isNotEmpty()) {
            "Cannot encrypt note $id: mymobiledeviceid is empty"
        }
        return Note(
            title = getEncryptedTitle(),
            description = getEncryptedDescription(),
            id = id,
            mymobiledeviceid = mymobiledeviceid,
            timestamp = timestamp,
            updatedAt = updatedAt,
            isPinned = isPinned,
            isArchived = isArchived,
            category = category,
            colorKey = colorKey
        )
    }

    companion object {
        private const val TAG = "Note"

        /** Sentinel id-prefix used when [fromEncryptedData] fails so the sync layer can drop it. */
        const val DECRYPT_FAILED_MARKER = "__SN_DECRYPT_FAILED__"

        // Create Note from encrypted Firebase data
        fun fromEncryptedData(encryptedData: Note): Note {
            return try {
                val decryptedTitle = EncryptionUtil.decrypt(encryptedData.title, encryptedData.mymobiledeviceid)
                val decryptedDescription = EncryptionUtil.decrypt(encryptedData.description, encryptedData.mymobiledeviceid)

                // If BOTH fields fail to decrypt and the source looked encrypted, treat as a
                // failed decrypt — caller (sync layer) must skip these instead of persisting
                // ciphertext masquerading as plaintext (which previously surfaced as "blank"
                // notes once stripped of HTML).
                val titleLooksEncrypted = EncryptionUtil.isPotentiallyEncrypted(encryptedData.title)
                val descLooksEncrypted = EncryptionUtil.isPotentiallyEncrypted(encryptedData.description)
                if (decryptedTitle == null && decryptedDescription == null &&
                    (titleLooksEncrypted || descLooksEncrypted)) {
                    Log.w(TAG, "Decryption returned null for note ${encryptedData.id} — marking as failed")
                    return encryptedData.copy(
                        id = DECRYPT_FAILED_MARKER + encryptedData.id,
                        title = "",
                        description = ""
                    )
                }

                Note(
                    title = decryptedTitle ?: encryptedData.title,
                    description = decryptedDescription ?: encryptedData.description,
                    id = encryptedData.id,
                    mymobiledeviceid = encryptedData.mymobiledeviceid,
                    timestamp = encryptedData.timestamp,
                    updatedAt = encryptedData.updatedAt,
                    isPinned = encryptedData.isPinned,
                    isArchived = encryptedData.isArchived,
                    category = encryptedData.category,
                    colorKey = encryptedData.colorKey
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error in fromEncryptedData for note ${encryptedData.id}", e)
                // Mark as failed-decrypt so the sync layer can skip/clean it.
                encryptedData.copy(
                    id = DECRYPT_FAILED_MARKER + encryptedData.id,
                    title = "",
                    description = ""
                )
            }
        }

        /**
         * Returns true if the note has no meaningful user content. Strips HTML tags first so
         * empty rich-text shells (`<p></p>`, `<br>`, etc.) and whitespace-only content count
         * as blank. Used by sync/persistence layers to refuse to store or upload empty notes.
         */
        fun isBlank(note: Note): Boolean {
            val title = note.title.trim()
            // Best-effort plain-text strip without pulling Jsoup into this hot path:
            // remove tags + collapse entities/whitespace. Anything left is real content.
            val descPlain = note.description
                .replace(Regex("<[^>]+>"), "")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .trim()
            return title.isEmpty() && descPlain.isEmpty()
        }
    }
}

/** Convenience extension matching the companion helper. */
fun Note.isBlank(): Boolean = Note.isBlank(this)

/** True when this note is the sentinel returned by [Note.fromEncryptedData] on failure. */
fun Note.isDecryptFailed(): Boolean = id.startsWith(Note.DECRYPT_FAILED_MARKER)

// Backwards compatibility alias — Firebase getValue() uses class name for deserialization
// This ensures existing Firebase data (stored as "dataclass") can still be read
@Suppress("unused")
typealias dataclass = Note
