package com.sil.services

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Environment
import android.os.FileObserver
import android.os.IBinder
import android.util.Log
import com.sil.listeners.SensorListener
import com.sil.others.Helpers
import com.sil.others.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

class ScreenshotService : Service() {
    // region Vars
    private val TAG = "Screenshot Service"

    private lateinit var sensorListener: SensorListener
    private lateinit var notificationHelper: NotificationHelper

    private var screenshotObserver: ScreenshotFileObserver? = null

    private val monitoringNotificationId = 2
    // endregion

    // region Common
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "onStartCommand")

        initRelated()
        return START_STICKY
    }
    override fun onDestroy() {
        Log.i(TAG, "onDestroy | Screenshot monitor service destroyed")

        sensorListener.unregister()
        stopMonitoring()
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun initRelated() {
        Log.i(TAG, "initRelated")

        // Listener related
        sensorListener = SensorListener(this)

        // Service related
        notificationHelper = NotificationHelper(this)
        startForeground(monitoringNotificationId, notificationHelper.createMonitoringNotification())

        // Observer related
        if (screenshotObserver == null) {
            startMonitoring()
        }
    }
    // endregion

    // region Monitoring Related
    private fun startMonitoring() {
        Log.i(TAG, "startMonitoring")

        val screenshotsPath = getScreenshotsPath()
        Log.i(TAG, "screenshotsPath: $screenshotsPath")

        if (screenshotsPath != null) {
            Log.i(TAG, "Starting to monitor screenshots folder: $screenshotsPath")

            screenshotObserver = ScreenshotFileObserver(screenshotsPath)
            screenshotObserver?.startWatching()
        } else {
            Log.e(TAG, "Could not determine screenshots directory path")
        }
    }
    private fun stopMonitoring() {
        Log.i(TAG, "Stopped monitoring screenshots folder")

        screenshotObserver?.stopWatching()
        screenshotObserver = null
    }

    private inner class ScreenshotFileObserver(path: String) : FileObserver(path, MOVED_TO) {
        private val observedPath = path

        override fun onEvent(event: Int, path: String?) {
            Log.i(TAG, "onEvent | event: $event | path: $path")

            if (event == MOVED_TO && path != null) {
                val file = File(observedPath, path)
                if (isImageFile(file.name)) {
                    Log.i(TAG, "New screenshot detected at ${file.absolutePath} with name: ${file.name}")

                    uploadImageFileWithMetadata(this@ScreenshotService, file)
                }
            }
        }
    }
    // endregion

    companion object {
        private val TAG = "Screenshot Service"

        fun uploadImageFileWithMetadata(context: Context, imageFile: File) {
            // Get the shared preferences for metadata values
            val sharedPrefs = context.getSharedPreferences("com.sil.mia.generalSharedPrefs", Context.MODE_PRIVATE)
            val saveImage = sharedPrefs.getString("saveImageFiles", "false")
            val preprocessImage = sharedPrefs.getString("preprocessImage", "false")

            CoroutineScope(Dispatchers.IO).launch {
                // Start upload process
                Helpers.scheduleContentUploadWork(
                    context,
                    "image",
                    imageFile,
                    saveImage,
                    preprocessImage
                )

                // Show notification
                val notificationHelper = NotificationHelper(context)
                notificationHelper.showImageProcessingNotification(context)
            }
        }
        fun getScreenshotsPath(): String? {
            // For most devices, screenshots are in DCIM/Screenshots or Pictures/Screenshots
            val dcimPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
            val picturesPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)

            val possiblePaths = listOf(
                File(picturesPath, "Screenshots"),
                // Some devices might use the root of DCIM or Pictures
                picturesPath
            )

            for (path in possiblePaths) {
                if (path.exists() && path.isDirectory) {
                    return path.absolutePath
                }
            }

            // If we can't find a known screenshots directory, default to DCIM/Screenshots
            val defaultPath = File(dcimPath, "Screenshots")
            defaultPath.mkdirs()

            return defaultPath.absolutePath
        }
        fun isImageFile(fileName: String): Boolean {
            Log.i(TAG, "isImageFile | fileName: $fileName")

            val lowerCaseName = fileName.lowercase()
            return lowerCaseName.endsWith(".jpg") ||
                    lowerCaseName.endsWith(".jpeg") ||
                    lowerCaseName.endsWith(".png") ||
                    lowerCaseName.endsWith(".webp")
        }
    }
}