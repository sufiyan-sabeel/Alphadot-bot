package com.alphadot.app.provider

import com.alphadot.app.data.ProviderConfig
import com.alphadot.app.data.SettingsStore

/**
 * Central place that turns user [ProviderConfig] into a concrete [AiProvider].
 * Keeping this in one factory is what makes the backend "replaceable".
 */
object ProviderFactory {

    fun create(config: ProviderConfig): AiProvider = when (config.type) {
        SettingsStore.TYPE_GEMINI -> GeminiProvider(config)
        SettingsStore.TYPE_OPENAI -> OpenAICompatibleProvider(config)
        else -> LocalProvider()
    }

    /** True when the selected provider needs credentials that are still missing. */
    fun isConfigured(config: ProviderConfig): Boolean = when (config.type) {
        SettingsStore.TYPE_GEMINI -> config.apiKey.isNotBlank()
        SettingsStore.TYPE_OPENAI -> config.apiKey.isNotBlank() && config.baseUrl.isNotBlank()
        else -> true
    }
}
