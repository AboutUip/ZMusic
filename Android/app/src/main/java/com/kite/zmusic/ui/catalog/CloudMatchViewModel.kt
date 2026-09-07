package com.kite.zmusic.ui.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kite.zmusic.data.CloudDiskRepository
import com.kite.zmusic.data.NcmHomeParse
import com.kite.zmusic.data.SearchRepository
import com.kite.zmusic.data.SessionRepository
import com.kite.zmusic.data.TrackRow
import com.kite.zmusic.ui.notice.IslandNoticeCenter
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CloudMatchUi(
    val query: String = "",
    val hits: List<TrackRow> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
)

class CloudMatchViewModel(
    private val songId: Long,
    initialTitle: String,
    initialArtists: String,
    private val sessionRepository: SessionRepository,
    private val search: SearchRepository,
    private val cloud: CloudDiskRepository,
    private val notices: IslandNoticeCenter,
) : ViewModel() {

    private val _ui = MutableStateFlow(
        CloudMatchUi(query = listOf(initialTitle, initialArtists).filter { it.isNotBlank() && it != "—" }.joinToString(" ")),
    )
    val ui: StateFlow<CloudMatchUi> = _ui.asStateFlow()

    private var job: Job? = null

    init {
        if (_ui.value.query.isNotBlank()) search()
    }

    fun onQuery(value: String) {
        _ui.update { it.copy(query = value) }
        job?.cancel()
        val q = value.trim()
        if (q.isEmpty()) {
            _ui.update { it.copy(hits = emptyList(), error = null, loading = false) }
            return
        }
        job = viewModelScope.launch {
            delay(280)
            search()
        }
    }

    fun clear() {
        job?.cancel()
        _ui.value = CloudMatchUi()
    }

    fun search() {
        val q = _ui.value.query.trim()
        if (q.isEmpty()) return
        val cookie = sessionRepository.session.value?.cookie.orEmpty()
        if (cookie.isBlank() || sessionRepository.session.value?.isGuest == true) {
            _ui.update { it.copy(loading = false, error = "请先登录", hits = emptyList()) }
            return
        }
        job?.cancel()
        job = viewModelScope.launch {
            _ui.update { it.copy(loading = true, error = null) }
            val json = runCatching {
                search.cloudSearch(q, cookie, type = 1, limit = 30, offset = 0)
            }.getOrElse {
                _ui.update { it.copy(loading = false, error = "搜索失败") }
                return@launch
            }
            val hits = NcmHomeParse.searchTracks(json).filter { it.id != songId }
            _ui.update {
                it.copy(
                    loading = false,
                    hits = hits,
                    error = null,
                )
            }
        }
    }

    fun match(track: TrackRow, done: (Boolean) -> Unit) {
        viewModelScope.launch {
            val msg = cloud.match(songId, track.id)
            notices.show(msg, track.coverUrl)
            val ok = msg.startsWith("已")
            done(ok)
        }
    }
}

class CloudMatchViewModelFactory(
    private val songId: Long,
    private val title: String,
    private val artists: String,
    private val sessionRepository: SessionRepository,
    private val search: SearchRepository,
    private val cloud: CloudDiskRepository,
    private val notices: IslandNoticeCenter,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        CloudMatchViewModel(
            songId, title, artists, sessionRepository, search, cloud, notices,
        ) as T
}
