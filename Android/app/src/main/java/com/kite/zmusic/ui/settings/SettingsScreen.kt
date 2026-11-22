package com.kite.zmusic.ui.settings

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kite.zmusic.BuildConfig
import com.kite.zmusic.R
import com.kite.zmusic.ZMusicApplication
import com.kite.zmusic.data.AppAppearance
import com.kite.zmusic.data.OrientationLock
import com.kite.zmusic.data.ChromeGlassStyle
import com.kite.zmusic.data.LongLyricRender
import com.kite.zmusic.data.LyricWordRender
import com.kite.zmusic.data.MiniQuickSkipAxis
import com.kite.zmusic.data.ServerConfigRepository
import com.kite.zmusic.data.UApiProStore
import com.kite.zmusic.data.platform.MusicPlatform
import com.kite.zmusic.data.platform.MusicPlatformStore
import com.kite.zmusic.i18n.I18n
import com.kite.zmusic.i18n.LanguageChoice
import com.kite.zmusic.i18n.t
import com.kite.zmusic.plugin.PluginEngineVersion
import com.kite.zmusic.ui.common.GlassAlertDialog
import com.kite.zmusic.ui.common.GlassPromptField
import com.kite.zmusic.ui.icons.ZIcons
import com.kite.zmusic.ui.legal.AboutLegalKind
import com.kite.zmusic.ui.legal.AboutLegalPage
import com.kite.zmusic.ui.legal.aboutLegalTitle
import com.kite.zmusic.ui.chrome.ChromeWallpaperBackdrop
import com.kite.zmusic.ui.chrome.chromePage
import com.kite.zmusic.ui.chrome.LocalWallpaperViewport
import com.kite.zmusic.ui.common.predictiveBackLayer
import com.kite.zmusic.ui.common.rememberPredictiveBackUi
import com.kite.zmusic.ui.orientation.OrientationLockApplier
import com.kite.zmusic.ui.main.LandscapeCoverEnter
import com.kite.zmusic.ui.main.LandscapeCoverExit
import com.kite.zmusic.ui.main.LocalWallpaperItemChrome
import com.kite.zmusic.ui.main.MainPalette
import com.kite.zmusic.ui.main.wallpaperItemChrome
import com.kite.zmusic.ui.notice.showIslandNotice
import com.kite.zmusic.ui.player.LongLyricSettingsPage
import com.kite.zmusic.ui.player.LyricMotionSettingsPage
import com.kite.zmusic.ui.player.WordLyricSettingsPage
import com.kite.zmusic.ui.player.lyricMotionLabel
import com.kite.zmusic.ui.player.lyricMotionListSubtitle
import com.kite.zmusic.ui.server.CommunityServerViewModel
import com.kite.zmusic.ui.server.CommunityServerViewModelFactory
import com.kite.zmusic.ui.server.ServerConfigViewModel
import com.kite.zmusic.ui.server.ServerConfigViewModelFactory
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class SettingsAudience {
    Music,
    Podcast,
}

private const val AboutGithubUrl = "https://github.com/AboutUip/ZMusic"
private const val AboutQqGroupId = "1015814598"

private val DrillSlideSpec = tween<IntOffset>(durationMillis = 320, easing = FastOutSlowInEasing)
private val DrillFadeSpec = tween<Float>(durationMillis = 220)

@Composable
fun SettingsScreen(
    contentBottomInset: Dp,
    onBack: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    audience: SettingsAudience = SettingsAudience.Music,
) {
    val podcastAudience = audience == SettingsAudience.Podcast
    val context = LocalContext.current
    val activity = LocalActivity.current ?: context as? Activity
    val landscape = LocalConfiguration.current.orientation ==
        android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val serverConfig = remember { ServerConfigRepository(context.applicationContext) }
    val vm: ServerConfigViewModel = viewModel(
        key = "settings-server",
        factory = ServerConfigViewModelFactory(serverConfig),
    )
    val communityStore = remember {
        (context.applicationContext as ZMusicApplication).communityServerStore
    }
    val communityVm: CommunityServerViewModel = viewModel(
        key = "settings-community-server",
        factory = CommunityServerViewModelFactory(communityStore),
    )
    val uapiProStore = remember {
        (context.applicationContext as ZMusicApplication).uapiProStore
    }
    val betterNcmMarketStore = remember {
        (context.applicationContext as ZMusicApplication).betterNcmMarketStore
    }
    var endpointLabel by remember {
        mutableStateOf(maskEndpoint(serverConfig.currentEndpoint()))
    }
    var communityLabel by remember {
        mutableStateOf(maskEndpoint(communityStore.current()))
    }
    var uapiProSubtitle by remember {
        mutableStateOf(uapiProRowSubtitle(uapiProStore.current()))
    }
    var betterNcmSubtitle by remember {
        mutableStateOf(betterNcmRowSubtitle(betterNcmMarketStore.current()))
    }
    var editServer by remember { mutableStateOf(false) }
    var editCommunity by remember { mutableStateOf(false) }
    var confirmLogout by remember { mutableStateOf(false) }
    var pendingPlatform by remember { mutableStateOf<MusicPlatform?>(null) }
    val platformStore = remember {
        (context.applicationContext as ZMusicApplication).musicPlatformStore
    }
    val customPlaySourceStore = remember {
        (context.applicationContext as ZMusicApplication).customPlaySourceStore
    }
    val platform by platformStore.currentFlow.collectAsStateWithLifecycle()
    var customSourceUrl by remember {
        mutableStateOf(customPlaySourceStore.url())
    }
    val uapiProVisible = remember { MutableTransitionState(false) }
    var uapiProSaveToken by remember { mutableIntStateOf(0) }
    val betterNcmVisible = remember { MutableTransitionState(false) }
    var betterNcmSaveToken by remember { mutableIntStateOf(0) }
    val aboutVisible = remember { MutableTransitionState(false) }
    val changelogVisible = remember { MutableTransitionState(false) }
    val sponsorVisible = remember { MutableTransitionState(false) }
    val partnersVisible = remember { MutableTransitionState(false) }
    val permissionsVisible = remember { MutableTransitionState(false) }
    val privacyVisible = remember { MutableTransitionState(false) }
    val unlockGrayDisclaimerVisible = remember { MutableTransitionState(false) }
    var unlockGrayConfirmVisible by remember { mutableStateOf(false) }
    val qualityVisible = remember { MutableTransitionState(false) }
    val platformVisible = remember { MutableTransitionState(false) }
    val persistentPlaybackVisible = remember { MutableTransitionState(false) }
    val cacheVisible = remember { MutableTransitionState(false) }
    val realtimeCacheVisible = remember { MutableTransitionState(false) }
    val wordLyricVisible = remember { MutableTransitionState(false) }
    val longLyricVisible = remember { MutableTransitionState(false) }
    val lyricMotionVisible = remember { MutableTransitionState(false) }
    val predictiveBackVisible = remember { MutableTransitionState(false) }
    val splashAccelVisible = remember { MutableTransitionState(false) }
    val quickSkipVisible = remember { MutableTransitionState(false) }
    val landscapeModeVisible = remember { MutableTransitionState(false) }
    val ignoreStatusBarVisible = remember { MutableTransitionState(false) }
    val playbackChromeVisible = remember { MutableTransitionState(false) }
    val testPlanVisible = remember { MutableTransitionState(false) }
    val pluginEngineDebugVisible = remember { MutableTransitionState(false) }
    val glassVisible = remember { MutableTransitionState(false) }
    val appearanceVisible = remember { MutableTransitionState(false) }
    val languageVisible = remember { MutableTransitionState(false) }
    val wallpaperVisible = remember { MutableTransitionState(false) }
    var permissionSnapshot by remember { mutableStateOf(AppPermissionSnapshot.read(context)) }
    val audioQualityStore = remember {
        (context.applicationContext as ZMusicApplication).audioQualityStore
    }
    val audioQuality by audioQualityStore.quality.collectAsStateWithLifecycle()
    val persistentPlaybackStore = remember {
        (context.applicationContext as ZMusicApplication).persistentPlaybackStore
    }
    val persistentPlayback by persistentPlaybackStore.enabled.collectAsStateWithLifecycle()
    val privacyStore = remember {
        (context.applicationContext as ZMusicApplication).privacyStore
    }
    val songUnlockGray by privacyStore.songUnlockGray.collectAsStateWithLifecycle()
    val downloadAccelStore = remember {
        (context.applicationContext as ZMusicApplication).downloadAccelStore
    }
    val downloadAccel by downloadAccelStore.enabled.collectAsStateWithLifecycle()
    val app = remember { context.applicationContext as ZMusicApplication }
    val realtimeCacheStore = remember { app.realtimeCacheStore }
    val realtimeCachePrefs by realtimeCacheStore.state.collectAsStateWithLifecycle()
    val realtimeCache = remember { app.realtimeCache }
    val realtimeOccupancy by realtimeCache.occupancy.collectAsStateWithLifecycle()
    val settingsScope = rememberCoroutineScope()
    val predictiveBackStore = remember {
        (context.applicationContext as ZMusicApplication).predictiveBackStore
    }
    val predictiveBack by predictiveBackStore.enabled.collectAsStateWithLifecycle()
    val landscapeModeStore = remember {
        (context.applicationContext as ZMusicApplication).landscapeModeStore
    }
    val landscapeMode by landscapeModeStore.enabled.collectAsStateWithLifecycle()
    val orientationLock by landscapeModeStore.orientationLock.collectAsStateWithLifecycle()
    val ignoreStatusBarStore = remember { app.ignoreStatusBarStore }
    val ignoreStatusBar by ignoreStatusBarStore.enabled.collectAsStateWithLifecycle()
    val playbackChromeStore = remember { app.playbackChromeStore }
    val playbackChrome by playbackChromeStore.mode.collectAsStateWithLifecycle()
    val splashAccelStore = remember { app.splashAccelStore }
    val splashAccel by splashAccelStore.enabled.collectAsStateWithLifecycle()
    val miniQuickSkipStore = remember { app.miniQuickSkipStore }
    val miniQuickSkip by miniQuickSkipStore.state.collectAsStateWithLifecycle()
    val appUpdateStore = remember { app.appUpdateStore }
    val testPlan by appUpdateStore.testPlanFlow.collectAsStateWithLifecycle()
    val pluginDebugStore = remember { app.pluginDebugStore }
    val pluginEngineDebug by pluginDebugStore.enabled.collectAsStateWithLifecycle()
    val pluginSlots by app.pluginEngine.ui.slots.collectAsStateWithLifecycle()
    val pluginSettingRows = remember(pluginSlots) {
        pluginSlots.filter { it.slot == com.kite.zmusic.plugin.PluginUiTree.SLOT_SETTINGS }
    }
    val lyricRenderStore = remember {
        (context.applicationContext as ZMusicApplication).lyricRenderStore
    }
    val lyricWordRender by lyricRenderStore.wordRender.collectAsStateWithLifecycle()
    val longLyric by lyricRenderStore.longLine.collectAsStateWithLifecycle()
    val lyricEnterMotion by lyricRenderStore.enterMotion.collectAsStateWithLifecycle()
    val lyricExitMotion by lyricRenderStore.exitMotion.collectAsStateWithLifecycle()
    val glassStore = remember {
        (context.applicationContext as ZMusicApplication).chromeGlassStore
    }
    val glassStyle by glassStore.style.collectAsStateWithLifecycle()
    val themeStore = remember {
        (context.applicationContext as ZMusicApplication).themeStore
    }
    val appearance by themeStore.appearance.collectAsStateWithLifecycle()
    val languageStore = remember {
        (context.applicationContext as ZMusicApplication).languageStore
    }
    val language by languageStore.language.collectAsStateWithLifecycle()
    val languageChoice by languageStore.choice.collectAsStateWithLifecycle()
    val wallpaperStore = remember {
        (context.applicationContext as ZMusicApplication).chromeWallpaperStore
    }
    val wallpaper by wallpaperStore.state.collectAsStateWithLifecycle()
    var glassDraft by remember { mutableStateOf(glassStyle) }
    var confirmGlassLeave by remember { mutableStateOf(false) }
    var showAppreciate by remember { mutableStateOf(false) }
    var appreciateAlipay by remember { mutableStateOf(false) }
    var settingsQuery by remember { mutableStateOf("") }
    var legalKind by remember { mutableStateOf<AboutLegalKind?>(null) }
    val legalVisible = remember { MutableTransitionState(false) }
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        reveal.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
    }
    LaunchedEffect(aboutVisible.targetState) {
        if (!aboutVisible.targetState) legalVisible.targetState = false
    }
    LaunchedEffect(unlockGrayDisclaimerVisible.targetState) {
        if (!unlockGrayDisclaimerVisible.targetState) {
            unlockGrayConfirmVisible = false
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        permissionSnapshot = AppPermissionSnapshot.read(context)
    }
    fun closePermissions() {
        permissionsVisible.targetState = false
        permissionSnapshot = AppPermissionSnapshot.read(context)
    }
    fun applyGlass() {
        if (glassDraft == glassStyle) return
        glassStore.apply(glassDraft)
        context.showIslandNotice(t("样式已应用"))
    }
    fun closeGlassPage() {
        confirmGlassLeave = false
        glassVisible.targetState = false
    }
    fun requestCloseGlass() {
        if (glassDraft != glassStyle) {
            confirmGlassLeave = true
        } else {
            closeGlassPage()
        }
    }

    Box(
        modifier
            .fillMaxSize()
            .chromePage()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            ),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            SettingsTopBar(
                title = t("设置"),
                onBack = onBack,
            )
            SettingsQueryField(
                query = settingsQuery,
                onQueryChange = { settingsQuery = it },
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .padding(top = 2.dp, bottom = 6.dp),
            )
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(bottom = contentBottomInset + 16.dp),
            ) {
                Spacer(Modifier.height(8.dp))
                val extensionItems = if (podcastAudience) {
                    emptyList()
                } else {
                    listOf(
                        SettingsIndexItem(
                            key = "platform",
                            title = t("客户端平台"),
                            subtitle = platformLabel(platform),
                            icon = ZIcons.Extension,
                            tint = Color(0xFF2F6FED),
                            keywords = t("音源 网易 汽水"),
                            onClick = { platformVisible.targetState = true },
                        ),
                    )
                }
                val connectionItems = buildList {
                    add(
                        SettingsIndexItem(
                            key = "music-server",
                            title = t("音乐服务器"),
                            subtitle = endpointLabel,
                            icon = ZIcons.Server,
                            tint = Color(0xFF5070F0),
                            keywords = t("接口 地址"),
                            onClick = {
                                vm.reloadFromStore()
                                editServer = true
                            },
                        ),
                    )
                    add(
                        SettingsIndexItem(
                            key = "community-server",
                            title = t("社区服务器"),
                            subtitle = communityLabel,
                            icon = ZIcons.Handshake,
                            tint = Color(0xFF6B5CE7),
                            keywords = t("社区"),
                            onClick = {
                                communityVm.reloadFromStore()
                                editCommunity = true
                            },
                        ),
                    )
                    if (!podcastAudience) {
                        add(
                            SettingsIndexItem(
                                key = "uapipro",
                                title = t("UApiPro"),
                                subtitle = uapiProSubtitle,
                                icon = ZIcons.Extension,
                                tint = Color(0xFF0EA5A0),
                                keywords = t("翻译"),
                                onClick = {
                                    uapiProSaveToken = 0
                                    uapiProVisible.targetState = true
                                },
                            ),
                        )
                        add(
                            SettingsIndexItem(
                                key = "betterncm",
                                title = t("BetterNCM"),
                                subtitle = betterNcmSubtitle,
                                icon = ZIcons.Workshop,
                                tint = Color(0xFF7C5CBF),
                                keywords = t("插件市场"),
                                onClick = {
                                    betterNcmSaveToken = 0
                                    betterNcmVisible.targetState = true
                                },
                            ),
                        )
                    }
                }
                val playbackItems = if (podcastAudience) {
                    emptyList()
                } else {
                    listOf(
                        SettingsIndexItem(
                            key = "quality",
                            title = t("音源默认质量"),
                            subtitle = "${audioQuality.title} · ${audioQuality.caption}",
                            icon = ZIcons.GraphicEq,
                            tint = Color(0xFFB08D57),
                            keywords = t("音质"),
                            onClick = { qualityVisible.targetState = true },
                        ),
                        SettingsIndexItem(
                            key = "persistent",
                            title = t("持续播放"),
                            subtitle = if (persistentPlayback) {
                                t("已开启 · 与其他应用同时出声")
                            } else {
                                t("已关闭 · 按系统规则让出")
                            },
                            icon = ZIcons.Headset,
                            tint = Color(0xFF2E9B6B),
                            keywords = t("后台"),
                            onClick = { persistentPlaybackVisible.targetState = true },
                        ),
                        SettingsIndexItem(
                            key = "word-lyric",
                            title = t("逐字歌词"),
                            subtitle = when (lyricWordRender) {
                                LyricWordRender.Word -> t("按字渲染 · 需歌曲提供逐字歌词")
                                LyricWordRender.Width -> t("按字宽渲染 · 需歌曲提供逐字歌词")
                                LyricWordRender.Line -> t("按行渲染")
                            },
                            icon = ZIcons.Lyrics,
                            tint = Color(0xFF5B8DEF),
                            keywords = t("歌词"),
                            onClick = { wordLyricVisible.targetState = true },
                        ),
                        SettingsIndexItem(
                            key = "long-lyric",
                            title = t("长歌词渲染"),
                            subtitle = if (longLyric == LongLyricRender.Horizontal) {
                                t("水平自然滚动")
                            } else {
                                t("换行渲染")
                            },
                            icon = ZIcons.SwapHoriz,
                            tint = Color(0xFF3AAFA9),
                            keywords = t("歌词"),
                            onClick = { longLyricVisible.targetState = true },
                        ),
                        SettingsIndexItem(
                            key = "lyric-motion",
                            title = t("歌词效果"),
                            subtitle = lyricMotionListSubtitle(lyricEnterMotion, lyricExitMotion),
                            icon = ZIcons.Lyrics,
                            tint = Color(0xFF8E6AD8),
                            keywords = t("入场 离场 粒子飘散"),
                            onClick = { lyricMotionVisible.targetState = true },
                        ),
                        SettingsIndexItem(
                            key = "quick-skip",
                            title = t("快速切歌"),
                            subtitle = quickSkipSubtitle(miniQuickSkip),
                            icon = ZIcons.SkipNext,
                            tint = Color(0xFFC45C7A),
                            keywords = t("切歌"),
                            onClick = { quickSkipVisible.targetState = true },
                        ),
                    )
                }
                val cacheItems = if (podcastAudience) {
                    emptyList()
                } else {
                    listOf(
                        SettingsIndexItem(
                            key = "download-accel",
                            title = t("下载加速"),
                            subtitle = if (downloadAccel) {
                                t("已开启 · 命中本机缓存则跳过网络")
                            } else {
                                t("已关闭 · 始终按音质在线拉取")
                            },
                            icon = ZIcons.Speed,
                            tint = Color(0xFF3D9B8F),
                            keywords = t("缓存"),
                            onClick = { cacheVisible.targetState = true },
                        ),
                        SettingsIndexItem(
                            key = "realtime-cache",
                            title = t("实时缓存"),
                            subtitle = if (realtimeCachePrefs.enabled) {
                                t("已开启 · %s模式", realtimeCachePrefs.mode.title)
                            } else {
                                t("已关闭 · 不采样不走本地缓存")
                            },
                            icon = ZIcons.Storage,
                            tint = Color(0xFF5070F0),
                            keywords = t("缓存"),
                            onClick = { realtimeCacheVisible.targetState = true },
                        ),
                    )
                }
                val themeItems = buildList {
                    add(
                        SettingsIndexItem(
                            key = "language",
                            title = t("语言"),
                            subtitle = if (languageChoice == LanguageChoice.Auto) {
                                t("自动 · %s", language.nativeName)
                            } else {
                                language.nativeName
                            },
                            icon = ZIcons.Translate,
                            tint = Color(0xFF3D8BFF),
                            keywords = t("翻译 中文"),
                            onClick = { languageVisible.targetState = true },
                        ),
                    )
                    add(
                        SettingsIndexItem(
                            key = "appearance",
                            title = t("外观"),
                            subtitle = appearance.subtitle,
                            icon = ZIcons.DarkMode,
                            tint = Color(0xFF6B7CFF),
                            keywords = t("深色 浅色 主题 夜间"),
                            onClick = { appearanceVisible.targetState = true },
                        ),
                    )
                    add(
                        SettingsIndexItem(
                            key = "glass",
                            title = t("液态玻璃样式"),
                            subtitle = glassStyle.settingsSubtitle,
                            icon = ZIcons.BlurOn,
                            tint = Color(0xFF2BB3B0),
                            keywords = t("模糊 玻璃 透明"),
                            onClick = {
                                glassDraft = glassStore.current()
                                glassVisible.targetState = true
                            },
                        ),
                    )
                    if (!podcastAudience) {
                        add(
                            SettingsIndexItem(
                                key = "wallpaper",
                                title = t("自定义背景"),
                                subtitle = wallpaper.settingsSubtitle,
                                icon = ZIcons.Wallpaper,
                                tint = Color(0xFF8B6BFF),
                                keywords = t("壁纸"),
                                onClick = { wallpaperVisible.targetState = true },
                            ),
                        )
                    }
                }
                val appItems = buildList {
                    add(
                        SettingsIndexItem(
                            key = "permissions",
                            title = t("权限"),
                            subtitle = permissionSnapshot.subtitle,
                            icon = ZIcons.Security,
                            tint = Color(0xFF5E5CE6),
                            onClick = { permissionsVisible.targetState = true },
                        ),
                    )
                    if (!podcastAudience) {
                        add(
                            SettingsIndexItem(
                                key = "privacy",
                                title = t("隐私"),
                                subtitle = if (songUnlockGray) {
                                    t("歌曲解灰已开启")
                                } else {
                                    t("默认关闭敏感能力")
                                },
                                icon = ZIcons.Lock,
                                tint = Color(0xFF8E4EC6),
                                keywords = t("解灰"),
                                onClick = { privacyVisible.targetState = true },
                            ),
                        )
                        add(
                            SettingsIndexItem(
                                key = "predictive-back",
                                title = t("预测性返回"),
                                subtitle = if (predictiveBack) {
                                    t("已开启 · 侧滑跟手预览")
                                } else {
                                    t("已关闭 · 返回不跟手（默认）")
                                },
                                icon = ZIcons.Swipe,
                                tint = Color(0xFF3D7CFF),
                                keywords = t("手势 返回"),
                                onClick = { predictiveBackVisible.targetState = true },
                            ),
                        )
                    }
                    add(
                        SettingsIndexItem(
                            key = "splash",
                            title = t("加速启动动画"),
                            subtitle = splashAccelSubtitle(splashAccel),
                            icon = ZIcons.Speed,
                            tint = Color(0xFFE07A3D),
                            keywords = t("启动"),
                            onClick = { splashAccelVisible.targetState = true },
                        ),
                    )
                    add(
                        SettingsIndexItem(
                            key = "landscape",
                            title = t("横屏模式"),
                            subtitle = landscapeModeSubtitle(landscapeMode, orientationLock),
                            icon = ZIcons.ScreenRotation,
                            tint = Color(0xFF4A8FA8),
                            keywords = t("旋转 方向 锁定 竖屏"),
                            onClick = { landscapeModeVisible.targetState = true },
                        ),
                    )
                    add(
                        SettingsIndexItem(
                            key = "status-bar",
                            title = t("忽略状态栏"),
                            subtitle = ignoreStatusBarSubtitle(ignoreStatusBar),
                            icon = ZIcons.Fullscreen,
                            tint = Color(0xFF5C6BC0),
                            keywords = t("全屏 沉浸"),
                            onClick = { ignoreStatusBarVisible.targetState = true },
                        ),
                    )
                    if (!podcastAudience) {
                        add(
                            SettingsIndexItem(
                                key = "playback-chrome",
                                title = t("播放组件模式"),
                                subtitle = playbackChromeSubtitle(playbackChrome),
                                icon = ZIcons.MusicNote,
                                tint = Color(0xFFE07A5F),
                                keywords = t("灵动岛 播放条"),
                                onClick = { playbackChromeVisible.targetState = true },
                            ),
                        )
                    }
                }
                val pluginItems = if (podcastAudience) {
                    emptyList()
                } else {
                    pluginSettingRows.map { entry ->
                        SettingsIndexItem(
                            key = "${entry.pluginId}:${entry.id}",
                            title = entry.title,
                            subtitle = entry.subtitle?.takeIf { it.isNotBlank() } ?: entry.pluginName,
                            icon = com.kite.zmusic.ui.plugin.pluginUiIcon(entry.icon),
                            tint = entry.accent?.let { com.kite.zmusic.ui.theme.parseThemeColor(it) }
                                ?: Color(0xFF5E5CE6),
                            keywords = entry.pluginName,
                            onClick = {
                                app.pluginEngine.ui.activateSlot(entry.pluginId, entry.slot, entry.id)
                            },
                        )
                    }
                }
                val aboutItems = buildList {
                    add(
                        SettingsIndexItem(
                            key = "about",
                            title = t("关于"),
                            subtitle = t("版本、开发者与协议"),
                            icon = ZIcons.Info,
                            tint = Color(0xFF5B7CFA),
                            onClick = { aboutVisible.targetState = true },
                        ),
                    )
                    add(
                        SettingsIndexItem(
                            key = "changelog",
                            title = t("更新日志"),
                            subtitle = t("按版本查阅更新预览"),
                            icon = ZIcons.History,
                            tint = Color(0xFF2A9D8F),
                            keywords = t("版本"),
                            onClick = { changelogVisible.targetState = true },
                        ),
                    )
                    add(
                        SettingsIndexItem(
                            key = "test-plan",
                            title = t("参与测试计划"),
                            subtitle = testPlanSubtitle(testPlan),
                            icon = ZIcons.Science,
                            tint = Color(0xFFC9A227),
                            keywords = t("测试"),
                            onClick = { testPlanVisible.targetState = true },
                        ),
                    )
                    if (!podcastAudience) {
                        add(
                            SettingsIndexItem(
                                key = "plugin-debug",
                                title = t("插件引擎调试"),
                                subtitle = pluginEngineDebugSubtitle(pluginEngineDebug),
                                icon = ZIcons.BugReport,
                                tint = Color(0xFF7B6B9E),
                                keywords = t("调试"),
                                onClick = { pluginEngineDebugVisible.targetState = true },
                            ),
                        )
                    }
                    add(
                        SettingsIndexItem(
                            key = "appreciate",
                            title = t("赞赏"),
                            subtitle = t("请小萱喝一口热乎的"),
                            icon = ZIcons.Favorite,
                            tint = Color(0xFFE85D75),
                            onClick = {
                                appreciateAlipay = false
                                showAppreciate = true
                            },
                        ),
                    )
                    add(
                        SettingsIndexItem(
                            key = "sponsors",
                            title = t("赞助名单"),
                            subtitle = t("谢谢投喂的人"),
                            icon = ZIcons.Sponsor,
                            tint = Color(0xFFE0A85C),
                            onClick = { sponsorVisible.targetState = true },
                        ),
                    )
                    add(
                        SettingsIndexItem(
                            key = "partners",
                            title = t("赞助商"),
                            subtitle = t("支持本应用的伙伴"),
                            icon = ZIcons.Handshake,
                            tint = Color(0xFF3478F6),
                            onClick = { partnersVisible.targetState = true },
                        ),
                    )
                }
                val accountItems = if (podcastAudience) {
                    emptyList()
                } else {
                    listOf(
                        SettingsIndexItem(
                            key = "logout",
                            title = t("退出登录"),
                            subtitle = t("当前账号会退出，播放也会停止"),
                            icon = ZIcons.Logout,
                            tint = MainPalette.Accent,
                            keywords = t("注销 账号"),
                            destructive = true,
                            onClick = { confirmLogout = true },
                        ),
                    )
                }
                val settingsGroups = buildList {
                    if (extensionItems.isNotEmpty()) {
                        add(SettingsIndexGroup(t("扩展"), 0f, 0.dp, extensionItems))
                    }
                    add(
                        SettingsIndexGroup(
                            t("连接"),
                            0f,
                            if (extensionItems.isNotEmpty()) 16.dp else 0.dp,
                            connectionItems,
                        ),
                    )
                    if (playbackItems.isNotEmpty()) {
                        add(SettingsIndexGroup(t("播放"), 0.08f, 22.dp, playbackItems))
                    }
                    if (cacheItems.isNotEmpty()) {
                        add(SettingsIndexGroup(t("缓存"), 0.12f, 22.dp, cacheItems))
                    }
                    add(SettingsIndexGroup(t("主题"), 0.14f, 22.dp, themeItems))
                    add(SettingsIndexGroup(t("应用"), 0.20f, 22.dp, appItems))
                    if (pluginItems.isNotEmpty()) {
                        add(SettingsIndexGroup(t("插件"), 0.24f, 22.dp, pluginItems))
                    }
                    add(SettingsIndexGroup("ZMusic", 0.26f, 22.dp, aboutItems))
                    if (accountItems.isNotEmpty()) {
                        add(SettingsIndexGroup(t("账号"), 0.34f, 22.dp, accountItems))
                    }
                }
                val settingsQueryTrimmed = settingsQuery.trim()
                val settingsAnyHit = settingsQueryTrimmed.isEmpty() || settingsGroups.any { group ->
                    group.title.contains(settingsQueryTrimmed, ignoreCase = true) ||
                        group.items.any { it.matches(settingsQueryTrimmed) }
                }
                AnimatedVisibility(
                    visible = settingsQueryTrimmed.isNotEmpty() && !settingsAnyHit,
                    enter = settingsIndexEnter(),
                    exit = settingsIndexExit(),
                ) {
                    SettingsQueryEmpty()
                }
                for (group in settingsGroups) {
                    SettingsIndexedGroup(
                        title = group.title,
                        reveal = reveal.value,
                        delay = group.delay,
                        query = settingsQuery,
                        topGap = group.topGap,
                        items = group.items,
                    )
                }
            }
        }
        SettingsDrillHost(
            visibleState = uapiProVisible,
            landscape = landscape,
            title = t("UApiPro"),
            onBack = { uapiProVisible.targetState = false },
            actionLabel = t("保存"),
            onAction = { uapiProSaveToken += 1 },
        ) {
            UApiProSettingsPage(
                store = uapiProStore,
                contentBottomInset = contentBottomInset,
                saveToken = uapiProSaveToken,
                onSaved = {
                    uapiProSubtitle = uapiProRowSubtitle(uapiProStore.current())
                    uapiProVisible.targetState = false
                    context.showIslandNotice(t("UApiPro 已更新"))
                },
                onError = { msg ->
                    context.showIslandNotice(msg)
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
        SettingsDrillHost(
            visibleState = betterNcmVisible,
            landscape = landscape,
            title = t("BetterNCM"),
            onBack = { betterNcmVisible.targetState = false },
            actionLabel = t("保存"),
            onAction = { betterNcmSaveToken += 1 },
        ) {
            BetterNcmMarketSettingsPage(
                store = betterNcmMarketStore,
                contentBottomInset = contentBottomInset,
                saveToken = betterNcmSaveToken,
                onSaved = {
                    betterNcmSubtitle = betterNcmRowSubtitle(betterNcmMarketStore.current())
                    betterNcmVisible.targetState = false
                    context.showIslandNotice(t("BetterNCM 已更新"))
                },
                onError = { msg ->
                    context.showIslandNotice(msg)
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
        SettingsDrillHost(
            visibleState = platformVisible,
            landscape = landscape,
            title = t("客户端平台"),
            onBack = { platformVisible.targetState = false },
        ) {
            ClientPlatformSettingsPage(
                selected = platform,
                sourceUrl = customSourceUrl,
                onSelect = { item ->
                    if (item == platform) return@ClientPlatformSettingsPage
                    if (item.reloginOnSwitch) {
                        pendingPlatform = item
                    } else {
                        platformStore.set(item)
                        context.showIslandNotice(t("已切换到%s", platformLabel(item)))
                    }
                },
                onSourceUrl = { next ->
                    customSourceUrl = next
                    customPlaySourceStore.set(next)
                },
                contentBottomInset = contentBottomInset,
                modifier = Modifier.fillMaxSize(),
            )
        }
        SettingsDrillHost(
            visibleState = qualityVisible,
            landscape = landscape,
            title = t("音源默认质量"),
            onBack = { qualityVisible.targetState = false },
        ) {
            AudioQualitySettingsPage(
                selected = audioQuality,
                onSelect = { next ->
                    if (next != audioQuality) {
                        audioQualityStore.set(next)
                        context.showIslandNotice(t("已切换到%s", next.title))
                    }
                },
                contentBottomInset = contentBottomInset,
                modifier = Modifier.fillMaxSize(),
            )
        }
        SettingsDrillHost(
            visibleState = persistentPlaybackVisible,
            landscape = landscape,
            title = t("持续播放"),
            onBack = { persistentPlaybackVisible.targetState = false },
        ) {
            PersistentPlaybackSettingsPage(
                enabled = persistentPlayback,
                onEnabledChange = { next ->
                    if (next == persistentPlayback) return@PersistentPlaybackSettingsPage
                    persistentPlaybackStore.setEnabled(next)
                    context.showIslandNotice(if (next) t("已开启持续播放") else t("已关闭持续播放"))
                },
                contentBottomInset = contentBottomInset,
                modifier = Modifier.fillMaxSize(),
            )
        }
        SettingsDrillHost(
            visibleState = wordLyricVisible,
            landscape = landscape,
            title = t("逐字歌词"),
            onBack = { wordLyricVisible.targetState = false },
        ) {
            WordLyricSettingsPage(
                mode = lyricWordRender,
                onModeChange = { next ->
                    if (next == lyricWordRender) return@WordLyricSettingsPage
                    lyricRenderStore.setWordRender(next)
                    context.showIslandNotice(
                        when (next) {
                            LyricWordRender.Word -> t("已切换到按字渲染")
                            LyricWordRender.Width -> t("已切换到按字宽渲染")
                            LyricWordRender.Line -> t("已切换到按行渲染")
                        },
                    )
                },
                contentBottomInset = contentBottomInset,
                modifier = Modifier.fillMaxSize(),
            )
        }
        SettingsDrillHost(
            visibleState = longLyricVisible,
            landscape = landscape,
            title = t("长歌词渲染"),
            onBack = { longLyricVisible.targetState = false },
        ) {
            LongLyricSettingsPage(
                mode = longLyric,
                onModeChange = { next ->
                    if (next == longLyric) return@LongLyricSettingsPage
                    lyricRenderStore.setLongLine(next)
                    context.showIslandNotice(
                        if (next == LongLyricRender.Horizontal) {
                            t("已切换到水平自然滚动")
                        } else {
                            t("已切换到换行渲染")
                        },
                    )
                },
                contentBottomInset = contentBottomInset,
                modifier = Modifier.fillMaxSize(),
            )
        }
        SettingsDrillHost(
            visibleState = lyricMotionVisible,
            landscape = landscape,
            title = t("歌词效果"),
            onBack = { lyricMotionVisible.targetState = false },
        ) {
            LyricMotionSettingsPage(
                enter = lyricEnterMotion,
                exit = lyricExitMotion,
                onEnterChange = { next ->
                    if (next == lyricEnterMotion) return@LyricMotionSettingsPage
                    lyricRenderStore.setEnterMotion(next)
                    context.showIslandNotice(t("歌词入场已改为%s", lyricMotionLabel(next)))
                },
                onExitChange = { next ->
                    if (next == lyricExitMotion) return@LyricMotionSettingsPage
                    lyricRenderStore.setExitMotion(next)
                    context.showIslandNotice(t("歌词离场已改为%s", lyricMotionLabel(next)))
                },
                contentBottomInset = contentBottomInset,
                modifier = Modifier.fillMaxSize(),
            )
        }
        SettingsDrillHost(
            visibleState = quickSkipVisible,
            landscape = landscape,
            title = t("快速切歌"),
            onBack = { quickSkipVisible.targetState = false },
        ) {
            QuickSkipSettingsPage(
                prefs = miniQuickSkip,
                onEnabledChange = { next ->
                    if (next == miniQuickSkip.enabled) return@QuickSkipSettingsPage
                    miniQuickSkipStore.setEnabled(next)
                    context.showIslandNotice(
                        if (next) t("已开启快速切歌") else t("已关闭快速切歌"),
                    )
                },
                onAxisChange = { next ->
                    if (next == miniQuickSkip.axis) return@QuickSkipSettingsPage
                    miniQuickSkipStore.setAxis(next)
                    context.showIslandNotice(
                        if (next == MiniQuickSkipAxis.Vertical) {
                            t("已切换到上下切歌")
                        } else {
                            t("已切换到左右切歌")
                        },
                    )
                },
                contentBottomInset = contentBottomInset,
                modifier = Modifier.fillMaxSize(),
            )
        }
        SettingsDrillHost(
            visibleState = cacheVisible,
            landscape = landscape,
            title = t("下载加速"),
            onBack = { cacheVisible.targetState = false },
        ) {
            CacheSettingsPage(
                downloadAccel = downloadAccel,
                onDownloadAccelChange = { next ->
                    if (next == downloadAccel) return@CacheSettingsPage
                    downloadAccelStore.setEnabled(next)
                    context.showIslandNotice(
                        if (next) t("已开启下载加速") else t("已关闭下载加速"),
                    )
                },
                contentBottomInset = contentBottomInset,
                modifier = Modifier.fillMaxSize(),
            )
        }
        SettingsDrillHost(
            visibleState = realtimeCacheVisible,
            landscape = landscape,
            title = t("实时缓存"),
            onBack = { realtimeCacheVisible.targetState = false },
        ) {
            RealtimeCacheSettingsPage(
                enabled = realtimeCachePrefs.enabled,
                onEnabledChange = { next ->
                    if (next == realtimeCachePrefs.enabled) return@RealtimeCacheSettingsPage
                    realtimeCacheStore.setEnabled(next)
                    context.showIslandNotice(
                        if (next) t("已开启实时缓存") else t("已关闭实时缓存"),
                    )
                },
                mode = realtimeCachePrefs.mode,
                onModeChange = { next ->
                    if (!next.available || next == realtimeCachePrefs.mode) return@RealtimeCacheSettingsPage
                    realtimeCacheStore.setMode(next)
                    context.showIslandNotice(t("已切换到%s模式", next.title))
                },
                spaceValue = realtimeCachePrefs.spaceValue,
                spaceUnit = realtimeCachePrefs.spaceUnit,
                onSpaceChange = { value, unit ->
                    realtimeCacheStore.setSpace(value, unit)
                },
                occupancy = realtimeOccupancy,
                onClearCache = {
                    settingsScope.launch {
                        realtimeCache.clearAudioCache()
                        context.showIslandNotice(t("已清空缓存"))
                    }
                },
                contentBottomInset = contentBottomInset,
                modifier = Modifier.fillMaxSize(),
            )
        }
        SettingsDrillHost(
            visibleState = glassVisible,
            landscape = landscape,
            title = t("液态玻璃样式"),
            onBack = { requestCloseGlass() },
            backEnabled = !confirmGlassLeave,
            actionLabel = t("应用"),
            actionEnabled = glassDraft != glassStyle,
            onAction = { applyGlass() },
        ) {
            LiquidGlassStylePage(
                style = glassDraft,
                applied = glassStyle,
                onMode = { glassDraft = glassDraft.copy(mode = it) },
                onRefraction = { glassDraft = glassDraft.withRefraction(it) },
                onBlur = { glassDraft = glassDraft.withBlur(it) },
                onReset = { glassDraft = ChromeGlassStyle.Default },
                onApply = { applyGlass() },
                contentBottomInset = contentBottomInset,
                modifier = Modifier.fillMaxSize(),
            )
        }
        SettingsDrillHost(
            visibleState = languageVisible,
            landscape = landscape,
            title = t("语言"),
            onBack = { languageVisible.targetState = false },
        ) {
            LanguageSettingsPage(
                selected = languageChoice,
                resolved = language,
                onSelect = { next ->
                    if (next == languageChoice) return@LanguageSettingsPage
                    languageStore.set(next)
                    I18n.applyToApp(languageStore.current())
                    I18n.relaunchApp(context)
                },
                contentBottomInset = contentBottomInset,
                modifier = Modifier.fillMaxSize(),
            )
        }
        SettingsDrillHost(
            visibleState = appearanceVisible,
            landscape = landscape,
            title = t("外观"),
            onBack = { appearanceVisible.targetState = false },
        ) {
            AppearanceSettingsPage(
                selected = appearance,
                onSelect = { next ->
                    if (next == appearance) return@AppearanceSettingsPage
                    themeStore.set(next)
                    context.showIslandNotice(
                        when (next) {
                            AppAppearance.Light -> t("已切换到浅色")
                            AppAppearance.Dark -> t("已切换到深色")
                            AppAppearance.System -> t("已跟随系统外观")
                        },
                    )
                },
                contentBottomInset = contentBottomInset,
                modifier = Modifier.fillMaxSize(),
            )
        }
        SettingsDrillHost(
            visibleState = wallpaperVisible,
            landscape = landscape,
            title = t("自定义背景"),
            onBack = { wallpaperVisible.targetState = false },
            skipWallpaper = true,
        ) {
            ChromeWallpaperSettingsPage(
                state = wallpaper,
                store = wallpaperStore,
                contentBottomInset = contentBottomInset,
                modifier = Modifier.fillMaxSize(),
            )
        }
        SettingsDrillHost(
            visibleState = aboutVisible,
            landscape = landscape,
            title = t("关于"),
            onBack = {
                legalVisible.targetState = false
                aboutVisible.targetState = false
            },
            backEnabled = !legalVisible.targetState && !showAppreciate,
        ) {
            AboutPage(
                contentBottomInset = contentBottomInset,
                onOpenLegal = { kind ->
                    legalKind = kind
                    legalVisible.targetState = true
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
        SettingsDrillHost(
            visibleState = legalVisible,
            landscape = landscape,
            title = legalKind?.let { aboutLegalTitle(it) }.orEmpty(),
            onBack = { legalVisible.targetState = false },
            coverZIndex = 5f,
        ) {
            val kind = legalKind
            if (kind != null) {
                AboutLegalPage(
                    kind = kind,
                    contentBottomInset = contentBottomInset,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        SettingsDrillHost(
            visibleState = changelogVisible,
            landscape = landscape,
            title = t("更新日志"),
            onBack = { changelogVisible.targetState = false },
        ) {
            ChangelogPage(
                contentBottomInset = contentBottomInset,
                modifier = Modifier.fillMaxSize(),
            )
        }
        SettingsDrillHost(
            visibleState = sponsorVisible,
            landscape = landscape,
            title = t("赞助名单"),
            onBack = { sponsorVisible.targetState = false },
        ) {
            SponsorListPage(
                contentBottomInset = contentBottomInset,
                modifier = Modifier.fillMaxSize(),
            )
        }
        SettingsDrillHost(
            visibleState = partnersVisible,
            landscape = landscape,
            title = t("赞助商"),
            onBack = { partnersVisible.targetState = false },
        ) {
            PartnerListPage(
                contentBottomInset = contentBottomInset,
                modifier = Modifier.fillMaxSize(),
            )
        }
        SettingsDrillHost(
            visibleState = permissionsVisible,
            landscape = landscape,
            title = t("权限"),
            onBack = { closePermissions() },
        ) {
            PermissionSettingsPage(
                contentBottomInset = contentBottomInset,
                modifier = Modifier.fillMaxSize(),
            )
        }
        SettingsDrillHost(
            visibleState = privacyVisible,
            landscape = landscape,
            title = t("隐私"),
            onBack = {
                unlockGrayDisclaimerVisible.targetState = false
                privacyVisible.targetState = false
            },
            backEnabled = !unlockGrayDisclaimerVisible.targetState,
        ) {
            PrivacySettingsPage(
                songUnlockGray = songUnlockGray,
                onSongUnlockGrayChange = { want ->
                    if (want) {
                        unlockGrayDisclaimerVisible.targetState = true
                    } else {
                        privacyStore.setSongUnlockGray(false)
                    }
                },
                contentBottomInset = contentBottomInset,
                modifier = Modifier.fillMaxSize(),
            )
        }
        SettingsDrillHost(
            visibleState = unlockGrayDisclaimerVisible,
            landscape = landscape,
            title = t("歌曲解灰"),
            onBack = {
                if (unlockGrayConfirmVisible) {
                    unlockGrayConfirmVisible = false
                } else {
                    unlockGrayDisclaimerVisible.targetState = false
                }
            },
            coverZIndex = 5f,
        ) {
            UnlockGrayDisclaimerPage(
                contentBottomInset = contentBottomInset,
                onRequestConfirm = { unlockGrayConfirmVisible = true },
                onCancel = { unlockGrayDisclaimerVisible.targetState = false },
                modifier = Modifier.fillMaxSize(),
            )
        }
        UnlockGrayConfirmOverlay(
            visible = unlockGrayConfirmVisible,
            landscape = landscape,
            onConfirm = {
                if (!unlockGrayConfirmVisible) return@UnlockGrayConfirmOverlay
                unlockGrayConfirmVisible = false
                settingsScope.launch {
                    delay(180)
                    privacyStore.setSongUnlockGray(true)
                    unlockGrayDisclaimerVisible.targetState = false
                    context.showIslandNotice(t("已强制开启歌曲解灰"))
                }
            },
            onDismiss = { unlockGrayConfirmVisible = false },
        )
        SettingsDrillHost(
            visibleState = predictiveBackVisible,
            landscape = landscape,
            title = t("预测性返回"),
            onBack = { predictiveBackVisible.targetState = false },
        ) {
            PredictiveBackSettingsPage(
                enabled = predictiveBack,
                onEnabledChange = { next ->
                    if (next == predictiveBack) return@PredictiveBackSettingsPage
                    predictiveBackStore.setEnabled(next)
                    context.showIslandNotice(
                        if (next) t("已开启预测性返回") else t("已关闭预测性返回"),
                    )
                },
                contentBottomInset = contentBottomInset,
                modifier = Modifier.fillMaxSize(),
            )
        }
        SettingsDrillHost(
            visibleState = splashAccelVisible,
            landscape = landscape,
            title = t("加速启动动画"),
            onBack = { splashAccelVisible.targetState = false },
        ) {
            SplashAccelSettingsPage(
                enabled = splashAccel,
                onEnabledChange = { next ->
                    if (next == splashAccel) return@SplashAccelSettingsPage
                    splashAccelStore.setEnabled(next)
                    context.showIslandNotice(
                        if (next) t("已开启加速启动动画") else t("已关闭加速启动动画"),
                    )
                },
                contentBottomInset = contentBottomInset,
                modifier = Modifier.fillMaxSize(),
            )
        }
        SettingsDrillHost(
            visibleState = landscapeModeVisible,
            landscape = landscape,
            title = t("横屏模式"),
            onBack = { landscapeModeVisible.targetState = false },
        ) {
            LandscapeModeSettingsPage(
                enabled = landscapeMode,
                onEnabledChange = { next ->
                    if (orientationLock != OrientationLock.Auto) return@LandscapeModeSettingsPage
                    if (next == landscapeMode) return@LandscapeModeSettingsPage
                    landscapeModeStore.setEnabled(next)
                    context.showIslandNotice(
                        if (next) t("已开启横屏模式") else t("已关闭横屏模式"),
                    )
                },
                orientationLock = orientationLock,
                onOrientationLockChange = { next ->
                    if (next == orientationLock) return@LandscapeModeSettingsPage
                    landscapeModeStore.setOrientationLock(next)
                    activity?.let { OrientationLockApplier.apply(it, next) }
                    context.showIslandNotice(
                        when (next) {
                            OrientationLock.Auto -> t("方向已恢复自动")
                            OrientationLock.Portrait -> t("已锁定竖屏")
                            OrientationLock.Landscape -> t("已锁定横屏")
                        },
                    )
                },
                showOrientationLock = !podcastAudience,
                contentBottomInset = contentBottomInset,
                modifier = Modifier.fillMaxSize(),
            )
        }
        SettingsDrillHost(
            visibleState = ignoreStatusBarVisible,
            landscape = landscape,
            title = t("忽略状态栏"),
            onBack = { ignoreStatusBarVisible.targetState = false },
        ) {
            IgnoreStatusBarSettingsPage(
                enabled = ignoreStatusBar,
                onEnabledChange = { next ->
                    if (next == ignoreStatusBar) return@IgnoreStatusBarSettingsPage
                    ignoreStatusBarStore.setEnabled(next)
                    context.showIslandNotice(
                        if (next) t("已开启忽略状态栏") else t("已关闭忽略状态栏"),
                    )
                },
                contentBottomInset = contentBottomInset,
                modifier = Modifier.fillMaxSize(),
            )
        }
        SettingsDrillHost(
            visibleState = playbackChromeVisible,
            landscape = landscape,
            title = t("播放组件模式"),
            onBack = { playbackChromeVisible.targetState = false },
        ) {
            PlaybackChromeSettingsPage(
                mode = playbackChrome,
                onSelect = { next ->
                    if (next == playbackChrome) return@PlaybackChromeSettingsPage
                    playbackChromeStore.setMode(next)
                    context.showIslandNotice(
                        if (next == com.kite.zmusic.data.PlaybackChromeMode.Island) {
                            t("已切换到灵动岛")
                        } else {
                            t("已切换到默认组件")
                        },
                    )
                },
                contentBottomInset = contentBottomInset,
                modifier = Modifier.fillMaxSize(),
            )
        }
        SettingsDrillHost(
            visibleState = testPlanVisible,
            landscape = landscape,
            title = t("参与测试计划"),
            onBack = { testPlanVisible.targetState = false },
        ) {
            TestPlanSettingsPage(
                enabled = testPlan,
                onEnabledChange = { next ->
                    if (next == testPlan) return@TestPlanSettingsPage
                    appUpdateStore.testPlan = next
                    context.showIslandNotice(
                        if (next) t("已开启测试计划") else t("已关闭测试计划"),
                    )
                },
                contentBottomInset = contentBottomInset,
                modifier = Modifier.fillMaxSize(),
            )
        }
        SettingsDrillHost(
            visibleState = pluginEngineDebugVisible,
            landscape = landscape,
            title = t("插件引擎调试"),
            onBack = { pluginEngineDebugVisible.targetState = false },
        ) {
            PluginEngineDebugSettingsPage(
                enabled = pluginEngineDebug,
                onEnabledChange = { next ->
                    if (next == pluginEngineDebug) return@PluginEngineDebugSettingsPage
                    pluginDebugStore.setEnabled(next)
                    context.showIslandNotice(
                        if (next) t("已开启") else t("已关闭"),
                    )
                },
                contentBottomInset = contentBottomInset,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

    if (editServer) {
        GlassAlertDialog(
            title = t("音乐服务器"),
            message = t("测试通过后才会保存"),
            confirmLabel = t("保存"),
            confirmEnabled = !vm.busy,
            onConfirm = {
                vm.saveAndConnect {
                    endpointLabel = maskEndpoint(serverConfig.currentEndpoint())
                    editServer = false
                    context.showIslandNotice(t("服务器已更新"))
                }
            },
            onDismiss = { if (!vm.busy) editServer = false },
            extraContent = {
                GlassPromptField(
                    value = vm.host,
                    onValueChange = vm::onHostChange,
                    placeholder = t("URL / 域名 / IP"),
                    maxLength = 2048,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Next,
                    ),
                )
                Spacer(Modifier.height(8.dp))
                GlassPromptField(
                    value = vm.portText,
                    onValueChange = vm::onPortChange,
                    placeholder = t("端口"),
                    maxLength = 5,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done,
                    ),
                )
                vm.bannerError?.let { err ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = err,
                        style = TextStyle(
                            color = MainPalette.Accent,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            textAlign = TextAlign.Center,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                vm.statusHint?.let { hint ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = hint,
                        style = TextStyle(
                            color = MainPalette.Secondary,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
        )
    }
    if (editCommunity) {
        GlassAlertDialog(
            title = t("社区服务器"),
            message = t("仅用于社区登录提交，与音乐服务器分开。测试通过后才会保存。"),
            confirmLabel = t("保存"),
            confirmEnabled = !communityVm.busy,
            onConfirm = {
                communityVm.saveAndConnect {
                    communityLabel = maskEndpoint(communityStore.current())
                    editCommunity = false
                    context.showIslandNotice(t("社区服务器已更新"))
                }
            },
            onDismiss = { if (!communityVm.busy) editCommunity = false },
            extraContent = {
                GlassPromptField(
                    value = communityVm.host,
                    onValueChange = communityVm::onHostChange,
                    placeholder = t("URL / 域名 / IP"),
                    maxLength = 2048,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Next,
                    ),
                )
                Spacer(Modifier.height(8.dp))
                GlassPromptField(
                    value = communityVm.portText,
                    onValueChange = communityVm::onPortChange,
                    placeholder = t("端口"),
                    maxLength = 5,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done,
                    ),
                )
                communityVm.bannerError?.let { err ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = err,
                        style = TextStyle(
                            color = MainPalette.Accent,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            textAlign = TextAlign.Center,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                communityVm.statusHint?.let { hint ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = hint,
                        style = TextStyle(
                            color = MainPalette.Secondary,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
        )
    }
    pendingPlatform?.let { next ->
        GlassAlertDialog(
            title = t("切换客户端平台"),
            message = t("应用后将自动重启 ZMusic，以便重新登录%s。", platformLabel(next)),
            confirmLabel = t("重启并切换"),
            onConfirm = {
                val target = next
                platformStore.setBlocking(target)
                pendingPlatform = null
                MusicPlatformStore.restart(context)
            },
            onDismiss = { pendingPlatform = null },
        )
    }
    if (confirmLogout) {
        GlassAlertDialog(
            title = t("退出登录"),
            message = t("当前账号会退出，播放也会停止。"),
            confirmLabel = t("退出"),
            confirmDestructive = true,
            onConfirm = {
                confirmLogout = false
                onLogout()
            },
            onDismiss = { confirmLogout = false },
        )
    }
    if (showAppreciate) {
        GlassAlertDialog(
            title = t("感谢投喂小小萱哦～"),
            message = t("微信或支付宝扫一扫，就像把热乎的奶茶递到小萱手边。不投喂也没关系，你愿意听，他就已经很开心了。"),
            confirmLabel = t("收下这份心意"),
            cancelLabel = null,
            onConfirm = { showAppreciate = false },
            onDismiss = { showAppreciate = false },
            extraContent = {
                val landscape = LocalConfiguration.current.orientation ==
                    android.content.res.Configuration.ORIENTATION_LANDSCAPE
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                ) {
                    AppreciateChannel(
                        label = t("微信"),
                        selected = !appreciateAlipay,
                        onClick = { appreciateAlipay = false },
                    )
                    AppreciateChannel(
                        label = t("支付宝"),
                        selected = appreciateAlipay,
                        onClick = { appreciateAlipay = true },
                    )
                }
                Spacer(Modifier.height(8.dp))
                Image(
                    painter = painterResource(
                        if (appreciateAlipay) {
                            R.drawable.img_alipay_appreciate
                        } else {
                            R.drawable.img_wechat_appreciate
                        },
                    ),
                    contentDescription = if (appreciateAlipay) {
                        t("小萱baibai 的支付宝收款码")
                    } else {
                        t("小萱baibai 的微信赞赏码")
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = if (landscape) 148.dp else 260.dp)
                        .clip(RoundedCornerShape(18.dp)),
                    contentScale = ContentScale.Fit,
                )
            },
        )
    }
    if (confirmGlassLeave) {
        GlassAlertDialog(
            title = t("保存这次调整？"),
            message = t("还没应用到 Dock、迷你条、弹窗和灵动岛。"),
            confirmLabel = t("保存"),
            cancelLabel = t("忽略"),
            onConfirm = {
                applyGlass()
                closeGlassPage()
            },
            onDismiss = { closeGlassPage() },
        )
    }
}

@Composable
private fun AppreciateChannel(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Text(
        text = label,
        color = if (selected) Color.White else MainPalette.Ink,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) MainPalette.Accent else MainPalette.TrackOff)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 6.dp),
    )
}

@Composable
private fun SettingsDrillHost(
    visibleState: MutableTransitionState<Boolean>,
    landscape: Boolean,
    title: String,
    onBack: () -> Unit,
    actionLabel: String? = null,
    actionEnabled: Boolean = true,
    onAction: (() -> Unit)? = null,
    backEnabled: Boolean = true,
    skipWallpaper: Boolean = false,
    coverZIndex: Float = 4f,
    content: @Composable () -> Unit,
) {
    val backUi = rememberPredictiveBackUi(
        enabled = visibleState.targetState && backEnabled,
        onBack = onBack,
    )
    val covering = visibleState.currentState || visibleState.targetState
    AnimatedVisibility(
        visibleState = visibleState,
        modifier = Modifier
            .fillMaxSize()
            .zIndex(if (covering) coverZIndex else 0f)
            .predictiveBackLayer(backUi),
        enter = if (landscape) {
            LandscapeCoverEnter
        } else {
            slideInHorizontally(DrillSlideSpec) { it } + fadeIn(DrillFadeSpec)
        },
        exit = if (landscape) {
            LandscapeCoverExit
        } else {
            slideOutHorizontally(DrillSlideSpec) { it } + fadeOut(DrillFadeSpec)
        },
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
        ) {
            if (skipWallpaper) {
                Box(Modifier.fillMaxSize().background(MainPalette.Page))
            } else {
                ChromeWallpaperBackdrop()
            }
            CompositionLocalProvider(
                LocalWallpaperItemChrome provides if (skipWallpaper) {
                    null
                } else {
                    LocalWallpaperItemChrome.current
                },
                LocalWallpaperViewport provides if (skipWallpaper) {
                    null
                } else {
                    LocalWallpaperViewport.current
                },
            ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding(),
            ) {
            SettingsTopBar(
                title = title,
                onBack = onBack,
                actionLabel = actionLabel,
                actionEnabled = actionEnabled,
                onAction = onAction,
            )
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                content()
            }
            }
            }
        }
    }
}

@Composable
private fun AboutPage(
    contentBottomInset: Dp,
    onOpenLegal: (AboutLegalKind) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val version = BuildConfig.VERSION_NAME
    Column(
        modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp)
            .padding(bottom = contentBottomInset + 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(28.dp))
        Image(
            painter = painterResource(R.drawable.ic_logo_vinyl_z),
            contentDescription = "ZMusic",
            modifier = Modifier
                .size(92.dp)
                .clip(RoundedCornerShape(22.dp)),
            contentScale = ContentScale.Crop,
        )
        Spacer(Modifier.height(18.dp))
        Text(
            text = "ZMusic",
            style = TextStyle(
                color = MainPalette.Ink,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.3.sp,
            ),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = t("给认真听歌的人"),
            style = TextStyle(
                color = MainPalette.Secondary,
                fontSize = 13.sp,
            ),
        )
        Spacer(Modifier.height(18.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AboutBrandIcon(
                drawableRes = R.drawable.ic_brand_github,
                contentDescription = "GitHub",
                onClick = { openAboutGithub(context) },
            )
            AboutBrandIcon(
                drawableRes = R.drawable.ic_brand_tencentqq,
                contentDescription = t("QQ 群"),
                onClick = { openAboutQqGroup(context) },
            )
        }
        Spacer(Modifier.height(28.dp))
        AboutMetaCard(
            rows = listOf(
                t("版本") to version,
                t("插件引擎") to PluginEngineVersion.DISPLAY,
                t("开发者") to t("小萱baibai"),
                t("开源协议") to "GNU GPL-2.0",
            ),
        )
        Spacer(Modifier.height(22.dp))
        Text(
            text = t("使用本软件，即表示你了解并同意下列约定。点开可阅读完整说明。"),
            style = TextStyle(
                color = MainPalette.Secondary,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                textAlign = TextAlign.Center,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(14.dp))
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AboutLegalLink(t("《服务条款》"), onClick = { onOpenLegal(AboutLegalKind.Terms) })
                AboutLegalLink(t("《隐私政策》"), onClick = { onOpenLegal(AboutLegalKind.Privacy) })
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AboutLegalLink(t("《开源许可》"), onClick = { onOpenLegal(AboutLegalKind.License) })
                AboutLegalLink(t("《尊重官方》"), onClick = { onOpenLegal(AboutLegalKind.Official) })
            }
        }
    }
}

@Composable
private fun AboutBrandIcon(
    drawableRes: Int,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(drawableRes),
            contentDescription = contentDescription,
            tint = MainPalette.Ink,
            modifier = Modifier.size(26.dp),
        )
    }
}

private fun openAboutGithub(context: Context) {
    val ok = runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(AboutGithubUrl)))
    }.isSuccess
    if (!ok) context.showIslandNotice(t("无法打开 GitHub"))
}

private fun openAboutQqGroup(context: Context) {
    val uri = Uri.parse(
        "mqqapi://card/show_pslcard?src_type=internal&version=1&uin=$AboutQqGroupId&card_type=group&source=qrcode",
    )
    val opened = runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
    }.isSuccess
    if (opened) return
    runCatching {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(t("QQ群"), AboutQqGroupId))
    }
    context.showIslandNotice(t("未安装 QQ，群号已复制：%s", AboutQqGroupId))
}

@Composable
private fun AboutMetaCard(rows: List<Pair<String, String>>) {
    Column(
        Modifier
            .fillMaxWidth()
            .wallpaperItemChrome(RoundedCornerShape(16.dp))
            .padding(horizontal = 18.dp, vertical = 6.dp),
    ) {
        rows.forEachIndexed { index, (label, value) ->
            if (index > 0) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(0.5.dp)
                        .background(MainPalette.Hairline),
                )
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = label,
                    style = TextStyle(
                        color = MainPalette.Secondary,
                        fontSize = 14.sp,
                    ),
                    modifier = Modifier.width(88.dp),
                )
                Text(
                    text = value,
                    style = TextStyle(
                        color = MainPalette.Ink,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}

@Composable
private fun AboutLegalLink(
    text: String,
    onClick: () -> Unit,
) {
    Text(
        text = text,
        modifier = Modifier.clickable(
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
private fun SettingsTopBar(
    title: String,
    onBack: () -> Unit,
    actionLabel: String? = null,
    actionEnabled: Boolean = true,
    onAction: (() -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onBack,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = ZIcons.Back,
                contentDescription = t("返回"),
                tint = MainPalette.Ink,
                modifier = Modifier.size(22.dp),
            )
        }
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = TextStyle(
                color = MainPalette.Ink,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
        if (actionLabel != null && onAction != null) {
            Text(
                text = actionLabel,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(
                        enabled = actionEnabled,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onAction,
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                style = TextStyle(
                    color = if (actionEnabled) MainPalette.Accent else MainPalette.Hint,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
            )
        }
    }
}

private class SettingsIndexItem(
    val key: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val tint: Color,
    val keywords: String = "",
    val destructive: Boolean = false,
    val onClick: () -> Unit,
) {
    fun matches(query: String): Boolean =
        title.contains(query, ignoreCase = true) ||
            subtitle.contains(query, ignoreCase = true) ||
            keywords.contains(query, ignoreCase = true)
}

private class SettingsIndexGroup(
    val title: String,
    val delay: Float,
    val topGap: Dp,
    val items: List<SettingsIndexItem>,
)

private fun settingsIndexEnter() =
    fadeIn(tween(160, easing = FastOutSlowInEasing)) +
        expandVertically(tween(220, easing = FastOutSlowInEasing), expandFrom = Alignment.Top) +
        slideInVertically(tween(220, easing = FastOutSlowInEasing)) { -it / 4 }

private fun settingsIndexExit() =
    fadeOut(tween(140, easing = FastOutSlowInEasing)) +
        shrinkVertically(tween(220, easing = FastOutSlowInEasing), shrinkTowards = Alignment.Top) +
        slideOutVertically(tween(220, easing = FastOutSlowInEasing)) { -it / 4 }

@Composable
private fun SettingsQueryField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    Row(
        modifier
            .fillMaxWidth()
            .height(44.dp)
            .wallpaperItemChrome(RoundedCornerShape(14.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { focusRequester.requestFocus() },
            )
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = ZIcons.Search,
            contentDescription = null,
            tint = MainPalette.Secondary,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = TextStyle(color = MainPalette.Ink, fontSize = 15.sp),
            cursorBrush = SolidColor(MainPalette.Accent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .focusRequester(focusRequester),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) {
                        Text(
                            text = t("搜索设置"),
                            style = TextStyle(color = MainPalette.Hint, fontSize = 15.sp),
                        )
                    }
                    inner()
                }
            },
        )
        AnimatedVisibility(
            visible = query.isNotEmpty(),
            enter = fadeIn(tween(120)) + expandHorizontally(tween(160, easing = FastOutSlowInEasing)),
            exit = fadeOut(tween(100)) + shrinkHorizontally(tween(140, easing = FastOutSlowInEasing)),
        ) {
            Box(
                Modifier
                    .padding(start = 4.dp)
                    .size(28.dp)
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onQueryChange("") },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = ZIcons.Close,
                    contentDescription = t("清除"),
                    tint = MainPalette.Secondary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun SettingsQueryEmpty() {
    Text(
        text = t("没有匹配的设置"),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 28.dp),
        textAlign = TextAlign.Center,
        style = TextStyle(
            color = MainPalette.Secondary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        ),
    )
}

@Composable
private fun SettingsHairline() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = 62.dp)
            .height(0.5.dp)
            .background(MainPalette.Hairline),
    )
}

@Composable
private fun SettingsIndexedGroup(
    title: String,
    reveal: Float,
    delay: Float,
    query: String,
    topGap: Dp,
    items: List<SettingsIndexItem>,
) {
    if (items.isEmpty()) return
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val trimmed = query.trim()
    val groupHit = trimmed.isNotEmpty() && title.contains(trimmed, ignoreCase = true)
    val matched = items.map { item ->
        trimmed.isEmpty() || groupHit || item.matches(trimmed)
    }
    AnimatedVisibility(
        visible = matched.any { it },
        enter = settingsIndexEnter(),
        exit = settingsIndexExit(),
    ) {
        Column(Modifier.fillMaxWidth()) {
            if (topGap > 0.dp) Spacer(Modifier.height(topGap))
            SettingsGroup(title = title, reveal = reveal, delay = delay) {
                for (index in items.indices) {
                    val item = items[index]
                    key(item.key) {
                        AnimatedVisibility(
                            visible = matched[index],
                            enter = settingsIndexEnter(),
                            exit = settingsIndexExit(),
                        ) {
                            Column(Modifier.fillMaxWidth()) {
                                if (matched.take(index).any { it }) SettingsHairline()
                                SettingsRow(
                                    title = item.title,
                                    subtitle = item.subtitle,
                                    icon = item.icon,
                                    tint = item.tint,
                                    destructive = item.destructive,
                                    onClick = {
                                        focusManager.clearFocus(force = true)
                                        keyboard?.hide()
                                        item.onClick()
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsGroup(
    title: String,
    reveal: Float,
    delay: Float,
    content: @Composable () -> Unit,
) {
    val t = ((reveal - delay) / (1f - delay).coerceAtLeast(0.01f)).coerceIn(0f, 1f)
    Column(
        Modifier.graphicsLayer {
            alpha = t
            translationY = 14.dp.toPx() * (1f - t)
        },
    ) {
        Text(
            text = title,
            style = TextStyle(
                color = MainPalette.Secondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.4.sp,
            ),
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
        )
        Column(
            Modifier
                .fillMaxWidth()
                .wallpaperItemChrome(RoundedCornerShape(16.dp)),
        ) {
            content()
        }
    }
}

@Composable
private fun SettingsRow(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    onClick: () -> Unit,
    destructive: Boolean = false,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(tint.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = TextStyle(
                    color = if (destructive) MainPalette.Accent else MainPalette.Ink,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
            )
            Text(
                text = subtitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(
                    color = MainPalette.Secondary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                ),
            )
        }
        if (!destructive) {
            Icon(
                imageVector = ZIcons.ChevronRight,
                contentDescription = null,
                tint = MainPalette.Hint,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

private fun maskEndpoint(endpoint: ServerConfigRepository.Endpoint): String =
    ServerConfigRepository.maskEndpoint(endpoint)

private fun betterNcmRowSubtitle(baseUrl: String): String {
    val host = runCatching { Uri.parse(baseUrl).host }.getOrNull()?.trim().orEmpty()
    return if (host.isEmpty()) t("未配置") else ServerConfigRepository.maskHost(host)
}

private fun uapiProRowSubtitle(credentials: UApiProStore.Credentials): String {
    val key = credentials.apiKey.trim()
    val url = credentials.baseUrl.trim()
    return when {
        key.isNotEmpty() -> UApiProStore.maskApiKey(key)
        url.isNotEmpty() -> url
        else -> t("未配置")
    }
}
