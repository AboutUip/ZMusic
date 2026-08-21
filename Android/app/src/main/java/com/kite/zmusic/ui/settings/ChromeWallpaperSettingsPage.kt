package com.kite.zmusic.ui.settings

import android.content.res.Configuration
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kite.zmusic.data.ChromeGlassMode
import com.kite.zmusic.data.ChromeWallpaperState
import com.kite.zmusic.data.ChromeWallpaperStore
import com.kite.zmusic.data.ChromeWallpaperSurface
import com.kite.zmusic.data.WallpaperFrame
import com.kite.zmusic.ui.chrome.ChromeWallpaperLayer
import com.kite.zmusic.ui.chrome.wallpaperAlignPan
import com.kite.zmusic.ui.common.GlassAlertDialog
import com.kite.zmusic.ui.icons.ZIcons
import com.kite.zmusic.ui.main.MainControls
import com.kite.zmusic.ui.main.MainPalette
import com.kite.zmusic.ui.main.wallpaperItemChrome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val PreviewMorphSpec = tween<Float>(durationMillis = 420, easing = FastOutSlowInEasing)
private val PreviewDpSpec = tween<Dp>(durationMillis = 420, easing = FastOutSlowInEasing)
private val PreviewColorSpec = tween<Color>(durationMillis = 280, easing = FastOutSlowInEasing)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChromeWallpaperSettingsPage(
    state: ChromeWallpaperState,
    store: ChromeWallpaperStore,
    contentBottomInset: Dp,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val phoneLandscape =
        LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    var editLandscape by remember { mutableStateOf(phoneLandscape) }
    var editSurface by remember { mutableStateOf<ChromeWallpaperSurface?>(null) }
    var ox by remember { mutableFloatStateOf(0.5f) }
    var oy by remember { mutableFloatStateOf(0.5f) }
    var scale by remember { mutableFloatStateOf(1f) }
    var locked by remember { mutableStateOf(false) }
    var path by remember { mutableStateOf("") }
    var inherited by remember { mutableStateOf(false) }
    var helpTopic by remember {
        mutableStateOf(
            if (store.helpSeen()) null else WallpaperHelpTopic.Guide,
        )
    }

    fun persist(next: WallpaperFrame) {
        inherited = false
        store.setEditorFrame(editSurface, editLandscape, next)
    }

    fun draftFrame(): WallpaperFrame = WallpaperFrame(
        imagePath = path,
        offsetX = ox,
        offsetY = oy,
        scale = scale,
        locked = locked,
    )

    fun showFrame(frame: WallpaperFrame) {
        path = frame.imagePath
        ox = frame.offsetX
        oy = frame.offsetY
        scale = frame.scale.coerceIn(ChromeWallpaperStore.SCALE_MIN, ChromeWallpaperStore.SCALE_MAX)
        locked = frame.locked
    }

    fun showResolved(surface: ChromeWallpaperSurface?, landscape: Boolean) {
        val s = store.current()
        inherited = s.editorInherited(surface, landscape)
        showFrame(s.resolvedEditorFrame(surface, landscape))
    }

    fun applySlot(surface: ChromeWallpaperSurface?, landscape: Boolean) {
        if (surface == editSurface && landscape == editLandscape) return
        if (!inherited) persist(draftFrame())
        editSurface = surface
        editLandscape = landscape
        showResolved(surface, landscape)
    }

    val storedOwn = state.editorFrame(editSurface, editLandscape).imagePath
    val storedGeneric =
        if (editLandscape) state.genericLandscape.imagePath else state.genericPortrait.imagePath
    LaunchedEffect(editSurface, editLandscape, storedOwn, storedGeneric) {
        inherited = state.editorInherited(editSurface, editLandscape)
        showFrame(state.resolvedEditorFrame(editSurface, editLandscape))
    }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri == null || locked) return@rememberLauncherForActivityResult
        scope.launch {
            store.import(editSurface, editLandscape, uri)
        }
    }

    val hasImage = path.isNotBlank()
    val editable = !locked
    val switchColors = MainControls.switchColors()
    val sceneLabel = if (editSurface == null) "通用" else editSurface!!.title
    val sideLabel = if (editLandscape) "横屏" else "竖屏"

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = contentBottomInset + 24.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        Text(
            text = "打开开关后，只铺在你勾选的页面，默认只有主页。弹窗、通知、播放页不铺。这一页始终不铺，方便对着预览构图。",
            style = TextStyle(
                color = MainPalette.Secondary,
                fontSize = 13.sp,
                lineHeight = 18.sp,
            ),
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SettingsAccentLink("使用说明") { helpTopic = WallpaperHelpTopic.Guide }
            SettingsAccentLink("不会铺的地方") { helpTopic = WallpaperHelpTopic.Limits }
        }
        Spacer(Modifier.height(16.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .wallpaperItemChrome(RoundedCornerShape(16.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { store.setEnabled(!state.enabled) },
                )
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "启用自定义背景",
                    style = TextStyle(
                        color = MainPalette.Ink,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                )
                Text(
                    text = if (state.enabled) {
                        "已开启 · 只铺在勾选的页面"
                    } else {
                        "已关闭 · 各页用主题底色，构图还在"
                    },
                    style = TextStyle(color = MainPalette.Secondary, fontSize = 12.sp),
                )
            }
            Switch(
                checked = state.enabled,
                onCheckedChange = { store.setEnabled(it) },
                colors = switchColors,
            )
        }
        Spacer(Modifier.height(12.dp))
        ItemChromePicker(
            selected = state.itemChrome,
            enabled = state.enabled,
            onSelect = { store.setItemChrome(it) },
            onChromeHelp = { helpTopic = WallpaperHelpTopic.Chrome },
        )
        Spacer(Modifier.height(22.dp))
        SectionLabel("预览与构图")
        Spacer(Modifier.height(6.dp))
        SettingsAccentLink(
            "构图怎么算",
            modifier = Modifier.padding(horizontal = 4.dp),
        ) { helpTopic = WallpaperHelpTopic.Canvas }
        Spacer(Modifier.height(8.dp))
        Text(
            text = "切场景不会丢掉缩放和位置。图可以小于屏幕，拖动画布即可。没单独配图的勾选页会继承通用。",
            style = TextStyle(color = MainPalette.Secondary, fontSize = 12.sp, lineHeight = 16.sp),
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Spacer(Modifier.height(12.dp))
        SideToggle(
            landscape = editLandscape,
            onChange = { applySlot(editSurface, it) },
        )
        Spacer(Modifier.height(10.dp))
        SceneChips(
            selected = editSurface,
            portraits = state.portraits,
            landscapes = state.landscapes,
            genericPortrait = state.genericPortrait,
            genericLandscape = state.genericLandscape,
            editLandscape = editLandscape,
            onSelect = { applySlot(it, editLandscape) },
        )
        Spacer(Modifier.height(12.dp))
        WallpaperPreviewCard(
            slotKey = "${editSurface?.name ?: "generic"}:${if (editLandscape) "l" else "p"}",
            path = path,
            offsetX = ox,
            offsetY = oy,
            scale = scale,
            locked = locked,
            landscape = editLandscape,
            title = buildString {
                append(sceneLabel)
                append(" · ")
                append(sideLabel)
                if (inherited) append(" · 继承通用")
            },
            onTransform = { panX, panY, zoom, width, height, imgW, imgH ->
                if (!editable || !hasImage) return@WallpaperPreviewCard
                val next = wallpaperAlignPan(
                    viewW = width,
                    viewH = height,
                    imgW = imgW,
                    imgH = imgH,
                    scale = scale,
                    offsetX = ox,
                    offsetY = oy,
                    panX = panX,
                    panY = panY,
                )
                ox = next.first
                oy = next.second
                scale = (scale * zoom).coerceIn(
                    ChromeWallpaperStore.SCALE_MIN,
                    ChromeWallpaperStore.SCALE_MAX,
                )
                persist(draftFrame())
            },
        )
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ActionChip(
                label = if (editable) "相册选图" else "已锁定",
                enabled = editable,
                accent = true,
                modifier = Modifier.weight(1f),
                onClick = {
                    picker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
            )
            ActionChip(
                label = if (locked) "解锁" else "锁定",
                enabled = hasImage || locked,
                accent = true,
                modifier = Modifier.weight(1f),
                onClick = {
                    val next = draftFrame().copy(locked = !locked)
                    locked = next.locked
                    persist(next)
                },
            )
            ActionChip(
                label = "清除",
                enabled = !inherited && (hasImage || locked),
                accent = false,
                modifier = Modifier.weight(1f),
                onClick = { store.clearEditorFrame(editSurface, editLandscape) },
            )
        }
        Spacer(Modifier.height(22.dp))
        SectionLabel("铺在哪些页面")
        Spacer(Modifier.height(6.dp))
        SettingsAccentLink(
            "覆盖、通用和分场景",
            modifier = Modifier.padding(horizontal = 4.dp),
        ) { helpTopic = WallpaperHelpTopic.Coverage }
        Spacer(Modifier.height(8.dp))
        if (editSurface == null) {
            Text(
                text = "没单独配图、且已勾选覆盖的页面会用这张通用图。默认只铺主页。",
                style = TextStyle(color = MainPalette.Secondary, fontSize = 12.sp, lineHeight = 16.sp),
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        } else {
            val surface = editSurface
            if (surface != null) {
            val covering = surface in state.coverage
            Row(
                Modifier
                    .fillMaxWidth()
                    .wallpaperItemChrome(RoundedCornerShape(16.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { store.toggleCoverage(surface) },
                    )
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "覆盖此页",
                        style = TextStyle(
                            color = MainPalette.Ink,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                    )
                    Text(
                        text = if (covering) {
                            "已开启 · 这一页用这张图"
                        } else {
                            "已关闭 · 这一页仍用主题底色"
                        },
                        style = TextStyle(color = MainPalette.Secondary, fontSize = 12.sp),
                    )
                }
                Switch(
                    checked = covering,
                    onCheckedChange = { store.toggleCoverage(surface) },
                    colors = switchColors,
                )
            }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ActionChip(
                label = "全部覆盖",
                enabled = true,
                accent = false,
                modifier = Modifier.weight(1f),
                onClick = { store.setCoverage(ChromeWallpaperSurface.entries.toSet()) },
            )
            ActionChip(
                label = "恢复默认",
                enabled = true,
                accent = false,
                modifier = Modifier.weight(1f),
                onClick = { store.setCoverage(ChromeWallpaperStore.DEFAULT_COVERAGE) },
            )
        }
        Spacer(Modifier.height(12.dp))
        SettingsAccentLink(
            "个人页和用户空间图",
            modifier = Modifier.padding(horizontal = 4.dp),
        ) { helpTopic = WallpaperHelpTopic.Profile }
    }
    helpTopic?.let { topic ->
        val doc = wallpaperHelp(topic)
        GlassAlertDialog(
            title = doc.title,
            onDismiss = {
                store.markHelpSeen()
                helpTopic = null
            },
            confirmLabel = "知道了",
            cancelLabel = null,
            onConfirm = {
                store.markHelpSeen()
                helpTopic = null
            },
            extraContent = {
                doc.sections.forEachIndexed { index, section ->
                    if (index > 0) Spacer(Modifier.height(14.dp))
                    Text(
                        text = section.heading,
                        style = TextStyle(
                            color = MainPalette.Ink,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            lineHeight = 20.sp,
                        ),
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = section.body,
                        style = TextStyle(
                            color = MainPalette.Secondary,
                            fontSize = 13.sp,
                            lineHeight = 20.sp,
                        ),
                    )
                }
            },
        )
    }
}

@Composable
internal fun SettingsAccentLink(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Text(
        text = text,
        modifier = modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick,
        ),
        style = TextStyle(
            color = MainPalette.Accent,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
        ),
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = TextStyle(
            color = MainPalette.Ink,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
        ),
        modifier = Modifier.padding(horizontal = 2.dp),
    )
}

@Composable
private fun SideToggle(landscape: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .wallpaperItemChrome(RoundedCornerShape(12.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        listOf(false to "竖屏", true to "横屏").forEach { (land, label) ->
            val on = landscape == land
            val bg by animateColorAsState(
                targetValue = if (on) MainPalette.Accent.copy(alpha = 0.14f) else Color.Transparent,
                animationSpec = PreviewColorSpec,
                label = "sideBg",
            )
            val fg by animateColorAsState(
                targetValue = if (on) MainPalette.Accent else MainPalette.Secondary,
                animationSpec = PreviewColorSpec,
                label = "sideFg",
            )
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(9.dp))
                    .background(bg)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onChange(land) },
                    )
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = TextStyle(
                        color = fg,
                        fontSize = 13.sp,
                        fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium,
                    ),
                )
            }
        }
    }
}

@Composable
private fun SceneChips(
    selected: ChromeWallpaperSurface?,
    portraits: Map<ChromeWallpaperSurface, WallpaperFrame>,
    landscapes: Map<ChromeWallpaperSurface, WallpaperFrame>,
    genericPortrait: WallpaperFrame,
    genericLandscape: WallpaperFrame,
    editLandscape: Boolean,
    onSelect: (ChromeWallpaperSurface?) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val genericHas = if (editLandscape) genericLandscape.hasImage else genericPortrait.hasImage
        CoverageChip(
            title = if (genericHas) "通用 · 有图" else "通用",
            selected = selected == null,
            onClick = { onSelect(null) },
        )
        ChromeWallpaperSurface.entries.forEach { surface ->
            val has = if (editLandscape) {
                landscapes[surface]?.hasImage == true
            } else {
                portraits[surface]?.hasImage == true
            }
            CoverageChip(
                title = when {
                    has -> "${surface.title} · 有图"
                    genericHas -> "${surface.title} · 继承"
                    else -> surface.title
                },
                selected = selected == surface,
                onClick = { onSelect(surface) },
            )
        }
    }
}

@Composable
private fun CoverageChip(title: String, selected: Boolean, onClick: () -> Unit) {
    val bg by animateColorAsState(
        targetValue = if (selected) MainPalette.Accent.copy(alpha = 0.14f) else MainPalette.Surface,
        animationSpec = PreviewColorSpec,
        label = "chipBg",
    )
    val fg by animateColorAsState(
        targetValue = if (selected) MainPalette.Accent else MainPalette.Ink,
        animationSpec = PreviewColorSpec,
        label = "chipFg",
    )
    val stroke by animateColorAsState(
        targetValue = if (selected) Color.Transparent else MainPalette.Hairline,
        animationSpec = PreviewColorSpec,
        label = "chipStroke",
    )
    Box(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .border(1.dp, stroke, RoundedCornerShape(20.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text(
            text = title,
            style = TextStyle(
                color = fg,
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun WallpaperPreviewCard(
    slotKey: String,
    path: String,
    offsetX: Float,
    offsetY: Float,
    scale: Float,
    locked: Boolean,
    landscape: Boolean,
    title: String,
    onTransform: (
        panX: Float,
        panY: Float,
        zoom: Float,
        width: Float,
        height: Float,
        imgW: Int,
        imgH: Int,
    ) -> Unit,
) {
    val config = LocalConfiguration.current
    val short = minOf(config.screenWidthDp, config.screenHeightDp).toFloat().coerceAtLeast(1f)
    val long = maxOf(config.screenWidthDp, config.screenHeightDp).toFloat().coerceAtLeast(1f)
    val ratio by animateFloatAsState(
        targetValue = if (landscape) long / short else short / long,
        animationSpec = PreviewMorphSpec,
        label = "previewRatio",
    )
    val maxH by animateDpAsState(
        targetValue = if (landscape) 168.dp else 300.dp,
        animationSpec = PreviewDpSpec,
        label = "previewH",
    )
    val visualKey = "$slotKey|$path"
    val current = WallpaperFrame(
        imagePath = path,
        offsetX = offsetX,
        offsetY = offsetY,
        scale = scale,
        locked = locked,
    )
    val frames = remember { mutableMapOf<String, WallpaperFrame>() }
    frames[visualKey] = current
    var imgW by remember(path) { mutableIntStateOf(0) }
    var imgH by remember(path) { mutableIntStateOf(0) }
    LaunchedEffect(path) {
        if (path.isBlank()) {
            imgW = 0
            imgH = 0
            return@LaunchedEffect
        }
        val bounds = withContext(Dispatchers.IO) {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, opts)
            opts.outWidth to opts.outHeight
        }
        imgW = bounds.first.coerceAtLeast(0)
        imgH = bounds.second.coerceAtLeast(0)
    }
    val transform = rememberUpdatedState(onTransform)
    val imgWState = rememberUpdatedState(imgW)
    val imgHState = rememberUpdatedState(imgH)
    Column(
        Modifier
            .fillMaxWidth()
            .wallpaperItemChrome(RoundedCornerShape(16.dp))
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AnimatedContent(
                targetState = title,
                modifier = Modifier.weight(1f),
                transitionSpec = {
                    fadeIn(tween(220, easing = FastOutSlowInEasing)) togetherWith
                        fadeOut(tween(140, easing = FastOutSlowInEasing)) using
                        SizeTransform(clip = false)
                },
                label = "previewTitle",
            ) { label ->
                Text(
                    text = label,
                    style = TextStyle(
                        color = MainPalette.Ink,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            AnimatedContent(
                targetState = locked,
                transitionSpec = {
                    fadeIn(tween(180)) togetherWith fadeOut(tween(120))
                },
                label = "previewLock",
            ) { on ->
                Icon(
                    imageVector = if (on) ZIcons.Lock else ZIcons.LockOpen,
                    contentDescription = null,
                    tint = MainPalette.Secondary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(maxH)
                .clipToBounds(),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .height(maxH)
                    .aspectRatio(ratio)
                    .clip(RoundedCornerShape(12.dp))
                    .clipToBounds()
                    .background(MainPalette.Page)
                    .pointerInput(locked, path) {
                        if (locked || path.isBlank()) return@pointerInput
                        detectTransformGestures { _, pan, zoom, _ ->
                            transform.value(
                                pan.x,
                                pan.y,
                                zoom,
                                size.width.toFloat(),
                                size.height.toFloat(),
                                imgWState.value,
                                imgHState.value,
                            )
                        }
                    },
            ) {
                AnimatedContent(
                    targetState = visualKey,
                    modifier = Modifier.fillMaxSize(),
                    transitionSpec = {
                        (
                            fadeIn(tween(280, easing = FastOutSlowInEasing)) +
                                scaleIn(
                                    initialScale = 0.97f,
                                    animationSpec = tween(280, easing = FastOutSlowInEasing),
                                )
                            ) togetherWith (
                            fadeOut(tween(180, easing = FastOutSlowInEasing)) +
                                scaleOut(
                                    targetScale = 1.02f,
                                    animationSpec = tween(180, easing = FastOutSlowInEasing),
                                )
                            ) using SizeTransform(clip = false)
                    },
                    label = "previewFrame",
                ) { key ->
                    val frame = if (key == visualKey) current else frames[key] ?: current
                    Box(Modifier.fillMaxSize()) {
                        if (frame.hasImage) {
                            ChromeWallpaperLayer(
                                frame = frame,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                        if (!frame.hasImage) {
                            Text(
                                text = "从相册选一张图",
                                style = TextStyle(color = MainPalette.Secondary, fontSize = 12.sp),
                                modifier = Modifier.align(Alignment.Center),
                            )
                        }
                    }
                }
            }
        }
        AnimatedVisibility(
            visible = !locked && path.isNotBlank(),
            enter = fadeIn(tween(220, easing = FastOutSlowInEasing)),
            exit = fadeOut(tween(140, easing = FastOutSlowInEasing)),
        ) {
            Column {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "在画布里拖动、捏合来构图。位置按图的边缘对齐画面，和缩放无关；图不必铺满。",
                    style = TextStyle(color = MainPalette.Hint, fontSize = 11.sp),
                )
            }
        }
    }
}

@Composable
private fun ItemChromePicker(
    selected: ChromeGlassMode,
    enabled: Boolean,
    onSelect: (ChromeGlassMode) -> Unit,
    onChromeHelp: () -> Unit,
) {
    val modes = listOf(
        ChromeGlassMode.Solid to "纯色",
        ChromeGlassMode.Frosted to "磨砂",
        ChromeGlassMode.Liquid to "液态玻璃",
    )
    Column(
        Modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = if (enabled) 1f else 0.46f },
    ) {
        SectionLabel("组件边界")
        Spacer(Modifier.height(6.dp))
        SettingsAccentLink(
            "三种边界怎么选",
            modifier = Modifier.padding(horizontal = 4.dp),
            onClick = onChromeHelp,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "只在已铺背景的页面生效。模糊和折射跟「液态玻璃样式」。主页和功能页不改组件；个人页只改下面的歌单列表。",
            style = TextStyle(color = MainPalette.Secondary, fontSize = 12.sp, lineHeight = 16.sp),
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .wallpaperItemChrome(RoundedCornerShape(12.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            modes.forEach { (mode, label) ->
                val on = selected == mode
                val bg by animateColorAsState(
                    targetValue = if (on) MainPalette.Accent.copy(alpha = 0.14f) else Color.Transparent,
                    animationSpec = PreviewColorSpec,
                    label = "itemChromeBg",
                )
                val fg by animateColorAsState(
                    targetValue = if (on) MainPalette.Accent else MainPalette.Secondary,
                    animationSpec = PreviewColorSpec,
                    label = "itemChromeFg",
                )
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(9.dp))
                        .background(bg)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { if (mode != selected) onSelect(mode) },
                        )
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        style = TextStyle(
                            color = fg,
                            fontSize = 13.sp,
                            fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionChip(
    label: String,
    enabled: Boolean,
    accent: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                when {
                    !enabled -> MainPalette.Surface
                    accent -> MainPalette.Accent.copy(alpha = 0.14f)
                    else -> MainPalette.Surface
                },
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = TextStyle(
                color = when {
                    !enabled -> MainPalette.Hint
                    accent -> MainPalette.Accent
                    else -> MainPalette.Ink
                },
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            ),
        )
    }
}
