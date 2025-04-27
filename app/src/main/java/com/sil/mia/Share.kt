package com.sil.mia

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.sil.services.ScreenshotService
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream


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

        if (type != null && (Intent.ACTION_SEND == action || Intent.ACTION_SEND_MULTIPLE == action)) {
            if (type.startsWith("image/")) {
                Toast.makeText(this, "Image(s) shared!", Toast.LENGTH_SHORT).show()
                handleSendImage(intent) // Handle single image
            }
        }

        // Immediately finish the activity after handling
        finish()
    }
    private fun handleSendImage(intent: Intent) {
        val imageUri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
        if (imageUri != null) {
            // Get the real path of the image
            var realPath = getRealPathFromUri(this, imageUri)
            if (realPath == null) {
                val tempFile = copyUriToTempFile(this, imageUri)
                realPath = if (tempFile != null) {
                    tempFile.absolutePath
                } else {
                    "-"
                }
            }

            // Do something with the real path
            val file = realPath?.let { File(it) }
            if (file != null) {
                ScreenshotService.uploadImageFileWithMetadata(this, file)
            }
        }
    }
    private fun processReceivedImage(imageUri: Uri): String {
        Log.d(TAG, "Received image: $imageUri")

        val realPath = getRealPathFromUri(this, imageUri)
        return if (realPath != null) {
            realPath
        } else {
            val tempFile = copyUriToTempFile(this, imageUri)
            if (tempFile != null) {
                tempFile.absolutePath
            } else {
                "-"
            }
        }
    }
    private fun handleSendMultipleImages(intent: Intent) {
        val imageUris = intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
        if (imageUris != null) {
            // Do something with multiple images
            processReceivedMultipleImages(imageUris)
        }
    }
    private fun processReceivedMultipleImages(imageUris: ArrayList<Uri>) {
        Log.d(TAG, "Received " + imageUris.size + " images")
        Toast.makeText(this, imageUris.size.toString() + " images received!", Toast.LENGTH_SHORT)
            .show()
    }
    // endregion

    // region Helper
    private fun getRealPathFromUri(context: Context, uri: Uri): String? {
        var realPath: String? = null

        if (DocumentsContract.isDocumentUri(context, uri)) {
            // If it's a document, like Google Photos
            val docId = DocumentsContract.getDocumentId(uri)
            val split = docId.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            if (split.size >= 2) {
                val type = split[0]
                val id = split[1]

                if ("image" == type) {
                    // Try querying MediaStore
                    val contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    val selection = "_id=?"
                    val selectionArgs = arrayOf(id)

                    realPath = queryContentResolver(context, contentUri, selection, selectionArgs)
                }
            }
        } else if ("content".equals(uri.scheme, ignoreCase = true)) {
            // General content:// URI
            realPath = queryContentResolver(context, uri, null, null)
        } else if ("file".equals(uri.scheme, ignoreCase = true)) {
            // Direct file path
            realPath = uri.path
        }

        return realPath
    }
    private fun queryContentResolver(context: Context, uri: Uri, selection: String?, selectionArgs: Array<String>?): String? {
        val projection = arrayOf(MediaStore.Images.Media.DATA)
        try {
            context.contentResolver.query(uri, projection, selection, selectionArgs, null)
                .use { cursor ->
                    if (cursor != null && cursor.moveToFirst()) {
                        val columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                        return cursor.getString(columnIndex)
                    }
                }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }
    private fun copyUriToTempFile(context: Context, uri: Uri): File? {
        var tempFile: File? = null
        try {
            val inputStream = context.contentResolver.openInputStream(uri)
            if (inputStream != null) {
                tempFile = File.createTempFile("shared_image_", ".jpg", context.cacheDir)
                val outputStream: OutputStream = FileOutputStream(tempFile)

                val buffer = ByteArray(4096)
                var bytesRead: Int
                while ((inputStream.read(buffer).also { bytesRead = it }) != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                }

                outputStream.close()
                inputStream.close()
            }
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }
        return tempFile
    }
    // endregion
}