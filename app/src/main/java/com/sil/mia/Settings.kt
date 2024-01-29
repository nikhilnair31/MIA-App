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

    private lateinit var usernameText: TextView
    private lateinit var audioSaveCheckbox: CheckBox
    private lateinit var backButton: ImageButton
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
        usernameText.text = "$userName"
    }

    private fun checkboxSetup() {
        audioSaveCheckbox = findViewById(R.id.audioSaveCheckbox)

        val currSaveAudioFilesFlag = generalSharedPreferences.getString("saveAudioFiles", "false")
        audioSaveCheckbox.isChecked = currSaveAudioFilesFlag.toBoolean()

        audioSaveCheckbox.setOnCheckedChangeListener { _, isChecked ->
            generalSharedPreferences.edit().putString("saveAudioFiles", isChecked.toString()).apply()
        }
    }

    private fun buttonSetup() {
        backButton = findViewById(R.id.buttonBack)
        backButton.setOnClickListener {
            onBackPressed()
        }
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