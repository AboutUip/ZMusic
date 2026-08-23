package com.kite.zmusic.data

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.net.URLEncoder

data class CommunityCatalogPage<T>(
    val ok: Boolean,
    val error: String,
    val more: Boolean,
    val entries: List<T>,
)

internal fun catalogString(value: Any?): String =
    value?.toString()?.trim().orEmpty().let { if (it == "null") "" else it }

internal fun catalogLong(value: Any?): Long? = when (value) {
    is Number -> value.toLong()
    is String -> value.trim().toLongOrNull()
    else -> null
}

internal fun <T> parseCatalogArray(
    snapshot: Any?,
    arrayKey: String,
    parseItem: (Any?) -> T?,
): CommunityCatalogPage<T> {
    val root = snapshot as? Map<*, *>
        ?: return CommunityCatalogPage(false, "unavailable", false, emptyList())
    val ok = root["ok"] != false
    val error = catalogString(root["error"])
    if (!ok) {
        return CommunityCatalogPage(false, error.ifBlank { "unavailable" }, false, emptyList())
    }
    val more = root["more"] as? Boolean ?: false
    val raw = root[arrayKey] as? List<*> ?: emptyList<Any?>()
    val entries = ArrayList<T>(raw.size)
    for (item in raw) {
        parseItem(item)?.let { entries += it }
    }
    return CommunityCatalogPage(true, "", more, entries)
}

/**
 * 公开目录：进页拉第 1 页，近底翻页，搜索走独立接口。缓存只活在本进程。
 */
open class PagedCommunityCatalog<T>(
    private val client: CommunityXaiopClient,
    private val rangePath: String,
    private val searchPath: String,
    private val perPage: Int,
    private val searchLimit: Int,
    private val parse: (Any?) -> CommunityCatalogPage<T>,
    private val listKey: (T) -> String,
    private val normalizeQuery: (String) -> String,
) {
    private val mutex = Mutex()
    private val searchCache = object : LinkedHashMap<String, List<T>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<T>>?) =
            size > SearchCacheCap
    }

    @Volatile var rangeEntries: List<T> = emptyList()
        private set
    @Volatile var more: Boolean = false
        private set
    @Volatile var rangeReady: Boolean = false
        private set
    @Volatile var rangeFailed: Boolean = false
        private set

    private var nextPage = 1
    private var cacheAuthority: String? = null

    suspend fun ensureRange() {
        mutex.withLock {
            bumpHost()
            if (rangeReady) return
            val page = fetchRange(1)
            if (page == null) {
                rangeFailed = true
                return
            }
            applyFirstPage(page)
        }
    }

    suspend fun refreshRange(): Boolean {
        mutex.withLock {
            bumpHost()
            val page = fetchRange(1) ?: run {
                if (!rangeReady) rangeFailed = true
                return false
            }
            applyFirstPage(page)
            searchCache.clear()
            return true
        }
    }

    suspend fun loadMore() {
        mutex.withLock {
            bumpHost()
            if (!rangeReady || !more) return
            val page = fetchRange(nextPage) ?: return
            rangeEntries = merge(rangeEntries, page.entries)
            more = page.more
            nextPage += 1
        }
    }

    suspend fun search(query: String): List<T>? {
        val q = normalizeQuery(query)
        if (q.isEmpty()) return rangeEntries
        mutex.withLock {
            bumpHost()
            searchCache[q]?.let { return it }
        }
        val page = fetchSearch(q) ?: return null
        mutex.withLock {
            searchCache[q] = page.entries
        }
        return page.entries
    }

    suspend fun refreshSearch(query: String): List<T>? {
        val q = normalizeQuery(query)
        if (q.isEmpty()) {
            val ok = refreshRange()
            return if (ok || rangeReady) rangeEntries else null
        }
        mutex.withLock {
            bumpHost()
            searchCache.remove(q)
        }
        return search(query)
    }

    private fun applyFirstPage(page: CommunityCatalogPage<T>) {
        rangeEntries = page.entries
        more = page.more
        nextPage = 2
        rangeReady = true
        rangeFailed = false
    }

    private fun bumpHost() {
        val now = client.authority()
        if (cacheAuthority != null && cacheAuthority != now) {
            rangeEntries = emptyList()
            more = false
            rangeReady = false
            rangeFailed = false
            nextPage = 1
            searchCache.clear()
        }
        cacheAuthority = now
    }

    private suspend fun fetchRange(page: Int): CommunityCatalogPage<T>? =
        client.withRemoteRetry {
            val snapshot = client.get(rangeUrl(page))
            val parsed = parse(snapshot)
            if (!parsed.ok) error(parsed.error.ifBlank { "unavailable" })
            parsed
        }

    private suspend fun fetchSearch(q: String): CommunityCatalogPage<T>? =
        client.withRemoteRetry {
            val snapshot = client.get(searchUrl(q))
            val parsed = parse(snapshot)
            if (!parsed.ok) error(parsed.error.ifBlank { "unavailable" })
            parsed
        }

    private fun merge(current: List<T>, incoming: List<T>): List<T> {
        if (incoming.isEmpty()) return current
        val seen = current.mapTo(HashSet(current.size + incoming.size), listKey)
        val out = ArrayList<T>(current.size + incoming.size)
        out.addAll(current)
        for (entry in incoming) {
            if (seen.add(listKey(entry))) out += entry
        }
        return out
    }

    private fun rangeUrl(page: Int): String =
        client.httpUrl(rangePath, "page=$page&per_page=$perPage")

    private fun searchUrl(q: String): String {
        val encoded = URLEncoder.encode(q, Charsets.UTF_8.name())
        return client.httpUrl(searchPath, "q=$encoded&limit=$searchLimit")
    }

    companion object {
        private const val SearchCacheCap = 16
    }
}

fun normalizeCatalogQuery(raw: String): String =
    raw.trim().filter { !it.isISOControl() }.lowercase()
