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
import com.sil.mia.Main
import com.sil.mia.R
import com.sil.receiver.FeedbackReceiver

class NotificationHelper(private val context: Context) {
    // region Vars
    private val TAG = "NotificationHelper"
    
    private val miaChannelName = "MIA Active Channel"
    private val miaNotificationIcon = R.drawable.mia_stat_name
    
    // Constants for listening notification
    private val listeningChannelId = "MiaListeningChannel"
    private val listeningChannelImportance = NotificationManager.IMPORTANCE_MIN
    private val listeningNotificationTitle = "MIA listening..."

    // Constants for monitoring notification
    private val monitoringChannelId = "MiaMonitoringChannel"
    private val monitoringChannelImportance = NotificationManager.IMPORTANCE_MIN
    private val monitoringNotificationTitle = "MIA monitoring..."

    // Constants for thoughts notification
    private val thoughtsChannelId = "MiaThoughtsChannel"
    private val thoughtsChannelGroup = "MIA Thoughts Group"
    private val thoughtsChannelImportance = NotificationManager.IMPORTANCE_DEFAULT
    private val thoughtsNotificationTitle = "MIA thoughts..."
    private val goodNotificationIcon = R.drawable.ic_good
    private val badNotificationIcon = R.drawable.ic_bad
    // endregion

    // region Notification Related
    fun createListeningNotification(): Notification {
        Log.i(TAG, "Creating listening notification channel")

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(listeningChannelId, miaChannelName, listeningChannelImportance)
        notificationManager.createNotificationChannel(channel)

        // Intent to open the main activity
        val intent = Intent(context, Main::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        val pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        return Notification.Builder(context, listeningChannelId)
            .setContentTitle(listeningNotificationTitle)
            .setSmallIcon(miaNotificationIcon)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setStyle(null)
            .setVisibility(Notification.VISIBILITY_SECRET)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }
    fun createMonitoringNotification(): Notification {
        Log.i(TAG, "Creating monitoring notification channel")

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(monitoringChannelId, miaChannelName, monitoringChannelImportance)
        notificationManager.createNotificationChannel(channel)

        // Intent to open the main activity
        val intent = Intent(context, Main::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return Notification.Builder(context, monitoringChannelId)
            .setContentTitle(monitoringNotificationTitle)
            .setSmallIcon(miaNotificationIcon)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setStyle(null)
            .setVisibility(Notification.VISIBILITY_SECRET)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    fun showThoughtsNotification(content: String, notificationId: Int) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(thoughtsChannelId, thoughtsChannelGroup, thoughtsChannelImportance)
        notificationManager.createNotificationChannel(channel)

        // Intent to open the main activity
        val intent = Intent(context, Main::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        val pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        Log.d(TAG, "notificationId: $notificationId")

        // Create good feedback intent
        val goodIntent = Intent(context, FeedbackReceiver::class.java).apply {
            action = "com.sil.mia.GOOD_FEEDBACK"
            putExtra("notification_id", notificationId)
        }
        val goodPendingIntent = PendingIntent.getBroadcast(context, notificationId, goodIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        // Create bad feedback intent
        val badIntent = Intent(context, FeedbackReceiver::class.java).apply {
            action = "com.sil.mia.BAD_FEEDBACK"
            putExtra("notification_id", notificationId)
        }
        val badPendingIntent = PendingIntent.getBroadcast(context, notificationId, badIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        // Create delete intent that triggers when notification is dismissed
        val deleteIntent = Intent(context, FeedbackReceiver::class.java).apply {
            action = "com.sil.mia.DISMISSED"
            putExtra("notification_id", notificationId)
        }
        val deletePendingIntent = PendingIntent.getBroadcast(context, notificationId, deleteIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        // Create notification
        val notification = NotificationCompat.Builder(context, thoughtsChannelId)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setContentTitle(thoughtsNotificationTitle)
            .setPriority(thoughtsChannelImportance)
            .setSmallIcon(miaNotificationIcon)
            .setGroup(thoughtsChannelGroup)
            .setContentIntent(pendingIntent)
            .setContentText(content)
            .setAutoCancel(true)
            .addAction(goodNotificationIcon, "Helpful", goodPendingIntent)  // Add Good button
            .addAction(badNotificationIcon, "Useless", badPendingIntent)     // Add Bad button
            .setDeleteIntent(deletePendingIntent)  // Set delete intent for swipe away
            .build()

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            notificationManager.notify(notificationId, notification)
        } else {
            Log.e(TAG, "Notification permission not granted")
        }
    }
    // endregion
}