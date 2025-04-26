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
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.sil.others.Helpers
import com.sil.services.AudioService
import com.sil.services.ScreenshotService
import com.sil.workers.PeriodicNotificationCallWorker
import com.sil.workers.PeriodicSensorDataWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class Main : AppCompatActivity() {
    // region Vars
    private val TAG = "Main"
    private val PREFS_GENERAL = "com.sil.mia.generalSharedPrefs"
    private val KEY_FIRST_RUN = "isFirstRun"
    private val KEY_AUDIO_ENABLED = "isAudioRecordingEnabled"
    private val KEY_SCREENSHOT_ENABLED = "isScreenshotMonitoringEnabled"
    private val KEY_NOTIFICATION_ENABLED = "isNotificationReceivingEnabled"
    private val KEY_SENSOR_UPLOAD_ENABLED = "isUploadingSensorDataEnabled"
    private val PERIODIC_NOTIFICATION_CHECK_WORK = "periodic_notification_check_work"
    private val PERIODIC_SENSOR_DATA_UPLOAD_WORK = "periodic_sensor_data_upload_work"

    private lateinit var generalSharedPref: SharedPreferences

    private lateinit var audioToggleButton: ToggleButton
    private lateinit var screenshotToggleButton: ToggleButton
    private lateinit var notificationsToggleButton: ToggleButton
    private lateinit var settingsButton: ImageButton
    // endregion

    // region Common
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initSharedPreferences()
        initViews()
        setupListeners()
        updateToggleStates()
    }

    private fun initSharedPreferences() {
        generalSharedPref = getSharedPreferences(PREFS_GENERAL, Context.MODE_PRIVATE)
        if (generalSharedPref.getBoolean(KEY_FIRST_RUN, true)) {
            generalSharedPref.edit { putBoolean(KEY_FIRST_RUN, false) }
        }
    }
    private fun initViews() {
        audioToggleButton = findViewById(R.id.audioToggleButton)
        screenshotToggleButton = findViewById(R.id.screenshotToggleButton)
        notificationsToggleButton = findViewById(R.id.notificationsToggleButton)

        settingsButton = findViewById(R.id.settingsButton)
    }
    private fun setupListeners() {
        settingsButton.setOnClickListener {
            startActivity(Intent(this, Settings::class.java))
        }
        audioToggleButton.setOnCheckedChangeListener { _, isChecked ->
            Log.i(TAG, "Audio toggle changed: isChecked=$isChecked")
            updateServiceState(
                AudioService::class.java,
                isChecked,
                KEY_AUDIO_ENABLED
            )
            val shouldUploadSensorData = audioToggleButton.isChecked || screenshotToggleButton.isChecked || notificationsToggleButton.isChecked
            updateWorkerState(
                "sensor",
                PERIODIC_SENSOR_DATA_UPLOAD_WORK,
                shouldUploadSensorData,
                KEY_SENSOR_UPLOAD_ENABLED
            )
        }
        screenshotToggleButton.setOnCheckedChangeListener { _, isChecked ->
            Log.i(TAG, "Screenshot toggle changed: isChecked=$isChecked")
            updateServiceState(
                ScreenshotService::class.java,
                isChecked,
                KEY_SCREENSHOT_ENABLED
            )
            val shouldUploadSensorData = audioToggleButton.isChecked || screenshotToggleButton.isChecked || notificationsToggleButton.isChecked
            updateWorkerState(
                "sensor",
                PERIODIC_SENSOR_DATA_UPLOAD_WORK,
                shouldUploadSensorData,
                KEY_SENSOR_UPLOAD_ENABLED
            )
        }
        notificationsToggleButton.setOnCheckedChangeListener { _, isChecked ->
            Log.i(TAG, "Notifications toggle changed: isChecked=$isChecked")
            updateWorkerState(
                "notification",
                PERIODIC_NOTIFICATION_CHECK_WORK,
                isChecked,
                KEY_NOTIFICATION_ENABLED
            )
            val shouldUploadSensorData = audioToggleButton.isChecked || screenshotToggleButton.isChecked || notificationsToggleButton.isChecked
            updateWorkerState(
                "sensor",
                PERIODIC_SENSOR_DATA_UPLOAD_WORK,
                shouldUploadSensorData,
                KEY_SENSOR_UPLOAD_ENABLED
            )
        }
    }
    private fun updateToggleStates() {
        audioToggleButton.isChecked = Helpers.isServiceRunning(this, AudioService::class.java)
        screenshotToggleButton.isChecked = Helpers.isServiceRunning(this, ScreenshotService::class.java)

        val sensorWorkInfoList =  WorkManager.getInstance(this).getWorkInfosForUniqueWork(PERIODIC_SENSOR_DATA_UPLOAD_WORK).get()
        val isSensorWorkerRunningOrEnqueued = sensorWorkInfoList.any {
            it.state == androidx.work.WorkInfo.State.RUNNING || it.state == androidx.work.WorkInfo.State.ENQUEUED
        }
        notificationsToggleButton.isChecked = isSensorWorkerRunningOrEnqueued
    }
    // endregion

    // region Service Management
    private fun updateServiceState(serviceClass: Class<*>, isEnabled: Boolean, preferenceKey: String) {
        val serviceIntent = Intent(this, serviceClass)
        if (isEnabled) {
            Log.i(TAG, "${serviceClass.simpleName} created")
            startForegroundService(serviceIntent)
        } else {
            Log.i(TAG, "${serviceClass.simpleName} stopped")
            stopService(serviceIntent)
        }
        CoroutineScope(Dispatchers.IO).launch {
            generalSharedPref.edit { putBoolean(preferenceKey, isEnabled) }
        }
    }
    // endregion

    // region Worker Management
    private fun updateWorkerState(workerType: String, workName: String, isEnabled: Boolean, preferenceKey: String, intervalMinutes: Long = 15) {
        if (isEnabled) {
            Log.i(TAG, "$workName created")

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            // Create the periodic work request
            val uploadWorkRequest = when (workerType) {
                "sensor" -> PeriodicWorkRequestBuilder<PeriodicSensorDataWorker>(intervalMinutes, TimeUnit.MINUTES)
                "notification" -> PeriodicWorkRequestBuilder<PeriodicNotificationCallWorker>(intervalMinutes, TimeUnit.MINUTES)
                else -> throw IllegalArgumentException("Unknown worker type: $workerType")
            }
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    OneTimeWorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .setConstraints(constraints)
                .build()

            // Schedule the work, replacing any existing one
            WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                workName,
                ExistingPeriodicWorkPolicy.REPLACE,
                uploadWorkRequest
            )
        } else {
            Log.i(TAG, "$workName stopped")
            WorkManager.getInstance(this).cancelUniqueWork(workName)
        }

        CoroutineScope(Dispatchers.IO).launch {
            generalSharedPref.edit { putBoolean(preferenceKey, isEnabled) }
        }
    }
    // endregion
}