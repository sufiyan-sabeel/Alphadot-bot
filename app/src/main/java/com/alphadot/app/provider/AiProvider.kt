package com.alphadot.app.provider

import com.alphadot.app.data.ChatMessage

/**
 * Result of a single completion request.
 */
sealed class AiResult {
    data class Success(val text: String) : AiResult()

    /**
     * A user-actionable failure. [message] is shown to the user, so it must
     * never contain secrets.
     */
    data class Error(val message: String) : AiResult()
}

/**
 * A replaceable AI backend.
 *
 * AlphaDot ships no credentials. Every implementation must obtain any required
 * key from [com.alphadot.app.data.SettingsStore] at call time. Implementations
 * must not read keys from the APK itself.
 */
interface AiProvider {
    /** Human-readable provider name shown in Settings. */
    val name: String

    /**
     * Run one completion. Implementations may block; callers are expected to
     * invoke this off the main thread.
     */
    fun complete(messages: List<ChatMessage>): AiResult
}

/**
 * Extract a compact, non-secret error message from an HTTP error body.
 * Never returns headers, tokens or full bodies.
 */
internal fun httpError(provider: String, code: Int, raw: String): String {
    val detail = try {
        val o = org.json.JSONObject(raw)
        o.optJSONObject("error")?.optString("message") ?: o.optString("message")
    } catch (_: Exception) {
        null
    }
    return buildString {
        append(provider).append(" error (HTTP ").append(code).append(")")
        if (!detail.isNullOrBlank()) append(": ").append(detail.take(300))
    }
}
