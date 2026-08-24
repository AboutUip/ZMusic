package com.kite.zmusic.plugin

import com.whl.quickjs.android.QuickJSLoader

internal object PluginQuickJs {
    @Volatile
    private var loaded = false
    private val lock = Any()

    fun ensure() {
        if (loaded) return
        synchronized(lock) {
            if (!loaded) {
                QuickJSLoader.init()
                loaded = true
            }
        }
    }
}
