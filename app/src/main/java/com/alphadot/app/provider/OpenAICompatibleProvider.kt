package com.alphadot.app.provider

import com.alphadot.app.data.ChatMessage
import com.alphadot.app.data.ProviderConfig
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Provider for any OpenAI-compatible chat completions endpoint
 * (OpenAI, Groq, OpenRouter, Together, a self-hosted server, ...).
 * Base URL, model and key are all user supplied at runtime.
 */
class OpenAICompatibleProvider(private val config: ProviderConfig) : AiProvider {

    override val name: String = "OpenAI-compatible"

    override fun complete(messages: List<ChatMessage>): AiResult {
        if (config.baseUrl.isBlank()) {
            return AiResult.Error("No base URL configured. Add one in Settings.")
        }
        if (config.apiKey.isBlank()) {
            return AiResult.Error("No API key configured. Add one in Settings.")
        }

        return try {
            val endpoint = config.baseUrl.trimEnd('/') + "/chat/completions"
            val url = URL(endpoint)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 30_000
                readTimeout = 60_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Authorization", "Bearer ${config.apiKey}")
            }

            val msgs = JSONArray()
            if (config.systemPrompt.isNotBlank()) {
                msgs.put(JSONObject().put("role", "system").put("content", config.systemPrompt))
            }
            messages.forEach { m ->
                val role = when (m.role) {
                    ChatMessage.Role.USER -> "user"
                    ChatMessage.Role.ASSISTANT -> "assistant"
                    ChatMessage.Role.SYSTEM -> "system"
                }
                msgs.put(JSONObject().put("role", role).put("content", m.content))
            }

            val body = JSONObject()
                .put("model", config.model.ifBlank { DEFAULT_MODEL })
                .put("messages", msgs)

            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(body.toString()) }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val raw = stream?.bufferedReader()?.use(BufferedReader::readText).orEmpty()

            if (code !in 200..299) {
                return AiResult.Error(httpError("Provider", code, raw))
            }

            val text = JSONObject(raw)
                .optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optJSONObject("message")
                ?.optString("content")

            if (text.isNullOrBlank()) {
                AiResult.Error("Provider returned an empty response.")
            } else {
                AiResult.Success(text)
            }
        } catch (e: Exception) {
            AiResult.Error("Provider request failed: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    companion object {
        const val DEFAULT_MODEL = "gpt-4o-mini"
    }
}
