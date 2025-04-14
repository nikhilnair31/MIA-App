package com.sil.others

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.sil.mia.Main
import com.sil.mia.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.UUID

class NotificationHelper(private val context: Context) {
    // region Vars
    // Constants for listening notification
    private val listeningChannelId = "AudioRecordingServiceChannel"
    private val listeningChannelName = "MIA Listening Channel"
    private val listeningChannelGroup = "MIA Listening Group"
    private val listeningChannelImportance = NotificationManager.IMPORTANCE_LOW
    private val listeningNotificationTitle = "MIA Listening..."
    private val listeningNotificationText = "MIA is active"
    private val listeningNotificationIcon = R.drawable.mia_stat_name
    private val listeningNotificationId = 1

    // Constants for thoughts notification
    private val thoughtsChannelId = "MiaThoughtsChannel"
    private val thoughtsChannelName = "MIA Thoughts Channel"
    private val thoughtsChannelImportance = NotificationManager.IMPORTANCE_DEFAULT
    // endregion

    // region Init Related
    fun createListeningNotification(): Notification {
        Log.i("NotificationHelper", "Creating listening notification channel")

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(listeningChannelId, listeningChannelName, listeningChannelImportance)
        notificationManager.createNotificationChannel(channel)

        // Intent to open the main activity
        val intent = Intent(context, Main::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        val pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        return Notification.Builder(context, listeningChannelId)
            .setContentTitle(listeningNotificationTitle)
            .setContentText(listeningNotificationText)
            .setSmallIcon(listeningNotificationIcon)
            .setGroup(listeningChannelGroup)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
    fun createThoughtsNotificationChannel() {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            thoughtsChannelId,
            thoughtsChannelName,
            thoughtsChannelImportance
        ).apply {
            description = "Channel for MIA thoughts and insights"
        }
        notificationManager.createNotificationChannel(channel)
    }
    // endregion

    // region Thoughts Related
    suspend fun checkIfShouldNotify(jsonResponse: JSONObject) {
        val showNotification = jsonResponse.optBoolean("notification_to_show", false)
        if (showNotification && jsonResponse.has("notification_content")) {
            val content = jsonResponse.optString("notification_content", "")
            if (content.isNotEmpty()) {
                withContext(Dispatchers.Main) {
                    showThoughtsNotification(content)
                }
            }
        }
    }
    private fun showThoughtsNotification(content: String) {
        val intent = Intent(context, Main::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        val pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val notificationId = UUID.randomUUID().hashCode()
        val notification = NotificationCompat.Builder(context, thoughtsChannelId)
            .setSmallIcon(R.drawable.mia_stat_name)
            .setContentTitle("MIA Thought")
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val notificationManager = NotificationManagerCompat.from(context)
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            notificationManager.notify(notificationId, notification)
        } else {
            Log.e("NotificationHelper", "Notification permission not granted")
        }
    }
    // endregion
}