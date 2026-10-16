package com.kite.zmusic.ui.server

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kite.zmusic.data.CommunityServerStore
import com.kite.zmusic.data.ServerConfigRepository
import kotlinx.coroutines.launch
import com.kite.zmusic.i18n.t

class CommunityServerViewModel(
    private val store: CommunityServerStore,
) : ViewModel() {

    private val initial = store.current()
    private var committed = initial
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
            val mask = ServerConfigRepository.maskHost(committed.host)
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

    fun reloadFromStore() {
        val endpoint = store.current()
        committed = endpoint
        hostIsMask = true
        host = ServerConfigRepository.maskHost(endpoint.host)
        portText = endpoint.port.toString()
        busy = false
        bannerError = null
        statusHint = null
    }

    fun saveAndConnect(onSuccess: () -> Unit) {
        if (busy) return
        val typed = host.trim()
        val endpoint = resolveEndpoint(typed) ?: return
        busy = true
        bannerError = null
        statusHint = t("正在探测连接…")
        viewModelScope.launch {
            val result = CommunityServerStore.probe(endpoint.host, endpoint.port)
            busy = false
            result.fold(
                onSuccess = {
                    store.persist(endpoint)
                    committed = endpoint
                    hostIsMask = true
                    host = ServerConfigRepository.maskHost(endpoint.host)
                    portText = endpoint.port.toString()
                    statusHint = t("连接成功")
                    onSuccess()
                },
                onFailure = {
                    statusHint = null
                    bannerError = t("无法连接该地址，请检查主机与端口")
                },
            )
        }
    }

    private fun resolveEndpoint(typed: String): ServerConfigRepository.Endpoint? {
        if (hostIsMask || typed.isEmpty()) {
            val port = portText.toIntOrNull()
            if (port == null || port !in 1..65535) {
                bannerError = t("端口须为 1–65535")
                return null
            }
            return committed.copy(port = port).normalized()
        }
        if (ServerConfigRepository.looksMasked(typed)) {
            bannerError = t("请输入完整的 URL、域名或 IP")
            return null
        }
        val fallback = portText.toIntOrNull()
        val parsed = ServerConfigRepository.parseServerAddress(typed, fallback ?: -1)
        if (parsed == null) {
            bannerError = if (
                (fallback == null || fallback !in 1..65535) &&
                !typed.contains("://") &&
                !typed.contains(':')
            ) {
                t("端口须为 1–65535")
            } else {
                t("请输入 URL、域名或 IP")
            }
            return null
        }
        return parsed
    }
}

class CommunityServerViewModelFactory(
    private val store: CommunityServerStore,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CommunityServerViewModel::class.java)) {
            return CommunityServerViewModel(store) as T
        }
        error("Unknown ViewModel: ${modelClass.name}")
    }
}
