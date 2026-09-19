package com.kite.zmusic.ui.lyricoverlay

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.coerceIn
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kite.zmusic.data.LrcLine
import com.kite.zmusic.data.LyricOverlayPrefs
import com.kite.zmusic.data.OverlayLyricRow
import com.kite.zmusic.data.overlayLyricRows
import com.kite.zmusic.data.overlayShowsWindowBackground
import com.kite.zmusic.data.pickDisplayLyricBundle
import com.kite.zmusic.data.PlayerDisplayPrefsStore
import com.kite.zmusic.data.sanitizedForDisplay
import com.kite.zmusic.playback.PlaybackUiState
import com.kite.zmusic.ui.icons.ZIcons
import com.kite.zmusic.ui.player.lyricActiveIndex
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import com.kite.zmusic.i18n.t

private val SettingsRevealSpec = tween<Float>(durationMillis = 280, easing = FastOutSlowInEasing)
private val SettingsHideSpec = tween<Float>(durationMillis = 240, easing = FastOutSlowInEasing)

@Composable
fun LyricOverlayContent(
    playbackUi: StateFlow<PlaybackUiState>,
    prefs: LyricOverlayPrefs,
    maxWidthPx: Int,
    onPrefs: (LyricOverlayPrefs) -> Unit,
    onLock: () -> Unit,
    onTogglePlay: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    onCenterHorizontally: () -> Unit,
    onClose: () -> Unit,
    idleChrome: Boolean,
    onWake: () -> Unit,
    onAllowWindowDrag: (Boolean) -> Unit,
) {
    var settingsOpen by remember { mutableStateOf(false) }
    LaunchedEffect(prefs.locked) {
        if (prefs.locked) {
            settingsOpen = false
            onWake()
        }
    }
    LaunchedEffect(settingsOpen) {
        if (settingsOpen) onWake()
    }
    LaunchedEffect(settingsOpen, prefs.locked) {
        onAllowWindowDrag(!settingsOpen && !prefs.locked)
    }
    val density = LocalDensity.current

    val playing by remember(playbackUi) {
        playbackUi.map { it.playWhenReady }.distinctUntilChanged()
    }.collectAsState(initial = playbackUi.value.playWhenReady)

    val availableDp = with(density) { maxWidthPx.toDp() }
    val overlayWidth = if (prefs.dynamicWidth) {
        availableDp
    } else {
        availableDp * prefs.widthPercent.coerceIn(
            LyricOverlayPrefs.WIDTH_PERCENT_MIN,
            LyricOverlayPrefs.WIDTH_PERCENT_MAX,
        ) / 100f
    }
    val widthMod = if (prefs.dynamicWidth) {
        Modifier.widthIn(min = 120.dp, max = availableDp)
    } else {
        Modifier.fillMaxWidth()
    }
    val hPad = when {
        overlayWidth < 168.dp -> 6.dp
        overlayWidth < 220.dp -> 8.dp
        else -> 10.dp
    }
    val compact = overlayWidth < 200.dp
    val shape = RoundedCornerShape(14.dp)
    val lyricsOnly = prefs.locked || idleChrome
    val showClose = !lyricsOnly
    val showWindowBg = overlayShowsWindowBackground(
        locked = prefs.locked,
        idleChrome = idleChrome,
        windowBackgroundEnabled = prefs.windowBackground,
    )
    val showLyricBg = prefs.lyricBackground && !idleChrome
    val blurT = if (prefs.windowBackground) {
        prefs.blurRadiusPx / LyricOverlayPrefs.BLUR_MAX.toFloat()
    } else {
        0f
    }
    val windowBg by animateColorAsState(
        targetValue = if (showWindowBg) {
            Color(
                red = 0.07f + 0.10f * blurT,
                green = 0.07f + 0.10f * blurT,
                blue = 0.09f + 0.10f * blurT,
                alpha = 0.88f - 0.16f * blurT.coerceIn(0f, 1f),
            )
        } else {
            Color.Transparent
        },
        animationSpec = tween(220, easing = FastOutSlowInEasing),
        label = "overlayWindowBg",
    )
    val closeBtn = if (compact) 26.dp else 28.dp
    val closeIcon = if (compact) 15.dp else 16.dp
    Box(
        modifier = widthMod
            .clip(shape)
            .background(windowBg),
    ) {
        Column(
            Modifier.padding(
                start = hPad,
                top = 8.dp,
                end = hPad,
                bottom = 8.dp,
            ),
        ) {
            OverlayLyricLines(
                playbackUi = playbackUi,
                prefs = prefs,
                lyricBackground = showLyricBg,
                closeGutter = if (showClose) closeBtn else 0.dp,
                marqueeEnabled = !lyricsOnly,
            )
            if (!lyricsOnly) {
                Spacer(Modifier.height(6.dp))
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val btn = ((maxWidth - 4.dp) / 5).coerceIn(24.dp, 32.dp)
                    val icon = if (btn < 28.dp) 15.dp else 18.dp
                    OverlayToolbar(
                        playing = playing,
                        settingsOpen = settingsOpen,
                        buttonSize = btn,
                        iconSize = icon,
                        onToggleSettings = { settingsOpen = !settingsOpen },
                        onTogglePlay = onTogglePlay,
                        onSkipPrevious = onSkipPrevious,
                        onSkipNext = onSkipNext,
                        onLock = {
                            settingsOpen = false
                            onLock()
                        },
                    )
                }
                OverlaySettingsReveal(visible = settingsOpen) {
                    Column(Modifier.fillMaxWidth()) {
                        Spacer(Modifier.height(8.dp))
                        LyricOverlaySettingsPanel(
                            prefs = prefs,
                            onChange = onPrefs,
                            onCenterHorizontally = onCenterHorizontally,
                            compact = compact,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
        if (showClose) {
            OverlayIconBtn(
                icon = ZIcons.Close,
                label = t("关闭悬浮窗"),
                buttonSize = closeBtn,
                iconSize = closeIcon,
                onClick = onClose,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(2.dp),
            )
        }
    }
}

/**
 * 以子内容实测高度为唯一高度来源。
 * 展开后 layout 高度 == 内容高度；动画只裁切已测高度，不再钉窗口、不再填 260dp。
 */
@Composable
private fun OverlaySettingsReveal(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val fraction = remember { Animatable(0f) }
    LaunchedEffect(visible) {
        fraction.animateTo(
            targetValue = if (visible) 1f else 0f,
            animationSpec = if (visible) SettingsRevealSpec else SettingsHideSpec,
        )
    }
    val t = fraction.value
    if (!visible && t <= 0.001f) return
    SubcomposeLayout(modifier.fillMaxWidth().clipToBounds()) { constraints ->
        val width = if (constraints.hasBoundedWidth) {
            constraints.maxWidth
        } else {
            constraints.minWidth
        }
        if (width <= 0) {
            return@SubcomposeLayout layout(0, 0) {}
        }
        val childConstraints = Constraints(
            minWidth = width,
            maxWidth = width,
            minHeight = 0,
            maxHeight = Constraints.Infinity,
        )
        val placeables = subcompose("panel", content).map { it.measure(childConstraints) }
        val contentH = placeables.maxOfOrNull { it.height } ?: 0
        val shownH = if (t >= 0.999f) contentH else (contentH * t).roundToInt().coerceAtLeast(0)
        layout(width, shownH) {
            placeables.forEach { it.placeRelative(0, 0) }
        }
    }
}

@Composable
private fun OverlayToolbar(
    playing: Boolean,
    settingsOpen: Boolean,
    buttonSize: Dp,
    iconSize: Dp,
    onToggleSettings: () -> Unit,
    onTogglePlay: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    onLock: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        OverlayIconBtn(ZIcons.Settings, if (settingsOpen) t("收起设置") else t("设置"), buttonSize, iconSize, onToggleSettings)
        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OverlayIconBtn(ZIcons.SkipPrevious, t("上一首"), buttonSize, iconSize, onSkipPrevious)
            OverlayIconBtn(if (playing) ZIcons.Pause else ZIcons.Play, if (playing) t("暂停") else t("播放"), buttonSize, iconSize, onTogglePlay)
            OverlayIconBtn(ZIcons.SkipNext, t("下一首"), buttonSize, iconSize, onSkipNext)
        }
        OverlayIconBtn(ZIcons.Lock, t("锁定"), buttonSize, iconSize, onLock)
    }
}

@Composable
private fun OverlayIconBtn(
    icon: ImageVector,
    label: String,
    buttonSize: Dp,
    iconSize: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .size(buttonSize)
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(iconSize))
    }
}

private data class OverlayLyricSnap(
    val lines: List<LrcLine>,
    val companions: List<LrcLine?>,
    val active: Int,
    val title: String,
)

@Composable
private fun OverlayLyricLines(
    playbackUi: StateFlow<PlaybackUiState>,
    prefs: LyricOverlayPrefs,
    lyricBackground: Boolean,
    closeGutter: Dp = 0.dp,
    marqueeEnabled: Boolean = true,
) {
    val playerTranslationOn = rememberPortraitTranslationOn()
    val preferTranslation = prefs.preferTranslation || playerTranslationOn
    val transKey = Triple(preferTranslation, prefs.translationCoexist, prefs.originalOnTop)
    val snap by remember(playbackUi, transKey) {
        playbackUi
            .map { ui -> overlayLyricSnap(ui, prefs, preferTranslation) }
            .distinctUntilChanged()
    }.collectAsState(
        initial = overlayLyricSnap(playbackUi.value, prefs, preferTranslation),
    )
    val font = prefs.fontSizeSp.sp
    val transFont = (prefs.fontSizeSp * LyricOverlayPrefs.TRANSLATION_FONT_SCALE).sp
    val lineHeight = (prefs.fontSizeSp * 1.35f).sp
    val transLineHeight = (prefs.fontSizeSp * LyricOverlayPrefs.TRANSLATION_FONT_SCALE * 1.35f).sp
    val density = LocalDensity.current
    val lineMin = with(density) { lineHeight.toDp() }
    val transMin = with(density) { transLineHeight.toDp() }
    val boxAlign = prefs.lineBoxAlign()
    val textAlign = prefs.lineTextAlign()
    val lines = snap.lines
    val companions = snap.companions
    val active = snap.active
    val center = prefs.textAlign == LyricOverlayPrefs.ALIGN_CENTER
    val transColor = Color(prefs.translationColorArgb)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(
                start = if (center) closeGutter else 0.dp,
                end = closeGutter,
            ),
    ) {
        repeat(prefs.playedLines) { i ->
            val idx = active - prefs.playedLines + i
            OverlayLyricSlot(
                rows = overlayLyricRows(
                    lineText = lines.getOrNull(idx)?.text.orEmpty(),
                    companionText = companions.getOrNull(idx)?.text,
                    originalOnTop = prefs.originalOnTop,
                    showCompanion = preferTranslation && prefs.translationCoexist && prefs.othersShowTranslation,
                ),
                roleColor = Color(prefs.playedColorArgb),
                translationColor = transColor,
                fontSize = font,
                translationFontSize = transFont,
                lineMinHeight = lineMin,
                translationMinHeight = transMin,
                lyricBackground = lyricBackground,
                current = false,
                marquee = false,
                boxAlign = boxAlign,
                textAlign = textAlign,
            )
        }
        OverlayLyricSlot(
            rows = overlayLyricRows(
                lineText = when {
                    active >= 0 -> lines[active].text
                    lines.isNotEmpty() -> lines.first().text
                    else -> ""
                },
                companionText = when {
                    active >= 0 -> companions.getOrNull(active)?.text
                    companions.isNotEmpty() -> companions.first()?.text
                    else -> null
                },
                originalOnTop = prefs.originalOnTop,
                showCompanion = preferTranslation && prefs.translationCoexist,
                fallback = snap.title.ifBlank { "♪" },
            ),
            roleColor = Color(prefs.currentColorArgb),
            translationColor = transColor,
            fontSize = font,
            translationFontSize = transFont,
            lineMinHeight = lineMin,
            translationMinHeight = transMin,
            lyricBackground = lyricBackground,
            current = true,
            marquee = marqueeEnabled,
            boxAlign = boxAlign,
            textAlign = textAlign,
        )
        repeat(prefs.upcomingLines) { i ->
            val idx = active + 1 + i
            OverlayLyricSlot(
                rows = overlayLyricRows(
                    lineText = lines.getOrNull(idx)?.text.orEmpty(),
                    companionText = companions.getOrNull(idx)?.text,
                    originalOnTop = prefs.originalOnTop,
                    showCompanion = preferTranslation && prefs.translationCoexist && prefs.othersShowTranslation,
                ),
                roleColor = Color(prefs.upcomingColorArgb),
                translationColor = transColor,
                fontSize = font,
                translationFontSize = transFont,
                lineMinHeight = lineMin,
                translationMinHeight = transMin,
                lyricBackground = lyricBackground,
                current = false,
                marquee = false,
                boxAlign = boxAlign,
                textAlign = textAlign,
            )
        }
    }
}

private fun overlayLyricSnap(
    ui: PlaybackUiState,
    prefs: LyricOverlayPrefs,
    preferTranslation: Boolean,
): OverlayLyricSnap {
    val bundle = pickDisplayLyricBundle(
        original = ui.lyricLines.mapNotNull { it.sanitizedForDisplay() },
        translated = ui.translatedLyricLines.mapNotNull { it.sanitizedForDisplay() },
        wordOriginal = emptyList(),
        wordTranslated = emptyList(),
        preferTranslation = preferTranslation,
        coexist = preferTranslation && prefs.translationCoexist,
        wordByWord = false,
    )
    return OverlayLyricSnap(
        lines = bundle.lines,
        companions = bundle.companions,
        active = lyricActiveIndex(bundle.lines, ui.positionMs),
        title = ui.currentTrack?.name.orEmpty(),
    )
}

@Composable
private fun rememberPortraitTranslationOn(): Boolean {
    val context = LocalContext.current.applicationContext
    val held = produceState(initialValue = false, context) {
        val store = PlayerDisplayPrefsStore(context, PlayerDisplayPrefsStore.PREFS_PORTRAIT)
        fun read() {
            value = store.load().portraitLyricPreferTranslation
        }
        read()
        val sp = context.getSharedPreferences(
            PlayerDisplayPrefsStore.PREFS_PORTRAIT,
            android.content.Context.MODE_PRIVATE,
        )
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == null || key == "portrait_lyric_prefer_translation") read()
        }
        sp.registerOnSharedPreferenceChangeListener(listener)
        awaitDispose { sp.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return held.value
}

@Composable
private fun OverlayLyricSlot(
    rows: List<OverlayLyricRow>,
    roleColor: Color,
    translationColor: Color,
    fontSize: TextUnit,
    translationFontSize: TextUnit,
    lineMinHeight: Dp,
    translationMinHeight: Dp,
    lyricBackground: Boolean,
    current: Boolean,
    marquee: Boolean,
    boxAlign: Alignment,
    textAlign: TextAlign,
) {
    Column(Modifier.fillMaxWidth()) {
        rows.forEach { row ->
            OverlayLine(
                text = row.text,
                color = if (row.translation) translationColor else roleColor,
                fontSize = if (row.translation) translationFontSize else fontSize,
                lyricBackground = lyricBackground,
                current = current,
                marquee = marquee,
                boxAlign = boxAlign,
                textAlign = textAlign,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = if (row.translation) translationMinHeight else lineMinHeight),
            )
        }
    }
}

private fun LyricOverlayPrefs.lineBoxAlign(): Alignment = when (textAlign) {
    LyricOverlayPrefs.ALIGN_CENTER -> Alignment.Center
    LyricOverlayPrefs.ALIGN_RIGHT -> Alignment.CenterEnd
    else -> Alignment.CenterStart
}

private fun LyricOverlayPrefs.lineTextAlign(): TextAlign = when (textAlign) {
    LyricOverlayPrefs.ALIGN_CENTER -> TextAlign.Center
    LyricOverlayPrefs.ALIGN_RIGHT -> TextAlign.End
    else -> TextAlign.Start
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun OverlayLine(
    text: String,
    color: Color,
    fontSize: TextUnit,
    lyricBackground: Boolean,
    current: Boolean,
    marquee: Boolean,
    boxAlign: Alignment,
    textAlign: TextAlign,
    modifier: Modifier = Modifier,
) {
    val bg = if (lyricBackground && text.isNotBlank()) {
        Color(0x66000000)
    } else {
        Color.Transparent
    }
    Box(
        modifier.fillMaxWidth(),
        contentAlignment = boxAlign,
    ) {
        Text(
            text = text,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            textAlign = textAlign,
            style = TextStyle(
                color = color,
                fontSize = fontSize,
                fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal,
            ),
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(bg)
                .padding(horizontal = 4.dp)
                .then(
                    if (marquee && text.isNotBlank()) {
                        Modifier.basicMarquee(iterations = Int.MAX_VALUE)
                    } else {
                        Modifier
                    },
                ),
        )
    }
}
