package com.kite.zmusic.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kite.zmusic.ZMusicApplication
import com.kite.zmusic.data.DanmakuRegion
import com.kite.zmusic.data.PlayerDisplayPrefs
import com.kite.zmusic.ui.common.UrlImage
import com.kite.zmusic.ui.common.UrlImageCache
import com.kite.zmusic.ui.main.LocalChromeHaze
import com.kite.zmusic.ui.main.playerOverlayGlass
import com.kite.zmusic.ui.theme.MainPalette
import dev.chrisbanes.haze.HazeState
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

private val DanmakuPill = RoundedCornerShape(50)

/** x 用普通浮点，由统一帧时钟推进，避免逐条 tween 重启造成的生硬步进。 */
private class FlyingDanmaku(
    val spawnId: Long,
    val line: DanmakuLine,
    val y: Float,
    val widthPx: Float,
    val vGap: Float,
    val hGap: Float,
    var x: Float,
)

@Composable
internal fun LandscapeDanmakuOverlay(
    songId: Long,
    density: Int,
    region: DanmakuRegion,
    speed: Float,
    scale: Float,
    playing: Boolean,
    /** false：停发；屏上已有弹幕飞完后再 [onFullyIdle] */
    enabled: Boolean,
    /**
     * 被上层界面遮挡（如黑胶扑克选歌）：停发新弹幕，已有弹幕继续飞、由上层盖住。
     */
    obscured: Boolean = false,
    hazeState: HazeState?,
    onFullyIdle: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    // playing = playWhenReady：仅用户暂停时冻结；切歌 loadPending 期间继续飞
    val context = LocalContext.current
    val app = context.applicationContext as ZMusicApplication
    val densityPx = LocalDensity.current
    val measurer = rememberTextMeasurer()
    // 切歌不重建：上一首弹幕继续飞完
    val flying = remember { mutableStateListOf<FlyingDanmaku>() }
    var spawnSeq by remember { mutableLongStateOf(0L) }
    var feed by remember { mutableStateOf(DanmakuFeed()) }
    val rng = remember { Random(System.nanoTime()) }
    var needMore by remember { mutableStateOf(0) }
    var layoutSize by remember { mutableStateOf(IntSize.Zero) }
    /** 每帧递增，驱动 graphicsLayer 刷新，不重组 Chip 内容参数 */
    var frameEpoch by remember { mutableLongStateOf(0L) }

    val playingNow by rememberUpdatedState(playing)
    val enabledNow by rememberUpdatedState(enabled)
    val obscuredNow by rememberUpdatedState(obscured)
    val onIdleNow by rememberUpdatedState(onFullyIdle)
    val feedNow by rememberUpdatedState(feed)
    val densityNow by rememberUpdatedState(
        density.coerceIn(
            PlayerDisplayPrefs.DANMAKU_DENSITY_MIN,
            PlayerDisplayPrefs.DANMAKU_DENSITY_MAX,
        ),
    )
    val speedNow by rememberUpdatedState(
        speed.coerceIn(
            PlayerDisplayPrefs.DANMAKU_SPEED_MIN,
            PlayerDisplayPrefs.DANMAKU_SPEED_MAX,
        ),
    )
    val scaleNow by rememberUpdatedState(
        scale.coerceIn(
            PlayerDisplayPrefs.DANMAKU_SCALE_MIN,
            PlayerDisplayPrefs.DANMAKU_SCALE_MAX,
        ),
    )
    val regionNow by rememberUpdatedState(region)
    val songIdNow by rememberUpdatedState(songId)
    val chromeHaze = hazeState ?: LocalChromeHaze.current

    val s = scaleNow
    val rowH = with(densityPx) { (32.dp * s).toPx() }
    val avatarDp = 22.dp * s
    val hPadDp = 10.dp * s
    val nickGapDp = 6.dp * s
    val bodyGapDp = 8.dp * s
    val padPx = with(densityPx) { 10.dp.toPx() }
    val baseSpeedPx = with(densityPx) { 86.dp.toPx() }
    val nickStyle = remember(s) {
        TextStyle(
            color = Color(0xCCFFFFFF),
            fontSize = (12f * s).sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
    val bodyStyle = remember(s) {
        TextStyle(
            color = Color(0xF2FFFFFF),
            fontSize = (13f * s).sp,
            fontWeight = FontWeight.Medium,
        )
    }

    LaunchedEffect(songId, enabled) {
        if (!enabled || songId <= 0L) return@LaunchedEffect
        val next = DanmakuFeed()
        feed = next
        needMore = 0
        next.loading = true
        val cookie = app.sessionRepository.session.value?.cookie.orEmpty()
        var page = 1
        var emptyStreak = 0
        while (isActive && enabledNow && songIdNow == songId && next.hasMore && page <= 12) {
            val fetched = withContext(Dispatchers.IO) {
                runCatching {
                    app.commentsRepository.pageNew(
                        songId = songId,
                        cookie = cookie,
                        pageNo = page,
                        pageSize = DanmakuPlaylist.PAGE_SIZE,
                        sortType = DanmakuPlaylist.HOT_SORT,
                        cursor = null,
                    )
                }.getOrElse {
                    if (page != 1) null
                    else runCatching {
                        app.commentsRepository.pageLegacy(
                            songId = songId,
                            cookie = cookie,
                            limit = DanmakuPlaylist.PAGE_SIZE,
                            offset = 0,
                            before = null,
                            includeHotFirst = true,
                        )
                    }.getOrNull()
                }
            } ?: break
            if (!isActive || songIdNow != songId) break
            val added = next.ingest(fetched.comments, fetched.hasMore, page)
            if (added == 0) emptyStreak++ else emptyStreak = 0
            if (!next.hasMore) break
            if (emptyStreak >= 3) {
                next.markExhausted()
                break
            }
            if (next.remaining >= DanmakuPlaylist.PREFETCH_REMAINING) break
            page++
        }
        if (songIdNow == songId) {
            next.loading = false
            if (!next.hasMore && next.remaining == 0 && next.collectedCount > 0) {
                next.markExhausted()
            }
        }
    }

    LaunchedEffect(songId, needMore, enabled) {
        val liveFeed = feedNow
        if (!enabled || needMore <= 0 || songId <= 0L || !liveFeed.needsPrefetch) {
            return@LaunchedEffect
        }
        liveFeed.loading = true
        val cookie = app.sessionRepository.session.value?.cookie.orEmpty()
        val nextPage = liveFeed.pageNo + 1
        val fetched = withContext(Dispatchers.IO) {
            runCatching {
                app.commentsRepository.pageNew(
                    songId = songId,
                    cookie = cookie,
                    pageNo = nextPage,
                    pageSize = DanmakuPlaylist.PAGE_SIZE,
                    sortType = DanmakuPlaylist.HOT_SORT,
                    cursor = null,
                )
            }.getOrNull()
        }
        if (songIdNow != songId) {
            liveFeed.loading = false
            return@LaunchedEffect
        }
        if (fetched == null) {
            liveFeed.markExhausted()
        } else {
            val added = liveFeed.ingest(fetched.comments, fetched.hasMore, nextPage)
            if (added == 0 && nextPage >= 4) liveFeed.markExhausted()
            if (!liveFeed.hasMore) liveFeed.markExhausted()
        }
        liveFeed.loading = false
    }

    LaunchedEffect(enabled, flying.size) {
        if (!enabled && flying.isEmpty()) onIdleNow()
    }

    LaunchedEffect(layoutSize.width, layoutSize.height) {
        val sw = layoutSize.width.toFloat()
        val sh = layoutSize.height.toFloat()
        if (sw < 8f || sh < 8f) return@LaunchedEffect
        var lastNs = 0L
        var spawnAccum = 0f
        while (isActive) {
            withFrameNanos { now ->
                if (lastNs == 0L) {
                    lastNs = now
                    return@withFrameNanos
                }
                val dt = ((now - lastNs) / 1_000_000_000f).coerceIn(0f, 0.048f)
                lastNs = now
                if (dt <= 0f) return@withFrameNanos

                val canSpawn = enabledNow && !obscuredNow && playingNow
                // 关闭 / 遮挡：继续飞完；仅用户暂停（playWhenReady=false）时冻结
                val canMove = !enabledNow || obscuredNow || playingNow
                val vx = baseSpeedPx * speedNow

                if (canMove) {
                    var i = 0
                    while (i < flying.size) {
                        val item = flying[i]
                        item.x -= vx * dt
                        if (item.x + item.widthPx <= 0f) {
                            flying.removeAt(i)
                        } else {
                            i++
                        }
                    }
                }

                if (canSpawn) {
                    spawnAccum += dt * 1000f
                    val spawnInterval = (420f - densityNow * 36f + rng.nextFloat() * 80f)
                        .coerceIn(120f, 520f)
                    if (spawnAccum >= spawnInterval && flying.size < densityNow) {
                        spawnAccum = 0f
                        val sLive = scaleNow
                        val liveRowH = with(densityPx) { (32.dp * sLive).toPx() }
                        val liveAvatarPx = with(densityPx) { (22.dp * sLive).toPx() }
                        val liveHPadPx = with(densityPx) { (10.dp * sLive).toPx() }
                        val liveNickGapPx = with(densityPx) { (6.dp * sLive).toPx() }
                        val liveBodyGapPx = with(densityPx) { (8.dp * sLive).toPx() }
                        val liveMinHGap = with(densityPx) { 48.dp.toPx() } * sLive
                        val liveMaxHGap = with(densityPx) { 160.dp.toPx() } * sLive
                        val liveMinVGap = with(densityPx) { 10.dp.toPx() } * sLive
                        val liveMaxVGap = with(densityPx) { 36.dp.toPx() } * sLive
                        val liveNickStyle = TextStyle(
                            color = Color(0xCCFFFFFF),
                            fontSize = (12f * sLive).sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        val liveBodyStyle = TextStyle(
                            color = Color(0xF2FFFFFF),
                            fontSize = (13f * sLive).sp,
                            fontWeight = FontWeight.Medium,
                        )
                        fun liveMeasure(line: DanmakuLine): Float {
                            val nickW = measurer.measure(
                                text = line.nickname,
                                style = liveNickStyle,
                                overflow = TextOverflow.Clip,
                                maxLines = 1,
                                softWrap = false,
                                constraints = Constraints(maxWidth = Constraints.Infinity),
                            ).size.width
                            val bodyW = measurer.measure(
                                text = line.content,
                                style = liveBodyStyle,
                                overflow = TextOverflow.Clip,
                                maxLines = 1,
                                softWrap = false,
                                constraints = Constraints(maxWidth = Constraints.Infinity),
                            ).size.width
                            return liveHPadPx * 2f + liveAvatarPx + liveNickGapPx + nickW +
                                liveBodyGapPx + bodyW
                        }
                        val liveFeed = feedNow
                        val band = DanmakuPlaylist.bandY(regionNow, sh, liveRowH, padPx)
                        val probes = flying.mapTo(ArrayList(flying.size + 2)) { fly ->
                            DanmakuFlightProbe(
                                x = fly.x,
                                y = fly.y,
                                widthPx = fly.widthPx,
                                vGap = fly.vGap,
                                hGap = fly.hGap,
                            )
                        }
                        val budget = (densityNow - flying.size).coerceAtMost(2)
                        var spawned = 0
                        while (spawned < budget) {
                            val line = liveFeed.next() ?: break
                            val w = liveMeasure(line).coerceAtLeast(liveAvatarPx + liveHPadPx * 2f)
                            val vGap = liveMinVGap + rng.nextFloat() * (liveMaxVGap - liveMinVGap)
                            val hGap = liveMinHGap + rng.nextFloat() * (liveMaxHGap - liveMinHGap)
                            val enterPad = hGap * (0.35f + rng.nextFloat() * 0.9f)
                            val spawnX = sw + enterPad
                            val y = DanmakuPlaylist.pickY(
                                band = band,
                                rowH = liveRowH,
                                spawnX = spawnX,
                                spawnWidth = w,
                                others = probes,
                                random = rng,
                            )
                            if (y == null) {
                                liveFeed.pushFront(line)
                                break
                            }
                            spawnSeq += 1
                            val item = FlyingDanmaku(
                                spawnId = spawnSeq,
                                line = line,
                                y = y,
                                widthPx = w,
                                vGap = vGap,
                                hGap = hGap,
                                x = spawnX,
                            )
                            flying.add(item)
                            probes.add(
                                DanmakuFlightProbe(
                                    x = spawnX,
                                    y = y,
                                    widthPx = w,
                                    vGap = vGap,
                                    hGap = hGap,
                                ),
                            )
                            spawned++
                        }
                    }
                } else {
                    spawnAccum = 0f
                }

                if (enabledNow && !obscuredNow && feedNow.needsPrefetch) {
                    val ask = feedNow.pageNo + 1
                    if (needMore != ask) needMore = ask
                }

                // 有在飞弹幕或本帧有位移时刷新渲染层
                if (flying.isNotEmpty() || canSpawn) {
                    frameEpoch = now
                }
            }
        }
    }

    CompositionLocalProvider(LocalChromeHaze provides chromeHaze) {
        // 订阅帧号，使 graphicsLayer 每帧拿到最新 x
        @Suppress("UNUSED_VARIABLE")
        val frame = frameEpoch
        Box(
            modifier
                .fillMaxSize()
                .clipToBounds()
                .onSizeChanged { layoutSize = it },
        ) {
            flying.forEach { fly ->
                key(fly.spawnId) {
                    DanmakuChip(
                        line = fly.line,
                        nickStyle = nickStyle,
                        bodyStyle = bodyStyle,
                        avatarDp = avatarDp,
                        hPadDp = hPadDp,
                        nickGapDp = nickGapDp,
                        bodyGapDp = bodyGapDp,
                        modifier = Modifier
                            .graphicsLayer {
                                // 读 frame 保证本层随帧失效；x 为连续浮点
                                translationX = fly.x + (frame * 0f)
                                translationY = fly.y
                            }
                            .height(with(densityPx) { rowH.toDp() }),
                    )
                }
            }
        }
    }
}

@Composable
private fun DanmakuChip(
    line: DanmakuLine,
    nickStyle: TextStyle,
    bodyStyle: TextStyle,
    avatarDp: androidx.compose.ui.unit.Dp,
    hPadDp: androidx.compose.ui.unit.Dp,
    nickGapDp: androidx.compose.ui.unit.Dp,
    bodyGapDp: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    val haze = LocalChromeHaze.current
    Row(
        modifier
            .wrapContentWidth(unbounded = true, align = Alignment.Start)
            .playerOverlayGlass(
                shape = DanmakuPill,
                haze = haze,
                solidColor = Color(0xE612141C),
                liquidSurface = MainPalette.glassFill(0.22f),
            )
            .padding(horizontal = hPadDp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(avatarDp)
                .clip(CircleShape)
                .background(MainPalette.Placeholder),
        ) {
            UrlImage(
                url = line.avatarUrl,
                contentDescription = line.nickname,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                showPlaceholder = false,
                maxPx = UrlImageCache.THUMB_MAX_PX,
            )
        }
        Text(
            text = line.nickname,
            style = nickStyle,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
            modifier = Modifier.padding(start = nickGapDp),
        )
        Text(
            text = line.content,
            style = bodyStyle,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
            modifier = Modifier.padding(start = bodyGapDp),
        )
    }
}
