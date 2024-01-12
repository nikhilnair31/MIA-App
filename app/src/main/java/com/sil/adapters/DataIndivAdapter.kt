package com.sil.adapters

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.sil.mia.R
import org.json.JSONArray

class DataIndivAdapter(private var dataList: JSONArray) :
    RecyclerView.Adapter<DataIndivAdapter.MessageViewHolder>() {

    class MessageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val addressTextView: TextView = view.findViewById(R.id.addressTextView)
        val weatherTextView: TextView = view.findViewById(R.id.weatherTextView)
        val textTextView: TextView = view.findViewById(R.id.textTextView)
    }

    fun updateData(newData: JSONArray) {
        dataList = newData
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        // Log.i("DataDumpAdapter", "onCreateViewHolder")

        val view = LayoutInflater.from(parent.context).inflate(R.layout.dataindiv_item, parent, false)
        return MessageViewHolder(view)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        Log.i("DataIndivAdapter", "onBindViewHolder")

        val context = holder.textTextView.context

        val message = dataList.getJSONObject(position)
        // Log.i("DataDumpAdapter", "message: $message")
        if (message != null) {
            holder.addressTextView.text = message.getString("address")
            holder.weatherTextView.text = message.getString("firstweatherdescription")
            holder.textTextView.text = message.getString("text")
        }
        else {
            holder.addressTextView.visibility = View.GONE
            holder.weatherTextView.visibility = View.GONE
            holder.textTextView.visibility = View.GONE
        }
    }

    override fun getItemCount() = dataList.length()
}
