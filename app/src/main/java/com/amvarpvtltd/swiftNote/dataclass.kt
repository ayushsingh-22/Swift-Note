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
            Log.w("DeviceIdentity", "Identity changed by [$caller] (old.length=${old.length}, new.length=${value.length})")
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
        Log.d("DeviceIdentity", "RESET for testing (length=${value.length})")
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
    var timestamp: Long = System.currentTimeMillis()
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
            timestamp = timestamp  // preserve original timestamp
        )
    }

    companion object {
        private const val TAG = "Note"

        // Create Note from encrypted Firebase data
        fun fromEncryptedData(encryptedData: Note): Note {
            return try {
                val decryptedTitle = EncryptionUtil.decrypt(encryptedData.title, encryptedData.mymobiledeviceid) ?: encryptedData.title
                val decryptedDescription = EncryptionUtil.decrypt(encryptedData.description, encryptedData.mymobiledeviceid) ?: encryptedData.description

                Note(
                    title = decryptedTitle,
                    description = decryptedDescription,
                    id = encryptedData.id,
                    mymobiledeviceid = encryptedData.mymobiledeviceid,
                    timestamp = encryptedData.timestamp  // preserve timestamp
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error in fromEncryptedData for note ${encryptedData.id}", e)
                // Return the original data - it might not be encrypted
                encryptedData
            }
        }
    }
}

// Backwards compatibility alias — Firebase getValue() uses class name for deserialization
// This ensures existing Firebase data (stored as "dataclass") can still be read
@Suppress("unused")
typealias dataclass = Note
