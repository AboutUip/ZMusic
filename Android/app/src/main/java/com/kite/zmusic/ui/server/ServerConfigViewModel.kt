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

    var host by mutableStateOf(initial.host)
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
        host = value.filter { !it.isWhitespace() }
        bannerError = null
        statusHint = null
    }

    fun onPortChange(value: String) {
        portText = value.filter { it.isDigit() }.take(5)
        bannerError = null
        statusHint = null
    }

    fun dismissError() {
        bannerError = null
    }

    /**
     * 校验 → 探测 → 持久化；成功回调 [onSuccess]。
     */
    fun saveAndConnect(onSuccess: () -> Unit) {
        if (busy) return
        val h = host.trim()
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
                    statusHint = "连接成功"
                    onSuccess()
                },
                onFailure = { e ->
                    statusHint = null
                    bannerError = "无法连接 ${endpoint.toBaseUrl()}\n${e.message ?: "网络错误"}"
                },
            )
        }
    }
}
