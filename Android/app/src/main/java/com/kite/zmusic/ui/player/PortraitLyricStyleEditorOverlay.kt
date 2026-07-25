package com.kite.zmusic.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kite.zmusic.data.LyricRoleStyle
import com.kite.zmusic.data.PlayerDisplayPrefs
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import kotlin.math.roundToInt

private val EditorLabel = Color(0xFFFFFFFF)
private val EditorHint = Color(0xFFE8F0F8)
private val EditorAccent = Color(0xFF9AF0F0)
private val EditorRowBg = Color(0xF0141A24)
private val EditorBorder = Color(0xFF9AA3AD).copy(alpha = 0.55f)

private val EditorGlassStyle = HazeStyle(
    backgroundColor = Color(0xFF03060A),
    tints = listOf(
        HazeTint(Color(0xFF070B12).copy(alpha = 0.22f)),
        HazeTint(Color.Black.copy(alpha = 0.14f)),
    ),
    blurRadius = 72.dp,
    noiseFactor = 0.10f,
    fallbackTint = HazeTint(Color(0x6605080E)),
)

/** 竖屏全屏歌词样式：慢启软落，进出场对称可预测 */
internal val PortraitLyricStylePanelEasing = CubicBezierEasing(0.33f, 0.0f, 0.2f, 1f)

private val PanelShape = RoundedCornerShape(22.dp)
private val PreviewSlotShape = RoundedCornerShape(14.dp)

private val PanelPadH = 16.dp
private val PanelPadTopBelowStatus = 8.dp
private val HeaderHeight = 44.dp
private val PreviewMinHeight = 168.dp
private val OpsPreviewGap = 12.dp

/**
 * 竖屏全屏歌词样式弹窗展开后，预览槽相对播放页根的静止几何。
 * 克隆 morph 必须以此为终点，避免跟动画中 bounds。
 */
fun portraitLyricStyleRestPreviewSlot(
    screenWidth: Dp,
    screenHeight: Dp,
    statusTop: Dp,
    navBottom: Dp,
): LyricStylePreviewSlot {
    val contentTop = statusTop + PanelPadTopBelowStatus + HeaderHeight + 8.dp
    val contentBottom = screenHeight - navBottom - 12.dp
    val available = (contentBottom - contentTop).coerceAtLeast(PreviewMinHeight + 120.dp)
    val previewH = (available * 0.36f).coerceIn(PreviewMinHeight, available * 0.46f)
    return LyricStylePreviewSlot(
        left = PanelPadH,
        top = contentTop,
        width = (screenWidth - PanelPadH * 2).coerceAtLeast(120.dp),
        height = previewH,
    )
}

/**
 * 竖屏歌词样式全屏弹窗：气质对齐设置 / 自定义背景；
 * 上预览槽（克隆落点）+ 下操作区；歌词穿透由 [LyricStyleCloneLayer] 承担。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortraitLyricStyleEditorOverlay(
    progress: Float,
    draftPlaying: LyricRoleStyle,
    draftPlayed: LyricRoleStyle,
    draftUnplayed: LyricRoleStyle,
    draftPlayedCount: Int,
    draftUpcomingCount: Int,
    draftLineSpacingDp: Float,
    onDraftPlayingChange: (LyricRoleStyle) -> Unit,
    onDraftPlayedChange: (LyricRoleStyle) -> Unit,
    onDraftUnplayedChange: (LyricRoleStyle) -> Unit,
    onDraftPlayedCountChange: (Int) -> Unit,
    onDraftUpcomingCountChange: (Int) -> Unit,
    onDraftLineSpacingChange: (Float) -> Unit,
    hazeState: HazeState,
    onDismiss: () -> Unit,
    onBackToSettings: () -> Unit,
    modifier: Modifier = Modifier,
    @Suppress("UNUSED_PARAMETER") hazeNonce: Int = 0,
) {
    val t = progress.coerceIn(0f, 1f)
    if (t <= 0.001f) return

    BackHandler(enabled = t > 0.02f) { onBackToSettings() }

    val switchColors = SwitchDefaults.colors(
        checkedThumbColor = Color(0xFFF8FAFC),
        checkedTrackColor = EditorAccent.copy(alpha = 0.62f),
        uncheckedThumbColor = Color(0xFFD0D8E2),
        uncheckedTrackColor = Color.White.copy(alpha = 0.16f),
        uncheckedBorderColor = Color.White.copy(alpha = 0.14f),
        checkedBorderColor = Color.Transparent,
    )
    val sliderColors = SliderDefaults.colors(
        thumbColor = Color(0xFFF8FAFC),
        activeTrackColor = EditorAccent.copy(alpha = 0.62f),
        inactiveTrackColor = Color.White.copy(alpha = 0.16f),
        activeTickColor = Color.Transparent,
        inactiveTickColor = Color.Transparent,
    )

    val statusPad = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navPad = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(modifier.fillMaxSize()) {
        // 全屏遮罩：淡入；点击空白关闭（优雅离场由外层 progress 驱动）
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = t }
                .background(Color(0xE603060A))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
        )

        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .padding(
                    start = PanelPadH,
                    end = PanelPadH,
                    top = statusPad + PanelPadTopBelowStatus,
                    bottom = navPad + 10.dp,
                )
                .graphicsLayer {
                    // 自下轻抬 + 淡入，与自定义背景同气质，行程更克制
                    translationY = (1f - t) * 36f
                    alpha = t
                },
        ) {
            val previewSlot = portraitLyricStyleRestPreviewSlot(
                screenWidth = maxWidth + PanelPadH * 2,
                screenHeight = maxHeight + statusPad + PanelPadTopBelowStatus + navPad + 10.dp,
                statusTop = statusPad,
                navBottom = navPad,
            )
            // 预览槽高度与静止几何一致（本 Box 已扣状态栏/导航，用本地高度）
            val localPreviewH = previewSlot.height

            Box(
                Modifier
                    .fillMaxSize()
                    .clip(PanelShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    ),
            ) {
                Box(
                    Modifier
                        .matchParentSize()
                        .hazeEffect(state = hazeState, style = EditorGlassStyle) {
                            blurRadius = 72.dp
                            noiseFactor = 0.10f
                        },
                )
                Box(
                    Modifier
                        .matchParentSize()
                        .background(Color(0x9905080E)),
                )
                Box(
                    Modifier
                        .matchParentSize()
                        .border(1.dp, EditorBorder, PanelShape),
                )

                Column(Modifier.fillMaxSize()) {
                    // 顶栏：返回设置
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(HeaderHeight)
                            .padding(horizontal = 6.dp),
                    ) {
                        Box(
                            Modifier
                                .align(Alignment.CenterStart)
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = onBackToSettings,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Canvas(Modifier.size(18.dp)) {
                                val stroke = Stroke(width = 2.2f, cap = StrokeCap.Round)
                                val cx = size.width * 0.58f
                                val cy = size.height / 2f
                                val arm = size.minDimension * 0.28f
                                drawLine(
                                    color = Color.White.copy(alpha = 0.92f),
                                    start = Offset(cx, cy - arm),
                                    end = Offset(cx - arm * 1.15f, cy),
                                    strokeWidth = stroke.width,
                                    cap = StrokeCap.Round,
                                )
                                drawLine(
                                    color = Color.White.copy(alpha = 0.92f),
                                    start = Offset(cx - arm * 1.15f, cy),
                                    end = Offset(cx, cy + arm),
                                    strokeWidth = stroke.width,
                                    cap = StrokeCap.Round,
                                )
                            }
                        }
                        Column(
                            Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = "LYRIC STYLE",
                                style = TextStyle(
                                    color = EditorAccent.copy(alpha = 0.75f),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 9.sp,
                                    letterSpacing = 2.sp,
                                ),
                            )
                            Text(
                                text = "歌词样式",
                                style = TextStyle(
                                    color = EditorLabel,
                                    fontFamily = FontFamily.SansSerif,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.sp,
                                ),
                            )
                        }
                    }

                    // 预览槽：克隆落点（空框）
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp)
                            .height(localPreviewH)
                            .clip(PreviewSlotShape)
                            .background(Color(0x66070B12))
                            .border(
                                width = 1.dp,
                                color = Color.White.copy(alpha = 0.10f),
                                shape = PreviewSlotShape,
                            ),
                    )

                    Spacer(Modifier.height(OpsPreviewGap))

                    Column(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Text(
                            text = "显示与间距 · 关闭后应用",
                            style = TextStyle(
                                color = EditorHint.copy(alpha = 0.72f),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp,
                                letterSpacing = 0.3.sp,
                            ),
                        )

                        PortraitLyricLayoutSection(
                            playedCount = draftPlayedCount,
                            upcomingCount = draftUpcomingCount,
                            lineSpacingDp = draftLineSpacingDp,
                            sliderColors = sliderColors,
                            onPlayedCountChange = onDraftPlayedCountChange,
                            onUpcomingCountChange = onDraftUpcomingCountChange,
                            onLineSpacingChange = onDraftLineSpacingChange,
                        )

                        LyricRoleStyleSection(
                            title = "播放中歌词",
                            role = LyricStyleRole.Playing,
                            style = draftPlaying,
                            switchColors = switchColors,
                            onChange = onDraftPlayingChange,
                        )
                        LyricRoleStyleSection(
                            title = "已播放歌词",
                            role = LyricStyleRole.Played,
                            style = draftPlayed,
                            switchColors = switchColors,
                            onChange = onDraftPlayedChange,
                        )
                        LyricRoleStyleSection(
                            title = "未播放歌词",
                            role = LyricStyleRole.Unplayed,
                            style = draftUnplayed,
                            switchColors = switchColors,
                            onChange = onDraftUnplayedChange,
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PortraitLyricLayoutSection(
    playedCount: Int,
    upcomingCount: Int,
    lineSpacingDp: Float,
    sliderColors: androidx.compose.material3.SliderColors,
    onPlayedCountChange: (Int) -> Unit,
    onUpcomingCountChange: (Int) -> Unit,
    onLineSpacingChange: (Float) -> Unit,
) {
    val played = playedCount.coerceIn(
        PlayerDisplayPrefs.LYRIC_AROUND_MIN,
        PlayerDisplayPrefs.PORTRAIT_LYRIC_AROUND_MAX,
    )
    val upcoming = upcomingCount.coerceIn(
        PlayerDisplayPrefs.LYRIC_AROUND_MIN,
        PlayerDisplayPrefs.PORTRAIT_LYRIC_AROUND_MAX,
    )
    val spacing = lineSpacingDp.coerceIn(
        PlayerDisplayPrefs.LINE_SPACING_MIN,
        PlayerDisplayPrefs.LINE_SPACING_MAX,
    )
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(EditorRowBg)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "通用布局",
            style = TextStyle(
                color = EditorLabel,
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
            ),
        )
        PortraitLayoutSliderRow(
            title = "已播放歌词数",
            valueLabel = played.toString(),
            value = played.toFloat(),
            valueRange = PlayerDisplayPrefs.LYRIC_AROUND_MIN.toFloat()..
                PlayerDisplayPrefs.PORTRAIT_LYRIC_AROUND_MAX.toFloat(),
            steps = PlayerDisplayPrefs.PORTRAIT_LYRIC_AROUND_MAX -
                PlayerDisplayPrefs.LYRIC_AROUND_MIN - 1,
            colors = sliderColors,
            onValueChange = {
                onPlayedCountChange(
                    it.roundToInt().coerceIn(
                        PlayerDisplayPrefs.LYRIC_AROUND_MIN,
                        PlayerDisplayPrefs.PORTRAIT_LYRIC_AROUND_MAX,
                    ),
                )
            },
        )
        PortraitLayoutSliderRow(
            title = "未播放歌词数",
            valueLabel = upcoming.toString(),
            value = upcoming.toFloat(),
            valueRange = PlayerDisplayPrefs.LYRIC_AROUND_MIN.toFloat()..
                PlayerDisplayPrefs.PORTRAIT_LYRIC_AROUND_MAX.toFloat(),
            steps = PlayerDisplayPrefs.PORTRAIT_LYRIC_AROUND_MAX -
                PlayerDisplayPrefs.LYRIC_AROUND_MIN - 1,
            colors = sliderColors,
            onValueChange = {
                onUpcomingCountChange(
                    it.roundToInt().coerceIn(
                        PlayerDisplayPrefs.LYRIC_AROUND_MIN,
                        PlayerDisplayPrefs.PORTRAIT_LYRIC_AROUND_MAX,
                    ),
                )
            },
        )
        PortraitLayoutSliderRow(
            title = "歌词行间距",
            valueLabel = String.format("%.0f", spacing),
            value = spacing,
            valueRange = PlayerDisplayPrefs.LINE_SPACING_MIN..PlayerDisplayPrefs.LINE_SPACING_MAX,
            steps = 0,
            colors = sliderColors,
            onValueChange = onLineSpacingChange,
        )
        Text(
            text = "播放中歌词始终垂直居中",
            style = TextStyle(
                color = EditorHint.copy(alpha = 0.62f),
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                letterSpacing = 0.2.sp,
            ),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PortraitLayoutSliderRow(
    title: String,
    valueLabel: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    colors: androidx.compose.material3.SliderColors,
    onValueChange: (Float) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = TextStyle(
                    color = EditorHint.copy(alpha = 0.85f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    letterSpacing = 0.4.sp,
                ),
                modifier = Modifier.weight(1f),
            )
            Text(
                text = valueLabel,
                style = TextStyle(
                    color = EditorAccent.copy(alpha = 0.95f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                ),
            )
        }
        Slider(
            value = value.coerceIn(valueRange.start, valueRange.endInclusive),
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            colors = colors,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
