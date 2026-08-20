package com.kite.zmusic.ui.artist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kite.zmusic.data.ArtistAlbumCard
import com.kite.zmusic.data.ArtistBio
import com.kite.zmusic.data.ArtistSimilar
import com.kite.zmusic.data.NcmArtistParse
import com.kite.zmusic.data.NcmJson
import com.kite.zmusic.data.NcmMvParse
import com.kite.zmusic.data.ArtistRepository
import com.kite.zmusic.data.RecommendMvCard
import com.kite.zmusic.data.SessionRepository
import com.kite.zmusic.data.TrackRow
import com.kite.zmusic.ui.notice.IslandNoticeCenter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val AlbumPage = 20
private const val MvPage = 20

data class ArtistUiState(
    val id: Long,
    val name: String,
    val coverUrl: String? = null,
    val aliasLine: String? = null,
    val identify: String? = null,
    val rankLabel: String? = null,
    val albumSize: Int = 0,
    val musicSize: Int = 0,
    val mvSize: Int = 0,
    val fansCount: Long = 0L,
    val followed: Boolean = false,
    val followBusy: Boolean = false,
    val songs: List<TrackRow> = emptyList(),
    val albums: List<ArtistAlbumCard> = emptyList(),
    val albumsMore: Boolean = false,
    val albumsLoading: Boolean = false,
    val mvs: List<RecommendMvCard> = emptyList(),
    val mvsMore: Boolean = false,
    val mvsLoading: Boolean = false,
    val similar: List<ArtistSimilar> = emptyList(),
    val bio: ArtistBio? = null,
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: String? = null,
)

class ArtistViewModel(
    private val artistId: Long,
    seedName: String,
    seedCover: String?,
    private val sessionRepository: SessionRepository,
    private val islandNotices: IslandNoticeCenter,
    private val artists: ArtistRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(
        ArtistUiState(
            id = artistId,
            name = seedName.ifBlank { "歌手" },
            coverUrl = seedCover,
        ),
    )
    val ui: StateFlow<ArtistUiState> = _ui.asStateFlow()

    private var loadJob: Job? = null
    private var albumJob: Job? = null
    private var mvJob: Job? = null

    fun load(force: Boolean = false) {
        if (!force && !_ui.value.loading && _ui.value.error == null && _ui.value.songs.isNotEmpty()) {
            return
        }
        loadJob?.cancel()
        loadJob = viewModelScope.launch { fetch(refresh = force && !_ui.value.loading) }
    }

    fun refresh() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch { fetch(refresh = true) }
    }

    fun loadMoreAlbums() {
        val state = _ui.value
        if (!state.albumsMore || state.albumsLoading || albumJob?.isActive == true) return
        albumJob = viewModelScope.launch {
            _ui.update { it.copy(albumsLoading = true) }
            try {
                val json = artists.albums(
                    artistId,
                    cookie(),
                    limit = AlbumPage,
                    offset = state.albums.size,
                )
                if (NcmJson.apiCode(json) != 200) {
                    _ui.update { it.copy(albumsLoading = false, albumsMore = false) }
                    return@launch
                }
                val (page, more) = NcmArtistParse.albums(json)
                _ui.update { cur ->
                    val merged = cur.albums.mergeById(page) { it.id }
                    cur.copy(
                        albums = merged,
                        albumsMore = more && page.isNotEmpty(),
                        albumsLoading = false,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _ui.update { it.copy(albumsLoading = false) }
            }
        }
    }

    fun loadMoreMvs() {
        val state = _ui.value
        if (!state.mvsMore || state.mvsLoading || mvJob?.isActive == true) return
        mvJob = viewModelScope.launch {
            _ui.update { it.copy(mvsLoading = true) }
            try {
                val json = artists.mvs(
                    artistId,
                    cookie(),
                    limit = MvPage,
                    offset = state.mvs.size,
                )
                if (NcmJson.apiCode(json) != 200) {
                    _ui.update { it.copy(mvsLoading = false, mvsMore = false) }
                    return@launch
                }
                val page = NcmMvParse.similar(json)
                _ui.update { cur ->
                    val merged = cur.mvs.mergeById(page) { it.id }
                    cur.copy(
                        mvs = merged,
                        mvsMore = NcmMvParse.hasMore(json, page.size, MvPage) && page.isNotEmpty(),
                        mvsLoading = false,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _ui.update { it.copy(mvsLoading = false) }
            }
        }
    }

    fun toggleFollow() {
        val state = _ui.value
        if (state.followBusy || state.id <= 0L) return
        val session = sessionRepository.session.value
        val cookie = session?.cookie.orEmpty()
        if (cookie.isBlank() || session?.isGuest == true) {
            islandNotices.show("请先登录")
            return
        }
        val next = !state.followed
        viewModelScope.launch {
            _ui.update { it.copy(followBusy = true, followed = next) }
            try {
                val json = artists.subscribe(state.id, next, cookie)
                val code = NcmJson.apiCode(json)
                if (code == 301 || code == 302) {
                    revertFollow(next)
                    islandNotices.show("请先登录")
                    return@launch
                }
                if (code != 200) {
                    revertFollow(next)
                    islandNotices.show(
                        NcmJson.userFacingMessage(
                            json,
                            if (next) "收藏失败" else "取消收藏失败",
                        ),
                        state.coverUrl,
                    )
                    return@launch
                }
                _ui.update { it.copy(followBusy = false, followed = next) }
                islandNotices.show(
                    if (next) "已收藏歌手" else "已取消收藏",
                    state.coverUrl,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                revertFollow(next)
                islandNotices.show(NcmJson.userFacingThrowable(e, "操作失败"), state.coverUrl)
            }
        }
    }

    private fun revertFollow(attempted: Boolean) {
        _ui.update { it.copy(followBusy = false, followed = !attempted) }
    }

    private suspend fun fetch(refresh: Boolean) {
        _ui.update {
            it.copy(
                loading = it.songs.isEmpty() && it.albums.isEmpty() && !refresh,
                refreshing = refresh,
                error = if (refresh) it.error else null,
            )
        }
        val cookie = cookie()
        try {
            coroutineScope {
                val detailDef = async {
                    runCatching { artists.detail(artistId, cookie) }
                }
                val dynDef = async {
                    runCatching { artists.detailDynamic(artistId, cookie) }
                }
                val songsDef = async {
                    runCatching { artists.topSongs(artistId, cookie) }
                }
                val albumsDef = async {
                    runCatching {
                        artists.albums(artistId, cookie, limit = AlbumPage, offset = 0)
                    }
                }
                val mvsDef = async {
                    runCatching {
                        artists.mvs(artistId, cookie, limit = MvPage, offset = 0)
                    }
                }
                val bioDef = async {
                    runCatching { artists.desc(artistId, cookie) }
                }
                val simiDef = async {
                    runCatching { artists.similar(artistId, cookie) }
                }

                val detailJson = detailDef.await().getOrNull()
                val detail = detailJson
                    ?.takeIf { NcmJson.apiCode(it) == 200 }
                    ?.let { NcmArtistParse.detail(it, artistId, _ui.value.name) }

                val dyn = dynDef.await().getOrNull()?.let { NcmArtistParse.dynamic(it) }

                val songsJson = songsDef.await().getOrNull()
                val songs = songsJson
                    ?.takeIf { NcmJson.apiCode(it) == 200 }
                    ?.let { NcmArtistParse.topSongs(it) }
                    .orEmpty()

                val albumsJson = albumsDef.await().getOrNull()
                val (albums, albumsMore) = albumsJson
                    ?.takeIf { NcmJson.apiCode(it) == 200 }
                    ?.let { NcmArtistParse.albums(it) }
                    ?: (emptyList<ArtistAlbumCard>() to false)

                val mvsJson = mvsDef.await().getOrNull()
                val mvs = mvsJson
                    ?.takeIf { NcmJson.apiCode(it) == 200 }
                    ?.let { NcmMvParse.similar(it) }
                    .orEmpty()
                val mvsMore = mvsJson
                    ?.takeIf { NcmJson.apiCode(it) == 200 }
                    ?.let { NcmMvParse.hasMore(it, mvs.size, MvPage) }
                    ?: false

                val bioJson = bioDef.await().getOrNull()
                val bio = bioJson
                    ?.takeIf { NcmJson.apiCode(it) == 200 }
                    ?.let { NcmArtistParse.bio(it) }

                val similar = simiDef.await().getOrNull()
                    ?.takeIf { NcmJson.apiCode(it) == 200 }
                    ?.let { NcmArtistParse.similar(it) }
                    .orEmpty()

                val failedHard = detail == null && songs.isEmpty() && albums.isEmpty() &&
                    mvs.isEmpty() && similar.isEmpty() && bio == null
                if (failedHard) {
                    val fallback = detailJson?.let {
                        NcmJson.userFacingMessage(it, "暂时无法打开这位歌手")
                    } ?: "暂时无法打开这位歌手"
                    _ui.update {
                        it.copy(loading = false, refreshing = false, error = fallback)
                    }
                    return@coroutineScope
                }

                _ui.update { cur ->
                    cur.copy(
                        name = detail?.name?.takeIf { it.isNotBlank() } ?: cur.name,
                        coverUrl = detail?.coverUrl ?: cur.coverUrl,
                        aliasLine = detail?.aliasLine,
                        identify = detail?.identify,
                        rankLabel = detail?.rankLabel,
                        albumSize = detail?.albumSize ?: cur.albumSize,
                        musicSize = detail?.musicSize ?: cur.musicSize,
                        mvSize = detail?.mvSize ?: cur.mvSize,
                        fansCount = dyn?.fansCount ?: cur.fansCount,
                        followed = dyn?.followed ?: cur.followed,
                        songs = songs,
                        albums = albums,
                        albumsMore = albumsMore,
                        albumsLoading = false,
                        mvs = mvs,
                        mvsMore = mvsMore && mvs.isNotEmpty(),
                        mvsLoading = false,
                        similar = similar,
                        bio = bio?.takeIf { !it.brief.isNullOrBlank() || it.blocks.isNotEmpty() }
                            ?: detail?.briefDesc?.let { ArtistBio(it, emptyList()) },
                        loading = false,
                        refreshing = false,
                        error = null,
                    )
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _ui.update {
                it.copy(
                    loading = false,
                    refreshing = false,
                    error = if (it.songs.isEmpty() && it.albums.isEmpty()) {
                        NcmJson.userFacingThrowable(e, "暂时无法打开这位歌手")
                    } else {
                        it.error
                    },
                )
            }
        }
    }

    private fun cookie(): String = sessionRepository.session.value?.cookie.orEmpty()
}

private fun <T> List<T>.mergeById(extra: List<T>, idOf: (T) -> Long): List<T> {
    if (extra.isEmpty()) return this
    val seen = mapTo(mutableSetOf()) { idOf(it) }
    return this + extra.filter { idOf(it) !in seen }
}
