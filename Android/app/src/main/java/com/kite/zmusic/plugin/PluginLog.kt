package com.kite.zmusic.plugin

import android.util.Log

internal object PluginLog {
    const val TAG = "ZMusic.PluginEngine"

    fun d(enabled: Boolean, message: String) {
        if (enabled) Log.d(TAG, message)
    }

    fun w(enabled: Boolean, message: String) {
        if (enabled) Log.w(TAG, message)
    }

    fun e(enabled: Boolean, message: String, error: Throwable? = null) {
        if (!enabled) return
        if (error != null) Log.e(TAG, message, error) else Log.e(TAG, message)
    }
}
