package com.amvarpvtltd.swiftNote.ai

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Groq REST API client using their OpenAI-compatible endpoint.
 * No external HTTP library dependency — uses java.net.HttpURLConnection.
 *
 * Groq free tier: 30 req/min, 14,400 req/day.
 * Models: llama-3.3-70b-versatile, llama-3.1-8b-instant, gemma2-9b-it, mixtral-8x7b-32768
 */
object GroqClient {

    private const val TAG = "GroqClient"
    private const val BASE_URL = "https://api.groq.com/openai/v1/chat/completions"
    private const val TIMEOUT_MS = 12_000

    /**
     * Generate text from Groq with a given model, system prompt, and user message.
     * Returns the assistant's content text or throws an exception.
     */
    suspend fun generateContent(
        apiKey: String,
        model: String,
        systemPrompt: String?,
        userMessage: String,
        temperature: Float = 0.3f,
        maxTokens: Int = 256
    ): String = withContext(Dispatchers.IO) {
        val url = URL(BASE_URL)
        val connection = url.openConnection() as HttpURLConnection

        try {
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Authorization", "Bearer $apiKey")
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.doOutput = true

            // Build messages array
            val messages = JSONArray()
            if (!systemPrompt.isNullOrBlank()) {
                messages.put(JSONObject().apply {
                    put("role", "system")
                    put("content", systemPrompt)
                })
            }
            messages.put(JSONObject().apply {
                put("role", "user")
                put("content", userMessage)
            })

            val requestBody = JSONObject().apply {
                put("model", model)
                put("messages", messages)
                put("temperature", temperature.toDouble())
                put("max_tokens", maxTokens)
            }

            OutputStreamWriter(connection.outputStream).use { writer ->
                writer.write(requestBody.toString())
                writer.flush()
            }

            val responseCode = connection.responseCode
            val responseBody = if (responseCode in 200..299) {
                BufferedReader(InputStreamReader(connection.inputStream)).use { it.readText() }
            } else {
                val errorBody = BufferedReader(InputStreamReader(connection.errorStream)).use { it.readText() }
                throw GroqApiException(responseCode, errorBody)
            }

            // Parse response
            val json = JSONObject(responseBody)
            val choices = json.getJSONArray("choices")
            if (choices.length() == 0) throw GroqApiException(200, "No choices in response")

            val content = choices.getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
                .trim()

            if (content.isBlank()) throw GroqApiException(200, "Empty content in response")
            content

        } finally {
            connection.disconnect()
        }
    }

    /**
     * Validate a Groq API key by making a minimal test call.
     * Returns a [ValidationResult] indicating success, quota error, auth error, etc.
     */
    suspend fun validateKey(apiKey: String): ValidationResult = withContext(Dispatchers.IO) {
        for (model in LlmModels.GROQ_MODELS) {
            try {
                val response = generateContent(
                    apiKey = apiKey,
                    model = model,
                    systemPrompt = null,
                    userMessage = "Reply with just: OK",
                    temperature = 0.1f,
                    maxTokens = 10
                )
                if (response.isNotBlank()) {
                    Log.i(TAG, "✅ Groq key valid — $model responded")
                    return@withContext ValidationResult.Success(model)
                }
            } catch (e: GroqApiException) {
                Log.e(TAG, "❌ $model validation: HTTP ${e.statusCode}")
                when {
                    e.statusCode == 429 || e.errorBody.contains("rate_limit", ignoreCase = true) -> {
                        Log.i(TAG, "✅ Key valid (rate-limited on $model)")
                        return@withContext ValidationResult.QuotaButValid(model)
                    }
                    e.statusCode == 401 || e.statusCode == 403 ||
                    e.errorBody.contains("invalid_api_key", ignoreCase = true) ||
                    e.errorBody.contains("authentication", ignoreCase = true) -> {
                        Log.e(TAG, "🔐 Key rejected: ${e.errorBody.take(200)}")
                        return@withContext ValidationResult.AuthError(e.errorBody.take(200))
                    }
                    e.statusCode == 404 || e.errorBody.contains("model_not_found", ignoreCase = true) -> {
                        Log.w(TAG, "⚠️ $model not found, trying next…")
                        continue
                    }
                    else -> {
                        Log.w(TAG, "⚠️ $model: ${e.errorBody.take(100)} → trying next")
                        continue
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ $model: ${e.message}")
                continue
            }
        }
        ValidationResult.AllModelsFailed
    }

    sealed class ValidationResult {
        data class Success(val model: String) : ValidationResult()
        data class QuotaButValid(val model: String) : ValidationResult()
        data class AuthError(val message: String) : ValidationResult()
        object AllModelsFailed : ValidationResult()
    }
}

/**
 * Exception representing an error response from the Groq API.
 */
class GroqApiException(val statusCode: Int, val errorBody: String) : Exception(
    "Groq API error (HTTP $statusCode): ${errorBody.take(200)}"
) {
    /** Extract a user-friendly message from the error body. */
    fun userMessage(): String {
        return try {
            val json = JSONObject(errorBody)
            json.optJSONObject("error")?.optString("message") ?: errorBody.take(150)
        } catch (_: Exception) {
            errorBody.take(150)
        }
    }
}

