package com.amvarpvtltd.swiftNote

import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import com.amvarpvtltd.swiftNote.security.EncryptionUtil
import android.util.Log
import androidx.compose.runtime.Stable

// BUG-007 FIX: Thread-safe global device ID using AtomicReference
// Prevents race conditions when multiple threads read/write concurrently
private val _globalDeviceId = AtomicReference("")

var myGlobalMobileDeviceId: String
    get() = _globalDeviceId.get()
    set(value) {
        val old = _globalDeviceId.getAndSet(value)
        if (old != value && value.isNotEmpty()) {
            Log.d("GlobalDeviceId", "Device ID updated (length=${value.length})")
        }
    }

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
