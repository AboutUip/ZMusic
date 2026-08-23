package com.kite.zmusic.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.kite.zmusic.data.ChromeGlassStyle
import com.kite.zmusic.data.ServerConfigRepository
import com.kite.zmusic.ui.common.GlassAlertDialog
import com.kite.zmusic.ui.common.GlassPromptField
import com.kite.zmusic.ui.icons.ZIcons
import com.kite.zmusic.ui.legal.AboutLegalGlassBody
import com.kite.zmusic.ui.legal.aboutLegalTitle
import com.kite.zmusic.ui.login.LoginLegalKind
import com.kite.zmusic.ui.chrome.ChromeWallpaperBackdrop
import com.kite.zmusic.ui.chrome.chromePage
import com.kite.zmusic.ui.chrome.LocalWallpaperViewport
import com.kite.zmusic.ui.common.predictiveBackLayer
import com.kite.zmusic.ui.common.rememberPredictiveBackUi
import com.kite.zmusic.ui.main.LandscapeCoverEnter
import com.kite.zmusic.ui.main.LandscapeCoverExit
import com.kite.zmusic.ui.main.LocalWallpaperItemChrome
import com.kite.zmusic.ui.main.MainPalette
import com.kite.zmusic.ui.main.wallpaperItemChrome
import com.kite.zmusic.ui.notice.showIslandNotice
import com.kite.zmusic.ui.player.WordLyricSettingsPage
import com.kite.zmusic.ui.server.CommunityServerViewModel
import com.kite.zmusic.ui.server.CommunityServerViewModelFactory
import com.kite.zmusic.ui.server.ServerConfigViewModel
import com.kite.zmusic.ui.server.ServerConfigViewModelFactory
import kotlinx.coroutines.launch

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
) {
    val context = LocalContext.current
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
    var endpointLabel by remember {
        mutableStateOf(maskEndpoint(serverConfig.currentEndpoint()))
    }
    var communityLabel by remember {
        mutableStateOf(maskEndpoint(communityStore.current()))
    }
    var editServer by remember { mutableStateOf(false) }
    var editCommunity by remember { mutableStateOf(false) }
    var confirmLogout by remember { mutableStateOf(false) }
    val aboutVisible = remember { MutableTransitionState(false) }
    val changelogVisible = remember { MutableTransitionState(false) }
    val sponsorVisible = remember { MutableTransitionState(false) }
    val partnersVisible = remember { MutableTransitionState(false) }
    val permissionsVisible = remember { MutableTransitionState(false) }
    val qualityVisible = remember { MutableTransitionState(false) }
    val persistentPlaybackVisible = remember { MutableTransitionState(false) }
    val cacheVisible = remember { MutableTransitionState(false) }
    val realtimeCacheVisible = remember { MutableTransitionState(false) }
    val wordLyricVisible = remember { MutableTransitionState(false) }
    val predictiveBackVisible = remember { MutableTransitionState(false) }
    val landscapeModeVisible = remember { MutableTransitionState(false) }
    val testPlanVisible = remember { MutableTransitionState(false) }
    val glassVisible = remember { MutableTransitionState(false) }
    val appearanceVisible = remember { MutableTransitionState(false) }
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
    val appUpdateStore = remember { app.appUpdateStore }
    val testPlan by appUpdateStore.testPlanFlow.collectAsStateWithLifecycle()
    val lyricRenderStore = remember {
        (context.applicationContext as ZMusicApplication).lyricRenderStore
    }
    val lyricWordByWord by lyricRenderStore.wordByWord.collectAsStateWithLifecycle()
    val glassStore = remember {
        (context.applicationContext as ZMusicApplication).chromeGlassStore
    }
    val glassStyle by glassStore.style.collectAsStateWithLifecycle()
    val themeStore = remember {
        (context.applicationContext as ZMusicApplication).themeStore
    }
    val appearance by themeStore.appearance.collectAsStateWithLifecycle()
    val wallpaperStore = remember {
        (context.applicationContext as ZMusicApplication).chromeWallpaperStore
    }
    val wallpaper by wallpaperStore.state.collectAsStateWithLifecycle()
    var glassDraft by remember { mutableStateOf(glassStyle) }
    var confirmGlassLeave by remember { mutableStateOf(false) }
    var showAppreciate by remember { mutableStateOf(false) }
    var legalKind by remember { mutableStateOf<LoginLegalKind?>(null) }
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        reveal.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
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
        context.showIslandNotice("样式已应用")
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
            .chromePage(),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            SettingsTopBar(
                title = "设置",
                onBack = onBack,
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
                SettingsGroup(
                    title = "连接",
                    reveal = reveal.value,
                    delay = 0f,
                ) {
                    SettingsRow(
                        title = "服务器",
                        subtitle = endpointLabel,
                        icon = ZIcons.Server,
                        tint = Color(0xFF5070F0),
                        onClick = {
                            vm.reloadFromStore()
                            editServer = true
                        },
                    )
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 62.dp)
                            .height(0.5.dp)
                            .background(MainPalette.Hairline),
                    )
                    SettingsRow(
                        title = "社区服务器",
                        subtitle = communityLabel,
                        icon = ZIcons.Handshake,
                        tint = Color(0xFF6B5CE7),
                        onClick = {
                            communityVm.reloadFromStore()
                            editCommunity = true
                        },
                    )
                }
                Spacer(Modifier.height(22.dp))
                SettingsGroup(
                    title = "播放",
                    reveal = reveal.value,
                    delay = 0.08f,
                ) {
                    SettingsRow(
                        title = "音源默认质量",
                        subtitle = "${audioQuality.title} · ${audioQuality.caption}",
                        icon = ZIcons.GraphicEq,
                        tint = Color(0xFFB08D57),
                        onClick = { qualityVisible.targetState = true },
                    )
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 62.dp)
                            .height(0.5.dp)
                            .background(MainPalette.Hairline),
                    )
                    SettingsRow(
                        title = "持续播放",
                        subtitle = if (persistentPlayback) {
                            "已开启 · 与其他应用同时出声"
                        } else {
                            "已关闭 · 按系统规则让出"
                        },
                        icon = ZIcons.Headset,
                        tint = Color(0xFF2E9B6B),
                        onClick = { persistentPlaybackVisible.targetState = true },
                    )
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 62.dp)
                            .height(0.5.dp)
                            .background(MainPalette.Hairline),
                    )
                    SettingsRow(
                        title = "逐字歌词",
                        subtitle = if (lyricWordByWord) {
                            "按字渲染 · 需歌曲提供逐字歌词"
                        } else {
                            "按行渲染"
                        },
                        icon = ZIcons.Lyrics,
                        tint = Color(0xFF5B8DEF),
                        onClick = { wordLyricVisible.targetState = true },
                    )
                }
                Spacer(Modifier.height(22.dp))
                SettingsGroup(
                    title = "缓存",
                    reveal = reveal.value,
                    delay = 0.12f,
                ) {
                    SettingsRow(
                        title = "下载加速",
                        subtitle = if (downloadAccel) {
                            "已开启 · 命中本机缓存则跳过网络"
                        } else {
                            "已关闭 · 始终按音质在线拉取"
                        },
                        icon = ZIcons.Speed,
                        tint = Color(0xFF3D9B8F),
                        onClick = { cacheVisible.targetState = true },
                    )
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 62.dp)
                            .height(0.5.dp)
                            .background(MainPalette.Hairline),
                    )
                    SettingsRow(
                        title = "实时缓存",
                        subtitle = if (realtimeCachePrefs.enabled) {
                            "已开启 · ${realtimeCachePrefs.mode.title}模式"
                        } else {
                            "已关闭 · 不采样不走本地缓存"
                        },
                        icon = ZIcons.Storage,
                        tint = Color(0xFF5070F0),
                        onClick = { realtimeCacheVisible.targetState = true },
                    )
                }
                Spacer(Modifier.height(22.dp))
                SettingsGroup(
                    title = "主题",
                    reveal = reveal.value,
                    delay = 0.14f,
                ) {
                    SettingsRow(
                        title = "外观",
                        subtitle = appearance.subtitle,
                        icon = ZIcons.DarkMode,
                        tint = Color(0xFF6B7CFF),
                        onClick = { appearanceVisible.targetState = true },
                    )
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 62.dp)
                            .height(0.5.dp)
                            .background(MainPalette.Hairline),
                    )
                    SettingsRow(
                        title = "液态玻璃样式",
                        subtitle = glassStyle.settingsSubtitle,
                        icon = ZIcons.BlurOn,
                        tint = Color(0xFF2BB3B0),
                        onClick = {
                            glassDraft = glassStore.current()
                            glassVisible.targetState = true
                        },
                    )
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 62.dp)
                            .height(0.5.dp)
                            .background(MainPalette.Hairline),
                    )
                    SettingsRow(
                        title = "自定义背景",
                        subtitle = wallpaper.settingsSubtitle,
                        icon = ZIcons.Wallpaper,
                        tint = Color(0xFF8B6BFF),
                        onClick = { wallpaperVisible.targetState = true },
                    )
                }
                Spacer(Modifier.height(22.dp))
                SettingsGroup(
                    title = "应用",
                    reveal = reveal.value,
                    delay = 0.20f,
                ) {
                    SettingsRow(
                        title = "权限",
                        subtitle = permissionSnapshot.subtitle,
                        icon = ZIcons.Security,
                        tint = Color(0xFF5E5CE6),
                        onClick = { permissionsVisible.targetState = true },
                    )
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 62.dp)
                            .height(0.5.dp)
                            .background(MainPalette.Hairline),
                    )
                    SettingsRow(
                        title = "预测性返回",
                        subtitle = if (predictiveBack) {
                            "已开启 · 侧滑跟手预览"
                        } else {
                            "已关闭 · 返回不跟手（默认）"
                        },
                        icon = ZIcons.Swipe,
                        tint = Color(0xFF3D7CFF),
                        onClick = { predictiveBackVisible.targetState = true },
                    )
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 62.dp)
                            .height(0.5.dp)
                            .background(MainPalette.Hairline),
                    )
                    SettingsRow(
                        title = "横屏模式",
                        subtitle = landscapeModeSubtitle(landscapeMode),
                        icon = ZIcons.ScreenRotation,
                        tint = Color(0xFF4A8FA8),
                        onClick = { landscapeModeVisible.targetState = true },
                    )
                }
                Spacer(Modifier.height(22.dp))
                SettingsGroup(
                    title = "ZMusic",
                    reveal = reveal.value,
                    delay = 0.26f,
                ) {
                    SettingsRow(
                        title = "关于",
                        subtitle = "版本、开发者与协议",
                        icon = ZIcons.Info,
                        tint = Color(0xFF5B7CFA),
                        onClick = { aboutVisible.targetState = true },
                    )
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 62.dp)
                            .height(0.5.dp)
                            .background(MainPalette.Hairline),
                    )
                    SettingsRow(
                        title = "更新日志",
                        subtitle = "按版本查阅更新预览",
                        icon = ZIcons.History,
                        tint = Color(0xFF2A9D8F),
                        onClick = { changelogVisible.targetState = true },
                    )
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 62.dp)
                            .height(0.5.dp)
                            .background(MainPalette.Hairline),
                    )
                    SettingsRow(
                        title = "参与测试计划",
                        subtitle = testPlanSubtitle(testPlan),
                        icon = ZIcons.Science,
                        tint = Color(0xFFC9A227),
                        onClick = { testPlanVisible.targetState = true },
                    )
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 62.dp)
                            .height(0.5.dp)
                            .background(MainPalette.Hairline),
                    )
                    SettingsRow(
                        title = "赞赏",
                        subtitle = "请小萱喝一口热乎的",
                        icon = ZIcons.Favorite,
                        tint = Color(0xFFE85D75),
                        onClick = { showAppreciate = true },
                    )
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 62.dp)
                            .height(0.5.dp)
                            .background(MainPalette.Hairline),
                    )
                    SettingsRow(
                        title = "赞助名单",
                        subtitle = "谢谢投喂的人",
                        icon = ZIcons.Sponsor,
                        tint = Color(0xFFE0A85C),
                        onClick = { sponsorVisible.targetState = true },
                    )
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 62.dp)
                            .height(0.5.dp)
                            .background(MainPalette.Hairline),
                    )
                    SettingsRow(
                        title = "赞助商",
                        subtitle = "支持本应用的伙伴",
                        icon = ZIcons.Handshake,
                        tint = Color(0xFF3478F6),
                        onClick = { partnersVisible.targetState = true },
                    )
                }
                Spacer(Modifier.height(22.dp))
                SettingsGroup(
                    title = "账号",
                    reveal = reveal.value,
                    delay = 0.34f,
                ) {
                    SettingsRow(
                        title = "退出登录",
                        subtitle = "当前账号会退出，播放也会停止",
                        icon = ZIcons.Logout,
                        tint = MainPalette.Accent,
                        destructive = true,
                        onClick = { confirmLogout = true },
                    )
                }
            }
        }
        SettingsDrillHost(
            visibleState = qualityVisible,
            landscape = landscape,
            title = "音源默认质量",
            onBack = { qualityVisible.targetState = false },
        ) {
            AudioQualitySettingsPage(
                selected = audioQuality,
                onSelect = { next ->
                    if (next != audioQuality) {
                        audioQualityStore.set(next)
                        context.showIslandNotice("已切换到${next.title}")
                    }
                },
                contentBottomInset = contentBottomInset,
                modifier = Modifier.fillMaxSize(),
            )
        }
        SettingsDrillHost(
            visibleState = persistentPlaybackVisible,
            landscape = landscape,
            title = "持续播放",
            onBack = { persistentPlaybackVisible.targetState = false },
        ) {
            PersistentPlaybackSettingsPage(
                enabled = persistentPlayback,
                onEnabledChange = { next ->
                    if (next == persistentPlayback) return@PersistentPlaybackSettingsPage
                    persistentPlaybackStore.setEnabled(next)
                    context.showIslandNotice(if (next) "已开启持续播放" else "已关闭持续播放")
                },
                contentBottomInset = contentBottomInset,
                modifier = Modifier.fillMaxSize(),
            )
        }
        SettingsDrillHost(
            visibleState = wordLyricVisible,
            landscape = landscape,
            title = "逐字歌词",
            onBack = { wordLyricVisible.targetState = false },
        ) {
            WordLyricSettingsPage(
                wordByWord = lyricWordByWord,
                onWordByWordChange = { next ->
                    if (next == lyricWordByWord) return@WordLyricSettingsPage
                    lyricRenderStore.setWordByWord(next)
                    context.showIslandNotice(if (next) "已切换到按字渲染" else "已切换到按行渲染")
                },
                contentBottomInset = contentBottomInset,
                modifier = Modifier.fillMaxSize(),
            )
        }
        SettingsDrillHost(
            visibleState = cacheVisible,
            landscape = landscape,
            title = "下载加速",
            onBack = { cacheVisible.targetState = false },
        ) {
            CacheSettingsPage(
                downloadAccel = downloadAccel,
                onDownloadAccelChange = { next ->
                    if (next == downloadAccel) return@CacheSettingsPage
                    downloadAccelStore.setEnabled(next)
                    context.showIslandNotice(
                        if (next) "已开启下载加速" else "已关闭下载加速",
                    )
                },
                contentBottomInset = contentBottomInset,
                modifier = Modifier.fillMaxSize(),
            )
        }
        SettingsDrillHost(
            visibleState = realtimeCacheVisible,
            landscape = landscape,
            title = "实时缓存",
            onBack = { realtimeCacheVisible.targetState = false },
        ) {
            RealtimeCacheSettingsPage(
                enabled = realtimeCachePrefs.enabled,
                onEnabledChange = { next ->
                    if (next == realtimeCachePrefs.enabled) return@RealtimeCacheSettingsPage
                    realtimeCacheStore.setEnabled(next)
                    context.showIslandNotice(
                        if (next) "已开启实时缓存" else "已关闭实时缓存",
                    )
                },
                mode = realtimeCachePrefs.mode,
                onModeChange = { next ->
                    if (!next.available || next == realtimeCachePrefs.mode) return@RealtimeCacheSettingsPage
                    realtimeCacheStore.setMode(next)
                    context.showIslandNotice("已切换到${next.title}模式")
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
                        context.showIslandNotice("已清空缓存")
                    }
                },
                contentBottomInset = contentBottomInset,
                modifier = Modifier.fillMaxSize(),
            )
        }
        SettingsDrillHost(
            visibleState = glassVisible,
            landscape = landscape,
            title = "液态玻璃样式",
            onBack = { requestCloseGlass() },
            backEnabled = !confirmGlassLeave,
            actionLabel = "应用",
            actionEnabled = glassDraft != glassStyle,
            onAction = { applyGlass() },
        ) {
            LiquidGlassStylePage(
                style = glassDraft,
                applied = glassStyle,
                onMode = { glassDraft = glassDraft.copy(mode = it) },
                onRefraction = { glassDraft = glassDraft.copy(refraction = it) },
                onBlur = { glassDraft = glassDraft.copy(blur = it) },
                onReset = { glassDraft = ChromeGlassStyle.Default },
                onApply = { applyGlass() },
                contentBottomInset = contentBottomInset,
                modifier = Modifier.fillMaxSize(),
            )
        }
        SettingsDrillHost(
            visibleState = appearanceVisible,
            landscape = landscape,
            title = "外观",
            onBack = { appearanceVisible.targetState = false },
        ) {
            AppearanceSettingsPage(
                selected = appearance,
                onSelect = { next ->
                    if (next == appearance) return@AppearanceSettingsPage
                    themeStore.set(next)
                    context.showIslandNotice(
                        when (next) {
                            AppAppearance.Light -> "已切换到浅色"
                            AppAppearance.Dark -> "已切换到深色"
                            AppAppearance.System -> "已跟随系统外观"
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
            title = "自定义背景",
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
            title = "关于",
            onBack = { aboutVisible.targetState = false },
            backEnabled = legalKind == null && !showAppreciate,
        ) {
            AboutPage(
                contentBottomInset = contentBottomInset,
                onOpenTerms = { legalKind = LoginLegalKind.Terms },
                onOpenPrivacy = { legalKind = LoginLegalKind.Privacy },
                modifier = Modifier.fillMaxSize(),
            )
        }
        SettingsDrillHost(
            visibleState = changelogVisible,
            landscape = landscape,
            title = "更新日志",
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
            title = "赞助名单",
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
            title = "赞助商",
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
            title = "权限",
            onBack = { closePermissions() },
        ) {
            PermissionSettingsPage(
                contentBottomInset = contentBottomInset,
                modifier = Modifier.fillMaxSize(),
            )
        }
        SettingsDrillHost(
            visibleState = predictiveBackVisible,
            landscape = landscape,
            title = "预测性返回",
            onBack = { predictiveBackVisible.targetState = false },
        ) {
            PredictiveBackSettingsPage(
                enabled = predictiveBack,
                onEnabledChange = { next ->
                    if (next == predictiveBack) return@PredictiveBackSettingsPage
                    predictiveBackStore.setEnabled(next)
                    context.showIslandNotice(
                        if (next) "已开启预测性返回" else "已关闭预测性返回",
                    )
                },
                contentBottomInset = contentBottomInset,
                modifier = Modifier.fillMaxSize(),
            )
        }
        SettingsDrillHost(
            visibleState = landscapeModeVisible,
            landscape = landscape,
            title = "横屏模式",
            onBack = { landscapeModeVisible.targetState = false },
        ) {
            LandscapeModeSettingsPage(
                enabled = landscapeMode,
                onEnabledChange = { next ->
                    if (next == landscapeMode) return@LandscapeModeSettingsPage
                    landscapeModeStore.setEnabled(next)
                    context.showIslandNotice(
                        if (next) "已开启横屏模式" else "已关闭横屏模式",
                    )
                },
                contentBottomInset = contentBottomInset,
                modifier = Modifier.fillMaxSize(),
            )
        }
        SettingsDrillHost(
            visibleState = testPlanVisible,
            landscape = landscape,
            title = "参与测试计划",
            onBack = { testPlanVisible.targetState = false },
        ) {
            TestPlanSettingsPage(
                enabled = testPlan,
                onEnabledChange = { next ->
                    if (next == testPlan) return@TestPlanSettingsPage
                    appUpdateStore.testPlan = next
                    context.showIslandNotice(
                        if (next) "已开启测试计划" else "已关闭测试计划",
                    )
                },
                contentBottomInset = contentBottomInset,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

    if (editServer) {
        GlassAlertDialog(
            title = "服务器",
            message = "测试通过后才会保存",
            confirmLabel = "保存",
            confirmEnabled = !vm.busy,
            onConfirm = {
                vm.saveAndConnect {
                    endpointLabel = maskEndpoint(serverConfig.currentEndpoint())
                    editServer = false
                    context.showIslandNotice("服务器已更新")
                }
            },
            onDismiss = { if (!vm.busy) editServer = false },
            extraContent = {
                GlassPromptField(
                    value = vm.host,
                    onValueChange = vm::onHostChange,
                    placeholder = "主机 / IP",
                    maxLength = 253,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Next,
                    ),
                )
                Spacer(Modifier.height(8.dp))
                GlassPromptField(
                    value = vm.portText,
                    onValueChange = vm::onPortChange,
                    placeholder = "端口",
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
            title = "社区服务器",
            message = "仅用于社区登录提交，与音乐服务器分开。测试通过后才会保存。",
            confirmLabel = "保存",
            confirmEnabled = !communityVm.busy,
            onConfirm = {
                communityVm.saveAndConnect {
                    communityLabel = maskEndpoint(communityStore.current())
                    editCommunity = false
                    context.showIslandNotice("社区服务器已更新")
                }
            },
            onDismiss = { if (!communityVm.busy) editCommunity = false },
            extraContent = {
                GlassPromptField(
                    value = communityVm.host,
                    onValueChange = communityVm::onHostChange,
                    placeholder = "主机 / IP",
                    maxLength = 253,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Next,
                    ),
                )
                Spacer(Modifier.height(8.dp))
                GlassPromptField(
                    value = communityVm.portText,
                    onValueChange = communityVm::onPortChange,
                    placeholder = "端口",
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
    if (confirmLogout) {
        GlassAlertDialog(
            title = "退出登录",
            message = "当前账号会退出，播放也会停止。",
            confirmLabel = "退出",
            confirmDestructive = true,
            onConfirm = {
                confirmLogout = false
                onLogout()
            },
            onDismiss = { confirmLogout = false },
        )
    }
    legalKind?.let { kind ->
        GlassAlertDialog(
            title = aboutLegalTitle(kind),
            message = null,
            confirmLabel = "我知道了",
            cancelLabel = null,
            onConfirm = { legalKind = null },
            onDismiss = { legalKind = null },
            extraContent = { AboutLegalGlassBody(kind) },
        )
    }
    if (showAppreciate) {
        GlassAlertDialog(
            title = "感谢投喂小小萱哦～",
            message = "扫一扫这份微信赞赏码，就像把热乎的奶茶递到小萱手边。不投喂也没关系，你愿意听，他就已经很开心了。",
            confirmLabel = "收下这份心意",
            cancelLabel = null,
            onConfirm = { showAppreciate = false },
            onDismiss = { showAppreciate = false },
            extraContent = {
                val landscape = LocalConfiguration.current.orientation ==
                    android.content.res.Configuration.ORIENTATION_LANDSCAPE
                Image(
                    painter = painterResource(R.drawable.img_wechat_appreciate),
                    contentDescription = "小萱baibai 的微信赞赏码",
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
            title = "保存这次调整？",
            message = "还没应用到 Dock、迷你条、弹窗和灵动岛。",
            confirmLabel = "保存",
            cancelLabel = "忽略",
            onConfirm = {
                applyGlass()
                closeGlassPage()
            },
            onDismiss = { closeGlassPage() },
        )
    }
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
            .zIndex(if (covering) 4f else 0f)
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
    onOpenTerms: () -> Unit,
    onOpenPrivacy: () -> Unit,
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
            text = "给认真听歌的人",
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
                contentDescription = "QQ 群",
                onClick = { openAboutQqGroup(context) },
            )
        }
        Spacer(Modifier.height(28.dp))
        AboutMetaCard(
            rows = listOf(
                "版本" to version,
                "开发者" to "小萱baibai",
                "开源协议" to "GNU GPL-2.0",
            ),
        )
        Spacer(Modifier.height(22.dp))
        Text(
            text = "使用本软件，即表示你了解并同意下列约定。点开可阅读完整说明。",
            style = TextStyle(
                color = MainPalette.Secondary,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                textAlign = TextAlign.Center,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(14.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AboutLegalLink("《服务条款》", onClick = onOpenTerms)
            AboutLegalLink("《隐私政策》", onClick = onOpenPrivacy)
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
    if (!ok) context.showIslandNotice("无法打开 GitHub")
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
        cm.setPrimaryClip(ClipData.newPlainText("QQ群", AboutQqGroupId))
    }
    context.showIslandNotice("未安装 QQ，群号已复制：$AboutQqGroupId")
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
                contentDescription = "返回",
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
