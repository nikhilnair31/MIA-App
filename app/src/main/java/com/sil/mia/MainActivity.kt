package com.sil.mia

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Environment
import android.os.SystemClock
import android.util.Log
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.amazonaws.AmazonServiceException
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model.ObjectMetadata
import com.amazonaws.services.s3.model.PutObjectRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    private lateinit var sendButton: Button
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
    private val messagesList = mutableListOf<Message>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

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
        recyclerView.layoutManager = LinearLayoutManager(this) // Set LinearLayoutManager for vertical list
        adapter = MessagesAdapter(messagesList)
        recyclerView.adapter = adapter

        editText = findViewById(R.id.editText)
        sendButton = findViewById(R.id.buttonSend)
        sendButton.setOnClickListener {
            val userMessage = editText.text.toString()
            if (userMessage.isNotEmpty()) {
                // Add message to the list and clear the input field
                messagesList.add(Message(userMessage, true))
                adapter.notifyItemInserted(messagesList.size - 1)
                editText.text.clear()

                // Call the API
                CoroutineScope(Dispatchers.Main).launch {
                    val systemMessage = callContextAPI(userMessage)
                    messagesList.add(Message(systemMessage, false))
                    adapter.notifyItemInserted(messagesList.size - 1)
                }
            }
        }
    }
    // endregion

    // region Old Stuff
    private suspend fun startProcessing() {
        // Use the saved audio file and send it for transcription
        val transcriptResponse = transcribeAudio()
        Log.i("AudioRecord", "transcriptResponse: $transcriptResponse")

        // Setup transcription cleaning parameters. Pull USER input for JSON payload, Set SYSTEM prompt for JSON payload, Set MODEL type for JSON payload
        val modelName = "gpt-3.5-turbo"
        val cleaningSystemPrompt = "You will receive a user's transcribed speech and are to process it to correct potential errors.\nDO NOT DO THE FOLLOWING:\n- Generate any additional content\n- Censor any of the content\n- Print repetitive content\nDO THE FOLLOWING:\n- Account for transcript include speech of multiple users\n- Only output corrected text\n- If too much of the content seems erroneous return '.'\nTranscription: "
        val cleanedTranscript = callOpenaiAPI(modelName, cleaningSystemPrompt, transcriptResponse)
        Log.i("AudioRecord", "cleanedTranscript: $cleanedTranscript")
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
    private suspend fun callOpenaiAPI(model: String, system: String, user: String): String {
        if (user.isEmpty()) return "No transcription available"

        return withContext(Dispatchers.IO) {
            try {
                val payload = """
                {
                    "model": "$model",
                    "messages": [
                        {
                            "role": "system",
                            "content": "${JsonObject.escape(system)}"
                        },
                        {
                            "role": "user",
                            "content": "${JsonObject.escape(user)}"
                        }
                    ]
                }
                """.trimIndent()

                val url = URL("https://api.openai.com/v1/chat/completions")
                val httpURLConnection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Authorization", "Bearer $openaiApiKey")
                    setRequestProperty("Content-Type", "application/json")
                    doOutput = true
                    outputStream.use { it.write(payload.toByteArray()) }
                }

                val responseCode = httpURLConnection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val responseJson = httpURLConnection.inputStream.bufferedReader().use { it.readText() }
                    val jsonResponse = JSONObject(responseJson)
                    val content = jsonResponse.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
                    content
                } else {
                    val errorResponse = httpURLConnection.errorStream.bufferedReader().use { it.readText() }
                    Log.e("AudioRecord", "Error Response: $errorResponse")
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
    // endregion

    // region Utility Functions
    object JsonObject {
        // Helper function to escape special characters in JSON strings
        fun escape(str: String): String {
            return str.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\b", "\\b")
                .replace("\t", "\\t")
        }
    }
    private fun showToast(message: String) {
        runOnUiThread {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }
    private fun rms(buffer: ShortArray, length: Int): Double {
        var sum = 0.0
        for (i in 0 until length) {
            sum += buffer[i] * buffer[i]
        }
        return kotlin.math.sqrt(sum / length) * 1000
    }
    private suspend fun callContextAPI(input_text: String): String {
        return withContext(Dispatchers.IO) {
            try {
                val payload = """
                {
                    "show_log": "True",
                    "input_text": "$input_text"
                }
                """.trimIndent()

                val url = URL(awsApiEndpoint)
                val httpURLConnection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json")
                    doOutput = true
                    outputStream.use { it.write(payload.toByteArray()) }
                }

                val responseCode = httpURLConnection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val responseJson = httpURLConnection.inputStream.bufferedReader().use { it.readText() }
                    val jsonResponse = JSONObject(responseJson)
                    val content = jsonResponse.getString("gpt_output")
                    Log.i("AudioRecord", "API Response: $content")
                    content
                } else {
                    val errorResponse = httpURLConnection.errorStream.bufferedReader().use { it.readText() }
                    Log.e("AudioRecord", "Error Response: $errorResponse")
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