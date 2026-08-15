package com.kite.zmusic.ui.server

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kite.zmusic.data.NcmConnectivityClient
import com.kite.zmusic.data.ServerConfigRepository
import kotlinx.coroutines.launch

class ServerConfigViewModel(
    private val serverConfigRepository: ServerConfigRepository,
    private val connectivityClient: NcmConnectivityClient = NcmConnectivityClient(),
) : ViewModel() {

    private val initial = serverConfigRepository.currentEndpoint()
    private var committedHost = initial.host
    private var hostIsMask = true

    var host by mutableStateOf(ServerConfigRepository.maskHost(initial.host))
        private set
    var portText by mutableStateOf(initial.port.toString())
        private set
    var busy by mutableStateOf(false)
        private set
    var bannerError by mutableStateOf<String?>(null)
        private set
    var statusHint by mutableStateOf<String?>(null)
        private set

    fun onHostChange(value: String) {
        val cleaned = value.filter { !it.isWhitespace() }
        bannerError = null
        statusHint = null
        if (hostIsMask) {
            val mask = ServerConfigRepository.maskHost(committedHost)
            if (cleaned == mask) {
                host = mask
                return
            }
            hostIsMask = false
            host = when {
                cleaned.isEmpty() -> ""
                ServerConfigRepository.looksMasked(cleaned) -> ""
                mask.startsWith(cleaned) -> ""
                else -> cleaned
            }
            return
        }
        host = cleaned
    }

    fun onPortChange(value: String) {
        portText = value.filter { it.isDigit() }.take(5)
        bannerError = null
        statusHint = null
    }

    fun dismissError() {
        bannerError = null
    }

    fun reloadFromStore() {
        val endpoint = serverConfigRepository.currentEndpoint()
        committedHost = endpoint.host
        hostIsMask = true
        host = ServerConfigRepository.maskHost(endpoint.host)
        portText = endpoint.port.toString()
        busy = false
        bannerError = null
        statusHint = null
    }

    /**
     * 校验 → 探测 → 持久化；成功回调 [onSuccess]。
     */
    fun saveAndConnect(onSuccess: () -> Unit) {
        if (busy) return
        val typed = host.trim()
        val h = when {
            hostIsMask -> committedHost
            typed.isEmpty() -> committedHost
            ServerConfigRepository.looksMasked(typed) -> {
                bannerError = "请输入完整主机或 IP"
                return
            }
            else -> typed
        }
        if (h.isEmpty()) {
            bannerError = "请输入服务器 IP 或主机名"
            return
        }
        val port = portText.toIntOrNull()
        if (port == null || port !in 1..65535) {
            bannerError = "端口须为 1–65535"
            return
        }
        busy = true
        bannerError = null
        statusHint = "正在探测连接…"
        viewModelScope.launch {
            val endpoint = ServerConfigRepository.Endpoint(h, port)
            val result = connectivityClient.checkReachable(endpoint.toBaseUrl())
            busy = false
            result.fold(
                onSuccess = {
                    serverConfigRepository.persist(h, port)
                    committedHost = h
                    hostIsMask = true
                    host = ServerConfigRepository.maskHost(h)
                    statusHint = "连接成功"
                    onSuccess()
                },
                onFailure = {
                    statusHint = null
                    bannerError = "无法连接该地址，请检查主机与端口"
                },
            )
        }
    }
}
