package com.sil.mia

import android.graphics.drawable.GradientDrawable
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import org.json.JSONArray
import org.json.JSONObject

class MessagesAdapter(private var messagesList: JSONArray) :
    RecyclerView.Adapter<MessagesAdapter.MessageViewHolder>() {

    class MessageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        // Bind views here
        val textView: TextView = view.findViewById(R.id.messageTextView)
    }

    fun updateMessages(newMessages: JSONArray) {
        messagesList = newMessages
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        Log.i("MessagesAdapter", "onCreateViewHolder")

        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.message_item, parent, false)
        return MessageViewHolder(view)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        Log.i("MessagesAdapter", "onBindViewHolder")

        val message = messagesList[position] as JSONObject
        Log.i("MessagesAdapter", "onBindViewHolder message: $message")
        holder.textView.text = message.getString("content")

        // Adjust gravity and text alignment
        val context = holder.textView.context
        val layoutParams = holder.textView.layoutParams as LinearLayout.LayoutParams
        val background = holder.textView.background as? GradientDrawable

        if (message["role"] == "user") {
            holder.textView.setTextColor(context.resources.getColor(R.color.gray_70, null))

            background?.setColor(ContextCompat.getColor(context, R.color.gray_8))

            layoutParams.gravity = Gravity.END
            holder.textView.textAlignment = View.TEXT_ALIGNMENT_TEXT_END
        }
        else {
            holder.textView.setTextColor(context.resources.getColor(R.color.accent_0, null))

            background?.setColor(ContextCompat.getColor(context, R.color.gray_5))

            layoutParams.gravity = Gravity.START
            holder.textView.textAlignment = View.TEXT_ALIGNMENT_TEXT_START
        }
        holder.textView.layoutParams = layoutParams

        // Max and Min width for messages
        holder.textView.minWidth = 300
        holder.textView.maxWidth = 800
    }

    override fun getItemCount() = messagesList.length()
}
