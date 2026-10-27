package com.kite.zmusic.ui.user

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.kite.zmusic.ui.common.UrlImage
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** 与目录页横滑同一时长，头像和主页共用这一条进度。 */
internal const val UserAvatarFlightMs = 320

private val UserAvatarFlightSpec = tween<Float>(
    durationMillis = UserAvatarFlightMs,
    easing = FastOutSlowInEasing,
)

internal const val UserAvatarFlightSettle = 0.999f

/**
 * 评论头像点进用户主页：记下起点，驱动同一次进入/返回。
 * 播放页留在下面，返回仍是评论，不跟播放页收起抢同一段时间。
 */
internal class UserAvatarHandoff {
    var token by mutableIntStateOf(0)
        private set
    var userId by mutableLongStateOf(0L)
        private set
    var avatarUrl by mutableStateOf<String?>(null)
        private set
    var origin by mutableStateOf<Rect?>(null)
        private set
    var destination by mutableStateOf<Rect?>(null)
    var progress by mutableFloatStateOf(1f)
    var active by mutableStateOf(false)
        private set
    var present by mutableStateOf(false)
    var exitRequested by mutableStateOf(false)
        private set

    fun capture(userId: Long, avatarUrl: String?, origin: Rect): Int {
        token += 1
        this.userId = userId
        this.avatarUrl = avatarUrl
        this.origin = origin
        this.destination = null
        this.progress = 0f
        this.exitRequested = false
        this.active = true
        return token
    }

    fun requestExit() {
        if (active) exitRequested = true
    }

    fun abandon() {
        token += 1
        active = false
        present = false
        exitRequested = false
        origin = null
        destination = null
        progress = 1f
    }

    fun deactivate() {
        active = false
        exitRequested = false
        origin = null
        destination = null
        progress = 1f
    }
}

internal val LocalUserAvatarHandoff = staticCompositionLocalOf<UserAvatarHandoff?> { null }

internal class UserAvatarTap(
    val open: () -> Unit,
    val modifier: Modifier,
)

@Composable
internal fun rememberUserAvatarTap(
    userId: Long,
    name: String,
    avatarUrl: String?,
    onOpenUser: (Long, String, String?) -> Unit,
): UserAvatarTap {
    val handoff = LocalUserAvatarHandoff.current
    var token by remember { mutableIntStateOf(0) }
    val coords = remember { arrayOfNulls<LayoutCoordinates>(1) }
    val hide = handoff != null &&
        token != 0 &&
        handoff.token == token &&
        handoff.active &&
        handoff.destination != null &&
        handoff.progress < UserAvatarFlightSettle
    val open = {
        if (userId > 0L) {
            val bounds = coords[0]?.takeIf { it.isAttached }?.boundsInWindow()
            if (bounds != null && bounds.width > 1f && bounds.height > 1f) {
                handoff?.let { token = it.capture(userId, avatarUrl, bounds) }
            }
            onOpenUser(userId, name, avatarUrl)
        }
    }
    val modifier = Modifier
        .onGloballyPositioned { coords[0] = it }
        .graphicsLayer { alpha = if (hide) 0f else 1f }
    return UserAvatarTap(open, modifier)
}

@Composable
internal fun UserAvatarFlightDriver(
    handoff: UserAvatarHandoff,
    onExit: () -> Unit,
) {
    val anim = remember { Animatable(1f) }
    val onExitState = rememberUpdatedState(onExit)
    LaunchedEffect(handoff.token) {
        if (!handoff.active || handoff.origin == null) return@LaunchedEffect
        anim.snapTo(0f)
        handoff.progress = 0f
        val mirror = launch {
            snapshotFlow { anim.value }.collect { handoff.progress = it }
        }
        val enter = launch { anim.animateTo(1f, UserAvatarFlightSpec) }
        snapshotFlow { handoff.exitRequested || !handoff.present }.first { it }
        enter.cancel()
        if (handoff.exitRequested && handoff.present) {
            anim.animateTo(0f, UserAvatarFlightSpec)
            onExitState.value()
        }
        mirror.cancel()
        handoff.deactivate()
    }
}

@Composable
internal fun UserAvatarFlightOverlay(
    handoff: UserAvatarHandoff,
    modifier: Modifier = Modifier,
) {
    val origin = handoff.origin
    if (!handoff.active || origin == null) return
    var layerOrigin by remember { mutableStateOf(Offset.Zero) }
    var layerWidth by remember { mutableFloatStateOf(0f) }
    Box(
        modifier
            .fillMaxSize()
            .onGloballyPositioned { coords ->
                layerOrigin = coords.boundsInWindow().topLeft
                layerWidth = coords.size.width.toFloat()
            },
    ) {
        val dest = handoff.destination ?: return@Box
        val progress = handoff.progress
        if (layerWidth <= 1f || progress >= UserAvatarFlightSettle) return@Box
        val slide = layerWidth * (1f - progress)
        val rest = dest.translate(-slide, 0f)
        val rect = lerpRect(origin, rest, progress).translate(-layerOrigin.x, -layerOrigin.y)
        if (rect.width <= 1f || rect.height <= 1f) return@Box
        val density = LocalDensity.current
        val ring = 2.dp * progress
        Box(
            Modifier
                .offset { IntOffset(rect.left.roundToInt(), rect.top.roundToInt()) }
                .size(
                    with(density) { rect.width.toDp() },
                    with(density) { rect.height.toDp() },
                )
                .border(ring, Color.White.copy(alpha = 0.92f * progress), CircleShape)
                .padding(ring)
                .clip(CircleShape),
        ) {
            UrlImage(
                url = handoff.avatarUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
    }
}

private fun lerpRect(start: Rect, stop: Rect, fraction: Float): Rect = Rect(
    lerp(start.left, stop.left, fraction),
    lerp(start.top, stop.top, fraction),
    lerp(start.right, stop.right, fraction),
    lerp(start.bottom, stop.bottom, fraction),
)
