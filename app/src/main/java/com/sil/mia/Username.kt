package com.sil.mia

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.EditText
import android.widget.ImageButton

class Username : AppCompatActivity() {
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var editText: EditText
    private lateinit var updateAndNextButton: ImageButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_username)

        usernameRelated()
    }

    private fun usernameRelated() {
        Log.i("Main", "usernameRelated")

        sharedPreferences = getSharedPreferences("com.sil.mia.generalSharedPrefs", Context.MODE_PRIVATE)

        updateAndNextButton = findViewById(R.id.buttonUpdateAndNext)
        updateAndNextButton.setOnClickListener {
            updateUsernameAndNext()
        }
    }
    private fun updateUsernameAndNext() {
        editText = findViewById(R.id.editText)
        val userNameText = editText.text.toString()
        if (userNameText.isNotEmpty()) {
            sharedPreferences.edit().putString("userName", userNameText).apply()
            Log.i("Username", "updateUsernameAndNext userName: $userNameText")

            val intent = Intent(this, Permissions::class.java)
            startActivity(intent)
            finish()
        }
        else {
            Helpers.showToast(this, "Not a a valid username :(")
        }
    }
}