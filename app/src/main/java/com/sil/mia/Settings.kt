package com.sil.mia

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.widget.EditText
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import com.sil.others.Helpers

class Settings : AppCompatActivity() {
    private lateinit var sharedPref: SharedPreferences
    private lateinit var editText: EditText
    private lateinit var backButton: ImageButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        buttonSetup()
    }

    override fun onBackPressed() {
        onBackPressedDispatcher.onBackPressed()
        finish()
    }
    private fun buttonSetup() {
        editText = findViewById(R.id.editText)
        sharedPref = getSharedPreferences("com.sil.mia.generalSharedPrefs", Context.MODE_PRIVATE)
        val currentUsername: String? = sharedPref.getString("userName", "")
        editText.setText(currentUsername)

        backButton = findViewById(R.id.buttonBack)
        backButton.setOnClickListener {
            updateUsernameAndBack()
        }
    }

    private fun updateUsernameAndBack() {
        val userNameText = editText.text.toString()
        if (userNameText.isNotEmpty()) {
            sharedPref.edit().putString("userName", userNameText).apply()
            Log.i("Username", "updateUsernameAndBack userName: $userNameText")

            onBackPressed()
        }
        else {
            Helpers.showToast(this, "Not a a valid username :(")
        }
    }
}