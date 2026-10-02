package com.alphadot.app.provider

import com.alphadot.app.data.ChatMessage
import java.util.Locale

/**
 * Fully offline provider used by default so AlphaDot works with no account and
 * no network. It produces a small, deterministic local reply. It performs no
 * network I/O and needs no credentials.
 */
class LocalProvider : AiProvider {

    override val name: String = "Local (offline demo)"

    override fun complete(messages: List<ChatMessage>): AiResult {
        val lastUser = messages.lastOrNull { it.role == ChatMessage.Role.USER }?.content
            ?: return AiResult.Success(
                "Hi! I'm AlphaDot running in local demo mode. " +
                    "Configure an AI provider in Settings to chat with a real model."
            )

        val text = buildString {
            append("AlphaDot local demo reply.\n\n")
            append("You said: \"").append(lastUser.trim().take(400)).append("\"\n\n")
            append("This reply is generated entirely on-device. ")
            append("To connect a real AI model, open Settings and choose ")
            append("Gemini or an OpenAI-compatible endpoint. ")
            append("No account is required for local mode.")
        }
        return AiResult.Success(text)
    }
}
