package com.sil.others

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.BatteryManager
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.amazonaws.AmazonServiceException
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.regions.Region
import com.amazonaws.regions.Regions
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model.ObjectMetadata
import com.amazonaws.services.s3.model.PutObjectRequest
import com.sil.listeners.SensorListener
import com.sil.mia.BuildConfig
import com.sil.workers.UploadWorker
import kotlinx.coroutines.delay
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class Helpers {
    companion object {
        // region API Keys
        private const val TAG = "Helper"

        private const val BUCKET_NAME = BuildConfig.BUCKET_NAME
        private const val AWS_ACCESS_KEY = BuildConfig.AWS_ACCESS_KEY
        private const val AWS_SECRET_KEY = BuildConfig.AWS_SECRET_KEY
        private const val NOTIFICATION_LAMBDA_ENDPOINT = BuildConfig.NOTIFICATION_LAMBDA_ENDPOINT
        // endregion

        // region Init Related
        private val credentials = BasicAWSCredentials(AWS_ACCESS_KEY, AWS_SECRET_KEY)
        private val s3Client = AmazonS3Client(credentials).apply {
            setRegion(Region.getRegion(Regions.AP_SOUTH_1))
        }
        // endregion

        // region API Related
        suspend fun callNotificationCheckLambda(payload: JSONObject): JSONObject {
            var lastException: IOException? = null
            val minRequestInterval = 1000L  // Minimum interval between requests in milliseconds
            var lastRequestTime = 0L
            var attempt = 0

            while (attempt < 3) {
                try {
                    // Check if enough time has passed since the last request
                    val currentTime = System.currentTimeMillis()
                    val timeSinceLastRequest = currentTime - lastRequestTime
                    if (timeSinceLastRequest < minRequestInterval) {
                        delay(minRequestInterval - timeSinceLastRequest)
                    }

                    // Update the last request time
                    lastRequestTime = System.currentTimeMillis()

                    val client = OkHttpClient.Builder()
                        .connectTimeout(30, TimeUnit.SECONDS)
                        .readTimeout(30, TimeUnit.SECONDS)
                        .build()

                    val mediaType = "application/json".toMediaTypeOrNull()
                    val requestBody = payload.toString().toRequestBody(mediaType)

                    val request = Request.Builder()
                        .url(NOTIFICATION_LAMBDA_ENDPOINT)
                        .post(requestBody)
                        .build()

                    val response = client.newCall(request).execute()

                    if (response.isSuccessful && response.body != null) {
                        val responseBody = response.body!!.string()
                        return if (responseBody.isNotEmpty() && responseBody != "null") {
                            JSONObject(responseBody)
                        } else {
                            JSONObject()
                        }
                    } else {
                        return JSONObject()
                    }
                }
                catch (e: IOException) {
                    Log.e(TAG, "callNotifLambda IO Exception on attempt $attempt: ${e.message}")
                    lastException = e
                    delay(2000L * (attempt + 1))  // Exponential backoff
                }

                attempt++
            }

            lastException?.let {
                Log.e(TAG, "callNotifLambda failed after all attempts", it)
            }

            return JSONObject()
        }
        fun callNotificationFeedbackLambda(payload: JSONObject) {
            try {
                val client = OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .build()

                val mediaType = "application/json".toMediaTypeOrNull()
                val requestBody = payload.toString().toRequestBody(mediaType)
                Log.d(TAG, "feedback requestBody: $requestBody")

                val request = Request.Builder()
                    .url(NOTIFICATION_LAMBDA_ENDPOINT)
                    .post(requestBody)
                    .build()

                val response = client.newCall(request).execute()

                if (response.isSuccessful && response.body != null) {
                    val responseBody = response.body!!.string()
                    if (responseBody.isNotEmpty() && responseBody != "null") {
                        Log.d(TAG, "Feedback sent successfully")
                    } else {
                        Log.e(TAG, "Failed to send feedback")
                    }
                } else {
                    Log.e(TAG, "Failed to send feedback")
                }
            } catch (e: Exception) {
                Log.e("NotificationHelper", "Error sending feedback: ${e.message}", e)
            }
        }
        // endregion

        // region Worker Related
        fun scheduleContentUploadWork(context: Context, source: String, file: File?, saveFile: String?, preprocessFile: String?) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val inputData = workDataOf(
                "filePath" to file?.absolutePath,
                "fileSource" to source,
                "fileSave" to saveFile,
                "filePreprocess" to preprocessFile
            )

            val uploadWorkRequest = OneTimeWorkRequestBuilder<UploadWorker>()
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    OneTimeWorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .setConstraints(constraints)
                .setInputData(inputData)
                .build()

            val appContext = context.applicationContext
            WorkManager.getInstance(appContext).enqueue(uploadWorkRequest)
        }
        // endregion

        // region S3 Related
        fun uploadAudioFileToS3(context: Context, audioFile: File?, saveFile: String?, preprocessFile: String?) {
            Log.i("Helpers", "Uploading Audio to S3...")

            try {
                audioFile?.let {
                    // Verify the file's readability and size
                    if (!it.exists() || !it.canRead() || it.length() <= 0) {
                        Log.e(TAG, "Audio file does not exist, is unreadable or empty")
                        return
                    }

                    // Upload audio file
                    val generalSharedPrefs: SharedPreferences = context.getSharedPreferences("com.sil.mia.generalSharedPrefs", Context.MODE_PRIVATE)
                    val userName = generalSharedPrefs.getString("userName", null)
                    val audioKeyName = "data/$userName/recordings/${it.name}"

                    // Metadata
                    val metadata = ObjectMetadata()
                    metadata.contentType = "media/m4a"
                    metadata.contentLength = it.length()

                    // Add user metadata
                    metadata.addUserMetadata("savefile", saveFile)
                    metadata.addUserMetadata("preprocessfile", preprocessFile)
                    metadata.addUserMetadata("source", "audio")
                    metadata.addUserMetadata("filename", it.name)
                    metadata.addUserMetadata("filepath", it.absolutePath)

                    // Start local file upload
                    Log.d(TAG, "Starting upload of $audioKeyName to $BUCKET_NAME")
                    FileInputStream(it).use { fileInputStream ->
                        val audioRequest = PutObjectRequest(BUCKET_NAME, audioKeyName, fileInputStream, metadata)
                        try {
                            s3Client.putObject(audioRequest)
                            Log.d(TAG, "Uploaded audio to S3!")
                        }
                        catch (e: Exception) {
                            Log.e(TAG, "Error in audio S3 upload: ${e.localizedMessage}")
                        }
                    }

                    // Delete local file after upload
                    if (it.delete()) {
                        Log.d(TAG, "Deleted local audio file after upload: ${it.name}")
                    }
                    else {
                        Log.e(TAG, "Failed to delete local audio file after upload: ${it.name}")
                    }
                }
            }
            catch (e: Exception) {
                when (e) {
                    is AmazonServiceException -> Log.e(TAG, "Error uploading audio to S3: ${e.message}")
                    is FileNotFoundException -> Log.e(TAG, "Audio file not found: ${e.message}")
                    else -> Log.e(TAG, "Error in audio S3 upload: ${e.localizedMessage}")
                }
                e.printStackTrace()
            }
        }
        fun uploadImageFileToS3(context: Context, imageFile: File?, saveFile: String?, preprocessFile: String?) {
            Log.i("Helpers", "Uploading Image to S3...")

            try {
                imageFile?.let {
                    // Verify the file's readability and size
                    if (!it.exists() || !it.canRead() || it.length() <= 0) {
                        Log.e(TAG, "Image file does not exist, is unreadable or empty")
                        return
                    }

                    // Upload file
                    val generalSharedPrefs: SharedPreferences = context.getSharedPreferences("com.sil.mia.generalSharedPrefs", Context.MODE_PRIVATE)
                    val userName = generalSharedPrefs.getString("userName", null)
                    val imageKeyName = "data/$userName/screenshots/${it.name}"

                    // Metadata
                    val metadata = ObjectMetadata()
                    metadata.contentType = "media/png"
                    metadata.contentLength = it.length()

                    // Add user metadata
                    metadata.addUserMetadata("savefile", saveFile)
                    metadata.addUserMetadata("preprocessfile", preprocessFile)
                    metadata.addUserMetadata("source", "sccreenshot")
                    metadata.addUserMetadata("filename", it.name)
                    metadata.addUserMetadata("filepath", it.absolutePath)

                    // Start local file upload
                    Log.d(TAG, "Starting upload of $imageKeyName to $BUCKET_NAME")
                    FileInputStream(it).use { fileInputStream ->
                        val imageRequest = PutObjectRequest(BUCKET_NAME, imageKeyName, fileInputStream, metadata)
                        try {
                            s3Client.putObject(imageRequest)
                            Log.d(TAG, "Uploaded image to S3!")
                        }
                        catch (e: Exception) {
                            Log.e(TAG, "Error in image S3 upload: ${e.localizedMessage}")
                        }
                    }
                }
            }
            catch (e: Exception) {
                when (e) {
                    is AmazonServiceException -> Log.e(TAG, "Error uploading image to S3: ${e.message}")
                    is FileNotFoundException -> Log.e(TAG, "Image file not found: ${e.message}")
                    else -> Log.e(TAG, "Error in image S3 upload: ${e.localizedMessage}")
                }
                e.printStackTrace()
            }
        }
        fun uploadSensorFileToS3(context: Context, sensorFile: File?) {
            Log.i("Helpers", "Uploading Sensor file to S3...")

            try {
                sensorFile?.let {
                    // Verify the file's readability and size
                    if (!it.exists() || !it.canRead() || it.length() <= 0) {
                        Log.e(TAG, "Sensor file does not exist, is unreadable or empty")
                        return
                    }

                    // Upload file
                    val generalSharedPrefs: SharedPreferences = context.getSharedPreferences("com.sil.mia.generalSharedPrefs", Context.MODE_PRIVATE)
                    val userName = generalSharedPrefs.getString("userName", null)
                    val sensorDataKeyName = "data/$userName/sensor/${it.name}"

                    // Metadata
                    val metadata = ObjectMetadata()
                    metadata.contentType = "media/json"
                    metadata.contentLength = it.length()

                    // Start local file upload
                    Log.d(TAG, "Starting upload of $sensorDataKeyName to $BUCKET_NAME")
                    FileInputStream(it).use { fileInputStream ->
                        val sensorDataRequest = PutObjectRequest(BUCKET_NAME, sensorDataKeyName, fileInputStream, metadata)
                        try {
                            s3Client.putObject(sensorDataRequest)
                            Log.d(TAG, "Uploaded sensor file to S3!")
                        }
                        catch (e: Exception) {
                            Log.e(TAG, "Error in sensor file S3 upload: ${e.localizedMessage}")
                        }
                    }
                }
            }
            catch (e: Exception) {
                when (e) {
                    is AmazonServiceException -> Log.e(TAG, "Error uploading sensor file to S3: ${e.message}")
                    is FileNotFoundException -> Log.e(TAG, "Sensor file not found: ${e.message}")
                    else -> Log.e(TAG, "Error in sensor file S3 upload: ${e.localizedMessage}")
                }
                e.printStackTrace()
            }
        }
        // endregion

        // region Service Related
        fun isServiceRunning(context: Context, serviceClass: Class<*>): Boolean {
            Log.i(TAG, "isServiceRunning | Checking if ${serviceClass.simpleName} is running...")

            val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            for (service in manager.getRunningServices(Integer.MAX_VALUE)) {
                if (serviceClass.name == service.service.className) {
                    return true
                }
            }
            return false
        }
        // endregion

        // region Sensor Data Related
        fun createSensorDataFile(context: Context): File {
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val file = File(context.cacheDir, "sensor_data_$timeStamp.json")

            file.writeText(collectSensorData(context).toString())
            return file
        }
        private fun collectSensorData(context: Context): JSONObject {
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
            return data
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
        // endregion

        // region UI Related
        fun showToast(context: Context, message: String) {
            if (context is Activity) {
                context.runOnUiThread {
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }
            }
            else {
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
        }
        // endregion
    }
}
