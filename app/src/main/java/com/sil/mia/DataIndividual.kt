package com.sil.mia

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.text.Editable
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import com.sil.others.Helpers
import org.json.JSONObject

class DataIndividual : AppCompatActivity() {
    // region Vars
    private lateinit var titleTextView: TextView

    private lateinit var backButton: ImageButton
    private lateinit var updateButton: ImageButton

    private lateinit var addressTextView: EditText
    private lateinit var weatherTextView: EditText
    private lateinit var sourceTextView: EditText
    private lateinit var textTextView: EditText

    private lateinit var selectedData: JSONObject
    // endregion

    // region Common
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_data_individual)

        dataRelated()
        buttonSetup()
    }
    // endregion

    // region Data Related
    private fun dataRelated() {
        Log.i("DataIndividual", "dataRelated")

        titleTextView = findViewById(R.id.titleTextView)
        addressTextView = findViewById(R.id.addressTextView)
        weatherTextView = findViewById(R.id.weatherTextView)
        sourceTextView = findViewById(R.id.sourceTextView)
        textTextView = findViewById(R.id.textTextView)

        val receivedIntent = intent
        val jsonString = receivedIntent.getStringExtra("selectedData")
        if (jsonString != null) {
            selectedData = JSONObject(jsonString)

            val timeString = selectedData.optString("currenttimeformattedstring", "-")
            val fileNameString = selectedData.optString("filename", "-")
            titleTextView.text = "$timeString\n$fileNameString"

            addressTextView.text = Editable.Factory.getInstance().newEditable(selectedData.optString("address", "-"))
            weatherTextView.text = Editable.Factory.getInstance().newEditable(selectedData.optString("firstweatherdescription", "-"))
            sourceTextView.text = Editable.Factory.getInstance().newEditable(selectedData.optString("source", "-"))
            textTextView.text = Editable.Factory.getInstance().newEditable(selectedData.optString("text", "-"))
        }
        else {
            addressTextView.visibility = View.GONE
            weatherTextView.visibility = View.GONE
            sourceTextView.visibility = View.GONE
            textTextView.visibility = View.GONE
        }
    }
    // endregion

    // region Button Related
    private fun buttonSetup() {
        updateButton = findViewById(R.id.updateButton)
        backButton = findViewById(R.id.buttonBack)

        updateButton.setOnClickListener {
            updateData()
        }
        backButton.setOnClickListener {
            onBackPressed()
        }
    }
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        onBackPressedDispatcher.onBackPressed()
        finish()
    }
    // FIXME: I should ideally regenerate the updated text's vector as well
    private fun updateData() {
        val vectorId = selectedData.optString("id") ?: ""
        Log.i("DataIndividual", "vectorId: $vectorId")

        Helpers.callPineconeUpdateByIdAPI(
            this,
            vectorId,
            addressTextView.text.toString(),
            weatherTextView.text.toString(),
            sourceTextView.text.toString(),
            textTextView.text.toString()
        )
        onBackPressed()
    }
    // endregion
}