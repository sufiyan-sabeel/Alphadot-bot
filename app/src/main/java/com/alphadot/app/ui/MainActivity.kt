package com.alphadot.app.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.alphadot.app.R
import com.alphadot.app.data.ChatMessage
import com.alphadot.app.data.Conversation
import com.alphadot.app.data.ConversationStore
import com.alphadot.app.data.SettingsStore
import com.alphadot.app.provider.AiResult
import com.alphadot.app.provider.ProviderFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * AlphaDot's main chat screen. Launches directly into local/demo mode with no
 * account. A configurable provider can be enabled from Settings.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var store: ConversationStore
    private lateinit var settings: SettingsStore
    private lateinit var adapter: MessageAdapter
    private lateinit var recycler: RecyclerView
    private lateinit var input: EditText
    private lateinit var send: Button

    private val conversations = mutableListOf<Conversation>()
    private lateinit var current: Conversation

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setSupportActionBar(findViewById(R.id.toolbar))

        store = ConversationStore(this)
        settings = SettingsStore(this)

        recycler = findViewById(R.id.messages)
        input = findViewById(R.id.input)
        send = findViewById(R.id.send)

        adapter = MessageAdapter()
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        conversations.addAll(store.loadAll())
        current = conversations.lastOrNull() ?: store.newConversation().also { conversations.add(it) }

        adapter.submit(current.messages)
        scrollToBottom()

        send.setOnClickListener { onSend() }
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                onSend(); true
            } else false
        }

        if (!ProviderFactory.isConfigured(settings.load())) {
            // Friendly, non-blocking notice. Local demo still works.
            Toast.makeText(this, R.string.error_no_provider, Toast.LENGTH_LONG).show()
        }
    }

    override fun onResume() {
        super.onResume()
        // Settings may have changed; nothing to reload here besides provider.
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_new -> {
            newConversation(); true
        }
        R.id.action_history -> {
            showHistory(); true
        }
        R.id.action_settings -> {
            startActivity(Intent(this, SettingsActivity::class.java)); true
        }
        else -> super.onOptionsItemSelected(item)
    }

    private fun newConversation() {
        current = store.newConversation()
        conversations.add(current)
        store.saveAll(conversations)
        adapter.submit(current.messages)
    }

    private fun showHistory() {
        if (conversations.isEmpty()) {
            Toast.makeText(this, R.string.history, Toast.LENGTH_SHORT).show()
            return
        }
        val titles = conversations.map { it.title }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(R.string.history)
            .setItems(titles) { _, which ->
                current = conversations[which]
                adapter.submit(current.messages)
                scrollToBottom()
            }
            .show()
    }

    private fun onSend() {
        val text = input.text.toString().trim()
        if (text.isEmpty()) return
        input.setText("")

        current.messages.add(ChatMessage(ChatMessage.Role.USER, text))
        if (current.title == "New conversation") {
            current.title = text.take(40)
        }
        current.updatedAt = System.currentTimeMillis()
        adapter.submit(current.messages)
        scrollToBottom()
        persist()

        val working = ChatMessage(ChatMessage.Role.ASSISTANT, "…")
        current.messages.add(working)
        adapter.submit(current.messages)
        scrollToBottom()

        val config = settings.load()
        val provider = ProviderFactory.create(config)
        val snapshot = current.messages.filter { it !== working }.toList()

        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    provider.complete(snapshot)
                } catch (e: Exception) {
                    AiResult.Error(e.message ?: "Unexpected error")
                }
            }
            // Replace the placeholder with the real result.
            val idx = current.messages.indexOf(working)
            if (idx >= 0) current.messages.removeAt(idx)
            when (result) {
                is AiResult.Success ->
                    current.messages.add(ChatMessage(ChatMessage.Role.ASSISTANT, result.text))
                is AiResult.Error ->
                    current.messages.add(
                        ChatMessage(ChatMessage.Role.ASSISTANT, "\u26A0 " + result.message)
                    )
            }
            current.updatedAt = System.currentTimeMillis()
            adapter.submit(current.messages)
            scrollToBottom()
            persist()
        }
    }

    private fun persist() {
        store.saveAll(conversations)
    }

    private fun scrollToBottom() {
        recycler.post {
            if (adapter.itemCount > 0) recycler.scrollToPosition(adapter.itemCount - 1)
        }
    }
}
