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
    private lateinit var adapter: DataDumpAdapter

    private lateinit var generalSharedPref: SharedPreferences
    private lateinit var dataSharedPref: SharedPreferences

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

        generalSharedPref = getSharedPreferences("com.sil.mia.generalSharedPrefs", Context.MODE_PRIVATE)

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

            val userName = generalSharedPref.getString("userName", "")
            if (userName != null) {
                dataDumpList = Helpers.refreshLocalContextRecordingData(userName, maxFetchCount)
            }
            dataSharedPref.edit().putString("dataDump", dataDumpList.toString()).apply()

            if (dataDumpList.length() > 0) dataPopulated() else dataNotPopulated()

            buttonRefresh.isEnabled = true
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