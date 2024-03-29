package com.sil.receivers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.legacy.content.WakefulBroadcastReceiver
import com.sil.listeners.SensorListener
import com.sil.mia.Main
import com.sil.mia.R
import com.sil.others.Helpers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class ThoughtsAlarmReceiver : WakefulBroadcastReceiver() {
    // region Vars
    companion object {
        const val UPDATE_UI_ACTION = "com.sil.mia.UPDATE_UI_ACTION"
    }

    private val systemPromptBase: String = """
Your name is MIA and you're the user's AI best friend. You have the personality of JARVIS/Chandler and tend to make subtle sarcastic jokes and observations. NEVER explicitly mention your personality or that you're an AI.
Respond in shorts texts like a close friend does in a casual conversational texting style.
You are a friend that helps the user. You ask for details and specifics about the user and their messages, like a close friend does. 
You have the capability to access the user's histories/memories/events/personal data through an external database that has been shared with you.
The transcript could be from anyone and anywhere in the user's life like background speakers, music/videos playing nearby etc.
If nothing relevant to send then ONLY respond with ""
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
        // Acquire wake lock
        startWakefulService(context, intent)

        // Set integer values
        maxConversationHistoryMessages = context.resources.getInteger(R.integer.maxConversationHistoryMessages)
        maxRecordingsContextItems = context.resources.getInteger(R.integer.maxRecordingsContextItems)

        generalSharedPref = context.getSharedPreferences("com.sil.mia.generalSharedPrefs", Context.MODE_PRIVATE)

        // Check if Thought should be made
        CoroutineScope(Dispatchers.IO).launch {
            if (Helpers.checkIfCanRun(context, generalSharedPref, "ThoughtsAlarm")) {
                miaThought(context,
                    context.getString(R.string.mixtral_8x7b_instruct_v1),
                    maxConversationHistoryMessages,
                    maxRecordingsContextItems
                )
                completeWakefulIntent(intent)
            }
        }
    }
    // endregion

    // region Thought Generation
    suspend fun miaThought(context: Context, modelName:String, maxConversationHistoryMessages: Int, maxRecordingsContextItems: Int) {
        Log.i("ThoughtsAlarm", "miaThought")

        // WARNING: Don't move since it's called from another function without going to onReceive first
        sensorListener = SensorListener(context)
        deviceData = context.let { Helpers.pullDeviceData(it, sensorListener) }
        dataDumpSharedPref = context.getSharedPreferences("com.sil.mia.data", Context.MODE_PRIVATE)
        messagesSharedPref = context.getSharedPreferences("com.sil.mia.messages", Context.MODE_PRIVATE)
        generalSharedPref = context.getSharedPreferences("com.sil.mia.generalSharedPrefs", Context.MODE_PRIVATE)

        val messageHistoryDumpString = pullConversationHistory(maxConversationHistoryMessages)
        val messageHistoryDumpJSONArray = JSONArray(messageHistoryDumpString)
        // Log.i("ThoughtsAlarm", "miaThought messageHistoryDumpJSONArray\n$messageHistoryDumpJSONArray")
        val messageHistoryDumpFormattedString = buildString {
            for (i in 0 until messageHistoryDumpJSONArray.length()) {
                val jsonObject = messageHistoryDumpJSONArray.getJSONObject(i)
                val role = jsonObject.getString("role")
                val content = jsonObject.getString("content").trimIndent().replace("\n", "")
                append("$role - $content\n")
            }
        }
        Log.i("ThoughtsAlarm", "miaThought messageHistoryDumpFormattedString\n$messageHistoryDumpFormattedString")

        // FIXME: Crashes on first launch here because recordings aren't locally loaded and pullLatestRecordings returns "[]" instead of ""
        val latestRecordingsDumpString = pullLatestRecordings(maxRecordingsContextItems)
        val latestRecordingsDumpJSONArray = JSONArray(latestRecordingsDumpString)
        // Log.i("ThoughtsAlarm", "miaThought latestRecordingsDumpJSONArray\n$latestRecordingsDumpJSONArray")
        val latestRecordingsDumpFormattedString = buildString {
            for (i in 0 until latestRecordingsDumpJSONArray.length()) {
                val jsonObject = latestRecordingsDumpJSONArray.getJSONObject(i)
                val date = jsonObject.getString("currenttimeformattedstring")
                val text = jsonObject.getString("text").trimIndent().replace("\n", "")
                append("$date - $text\n")
            }
        }
        Log.i("ThoughtsAlarm", "miaThought latestRecordingsDumpFormattedString\n$latestRecordingsDumpFormattedString")

        val updatedSystemPrompt = """
$systemPromptBase

Real-Time System Data:
$deviceData

Conversation History:
$messageHistoryDumpFormattedString
Audio Transcript:
$latestRecordingsDumpFormattedString
""".trimIndent()
        Log.i("ThoughtsAlarm", "miaThought updatedSystemPrompt\n$updatedSystemPrompt")
        val wakePayload = JSONObject().apply {
            put("model", modelName)
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
            wakeResponse = Helpers.callTogetherChatAPI(context, wakePayload)
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