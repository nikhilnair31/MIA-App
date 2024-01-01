package com.sil.mia

import android.app.ActivityManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.app.PendingIntent.FLAG_UPDATE_CURRENT
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class ThoughtsAlarmReceiver : BroadcastReceiver() {
    // region Vars
    private val systemPromptBase: String = """
        Your name is MIA and you're an AI companion of the user. Keep your responses very short and a single line.  Reply in a casual texting style and lingo. 
        Internally you have the personality of JARVIS and Chandler Bing combined. You tend to make sarcastic jokes and observations. Do not patronize the user but adapt to how they behave with you.
        You help the user with all their requests, questions and tasks. Be honest and admit if you don't know something when asked.
        Use the context of their prior conversation and the metadata of their real world live audio recordings. 
        Using this data decide if there's anything helpful to message the user. If not respond with "null"
    """
    private var contextMain: Context? = null
    data class Message(val content: String, val isUser: Boolean)
    private val channelId = "MIAThoughtChannel"
    // endregion

    // region Initial
    override fun onReceive(context: Context, intent: Intent) {
        Log.i("ThoughtsAlarm", "onReceive")

        contextMain = context
        CoroutineScope(Dispatchers.IO).launch {
            if (isAppInForeground()) {
                Log.i("ThoughtsAlarm", "App is in foreground. Not showing notification.")
            }
            else {
                Log.i("ThoughtsAlarm", "App is NOT in foreground. Showing notification!")
                createMiaThought()
            }
        }
    }
    private fun isAppInForeground(): Boolean {
        val activityManager = contextMain?.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val appProcesses = activityManager.runningAppProcesses ?: return false

        val packageName = contextMain?.packageName
        return appProcesses.any { processInfo ->
            (processInfo.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND || processInfo.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE) && processInfo.processName == packageName
        }
    }
    // endregion

    // region Thought Generation
    private suspend fun createMiaThought() {
        Log.i("ThoughtsAlarm", "createMiaThought")

        val systemPrompt = createSystemPrompt()

        val wakePayload = JSONObject().apply {
            put("model", "gpt-4-1106-preview")
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", systemPrompt)
                })
            })
            put("seed", 48)
            put("max_tokens", 256)
            put("temperature", 0.9)
        }
        Log.i("ThoughtsAlarm", "ThoughtsAlarmReciever wakePayload: $wakePayload")
        val wakeResponse = Helpers.callOpenaiAPI(wakePayload)
        Log.i("ThoughtsAlarm", "ThoughtsAlarmReciever wakeResponse: $wakeResponse")

        if(wakeResponse != "null") {
            saveMessages(wakeResponse)

            withContext(Dispatchers.Main) {
                showNotification(wakeResponse)
            }
        }
    }

    private suspend fun createSystemPrompt(): String {
        val deviceData = contextMain?.let { Helpers.pullDeviceData(it) }
        val messageHistory = pullConversationHistory()
        val latestRecordings = pullLatestRecordings()

        return "$systemPromptBase\nCurrent Device Data:$deviceData\nConversation History:$messageHistory\nAudio Recording Transcript History:$latestRecordings"
    }

    // TODO: Fix this logic it's still using the Message data type
    private fun pullConversationHistory(): JSONArray {
        val messagesDataSharedPref = contextMain?.getSharedPreferences("com.sil.mia.messagesdata", Context.MODE_PRIVATE)
        val messagesDataJson = messagesDataSharedPref?.getString("messagesdata", null)
        if(messagesDataJson != null) {
            val type = object : TypeToken<List<JSONObject>>() {}.type
            val messagesListData = mutableListOf<JSONObject>()
            messagesListData.addAll(Gson().fromJson(messagesDataJson, type))
        }

        val messageArray = JSONArray()
        val sharedPref = contextMain?.getSharedPreferences("com.sil.mia.messages", Context.MODE_PRIVATE)
        val messagesJson = sharedPref?.getString("messages", null)
        if (messagesJson != null) {
            val type = object : TypeToken<List<Message>>() {}.type
            val messagesFromJson = Gson().fromJson<List<Message>>(messagesJson, type)
            for (message in messagesFromJson) {
                val role = if (message.isUser) "user" else "assistant"
                val messageJson = JSONObject().apply {
                    put("role", role)
                    put("content", message.content)
                }
                messageArray.put(messageJson)
            }
        }
        return messageArray
    }
    private suspend fun pullLatestRecordings(): String {
        val contextData = contextMain?.let { Helpers.pullDeviceData(it) }
        val finalUserMessage = "Context:\n$contextData"

        // Use GPT to create a filter
        val queryGeneratorPayload = JSONObject().apply {
            put("model", "gpt-4")
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put(
                        "content",
                        """
                    You are a system that takes system data context as a JSON and outputs a JSON payload to query a vector database.

                    The input contains:
                    - systemTime (Unix timestamp in milliseconds) 
                    - currentTimeFormattedString (in format '2023-12-28T12:02:00')
                    - day (1-31)
                    - month (1-12)
                    - year (in format 20XX)
                    - hours (in 24h format)
                    - minutes (0-59)
                    The output should contain:
                    - empty query text ""
                    - any of the time filters with a $\gte and $\lte value based on user's request
                    
                    Create a JSON payload best suited to help the user. Output only the filter JSON.
                    
                    Examples:
                    Example #1:
                    Input:
                    Context: {"systemTime":1703864901927,"currentTimeFormattedString":"Fri 29/12/23 10:48", "day": 29, "month": 12, "year": 2023, "hours": 10, "minutes": 48}
                    Output: {"query": "", "query_filter": {"day": { "$\gte": 27, "$\lte": 29 }, "month": { "$\eq": 12 }, "year": { "$\eq": 2023 }}}
                    
                    Example #2:
                    Input:
                    Context: {"systemTime":1703864901927,"currentTimeFormattedString":"Fri 29/12/23 10:48", "day": 29, "month": 12, "year": 2023, "hours": 10, "minutes": 48}
                    Output: {"query": "", "query_filter": {"month": { "$\gte": 11, "$\lte": 12 }, "year": { "$\eq": 2023 }}}
                    """
                    )
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", finalUserMessage)
                })
            })
            put("seed", 48)
            put("max_tokens", 256)
            put("temperature", 0)
        }
        Log.i("ThoughtsAlarm", "pullLatestRecordings queryGeneratorPayload: $queryGeneratorPayload")
        val queryResponse = Helpers.callOpenaiAPI(queryGeneratorPayload)
        val queryResultJSON = JSONObject(queryResponse)
        Log.i("ThoughtsAlarm", "pullLatestRecordings queryResultJSON: $queryResultJSON")

        // Parse the filter JSON to handle various keys and filter types
        val filterJSONObject = JSONObject().apply {
            // Check if 'query_filter' is present and iterate through its keys
            if (queryResultJSON.has("query_filter")) {
                val queryFilters = queryResultJSON.getJSONObject("query_filter")
                queryFilters.keys().forEach { key ->
                    val timeFilter = queryFilters.getJSONObject(key)
                    val timeFilterJSONObject = JSONObject()
                    timeFilter.keys().forEach { filterKey ->
                        when (filterKey) {
                            "\$gte", "\$lte", "\$eq" -> timeFilterJSONObject.put(filterKey, timeFilter.getInt(filterKey))
                            // Add cases for other possible filter keys if needed
                        }
                    }
                    put(key, timeFilterJSONObject)
                }
            }
        }
        Log.i("ThoughtsAlarm", "pullLatestRecordings filterJSONObject: $filterJSONObject")

        // Pull relevant data using a query
        val queryText = queryResultJSON.optString("query", "")
        val contextPayload = JSONObject().apply {
            put("query_text", queryText)
            put("query_filter", filterJSONObject)
            put("query_top_k", 20)
            put("show_log", "True")
        }
        Log.i("ThoughtsAlarm", "pullLatestRecordings contextPayload: $contextPayload")
        val contextMemory = Helpers.callContextAPI(contextPayload)
        Log.i("ThoughtsAlarm", "pullLatestRecordings contextMemory: $contextMemory")

        return contextMemory
    }
    // endregion

    // region Notification
    private fun showNotification(content: String) {
        val title = "MIA"
        val channelName = "MIA Thoughts Channel"
        val importance = NotificationManager.IMPORTANCE_DEFAULT
        val channel = NotificationChannel(channelId, channelName, importance)

        val notificationManager = contextMain?.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)

        // Intent to open the main activity
        val intent = Intent(contextMain, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        val pendingIntent = PendingIntent.getActivity(contextMain, 0, intent, FLAG_IMMUTABLE)

        val notification = contextMain?.let {
            NotificationCompat.Builder(it, channelId)
                .setSmallIcon(R.drawable.mia_stat_name)
                .setContentTitle(title)
                .setContentText(content)
                // .setStyle(NotificationCompat.BigTextStyle().bigText(content))
                .setContentIntent(pendingIntent)
                .setAutoCancel(true) // Remove the notification once tapped
                .build()
        }

        notificationManager.notify(2, notification)
    }
    // endregion

    // region Message Data
    private fun saveMessages(assistantMessage: String) {
        val type = object : TypeToken<List<JSONObject>>() {}.type

        val uitype = "messagesui"
        val messagesListUI = mutableListOf<JSONObject>()
        val messagesUiSharedPref = contextMain?.getSharedPreferences("com.sil.mia.$uitype", Context.MODE_PRIVATE)
        val messagesUiJson = messagesUiSharedPref?.getString(uitype, null)
        messagesListUI.addAll(Gson().fromJson(messagesUiJson, type))
        messagesListUI.add(JSONObject().apply {
            put("role", "assistant")
            put("content", assistantMessage)
            put("time", Helpers.pullTimeFormattedString())
        })
        messagesUiSharedPref?.edit()?.apply {
            putString(uitype, Gson().toJson(messagesListUI))
            apply()
        }

        val datatype = "messagesdata"
        val messagesListData = mutableListOf<JSONObject>()
        val messagesDataSharedPref = contextMain?.getSharedPreferences("com.sil.mia.$datatype", Context.MODE_PRIVATE)
        val messagesDataJson = messagesDataSharedPref?.getString(datatype, null)
        messagesListData.addAll(Gson().fromJson(messagesDataJson, type))
        messagesListData.add(JSONObject().apply {
            put("role", "assistant")
            put("content", assistantMessage)
            put("time", Helpers.pullTimeFormattedString())
        })
        messagesDataSharedPref?.edit()?.apply {
            putString(datatype, Gson().toJson(messagesListData))
            apply()
        }
    }
    // endregion
}