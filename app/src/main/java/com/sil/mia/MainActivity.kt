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
import kotlin.math.abs

class MainActivity : AppCompatActivity() {
    // region Vars
    private val initRequestCode = 100
    private val backgroundlocationRequestCode = 104
    private var userMessage: String = ""

    private lateinit var recyclerView: RecyclerView
    private lateinit var loudnessTextView: TextView
    private lateinit var editText: EditText
    private lateinit var sendButton: ImageButton
    private lateinit var toggleButton: ToggleButton

    private lateinit var audioUpdateReceiver: BroadcastReceiver
    private lateinit var adapter: MessagesAdapter
    private val messagesListUI = mutableListOf<JSONObject>()
    private val messagesListData = mutableListOf<JSONObject>()
    private lateinit var messagesUiSharedPref: SharedPreferences
    private lateinit var messagesDataSharedPref: SharedPreferences

    private val alarmIntervalInMin: Long = 2
    // endregion

    // region Common
    override fun onCreate(savedInstanceState: Bundle?) {
        Log.i("AudioRecord", "onCreate")
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        permissionRelated()
        audioRelated()
        chatRelated()
        // scheduleRepeatingAlarm(this)
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
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
            || ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            || ContextCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS) != PackageManager.PERMISSION_GRANTED
            || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
            || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            val permList = arrayOf(
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.POST_NOTIFICATIONS,
                Manifest.permission.BODY_SENSORS,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            )
            ActivityCompat.requestPermissions(this, permList, initRequestCode)
            Log.i("AudioRecord", "Requesting permissions: $permList")
        }
    }
    private fun getBackgroundLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            val permList = arrayOf(
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            )
            ActivityCompat.requestPermissions(this, permList, backgroundlocationRequestCode)
            Log.i("AudioRecord", "Requesting permissions: $permList")
        }
    }
    private fun getBatteryUnrestrictedPermission() {
        if (!(this.getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(this.packageName)) {
            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            startActivity(intent)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            initRequestCode -> {
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    getBackgroundLocationPermission()
                } else {
                    // showToast("Permission denied")
                    // toggleButton.isChecked = false
                }
                return
            }
            backgroundlocationRequestCode -> {
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    getBatteryUnrestrictedPermission()
                } else {
                    // showToast("Permission denied")
                    // toggleButton.isChecked = false
                }
                return
            }
            else -> {
                // Ignore all other requests.
            }
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

        // Reference the loudness TextView and update its value using the BroadcastReceiver
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

        adapter = MessagesAdapter(messagesListUI)
        recyclerView.adapter = adapter
        recyclerView.scrollToPosition(adapter.itemCount - 1)

        CoroutineScope(Dispatchers.Main).launch {
            loadMessages()
        }
    }
    private fun loadMessages() {
        messagesUiSharedPref = getSharedPreferences("com.sil.mia.messagesui", Context.MODE_PRIVATE)
        messagesDataSharedPref = getSharedPreferences("com.sil.mia.messagesdata", Context.MODE_PRIVATE)
        val messagesUiJson = messagesUiSharedPref.getString("messagesui", null)
        val messagesDataJson = messagesDataSharedPref.getString("messagesdata", null)
        // If saved messages are empty then add a message from MIA and save it
        if (messagesUiJson == null || messagesDataJson == null) {
            val initSystemJson = JSONObject().apply {
                put("role", "system")
                put(
                    "content",
                    """
                    Your name is MIA and you're an AI companion of the user. Keep your responses short. 
                    Internally you have the personality of JARVIS and Chandler Bing combined. You tend to make sarcastic jokes and observations. 
                    Do not patronize the user but adapt to how they behave with you. NEVER explicitly mention your personality or that you're an AI.
                    You have access to the user's histories and memories through an external database. Be honest and admit if you don't know something by saying you don't remember.
                    The user's message may or may not contain:
                    - Some historical conversation transcript as context. 
                    - Extra data like their current location, battery level etc.
                    Do not call these out but use if needed. You help the user with all their requests, questions and tasks. 
                    Reply like a close friend would in a conversational manner without any formatting, bullet points etc.
                    """
                )
                put("time", Helpers.pullTimeFormattedString())
            }
            messagesListData.add(initSystemJson)
            val firstAssistantJson = JSONObject().apply {
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
            messagesListData.add(firstAssistantJson)
            messagesListUI.add(firstAssistantJson)
        }
        // If saved messages exist then pull and populate messages
        else {
            val type = object : TypeToken<List<JSONObject>>() {}.type
            messagesListData.addAll(Gson().fromJson(messagesDataJson, type))
            messagesListUI.addAll(Gson().fromJson(messagesUiJson, type))
        }
        adapter.notifyItemInserted(messagesListUI.size - 1)
        recyclerView.scrollToPosition(adapter.itemCount - 1)
        saveMessages()
    }

    private fun sendMessage() {
        userMessage = editText.text.toString()
        if (userMessage.isNotEmpty()) {
            // Disable send button once message sent
            sendButton.isEnabled = false
            val userJSON = JSONObject().apply {
                put("role", "user")
                put("content", userMessage)
                put("time", Helpers.pullTimeFormattedString())
            }
            messagesListUI.add(userJSON)
            adapter.notifyItemInserted(messagesListUI.size - 1)
            recyclerView.scrollToPosition(adapter.itemCount - 1)
            editText.text.clear()
            saveMessages()

            // Call the API to determine if MIA should reply directly or look at Pinecone
            CoroutineScope(Dispatchers.Main).launch {
                val assistantMessage = createMiaResponse()

                // Add response to user's message, display it and save it
                val assistantJSON = JSONObject().apply {
                    put("role", "assistant")
                    put("content", assistantMessage)
                    put("time", Helpers.pullTimeFormattedString())
                }
                messagesListUI.add(assistantJSON)
                adapter.notifyItemInserted(messagesListUI.size - 1)
                recyclerView.scrollToPosition(adapter.itemCount - 1)
                saveMessages()

                // Enable send button once response is received
                sendButton.isEnabled = true
            }
        }
    }
    private suspend fun createMiaResponse(): String {
        // If MIA should should look into Pinecone or reply directly
        val userMessageWithOrWithoutContext =
            if(shouldMiaLookExternally())
                ifUserMessageIsTask()
            else
                ifUserMessageIsNotTask()

        val systemData = Helpers.pullDeviceData(this@MainActivity)
        val finalUserMessage = "$userMessageWithOrWithoutContext\nExtra Data:\n$systemData"

        // Append JSON for user's message with/without context
        val userJSON = JSONObject().apply {
            put("role", "user")
            put("content", finalUserMessage)
            put("time", Helpers.pullTimeFormattedString())
        }
        messagesListData.add(userJSON)

        // Remove time key from array to send to OpenaAI API
        val messagesArrayWithoutTime = Helpers.removeKeyFromJsonList(messagesListData)

        // Generate response from user's message
        val replyPayload = JSONObject().apply {
            put("model", "gpt-4-1106-preview")
            put("messages", messagesArrayWithoutTime)
            put("seed", 48)
            put("max_tokens", 1024)
            put("temperature", 0)
        }
        Log.i("AudioRecord", "createMiaResponse replyPayload: $replyPayload")
        val assistantMessage = Helpers.callOpenaiAPI(replyPayload)
        Log.i("AudioRecord", "createMiaResponse assistantMessage: $assistantMessage")

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
                        Depending on the user's messages determine where to look to reply. Answer with "int" if internal and "ext" if external else "none". 
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
        Log.i("AudioRecord", "shouldMiaLookExternally taskPayload: $taskPayload")
        val taskGuess = Helpers.callOpenaiAPI(taskPayload)
        Log.i("AudioRecord", "shouldMiaLookExternally taskGuess: $taskGuess")

        return taskGuess == "ext"
    }
    private suspend fun ifUserMessageIsNotTask(): String {
        return userMessage
    }
    private suspend fun ifUserMessageIsTask(): String {
        val contextData = Helpers.pullDeviceData(this@MainActivity)
        val finalUserMessage = "$userMessage\nContext:\n$contextData"

        // Use GPT to create a filter
        val queryGeneratorPayload = JSONObject().apply {
            put("model", "gpt-4")
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put(
                        "content",
                        """
                        You are a system that takes a user message and context as a JSON and outputs a JSON payload to query a vector database.

                        The input contains:
                         - user message
                        - a systemtime (Unix timestamp in milliseconds) 
                        - currentTimeFormattedString (in format '2023-12-28T12:02:00')
                        The output should contain:
                        - query text with keywords that maximize cosine similarity
                        - systemtime with ${'$'}\gte and ${'$'}\lte
                        
                        The query text can be empty if no particular factual topic specified. Create a JSON payload best suited to answer the user's message. Output only the filter JSON.
                        
                        Examples:
                        Example #1:
                        Input:
                        summarize my marketing project's presentation from last week for me
                        Context: {"systemtime":1703864901927,"currentTimeFormattedString":"Fri 29\/12\/23 10:48","batteryLevel":100,"latitude":39.954080429399724,"longitude":-75.19560390754498,"address":"Sansom Place West, Sansom Street, Powelton Village, Philadelphia, Philadelphia County, Pennsylvania, 19104, United States","firstWeatherDescription":"broken clouds","feelsLike":"7.52","humidity":"76","windSpeed":"3.6","cloudAll":"75"}
                        Output: {"query": "marketing project presentation", {"systemtime": { "$\gte": 1703260101000, "$\lte": 1703864901927, }}}
                        
                        Example #2:
                        Input:
                        what has happened the past couple days
                        Context: {"systemtime":1703864901927,"currentTimeFormattedString":"Fri 29\/12\/23 10:48","batteryLevel":100,"latitude":39.954080429399724,"longitude":-75.19560390754498,"address":"Sansom Place West, Sansom Street, Powelton Village, Philadelphia, Philadelphia County, Pennsylvania, 19104, United States","firstWeatherDescription":"broken clouds","feelsLike":"7.52","humidity":"76","windSpeed":"3.6","cloudAll":"75"}
                        Output: {"query": "", {"systemtime": { "$\gte": 1703692101000, "$\lte": 1703864901927, }}}
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
        Log.i("AudioRecord", "ifUserMessageIsTask queryGeneratorPayload: $queryGeneratorPayload")
        val queryResponse = Helpers.callOpenaiAPI(queryGeneratorPayload)
        val queryResultJSON = JSONObject(queryResponse)
        Log.i("AudioRecord", "ifUserMessageIsTask queryResultJSON: $queryResultJSON")

        // Pull relevant Pinecone data using a query
        val queryText = if (queryResultJSON.has("query")) queryResultJSON.getString("query") else userMessage
        val filterJSONObject = JSONObject().apply {
            put("systemtime", JSONObject().apply {
                // Check if `$lte` exists and get its value if it does
                if (queryResultJSON.has("systemtime") && queryResultJSON.getJSONObject("systemtime").has("\$lte")) {
                    val lte = abs(queryResultJSON.getJSONObject("systemtime").getInt("\$lte"))
                    put("\$lte", lte)
                }
                // Check if `$gte` exists and get its value if it does
                if (queryResultJSON.has("systemtime") && queryResultJSON.getJSONObject("systemtime").has("\$gte")) {
                    val gte = abs(queryResultJSON.getJSONObject("systemtime").getInt("\$gte"))
                    put("\$gte", gte)
                }
            })
        }
        val contextPayload = JSONObject().apply {
            put("query_text", queryText)
            put("query_filter", filterJSONObject)
            put("query_top_k", 5)
            put("show_log", "True")
        }
        Log.i("AudioRecord", "ifUserMessageIsTask contextPayload: $contextPayload")
        val contextMemory = Helpers.callContextAPI(contextPayload)
        Log.i("AudioRecord", "ifUserMessageIsTask contextMemory: $contextMemory")

        return "$userMessage\nContext:\n$contextMemory"
    }

    private fun saveMessages() {
        val messagesUiEditor = messagesUiSharedPref.edit()
        val messagesUiJson = Gson().toJson(messagesListUI)
        messagesUiEditor.putString("messagesui", messagesUiJson)
        messagesUiEditor.apply()

        val messagesDataEditor = messagesDataSharedPref.edit()
        val messagesDataJson = Gson().toJson(messagesListData)
        messagesDataEditor.putString("messagesdata", messagesDataJson)
        messagesDataEditor.apply()
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