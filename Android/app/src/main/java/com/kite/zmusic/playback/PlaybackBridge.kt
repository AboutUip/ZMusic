package com.kite.zmusic.playback

import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.kite.zmusic.data.LyricRepository
import com.kite.zmusic.data.SessionRepository
import com.kite.zmusic.data.TrackRow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Application 级门面：
 * - MediaController 拉起 [PlaybackService]
 * - 进程内 [PlaylistCoordinator] 处理 playQueue 等业务
 * - UI StateFlow：**禁止**用空队列覆盖已有快照（除非明确 clear）
 */
@OptIn(UnstableApi::class)
class PlaybackBridge(
    context: Context,
    private val sessionRepository: SessionRepository,
) {
    private val appContext = context.applicationContext
    private val stateStore = PlaybackStateStore(appContext)
    val lyricRepository = LyricRepository(appContext)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @Volatile
    private var coordinator: PlaylistCoordinator? = null

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null
    private var uiCollectJob: Job? = null
    private var lyricJob: Job? = null

    private val _ui = MutableStateFlow(stateStore.load() ?: PlaybackUiState())
    val ui: StateFlow<PlaybackUiState> = _ui.asStateFlow()

    private val pending = CopyOnWriteArrayList<() -> Unit>()
    private val connecting = AtomicBoolean(false)

    init {
        // 冷启动：有队列快照时立刻补歌词，不必等点播放才拉起 Service
        ensureLyricsForCurrentTrack()
    }

    fun stateStore(): PlaybackStateStore = stateStore
    fun sessionRepository(): SessionRepository = sessionRepository
    fun lyricRepository(): LyricRepository = lyricRepository

    /** Service onCreate：注册 Coordinator 并刷 pending。 */
    @Synchronized
    fun attachCoordinator(coord: PlaylistCoordinator) {
        coordinator = coord
        uiCollectJob?.cancel()
        uiCollectJob = scope.launch {
            coord.ui.collectLatest { publishFromCoordinator(it, isExplicitClear = false) }
        }
        // 若 Coordinator 刚从快照恢复了队列，合并到 UI
        publishFromCoordinator(coord.ui.value, isExplicitClear = false)
        flushPending()
        ensureLyricsForCurrentTrack()
        Log.i(TAG, "coordinator attached")
    }

    @Synchronized
    fun detachCoordinator(coord: PlaylistCoordinator) {
        if (coordinator !== coord) return
        uiCollectJob?.cancel()
        uiCollectJob = null
        coordinator = null
        // 保留队列快照到 UI（暂停态）
        val kept = _ui.value.copy(
            isPlaying = false,
            buffering = false,
            loadPending = false,
        )
        if (kept.queue.isNotEmpty()) {
            _ui.value = kept.copy(hasQueue = true)
            stateStore.save(_ui.value)
        }
        Log.i(TAG, "coordinator detached, queue kept=${kept.queue.size}")
    }

    fun hydrateForUi() {
        if (_ui.value.queue.isEmpty()) {
            stateStore.load()?.let { _ui.value = it }
        }
        ensureLyricsForCurrentTrack()
    }

    /** 当前曲目无歌词时异步补齐（磁盘优先，不依赖是否正在播放）。 */
    private fun ensureLyricsForCurrentTrack() {
        val trackId = _ui.value.currentTrack?.id ?: return
        if (_ui.value.lyricLines.isNotEmpty()) return
        lyricRepository.peekMemory(trackId)?.takeIf { it.isNotEmpty() }?.let { cached ->
            _ui.value = _ui.value.copy(lyricLines = cached)
            return
        }
        lyricJob?.cancel()
        lyricJob = scope.launch {
            val cookie = sessionRepository.session.value?.cookie.orEmpty()
            val lines = lyricRepository.loadBestEffort(trackId, cookie)
            if (lines.isEmpty()) return@launch
            if (_ui.value.currentTrack?.id == trackId) {
                _ui.value = _ui.value.copy(lyricLines = lines)
            }
        }
    }

    fun playQueue(
        tracks: List<TrackRow>,
        startIndex: Int,
        sourcePlaylistId: Long? = null,
        sourcePlaylistTitle: String? = null,
    ) {
        runOnCoordinator {
            it.playQueue(tracks, startIndex, sourcePlaylistId, sourcePlaylistTitle)
        }
    }

    fun clearQueue() {
        stateStore.clear()
        coordinator?.clearQueue()
        publishFromCoordinator(
            PlaybackUiState(playbackMode = _ui.value.playbackMode, hasQueue = false),
            isExplicitClear = true,
        )
    }

    fun togglePlayPause() = runOnCoordinator { it.togglePlayPause() }

    fun seekTo(ms: Long) = runOnCoordinator { it.seekTo(ms) }

    fun skipNext() = runOnCoordinator { it.skipNext() }

    fun skipPrevious() = runOnCoordinator { it.skipPrevious() }

    fun cyclePlaybackMode() = runOnCoordinator { it.cyclePlaybackMode() }

    fun stopForLogout() {
        stateStore.clear()
        val c = coordinator
        if (c != null) {
            c.clearQueue()
        }
        publishFromCoordinator(PlaybackUiState(), isExplicitClear = true)
        releaseController()
    }

    /**
     * @param isExplicitClear 用户清空 / 登出时允许发布空队列
     */
    private fun publishFromCoordinator(incoming: PlaybackUiState, isExplicitClear: Boolean) {
        if (!isExplicitClear &&
            incoming.queue.isEmpty() &&
            _ui.value.queue.isNotEmpty()
        ) {
            // 忽略 Coordinator 空初始态，防止冲掉迷你条
            return
        }
        // Coordinator 快照不含歌词时，保留 Bridge 已加载的同曲歌词
        val merged = if (
            !isExplicitClear &&
            incoming.lyricLines.isEmpty() &&
            _ui.value.lyricLines.isNotEmpty() &&
            incoming.currentTrack?.id != null &&
            incoming.currentTrack?.id == _ui.value.currentTrack?.id
        ) {
            incoming.copy(lyricLines = _ui.value.lyricLines)
        } else {
            incoming
        }
        _ui.value = merged
        if (merged.queue.isNotEmpty()) {
            stateStore.save(merged)
            if (merged.lyricLines.isEmpty()) {
                ensureLyricsForCurrentTrack()
            }
        } else if (isExplicitClear) {
            stateStore.clear()
        }
    }

    private fun runOnCoordinator(block: (PlaylistCoordinator) -> Unit) {
        val c = coordinator
        if (c != null) {
            mainHandler.post { block(c) }
        } else {
            pending.add { coordinator?.let(block) }
            ensureController()
        }
    }

    /** 通过 MediaController 连接以启动 MediaSessionService。 */
    private fun ensureController() {
        if (coordinator != null) {
            flushPending()
            return
        }
        if (!connecting.compareAndSet(false, true) && controllerFuture != null) return

        val token = SessionToken(
            appContext,
            ComponentName(appContext, PlaybackService::class.java),
        )
        val future = MediaController.Builder(appContext, token).buildAsync()
        controllerFuture = future
        future.addListener(
            {
                try {
                    val controller = future.get()
                    mediaController = controller
                    // 监听仅作保活；业务状态以 Coordinator→Bridge 为准
                    controller.addListener(object : Player.Listener {})
                    Log.i(TAG, "MediaController connected")
                } catch (e: Exception) {
                    Log.e(TAG, "MediaController connect failed", e)
                    controllerFuture = null
                } finally {
                    connecting.set(false)
                    // Service onCreate 应已 attach；再刷一次 pending
                    mainHandler.post { flushPending() }
                }
            },
            MoreExecutors.directExecutor(),
        )
    }

    private fun flushPending() {
        if (coordinator == null) return
        val copy = ArrayList(pending)
        pending.clear()
        copy.forEach { action ->
            mainHandler.post { action() }
        }
    }

    private fun releaseController() {
        mediaController?.release()
        mediaController = null
        controllerFuture?.cancel(true)
        controllerFuture = null
        connecting.set(false)
    }

    fun shutdown() {
        releaseController()
        scope.cancel()
    }

    companion object {
        private const val TAG = "PlaybackBridge"
    }
}
