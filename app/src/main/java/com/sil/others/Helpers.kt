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
import androidx.work.*
import com.amazonaws.AmazonServiceException
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model.GetObjectRequest
import com.amazonaws.services.s3.model.ObjectMetadata
import com.amazonaws.services.s3.model.PutObjectRequest
import com.sil.mia.R
import com.sil.mia.BuildConfig
import com.sil.workers.UploadWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

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
        private const val pineconeApiEndpoint = BuildConfig.PINECONE_API_ENDPOINT
        private const val pineconeApiKey = BuildConfig.PINECONE_API_KEY
        // endregion

        // region Init Related
        private val credentials = BasicAWSCredentials(awsAccessKey, awsSecretKey)
        private val s3Client = AmazonS3Client(credentials)
        // endregion
        
        // region APIs Related
        suspend fun callContextAPI(payload: JSONObject): String {
            var lastException: IOException? = null

            repeat(3) { attempt ->
                try {
                    val client = OkHttpClient.Builder()
                        .connectTimeout(15000, TimeUnit.MILLISECONDS)
                        .readTimeout(15000, TimeUnit.MILLISECONDS)
                        .build()

                    val url = awsApiEndpoint
                    val request = Request.Builder()
                        .url(url)
                        .post(payload.toString().toRequestBody("application/json".toMediaTypeOrNull()))
                        .addHeader("Content-Type", "application/json")
                        .build()

                    val response = client.newCall(request).execute()
                    val responseCode = response.code

                    return if (responseCode == HttpURLConnection.HTTP_OK) {
                        val responseJson = response.body.string()
                        val jsonResponse = JSONArray(responseJson)
                        Log.d("Helper", "callContextAPI Response: $jsonResponse")
                        val content = jsonResponse.toString()
                        content
                    } else {
                        Log.e("Helper", "callContextAPI Error Response: ${response.body.string()}")
                        ""
                    }
                } catch (e: IOException) {
                    val message = e.message ?: "Unknown IO exception"
                    Log.e("Helper", "callContextAPI IO Exception on attempt $attempt: $message", e)
                    if (e is java.net.SocketException) {
                        Log.e("Helper", "SocketException details: ", e)
                    }
                    lastException = e
                    delay(1000L * (attempt + 1))
                } catch (e: Exception) {
                    Log.e("Helper", "callContextAPI Unexpected Exception: ${e.message}")
                    return ""
                }
            }
            lastException?.let {
                throw it
            }
            return ""
        }
        suspend fun callOpenaiAPI(payload: JSONObject): String {
            var lastException: IOException? = null

            repeat(3) { attempt ->
                try {
                    val client = OkHttpClient()
                    val url = "https://api.openai.com/v1/chat/completions"
                    val request = Request.Builder()
                        .url(url)
                        .post(payload.toString().toRequestBody("application/json".toMediaTypeOrNull()))
                        .addHeader("Authorization", "Bearer $openaiApiKey")
                        .build()

                    val response = client.newCall(request).execute()
                    val responseCode = response.code

                    return if (responseCode == HttpURLConnection.HTTP_OK) {
                        val jsonResponse = JSONObject(response.body.string())
                        Log.d("Helper", "callOpenaiAPI Response: $jsonResponse")
                        val content = jsonResponse.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
                        content
                    } else {
                        Log.e("Helper", "callOpenaiAPI Error Response: ${response.body.string()}")
                        ""
                    }
                } catch (e: IOException) {
                    Log.e("Helper", "callOpenaiAPI IO Exception on attempt $attempt: ${e.message}")
                    lastException = e
                    delay(1000L * (attempt + 1))
                } catch (e: Exception) {
                    Log.e("Helper", "callOpenaiAPI Unexpected Exception: $e")
                    return ""
                }
            }
            lastException?.let {
                throw it
            }
            return ""
        }
        private suspend fun callGeocodingAPI(context: Context, latitude: Double, longitude: Double): String = withContext(Dispatchers.IO) {
            try {
                val client = OkHttpClient()
                val url = context.getString(R.string.addressURL)
                val params = mapOf("lat" to latitude, "lon" to longitude, "api_key" to locationApiEndpoint)

                val request = Request.Builder()
                    .url("$url?${params.entries.joinToString("&") { "${it.key}=${it.value}" }}")
                    .get()
                    .build()

                val response = client.newCall(request).execute()

                if (response.isSuccessful) {
                    val jsonResponse = JSONObject(response.body.string())
                    // Log.d("Helper", "callGeocodingAPI Response\n$jsonResponse")
                    val content = jsonResponse.getString("display_name")
                    content
                } else {
                    Log.e("Helper", "callGeocodingAPI Error Response\n${response.body.string()}")
                    ""
                }
            } catch (e: Exception) {
                Log.e("Helper", "callGeocodingAPI Exception\n$e")
                ""
            }
        }
        private suspend fun callWeatherAPI(context: Context, latitude: Double, longitude: Double): JSONObject? = withContext(Dispatchers.IO) {
            try {
                val client = OkHttpClient()
                val url = context.getString(R.string.weatherURL)
                val params = mapOf("lat" to latitude, "lon" to longitude, "units" to "metric", "appid" to weatherApiEndpoint)

                val request = Request.Builder()
                    .url("$url?${params.entries.joinToString("&") { "${it.key}=${it.value}" }}")
                    .get()
                    .build()

                val response = client.newCall(request).execute()

                if (response.isSuccessful) {
                    val jsonResponse = JSONObject(response.body.string())
                    // Log.d("Helper", "callWeatherAPI Response\n$jsonResponse")
                    jsonResponse
                } else {
                    Log.e("Helper", "callWeatherAPI Error Response\n${response.body.string()}")
                    null
                }
            } catch (e: Exception) {
                Log.e("Helper", "callWeatherAPI Exception\n$e")
                null
            }
        }
        // endregion

        // region Pinecone Related
        suspend fun fetchPineconeVectorMetadata(onComplete: (success: Boolean, responseJsonObject: JSONObject) -> Unit): JSONObject {
            Log.i("Helpers", "Fetching from Pinecone...")

            var lastException: IOException? = null

            repeat(3) { attempt ->
                try {
                    val client = OkHttpClient()

                    val vectorArray = FloatArray(1536)
                    val vectorArrayJson = JSONArray().apply {
                        for (value in vectorArray) {
                            put(value)
                        }
                    }

                    val mediaType = "application/json".toMediaTypeOrNull()
                    val body = JSONObject().apply {
                        // FIXME: Update this to use the generated filters (or not)
                        // put("filter", JSONObject().apply {
                        //     put("year", 2024)
                        //     put("day", 13)
                        // })
                        put("includeValues", false)
                        put("includeMetadata", true)
                        put("topK", 50)
                        put("vector", vectorArrayJson)
                    }.toString().toRequestBody(mediaType)

                    val request = Request.Builder()
                        .url("https://mia-170756d.svc.gcp-starter.pinecone.io/query")
                        .post(body)
                        .addHeader("accept", "application/json")
                        .addHeader("content-type", "application/json")
                        .addHeader("Api-Key", pineconeApiKey)
                        .build()

                    val response = client.newCall(request).execute()
                    val responseCode = response.code

                    return if (responseCode == HttpURLConnection.HTTP_OK) {
                        val responseBody = response.body.string()
                        val bodyJsonArray = JSONObject(responseBody)
                        onComplete.invoke(true, bodyJsonArray)
                        bodyJsonArray
                    } else {
                        Log.e("Helper", "Pinecone API Error Response: ${response.body.string()}")
                        JSONObject()
                    }
                }
                catch (e: IOException) {
                    val message = e.message ?: "Unknown IO exception"
                    Log.e("Helper", "Pinecone API IO Exception on attempt $attempt: $message", e)
                    lastException = e
                    delay(1000L * (attempt + 1))
                }
                catch (e: Exception) {
                    Log.e("Helper", "Pinecone API Unexpected Exception: $e")
                    onComplete.invoke(false, JSONObject())
                    return JSONObject()
                }
            }
            lastException?.let {
                throw it
            }
            return JSONObject()
        }
        fun deletePineconeVectorById(context: Context, vectorId: String) {
            Log.i("Helpers", "Deleting from Pinecone...")

            Thread {
                var connection: HttpURLConnection? = null
                try {
                    val url = URL("$pineconeApiEndpoint/vectors/delete")
                    Log.d("Helper", "deleteVectorById url: $url")

                    connection = url.openConnection() as HttpURLConnection
                    connection.apply {
                        requestMethod = "POST"
                        doOutput = true
                        setRequestProperty("Content-Type", "application/json")
                        setRequestProperty("Accept", "application/json")
                        setRequestProperty("Api-Key", pineconeApiKey)

                        // Create JSON payload
                        val jsonPayload = JSONObject().apply {
                            put("deleteAll", false)
                            put("ids", JSONArray().put(vectorId))
                        }
                        Log.d("Helper", "deleteVectorById jsonPayload: $jsonPayload")

                        // Write JSON payload to body
                        OutputStreamWriter(outputStream).use { writer ->
                            writer.write(jsonPayload.toString())
                            writer.flush()
                        }

                        connect()

                        if (responseCode == HttpURLConnection.HTTP_OK) {
                            showToast(context, "Pinecone Vector Delete: Successful!")
                            Log.d("Helper", "Pinecone API Vector Delete: Successful!")
                        } else {
                            showToast(context, "Pinecone Vector Delete: Error :(")
                            Log.e("Helper", "Pinecone API Vector Delete Error: $responseCode")
                        }
                    }
                } catch (e: Exception) {
                    showToast(context, "Pinecone Vector Delete: Error :(")
                    Log.e("Helper", "Pinecone API Vector Delete Error: Error making Pinecone request\n", e)
                    e.printStackTrace()
                } finally {
                    connection?.disconnect()
                }
            }.start()
        }
        fun updatePineconeVectorById(context: Context, vectorId: String, addressText: String, weatherText: String, sourceText: String, textText: String) {
            Log.i("Helpers", "Updating Pinecone Vector...")

            Thread {
                try {
                    val client = OkHttpClient()

                    val url = URL("$pineconeApiEndpoint/vectors/update")
                    val mediaType = "application/json".toMediaTypeOrNull()
                    val body = JSONObject().apply {
                        put("setMetadata", JSONObject().apply {
                            put("address", addressText)
                            put("firstweatherdescription", weatherText)
                            put("source", sourceText)
                            put("text", textText)
                        })
                        put("id", vectorId)
                    }.toString().toRequestBody(mediaType)

                    val request = Request.Builder()
                        .url(url)
                        .post(body)
                        .addHeader("accept", "application/json")
                        .addHeader("content-type", "application/json")
                        .addHeader("Api-Key", pineconeApiKey)
                        .build()

                    val response = client.newCall(request).execute()

                    if (response.isSuccessful) {
                        showToast(context, "Pinecone Vector Update: Successful!")
                        Log.d("Helpers", "Pinecone Vector Update: Successful!")
                    } else {
                        showToast(context, "Pinecone Vector Update Error :(")
                        Log.e("Helpers", "Pinecone Vector Update Error: $response")
                    }
                } catch (e: Exception) {
                    showToast(context, "Pinecone Vector Update Error :(")
                    Log.e("Helpers", "Pinecone Vector Update Error: $e")
                    e.printStackTrace()
                }
            }.start()
        }
        // endregion

        // region S3 Related
        fun downloadFromS3(context: Context, fileName: String, destinationFile: File, onComplete: (success: Boolean) -> Unit) {
            Log.i("Helpers", "Downloading from S3...")

            Thread {
                try {
                    val generalSharedPrefs: SharedPreferences = context.getSharedPreferences("com.sil.mia.generalSharedPrefs", Context.MODE_PRIVATE)
                    val userName = generalSharedPrefs.getString("userName", null)
                    val dataKeyName = "$userName/recordings/$fileName"
                    Log.d("Helper", "downloadFromS3 dataKeyName: $dataKeyName")

                    // Create GetObjectRequest
                    val getObjectRequest = GetObjectRequest(bucketName, dataKeyName)

                    // Download the S3 object
                    val s3Object = s3Client.getObject(getObjectRequest)
                    val inputStream = s3Object.objectContent

                    // Write the S3 object content to a local file
                    val outputStream = FileOutputStream(destinationFile)

                    inputStream.use { input ->
                        outputStream.use { output ->
                            input.copyTo(output)
                        }
                        onComplete.invoke(true)
                        Log.d("Helper", "S3 download complete!")
                    }

                    // Close the streams
                    inputStream.close()
                    outputStream.close()
                }
                catch (e: Exception) {
                    when (e) {
                        is AmazonServiceException -> Log.e("Helper", "Error uploading to S3: ${e.message}")
                        is FileNotFoundException -> Log.e("Helper", "File not found: ${e.message}")
                        else -> Log.e("Helper", "Error in S3 upload: ${e.localizedMessage}")
                    }
                    onComplete.invoke(false)
                    e.printStackTrace()
                }
            }.start()
        }
        fun uploadToS3AndDelete(context: Context, audioFile: File?, metadataJson: JSONObject) {
            Log.i("Helpers", "Uploading to S3...")

            try {
                audioFile?.let {
                    // Verify the file's readability and size
                    if (!it.exists() || !it.canRead() || it.length() <= 0) {
                        Log.e("Helper", "File does not exist, is unreadable or empty")
                        return
                    }

                    // Upload audio file
                    val generalSharedPrefs: SharedPreferences = context.getSharedPreferences("com.sil.mia.generalSharedPrefs", Context.MODE_PRIVATE)
                    val userName = generalSharedPrefs.getString("userName", null)
                    val audioKeyName = "$userName/recordings/${it.name}"

                    // Metadata
                    val audioMetadata = ObjectMetadata()
                    audioMetadata.contentType = "media/m4a"
                    audioMetadata.contentLength = it.length()

                    // Convert JSONObject to Map and add to metadata
                    val metadataMap = metadataJson.toMap()

                    // TODO: Add extra variables defining each variable's type
                    val metadataSize = calculateMetadataSize(metadataMap)
                    Log.d("Helper", "User-defined metadata size: $metadataSize")

                    metadataMap.forEach { (key, value) ->
                        // Log.d("Helper", "Metadata - Key: $key, Value: $value")
                        audioMetadata.addUserMetadata(key, value)
                    }

                    // Start upload
                    FileInputStream(it).use { fileInputStream ->
                        val audioRequest = PutObjectRequest(bucketName, audioKeyName, fileInputStream, audioMetadata)
                        try {
                            s3Client.putObject(audioRequest)
                            Log.d("Helper", "Uploaded to S3!")
                        }
                        catch (e: Exception) {
                            Log.e("Helper", "Error in S3 upload: ${e.localizedMessage}")
                        }
                    }

                    // Delete the file after upload
                    if (it.delete()) {
                        Log.d("Helper", "Deleted audio file after upload: ${it.name}")
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
        // endregion

        // region Data Related
        suspend fun pullDeviceData(context: Context, sensorHelper: SensorHelper?): JSONObject {
            val finalOutput = JSONObject()

            // region Time
            val calendar = Calendar.getInstance().apply {
                timeInMillis = pullSystemTime()
            }
            finalOutput.apply {
                put("currentTimeFormattedString", pullTimeFormattedString())
                put("day", calendar.get(Calendar.DAY_OF_MONTH))
                put("month", calendar.get(Calendar.MONTH) + 1)
                put("year", calendar.get(Calendar.YEAR))
                put("hours", calendar.get(Calendar.HOUR_OF_DAY))
                put("minutes", calendar.get(Calendar.MINUTE))
            }
            // endregion
            // region Battery
            // Pulling current battery level
            val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            finalOutput.put("batteryLevel", batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY))
            // endregion
            // region Location & Climate
            if (hasLocationPermission(context)) {
                val location = getLastKnownLocation(context)
                location?.let { locIt ->
                    val (latitude, longitude) = Pair(locIt.latitude, locIt.longitude)
                    val address = callGeocodingAPI(context, latitude, longitude)
                    val weatherJSON = callWeatherAPI(context, latitude, longitude)

                    finalOutput.apply {
                        put("address", address)
                        weatherJSON?.let {
                            val weatherArray = it.getJSONArray("weather")
                            put("firstWeatherDescription", weatherArray.getJSONObject(0).getString("description"))

                            val mainObject = it.getJSONObject("main")
                            put("feelsLike", mainObject.getDouble("feels_like").toString())
                            put("humidity", mainObject.getInt("humidity").toString())

                            val windObject = it.getJSONObject("wind")
                            put("windSpeed", windObject.getDouble("speed").toString())

                            val cloudsObject = it.getJSONObject("clouds")
                            put("cloudAll", cloudsObject.getInt("all").toString())
                        }
                    }
                }
            }
            // endregion
            // region Motion
            val deviceSpeed = sensorHelper?.getDeviceStatus() ?: 0f
            finalOutput.apply {
                put("movementStatus", when {
                    deviceSpeed < 10 -> "idle"
                    deviceSpeed >= 10 && deviceSpeed < 150 -> "moving normal"
                    deviceSpeed >= 150 && deviceSpeed < 500 -> "moving fast"
                    else -> "unknown"
                })
            }
            // Log.d("Helper", "deviceSpeed: $deviceSpeed")
            // endregion

            // Log.d("Helper", "pullDeviceData finalOutput: $finalOutput")
            return finalOutput
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
                Log.e("Helper", "SecurityException: ${securityException.message}")
                null
            }
        }
        private fun pullSystemTime(): Long {
            return System.currentTimeMillis()
        }
        private fun pullTimeFormattedString(): String {
            val dateFormat = SimpleDateFormat("EEE dd/MM/yy HH:mm", Locale.getDefault())
            return dateFormat.format(Date())
        }
        // endregion

        // region Message Related
        fun messageDataWindow(fullJsonString: String?, maxMessages: Int?): JSONArray {
            // Log.i("Helpers", "messageDataWindow")

            val fullJsonArray = JSONArray(fullJsonString)
            // Log.d("Helpers", "fullJsonArray\n$fullJsonArray")

            // Filter out system JSON objects
            val nonSystemJsonArray = JSONArray()
            for (i in 0 until fullJsonArray.length()) {
                val jsonObject = fullJsonArray.getJSONObject(i)
                if (jsonObject.getString("role") != "system") {
                    nonSystemJsonArray.put(jsonObject)
                }
            }
            // Log.d("Helpers", "system-free fullJsonArray\n$nonSystemJsonArray")

            // Return full JSON array if maxMessages is null
            if (maxMessages == null) {
                // Log.d("Helpers", "Returning full JSON array")
                return nonSystemJsonArray
            }

            // Return last maxMessages JSON objects otherwise
            val lastNObjects = JSONArray()
            val startIndex = maxOf(nonSystemJsonArray.length() - maxMessages, 0)
            for (i in startIndex until nonSystemJsonArray.length()) {
                lastNObjects.put(nonSystemJsonArray.getJSONObject(i))
            }
            // Log.d("Helpers", "lastNObjects\n$lastNObjects")

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

        // region Other
        private fun JSONObject.toMap(): Map<String, String> = keys().asSequence().associateWith { getString(it) }
        private fun calculateMetadataSize(metadataMap: Map<String, Any>): Int {
            // Calculate the UTF-8 encoded size of each key and value
            val size = metadataMap.entries.sumOf { entry ->
                entry.key.toByteArray(Charsets.UTF_8).size + entry.value.toString().toByteArray(Charsets.UTF_8).size
            }
            return size
        }
        // endregion
    }
}
