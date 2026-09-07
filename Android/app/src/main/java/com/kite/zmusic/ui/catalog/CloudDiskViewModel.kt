package com.kite.zmusic.ui.catalog

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kite.zmusic.data.CloudDiskRepository
import com.kite.zmusic.data.CloudDiskSong
import com.kite.zmusic.data.NcmCloudParse
import com.kite.zmusic.data.SessionRepository
import com.kite.zmusic.data.TrackRow
import com.kite.zmusic.ui.notice.IslandNoticeCenter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class CloudDiskViewModel(
    private val sessionRepository: SessionRepository,
    private val repo: CloudDiskRepository,
    private val notices: IslandNoticeCenter,
) : ViewModel() {

    private val _list = MutableStateFlow(
        CatalogListState(
            title = "音乐云盘",
            creatorName = "网易云",
            loading = true,
            complete = false,
            canPage = true,
            isOwnedPlaylist = true,
        ),
    )
    val list: StateFlow<CatalogListState> = _list.asStateFlow()

    private val _songs = MutableStateFlow<List<CloudDiskSong>>(emptyList())
    val songs: StateFlow<List<CloudDiskSong>> = _songs.asStateFlow()

    private val _lyric = MutableStateFlow<Pair<String, String>?>(null)
    val lyric: StateFlow<Pair<String, String>?> = _lyric.asStateFlow()

    private var loadingMore = false
    private var uploading = false
    private val firstMutex = Mutex()

    fun consumeLyric() {
        _lyric.value = null
    }

    fun matched(id: Long): Boolean = _songs.value.any { it.track.id == id && it.matched }

    fun load(force: Boolean = false) {
        viewModelScope.launch { loadFirst(force) }
    }

    fun loadMore() {
        viewModelScope.launch { loadMoreSuspend() }
    }

    suspend fun loadRemaining() {
        if (_list.value.loading || _list.value.refreshing) {
            _list.first { !it.loading && !it.refreshing }
        } else if (_songs.value.isEmpty() && !_list.value.complete) {
            loadFirst()
        }
        var guard = 0
        while (!_list.value.complete && guard < 40) {
            val before = _songs.value.size
            loadMoreSuspend()
            if (_songs.value.size <= before) break
            guard++
        }
    }

    private suspend fun loadFirst(force: Boolean = false) {
        firstMutex.withLock {
            if (!force && _list.value.refreshing) return@withLock
            val session = sessionRepository.session.value
            if (session == null || session.isGuest || session.cookie.isBlank()) {
                _songs.value = emptyList()
                _list.update {
                    it.copy(
                        loading = false,
                        refreshing = false,
                        tracks = emptyList(),
                        error = "请先登录",
                        complete = true,
                    )
                }
                return@withLock
            }
            val had = _songs.value.isNotEmpty()
            _list.update {
                it.copy(
                    loading = !had,
                    refreshing = had,
                    error = null,
                )
            }
            repo.list(offset = 0)
                .onSuccess { page ->
                    applyPage(page.songs, page, replace = true)
                }
                .onFailure { e ->
                    _list.update { cur ->
                        cur.copy(
                            loading = false,
                            refreshing = false,
                            error = if (cur.tracks.isEmpty()) {
                                e.message?.ifBlank { null } ?: "暂时无法打开云盘，点这里重试"
                            } else {
                                cur.error
                            },
                        )
                    }
                    if (_songs.value.isNotEmpty()) {
                        notices.show(e.message ?: "刷新失败")
                    }
                }
        }
    }

    private suspend fun loadMoreSuspend() {
        if (loadingMore || _list.value.complete || _list.value.refreshing) return
        loadingMore = true
        repo.list(offset = _songs.value.size)
            .onSuccess { page ->
                applyPage(_songs.value + page.songs, page, replace = false)
            }
            .onFailure {
                notices.show(it.message ?: "加载失败")
            }
        loadingMore = false
    }

    fun removeTrack(track: TrackRow) {
        viewModelScope.launch {
            val msg = repo.delete(listOf(track.id))
            notices.show(msg, track.coverUrl)
            if (msg.startsWith("已")) load(force = true)
        }
    }

    fun removeTracks(tracks: List<TrackRow>, done: (Boolean) -> Unit) {
        viewModelScope.launch {
            val msg = repo.delete(tracks.map { it.id })
            notices.show(msg, tracks.firstOrNull()?.coverUrl)
            val ok = msg.startsWith("已")
            if (ok) load(force = true)
            done(ok)
        }
    }

    fun unmatch(track: TrackRow) {
        viewModelScope.launch {
            val msg = repo.match(track.id, 0L)
            notices.show(msg, track.coverUrl)
            if (msg.startsWith("已")) load(force = true)
        }
    }

    fun showLyric(track: TrackRow) {
        viewModelScope.launch {
            val text = repo.lyric(track.id)
            if (text.isNullOrBlank()) {
                notices.show("这首云盘文件没有内嵌歌词", track.coverUrl)
            } else {
                _lyric.value = track.name to text
            }
        }
    }

    fun upload(uris: List<Uri>) {
        if (uris.isEmpty() || uploading) return
        viewModelScope.launch {
            uploading = true
            uris.forEachIndexed { i, uri ->
                if (uris.size > 1) {
                    notices.show("正在上传 ${i + 1}/${uris.size}")
                } else {
                    notices.show("正在上传")
                }
                val msg = repo.upload(uri)
                notices.show(msg)
            }
            uploading = false
            load(force = true)
        }
    }

    private fun applyPage(
        all: List<CloudDiskSong>,
        page: com.kite.zmusic.data.CloudDiskPage,
        replace: Boolean,
    ) {
        val songs = if (replace) page.songs else all.distinctBy { it.track.id }
        _songs.value = songs
        val used = page.sizeBytes
        val max = page.maxSizeBytes
        val quota = if (max > 0L) {
            "${NcmCloudParse.formatBytes(used)} / ${NcmCloudParse.formatBytes(max)}"
        } else {
            null
        }
        val count = page.count.coerceAtLeast(songs.size)
        _list.update {
            it.copy(
                tracks = songs.map { s -> s.track },
                coverUrl = songs.firstOrNull()?.track?.coverUrl,
                subtitle = buildString {
                    append("${count} 首")
                    if (quota != null) append(" · $quota")
                },
                expectedCount = count,
                loading = false,
                refreshing = false,
                error = null,
                complete = !page.hasMore,
                canPage = true,
                isOwnedPlaylist = true,
                creatorName = "网易云",
            )
        }
    }
}

class CloudDiskViewModelFactory(
    private val sessionRepository: SessionRepository,
    private val repo: CloudDiskRepository,
    private val notices: IslandNoticeCenter,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        CloudDiskViewModel(sessionRepository, repo, notices) as T
}
