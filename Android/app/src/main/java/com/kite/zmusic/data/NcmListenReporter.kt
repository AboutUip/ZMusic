package com.kite.zmusic.data

import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * 把 ZMusic 的实际收听报到网易云：听歌排行（`/scrobble`）、收听时长（`/scrobble/v1`）、
 * 播放状态（`/relay/play/state/submit`）。游客 / 失败静默，不挡播放。
 */
class NcmListenReporter(
    private val userClient: NcmUserClient,
    private val sessionRepository: SessionRepository,
    private val scope: CoroutineScope,
) {
    private var trackId = 0L
    private var trackName = ""
    private var trackArtist = ""
    private var sourceId = 0L
    private var sourceTitle = ""
    private var playMode = "list_loop"
    private var quality: AudioQuality = AudioQuality.Default
    private var sessionId = ""
    private var listenedMs = 0L
    private var durationMs = 0L
    private var lastPos = -1L
    private var accumAt = 0L
    private var lastStateAt = 0L
    private var submitted = false

    fun tick(
        playing: Boolean,
        track: TrackRow?,
        durationMs: Long,
        positionMs: Long,
        quality: AudioQuality,
        sourcePlaylistId: Long?,
        sourcePlaylistTitle: String?,
        playMode: String,
    ) {
        val id = track?.id ?: 0L
        if (id <= 0L) {
            flush()
            return
        }
        val wrapped = NcmListenLogic.wrapped(
            sameTrack = trackId == id,
            lastPos = lastPos,
            durationMs = durationMs,
            positionMs = positionMs,
            listenedMs = listenedMs,
        )
        if (wrapped || trackId != id) {
            if (wrapped || trackId > 0L) flush()
            begin(
                track = track,
                durationMs = durationMs,
                positionMs = positionMs,
                quality = quality,
                sourcePlaylistId = sourcePlaylistId,
                sourcePlaylistTitle = sourcePlaylistTitle,
                playMode = playMode,
            )
        }
        if (durationMs > this.durationMs) this.durationMs = durationMs
        this.quality = quality
        this.playMode = playMode.ifBlank { "list_loop" }
        val src = sourcePlaylistId?.takeIf { it > 0L }
        if (src != null) sourceId = src
        if (!sourcePlaylistTitle.isNullOrBlank()) sourceTitle = sourcePlaylistTitle.trim()
        lastPos = positionMs
        val now = SystemClock.elapsedRealtime()
        val session = sessionRepository.session.value
        val canReport = session != null && !session.isGuest && session.cookie.isNotBlank()
        if (playing) {
            listenedMs = NcmListenLogic.accumulate(listenedMs, accumAt, now)
            accumAt = now
            if (canReport) {
                maybeSubmitState(session.cookie, positionMs, force = false)
                trySubmit(session)
            }
        } else {
            if (accumAt > 0L && canReport) {
                maybeSubmitState(session.cookie, positionMs, force = true)
            }
            accumAt = 0L
        }
    }

    fun onCompleted() {
        if (trackId <= 0L) return
        flush()
    }

    fun flushIfTrackChanged(nextId: Long) {
        if (trackId > 0L && trackId != nextId) flush()
    }

    fun flush() {
        val session = sessionRepository.session.value
        if (session != null && !session.isGuest && session.cookie.isNotBlank()) {
            trySubmit(session)
        }
        reset()
    }

    private fun trySubmit(session: SessionRepository.StoredSession) {
        if (!NcmListenLogic.shouldSubmit(submitted, listenedMs, durationMs)) return
        val id = trackId
        if (id <= 0L) return
        submitted = true
        val timeSec = (listenedMs / 1000L).toInt().coerceAtLeast(1)
        val totalSec = (durationMs / 1000L).toInt().coerceAtLeast(timeSec)
        val progressSec = (lastPos.coerceAtLeast(0L) / 1000L).toInt()
        val cookie = session.cookie
        val name = trackName
        val artist = trackArtist
        val source = sourceId.takeIf { it > 0L } ?: id
        val sourceName = sourceTitle
        val mode = playMode
        val q = quality
        val sid = sessionId
        scope.launch(Dispatchers.IO) {
            runCatching {
                userClient.relayPlayStateSubmit(cookie, id, sid, progressSec, mode)
            }.onFailure { Log.w(TAG, "relay submit failed id=$id", it) }
            runCatching {
                userClient.scrobble(cookie, id, source, timeSec)
            }.onFailure { Log.w(TAG, "scrobble failed id=$id", it) }
            runCatching {
                userClient.scrobbleV1(
                    cookie = cookie,
                    songId = id,
                    timeSec = timeSec,
                    totalSec = totalSec,
                    sourceId = source,
                    name = name,
                    artist = artist,
                    bitrate = (q.legacyBr / 1000).coerceAtLeast(128),
                    level = q.level,
                    source = sourceName.ifBlank { "list" },
                )
            }.onFailure { Log.w(TAG, "scrobble/v1 failed id=$id", it) }
        }
    }

    private fun begin(
        track: TrackRow?,
        durationMs: Long,
        positionMs: Long,
        quality: AudioQuality,
        sourcePlaylistId: Long?,
        sourcePlaylistTitle: String?,
        playMode: String,
    ) {
        val row = track ?: return
        trackId = row.id
        trackName = row.name
        trackArtist = row.artists
        sourceId = sourcePlaylistId?.takeIf { it > 0L } ?: row.id
        sourceTitle = sourcePlaylistTitle?.trim().orEmpty()
        this.playMode = playMode.ifBlank { "list_loop" }
        this.quality = quality
        sessionId = newSessionId()
        listenedMs = 0L
        this.durationMs = durationMs.coerceAtLeast(row.durationMs).coerceAtLeast(0L)
        lastPos = positionMs
        accumAt = 0L
        lastStateAt = 0L
        submitted = false
    }

    private fun maybeSubmitState(cookie: String, positionMs: Long, force: Boolean) {
        if (trackId <= 0L || sessionId.isBlank()) return
        val now = SystemClock.elapsedRealtime()
        if (!force && lastStateAt > 0L && now - lastStateAt < STATE_INTERVAL_MS) return
        lastStateAt = now
        val id = trackId
        val sid = sessionId
        val mode = playMode
        val progressSec = (positionMs.coerceAtLeast(0L) / 1000L).toInt()
        scope.launch(Dispatchers.IO) {
            runCatching {
                userClient.relayPlayStateSubmit(cookie, id, sid, progressSec, mode)
            }.onFailure { Log.w(TAG, "relay submit failed id=$id", it) }
        }
    }

    private fun reset() {
        trackId = 0L
        trackName = ""
        trackArtist = ""
        sourceId = 0L
        sourceTitle = ""
        playMode = "list_loop"
        quality = AudioQuality.Default
        sessionId = ""
        listenedMs = 0L
        durationMs = 0L
        lastPos = -1L
        accumAt = 0L
        lastStateAt = 0L
        submitted = false
    }

    private fun newSessionId(): String {
        val chars = SESSION_CHARS
        return buildString(12) {
            repeat(12) { append(chars[Random.nextInt(chars.length)]) }
        }
    }

    companion object {
        private const val TAG = "NcmListen"
        private const val STATE_INTERVAL_MS = 25_000L
        private const val SESSION_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
    }
}
