package com.kite.zmusic.ui.common

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kite.zmusic.ui.main.MainPalette
import com.kite.zmusic.ui.main.dialogLiquidGlass
import com.kyant.backdrop.Backdrop
import kotlinx.coroutines.delay

private val AlertShape = RoundedCornerShape(32.dp)
private val AlertPopEasing = CubicBezierEasing(0.16f, 1.12f, 0.28f, 1f)
private val AlertHideEasing = CubicBezierEasing(0.4f, 0.02f, 0.2f, 1f)

internal val LocalGlassAlertHost = staticCompositionLocalOf<GlassAlertHostState?> { null }

@Stable
internal class GlassAlertHostState {
    var spec by mutableStateOf<GlassAlertSpec?>(null)
        private set
    var visible by mutableStateOf(false)
        private set

    fun present(spec: GlassAlertSpec) {
        this.spec = spec
        visible = true
    }

    fun update(spec: GlassAlertSpec) {
        if (visible) this.spec = spec
    }

    fun hide() {
        visible = false
    }

    fun finishHide() {
        if (!visible) spec = null
    }
}

internal data class GlassAlertSpec(
    val title: String,
    val message: String?,
    val confirmLabel: String,
    val cancelLabel: String?,
    val confirmDestructive: Boolean,
    val confirmEnabled: Boolean,
    val onConfirm: () -> Unit,
    val onDismiss: () -> Unit,
    val extraContent: (@Composable ColumnScope.() -> Unit)?,
)

/**
 * 二次确认弹窗（取消收藏、退出登录、登录同意条款等）。
 *
 * 画在 [com.kite.zmusic.ui.notice.IslandNoticeRoot] 记录层之外，与 Dock / 灵动岛同窗采样液体玻璃，
 * **不要**用系统 Dialog：另开窗口会让状态栏变黑，且不能画 Kyant Backdrop。
 */
@Composable
fun GlassAlertDialog(
    title: String,
    onDismiss: () -> Unit,
    confirmLabel: String,
    onConfirm: () -> Unit,
    message: String? = null,
    cancelLabel: String? = "取消",
    confirmDestructive: Boolean = false,
    confirmEnabled: Boolean = true,
    extraContent: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val host = LocalGlassAlertHost.current ?: return
    val onConfirmUpdated = rememberUpdatedState(onConfirm)
    val onDismissUpdated = rememberUpdatedState(onDismiss)
    val extraUpdated = rememberUpdatedState(extraContent)
    fun currentSpec() = GlassAlertSpec(
        title = title,
        message = message,
        confirmLabel = confirmLabel,
        cancelLabel = cancelLabel,
        confirmDestructive = confirmDestructive,
        confirmEnabled = confirmEnabled,
        onConfirm = { onConfirmUpdated.value() },
        onDismiss = { onDismissUpdated.value() },
        extraContent = extraUpdated.value,
    )
    DisposableEffect(host, title, message, confirmLabel, cancelLabel, confirmDestructive) {
        host.present(currentSpec())
        onDispose { host.hide() }
    }
    SideEffect {
        host.update(currentSpec())
    }
}

@Composable
internal fun GlassAlertOverlay(
    state: GlassAlertHostState,
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

    BackHandler(enabled = visible) { spec.onDismiss() }

    val reveal = remember { Animatable(0f) }
    LaunchedEffect(visible) {
        if (visible) {
            reveal.snapTo(0f)
            reveal.animateTo(1f, tween(320, easing = AlertPopEasing))
        } else {
            reveal.animateTo(0f, tween(160, easing = AlertHideEasing))
        }
    }

    val t = reveal.value
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
        GlassAlertCard(
            spec = spec,
            backdrop = backdrop,
            landscape = landscape,
            modifier = Modifier
                .imePadding()
                .graphicsLayer {
                    val s = 0.92f + 0.08f * t
                    scaleX = s
                    scaleY = s
                },
        )
    }
}

@Composable
private fun GlassAlertCard(
    spec: GlassAlertSpec,
    backdrop: Backdrop,
    landscape: Boolean,
    modifier: Modifier = Modifier,
) {
    val screenH = LocalConfiguration.current.screenHeightDp
    val extraMax = (screenH * 0.40f).dp
    Column(
        modifier
            .padding(horizontal = if (landscape) 48.dp else 28.dp)
            .widthIn(min = 300.dp, max = if (landscape) 400.dp else 360.dp)
            .then(
                if (landscape) Modifier.heightIn(max = (screenH * 0.82f).dp) else Modifier,
            )
            .fillMaxWidth()
            .dialogLiquidGlass(backdrop, AlertShape)
            .clip(AlertShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            ),
    ) {
        Spacer(Modifier.height(if (landscape) 18.dp else 28.dp))
        Text(
            text = spec.title,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            style = TextStyle(
                color = MainPalette.Ink,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                letterSpacing = (-0.3).sp,
                lineHeight = 26.sp,
            ),
        )
        if (!spec.message.isNullOrBlank()) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = spec.message,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                style = TextStyle(
                    color = MainPalette.Secondary,
                    fontSize = 15.sp,
                    lineHeight = 21.sp,
                    textAlign = TextAlign.Center,
                ),
            )
        }
        spec.extraContent?.let { extra ->
            Spacer(Modifier.height(12.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .then(
                        if (landscape) {
                            Modifier
                                .heightIn(max = extraMax)
                                .verticalScroll(rememberScrollState())
                        } else {
                            Modifier
                        },
                    ),
                content = extra,
            )
        }
        Spacer(Modifier.height(if (landscape) 14.dp else 22.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .background(Color.Black.copy(alpha = 0.10f)),
        )
        val cancel = spec.cancelLabel
        if (cancel.isNullOrBlank()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clickable(
                        enabled = spec.confirmEnabled,
                        onClick = spec.onConfirm,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    spec.confirmLabel,
                    style = TextStyle(
                        color = MainPalette.Accent.copy(
                            alpha = if (spec.confirmEnabled) 1f else 0.38f,
                        ),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                )
            }
        } else {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            ) {
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .clickable(onClick = spec.onDismiss),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        cancel,
                        style = TextStyle(
                            color = MainPalette.Ink,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                    )
                }
                Box(
                    Modifier
                        .width(0.5.dp)
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.10f)),
                )
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .clickable(
                            enabled = spec.confirmEnabled,
                            onClick = spec.onConfirm,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        spec.confirmLabel,
                        style = TextStyle(
                            color = MainPalette.Accent.copy(
                                alpha = if (spec.confirmEnabled) 1f else 0.38f,
                            ),
                            fontSize = 18.sp,
                            fontWeight = if (spec.confirmDestructive) {
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
}

@Composable
fun GlassPromptField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    maxLength: Int = 40,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    BasicTextField(
        value = value,
        onValueChange = { onValueChange(it.take(maxLength)) },
        singleLine = true,
        keyboardOptions = keyboardOptions,
        textStyle = TextStyle(
            color = MainPalette.Ink,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
        ),
        cursorBrush = SolidColor(MainPalette.Accent),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.62f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        color = MainPalette.Hint,
                        fontSize = 16.sp,
                    )
                }
                inner()
            }
        },
    )
}
