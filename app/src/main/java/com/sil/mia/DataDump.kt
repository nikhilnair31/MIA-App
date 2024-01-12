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
    private lateinit var backButton: ImageButton
    private lateinit var loadingTextView: TextView

    private lateinit var dataSharedPref: SharedPreferences
    private lateinit var adapter: DataDumpAdapter
    private var dataListUI = JSONArray()
    // endregion

    // region Common
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_datadump)

        dataRelated()
        buttonSetup()
    }
    // endregion

    // region DataDump Related
    private fun dataRelated() {
        Log.i("DataDump", "DataDump dataRelated")

        loadingTextView = findViewById(R.id.loadingTextView)
        recyclerView = findViewById(R.id.recyclerView)

        dataSharedPref = getSharedPreferences("com.sil.mia.data", Context.MODE_PRIVATE)
        val dataDumpString = dataSharedPref.getString("dataDump", "")
        dataListUI = JSONArray(dataDumpString)
        Log.i("DataDump", "dataListUI\n$dataListUI")

        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = DataDumpAdapter(dataListUI, this)
        recyclerView.adapter = adapter

        if (dataListUI.length() == 0) {
            CoroutineScope(Dispatchers.Main).launch {
                dataListUI = withContext(Dispatchers.IO) {
                    Helpers.getObjectsInS3(this@DataDump)
                }
                dataSharedPref.edit().putString("dataDump", dataListUI.toString()).apply()

                if (dataListUI.length() > 0) {
                    loadingTextView.text = ""
                    loadingTextView.visibility = View.GONE
                    recyclerView.visibility = View.VISIBLE
                } else {
                    loadingTextView.text = getString(R.string.noData)
                    loadingTextView.visibility = View.VISIBLE
                    recyclerView.visibility = View.GONE
                }
                adapter.updateData(dataListUI)
                recyclerView.scrollToPosition(adapter.itemCount - 1)
            }
        }
        else {
            loadingTextView.text = ""
            loadingTextView.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
        }
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