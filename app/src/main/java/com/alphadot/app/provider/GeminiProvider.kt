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
 * Google Gemini provider. The API key is supplied by the user at runtime and is
 * never bundled with AlphaDot.
 *
 * Endpoint: https://generativelanguage.googleapis.com
 */
class GeminiProvider(private val config: ProviderConfig) : AiProvider {

    override val name: String = "Gemini"

    override fun complete(messages: List<ChatMessage>): AiResult {
        if (config.apiKey.isBlank()) {
            return AiResult.Error("No Gemini API key configured. Add one in Settings.")
        }
        val model = config.model.ifBlank { DEFAULT_MODEL }
        val base = config.baseUrl.ifBlank { DEFAULT_BASE }

        return try {
            val url = URL("$base/v1beta/models/$model:generateContent?key=${config.apiKey}")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 30_000
                readTimeout = 60_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }

            val body = JSONObject().apply {
                val contents = JSONArray()
                messages.filter { it.role != ChatMessage.Role.SYSTEM }.forEach { m ->
                    contents.put(
                        JSONObject().apply {
                            put("role", if (m.role == ChatMessage.Role.USER) "user" else "model")
                            put(
                                "parts",
                                JSONArray().put(JSONObject().put("text", m.content))
                            )
                        }
                    )
                }
                put("contents", contents)
                if (config.systemPrompt.isNotBlank()) {
                    put(
                        "systemInstruction",
                        JSONObject().put(
                            "parts",
                            JSONArray().put(JSONObject().put("text", config.systemPrompt))
                        )
                    )
                }
            }

            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(body.toString()) }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val raw = stream?.bufferedReader()?.use(BufferedReader::readText).orEmpty()

            if (code !in 200..299) {
                return AiResult.Error(httpError("Gemini", code, raw))
            }

            val text = JSONObject(raw)
                .optJSONArray("candidates")
                ?.optJSONObject(0)
                ?.optJSONObject("content")
                ?.optJSONArray("parts")
                ?.optJSONObject(0)
                ?.optString("text")

            if (text.isNullOrBlank()) {
                AiResult.Error("Gemini returned an empty response.")
            } else {
                AiResult.Success(text)
            }
        } catch (e: Exception) {
            AiResult.Error("Gemini request failed: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    companion object {
        const val DEFAULT_BASE = "https://generativelanguage.googleapis.com"
        const val DEFAULT_MODEL = "gemini-1.5-flash"
    }
}
