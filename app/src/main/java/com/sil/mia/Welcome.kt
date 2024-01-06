package com.sil.mia

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.ImageButton

class Welcome : AppCompatActivity() {
    // region Vars
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var nextButton: ImageButton
    // endregion

    // region Common
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_welcome)

        firstLaunchCheck()
    }
    // endregion

    // region First Launch Related
    private fun firstLaunchCheck() {
        sharedPreferences = getSharedPreferences("com.sil.mia.generalSharedPrefs", Context.MODE_PRIVATE)

        val isFirstRun = sharedPreferences.getBoolean("isFirstRun", true)
        Log.i("Welcome", "isFirstRun: $isFirstRun")

        if (isFirstRun) {
            usernamePageButtonSetup()
        } else {
            launchNextActivity(Main::class.java)
        }
    }
    private fun usernamePageButtonSetup() {
        nextButton = findViewById(R.id.buttonNext)
        nextButton.setOnClickListener {
            launchNextActivity(Username::class.java)
        }
    }
    private fun launchNextActivity(activityClass: Class<*>) {
        val intent = Intent(this, activityClass)
        startActivity(intent)
        finish()
    }
    // endregion
}