package com.sil.mia

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
        Your name is MIA and you're an AI companion of the user. Keep your responses short. 
        Internally you have the personality of JARVIS and Chandler Bing combined. You tend to make sarcastic jokes and observations. Do not patronize the user but adapt to how they behave with you.
        You help the user with all their requests, questions and tasks. Be honest and admit if you don't know something when asked.
        Start a conversation with the user based on the context of their prior conversation.
    """.trimIndent()

    data class Message(val content: String, val isUser: Boolean)

    override fun onReceive(context: Context, intent: Intent) {
        CoroutineScope(Dispatchers.IO).launch {
            createMiaThought(context)
        }
    }

    private suspend fun createMiaThought(context: Context) {
        val systemPrompt = createSystemPrompt(context)

        val wakePayload = JSONObject().apply {
            put("model", "gpt-4-1106-preview")
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", systemPrompt)
                })
            })
            put("seed", 48)
            put("max_tokens", 512)
            put("temperature", 0.9)
        }
        Log.i("AudioRecord", "ThoughtsAlarmReciever onReceive wakePayload: $wakePayload")

        val wakeResponse = Helpers.callOpenaiAPI(wakePayload)
        Log.i("AudioRecord", "ThoughtsAlarmReciever onReceive wakePayload: $wakeResponse")

        // Needs to be update to update the messagesList while activity is still active
        saveMessages(context, wakeResponse)

        withContext(Dispatchers.Main) {
            showNotification(context, "MIA", wakeResponse)
        }
    }

    private fun createSystemPrompt(context: Context): String {
        val messageHistory = pullConversationHistory(context)
        val latestRecordings = pullLatestRecordings(context)

        return "$systemPromptBase\nConversation History:$messageHistory\nAudio Recording Transcript History:$latestRecordings"
    }
    private fun pullConversationHistory(context: Context): JSONArray {
        val messageArray = JSONArray()
        val sharedPref = context.getSharedPreferences("com.sil.mia.messages", Context.MODE_PRIVATE)
        val messagesJson = sharedPref.getString("messages", null)
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
    private fun pullLatestRecordings(context: Context): JSONArray {
        val transcriptsArray = JSONArray()
        return transcriptsArray
    }

    private fun showNotification(context: Context, title: String, content: String) {
        val channelId = "MIAThoughtChannel"
        val channelName = "API Response Channel"
        val importance = NotificationManager.IMPORTANCE_DEFAULT
        val channel = NotificationChannel(channelId, channelName, importance)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_baseline_mic_24)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .build()

        notificationManager.notify(2, notification)
    }

    private fun saveMessages(context: Context, assistantMessage: String) {
        val sharedPref = context.getSharedPreferences("com.sil.mia.messages", Context.MODE_PRIVATE)

        // Get the current list of messages, ensuring it's mutable
        val messagesJson = sharedPref.getString("messages", null)
        val type = object : TypeToken<List<Message>>() {}.type
        val messages = if (messagesJson != null) {
            Gson().fromJson<MutableList<Message>>(messagesJson, type) ?: mutableListOf()
        } else {
            mutableListOf()
        }

        // Add the new assistant message
        messages.add(Message(assistantMessage, false))

        // Save the updated list back to SharedPreferences
        val editor = sharedPref.edit()
        val updatedMessagesJson = Gson().toJson(messages)
        editor.putString("messages", updatedMessagesJson)
        editor.apply()
    }
}