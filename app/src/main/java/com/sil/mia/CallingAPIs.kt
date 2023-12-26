package com.sil.mia

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class CallingAPIs {
    companion object {
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
                        val responseJson =
                            httpURLConnection.inputStream.bufferedReader().use { it.readText() }
                        val jsonResponse = JSONObject(responseJson)
                        Log.i("AudioRecord", "callContextAPI API Response: $jsonResponse")
                        val content = jsonResponse.getString("output")
                        content
                    } else {
                        val errorResponse =
                            httpURLConnection.errorStream.bufferedReader().use { it.readText() }
                        Log.e("AudioRecord", "callContextAPI Error Response: $errorResponse")
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
        suspend fun callOpenaiAPI(payload: JSONObject): String {
            return withContext(Dispatchers.IO) {
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
                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        val responseJson =
                            httpURLConnection.inputStream.bufferedReader().use { it.readText() }
                        val jsonResponse = JSONObject(responseJson)
                        Log.i("AudioRecord", "callOpenaiAPI Response: $jsonResponse")
                        val content = jsonResponse.getJSONArray("choices").getJSONObject(0)
                            .getJSONObject("message").getString("content")
                        content
                    } else {
                        val errorResponse =
                            httpURLConnection.errorStream.bufferedReader().use { it.readText() }
                        Log.e("AudioRecord", "callOpenaiAPI Error Response: $errorResponse")
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
    }
}