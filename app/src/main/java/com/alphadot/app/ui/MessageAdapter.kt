package com.alphadot.app.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.alphadot.app.R
import com.alphadot.app.data.ChatMessage

/** Renders chat bubbles, right-aligned for the user and left-aligned for AlphaDot. */
class MessageAdapter : RecyclerView.Adapter<MessageAdapter.VH>() {

    private val items = mutableListOf<ChatMessage>()

    fun submit(list: List<ChatMessage>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_message, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position])

    override fun getItemCount(): Int = items.size

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        private val row: LinearLayout = view.findViewById(R.id.row)
        private val bubble: TextView = view.findViewById(R.id.bubble)

        fun bind(message: ChatMessage) {
            bubble.text = message.content
            val isUser = message.role == ChatMessage.Role.USER
            row.gravity = if (isUser) android.view.Gravity.END else android.view.Gravity.START
            bubble.setBackgroundResource(
                if (isUser) R.drawable.bubble_user else R.drawable.bubble_assistant
            )
        }
    }
}
