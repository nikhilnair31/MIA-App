package com.sil.adapters

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.RecyclerView
import com.sil.mia.IndivData
import com.sil.mia.R
import com.sil.others.Helpers
import org.json.JSONArray

class DataDumpAdapter(private var dataDumpList: JSONArray, private val context: Context) :
    RecyclerView.Adapter<DataDumpAdapter.MessageViewHolder>() {

    class MessageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val vectorIdAndFilenameTextView: TextView = view.findViewById(R.id.vectorIdAndFilenameTextView)
        val textTextView: TextView = view.findViewById(R.id.textTextView)
        val deleteButton: ImageButton = view.findViewById(R.id.deleteButton)
        val downloadButton: ImageButton = view.findViewById(R.id.downloadButton)
        val dataIndivConstraintLayout: ConstraintLayout = view.findViewById(R.id.indivData)
    }

    fun updateData(newData: JSONArray) {
        dataDumpList = newData
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        // Log.i("DataDumpAdapter", "onCreateViewHolder")

        val view = LayoutInflater.from(parent.context).inflate(R.layout.datadump_item, parent, false)
        return MessageViewHolder(view)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        // Log.i("DataDumpAdapter", "onBindViewHolder")

        val context = holder.textTextView.context

        val data = dataDumpList.getJSONObject(position)
        // Log.i("DataDumpAdapter", "data: $message")
        if (data != null) {
            holder.vectorIdAndFilenameTextView.text = "${data.getString("currenttimeformattedstring")}\n${data.getString("filename")}"
            holder.textTextView.text = data.getString("text")

            holder.vectorIdAndFilenameTextView.setTextColor(context.resources.getColor(R.color.accent_0, null))
        }
        else {
            holder.vectorIdAndFilenameTextView.visibility = View.GONE
            holder.textTextView.visibility = View.GONE
        }

        holder.deleteButton.setOnClickListener {
            handleDeleteButtonClick(position)
        }
        holder.downloadButton.setOnClickListener {
            handleDownloadButtonClick(position)
        }
        holder.dataIndivConstraintLayout.setOnClickListener {
            val intent = Intent(context, IndivData::class.java)
            intent.putExtra("selectedData", data.toString())
            context.startActivity(intent)
        }
    }

    private fun handleDeleteButtonClick(adapterPosition: Int) {
        Log.i("DataDumpAdapter", "handleDeleteButtonClick")

        val dataItem = dataDumpList.getJSONObject(adapterPosition)
        val vectorId: String? = dataItem.optString("vector_id")
        val fileName: String? = dataItem.optString("filename")
        Log.i("DataDumpAdapter", "$adapterPosition\n$dataItem\n$vectorId\n$fileName")

        // Implement your logic to handle the delete action
        if (vectorId != null) {
            Helpers.deleteVectorById(vectorId) {
            }
        }
        if (fileName != null) {
            Helpers.deleteS3ObjectByFilename(context, fileName) {
            }
        }
        if (context is Activity) {
            context.runOnUiThread {
                dataDumpList.remove(adapterPosition)
                notifyItemRemoved(adapterPosition)
            }
        }
    }
    private fun handleDownloadButtonClick(adapterPosition: Int) {
        Log.i("DataDumpAdapter", "handleDownloadButtonClick")

        //TODO: Add code to download S3 object
        val dataItem = dataDumpList.getJSONObject(adapterPosition)
        val fileName: String? = dataItem.optString("filename")
        Log.i("DataDumpAdapter", "$adapterPosition\n$dataItem\n$$fileName")

        if (fileName != null) {
            Helpers.downloadFromS3(context, fileName)
        }
    }

    override fun getItemCount() = dataDumpList.length()
}
