package com.sil.mia

import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class MessagesAdapter(private val messagesList: List<MainActivity.Message>) :
    RecyclerView.Adapter<MessagesAdapter.MessageViewHolder>() {

    class MessageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        // Bind views here
        val textView: TextView = view.findViewById(R.id.messageTextView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.message_item, parent, false)
        return MessageViewHolder(view)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val message = messagesList[position]
        holder.textView.text = message.content

        // Set the text color based on whether the message is from the user
        holder.textView.setTextColor(
            if (message.isUser) {
                holder.textView.context.resources.getColor(R.color.gray_600, null)
            } else {
                holder.textView.context.resources.getColor(R.color.beige, null)
            }
        )

        val layoutParams = holder.textView.layoutParams as LinearLayout.LayoutParams
        layoutParams.gravity = if (message.isUser) Gravity.START else Gravity.END
        holder.textView.layoutParams = layoutParams
    }

    override fun getItemCount() = messagesList.size
}
