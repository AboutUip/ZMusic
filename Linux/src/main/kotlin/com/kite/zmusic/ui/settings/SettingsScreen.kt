package com.kite.zmusic.ui.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kite.zmusic.config.NcmApiConfig
import com.kite.zmusic.data.AppPrefs
import com.kite.zmusic.data.AudioQuality
import com.kite.zmusic.data.ChangelogRoster
import com.kite.zmusic.data.CommunityCatalogClient
import com.kite.zmusic.data.GlassStyle
import com.kite.zmusic.data.PartnerRoster
import com.kite.zmusic.data.SponsorRoster
import com.kite.zmusic.ui.chrome.chromeGlassSurface
import com.kite.zmusic.ui.chrome.itemChrome
import com.kite.zmusic.ui.icons.ZIcons
import com.kite.zmusic.ui.main.LandscapeSettingsIconWell
import com.kite.zmusic.ui.main.MainContentPadTop
import com.kite.zmusic.ui.main.mainContentPadH
import com.kite.zmusic.ui.notice.IslandNoticeCenter
import com.kite.zmusic.ui.theme.MainPalette
import java.awt.FileDialog
import java.awt.Frame

private enum class SettingsPage {
    Root, Appearance, Glass, DownloadAccel, RealtimeCache,
    Changelog, Sponsors, Appreciate, Partners, About, Legal,
}

@Composable
fun SettingsScreen(
    prefs: AppPrefs,
    catalog: CommunityCatalogClient,
    notices: IslandNoticeCenter,
    onUpdate: ((AppPrefs) -> AppPrefs) -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var page by remember { mutableStateOf(SettingsPage.Root) }
    AnimatedContent(
        targetState = page,
        transitionSpec = {
            fadeIn(tween(220)) togetherWith fadeOut(tween(160))
        },
        label = "settingsPage",
        modifier = modifier,
    ) { current ->
    when (current) {
        SettingsPage.Root -> SettingsRoot(
            prefs = prefs,
            onUpdate = onUpdate,
            onLogout = onLogout,
            onOpen = { page = it },
            notices = notices,
            modifier = Modifier,
        )
        SettingsPage.Appearance -> AppearanceSettingsPage(
            selected = prefs.appearance,
            onSelect = { next -> onUpdate { it.copy(appearance = next) } },
            onBack = { page = SettingsPage.Root },
        )
        SettingsPage.Glass -> LiquidGlassStylePage(
            style = prefs.glass,
            onMode = { mode -> onUpdate { it.copy(glass = it.glass.copy(mode = mode)) } },
            onRefraction = { v -> onUpdate { it.copy(glass = it.glass.copy(refraction = v)) } },
            onBlur = { v -> onUpdate { it.copy(glass = it.glass.copy(blur = v)) } },
            onReset = { onUpdate { it.copy(glass = GlassStyle()) } },
            onBack = { page = SettingsPage.Root },
        )
        SettingsPage.DownloadAccel -> DownloadAccelSettingsPage(
            enabled = prefs.downloadAccel,
            onEnabled = { v -> onUpdate { it.copy(downloadAccel = v) } },
            glass = prefs.glass,
            onBack = { page = SettingsPage.Root },
        )
        SettingsPage.RealtimeCache -> RealtimeCacheSettingsPage(
            prefs = prefs.realtimeCache,
            glass = prefs.glass,
            onUpdate = { next -> onUpdate { it.copy(realtimeCache = next) } },
            onBack = { page = SettingsPage.Root },
        )
        SettingsPage.Changelog -> CatalogListPage("更新日志", { page = SettingsPage.Root }) {
            val snap = catalog.get("/api/v1/communities/zmusic/releases", "page=1&per_page=20")
            ChangelogRoster.parseRemote(snap).entries.map { "${it.versionLabel}  ${it.notice.ifBlank { it.kind }}" }
        }
        SettingsPage.Sponsors -> CatalogListPage("赞助名单", { page = SettingsPage.Root }) {
            val snap = catalog.get("/api/v1/communities/zmusic/sponsors", "page=1&per_page=40")
            SponsorRoster.parseRemote(snap).entries.map { "${it.name}  ${it.amount}" }
        }
        SettingsPage.Appreciate -> AppreciateSettingsPage { page = SettingsPage.Root }
        SettingsPage.Partners -> CatalogListPage("赞助商", { page = SettingsPage.Root }) {
            val snap = catalog.get("/api/v1/communities/zmusic/vendors", "page=1&per_page=20")
            PartnerRoster.parseRemote(snap).entries.map { it.name }
        }
        SettingsPage.About -> SimplePage("关于", { page = SettingsPage.Root }) {
            Text("ZMusic Linux ${NcmApiConfig.PRODUCT_VERSION}", color = MainPalette.Ink, fontSize = 16.sp)
            Spacer(Modifier.height(8.dp))
            Text("GPL-2.0 · 横屏听歌客户端", color = MainPalette.Secondary, fontSize = 14.sp)
        }
        SettingsPage.Legal -> SimplePage("条款与隐私", { page = SettingsPage.Root }) {
            Text(
                "使用本应用即表示你同意以 GPL-2.0 使用客户端源码。账号与Cookie仅保存在本机加密文件中，不会上传到社区目录。",
                color = MainPalette.Ink,
                fontSize = 14.sp,
            )
        }
    }
    }
}

@Composable
private fun SettingsRoot(
    prefs: AppPrefs,
    onUpdate: ((AppPrefs) -> AppPrefs) -> Unit,
    onLogout: () -> Unit,
    onOpen: (SettingsPage) -> Unit,
    notices: IslandNoticeCenter,
    modifier: Modifier,
) {
    var editingMusic by remember { mutableStateOf(false) }
    var editingCommunity by remember { mutableStateOf(false) }
    val glass = prefs.glass
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = mainContentPadH())
            .padding(top = MainContentPadTop, bottom = 32.dp),
    ) {
        Text(
            "设置",
            style = TextStyle(color = MainPalette.Ink, fontSize = 26.sp, fontWeight = FontWeight.Bold),
        )
        Spacer(Modifier.height(8.dp))
        SettingsGroup("连接", glass) {
            if (editingMusic) {
                ServerField(prefs.musicServer.ifBlank { NcmApiConfig.baseUrl }) { v ->
                    onUpdate { it.copy(musicServer = v.trim()) }
                    editingMusic = false
                }
            } else {
                SettingsRow("服务器", prefs.musicServer.ifBlank { NcmApiConfig.baseUrl }, ZIcons.Server, Color(0xFF5070F0)) {
                    editingMusic = true
                }
            }
            SettingsHairline()
            if (editingCommunity) {
                ServerField(prefs.communityServer) { v ->
                    onUpdate { it.copy(communityServer = v.trim()) }
                    editingCommunity = false
                }
            } else {
                SettingsRow("社区服务器", prefs.communityServer, ZIcons.Cloud, Color(0xFF6B5CE7)) {
                    editingCommunity = true
                }
            }
        }
        SettingsGroup("播放", glass) {
            QualityRow(prefs.audioQuality) { q -> onUpdate { it.copy(audioQuality = q) } }
            SettingsHairline()
            SwitchRow(
                "持续播放",
                if (prefs.persistentPlayback) "已开启 · 与其他应用同时出声" else "已关闭 · 按系统规则让出",
                prefs.persistentPlayback,
                ZIcons.Headset,
                Color(0xFF2E9B6B),
            ) { v -> onUpdate { it.copy(persistentPlayback = v) } }
            SettingsHairline()
            SwitchRow(
                "逐字歌词",
                if (prefs.lyricWordByWord) "按字渲染 · 需歌曲提供逐字歌词" else "按行渲染",
                prefs.lyricWordByWord,
                ZIcons.Lyrics,
                Color(0xFF5B8DEF),
            ) { v -> onUpdate { it.copy(lyricWordByWord = v) } }
        }
        SettingsGroup("缓存", glass) {
            SettingsRow(
                "下载加速",
                if (prefs.downloadAccel) "已开启 · 命中本机缓存则跳过网络" else "已关闭 · 始终按音质在线拉取",
                ZIcons.Speed,
                Color(0xFF3D9B8F),
            ) { onOpen(SettingsPage.DownloadAccel) }
            SettingsHairline()
            SettingsRow(
                "实时缓存",
                prefs.realtimeCache.settingsSubtitle,
                ZIcons.Storage,
                Color(0xFF5070F0),
            ) { onOpen(SettingsPage.RealtimeCache) }
        }
        SettingsGroup("主题", glass) {
            SettingsRow("外观", prefs.appearance.subtitle, ZIcons.DarkMode, Color(0xFF6B7CFF)) {
                onOpen(SettingsPage.Appearance)
            }
            SettingsHairline()
            SettingsRow("液态玻璃样式", prefs.glass.settingsSubtitle, ZIcons.Glass, Color(0xFF2BB3B0)) {
                onOpen(SettingsPage.Glass)
            }
            SettingsHairline()
            SettingsRow(
                "自定义背景",
                prefs.wallpaperPath.substringAfterLast('/', prefs.wallpaperPath).ifBlank { "未选择" },
                ZIcons.Wallpaper,
                Color(0xFF8B6BFF),
            ) {
                val picked = pickImageFile()
                if (picked != null) onUpdate { it.copy(wallpaperPath = picked) }
                else notices.show("未选择图片")
            }
        }
        SettingsGroup("ZMusic", glass) {
            SettingsRow("关于", "版本、开发者与协议", ZIcons.Info, Color(0xFF5B7CFA)) {
                onOpen(SettingsPage.About)
            }
            SettingsHairline()
            SettingsRow("更新日志", "按版本查阅更新预览", ZIcons.History, Color(0xFF2A9D8F)) {
                onOpen(SettingsPage.Changelog)
            }
            SettingsHairline()
            SettingsRow("赞赏", "请小萱喝一口热乎的", ZIcons.Sponsor, Color(0xFFEC4141)) {
                onOpen(SettingsPage.Appreciate)
            }
            SettingsHairline()
            SettingsRow("赞助名单", "谢谢投喂的人", ZIcons.Favorite, Color(0xFFEC4141)) {
                onOpen(SettingsPage.Sponsors)
            }
            SettingsHairline()
            SettingsRow("赞助商", "支持本应用的伙伴", ZIcons.Partners, Color(0xFF5E5CE6)) {
                onOpen(SettingsPage.Partners)
            }
            SettingsHairline()
            SettingsRow("条款与隐私", "服务条款与隐私说明", ZIcons.Legal, Color(0xFF8E8E93)) {
                onOpen(SettingsPage.Legal)
            }
        }
        SettingsGroup("账号", glass) {
            SettingsRow(
                "退出登录",
                "当前账号会退出，播放也会停止",
                ZIcons.Logout,
                MainPalette.Accent,
                destructive = true,
                onClick = onLogout,
            )
        }
    }
}

@Composable
private fun CatalogListPage(title: String, onBack: () -> Unit, load: suspend () -> List<String>) {
    var lines by remember { mutableStateOf(listOf("加载中…")) }
    LaunchedEffect(title) {
        lines = runCatching { load() }.getOrDefault(emptyList()).ifEmpty { listOf("暂时没有内容") }
    }
    SimplePage(title, onBack) {
        lines.forEach { line ->
            Text(
                line,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .itemChrome(RoundedCornerShape(14.dp))
                    .padding(14.dp),
                color = MainPalette.Ink,
                fontSize = 14.sp,
            )
        }
    }
}

@Composable
private fun SimplePage(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = mainContentPadH())
            .padding(top = MainContentPadTop, bottom = 32.dp),
    ) {
        SettingsBack(onBack)
        Text(title, style = TextStyle(color = MainPalette.Ink, fontSize = 26.sp, fontWeight = FontWeight.Bold))
        Spacer(Modifier.height(16.dp))
        content()
    }
}

@Composable
private fun SettingsGroup(
    title: String,
    glass: GlassStyle,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.padding(top = 22.dp)) {
        Text(
            title,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
            style = TextStyle(
                color = MainPalette.Secondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.4.sp,
            ),
        )
        Column(
            Modifier
                .fillMaxWidth()
                .chromeGlassSurface(RoundedCornerShape(16.dp), glass),
            content = content,
        )
    }
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
private fun ServerField(value: String, onCommit: (String) -> Unit) {
    var text by remember { mutableStateOf(value) }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 12.dp),
    ) {
        BasicTextField(
            value = text,
            onValueChange = { text = it },
            textStyle = TextStyle(color = MainPalette.Ink, fontSize = 15.sp),
            cursorBrush = SolidColor(MainPalette.Accent),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "保存",
            color = MainPalette.Accent,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { onCommit(text) },
            ),
        )
    }
}

@Composable
private fun SettingsRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    tint: Color,
    destructive: Boolean = false,
    onClick: () -> Unit,
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
                .size(LandscapeSettingsIconWell)
                .clip(CircleShape)
                .background(tint.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = TextStyle(
                    color = if (destructive) MainPalette.Accent else MainPalette.Ink,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
            )
            Text(
                subtitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(color = MainPalette.Secondary, fontSize = 12.sp, lineHeight = 16.sp),
            )
        }
        if (!destructive) {
            Icon(
                ZIcons.Chevron,
                contentDescription = null,
                tint = MainPalette.Hint,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    value: Boolean,
    icon: ImageVector,
    tint: Color,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { onChange(!value) },
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(LandscapeSettingsIconWell)
                .clip(CircleShape)
                .background(tint.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = TextStyle(color = MainPalette.Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold))
            Text(subtitle, style = TextStyle(color = MainPalette.Secondary, fontSize = 12.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Switch(checked = value, onCheckedChange = onChange)
    }
}

@Composable
private fun QualityRow(current: AudioQuality, onPick: (AudioQuality) -> Unit) {
    val next = AudioQuality.entries.let { e ->
        e[(e.indexOf(current) + 1) % e.size]
    }
    SettingsRow(
        title = "音源默认质量",
        subtitle = "${current.title} · ${current.caption}",
        icon = ZIcons.GraphicEq,
        tint = Color(0xFFB08D57),
        onClick = { onPick(next) },
    )
}

private fun pickImageFile(): String? {
    var path: String? = null
    val run = {
        val frame = Frame()
        try {
            val dialog = FileDialog(frame, "选择横屏背景", FileDialog.LOAD)
            dialog.isVisible = true
            val file = dialog.file
            val dir = dialog.directory
            if (file != null && dir != null) path = java.io.File(dir, file).absolutePath
        } finally {
            frame.dispose()
        }
    }
    if (java.awt.EventQueue.isDispatchThread()) run() else java.awt.EventQueue.invokeAndWait(run)
    return path
}
