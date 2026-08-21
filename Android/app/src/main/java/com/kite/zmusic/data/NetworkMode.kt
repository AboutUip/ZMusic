package com.kite.zmusic.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

enum class NetworkPhase {
    Online,
    Fluctuating,
    Offline,
}

data class NetworkUiState(
    val phase: NetworkPhase,
    val online: Boolean,
)

sealed class NetworkCommand {
    data object ForceHome : NetworkCommand()
}

internal object NetworkPhaseLogic {
    const val GRACE_MS = 60_000L

    fun onLinkChanged(phase: NetworkPhase, online: Boolean): NetworkPhase = when (phase) {
        NetworkPhase.Online -> if (online) NetworkPhase.Online else NetworkPhase.Fluctuating
        NetworkPhase.Fluctuating -> if (online) NetworkPhase.Online else NetworkPhase.Fluctuating
        NetworkPhase.Offline -> if (online) NetworkPhase.Online else NetworkPhase.Offline
    }

    fun onGraceExpired(phase: NetworkPhase, online: Boolean): NetworkPhase {
        if (phase != NetworkPhase.Fluctuating) return phase
        return if (online) NetworkPhase.Online else NetworkPhase.Offline
    }

    /** [from] 为 null 表示主界面第一次看到该相位：在线开场不提示。 */
    fun islandNotice(from: NetworkPhase?, to: NetworkPhase): String? = when (to) {
        NetworkPhase.Fluctuating -> "网络波动，正在等待恢复"
        NetworkPhase.Offline -> "已进入离线模式"
        NetworkPhase.Online -> if (from != null) "网络已恢复" else null
    }
}

fun Context.isNetworkOnline(): Boolean {
    val cm = getSystemService(ConnectivityManager::class.java) ?: return false
    val network = cm.activeNetwork ?: return false
    val caps = cm.getNetworkCapabilities(network) ?: return false
    // 只看是否具备上网能力。VALIDATED 依赖系统探测（常走 Google），国内 5G 经常失败。
    return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}
