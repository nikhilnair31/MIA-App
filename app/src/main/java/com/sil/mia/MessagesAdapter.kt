package com.sil.mia

import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import org.json.JSONObject

class MessagesAdapter(private val messagesList: List<JSONObject>) :
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
        holder.textView.text = message["content"] as CharSequence?

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

    override fun getItemCount() = messagesList.size
}
