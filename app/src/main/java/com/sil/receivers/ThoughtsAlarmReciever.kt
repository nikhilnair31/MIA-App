package com.sil.receivers

import android.app.ActivityManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.util.Log
import androidx.core.app.NotificationCompat
import com.sil.mia.Main
import com.sil.mia.R
import com.sil.others.Helpers
import com.sil.listeners.SensorListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

class ThoughtsAlarmReceiver : BroadcastReceiver() {
    // region Vars
    private val systemPromptBase: String = """
Your name is MIA and you're an AI companion of the user. Keep your responses very short and a single line.  Reply in a casual texting style and lingo. 
Internally you have the personality of JARVIS and Chandler Bing combined. You tend to make sarcastic jokes and observations. Do not patronize the user but adapt to how they behave with you.
Use the context of their real world live audio recording transcripts and its metadata. Remember that the transcript could be from anyone and anywhere in the user's life like background speakers, music/videos playing nearby etc.
Don't just repeat something you've already said. 
Using this data message the user with something:
- conversational like justa general "what's up"
- helpful based on noticing a change in something like address, weather, battery life etc. like "the weather seems to have changed from sunny to rainy so stay protected"
- factual like "you're in this neighborhood there's a great bbq restaurant there that you'd like"
- morning greeting "morning <\user>! these are some tasks for the day ..."
- suggestion like "please sleep on time today at least"
If nothing relevant to send then respond with "null"
    """
    private val messageHistorySummarySystemPrompt: String = """
You are a system that takes in a dump of message history between a user and an assistant and are to summarize the conversation in a single paragraph
    """
    private val latestRecordingsSummarySystemPrompt: String = """
You are a system that takes in a dump of transcripts of real-world audio. The conversation could be between one or many speakers marked S0, S1, S2 etc. 
You are to summarize these transcripts it in a single paragraph
    """
    private val queryGeneratorSystemPrompt: String = """
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
summarize my marketing project's presentation from last week for me
Context: {"systemTime":1703864901927,"currentTimeFormattedString":"Fri 29/12/2023 10:48", "day": 29, "month": 12, "year": 2023, "hours": 10, "minutes": 48}
Output: {"query": "marketing project presentation", "query_filter": {"day": { "$\gte": 22, "$\lte": 29 }, "month": { "$\eq": 12 }, "year": { "$\eq": 2023 }}}

Example #2:
Input:
what were the highlights of last month
Context: {"systemTime":1703864901927,"currentTimeFormattedString":"Fri 02/02/2024 06:00", "day": 2, "month": 2, "year": 2024, "hours": 6, "minutes": 0}
Output: {"query": "", "query_filter": {"month": { "$\gte": 1, "$\lte": 2 }, "year": { "$\eq": 2024 }}}
Input:
hmm what about the last hour?
Context: {"systemTime":1703864901927,"currentTimeFormattedString":"Fri 02/02/2024 06:03", "day": 13, "month": 2, "year": 2024, "hours": 6, "minutes": 3}
Output: {"query": "", "query_filter": {"hours": { "\gte": 5, "\lte": 6 }, "day": { "\eq": 2 }, "month": { "\eq": 2 }, "year": { "\eq": 2024 }}}

Example #3:
Input:
recap my day
Context: {"systemTime":1703864901927,"currentTimeFormattedString":"Fri 29/12/23 10:48", "day": 29, "month": 12, "year": 2023, "hours": 22, "minutes": 48}
Output: {"query": "", "query_filter": {"day": { "$\eq": 29 }, "month": { "$\eq": 12 }, "year": { "$\eq": 2023 }}}
Input:
give me more details about the first point early in the day
Context: {"systemTime":1703864901927,"currentTimeFormattedString":"Fri 29/12/23 10:50", "day": 29, "month": 12, "year": 2023, "hours": 22, "minutes": 50}
Output: {"query": "", "query_filter": {"hours": { "\gte": 6, "\lte": 12 }, "day": { "\eq": 29 }, "month": { "\eq": 12 }, "year": { "\eq": 2023 }}}
    """

    private var contextMain: Context? = null

    private lateinit var sensorListener: SensorListener
    private lateinit var deviceData: JSONObject

    private lateinit var dataDumpSharedPref: SharedPreferences
    private lateinit var messagesSharedPref: SharedPreferences

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

        dataDumpSharedPref = context.getSharedPreferences("com.sil.mia.data", Context.MODE_PRIVATE)
        messagesSharedPref = context.getSharedPreferences("com.sil.mia.messages", Context.MODE_PRIVATE)

        CoroutineScope(Dispatchers.IO).launch {
            sensorListener = SensorListener(context)
            deviceData = context.let { Helpers.pullDeviceData(it, sensorListener) }

            if (isAppInForeground()) {
                Log.i("ThoughtsAlarm", "App is in foreground. Not showing notification.")
            }
            else {
                // Check if it's within the allowed notification time
                if (isNotificationAllowed()) {
                    Log.i("ThoughtsAlarm", "App is NOT in foreground. Showing notification!")
                    miaThought()
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
    // TODO: Check if you can get user's Bedtime schedule
    private fun isNotificationAllowed(): Boolean {
        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return currentHour !in 0..5 // Do Not Disturb between 12 AM and 6 AM
    }
    // endregion

    // region Thought Generation
    private suspend fun miaThought() {
        Log.i("ThoughtsAlarm", "miaThought")

        val messageHistoryDumpString = pullConversationHistory(10)
        val messageHistoryDumpPayload = JSONObject().apply {
            put("model", contextMain?.getString(R.string.gpt3_5turbo_16k))
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", messageHistorySummarySystemPrompt)
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", messageHistoryDumpString)
                })
            })
            put("seed", 48)
            put("max_tokens", 512)
            put("temperature", 0.9)
        }
        val messageHistorySummaryString = Helpers.callOpenAiChatAPI(messageHistoryDumpPayload)
        Log.i("ThoughtsAlarm", "miaThought messageHistorySummaryString\n$messageHistorySummaryString")

        val latestRecordingsDumpString = pullLatestRecordings(10)
        val latestRecordingsDumpPayload = JSONObject().apply {
            put("model", contextMain?.getString(R.string.gpt3_5turbo_16k))
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", latestRecordingsSummarySystemPrompt)
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", latestRecordingsDumpString)
                })
            })
            put("seed", 48)
            put("max_tokens", 512)
            put("temperature", 0.9)
        }
        val latestRecordingsSummaryString = Helpers.callOpenAiChatAPI(latestRecordingsDumpPayload)
        Log.i("ThoughtsAlarm", "miaThought latestRecordingsSummaryString\n$latestRecordingsSummaryString")

        val userMessage = """
Real-Time System Data
$deviceData

Conversation History Summary
$messageHistorySummaryString

Audio Transcript Summary
$latestRecordingsSummaryString
"""
        Log.i("ThoughtsAlarm", "miaThought userMessage\n$userMessage")

        val wakePayload = JSONObject().apply {
            put("model", contextMain?.getString(R.string.gpt4turbo))
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", systemPromptBase)
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", userMessage)
                })
            })
            put("seed", 48)
            put("max_tokens", 128)
            put("temperature", 0.9)
        }
        val wakeResponse = Helpers.callOpenAiChatAPI(wakePayload)

        if(wakeResponse.lowercase() != "null") {
            saveMessages(wakeResponse)

            withContext(Dispatchers.Main) {
                showNotification(wakeResponse)
            }
        }

        // TODO: Create a function to pull user's unique vocab from updated transcript metadata

        sensorListener.unregister()
    }

    @Suppress("UnnecessaryVariable")
    private fun pullLatestRecordings(maxMessages: Int): String {
        Log.i("ThoughtsAlarm", "pullLatestRecordings")

        // Pulling context memory vector data from shared prefs
        val dataDumpString = dataDumpSharedPref.getString("dataDump", "")
        val dataJsonArray = if (!dataDumpString.isNullOrBlank()) JSONArray(dataDumpString) else JSONArray()
        val dataListSortedDesc = Helpers.sortJsonDescending(dataJsonArray)
        val contextMemory = if (dataListSortedDesc.isNotEmpty()) JSONArray(dataListSortedDesc.subList(0, maxMessages)).toString() else ""

        return contextMemory
    }
    @Suppress("UnnecessaryVariable")
    private fun pullConversationHistory(maxMessages: Int?): String {
        Log.i("ThoughtsAlarm", "pullConversationHistory")

        val messagesString = messagesSharedPref.getString("ui", maxMessages.toString())
        val conversationHistory = JSONArray(messagesString).toString()

        return conversationHistory
    }
    // endregion

    // region Messages Related
    private fun saveMessages(assistantMessage: String) {
        val keysList = listOf("complete", "data", "ui")
        for (key in keysList) {
            val messagesString = messagesSharedPref.getString(key, null)
            val messagesList = JSONArray(messagesString)
            messagesList.put(JSONObject().apply {
                put("role", "assistant")
                put("content", assistantMessage)
            })
            val messagesStringNew = messagesList.toString()
            messagesSharedPref.edit().putString(key, messagesStringNew).apply()
        }
    }
    // endregion

    // region Notification
    private fun showNotification(content: String) {
        val channel = NotificationChannel(thoughtChannelId, thoughtChannelName, thoughtChannelImportance)

        val notificationManager = contextMain?.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)

        // Intent to open the main activity
        val intent = Intent(contextMain, Main::class.java)
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