package com.sil.mia

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.widget.ImageButton
import android.widget.ToggleButton
import androidx.appcompat.app.AppCompatActivity
import com.sil.services.AudioService
import com.sil.services.ScreenshotService

class Main : AppCompatActivity() {
    // region Vars
    private val TAG = "Main"

    private var audioServiceIntent: Intent? = null
    private var screenshotServiceIntent: Intent? = null

    private lateinit var audioToggleButton: ToggleButton
    private lateinit var screenshotToggleButton: ToggleButton
    private lateinit var settingsButton: ImageButton

    private lateinit var generalSharedPref: SharedPreferences
    private lateinit var dataSharedPref: SharedPreferences
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
        dataSharedPref = getSharedPreferences("com.sil.mia.data", Context.MODE_PRIVATE)
        generalSharedPref = getSharedPreferences("com.sil.mia.generalSharedPrefs", Context.MODE_PRIVATE)

        generalSharedPref.edit().putBoolean("isFirstRun", false).apply()

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

        if (isServiceRunning(AudioService::class.java)) {
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
        }
    }
    private fun screenshotToggleRelated() {
        Log.i(TAG, "screenshotToggleRelated")

        if (isServiceRunning(ScreenshotService::class.java)) {
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
        }
    }

    private fun isServiceRunning(serviceClass: Class<*>): Boolean {
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        for (service in manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.name == service.service.className) {
                return true
            }
        }
        return false
    }
    // endregion
}