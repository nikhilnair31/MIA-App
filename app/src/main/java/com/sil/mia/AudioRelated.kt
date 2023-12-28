package com.sil.mia

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Environment
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException

class AudioRelated : Service() {
    // region Vars
    companion object {
        const val AUDIO_SERVICE_UPDATE = "com.sil.mia.AUDIO_SERVICE_UPDATE"
    }

    private var mediaRecorder: MediaRecorder? = null
    private lateinit var audioRecord: AudioRecord
    private var audioFile: File? = null
    private var isListening = false
    private var isRecording = false

    private val sampleRate = 44100
    private var recordingStartTime: Long = 0
    private var lastTimeAboveThreshold = System.currentTimeMillis()

    private val threshold = 5
    private val timeoutInSec = 4
    private val maxRecordingTimeInSec = 600
    private val minRecordingTimeInSec = 6

    private val channelId = "AudioRecordingServiceChannel"
    // endregion

    // region Common
    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        Log.i("AudioRecord", "onStartCommand")

        startForeground(1, createNotification())
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

        val channel = NotificationChannel(channelId, "MIA Listening Channel", NotificationManager.IMPORTANCE_LOW)
        val notificationManager: NotificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)

        return Notification.Builder(this, channelId)
            .setContentTitle("MIA Listening...")
            .setContentText(null)
            .setStyle(Notification.BigTextStyle().bigText(""))
            .setOngoing(true)
            .setSmallIcon(R.drawable.mia_stat_name)
            .build()
    }
    // endregion

    // region Listening and Recording Related
    // Listening Related
    private fun startListening() {
        Log.i("AudioRecord", "startListening")

        val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            return
        }
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
        Log.i("AudioRecord", "Started Listening!")

        Thread {
            val audioData = ShortArray(minBufferSize)
            var isCurrentlyRecording = false

            while (isListening) {
                val numberOfShort = audioRecord.read(audioData, 0, minBufferSize)
                val rmsValue = Helpers.rms(audioData, numberOfShort) / 100000
                val formattedRmsValue = String.format("%.1f", rmsValue)

                val intent = Intent(AUDIO_SERVICE_UPDATE)
                intent.putExtra("formattedRmsValue", formattedRmsValue)
                sendBroadcast(intent)

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
    private fun stopListening() {
        Log.i("AudioRecord", "Stopped Listening")

        val serviceIntent = Intent(this, AudioRelated::class.java)
        stopService(serviceIntent)

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

    // Recording Related
    private fun startRecording() {
        mediaRecorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)

            // Set the maximum recording duration to N minutes (N*1000 milliseconds)
            setMaxDuration(maxRecordingTimeInSec*1000)

            // Set an output file in Documents folder
            // val audioFile = File.createTempFile("recording_${SystemClock.elapsedRealtime()}", ".m4a")
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
                Log.i("AudioRecord", "Started Recording")
            }
            catch (e: IOException) {
                Log.e("AudioRecord", "Recording failed to start")
            }
        }
    }
    private fun stopRecording(stopReasonText: String) {
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
                val metadataJson = Helpers.pullDeviceData(this@AudioRelated)
                Helpers.uploadToS3(audioFile, metadataJson)
            }
        } else {
            Log.i("AudioRecord", "Recording duration was less than minimum. Not uploading.")
        }
    }
    // endregion
}
