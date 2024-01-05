package com.sil.audio

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Environment
import android.os.IBinder
import android.util.Log
import androidx.core.app.ActivityCompat
import com.sil.mia.Main
import com.sil.mia.R
import com.sil.others.Helpers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

class AudioRelated : Service() {
    // region Vars
    private var mediaRecorder: MediaRecorder? = null
    private var latestAudioFile: File? = null
    private val maxRecordingTimeInMin = 20

    private val listeningChannelId = "AudioRecordingServiceChannel"
    private val listeningChannelName = "MIA Listening Channel"
    private val listeningChannelGroup = "MIA Listening Group"
    private val listeningChannelImportance = NotificationManager.IMPORTANCE_LOW
    private val listeningNotificationTitle = "MIA Listening..."
    private val listeningNotificationText = "MIA is active"
    private val listeningNotificationIcon = R.drawable.mia_stat_name
    private val listeningNotificationId = 1
    // endregion

    // region Common
    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        Log.i("AudioRecord", "onStartCommand")

        startForeground(listeningNotificationId, createNotification())
        startListening()
        return START_STICKY
    }
    override fun onDestroy() {
        Log.i("AudioRecord", "onDestroy")

        stopListening()
        super.onDestroy()
    }
    override fun onBind(intent: Intent): IBinder? {
        return null
    }
    // endregion

    // region Notification Related
    private fun createNotification(): Notification {
        Log.i("AudioRecord", "Creating notification channel")

        val notificationManager: NotificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(listeningChannelId, listeningChannelName, listeningChannelImportance)
        notificationManager.createNotificationChannel(channel)

        // Intent to open the main activity
        val intent = Intent(this@AudioRelated, Main::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        val pendingIntent = PendingIntent.getActivity(this@AudioRelated, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        return Notification.Builder(this, listeningChannelId)
            .setContentTitle(listeningNotificationTitle)
            .setContentText(listeningNotificationText)
            .setSmallIcon(listeningNotificationIcon)
            .setGroup(listeningChannelGroup)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
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
        mediaRecorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioSamplingRate(44100) // Set CD quality sampling rate
            setAudioEncodingBitRate(128000) // Set a higher bit rate for better quality
            setAudioChannels(2) // Set for stereo recording
            setMaxDuration(maxRecordingTimeInMin * 60 * 1000)
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
            val serviceIntent = Intent(this, AudioRelated::class.java)
            stopService(serviceIntent)

            mediaRecorder?.apply {
                stop()
                release()
            }
        } catch (e: Exception) {
            Log.e("AudioRecord", "Error stopping media recorder", e)
        } finally {
            mediaRecorder = null
            latestAudioFile?.let { uploadAudioFileAndMetadata(it) }
        }
    }
    private fun stopRecordingAndUpload(audioFile: File) {
        mediaRecorder?.apply {
            stop()
            release()
        }
        mediaRecorder = null
        uploadAudioFileAndMetadata(audioFile)
        startListening()
    }

    private fun createAudioFile(): File {
        val timeStamp = System.currentTimeMillis()
        val audioFileName = "recording_$timeStamp.m4a"
        Log.i("AudioRecord", "Created New Audio File: $audioFileName")
        return File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), audioFileName)
    }
    private fun uploadAudioFileAndMetadata(audioFile: File) {
        CoroutineScope(Dispatchers.IO).launch {
            val context = this@AudioRelated
            val metadataJson = Helpers.pullDeviceData(context)
            Helpers.scheduleUploadWork(context, audioFile, metadataJson)
        }
    }
    // endregion
}
