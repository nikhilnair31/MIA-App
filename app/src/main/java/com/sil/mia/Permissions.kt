package com.sil.mia

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.widget.Button
import android.widget.ImageButton
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class Permissions : AppCompatActivity() {
    // region Vars
    private val initRequestCode = 100
    private val backgroundLocationRequestCode = 102
    private val batteryUnrestrictedRequestCode = 103

    private lateinit var permissionButton: Button
    private lateinit var updateAndNextButton: ImageButton
    // endregion

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_permissions)

        buttonSetup()
    }

    // region Buttons Related
    private fun buttonSetup() {
        permissionButton = findViewById(R.id.buttonPermission)
        permissionButton.setOnClickListener {
            permissionRelated()
        }

        updateAndNextButton = findViewById(R.id.buttonUpdateAndNext)
        updateAndNextButton.setOnClickListener {
            launchChatActivity()
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
}