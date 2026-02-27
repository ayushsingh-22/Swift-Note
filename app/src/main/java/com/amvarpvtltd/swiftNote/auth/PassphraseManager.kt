package com.amvarpvtltd.swiftNote.auth

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.amvarpvtltd.swiftNote.utils.QRUtils
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import androidx.core.content.edit

object PassphraseManager {
    private const val TAG = "PassphraseManager"
    private const val PREFS = "passphrase_prefs"
    private const val KEY_PASSPHRASE = "device_passphrase"

    fun getStoredPassphrase(context: Context): String? {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_PASSPHRASE, null)
    }


    suspend fun storePassphrase(context: Context, passphrase: String): Result<Unit> = withContext(Dispatchers.IO) {
        // Ensure authenticated before writing to Firebase
        val authResult = ensureAuthenticated()
        if (authResult.isFailure) {
            val ex = authResult.exceptionOrNull()
            Log.e(TAG, "Auth failed before storePassphrase: ${ex?.message}")
            return@withContext Result.failure(Exception("Authentication failed: ${ex?.message ?: "unknown"}"))
        }

        return@withContext try {
            // Store locally
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit {
                    putString(KEY_PASSPHRASE, passphrase)
                }

            // Store in Firebase under the passphrase node
            val database = FirebaseDatabase.getInstance()
            val userRef = database.getReference("users").child(passphrase)

            val userData = mapOf(
                "passphrase" to passphrase,
                "createdAt" to System.currentTimeMillis(),
                "deviceType" to "android",
                "lastActiveAt" to System.currentTimeMillis()
            )

            userRef.updateChildren(userData).await()

            Log.d(TAG, "Passphrase stored successfully: $passphrase")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to store passphrase", e)
            Result.failure(e)
        }
    }

    // Ensure the app is authenticated (anonymous) so database rules that require auth can be satisfied
    suspend fun ensureAuthenticated(): Result<Unit> = withContext(Dispatchers.IO) {
         return@withContext try {
             val auth = FirebaseAuth.getInstance()
             if (auth.currentUser != null) {
                 return@withContext Result.success(Unit)
             }
             val authResult = auth.signInAnonymously().await()
             val user = authResult.user
             if (user != null) {
                 Log.d(TAG, "Signed in anonymously: ${user.uid}")
                 Result.success(Unit)
             } else {
                 Result.failure(Exception("Anonymous sign-in returned null user"))
             }
         } catch (e: Exception) {
             Log.w(TAG, "Anonymous sign-in failed", e)
             Result.failure(e)
         }
     }

    suspend fun verifyPassphrase(passphrase: String): Result<Boolean> = withContext(Dispatchers.IO) {
        // First, ensure we are authenticated (some DB rules may require authentication)
        val authResult = ensureAuthenticated()
        if (authResult.isFailure) {
            val ex = authResult.exceptionOrNull()
            Log.e(TAG, "Auth failure before verifyPassphrase", ex)
            return@withContext Result.failure(Exception("Authentication failed: ${ex?.message ?: "unknown"}. Please check network or Firebase configuration."))
        }

        return@withContext try {
            val database = FirebaseDatabase.getInstance()
            val userRef = database.getReference("users").child(passphrase)
            val snapshot = userRef.get().await()

            val exists = snapshot.exists()
            Log.d(TAG, "Passphrase verification: $passphrase exists: $exists")
            Result.success(exists)
        } catch (e: Exception) {
            // Map permission denied errors to clearer messages for the UI
            val message = e.message ?: "Unknown error"
            Log.e(TAG, "Failed to verify passphrase", e)
            if (message.contains("Permission denied", ignoreCase = true)) {
                // Provide actionable guidance for developers/operators in the error message
                return@withContext Result.failure(Exception("Permission denied: passphrase not accessible. Ensure your Realtime Database rules allow reads for authenticated users or adjust rules for /users nodes."))
            }
            if (message.contains("Network error", ignoreCase = true) || message.contains("connect", ignoreCase = true)) {
                return@withContext Result.failure(Exception("Network error: unable to verify passphrase. Check your connection and try again."))
            }
            Result.failure(e)
        }
    }


    fun generateQRCode(passphrase: String): Bitmap {
        // Create a deep link format for the QR code
        val deepLink = "SwiftNote://sync?passphrase=$passphrase"
        return QRUtils.generateQrBitmap(deepLink)
    }

    fun extractPassphraseFromQR(qrContent: String): String? {
        try {
            Log.d(TAG, "Processing QR content: $qrContent")

            // Basic normalization: trim, remove control characters and surrounding quotes
            var content = qrContent.trim().replace("\n", "").replace("\r", "").trim()
            if (content.startsWith("\"") && content.endsWith("\"")) {
                content = content.substring(1, content.length - 1).trim()
            }

            // Iteratively URL-decode up to a few times to handle nested encodings
            try {
                var decoded = content
                var attempts = 0
                do {
                    val prev = decoded
                    try {
                        decoded = java.net.URLDecoder.decode(prev, "UTF-8").trim()
                    } catch (e: Exception) {
                        Log.d(TAG, "Inner URL-decode failed", e)
                        break
                    }
                    attempts++
                } while (decoded != content && attempts < 3)
                if (decoded != content) {
                    Log.d(TAG, "Nested URL-decode produced: ${'$'}{decoded.take(120)}")
                    content = decoded
                }
            } catch (e: Exception) {
                Log.d(TAG, "Nested URL-decode failed", e)
            }

            // If a SwiftNote deep link appears anywhere (maybe embedded), extract passphrase
            try {
                val idx = content.indexOf("SwiftNote://sync?passphrase=", ignoreCase = true)
                if (idx >= 0) {
                    val substr = content.substring(idx)
                    val extracted = substr.substringAfter("passphrase=", "").substringBefore("&").trim()
                    if (extracted.isNotEmpty()) {
                        Log.d(TAG, "Extracted passphrase from embedded SwiftNote link: $extracted")
                        return extracted
                    }
                }
                // Also handle percent-encoded SwiftNote (SwiftNote%3A%2F%2F...)
                val pctIdx = content.indexOf("SwiftNote%3A", ignoreCase = true)
                if (pctIdx >= 0) {
                    val after = content.substring(pctIdx)
                    try {
                        val dec = java.net.URLDecoder.decode(after, "UTF-8")
                        val extracted = dec.substringAfter("passphrase=", "").substringBefore("&").trim()
                        if (extracted.isNotEmpty()) {
                            Log.d(TAG, "Extracted passphrase from percent-encoded SwiftNote link: $extracted")
                            return extracted
                        }
                    } catch (e: Exception) {
                        Log.d(TAG, "Percent-decode of embedded SwiftNote failed", e)
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "Embedded SwiftNote extraction failed", e)
            }

            // If content looks like JSON, try to parse passphrase/deviceId
            try {
                val trimmed = content.trim()
                if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
                    try {
                        val json = org.json.JSONObject(trimmed)
                        if (json.has("passphrase")) {
                            val p = json.optString("passphrase", "").trim()
                            if (p.isNotEmpty()) return p
                        }
                        if (json.has("deviceId")) {
                            val d = json.optString("deviceId", "").trim()
                            if (d.isNotEmpty()) return d
                        }
                        // Try common alternate keys
                        if (json.has("id")) {
                            val idv = json.optString("id", "").trim()
                            if (idv.isNotEmpty()) return idv
                        }
                    } catch (je: Exception) {
                        Log.d(TAG, "JSON parse failed for QR content", je)
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "JSON heuristics failed", e)
            }

            // If content is a full URI, parse query params for passphrase/deviceId
            try {
                val uri = java.net.URI(content)
                val query = uri.query
                if (query != null) {
                    val pairs = query.split("&")
                    for (pair in pairs) {
                        val parts = pair.split("=")
                        if (parts.size >= 2) {
                            val key = parts[0]
                            val value = parts.subList(1, parts.size).joinToString("=")
                            if (key.equals("passphrase", ignoreCase = true)) {
                                val candidate = value.trim()
                                Log.d(TAG, "Found passphrase param in URI: $candidate")
                                if (isValidPassphraseFormat(candidate)) return candidate
                                if (candidate.isNotEmpty()) return candidate
                            }
                            if (key.equals("deviceId", ignoreCase = true)) {
                                val candidate = value.trim()
                                Log.d(TAG, "Found deviceId param in URI: $candidate")
                                if (candidate.isNotEmpty()) return candidate
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "Content is not a parseable URI - proceeding with heuristics", e)
            }

            // Common deep-link forms
            if (content.contains("passphrase=", ignoreCase = true)) {
                val extracted = content.substringAfter("passphrase=", "").substringBefore("&").trim()
                if (extracted.isNotEmpty()) {
                    Log.d(TAG, "Extracted passphrase by marker: $extracted")
                    return extracted
                }
            }
            if (content.contains("deviceId=", ignoreCase = true)) {
                val extracted = content.substringAfter("deviceId=", "").substringBefore("&").trim()
                if (extracted.isNotEmpty()) {
                    Log.d(TAG, "Extracted deviceId by marker: $extracted")
                    return extracted
                }
            }

            // Direct passphrase (adjective-noun-number)
            if (isValidPassphraseFormat(content)) {
                Log.d(TAG, "QR content is a direct passphrase: $content")
                return content
            }

            // Accept UUID-ish device IDs (fallback)
            if (content.matches(Regex("[0-9a-fA-F-]{8,}"))) {
                Log.d(TAG, "QR content looks like a device id / UUID: $content")
                return content
            }

            // Additional heuristic: search anywhere for a passphrase-like token (adjective-noun-123)
            try {
                val passRegex = Regex("[A-Za-z]+-[A-Za-z]+-\\d{3}")
                val found = passRegex.find(content)
                if (found != null) {
                    val candidate = found.value.trim()
                    Log.d(TAG, "Found passphrase pattern inside content: $candidate")
                    return candidate
                }
            } catch (e: Exception) {
                Log.d(TAG, "Passphrase regex search failed", e)
            }

            // Also accept common 'passphrase:' markers (colon instead of equals)
            if (content.contains("passphrase:", ignoreCase = true)) {
                val extracted = content.substringAfter("passphrase:", "").substringBefore("&").trim()
                if (extracted.isNotEmpty()) {
                    Log.d(TAG, "Extracted passphrase by colon marker: $extracted")
                    return extracted
                }
            }

            // Conservative final fallback: if the content is a single token (no whitespace), reasonable length, accept it
            val singleToken = content.trim()
            if (!singleToken.contains(Regex("\\s")) && singleToken.length in 8..128) {
                Log.d(TAG, "Fallback accepting single-token QR content as passphrase/deviceId: ${singleToken.take(60)}")
                return singleToken
            }

            Log.w(TAG, "Unknown QR format after heuristics: $content")
             return null
         } catch (e: Exception) {
             Log.e(TAG, "Failed to extract passphrase from QR: $qrContent", e)
             return null
         }
     }
    fun isValidPassphraseFormat(passphrase: String): Boolean {
        val parts = passphrase.split("-")
        return parts.size == 3 &&
               parts[0].isNotEmpty() &&
               parts[1].isNotEmpty() &&
               parts[2].matches(Regex("\\d{3}"))
    }

    fun clearStoredPassphrase(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit {
                remove(KEY_PASSPHRASE)
            }
    }
}
