package com.kite.zmusic.plugin

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 失败事件队列。一次只展示一条，关掉后再出下一条。
 */
internal class PluginFaultCenter {
    private val lock = Any()
    private val pending = ArrayDeque<PluginFault>(MaxQueued)
    private val _current = MutableStateFlow<PluginFault?>(null)
    val current: StateFlow<PluginFault?> = _current.asStateFlow()

    fun report(fault: PluginFault) {
        synchronized(lock) {
            if (_current.value == null) {
                _current.value = fault
                return
            }
            while (pending.size >= MaxQueued) pending.removeFirst()
            pending.addLast(fault)
        }
    }

    fun dismiss() {
        synchronized(lock) {
            _current.value = pending.removeFirstOrNull()
        }
    }

    companion object {
        const val MaxQueued = 8
    }
}
