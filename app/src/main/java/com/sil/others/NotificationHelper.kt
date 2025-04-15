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
import com.sil.receiver.FeedbackReceiver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class NotificationHelper(private val context: Context) {
    // region Vars
    // Constants for listening notification
    private val listeningChannelId = "AudioRecordingServiceChannel"
    private val listeningChannelName = "MIA Listening Channel"
    private val listeningChannelGroup = "MIA Listening Group"
    private val listeningChannelImportance = NotificationManager.IMPORTANCE_MIN
    private val listeningNotificationTitle = "MIA listening..."
    private val listeningNotificationText = "Active"
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
            .setStyle(null)
            .setVisibility(Notification.VISIBILITY_SECRET)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
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
        val contentNotification = jsonResponse.optString("notification_content", "")
        if (showNotification && contentNotification.isNullOrEmpty()) {
            val content = jsonResponse.optString("notification_content", "")
            val notificationId = jsonResponse.optInt("notification_id", 1)

            if (content.isNotEmpty()) {
                withContext(Dispatchers.Main) {
                    showThoughtsNotification(content, notificationId)
                }
            }
        }
    }
    private fun showThoughtsNotification(content: String, notificationId: Int) {
        val intent = Intent(context, Main::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        val pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        Log.d("Helper", "notificationId: $notificationId")

        // Create good feedback intent
        val goodIntent = Intent(context, FeedbackReceiver::class.java).apply {
            action = "com.sil.mia.GOOD_FEEDBACK"
            putExtra("notification_id", notificationId)
        }
        val goodPendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId,
            goodIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Create bad feedback intent
        val badIntent = Intent(context, FeedbackReceiver::class.java).apply {
            action = "com.sil.mia.BAD_FEEDBACK"
            putExtra("notification_id", notificationId)
        }
        val badPendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId,
            badIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Create delete intent that triggers when notification is dismissed
        val deleteIntent = Intent(context, FeedbackReceiver::class.java).apply {
            action = "com.sil.mia.DISMISSED"
            putExtra("notification_id", notificationId)
        }
        val deletePendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId,
            deleteIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, thoughtsChannelId)
            .setSmallIcon(R.drawable.mia_stat_name)
            .setContentTitle("MIA Thought")
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .addAction(R.drawable.ic_good, "Helpful", goodPendingIntent)  // Add Good button
            .addAction(R.drawable.ic_bad, "Useless", badPendingIntent)     // Add Bad button
            .setAutoCancel(true)
            .setDeleteIntent(deletePendingIntent)  // Set delete intent for swipe away
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