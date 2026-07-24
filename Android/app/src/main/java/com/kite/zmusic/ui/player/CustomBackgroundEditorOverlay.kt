package com.kite.zmusic.ui.player

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kite.zmusic.data.PlayerBackgroundPreset
import com.kite.zmusic.data.PlayerDisplayPrefs
import com.kite.zmusic.data.TrackRow
import com.kite.zmusic.data.VinylPlateColors
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val BgEditorAccent = Color(0xFF9AF0F0)
private val BgEditorLabel = Color(0xFFFFFFFF)
private val BgEditorHint = Color(0xFFE8F0F8)
private val BgEditorRowBg = Color.Black.copy(alpha = 0.42f)
private val BgEditorCurve = CubicBezierEasing(0.16f, 1.02f, 0.3f, 1f)
private val BgEditorShadow = Shadow(color = Color.Black.copy(alpha = 0.7f), blurRadius = 10f)

internal fun playerBackgroundDir(context: Context): File =
    File(context.filesDir, "player_backgrounds").also { it.mkdirs() }

internal fun playerBackgroundFile(context: Context, index: Int): File =
    File(playerBackgroundDir(context), "preset_$index.jpg")

internal suspend fun copyBackgroundImageToPreset(
    context: Context,
    uri: Uri,
    index: Int,
): String? = withContext(Dispatchers.IO) {
    runCatching {
        val out = playerBackgroundFile(context, index)
        context.contentResolver.openInputStream(uri)?.use { input ->
            out.outputStream().use { output -> input.copyTo(output) }
        } ?: return@runCatching null
        out.absolutePath
    }.getOrNull()
}

internal suspend fun deleteBackgroundImageFile(path: String?) = withContext(Dispatchers.IO) {
    if (path.isNullOrBlank()) return@withContext
    runCatching { File(path).takeIf { it.exists() }?.delete() }
}

@Composable
internal fun LocalPathImage(
    path: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    alignment: Alignment = Alignment.Center,
) {
    var bitmap by remember(path) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(path) {
        if (path.isNullOrBlank()) {
            bitmap = null
            return@LaunchedEffect
        }
        bitmap = withContext(Dispatchers.IO) {
            runCatching {
                BitmapFactory.decodeFile(path)?.asImageBitmap()
            }.getOrNull()
        }
    }
    val bmp = bitmap
    if (bmp != null) {
        Image(
            bitmap = bmp,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale,
            alignment = alignment,
        )
    } else {
        Box(modifier.background(Color(0xFF12141A)))
    }
}

/**
 * 全屏沉浸背景层：铺满含状态栏/导航条区域。
 * [progress] 0=隐藏，1=显示；与光球层做交叉淡入。
 */
@Composable
fun PlayerCustomBackgroundLayer(
    preset: PlayerBackgroundPreset?,
    progress: Float,
    modifier: Modifier = Modifier,
) {
    val t = progress.coerceIn(0f, 1f)
    val targetOx = preset?.offsetX ?: 0.5f
    val targetOy = preset?.offsetY ?: 0.5f
    val targetScale = preset?.scale ?: 1f
    val ox by animateFloatAsState(
        targetValue = targetOx,
        animationSpec = tween(420, easing = FastOutSlowInEasing),
        label = "bgOx",
    )
    val oy by animateFloatAsState(
        targetValue = targetOy,
        animationSpec = tween(420, easing = FastOutSlowInEasing),
        label = "bgOy",
    )
    val sc by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = tween(420, easing = FastOutSlowInEasing),
        label = "bgScale",
    )
    Box(
        modifier
            .fillMaxSize()
            .graphicsLayer { alpha = t },
    ) {
        if (preset != null && preset.hasImage && t > 0.001f) {
            LocalPathImage(
                path = preset.imagePath,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = sc
                        scaleY = sc
                        // 0→左/上，1→右/下；以中心为 0
                        translationX = (ox - 0.5f) * size.width * 0.55f
                        translationY = (oy - 0.5f) * size.height * 0.55f
                    },
                // 不自动裁切：完整显示原图，取景交给 scale / offset
                contentScale = ContentScale.Fit,
            )
            // 轻微压暗，保证前景可读
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.28f * t)),
            )
        }
    }
}

@Composable
fun CustomBackgroundEditorOverlay(
    open: Boolean,
    prefs: PlayerDisplayPrefs,
    sampleTrack: TrackRow?,
    onPrefsChange: (PlayerDisplayPrefs) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(open) {
        progress.animateTo(
            if (open) 1f else 0f,
            animationSpec = tween(460, easing = BgEditorCurve),
        )
    }
    if (progress.value <= 0.001f && !open) return

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val t = progress.value
    var editIndex by remember { mutableIntStateOf(prefs.backgroundPresetIndex) }
    var draft by remember {
        mutableStateOf(
            prefs.backgroundPresets.getOrElse(editIndex) { PlayerBackgroundPreset() },
        )
    }
    var draftOx by remember { mutableFloatStateOf(draft.offsetX) }
    var draftOy by remember { mutableFloatStateOf(draft.offsetY) }
    var draftScale by remember { mutableFloatStateOf(draft.scale) }

    fun loadDraft(index: Int) {
        editIndex = index
        val p = prefs.backgroundPresets.getOrElse(index) { PlayerBackgroundPreset() }
        draft = p
        draftOx = p.offsetX
        draftOy = p.offsetY
        draftScale = p.scale
    }

    LaunchedEffect(open, prefs.backgroundPresetIndex) {
        if (open) loadDraft(prefs.backgroundPresetIndex)
    }

    val pickLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri == null || draft.locked) return@rememberLauncherForActivityResult
        scope.launch {
            val path = copyBackgroundImageToPreset(context, uri, editIndex) ?: return@launch
            draft = draft.copy(imagePath = path, locked = false)
        }
    }

    BackHandler(enabled = open) { onDismiss() }

    val editable = !draft.locked
    val hasImage = draft.hasImage
    val canConfirm = editable && hasImage
    val canReset = draft.hasImage || draft.locked ||
        draftOx != 0.5f || draftOy != 0.5f || draftScale != 1f

    val sliderColors = SliderDefaults.colors(
        thumbColor = Color(0xFFF8FAFC),
        activeTrackColor = BgEditorAccent.copy(alpha = 0.62f),
        inactiveTrackColor = Color.White.copy(alpha = 0.16f),
        disabledThumbColor = Color.White.copy(alpha = 0.28f),
        disabledActiveTrackColor = Color.White.copy(alpha = 0.12f),
        disabledInactiveTrackColor = Color.White.copy(alpha = 0.08f),
    )

    Box(
        modifier
            .fillMaxSize()
            .graphicsLayer { alpha = t },
    ) {
        // 全屏沉浸：不避让系统栏；半透明底
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0xE603060A))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
        )
        val statusPad = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val navPad = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        Column(
            Modifier
                .fillMaxSize()
                .padding(top = statusPad, bottom = navPad)
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .graphicsLayer {
                    translationY = (1f - t) * 48f
                    alpha = t
                }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
        ) {
            // ── 顶部工具区（紧凑） ──
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "自定义背景",
                    style = TextStyle(
                        color = BgEditorLabel,
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 17.sp,
                        shadow = BgEditorShadow,
                    ),
                    modifier = Modifier.weight(1f),
                )
                AnimatedContent(
                    targetState = when {
                        draft.locked -> "已锁定"
                        hasImage -> "可调整"
                        else -> "待上传"
                    },
                    transitionSpec = {
                        fadeIn(tween(200)) togetherWith fadeOut(tween(140))
                    },
                    label = "bgStatus",
                ) { msg ->
                    Text(
                        text = msg,
                        style = TextStyle(
                            color = BgEditorHint.copy(alpha = 0.72f),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                        ),
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
                Text(
                    text = "关闭",
                    style = TextStyle(
                        color = BgEditorAccent,
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                    ),
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = onDismiss)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }

            Spacer(Modifier.height(10.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                repeat(PlayerDisplayPrefs.BACKGROUND_PRESET_COUNT) { i ->
                    val preset = prefs.backgroundPresets.getOrElse(i) { PlayerBackgroundPreset() }
                    val selected = i == editIndex
                    val selT by animateFloatAsState(
                        targetValue = if (selected) 1f else 0f,
                        animationSpec = tween(280, easing = FastOutSlowInEasing),
                        label = "bgChip$i",
                    )
                    Box(
                        Modifier
                            .weight(1f)
                            .height(44.dp)
                            .graphicsLayer {
                                scaleX = 0.94f + 0.06f * selT
                                scaleY = 0.94f + 0.06f * selT
                            }
                            .clip(RoundedCornerShape(11.dp))
                            .border(
                                width = (1f + selT).dp,
                                color = BgEditorAccent.copy(alpha = 0.25f + 0.65f * selT),
                                shape = RoundedCornerShape(11.dp),
                            )
                            .background(BgEditorRowBg)
                            .clickable {
                                onPrefsChange(prefs.withBackgroundPresetIndex(i))
                                loadDraft(i)
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (preset.hasImage) {
                            LocalPathImage(
                                path = preset.imagePath,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                            )
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.25f)),
                            )
                        }
                        Text(
                            text = "${i + 1}",
                            style = TextStyle(
                                color = Color.White.copy(alpha = 0.7f + 0.3f * selT),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                            ),
                        )
                        if (preset.locked) {
                            Box(
                                Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(4.dp)
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(BgEditorAccent),
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                BgEditorActionButton(
                    label = if (editable) "上传图片" else "已锁定",
                    enabled = editable,
                    emphasize = true,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        pickLauncher.launch(
                            PickVisualMediaRequest(
                                ActivityResultContracts.PickVisualMedia.ImageOnly,
                            ),
                        )
                    },
                )
                BgEditorActionButton(
                    label = "确定锁定",
                    enabled = canConfirm,
                    emphasize = true,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        val locked = draft.copy(
                            offsetX = draftOx,
                            offsetY = draftOy,
                            scale = draftScale,
                            locked = true,
                        )
                        draft = locked
                        onPrefsChange(prefs.withBackgroundPresetAt(editIndex, locked))
                    },
                )
                BgEditorActionButton(
                    label = "重置预设",
                    enabled = canReset,
                    emphasize = false,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        scope.launch {
                            deleteBackgroundImageFile(draft.imagePath)
                            draft = PlayerBackgroundPreset()
                            draftOx = 0.5f
                            draftOy = 0.5f
                            draftScale = 1f
                            onPrefsChange(prefs.resetBackgroundPresetAt(editIndex))
                        }
                    },
                )
            }

            Column(
                Modifier.padding(top = 10.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                // 始终占位：无图时禁用，避免显隐撑缩导致预览区跳动/错位
                val slidersEnabled = editable && hasImage
                BgSliderRow(
                    title = "水平位置",
                    value = draftOx,
                    valueRange = PlayerDisplayPrefs.BG_OFFSET_MIN..PlayerDisplayPrefs.BG_OFFSET_MAX,
                    enabled = slidersEnabled,
                    colors = sliderColors,
                    label = String.format("%.0f%%", draftOx * 100f),
                    onValueChange = { draftOx = it },
                )
                BgSliderRow(
                    title = "垂直位置",
                    value = draftOy,
                    valueRange = PlayerDisplayPrefs.BG_OFFSET_MIN..PlayerDisplayPrefs.BG_OFFSET_MAX,
                    enabled = slidersEnabled,
                    colors = sliderColors,
                    label = String.format("%.0f%%", draftOy * 100f),
                    onValueChange = { draftOy = it },
                )
                BgSliderRow(
                    title = "图片缩放",
                    value = draftScale,
                    valueRange = PlayerDisplayPrefs.BG_SCALE_MIN..PlayerDisplayPrefs.BG_SCALE_MAX,
                    enabled = slidersEnabled,
                    colors = sliderColors,
                    label = String.format("%.0f%%", draftScale * 100f),
                    onValueChange = { draftScale = it },
                )
            }

            Spacer(Modifier.height(12.dp))

            // ── 下方预览区：真实机身比例 + 预览坐标系内等比联动（无 graphicsLayer 整页缩放） ──
            PortraitBackgroundPreview(
                path = draft.imagePath.takeIf { it.isNotBlank() },
                offsetX = draftOx,
                offsetY = draftOy,
                scale = draftScale,
                sampleTrack = sampleTrack,
                displayPrefs = prefs,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun BgEditorActionButton(
    label: String,
    enabled: Boolean,
    emphasize: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val enT by animateFloatAsState(
        targetValue = if (enabled) 1f else 0.38f,
        animationSpec = tween(280, easing = FastOutSlowInEasing),
        label = "bgBtnEn",
    )
    Box(
        modifier
            .graphicsLayer { alpha = enT }
            .clip(RoundedCornerShape(11.dp))
            .background(
                if (emphasize) BgEditorAccent.copy(alpha = 0.18f) else BgEditorRowBg,
            )
            .border(
                1.dp,
                if (emphasize) BgEditorAccent.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.12f),
                RoundedCornerShape(11.dp),
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = TextStyle(
                color = if (emphasize) BgEditorAccent else BgEditorLabel,
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
            ),
        )
    }
}

@Composable
private fun BgSliderRow(
    title: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    enabled: Boolean,
    colors: androidx.compose.material3.SliderColors,
    label: String,
    onValueChange: (Float) -> Unit,
) {
    val enT by animateFloatAsState(
        targetValue = if (enabled) 1f else 0.42f,
        animationSpec = tween(260),
        label = "bgSliderEn",
    )
    Column(
        Modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = enT }
            .clip(RoundedCornerShape(10.dp))
            .background(BgEditorRowBg)
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = TextStyle(
                    color = BgEditorLabel,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                ),
                modifier = Modifier.weight(1f),
            )
            Text(
                text = label,
                style = TextStyle(
                    color = BgEditorHint.copy(alpha = 0.8f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                ),
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            enabled = enabled,
            colors = colors,
            modifier = Modifier.height(28.dp),
        )
    }
}

@Composable
private fun PortraitBackgroundPreview(
    path: String?,
    offsetX: Float,
    offsetY: Float,
    scale: Float,
    sampleTrack: TrackRow?,
    displayPrefs: PlayerDisplayPrefs,
    modifier: Modifier = Modifier,
) {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenW = configuration.screenWidthDp.dp.coerceAtLeast(1.dp)
    val screenH = configuration.screenHeightDp.dp.coerceAtLeast(1.dp)

    val vinylSizeScale = displayPrefs.vinylSizeScale
        .coerceIn(PlayerDisplayPrefs.VINYL_SIZE_SCALE_MIN, PlayerDisplayPrefs.VINYL_SIZE_SCALE_MAX)
    val vinylOffsetYDp = displayPrefs.vinylOffsetYDp
        .coerceIn(PlayerDisplayPrefs.VINYL_OFFSET_MIN, PlayerDisplayPrefs.VINYL_OFFSET_MAX)
    val vinylFullCover = displayPrefs.vinylFullCover
    val prefsUiScale = displayPrefs.uiScale
        .coerceIn(PlayerDisplayPrefs.UI_MIN, PlayerDisplayPrefs.UI_MAX)

    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val phoneAspect = screenW / screenH
        val fitByHeight = maxHeight * phoneAspect <= maxWidth
        val previewW = if (fitByHeight) maxHeight * phoneAspect else maxWidth
        val previewH = if (fitByHeight) maxHeight else maxWidth / phoneAspect
        // 预览相对真机宽度的比例；全部在预览坐标系布局，禁止整页 graphicsLayer 缩放
        val uiScale = (previewW / screenW).coerceAtLeast(0.01f)
        val frameShape = RoundedCornerShape((22f * uiScale).coerceAtLeast(10f).dp)

        val statusPad = with(density) {
            WindowInsets.statusBars.getTop(this).toDp()
        } * uiScale
        val navPad = with(density) {
            WindowInsets.navigationBars.getBottom(this).toDp()
        } * uiScale
        val contentHPad = 16.dp * uiScale
        val contentTopPad = 6.dp * uiScale

        Box(
            Modifier
                .size(previewW, previewH)
                .clip(frameShape)
                .border(1.dp, Color.White.copy(alpha = 0.22f), frameShape)
                .background(Color(0xFF0A0C12)),
        ) {
            if (!path.isNullOrBlank()) {
                LocalPathImage(
                    path = path,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = (offsetX - 0.5f) * size.width * 0.55f
                            translationY = (offsetY - 0.5f) * size.height * 0.55f
                        },
                    contentScale = ContentScale.Fit,
                )
            } else {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0xFF151820), Color(0xFF090B12)),
                            ),
                        ),
                )
            }
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.22f)))

            Column(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = prefsUiScale
                        scaleY = prefsUiScale
                        transformOrigin = TransformOrigin(0.5f, 0.5f)
                        clip = false
                    }
                    .padding(
                        start = contentHPad,
                        end = contentHPad,
                        top = statusPad + contentTopPad,
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Spacer(
                        Modifier.size(
                            width = NowPlayingChromeIconWidth * uiScale,
                            height = NowPlayingChromeIconHeight * uiScale,
                        ),
                    )
                    Text(
                        text = sampleTrack?.name.orEmpty().ifBlank { "预览" },
                        style = TextStyle(
                            color = Color(0xFFF2EDE6),
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = (19f * uiScale).sp,
                            letterSpacing = 0.2.sp,
                            textAlign = TextAlign.Start,
                            shadow = BgEditorShadow,
                        ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(
                        Modifier.size(
                            width = NowPlayingChromeIconWidth * uiScale,
                            height = NowPlayingChromeIconHeight * uiScale,
                        ),
                    )
                }
                Spacer(Modifier.height(8.dp * uiScale))

                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    BoxWithConstraints(
                        Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        val base = maxWidth
                            .coerceAtMost(312.dp * uiScale)
                            .coerceAtLeast(200.dp * uiScale)
                        val side = base * vinylSizeScale
                        Box(
                            Modifier
                                .size(side)
                                .offset(y = vinylOffsetYDp.dp * uiScale),
                        ) {
                            if (sampleTrack != null) {
                                VinylDiscFace(
                                    track = sampleTrack,
                                    spinDeg = 0f,
                                    spinning = false,
                                    fullCover = vinylFullCover,
                                    centerRadiusFrac = 0.20f,
                                    outerScale = 1f,
                                    plateColors = VinylPlateColors.Black,
                                    modifier = Modifier.fillMaxSize(),
                                    animateStyleChanges = false,
                                )
                            }
                        }
                    }
                }

                PortraitTransportHeightStub(uiScale = uiScale, navBottom = navPad)
            }
        }
    }
}

@Composable
private fun PortraitTransportHeightStub(
    uiScale: Float,
    navBottom: androidx.compose.ui.unit.Dp,
) {
    val sliderH = 16.dp * uiScale
    val playSize = 50.dp * uiScale
    val portraitBottomBandHeight = 36.dp * uiScale
    val bottomZoneHeight = navBottom + 56.dp * uiScale
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 1.dp * uiScale),
    ) {
        Spacer(Modifier.height((14.dp + 6.dp) * uiScale))
        Spacer(Modifier.height(sliderH))
        Spacer(Modifier.height(16.dp * uiScale))
        Spacer(
            Modifier
                .fillMaxWidth()
                .height(playSize + 8.dp * uiScale),
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(bottomZoneHeight),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(portraitBottomBandHeight)
                    .clip(RoundedCornerShape(14.dp * uiScale))
                    .background(Color.Black.copy(alpha = 0.22f)),
            )
        }
    }
}
