package com.kite.zmusic.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kite.zmusic.R
import com.kite.zmusic.ui.common.rememberPredictiveBackUi
import com.kite.zmusic.ui.icons.ZIcons
import com.kite.zmusic.ui.main.MainPalette
import com.kite.zmusic.ui.main.pageSheetHazeStyle
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import kotlinx.coroutines.delay
import com.kite.zmusic.i18n.t

private val SharePanelShape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)
private val ShareSheetPopEasing = CubicBezierEasing(0.16f, 1.12f, 0.28f, 1f)
private val ShareSheetHideEasing = CubicBezierEasing(0.4f, 0.02f, 0.2f, 1f)

internal val LocalShareSheetHost = staticCompositionLocalOf<ShareSheetHostState?> { null }

@Stable
internal class ShareSheetHostState {
    var spec by mutableStateOf<ShareSheetSpec?>(null)
        private set
    var visible by mutableStateOf(false)
        private set
    var presentSeq by mutableStateOf(0)
        private set

    fun present(spec: ShareSheetSpec) {
        this.spec = spec
        visible = true
        presentSeq += 1
    }

    fun update(spec: ShareSheetSpec) {
        if (visible) this.spec = spec
    }

    fun hide(contentKey: String? = null) {
        val cur = spec ?: return
        if (contentKey != null && cur.contentKey != contentKey) return
        visible = false
    }

    fun finishHide() {
        if (!visible) spec = null
    }
}

internal data class ShareSheetSpec(
    val contentKey: String,
    val onPick: (NcmShareTarget) -> Unit,
    val onDismiss: () -> Unit,
)

/** 全应用唯一分享底栏入口：走 [PortraitShareSheet]，不要再用 GlassActionSheet 列分享目标。 */
@Composable
internal fun ShareSheet(
    onPick: (NcmShareTarget) -> Unit,
    onDismiss: () -> Unit,
    contentKey: String,
) {
    val host = LocalShareSheetHost.current ?: return
    val onPickUpdated = rememberUpdatedState(onPick)
    val onDismissUpdated = rememberUpdatedState(onDismiss)
    fun currentSpec() = ShareSheetSpec(
        contentKey = contentKey,
        onPick = { onPickUpdated.value(it) },
        onDismiss = { onDismissUpdated.value() },
    )
    DisposableEffect(host, contentKey) {
        host.present(currentSpec())
        onDispose { host.hide(contentKey) }
    }
    SideEffect {
        host.update(currentSpec())
    }
}

@Composable
internal fun ShareSheetOverlay(
    state: ShareSheetHostState,
    hazeState: HazeState? = null,
    modifier: Modifier = Modifier,
) {
    val spec = state.spec
    val visible = state.visible
    LaunchedEffect(visible) {
        if (!visible && state.spec != null) {
            delay(220)
            state.finishHide()
        }
    }
    if (spec == null) return

    val density = LocalDensity.current
    val fallbackHPx = with(density) { rememberPortraitShareSheetHeight().toPx() }
    var measuredHPx by remember { mutableFloatStateOf(0f) }
    val sheetHPx = measuredHPx.takeIf { it > 1f } ?: fallbackHPx
    val backUi = rememberPredictiveBackUi(enabled = visible, onBack = spec.onDismiss)
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(visible, state.presentSeq) {
        if (visible) {
            reveal.snapTo(0f)
            reveal.animateTo(1f, tween(320, easing = ShareSheetPopEasing))
        } else {
            reveal.animateTo(0f, tween(180, easing = ShareSheetHideEasing))
        }
    }
    val t = reveal.value * (1f - backUi.progress)
    Box(
        modifier
            .fillMaxSize()
            .graphicsLayer { alpha = t },
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.22f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = spec.onDismiss,
                ),
        )
        PortraitShareSheet(
            onPick = spec.onPick,
            hazeState = hazeState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .onSizeChanged { measuredHPx = it.height.toFloat() }
                .graphicsLayer {
                    transformOrigin = TransformOrigin(0.5f, 1f)
                    translationY = (1f - t) * sheetHPx
                    alpha = t
                },
        )
    }
}

/**
 * 分享底栏内容高度（不含导航 inset）。
 * 与真实内容对齐：把手 + 标题 + 一排图标文案 + 上下内边距。
 */
internal val PortraitShareSheetBodyHeight = 126.dp

@Composable
internal fun rememberPortraitShareSheetHeight(): Dp {
    val nav = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    return PortraitShareSheetBodyHeight + nav
}

@Composable
internal fun PortraitShareSheet(
    onPick: (NcmShareTarget) -> Unit,
    hazeState: HazeState? = null,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .clip(SharePanelShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            ),
    ) {
        if (hazeState != null) {
            Box(
                Modifier
                    .matchParentSize()
                    .hazeEffect(state = hazeState, style = pageSheetHazeStyle()),
            )
        } else {
            Box(
                Modifier
                    .matchParentSize()
                    .background(MainPalette.Page.copy(alpha = 0.96f)),
            )
        }
        Box(
            Modifier
                .matchParentSize()
                .background(MainPalette.SheetWash),
        )
        Column(
            Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 8.dp),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .width(36.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MainPalette.Hint),
                )
            }
            Text(
                text = t("分享"),
                style = TextStyle(
                    color = MainPalette.Ink,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    letterSpacing = (-0.2).sp,
                ),
            )
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top,
            ) {
                ShareBrandAction(
                    drawableRes = R.drawable.ic_share_wechat_moments,
                    label = t("微信朋友圈"),
                    onClick = { onPick(NcmShareTarget.WeChatMoments) },
                )
                ShareBrandAction(
                    drawableRes = R.drawable.ic_share_wechat,
                    label = t("微信好友"),
                    onClick = { onPick(NcmShareTarget.WeChatFriend) },
                )
                ShareBrandAction(
                    drawableRes = R.drawable.ic_share_qq,
                    label = t("QQ好友"),
                    onClick = { onPick(NcmShareTarget.QqFriend) },
                )
                ShareVectorAction(
                    icon = ZIcons.Link,
                    label = t("仅复制链接"),
                    onClick = { onPick(NcmShareTarget.CopyLink) },
                )
            }
        }
    }
}

@Composable
private fun ShareBrandAction(
    drawableRes: Int,
    label: String,
    onClick: () -> Unit,
) {
    Column(
        Modifier
            .width(72.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(drawableRes),
            contentDescription = label,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            style = TextStyle(
                color = MainPalette.Ink,
                fontSize = 11.sp,
                lineHeight = 13.sp,
            ),
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ShareVectorAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Column(
        Modifier
            .width(72.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(MainPalette.Hairline.copy(alpha = 0.55f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = MainPalette.Ink,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            style = TextStyle(
                color = MainPalette.Ink,
                fontSize = 11.sp,
                lineHeight = 13.sp,
            ),
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
