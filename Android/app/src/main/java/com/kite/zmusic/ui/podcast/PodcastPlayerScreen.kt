package com.kite.zmusic.ui.podcast

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kite.zmusic.ZMusicApplication
import com.kite.zmusic.data.PodcastEpisode
import com.kite.zmusic.i18n.t
import com.kite.zmusic.playback.PlaybackUiState
import com.kite.zmusic.ui.common.UrlImage
import com.kite.zmusic.ui.icons.ZIcons
import com.kite.zmusic.ui.notice.showIslandNotice
import com.kite.zmusic.ui.player.NcmShare
import com.kite.zmusic.ui.player.NcmShareResult
import com.kite.zmusic.ui.player.NcmShareTarget
import com.kite.zmusic.ui.player.PlayerProgressSlider
import com.kite.zmusic.ui.player.ShareSheet
import com.kite.zmusic.ui.theme.MainPalette
import com.kite.zmusic.ui.theme.TextTheme
import kotlinx.coroutines.launch

@Composable
internal fun PodcastPlayerScreen(
    ui: PlaybackUiState,
    episode: PodcastEpisode?,
    onClose: () -> Unit,
    onToggle: () -> Unit,
    onSeek: (Long) -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onLike: () -> Unit,
    onComments: () -> Unit,
    onOpenShow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val shareScope = rememberCoroutineScope()
    var shareOpen by remember { mutableStateOf(false) }
    val landscape = LocalConfiguration.current.orientation ==
        android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val track = ui.currentTrack
    val title = episode?.name ?: track?.name ?: t("播客")
    val showName = episode?.podcastName?.takeIf { it.isNotBlank() } ?: track?.album
    val cover = episode?.coverUrl ?: track?.coverUrl
    val duration = ui.durationMs.takeIf { it > 0L } ?: episode?.durationMs ?: track?.durationMs ?: 0L
    var scrub by remember(track?.id) { mutableStateOf<Float?>(null) }
    val fraction = when {
        scrub != null -> scrub ?: 0f
        duration > 0L -> (ui.positionMs.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
        else -> 0f
    }
    val face = PodcastFace(
        id = episode?.programId?.takeIf { it > 0L } ?: track?.id ?: 0L,
        cover = cover,
        title = title,
        showName = showName,
        description = episode?.description,
    )
    val queueIndex = ui.index
    var seenIndex by remember { mutableIntStateOf(queueIndex) }
    val turnForward = queueIndex >= seenIndex
    SideEffect { seenIndex = queueIndex }
    val cube = rememberPodcastCube(face, turnForward)
    val backdrop = if (cube.outgoing != null && cube.progress.value < 0.5f) {
        cube.outgoing?.cover
    } else {
        cube.front.cover ?: cover
    }
    val openShare = {
        val current = episode
        if (current == null || current.programId <= 0L) {
            context.showIslandNotice(t("这一期暂时无法分享"))
        } else {
            shareOpen = true
        }
    }
    Box(
        modifier
            .fillMaxSize()
            .background(MainPalette.Page)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            ),
    ) {
        Crossfade(
            targetState = backdrop,
            animationSpec = tween(420, easing = FastOutSlowInEasing),
            label = "podcastBackdrop",
        ) { url ->
            if (url.isNullOrBlank()) return@Crossfade
            UrlImage(
                url = url,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = 0.18f },
                contentScale = ContentScale.Crop,
            )
        }
        if (!backdrop.isNullOrBlank()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MainPalette.Page.copy(alpha = 0.72f)),
            )
        }
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 22.dp),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = ZIcons.Close,
                    contentDescription = t("关闭"),
                    tint = MainPalette.Ink,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onClose,
                        )
                        .padding(8.dp),
                )
                Text(
                    text = t("正在收听"),
                    color = MainPalette.Secondary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            if (landscape) {
                Row(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BoxWithConstraints(
                        Modifier
                            .fillMaxHeight()
                            .padding(vertical = 8.dp),
                    ) {
                        val edge = minOf(maxHeight, 220.dp)
                        CubeFaces(
                            cube = cube,
                            modifier = Modifier.size(edge),
                        ) { shown ->
                            PodcastCover(shown.cover, edge, shape = RoundedCornerShape(28.dp))
                        }
                    }
                    Spacer(Modifier.width(24.dp))
                    Column(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        CubeFaces(
                            cube = cube,
                            modifier = Modifier.fillMaxWidth(),
                        ) { shown ->
                            PlayerCopy(
                                title = shown.title,
                                showName = shown.showName,
                                description = shown.description,
                                onOpenShow = onOpenShow,
                                scroll = false,
                            )
                        }
                        PlayerDeck(
                            fraction = fraction,
                            duration = duration,
                            positionMs = ui.positionMs,
                            scrubbing = scrub != null,
                            enabled = duration > 0L,
                            buffering = ui.buffering || ui.loadPending,
                            playing = ui.playWhenReady,
                            liked = episode?.liked == true,
                            onScrub = { scrub = it },
                            onScrubFinished = {
                                val next = scrub
                                scrub = null
                                if (next != null && duration > 0L) onSeek((next * duration).toLong())
                            },
                            onSeek = onSeek,
                            onPrev = onPrev,
                            onToggle = onToggle,
                            onNext = onNext,
                            onLike = onLike,
                            onComments = onComments,
                            onShare = openShare,
                        )
                    }
                }
            } else {
                PlayerStage(
                    cube = cube,
                    onOpenShow = onOpenShow,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
                PodcastProgress(
                    fraction = fraction,
                    duration = duration,
                    positionMs = ui.positionMs,
                    scrubbing = scrub != null,
                    enabled = duration > 0L,
                    onScrub = { scrub = it },
                    onScrubFinished = {
                        val next = scrub
                        scrub = null
                        if (next != null && duration > 0L) onSeek((next * duration).toLong())
                    },
                    modifier = Modifier.offset(y = -PodcastProgressLift),
                )
                Spacer(Modifier.height(8.dp))
                PodcastTransport(
                    duration = duration,
                    positionMs = ui.positionMs,
                    buffering = ui.buffering || ui.loadPending,
                    playing = ui.playWhenReady,
                    onSeek = onSeek,
                    onPrev = onPrev,
                    onToggle = onToggle,
                    onNext = onNext,
                    modifier = Modifier.offset(y = PodcastTransportDrop),
                )
                Spacer(Modifier.height(48.dp))
                PodcastActions(
                    liked = episode?.liked == true,
                    onLike = onLike,
                    onComments = onComments,
                    onShare = openShare,
                    modifier = Modifier.padding(bottom = PodcastActionBottom),
                )
            }
        }
        val sharing = episode
        if (shareOpen && sharing != null && sharing.programId > 0L) {
            ShareSheet(
                contentKey = "podcast-${sharing.programId}",
                onDismiss = { shareOpen = false },
                onPick = { target ->
                    shareOpen = false
                    if (target == NcmShareTarget.CopyLink) {
                        if (NcmShare.copyProgramLink(context, sharing.programId)) {
                            context.showIslandNotice(t("已复制链接"))
                        } else {
                            context.showIslandNotice(t("这一期暂时无法分享"))
                        }
                    } else {
                        val app = context.applicationContext as ZMusicApplication
                        shareScope.launch {
                            context.showIslandNotice(t("正在生成分享图"))
                            val uri = SharePodcastPoster.prepareShareUri(app, sharing)
                            if (uri == null) {
                                context.showIslandNotice(t("分享图生成失败"))
                            } else {
                                when (val result = NcmShare.sendImage(context, uri, target)) {
                                    NcmShareResult.Opened -> Unit
                                    else -> NcmShare.imageResultNotice(target, result)
                                        ?.let { context.showIslandNotice(it) }
                                }
                            }
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun PlayerStage(
    cube: PodcastCube,
    onOpenShow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val screenHeightPx = with(density) { LocalConfiguration.current.screenHeightDp.dp.toPx() }
    var topInWindow by remember { mutableFloatStateOf(0f) }
    Column(
        modifier
            .fillMaxSize()
            .onGloballyPositioned { topInWindow = it.positionInWindow().y },
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val room = if (topInWindow <= 0f) {
                maxWidth.coerceAtMost(220.dp)
            } else {
                with(density) { (screenHeightPx / 2f - topInWindow).coerceAtLeast(0f).toDp() }
            }
            val coverSize = minOf(maxWidth, room)
            if (coverSize > 0.dp) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CubeFaces(
                        cube = cube,
                        modifier = Modifier.size(coverSize),
                    ) { shown ->
                        PodcastCover(shown.cover, coverSize, shape = RoundedCornerShape(28.dp))
                    }
                }
            }
        }
        BoxWithConstraints(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(top = 18.dp),
        ) {
            CubeFaces(
                cube = cube,
                modifier = Modifier.fillMaxSize(),
            ) { shown ->
                val scroll = rememberScrollState()
                Column(
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(scroll)
                        .heightIn(min = maxHeight),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = shown.title,
                        color = MainPalette.Ink,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 28.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (!shown.showName.isNullOrBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = shown.showName,
                            color = MainPalette.Accent,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = onOpenShow,
                                ),
                        )
                    }
                    val description = shown.description
                    if (!description.isNullOrBlank()) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            description,
                            color = MainPalette.Secondary,
                            fontSize = 14.sp,
                            lineHeight = 21.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

private val PodcastProgressLift = 14.dp
private val PodcastTransportDrop = 20.dp
private val PodcastActionBottom = 12.dp

@Composable
private fun PlayerDeck(
    fraction: Float,
    modifier: Modifier = Modifier,
    duration: Long,
    positionMs: Long,
    scrubbing: Boolean,
    enabled: Boolean,
    buffering: Boolean,
    playing: Boolean,
    liked: Boolean,
    onScrub: (Float) -> Unit,
    onScrubFinished: () -> Unit,
    onSeek: (Long) -> Unit,
    onPrev: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onLike: () -> Unit,
    onComments: () -> Unit,
    onShare: () -> Unit,
) {
    Column(modifier.fillMaxWidth()) {
        PodcastProgress(fraction, duration, positionMs, scrubbing, enabled, onScrub, onScrubFinished)
        Spacer(Modifier.height(12.dp))
        PodcastTransport(duration, positionMs, buffering, playing, onSeek, onPrev, onToggle, onNext)
        Spacer(Modifier.height(16.dp))
        PodcastActions(liked, onLike, onComments, onShare)
    }
}

@Composable
private fun PodcastProgress(
    fraction: Float,
    duration: Long,
    positionMs: Long,
    scrubbing: Boolean,
    enabled: Boolean,
    onScrub: (Float) -> Unit,
    onScrubFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                podcastClock(if (scrubbing) (fraction * duration).toLong() else positionMs),
                color = MainPalette.Secondary,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
            )
            Text(
                podcastClock(duration),
                color = MainPalette.Secondary,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
            )
        }
        Spacer(Modifier.height(2.dp))
        PlayerProgressSlider(
            value = fraction,
            onValueChange = onScrub,
            onValueChangeFinished = onScrubFinished,
            valueRange = 0f..1f,
            enabled = enabled,
            trackHeight = 20.dp,
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor = TextTheme.PlayerProgressThumb,
                activeTrackColor = TextTheme.PlayerProgressActive,
                inactiveTrackColor = TextTheme.PlayerProgressOff,
            ),
        )
    }
}

@Composable
private fun PodcastTransport(
    duration: Long,
    positionMs: Long,
    buffering: Boolean,
    playing: Boolean,
    onSeek: (Long) -> Unit,
    onPrev: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "−15",
            color = MainPalette.Ink,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onSeek((positionMs - 15_000L).coerceAtLeast(0L)) },
        )
        Icon(
            imageVector = ZIcons.SkipPrevious,
            contentDescription = t("上一期"),
            tint = MainPalette.Ink,
            modifier = Modifier
                .size(36.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onPrev,
                ),
        )
        Box(
            Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(MainPalette.Accent)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onToggle,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (buffering) {
                CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(26.dp),
                )
            } else {
                Icon(
                    imageVector = if (playing) ZIcons.Pause else ZIcons.Play,
                    contentDescription = if (playing) t("暂停") else t("播放"),
                    tint = Color.White,
                    modifier = Modifier.size(32.dp),
                )
            }
        }
        Icon(
            imageVector = ZIcons.SkipNext,
            contentDescription = t("下一期"),
            tint = MainPalette.Ink,
            modifier = Modifier
                .size(36.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onNext,
                ),
        )
        Text(
            text = "+15",
            color = MainPalette.Ink,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                val cap = duration.takeIf { it > 0L } ?: Long.MAX_VALUE
                onSeek((positionMs + 15_000L).coerceAtMost(cap))
            },
        )
    }
}

@Composable
private fun PodcastActions(
    liked: Boolean,
    onLike: () -> Unit,
    onComments: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        PlayerAction(
            icon = if (liked) ZIcons.Favorite else ZIcons.FavoriteBorder,
            label = if (liked) t("已喜欢") else t("喜欢"),
            onClick = onLike,
            tint = if (liked) PodcastLiked else MainPalette.Ink,
            labelColor = if (liked) PodcastLiked else MainPalette.Secondary,
        )
        PlayerAction(ZIcons.Comments, t("评论"), onComments)
        PlayerAction(ZIcons.Share, t("分享"), onClick = onShare)
    }
}

@Composable
private fun PlayerCopy(
    title: String,
    showName: String?,
    description: String?,
    onOpenShow: () -> Unit,
    modifier: Modifier = Modifier,
    scroll: Boolean = true,
) {
    Column(if (scroll) modifier.verticalScroll(rememberScrollState()) else modifier) {
        Text(
            text = title,
            color = MainPalette.Ink,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 30.sp,
        )
        if (!showName.isNullOrBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = showName,
                color = MainPalette.Accent,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onOpenShow,
                ),
            )
        }
        if (!description.isNullOrBlank()) {
            Spacer(Modifier.height(14.dp))
            Text(description, color = MainPalette.Secondary, fontSize = 14.sp, lineHeight = 21.sp)
        }
    }
}

private val PodcastLiked = Color(0xFFE53935)

@Composable
private fun PlayerAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: Color = MainPalette.Ink,
    labelColor: Color = MainPalette.Secondary,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick,
        ),
    ) {
        Box(
            Modifier.size(width = 40.dp, height = 42.dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.height(2.dp))
        Text(
            label,
            color = labelColor,
            fontSize = 11.sp,
        )
    }
}

private data class PodcastFace(
    val id: Long,
    val cover: String?,
    val title: String,
    val showName: String?,
    val description: String?,
)

private class PodcastCube {
    var front by mutableStateOf(PodcastFace(0L, null, "", null, null))
    var outgoing by mutableStateOf<PodcastFace?>(null)
    var forward by mutableStateOf(true)
    val progress = Animatable(1f)
}

@Composable
private fun rememberPodcastCube(face: PodcastFace, forward: Boolean): PodcastCube {
    val cube = remember { PodcastCube() }
    LaunchedEffect(face.id) {
        if (face.id == 0L) return@LaunchedEffect
        if (cube.front.id == 0L || cube.front.id == face.id) {
            cube.front = face
            return@LaunchedEffect
        }
        cube.forward = forward
        cube.outgoing = cube.front
        cube.front = face
        cube.progress.snapTo(0f)
        cube.progress.animateTo(1f, tween(480, easing = FastOutSlowInEasing))
        cube.outgoing = null
    }
    SideEffect {
        if (cube.outgoing == null && cube.front.id == face.id && cube.front != face) {
            cube.front = face
        }
    }
    return cube
}

@Composable
private fun CubeFaces(
    cube: PodcastCube,
    modifier: Modifier = Modifier,
    content: @Composable (PodcastFace) -> Unit,
) {
    val outgoing = cube.outgoing
    val progress = cube.progress.value
    if (outgoing == null || progress >= 0.999f) {
        Box(modifier) { content(cube.front) }
        return
    }
    val sign = if (cube.forward) 1f else -1f
    val angle = sign * 180f * progress
    val showBack = progress >= 0.5f
    Box(modifier.clipToBounds()) {
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer {
                    transformOrigin = TransformOrigin.Center
                    val widthPx = size.width
                    // 距离单位是像素。太近时封面会被透视拉满屏幕，拉远后只剩绕轴变窄。
                    cameraDistance = if (widthPx.isFinite() && widthPx > 1f) widthPx * 16f else 12000f
                    rotationY = if (showBack) angle - sign * 180f else angle
                },
        ) {
            content(if (showBack) cube.front else outgoing)
        }
    }
}
