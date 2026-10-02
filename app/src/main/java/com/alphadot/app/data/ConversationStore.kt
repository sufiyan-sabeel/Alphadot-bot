package com.alphadot.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Very small local conversation store backed by SharedPreferences.
 * No account, no network, no cloud. All history stays on device.
 */
class ConversationStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun loadAll(): MutableList<Conversation> {
        val raw = prefs.getString(KEY_CONVERSATIONS, null) ?: return mutableListOf()
        return try {
            val arr = JSONArray(raw)
            val out = mutableListOf<Conversation>()
            for (i in 0 until arr.length()) out.add(fromJson(arr.getJSONObject(i)))
            out
        } catch (_: Exception) {
            mutableListOf()
        }
    }

    fun saveAll(conversations: List<Conversation>) {
        val arr = JSONArray()
        conversations.forEach { arr.put(toJson(it)) }
        prefs.edit().putString(KEY_CONVERSATIONS, arr.toString()).apply()
    }

    fun newConversation(): Conversation =
        Conversation(id = UUID.randomUUID().toString(), title = "New conversation")

    private fun toJson(c: Conversation): JSONObject {
        val msgs = JSONArray()
        c.messages.forEach { m ->
            msgs.put(
                JSONObject()
                    .put("role", m.role.name)
                    .put("content", m.content)
                    .put("ts", m.timestamp)
            )
        }
        return JSONObject()
            .put("id", c.id)
            .put("title", c.title)
            .put("created", c.createdAt)
            .put("updated", c.updatedAt)
            .put("messages", msgs)
    }

    private fun fromJson(o: JSONObject): Conversation {
        val msgs = mutableListOf<ChatMessage>()
        val arr = o.optJSONArray("messages") ?: JSONArray()
        for (i in 0 until arr.length()) {
            val m = arr.getJSONObject(i)
            val role = try {
                ChatMessage.Role.valueOf(m.optString("role", "USER"))
            } catch (_: Exception) {
                ChatMessage.Role.USER
            }
            msgs.add(ChatMessage(role, m.optString("content"), m.optLong("ts")))
        }
        return Conversation(
            id = o.optString("id"),
            title = o.optString("title", "Conversation"),
            messages = msgs,
            createdAt = o.optLong("created"),
            updatedAt = o.optLong("updated")
        )
    }

    companion object {
        private const val PREFS = "alphadot_conversations"
        private const val KEY_CONVERSATIONS = "conversations"
    }
}
