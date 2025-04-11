package com.sil.mia

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
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

    private lateinit var dataDumpButton: Button

    private lateinit var thoughtsStartTimeEditText: EditText
    private lateinit var thoughtsEndTimeEditText: EditText

    private lateinit var audioSaveCheckbox: CheckBox
    private lateinit var cleanAudioCheckbox: CheckBox
    private lateinit var filterMusicCheckbox: CheckBox
    private lateinit var normalizeLoudnessCheckbox: CheckBox
    private lateinit var removeSilenceCheckbox: CheckBox
    // endregion

    // region Common
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        generalSharedPreferences = getSharedPreferences("com.sil.mia.generalSharedPrefs", Context.MODE_PRIVATE)

        usernameText = findViewById(R.id.usernameText)
        dataDumpButton = findViewById(R.id.dataDumpButton)
        thoughtsStartTimeEditText = findViewById(R.id.thoughtsStartTimeEditText)
        thoughtsEndTimeEditText = findViewById(R.id.thoughtsEndTimeEditText)
        backButton = findViewById(R.id.buttonBack)
        audioSaveCheckbox = findViewById(R.id.audioSaveCheckbox)
        cleanAudioCheckbox = findViewById(R.id.cleanAudioCheckbox)
        filterMusicCheckbox = findViewById(R.id.filterMusicCheckbox)
        normalizeLoudnessCheckbox = findViewById(R.id.normalizeLoudnessCheckbox)
        removeSilenceCheckbox = findViewById(R.id.removeSilenceCheckbox)

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
        cleanAudioCheckbox.isChecked =  generalSharedPreferences.getString("cleanAudio", "false").toBoolean()
        filterMusicCheckbox.isChecked =  generalSharedPreferences.getString("filterMusic", "false").toBoolean()
        normalizeLoudnessCheckbox.isChecked =  generalSharedPreferences.getString("normalizeLoudness", "false").toBoolean()
        removeSilenceCheckbox.isChecked =  generalSharedPreferences.getString("removeSilence", "false").toBoolean()

        // Check clean audio if any of the other checkboxes is checked
        if (audioSaveCheckbox.isChecked || filterMusicCheckbox.isChecked || normalizeLoudnessCheckbox.isChecked || removeSilenceCheckbox.isChecked) {
            cleanAudioCheckbox.isChecked = true
        }

        audioSaveCheckbox.setOnCheckedChangeListener { _, isChecked ->
            generalSharedPreferences.edit().putString("saveAudioFiles", isChecked.toString()).apply()
        }
        cleanAudioCheckbox.setOnCheckedChangeListener { _, isChecked ->
            generalSharedPreferences.edit().putString("cleanAudio", isChecked.toString()).apply()
            if (!isChecked) {
                filterMusicCheckbox.isChecked = false
                normalizeLoudnessCheckbox.isChecked = false
                removeSilenceCheckbox.isChecked = false
            }
        }
        filterMusicCheckbox.setOnCheckedChangeListener { _, isChecked ->
            generalSharedPreferences.edit().putString("filterMusic", isChecked.toString()).apply()
            if (isChecked) {
                cleanAudioCheckbox.isChecked = true
            }
        }
        normalizeLoudnessCheckbox.setOnCheckedChangeListener { _, isChecked ->
            generalSharedPreferences.edit().putString("normalizeLoudness", isChecked.toString()).apply()
            if (isChecked) {
                cleanAudioCheckbox.isChecked = true
            }
        }
        removeSilenceCheckbox.setOnCheckedChangeListener { _, isChecked ->
            generalSharedPreferences.edit().putString("removeSilence", isChecked.toString()).apply()
            if (isChecked) {
                cleanAudioCheckbox.isChecked = true
            }
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