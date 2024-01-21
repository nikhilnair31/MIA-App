package com.sil.mia

import android.app.ActivityManager
import android.app.AlarmManager
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.content.*
import android.os.*
import android.util.Log
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.sil.services.AudioService
import com.sil.others.Helpers
import com.sil.adapters.MessagesAdapter
import com.sil.receivers.ThoughtsAlarmReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class Main : AppCompatActivity() {
    // region Vars
    private var userMessage: String = ""

    private lateinit var recyclerView: RecyclerView
    private lateinit var editText: EditText
    private lateinit var sendButton: ImageButton
    private lateinit var toggleButton: ToggleButton
    private lateinit var dataButton: ImageButton

    private lateinit var generalSharedPref: SharedPreferences
    private lateinit var messagesSharedPref: SharedPreferences
    private lateinit var dataSharedPref: SharedPreferences

    private lateinit var adapter: MessagesAdapter
    private var messagesListComplete = JSONArray()
    private var messagesListData = JSONArray()
    private var messagesListUI = JSONArray()

    private val lookExtSystemPrompt = """
You are a system with 2 types of memory. The first is your internal chat training data itself and another is from an external memories database. 
You will receive the user's conversation history along with the latest message. Based on this determine where to look to reply. ONLY answer with 'int' if internal and 'ext' if external else 'none'. 
Examples: 
- user: help me make a crème caramel assistant: 'int' 
- user: what did they discuss about the marketing project? assistant: 'ext' 
- assistant: Apologies if the response seemed robotic. What is on your mind? user: bruhhh where's the personality at assistant: 'int'
- user: who is steve jobs? assistant: the ceo of apple user: no tell me what i've heard about him assistant: 'ext'
- user: what is my opinion on niche music assistant: 'int'
- user: just thinking about last night assistant: 'ext'
- user: yoooo assistant: 'int'
    """
    private val queryGeneratorSystemPrompt = """
You are a system that takes a user message and context as a JSON and outputs a JSON payload to query a vector database.

The input contains:
- user message
- systemTime (Unix timestamp in milliseconds) 
- currentTimeFormattedString (in format '2023-12-28T12:02:00')
- day (1-31)
- month (1-12)
- year (in format 20XX)
- hours (in 24h format)
- minutes (0-59)
The output should contain:
- query text with keywords that maximize cosine similarity
- any of the time filters with a $\gte and $\lte value based on user's request

The query text can be empty if no particular factual topic specified. 
Create a JSON payload best suited to answer the user's message. 
NEVER output like ```json\n{}\n```, it should just be {}. Output only the filter JSON.

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
    private val initSystemPrompt = """
Your name is MIA and you're the user's AI best friend and companion. Keep your responses short. You help the user with all their requests, questions and tasks.
Internally you have the personality of JARVIS and Chandler Bing combined. You tend to make subtle sarcastic jokes and observations. 
Do not patronize the user. NEVER explicitly mention your personality or that you're an AI.
You have the capability to access the user's histories/memories/events/personal data through an external database that has been shared with you. Be honest and admit if you don't know something by ONLY saying you don't remember.
Ask for more details and specifics about the user and their messages, like a close friend would. 
Reply like a close friend would in a casual low-key conversational texting style without any formatting, bullet points etc. Match the user's texting style.
The user's message will contain:
- Real-Time System Data
- Context Memory of historical conversation transcripts
Use these as needed. NEVER explicitly show these to the user.
    """
    private val initAssistantPrompt = """
hey i'm MIA. what's up?
    """

    private val alarmIntervalInMin: Double = 21.0
    private val maxDataMessages: Int = 20
    // endregion

    // region Common
    override fun onCreate(savedInstanceState: Bundle?) {
        // Log.i("Main", "onCreate")

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initRelated()
        audioRelated()
        chatRelated()
        thoughtsRelated()
    }
    // endregion

    // region Initial
    private fun initRelated() {
        dataSharedPref = getSharedPreferences("com.sil.mia.data", Context.MODE_PRIVATE)
        generalSharedPref = getSharedPreferences("com.sil.mia.generalSharedPrefs", Context.MODE_PRIVATE)

        generalSharedPref.edit().putBoolean("isFirstRun", false).apply()
        val userName = generalSharedPref.getString("userName", null)
        Log.i("Main", "initRelated userName: $userName")

        dataButton = findViewById(R.id.dataButton)
        dataButton.setOnClickListener {
            val intent = Intent(this, DataDump::class.java)
            this.startActivity(intent)
        }
    }
    // endregion

    // region Audio Related Functions
    private fun audioRelated() {
        Log.i("Main", "audioRelated")
        
        toggleButton = findViewById(R.id.toggleButton)
        if (isServiceRunning(AudioService::class.java)) {
            Log.i("Main", "Service IS Running")
            toggleButton.isChecked = true
        }
        toggleButton.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                Log.i("Main", "startService")
                startForegroundService(Intent(this, AudioService::class.java))
            } else {
                Log.i("Main", "stopService")
                stopService(Intent(this, AudioService::class.java))
            }
        }
    }
    private fun isServiceRunning(serviceClass: Class<*>): Boolean {
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        for (service in manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.name == service.service.className) {
                return true
            }
        }
        return false
    }
    // endregion

    // region Chat Related Functions
    private fun chatRelated() {
        // Log.i("Main", "chatRelated")

        setupChat()

        editText = findViewById(R.id.editText)
        sendButton = findViewById(R.id.buttonSend)
        sendButton.setOnClickListener {
            sendMessage()
        }
    }
    private fun setupChat() {
        // Log.i("Main", "setupChat")

        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)

        adapter = MessagesAdapter(messagesListUI)
        recyclerView.adapter = adapter

        // Load messages and update RecyclerView
        loadMessages()
        adapter.updateMessages(messagesListUI)
        recyclerView.scrollToPosition(adapter.itemCount - 1)
    }

    private fun sendMessage() {
        userMessage = editText.text.toString()
        if (userMessage.isNotEmpty()) {
            // Disable send button once message sent
            sendButton.isEnabled = false
            val userJSON = JSONObject().apply {
                put("role", "user")
                put("content", userMessage)
            }
            messagesListUI.put(userJSON)
            adapter.notifyItemInserted(messagesListUI.length() - 1)
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
                }
                messagesListComplete.put(assistantJSON)
                messagesListData.put(assistantJSON)
                messagesListUI.put(assistantJSON)
                adapter.notifyItemInserted(messagesListUI.length() - 1)
                recyclerView.scrollToPosition(adapter.itemCount - 1)
                saveMessages()

                // Enable send button once response is received
                sendButton.isEnabled = true
            }
        }
    }
    private suspend fun createMiaResponse(): String {
        val systemData = Helpers.pullDeviceData(this@Main, null)
        Log.i("Main", "createMiaResponse systemData\n$systemData")
        val updatedUserMessage = "$userMessage\nExtra Data Dump:\n$systemData"
        Log.i("Main", "createMiaResponse updatedUserMessage\n$updatedUserMessage")

        val messagesListUiCopy = Helpers.messageDataWindow(messagesListUI.toString(), 5)
        messagesListUiCopy.put(JSONObject().apply {
            put("role", "user")
            put("content", updatedUserMessage)
        })
        val conversationHistoryText = messagesListUiCopy.toString()
        Log.i("Main", "createMiaResponse conversationHistoryText: $conversationHistoryText")

        // If MIA should should look into Pinecone or reply directly
        val withOrWithoutContextMemory =
            if(shouldMiaLookExternally(conversationHistoryText))
                lookingExternally(conversationHistoryText)
            else
                ""
        val finalUserMessage = "$updatedUserMessage\nContext Memory:\n$withOrWithoutContextMemory"
        Log.i("Main", "createMiaResponse finalUserMessage\n$finalUserMessage")

        // Append JSON for user's message with/without context
        val userJSON = JSONObject().apply {
            put("role", "user")
            put("content", finalUserMessage)
        }
        messagesListComplete.put(userJSON)
        messagesListData.put(userJSON)

        // Generate response from user's message
        val replyPayload = JSONObject().apply {
            put("model", getString(R.string.gpt4turbo))
            put("messages", messagesListData)
            put("seed", 48)
            put("max_tokens", 512)
            put("temperature", 0)
        }
        Log.i("Main", "createMiaResponse replyPayload\n$replyPayload")
        print("createMiaResponse replyPayload: $replyPayload")
        val assistantMessage = withContext(Dispatchers.IO) {
            Helpers.callOpenAiChatAPI(replyPayload)
        }
        Log.i("Main", "createMiaResponse assistantMessage\n$assistantMessage")

        return assistantMessage
    }
    private suspend fun shouldMiaLookExternally(conversationHistoryText: String): Boolean {
        val taskPayload = JSONObject().apply {
            put("model", getString(R.string.gpt4turbo))
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", lookExtSystemPrompt)
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", conversationHistoryText)
                })
            })
            put("seed", 48)
            put("max_tokens", 24)
            put("temperature", 0)
        }
        Log.i("Main", "shouldMiaLookExternally taskPayload: $taskPayload")

        var taskGuess = withContext(Dispatchers.IO) {
            Helpers.callOpenAiChatAPI(taskPayload)
        }
        taskGuess = taskGuess.replace("'", "").trim()
        Log.i("Main", "shouldMiaLookExternally taskGuess: $taskGuess")

        return taskGuess.contains("ext")
    }
    private suspend fun lookingExternally(conversationHistoryText: String): String {
        // Use GPT to create a query and filter
        val queryGeneratorPayload = JSONObject().apply {
            put("model", getString(R.string.gpt4turbo))
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", queryGeneratorSystemPrompt)
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", conversationHistoryText)
                })
            })
            put("seed", 48)
            put("max_tokens", 256)
            put("temperature", 0)
        }
        val queryResponse = withContext(Dispatchers.IO) {
            Helpers.callOpenAiChatAPI(queryGeneratorPayload)
        }
        val queryResultJSON = JSONObject(queryResponse)

        // Generate JSONArray of embeddings from generated query text
        val queryVectorArrayJson = withContext(Dispatchers.IO) {
            val queryText = queryResultJSON.optString("query", userMessage)
            val queryEmbeddingsOutput = Helpers.callOpenAiEmbeddingAPI(queryText)
            JSONArray().apply {
                for (value in queryEmbeddingsOutput) {
                    put(value)
                }
            }
        }

        // Parse the filter JSON to handle various keys and filter types
        val filterJsonObject = JSONObject().apply {
            // Time based filters
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

            // Source for user based on userName
            val sharedPrefs: SharedPreferences = this@Main.getSharedPreferences("com.sil.mia.generalSharedPrefs", Context.MODE_PRIVATE)
            val userName = sharedPrefs.getString("userName", null)
            put("username", userName)
        }

        // Final output vectors to be used as context memory
        val contextMemory = withContext(Dispatchers.IO) {
            Helpers.callPineconeFetchAPI(queryVectorArrayJson, filterJsonObject, 10) { _ , _ -> }
        }.toString()

        return contextMemory
    }

    private fun loadMessages() {
        // Log.i("Main", "loadMessages")

        messagesSharedPref = getSharedPreferences("com.sil.mia.messages", Context.MODE_PRIVATE)

        val messagesCompleteString = messagesSharedPref.getString("complete", null)
        val messagesDataString = messagesSharedPref.getString("data", null)
        val messagesUiString = messagesSharedPref.getString("ui", null)
        // Log.i("Main", "messagesCompleteString\n$messagesCompleteString\nmessagesDataString\n$messagesDataString\nmessagesUiString\n$messagesUiString")

        // If saved messages are empty then add a message from MIA and save it
        if (messagesUiString == null && messagesDataString == null) {
            Log.i("Main", "no messages saved")

            val initSystemJson = JSONObject().apply {
                put("role", "system")
                put("content", initSystemPrompt)
            }
            messagesListComplete.put(initSystemJson)
            messagesListData.put(initSystemJson)
            val firstAssistantJson = JSONObject().apply {
                put("role", "assistant")
                put("content", initAssistantPrompt)
            }
            messagesListComplete.put(firstAssistantJson)
            messagesListData.put(firstAssistantJson)
            messagesListUI.put(firstAssistantJson)
            // Log.i("Main", "messagesListComplete\n$messagesListComplete\nmessagesListData\n$messagesListData\nmessagesListUI\n$messagesListUI")
            adapter.notifyItemInserted(messagesListUI.length() - 1)
            saveMessages()
        }
        // If saved messages exist then pull and populate messages
        else {
            Log.i("Main", "messages exist!")

            messagesListComplete = Helpers.messageDataWindow(messagesCompleteString, null)
            messagesListData = Helpers.messageDataWindow(messagesDataString, maxDataMessages)
            messagesListUI = Helpers.messageDataWindow(messagesUiString, null)
            Log.i("Main", "messagesListComplete: ${messagesListComplete.length()}\nmessagesListData: ${messagesListData.length()}\nmessagesListUI: ${messagesListUI.length()}")

            adapter.notifyDataSetChanged()
        }
        recyclerView.scrollToPosition(adapter.itemCount - 1)
    }
    private fun saveMessages() {
        // Log.i("Main", "saveMessages")

        val messagesCompleteString = messagesListComplete.toString()
        messagesSharedPref.edit().putString(getString(R.string.dataComplete), messagesCompleteString).apply()

        val messagesDataString = messagesListData.toString()
        messagesSharedPref.edit().putString(getString(R.string.dataData), messagesDataString).apply()

        val messagesUiString = messagesListUI.toString()
        messagesSharedPref.edit().putString(getString(R.string.dataUi), messagesUiString).apply()
    }
    // endregion

    // region Periodic Thought Notifications Related
    private fun thoughtsRelated() {
        val alarmManager = this.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, ThoughtsAlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(this, 0, intent, FLAG_IMMUTABLE)

        // Set the alarm to wake up the device and fire approximately every N minutes
        val intervalInMin : Double = alarmIntervalInMin * 60 * 1000

        // `setInexactRepeating()` is battery-friendly as it allows the system to adjust the alarm's timing to match other alarms
        alarmManager.setInexactRepeating(
            AlarmManager.RTC_WAKEUP,
            System.currentTimeMillis(),
            intervalInMin.toLong(),
            pendingIntent
        )
    }
    // endregion
}