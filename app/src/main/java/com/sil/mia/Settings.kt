package com.sil.mia

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.TextView

class Settings : AppCompatActivity() {
    // region Vars
    private lateinit var generalSharedPreferences: SharedPreferences

    private lateinit var backButton: ImageButton

    private lateinit var usernameText: TextView

    private lateinit var audioSaveCheckbox: CheckBox
    private lateinit var cleanAudioCheckbox: CheckBox
    private lateinit var filterMusicCheckbox: CheckBox
    private lateinit var normalizeLoudnessCheckbox: CheckBox
    private lateinit var removeSilenceCheckbox: CheckBox

    private lateinit var dataDumpButton: Button
    // endregion

    // region Common
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        generalSharedPreferences = getSharedPreferences("com.sil.mia.generalSharedPrefs", Context.MODE_PRIVATE)

        textSetup()
        checkboxSetup()
        buttonSetup()
    }
    // endregion

    // region UI Related
    private fun textSetup() {
        usernameText = findViewById(R.id.usernameTextView)

        val userName = generalSharedPreferences.getString("userName", "")
        usernameText.text = "Username: $userName"
    }

    private fun checkboxSetup() {
        audioSaveCheckbox = findViewById(R.id.audioSaveCheckbox)
        cleanAudioCheckbox = findViewById(R.id.cleanAudioCheckbox)
        filterMusicCheckbox = findViewById(R.id.filterMusicCheckbox)
        normalizeLoudnessCheckbox = findViewById(R.id.normalizeLoudnessCheckbox)
        removeSilenceCheckbox = findViewById(R.id.removeSilenceCheckbox)

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
        backButton = findViewById(R.id.buttonBack)
        backButton.setOnClickListener {
            onBackPressed()
        }

        // TODO: Figure out how to replicate the ripple effect for all other buttons
        dataDumpButton = findViewById(R.id.dataDumpButton)
        dataDumpButton.setOnClickListener {
            val intent = Intent(this, DataDump::class.java)
            this.startActivity(intent)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        onBackPressedDispatcher.onBackPressed()
        finish()
    }
    // endregion
}