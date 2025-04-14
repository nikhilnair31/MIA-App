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
        val notificationId = intent.getIntExtra("notification_id", -1)
        if (notificationId == -1) return

        val feedbackType = when (intent.action) {
            "com.sil.mia.GOOD_FEEDBACK" -> {
                dismissNotification(context, notificationId.toString())
                "good"
            }
            "com.sil.mia.BAD_FEEDBACK" -> {
                dismissNotification(context, notificationId.toString())
                "bad"
            }
            "com.sil.mia.DISMISSED" -> "dismissed"
            else -> return
        }

        val jsonObject = JSONObject().apply {
            put("action", "feedback")
            put("notification_id", notificationId)
            put("feedback", feedbackType)
        }
        Log.d("FeedbackReceiver", "jsonObject $jsonObject")

        CoroutineScope(Dispatchers.IO).launch {
            Helpers.callNotificationFeedbackLambda(context, jsonObject)
        }
    }

    private fun dismissNotification(context: Context, thoughtId: String) {
        val notificationManager = NotificationManagerCompat.from(context)
        notificationManager.cancel(thoughtId.hashCode()) // Cancel notification by thoughtId
    }
}