package com.kite.zmusic.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kite.zmusic.ui.main.MainPalette
import com.kite.zmusic.ui.main.dialogLiquidGlass
import com.kyant.backdrop.Backdrop
import kotlinx.coroutines.delay

private val SheetShape = RoundedCornerShape(32.dp)
private val SheetPopEasing = CubicBezierEasing(0.16f, 1.12f, 0.28f, 1f)
private val SheetHideEasing = CubicBezierEasing(0.4f, 0.02f, 0.2f, 1f)

internal val LocalGlassActionSheetHost = staticCompositionLocalOf<GlassActionSheetHostState?> { null }

data class GlassSheetAction(
    val label: String,
    val destructive: Boolean = false,
    val coverUrl: String? = null,
    val showCover: Boolean = false,
    val onClick: () -> Unit,
)

@Stable
internal class GlassActionSheetHostState {
    var spec by mutableStateOf<GlassActionSheetSpec?>(null)
        private set
    var visible by mutableStateOf(false)
        private set
    var presentSeq by mutableStateOf(0)
        private set

    fun present(spec: GlassActionSheetSpec) {
        this.spec = spec
        visible = true
        presentSeq += 1
    }

    fun update(spec: GlassActionSheetSpec) {
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

internal data class GlassActionSheetSpec(
    val title: String,
    val message: String?,
    val coverUrl: String?,
    val contentKey: String,
    val actions: List<GlassSheetAction>,
    val onDismiss: () -> Unit,
)

@Composable
fun GlassActionSheet(
    title: String,
    onDismiss: () -> Unit,
    actions: List<GlassSheetAction>,
    message: String? = null,
    coverUrl: String? = null,
    contentKey: String = title,
) {
    val host = LocalGlassActionSheetHost.current ?: return
    val onDismissUpdated = rememberUpdatedState(onDismiss)
    val actionsUpdated = rememberUpdatedState(actions)
    fun currentSpec() = GlassActionSheetSpec(
        title = title,
        message = message,
        coverUrl = coverUrl,
        contentKey = contentKey,
        actions = actionsUpdated.value,
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
internal fun GlassActionSheetOverlay(
    state: GlassActionSheetHostState,
    backdrop: Backdrop,
    landscape: Boolean,
    modifier: Modifier = Modifier,
) {
    val spec = state.spec
    val visible = state.visible
    LaunchedEffect(visible) {
        if (!visible && state.spec != null) {
            delay(200)
            state.finishHide()
        }
    }
    if (spec == null) return

    val screenH = LocalConfiguration.current.screenHeightDp
    val backUi = rememberPredictiveBackUi(enabled = visible, onBack = spec.onDismiss)

    val reveal = remember { Animatable(0f) }
    LaunchedEffect(visible, state.presentSeq) {
        if (visible) {
            reveal.snapTo(0f)
            reveal.animateTo(1f, tween(320, easing = SheetPopEasing))
        } else {
            reveal.animateTo(0f, tween(160, easing = SheetHideEasing))
        }
    }

    val t = reveal.value * (1f - backUi.progress)
    Box(
        modifier
            .fillMaxSize()
            .graphicsLayer { alpha = t },
        contentAlignment = Alignment.Center,
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
        Column(
            Modifier
                .padding(horizontal = if (landscape) 48.dp else 28.dp)
                .widthIn(min = 300.dp, max = if (landscape) 400.dp else 360.dp)
                .then(
                    if (landscape) Modifier.heightIn(max = (screenH * 0.82f).dp) else Modifier,
                )
                .fillMaxWidth()
                .graphicsLayer {
                    val s = 0.92f + 0.08f * t
                    scaleX = s
                    scaleY = s
                }
                .dialogLiquidGlass(backdrop, SheetShape)
                .clip(SheetShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
        ) {
            Spacer(Modifier.height(22.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                UrlImage(
                    url = spec.coverUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(10.dp)),
                    contentScale = ContentScale.Crop,
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = spec.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = TextStyle(
                            color = MainPalette.Ink,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = (-0.2).sp,
                        ),
                    )
                    if (!spec.message.isNullOrBlank()) {
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = spec.message,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = TextStyle(
                                color = MainPalette.Secondary,
                                fontSize = 13.sp,
                            ),
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            val withCovers = spec.actions.any { it.showCover }
            Column(
                Modifier
                    .heightIn(max = if (landscape) 200.dp else 420.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                spec.actions.forEach { action ->
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(0.5.dp)
                            .background(Color.Black.copy(alpha = 0.10f)),
                    )
                    if (withCovers || action.showCover) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .height(58.dp)
                                .clickable(onClick = action.onClick)
                                .padding(horizontal = 22.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            UrlImage(
                                url = action.coverUrl,
                                contentDescription = action.label,
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop,
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                action.label,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = TextStyle(
                                    color = if (action.destructive) {
                                        MainPalette.Accent
                                    } else {
                                        MainPalette.Ink
                                    },
                                    fontSize = 16.sp,
                                    fontWeight = if (action.destructive) {
                                        FontWeight.SemiBold
                                    } else {
                                        FontWeight.Medium
                                    },
                                ),
                            )
                        }
                    } else {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                                .clickable(onClick = action.onClick),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                action.label,
                                style = TextStyle(
                                    color = if (action.destructive) {
                                        MainPalette.Accent
                                    } else {
                                        MainPalette.Ink
                                    },
                                    fontSize = 18.sp,
                                    fontWeight = if (action.destructive) {
                                        FontWeight.SemiBold
                                    } else {
                                        FontWeight.Medium
                                    },
                                ),
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}
