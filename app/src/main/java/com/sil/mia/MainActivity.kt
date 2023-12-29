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
    private var userMessage: String = ""

    private lateinit var recyclerView: RecyclerView
    private lateinit var loudnessTextView: TextView
    private lateinit var editText: EditText
    private lateinit var sendButton: ImageButton
    private lateinit var toggleButton: ToggleButton

    private lateinit var audioUpdateReceiver: BroadcastReceiver
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

        permissionRelated()
        audioRelated()
        chatRelated()
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
    private fun permissionRelated() {
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
    private fun audioRelated() {
        toggleButton = findViewById(R.id.toggleButton)
        toggleButton.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                Log.i("AudioRecord", "startService")
                startForegroundService(Intent(this, AudioRelated::class.java))
            }
            else {
                Log.i("AudioRecord", "stopService")
                stopService(Intent(this, AudioRelated::class.java))
            }
        }

        setupAudioUpdateReceiver()
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
    private fun chatRelated() {
        setupChat()

        editText = findViewById(R.id.editText)
        sendButton = findViewById(R.id.buttonSend)
        sendButton.setOnClickListener {
            sendMessage()
        }
    }
    private fun setupChat() {
        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)

        adapter = MessagesAdapter(messagesList)
        recyclerView.adapter = adapter
        recyclerView.scrollToPosition(adapter.itemCount - 1)

        CoroutineScope(Dispatchers.Main).launch {
            loadMessages()
        }
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
                    put("time", Helpers.pullTimeFormattedString())
                }
            )
        }
        // If saved messages exist then pull and populate messages
        else {
            val type = object : TypeToken<List<JSONObject>>() {}.type
            messagesList.addAll(Gson().fromJson(messagesJson, type))
        }
        adapter.notifyItemInserted(messagesList.size - 1)
        recyclerView.scrollToPosition(adapter.itemCount - 1)
        saveMessages()

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
            put("time", Helpers.pullTimeFormattedString())
        }
        messageArray.put(systemJson)
        messagesList.forEach { message ->
            messageArray.put(
                JSONObject().apply {
                    put("role", message["role"])
                    put("content", message["content"])
                    put("time", message["time"])
                }
            )
        }
    }

    private fun sendMessage() {
        userMessage = editText.text.toString()
        if (userMessage.isNotEmpty()) {
            // Disable send button once message sent
            sendButton.isEnabled = false

            // Add message to the list and clear the input field
            messagesList.add(JSONObject().apply {
                put("role", "user")
                put("content", userMessage)
                put("time", Helpers.pullTimeFormattedString())
            })
            adapter.notifyItemInserted(messagesList.size - 1)
            recyclerView.scrollToPosition(adapter.itemCount - 1)
            editText.text.clear()
            saveMessages()

            // Call the API to determine if MIA should reply directly or look at Pinecone
            CoroutineScope(Dispatchers.Main).launch {
                val assistantMessage = createMiaResponse()

                // Add response to user's message, display it and save it
                messagesList.add(JSONObject().apply {
                    put("role", "assistant")
                    put("content", assistantMessage)
                    put("time", Helpers.pullTimeFormattedString())
                })
                adapter.notifyItemInserted(messagesList.size - 1)
                recyclerView.scrollToPosition(adapter.itemCount - 1)
                saveMessages()

                // Enable send button once response is received
                sendButton.isEnabled = true
            }
        }
    }
    private suspend fun createMiaResponse(): String {
        // If MIA should should look into Pinecone or reply directly
        val updatedUserMessage =
            if(shouldMiaLookExternally())
                ifUserMessageIsTask()
            else
                ifUserMessageIsNotTask()

        // Append JSON for user's message with/without context
        messageArray.put(JSONObject().apply {
            put("role", "user")
            put("content", updatedUserMessage)
            put("time", Helpers.pullTimeFormattedString())
        })

        // Remove time key from array to send to OpenaAI API
        val messagesArrayWithoutTime = Helpers.removeKeyFromJsonArray(messageArray)

        // Generate response from user's message
        val replyPayload = JSONObject().apply {
            put("model", "gpt-4-1106-preview")
            put("messages", messagesArrayWithoutTime)
            put("seed", 48)
            put("max_tokens", 1024)
            put("temperature", 0)
        }
        Log.i("AudioRecord", "sendMessage replyPayload: $replyPayload")
        val assistantMessage = Helpers.callOpenaiAPI(replyPayload)
        Log.i("AudioRecord", "sendMessage assistantMessage: $assistantMessage")

        // Return
        return assistantMessage
    }
    private suspend fun shouldMiaLookExternally(): Boolean {
        val taskPayload = JSONObject().apply {
            put("model", "gpt-4-1106-preview")
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put(
                        "content",
                        """
                        You are a system with 2 types of memory. The first is your internal training data itself and another is from an external memories database. 
                        Depending on the user's messages determine where to look to reply. Answer with "int" if internal and "ext" if external. 
                        Examples: 
                        Example #1: user: help me make a crème caramel assistant: "int"
                        Example #2: user: what did they discuss about the marketing project? assistant: "ext"
                        Example #3: user: who is steve jobs? assistant: "int" user: no i heard something about him i'm sure assistant: "ext"
                        """
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
        Log.i("AudioRecord", "ifMiaShouldLookInPinecone taskPayload: $taskPayload")
        val taskGuess = Helpers.callOpenaiAPI(taskPayload)
        Log.i("AudioRecord", "ifMiaShouldLookInPinecone taskGuess: $taskGuess")

        return taskGuess == "ext"
    }
    private suspend fun ifUserMessageIsNotTask(): String {
        return userMessage
    }
    private suspend fun ifUserMessageIsTask(): String {
        val contextData = Helpers.pullDeviceData(this@MainActivity)

        // Use GPT to create a good query
        val queryTextGeneratorPayload = JSONObject().apply {
            put("model", "gpt-4-1106-preview")
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put(
                        "content",
                        """
                        You are a system that generates queries based on the user's message. 
                        These queries are used to search a vector database using its embedding's cosine similarity.
                        The database itself contains transcripts of the user's continuous daily life audio recordings.
                        You will be provided with some contextual information like current time, location etc. 
                        You may choose to keep it or discard t in the final query.
                        You are to reply with a single line of text that is best suited to extract the relevant information.
                        Context:
                        $contextData"""
                    )
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", userMessage)
                })
            })
            put("seed", 48)
            put("max_tokens", 512)
            put("temperature", 0.9)
        }
        Log.i("AudioRecord", "sendMessage queryTextGeneratorPayload: $queryTextGeneratorPayload")
        val queryTextMessage = Helpers.callOpenaiAPI(queryTextGeneratorPayload)
        Log.i("AudioRecord", "sendMessage queryTextMessage: $queryTextMessage")

        // Use GPT to determine response length
        val queryLengthGeneratorPayload = JSONObject().apply {
            put("model", "gpt-4-1106-preview")
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put(
                        "content",
                        """
                        You are a system that outputs query length based on the user's message. 
                        If message has anything to do with time then output "all" else "few". 
                        You are to reply with a single word.
                        Context:
                        $contextData
                        Example:
                        Input: what movie did i watch a few days ago?
                        Output: all"""
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
        Log.i("AudioRecord", "sendMessage queryLengthGeneratorPayload: $queryLengthGeneratorPayload")
        val queryLengthMessage = Helpers.callOpenaiAPI(queryLengthGeneratorPayload)
        val lengthTopK = if (queryLengthMessage == "all") 10000 else 3
        Log.i("AudioRecord", "sendMessage queryLengthMessage: $queryLengthMessage - lengthTopK: $lengthTopK")

        // Pull relevant Pinecone data using a query
        val contextPayload = JSONObject().apply {
            put("query_text", queryTextMessage)
            put("query_top_k", lengthTopK)
            put("show_log", "True")
        }
        Log.i("AudioRecord", "sendMessage contextPayload: $contextPayload")
        val contextMemory = Helpers.callContextAPI(contextPayload)
        Log.i("AudioRecord", "sendMessage contextMemory: $contextMemory")

        return "$userMessage\nContext:\n$contextMemory"
    }

    private fun saveMessages() {
        val editor = sharedPref.edit()
        val messagesJson = Gson().toJson(messagesList)
        editor.putString("messages", messagesJson)
        editor.apply()
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