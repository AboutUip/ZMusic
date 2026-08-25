package com.kite.zmusic.plugin

/**
 * 全局钩子登记。监听器函数留在各 [PluginSession]。
 * 投递顺序：插件 id 字典序，同插件内按 add 先后。
 */
internal class PluginHookRegistry {
    data class Ref(
        val pluginId: String,
        val event: String,
        val listenerId: String,
        val order: Long,
    )

    private val lock = Any()
    private var nextOrder = 0L
    private val byPlugin = LinkedHashMap<String, LinkedHashMap<String, LinkedHashMap<String, Long>>>()

    fun add(pluginId: String, event: String, listenerId: String) {
        synchronized(lock) {
            val events = byPlugin.getOrPut(pluginId) { LinkedHashMap() }
            val ids = events.getOrPut(event) { LinkedHashMap() }
            if (listenerId in ids) {
                ids[listenerId] = ids[listenerId]!!
                return
            }
            ids[listenerId] = nextOrder++
        }
    }

    fun remove(pluginId: String, event: String, listenerId: String) {
        synchronized(lock) {
            val events = byPlugin[pluginId] ?: return
            val ids = events[event] ?: return
            ids.remove(listenerId)
            if (ids.isEmpty()) events.remove(event)
            if (events.isEmpty()) byPlugin.remove(pluginId)
        }
    }

    fun dropPlugin(pluginId: String) {
        synchronized(lock) { byPlugin.remove(pluginId) }
    }

    fun listeners(event: String): List<Ref> {
        synchronized(lock) {
            val out = ArrayList<Ref>()
            for ((pluginId, events) in byPlugin) {
                val ids = events[event] ?: continue
                for ((listenerId, order) in ids) {
                    out.add(Ref(pluginId, event, listenerId, order))
                }
            }
            return out.sortedWith(compareBy({ it.pluginId }, { it.order }))
        }
    }
}
