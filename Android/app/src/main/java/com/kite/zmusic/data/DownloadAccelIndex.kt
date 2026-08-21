package com.kite.zmusic.data

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger

data class DownloadAccelHit(
    val trackId: Long,
    val audioUri: String,
    val lyricUri: String? = null,
    val transLyricUri: String? = null,
    val coverUri: String? = null,
)

/**
 * 仅在下载加速开启时扫描 `Download/ZMusic`，按歌曲 id 做成内存索引，
 * 播放时 O(1) 命中，避免开了加速反而先卡在扫盘。
 */
class DownloadAccelIndex(
    context: Context,
    private val exporter: TrackExportRepository,
    private val store: DownloadAccelStore,
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _hits = MutableStateFlow<Map<Long, DownloadAccelHit>>(emptyMap())
    val hits: StateFlow<Map<Long, DownloadAccelHit>> = _hits.asStateFlow()

    private val gen = AtomicInteger(0)
    private var debounceJob: Job? = null
    private var observer: ContentObserver? = null
    private var started = false

    fun start() {
        if (started) return
        started = true
        scope.launch {
            store.enabled.collect { on ->
                if (on) {
                    registerObserver()
                    refresh()
                } else {
                    unregisterObserver()
                    gen.incrementAndGet()
                    _hits.value = emptyMap()
                }
            }
        }
    }

    fun lookup(id: Long): DownloadAccelHit? = if (id > 0L) _hits.value[id] else null

    fun audioUri(id: Long): String? = lookup(id)?.audioUri

    fun notifyLibraryChanged() {
        if (!store.current()) return
        scheduleRefresh()
    }

    private fun scheduleRefresh() {
        debounceJob?.cancel()
        debounceJob = scope.launch {
            delay(420)
            refresh()
        }
    }

    private suspend fun refresh() {
        if (!store.current()) {
            _hits.value = emptyMap()
            return
        }
        val g = gen.incrementAndGet()
        val tracks = withContext(Dispatchers.IO) {
            runCatching { exporter.scanCachedTracks() }.getOrDefault(emptyList())
        }
        if (g != gen.get() || !store.current()) return
        _hits.value = buildMap {
            for (t in tracks) {
                val audio = t.localAudioUri?.takeIf { it.isNotBlank() } ?: continue
                if (t.id <= 0L) continue
                put(
                    t.id,
                    DownloadAccelHit(
                        trackId = t.id,
                        audioUri = audio,
                        lyricUri = t.localLyricUri,
                        transLyricUri = t.localTransLyricUri,
                        coverUri = t.coverUrl,
                    ),
                )
            }
        }
    }

    private fun registerObserver() {
        if (observer != null) return
        val obs = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                scheduleRefresh()
            }
        }
        observer = obs
        runCatching {
            appContext.contentResolver.registerContentObserver(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                true,
                obs,
            )
        }
    }

    private fun unregisterObserver() {
        val obs = observer ?: return
        observer = null
        runCatching { appContext.contentResolver.unregisterContentObserver(obs) }
    }
}
