package com.kite.zmusic.ui.library

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.kite.zmusic.data.PlaylistSummary
import com.kite.zmusic.data.SubcountBrief
import com.kite.zmusic.data.UserProfileBrief
import com.kite.zmusic.data.VipKind
import com.kite.zmusic.ui.common.GlassAlertDialog
import com.kite.zmusic.ui.common.UrlImage
import com.kite.zmusic.ui.icons.ZIcons
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val SpaceInk = Color.White
private val SpaceMuted = Color.White.copy(alpha = 0.72f)
private val SpaceLine = Color.White.copy(alpha = 0.28f)
private val SpaceLineStrong = Color.White.copy(alpha = 0.5f)
private val SpaceScrim = Color(0xCC07080C)

private enum class UserConstellation(
    val title: String,
    val subtitle: String,
) {
    Fate("本命", "身份与等级"),
    Voice("声纹", "听歌轨迹"),
    Vault("藏馆", "歌单与收藏"),
}

private data class StarNode(
    val title: String,
    val value: String,
    val ax: Float,
    val ay: Float,
)

private data class StarVisual(
    val pos: Offset,
    val from: StarNode?,
    val to: StarNode?,
    val outAlpha: Float,
    val inAlpha: Float,
    val starAlpha: Float,
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun UserSpaceOverlay(
    progress: Float,
    profile: UserProfileBrief?,
    playlists: List<PlaylistSummary>,
    likedTrackCount: Int,
    subcount: SubcountBrief?,
    customBgPath: String?,
    avatarStart: Offset,
    avatarStartSize: Float,
    reveal: UserSpaceRevealState,
    onClose: () -> Unit,
    onPickBackground: () -> Unit,
    onClearBackground: () -> Unit,
    modifier: Modifier = Modifier.fillMaxSize(),
) {
    val t = progress.coerceIn(0f, 1f)
    if (t <= SpaceAvatarHandoffProgress || profile == null) return
    val scope = rememberCoroutineScope()
    val pager = rememberConstellationPagerState(scope, UserConstellation.entries.size)
    val opened = reveal.isOpen
    val chromeT = spaceChromeProgress(t)
    var confirmClearBg by remember { mutableStateOf(false) }
    BackHandler(enabled = t > 0.04f, onBack = onClose)

    var overlayOrigin by remember { mutableStateOf(Offset.Zero) }
    BoxWithConstraints(
        modifier
            .zIndex(80f)
            .onGloballyPositioned { overlayOrigin = it.positionInWindow() },
    ) {
        val w = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val h = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        val landscape = LocalConfiguration.current.orientation ==
            android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val density = LocalDensity.current
        val hangPx = with(density) { ProfileAvatarBadgeHang.toPx() }
        val endCircle = with(density) { 108.dp.toPx() }
        val startLayout = avatarStartSize.takeIf { it > 1f }
            ?: with(density) { (80.dp + ProfileAvatarBadgeHang).toPx() }
        val startCircle = (startLayout - hangPx).coerceAtLeast(1f)
        val circleNow = startCircle + (endCircle - startCircle) * t
        val avatarSize = circleNow + hangPx
        val endX = (w - avatarSize) / 2f
        val endY = if (landscape) {
            (h - avatarSize) / 2f
        } else {
            h * 0.38f - avatarSize / 2f
        }
        val startX = avatarStart.x - overlayOrigin.x
        val startY = avatarStart.y - overlayOrigin.y
        val avatarX = startX + (endX - startX) * t
        val avatarY = startY + (endY - startY) * t
        val center = Offset(avatarX + avatarSize / 2f, avatarY + avatarSize / 2f)
        val orbit = minOf(w, h) * 0.34f
        val kinds = UserConstellation.entries
        val fromKind = kinds[pager.fromIndex]
        val toKind = kinds[pager.toIndex]
        val fromNodes = remember(fromKind, profile, playlists, likedTrackCount, subcount) {
            constellationNodes(fromKind, profile, playlists, likedTrackCount, subcount)
        }
        val toNodes = remember(toKind, profile, playlists, likedTrackCount, subcount) {
            constellationNodes(toKind, profile, playlists, likedTrackCount, subcount)
        }
        val stars = remember(fromNodes, toNodes, pager.morphT, center, orbit, t) {
            morphStars(fromNodes, toNodes, pager.morphT, center, orbit, t)
        }

        UserSpaceBackdrop(
            progress = t,
            pageOffset = pager.offset,
        )

        ConstellationStage(
            stars = stars,
            center = center,
            appear = t,
            titleAppear = chromeT,
            fromKind = fromKind,
            toKind = toKind,
            morphT = pager.morphT,
        )

        ProfileAvatar(
            profile = profile,
            size = with(density) { startCircle.toDp() },
            placeholderSp = 32.sp,
            modifier = Modifier.graphicsLayer {
                translationX = avatarX
                translationY = avatarY
                val scale = circleNow / startCircle
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(0f, 0f)
                clip = false
                compositingStrategy = CompositingStrategy.ModulateAlpha
            },
        )

        if (opened) {
            Box(
                Modifier
                    .fillMaxSize()
                    .userSpaceOpenPad(reveal, pager),
            )
        }

        if (chromeT > 0.01f) {
            val btnY = (center.y - with(density) { 22.dp.toPx() }).roundToInt()
            Row(
                Modifier
                    .fillMaxWidth()
                    .zIndex(8f)
                    .windowInsetsPadding(
                        WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal),
                    )
                    .offset { IntOffset(0, btnY) }
                    .padding(horizontal = 10.dp)
                    .graphicsLayer {
                        alpha = chromeT
                        translationY = (1f - chromeT) * 18f
                    },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SpaceNavButton(
                    left = true,
                    enabled = opened,
                ) {
                    pager.prev()
                }
                SpaceNavButton(
                    left = false,
                    enabled = opened,
                ) {
                    pager.next()
                }
            }
        }

        if (chromeT > 0.01f) {
            SpaceTopBar(
                progress = chromeT,
                currentPage = pager.displayPage,
                hasCustomBg = !customBgPath.isNullOrBlank(),
                interactive = opened,
                onClose = onClose,
                onPickBackground = onPickBackground,
                onClearBackground = { confirmClearBg = true },
                onSelectPage = { pager.goTo(it) },
                modifier = Modifier.zIndex(9f),
            )
        }

        if (confirmClearBg) {
            GlassAlertDialog(
                title = "恢复默认背景",
                message = "移除自定义封面，改回账号默认背景。",
                confirmLabel = "恢复",
                onConfirm = {
                    onClearBackground()
                    confirmClearBg = false
                },
                onDismiss = { confirmClearBg = false },
            )
        }

        if (!opened && !landscape && t in 0.02f..0.92f) {
            val hint = "下拉进入用户空间"
            Text(
                text = hint,
                style = TextStyle(
                    color = SpaceMuted,
                    fontSize = 12.sp,
                    shadow = Shadow(Color.Black.copy(alpha = 0.45f), Offset(0f, 1f), 8f),
                ),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 28.dp)
                    .graphicsLayer {
                        alpha = (t / 0.28f).coerceIn(0f, 1f) *
                            (1f - chromeT)
                    },
            )
        }
    }
}


@Composable
private fun UserSpaceBackdrop(
    progress: Float,
    pageOffset: Float,
) {
    val t = progress.coerceIn(0f, 1f)
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(SpaceScrim.copy(alpha = 0.10f + 0.58f * t)),
        )
        SpaceStarField(progress = t, pageOffset = pageOffset)
    }
}

@Composable
private fun SpaceStarField(progress: Float, pageOffset: Float) {
    val stars = remember {
        List(56) { i ->
            val n = (i * 127.1f) % 1f
            Triple(
                ((i * 73) % 1000) / 1000f,
                ((i * 191) % 1000) / 1000f,
                0.35f + n * 0.65f,
            )
        }
    }
    Canvas(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                translationX = -pageOffset * 18f
                translationY = (1f - progress) * 28f
                alpha = progress
            },
    ) {
        stars.forEach { (x, y, a) ->
            drawCircle(
                color = Color.White.copy(alpha = 0.18f + 0.45f * a),
                radius = 1.2f + a * 1.6f,
                center = Offset(x * size.width, y * size.height),
            )
        }
    }
}

@Composable
private fun ConstellationStage(
    stars: List<StarVisual>,
    center: Offset,
    appear: Float,
    titleAppear: Float,
    fromKind: UserConstellation,
    toKind: UserConstellation,
    morphT: Float,
) {
    val density = LocalDensity.current
    val labelHalf = with(density) { 64.dp.toPx() }
    val labelUp = with(density) { 58.dp.toPx() }
    val labelDown = with(density) { 12.dp.toPx() }
    val positions = stars.map { it.pos }
    Canvas(Modifier.fillMaxSize()) {
        val visible = stars.filter { it.starAlpha > 0.04f }
        if (visible.size >= 2) {
            val effect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)
            for (i in 0 until visible.lastIndex) {
                drawLine(
                    color = SpaceLineStrong.copy(alpha = (0.35f + 0.4f * appear) * visible[i].starAlpha),
                    start = visible[i].pos,
                    end = visible[i + 1].pos,
                    strokeWidth = 1.6f,
                    pathEffect = effect,
                )
            }
        }
        positions.forEachIndexed { i, pos ->
            val a = stars[i].starAlpha * appear
            if (a <= 0.02f) return@forEachIndexed
            drawLine(
                color = SpaceLine.copy(alpha = (0.25f + 0.5f * appear) * a),
                start = center,
                end = pos,
                strokeWidth = 1.2f,
            )
            drawCircle(Color.White.copy(alpha = 0.92f * a), radius = 4.2f, center = pos)
            drawCircle(Color.White.copy(alpha = 0.18f * a), radius = 9f, center = pos)
        }
    }
    stars.forEach { star ->
        if (star.starAlpha <= 0.02f) return@forEach
        val above = star.pos.y < center.y
        Box(
            Modifier
                .offset {
                    IntOffset(
                        (star.pos.x - labelHalf).roundToInt(),
                        (star.pos.y + if (above) -labelUp else labelDown).roundToInt(),
                    )
                }
                .graphicsLayer { alpha = appear * star.starAlpha }
                .width(128.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            StarTexts(star = star)
        }
    }
    val outA = (1f - morphT / 0.46f).coerceIn(0f, 1f)
    val inA = ((morphT - 0.28f) / 0.72f).coerceIn(0f, 1f)
    Column(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(top = 58.dp)
            .graphicsLayer {
                alpha = titleAppear
                translationY = (1f - titleAppear) * -28f
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (outA > 0.02f) {
                ConstellationTitle(fromKind, outA)
            }
            if (inA > 0.02f && (toKind != fromKind || morphT > 0.5f)) {
                ConstellationTitle(toKind, inA)
            }
        }
    }
}

@Composable
private fun ConstellationTitle(kind: UserConstellation, alpha: Float) {
    Column(
        modifier = Modifier.graphicsLayer { this.alpha = alpha },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = kind.title,
            style = TextStyle(
                color = SpaceInk,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 3.sp,
                shadow = Shadow(Color.Black.copy(alpha = 0.5f), Offset.Zero, 10f),
            ),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = kind.subtitle,
            style = TextStyle(color = SpaceMuted, fontSize = 12.sp),
        )
    }
}

@Composable
private fun StarTexts(star: StarVisual) {
    Box(contentAlignment = Alignment.TopCenter) {
        val from = star.from
        if (from != null && star.outAlpha > 0.02f) {
            StarLabel(from.title, from.value, star.outAlpha)
        }
        val to = star.to
        if (to != null && star.inAlpha > 0.02f) {
            StarLabel(to.title, to.value, star.inAlpha)
        }
    }
}

@Composable
private fun StarLabel(title: String, value: String, alpha: Float) {
    Column(
        modifier = Modifier.graphicsLayer { this.alpha = alpha },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            style = TextStyle(
                color = SpaceMuted,
                fontSize = 11.sp,
                shadow = Shadow(Color.Black.copy(alpha = 0.5f), Offset.Zero, 8f),
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = value,
            style = TextStyle(
                color = SpaceInk,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                shadow = Shadow(Color.Black.copy(alpha = 0.55f), Offset(0f, 1f), 10f),
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SpaceTopBar(
    progress: Float,
    currentPage: Int,
    hasCustomBg: Boolean,
    interactive: Boolean,
    onClose: () -> Unit,
    onPickBackground: () -> Unit,
    onClearBackground: () -> Unit,
    onSelectPage: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val status = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Box(
        modifier
            .fillMaxWidth()
            .windowInsetsPadding(
                WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal),
            )
            .padding(top = status)
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .graphicsLayer {
                alpha = progress
                translationY = (1f - progress) * -64f
            },
    ) {
        SpaceChromeButton(
            onClick = onClose,
            enabled = interactive,
            modifier = Modifier.align(Alignment.CenterStart),
        ) {
            Icon(
                imageVector = ZIcons.Close,
                contentDescription = "退出用户空间",
                tint = Color.White,
                modifier = Modifier.size(20.dp),
            )
        }
        Row(
            Modifier.align(Alignment.Center),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            UserConstellation.entries.forEachIndexed { i, _ ->
                Box(
                    Modifier
                        .size(18.dp)
                        .clickable(enabled = interactive) { onSelectPage(i) },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .size(if (currentPage == i) 7.dp else 5.dp)
                            .clip(CircleShape)
                            .background(
                                if (currentPage == i) Color.White else Color.White.copy(alpha = 0.35f),
                            ),
                    )
                }
            }
        }
        Row(
            Modifier.align(Alignment.CenterEnd),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (hasCustomBg) {
                SpaceChromeButton(
                    onClick = onClearBackground,
                    enabled = interactive,
                ) {
                    Icon(
                        imageVector = ZIcons.HideImage,
                        contentDescription = "恢复默认背景",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            SpaceChromeButton(
                onClick = onPickBackground,
                enabled = interactive,
            ) {
                Icon(
                    imageVector = ZIcons.Wallpaper,
                    contentDescription = "设置背景",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SpaceNavButton(
    left: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = if (enabled) 0.16f else 0.07f))
            .border(1.dp, Color.White.copy(alpha = 0.22f), CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (left) ZIcons.ChevronLeft else ZIcons.ChevronRight,
            contentDescription = if (left) "上一星座" else "下一星座",
            tint = Color.White.copy(alpha = if (enabled) 1f else 0.35f),
            modifier = Modifier.size(22.dp),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SpaceChromeButton(
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.14f))
            .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
            .combinedClickable(
                enabled = enabled,
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
internal fun ProfileLocalImage(path: String?, modifier: Modifier = Modifier) {
    var bitmap by remember(path) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(path) {
        if (path.isNullOrBlank()) {
            bitmap = null
            return@LaunchedEffect
        }
        bitmap = withContext(Dispatchers.IO) {
            runCatching { BitmapFactory.decodeFile(path)?.asImageBitmap() }.getOrNull()
        }
    }
    val bmp = bitmap
    if (bmp != null) {
        Image(
            bitmap = bmp,
            contentDescription = null,
            modifier = modifier,
            contentScale = ContentScale.Crop,
        )
    }
}

private fun morphStars(
    from: List<StarNode>,
    to: List<StarNode>,
    morphT: Float,
    center: Offset,
    orbit: Float,
    appear: Float,
): List<StarVisual> {
    val slots = maxOf(from.size, to.size, 1)
    val t = morphT.coerceIn(0f, 1f)
    return List(slots) { i ->
        val a = from.getOrNull(i)
        val b = to.getOrNull(i)
        val start = worldPos(a, center, orbit, appear)
        val end = worldPos(b, center, orbit, appear)
        val slotT = staggered(t, i, slots)
        val eased = SpaceMorphEasing.transform(slotT)
        val pos = if (a == null) {
            arcBezier(center, end, center, eased, i)
        } else if (b == null) {
            arcBezier(start, center, center, eased, i)
        } else {
            arcBezier(start, end, center, eased, i)
        }
        val born = a == null && b != null
        val dying = a != null && b == null
        val starAlpha = when {
            born -> eased
            dying -> 1f - eased
            else -> 1f
        }
        StarVisual(
            pos = pos,
            from = a,
            to = b,
            outAlpha = (1f - slotT / 0.48f).coerceIn(0f, 1f),
            inAlpha = ((slotT - 0.28f) / 0.72f).coerceIn(0f, 1f),
            starAlpha = starAlpha,
        )
    }
}

private fun staggered(t: Float, slot: Int, total: Int): Float {
    val delay = 0.07f * slot
    val span = 1f - 0.07f * (total - 1).coerceAtLeast(0)
    return ((t - delay) / span.coerceAtLeast(0.55f)).coerceIn(0f, 1f)
}

private fun worldPos(node: StarNode?, center: Offset, orbit: Float, appear: Float): Offset {
    if (node == null) return center
    return Offset(
        center.x + node.ax * orbit * appear,
        center.y + node.ay * orbit * appear,
    )
}

private fun arcBezier(from: Offset, to: Offset, center: Offset, t: Float, slot: Int): Offset {
    val chord = to - from
    val mid = Offset((from.x + to.x) * 0.5f, (from.y + to.y) * 0.5f)
    val radial = (mid - center).safeNormalize()
    val perp = Offset(-radial.y, radial.x) * if (slot % 2 == 0) 1f else -1f
    val dist = chord.getDistance().coerceAtLeast(48f)
    val bulge = dist * (0.38f + 0.08f * (slot % 3))
    val ctrl = mid + perp * bulge + radial * (bulge * 0.22f)
    val u = 1f - t
    return Offset(
        u * u * from.x + 2f * u * t * ctrl.x + t * t * to.x,
        u * u * from.y + 2f * u * t * ctrl.y + t * t * to.y,
    )
}

private fun Offset.safeNormalize(): Offset {
    val d = getDistance()
    return if (d < 0.001f) Offset(0f, -1f) else this / d
}

private fun constellationNodes(
    kind: UserConstellation,
    profile: UserProfileBrief,
    playlists: List<PlaylistSummary>,
    likedTrackCount: Int,
    subcount: SubcountBrief?,
): List<StarNode> {
    return when (kind) {
        UserConstellation.Fate -> buildList {
            profile.level?.let { add(StarNode("等级", "Lv.$it", -0.78f, -0.46f)) }
            val vip = when (profile.vipKind) {
                VipKind.Svip -> "SVIP"
                VipKind.Vip -> "VIP"
                VipKind.None -> "未开通"
            }
            add(StarNode("黑胶", vip, 0.80f, -0.28f))
            profile.listenSongs?.let {
                add(StarNode("听歌", formatPlayCount(it), 0.48f, 0.70f))
            }
            val sig = profile.signature?.trim().orEmpty()
            if (sig.isNotEmpty()) {
                add(StarNode("签名", sig.take(8), -0.62f, 0.52f))
            } else {
                add(StarNode("昵称", profile.nickname.take(8), -0.62f, 0.52f))
            }
        }
        UserConstellation.Voice -> buildList {
            profile.listenSongs?.let {
                add(StarNode("累计", formatPlayCount(it), 0f, -0.82f))
            }
            val lv = profile.level
            if (lv != null && lv >= 10) {
                add(StarNode("等级", "听歌满级", 0f, 0.78f))
            } else {
                profile.levelProgress?.let { p ->
                    add(StarNode("进度", "${(p * 100f).toInt().coerceIn(0, 100)}%", 0f, 0.78f))
                }
                val now = profile.nowPlayCount
                val next = profile.nextPlayCount
                if (now != null && next != null && next > now) {
                    add(StarNode("距升级", "${formatPlayCount(next - now)} 首", -0.78f, 0.08f))
                }
            }
            profile.nowPlayCount?.let {
                add(StarNode("本级", formatPlayCount(it), 0.78f, 0.08f))
            }
            if (isEmpty()) {
                add(StarNode("听歌", "暂无记录", 0f, -0.2f))
            }
        }
        UserConstellation.Vault -> buildList {
            val liked = likedTrackCount.takeIf { it > 0 }
                ?: playlists.firstOrNull { it.isHeartPlaylist }?.trackCount
            liked?.let { add(StarNode("我喜欢", "${it} 首", -0.78f, -0.38f)) }
            val created = subcount?.createdPlaylistCount
                ?: playlists.count { it.isOwned && !it.isHeartPlaylist }
            add(StarNode("创建", "$created 个", -0.28f, -0.72f))
            val collected = subcount?.subPlaylistCount
                ?: playlists.count { it.isSubscribed }
            add(StarNode("收藏", "$collected 个", 0.72f, -0.22f))
            val extra = subcount?.let { sc ->
                when {
                    sc.subArtistCount > 0 -> StarNode("歌手", "${sc.subArtistCount} 位", 0.42f, 0.68f)
                    sc.subAlbumCount > 0 -> StarNode("专辑", "${sc.subAlbumCount} 张", 0.42f, 0.68f)
                    else -> null
                }
            }
            add(extra ?: StarNode("歌单", "${playlists.size} 个", 0.42f, 0.68f))
        }
    }
}

internal fun formatPlayCount(n: Long): String = when {
    n >= 100_000_000 -> "%.1f亿".format(n / 100_000_000.0)
    n >= 10_000 -> "%.1f万".format(n / 10_000.0)
    else -> n.toString()
}
