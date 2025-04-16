package com.sil.services

import android.Manifest
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.media.MediaMetadataRetriever
import android.media.MediaRecorder
import android.os.Environment
import android.os.IBinder
import android.util.Log
import androidx.core.app.ActivityCompat
import com.sil.listeners.SensorListener
import com.sil.mia.R
import com.sil.others.Helpers
import com.sil.others.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AudioService : Service() {
    // region Vars
    private lateinit var sensorListener: SensorListener
    private lateinit var notificationHelper: NotificationHelper

    private var mediaRecorder: MediaRecorder? = null
    private var latestAudioFile: File? = null

    private var maxRecordingTimeInMin = 5
    private var recordingEncodingBitrate = 32000
    private var recordingSamplingRate = 16000
    private var recordingChannels = 1

    private val listeningNotificationId = 1
    // endregion

    // region Common
    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        Log.i("AudioRecord", "onStartCommand")
        initRelated()
        return START_STICKY
    }
    override fun onDestroy() {
        Log.i("AudioRecord", "onDestroy")

        sensorListener.unregister()
        stopListening()
        super.onDestroy()
    }
    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    private fun initRelated() {
        // Create the thoughts notification channel
        notificationHelper = NotificationHelper(this)
        notificationHelper.createThoughtsNotificationChannel()

        // Service related
        sensorListener = SensorListener(this@AudioService)
        startForeground(listeningNotificationId, notificationHelper.createListeningNotification())
        startListening()

        // Set integer values
        maxRecordingTimeInMin = resources.getInteger(R.integer.maxRecordingTimeInMin)
        recordingEncodingBitrate = resources.getInteger(R.integer.recordingEncodingBitrate)
        recordingSamplingRate = resources.getInteger(R.integer.recordingSamplingRate)
        recordingChannels = resources.getInteger(R.integer.recordingChannels)
    }
    // endregion

    // region Listening and Recording Related
    private fun startListening() {
        Log.i("AudioRecord", "Started Listening!")

        // Check if audio permission given else log and return
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.e("AudioRecord", "Recording permission not granted")
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            val audioFile: File = createAudioFile()
            latestAudioFile = audioFile
            setupMediaRecorder(audioFile)
            try {
                mediaRecorder?.start()
            }
            catch (e: IllegalStateException) {
                Log.e("AudioRecord", "Exception starting media recorder", e)
            }
        }
    }
    private fun setupMediaRecorder(audioFile: File) {
        val audioSource = MediaRecorder.AudioSource.DEFAULT

        mediaRecorder = MediaRecorder().apply {
            setAudioSource(audioSource)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioEncodingBitRate(recordingEncodingBitrate)
            setMaxDuration(maxRecordingTimeInMin * 60 * 1000)
            setAudioSamplingRate(recordingSamplingRate)
            setAudioChannels(recordingChannels)
            setOutputFile(audioFile.absolutePath)
            prepare()

            setOnInfoListener { _, what, _ ->
                if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) {
                    Log.i("AudioRecord", "Max Recording Duration!")

                    stopRecordingAndUpload(audioFile)
                }
            }
        }
    }

    private fun stopListening() {
        Log.i("AudioRecord", "Stopped Listening")

        try {
            val serviceIntent = Intent(this, AudioService::class.java)
            stopService(serviceIntent)

            mediaRecorder?.apply {
                stop()
                release()
            }
        }
        catch (e: Exception) {
            Log.e("AudioRecord", "Error stopping media recorder", e)
        }
        finally {
            mediaRecorder = null
            latestAudioFile?.let { uploadAudioFileWithMetadata(it) }
        }
    }
    private fun stopRecordingAndUpload(audioFile: File) {
        mediaRecorder?.apply {
            stop()
            release()
        }
        mediaRecorder = null
        uploadAudioFileWithMetadata(audioFile)
        startListening()
    }

    private fun createAudioFile(): File {
        // val timeStamp = System.currentTimeMillis()
        val timeStamp = SimpleDateFormat("ddMMyyyyHHmmss", Locale.getDefault()).format(Date())
        val audioFileName = "recording_$timeStamp.m4a"
        Log.i("AudioRecord", "Created New Audio File: $audioFileName")
        return File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), audioFileName)
    }
    private fun uploadAudioFileWithMetadata(audioFile: File) {
        CoroutineScope(Dispatchers.IO).launch {
            // Create metadata
            val metadata = createMetadataJson(audioFile)

            // Start upload process
            Helpers.scheduleUploadWork(
                this@AudioService,
                audioFile,
                metadata
            )

            // After uploading, call Lambda to check if we should send a notification
            try {
                val notifPaylodJson = JSONObject().apply {
                    put("action", "get_notification")
                    put("username", metadata.get("username"))
                }
                 Log.i("AudioRecord", "notifPaylodJson: $notifPaylodJson")
                val notifLambdaResponseJson = Helpers.callNotificationCheckLambda(this@AudioService, notifPaylodJson)
                 Log.i("AudioRecord", "notifLambdaResponseJson: $notifLambdaResponseJson")
                notificationHelper.checkIfShouldNotify(notifLambdaResponseJson)
            } catch (e: Exception) {
                Log.e("AudioRecord", "Error calling notification lambda", e)
            }
        }
    }
    private fun createMetadataJson(audioFile: File): JSONObject {
        val sharedPrefs: SharedPreferences = this@AudioService.getSharedPreferences("com.sil.mia.generalSharedPrefs", Context.MODE_PRIVATE)
        val metadataJson = JSONObject()

        val audioFileName = audioFile.name

        // Add some extra keys to metadata JSON
        metadataJson.put("filename", audioFileName)
        metadataJson.put("source", "audio")

        // Get duration in seconds
        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(audioFile.absolutePath)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0
            val durationSeconds = durationMs / 1000 // Convert milliseconds to seconds
            metadataJson.put("duration", durationSeconds)
            retriever.release()
        } catch (e: Exception) {
            Log.e("AudioService", "Error getting audio duration: ${e.message}")
            metadataJson.put("duration", 0) // Default value if we can't get the duration
        }

        // Add username and audio downloading/cleaning related metadata
        val userName = sharedPrefs.getString("userName", null)
        metadataJson.put("username", userName)

        // Add audio downloading/cleaning related metadata
        val saveAudioFilesState = sharedPrefs.getString("saveAudioFiles", "false")
        metadataJson.put("saveaudiofile", saveAudioFilesState)
        val preprocessAudioFilesState = sharedPrefs.getString("preprocessAudio", "false")
        metadataJson.put("preprocessaudiofile", preprocessAudioFilesState)

        // Pull individual keys and values from system data into metadata JSON
        val systemData = Helpers.pullDeviceData(this@AudioService, sensorListener)
        for (key in systemData.keys()) {
            metadataJson.put(key, systemData[key])
        }
        // Log.d("Helper", "createMetadataJson systemData: $systemData")

        return metadataJson
    }
    // endregion
}
