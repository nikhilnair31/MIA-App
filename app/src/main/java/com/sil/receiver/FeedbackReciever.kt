package com.sil.receiver

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import com.sil.others.Helpers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject

class FeedbackReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        val notificationId = intent.getIntExtra("notification_id", -1)

        if (notificationId == -1) {
            Log.w("FeedbackReceiver", "Notification ID not found in intent")
            return
        }

        when (action) {
            "com.sil.mia.GOOD_FEEDBACK" -> {
                Log.d("FeedbackReceiver", "Good feedback received for notification $notificationId")
                dismissNotification(context, "good", notificationId)
            }
            "com.sil.mia.BAD_FEEDBACK" -> {
                Log.d("FeedbackReceiver", "Bad feedback received for notification $notificationId")
                dismissNotification(context, "bad", notificationId)
            }
            "com.sil.mia.DISMISSED" -> {
                Log.d("FeedbackReceiver", "Notification $notificationId was dismissed")
                dismissNotification(context, "neutral", notificationId)
            }
        }
    }

    private fun dismissNotification(context: Context, feedbackType: String, notificationId: Int) {
        // Build feedback Json
        val jsonObject = JSONObject().apply {
            put("action", "feedback")
            put("feedback", feedbackType)
            put("notification_id", notificationId)
        }
        Log.d("FeedbackReceiver", "dismissNotification jsonObject $jsonObject")

        // Call notification lambda to send feedback
        CoroutineScope(Dispatchers.IO).launch {
            Helpers.callNotificationFeedbackLambda(context, jsonObject)
        }

        // Cancel the notification to ensure it's gone
        NotificationManagerCompat.from(context).cancel(notificationId)
    }
}