package com.meshalive.app.ui

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

/** Saves a text file to the phone's Downloads folder (MediaStore on Android 10+). Returns a user-facing message. */
object Downloads {
    fun saveText(context: Context, name: String, mime: String, text: String): String = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, mime)
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: error("could not create file")
            context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
            "Saved to Downloads/$name"
        } else {
            @Suppress("DEPRECATION")
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            try {
                dir.mkdirs()
                File(dir, name).writeText(text)
                "Saved to Downloads/$name"
            } catch (e: Exception) {
                val fallback = File(context.getExternalFilesDir(null) ?: context.filesDir, name)
                fallback.writeText(text)
                "No storage permission - saved to ${fallback.absolutePath}"
            }
        }
    } catch (e: Exception) {
        "Export failed: ${e.message}"
    }
}
