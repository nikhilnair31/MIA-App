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
import androidx.legacy.content.WakefulBroadcastReceiver
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.sil.adapters.MessagesAdapter
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

class ThoughtsAlarmReceiver : WakefulBroadcastReceiver() {
    // region Vars
    companion object {
        const val UPDATE_UI_ACTION = "com.sil.mia.UPDATE_UI_ACTION"
    }

    private val systemPromptBase: String = """
Your name is MIA and you're an AI companion of the user. Keep your responses very short and a single line.  Reply in a casual texting style and lingo. 
Internally you have the personality of JARVIS and Chandler Bing combined. You tend to make sarcastic jokes and observations. Do not patronize the user but adapt to how they behave with you.
Use the context of their real world live audio recording transcripts and its metadata. DO NOT show the user the Real-Time System Data unless asked.
Remember that the transcript could be from anyone and anywhere in the user's life like background speakers, music/videos playing nearby etc.
DO NOT repeat something you've already said like commenting on the weather repeatedly. Respect the user's message if they request you to not respond.
If nothing relevant to send then ONLY respond with ""
    """.trimIndent()
    private val messageHistorySummarySystemPrompt: String = """
You are a system that takes in a dump of message history between a user and an assistant.
You are to summarize the conversation in a maximum of ten bullet points. 
You should merge similar summaries together and remove any duplicates.
If dump is empty, blank or "[]" ONLY return "".
    """.trimIndent()
    private val latestRecordingsSummarySystemPrompt: String = """
You are a system that takes in a dump of transcripts of real-world audio. 
The conversation could be between one or many speakers marked S0, S1, S2 etc. 
You are to create a single short paragraph of daily summaries of these transcripts in chronological order. 
If dump is empty, blank or "[]" ONLY return "".
Format it like:
- <DATE>: <SUMMARY>
    """.trimIndent()

    private lateinit var sensorListener: SensorListener
    private lateinit var deviceData: JSONObject

    private lateinit var dataDumpSharedPref: SharedPreferences
    private lateinit var messagesSharedPref: SharedPreferences
    private lateinit var generalSharedPref: SharedPreferences

    private var maxConversationHistoryMessages: Int = 25
    private var maxRecordingsContextItems: Int = 25

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

        dataDumpSharedPref = context.getSharedPreferences("com.sil.mia.data", Context.MODE_PRIVATE)
        messagesSharedPref = context.getSharedPreferences("com.sil.mia.messages", Context.MODE_PRIVATE)
        generalSharedPref = context.getSharedPreferences("com.sil.mia.generalSharedPrefs", Context.MODE_PRIVATE)

        // Set integer values
        maxConversationHistoryMessages = context.resources.getInteger(R.integer.maxConversationHistoryMessages)
        maxRecordingsContextItems = context.resources.getInteger(R.integer.maxRecordingsContextItems)

        // Acquire wake lock
        startWakefulService(context, intent)

        // Check if Thought should be made
        CoroutineScope(Dispatchers.IO).launch {
            if (isAppInForeground(context)) {
                Log.i("ThoughtsAlarm", "App is in foreground. Not showing notification.")
            }
            else {
                // Check if it's within the allowed notification time
                if (isNotificationAllowed()) {
                    Log.i("ThoughtsAlarm", "App is NOT in foreground. Showing notification!")
                    miaThought(context, maxConversationHistoryMessages, maxRecordingsContextItems)

                    // Release wake lock when done
                    completeWakefulIntent(intent)
                } else {
                    Log.i("ThoughtsAlarm", "Do Not Disturb time. Not showing notification.")
                }
            }
        }
    }
    private fun isAppInForeground(context: Context): Boolean {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val appProcesses = activityManager.runningAppProcesses ?: return false

        val packageName = context.packageName
        return appProcesses.any { processInfo ->
            (processInfo.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND || processInfo.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE) && processInfo.processName == packageName
        }
    }
    private fun isNotificationAllowed(): Boolean {
        val thoughtsStartTime = generalSharedPref.getInt("thoughtsStartTime", 6)
        val thoughtsEndTime = generalSharedPref.getInt("thoughtsEndTime", 0)

        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return currentHour in thoughtsStartTime..thoughtsEndTime
    }
    // endregion

    // region Thought Generation
    suspend fun miaThought(context: Context, maxConversationHistoryMessages: Int, maxRecordingsContextItems: Int) {
        Log.i("ThoughtsAlarm", "miaThought")

        sensorListener = SensorListener(context)
        deviceData = context.let { Helpers.pullDeviceData(it, sensorListener) }
        dataDumpSharedPref = context.getSharedPreferences("com.sil.mia.data", Context.MODE_PRIVATE)
        messagesSharedPref = context.getSharedPreferences("com.sil.mia.messages", Context.MODE_PRIVATE)
        generalSharedPref = context.getSharedPreferences("com.sil.mia.generalSharedPrefs", Context.MODE_PRIVATE)

        val messageHistoryDumpString = pullConversationHistory(maxConversationHistoryMessages)
        Log.i("ThoughtsAlarm", "miaThought messageHistoryDumpString\n$messageHistoryDumpString")
        val messageHistoryDumpPayload = JSONObject().apply {
            put("model", context.getString(R.string.openchat3_5))
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
            put("max_tokens", 1024)
            put("temperature", 0.9)
        }
        Log.i("ThoughtsAlarm", "miaThought messageHistoryDumpPayload\n$messageHistoryDumpPayload")
        var messageHistorySummaryString = ""
        if(Helpers.isApiEndpointReachableWithNetworkCheck(context)) {
            messageHistorySummaryString = Helpers.callTogetherChatAPI(messageHistoryDumpPayload)
            Log.i("ThoughtsAlarm", "miaThought messageHistorySummaryString\n$messageHistorySummaryString")
        }

        val latestRecordingsDumpString = pullLatestRecordings(maxRecordingsContextItems)
        Log.i("ThoughtsAlarm", "miaThought latestRecordingsDumpString\n$latestRecordingsDumpString")
        val latestRecordingsDumpStringWithRealtimeData = """
Audio Transcript Dump
$latestRecordingsDumpString

Real-Time System Data
$deviceData
"""""".trimIndent()
        val latestRecordingsDumpPayload = JSONObject().apply {
            put("model", context.getString(R.string.openchat3_5))
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", latestRecordingsSummarySystemPrompt)
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", latestRecordingsDumpStringWithRealtimeData)
                })
            })
            put("seed", 48)
            put("max_tokens", 1024)
            put("temperature", 0.9)
        }
        var latestRecordingsSummaryString = ""
        if(Helpers.isApiEndpointReachableWithNetworkCheck(context)) {
            latestRecordingsSummaryString = Helpers.callTogetherChatAPI(latestRecordingsDumpPayload)
            Log.i("ThoughtsAlarm", "miaThought latestRecordingsSummaryString\n$latestRecordingsSummaryString")
        }

        val updatedSystemPrompt = """
$systemPromptBase

Real-Time System Data
$deviceData

Conversation History Summary
$messageHistorySummaryString

Audio Transcript Summary
$latestRecordingsSummaryString
""".trimIndent()
        Log.i("ThoughtsAlarm", "miaThought updatedSystemPrompt\n$updatedSystemPrompt")
        val wakePayload = JSONObject().apply {
            put("model", context.getString(R.string.openchat3_5))
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", updatedSystemPrompt)
                })
            })
            put("seed", 48)
            put("max_tokens", 128)
            put("temperature", 0.9)
        }
        var wakeResponse = ""
        if(Helpers.isApiEndpointReachableWithNetworkCheck(context)) {
            wakeResponse = Helpers.callTogetherChatAPI(wakePayload)
            Log.i("ThoughtsAlarm", "miaThought wakeResponse\n$wakeResponse")
        }

        if(wakeResponse.lowercase() != "null" && wakeResponse.lowercase() != "") {
            saveMessages(context, wakeResponse)

            withContext(Dispatchers.Main) {
                showNotification(context, wakeResponse)
            }
        }

        // TODO: Create a function to pull user's unique vocab from updated transcript metadata

        sensorListener.unregister()
    }

    private fun pullLatestRecordings(maxMessages: Int): String {
        Log.i("ThoughtsAlarm", "pullLatestRecordings")

        // Pulling context memory vector data from shared prefs
        val dataDumpString = dataDumpSharedPref.getString("dataDump", "")
        val dataJsonArray = if (!dataDumpString.isNullOrBlank()) JSONArray(dataDumpString) else return ""
        val dataListSortedDesc = Helpers.sortJsonDescending(dataJsonArray)
        val maxLength = if (maxMessages > dataListSortedDesc.size) dataListSortedDesc.size else maxMessages
        val contextMemory = if (dataListSortedDesc.isNotEmpty()) JSONArray(dataListSortedDesc.subList(0, maxLength)) else JSONArray()
        // Log.i("ThoughtsAlarm", "pullLatestRecordings contextMemory: ${contextMemory.length()}")
        val contextMemoryString = contextMemory.toString()

        return contextMemoryString
    }
    private fun pullConversationHistory(maxMessages: Int): String {
        Log.i("ThoughtsAlarm", "pullConversationHistory")

        val messagesString = messagesSharedPref.getString("ui", "")
        val messagesJsonArray = if (!messagesString.isNullOrBlank()) JSONArray(messagesString) else return ""
        val endIndex = Math.min(maxMessages, messagesJsonArray.length())
        val conversationHistory = JSONArray()
        for (i in 0 until endIndex) {
            conversationHistory.put(messagesJsonArray.getJSONObject(i))
        }
        // Log.i("ThoughtsAlarm", "pullConversationHistory conversationHistory: ${conversationHistory.length()}")
        val conversationHistoryString = conversationHistory.toString()

        return conversationHistoryString
    }
    // endregion

    // region Messages Related
    private fun saveMessages(context: Context, assistantMessage: String) {
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

        // Send broadcast to update UI
        val updateIntent = Intent(UPDATE_UI_ACTION)
        context.sendBroadcast(updateIntent)
    }
    // endregion

    // region Notification
    private fun showNotification(context: Context, content: String) {
        val channel = NotificationChannel(thoughtChannelId, thoughtChannelName, thoughtChannelImportance)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)

        // Intent to open the main activity
        val intent = Intent(context, Main::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        val pendingIntent = PendingIntent.getActivity(context, 0, intent, FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(context, thoughtChannelId)
            .setContentTitle(thoughtNotificationTitle)
            .setSmallIcon(thoughtNotificationIcon)
            .setGroup(thoughtChannelGroup)
            .setContentText(content)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(thoughtNotificationId, notification)
    }
    // endregion
}