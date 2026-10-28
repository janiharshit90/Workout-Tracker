package com.example.mentzertracker.billing

import android.app.Activity
import android.content.Context
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * GitHub edition: everything is unlocked, nothing to buy. The Play edition has the real
 * implementation (src/play/), with exactly the same API.
 */
object ProAccess {
    /** Whether this edition sells Pro at all (false here, so no Pro UI is shown). */
    const val SOLD_HERE = false

    val isPro: StateFlow<Boolean> = MutableStateFlow(true)
    val price: StateFlow<String?> = MutableStateFlow(null)
    val messages: SharedFlow<String> = MutableSharedFlow()

    fun init(ctx: Context) {}
    fun refresh() {}
    fun purchase(activity: Activity) {}
}
