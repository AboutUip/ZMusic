package com.kite.zmusic.playback

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kite.zmusic.data.TrackRow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class PlaybackViewModelFactory(
    private val bridge: PlaybackBridge,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PlaybackViewModel::class.java)) {
            return PlaybackViewModel(bridge) as T
        }
        error("Unknown ViewModel class")
    }
}

/** UI 薄封装：状态与命令经 [PlaybackBridge] 到达 Service 内 Coordinator。 */
class PlaybackViewModel(
    private val bridge: PlaybackBridge,
) : ViewModel() {

    val ui: StateFlow<PlaybackUiState> = bridge.ui
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = bridge.ui.value,
        )

    val spectrum: StateFlow<AudioSpectrumBands> = bridge.spectrum
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = AudioSpectrumBands.ZERO,
        )

    fun playQueue(
        tracks: List<TrackRow>,
        startIndex: Int,
        sourcePlaylistId: Long? = null,
        sourcePlaylistTitle: String? = null,
    ) {
        bridge.playQueue(tracks, startIndex, sourcePlaylistId, sourcePlaylistTitle)
    }

    fun clearQueue() = bridge.clearQueue()

    fun togglePlayPause() = bridge.togglePlayPause()

    fun seekTo(ms: Long) = bridge.seekTo(ms)

    fun skipNext() = bridge.skipNext()

    fun skipPrevious() = bridge.skipPrevious()

    fun cyclePlaybackMode() = bridge.cyclePlaybackMode()
}
