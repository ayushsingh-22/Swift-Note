package com.amvarpvtltd.swiftNote.security

import android.util.Base64
import android.util.Log
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec
import java.security.MessageDigest

object EncryptionUtil {
    private const val ALGORITHM = "AES"
    private const val TRANSFORMATION = "AES/ECB/PKCS5Padding"
    private const val TAG = "EncryptionUtil"
    private const val AES_BLOCK_SIZE_BYTES = 16 // AES block size is 128 bits = 16 bytes

    private fun generateKey(deviceId: String, keyBytesLen: Int = AES_BLOCK_SIZE_BYTES): SecretKey {
        val actualDeviceId = deviceId.ifEmpty {
            Log.w(TAG, "Device ID is empty, using fallback. This is a security risk if multiple devices use it.")
            "DefaultDeviceId123"
        }
        // If deviceId is prefixed with "HEX:", interpret the following as raw hex key material
        val hash: ByteArray = try {
            if (actualDeviceId.startsWith("HEX:", ignoreCase = true)) {
                val hex = actualDeviceId.substringAfter("HEX:")
                hexToBytes(hex).let { bytes ->
                    // If provided raw bytes shorter than desired, hash them to get key material
                    if (bytes.size >= keyBytesLen) bytes else MessageDigest.getInstance("SHA-256").digest(bytes)
                }
            } else {
                val digest = MessageDigest.getInstance("SHA-256")
                digest.digest(actualDeviceId.toByteArray(Charsets.UTF_8))
            }
        } catch (e: Exception) {
            MessageDigest.getInstance("SHA-256").digest(actualDeviceId.toByteArray(Charsets.UTF_8))
        }
        // If requested length is <= hash length, copyOf will trim/extend accordingly
        val keyBytes = when {
            keyBytesLen <= 0 -> hash.copyOf(AES_BLOCK_SIZE_BYTES)
            else -> hash.copyOf(keyBytesLen)
        }
        return SecretKeySpec(keyBytes, ALGORITHM)
    }

    private fun hexToBytes(hex: String): ByteArray {
        val s = hex.trim().removePrefix("0x").replace(Regex("[^0-9a-fA-F]"), "")
        val len = s.length
        val out = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            out[i / 2] = ((s.substring(i, i + 2).toInt(16)) and 0xFF).toByte()
            i += 2
        }
        return out
    }

    /**
     * Returns a short hex preview of the derived AES key for logging (first 8 bytes hex)
     */
    fun getKeyPreview(deviceId: String): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(deviceId.toByteArray(Charsets.UTF_8))
            hash.copyOf(8).joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            "unknown"
        }
    }

    fun encrypt(plainText: String, deviceId: String): String {
        return try {
            if (plainText.isEmpty()) return plainText // No need to encrypt empty strings

            val key = generateKey(deviceId)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key)

            val encryptedBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            val encrypted = Base64.encodeToString(encryptedBytes, Base64.NO_WRAP)
            // Log.d(TAG, "Encryption successful, result length: ${encrypted.length}")
            encrypted
        } catch (e: Exception) {
            Log.e(TAG, "Encryption failed for text with length ${plainText.length}", e)
            plainText // Returning original text on encryption failure might hide issues. Consider throwing or returning null.
        }
    }

    fun decrypt(encryptedText: String, deviceId: String): String? {
        if (encryptedText.isEmpty()) {
            // Log.d(TAG, "encryptedText is empty, returning as is.")
            return encryptedText // Or null/empty string depending on desired behavior for empty inputs.
        }

        if (!isPotentiallyEncrypted(encryptedText)) {
            Log.w(TAG, "Text (first 20 chars: '${encryptedText.take(20)}') does not appear to be valid encrypted data for this system. Cannot decrypt.")
            // This data is not structured like our AES/ECB/Base64 encrypted data.
            // It might be plaintext that was mistakenly passed here, or corrupted.
            // Returning null is safer than returning the (potentially sensitive) input.
            return null
        }

        return try {
            // Try multiple derived key lengths to be compatible with older encryption variants
            val candidateKeyLengths = listOf(AES_BLOCK_SIZE_BYTES, 32) // AES-128 and AES-256 key lengths
            val encryptedBytes = try { Base64.decode(encryptedText, Base64.NO_WRAP) } catch (e: IllegalArgumentException) {
                Log.e(TAG, "Base64 decode failed for text (first 20: '${encryptedText.take(20)}')", e)
                return null
            }

            if (encryptedBytes.isEmpty() || encryptedBytes.size % AES_BLOCK_SIZE_BYTES != 0) {
                Log.e(TAG, "Decoded Base64 not multiple of AES block size. length=${encryptedBytes.size} text='${encryptedText.take(50)}...'")
                return null
            }

            for (keyLen in candidateKeyLengths) {
                try {
                    val key = generateKey(deviceId, keyLen)
                    val cipher = Cipher.getInstance(TRANSFORMATION)
                    cipher.init(Cipher.DECRYPT_MODE, key)
                    val decryptedBytes = cipher.doFinal(encryptedBytes)
                    val decrypted = String(decryptedBytes, Charsets.UTF_8)
                    Log.d(TAG, "Decryption OK with keyLen=$keyLen for text (first20:'${encryptedText.take(20)}')")
                    return decrypted
                } catch (e: Exception) {
                    Log.d(TAG, "Decryption attempt with keyLen=$keyLen failed for text (first20:'${encryptedText.take(20)}')", e)
                    // try next
                }
            }

            Log.e(TAG, "All decryption key-length attempts failed for text (first20:'${encryptedText.take(20)}')")
            null
        } catch (e: Exception) {
            Log.e(TAG, "Decryption unexpected failure for text (first 20: '${encryptedText.take(20)}')", e)
            null
        }
    }

    /**
     * Checks if a string *could* be Base64 encoded data that fits the expected structure
     * for AES/ECB/PKCS5Padding encryption (multiple of AES block size after decoding).
     * This is a heuristic and not a definitive proof of encryption by *this* system's key.
     */
    fun isPotentiallyEncrypted(text: String): Boolean {
        if (text.isEmpty()) return false

        // Quick check for non-Base64 characters or typical plaintext characteristics.
        if (text.contains(" ") || text.contains("\n")) return false

        // It also doesn't guarantee the padding is correct for the length.
        if (!text.matches(Regex("^[A-Za-z0-9+/]*={0,2}$"))) {
            // Log.d(TAG, "isPotentiallyEncrypted: Text fails Base64 regex: ${text.take(20)}")
            return false
        }

        return try {
            val decodedBytes = Base64.decode(text, Base64.NO_WRAP)
            if (decodedBytes.isEmpty()) {
                // Log.d(TAG, "isPotentiallyEncrypted: Decoded bytes are empty: ${text.take(20)}")
                return false
            }
            // must be a multiple of the block size and practically at least one block long.
            val isValidLength = decodedBytes.size >= AES_BLOCK_SIZE_BYTES && decodedBytes.size % AES_BLOCK_SIZE_BYTES == 0

            isValidLength
        } catch (e: IllegalArgumentException) {

            false
        }
    }
}
