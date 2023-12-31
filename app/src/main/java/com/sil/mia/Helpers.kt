package com.sil.mia

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.BatteryManager
import android.util.Log
import android.util.Log.WARN
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.getSystemService
import androidx.recyclerview.widget.RecyclerView
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
                    Log.d("AudioRecord", "callContextAPI payload: $payload")

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
                        val jsonResponse = JSONObject(responseJson)
                        Log.d("AudioRecord", "callContextAPI API Response: $jsonResponse")
                        val content = jsonResponse.getString("output")
                        content
                    }
                    else {
                        val errorResponse = httpURLConnection.errorStream.bufferedReader().use { it.readText() }
                        Log.e("AudioRecord", "callContextAPI Error Response: $errorResponse")
                        ""
                    }
                }
                catch (e: IOException) {
                    Log.e("AudioRecord", "IO Exception: ${e.message}")
                    ""
                }
                catch (e: Exception) {
                    Log.e("AudioRecord", "Exception: ${e.message}")
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
                        Log.d("AudioRecord", "callOpenaiAPI Response: $jsonResponse")
                        val content = jsonResponse.getJSONArray("choices").getJSONObject(0)
                            .getJSONObject("message").getString("content")
                        content
                    }
                    else {
                        val errorResponse = httpURLConnection.errorStream.bufferedReader().use { it.readText() }
                        Log.e("AudioRecord", "callOpenaiAPI Error Response: $errorResponse")
                        ""
                    }
                }
                catch (e: IOException) {
                    Log.e("AudioRecord", "IO Exception: ${e.message}")
                    ""
                }
                catch (e: Exception) {
                    Log.e("AudioRecord", "Exception: ${e.message}")
                    ""
                }
            }
        }
        suspend fun callGeocodingAPI(latitude: Double, longitude: Double): String {
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
                        Log.d("AudioRecord", "callGeocodingAPI Response: $jsonResponse")
                        val content = jsonResponse.getString("display_name")
                        content
                    } else {
                        val errorResponse = httpURLConnection.errorStream.bufferedReader().use { it.readText() }
                        Log.e("AudioRecord", "callGeocodingAPI Error Response: $errorResponse")
                        ""
                    }
                } catch (e: IOException) {
                    Log.e("AudioRecord", "IO Exception: ${e.message}")
                    ""
                } catch (e: Exception) {
                    Log.e("AudioRecord", "Exception: ${e.message}")
                    ""
                }
            }
        }
        suspend fun callWeatherAPI(latitude: Double, longitude: Double): JSONObject? {
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
                        Log.d("AudioRecord", "callWeatherAPI Response: $jsonResponse")
                        jsonResponse
                    } else {
                        val errorResponse = httpURLConnection.errorStream.bufferedReader().use { it.readText() }
                        Log.e("AudioRecord", "callWeatherAPI Error Response: $errorResponse")
                        null
                    }
                } catch (e: IOException) {
                    Log.e("AudioRecord", "IO Exception: ${e.message}")
                    null
                } catch (e: Exception) {
                    Log.e("AudioRecord", "Exception: ${e.message}")
                    null
                }
            }
        }
        // endregion

        // region Cloud Related
        fun uploadToS3(audioFile: File?, metadataJson: JSONObject) {
            Log.i("AudioRecord", "Uploading to S3...")

            try {
                val credentials = BasicAWSCredentials(awsAccessKey, awsSecretKey)
                val s3Client = AmazonS3Client(credentials)

                audioFile?.let {
                    // Verify the file's readability and size
                    if (!it.exists() || !it.canRead() || it.length() <= 0) {
                        Log.e("AudioRecord", "File does not exist, is unreadable or empty")
                        return
                    }

                    // Upload audio file
                    val audioKeyName = "recordings/" + it.name
                    val audioMetadata = ObjectMetadata()
                    audioMetadata.contentType = "media/m4a"
                    audioMetadata.contentLength = it.length()

                    // Convert JSONObject to Map and add to metadata
                    val metadataMap = metadataJson.toMap()
                    metadataMap.forEach { (key, value) ->
                        Log.d("AudioRecord", "Metadata - Key: $key, Value: $value")
                        audioMetadata.addUserMetadata(key, value)
                    }

                    FileInputStream(it).use { fileInputStream ->
                        val audioRequest = PutObjectRequest(bucketName, audioKeyName, fileInputStream, audioMetadata)
                        try {
                            s3Client.putObject(audioRequest)
                            Log.i("AudioRecord", "Uploaded to S3!")
                        }
                        catch (e: Exception) {
                            Log.e("AudioRecord", "Error in S3 upload: ${e.localizedMessage}")
                        }
                    }
                }
            }
            catch (e: AmazonServiceException) {
                e.printStackTrace()
                Log.e("AudioRecord", "Error uploading to S3: ${e.message}")
            }
        }
        private fun JSONObject.toMap(): Map<String, String> = keys().asSequence().associateWith { getString(it) }
        // endregion

        // region Data Related
        suspend fun pullDeviceData(context: Context): JSONObject {
            val finalOutput = JSONObject()

            // region Time
            // Pulling system time in ms
            val currentSystemTime = System.currentTimeMillis()
            finalOutput.apply {
                put("systemTime", currentSystemTime)
            }
            // Pulling formatted time string
            val currentTimeFormattedString = pullTimeFormattedString()
            finalOutput.apply {
                put("currentTimeFormattedString", currentTimeFormattedString)
            }
            // endregion
            // region Battery
            // Pulling current battery level
            val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            val batteryLevel = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            finalOutput.apply {
                put("batteryLevel", batteryLevel)
            }
            // Pulling battery charging status
            // endregion
            // region Location
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
                Log.v("AudioRecord", "pullDeviceData location: $location")
                location?.let {
                    Log.v("AudioRecord", "pullDeviceData it.latitude: ${it.latitude} it.longitude: ${it.longitude}")
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
            // Pull user's current movement (accelerometer/gyroscope)
            // endregion

            Log.d("AudioRecord", "pullDeviceData finalOutput: $finalOutput")
            return finalOutput
        }
        fun pullTimeFormattedString(): String {
            val dateFormat = SimpleDateFormat("EEE dd/MM/yy HH:mm", Locale.getDefault())
            return dateFormat.format(Date())
        }
        fun removeKeyFromJsonList(jsonList: MutableList<JSONObject>): JSONArray {
            jsonList.forEach { jsonObject ->
                jsonObject.remove("time")
                jsonObject.remove("metadata")
            }
            return JSONArray(jsonList)
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

        // region Audio Related
        fun rms(buffer: ShortArray, length: Int): Double {
            var sum = 0.0
            for (i in 0 until length) {
                sum += buffer[i] * buffer[i]
            }
            return kotlin.math.sqrt(sum / length) * 1000
        }
        // endregion
    }
}
