package com.example.d2rtz_fgservice

import android.os.Build
import android.os.Environment
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// FileLogger you can use to write logs to a file.
// Usage example: FileLogger.write("updateForegroundNotification", "title=${tzCurrent}, text=$nextTerrorTimeUtc $tzNext")
// Add to manifest:
//  <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" android:maxSdkVersion="32"/>
//  <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" android:maxSdkVersion="28"/>

object FileLogger {

    private const val LOG_FILE_NAME = "d2rtz-log.txt"

    // Obtiene la ruta Documents/LOG_FILE_NAME
    private fun getPublicLogFile(): File {
        val dir = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) { // Android 10+
            // /Internal storage/Documents
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        } else {
            Environment.getExternalStorageDirectory()
        }

        if (!dir.exists()) dir.mkdirs()
        return File(dir, LOG_FILE_NAME)
    }

    // Escribir log
    fun write(tag: String, message: String) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val line = "$timestamp [$tag] $message\n"
        getPublicLogFile().appendText(line)
    }

    // Leer logs
    fun read(): String {
        val file = getPublicLogFile()
        return if (file.exists()) file.readText() else ""
    }

    // Limpiar logs
    fun clear() {
        getPublicLogFile().writeText("")
    }

    // Obtener archivo
    fun getFile(): File = getPublicLogFile()
}
