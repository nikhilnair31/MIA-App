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

    private lateinit var toggleButton: ToggleButton
    private lateinit var settingsButton: ImageButton

    private lateinit var generalSharedPref: SharedPreferences
    private lateinit var dataSharedPref: SharedPreferences
    // endregion

    // region Common
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initRelated()
        toggleRelated()
    }

    private fun initRelated() {
        dataSharedPref = getSharedPreferences("com.sil.mia.data", Context.MODE_PRIVATE)
        generalSharedPref = getSharedPreferences("com.sil.mia.generalSharedPrefs", Context.MODE_PRIVATE)

        generalSharedPref.edit().putBoolean("isFirstRun", false).apply()

        toggleButton = findViewById(R.id.toggleButton)
        settingsButton = findViewById(R.id.settingsButton)

        settingsButton.setOnClickListener {
            val intent = Intent(this, Settings::class.java)
            this.startActivity(intent)
        }
    }

    private fun toggleRelated() {
        Log.i(TAG, "toggleRelated")

        if (isServiceRunning(AudioService::class.java) && isServiceRunning(ScreenshotService::class.java)) {
            Log.i(TAG, "Audio and Screenshots services ARE Running")
            toggleButton.isChecked = true
        }
        toggleButton.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                Log.i(TAG, "Audio and Screenshots services created")

                audioServiceIntent = Intent(this, AudioService::class.java)
                startForegroundService(audioServiceIntent)

                screenshotServiceIntent = Intent(this, ScreenshotService::class.java)
                startService(screenshotServiceIntent)
            } else {
                Log.i(TAG, "Audio and Screenshots services stopped")
                stopService(audioServiceIntent)
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