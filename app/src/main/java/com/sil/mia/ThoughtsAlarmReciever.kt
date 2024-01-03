package com.sil.mia

import android.app.ActivityManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.res.Resources
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
import java.util.*

class ThoughtsAlarmReceiver : BroadcastReceiver() {
    // region Vars
    private val systemPromptBase: String = """
        Your name is MIA and you're an AI companion of the user. Keep your responses very short and a single line.  Reply in a casual texting style and lingo. 
        Internally you have the personality of JARVIS and Chandler Bing combined. You tend to make sarcastic jokes and observations. Do not patronize the user but adapt to how they behave with you.
        You help the user with all their requests, questions and tasks. Be honest and admit if you don't know something when asked.
        Use the context of their prior conversation and the metadata of their real world live audio recordings. 
        Using this data message the user with something; conversational, helpful, factual etc. If not respond with "null"
    """
    private var contextMain: Context? = null

    private val thoughtChannelId = "MIAThoughtChannel"
    private val thoughtChannelName = "MIA Thoughts Channel"
    private val thoughtChannelGroup = "MIA Thoughts Channel"
    private val thoughtChannelImportance = NotificationManager.IMPORTANCE_DEFAULT
    private val thoughtNotificationTitle = "MIA"
    private val thoughtNotificationIcon = R.drawable.mia_stat_name
    private val thoughtNotificationId = 2
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
                // Check if it's within the allowed notification time
                if (isNotificationAllowed()) {
                    Log.i("ThoughtsAlarm", "App is NOT in foreground. Showing notification!")
                    createMiaThought()
                } else {
                    Log.i("ThoughtsAlarm", "Do Not Disturb time. Not showing notification.")
                }
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
    // TODO: Update to use actual Do Not Disturb system timings if available
    private fun isNotificationAllowed(): Boolean {
        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return currentHour !in 0..5 // Do Not Disturb between 12 AM and 6 AM
    }
    // endregion

    // region Thought Generation
    private suspend fun createMiaThought() {
        Log.i("ThoughtsAlarm", "createMiaThought")

        val systemPrompt = createSystemPrompt()

        val wakePayload = JSONObject().apply {
            put("model", contextMain?.getString(R.string.gpt4turbo))
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", systemPrompt)
                })
            })
            put("seed", 48)
            put("max_tokens", 128)
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
        Log.i("ThoughtsAlarm", "createSystemPrompt")

        val deviceData = contextMain?.let { Helpers.pullDeviceData(it) }
        Log.i("ThoughtsAlarm", "createSystemPrompt deviceData\n$deviceData")
        val latestRecordings = pullLatestRecordings()
        Log.i("ThoughtsAlarm", "createSystemPrompt latestRecordings\n$latestRecordings")
        val messageHistory = pullConversationHistory()
        Log.i("ThoughtsAlarm", "createSystemPrompt messageHistory\n$messageHistory")

        val finalPrompt = "$systemPromptBase\nCurrent Device Data:$deviceData\nConversation History:$messageHistory\nAudio Recording Transcript History:$latestRecordings"
        Log.i("ThoughtsAlarm", "createSystemPrompt finalPrompt\n$finalPrompt")

        return finalPrompt
    }

    private suspend fun pullLatestRecordings(): String {
        Log.i("ThoughtsAlarm", "pullLatestRecordings")

        val contextData = contextMain?.let { Helpers.pullDeviceData(it) }
        val finalUserMessage = "Context:\n$contextData"

        // Use GPT to create a filter
        val queryGeneratorPayload = JSONObject().apply {
            put("model", contextMain?.getString(R.string.gpt3_5turbo))
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
        Log.i("ThoughtsAlarm", "pullLatestRecordings queryResponse: $queryResponse")
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
            put("query_top_k", 5)
            put("show_log", "True")
        }
        Log.i("ThoughtsAlarm", "pullLatestRecordings contextPayload: $contextPayload")
        val contextMemory = Helpers.callContextAPI(contextPayload)
        Log.i("ThoughtsAlarm", "pullLatestRecordings contextMemory: $contextMemory")

        return contextMemory
    }
    private fun pullConversationHistory(): JSONArray {
        Log.i("ThoughtsAlarm", "pullConversationHistory")

        val messagesDataSharedPref = contextMain?.getSharedPreferences("com.sil.mia.messagesdata", Context.MODE_PRIVATE)
        val messagesDataString = messagesDataSharedPref?.getString("messagesdata", null)
        Log.i("ThoughtsAlarm", "messagesDataString\n$messagesDataString")

        val messagesDataArray = JSONArray(messagesDataString)
        Log.i("ThoughtsAlarm", "messagesDataArray\n$messagesDataArray")
        return messagesDataArray
    }

    private fun saveMessages(assistantMessage: String) {
        val typeUi = contextMain?.getString(R.string.dataUi)
        // Log.i("ThoughtsAlarm", "saveMessages typeUi: $typeUi")
        val messagesUiSharedPref = contextMain?.getSharedPreferences("com.sil.mia.$typeUi", Context.MODE_PRIVATE)
        var messagesUiString = messagesUiSharedPref?.getString(typeUi, null)
        // Log.i("ThoughtsAlarm", "saveMessages old messagesUiString: $messagesUiString")
        val messagesListUI = JSONArray(messagesUiString)
        val messagesUiEditor = messagesUiSharedPref?.edit()
        messagesListUI.put(JSONObject().apply {
            put("role", "assistant")
            put("content", assistantMessage)
        })
        messagesUiString = messagesListUI.toString()
        // Log.i("ThoughtsAlarm", "saveMessages NEW messagesUiString: $messagesUiString")
        messagesUiEditor?.putString(typeUi, messagesUiString)
        messagesUiEditor?.apply()

        val typeData = contextMain?.getString(R.string.dataData)
        // Log.i("ThoughtsAlarm", "saveMessages typeData: $typeData")
        val messagesDataSharedPref = contextMain?.getSharedPreferences("com.sil.mia.$typeData", Context.MODE_PRIVATE)
        var messagesDataString = messagesDataSharedPref?.getString(typeData, null)
        // Log.i("ThoughtsAlarm", "saveMessages old messagesDataString: $messagesDataString")
        val messagesListData = JSONArray(messagesDataString)
        val messagesDataEditor = messagesDataSharedPref?.edit()
        messagesListData.put(JSONObject().apply {
            put("role", "assistant")
            put("content", assistantMessage)
        })
        messagesDataString = messagesListData.toString()
        // Log.i("ThoughtsAlarm", "saveMessages NEW messagesDataString: $messagesDataString")
        messagesDataEditor?.putString(typeData, messagesDataString)
        messagesDataEditor?.apply()
    }
    // endregion

    // region Notification
    private fun showNotification(content: String) {
        val channel = NotificationChannel(thoughtChannelId, thoughtChannelName, thoughtChannelImportance)

        val notificationManager = contextMain?.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)

        // Intent to open the main activity
        val intent = Intent(contextMain, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        val pendingIntent = PendingIntent.getActivity(contextMain, 0, intent, FLAG_IMMUTABLE)

        val notification = contextMain?.let {
            NotificationCompat.Builder(it, thoughtChannelId)
                .setContentTitle(thoughtNotificationTitle)
                .setSmallIcon(thoughtNotificationIcon)
                .setGroup(thoughtChannelGroup)
                .setContentText(content)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()
        }

        notificationManager.notify(thoughtNotificationId, notification)
    }
    // endregion
}