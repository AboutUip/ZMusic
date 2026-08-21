package com.kite.zmusic.data

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

data class RealtimeCacheOccupancy(
    val usedBytes: Long = 0L,
    val limitBytes: Long = REALTIME_CACHE_MIN_LIMIT_BYTES,
    val fileCount: Int = 0,
    val enabled: Boolean = false,
    val canDownload: Boolean = false,
    val downloading: Boolean = false,
)

internal data class RealtimeCacheFile(
    val trackId: Long,
    val quality: String,
    val fileName: String,
    val bytes: Long,
    val scaledScore: Double,
    val downloadedAt: Long,
    val listenCount: Long = 0L,
    val lastHeardAt: Long = 0L,
) {
    val key: String get() = cacheKey(trackId, quality)
    val heardAt: Long get() = if (lastHeardAt > 0L) lastHeardAt else downloadedAt
}

/**
 * 谨慎模式：原数据按天追加且不删；满一周后按评分裁队列，运行期间后台补下载。
 * 关闭后不采样、不播这套缓存、不删原数据。
 */
class RealtimeCacheController(
    context: Context,
    private val store: RealtimeCacheStore,
    private val sessionRepository: SessionRepository,
    private val userClient: NcmUserClient,
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val ioMutex = Mutex()
    private val rootDir = File(appContext.filesDir, DIR_ROOT).apply { mkdirs() }
    private val sessionDir = File(rootDir, DIR_SESSIONS).apply { mkdirs() }
    private val audioDir = File(rootDir, DIR_AUDIO).apply { mkdirs() }
    private val indexFile = File(rootDir, "index.json")

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    private val _occupancy = MutableStateFlow(RealtimeCacheOccupancy())
    val occupancy: StateFlow<RealtimeCacheOccupancy> = _occupancy.asStateFlow()

    private val _indexRevision = MutableStateFlow(0L)
    val indexRevision: StateFlow<Long> = _indexRevision.asStateFlow()

    @Volatile
    private var files: Map<String, RealtimeCacheFile> = emptyMap()

    private var started = false
    private var downloadJob: Job? = null
    private var liveJob: Job? = null
    @Volatile private var liveCall: Call? = null
    @Volatile private var liveTargetKey: String? = null
    @Volatile private var liveAbandoned = false
    private val pendingListens = HashMap<String, Long>()
    private val pendingHeardAt = HashMap<String, Long>()
    @Volatile private var lastAggressiveListenKey: String? = null

    fun start() {
        if (started) return
        started = true
        scope.launch(Dispatchers.IO) {
            ioMutex.withLock {
                files = readIndex()
                publishOccupancy(downloading = false)
            }
        }
        scope.launch {
            var lastEnabled = false
            var lastLimit = -1L
            var lastMode: RealtimeCacheMode? = null
            store.state.collect { prefs ->
                val cautiousOn = prefs.enabled && prefs.mode == RealtimeCacheMode.Cautious
                val liveOn = prefs.liveDownloadEnabled
                publishOccupancy(downloading = _occupancy.value.downloading)
                if (cautiousOn) {
                    ensureDownloadLoop()
                    val flipped = !lastEnabled || prefs.limitBytes != lastLimit || prefs.mode != lastMode
                    if (flipped) {
                        scope.launch { refreshQueue(forceDownload = true) }
                    } else {
                        maybeDailyRefresh()
                    }
                } else {
                    downloadJob?.cancel()
                    downloadJob = null
                }
                if (!liveOn) cancelLiveDownload()
                if (prefs.enabled && prefs.mode == RealtimeCacheMode.Aggressive) {
                    maybeAggressiveWeeklySweep()
                }
                lastEnabled = cautiousOn
                lastLimit = prefs.limitBytes
                lastMode = prefs.mode
            }
        }
        scope.launch {
            while (true) {
                delay(60_000L)
                if (store.current().enabled) {
                    maybeDailyRefresh()
                    maybeAggressiveWeeklySweep()
                }
            }
        }
    }

    fun playUri(trackId: Long, quality: AudioQuality): String? {
        val prefs = store.current()
        if (!prefs.cachePlaybackEnabled) return null
        if (trackId <= 0L) return null
        val hit = files[cacheKey(trackId, quality.level)] ?: return null
        val file = File(audioDir, hit.fileName)
        if (!file.isFile || file.length() <= 0L) return null
        return Uri.fromFile(file).toString()
    }

    fun recordSession(
        trackId: Long,
        quality: AudioQuality,
        listenedMs: Long,
        durationMs: Long,
        startedAt: Long,
        endedAt: Long,
    ) {
        val prefs = store.current()
        if (!prefs.enabled || prefs.mode != RealtimeCacheMode.Cautious) return
        if (trackId <= 0L || listenedMs < MIN_SESSION_MS) return
        val session = RealtimeCacheSession(
            trackId = trackId,
            quality = quality.level,
            listenedMs = listenedMs,
            durationMs = durationMs.coerceAtLeast(0L),
            startedAt = startedAt,
            endedAt = endedAt,
        )
        scope.launch(Dispatchers.IO) {
            ioMutex.withLock { appendSession(session) }
        }
    }

    fun notifyRealtimePlayStarted(trackId: Long, quality: AudioQuality) {
        val prefs = store.current()
        if (!prefs.liveDownloadEnabled) return
        if (trackId <= 0L) return
        val key = cacheKey(trackId, quality.level)
        if (prefs.mode == RealtimeCacheMode.Aggressive) {
            noteAggressiveListen(key)
        }
        if (files[key] != null) return
        if (liveTargetKey == key && liveJob?.isActive == true) return
        cancelLiveDownload()
        liveAbandoned = false
        liveTargetKey = key
        liveJob = scope.launch(Dispatchers.IO) {
            runCatching { downloadLive(trackId, quality) }
                .onFailure { Log.w(TAG, "live download failed id=$trackId", it) }
        }
    }

    fun notifyRealtimePlayEnded(
        trackId: Long,
        quality: AudioQuality,
        listenedMs: Long,
        durationMs: Long,
    ) {
        val prefs = store.current()
        if (!prefs.enabled || prefs.mode != RealtimeCacheMode.Realtime) return
        if (trackId <= 0L) return
        val score = livePlayScore(listenedMs, durationMs)
        val key = cacheKey(trackId, quality.level)
        if (score < REALTIME_LIVE_SCORE_KEEP) {
            if (liveTargetKey == key) {
                liveAbandoned = true
                liveCall?.cancel()
                liveJob?.cancel()
                liveJob = null
                liveTargetKey = null
            }
            scope.launch(Dispatchers.IO) {
                ioMutex.withLock { deleteKey(key) }
            }
            return
        }
        if (liveTargetKey == key && liveJob?.isActive != true) {
            liveTargetKey = null
        }
        scope.launch(Dispatchers.IO) {
            ioMutex.withLock {
                val cur = files[key] ?: return@withLock
                files = files + (key to cur.copy(scaledScore = score))
                writeIndex(files)
                publishOccupancy(downloading = false)
            }
        }
    }

    fun cancelLiveDownload() {
        liveAbandoned = true
        liveCall?.cancel()
        liveCall = null
        liveJob?.cancel()
        liveJob = null
        liveTargetKey = null
        publishOccupancy(downloading = false)
    }

    fun resetAggressiveListenSession() {
        lastAggressiveListenKey = null
    }

    private fun noteAggressiveListen(key: String) {
        if (lastAggressiveListenKey == key) return
        lastAggressiveListenKey = key
        val now = System.currentTimeMillis()
        scope.launch(Dispatchers.IO) {
            ioMutex.withLock {
                val cur = files[key]
                if (cur != null) {
                    files = files + (key to cur.copy(
                        listenCount = cur.listenCount + 1L,
                        lastHeardAt = now,
                    ))
                    writeIndex(files)
                } else {
                    pendingListens[key] = (pendingListens[key] ?: 0L) + 1L
                    pendingHeardAt[key] = now
                }
                publishOccupancy(downloading = _occupancy.value.downloading)
            }
        }
    }

    suspend fun clearAudioCache() {
        cancelLiveDownload()
        ioMutex.withLock {
            audioDir.listFiles()?.forEach { runCatching { it.delete() } }
            files = emptyMap()
            pendingListens.clear()
            pendingHeardAt.clear()
            lastAggressiveListenKey = null
            writeIndex(files)
            bumpIndex()
            publishOccupancy(downloading = false)
        }
    }

    private fun ensureDownloadLoop() {
        if (downloadJob?.isActive == true) return
        downloadJob = scope.launch {
            while (store.current().let { it.enabled && it.mode == RealtimeCacheMode.Cautious }) {
                val did = runCatching { fillMissingDownloads() }.getOrDefault(false)
                delay(if (did) 400L else 30_000L)
            }
        }
    }

    private fun maybeDailyRefresh() {
        val prefs = store.current()
        if (!prefs.enabled || prefs.mode != RealtimeCacheMode.Cautious) return
        val today = LocalDate.now(ZoneId.systemDefault()).toEpochDay()
        if (today == prefs.lastRefreshEpochDay) {
            if (canDownloadAudio(prefs)) ensureDownloadLoop()
            return
        }
        scope.launch { refreshQueue(forceDownload = true) }
    }

    private fun maybeAggressiveWeeklySweep() {
        val prefs = store.current()
        if (!prefs.enabled || prefs.mode != RealtimeCacheMode.Aggressive) return
        val today = LocalDate.now(ZoneId.systemDefault()).toEpochDay()
        val last = prefs.lastAggressiveSweepEpochDay
        if (last == Long.MIN_VALUE) {
            store.markAggressiveSwept(today)
            return
        }
        if (today - last < 7L) return
        scope.launch(Dispatchers.IO) {
            ioMutex.withLock {
                val cutoff = System.currentTimeMillis() - REALTIME_CACHE_WINDOW_MS
                val kept = LinkedHashMap<String, RealtimeCacheFile>()
                for ((key, f) in files) {
                    if (f.heardAt >= cutoff) {
                        kept[key] = f
                    } else {
                        runCatching { File(audioDir, f.fileName).delete() }
                    }
                }
                files = kept
                writeIndex(files)
                bumpIndex()
                publishOccupancy(downloading = false)
                store.markAggressiveSwept(today)
            }
        }
    }

    private suspend fun refreshQueue(forceDownload: Boolean) {
        val prefs = store.current()
        if (!prefs.enabled || prefs.mode != RealtimeCacheMode.Cautious) return
        ioMutex.withLock {
            val now = System.currentTimeMillis()
            val scored = scoreRealtimeCache(readWindowSessions(now), now)
            val eligible = scored
                .filter { it.scaledScore > REALTIME_CACHE_SCORE_THRESHOLD }
                .sortedWith(
                    compareByDescending<RealtimeCacheScored> { it.scaledScore }
                        .thenByDescending { it.playCount }
                        .thenByDescending { it.avgRatio },
                )
            val eligibleKeys = eligible.map { it.key }.toSet()
            val scoreByKey = scored.associateBy { it.key }
            val kept = LinkedHashMap<String, RealtimeCacheFile>()
            var used = 0L
            val limit = prefs.limitBytes
            for (item in eligible) {
                val existing = files[item.key] ?: continue
                val disk = File(audioDir, existing.fileName)
                if (!disk.isFile || disk.length() <= 0L) continue
                val bytes = disk.length()
                if (used + bytes > limit) {
                    runCatching { disk.delete() }
                    continue
                }
                used += bytes
                kept[item.key] = existing.copy(bytes = bytes, scaledScore = item.scaledScore)
            }
            for ((key, old) in files) {
                if (kept.containsKey(key)) continue
                val latest = scoreByKey[key]?.scaledScore
                if (latest == null || latest <= REALTIME_CACHE_SCORE_THRESHOLD || !eligibleKeys.contains(key)) {
                    runCatching { File(audioDir, old.fileName).delete() }
                } else if (!kept.containsKey(key)) {
                    runCatching { File(audioDir, old.fileName).delete() }
                }
            }
            files = kept
            writeIndex(files)
            bumpIndex()
            publishOccupancy(downloading = false)
            store.markRefreshed(LocalDate.now(ZoneId.systemDefault()).toEpochDay())
        }
        if (forceDownload) ensureDownloadLoop()
    }

    private suspend fun fillMissingDownloads(): Boolean {
        val prefs = store.current()
        if (!prefs.enabled || prefs.mode != RealtimeCacheMode.Cautious) return false
        if (!canDownloadAudio(prefs)) return false
        if (!appContext.isNetworkOnline()) return false
        val cookie = sessionRepository.session.value?.cookie.orEmpty()
        if (cookie.isBlank()) return false
        val target = ioMutex.withLock {
            val now = System.currentTimeMillis()
            val scored = scoreRealtimeCache(readWindowSessions(now), now)
            val eligible = scored
                .filter { it.scaledScore > REALTIME_CACHE_SCORE_THRESHOLD }
                .sortedWith(
                    compareByDescending<RealtimeCacheScored> { it.scaledScore }
                        .thenByDescending { it.playCount },
                )
            val used = files.values.sumOf { it.bytes }
            val limit = prefs.limitBytes
            if (used >= limit) return@withLock null
            eligible.firstOrNull { files[it.key] == null }
        } ?: return false
        publishOccupancy(downloading = true)
        val quality = AudioQuality.fromLevel(target.quality)
        val ok = runCatching {
            downloadOne(target.trackId, quality, target.scaledScore, cookie)
        }.onFailure {
            Log.w(TAG, "download failed id=${target.trackId} q=${target.quality}", it)
        }.getOrDefault(false)
        publishOccupancy(downloading = false)
        return ok
    }

    private suspend fun downloadLive(trackId: Long, quality: AudioQuality) {
        val key = cacheKey(trackId, quality.level)
        if (liveAbandoned || liveTargetKey != key) return
        if (!appContext.isNetworkOnline()) return
        val cookie = sessionRepository.session.value?.cookie.orEmpty()
        if (cookie.isBlank()) return
        publishOccupancy(downloading = true)
        val url = PlayUrlResolver.resolveExact(userClient, trackId, cookie, quality) ?: return
        if (liveAbandoned || liveTargetKey != key) return
        val bytes = downloadBytes(url, live = true) ?: return
        if (bytes.isEmpty()) return
        if (liveAbandoned || liveTargetKey != key) return
        ioMutex.withLock {
            if (liveAbandoned || liveTargetKey != key) return@withLock
            val prefs = store.current()
            if (!prefs.liveDownloadEnabled) return@withLock
            if (!makeRoom(bytes.size.toLong(), key, prefs.limitBytes, prefs.mode)) return@withLock
            val (name, _) = audioFileOf(trackId, quality.level, bytes)
            val dest = File(audioDir, name)
            dest.writeBytes(bytes)
            val now = System.currentTimeMillis()
            val listens = pendingListens.remove(key) ?: 1L
            val heard = pendingHeardAt.remove(key) ?: now
            val entry = RealtimeCacheFile(
                trackId = trackId,
                quality = quality.level,
                fileName = name,
                bytes = dest.length(),
                scaledScore = if (prefs.mode == RealtimeCacheMode.Realtime) {
                    REALTIME_LIVE_SCORE_KEEP
                } else {
                    0.0
                },
                downloadedAt = now,
                listenCount = listens,
                lastHeardAt = heard,
            )
            files = files + (entry.key to entry)
            writeIndex(files)
            bumpIndex()
            publishOccupancy(downloading = false)
        }
    }

    private fun deleteKey(key: String) {
        val old = files[key] ?: return
        runCatching { File(audioDir, old.fileName).delete() }
        files = files - key
        writeIndex(files)
        bumpIndex()
        publishOccupancy(downloading = false)
    }

    private fun makeRoom(
        need: Long,
        protectKey: String,
        limit: Long,
        mode: RealtimeCacheMode,
    ): Boolean {
        var used = files.values.sumOf { it.bytes }
        if (used + need <= limit) return true
        val victims = files.values
            .filter { it.key != protectKey }
            .sortedWith(
                if (mode == RealtimeCacheMode.Aggressive) {
                    compareBy<RealtimeCacheFile> { it.listenCount }.thenBy { it.heardAt }
                } else {
                    compareBy<RealtimeCacheFile> { it.scaledScore }.thenBy { it.downloadedAt }
                },
            )
        val next = LinkedHashMap(files)
        for (v in victims) {
            if (used + need <= limit) break
            runCatching { File(audioDir, v.fileName).delete() }
            next.remove(v.key)
            used -= v.bytes
        }
        files = next
        writeIndex(files)
        return used + need <= limit
    }

    private suspend fun downloadOne(
        trackId: Long,
        quality: AudioQuality,
        scaledScore: Double,
        cookie: String,
    ): Boolean = withContext(Dispatchers.IO) {
        val url = PlayUrlResolver.resolveExact(userClient, trackId, cookie, quality)
            ?: return@withContext false
        val bytes = downloadBytes(url) ?: return@withContext false
        if (bytes.isEmpty()) return@withContext false
        ioMutex.withLock {
            val prefs = store.current()
            if (!prefs.enabled) return@withLock false
            val used = files.values.sumOf { it.bytes }
            if (used + bytes.size > prefs.limitBytes) return@withLock false
            val (name, _) = audioFileOf(trackId, quality.level, bytes)
            val dest = File(audioDir, name)
            dest.writeBytes(bytes)
            val entry = RealtimeCacheFile(
                trackId = trackId,
                quality = quality.level,
                fileName = name,
                bytes = dest.length(),
                scaledScore = scaledScore,
                downloadedAt = System.currentTimeMillis(),
            )
            files = files + (entry.key to entry)
            writeIndex(files)
            bumpIndex()
            publishOccupancy(downloading = true)
            true
        }
    }

    private fun canDownloadAudio(prefs: RealtimeCachePrefs): Boolean {
        if (!prefs.enabled) return false
        if (prefs.mode == RealtimeCacheMode.Realtime ||
            prefs.mode == RealtimeCacheMode.Aggressive
        ) {
            return true
        }
        if (prefs.mode != RealtimeCacheMode.Cautious) return false
        if (prefs.firstEnabledAt <= 0L) return false
        return System.currentTimeMillis() - prefs.firstEnabledAt >= REALTIME_CACHE_WINDOW_MS
    }

    private fun publishOccupancy(downloading: Boolean) {
        val prefs = store.current()
        val used = files.values.sumOf { it.bytes }
        _occupancy.value = RealtimeCacheOccupancy(
            usedBytes = used,
            limitBytes = prefs.limitBytes,
            fileCount = files.size,
            enabled = prefs.enabled,
            canDownload = canDownloadAudio(prefs),
            downloading = downloading,
        )
    }

    private fun bumpIndex() {
        _indexRevision.value = SystemClock.elapsedRealtime()
    }

    private fun appendSession(session: RealtimeCacheSession) {
        val day = Instant.ofEpochMilli(session.startedAt).atZone(ZoneId.systemDefault()).toLocalDate()
        val file = File(sessionDir, "${day.format(DAY_FMT)}.jsonl")
        val line = JSONObject()
            .put("trackId", session.trackId)
            .put("quality", session.quality)
            .put("listenedMs", session.listenedMs)
            .put("durationMs", session.durationMs)
            .put("startedAt", session.startedAt)
            .put("endedAt", session.endedAt)
            .toString()
        file.appendText(line + "\n", Charsets.UTF_8)
    }

    private fun readWindowSessions(nowMs: Long): List<RealtimeCacheSession> {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val out = ArrayList<RealtimeCacheSession>()
        for (i in 0..7) {
            val day = today.minusDays(i.toLong())
            val file = File(sessionDir, "${day.format(DAY_FMT)}.jsonl")
            if (!file.isFile) continue
            file.forEachLine(Charsets.UTF_8) { raw ->
                val line = raw.trim()
                if (line.isEmpty()) return@forEachLine
                parseSession(line)?.let { out.add(it) }
            }
        }
        val from = nowMs - REALTIME_CACHE_WINDOW_MS
        return out.filter { it.startedAt >= from }
    }

    private fun parseSession(raw: String): RealtimeCacheSession? {
        val o = runCatching { JSONObject(raw) }.getOrNull() ?: return null
        val id = o.optLong("trackId", 0L)
        val quality = o.optString("quality", "").trim()
        if (id <= 0L || quality.isEmpty()) return null
        return RealtimeCacheSession(
            trackId = id,
            quality = quality,
            listenedMs = o.optLong("listenedMs", 0L),
            durationMs = o.optLong("durationMs", 0L),
            startedAt = o.optLong("startedAt", 0L),
            endedAt = o.optLong("endedAt", 0L),
        )
    }

    private fun readIndex(): Map<String, RealtimeCacheFile> {
        if (!indexFile.isFile) return emptyMap()
        val raw = runCatching { indexFile.readText(Charsets.UTF_8) }.getOrNull().orEmpty()
        if (raw.isBlank()) return emptyMap()
        val root = runCatching { JSONObject(raw) }.getOrNull() ?: return emptyMap()
        val arr = root.optJSONArray("files") ?: JSONArray()
        val map = LinkedHashMap<String, RealtimeCacheFile>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val file = RealtimeCacheFile(
                trackId = o.optLong("trackId", 0L),
                quality = o.optString("quality", ""),
                fileName = o.optString("fileName", ""),
                bytes = o.optLong("bytes", 0L),
                scaledScore = o.optDouble("scaledScore", 0.0),
                downloadedAt = o.optLong("downloadedAt", 0L),
                listenCount = o.optLong("listenCount", 0L),
                lastHeardAt = o.optLong("lastHeardAt", 0L),
            )
            if (file.trackId <= 0L || file.quality.isBlank() || file.fileName.isBlank()) continue
            val disk = File(audioDir, file.fileName)
            if (!disk.isFile) continue
            map[file.key] = file.copy(bytes = disk.length())
        }
        return map
    }

    private fun writeIndex(map: Map<String, RealtimeCacheFile>) {
        val arr = JSONArray()
        map.values.forEach { f ->
            arr.put(
                JSONObject()
                    .put("trackId", f.trackId)
                    .put("quality", f.quality)
                    .put("fileName", f.fileName)
                    .put("bytes", f.bytes)
                    .put("scaledScore", f.scaledScore)
                    .put("downloadedAt", f.downloadedAt)
                    .put("listenCount", f.listenCount)
                    .put("lastHeardAt", f.lastHeardAt),
            )
        }
        indexFile.writeText(JSONObject().put("files", arr).toString(), Charsets.UTF_8)
    }

    private fun downloadBytes(url: String, live: Boolean = false): ByteArray? {
        val req = Request.Builder().url(url).get().build()
        val call = client.newCall(req)
        if (live) liveCall = call
        return runCatching {
            call.execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                resp.body?.bytes()
            }
        }.onFailure {
            if (call.isCanceled()) return null
            Log.w(TAG, "http download failed", it)
        }.getOrNull().also {
            if (live && liveCall === call) liveCall = null
        }
    }

    private fun audioFileOf(trackId: Long, quality: String, bytes: ByteArray): Pair<String, String> {
        val base = "${trackId}_$quality"
        if (bytes.size >= 4) {
            val head4 = bytes.copyOfRange(0, 4).toString(Charsets.US_ASCII)
            if (head4 == "fLaC") return "$base.flac" to "audio/flac"
            if (head4 == "OggS") return "$base.ogg" to "audio/ogg"
        }
        if (bytes.size >= 12) {
            val brand = bytes.copyOfRange(4, 8).toString(Charsets.US_ASCII)
            if (brand == "ftyp") return "$base.m4a" to "audio/mp4"
            val riff = bytes.copyOfRange(0, 4).toString(Charsets.US_ASCII)
            val wave = bytes.copyOfRange(8, 12).toString(Charsets.US_ASCII)
            if (riff == "RIFF" && wave == "WAVE") return "$base.wav" to "audio/wav"
        }
        return "$base.mp3" to "audio/mpeg"
    }

    companion object {
        private const val TAG = "RealtimeCache"
        private const val DIR_ROOT = "realtime_cache"
        private const val DIR_SESSIONS = "sessions"
        private const val DIR_AUDIO = "audio"
        private const val MIN_SESSION_MS = 200L
        private val DAY_FMT: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    }
}
