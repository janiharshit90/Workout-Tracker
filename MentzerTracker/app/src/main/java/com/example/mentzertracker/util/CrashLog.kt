package com.example.mentzertracker.util

import android.content.Context
import android.os.Build
import com.example.mentzertracker.BuildConfig
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Saves the stack trace of the last crash to a small file so a tester can share it from
 * Settings -> Send feedback. Nothing leaves the phone unless the person shares it themselves.
 */
object CrashLog {
    private const val FILE = "last_crash.txt"

    fun install(ctx: Context) {
        val app = ctx.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                val sw = StringWriter()
                error.printStackTrace(PrintWriter(sw))
                File(app.filesDir, FILE).writeText(
                    "Rarelift crash\n" + header() + "\nThread: ${thread.name}\n\n" + sw.toString().take(20_000)
                )
            } catch (_: Throwable) { /* never make a crash worse */ }
            previous?.uncaughtException(thread, error)
        }
    }

    /** Version + device line used in both feedback text and the crash file. */
    fun header(): String =
        "Version: ${BuildConfig.VERSION_NAME}\n" +
            "Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n" +
            "Device: ${Build.MANUFACTURER} ${Build.MODEL}"

    fun read(ctx: Context): String? =
        File(ctx.filesDir, FILE).takeIf { it.exists() }?.readText()?.takeIf { it.isNotBlank() }

    fun clear(ctx: Context) { File(ctx.filesDir, FILE).delete() }
}
