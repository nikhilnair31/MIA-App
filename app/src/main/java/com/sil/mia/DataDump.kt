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
import java.time.LocalDate

class DataDump : AppCompatActivity() {
    // region Vars
    private lateinit var recyclerView: RecyclerView
    private lateinit var loadingTextView: TextView
    private lateinit var backButton: ImageButton
    private lateinit var buttonRefresh: ImageButton

    private lateinit var generalSharedPref: SharedPreferences
    private lateinit var dataSharedPref: SharedPreferences
    private lateinit var adapter: DataDumpAdapter
    private var dataDumpList = JSONArray()
    private var maxFetchCount: Int = 100
    // endregion

    // region Common
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_data_dump)

        loadingTextView = findViewById(R.id.loadingTextView)
        buttonRefresh = findViewById(R.id.buttonRefresh)
        recyclerView = findViewById(R.id.recyclerView)

        dataRelated()
        buttonSetup()
    }
    // endregion

    // region Data Related
    private fun dataRelated() {
        Log.i("DataDump", "DataDump dataRelated")

        // Set integer values
        maxFetchCount = resources.getInteger(R.integer.maxFetchCount)

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

            val queryVectorArray = FloatArray(1536)
            val queryVectorArrayJson = JSONArray().apply {
                for (value in queryVectorArray) {
                    put(value)
                }
            }
            // Log.i("DataDump", "queryVectorArrayJson: $queryVectorArrayJson")

            // Creating final filter object
            val filterJsonObject = JSONObject().apply {
                // Objects to get last month's filter
                val currentDate = LocalDate.now()
                val oneMonthAgo = currentDate.minusMonths(1)
                // Log.i("DataDump", "currentDate: $currentDate\noneMonthAgo: $oneMonthAgo")

                // Doing this to solve cases where last month was different
                put("month", JSONObject().apply {
                    put("\$in", JSONArray().apply {
                        put(oneMonthAgo.monthValue)
                        put(currentDate.monthValue)
                    })
                })
                put("year", JSONObject().apply {
                    put("\$in", JSONArray().apply {
                        put(oneMonthAgo.year)
                        put(currentDate.year)
                    })
                })

                // Need to filter vectors by username
                generalSharedPref = getSharedPreferences("com.sil.mia.generalSharedPrefs", Context.MODE_PRIVATE)
                val userName = generalSharedPref.getString("userName", null)
                put("username", userName)
            }
            // Log.i("DataDump", "filterJsonObject: $filterJsonObject")

            withContext(Dispatchers.IO) {
                Helpers.callPineconeFetchAPI(queryVectorArrayJson, filterJsonObject, maxFetchCount) { success, response ->
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

            val metadataList = Helpers.sortJsonDescending(metadataArray)
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
    @Deprecated("Deprecated in Java")
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