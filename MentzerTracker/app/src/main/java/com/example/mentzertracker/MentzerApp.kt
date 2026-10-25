package com.example.mentzertracker

import android.app.Application
import com.example.mentzertracker.util.CrashLog

class MentzerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
    }
}
