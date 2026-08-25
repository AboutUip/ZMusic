package com.kite.zmusic.plugin

import com.whl.quickjs.wrapper.JSArray
import com.whl.quickjs.wrapper.JSFunction
import com.whl.quickjs.wrapper.JSObject
import java.util.Collections
import java.util.IdentityHashMap

/**
 * `hook.run` 参数：必须能 JSON 表达。失败与 JSON `null` 区分。
 */
internal sealed class PluginJsonCopy {
    class Ok(val value: Any?) : PluginJsonCopy()
    data object Fail : PluginJsonCopy()
}

internal object PluginJsonable {
    fun copy(raw: Any?): PluginJsonCopy {
        val jsSeen = HashSet<Long>()
        val javaSeen = Collections.newSetFromMap(IdentityHashMap<Any, Boolean>())
        return copy(raw, jsSeen, javaSeen)
    }

    private fun copy(
        raw: Any?,
        jsSeen: HashSet<Long>,
        javaSeen: MutableSet<Any>,
    ): PluginJsonCopy {
        when (raw) {
            null -> return PluginJsonCopy.Ok(null)
            is Boolean -> return PluginJsonCopy.Ok(raw)
            is String -> return PluginJsonCopy.Ok(raw)
            is Int -> return PluginJsonCopy.Ok(raw)
            is Long -> return PluginJsonCopy.Ok(raw)
            is Double -> {
                if (raw.isNaN() || raw.isInfinite()) return PluginJsonCopy.Fail
                return PluginJsonCopy.Ok(raw)
            }
            is Float -> {
                val d = raw.toDouble()
                if (d.isNaN() || d.isInfinite()) return PluginJsonCopy.Fail
                return PluginJsonCopy.Ok(d)
            }
            is JSFunction -> return PluginJsonCopy.Fail
            is JSArray -> {
                val ptr = raw.getPointer()
                if (!jsSeen.add(ptr)) return PluginJsonCopy.Fail
                try {
                    val n = raw.length()
                    val list = ArrayList<Any?>(n)
                    for (i in 0 until n) {
                        when (val item = copy(raw.get(i), jsSeen, javaSeen)) {
                            PluginJsonCopy.Fail -> return PluginJsonCopy.Fail
                            is PluginJsonCopy.Ok -> list.add(item.value)
                        }
                    }
                    return PluginJsonCopy.Ok(list)
                } finally {
                    jsSeen.remove(ptr)
                }
            }
            is JSObject -> {
                val ptr = raw.getPointer()
                if (!jsSeen.add(ptr)) return PluginJsonCopy.Fail
                val names = raw.getNames()
                if (names == null) {
                    jsSeen.remove(ptr)
                    return PluginJsonCopy.Fail
                }
                try {
                    val map = LinkedHashMap<String, Any?>()
                    val n = names.length()
                    for (i in 0 until n) {
                        val key = names.get(i) as? String ?: return PluginJsonCopy.Fail
                        when (val item = copy(raw.getProperty(key), jsSeen, javaSeen)) {
                            PluginJsonCopy.Fail -> return PluginJsonCopy.Fail
                            is PluginJsonCopy.Ok -> map[key] = item.value
                        }
                    }
                    return PluginJsonCopy.Ok(map)
                } finally {
                    names.release()
                    jsSeen.remove(ptr)
                }
            }
            is Map<*, *> -> {
                if (!javaSeen.add(raw)) return PluginJsonCopy.Fail
                try {
                    val map = LinkedHashMap<String, Any?>()
                    for ((k, v) in raw) {
                        val key = k as? String ?: return PluginJsonCopy.Fail
                        when (val item = copy(v, jsSeen, javaSeen)) {
                            PluginJsonCopy.Fail -> return PluginJsonCopy.Fail
                            is PluginJsonCopy.Ok -> map[key] = item.value
                        }
                    }
                    return PluginJsonCopy.Ok(map)
                } finally {
                    javaSeen.remove(raw)
                }
            }
            is List<*> -> {
                if (!javaSeen.add(raw)) return PluginJsonCopy.Fail
                try {
                    val list = ArrayList<Any?>(raw.size)
                    for (v in raw) {
                        when (val item = copy(v, jsSeen, javaSeen)) {
                            PluginJsonCopy.Fail -> return PluginJsonCopy.Fail
                            is PluginJsonCopy.Ok -> list.add(item.value)
                        }
                    }
                    return PluginJsonCopy.Ok(list)
                } finally {
                    javaSeen.remove(raw)
                }
            }
            is Number -> {
                val d = raw.toDouble()
                if (d.isNaN() || d.isInfinite()) return PluginJsonCopy.Fail
                return PluginJsonCopy.Ok(raw)
            }
            else -> return PluginJsonCopy.Fail
        }
    }
}
