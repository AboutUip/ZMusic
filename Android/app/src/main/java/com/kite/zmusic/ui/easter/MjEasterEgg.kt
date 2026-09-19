package com.kite.zmusic.ui.easter

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 聊天 / 搜索 / 评论输入 `mj` 发送时触发的彩蛋。
 * 不拦截原逻辑，只额外弹出图层。
 */
object MjEasterEgg {
    private val _generation = MutableStateFlow(0)
    val generation: StateFlow<Int> = _generation.asStateFlow()

    fun consider(text: String) {
        if (!matches(text)) return
        _generation.update { it + 1 }
    }

    fun matches(text: String): Boolean =
        text.trim().equals(TRIGGER, ignoreCase = true)

    private const val TRIGGER = "mj"
}
