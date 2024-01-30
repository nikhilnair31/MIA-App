package com.sil.receivers

import android.app.ActivityManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.util.Log
import android.view.View
import androidx.core.app.NotificationCompat
import androidx.legacy.content.WakefulBroadcastReceiver
import androidx.recyclerview.widget.RecyclerView
import com.sil.adapters.DataDumpAdapter
import com.sil.mia.Main
import com.sil.mia.R
import com.sil.others.Helpers
import com.sil.listeners.SensorListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.*

class RefreshAlarmReceiver : BroadcastReceiver() {
    // region Vars
    private lateinit var generalSharedPref: SharedPreferences
    private lateinit var dataSharedPref: SharedPreferences

    private var contextMain: Context? = null

    private var dataDumpList = JSONArray()
    private var maxFetchCount: Int = 100
    // endregion

    // region Initial
    override fun onReceive(context: Context, intent: Intent) {
        // Log.i("RefreshAlarm", "onReceive")

        contextMain = context

        generalSharedPref = context.getSharedPreferences("com.sil.mia.generalSharedPrefs", Context.MODE_PRIVATE)
        dataSharedPref = context.getSharedPreferences("com.sil.mia.data", Context.MODE_PRIVATE)

        // Set integer values
        maxFetchCount = context.resources.getInteger(R.integer.maxFetchCount)

        CoroutineScope(Dispatchers.IO).launch {
            if (isAppInForeground()) {
                Log.i("RefreshAlarm", "App is in foreground. Not refreshing.")
            }
            else {
                // Check if it's within the allowed notification time
                if (isNotificationAllowed()) {
                    Log.i("RefreshAlarm", "App is NOT in foreground. Refreshing!")
                    refreshDataDump()
                } else {
                    Log.i("RefreshAlarm", "Do Not Disturb time. Not refreshing.")
                }
            }
        }
    }
    private fun isAppInForeground(): Boolean {
        val activityManager = contextMain?.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val appProcesses = activityManager.runningAppProcesses ?: return false

        val packageName = contextMain?.packageName
        return appProcesses.any { processInfo ->
            (processInfo.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND || processInfo.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE) && processInfo.processName == packageName
        }
    }
    private fun isNotificationAllowed(): Boolean {
        val thoughtsStartTime = generalSharedPref.getInt("thoughtsStartTime", 6)
        val thoughtsEndTime = generalSharedPref.getInt("thoughtsEndTime", 0)

        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return currentHour in thoughtsStartTime..thoughtsEndTime
    }
    // endregion

    // region Refreshing Data Dump
    private suspend fun refreshDataDump() {
        CoroutineScope(Dispatchers.Main).launch {
            var responseJsonObject = JSONObject()

            val queryVectorArray = FloatArray(1536)
            val queryVectorArrayJson = JSONArray().apply {
                for (value in queryVectorArray) {
                    put(value)
                }
            }

            // Creating final filter object
            val filterJsonObject = JSONObject().apply {
                // Objects to get last week's filter
                val currentDate = LocalDate.now()
                val oneWeekAgo = currentDate.minusWeeks(1)
                put("day", JSONObject().apply {
                    put("\$gte", oneWeekAgo.dayOfMonth)
                    put("\$lte", currentDate.dayOfMonth)
                })
                put("month", JSONObject().apply {
                    put("\$eq", oneWeekAgo.monthValue)
                })
                put("year", JSONObject().apply {
                    put("\$eq", oneWeekAgo.year)
                })

                // Need to filter vectors by username
                val userName = generalSharedPref.getString("userName", null)
                put("username", userName)
            }

            withContext(Dispatchers.IO) {
                Helpers.callPineconeFetchAPI(queryVectorArrayJson, filterJsonObject, maxFetchCount) { success, response ->
                    if (success) {
                        responseJsonObject = response
                    }
                }
            }

            // Doing this to get array in matches key
            val bodyJsonArray = responseJsonObject.getJSONArray("matches")
            // Selecting only metadata object from it
            val metadataArray = JSONArray()
            for (i in 0 until bodyJsonArray.length()) {
                val jsonObject = bodyJsonArray.getJSONObject(i)
                val metadataObject = jsonObject.optJSONObject("metadata")

                // Doing this to include ID of the vector which isn't in the metadata JSON object
                if (metadataObject != null) {
                    val modifiedMetadataObject = JSONObject(metadataObject.toString())
                    modifiedMetadataObject.put("id", jsonObject.getString("id"))
                    metadataArray.put(modifiedMetadataObject)
                }
            }

            val metadataList = Helpers.sortJsonDescending(metadataArray)
            dataDumpList = JSONArray(metadataList)
            dataSharedPref.edit().putString("dataDump", dataDumpList.toString()).apply()

            Log.i("RefreshAlarm", "Refresh Complete")
        }
    }
    // endregion
}