package com.sil.mia

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.BatteryManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.*
import com.amazonaws.AmazonServiceException
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model.ObjectMetadata
import com.amazonaws.services.s3.model.PutObjectRequest
import kotlinx.coroutines.Dispatchers
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
        suspend fun callContextAPI(payload: JSONObject): String {
            return withContext(Dispatchers.IO) {
                try {
                    Log.d("Helper", "callContextAPI payload: $payload")

                    val url = URL(awsApiEndpoint)
                    val httpURLConnection = (url.openConnection() as HttpURLConnection).apply {
                        requestMethod = "POST"
                        setRequestProperty("Content-Type", "application/json")
                        doOutput = true
                        outputStream.use { it.write(payload.toString().toByteArray()) }
                    }

                    val responseCode = httpURLConnection.responseCode
                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        val responseJson = httpURLConnection.inputStream.bufferedReader().use { it.readText() }
                        val jsonResponse = JSONArray(responseJson)
                        Log.d("Helper", "callContextAPI API Response: $jsonResponse")
                        val content = jsonResponse.toString()
                        content
                    }
                    else {
                        val errorResponse = httpURLConnection.errorStream.bufferedReader().use { it.readText() }
                        Log.e("Helper", "callContextAPI Error Response: $errorResponse")
                        ""
                    }
                }
                catch (e: IOException) {
                    Log.e("Helper", "IO Exception: ${e.message}")
                    ""
                }
                catch (e: Exception) {
                    Log.e("Helper", "Exception: ${e.message}")
                    ""
                }
            }
        }
        suspend fun callOpenaiAPI(payload: JSONObject): String {
            return withContext(Dispatchers.IO) {
                try {
                    val url = URL("https://api.openai.com/v1/chat/completions")
                    val httpURLConnection = (url.openConnection() as HttpURLConnection).apply {
                        requestMethod = "POST"
                        setRequestProperty("Authorization", "Bearer ${openaiApiKey}")
                        setRequestProperty("Content-Type", "application/json")
                        doOutput = true
                        outputStream.use { it.write(payload.toString().toByteArray()) }
                    }

                    val responseCode = httpURLConnection.responseCode
                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        val responseJson = httpURLConnection.inputStream.bufferedReader().use { it.readText() }
                        val jsonResponse = JSONObject(responseJson)
                        Log.d("Helper", "callOpenaiAPI Response: $jsonResponse")
                        val content = jsonResponse.getJSONArray("choices").getJSONObject(0)
                            .getJSONObject("message").getString("content")
                        content
                    }
                    else {
                        val errorResponse = httpURLConnection.errorStream.bufferedReader().use { it.readText() }
                        Log.e("Helper", "callOpenaiAPI Error Response: $errorResponse")
                        ""
                    }
                }
                catch (e: IOException) {
                    Log.e("Helper", "IO Exception: ${e.message}")
                    ""
                }
                catch (e: Exception) {
                    Log.e("Helper", "Exception: ${e.message}")
                    ""
                }
            }
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
        class UploadWorker(context: Context, workerParams: WorkerParameters) : Worker(context, workerParams) {
            override fun doWork(): Result {
                val audioFilePath = inputData.getString("audioFile")
                val metadataJsonString = inputData.getString("metadataJson")

                if (!audioFilePath.isNullOrEmpty() && !metadataJsonString.isNullOrEmpty()) {
                    val audioFile = File(audioFilePath)
                    val metadataJson = JSONObject(metadataJsonString)

                    uploadToS3AndDelete(applicationContext, audioFile, metadataJson)

                    return Result.success()
                }

                return Result.failure()
            }
        }
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

            val appContext = context.applicationContext // Use the application context
            WorkManager.getInstance(appContext).enqueue(uploadWorkRequest)
        }
        private fun uploadToS3AndDelete(context: Context, audioFile: File?, metadataJson: JSONObject) {
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
                    val sharedPrefs: SharedPreferences = context.getSharedPreferences("com.sil.mia.deviceid", Context.MODE_PRIVATE)
                    val uniqueID = sharedPrefs.getString("deviceid", null)
                    val audioKeyName = "$uniqueID/recordings/${it.name}"

                    // Metadata
                    val audioMetadata = ObjectMetadata()
                    audioMetadata.contentType = "media/m4a"
                    audioMetadata.contentLength = it.length()

                    // Convert JSONObject to Map and add to metadata
                    val metadataMap = metadataJson.toMap()
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
        // endregion

        // region Data Related
        suspend fun pullDeviceData(context: Context): JSONObject {
            val finalOutput = JSONObject()

            // region Source
            val sharedPrefs: SharedPreferences = context.getSharedPreferences("com.sil.mia.deviceid", Context.MODE_PRIVATE)
            val uniqueID = sharedPrefs.getString("deviceid", null)
            finalOutput.apply {
                put("source", uniqueID)
            }
            // endregion
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
                put("systemTime", currentSystemTime)
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
                put("latitude", latitude)
                put("longitude", longitude)
                put("address", address)
                put("firstWeatherDescription", firstWeatherDescription)
                put("feelsLike", feelsLike)
                put("humidity", humidity)
                put("windSpeed", windSpeed)
                put("cloudAll", cloudAll)
            }
            // endregion
            // region Motion
            // TODO: Pull user's current movement (accelerometer/gyroscope)
            // endregion

            // Log.d("Helper", "pullDeviceData finalOutput: $finalOutput")
            return finalOutput
        }
        private fun pullSystemTime(): Long {
            return System.currentTimeMillis()
        }
        fun pullTimeFormattedString(): String {
            val dateFormat = SimpleDateFormat("EEE dd/MM/yy HH:mm", Locale.getDefault())
            return dateFormat.format(Date())
        }
        // endregion
    }
}
