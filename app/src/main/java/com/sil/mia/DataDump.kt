package com.sil.mia

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.sil.others.Helpers
import com.sil.adapters.DataDumpAdapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Locale

class DataDump : AppCompatActivity() {
    // region Vars
    private lateinit var recyclerView: RecyclerView
    private lateinit var loadingTextView: TextView
    private lateinit var backButton: ImageButton
    private lateinit var buttonRefresh: ImageButton

    private lateinit var dataSharedPref: SharedPreferences
    private lateinit var adapter: DataDumpAdapter
    private var dataDumpList = JSONArray()
    // endregion

    // region Common
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_datadump)

        dataRelated()
        buttonSetup()
    }
    // endregion

    // region Data Related
    private fun dataRelated() {
        Log.i("DataDump", "DataDump dataRelated")

        loadingTextView = findViewById(R.id.loadingTextView)
        buttonRefresh = findViewById(R.id.buttonRefresh)
        recyclerView = findViewById(R.id.recyclerView)

        dataSharedPref = getSharedPreferences("com.sil.mia.data", Context.MODE_PRIVATE)
        val dataDumpString = dataSharedPref.getString("dataDump", "")
        dataDumpList = if (!dataDumpString.isNullOrBlank()) JSONArray(dataDumpString) else JSONArray()
        Log.i("DataDump", "dataDumpList.length: ${dataDumpList.length()}")

        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = DataDumpAdapter(dataDumpList, this)
        recyclerView.adapter = adapter

        if (dataDumpList.length() == 0) fetchVectorsMetadata() else dataPopulated()

        buttonRefresh.setOnClickListener {
            dataLoading()
            fetchVectorsMetadata()
        }
    }
    private fun fetchVectorsMetadata() {
        CoroutineScope(Dispatchers.Main).launch {
            buttonRefresh.isEnabled = false
            var responseJsonObject = JSONObject()
            withContext(Dispatchers.IO) {
                Helpers.callPineconeFetchAPI(this@DataDump) { success, response ->
                    if (success) {
                        responseJsonObject = response
                    }
                    this@DataDump.runOnUiThread {
                        buttonRefresh.isEnabled = true
                    }
                }
            }

            // Doing this to get array in matches key
            val bodyJsonArray = responseJsonObject.getJSONArray("matches")
            // Selecting only metadata object from it
            val metadataArray = JSONArray()
            for (i in 0 until bodyJsonArray.length()) {
                val jsonObject = bodyJsonArray.getJSONObject(i)
                val metadataObject = jsonObject.optJSONObject("metadata")

                // Doing this to include ID of the vector which isn't in the metadata JSON object
                if (metadataObject != null) {
                    val modifiedMetadataObject = JSONObject(metadataObject.toString())
                    modifiedMetadataObject.put("id", jsonObject.getString("id"))
                    metadataArray.put(modifiedMetadataObject)
                }
            }

            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            // Create a comparator to compare JSONObjects based on the currenttimeformattedstring field
            val comparator = Comparator<JSONObject> { json1, json2 ->
                val id1 = json1.optString("id")
                val id2 = json2.optString("id")
                val timestamp1 = json1.optString("currenttimeformattedstring")
                val timestamp2 = json2.optString("currenttimeformattedstring")
                // Log.i("DataDump", "json1\n$json1\n\njson2\n$json2")

                // Convert timestamps to milliseconds
                val millis1 = dateFormat.parse(timestamp1)
                val millis2 = dateFormat.parse(timestamp2)

                // Compare dates
                millis2?.compareTo(millis1) ?: 0
            }
            // Convert JSONArray to a List of JSONObjects
            val metadataList = (0 until metadataArray.length()).mapTo(mutableListOf()) { metadataArray.getJSONObject(it) }
            // Sort the List using the comparator
            Collections.sort(metadataList, comparator)

            dataDumpList = JSONArray(metadataList)
            dataSharedPref.edit().putString("dataDump", dataDumpList.toString()).apply()

            if (dataDumpList.length() > 0) dataPopulated() else dataNotPopulated()

            adapter.updateData(dataDumpList)
            recyclerView.scrollToPosition(0)
        }
    }
    private fun dataLoading() {
        loadingTextView.text = getString(R.string.loadingText)
        loadingTextView.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE
    }
    private fun dataPopulated() {
        loadingTextView.text = ""
        loadingTextView.visibility = View.GONE
        recyclerView.visibility = View.VISIBLE
    }
    private fun dataNotPopulated() {
        loadingTextView.text = getString(R.string.noDataText)
        loadingTextView.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE
    }
    // endregion

    // region Back Related
    override fun onBackPressed() {
        onBackPressedDispatcher.onBackPressed()
        finish()
    }
    private fun buttonSetup() {
        backButton = findViewById(R.id.buttonBack)
        backButton.setOnClickListener {
            onBackPressed()
        }
    }
    // endregion
}