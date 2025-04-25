package com.sil.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.sil.others.Helpers
import com.sil.others.NotificationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class PeriodicNotificationCallWorker(context: Context, workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams) {
    // region Vars
    private val TAG = "PeriodicNotificationCallWorker"

    private val notificationHelper = NotificationHelper(context)
    // endregion

    override suspend fun doWork(): Result {
        Log.i(TAG, "doWork started at ${System.currentTimeMillis()}")

        try {
            withContext(Dispatchers.IO) {
                val sharedPrefs = applicationContext.getSharedPreferences(
                    "com.sil.mia.generalSharedPrefs",
                    Context.MODE_PRIVATE
                )
                val userName = sharedPrefs.getString("userName", null)
                if (userName.isNullOrEmpty()) {
                    Log.e(TAG, "Username is null or empty, cannot proceed with notification check")
                    return@withContext
                }

                val notificationPayloadJson = JSONObject().apply {
                    put("action", "get_notification")
                    put("username", userName)
                }
                Log.i(TAG, "notificationPayloadJson: $notificationPayloadJson")

                val notificationLambdaResponseJson = Helpers.callNotificationCheckLambda(notificationPayloadJson)
                Log.i(TAG, "notificationLambdaResponseJson: $notificationLambdaResponseJson")

                checkIfShouldNotify(notificationLambdaResponseJson)
            }
        }
        catch (e: Exception) {
            Log.e(TAG, "Error calling notification lambda", e)
        }

        Log.i(TAG, "doWork completed at ${System.currentTimeMillis()}")
        return Result.success()
    }

    private suspend fun checkIfShouldNotify(jsonResponse: JSONObject) {
        val notificationId = jsonResponse.optInt("notification_id", 1)
        val showNotification = jsonResponse.optBoolean("notification_to_show", false)
        val contentNotification = jsonResponse.optString("notification_content", "")
        Log.d(TAG, "showNotification: $showNotification | contentNotification: $contentNotification")

        if (showNotification && !contentNotification.isNullOrEmpty()) {
            Log.d(TAG, "Notification to show")

            withContext(Dispatchers.Main) {
                notificationHelper.showThoughtsNotification(contentNotification, notificationId)
            }
        }
        else {
            Log.d(TAG, "No notification to show")
        }
    }
}