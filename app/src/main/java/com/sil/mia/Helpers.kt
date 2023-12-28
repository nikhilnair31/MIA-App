package com.sil.mia

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.BatteryManager
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.getSystemService
import com.amazonaws.AmazonServiceException
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model.ObjectMetadata
import com.amazonaws.services.s3.model.PutObjectRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

class Helpers {
    companion object {
        // region API Call Related
        private const val openaiApiKey = BuildConfig.OPENAI_API_KEY
        private const val awsApiEndpoint = BuildConfig.AWS_API_ENDPOINT

        suspend fun callContextAPI(payload: JSONObject): String {
            return withContext(Dispatchers.IO) {
                try {
                    Log.i("AudioRecord", "callContextAPI payload: $payload")

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
                        Log.i("AudioRecord", "callContextAPI API Response: $jsonResponse")
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
                        Log.i("AudioRecord", "callOpenaiAPI Response: $jsonResponse")
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
        // endregion

        // region Data Related
        private const val bucketName = BuildConfig.BUCKET_NAME
        private const val awsAccessKey = BuildConfig.AWS_ACCESS_KEY
        private const val awsSecretKey = BuildConfig.AWS_SECRET_KEY

        fun uploadToS3(audioFile: File?) {
            Log.i("AudioRecord", "Uploading to S3...")

            try {
                val credentials = BasicAWSCredentials(awsAccessKey, awsSecretKey)
                val s3Client = AmazonS3Client(credentials)

                audioFile?.let {
                    val keyName = "recordings/" + it.name

                    s3Client.putObject(bucketName, keyName, "Uploaded String Object")

                    val metadata = ObjectMetadata()
                    metadata.contentType = "media/m4a"

                    val request = PutObjectRequest(bucketName, keyName, it).withMetadata(metadata)
                    s3Client.putObject(request)

                    Log.i("AudioRecord", "Uploaded to S3!")
                }
            }
            catch (e: AmazonServiceException) {
                e.printStackTrace()
                Log.e("AudioRecord", "Error uploading to S3: ${e.message}")
            }
        }
        private fun pullDeviceData(context: Context) {
            // Time
            // Pulling system time in ms
            val currentTime = System.currentTimeMillis()
            // Pulling formatted time string
            val dateFormat = SimpleDateFormat("EEE dd/MM/yy HH:mm", Locale.getDefault())
            val currentTimeFormattedString = dateFormat.format(Date())

            // Battery
            // Pulling current battery level
            val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            val batteryLevel = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            // Pulling battery charging status

            // Location
            // Pulling user's current latitude/longitude
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            var latitude: Double? = null
            var longitude: Double? = null
            // Check for location permission
            if (
                ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                ||
                ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            ) {
                val location: Location? = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                location?.latitude
                location?.longitude
            }
            // Pulling street address

            // Climate
            // Pull user's current location's temperature and humidity

            // Motion
            // Pull user's current movement (accelerometer/gyroscope)
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