package com.kite.zmusic.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 搜索历史：网易云 API 无对应接口，本地持久化。
 * 最多 [MAX] 条（新的在前），与常见音乐客户端一致，避免挤掉热搜。
 */
class SearchHistoryRepository(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val cacheFile = File(appContext.filesDir, "zmusic_search_history.json")

    private val _items = MutableStateFlow<List<String>>(emptyList())
    val items: StateFlow<List<String>> = _items.asStateFlow()

    init {
        scope.launch(Dispatchers.IO) {
            val loaded = loadFromDisk()
            if (loaded.isNotEmpty()) _items.value = loaded
        }
    }

    fun record(keyword: String) {
        val q = keyword.trim()
        if (q.isEmpty()) return
        val next = buildList {
            add(q)
            _items.value.forEach { old ->
                if (!old.equals(q, ignoreCase = true) && size < MAX) add(old)
            }
        }
        _items.value = next
        persist()
    }

    fun remove(keyword: String) {
        _items.value = _items.value.filterNot { it.equals(keyword, ignoreCase = true) }
        persist()
    }

    fun clear() {
        _items.value = emptyList()
        persist()
    }

    private fun persist() {
        val snapshot = _items.value
        scope.launch(Dispatchers.IO) {
            runCatching {
                val arr = JSONArray()
                snapshot.forEach { arr.put(it) }
                cacheFile.writeText(JSONObject().put("items", arr).toString(), Charsets.UTF_8)
            }.onFailure { Log.w(TAG, "persist search history failed", it) }
        }
    }

    private fun loadFromDisk(): List<String> {
        if (!cacheFile.exists()) return emptyList()
        return runCatching {
            val arr = JSONObject(cacheFile.readText(Charsets.UTF_8)).optJSONArray("items")
                ?: return emptyList()
            val out = ArrayList<String>(MAX)
            for (i in 0 until arr.length()) {
                val w = arr.optString(i, "").trim()
                if (w.isEmpty()) continue
                if (out.any { it.equals(w, ignoreCase = true) }) continue
                out.add(w)
                if (out.size >= MAX) break
            }
            out
        }.getOrElse { emptyList() }
    }

    companion object {
        private const val TAG = "SearchHistoryRepo"
        const val MAX = 10
    }
}
