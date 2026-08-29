package com.kite.zmusic.ui.notice

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class IslandNotice(
    val token: Long,
    val message: String,
    val coverUrl: String? = null,
)

class IslandNoticeCenter {
    private val _notice = MutableStateFlow<IslandNotice?>(null)
    val notice: StateFlow<IslandNotice?> = _notice.asStateFlow()

    fun show(message: String, coverUrl: String? = null) {
        val t = message.trim()
        if (t.isEmpty()) return
        _notice.value = IslandNotice(System.currentTimeMillis(), t, coverUrl)
    }

    fun clear() {
        _notice.value = null
    }
}
