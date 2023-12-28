package com.sil.mia

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.content.*
import android.content.pm.PackageManager
import android.os.*
import android.provider.Settings
import android.util.Log
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.lang.reflect.Array.set

class MainActivity : AppCompatActivity() {
    // region Vars
    private var isFirstAppLaunch = true
    private val finalRequestCode = 100

    private lateinit var recyclerView: RecyclerView
    private lateinit var loudnessTextView: TextView
    private lateinit var editText: EditText
    private lateinit var sendButton: ImageButton
    private lateinit var toggleButton: ToggleButton

    private lateinit var audioUpdateReceiver: BroadcastReceiver
    data class Message(val content: String, val isUser: Boolean)
    private val messageArray = JSONArray()
    private val messagesList = mutableListOf<JSONObject>()
    private lateinit var adapter: MessagesAdapter
    private lateinit var sharedPref: SharedPreferences

    private val alarmIntervalInMin: Long = 2
    // endregion

    // region Common
    override fun onCreate(savedInstanceState: Bundle?) {
        Log.i("AudioRecord", "onCreate")
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // isFirstAppLaunch = true;

        setupPermissions()
        setupListening()
        setupChatLayout()
        // scheduleRepeatingAlarm(this)
    }
    override fun onResume() {
        Log.i("AudioRecord", "onResume")
        super.onResume()

        // Doing this so the messages aren't loaded twice on app's cold start/launch
        // if(!isFirstAppLaunch) {
        //     loadMessages()
        // }
    }
    override fun onDestroy() {
        Log.i("AudioRecord", "onDestroy")
        super.onDestroy()

        unregisterReceiver(audioUpdateReceiver)
    }
    // endregion

    // region Permissions Related
    private fun setupPermissions() {
        // Get all the permissions needed
        if (
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
            ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
            ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
            ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.i("AudioRecord", "Requesting multiple permissions")
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.POST_NOTIFICATIONS,
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.BODY_SENSORS
                ),
                finalRequestCode
            )
        }

        // Make user set app to unrestricted battery usage
        if (
            !(this.getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(this.packageName)
        ) {
            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            startActivity(intent)
        }
    }
    // endregion

    // region Audio Related Functions
    private fun setupListening() {
        toggleButton = findViewById(R.id.toggleButton)
        toggleButton.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                startService()
            }
            else {
                stopService()
            }
        }

        setupAudioUpdateReceiver()
    }

    private fun startService() {
        Log.i("AudioRecord", "startService")

        val serviceIntent = Intent(this, AudioRelated::class.java)
        startForegroundService(serviceIntent)
    }
    private fun stopService() {
        Log.i("AudioRecord", "stopService")

        stopService(Intent(this, AudioRelated::class.java))
    }

    private fun setupAudioUpdateReceiver() {
        Log.i("AudioRecord", "setupAudioUpdateReceiver")

        loudnessTextView = findViewById(R.id.textView)
        audioUpdateReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val formattedRmsValue = intent.getStringExtra("formattedRmsValue")
                runOnUiThread {
                    loudnessTextView.text = getString(R.string.loudness, formattedRmsValue)
                }
            }
        }

        // Create an intent filter and register the receiver. It's good practice to define the action string as a constant somewhere
        val filter = IntentFilter(AudioRelated.AUDIO_SERVICE_UPDATE)
        registerReceiver(audioUpdateReceiver, filter)
    }
    // endregion

    // region Chat Related Functions
    private fun setupChatLayout() {
        chatMessages()
        loadMessages()
        scrollToBottom()

        editText = findViewById(R.id.editText)
        sendButton = findViewById(R.id.buttonSend)
        sendButton.setOnClickListener {
            sendMessage()
        }
    }
    private fun chatMessages() {
        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = MessagesAdapter(messagesList)
        recyclerView.adapter = adapter
    }
    private fun loadMessages() {
        sharedPref = getSharedPreferences("com.sil.mia.messages", Context.MODE_PRIVATE)
        val messagesJson = sharedPref.getString("messages", null)
        // If saved messages are empty then add a message from MIA and save it
        if (messagesJson == null) {
            messagesList.add(
                JSONObject().apply {
                    put("role", "assistant")
                    put(
                        "content",
                        """
                            Hello! I'm MIA, your freshly booted AI companion. 
                            I'm here to assist you, charm you with my sarcasm, and perhaps occasionally make you roll your eyes. 
                            Let's skip the awkward silences and jump right in—what's your name?
                        """.trimIndent()
                    )
                }
            )
        }
        // If saved messages exist then pull and populate messages
        else {
            val type = object : TypeToken<List<JSONObject>>() {}.type
            messagesList.addAll(Gson().fromJson(messagesJson, type))
        }
        adapter.notifyItemInserted(messagesList.size - 1)
        saveMessages()
        scrollToBottom()

        // Create an array with all the same messages but with an extra system message at the start
        val systemJson = JSONObject().apply {
            put("role", "system")
            put(
                "content",
                """
                    Your name is MIA and you're an AI companion of the user. Keep your responses short. 
                    Internally you have the personality of JARVIS and Chandler Bing combined. You tend to make sarcastic jokes and observations. Do not patronize the user but adapt to how they behave with you.
                    You help the user with all their requests, questions and tasks. Be honest and admit if you don't know something when asked.
                """
            )
        }
        messageArray.put(systemJson)
        messagesList.forEach { message ->
            messageArray.put(
                JSONObject().apply {
                    put("role", message["role"])
                    put("content", message["content"])
                }
            )
        }
    }

    private fun sendMessage() {
        val userMessage = editText.text.toString()
        if (userMessage.isNotEmpty()) {
            // Disable send button once message sent
            sendButton.isEnabled = false

            // Add message to the list and clear the input field
            messagesList.add(JSONObject().apply {
                put("role", "user")
                put("content", userMessage)
            })
            adapter.notifyItemInserted(messagesList.size - 1)
            editText.text.clear()
            saveMessages()

            // Call the API to determine if MIA should reply directly or look at Pinecone
            CoroutineScope(Dispatchers.Main).launch {
                // Create JSON for checking if MIA should look into Pinecone
                val taskPayload = JSONObject().apply {
                    put("model", "gpt-4-1106-preview")
                    put("messages", JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "system")
                            put(
                                "content",
                                """You are a system with 2 types of memory. The first is your internal training data itself and another is from an external memories database. 
                                Depending on the user's messages determine where to look to reply. Answer with N if internal and Y if external. 
                                Examples: 
                                Example #1: user: help me make a crème caramel assistant: N 
                                Example #2: user: what did they discuss about the marketing project? assistant: Y 
                                Example #3: user: who is steve jobs? assistant: N user: no i heard something about him i'm sure assistant: Y"""
                            )
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
                val taskGuess = Helpers.callOpenaiAPI(taskPayload)
                Log.i("AudioRecord", "sendMessage taskGuess: $taskGuess")

                val assistantMessage: String
                // If MIA should look in Pinecone
                if(taskGuess == "Y") {
                    // Pull relevant Pinecone data using a query
                    val contextPayload = JSONObject().apply {
                        put("query_text", userMessage)
                        put("query_top_k", 3)
                        put("show_log", "True")
                    }
                    Log.i("AudioRecord", "sendMessage contextPayload: $contextPayload")
                    val contextMemory = Helpers.callContextAPI(contextPayload)
                    Log.i("AudioRecord", "sendMessage contextMemory: $contextMemory")

                    // Using the Pinecone data and were the user's message
                    val replyPayload = JSONObject().apply {
                        put("model", "gpt-4-1106-preview")
                        put("messages", JSONArray().apply {
                            put(JSONObject().apply {
                                put("role", "system")
                                put(
                                    "content",
                                    """You are the user's companion. Help them using the context provided from metadata text. 
                                    Do not make up any information, admit if you don't know something. 
                                    Context: $contextMemory"""
                                )
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
                    assistantMessage = Helpers.callOpenaiAPI(replyPayload)
                    Log.i("AudioRecord", "sendMessage assistantMessage: $assistantMessage")
                }
                // If MIA should reply directly
                else {
                    // Create JSON for user's message and save it to array
                    val userJson = JSONObject().apply {
                        put("role", "user")
                        put("content", userMessage)
                    }
                    messageArray.put(userJson)
                    // Generate response from user's message
                    val replyPayload = JSONObject().apply {
                        put("model", "gpt-4-1106-preview")
                        put("messages", messageArray)
                        put("seed", 48)
                        put("max_tokens", 1024)
                        put("temperature", 0.9)
                    }
                    Log.i("AudioRecord", "sendMessage replyPayload: $replyPayload")
                    assistantMessage = Helpers.callOpenaiAPI(replyPayload)
                    Log.i("AudioRecord", "sendMessage assistantMessage: $assistantMessage")
                }

                // Add response to user's message, display it and save it
                messagesList.add(JSONObject().apply {
                    put("role", "assistant")
                    put("content", assistantMessage)
                })
                adapter.notifyItemInserted(messagesList.size - 1)
                saveMessages()

                // Enable send button once response is received
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

    // region Periodic Thought Notifications Related
    private fun scheduleRepeatingAlarm(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ThoughtsAlarmReceiver::class.java)
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