package com.amvarpvtltd.swiftNote

import java.util.UUID
import com.amvarpvtltd.swiftNote.security.EncryptionUtil
import android.util.Log


var myGlobalMobileDeviceId: String = ""


data class dataclass(
    val title: String = "",
    val description: String = "",
    var id: String = UUID.randomUUID().toString(),
    var mymobiledeviceid: String =  myGlobalMobileDeviceId,
    var timestamp: Long = System.currentTimeMillis()  // Always use current time when creating new note
) {
    // Encrypted versions for Firebase storage
    fun getEncryptedTitle(): String {
        return EncryptionUtil.encrypt(title, mymobiledeviceid)
    }

    fun getEncryptedDescription(): String {
        return EncryptionUtil.encrypt(description, mymobiledeviceid)
    }

    // Create encrypted version for Firebase with preserved timestamp
    fun toEncryptedData(): dataclass {
        // Log key preview for debugging
        try {
            val preview = EncryptionUtil.getKeyPreview(mymobiledeviceid)
            Log.d("dataclass", "Encrypting note ${id} using deviceId='${mymobiledeviceid.take(40)}' keyPreview=$preview")
        } catch (e: Exception) {
            // ignore logging failures
        }

        return dataclass(
            title = getEncryptedTitle(),
            description = getEncryptedDescription(),
            id = id,
            mymobiledeviceid = mymobiledeviceid,
            timestamp = timestamp  // preserve original timestamp
        )
    }

    companion object {
        private const val TAG = "dataclass"

        // Create dataclass from encrypted Firebase data
        fun fromEncryptedData(encryptedData: dataclass): dataclass {
            return try {
                Log.d(TAG, "Decrypting note with ID: ${encryptedData.id}")
                Log.d(TAG, "Device ID for decryption: ${encryptedData.mymobiledeviceid}")

                val decryptedTitle = EncryptionUtil.decrypt(encryptedData.title, encryptedData.mymobiledeviceid) ?: encryptedData.title
                val decryptedDescription = EncryptionUtil.decrypt(encryptedData.description, encryptedData.mymobiledeviceid) ?: encryptedData.description

                Log.d(TAG, "Decryption completed for note: ${encryptedData.id}")

                dataclass(
                    title = decryptedTitle,
                    description = decryptedDescription,
                    id = encryptedData.id,
                    mymobiledeviceid = encryptedData.mymobiledeviceid,
                    timestamp = encryptedData.timestamp  // preserve timestamp
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error in fromEncryptedData", e)
                // Return the original data - it might not be encrypted
                encryptedData
            }
        }
    }
}
