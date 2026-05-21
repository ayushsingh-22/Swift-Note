package com.amvarpvtltd.swiftNote.security

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Cryptographic hash utilities.
 *
 * For password/passphrase verification, use [hashPassphrase] and [verifyPassphrase]
 * which use PBKDF2-HMAC-SHA256 with 120,000 iterations (OWASP 2024 recommendation).
 *
 * Plain [sha256] is only for non-security uses (checksums, deduplication).
 */
object HashUtils {
    private val secureRandom = SecureRandom()

    // PBKDF2 parameters — OWASP recommended minimum for PBKDF2-HMAC-SHA256
    private const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val PBKDF2_ITERATIONS = 120_000
    private const val PBKDF2_KEY_LENGTH_BITS = 256
    private const val SALT_LENGTH_BYTES = 16
    private const val HASH_SEPARATOR = ":"

    /**
     * Hash a passphrase using PBKDF2-HMAC-SHA256.
     *
     * Returns: "iterations:salt_hex:hash_hex"
     *
     * This is suitable for storing passphrase hashes locally to verify user input
     * without storing the passphrase in plaintext.
     */
    fun hashPassphrase(passphrase: String): String {
        val salt = ByteArray(SALT_LENGTH_BYTES).also { secureRandom.nextBytes(it) }
        val spec = PBEKeySpec(
            passphrase.toCharArray(),
            salt,
            PBKDF2_ITERATIONS,
            PBKDF2_KEY_LENGTH_BITS
        )
        val factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM)
        val hash = factory.generateSecret(spec).encoded
        spec.clearPassword()

        val saltHex = salt.joinToString("") { "%02x".format(it) }
        val hashHex = hash.joinToString("") { "%02x".format(it) }
        return "$PBKDF2_ITERATIONS$HASH_SEPARATOR$saltHex$HASH_SEPARATOR$hashHex"
    }

    /**
     * Verify a passphrase against a stored PBKDF2 hash.
     * Uses constant-time comparison to prevent timing attacks.
     *
     * @param passphrase The user-provided passphrase to verify
     * @param storedHash The stored hash string (from [hashPassphrase])
     * @return true if the passphrase matches
     */
    fun verifyPassphrase(passphrase: String, storedHash: String): Boolean {
        return try {
            val parts = storedHash.split(HASH_SEPARATOR)
            if (parts.size != 3) return false

            val iterations = parts[0].toIntOrNull() ?: return false
            val salt = hexStringToBytes(parts[1])
            val expectedHash = hexStringToBytes(parts[2])

            val spec = PBEKeySpec(
                passphrase.toCharArray(),
                salt,
                iterations,
                expectedHash.size * 8
            )
            val factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM)
            val actualHash = factory.generateSecret(spec).encoded
            spec.clearPassword()

            // Constant-time comparison to prevent timing attacks
            MessageDigest.isEqual(actualHash, expectedHash)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Simple SHA-256 hash. Do NOT use for passwords/passphrases.
     * Use [hashPassphrase] instead for anything security-sensitive.
     */
    fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Generate a cryptographically secure random salt.
     */
    fun generateSalt(lengthBytes: Int = SALT_LENGTH_BYTES): String {
        val bytes = ByteArray(lengthBytes)
        secureRandom.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun hexStringToBytes(hex: String): ByteArray {
        val len = hex.length
        val bytes = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            bytes[i / 2] = ((hex.substring(i, i + 2).toInt(16)) and 0xFF).toByte()
            i += 2
        }
        return bytes
    }
}
