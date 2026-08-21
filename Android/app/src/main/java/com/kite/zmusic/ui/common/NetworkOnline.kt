package com.kite.zmusic.ui.common

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kite.zmusic.ZMusicApplication
import com.kite.zmusic.data.isNetworkOnline

@Composable
fun rememberNetworkOnline(): Boolean {
    val context = LocalContext.current.applicationContext
    val app = context as? ZMusicApplication
    if (app != null) {
        val net by app.networkMode.state.collectAsStateWithLifecycle()
        return net.online
    }
    var online by remember { mutableStateOf(context.isNetworkOnline()) }
    DisposableEffect(context) {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        if (cm == null) {
            online = false
            return@DisposableEffect onDispose { }
        }
        val callback = object : ConnectivityManager.NetworkCallback() {
            private fun refresh() {
                context.mainExecutor.execute {
                    online = context.isNetworkOnline()
                }
            }

            override fun onAvailable(network: Network) = refresh()
            override fun onLost(network: Network) = refresh()
            override fun onUnavailable() = refresh()
            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities,
            ) = refresh()
        }
        runCatching { cm.registerDefaultNetworkCallback(callback) }
        online = context.isNetworkOnline()
        onDispose { runCatching { cm.unregisterNetworkCallback(callback) } }
    }
    return online
}
