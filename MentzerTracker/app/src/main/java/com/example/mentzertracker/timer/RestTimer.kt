package com.example.mentzertracker.timer

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.SystemClock
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.mentzertracker.MainActivity
import com.example.mentzertracker.R
import com.example.mentzertracker.util.alertRestOver
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * The rest timer, built to keep working when the app isn't on screen:
 *  - the end time is stored as wall-clock time, so it survives the app being closed;
 *  - an alarm fires at that moment even if the phone is locked or dozing;
 *  - while it runs, a lock-screen notification shows a live countdown (drawn by the system,
 *    so it keeps ticking with the app closed), what's next, and +30s / Stop buttons;
 *  - when it ends, a loud "Rest is over" notification buzzes and chimes.
 */
object RestTimer {
    const val ACTION_DONE = "com.example.mentzertracker.REST_DONE"
    const val ACTION_ADD = "com.example.mentzertracker.REST_ADD"
    const val ACTION_STOP = "com.example.mentzertracker.REST_STOP"

    private const val ADD_SECONDS = 30
    private const val PREFS = "rest_timer"
    private const val KEY_END = "end_at"
    private const val KEY_TITLE = "title"
    private const val KEY_NEXT = "next"
    private const val CH_RUNNING = "rest_running"
    private const val CH_DONE = "rest_done"
    private const val ID_RUNNING = 1001
    private const val ID_DONE = 1002

    private val _endAt = MutableStateFlow<Long?>(null)
    /** Wall-clock millis (System.currentTimeMillis) when the rest ends, or null when idle. */
    val endAt: StateFlow<Long?> = _endAt

    private val _finished = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    /** Emits when a rest runs out while the app process is alive (used for the in-app message). */
    val finished: SharedFlow<Unit> = _finished.asSharedFlow()

    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Call once from Application.onCreate. */
    fun init(ctx: Context) {
        createChannels(ctx)
        val p = prefs(ctx)
        val saved = p.getLong(KEY_END, 0L)
        val now = System.currentTimeMillis()
        when {
            saved > now -> _endAt.value = saved
            // Just expired: leave it, the alarm that started this process is about to report it.
            saved > 0L && now - saved < 60_000L -> Unit
            saved > 0L -> p.edit().remove(KEY_END).apply()
        }
    }

    fun start(ctx: Context, seconds: Int, title: String, nextLine: String) {
        val end = System.currentTimeMillis() + seconds * 1000L
        prefs(ctx).edit().putLong(KEY_END, end).putString(KEY_TITLE, title).putString(KEY_NEXT, nextLine).apply()
        _endAt.value = end
        schedule(ctx, end)
        postRunning(ctx, end, title, nextLine)
    }

    fun addSeconds(ctx: Context, seconds: Int = ADD_SECONDS) {
        val p = prefs(ctx)
        val end = p.getLong(KEY_END, 0L)
        if (end <= System.currentTimeMillis()) return
        val newEnd = end + seconds * 1000L
        p.edit().putLong(KEY_END, newEnd).apply()
        _endAt.value = newEnd
        schedule(ctx, newEnd)
        postRunning(ctx, newEnd, p.getString(KEY_TITLE, "Rest").orEmpty(), p.getString(KEY_NEXT, "").orEmpty())
    }

    fun stop(ctx: Context) {
        cancelAlarm(ctx)
        NotificationManagerCompat.from(ctx).cancel(ID_RUNNING)
        prefs(ctx).edit().remove(KEY_END).apply()
        _endAt.value = null
    }

    /** The alarm went off. Ignores stale alarms (e.g. one left over from before a +30s). */
    fun onAlarm(ctx: Context) {
        val p = prefs(ctx)
        val end = p.getLong(KEY_END, 0L)
        if (end <= 0L || System.currentTimeMillis() < end - 1500L) return
        val next = p.getString(KEY_NEXT, "").orEmpty()
        p.edit().remove(KEY_END).apply()
        _endAt.value = null
        NotificationManagerCompat.from(ctx).cancel(ID_RUNNING)
        notifyDone(ctx, next)
        _finished.tryEmit(Unit)
    }

    // ---------------------------------------------------------------------------------------------

    private fun broadcast(ctx: Context, action: String, code: Int): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, code, Intent(ctx, RestTimerReceiver::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    private fun openApp(ctx: Context): PendingIntent =
        PendingIntent.getActivity(
            ctx, 0,
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    /** setAlarmClock needs no special permission and isn't delayed by Doze. */
    private fun schedule(ctx: Context, endAt: Long) {
        try {
            val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            am.setAlarmClock(AlarmManager.AlarmClockInfo(endAt, openApp(ctx)), broadcast(ctx, ACTION_DONE, 1))
        } catch (_: Exception) { /* the in-app timer UI still works */ }
    }

    private fun cancelAlarm(ctx: Context) {
        try {
            (ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(broadcast(ctx, ACTION_DONE, 1))
        } catch (_: Exception) { }
    }

    private fun postRunning(ctx: Context, endAt: Long, title: String, nextLine: String) {
        val nm = NotificationManagerCompat.from(ctx)
        if (!nm.areNotificationsEnabled()) return
        val remaining = (endAt - System.currentTimeMillis()).coerceAtLeast(0L)
        val view = RemoteViews(ctx.packageName, R.layout.notif_rest).apply {
            setTextViewText(R.id.rest_title, title.uppercase())
            setTextViewText(R.id.rest_next, nextLine)
            setChronometerCountDown(R.id.rest_chrono, true)
            setChronometer(R.id.rest_chrono, SystemClock.elapsedRealtime() + remaining, null, true)
        }
        val n = NotificationCompat.Builder(ctx, CH_RUNNING)
            .setSmallIcon(R.drawable.ic_timer)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(view)
            .setContentTitle(title)
            .setContentText(nextLine)
            .setColor(0xFFD7FF3F.toInt())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(openApp(ctx))
            .addAction(0, "+30s", broadcast(ctx, ACTION_ADD, 2))
            .addAction(0, "Stop", broadcast(ctx, ACTION_STOP, 3))
            .setTimeoutAfter(remaining + 5_000L)
            .build()
        try { nm.notify(ID_RUNNING, n) } catch (_: SecurityException) { }
    }

    private fun notifyDone(ctx: Context, nextLine: String) {
        val nm = NotificationManagerCompat.from(ctx)
        val enabled = nm.areNotificationsEnabled()
        // Without a notification (older Android, or permission denied) fall back to buzz + chime.
        if (!enabled || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) alertRestOver(ctx)
        if (!enabled) return
        val n = NotificationCompat.Builder(ctx, CH_DONE)
            .setSmallIcon(R.drawable.ic_timer)
            .setContentTitle("Rest is over")
            .setContentText(nextLine.ifBlank { "Time for your next set" })
            .setColor(0xFFD7FF3F.toInt())
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(openApp(ctx))
            .setTimeoutAfter(10 * 60_000L)
            .build()
        try { nm.notify(ID_DONE, n) } catch (_: SecurityException) { }
    }

    private fun createChannels(ctx: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CH_RUNNING, "Rest timer", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Live countdown while you rest between sets"
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_DONE, "Rest finished", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Buzzes and chimes when your rest is over"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 400, 200, 400)
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
                )
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
        )
    }
}
