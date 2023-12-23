package com.sil.mia

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder

class AudioRecordingService : Service() {

    private val channelId = "AudioRecordingServiceChannel"

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        createNotificationChannel()
        val notification: Notification = Notification.Builder(this, channelId)
            .setContentTitle("Audio Recording")
            .setContentText("Recording in progress...")
            .setSmallIcon(R.drawable.ic_baseline_mic_24) // replace with your own icon
            .build()

        startForeground(1, notification)

        // Your audio recording logic here

        return START_STICKY
    }

    private fun createNotificationChannel() {
        val serviceChannel = NotificationChannel(
            channelId,
            "Audio Recording Service Channel",
            NotificationManager.IMPORTANCE_DEFAULT
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(serviceChannel)
    }

    override fun onDestroy() {
        super.onDestroy()
        // Stop your recording or release resources
    }
}
