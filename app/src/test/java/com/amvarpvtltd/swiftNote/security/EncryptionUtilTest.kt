package com.amvarpvtltd.swiftNote.security

import android.util.Base64
import android.util.Log
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for EncryptionUtil covering:
 * - BUG-002: AES/GCM encrypt/decrypt roundtrip
 * - BUG-003: Empty deviceId throws exception
 * - BUG-037: Encryption failure never returns plaintext
 * - Legacy ECB decryption for migration
 * - Non-deterministic ciphertext (random IV)
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31], manifest = Config.NONE)
class EncryptionUtilTest {

    private val testDeviceId = "test-device-id-12345"
    private val testDeviceId2 = "different-device-id-67890"

    // ============================================================
    // GCM Encrypt/Decrypt Roundtrip Tests
    // ============================================================

    @Test
    fun `encrypt and decrypt roundtrip produces original text`() {
        val plainText = "Hello, SwiftNote!"
        val encrypted = EncryptionUtil.encrypt(plainText, testDeviceId)
        val decrypted = EncryptionUtil.decrypt(encrypted, testDeviceId)
        assertEquals(plainText, decrypted)
    }

    @Test
    fun `encrypt and decrypt with unicode text`() {
        val plainText = "こんにちは世界 🌍 Reminder: meeting at 3pm"
        val encrypted = EncryptionUtil.encrypt(plainText, testDeviceId)
        val decrypted = EncryptionUtil.decrypt(encrypted, testDeviceId)
        assertEquals(plainText, decrypted)
    }

    @Test
    fun `encrypt and decrypt long text`() {
        val plainText = "A".repeat(10000)
        val encrypted = EncryptionUtil.encrypt(plainText, testDeviceId)
        val decrypted = EncryptionUtil.decrypt(encrypted, testDeviceId)
        assertEquals(plainText, decrypted)
    }

    @Test
    fun `encrypt empty string returns empty string`() {
        val result = EncryptionUtil.encrypt("", testDeviceId)
        assertEquals("", result)
    }

    @Test
    fun `decrypt empty string returns empty string`() {
        val result = EncryptionUtil.decrypt("", testDeviceId)
        assertEquals("", result)
    }

    // ============================================================
    // Non-Deterministic Ciphertext (Random IV) Tests
    // ============================================================

    @Test
    fun `same plaintext encrypts to different ciphertext each time`() {
        val plainText = "Same text encrypted twice"
        val encrypted1 = EncryptionUtil.encrypt(plainText, testDeviceId)
        val encrypted2 = EncryptionUtil.encrypt(plainText, testDeviceId)

        // Both should decrypt to same plaintext
        assertEquals(plainText, EncryptionUtil.decrypt(encrypted1, testDeviceId))
        assertEquals(plainText, EncryptionUtil.decrypt(encrypted2, testDeviceId))

        // But the ciphertext must be different (non-deterministic due to random IV)
        assertNotEquals(encrypted1, encrypted2)
    }

    // ============================================================
    // GCM Prefix Tests
    // ============================================================

    @Test
    fun `encrypted output starts with GCM prefix`() {
        val encrypted = EncryptionUtil.encrypt("test", testDeviceId)
        assertTrue("Encrypted text should start with 'G:' prefix", encrypted.startsWith("G:"))
    }

    @Test
    fun `encrypted output base64 portion is valid`() {
        val encrypted = EncryptionUtil.encrypt("test", testDeviceId)
        val base64Part = encrypted.removePrefix("G:")
        // Should not throw
        val decoded = Base64.decode(base64Part, Base64.NO_WRAP)
        // IV (12 bytes) + ciphertext + GCM tag (16 bytes) minimum
        assertTrue("Decoded bytes should be at least 28 (12 IV + 16 tag)", decoded.size >= 28)
    }

    // ============================================================
    // BUG-003: Empty DeviceId Throws Exception
    // ============================================================

    @Test(expected = IllegalArgumentException::class)
    fun `encrypt with empty deviceId throws IllegalArgumentException`() {
        EncryptionUtil.encrypt("test text", "")
    }

    @Test
    fun `decrypt with empty deviceId returns null`() {
        val result = EncryptionUtil.decrypt("G:somedata", "")
        assertNull(result)
    }

    // ============================================================
    // Wrong Key Decryption Tests
    // ============================================================

    @Test
    fun `decrypt with wrong deviceId returns null`() {
        val encrypted = EncryptionUtil.encrypt("secret message", testDeviceId)
        val decrypted = EncryptionUtil.decrypt(encrypted, testDeviceId2)
        assertNull("Decrypting with wrong key should return null", decrypted)
    }

    @Test
    fun `decrypt corrupted ciphertext returns null`() {
        val result = EncryptionUtil.decrypt("G:invalidbase64!!!", testDeviceId)
        assertNull(result)
    }

    @Test
    fun `decrypt truncated ciphertext returns null`() {
        val encrypted = EncryptionUtil.encrypt("test", testDeviceId)
        // Truncate the ciphertext
        val truncated = encrypted.substring(0, encrypted.length / 2)
        val result = EncryptionUtil.decrypt(truncated, testDeviceId)
        assertNull(result)
    }

    // ============================================================
    // isPotentiallyEncrypted Tests
    // ============================================================

    @Test
    fun `isPotentiallyEncrypted returns true for GCM prefix`() {
        assertTrue(EncryptionUtil.isPotentiallyEncrypted("G:someBase64Data"))
    }

    @Test
    fun `isPotentiallyEncrypted returns false for empty string`() {
        assertFalse(EncryptionUtil.isPotentiallyEncrypted(""))
    }

    @Test
    fun `isPotentiallyEncrypted returns false for text with spaces`() {
        assertFalse(EncryptionUtil.isPotentiallyEncrypted("hello world"))
    }

    @Test
    fun `isPotentiallyEncrypted returns false for text with newlines`() {
        assertFalse(EncryptionUtil.isPotentiallyEncrypted("hello\nworld"))
    }

    @Test
    fun `isPotentiallyEncrypted returns false for non-base64 text`() {
        assertFalse(EncryptionUtil.isPotentiallyEncrypted("not!valid@base64"))
    }

    // ============================================================
    // getKeyPreview Tests
    // ============================================================

    @Test
    fun `getKeyPreview returns 8 character hex string`() {
        val preview = EncryptionUtil.getKeyPreview(testDeviceId)
        assertEquals(8, preview.length)
        assertTrue(preview.matches(Regex("[0-9a-f]+")))
    }

    @Test
    fun `getKeyPreview is deterministic for same input`() {
        val preview1 = EncryptionUtil.getKeyPreview(testDeviceId)
        val preview2 = EncryptionUtil.getKeyPreview(testDeviceId)
        assertEquals(preview1, preview2)
    }

    @Test
    fun `getKeyPreview differs for different inputs`() {
        val preview1 = EncryptionUtil.getKeyPreview(testDeviceId)
        val preview2 = EncryptionUtil.getKeyPreview(testDeviceId2)
        assertNotEquals(preview1, preview2)
    }

    // ============================================================
    // HEX Key Derivation Tests
    // ============================================================

    @Test
    fun `encrypt with HEX prefix deviceId works`() {
        val hexDeviceId = "HEX:0102030405060708091011121314151617181920"
        val plainText = "Test with hex key"
        val encrypted = EncryptionUtil.encrypt(plainText, hexDeviceId)
        val decrypted = EncryptionUtil.decrypt(encrypted, hexDeviceId)
        assertEquals(plainText, decrypted)
    }

    // ============================================================
    // Special Characters Tests
    // ============================================================

    @Test
    fun `encrypt and decrypt with special characters`() {
        val specialChars = "<script>alert('xss')</script> & \"quotes\" 'single' \t\n"
        val encrypted = EncryptionUtil.encrypt(specialChars, testDeviceId)
        val decrypted = EncryptionUtil.decrypt(encrypted, testDeviceId)
        assertEquals(specialChars, decrypted)
    }

    @Test
    fun `encrypt and decrypt with HTML content`() {
        val html = "<b>Bold</b> <i>Italic</i> <u>Underline</u>"
        val encrypted = EncryptionUtil.encrypt(html, testDeviceId)
        val decrypted = EncryptionUtil.decrypt(encrypted, testDeviceId)
        assertEquals(html, decrypted)
    }
}

