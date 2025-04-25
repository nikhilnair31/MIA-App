package com.sil.services

import android.Manifest
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AudioService : Service() {
    // region Vars
    private val TAG = "Audio Service"
    
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
        Log.i(TAG, "onStartCommand")

        initRelated()
        return START_STICKY
    }
    override fun onDestroy() {
        Log.i(TAG, "onDestroy")

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

        // Listener related
        sensorListener = SensorListener(this@AudioService)

        // Service related
        startForeground(listeningNotificationId, notificationHelper.createListeningNotification())

        // Set integer values
        maxRecordingTimeInMin = resources.getInteger(R.integer.maxRecordingTimeInMin)
        recordingEncodingBitrate = resources.getInteger(R.integer.recordingEncodingBitrate)
        recordingSamplingRate = resources.getInteger(R.integer.recordingSamplingRate)
        recordingChannels = resources.getInteger(R.integer.recordingChannels)

        startListening()
    }
    // endregion

    // region Listening Related
    private fun startListening() {
        Log.i(TAG, "Started Listening!")

        // Check if audio permission given else log and return
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Recording permission not granted")
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
                Log.e(TAG, "Exception starting media recorder", e)
            }
        }
    }
    private fun stopListening() {
        Log.i(TAG, "Stopped Listening")

        try {
            val serviceIntent = Intent(this, AudioService::class.java)
            stopService(serviceIntent)

            mediaRecorder?.apply {
                stop()
                release()
            }
        }
        catch (e: Exception) {
            Log.e(TAG, "Error stopping media recorder", e)
        }
        finally {
            mediaRecorder = null
            latestAudioFile?.let { uploadAudioFileWithMetadata(it) }
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
                    Log.i(TAG, "Max Recording Duration!")

                    stopRecordingAndUpload(audioFile)
                }
            }
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
        Log.i(TAG, "Created New Audio File: $audioFileName")
        return File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), audioFileName)
    }
    private fun uploadAudioFileWithMetadata(audioFile: File) {
        CoroutineScope(Dispatchers.IO).launch {
            // Get the shared preferences for metadata values
            val sharedPrefs = getSharedPreferences("com.sil.mia.generalSharedPrefs", Context.MODE_PRIVATE)
            val saveAudio = sharedPrefs.getString("saveAudioFiles", "false")
            val preprocessAudio = sharedPrefs.getString("preprocessAudio", "false")

            // Start upload process
            Helpers.scheduleContentUploadWork(
                this@AudioService,
                "audio",
                audioFile,
                saveAudio,
                preprocessAudio
            )
        }
    }
    // endregion
}
