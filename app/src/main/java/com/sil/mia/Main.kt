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

class Main : AppCompatActivity() {
    // region Vars
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
        audioRelated()
    }
    // endregion

    // region Initial
    private fun initRelated() {
        dataSharedPref = getSharedPreferences("com.sil.mia.data", Context.MODE_PRIVATE)
        generalSharedPref = getSharedPreferences("com.sil.mia.generalSharedPrefs", Context.MODE_PRIVATE)

        generalSharedPref.edit().putBoolean("isFirstRun", false).apply()

        settingsButton = findViewById(R.id.settingsButton)
        settingsButton.setOnClickListener {
            val intent = Intent(this, Settings::class.java)
            this.startActivity(intent)
        }
    }
    // endregion

    // region Audio Related Functions
    private fun audioRelated() {
        Log.i("Main", "audioRelated")
        
        toggleButton = findViewById(R.id.toggleButton)
        if (isServiceRunning(AudioService::class.java)) {
            Log.i("Main", "Service IS Running")
            toggleButton.isChecked = true
        }
        toggleButton.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                Log.i("Main", "startService")
                startForegroundService(Intent(this, AudioService::class.java))
            } else {
                Log.i("Main", "stopService")
                stopService(Intent(this, AudioService::class.java))
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