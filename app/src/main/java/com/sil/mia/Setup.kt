package com.sil.mia

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.sil.others.Helpers

class Setup : AppCompatActivity() {
    // region Vars
    private val TAG = "Setup"

    private lateinit var generalSharedPreferences: SharedPreferences

    private val initRequestCode = 100
    private val backgroundLocationRequestCode = 102
    private val batteryUnrestrictedRequestCode = 103

    private lateinit var usernameEditText: EditText

    private lateinit var permissionButton: Button

    private lateinit var updateAndNextButton: ImageButton
    // endregion

    // region Common
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup)

        initRelated()
    }

    private fun initRelated() {
        Log.i(TAG, "initRelated")

        generalSharedPreferences = getSharedPreferences("com.sil.mia.generalSharedPrefs", Context.MODE_PRIVATE)

        // Setup UI related
        usernameEditText = findViewById(R.id.usernameEditText)
        permissionButton = findViewById(R.id.buttonPermission)
        updateAndNextButton = findViewById(R.id.buttonUpdateAndNext)

        // Setup button related
        permissionButton.setOnClickListener {
            permissionRelated()
        }
        updateAndNextButton.setOnClickListener {
            goToMain()
        }

        // Check if permissions are granted
        updatePermissionButtonColor()
    }

    private fun goToMain() {
        val userNameText = usernameEditText.text.toString()

        if (userNameText.isNotEmpty()) {
            Log.i(TAG, "goToMain\nuserName: $userNameText")

            generalSharedPreferences.edit().putString("userName", userNameText).apply()

            launchMainActivity()
        }
        else {
            Helpers.showToast(this, "Invalid username")
        }
    }
    private fun launchMainActivity() {
        val intent = Intent(this, Main::class.java)
        startActivity(intent)
        finish()
    }
    private fun updatePermissionButtonColor() {
        if (areAllPermissionsGranted()) {
            // Change button color to green (use your specific green color resource)
            permissionButton.setBackgroundColor(ContextCompat.getColor(this, R.color.accent_0))
            permissionButton.setTextColor(ContextCompat.getColor(this, R.color.white))
            permissionButton.text = getString(R.string.gavePermissionsText)
        } else {
            // Keep or reset to default color
            permissionButton.setBackgroundColor(ContextCompat.getColor(this, R.color.white))
            permissionButton.setTextColor(ContextCompat.getColor(this, R.color.gray_70))
            permissionButton.text = getString(R.string.givePermissionsText)
        }
    }
    // endregion

    // region Permissions Related
    private fun permissionRelated() {
        Log.i(TAG, "Requesting initial permissions")

        if (!areAllPermissionsGranted()) {
            val permList = arrayOf(
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.POST_NOTIFICATIONS,
                Manifest.permission.BODY_SENSORS,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.READ_MEDIA_IMAGES,
            )
            ActivityCompat.requestPermissions(this, permList, initRequestCode)
        }
    }

    private fun areAllPermissionsGranted(): Boolean {
        // Check for all regular permissions
        val hasAudioPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val hasNotificationPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        val hasSensorPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS) == PackageManager.PERMISSION_GRANTED
        val hasFineLocationPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarseLocationPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasBackgroundLocationPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasMediaImagesPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED

        // Check for battery optimization exemption
        val powerManager = this.getSystemService(Context.POWER_SERVICE) as PowerManager
        val isBatteryOptimizationIgnored = powerManager.isIgnoringBatteryOptimizations(this.packageName)

        val hasAllPermissions = hasAudioPermission && hasNotificationPermission && hasSensorPermission &&
                hasFineLocationPermission && hasCoarseLocationPermission &&
                hasBackgroundLocationPermission && isBatteryOptimizationIgnored
                && hasMediaImagesPermission
        Log.i(TAG, "hasAllPermissions: $hasAllPermissions")

        return hasAllPermissions
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == batteryUnrestrictedRequestCode) {
            // Check if permission was granted
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (powerManager.isIgnoringBatteryOptimizations(packageName)) {
                Log.i("Permissions", "Battery optimization ignored")
                // Update button color
                updatePermissionButtonColor()
            }
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
                    updatePermissionButtonColor()
                    launchMainActivity()
                }
                return
            }
        }
    }
    private fun getBackgroundLocationPermission() {
        Log.i(TAG, "Requesting getBackgroundLocationPermission")

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            val permList = arrayOf(
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            )
            ActivityCompat.requestPermissions(this, permList, backgroundLocationRequestCode)
        }
    }
    private fun getBatteryUnrestrictedPermission() {
        Log.i(TAG, "Requesting getBatteryUnrestrictedPermission")

        if (!(this.getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(this.packageName)) {
            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            startActivityForResult(intent, batteryUnrestrictedRequestCode)
        }
    }
    // endregion
}