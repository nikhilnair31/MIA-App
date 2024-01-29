package com.sil.adapters

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.RecyclerView
import com.sil.mia.DataIndividual
import com.sil.mia.R
import com.sil.others.Helpers
import org.json.JSONArray
import java.io.File


class DataDumpAdapter(private var dataDumpList: JSONArray, private val context: Context) : RecyclerView.Adapter<DataDumpAdapter.MessageViewHolder>() {

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
        if (data != null) {
            val timeString = data.optString("currenttimeformattedstring", "") ?: ""
            val fileNameString = data.optString("filename", "") ?: ""

            holder.vectorIdAndFilenameTextView.text = "$timeString\n$fileNameString"
            holder.textTextView.text = data.getString("text")

            holder.vectorIdAndFilenameTextView.setTextColor(context.resources.getColor(R.color.accent_0, null))

            // Check if the data contains the key "keepFile"
            val allowDownload = !data.has("saveaudiofiles") || data.optString("saveaudiofiles", "").lowercase() == "true"

            holder.downloadButton.visibility = if (allowDownload) View.VISIBLE else View.GONE

            if (allowDownload) {
                holder.downloadButton.setOnClickListener {
                    handleDownloadButtonClick(position, holder.downloadButton)
                }
            }
        }
        else {
            holder.vectorIdAndFilenameTextView.visibility = View.GONE
            holder.textTextView.visibility = View.GONE
        }

        holder.deleteButton.setOnClickListener {
            showDeleteConfirmationDialog(position)
        }
        holder.downloadButton.setOnClickListener {
            handleDownloadButtonClick(position, holder.downloadButton)
        }
        holder.dataIndivConstraintLayout.setOnClickListener {
            val intent = Intent(context, DataIndividual::class.java)
            intent.putExtra("selectedData", data.toString())
            context.startActivity(intent)
        }
    }

    private fun handleDeleteButtonClick(adapterPosition: Int) {
        Log.i("DataDumpAdapter", "handleDeleteButtonClick")

        val dataItem = dataDumpList.getJSONObject(adapterPosition)
        val vectorId: String? = dataItem.optString("id")
        Log.i("DataDumpAdapter", "$adapterPosition\n$dataItem\n$vectorId")

        if (vectorId != null) {
            Helpers.callPineconeDeleteByIdAPI(
                context,
                vectorId
            )

            if (context is Activity) {
                context.runOnUiThread {
                    dataDumpList.remove(adapterPosition)
                    notifyItemRemoved(adapterPosition)

                    // Update indices of the remaining items
                    for (i in adapterPosition until dataDumpList.length()) {
                        notifyItemChanged(i)
                    }
                }
            }
        }
    }
    private fun handleDownloadButtonClick(adapterPosition: Int, downloadButton: ImageButton) {
        Log.i("DataDumpAdapter", "handleDownloadButtonClick")

        val dataItem = dataDumpList.getJSONObject(adapterPosition)
        val fileName: String? = dataItem.optString("filename")
        Log.i("DataDumpAdapter", "$adapterPosition\n$dataItem\n$$fileName")

        if (fileName != null) {
            val downloadsDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val folderName = context.getString(R.string.appName)
            val folder = File(downloadsDirectory, folderName)
            if (!folder.exists()) folder.mkdirs()
            val destinationFile = File(folder, fileName)

            // TODO: Check first if the file exists only then show the download button
            // TODO: Manage its enabled/disabled state on leaving the activity
            downloadButton.isEnabled = false
            Helpers.downloadFromS3(context, fileName, destinationFile) { success ->
                Handler(Looper.getMainLooper()).post {
                    downloadButton.isEnabled = true
                    if (success) {
                        Helpers.showToast(context, "S3 download complete!")
                    } else {
                        Helpers.showToast(context, "S3 download failed :(")
                    }
                }
            }
        }
    }

    private fun showDeleteConfirmationDialog(adapterPosition: Int) {
        val builder = AlertDialog.Builder(context)
        builder.setTitle("Confirm Delete")
        builder.setMessage("Are you sure you want to delete this item?")

        builder.setPositiveButton("Yes") { dialog, which ->
            // Handle delete when the user clicks "Yes"
            handleDeleteButtonClick(adapterPosition)
        }

        builder.setNegativeButton("No") { dialog, which ->
            // Do nothing when the user clicks "No"
            dialog.dismiss()
        }

        val dialog = builder.create()
        dialog.show()
    }

    override fun getItemCount() = dataDumpList.length()
}
