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

    private var dataDumpList = JSONArray()
    private var maxFetchCount: Int = 100
    // endregion

    // region Initial
    override fun onReceive(context: Context, intent: Intent) {
        // Set integer values
        maxFetchCount = context.resources.getInteger(R.integer.maxFetchCount)

        generalSharedPref = context.getSharedPreferences("com.sil.mia.generalSharedPrefs", Context.MODE_PRIVATE)
        dataSharedPref = context.getSharedPreferences("com.sil.mia.data", Context.MODE_PRIVATE)

        CoroutineScope(Dispatchers.IO).launch {
            if (Helpers.checkIfCanRun(context, generalSharedPref, "RefreshAlarm")) {
                refreshDataDump()
            }
        }
    }
    // endregion

    // region Refreshing Data Dump
    private suspend fun refreshDataDump() {
        CoroutineScope(Dispatchers.Main).launch {
            val userName = generalSharedPref.getString("userName", "")
            if (userName != null) {
                dataDumpList = Helpers.refreshLocalContextRecordingData(userName, maxFetchCount)
            }
            dataSharedPref.edit().putString("dataDump", dataDumpList.toString()).apply()
            Log.i("RefreshAlarm", "Refresh Complete")
        }
    }
    // endregion
}