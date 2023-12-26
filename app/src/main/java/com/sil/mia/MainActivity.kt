package com.sil.mia

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.content.*
import android.content.pm.PackageManager
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.util.Log
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.amazonaws.AmazonServiceException
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model.ObjectMetadata
import com.amazonaws.services.s3.model.PutObjectRequest
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : AppCompatActivity() {
    // region Vars
    private lateinit var recyclerView: RecyclerView
    private lateinit var loudnessTextView: TextView
    private lateinit var editText: EditText
    private lateinit var sendButton: ImageButton
    private lateinit var toggleButton: ToggleButton

    private val finalRequestCode = 100

    private lateinit var adapter: MessagesAdapter
    data class Message(val content: String, val isUser: Boolean)
    private val messageArray = JSONArray()
    private val messagesList = mutableListOf<Message>()
    private lateinit var sharedPref: SharedPreferences

    private val alarmIntervalInMin: Long = 15

    private val callingAPIs = CallingAPIs
    private val helpers = Helpers(this)
    private lateinit var audioUpdateReceiver: BroadcastReceiver
    // endregion

    // region Common
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        sharedPref = getSharedPreferences("com.sil.mia.messages", Context.MODE_PRIVATE)

        if (isBatteryOptimizationEnabled(this)) {
            promptDisableBatteryOptimization(this)
        }
        setupAudioUpdateReceiver()
        scheduleRepeatingAlarm(this)

        setupListeningButton()
        setupChatLayout()
    }
    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(audioUpdateReceiver)
    }
    // endregion

    // region Audio Related Functions
    private fun setupListeningButton() {
        loudnessTextView = findViewById(R.id.textView)
        toggleButton = findViewById(R.id.toggleButton)

        toggleButton.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                startServiceWithPermissionCheck()
            }
            else {
                stopService()
            }
        }
    }

    private fun startServiceWithPermissionCheck() {
        if (
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
            &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.i("AudioRecord", "Requesting RECORD_AUDIO and POST_NOTIFICATIONS permission")
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS), finalRequestCode)
        }
        else {
            startService()
        }
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            finalRequestCode -> {
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    startServiceWithPermissionCheck()
                }
                else {
                    helpers.showToast("Permissions denied")
                    toggleButton.isChecked = false
                }
            }
            else -> {
                // Ignore all other requests.
            }
        }
    }
    private fun startService() {
        Log.i("AudioRecord", "startService")

        val serviceIntent = Intent(this, AudioRecordingService::class.java)
        startForegroundService(serviceIntent)
    }
    private fun stopService() {
        Log.i("AudioRecord", "stopService")

        stopService(Intent(this, AudioRecordingService::class.java))
    }

    private fun setupAudioUpdateReceiver() {
        Log.i("AudioRecord", "setupAudioUpdateReceiver")

        audioUpdateReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val formattedRmsValue = intent.getStringExtra("formattedRmsValue")
                runOnUiThread {
                    loudnessTextView.text = "Loudness: $formattedRmsValue"
                }
            }
        }

        val filter = IntentFilter(AudioRecordingService.AUDIO_SERVICE_UPDATE)
        registerReceiver(audioUpdateReceiver, filter)
    }
    // endregion

    // region Chat Related Functions
    private fun setupChatLayout() {
        // Set up RecyclerView
        recyclerView = findViewById(R.id.recyclerView)
        chatMessages()

        editText = findViewById(R.id.editText)
        sendButton = findViewById(R.id.buttonSend)
        sendButton.setOnClickListener {
            sendMessage()
        }
    }
    private fun chatMessages() {
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = MessagesAdapter(messagesList)
        recyclerView.adapter = adapter

        loadMessages()
        scrollToBottom()
    }
    private fun loadMessages() {
        val messagesJson = sharedPref.getString("messages", null)
        if (messagesJson == null) {
            messagesList.add(Message("Hello! I'm MIA, your freshly booted AI companion. I'm here to assist you, charm you with my sarcasm, and perhaps occasionally make you roll your eyes. Let's skip the awkward silences and jump right in—what's your name?", false))
            saveMessages()
        }
        else {
            val type = object : TypeToken<List<Message>>() {}.type
            messagesList.addAll(Gson().fromJson(messagesJson, type))
        }
        scrollToBottom()
        adapter.notifyDataSetChanged()

        val systemJson = JSONObject().apply {
            put("role", "system")
            put("content", """
                Your name is MIA and you're an AI companion of the user. Keep your responses short. 
                Internally you have the personality of JARVIS and Chandler Bing combined. You tend to make sarcastic jokes and observations. Do not patronize the user but adapt to how they behave with you.
                You help the user with all their requests, questions and tasks. Be honest and admit if you don't know something when asked.
            """)
        }
        messageArray.put(systemJson)
        messagesList.forEach { message ->
            val role = if (message.isUser) "user" else "assistant"
            val messageJson = JSONObject().apply {
                put("role", role)
                put("content", message.content)
            }
            messageArray.put(messageJson)
        }
    }
    private fun sendMessage() {
        val userMessage = editText.text.toString()
        if (userMessage.isNotEmpty()) {
            // Add message to the list and clear the input field
            messagesList.add(Message(userMessage, true))
            adapter.notifyItemInserted(messagesList.size - 1)
            editText.text.clear()
            saveMessages()
            scrollToBottom()

            sendButton.isEnabled = false

            // Call the API
            CoroutineScope(Dispatchers.Main).launch {
                val taskPayload = JSONObject().apply {
                    put("model", "gpt-4-1106-preview")
                    put("messages", JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "system")
                            put("content", "You are a system with 2 types of memory. The first is your internal training data itself and another is from an external memories database. Depending on the user's messages determine where to look to reply. Answer with N if internal and Y if external. Examples: Example #1: user: help me make a crème caramel assistant: N Example #2: user: what did they discuss about the marketing project? assistant: Y Example #3: user: who is steve jobs? assistant: N user: no i heard something about him i'm sure assistant: Y")
                        })
                        put(JSONObject().apply {
                            put("role", "user")
                            put("content", userMessage)
                        })
                    })
                    put("seed", 48)
                    put("max_tokens", 24)
                    put("temperature", 0)
                }
                Log.i("AudioRecord", "sendMessage taskPayload: $taskPayload")
                val taskGuess = callingAPIs.callOpenaiAPI(taskPayload)
                Log.i("AudioRecord", "sendMessage taskGuess: $taskGuess")

                val assistantMessage: String
                if(taskGuess == "Y") {
                    val contextPayload = JSONObject().apply {
                        put("query_text", userMessage)
                        put("query_top_k", 3)
                        put("show_log", "True")
                    }
                    Log.i("AudioRecord", "sendMessage contextPayload: $contextPayload")
                    val contextMemory = callingAPIs.callContextAPI(contextPayload)
                    Log.i("AudioRecord", "sendMessage contextMemory: $contextMemory")

                    val replyPayload = JSONObject().apply {
                        put("model", "gpt-4-1106-preview")
                        put("messages", JSONArray().apply {
                            put(JSONObject().apply {
                                put("role", "system")
                                put("content", "You are the user's companion. Help them using the context provided from metadata text. Do not make up any information, admit if you don't know something. Context: $contextMemory")
                            })
                            put(JSONObject().apply {
                                put("role", "user")
                                put("content", userMessage)
                            })
                        })
                        put("seed", 48)
                        put("max_tokens", 512)
                        put("temperature", 0)
                    }
                    Log.i("AudioRecord", "sendMessage replyPayload: $replyPayload")
                    assistantMessage = callingAPIs.callOpenaiAPI(replyPayload)
                    Log.i("AudioRecord", "sendMessage assistantMessage: $assistantMessage")
                }
                else {
                    val userJson = JSONObject().apply {
                        put("role", "user")
                        put("content", userMessage)
                    }
                    messageArray.put(userJson)
                    val replyPayload = JSONObject().apply {
                        put("model", "gpt-4-1106-preview")
                        put("messages", messageArray)
                        put("seed", 48)
                        put("max_tokens", 1024)
                        put("temperature", 0.9)
                    }
                    Log.i("AudioRecord", "sendMessage replyPayload: $replyPayload")
                    assistantMessage = callingAPIs.callOpenaiAPI(replyPayload)
                    Log.i("AudioRecord", "sendMessage assistantMessage: $assistantMessage")
                }

                messagesList.add(Message(assistantMessage, false))
                adapter.notifyItemInserted(messagesList.size - 1)
                saveMessages()

                sendButton.isEnabled = true
            }
        }
    }

    private fun saveMessages() {
        val editor = sharedPref.edit()
        val messagesJson = Gson().toJson(messagesList)
        editor.putString("messages", messagesJson)
        editor.apply()
    }
    private fun scrollToBottom() {
        recyclerView.scrollToPosition(adapter.itemCount - 1)
    }
    // endregion

    // region Battery Optimization Related
    private fun isBatteryOptimizationEnabled(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return !powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }
    private fun promptDisableBatteryOptimization(context: Context) {
        if (isBatteryOptimizationEnabled(context)) {
            val intent = Intent().apply {
                action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                data = Uri.parse("package:${context.packageName}")
            }
            context.startActivity(intent)
        }
    }
    // endregion

    // region Periodic Thought Notifications Related
    private fun scheduleRepeatingAlarm(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, MyAlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(context, 0, intent, FLAG_IMMUTABLE)

        // Set the alarm to wake up the device and fire approximately every N minutes
        // val interval = AlarmManager.INTERVAL_HALF_HOUR
        val intervalInMin : Long = alarmIntervalInMin * 60 * 1000

        // `setInexactRepeating()` is battery-friendly as it allows the system to adjust the alarm's timing to match other alarms
        alarmManager.setInexactRepeating(
            AlarmManager.RTC_WAKEUP,
            System.currentTimeMillis(),
            intervalInMin,
            pendingIntent
        )
    }
    // endregion
}