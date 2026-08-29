package com.kite.zmusic.ui.settings

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Business
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.Opacity
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.material.icons.outlined.VolunteerActivism
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
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
import com.kite.zmusic.data.AppAppearance
import com.kite.zmusic.data.AppPrefs
import com.kite.zmusic.data.AudioQuality
import com.kite.zmusic.data.ChangelogRoster
import com.kite.zmusic.data.CommunityCatalogClient
import com.kite.zmusic.data.PartnerRoster
import com.kite.zmusic.data.SponsorRoster
import com.kite.zmusic.ui.chrome.itemChrome
import com.kite.zmusic.ui.main.LandscapeSettingsIconWell
import com.kite.zmusic.ui.main.MainContentPadTop
import com.kite.zmusic.ui.main.mainContentPadH
import com.kite.zmusic.ui.notice.IslandNoticeCenter
import com.kite.zmusic.ui.theme.MainPalette
import java.awt.FileDialog
import java.awt.Frame

private enum class SettingsPage { Root, Changelog, Sponsors, Partners, About, Legal }

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
    when (page) {
        SettingsPage.Root -> SettingsRoot(
            prefs = prefs,
            onUpdate = onUpdate,
            onLogout = onLogout,
            onOpen = { page = it },
            notices = notices,
            modifier = modifier,
        )
        SettingsPage.Changelog -> CatalogListPage("更新日志", { page = SettingsPage.Root }) {
            val snap = catalog.get("/api/v1/communities/zmusic/releases", "page=1&per_page=20")
            ChangelogRoster.parseRemote(snap).entries.map { "${it.versionLabel}  ${it.notice.ifBlank { it.kind }}" }
        }
        SettingsPage.Sponsors -> CatalogListPage("赞助名单", { page = SettingsPage.Root }) {
            val snap = catalog.get("/api/v1/communities/zmusic/sponsors", "page=1&per_page=40")
            SponsorRoster.parseRemote(snap).entries.map { "${it.name}  ${it.amount}" }
        }
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
        Spacer(Modifier.height(20.dp))
        Group("连接")
        if (editingMusic) {
            ServerField(prefs.musicServer.ifBlank { NcmApiConfig.baseUrl }) { v ->
                onUpdate { it.copy(musicServer = v.trim()) }
                editingMusic = false
            }
        } else {
            SettingsRow("服务器", prefs.musicServer.ifBlank { NcmApiConfig.baseUrl }, Icons.Outlined.Dns, Color(0xFF5B8DEF)) {
                editingMusic = true
            }
        }
        if (editingCommunity) {
            ServerField(prefs.communityServer) { v ->
                onUpdate { it.copy(communityServer = v.trim()) }
                editingCommunity = false
            }
        } else {
            SettingsRow("社区服务器", prefs.communityServer, Icons.Outlined.Cloud, Color(0xFF30D158)) {
                editingCommunity = true
            }
        }
        Group("播放")
        QualityRow(prefs.audioQuality) { q -> onUpdate { it.copy(audioQuality = q) } }
        SwitchRow("持续播放", "与其它程序同时出声", prefs.persistentPlayback, Icons.Outlined.PlayArrow, Color(0xFF5B8DEF)) { v ->
            onUpdate { it.copy(persistentPlayback = v) }
        }
        SwitchRow("逐字歌词", "有逐字数据时按字高亮", prefs.lyricWordByWord, Icons.Outlined.Subtitles, Color(0xFFFF9500)) { v ->
            onUpdate { it.copy(lyricWordByWord = v) }
        }
        Group("缓存")
        SwitchRow("下载加速", "命中本机文件则直接播", prefs.downloadAccel, Icons.Outlined.Folder, Color(0xFF30D158)) { v ->
            onUpdate { it.copy(downloadAccel = v) }
        }
        SwitchRow("实时缓存", "边播边写入本机", prefs.realtimeCache.enabled, Icons.Outlined.Folder, Color(0xFF64D2FF)) { v ->
            onUpdate { it.copy(realtimeCache = it.realtimeCache.copy(enabled = v)) }
        }
        Group("主题")
        SettingsRow("外观", prefs.appearance.subtitle, Icons.Outlined.Palette, Color(0xFFAF52DE)) {
            onUpdate {
                it.copy(
                    appearance = if (it.appearance == AppAppearance.Light) {
                        AppAppearance.Dark
                    } else {
                        AppAppearance.Light
                    },
                )
            }
        }
        SettingsRow("液态玻璃样式", "折射率与模糊", Icons.Outlined.Opacity, Color(0xFF64D2FF)) {}
        Text("折射率", style = TextStyle(color = MainPalette.Ink, fontSize = 14.sp), modifier = Modifier.padding(top = 4.dp))
        Slider(
            value = prefs.glass.refraction,
            onValueChange = { v -> onUpdate { it.copy(glass = it.glass.copy(refraction = v)) } },
        )
        Text("模糊程度", style = TextStyle(color = MainPalette.Ink, fontSize = 14.sp))
        Slider(
            value = prefs.glass.blur,
            onValueChange = { v -> onUpdate { it.copy(glass = it.glass.copy(blur = v)) } },
        )
        SettingsRow(
            "自定义背景",
            prefs.wallpaperPath.ifBlank { "选择横屏壁纸" },
            Icons.Outlined.Image,
            Color(0xFF8E8E93),
        ) {
            val picked = pickImageFile()
            if (picked != null) onUpdate { it.copy(wallpaperPath = picked) }
            else notices.show("未选择图片")
        }
        Group("ZMusic")
        SettingsRow("关于", "ZMusic Linux ${NcmApiConfig.PRODUCT_VERSION} · GPL-2.0", Icons.Outlined.Info, Color(0xFF5B8DEF)) {
            onOpen(SettingsPage.About)
        }
        SettingsRow("更新日志", "按版本查阅更新预览", Icons.Outlined.History, Color(0xFFFF9500)) {
            onOpen(SettingsPage.Changelog)
        }
        SettingsRow("赞赏", "请小萱喝一口热乎的", Icons.Outlined.VolunteerActivism, Color(0xFFEC4141)) {
            onOpen(SettingsPage.Sponsors)
        }
        SettingsRow("赞助名单", "谢谢投喂的人", Icons.Outlined.Favorite, Color(0xFFEC4141)) {
            onOpen(SettingsPage.Sponsors)
        }
        SettingsRow("赞助商", "支持本应用的伙伴", Icons.Outlined.Business, Color(0xFF5E5CE6)) {
            onOpen(SettingsPage.Partners)
        }
        SettingsRow("条款与隐私", "服务条款与隐私说明", Icons.Outlined.Description, Color(0xFF8E8E93)) {
            onOpen(SettingsPage.Legal)
        }
        Group("账号")
        SettingsRow(
            "退出登录",
            "当前账号会退出，播放也会停止",
            Icons.Outlined.Logout,
            MainPalette.Accent,
            destructive = true,
            onClick = onLogout,
        )
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
        Text(
            "返回",
            color = MainPalette.Accent,
            modifier = Modifier
                .padding(top = 8.dp, bottom = 12.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onBack,
                ),
        )
        Text(title, style = TextStyle(color = MainPalette.Ink, fontSize = 26.sp, fontWeight = FontWeight.Bold))
        Spacer(Modifier.height(16.dp))
        content()
    }
}

@Composable
private fun ServerField(value: String, onCommit: (String) -> Unit) {
    var text by remember { mutableStateOf(value) }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .itemChrome(RoundedCornerShape(16.dp))
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
private fun Group(title: String) {
    Text(
        title,
        modifier = Modifier.padding(top = 18.dp, bottom = 8.dp),
        style = TextStyle(
            color = MainPalette.Secondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        ),
    )
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
            .padding(bottom = 8.dp)
            .itemChrome(RoundedCornerShape(16.dp))
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
                Icons.Outlined.KeyboardArrowRight,
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
            .padding(bottom = 8.dp)
            .itemChrome(RoundedCornerShape(16.dp))
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
        icon = Icons.Outlined.GraphicEq,
        tint = Color(0xFFEC4141),
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
