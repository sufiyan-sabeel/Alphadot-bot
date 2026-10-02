package com.alphadot.app.data

import android.content.Context

/**
 * User-configurable provider settings. Nothing is hard-coded and no secret is
 * ever bundled in the APK: the user supplies their own credentials at runtime.
 */
data class ProviderConfig(
    val type: String,
    val apiKey: String,
    val baseUrl: String,
    val model: String,
    val systemPrompt: String
)

class SettingsStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(): ProviderConfig = ProviderConfig(
        type = prefs.getString(KEY_TYPE, TYPE_LOCAL) ?: TYPE_LOCAL,
        apiKey = prefs.getString(KEY_API_KEY, "") ?: "",
        baseUrl = prefs.getString(KEY_BASE_URL, "") ?: "",
        model = prefs.getString(KEY_MODEL, "") ?: "",
        systemPrompt = prefs.getString(KEY_SYSTEM_PROMPT, DEFAULT_SYSTEM_PROMPT)
            ?: DEFAULT_SYSTEM_PROMPT
    )

    fun save(config: ProviderConfig) {
        prefs.edit()
            .putString(KEY_TYPE, config.type)
            .putString(KEY_API_KEY, config.apiKey)
            .putString(KEY_BASE_URL, config.baseUrl)
            .putString(KEY_MODEL, config.model)
            .putString(KEY_SYSTEM_PROMPT, config.systemPrompt)
            .apply()
    }

    companion object {
        private const val PREFS = "alphadot_settings"
        private const val KEY_TYPE = "provider_type"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_BASE_URL = "base_url"
        private const val KEY_MODEL = "model"
        private const val KEY_SYSTEM_PROMPT = "system_prompt"

        const val TYPE_LOCAL = "local"
        const val TYPE_GEMINI = "gemini"
        const val TYPE_OPENAI = "openai_compatible"

        const val DEFAULT_SYSTEM_PROMPT =
            "You are AlphaDot, a helpful, concise AI assistant."
    }
}
