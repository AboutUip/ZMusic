package com.kite.zmusic.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kite.zmusic.data.NcmAuthClient
import com.kite.zmusic.data.NcmJson
import com.kite.zmusic.data.NcmLibraryParse
import com.kite.zmusic.data.NcmUserClient
import com.kite.zmusic.data.PlaylistSummary
import com.kite.zmusic.data.SessionRepository
import com.kite.zmusic.data.SubcountBrief
import com.kite.zmusic.data.TrackRow
import com.kite.zmusic.data.UserProfileBrief
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive

data class LibraryUiState(
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val error: String? = null,
    val isGuest: Boolean = false,
    val profile: UserProfileBrief? = null,
    val subcount: SubcountBrief? = null,
    val playlists: List<PlaylistSummary> = emptyList(),
    val likedTrackCount: Int = 0,
    val sheet: LibrarySheet = LibrarySheet.Hidden,
)

sealed class LibrarySheet {
    data object Hidden : LibrarySheet()
    data class Loading(val id: Long, val title: String) : LibrarySheet()
    data class Ready(val id: Long, val title: String, val tracks: List<TrackRow>) : LibrarySheet()
    data class Failed(val id: Long, val title: String, val message: String) : LibrarySheet()
}

class LibraryViewModel(
    private val sessionRepository: SessionRepository,
    private val authClient: NcmAuthClient = NcmAuthClient(),
    private val userClient: NcmUserClient = NcmUserClient(),
) : ViewModel() {

    private val _ui = MutableStateFlow(LibraryUiState())
    val ui: StateFlow<LibraryUiState> = _ui.asStateFlow()

    private var sheetLoadJob: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val session = sessionRepository.session.value
            if (session == null) {
                _ui.update {
                    it.copy(
                        loading = false,
                        error = "未登录",
                        profile = null,
                        playlists = emptyList(),
                    )
                }
                return@launch
            }
            _ui.update { it.copy(loading = it.playlists.isEmpty(), refreshing = it.playlists.isNotEmpty(), error = null) }
            try {
                val status = authClient.loginStatus(session.cookie)
                val uid = NcmJson.userIdFromLoginStatus(status)
                if (uid == null) {
                    _ui.update {
                        it.copy(
                            loading = false,
                            refreshing = false,
                            error = "无法获取用户信息：登录状态里缺少用户 ID，请重新登录或检查 API 返回格式",
                            isGuest = session.isGuest,
                        )
                    }
                    return@launch
                }
                val detailJson = userClient.userDetail(uid, session.cookie)
                var profile = NcmLibraryParse.userProfileFromDetail(detailJson)
                if (profile == null) {
                    profile = UserProfileBrief(
                        userId = uid,
                        nickname = session.displayLabel?.trim().orEmpty().ifBlank { "用户" },
                        avatarUrl = null,
                        signature = null,
                        level = null,
                        listenSongs = null,
                    )
                }
                val subJson = try {
                    userClient.userSubcount(session.cookie)
                } catch (_: Exception) {
                    null
                }
                val sub = subJson?.let { NcmLibraryParse.subcountFromJson(it) }
                val plJson = userClient.userPlaylist(uid, session.cookie, limit = 80, offset = 0)
                val playlists = NcmLibraryParse.playlistsFromUserPlaylist(plJson, uid)
                val likeJson = try {
                    userClient.likeList(uid, session.cookie)
                } catch (_: Exception) {
                    null
                }
                val likeN = likeJson?.let { NcmLibraryParse.likeIdsCount(it) } ?: 0

                // 只更新首页字段，勿整表重建，避免冲掉正在加载的 sheet
                _ui.update {
                    it.copy(
                        loading = false,
                        refreshing = false,
                        error = null,
                        isGuest = session.isGuest,
                        profile = profile,
                        subcount = sub,
                        playlists = playlists,
                        likedTrackCount = likeN,
                    )
                }
            } catch (e: Exception) {
                _ui.update {
                    it.copy(
                        loading = false,
                        refreshing = false,
                        error = e.message ?: "加载失败",
                    )
                }
            }
        }
    }

    fun openPlaylist(p: PlaylistSummary) {
        sheetLoadJob?.cancel()
        sheetLoadJob = viewModelScope.launch {
            val cookie = sessionRepository.session.value?.cookie ?: return@launch
            _ui.update { it.copy(sheet = LibrarySheet.Loading(p.id, p.name)) }
            try {
                val tracks = loadTracks(p.id, cookie)
                if (!isActive) return@launch
                _ui.update { it.copy(sheet = LibrarySheet.Ready(p.id, p.name, tracks)) }
            } catch (e: CancellationException) {
                // 用户已退出/关闭详情：忽略取消后的回写
                return@launch
            } catch (e: Exception) {
                if (!isActive) return@launch
                _ui.update {
                    it.copy(
                        sheet = LibrarySheet.Failed(
                            p.id,
                            p.name,
                            e.message ?: "加载曲目失败",
                        ),
                    )
                }
            }
        }
    }

    fun dismissSheet() {
        sheetLoadJob?.cancel()
        sheetLoadJob = null
        _ui.update { it.copy(sheet = LibrarySheet.Hidden) }
    }

    /** 从播放器「回到歌单」等场景：仅依赖 id + 标题即可拉取曲目 */
    fun openPlaylistFromId(playlistId: Long, title: String) {
        sheetLoadJob?.cancel()
        sheetLoadJob = viewModelScope.launch {
            val cookie = sessionRepository.session.value?.cookie ?: return@launch
            _ui.update { it.copy(sheet = LibrarySheet.Loading(playlistId, title)) }
            try {
                val tracks = loadTracks(playlistId, cookie)
                if (!isActive) return@launch
                _ui.update { it.copy(sheet = LibrarySheet.Ready(playlistId, title, tracks)) }
            } catch (e: CancellationException) {
                return@launch
            } catch (e: Exception) {
                if (!isActive) return@launch
                _ui.update {
                    it.copy(
                        sheet = LibrarySheet.Failed(
                            playlistId,
                            title,
                            e.message ?: "加载曲目失败",
                        ),
                    )
                }
            }
        }
    }

    private suspend fun loadTracks(playlistId: Long, cookie: String): List<TrackRow> {
        val detail = userClient.playlistDetail(playlistId, cookie)
        val fromPl = NcmLibraryParse.tracksFromPlaylistDetail(detail)
        if (fromPl.isNotEmpty()) return fromPl
        val ids = NcmLibraryParse.trackIdsFromPlaylistDetail(detail)
        if (ids.isEmpty()) return emptyList()
        return ids.chunked(400).flatMap { chunk ->
            NcmLibraryParse.tracksFromSongDetail(userClient.songDetail(chunk, cookie))
        }
    }
}
