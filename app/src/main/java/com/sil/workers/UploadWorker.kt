package com.sil.workers

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.sil.others.Helpers
import org.json.JSONObject
import java.io.File

class UploadWorker(context: Context, workerParams: WorkerParameters) : Worker(context, workerParams) {
    override fun doWork(): Result {
        val audioFilePath = inputData.getString("audioFile")
        val metadataJsonString = inputData.getString("metadataJson")

        if (!audioFilePath.isNullOrEmpty() && !metadataJsonString.isNullOrEmpty()) {
            val audioFile = File(audioFilePath)
            val metadataJson = JSONObject(metadataJsonString)

            Helpers.uploadAudioToS3AndDelete(applicationContext, audioFile, metadataJson)

            return Result.success()
        }

        return Result.failure()
    }
}