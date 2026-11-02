package com.amvarpvtltd.swiftNote.ai

import android.content.Context
import androidx.core.content.edit

/**
 * How [LlmService] picks a provider for each call.
 * AUTOMATIC lets LlmService choose (and fall back) across configured providers;
 * GEMINI/GROQ pin every call to that provider, with no cross-provider fallback.
 */
enum class AiMode { AUTOMATIC, GEMINI, GROQ }

/**
 * Non-sensitive AI routing preferences — which provider Automatic mode prefers and
 * whether it's allowed to fall back to the other configured provider. Never stores
 * API keys or any credential; see [com.amvarpvtltd.swiftNote.security.GeminiKeyManager]
 * for that.
 */
object AiModePreferences {
    private const val PREFS_NAME = "ai_mode_prefs"
    private const val KEY_MODE = "ai_mode"
    private const val KEY_PREFERRED_PROVIDER = "preferred_provider"
    private const val KEY_FALLBACK_ENABLED = "fallback_enabled"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getMode(context: Context): AiMode {
        val name = prefs(context).getString(KEY_MODE, null) ?: return AiMode.AUTOMATIC
        return AiMode.entries.find { it.name == name } ?: AiMode.AUTOMATIC
    }

    fun setMode(context: Context, mode: AiMode) {
        prefs(context).edit { putString(KEY_MODE, mode.name) }
    }

    fun getPreferredProvider(context: Context): LlmProvider? {
        val name = prefs(context).getString(KEY_PREFERRED_PROVIDER, null) ?: return null
        return LlmProvider.entries.find { it.name == name }
    }

    fun setPreferredProvider(context: Context, provider: LlmProvider?) {
        prefs(context).edit { putString(KEY_PREFERRED_PROVIDER, provider?.name) }
    }

    /** Whether Automatic mode may try the other configured provider on a retryable failure. */
    fun isFallbackEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_FALLBACK_ENABLED, true)

    fun setFallbackEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit { putBoolean(KEY_FALLBACK_ENABLED, enabled) }
    }
}
