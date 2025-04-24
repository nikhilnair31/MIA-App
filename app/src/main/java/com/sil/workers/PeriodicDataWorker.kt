package com.sil.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.sil.others.Helpers

class PeriodicDataWorker(context: Context, workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        Log.i("PeriodicDataWorker", "doWork ")

        val sensorFilePath = Helpers.createSensorDataFile(applicationContext)
        Helpers.uploadSensorFileToS3(applicationContext, sensorFilePath)

        return Result.success()
    }
}