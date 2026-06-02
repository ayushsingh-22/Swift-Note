package com.amvarpvtltd.swiftNote.ai

/**
 * Supported LLM providers.
 * Each provider has a display name, available models, and a validation endpoint.
 */
enum class LlmProvider(val displayName: String, val keyHint: String, val keyGetUrl: String) {
    GEMINI(
        displayName = "Gemini",
        keyHint = "AIzaSy...",
        keyGetUrl = "https://aistudio.google.com/apikey"
    ),
    GROQ(
        displayName = "Groq",
        keyHint = "gsk_...",
        keyGetUrl = "https://console.groq.com/keys"
    );

    companion object {
        fun fromName(name: String): LlmProvider =
            entries.find { it.name.equals(name, ignoreCase = true) } ?: GEMINI
    }
}

/**
 * Models available per provider (free tier).
 */
object LlmModels {

    val GEMINI_MODELS = listOf(
        "gemini-2.5-flash",
        "gemini-2.5-flash-lite",
        "gemini-2.0-flash",
        "gemini-2.0-flash-lite",
        "gemini-2.5-pro",
        "gemini-1.5-flash",
        "gemini-1.5-flash-8b",
        "gemini-1.5-pro",
    )

    val GROQ_MODELS = listOf(
        "llama-3.3-70b-versatile",
        "llama-3.1-8b-instant",
        "gemma2-9b-it",
        "mixtral-8x7b-32768",
    )

    fun modelsFor(provider: LlmProvider): List<String> = when (provider) {
        LlmProvider.GEMINI -> GEMINI_MODELS
        LlmProvider.GROQ -> GROQ_MODELS
    }
}

