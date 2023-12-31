package com.sil.mia

import android.app.ActivityManager
import android.app.NotificationChannel
import android.app.NotificationManager
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
    private val systemPromptBase: String = """
        Your name is MIA and you're an AI companion of the user. Keep your responses very short and a single line. 
        Internally you have the personality of JARVIS and Chandler Bing combined. You tend to make sarcastic jokes and observations. Do not patronize the user but adapt to how they behave with you.
        You help the user with all their requests, questions and tasks. Be honest and admit if you don't know something when asked.
        Use the context of their prior conversation and the metadata of their real world live audio recordings. 
        Using this data decide if there's anything helpful to message the user. If not respond with .
    """
    private var contextMain: Context? = null

    data class Message(val content: String, val isUser: Boolean)

    override fun onReceive(context: Context, intent: Intent) {
        contextMain = context
        CoroutineScope(Dispatchers.IO).launch {
            if (isAppInForeground()) {
                Log.i("ThoughtsAlarmReceiver", "App is in foreground. Not showing notification.")
                return@launch
            }
            createMiaThought()
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

    private suspend fun createMiaThought() {
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
        Log.i("AudioRecord", "ThoughtsAlarmReciever onReceive wakePayload: $wakePayload")
        val wakeResponse = Helpers.callOpenaiAPI(wakePayload)
        Log.i("AudioRecord", "ThoughtsAlarmReciever onReceive wakePayload: $wakeResponse")

        // TODO: Needs to be update to update the messagesList while activity is still active
        // saveMessages(wakeResponse)

        withContext(Dispatchers.Main) {
            showNotification(wakeResponse)
        }
    }

    private suspend fun createSystemPrompt(): String {
        val messageHistory = pullConversationHistory()
        val latestRecordings = pullLatestRecordings()

        return "$systemPromptBase\nConversation History:$messageHistory\nAudio Recording Transcript History:$latestRecordings"
    }
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
        Log.i("AudioRecord", "pullLatestRecordings queryGeneratorPayload: $queryGeneratorPayload")
        val queryResponse = Helpers.callOpenaiAPI(queryGeneratorPayload)
        val queryResultJSON = JSONObject(queryResponse)
        Log.i("AudioRecord", "pullLatestRecordings queryResultJSON: $queryResultJSON")

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
        Log.i("AudioRecord", "pullLatestRecordings filterJSONObject: $filterJSONObject")

        // Pull relevant data using a query
        val queryText = queryResultJSON.optString("query", "")
        val contextPayload = JSONObject().apply {
            put("query_text", queryText)
            put("query_filter", filterJSONObject)
            put("query_top_k", 20)
            put("show_log", "True")
        }
        Log.i("AudioRecord", "pullLatestRecordings contextPayload: $contextPayload")
        val contextMemory = Helpers.callContextAPI(contextPayload)
        Log.i("AudioRecord", "pullLatestRecordings contextMemory: $contextMemory")

        return contextMemory
    }

    private fun showNotification(content: String) {
        if(content.trimIndent().strip() != ".") {
            val title = "MIA"
            val channelId = "MIAThoughtChannel"
            val channelName = "MIA Thoughts Channel"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(channelId, channelName, importance)

            val notificationManager =
                contextMain?.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)

            val notification = contextMain?.let {
                NotificationCompat.Builder(it, channelId)
                    .setSmallIcon(R.drawable.mia_stat_name)
                    .setContentTitle(title)
                    .setContentText(content)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(content))
                    .build()
            }

            notificationManager.notify(2, notification)
        }
    }

    private fun saveMessages(assistantMessage: String) {
        val sharedPref = contextMain?.getSharedPreferences("com.sil.mia.messages", Context.MODE_PRIVATE)

        // Get the current list of messages, ensuring it's mutable
        val messagesJson = sharedPref?.getString("messages", null)
        val type = object : TypeToken<List<Message>>() {}.type
        val messages = if (messagesJson != null) {
            Gson().fromJson<MutableList<Message>>(messagesJson, type) ?: mutableListOf()
        } else {
            mutableListOf()
        }

        // Add the new assistant message
        messages.add(Message(assistantMessage, false))

        // Save the updated list back to SharedPreferences
        val editor = sharedPref?.edit()
        val updatedMessagesJson = Gson().toJson(messages)
        editor?.putString("messages", updatedMessagesJson)
        editor?.apply()
    }
}