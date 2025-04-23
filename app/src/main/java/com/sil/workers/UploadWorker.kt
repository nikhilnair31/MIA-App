package com.sil.workers

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.sil.others.Helpers
import java.io.File

class UploadWorker(context: Context, workerParams: WorkerParameters) : Worker(context, workerParams) {
    override fun doWork(): Result {
        val filePath = inputData.getString("file")
        // TODO: Instead of getting source like this just use the metadata JSON object
        val fileSource = inputData.getString("source")

        if (!filePath.isNullOrEmpty() && !fileSource.isNullOrEmpty()) {
            val file = File(filePath)

            if (fileSource == "audio") {
                Helpers.uploadAudioFileToS3(applicationContext, file)
            }
            else if (fileSource == "image") {
                Helpers.uploadImageFileToS3(applicationContext, file)
            }

            return Result.success()
        }

        return Result.failure()
    }
}