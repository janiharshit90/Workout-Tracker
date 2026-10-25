package com.example.mentzertracker.timer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Receives the rest-over alarm and the +30s / Stop notification buttons. */
class RestTimerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            RestTimer.ACTION_DONE -> RestTimer.onAlarm(context)
            RestTimer.ACTION_ADD -> RestTimer.addSeconds(context)
            RestTimer.ACTION_STOP -> RestTimer.stop(context)
        }
    }
}
