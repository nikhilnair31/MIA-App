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
    private val systemPromptBase: String = """
Your name is MIA and you're an AI companion of the user. Keep your responses very short and a single line.  Reply in a casual texting style and lingo. 
Internally you have the personality of JARVIS and Chandler Bing combined. You tend to make sarcastic jokes and observations. Do not patronize the user but adapt to how they behave with you.
Use the context of their real world live audio recording transcripts and its metadata. Remember that the transcript could be from anyone and anywhere in the user's life like background speakers, music/videos playing nearby etc.
DO NOT repeat something you've already said like commenting on the weather repeatedly.
Using this data message the user with something:
- conversational like just a general "what's up"
- helpful based on noticing a CHANGE in something like address, weather, battery life etc. like "the weather seems to have changed from sunny to rainy so stay protected"
- FACTUAL like "you're in this neighborhood there's a great bbq restaurant there that you'd like"
- morning GREETING "morning <\user>! these are some tasks for the day ..."
- SUGGESTION like "please sleep on time today at least"
If nothing relevant to send then respond with "null"
    """
    private val messageHistorySummarySystemPrompt: String = """
You are a system that takes in a dump of message history between a user and an assistant.
You are to summarize the conversation in a maximum of ten bullet points. 
You should merge similar summaries together and remove any duplicates.
    """
    private val latestRecordingsSummarySystemPrompt: String = """
You are a system that takes in a dump of transcripts of real-world audio. 
The conversation could be between one or many speakers marked S0, S1, S2 etc. 
You are to create a single short paragraph of daily summaries of these transcripts in chronological order. 
Format it like:
- <DATE>: <SUMMARY>
    """

    private var contextMain: Context? = null

    private lateinit var sensorListener: SensorListener
    private lateinit var deviceData: JSONObject

    private lateinit var dataDumpSharedPref: SharedPreferences
    private lateinit var messagesSharedPref: SharedPreferences

    private val maxConversationHistoryMessages: Int = 20
    private val maxRecordingsContextItems: Int = 20

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

        // Acquire wake lock
        startWakefulService(context, intent)

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

                    // Release wake lock when done
                    completeWakefulIntent(intent)
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

    // TODO: Make this user controllable
    private fun isNotificationAllowed(): Boolean {
        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return currentHour !in 0..5
    }
    // endregion

    // region Thought Generation
    private suspend fun miaThought() {
        Log.i("ThoughtsAlarm", "miaThought")

        val messageHistoryDumpString = pullConversationHistory(maxConversationHistoryMessages)
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

        val latestRecordingsDumpString = pullLatestRecordings(maxRecordingsContextItems)
        val latestRecordingsDumpStringWithRealtimeData = """
Audio Transcript Dump
$latestRecordingsDumpString

Real-Time System Data
$deviceData
"""
        val latestRecordingsDumpPayload = JSONObject().apply {
            put("model", contextMain?.getString(R.string.gpt3_5turbo_16k))
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
            put("max_tokens", 512)
            put("temperature", 0.9)
        }
        val latestRecordingsSummaryString = Helpers.callOpenAiChatAPI(latestRecordingsDumpPayload)
        Log.i("ThoughtsAlarm", "miaThought latestRecordingsSummaryString\n$latestRecordingsSummaryString")

        val updatedSystemPrompt = """
$systemPromptBase

Real-Time System Data
$deviceData

Conversation History Summary
$messageHistorySummaryString

Audio Transcript Summary
$latestRecordingsSummaryString
"""
        Log.i("ThoughtsAlarm", "miaThought updatedSystemPrompt\n$updatedSystemPrompt")

        val wakePayload = JSONObject().apply {
            put("model", contextMain?.getString(R.string.gpt4turbo))
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", updatedSystemPrompt)
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
        val dataJsonArray = if (!dataDumpString.isNullOrBlank()) JSONArray(dataDumpString) else return ""
        val dataListSortedDesc = Helpers.sortJsonDescending(dataJsonArray)
        val contextMemory = if (dataListSortedDesc.isNotEmpty()) JSONArray(dataListSortedDesc.subList(0, maxMessages)) else JSONArray()
        // Log.i("ThoughtsAlarm", "pullLatestRecordings contextMemory: ${contextMemory.length()}")
        val contextMemoryString = contextMemory.toString()

        return contextMemoryString
    }
    @Suppress("UnnecessaryVariable")
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