package com.sil.mia

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Environment
import android.os.SystemClock
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
    private val threshold = 5
    private val timeoutInSec = 3
    private val maxRecordingTimeInSec = 600
    private val minRecordingTimeInSec = 5

    private val openaiApiKey = BuildConfig.OPENAI_API_KEY
    private val bucketName = BuildConfig.BUCKET_NAME
    private val awsAccessKey = BuildConfig.AWS_ACCESS_KEY
    private val awsSecretKey = BuildConfig.AWS_SECRET_KEY
    private val awsApiEndpoint = BuildConfig.AWS_API_ENDPOINT

    private lateinit var recyclerView: RecyclerView
    private lateinit var editText: EditText
    private lateinit var sendButton: ImageButton
    private lateinit var toggleButton: ToggleButton
    private lateinit var audioRecord: AudioRecord
    private var mediaRecorder: MediaRecorder? = null
    private var isListening = false
    private var isRecording = false
    private var audioFile: File? = null
    private val recordRequestCode = 101
    private val sampleRate = 44100
    private var recordingStartTime: Long = 0
    private var lastTimeAboveThreshold = System.currentTimeMillis()
    private lateinit var adapter: MessagesAdapter
    data class Message(val content: String, val isUser: Boolean)
    private val messageArray = JSONArray()
    private val messagesList = mutableListOf<Message>()
    private lateinit var sharedPref: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        sharedPref = getSharedPreferences("com.sil.mia.messages", Context.MODE_PRIVATE)

        setupListeningButton()
        setupChatLayout()
    }
    // endregion

    // region Audio Related Functions
    private fun setupListeningButton() {
        toggleButton = findViewById(R.id.toggleButton)
        toggleButton.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                startListening()
            }
            else {
                if (isListening) stopListening()
            }
        }
    }

    private fun startListening() {
        val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                recordRequestCode
            )
        }
        else {
            audioRecord = AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.MIC)
                .setAudioFormat(
                    AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                    .build()
                )
                .setBufferSizeInBytes(minBufferSize)
                .build()

            isListening = true
            audioRecord.startRecording()
            // showToast("Started Listening!")
            Log.i("AudioRecord", "Started Listening!")

            Thread {
                val audioData = ShortArray(minBufferSize)
                var isCurrentlyRecording = false

                while (isListening) {
                    val numberOfShort = audioRecord.read(audioData, 0, minBufferSize)
                    val rmsValue = rms(audioData, numberOfShort) / 100000
                    val formattedRmsValue = String.format("%.1f", rmsValue)
                    //Log.i("AudioRecord", "rmsValue: $formattedRmsValue")

                    // Run on the UI thread to update the TextView
                    runOnUiThread {
                        val textView = findViewById<TextView>(R.id.textView)
                        textView.text = "Loudness: $formattedRmsValue"
                    }

                    if (rmsValue > threshold) {
                        lastTimeAboveThreshold = System.currentTimeMillis()
                        if (!isCurrentlyRecording) {
                            isCurrentlyRecording = true
                            startRecording()
                        }
                    }
                    else if (isCurrentlyRecording) {
                        val timeDiff = System.currentTimeMillis() - lastTimeAboveThreshold
                        if (timeDiff >= timeoutInSec*1000) {
                            stopRecording("Stopped recording due to silence timeout.")
                            isCurrentlyRecording = false
                        }
                    }

                    // Wait for X milliseconds before updating again
                    try {
                        Thread.sleep(2)
                    }
                    catch (e: InterruptedException) {
                        // Handle the exception if the Thread is interrupted
                        e.printStackTrace()
                    }
                }
            }.start()
        }
    }
    private fun stopListening() {
        // showToast("Stopped Listening")
        Log.i("AudioRecord", "Stopped Listening")

        if (isRecording) {
            stopRecording("Stopped Listening so stopping Recording")
        }
        isListening = false
        // Stop and release AudioRecord if it's initialized
        if (::audioRecord.isInitialized && audioRecord.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
            audioRecord.stop()
            audioRecord.release()
        }
    }
    private fun startRecording() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                recordRequestCode
            )
        }
        else {
            mediaRecorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)

                // Set the maximum recording duration to N minutes (N*1000 milliseconds)
                setMaxDuration(maxRecordingTimeInSec*1000)

                // Set an output file in Documents folder
                audioFile = File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "recording_${SystemClock.elapsedRealtime()}.m4a")
                val audioFilePath = audioFile?.absolutePath
                setOutputFile(audioFilePath)
                Log.i("AudioRecord", "setOutputFile $audioFilePath")

                // On max duration reached, stop the recording
                setOnInfoListener { _, what, _ ->
                    if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) {
                        stopRecording("Max Recording Time!")
                    }
                }

                try {
                    prepare()
                    start()
                    isRecording = true
                    recordingStartTime = System.currentTimeMillis()
                    showToast("Started Recording")
                    Log.i("AudioRecord", "Started Recording")
                }
                catch (e: IOException) {
                    showToast("Recording failed to start")
                    Log.e("AudioRecord", "Recording failed to start")
                }
            }
        }

        // val serviceIntent = Intent(this, AudioRecordingService::class.java)
        // startForegroundService(serviceIntent)
        // isRecording = true
    }
    private fun stopRecording(stopReasonText: String) {
        showToast("Recording stopped")
        Log.i("AudioRecord", "stopRecording: $stopReasonText")

        mediaRecorder?.apply {
            try {
                stop()
                release()
                mediaRecorder = null
            }
            catch (e: RuntimeException) {
                Log.e("AudioRecord", "stop() called before start()")
            }
        }

        isRecording = false
        val recordingEndTime = System.currentTimeMillis()
        if ((recordingEndTime - recordingStartTime) > minRecordingTimeInSec*1000) {
            CoroutineScope(Dispatchers.IO).launch {
                uploadToS3()
            }
        } else {
            Log.i("AudioRecord", "Recording duration was less than minimum. Not uploading.")
        }
    }

    private fun uploadToS3() {
        Log.i("AudioRecord", "Uploading to S3...")

        try {
            val credentials = BasicAWSCredentials(awsAccessKey, awsSecretKey)
            val s3Client = AmazonS3Client(credentials)

            audioFile?.let {
                val keyName = "recordings/" + it.name

                s3Client.putObject(bucketName, keyName, "Uploaded String Object")

                val metadata = ObjectMetadata()
                metadata.contentType = "media/m4a"

                val request = PutObjectRequest(bucketName, keyName, it).withMetadata(metadata)
                s3Client.putObject(request)

                showToast("Uploaded to S3!")
                Log.i("AudioRecord", "Uploaded to S3!")
            }
        }
        catch (e: AmazonServiceException) {
            e.printStackTrace()
            Log.e("AudioRecord", "Error uploading to S3: ${e.message}")
        }
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
        if (messagesJson != null) {
            val type = object : TypeToken<List<Message>>() {}.type
            messagesList.addAll(Gson().fromJson(messagesJson, type))
            adapter.notifyDataSetChanged()
        }

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
                val taskGuess = callOpenaiAPI(taskPayload)
                Log.i("AudioRecord", "sendMessage taskGuess: $taskGuess")

                val assistantMessage: String
                if(taskGuess == "Y") {
                    val contextPayload = JSONObject().apply {
                        put("query_text", userMessage)
                        put("query_top_k", 3)
                        put("show_log", "True")
                    }
                    Log.i("AudioRecord", "sendMessage contextPayload: $contextPayload")
                    val contextMemory = callContextAPI(contextPayload)
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
                    assistantMessage = callOpenaiAPI(replyPayload)
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
                    assistantMessage = callOpenaiAPI(replyPayload)
                    Log.i("AudioRecord", "sendMessage assistantMessage: $assistantMessage")
                }

                messagesList.add(Message(assistantMessage, false))
                adapter.notifyItemInserted(messagesList.size - 1)
                saveMessages()
                scrollToBottom()

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

    // region Old Stuff
    private suspend fun startProcessing() {
        // Use the saved audio file and send it for transcription
        val transcriptResponse = transcribeAudio()
        Log.i("AudioRecord", "transcriptResponse: $transcriptResponse")

        // Setup transcription cleaning parameters. Pull USER input for JSON payload, Set SYSTEM prompt for JSON payload, Set MODEL type for JSON payload
        // val modelName = "gpt-3.5-turbo"
        // val cleaningSystemPrompt = "You will receive a user's transcribed speech and are to process it to correct potential errors.\nDO NOT DO THE FOLLOWING:\n- Generate any additional content\n- Censor any of the content\n- Print repetitive content\nDO THE FOLLOWING:\n- Account for transcript include speech of multiple users\n- Only output corrected text\n- If too much of the content seems erroneous return '.'\nTranscription: "
        // val cleanedTranscript = callOpenaiAPI(modelName, cleaningSystemPrompt, transcriptResponse)
        // Log.i("AudioRecord", "cleanedTranscript: $cleanedTranscript")
    }
    private suspend fun transcribeAudio(): String {
        return withContext(Dispatchers.IO) {
            audioFile?.let { file ->
                try {
                    val boundary = "Boundary-${System.currentTimeMillis()}"
                    val lineEnd = "\r\n"
                    val twoHyphens = "--"
                    val audioFilePath = audioFile?.absolutePath

                    val url = URL("https://api.openai.com/v1/audio/translations")
                    val httpURLConnection = (url.openConnection() as HttpURLConnection).apply {
                        requestMethod = "POST"
                        setRequestProperty("Authorization", "Bearer $openaiApiKey")
                        setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                        doOutput = true
                    }

                    DataOutputStream(httpURLConnection.outputStream).use { dos ->
                        dos.writeBytes(twoHyphens + boundary + lineEnd)
                        dos.writeBytes("Content-Disposition: form-data; name=\"model\"$lineEnd")
                        dos.writeBytes(lineEnd)
                        dos.writeBytes("whisper-1")
                        dos.writeBytes(lineEnd)
                        dos.writeBytes(twoHyphens + boundary + lineEnd)
                        dos.writeBytes("Content-Disposition: form-data; name=\"file\";filename=\"$audioFilePath\"$lineEnd")
                        dos.writeBytes("Content-Type: audio/mp4$lineEnd")
                        dos.writeBytes(lineEnd)
                        file.inputStream().copyTo(dos)
                        dos.writeBytes(lineEnd)
                        dos.writeBytes(twoHyphens + boundary + twoHyphens + lineEnd)
                    }

                    val responseCode = httpURLConnection.responseCode
                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        val responseJson = httpURLConnection.inputStream.bufferedReader().use { it.readText() }
                        val jsonResponse = JSONObject(responseJson)
                        val transcriptText = jsonResponse.optString("text", "No transcription found")
                        transcriptText
                    }
                    else {
                        val errorResponse = httpURLConnection.errorStream.bufferedReader().use { it.readText() }
                        Log.e("AudioRecord", "Transcription Error Response: $errorResponse")
                        ""
                    }
                }
                catch (e: Exception) {
                    Log.e("WhisperTranscription", "Exception in transcribing: ", e)
                    ""
                }
            } ?: run {
                Log.e("AudioRecord", "Audio file is null")
                ""
            }
        }
    }
    // endregion

    // region Utility Functions
    private suspend fun callContextAPI(payload: JSONObject): String {
        return withContext(Dispatchers.IO) {
            try {
                Log.i("AudioRecord", "callContextAPI payload: $payload")

                val url = URL(awsApiEndpoint)
                val httpURLConnection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json")
                    doOutput = true
                    outputStream.use { it.write(payload.toString().toByteArray()) }
                }

                val responseCode = httpURLConnection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val responseJson = httpURLConnection.inputStream.bufferedReader().use { it.readText() }
                    val jsonResponse = JSONObject(responseJson)
                    Log.i("AudioRecord", "callContextAPI API Response: $jsonResponse")
                    val content = jsonResponse.getString("output")
                    content
                } else {
                    val errorResponse = httpURLConnection.errorStream.bufferedReader().use { it.readText() }
                    Log.e("AudioRecord", "callContextAPI Error Response: $errorResponse")
                    ""
                }
            } catch (e: IOException) {
                Log.e("AudioRecord", "IO Exception: ${e.message}")
                ""
            } catch (e: Exception) {
                Log.e("AudioRecord", "Exception: ${e.message}")
                ""
            }
        }
    }
    private suspend fun callOpenaiAPI(payload: JSONObject): String {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL("https://api.openai.com/v1/chat/completions")
                val httpURLConnection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Authorization", "Bearer $openaiApiKey")
                    setRequestProperty("Content-Type", "application/json")
                    doOutput = true
                    outputStream.use { it.write(payload.toString().toByteArray()) }
                }

                val responseCode = httpURLConnection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val responseJson = httpURLConnection.inputStream.bufferedReader().use { it.readText() }
                    val jsonResponse = JSONObject(responseJson)
                    Log.i("AudioRecord", "callOpenaiAPI Response: $jsonResponse")
                    val content = jsonResponse.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
                    content
                } else {
                    val errorResponse = httpURLConnection.errorStream.bufferedReader().use { it.readText() }
                    Log.e("AudioRecord", "callOpenaiAPI Error Response: $errorResponse")
                    ""
                }
            } catch (e: IOException) {
                Log.e("AudioRecord", "IO Exception: ${e.message}")
                ""
            } catch (e: Exception) {
                Log.e("AudioRecord", "Exception: ${e.message}")
                ""
            }
        }
    }

    private fun rms(buffer: ShortArray, length: Int): Double {
        var sum = 0.0
        for (i in 0 until length) {
            sum += buffer[i] * buffer[i]
        }
        return kotlin.math.sqrt(sum / length) * 1000
    }

    private fun showToast(message: String) {
        runOnUiThread {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            recordRequestCode -> {
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    startRecording()
                } else {
                    showToast("Permission denied")
                    toggleButton.isChecked = false
                }
                return
            }
            else -> {
                // Ignore all other requests.
            }
        }
    }
    // endregion
}