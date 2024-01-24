package com.sil.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.sil.others.Helpers
import org.json.JSONObject

class ApiCallWorker (context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        return try {
            val payloadString = inputData.getString("payloadJson")

            if (!payloadString.isNullOrEmpty()) {
                val payload = JSONObject(payloadString)
                Helpers.callTogetherChatAPI(payload)
            }
            Result.success()
        }
        catch (e: Exception) {
            Log.e("ThoughtsWorker", "Error pulling latest recordings", e)
            Result.failure()
        }
    }
}