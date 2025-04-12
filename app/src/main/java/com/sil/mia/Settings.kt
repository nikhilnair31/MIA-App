package com.sil.mia

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.sil.others.Helpers

class Settings : AppCompatActivity() {
    // region Vars
    private lateinit var generalSharedPreferences: SharedPreferences

    private lateinit var backButton: ImageButton

    private lateinit var usernameText: TextView

    private lateinit var thoughtsStartTimeEditText: EditText
    private lateinit var thoughtsEndTimeEditText: EditText

    private lateinit var audioSaveCheckbox: CheckBox
    private lateinit var audioPreprocessCheckbox: CheckBox
    // endregion

    // region Common
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        generalSharedPreferences = getSharedPreferences("com.sil.mia.generalSharedPrefs", Context.MODE_PRIVATE)

        usernameText = findViewById(R.id.usernameText)
        thoughtsStartTimeEditText = findViewById(R.id.thoughtsStartTimeEditText)
        thoughtsEndTimeEditText = findViewById(R.id.thoughtsEndTimeEditText)
        audioSaveCheckbox = findViewById(R.id.audioSaveCheckbox)
        audioPreprocessCheckbox = findViewById(R.id.preprocessAudioCheckbox)
        backButton = findViewById(R.id.buttonBack)

        textSetup()
        editTextSetup()
        checkboxSetup()
        buttonSetup()
    }
    // endregion

    // region UI Related
    private fun textSetup() {
        usernameText.text = generalSharedPreferences.getString("userName", "")
    }

    private fun editTextSetup() {
        thoughtsStartTimeEditText.text = Editable.Factory.getInstance().newEditable(generalSharedPreferences.getInt("thoughtsStartTime", 6).toString())
        thoughtsEndTimeEditText.text = Editable.Factory.getInstance().newEditable(generalSharedPreferences.getInt("thoughtsEndTime", 0).toString())

        thoughtsStartTimeEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}
            override fun onTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}
            override fun afterTextChanged(editable: Editable) {
                try {
                    val thoughtsStartTime = editable.toString()
                    if (thoughtsStartTime.isNotEmpty()) {
                        generalSharedPreferences.edit().putInt("thoughtsStartTime", thoughtsStartTime.toInt()).apply()
                    }
                    else {
                        Helpers.showToast(this@Settings, "Invalid start time")
                    }
                }
                catch (e: NumberFormatException) {
                    e.printStackTrace()
                }
            }
        })
        thoughtsEndTimeEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}
            override fun onTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}
            override fun afterTextChanged(editable: Editable) {
                try {
                    val thoughtsEndTime = editable.toString()
                    if (thoughtsEndTime.isNotEmpty()) {
                        generalSharedPreferences.edit().putInt("thoughtsEndTime", thoughtsEndTime.toInt()).apply()
                    }
                    else {
                        Helpers.showToast(this@Settings, "Invalid end time")
                    }
                }
                catch (e: NumberFormatException) {
                    e.printStackTrace()
                }
            }
        })
    }

    private fun checkboxSetup() {
        audioSaveCheckbox.isChecked =  generalSharedPreferences.getString("saveAudioFiles", "false").toBoolean()
        audioPreprocessCheckbox.isChecked =  generalSharedPreferences.getString("preprocessAudio", "false").toBoolean()

        audioSaveCheckbox.setOnCheckedChangeListener { _, isChecked ->
            generalSharedPreferences.edit().putString("saveAudioFiles", isChecked.toString()).apply()
        }
        audioPreprocessCheckbox.setOnCheckedChangeListener { _, isChecked ->
            generalSharedPreferences.edit().putString("preprocessAudio", isChecked.toString()).apply()
        }
    }

    private fun buttonSetup() {
        backButton.setOnClickListener {
            onBackPressed()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        onBackPressedDispatcher.onBackPressed()
        finish()
    }
    // endregion
}