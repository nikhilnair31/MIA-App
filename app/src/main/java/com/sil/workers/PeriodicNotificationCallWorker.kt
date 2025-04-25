package com.sil.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.sil.others.Helpers
import com.sil.others.NotificationHelper
import org.json.JSONObject

class PeriodicNotificationCallWorker(context: Context, workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams) {
    // region Vars
    private val TAG = "PeriodicNotificationCallWorker"

    private val notificationHelper = NotificationHelper(context)
    // endregion

    override suspend fun doWork(): Result {
        Log.i(TAG, "doWork ")

        try {
            val sharedPrefs = applicationContext.getSharedPreferences("com.sil.mia.generalSharedPrefs", Context.MODE_PRIVATE)
            val userName = sharedPrefs.getString("userName", null)

            val notificationPayloadJson = JSONObject().apply {
                put("action", "get_notification")
                put("username", userName)
            }
            Log.i(TAG, "notificationPayloadJson: $notificationPayloadJson")

            val notificationLambdaResponseJson = Helpers.callNotificationCheckLambda(notificationPayloadJson)
            Log.i(TAG, "notificationLambdaResponseJson: $notificationLambdaResponseJson")

            notificationHelper.checkIfShouldNotify(notificationLambdaResponseJson)
        }
        catch (e: Exception) {
            Log.e(TAG, "Error calling notification lambda", e)
        }

        return Result.success()
    }
}