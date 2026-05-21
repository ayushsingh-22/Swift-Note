package com.amvarpvtltd.swiftNote.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Production-grade encryption utility.
 *
 * Security improvements over original:
 * - AES/GCM (authenticated encryption) replaces insecure AES/ECB
 * - Random IV per encryption (non-deterministic ciphertext)
 * - No hardcoded fallback keys — empty deviceId throws immediately
 * - Encryption failure throws instead of returning plaintext
 * - Android Keystore integration for local-only encryption needs
 * - Legacy ECB decryption preserved for migration of existing data
 */
object EncryptionUtil {
    private const val ALGORITHM = "AES"
    private const val TRANSFORMATION_GCM = "AES/GCM/NoPadding"
    private const val LEGACY_TRANSFORMATION_ECB = "AES/ECB/PKCS5Padding"
    private const val TAG = "EncryptionUtil"
    private const val AES_KEY_SIZE_BYTES = 16 // AES-128
    private const val GCM_IV_SIZE_BYTES = 12
    private const val GCM_TAG_SIZE_BITS = 128

    // Prefix to distinguish GCM-encrypted data from legacy ECB data
    private const val GCM_PREFIX = "G:"

    // Android Keystore constants
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEYSTORE_ALIAS = "SwiftNoteLocalEncKey"

    // ============================================================
    // PUBLIC API — Passphrase-derived encryption (for sync/Firebase)
    // ============================================================

    /**
     * Encrypt plaintext using AES-128-GCM with a key derived from [deviceId].
     *
     * Output format: "G:" + Base64(12-byte-IV || ciphertext || 16-byte-GCM-tag)
     *
     * @throws IllegalArgumentException if deviceId is empty
     * @throws RuntimeException if encryption fails (NEVER returns plaintext)
     */
    fun encrypt(plainText: String, deviceId: String): String {
        if (plainText.isEmpty()) return plainText
        requireValidDeviceId(deviceId)

        return try {
            val key = deriveKey(deviceId)
            val iv = ByteArray(GCM_IV_SIZE_BYTES).also { SecureRandom().nextBytes(it) }
            val cipher = Cipher.getInstance(TRANSFORMATION_GCM)
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_SIZE_BITS, iv))
            val cipherBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            // IV || ciphertext+tag
            val combined = iv + cipherBytes
            GCM_PREFIX + Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (e: IllegalArgumentException) {
            throw e // Re-throw validation errors
        } catch (e: Exception) {
            // SECURITY: Never return plaintext on failure
            Log.e(TAG, "Encryption failed (length=${plainText.length})")
            throw RuntimeException("Encryption failed — refusing to expose plaintext", e)
        }
    }

    /**
     * Decrypt ciphertext. Supports both GCM (new) and legacy ECB (migration).
     *
     * @return decrypted plaintext, or null if decryption fails
     */
    fun decrypt(encryptedText: String, deviceId: String): String? {
        if (encryptedText.isEmpty()) return encryptedText
        if (deviceId.isEmpty()) {
            Log.w(TAG, "Cannot decrypt: empty deviceId")
            return null
        }

        // Route to appropriate decryption based on format
        return if (encryptedText.startsWith(GCM_PREFIX)) {
            decryptGcm(encryptedText.removePrefix(GCM_PREFIX), deviceId)
        } else {
            decryptLegacyEcb(encryptedText, deviceId)
        }
    }

    // ============================================================
    // ANDROID KEYSTORE — For local-only encryption (preferences, etc.)
    // ============================================================

    /**
     * Encrypt data using a hardware-backed key from Android Keystore.
     * Use this for local-only data that never leaves the device.
     */
    fun encryptWithKeystore(plainText: String): String {
        if (plainText.isEmpty()) return plainText
        val key = getOrCreateKeystoreKey()
        val cipher = Cipher.getInstance(TRANSFORMATION_GCM)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val cipherBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        val combined = iv + cipherBytes
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    /**
     * Decrypt data that was encrypted with [encryptWithKeystore].
     */
    fun decryptWithKeystore(encryptedText: String): String? {
        if (encryptedText.isEmpty()) return encryptedText
        return try {
            val combined = Base64.decode(encryptedText, Base64.NO_WRAP)
            if (combined.size < GCM_IV_SIZE_BYTES + 1) return null
            val iv = combined.copyOfRange(0, GCM_IV_SIZE_BYTES)
            val cipherBytes = combined.copyOfRange(GCM_IV_SIZE_BYTES, combined.size)
            val key = getOrCreateKeystoreKey()
            val cipher = Cipher.getInstance(TRANSFORMATION_GCM)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_SIZE_BITS, iv))
            String(cipher.doFinal(cipherBytes), Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e(TAG, "Keystore decryption failed")
            null
        }
    }

    // ============================================================
    // UTILITY
    // ============================================================

    /**
     * Returns a short, non-reversible preview of the derived key for debug identification.
     * Only shows first 4 bytes of the SHA-256 hash — not enough to reconstruct the key.
     */
    fun getKeyPreview(deviceId: String): String {
        return try {
            val hash = MessageDigest.getInstance("SHA-256")
                .digest(deviceId.toByteArray(Charsets.UTF_8))
            hash.copyOf(4).joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            "err"
        }
    }

    /**
     * Heuristic check if a string looks like data encrypted by this system.
     */
    fun isPotentiallyEncrypted(text: String): Boolean {
        if (text.isEmpty()) return false
        if (text.startsWith(GCM_PREFIX)) return true
        if (text.contains(" ") || text.contains("\n")) return false
        if (!text.matches(Regex("^[A-Za-z0-9+/]*={0,2}$"))) return false
        return try {
            val decoded = Base64.decode(text, Base64.NO_WRAP)
            decoded.size >= AES_KEY_SIZE_BYTES && decoded.size % AES_KEY_SIZE_BYTES == 0
        } catch (e: IllegalArgumentException) {
            false
        }
    }

    // ============================================================
    // PRIVATE IMPLEMENTATION
    // ============================================================

    private fun requireValidDeviceId(deviceId: String) {
        require(deviceId.isNotEmpty()) {
            "Device ID / passphrase must not be empty. " +
            "This prevents accidental use of a shared fallback key."
        }
    }

    /**
     * Derive an AES key from a passphrase/deviceId using SHA-256.
     * The first [keyBytesLen] bytes of the hash are used as the key.
     */
    private fun deriveKey(deviceId: String, keyBytesLen: Int = AES_KEY_SIZE_BYTES): SecretKey {
        requireValidDeviceId(deviceId)

        val hash: ByteArray = try {
            if (deviceId.startsWith("HEX:", ignoreCase = true)) {
                val hex = deviceId.substringAfter("HEX:")
                val hexBytes = hexToBytes(hex)
                if (hexBytes.size >= keyBytesLen) hexBytes
                else MessageDigest.getInstance("SHA-256").digest(hexBytes)
            } else {
                MessageDigest.getInstance("SHA-256")
                    .digest(deviceId.toByteArray(Charsets.UTF_8))
            }
        } catch (e: Exception) {
            MessageDigest.getInstance("SHA-256")
                .digest(deviceId.toByteArray(Charsets.UTF_8))
        }

        val keyBytes = hash.copyOf(keyBytesLen.coerceAtLeast(AES_KEY_SIZE_BYTES))
        return SecretKeySpec(keyBytes, ALGORITHM)
    }

    private fun decryptGcm(base64Data: String, deviceId: String): String? {
        return try {
            val combined = Base64.decode(base64Data, Base64.NO_WRAP)
            if (combined.size < GCM_IV_SIZE_BYTES + 16) return null

            val iv = combined.copyOfRange(0, GCM_IV_SIZE_BYTES)
            val cipherBytes = combined.copyOfRange(GCM_IV_SIZE_BYTES, combined.size)

            val key = deriveKey(deviceId)
            val cipher = Cipher.getInstance(TRANSFORMATION_GCM)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_SIZE_BITS, iv))
            String(cipher.doFinal(cipherBytes), Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e(TAG, "GCM decryption failed")
            null
        }
    }

    /**
     * Legacy ECB decryption — kept ONLY for reading old data during migration.
     * New data is always encrypted with GCM.
     */
    private fun decryptLegacyEcb(encryptedText: String, deviceId: String): String? {
        if (!isPotentiallyEncrypted(encryptedText)) return null
        return try {
            val encryptedBytes = try {
                Base64.decode(encryptedText, Base64.NO_WRAP)
            } catch (e: IllegalArgumentException) {
                return null
            }
            if (encryptedBytes.isEmpty() || encryptedBytes.size % AES_KEY_SIZE_BYTES != 0) return null

            for (keyLen in listOf(AES_KEY_SIZE_BYTES, 32)) {
                try {
                    val key = deriveKey(deviceId, keyLen)
                    val cipher = Cipher.getInstance(LEGACY_TRANSFORMATION_ECB)
                    cipher.init(Cipher.DECRYPT_MODE, key)
                    val decryptedBytes = cipher.doFinal(encryptedBytes)
                    return String(decryptedBytes, Charsets.UTF_8)
                } catch (e: Exception) {
                    // Try next key length
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Get or create a hardware-backed AES key in Android Keystore.
     */
    private fun getOrCreateKeystoreKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)

        // Return existing key if present
        keyStore.getKey(KEYSTORE_ALIAS, null)?.let { return it as SecretKey }

        // Generate new key backed by hardware (or TEE)
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE
        )
        keyGenerator.init(
            KeyGenParameterSpec.Builder(
                KEYSTORE_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return keyGenerator.generateKey()
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
}
