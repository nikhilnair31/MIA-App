package com.sil.mia

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.sil.others.Helpers
import com.sil.services.ScreenshotService
import java.io.File


class Share : AppCompatActivity() {
    // region Vars
    private val TAG = "Share"
    // endregion

    // region Common
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
    }
    // endregion

    // region Share Related
    private fun handleIntent(intent: Intent) {
        val action = intent.action
        val type = intent.type

        Log.d(TAG, "Intent action: $action")
        if (type != null && type.startsWith("image/")) {
            when (action) {
                Intent.ACTION_SEND -> {
                    val imageUri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                    handleSendImage(imageUri)
                }
                Intent.ACTION_SEND_MULTIPLE -> {
                    val imageUriList = intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
                    handleSendMultipleImages(imageUriList)
                }
            }
        }

        // Immediately finish the activity after handling
        finish()
    }
    private fun handleSendImage(imageUri: Uri?) {
        Toast.makeText(this, "Image shared!", Toast.LENGTH_SHORT).show()

        imageUri?.let {
            var realPath = Helpers.getRealPathFromUri(this, it)
            if (realPath == null) {
                val tempFile = Helpers.copyUriToTempFile(this, it)
                realPath = tempFile?.absolutePath
            }

            realPath?.let { path ->
                val file = File(path)
                ScreenshotService.uploadImageFileWithMetadata(this, file)
            }
        }
    }
    private fun handleSendMultipleImages(imageUris: ArrayList<Uri>?) {
        Log.d(TAG, "handleSendMultipleImages | imageUris.size: ${imageUris?.size}")

        Toast.makeText(this, "Images shared!", Toast.LENGTH_SHORT).show()

        imageUris?.forEach { uri ->
            var realPath = Helpers.getRealPathFromUri(this, uri)
            if (realPath == null) {
                val tempFile = Helpers.copyUriToTempFile(this, uri)
                realPath = tempFile?.absolutePath
            }
            Log.d(TAG, "handleSendMultipleImages | realPath: $realPath")

            realPath?.let { path ->
                val file = File(path)
                ScreenshotService.uploadImageFileWithMetadata(this, file)
            }
        }
    }
    // endregion
}