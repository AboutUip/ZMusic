package com.kite.zmusic.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kite.zmusic.data.HotSearchWord
import com.kite.zmusic.data.NcmHomeParse
import com.kite.zmusic.data.NcmJson
import com.kite.zmusic.data.NcmLibraryParse
import com.kite.zmusic.data.NcmUserClient
import com.kite.zmusic.data.RecommendMvCard
import com.kite.zmusic.data.SearchArtistHit
import com.kite.zmusic.data.SearchHistoryRepository
import com.kite.zmusic.data.SearchPlaylistHit
import com.kite.zmusic.data.SearchUserHit
import com.kite.zmusic.data.SessionRepository
import com.kite.zmusic.data.TrackRow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject

enum class SearchPhase {
    Idle,
    Suggest,
    Results,
}

enum class SearchKind(val apiType: Int, val label: String, val emptyHint: String, val countKey: String) {
    Song(1, "歌曲", "没有找到相关歌曲", "songCount"),
    Playlist(1000, "歌单", "没有找到相关歌单", "playlistCount"),
    Mv(1004, "MV", "没有找到相关 MV", "mvCount"),
    Artist(100, "歌手", "没有找到相关歌手", "artistCount"),
    User(1002, "用户", "没有找到相关用户", "userprofileCount"),
}

private const val SearchPageSize = 30

private data class SearchKindPage(
    val tracks: List<TrackRow> = emptyList(),
    val playlists: List<SearchPlaylistHit> = emptyList(),
    val mvs: List<RecommendMvCard> = emptyList(),
    val artists: List<SearchArtistHit> = emptyList(),
    val users: List<SearchUserHit> = emptyList(),
    val hasMore: Boolean = false,
    val nextOffset: Int = 0,
    val useLegacySearch: Boolean = false,
) {
    fun isEmpty(kind: SearchKind): Boolean = when (kind) {
        SearchKind.Song -> tracks.isEmpty()
        SearchKind.Playlist -> playlists.isEmpty()
        SearchKind.Mv -> mvs.isEmpty()
        SearchKind.Artist -> artists.isEmpty()
        SearchKind.User -> users.isEmpty()
    }

    fun size(kind: SearchKind): Int = when (kind) {
        SearchKind.Song -> tracks.size
        SearchKind.Playlist -> playlists.size
        SearchKind.Mv -> mvs.size
        SearchKind.Artist -> artists.size
        SearchKind.User -> users.size
    }

    fun append(other: SearchKindPage, kind: SearchKind): SearchKindPage = when (kind) {
        SearchKind.Song -> copy(tracks = tracks.mergeById(other.tracks) { it.id })
        SearchKind.Playlist -> copy(playlists = playlists.mergeById(other.playlists) { it.id })
        SearchKind.Mv -> copy(mvs = mvs.mergeById(other.mvs) { it.id })
        SearchKind.Artist -> copy(artists = artists.mergeById(other.artists) { it.id })
        SearchKind.User -> copy(users = users.mergeById(other.users) { it.id })
    }
}

private fun <T> List<T>.mergeById(extra: List<T>, idOf: (T) -> Long): List<T> {
    if (extra.isEmpty()) return this
    val seen = mapTo(mutableSetOf()) { idOf(it) }
    return this + extra.filter { seen.add(idOf(it)) }
}

data class SearchUiState(
    val query: String = "",
    val phase: SearchPhase = SearchPhase.Idle,
    val kind: SearchKind = SearchKind.Song,
    val suggesting: Boolean = false,
    val searchError: String? = null,
    val results: List<TrackRow> = emptyList(),
    val playlists: List<SearchPlaylistHit> = emptyList(),
    val mvs: List<RecommendMvCard> = emptyList(),
    val artists: List<SearchArtistHit> = emptyList(),
    val users: List<SearchUserHit> = emptyList(),
    val suggestions: List<String> = emptyList(),
    val hotWords: List<HotSearchWord> = emptyList(),
    val history: List<String> = emptyList(),
    val loadingKinds: Set<SearchKind> = emptySet(),
    val loadingMoreKinds: Set<SearchKind> = emptySet(),
    val songHasMore: Boolean = false,
    val playlistHasMore: Boolean = false,
    val mvHasMore: Boolean = false,
    val artistHasMore: Boolean = false,
    val userHasMore: Boolean = false,
) {
    fun empty(kind: SearchKind): Boolean = when (kind) {
        SearchKind.Song -> results.isEmpty()
        SearchKind.Playlist -> playlists.isEmpty()
        SearchKind.Mv -> mvs.isEmpty()
        SearchKind.Artist -> artists.isEmpty()
        SearchKind.User -> users.isEmpty()
    }

    fun hasMore(kind: SearchKind): Boolean = when (kind) {
        SearchKind.Song -> songHasMore
        SearchKind.Playlist -> playlistHasMore
        SearchKind.Mv -> mvHasMore
        SearchKind.Artist -> artistHasMore
        SearchKind.User -> userHasMore
    }

    fun loading(kind: SearchKind): Boolean = kind in loadingKinds

    fun loadingMore(kind: SearchKind): Boolean = kind in loadingMoreKinds
}

class SearchViewModel(
    private val sessionRepository: SessionRepository,
    private val searchHistory: SearchHistoryRepository,
    private val userClient: NcmUserClient = NcmUserClient(),
) : ViewModel() {

    private val _ui = MutableStateFlow(SearchUiState(history = searchHistory.items.value))
    val ui: StateFlow<SearchUiState> = _ui.asStateFlow()

    private var suggestJob: Job? = null
    private var lastSearchedQuery: String = ""
    private val pageCache = mutableMapOf<Pair<String, SearchKind>, SearchKindPage>()
    private val kindJobs = mutableMapOf<SearchKind, Job>()
    private val moreJobs = mutableMapOf<SearchKind, Job>()

    init {
        viewModelScope.launch {
            searchHistory.items.collect { words ->
                _ui.update { it.copy(history = words) }
            }
        }
        viewModelScope.launch {
            val cookie = sessionRepository.session.value?.cookie.orEmpty()
            if (cookie.isBlank()) return@launch
            try {
                val json = userClient.searchHotDetail(cookie)
                _ui.update { it.copy(hotWords = NcmHomeParse.hotSearchWords(json).take(12)) }
            } catch (_: Exception) {
            }
        }
    }

    fun resetToIdle() {
        cancelKindWork()
        suggestJob?.cancel()
        pageCache.clear()
        lastSearchedQuery = ""
        _ui.update {
            it.copy(
                query = "",
                phase = SearchPhase.Idle,
                kind = SearchKind.Song,
                suggesting = false,
                searchError = null,
                results = emptyList(),
                playlists = emptyList(),
                mvs = emptyList(),
                artists = emptyList(),
                users = emptyList(),
                suggestions = emptyList(),
                loadingKinds = emptySet(),
                loadingMoreKinds = emptySet(),
                songHasMore = false,
                playlistHasMore = false,
                mvHasMore = false,
                artistHasMore = false,
                userHasMore = false,
            )
        }
    }

    fun onQueryChange(value: String) {
        cancelKindWork()
        suggestJob?.cancel()
        val q = value.trim()
        if (q.isEmpty()) {
            pageCache.clear()
            lastSearchedQuery = ""
            _ui.update {
                it.copy(
                    query = value,
                    phase = SearchPhase.Idle,
                    kind = SearchKind.Song,
                    suggesting = false,
                    searchError = null,
                    results = emptyList(),
                    playlists = emptyList(),
                    mvs = emptyList(),
                    artists = emptyList(),
                    users = emptyList(),
                    suggestions = emptyList(),
                    loadingKinds = emptySet(),
                    loadingMoreKinds = emptySet(),
                    songHasMore = false,
                    playlistHasMore = false,
                    mvHasMore = false,
                    artistHasMore = false,
                    userHasMore = false,
                )
            }
            return
        }
        _ui.update {
            it.copy(
                query = value,
                phase = SearchPhase.Suggest,
                searchError = null,
                loadingKinds = emptySet(),
                loadingMoreKinds = emptySet(),
            )
        }
        suggestJob = viewModelScope.launch {
            delay(280)
            loadSuggest(q)
        }
    }

    fun submitSearch() {
        val q = _ui.value.query.trim()
        if (q.isEmpty()) return
        runFullSearch(q, kindForQuery(q))
    }

    fun setKind(kind: SearchKind) {
        if (_ui.value.phase != SearchPhase.Results) return
        val q = _ui.value.query.trim()
        if (q.isEmpty()) return
        val same = _ui.value.kind == kind
        val cached = pageCache[q to kind]
        if (cached != null) {
            _ui.update {
                it.copy(kind = kind, searchError = null).withPage(kind, cached)
            }
            return
        }
        if (same && _ui.value.loading(kind)) return
        runFullSearch(q, kind, recordHistory = false)
    }

    fun loadMore(kind: SearchKind) {
        if (_ui.value.phase != SearchPhase.Results) return
        val q = _ui.value.query.trim()
        if (q.isEmpty()) return
        val page = pageCache[q to kind] ?: return
        if (!page.hasMore) return
        if (_ui.value.loading(kind) || _ui.value.loadingMore(kind)) return
        if (moreJobs[kind]?.isActive == true) return
        moreJobs[kind] = viewModelScope.launch { searchMore(q, kind, page) }
    }

    fun applySuggestion(word: String) {
        val q = word.trim()
        if (q.isEmpty()) return
        _ui.update { it.copy(query = q) }
        runFullSearch(q, kindForQuery(q))
    }

    fun applyHotWord(word: String) = applySuggestion(word)

    fun applyHistory(word: String) = applySuggestion(word)

    fun removeHistory(word: String) = searchHistory.remove(word)

    fun clearHistory() = searchHistory.clear()

    private fun kindForQuery(keywords: String): SearchKind {
        return if (keywords == lastSearchedQuery) _ui.value.kind else SearchKind.Song
    }

    private fun runFullSearch(
        keywords: String,
        kind: SearchKind,
        recordHistory: Boolean = true,
    ) {
        suggestJob?.cancel()
        val queryChanged = keywords != lastSearchedQuery
        lastSearchedQuery = keywords
        if (recordHistory) searchHistory.record(keywords)
        if (queryChanged) {
            cancelKindWork()
            _ui.update { it.clearedAllKinds() }
        }
        val cached = pageCache[keywords to kind]
        if (cached != null) {
            _ui.update {
                it.copy(
                    phase = SearchPhase.Results,
                    kind = kind,
                    suggesting = false,
                    searchError = null,
                    suggestions = emptyList(),
                ).withPage(kind, cached)
            }
            return
        }
        kindJobs[kind]?.cancel()
        kindJobs[kind] = viewModelScope.launch { searchNow(keywords, kind) }
    }

    private fun cancelKindWork() {
        kindJobs.values.forEach { it.cancel() }
        kindJobs.clear()
        moreJobs.values.forEach { it.cancel() }
        moreJobs.clear()
    }

    private suspend fun loadSuggest(keywords: String) {
        val cookie = sessionRepository.session.value?.cookie.orEmpty()
        _ui.update { it.copy(suggesting = true) }
        try {
            val json = userClient.searchSuggest(keywords, cookie, mobile = true)
            var words = NcmHomeParse.searchSuggestKeywords(json)
            if (words.isEmpty()) {
                val web = userClient.searchSuggest(keywords, cookie, mobile = false)
                words = NcmHomeParse.searchSuggestKeywords(web)
            }
            if (_ui.value.phase != SearchPhase.Suggest) return
            if (_ui.value.query.trim() != keywords) return
            _ui.update {
                it.copy(
                    suggesting = false,
                    suggestions = words.filter { w -> !w.equals(keywords, ignoreCase = true) },
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            if (_ui.value.phase != SearchPhase.Suggest) return
            _ui.update { it.copy(suggesting = false, suggestions = emptyList()) }
        }
    }

    private suspend fun searchNow(keywords: String, kind: SearchKind) {
        val cookie = sessionRepository.session.value?.cookie.orEmpty()
        _ui.update {
            it.copy(
                phase = SearchPhase.Results,
                kind = kind,
                suggesting = false,
                searchError = null,
                suggestions = emptyList(),
                loadingKinds = it.loadingKinds + kind,
            ).cleared(kind)
        }
        try {
            val fetched = fetchSearch(keywords, cookie, kind, offset = 0, useLegacy = null)
            val parsed = parseKind(fetched.json, kind)
            val empty = parsed.isEmpty(kind)
            if (empty && NcmJson.apiCode(fetched.json) != 200 && NcmJson.apiCode(fetched.json) != -1) {
                _ui.update {
                    it.copy(
                        loadingKinds = it.loadingKinds - kind,
                        searchError = if (it.kind == kind) {
                            NcmJson.userFacingMessage(fetched.json, kind.emptyHint)
                        } else {
                            it.searchError
                        },
                    )
                }
                return
            }
            val page = parsed.copy(
                hasMore = !empty && NcmHomeParse.searchHasMore(
                    fetched.json,
                    parsed.size(kind),
                    SearchPageSize,
                    kind.countKey,
                ),
                nextOffset = SearchPageSize,
                useLegacySearch = fetched.legacy,
            )
            pageCache[keywords to kind] = page
            if (_ui.value.query.trim() != keywords) return
            _ui.update {
                it.copy(
                    loadingKinds = it.loadingKinds - kind,
                    searchError = when {
                        it.kind != kind -> it.searchError
                        empty -> kind.emptyHint
                        else -> null
                    },
                ).withPage(kind, page)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _ui.update {
                it.copy(
                    loadingKinds = it.loadingKinds - kind,
                    searchError = if (it.kind == kind) {
                        NcmJson.userFacingThrowable(e, "搜索失败")
                    } else {
                        it.searchError
                    },
                )
            }
        }
    }

    private suspend fun searchMore(keywords: String, kind: SearchKind, current: SearchKindPage) {
        val cookie = sessionRepository.session.value?.cookie.orEmpty()
        _ui.update { it.copy(loadingMoreKinds = it.loadingMoreKinds + kind) }
        try {
            val fetched = fetchSearch(
                keywords,
                cookie,
                kind,
                offset = current.nextOffset,
                useLegacy = current.useLegacySearch,
            )
            val incoming = parseKind(fetched.json, kind)
            if (incoming.isEmpty(kind)) {
                val done = current.copy(hasMore = false)
                pageCache[keywords to kind] = done
                _ui.update {
                    it.copy(loadingMoreKinds = it.loadingMoreKinds - kind).withPage(kind, done)
                }
                return
            }
            val merged = current.append(incoming, kind).let { page ->
                if (kind != SearchKind.Song) page
                else page.copy(tracks = NcmLibraryParse.mergeTrackRows(current.tracks, page.tracks))
            }.copy(
                hasMore = NcmHomeParse.searchHasMore(
                    fetched.json,
                    current.size(kind) + incoming.size(kind),
                    SearchPageSize,
                    kind.countKey,
                ),
                nextOffset = current.nextOffset + SearchPageSize,
                useLegacySearch = fetched.legacy,
            )
            pageCache[keywords to kind] = merged
            if (_ui.value.query.trim() != keywords) return
            _ui.update {
                it.copy(loadingMoreKinds = it.loadingMoreKinds - kind).withPage(kind, merged)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            _ui.update { it.copy(loadingMoreKinds = it.loadingMoreKinds - kind) }
        }
    }

    private data class SearchFetch(val json: JSONObject, val legacy: Boolean)

    private suspend fun fetchSearch(
        keywords: String,
        cookie: String,
        kind: SearchKind,
        offset: Int,
        useLegacy: Boolean?,
    ): SearchFetch {
        if (useLegacy == true) {
            return SearchFetch(
                userClient.search(
                    keywords,
                    cookie,
                    type = kind.apiType,
                    limit = SearchPageSize,
                    offset = offset,
                ),
                true,
            )
        }
        if (useLegacy == false) {
            return SearchFetch(
                userClient.cloudSearch(
                    keywords,
                    cookie,
                    type = kind.apiType,
                    limit = SearchPageSize,
                    offset = offset,
                ),
                false,
            )
        }
        val cloud = runCatching {
            userClient.cloudSearch(
                keywords,
                cookie,
                type = kind.apiType,
                limit = SearchPageSize,
                offset = 0,
            )
        }.getOrNull()
        if (cloud != null && !parseKind(cloud, kind).isEmpty(kind)) {
            return SearchFetch(cloud, false)
        }
        return SearchFetch(
            userClient.search(
                keywords,
                cookie,
                type = kind.apiType,
                limit = SearchPageSize,
                offset = 0,
            ),
            true,
        )
    }

    private fun parseKind(json: JSONObject, kind: SearchKind): SearchKindPage = when (kind) {
        SearchKind.Song -> SearchKindPage(tracks = NcmHomeParse.searchTracks(json))
        SearchKind.Playlist -> SearchKindPage(playlists = NcmHomeParse.searchPlaylists(json))
        SearchKind.Mv -> SearchKindPage(mvs = NcmHomeParse.searchMvs(json))
        SearchKind.Artist -> SearchKindPage(artists = NcmHomeParse.searchArtists(json))
        SearchKind.User -> SearchKindPage(users = NcmHomeParse.searchUsers(json))
    }

    private fun SearchUiState.withPage(kind: SearchKind, page: SearchKindPage) = when (kind) {
        SearchKind.Song -> copy(results = page.tracks, songHasMore = page.hasMore)
        SearchKind.Playlist -> copy(playlists = page.playlists, playlistHasMore = page.hasMore)
        SearchKind.Mv -> copy(mvs = page.mvs, mvHasMore = page.hasMore)
        SearchKind.Artist -> copy(artists = page.artists, artistHasMore = page.hasMore)
        SearchKind.User -> copy(users = page.users, userHasMore = page.hasMore)
    }

    private fun SearchUiState.cleared(kind: SearchKind) = when (kind) {
        SearchKind.Song -> copy(results = emptyList(), songHasMore = false)
        SearchKind.Playlist -> copy(playlists = emptyList(), playlistHasMore = false)
        SearchKind.Mv -> copy(mvs = emptyList(), mvHasMore = false)
        SearchKind.Artist -> copy(artists = emptyList(), artistHasMore = false)
        SearchKind.User -> copy(users = emptyList(), userHasMore = false)
    }

    private fun SearchUiState.clearedAllKinds() = copy(
        results = emptyList(),
        playlists = emptyList(),
        mvs = emptyList(),
        artists = emptyList(),
        users = emptyList(),
        songHasMore = false,
        playlistHasMore = false,
        mvHasMore = false,
        artistHasMore = false,
        userHasMore = false,
        loadingKinds = emptySet(),
        loadingMoreKinds = emptySet(),
        searchError = null,
    )
}
