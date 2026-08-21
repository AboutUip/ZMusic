package com.kite.zmusic.data

import kotlin.math.exp

internal const val REALTIME_CACHE_SCORE_THRESHOLD = 6.5
internal const val REALTIME_LIVE_SCORE_KEEP = 6.0
internal const val REALTIME_CACHE_WINDOW_MS = 7L * 24L * 60L * 60L * 1000L

data class RealtimeCacheSession(
    val trackId: Long,
    val quality: String,
    val listenedMs: Long,
    val durationMs: Long,
    val startedAt: Long,
    val endedAt: Long,
)

data class RealtimeCacheScored(
    val trackId: Long,
    val quality: String,
    val playCount: Int,
    val avgRatio: Double,
    val rawScore: Double,
    val scaledScore: Double,
) {
    val key: String get() = cacheKey(trackId, quality)
}

internal fun cacheKey(trackId: Long, quality: String): String = "$trackId:$quality"

/**
 * 窗口内按 (歌曲, 音质) 计次与平均完播比例，原始分 0–10，再在集合内 min-max 到 0 和 10。
 * 只有一首时记为 10。缩放后严格大于 6.5 才进队列。
 */
internal fun scoreRealtimeCache(
    sessions: List<RealtimeCacheSession>,
    nowMs: Long,
    windowMs: Long = REALTIME_CACHE_WINDOW_MS,
): List<RealtimeCacheScored> {
    val from = nowMs - windowMs
    val grouped = LinkedHashMap<String, MutableList<RealtimeCacheSession>>()
    for (s in sessions) {
        if (s.trackId <= 0L || s.quality.isBlank()) continue
        if (s.startedAt < from) continue
        grouped.getOrPut(cacheKey(s.trackId, s.quality)) { mutableListOf() }.add(s)
    }
    if (grouped.isEmpty()) return emptyList()
    val raw = grouped.map { (_, list) ->
        val first = list.first()
        val n = list.size
        val avgRatio = list.map { sessionRatio(it) }.average()
        val freq = 1.0 - exp(-n / 3.0)
        val rawScore = (10.0 * (0.55 * freq + 0.45 * avgRatio)).coerceIn(0.0, 10.0)
        RealtimeCacheScored(
            trackId = first.trackId,
            quality = first.quality,
            playCount = n,
            avgRatio = avgRatio,
            rawScore = rawScore,
            scaledScore = rawScore,
        )
    }
    val minRaw = raw.minOf { it.rawScore }
    val maxRaw = raw.maxOf { it.rawScore }
    val span = maxRaw - minRaw
    return raw.map { item ->
        val scaled = when {
            raw.size == 1 -> 10.0
            span <= 1e-9 -> 10.0
            else -> ((item.rawScore - minRaw) / span * 10.0).coerceIn(0.0, 10.0)
        }
        item.copy(scaledScore = scaled)
    }
}

internal fun sessionRatio(session: RealtimeCacheSession): Double {
    val dur = session.durationMs.coerceAtLeast(1L)
    return (session.listenedMs.toDouble() / dur.toDouble()).coerceIn(0.0, 1.0)
}

/** 实时模式：只看这一次，10 × 完播比例。 */
internal fun livePlayScore(listenedMs: Long, durationMs: Long): Double {
    val dur = durationMs.coerceAtLeast(1L)
    val ratio = (listenedMs.toDouble() / dur.toDouble()).coerceIn(0.0, 1.0)
    return (10.0 * ratio).coerceIn(0.0, 10.0)
}
