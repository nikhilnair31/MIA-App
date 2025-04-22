package com.sil.workers

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.sil.others.Helpers
import org.json.JSONObject
import java.io.File

class UploadWorker(context: Context, workerParams: WorkerParameters) : Worker(context, workerParams) {
    override fun doWork(): Result {
        val filePath = inputData.getString("file")
        val fileSource = inputData.getString("source")
        val metadataJsonString = inputData.getString("metadataJson")

        if (!filePath.isNullOrEmpty() && !fileSource.isNullOrEmpty() && !metadataJsonString.isNullOrEmpty()) {
            val file = File(filePath)
            val metadataJsonObj = JSONObject(metadataJsonString)

            if (fileSource == "audio") {
                Helpers.uploadAudioFileToS3(applicationContext, file, metadataJsonObj)
            }
            else if (fileSource == "image") {
                Helpers.uploadImageFileToS3(applicationContext, file, metadataJsonObj)
            }

            return Result.success()
        }

        return Result.failure()
    }
}