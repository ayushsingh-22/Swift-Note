package com.amvarpvtltd.swiftNote.security

import java.security.MessageDigest
import java.security.SecureRandom

object HashUtils {
    private val secureRandom = SecureRandom()

    fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun generateSalt(lengthBytes: Int = 16): String {
        val bytes = ByteArray(lengthBytes)
        secureRandom.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }
}

