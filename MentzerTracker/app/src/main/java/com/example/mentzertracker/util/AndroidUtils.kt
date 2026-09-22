package com.example.mentzertracker.util

import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.content.FileProvider
import java.io.File

fun fileProviderAuthority(ctx: Context) = "${ctx.packageName}.fileprovider"

/** Opens the system share sheet for a file in cacheDir (see res/xml/file_paths.xml). */
fun shareFile(ctx: Context, file: File, mime: String, chooserTitle: String) {
    val uri = FileProvider.getUriForFile(ctx, fileProviderAuthority(ctx), file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = mime
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    ctx.startActivity(Intent.createChooser(send, chooserTitle))
}

/** Buzz + notification chime when the rest timer finishes. Never throws. */
fun alertRestOver(ctx: Context) {
    try {
        val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= 31) {
            ctx.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        val pattern = longArrayOf(0, 400, 200, 400)
        if (vibrator?.hasVibrator() == true) {
            if (Build.VERSION.SDK_INT >= 26) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(pattern, -1)
            }
        }
    } catch (_: Exception) { }
    try {
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        RingtoneManager.getRingtone(ctx, uri)?.play()
    } catch (_: Exception) { }
}
