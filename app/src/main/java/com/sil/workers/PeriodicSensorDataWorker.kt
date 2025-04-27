package com.sil.workers

import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.BatteryManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.sil.listeners.SensorListener
import com.sil.others.Helpers
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PeriodicSensorDataWorker(context: Context, workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams) {
    private val TAG = "PeriodicSensorDataWorker"

    override suspend fun doWork(): Result {
        Log.i(TAG, "doWork ")

        val sensorDataStr = collectSensorData(applicationContext)
        val sensorFile = createSensorDataFile(applicationContext, sensorDataStr)
        Helpers.uploadSensorFileToS3(applicationContext, sensorFile)

        return Result.success()
    }

    private fun collectSensorData(context: Context): String {
        val data = JSONObject()

        // Add timestamp
        data.put("timestamp", pullTimeFormattedString())

        // Add battery info
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        data.put("battery", batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY))

        // Add location if permitted
        if (hasLocationPermission(context)) {
            getLastKnownLocation(context)?.let { location ->
                data.put("latitude", location.latitude)
                data.put("longitude", location.longitude)
            }
        }

        // Add movement status
        val deviceSpeed = SensorListener(context).getDeviceStatus()
        data.put("movement", when {
            deviceSpeed < 10 -> "idle"
            deviceSpeed < 150 -> "normal"
            deviceSpeed < 500 -> "fast"
            else -> "unknown"
        })

        Log.d(TAG, "Sensor data: $data")
        return data.toString()
    }

    private fun createSensorDataFile(context: Context, sensorDataStr: String): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val file = File(context.cacheDir, "sensor_data_$timeStamp.json")

        file.writeText(sensorDataStr)
        return file
    }

    private fun hasLocationPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
    }
    private fun getLastKnownLocation(context: Context): Location? {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

        return try {
            if (hasLocationPermission(context)) {
                locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            } else null
        } catch (securityException: SecurityException) {
            // Handle SecurityException (e.g., show a message to the user)
            Log.e(TAG, "SecurityException: ${securityException.message}")
            null
        }
    }
    private fun pullTimeFormattedString(): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return dateFormat.format(Date())
    }
}