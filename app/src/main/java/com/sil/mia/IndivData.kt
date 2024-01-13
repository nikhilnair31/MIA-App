package com.sil.mia

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.sil.adapters.DataIndivAdapter
import org.json.JSONArray
import org.json.JSONObject

class IndivData : AppCompatActivity() {
    // region Vars
    private lateinit var recyclerView: RecyclerView
    private lateinit var backButton: ImageButton
    private lateinit var updateButton: ImageButton
    private lateinit var cancelButton: ImageButton
    private lateinit var titleTextView: TextView

    private lateinit var adapter: DataIndivAdapter
    private var dataDumpList = JSONArray()
    // endregion

    // region Common
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_indivdata)

        dataRelated()
        buttonSetup()
    }
    // endregion

    // region IndivData Related
    private fun dataRelated() {
        Log.i("IndivData", "dataRelated")

        titleTextView = findViewById(R.id.titleTextView)
        recyclerView = findViewById(R.id.recyclerView)

        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = DataIndivAdapter(dataDumpList)
        recyclerView.adapter = adapter

        val receivedIntent = intent
        val jsonString = receivedIntent.getStringExtra("selectedData")
        if (jsonString != null) {
            val selectedData = JSONObject(jsonString)
            dataDumpList.put(selectedData)
            titleTextView.text = "${selectedData.getString("currenttimeformattedstring")}" + "\n${selectedData.getString("filename")}\n" // + "\n${selectedData.getString("vector_id")}"

            adapter.updateData(dataDumpList)
            recyclerView.scrollToPosition(adapter.itemCount - 1)
        }
    }
    // endregion

    // region Button Related
    override fun onBackPressed() {
        onBackPressedDispatcher.onBackPressed()
        finish()
    }
    private fun buttonSetup() {
        backButton = findViewById(R.id.buttonBack)
        backButton.setOnClickListener {
            onBackPressed()
        }

        //TODO: Do something with this to update Pinecone/AWS metadata
        updateButton = findViewById(R.id.updateButton)
        cancelButton = findViewById(R.id.cancelButton)
    }
    // endregion
}