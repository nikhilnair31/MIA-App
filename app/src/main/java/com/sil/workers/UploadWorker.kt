package com.sil.workers

import android.content.Context
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.sil.others.Helpers
import java.io.File

class UploadWorker(context: Context, workerParams: WorkerParameters) : Worker(context, workerParams) {
    override fun doWork(): Result {
        Log.i("UploadWorker", "doWork | inputData: $inputData")

        val filePath = inputData.getString("filePath")
        val fileSource = inputData.getString("fileSource")
        val fileSave = inputData.getString("fileSave")
        val filePreprocess = inputData.getString("filePreprocess")

        if (!filePath.isNullOrEmpty()) {
            val file = File(filePath)

            if (fileSource == "audio") {
                Helpers.uploadAudioFileToS3(applicationContext, file, fileSave, filePreprocess)
            }
            else if (fileSource == "image") {
                Helpers.uploadImageFileToS3(applicationContext, file, fileSave, filePreprocess)
            }

            return Result.success()
        }

        return Result.failure()
    }
}