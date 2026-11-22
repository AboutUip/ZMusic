package com.kite.zmusic.playback

import android.content.Context
import com.kite.zmusic.data.AppMode

/**
 * 音乐和播客各留一份队列。正在听的仍写进默认快照，冷启动只续当前模式。
 * 切走时把现场进度收进离开的那一边，再把另一边上次的队列装回来。
 */
class ModePlayback(
    context: Context,
    private val bridge: PlaybackBridge,
    private val isPodcastTrack: (Long) -> Boolean,
) {
    private val vaultMusic = PlaybackStateStore(context, PREFS_MUSIC)
    private val vaultPodcast = PlaybackStateStore(context, PREFS_PODCAST)

    fun clear() {
        vaultMusic.clear()
        vaultPodcast.clear()
    }

    /**
     * 冷启动。默认快照就是上次退出时的模式。
     * 只有队列明显属于另一个模式时才对调，避免把已经清空的队列又从旧档里捞回来。
     */
    fun reconcileColdStart(mode: AppMode) {
        val snap = bridge.captureSnapshot()
        val engine = engineMode(snap) ?: return
        if (engine == mode) return
        vault(engine).save(snap)
        restore(mode)
    }

    /** 用户切换模式。同一模式再点一次什么都不做。 */
    fun switchTo(mode: AppMode) {
        val snap = bridge.captureSnapshot()
        val engine = engineMode(snap)
        if (engine == mode) return
        if (engine != null) vault(engine).save(snap)
        restore(mode)
    }

    private fun restore(mode: AppMode) {
        val next = vault(mode).load(restorePlayIntent = true)
        if (next == null || next.queue.isEmpty()) {
            bridge.clearQueue()
        } else {
            bridge.adoptSnapshot(next)
        }
    }

    private fun engineMode(state: PlaybackUiState): AppMode? {
        val id = state.currentTrack?.id ?: return null
        if (id <= 0L || state.queue.isEmpty()) return null
        return if (isPodcastTrack(id)) AppMode.Podcast else AppMode.Music
    }

    private fun vault(mode: AppMode): PlaybackStateStore = when (mode) {
        AppMode.Music -> vaultMusic
        AppMode.Podcast -> vaultPodcast
    }

    companion object {
        private const val PREFS_MUSIC = "zmusic_playback_music"
        private const val PREFS_PODCAST = "zmusic_playback_podcast"
    }
}
