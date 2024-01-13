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
        dataDumpList = JSONArray(dataDumpString)
        Log.i("DataDump", "dataDumpList\n$dataDumpList")

        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = DataDumpAdapter(dataDumpList, this)
        recyclerView.adapter = adapter

        if (dataDumpList.length() == 0) pullS3ObjectsData() else dataPopulated()

        buttonRefresh.setOnClickListener {
            dataLoading()
            pullS3ObjectsData()
        }
    }
    private fun pullS3ObjectsData() {
        CoroutineScope(Dispatchers.Main).launch {
            dataDumpList = withContext(Dispatchers.IO) {
                Helpers.getObjectsInS3(this@DataDump)
            }
            dataSharedPref.edit().putString("dataDump", dataDumpList.toString()).apply()

            if (dataDumpList.length() > 0) dataPopulated() else dataNotPopulated()

            adapter.updateData(dataDumpList)
            recyclerView.scrollToPosition(adapter.itemCount - 1)
        }
    }
    private fun dataLoading() {
        loadingTextView.text = getString(R.string.loading)
        loadingTextView.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE
    }
    private fun dataPopulated() {
        loadingTextView.text = ""
        loadingTextView.visibility = View.GONE
        recyclerView.visibility = View.VISIBLE
    }
    private fun dataNotPopulated() {
        loadingTextView.text = getString(R.string.noData)
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