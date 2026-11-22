package com.kite.zmusic.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/** 播客模式这一次进程里的页面缓存。返回上一页、再次进入同一页时先用这里的结果。 */
class PodcastSessionCache {
    data class Episodes(
        val show: PodcastShow?,
        val episodes: List<PodcastEpisode>,
        val hasMore: Boolean,
        val at: Long,
    )

    data class Remote(
        val shows: List<PodcastShow>,
        val episodes: List<PodcastEpisode>,
        val hasMore: Boolean,
        val at: Long,
    )

    data class Comments(
        val comments: List<SongComment>,
        val hasMore: Boolean,
        val at: Long,
    )

    private val episodes = HashMap<String, Episodes>()
    private val remotes = HashMap<String, Remote>()
    private val comments = HashMap<Long, Comments>()
    private val scripts = HashMap<Long, String>()
    private val searches = HashMap<String, Remote>()

    private val subscriptions = HashMap<Long, PodcastShow>()

    /** 订阅变更后加一，界面读它才会跟着刷新。 */
    var subscriptionRevision by mutableIntStateOf(0)
        private set

    @Volatile
    private var homeFeed: PodcastFeed? = null

    /** 发现页整页结果。离开播客模式再进来仍用这份，下拉刷新才会重拉。 */
    fun homeFeed(): PodcastFeed? = homeFeed?.takeIf { it.hasDiscoverContent() }

    fun saveHomeFeed(feed: PodcastFeed) {
        if (feed.hasDiscoverContent()) homeFeed = feed
    }

    fun episodes(showId: Long, asc: Boolean): Episodes? = episodes["$showId:$asc"]?.takeIf { fresh(it.at) }

    fun saveEpisodes(showId: Long, asc: Boolean, show: PodcastShow?, list: List<PodcastEpisode>, hasMore: Boolean) {
        episodes["$showId:$asc"] = Episodes(show, list, hasMore, System.currentTimeMillis())
    }

    fun remote(key: String): Remote? = remotes[key]?.takeIf { fresh(it.at) }

    fun saveRemote(key: String, shows: List<PodcastShow>, episodes: List<PodcastEpisode>, hasMore: Boolean) {
        remotes[key] = Remote(shows, episodes, hasMore, System.currentTimeMillis())
    }

    fun comments(programId: Long): Comments? = comments[programId]?.takeIf { fresh(it.at) }

    fun saveComments(programId: Long, list: List<SongComment>, hasMore: Boolean) {
        comments[programId] = Comments(list, hasMore, System.currentTimeMillis())
    }

    fun script(programId: Long): String? = scripts[programId]

    fun saveScript(programId: Long, text: String) {
        if (text.isNotBlank()) scripts[programId] = text
    }

    fun search(key: String): Remote? = searches[key]?.takeIf { fresh(it.at) }

    fun saveSearch(key: String, shows: List<PodcastShow>, episodes: List<PodcastEpisode>, hasMore: Boolean) {
        searches[key] = Remote(shows, episodes, hasMore, System.currentTimeMillis())
    }

    fun clear() {
        episodes.clear()
        remotes.clear()
        comments.clear()
        scripts.clear()
        searches.clear()
        subscriptions.clear()
        subscriptionRevision++
        homeFeed = null
    }

    /** 这次进程里用户刚改过的订阅。切页、重新进电台都认这份，不被旧缓存盖掉。 */
    fun rememberSubscription(show: PodcastShow) {
        subscriptions[show.id] = show
        val prefix = "${show.id}:"
        for (key in episodes.keys.filter { it.startsWith(prefix) }) {
            val item = episodes[key] ?: continue
            val base = item.show ?: show
            episodes[key] = item.copy(
                show = base.copy(
                    subscribed = show.subscribed,
                    subCount = show.subCount,
                ),
            )
        }
        homeFeed = homeFeed?.let { withSubscriptions(it) }
        subscriptionRevision++
    }

    fun applySubscription(show: PodcastShow?): PodcastShow? {
        if (show == null) return null
        val remembered = subscriptions[show.id] ?: return show
        return show.copy(subscribed = remembered.subscribed, subCount = remembered.subCount)
    }

    fun withSubscriptions(feed: PodcastFeed): PodcastFeed {
        if (subscriptions.isEmpty()) return feed
        return feed.copy(subscribed = mergedSubscribed(feed.subscribed))
    }

    private fun mergedSubscribed(remote: List<PodcastShow>): List<PodcastShow> {
        val out = LinkedHashMap<Long, PodcastShow>()
        for (show in remote) {
            val next = applySubscription(show) ?: continue
            if (next.subscribed) out[next.id] = next
        }
        for (show in subscriptions.values) {
            if (show.subscribed) out.putIfAbsent(show.id, show) else out.remove(show.id)
        }
        return out.values.toList()
    }

    private fun fresh(at: Long): Boolean = System.currentTimeMillis() - at < FRESH_MS

    private companion object {
        const val FRESH_MS = 3 * 60 * 1000L
    }
}

private fun PodcastFeed.hasDiscoverContent(): Boolean =
    banners.isNotEmpty() || today != null || recommends.isNotEmpty() ||
        hot.isNotEmpty() || categories.isNotEmpty() || subscribed.isNotEmpty() ||
        newest.isNotEmpty() || programChart.isNotEmpty()
