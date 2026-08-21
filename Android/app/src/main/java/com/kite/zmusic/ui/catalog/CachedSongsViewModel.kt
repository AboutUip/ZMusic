package com.kite.zmusic.ui.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kite.zmusic.data.TrackExportRepository
import com.kite.zmusic.data.TrackRow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class CachedSongsViewModel(
    private val exporter: TrackExportRepository,
) : ViewModel() {

    private val _list = MutableStateFlow(
        CatalogListState(
            title = "缓存的歌曲",
            creatorName = "本机",
            loading = true,
            complete = true,
        ),
    )
    val list: StateFlow<CatalogListState> = _list.asStateFlow()

    fun load() {
        viewModelScope.launch {
            val had = _list.value.tracks.isNotEmpty()
            _list.update {
                it.copy(
                    loading = !had,
                    refreshing = had,
                    error = null,
                )
            }
            runCatching { exporter.scanCachedTracks() }
                .onSuccess { tracks ->
                    _list.update {
                        it.copy(
                            tracks = tracks,
                            coverUrl = tracks.firstOrNull()?.coverUrl,
                            subtitle = "${tracks.size} 首",
                            expectedCount = tracks.size,
                            loading = false,
                            refreshing = false,
                            error = null,
                            complete = true,
                        )
                    }
                }
                .onFailure {
                    _list.update { cur ->
                        cur.copy(
                            loading = false,
                            refreshing = false,
                            error = if (cur.tracks.isEmpty()) "无法读取下载目录，点这里重试" else cur.error,
                        )
                    }
                }
        }
    }

    fun removeTrack(track: TrackRow) {
        viewModelScope.launch {
            exporter.deleteCachedFolder(track.localFolder)
            load()
        }
    }

    fun removeTracks(tracks: List<TrackRow>, done: (Boolean) -> Unit) {
        viewModelScope.launch {
            tracks.forEach { exporter.deleteCachedFolder(it.localFolder) }
            load()
            done(true)
        }
    }
}

class CachedSongsViewModelFactory(
    private val exporter: TrackExportRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        CachedSongsViewModel(exporter) as T
}
