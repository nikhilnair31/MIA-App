package com.sil.receiver

import android.content.Context
import android.content.Intent
import com.sil.services.AudioService
import com.sil.services.ScreenshotService

class BootCompletedReceiver : android.content.BroadcastReceiver() {
    val TAG = "BootCompletedReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val prefs = context.getSharedPreferences("com.sil.mia.generalSharedPrefs", Context.MODE_PRIVATE)

            // Check if audio or screenshot services were active
            if (prefs.getBoolean("isAudioRecordingEnabled", false)) {
                val serviceIntent = Intent(context, AudioService::class.java)
                context.startForegroundService(serviceIntent)
            }
            if (prefs.getBoolean("isScreenshotMonitoringEnabled", false)) {
                val serviceIntent = Intent(context, ScreenshotService::class.java)
                context.startForegroundService(serviceIntent)
            }

            // Always set up the periodic workers
            setupPeriodicWorkers(context)
        }
    }

    private fun setupPeriodicWorkers(context: Context) {
        // Same implementation as above
    }
}