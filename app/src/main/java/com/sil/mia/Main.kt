package com.sil.mia

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.widget.ImageButton
import android.widget.ToggleButton
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import com.sil.others.Helpers
import com.sil.services.AudioService
import com.sil.services.ScreenshotService

class Main : AppCompatActivity() {
    // region Vars
    private val TAG = "Main"

    private var audioServiceIntent: Intent? = null
    private var screenshotServiceIntent: Intent? = null

    private lateinit var generalSharedPref: SharedPreferences

    private lateinit var audioToggleButton: ToggleButton
    private lateinit var screenshotToggleButton: ToggleButton
    private lateinit var settingsButton: ImageButton
    // endregion

    // region Common
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initRelated()
        audioToggleRelated()
        screenshotToggleRelated()
    }

    private fun initRelated() {
        generalSharedPref = getSharedPreferences("com.sil.mia.generalSharedPrefs", Context.MODE_PRIVATE)
        generalSharedPref.edit { putBoolean("isFirstRun", false) }

        audioToggleButton = findViewById(R.id.audioToggleButton)
        screenshotToggleButton = findViewById(R.id.screenshotToggleButton)
        settingsButton = findViewById(R.id.settingsButton)

        settingsButton.setOnClickListener {
            val intent = Intent(this, Settings::class.java)
            this.startActivity(intent)
        }
    }

    private fun audioToggleRelated() {
        Log.i(TAG, "audioToggleRelated")

        if (Helpers.isServiceRunning(this, AudioService::class.java)) {
            Log.i(TAG, "Audio service IS Running")
            audioToggleButton.isChecked = true
        }
        audioToggleButton.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                Log.i(TAG, "Audio service created")

                audioServiceIntent = Intent(this, AudioService::class.java)
                startForegroundService(audioServiceIntent)
            } else {
                Log.i(TAG, "Audio service stopped")
                stopService(audioServiceIntent)
            }
            handlePeriodicTasks()
        }
    }
    private fun screenshotToggleRelated() {
        Log.i(TAG, "screenshotToggleRelated")

        if (Helpers.isServiceRunning(this, ScreenshotService::class.java)) {
            Log.i(TAG, "Screenshot service ARE Running")
            screenshotToggleButton.isChecked = true
        }
        screenshotToggleButton.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                Log.i(TAG, "Screenshot service created")

                screenshotServiceIntent = Intent(this, ScreenshotService::class.java)
                startService(screenshotServiceIntent)
            } else {
                Log.i(TAG, "Screenshot service stopped")
                stopService(screenshotServiceIntent)
            }
            handlePeriodicTasks()
        }
    }

    private fun handlePeriodicTasks() {
        Log.i(TAG, "handlePeriodicTasks")

        val isAnyFeatureActive = audioToggleButton.isChecked || screenshotToggleButton.isChecked

        if (isAnyFeatureActive) {
            Helpers.schedulePeriodicUploadWork(this)
            Helpers.schedulePeriodicNotificationCheckWork(this)
        }
        else {
            Helpers.cancelPeriodicUploadWork(this)
            Helpers.cancelPeriodicNotificationCheckWork(this)
        }
    }
    // endregion
}