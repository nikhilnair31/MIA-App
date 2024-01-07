package com.sil.others

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.BatteryManager
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.*
import com.amazonaws.AmazonServiceException
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model.ObjectMetadata
import com.amazonaws.services.s3.model.PutObjectRequest
import com.sil.mia.BuildConfig
import com.sil.workers.ApiCallWorker
import com.sil.workers.UploadWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

class Helpers {
    companion object {
        // region API Keys
        private const val bucketName = BuildConfig.BUCKET_NAME
        private const val awsAccessKey = BuildConfig.AWS_ACCESS_KEY
        private const val awsSecretKey = BuildConfig.AWS_SECRET_KEY
        private const val openaiApiKey = BuildConfig.OPENAI_API_KEY
        private const val awsApiEndpoint = BuildConfig.AWS_API_ENDPOINT
        private const val weatherApiEndpoint = BuildConfig.WEATHER_API_KEY
        private const val locationApiEndpoint = BuildConfig.GEOLOCATION_API_KEY
        // endregion

        // region API Call Related
        fun scheduleApiCallWork(context: Context, payload: JSONObject) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val inputData = workDataOf(
                "payloadJson" to payload.toString()
            )

            val apiCallWorkRequest = OneTimeWorkRequestBuilder<ApiCallWorker>()
                .setConstraints(constraints)
                .setInputData(inputData)
                .build()

            val appContext = context.applicationContext
            WorkManager.getInstance(appContext).enqueue(apiCallWorkRequest)
        }
        suspend fun callContextAPI(payload: JSONObject): String {
            var lastException: IOException? = null

            repeat(3) { attempt ->
                try {
                    val url = URL(awsApiEndpoint)
                    val httpURLConnection = (url.openConnection() as HttpURLConnection).apply {
                        connectTimeout = 15000
                        readTimeout = 15000
                        requestMethod = "POST"
                        setRequestProperty("Content-Type", "application/json")
                        doOutput = true
                        outputStream.use { it.write(payload.toString().toByteArray()) }
                    }

                    val responseCode = httpURLConnection.responseCode
                    return if (responseCode == HttpURLConnection.HTTP_OK) {
                        val responseJson = httpURLConnection.inputStream.bufferedReader().use { it.readText() }
                        val jsonResponse = JSONArray(responseJson)
                        Log.d("Helper", "callContextAPI Response: $jsonResponse")
                        val content = jsonResponse.toString()
                        content
                    } else {
                        val errorResponse = httpURLConnection.errorStream.bufferedReader().use { it.readText() }
                        Log.e("Helper", "callContextAPI Error Response: $errorResponse")
                        ""
                    }
                } catch (e: IOException) {
                    val message = e.message ?: "Unknown IO exception"
                    Log.e("Helper", "callContextAPI IO Exception on attempt $attempt: $message", e)
                    if (e is java.net.SocketException) {
                        Log.e("Helper", "SocketException details: ", e)
                    }
                    lastException = e
                    // Delay before retrying
                    delay(1000L * (attempt + 1)) // Exponential back-off
                } catch (e: Exception) {
                    Log.e("Helper", "callContextAPI Unexpected Exception: ${e.message}")
                    return ""
                }
            }
            lastException?.let {
                throw it // Re-throw the last IO exception if all retries fail
            }
            return ""
        }
        suspend fun callOpenaiAPI(payload: JSONObject): String {
            var lastException: IOException? = null

            repeat(3) { attempt ->
                try {
                    val url = URL("https://api.openai.com/v1/chat/completions")
                    val httpURLConnection = (url.openConnection() as HttpURLConnection).apply {
                        requestMethod = "POST"
                        setRequestProperty("Authorization", "Bearer $openaiApiKey")
                        setRequestProperty("Content-Type", "application/json")
                        doOutput = true
                        outputStream.use { it.write(payload.toString().toByteArray()) }
                    }

                    val responseCode = httpURLConnection.responseCode
                    return if (responseCode == HttpURLConnection.HTTP_OK) {
                        val responseJson = httpURLConnection.inputStream.bufferedReader().use { it.readText() }
                        val jsonResponse = JSONObject(responseJson)
                        Log.d("Helper", "callOpenaiAPI Response: $jsonResponse")
                        val content = jsonResponse.getJSONArray("choices").getJSONObject(0)
                            .getJSONObject("message").getString("content")
                        content
                    } else {
                        val errorResponse = httpURLConnection.errorStream.bufferedReader().use { it.readText() }
                        Log.e("Helper", "callOpenaiAPI Error Response: $errorResponse")
                        ""
                    }
                } catch (e: IOException) {
                    Log.e("Helper", "callOpenaiAPI IO Exception on attempt $attempt: ${e.message}")
                    lastException = e
                    // Delay before retrying
                    delay(1000L * (attempt + 1)) // Exponential back-off
                } catch (e: Exception) {
                    Log.e("Helper", "callOpenaiAPI Unexpected Exception: $e")
                    return ""
                }
            }
            lastException?.let {
                throw it // Re-throw the last IO exception if all retries fail
            }
            return ""
        }
        private suspend fun callGeocodingAPI(latitude: Double, longitude: Double): String {
            return withContext(Dispatchers.IO) {
                try {
                    // Construct the URL with latitude and longitude
                    val urlString = "https://geocode.maps.co/reverse?lat=$latitude&lon=$longitude&api_key=$locationApiEndpoint"
                    val url = URL(urlString)

                    // Open the connection
                    val httpURLConnection = (url.openConnection() as HttpURLConnection).apply {
                        requestMethod = "GET" // This is a GET request
                    }

                    // Handle the response
                    val responseCode = httpURLConnection.responseCode
                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        val responseJson = httpURLConnection.inputStream.bufferedReader().use { it.readText() }
                        val jsonResponse = JSONObject(responseJson)
                        // Log.d("Helper", "callGeocodingAPI Response: $jsonResponse")
                        val content = jsonResponse.getString("display_name")
                        content
                    } else {
                        val errorResponse = httpURLConnection.errorStream.bufferedReader().use { it.readText() }
                        Log.e("Helper", "callGeocodingAPI Error Response: $errorResponse")
                        ""
                    }
                } catch (e: IOException) {
                    Log.e("Helper", "IO Exception: ${e.message}")
                    ""
                } catch (e: Exception) {
                    Log.e("Helper", "Exception: ${e.message}")
                    ""
                }
            }
        }
        private suspend fun callWeatherAPI(latitude: Double, longitude: Double): JSONObject? {
            return withContext(Dispatchers.IO) {
                try {
                    // Construct the URL with latitude and longitude
                    val urlString = "https://api.openweathermap.org/data/2.5/weather?lat=$latitude&lon=$longitude&units=metric&appid=$weatherApiEndpoint"
                    val url = URL(urlString)

                    // Open the connection
                    val httpURLConnection = (url.openConnection() as HttpURLConnection).apply {
                        requestMethod = "GET" // This is a GET request
                    }

                    // Handle the response
                    val responseCode = httpURLConnection.responseCode
                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        val responseJson = httpURLConnection.inputStream.bufferedReader().use { it.readText() }
                        val jsonResponse = JSONObject(responseJson)
                        // Log.d("Helper", "callWeatherAPI Response: $jsonResponse")
                        jsonResponse
                    } else {
                        val errorResponse = httpURLConnection.errorStream.bufferedReader().use { it.readText() }
                        Log.e("Helper", "callWeatherAPI Error Response: $errorResponse")
                        null
                    }
                } catch (e: IOException) {
                    Log.e("Helper", "IO Exception: ${e.message}")
                    null
                } catch (e: Exception) {
                    Log.e("Helper", "Exception: ${e.message}")
                    null
                }
            }
        }
        // endregion

        // region Cloud Related
        fun scheduleUploadWork(context: Context, audioFile: File?, metadataJson: JSONObject) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val inputData = workDataOf(
                "audioFile" to audioFile?.absolutePath,
                "metadataJson" to metadataJson.toString()
            )

            val uploadWorkRequest = OneTimeWorkRequestBuilder<UploadWorker>()
                .setConstraints(constraints)
                .setInputData(inputData)
                .build()

            val appContext = context.applicationContext
            WorkManager.getInstance(appContext).enqueue(uploadWorkRequest)
        }
        fun uploadToS3AndDelete(context: Context, audioFile: File?, metadataJson: JSONObject) {
            Log.i("Helpers", "Uploading to S3...")

            try {
                val credentials = BasicAWSCredentials(awsAccessKey, awsSecretKey)
                val s3Client = AmazonS3Client(credentials)

                audioFile?.let {
                    // Verify the file's readability and size
                    if (!it.exists() || !it.canRead() || it.length() <= 0) {
                        Log.e("Helper", "File does not exist, is unreadable or empty")
                        return
                    }

                    // Upload audio file
                    val sharedPrefs: SharedPreferences = context.getSharedPreferences("com.sil.mia.generalSharedPrefs", Context.MODE_PRIVATE)
                    val userName = sharedPrefs.getString("userName", null)
                    val audioKeyName = "$userName/recordings/${it.name}"

                    // Metadata
                    val audioMetadata = ObjectMetadata()
                    audioMetadata.contentType = "media/m4a"
                    audioMetadata.contentLength = it.length()

                    // Convert JSONObject to Map and add to metadata
                    val metadataMap = metadataJson.toMap()

                    // TODO: Add extra variables defining each variable's type
                    val metadataSize = calculateMetadataSize(metadataMap)
                    Log.i("Helper", "User-defined metadata size: $metadataSize")

                    metadataMap.forEach { (key, value) ->
                        // Log.d("Helper", "Metadata - Key: $key, Value: $value")
                        audioMetadata.addUserMetadata(key, value)
                    }

                    // Start upload
                    FileInputStream(it).use { fileInputStream ->
                        val audioRequest = PutObjectRequest(bucketName, audioKeyName, fileInputStream, audioMetadata)
                        try {
                            s3Client.putObject(audioRequest)
                            Log.i("Helper", "Uploaded to S3!")
                        }
                        catch (e: Exception) {
                            Log.e("Helper", "Error in S3 upload: ${e.localizedMessage}")
                        }
                    }

                    // Delete the file after upload
                    if (it.delete()) {
                        Log.i("Helper", "Deleted audio file after upload: ${it.name}")
                    } else {
                        Log.e("Helper", "Failed to delete audio file after upload: ${it.name}")
                    }
                }
            }
            catch (e: Exception) {
                when (e) {
                    is AmazonServiceException -> Log.e("Helper", "Error uploading to S3: ${e.message}")
                    is FileNotFoundException -> Log.e("Helper", "File not found: ${e.message}")
                    else -> Log.e("Helper", "Error in S3 upload: ${e.localizedMessage}")
                }
                e.printStackTrace()
            }
        }
        private fun JSONObject.toMap(): Map<String, String> = keys().asSequence().associateWith { getString(it) }
        private fun calculateMetadataSize(metadataMap: Map<String, Any>): Int {
            // Calculate the UTF-8 encoded size of each key and value
            val size = metadataMap.entries.sumOf { entry ->
                entry.key.toByteArray(Charsets.UTF_8).size + entry.value.toString().toByteArray(Charsets.UTF_8).size
            }
            return size
        }
        // endregion

        // region Data Related
        suspend fun pullDeviceData(context: Context, sensorHelper: SensorHelper?): JSONObject {
            val finalOutput = JSONObject()

            // region Time
            // Pulling system time in ms
            val currentSystemTime = pullSystemTime()
            // Pulling formatted time string
            val currentTimeFormattedString = pullTimeFormattedString()
            // Creating a Calendar instance
            val calendar = Calendar.getInstance().apply {
                timeInMillis = currentSystemTime
            }
            // Extracting day, month, year, hours, and minutes
            val day = calendar.get(Calendar.DAY_OF_MONTH)
            val month = calendar.get(Calendar.MONTH) + 1
            val year = calendar.get(Calendar.YEAR)
            val hours = calendar.get(Calendar.HOUR_OF_DAY)
            val minutes = calendar.get(Calendar.MINUTE)
            // Adding values to finalOutput
            finalOutput.apply {
                put("currentTimeFormattedString", currentTimeFormattedString)
                put("day", day)
                put("month", month)
                put("year", year)
                put("hours", hours)
                put("minutes", minutes)
            }
            // endregion
            // region Battery
            // Pulling current battery level
            val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            val batteryLevel = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            finalOutput.apply {
                put("batteryLevel", batteryLevel)
            }
            // endregion
            // region Location & Climate
            // Pulling user's current latitude/longitude
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val location: Location?
            var address = ""
            var latitude: Double? = null
            var longitude: Double? = null
            var firstWeatherDescription = ""
            var feelsLike = ""
            var humidity = ""
            var windSpeed = ""
            var cloudAll = ""
            // Check for location permission
            if (
                ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                ||
                ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            ) {
                location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                // Log.v("Helper", "pullDeviceData location: $location")
                location?.let {
                    // Log.v("Helper", "pullDeviceData it.latitude: ${it.latitude} it.longitude: ${it.longitude}")
                    latitude = it.latitude
                    longitude = it.longitude
                    address = callGeocodingAPI(latitude!!, longitude!!)

                    // region Climate
                    // Pull user's current location's temperature and humidity
                    val weatherJSON = callWeatherAPI(latitude!!, longitude!!)
                    if (weatherJSON != null) {
                        val weatherArray = weatherJSON.getJSONArray("weather")
                        firstWeatherDescription = weatherArray.getJSONObject(0).getString("description")

                        val mainObject = weatherJSON.getJSONObject("main")
                        feelsLike = mainObject.getDouble("feels_like").toString()
                        humidity = mainObject.getInt("humidity").toString()

                        val windObject = weatherJSON.getJSONObject("wind")
                        windSpeed = windObject.getDouble("speed").toString()

                        val cloudsObject = weatherJSON.getJSONObject("clouds")
                        cloudAll = cloudsObject.getInt("all").toString()
                    }
                    // endregion
                }
            }
            finalOutput.apply {
                // put("latitude", latitude)
                // put("longitude", longitude)
                put("address", address)
                put("firstWeatherDescription", firstWeatherDescription)
                put("feelsLike", feelsLike)
                put("humidity", humidity)
                put("windSpeed", windSpeed)
                put("cloudAll", cloudAll)
            }
            // endregion
            // region Motion
            val deviceSpeed = sensorHelper?.getDeviceStatus() ?: 0f
            Log.i("Helper", "deviceSpeed: $deviceSpeed")
            val deviceStatus = when {
                deviceSpeed < 10 -> "idle"
                deviceSpeed >= 10 && deviceSpeed < 150 -> "moving normal"
                deviceSpeed >= 150 && deviceSpeed < 500 -> "moving fast"
                else -> "unknown"
            }
            finalOutput.apply {
                put("movementStatus", deviceStatus)
            }
            // endregion

            // Log.d("Helper", "pullDeviceData finalOutput: $finalOutput")
            return finalOutput
        }
        private fun pullSystemTime(): Long {
            return System.currentTimeMillis()
        }
        private fun pullTimeFormattedString(): String {
            val dateFormat = SimpleDateFormat("EEE dd/MM/yy HH:mm", Locale.getDefault())
            return dateFormat.format(Date())
        }
        fun messageDataWindow(fullJsonString: String?, maxMessages: Int?): JSONArray {
            // Log.i("Helpers", "messageDataWindow")

            val fullJsonArray = JSONArray(fullJsonString)
            // Log.i("Helpers", "fullJsonArray\n$fullJsonArray")

            // Filter out system JSON objects
            val nonSystemJsonArray = JSONArray()
            for (i in 0 until fullJsonArray.length()) {
                val jsonObject = fullJsonArray.getJSONObject(i)
                if (jsonObject.getString("role") != "system") {
                    nonSystemJsonArray.put(jsonObject)
                }
            }
            // Log.i("Helpers", "system-free fullJsonArray\n$nonSystemJsonArray")

            // Return full JSON array if maxMessages is null
            if (maxMessages == null) {
                // Log.i("Helpers", "Returning full JSON array")
                return nonSystemJsonArray
            }

            // Return last maxMessages JSON objects otherwise
            val lastNObjects = JSONArray()
            val startIndex = maxOf(nonSystemJsonArray.length() - maxMessages, 0)
            for (i in startIndex until nonSystemJsonArray.length()) {
                lastNObjects.put(nonSystemJsonArray.getJSONObject(i))
            }
            // Log.i("Helpers", "lastNObjects\n$lastNObjects")

            return lastNObjects
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
