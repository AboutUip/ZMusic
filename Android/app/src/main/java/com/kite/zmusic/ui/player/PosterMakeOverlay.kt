package com.kite.zmusic.ui.player

import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.kite.zmusic.data.LrcLine
import com.kite.zmusic.data.TrackRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

private val PosterPanelCurve = CubicBezierEasing(0.33f, 0f, 0.2f, 1f)
private val PosterAccent = Color(0xFFD4C4A8)
private val PosterLabel = Color(0xFFF4F0E8)
private val PosterHint = Color(0xFFB8C0CC)
private val PosterDim = Color(0xFF7A8899)
private val PosterBtnBg = Color.Black.copy(alpha = 0.55f)
private val PosterBtnDisabled = Color.White.copy(alpha = 0.14f)
private const val PosterMaxLyricLines = 6

private enum class PosterWizardStep {
    LyricPick,
    PresetPick,
    Edit,
}

/**
 * 竖屏「制作海报」全屏向导：选歌词 → 选预设 → 编辑/导出。
 * 进度动画对齐歌词样式面板；取消/完成后由 [onDismiss] 关闭整树（可回封面）。
 */
@Composable
fun PosterMakeOverlay(
    open: Boolean,
    track: TrackRow,
    lines: List<LrcLine>,
    frozenPositionMs: Long,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(open) {
        progress.animateTo(
            if (open) 1f else 0f,
            animationSpec = tween(
                durationMillis = if (open) 920 else 780,
                easing = PosterPanelCurve,
            ),
        )
    }
    if (progress.value <= 0.001f && !open) return

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val t = progress.value

    var step by remember { mutableStateOf(PosterWizardStep.LyricPick) }
    var selectedIndices by remember { mutableStateOf(linkedSetOf<Int>()) }
    var presetId by remember { mutableStateOf<String?>(null) }
    var showTime by remember { mutableStateOf(true) }
    var coverTone by remember { mutableStateOf(PosterCoverTone.Dark) }
    var signature by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var renderedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var showRenderedPreview by remember { mutableStateOf(false) }
    var hint by remember { mutableStateOf<String?>(null) }
    val timeText = rememberPosterTimeText()

    LaunchedEffect(open) {
        if (open) {
            step = PosterWizardStep.LyricPick
            selectedIndices = linkedSetOf()
            presetId = null
            showTime = true
            coverTone = PosterCoverTone.Dark
            signature = ""
            busy = false
            renderedBitmap?.recycle()
            renderedBitmap = null
            showRenderedPreview = false
            hint = null
        }
    }

    LaunchedEffect(hint) {
        val msg = hint ?: return@LaunchedEffect
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        hint = null
    }

    fun closeAll() {
        showRenderedPreview = false
        onDismiss()
    }

    fun clearRendered() {
        val prev = renderedBitmap
        renderedBitmap = null
        showRenderedPreview = false
        prev?.recycle()
    }

    BackHandler(enabled = open && progress.value > 0.05f) {
        when {
            showRenderedPreview -> showRenderedPreview = false
            step == PosterWizardStep.Edit -> step = PosterWizardStep.PresetPick
            step == PosterWizardStep.PresetPick -> step = PosterWizardStep.LyricPick
            else -> closeAll()
        }
    }

    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val selectedLyrics = remember(selectedIndices, lines) {
        selectedIndices.sorted().mapNotNull { i -> lines.getOrNull(i)?.text?.trim()?.takeIf { it.isNotEmpty() } }
    }

    Box(
        modifier
            .fillMaxSize()
            .zIndex(40f)
            .graphicsLayer { alpha = t },
    ) {
        // 近乎不透明底板：避免透出播放页导致「发灰/发虚」
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0xF2080A0E))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { /* absorb */ },
                ),
        )

        Column(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    // 歌词快照渗透感：面板自下抬入，内容稍后淡入
                    translationY = (1f - t) * 48f
                    transformOrigin = TransformOrigin(0.5f, 1f)
                }
                .padding(top = statusTop),
        ) {
            PosterWizardHeader(
                step = step,
                selectedCount = selectedIndices.size,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 10.dp),
            )

            AnimatedContent(
                targetState = step,
                transitionSpec = {
                    val forward = targetState.ordinal > initialState.ordinal
                    val enter = slideInHorizontally(
                        animationSpec = tween(520, easing = PosterPanelCurve),
                        initialOffsetX = { if (forward) it / 5 else -it / 5 },
                    ) + fadeIn(tween(420, easing = PosterPanelCurve))
                    val exit = slideOutHorizontally(
                        animationSpec = tween(480, easing = PosterPanelCurve),
                        targetOffsetX = { if (forward) -it / 6 else it / 6 },
                    ) + fadeOut(tween(360, easing = PosterPanelCurve))
                    enter togetherWith exit
                },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                label = "posterStep",
            ) { current ->
                when (current) {
                    PosterWizardStep.LyricPick -> PosterLyricPickStep(
                        lines = lines,
                        frozenPositionMs = frozenPositionMs,
                        selectedIndices = selectedIndices,
                        panelProgress = t,
                        onToggle = { index ->
                            val next = LinkedHashSet(selectedIndices)
                            if (index in next) {
                                next.remove(index)
                            } else if (next.size >= PosterMaxLyricLines) {
                                hint = "最多选择 ${PosterMaxLyricLines} 行"
                                return@PosterLyricPickStep
                            } else {
                                next.add(index)
                            }
                            selectedIndices = next
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                    PosterWizardStep.PresetPick -> PosterPresetPickStep(
                        selectedId = presetId,
                        onSelect = { presetId = it },
                        modifier = Modifier.fillMaxSize(),
                    )
                    PosterWizardStep.Edit -> PosterEditStep(
                        track = track,
                        lyricLines = selectedLyrics,
                        showTime = showTime,
                        onShowTimeChange = {
                            showTime = it
                            clearRendered()
                        },
                        coverTone = coverTone,
                        onCoverToneChange = {
                            coverTone = it
                            clearRendered()
                        },
                        signature = signature,
                        onSignatureChange = {
                            if (it.length <= 40) {
                                signature = it
                                clearRendered()
                            }
                        },
                        timeText = timeText,
                        renderedReady = renderedBitmap != null,
                        onViewRendered = {
                            if (renderedBitmap != null) showRenderedPreview = true
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            PosterWizardBottomBar(
                step = step,
                canNextFromLyrics = selectedIndices.isNotEmpty(),
                canContinuePreset = presetId != null,
                busy = busy,
                navBottom = navBottom,
                onCancel = { closeAll() },
                onBack = {
                    when (step) {
                        PosterWizardStep.PresetPick -> step = PosterWizardStep.LyricPick
                        PosterWizardStep.Edit -> step = PosterWizardStep.PresetPick
                        else -> closeAll()
                    }
                },
                onNextLyrics = {
                    if (selectedIndices.isEmpty()) {
                        hint = "请先选择歌词"
                        return@PosterWizardBottomBar
                    }
                    step = PosterWizardStep.PresetPick
                },
                onContinuePreset = {
                    if (presetId == null) {
                        hint = "请先选择预设"
                        return@PosterWizardBottomBar
                    }
                    step = PosterWizardStep.Edit
                },
                onRender = {
                    if (busy) return@PosterWizardBottomBar
                    busy = true
                    scope.launch {
                        val bmp = runCatching {
                            renderPosterCoverPresetBitmap(
                                context = context,
                                track = track,
                                lyricLines = selectedLyrics,
                                showTime = showTime,
                                signature = signature,
                                tone = coverTone,
                                timeText = timeText,
                            )
                        }.getOrNull()
                        if (bmp != null) {
                            val prev = renderedBitmap
                            renderedBitmap = bmp
                            prev?.recycle()
                            busy = false
                            hint = "已渲染，可点查看"
                        } else {
                            busy = false
                            hint = "渲染失败，请重试"
                        }
                    }
                },
                onSave = {
                    if (busy) return@PosterWizardBottomBar
                    busy = true
                    scope.launch {
                        val bmp = renderedBitmap ?: runCatching {
                            renderPosterCoverPresetBitmap(
                                context = context,
                                track = track,
                                lyricLines = selectedLyrics,
                                showTime = showTime,
                                signature = signature,
                                tone = coverTone,
                                timeText = timeText,
                            )
                        }.getOrNull()
                        if (bmp == null) {
                            busy = false
                            hint = "保存失败，请重试"
                            return@launch
                        }
                        val result = withContext(Dispatchers.IO) {
                            PlayerDisplayQr.saveToGallery(
                                context,
                                bmp,
                                "ZMusic_poster_${System.currentTimeMillis()}.png",
                            )
                        }
                        busy = false
                        result.onSuccess {
                            hint = "已保存到相册"
                            closeAll()
                        }.onFailure {
                            hint = "保存失败，请重试"
                        }
                    }
                },
                onDisabledHint = { msg -> hint = msg },
            )
        }

        if (showRenderedPreview && renderedBitmap != null) {
            PosterRenderedFullscreen(
                bitmap = renderedBitmap!!,
                onClose = { showRenderedPreview = false },
            )
        }
    }
}

@Composable
private fun PosterWizardHeader(
    step: PosterWizardStep,
    selectedCount: Int,
    modifier: Modifier = Modifier,
) {
    val title = when (step) {
        PosterWizardStep.LyricPick -> "选择歌词"
        PosterWizardStep.PresetPick -> "选择预设"
        PosterWizardStep.Edit -> "编辑海报"
    }
    val sub = when (step) {
        PosterWizardStep.LyricPick ->
            if (selectedCount == 0) "可跨行选择，最多 $PosterMaxLyricLines 行"
            else "已选 $selectedCount / $PosterMaxLyricLines"
        PosterWizardStep.PresetPick -> "挑选海报版式"
        PosterWizardStep.Edit -> "实时预览 · 保存导出"
    }
    Column(modifier) {
        Text(
            text = title,
            style = TextStyle(
                color = PosterLabel,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.4.sp,
            ),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = sub,
            style = TextStyle(
                color = PosterHint.copy(alpha = 0.72f),
                fontSize = 12.sp,
            ),
        )
    }
}

@Composable
private fun PosterLyricPickStep(
    lines: List<LrcLine>,
    frozenPositionMs: Long,
    selectedIndices: Set<Int>,
    panelProgress: Float,
    onToggle: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 剥离播放态：冻结时刻仅作初始定位；固定行高 + 矩形选中底（对齐横屏选句）
    val focus = remember(lines, frozenPositionMs) {
        if (lines.isEmpty()) 0
        else lines.indexOfLast { it.timeMs <= frozenPositionMs }.coerceAtLeast(0)
    }
    val listState = rememberLazyListState()
    val rowHeight = 48.dp
    val selectedBg = Color.White.copy(alpha = 0.22f)
    val selectedText = Color(0xFFFFFFFF)
    val normalText = Color(0xFFB8C0CC).copy(alpha = 0.88f)
    // 面板抬入后，歌词列表再淡入上移 —— 模拟「快照渗入弹窗」
    val contentT = ((panelProgress - 0.22f) / 0.55f).coerceIn(0f, 1f)

    LaunchedEffect(focus, lines.size) {
        if (lines.isNotEmpty()) {
            delay(160)
            runCatching {
                listState.scrollToItem(focus.coerceIn(0, lines.lastIndex))
            }
        }
    }

    if (lines.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "暂无逐行歌词",
                style = TextStyle(color = PosterDim, fontSize = 14.sp),
            )
        }
        return
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                alpha = contentT
                translationY = (1f - contentT) * 28f
            },
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        // 无间距：相邻选中拼成连续矩形块
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        itemsIndexed(
            lines,
            key = { index, line -> "${index}_${line.timeMs}" },
        ) { index, line ->
            val selected = index in selectedIndices
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(rowHeight)
                    .background(if (selected) selectedBg else Color.Transparent)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onToggle(index) },
                    )
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = line.text.ifBlank { " " },
                    style = TextStyle(
                        color = if (selected) selectedText else normalText,
                        fontSize = 15.sp,
                        fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                        textAlign = TextAlign.Center,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun PosterPresetPickStep(
    selectedId: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 单列弹性比例卡片；日后可改为 LazyVerticalStaggeredGrid 瀑布流
    Column(
        modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        PosterPresetCatalog.forEach { preset ->
            val selected = selectedId == preset.id
            val shape = RoundedCornerShape(16.dp)
            Column(
                Modifier
                    .fillMaxWidth(0.72f)
                    .align(Alignment.CenterHorizontally)
                    .clip(shape)
                    .background(
                        if (selected) Color(0xFF1A1E26) else Color(0xFF12151C),
                    )
                    .border(
                        width = 1.5.dp,
                        color = if (selected) PosterAccent.copy(alpha = 0.85f)
                        else Color.White.copy(alpha = 0.10f),
                        shape = shape,
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onSelect(preset.id) },
                    )
                    .padding(10.dp),
            ) {
                when (preset.id) {
                    PosterPresetCoverId -> PosterSongCoverStyleThumb(
                        modifier = Modifier.fillMaxWidth(),
                        aspectRatio = preset.thumbAspectRatio,
                    )
                    else -> {
                        val ratio = preset.thumbAspectRatio.coerceIn(0.55f, 1.35f)
                        BoxWithConstraints(Modifier.fillMaxWidth()) {
                            val thumbH = maxWidth / ratio
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(thumbH)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF1C2028)),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = preset.title,
                    style = TextStyle(
                        color = PosterLabel,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = preset.description,
                    style = TextStyle(
                        color = PosterHint.copy(alpha = 0.78f),
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                    ),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                if (selected) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "已选择",
                        style = TextStyle(
                            color = PosterAccent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun PosterEditStep(
    track: TrackRow,
    lyricLines: List<String>,
    showTime: Boolean,
    onShowTimeChange: (Boolean) -> Unit,
    coverTone: PosterCoverTone,
    onCoverToneChange: (PosterCoverTone) -> Unit,
    signature: String,
    onSignatureChange: (String) -> Unit,
    timeText: String,
    renderedReady: Boolean,
    onViewRendered: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
    ) {
        BoxWithConstraints(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            // 避免 aspectRatio 在矮屏上撑破 maxHeight 崩溃
            val maxCardH = maxHeight * 0.92f
            val maxCardW = maxWidth * 0.86f
            val byWidthH = maxCardW * 1.5f
            val cardH = if (byWidthH <= maxCardH) byWidthH else maxCardH
            val cardW = cardH * (2f / 3f)
            PosterCoverPresetCard(
                track = track,
                lyricLines = lyricLines,
                showTime = showTime,
                signature = signature,
                tone = coverTone,
                timeText = timeText,
                modifier = Modifier.size(cardW, cardH),
            )
        }

        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xF0141820))
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "色调",
                        style = TextStyle(
                            color = PosterLabel,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                    )
                    Text(
                        text = "深色沉浸 / 浅色纸感",
                        style = TextStyle(
                            color = PosterHint.copy(alpha = 0.65f),
                            fontSize = 11.sp,
                        ),
                    )
                }
                PosterToneSegment(
                    tone = coverTone,
                    onChange = onCoverToneChange,
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "显示时间",
                        style = TextStyle(
                            color = PosterLabel,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                    )
                    Text(
                        text = "人类可读：如 $timeText",
                        style = TextStyle(
                            color = PosterHint.copy(alpha = 0.65f),
                            fontSize = 11.sp,
                        ),
                    )
                }
                Switch(
                    checked = showTime,
                    onCheckedChange = onShowTimeChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFFF8FAFC),
                        checkedTrackColor = PosterAccent.copy(alpha = 0.62f),
                        uncheckedThumbColor = Color(0xFFD0D8E2),
                        uncheckedTrackColor = Color.White.copy(alpha = 0.16f),
                        uncheckedBorderColor = Color.White.copy(alpha = 0.14f),
                        checkedBorderColor = Color.Transparent,
                    ),
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = "个性签名（可选）",
                style = TextStyle(
                    color = PosterLabel,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                ),
            )
            Spacer(Modifier.height(8.dp))
            BasicTextField(
                value = signature,
                onValueChange = onSignatureChange,
                singleLine = true,
                textStyle = TextStyle(
                    color = PosterLabel,
                    fontSize = 14.sp,
                ),
                cursorBrush = SolidColor(PosterAccent),
                decorationBox = { inner ->
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.White.copy(alpha = 0.06f))
                            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        if (signature.isEmpty()) {
                            Text(
                                text = "写下想留下的一句话",
                                style = TextStyle(
                                    color = PosterDim.copy(alpha = 0.7f),
                                    fontSize = 14.sp,
                                ),
                            )
                        }
                        inner()
                    }
                },
            )
            if (renderedReady) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "查看渲染图",
                    style = TextStyle(
                        color = PosterAccent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    modifier = Modifier
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onViewRendered,
                        )
                        .padding(vertical = 4.dp),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun PosterToneSegment(
    tone: PosterCoverTone,
    onChange: (PosterCoverTone) -> Unit,
) {
    val shape = RoundedCornerShape(10.dp)
    Row(
        Modifier
            .clip(shape)
            .background(Color.White.copy(alpha = 0.06f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), shape)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        listOf(
            PosterCoverTone.Dark to "深色",
            PosterCoverTone.Light to "浅色",
        ).forEach { (value, label) ->
            val selected = tone == value
            Box(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (selected) PosterAccent.copy(alpha = 0.88f) else Color.Transparent)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onChange(value) },
                    )
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = TextStyle(
                        color = if (selected) Color(0xFF1A1510) else PosterHint,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                )
            }
        }
    }
}

@Composable
private fun PosterWizardBottomBar(
    step: PosterWizardStep,
    canNextFromLyrics: Boolean,
    canContinuePreset: Boolean,
    busy: Boolean,
    navBottom: androidx.compose.ui.unit.Dp,
    onCancel: () -> Unit,
    onBack: () -> Unit,
    onNextLyrics: () -> Unit,
    onContinuePreset: () -> Unit,
    onRender: () -> Unit,
    onSave: () -> Unit,
    onDisabledHint: (String) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .padding(top = 8.dp, bottom = navBottom + 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (step) {
            PosterWizardStep.LyricPick -> {
                PosterActionButton(
                    text = "取消",
                    enabled = true,
                    primary = false,
                    modifier = Modifier.weight(1f),
                    onClick = onCancel,
                )
                PosterActionButton(
                    text = "下一步",
                    enabled = canNextFromLyrics && !busy,
                    primary = true,
                    modifier = Modifier.weight(1f),
                    onClick = onNextLyrics,
                    onDisabledClick = { onDisabledHint("请先选择歌词") },
                )
            }
            PosterWizardStep.PresetPick -> {
                PosterActionButton(
                    text = "取消",
                    enabled = true,
                    primary = false,
                    modifier = Modifier.weight(1f),
                    onClick = onCancel,
                )
                PosterActionButton(
                    text = "上一步",
                    enabled = true,
                    primary = false,
                    modifier = Modifier.weight(1f),
                    onClick = onBack,
                )
                PosterActionButton(
                    text = "继续",
                    enabled = canContinuePreset && !busy,
                    primary = true,
                    modifier = Modifier.weight(1f),
                    onClick = onContinuePreset,
                    onDisabledClick = { onDisabledHint("请先选择预设") },
                )
            }
            PosterWizardStep.Edit -> {
                PosterActionButton(
                    text = "取消",
                    enabled = !busy,
                    primary = false,
                    compact = true,
                    modifier = Modifier.weight(0.9f),
                    onClick = onCancel,
                )
                PosterActionButton(
                    text = "上一步",
                    enabled = !busy,
                    primary = false,
                    compact = true,
                    modifier = Modifier.weight(0.9f),
                    onClick = onBack,
                )
                PosterActionButton(
                    text = if (busy) "…" else "渲染",
                    enabled = !busy,
                    primary = false,
                    compact = true,
                    modifier = Modifier.weight(0.9f),
                    onClick = onRender,
                )
                PosterActionButton(
                    text = if (busy) "…" else "保存到相册",
                    enabled = !busy,
                    primary = true,
                    compact = true,
                    modifier = Modifier.weight(1.2f),
                    onClick = onSave,
                )
            }
        }
    }
}

@Composable
private fun PosterActionButton(
    text: String,
    enabled: Boolean,
    primary: Boolean,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    onClick: () -> Unit,
    onDisabledClick: (() -> Unit)? = null,
) {
    val bg = when {
        !enabled -> PosterBtnDisabled
        primary -> PosterAccent.copy(alpha = 0.88f)
        else -> PosterBtnBg
    }
    val fg = when {
        !enabled -> Color.White.copy(alpha = 0.35f)
        primary -> Color(0xFF1A1510)
        else -> PosterLabel
    }
    Box(
        modifier
            .heightIn(min = if (compact) 40.dp else 44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {
                    if (enabled) onClick() else onDisabledClick?.invoke()
                },
            )
            .padding(horizontal = if (compact) 8.dp else 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = TextStyle(
                color = fg,
                fontSize = if (compact) 12.sp else 14.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun PosterRenderedFullscreen(
    bitmap: Bitmap,
    onClose: () -> Unit,
) {
    BackHandler { onClose() }
    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }
    val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }

    fun resetTransform() {
        scale = 1f
        offsetX = 0f
        offsetY = 0f
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.96f))
            .zIndex(50f),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .padding(bottom = 56.dp)
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val next = (scale * zoom).coerceIn(0.85f, 6f)
                        // 细粒度：按当前缩放加权平移，放大时拖移更跟手
                        val panFactor = (0.55f + next * 0.45f).coerceIn(0.7f, 2.2f)
                        scale = next
                        if (scale > 1.02f) {
                            offsetX += pan.x * panFactor
                            offsetY += pan.y * panFactor
                            val bounds: IntSize = this.size
                            val maxPan = minOf(bounds.width, bounds.height) * (scale - 1f) * 0.72f
                            offsetX = offsetX.coerceIn(-maxPan, maxPan)
                            offsetY = offsetY.coerceIn(-maxPan, maxPan)
                        } else {
                            offsetX *= 0.85f
                            offsetY *= 0.85f
                            if (abs(offsetX) < 2f) offsetX = 0f
                            if (abs(offsetY) < 2f) offsetY = 0f
                        }
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = { resetTransform() },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Image(
                bitmap = imageBitmap,
                contentDescription = "海报预览",
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .clip(RoundedCornerShape(12.dp))
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offsetX
                        translationY = offsetY
                    },
                contentScale = ContentScale.Fit,
            )
        }

        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "双指缩放 · 拖移 · 双击复位",
                style = TextStyle(color = PosterHint.copy(alpha = 0.72f), fontSize = 12.sp),
            )
            Text(
                text = "关闭",
                style = TextStyle(
                    color = PosterAccent,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClose,
                    )
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}

/**
 * 竖屏底栏「制作海报」图标：相框 + 一角折页，与设置钮同尺寸。
 */
@Composable
fun NowPlayingPosterIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    chromeBackground: Boolean = false,
) {
    val iconSize = if (chromeBackground) 18.dp else 15.dp
    val icon: @Composable () -> Unit = {
        Canvas(Modifier.size(iconSize)) {
            val strokeW = size.minDimension * if (chromeBackground) 0.11f else 0.075f
            val inset = size.minDimension * 0.14f
            val frame = androidx.compose.ui.geometry.Rect(
                inset,
                inset,
                size.width - inset,
                size.height - inset,
            )
            drawRoundRect(
                color = Color(0xFFB8C5D4),
                topLeft = Offset(frame.left, frame.top),
                size = androidx.compose.ui.geometry.Size(frame.width, frame.height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(
                    size.minDimension * 0.12f,
                ),
                style = Stroke(width = strokeW, cap = StrokeCap.Round),
            )
            val fold = size.minDimension * 0.28f
            val path = Path().apply {
                moveTo(frame.right - fold, frame.top)
                lineTo(frame.right, frame.top + fold)
                lineTo(frame.right - fold * 0.15f, frame.top + fold)
                close()
            }
            drawPath(path, color = Color(0xFFB8C5D4))
            // 中央横线示意歌词
            val y1 = size.height * 0.48f
            val y2 = size.height * 0.62f
            val x0 = size.width * 0.28f
            val x1 = size.width * 0.72f
            drawLine(
                Color(0xFFB8C5D4),
                Offset(x0, y1),
                Offset(x1, y1),
                strokeWidth = strokeW * 0.85f,
                cap = StrokeCap.Round,
            )
            drawLine(
                Color(0xFFB8C5D4),
                Offset(x0, y2),
                Offset(x1 * 0.86f, y2),
                strokeWidth = strokeW * 0.85f,
                cap = StrokeCap.Round,
            )
        }
    }
    Box(
        modifier
            .size(width = 32.dp, height = 28.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
        content = { icon() },
    )
}
