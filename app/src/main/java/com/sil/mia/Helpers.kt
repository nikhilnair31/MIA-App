package com.sil.mia

import android.app.Activity
import android.content.Context
import android.widget.Toast

class Helpers(private val context: Context) {
    fun showToast(message: String) {
        if (context is Activity) {
            context.runOnUiThread {
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
        }
        else {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
}