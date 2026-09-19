package com.kite.zmusic.ui.player

import com.kite.zmusic.data.DanmakuRegion
import com.kite.zmusic.data.SongComment
import java.util.ArrayDeque
import kotlin.math.abs
import kotlin.random.Random
import com.kite.zmusic.i18n.t

internal data class DanmakuLine(
    val commentId: Long,
    val nickname: String,
    val avatarUrl: String?,
    val content: String,
)

/** 碰撞检测用的飞行条快照（不依赖 Compose 状态）。 */
internal data class DanmakuFlightProbe(
    val x: Float,
    val y: Float,
    val widthPx: Float,
    val vGap: Float,
    val hGap: Float,
)

internal object DanmakuPlaylist {
    const val GROUP_SIZE = 15
    const val PAGE_SIZE = 45
    const val PREFETCH_REMAINING = 8
    const val HOT_SORT = 2

    fun singleLineContent(raw: String): String? {
        val text = raw.trim()
        if (text.isEmpty()) return null
        if (text.indexOf('\n') >= 0 || text.indexOf('\r') >= 0) return null
        return text
    }

    fun <T> shuffleGroups(items: List<T>, groupSize: Int = GROUP_SIZE): List<T> {
        if (items.isEmpty()) return emptyList()
        val out = ArrayList<T>(items.size)
        var i = 0
        while (i < items.size) {
            val end = (i + groupSize).coerceAtMost(items.size)
            val slice = items.subList(i, end).toMutableList()
            slice.shuffle()
            out.addAll(slice)
            i = end
        }
        return out
    }

    fun fromComment(comment: SongComment): DanmakuLine? {
        val content = singleLineContent(comment.content) ?: return null
        val nick = comment.nickname.trim().ifBlank { t("用户") }
        return DanmakuLine(
            commentId = comment.commentId,
            nickname = nick,
            avatarUrl = comment.avatarUrl,
            content = content,
        )
    }

    /**
     * 弹幕顶边 Y 的合法区间，使整条（高 [rowH]）落在所选屏区。
     */
    fun bandY(
        region: DanmakuRegion,
        heightPx: Float,
        rowH: Float,
        padPx: Float,
    ): ClosedFloatingPointRange<Float> {
        val h = heightPx.coerceAtLeast(rowH + padPx * 2f)
        val top = padPx.coerceAtLeast(0f)
        val bottom = (h - rowH - padPx).coerceAtLeast(top)
        val mid = h * 0.5f
        fun band(a: Float, b: Float): ClosedFloatingPointRange<Float> {
            val s = a.coerceIn(top, bottom)
            val e = b.coerceIn(top, bottom)
            return if (e >= s) s..e else s..s
        }
        return when (region) {
            DanmakuRegion.TOP -> band(top, h * 0.20f - rowH)
            DanmakuRegion.UPPER -> band(top, mid - rowH)
            DanmakuRegion.LOWER -> band(mid, bottom)
            DanmakuRegion.BOTTOM -> band(h * 0.80f, bottom)
            DanmakuRegion.FULL -> band(top, bottom)
        }
    }

    fun overlaps(
        y: Float,
        spawnX: Float,
        spawnWidth: Float,
        rowH: Float,
        other: DanmakuFlightProbe,
    ): Boolean {
        val minV = (rowH * 0.92f + other.vGap).coerceAtLeast(rowH)
        if (abs(y - other.y) >= minV) return false
        val gap = other.hGap.coerceAtLeast(rowH * 0.75f)
        val aL = spawnX
        val aR = spawnX + spawnWidth
        val bL = other.x
        val bR = other.x + other.widthPx
        return aL < bR + gap && bL < aR + gap
    }

    fun pickY(
        band: ClosedFloatingPointRange<Float>,
        rowH: Float,
        spawnX: Float,
        spawnWidth: Float,
        others: List<DanmakuFlightProbe>,
        random: Random,
        attempts: Int = 14,
    ): Float? {
        val span = band.endInclusive - band.start
        repeat(attempts) {
            val y = if (span <= 1f) {
                band.start
            } else {
                band.start + random.nextFloat() * span
            }
            if (others.none { overlaps(y, spawnX, spawnWidth, rowH, it) }) return y
        }
        return null
    }
}

internal class DanmakuFeed {
    private val seen = LinkedHashSet<Long>()
    private val collected = ArrayList<DanmakuLine>()
    private val playQueue = ArrayDeque<DanmakuLine>()
    var pageNo: Int = 0
        private set
    var hasMore: Boolean = true
        private set
    var loading: Boolean = false

    val remaining: Int get() = playQueue.size
    val collectedCount: Int get() = collected.size
    val needsPrefetch: Boolean
        get() = hasMore && remaining <= DanmakuPlaylist.PREFETCH_REMAINING && !loading

    fun next(): DanmakuLine? {
        if (playQueue.isEmpty() && collected.isNotEmpty() && !hasMore) {
            refillFromCollected()
        }
        return playQueue.pollFirst()
    }

    fun pushFront(line: DanmakuLine) {
        playQueue.addFirst(line)
    }

    fun ingest(page: List<SongComment>, pageHasMore: Boolean, fetchedPageNo: Int): Int {
        pageNo = fetchedPageNo
        val batch = ArrayList<DanmakuLine>()
        page.forEach { comment ->
            if (!seen.add(comment.commentId)) return@forEach
            val line = DanmakuPlaylist.fromComment(comment) ?: return@forEach
            collected.add(line)
            batch.add(line)
        }
        if (batch.isNotEmpty()) {
            playQueue.addAll(DanmakuPlaylist.shuffleGroups(batch))
        }
        hasMore = when {
            !pageHasMore -> false
            page.isEmpty() -> false
            batch.isEmpty() && fetchedPageNo >= 8 -> false
            else -> true
        }
        return batch.size
    }

    fun markExhausted() {
        hasMore = false
        if (playQueue.isEmpty() && collected.isNotEmpty()) {
            refillFromCollected()
        }
    }

    private fun refillFromCollected() {
        playQueue.clear()
        playQueue.addAll(DanmakuPlaylist.shuffleGroups(collected))
    }
}
