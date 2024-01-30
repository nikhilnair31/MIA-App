package com.sil.mia

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageButton
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.sil.others.Helpers

class Setup : AppCompatActivity() {
    // region Vars
    private lateinit var generalSharedPreferences: SharedPreferences

    private val initRequestCode = 100
    private val backgroundLocationRequestCode = 102
    private val batteryUnrestrictedRequestCode = 103

    private lateinit var editText: EditText
    
    private lateinit var permissionButton: Button
    
    private lateinit var audioSaveCheckbox: CheckBox
    private lateinit var cleanAudioCheckbox: CheckBox
    private lateinit var filterMusicCheckbox: CheckBox
    private lateinit var normalizeLoudnessCheckbox: CheckBox
    private lateinit var removeSilenceCheckbox: CheckBox

    private lateinit var updateAndNextButton: ImageButton
    // endregion

    // region Common
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup)

        generalSharedPreferences = getSharedPreferences("com.sil.mia.generalSharedPrefs", Context.MODE_PRIVATE)

        editText = findViewById(R.id.editText)
        permissionButton = findViewById(R.id.buttonPermission)
        audioSaveCheckbox = findViewById(R.id.audioSaveCheckbox)
        cleanAudioCheckbox = findViewById(R.id.cleanAudioCheckbox)
        filterMusicCheckbox = findViewById(R.id.filterMusicCheckbox)
        normalizeLoudnessCheckbox = findViewById(R.id.normalizeLoudnessCheckbox)
        removeSilenceCheckbox = findViewById(R.id.removeSilenceCheckbox)
        updateAndNextButton = findViewById(R.id.buttonUpdateAndNext)

        buttonSetup()
        checkboxSetup()
    }

    private fun buttonSetup() {
        Log.i("Main", "buttonSetup")

        permissionButton.setOnClickListener {
            permissionRelated()
        }
        updateAndNextButton.setOnClickListener {
            goToMain()
        }
    }
    private fun goToMain() {
        val userNameText = editText.text.toString()
        if (userNameText.isNotEmpty()) {
            Log.i("Setup", "goToMain userName: $userNameText")

            generalSharedPreferences.edit().putString("userName", userNameText).apply()
            generalSharedPreferences.edit().putString("saveAudioFiles", audioSaveCheckbox.isChecked.toString().lowercase()).apply()
            generalSharedPreferences.edit().putString("cleanAudio", cleanAudioCheckbox.isChecked.toString().lowercase()).apply()
            generalSharedPreferences.edit().putString("filterMusic", filterMusicCheckbox.isChecked.toString().lowercase()).apply()
            generalSharedPreferences.edit().putString("normalizeLoudness", normalizeLoudnessCheckbox.isChecked.toString().lowercase()).apply()
            generalSharedPreferences.edit().putString("removeSilence", removeSilenceCheckbox.isChecked.toString().lowercase()).apply()

            launchChatActivity()
        }
        else {
            Helpers.showToast(this, "Not a a valid username :(")
        }
    }
    private fun launchChatActivity() {
        val intent = Intent(this, Main::class.java)
        startActivity(intent)
        finish()
    }
    // endregion

    // region Permissions Related
    private fun permissionRelated() {
        Log.i("Main", "Requesting initial permissions")

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
            || ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            || ContextCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS) != PackageManager.PERMISSION_GRANTED
            || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
            || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            val permList = arrayOf(
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.POST_NOTIFICATIONS,
                Manifest.permission.BODY_SENSORS,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            )
            ActivityCompat.requestPermissions(this, permList, initRequestCode)
        }
    }
    private fun getBackgroundLocationPermission() {
        Log.i("Main", "Requesting getBackgroundLocationPermission")

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            val permList = arrayOf(
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            )
            ActivityCompat.requestPermissions(this, permList, backgroundLocationRequestCode)
        }
    }
    private fun getBatteryUnrestrictedPermission() {
        Log.i("Main", "Requesting getBatteryUnrestrictedPermission")

        if (!(this.getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(this.packageName)) {
            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            startActivityForResult(intent, batteryUnrestrictedRequestCode)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            initRequestCode -> {
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    Log.i("Permissions", "initRequestCode granted")
                    getBackgroundLocationPermission()
                }
                return
            }
            backgroundLocationRequestCode -> {
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    Log.i("Permissions", "backgroundLocationRequestCode granted")
                    getBatteryUnrestrictedPermission()
                }
                return
            }
            batteryUnrestrictedRequestCode -> {
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    Log.i("Permissions", "batteryUnrestrictedRequestCode granted")
                    launchChatActivity()
                }
                return
            }
        }
    }
    // endregion

    // region Checkboxes
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
    // endregion
}