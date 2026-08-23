package com.kite.zmusic.ui.settings

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.kite.zmusic.ui.icons.ZIcons
import com.kite.zmusic.ui.main.MainPalette
import com.kite.zmusic.ui.main.wallpaperItemChrome
import com.kite.zmusic.ui.notice.showIslandNotice

internal data class AppPermissionSnapshot(
    val notifications: Boolean,
    val backgroundRun: Boolean,
    val camera: Boolean,
    val overlay: Boolean,
    val nearbyDevices: Boolean,
    val installPackages: Boolean,
) {
    val subtitle: String
        get() {
            val missing = buildList {
                if (!notifications) add("通知")
                if (!backgroundRun) add("后台运行")
                if (!camera) add("相机")
                if (!overlay) add("悬浮窗")
                if (!nearbyDevices) add("附近的设备")
                if (!installPackages) add("安装应用")
            }
            return when {
                missing.isEmpty() -> "通知、后台运行、相机、悬浮窗、附近的设备、安装应用均已开启"
                missing.size == 6 -> "通知、后台运行、相机、悬浮窗、附近的设备、安装应用未开启"
                else -> missing.joinToString("、") + "未开启"
            }
        }

    companion object {
        fun read(context: Context) = AppPermissionSnapshot(
            notifications = notificationsEnabled(context),
            backgroundRun = backgroundRunEnabled(context),
            camera = cameraGranted(context),
            overlay = overlayGranted(context),
            nearbyDevices = nearbyDevicesGranted(context),
            installPackages = installPackagesGranted(context),
        )
    }
}

@Composable
internal fun PermissionSettingsPage(
    contentBottomInset: Dp,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var snapshot by remember { mutableStateOf(AppPermissionSnapshot.read(context)) }
    fun refresh() {
        snapshot = AppPermissionSnapshot.read(context)
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        refresh()
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        refresh()
        if (granted && notificationsEnabled(context)) {
            context.showIslandNotice("已开启通知")
        } else {
            context.showIslandNotice("未开启通知时，系统可能在息屏后限制后台播放")
        }
    }
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        refresh()
        if (granted) {
            context.showIslandNotice("已开启相机")
        } else {
            context.showIslandNotice("需要相机权限才能扫码")
        }
    }
    val nearbyLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        refresh()
        if (granted) {
            context.showIslandNotice("已开启附近的设备")
        } else {
            context.showIslandNotice("未开启时，蓝牙耳机可能只显示通用名称")
        }
    }
    val backgroundLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        refresh()
        if (backgroundRunEnabled(context)) {
            context.showIslandNotice("已允许后台运行")
        } else {
            context.showIslandNotice("未忽略电池优化时，系统可能在息屏后停止播放")
        }
    }
    val overlayLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        refresh()
        if (overlayGranted(context)) {
            context.showIslandNotice("已开启悬浮窗")
        } else {
            context.showIslandNotice("未开启悬浮窗时，无法在应用外显示歌词")
        }
    }
    val installLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        refresh()
        if (installPackagesGranted(context)) {
            context.showIslandNotice("已允许安装应用")
        } else {
            context.showIslandNotice("未允许时，无法安装 ZMusic 更新")
        }
    }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = contentBottomInset + 24.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        Text(
            text = "这些权限由系统管理。未开启时可在此申请；已开启可进入系统设置调整。从系统设置返回后，状态会自动刷新。",
            style = TextStyle(
                color = MainPalette.Secondary,
                fontSize = 12.sp,
                lineHeight = 18.sp,
            ),
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Spacer(Modifier.height(18.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .wallpaperItemChrome(RoundedCornerShape(16.dp)),
        ) {
            PermissionManageRow(
                title = "通知",
                purpose = "媒体通知与息屏后播放",
                granted = snapshot.notifications,
                icon = ZIcons.Notifications,
                tint = Color(0xFF5070F0),
                onClick = {
                    handleNotificationClick(
                        context = context,
                        enabled = snapshot.notifications,
                        launcher = { notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                    )
                },
            )
            PermissionRowDivider()
            PermissionManageRow(
                title = "后台运行",
                purpose = "忽略电池优化，减少息屏后被系统杀掉",
                granted = snapshot.backgroundRun,
                icon = ZIcons.Battery,
                tint = Color(0xFFFF9500),
                onClick = {
                    handleBackgroundClick(
                        context = context,
                        enabled = snapshot.backgroundRun,
                        launch = { backgroundLauncher.launch(it) },
                    )
                },
            )
            PermissionRowDivider()
            PermissionManageRow(
                title = "相机",
                purpose = "扫描播放器显示配置二维码",
                granted = snapshot.camera,
                icon = ZIcons.Camera,
                tint = Color(0xFF30B0C7),
                onClick = {
                    handleCameraClick(
                        context = context,
                        granted = snapshot.camera,
                        launcher = { cameraLauncher.launch(Manifest.permission.CAMERA) },
                    )
                },
            )
            PermissionRowDivider()
            PermissionManageRow(
                title = "悬浮窗",
                purpose = "通知栏歌词显示（仅应用外）",
                granted = snapshot.overlay,
                icon = ZIcons.Lyrics,
                tint = Color(0xFF7C5CE6),
                onClick = {
                    handleOverlayClick(
                        context = context,
                        granted = snapshot.overlay,
                        launch = { overlayLauncher.launch(it) },
                    )
                },
            )
            PermissionRowDivider()
            PermissionManageRow(
                title = "附近的设备",
                purpose = "识别耳机、音箱等音频输出设备名称",
                granted = snapshot.nearbyDevices,
                icon = ZIcons.Bluetooth,
                tint = Color(0xFF0A84FF),
                onClick = {
                    handleNearbyDevicesClick(
                        context = context,
                        granted = snapshot.nearbyDevices,
                        launcher = {
                            if (Build.VERSION.SDK_INT >= 31) {
                                nearbyLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
                            }
                        },
                    )
                },
            )
            PermissionRowDivider()
            PermissionManageRow(
                title = "安装应用",
                purpose = "用于安装 ZMusic 更新",
                granted = snapshot.installPackages,
                icon = ZIcons.GetApp,
                tint = Color(0xFF2A9D8F),
                onClick = {
                    handleInstallPackagesClick(
                        context = context,
                        granted = snapshot.installPackages,
                        launch = { installLauncher.launch(it) },
                    )
                },
            )
        }
    }
}

@Composable
private fun PermissionRowDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = 62.dp)
            .height(0.5.dp)
            .background(MainPalette.Hairline),
    )
}

@Composable
private fun PermissionManageRow(
    title: String,
    purpose: String,
    granted: Boolean,
    icon: ImageVector,
    tint: Color,
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
                    color = MainPalette.Ink,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
            )
            Text(
                text = if (granted) "已开启 · $purpose" else "未开启 · $purpose",
                style = TextStyle(
                    color = MainPalette.Secondary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                ),
            )
        }
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(if (granted) MainPalette.Page else tint.copy(alpha = 0.14f))
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(
                text = if (granted) "管理" else "去开启",
                style = TextStyle(
                    color = if (granted) MainPalette.Secondary else tint,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                ),
            )
        }
    }
}

private object RuntimePermissionAsk {
    var notifications: Boolean = false
    var camera: Boolean = false
    var nearbyDevices: Boolean = false
}

private fun handleNotificationClick(
    context: Context,
    enabled: Boolean,
    launcher: () -> Unit,
) {
    if (enabled) {
        openNotificationSettings(context)
        return
    }
    val runtimeMissing = Build.VERSION.SDK_INT >= 33 &&
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) != PackageManager.PERMISSION_GRANTED
    if (runtimeMissing) {
        val activity = context.findActivity()
        val canDialog = !RuntimePermissionAsk.notifications ||
            activity?.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) == true
        if (canDialog) {
            RuntimePermissionAsk.notifications = true
            launcher()
            return
        }
    }
    openNotificationSettings(context)
}

private fun handleCameraClick(
    context: Context,
    granted: Boolean,
    launcher: () -> Unit,
) {
    if (granted) {
        openAppDetailsSettings(context)
        return
    }
    val activity = context.findActivity()
    val canDialog = !RuntimePermissionAsk.camera ||
        activity?.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) == true
    if (canDialog) {
        RuntimePermissionAsk.camera = true
        launcher()
        return
    }
    openAppDetailsSettings(context)
}

private fun handleBackgroundClick(
    context: Context,
    enabled: Boolean,
    launch: (Intent) -> Unit,
) {
    if (enabled) {
        openBackgroundManage(context)
        return
    }
    val request = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
        data = Uri.parse("package:${context.packageName}")
    }
    if (canStart(context, request)) {
        launch(request)
        return
    }
    openBackgroundManage(context)
}

private fun handleOverlayClick(
    context: Context,
    granted: Boolean,
    launch: (Intent) -> Unit,
) {
    val intent = overlayPermissionIntent(context.packageName)
    if (granted) {
        if (canStart(context, intent)) {
            launch(intent)
        } else {
            openAppDetailsSettings(context)
        }
        return
    }
    if (canStart(context, intent)) {
        launch(intent)
        return
    }
    openAppDetailsSettings(context)
}

internal fun overlayGranted(context: Context): Boolean =
    Settings.canDrawOverlays(context)

internal fun installPackagesGranted(context: Context): Boolean =
    context.packageManager.canRequestPackageInstalls()

private fun overlayPermissionIntent(pkg: String): Intent =
    Intent(
        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
        Uri.fromParts("package", pkg, null),
    )

private fun installPackagesIntent(pkg: String): Intent =
    Intent(
        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
        Uri.fromParts("package", pkg, null),
    )

private fun handleInstallPackagesClick(
    context: Context,
    granted: Boolean,
    launch: (Intent) -> Unit,
) {
    val intent = installPackagesIntent(context.packageName)
    if (granted) {
        if (canStart(context, intent)) {
            launch(intent)
        } else {
            openAppDetailsSettings(context)
        }
        return
    }
    if (canStart(context, intent)) {
        launch(intent)
        return
    }
    openAppDetailsSettings(context)
}

internal fun notificationsEnabled(context: Context): Boolean {
    if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
    if (Build.VERSION.SDK_INT >= 33) {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }
    return true
}

private fun cameraGranted(context: Context): Boolean =
    ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.CAMERA,
    ) == PackageManager.PERMISSION_GRANTED

private fun handleNearbyDevicesClick(
    context: Context,
    granted: Boolean,
    launcher: () -> Unit,
) {
    if (granted) {
        openAppDetailsSettings(context)
        return
    }
    if (Build.VERSION.SDK_INT < 31) {
        openAppDetailsSettings(context)
        return
    }
    val activity = context.findActivity()
    val canDialog = !RuntimePermissionAsk.nearbyDevices ||
        activity?.shouldShowRequestPermissionRationale(Manifest.permission.BLUETOOTH_CONNECT) == true
    if (canDialog) {
        RuntimePermissionAsk.nearbyDevices = true
        launcher()
        return
    }
    openAppDetailsSettings(context)
}

internal fun nearbyDevicesGranted(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < 31) return true
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.BLUETOOTH_CONNECT,
    ) == PackageManager.PERMISSION_GRANTED
}

internal fun backgroundRunEnabled(context: Context): Boolean {
    val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    return pm.isIgnoringBatteryOptimizations(context.packageName)
}

private fun openBackgroundManage(context: Context) {
    for (intent in oemBackgroundIntents(context.packageName)) {
        if (canStart(context, intent)) {
            startSafely(context, intent)
            return
        }
    }
    val batteryList = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
    startSafely(context, batteryList) { openAppDetailsSettings(context) }
}

private fun oemBackgroundIntents(pkg: String): List<Intent> {
    val brand = Build.MANUFACTURER.lowercase()
    val intents = ArrayList<Intent>(6)
    fun component(packageName: String, className: String) {
        intents += Intent().setComponent(ComponentName(packageName, className))
    }
    when {
        listOf("xiaomi", "redmi", "poco").any { brand.contains(it) } -> {
            intents += Intent("miui.intent.action.OP_AUTO_START")
            component("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")
            component("com.miui.securitycenter", "com.miui.powercenter.PowerSettings")
        }
        listOf("huawei", "honor").any { brand.contains(it) } -> {
            component(
                "com.huawei.systemmanager",
                "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
            )
            component(
                "com.huawei.systemmanager",
                "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity",
            )
            component("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity")
        }
        listOf("oppo", "realme", "oneplus").any { brand.contains(it) } -> {
            component(
                "com.coloros.safecenter",
                "com.coloros.safecenter.permission.startup.StartupAppListActivity",
            )
            component(
                "com.oplus.safecenter",
                "com.oplus.safecenter.permission.startup.StartupAppListActivity",
            )
            component(
                "com.coloros.safecenter",
                "com.coloros.safecenter.startupapp.StartupAppListActivity",
            )
        }
        listOf("vivo", "iqoo").any { brand.contains(it) } -> {
            component(
                "com.vivo.permissionmanager",
                "com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
            )
            component("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity")
            component("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager")
        }
        brand.contains("samsung") -> {
            component("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity")
            component("com.samsung.android.sm", "com.samsung.android.sm.ui.battery.BatteryActivity")
        }
        brand.contains("meizu") -> {
            intents += Intent("com.meizu.safe.security.SHOW_APPSEC").putExtra("packageName", pkg)
        }
    }
    return intents
}

private fun openNotificationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        putExtra("app_package", context.packageName)
        putExtra("app_uid", context.applicationInfo.uid)
    }
    startSafely(context, intent) { openAppDetailsSettings(context) }
}

private fun openAppDetailsSettings(context: Context) {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", context.packageName, null),
    )
    startSafely(context, intent)
}

private fun startSafely(context: Context, intent: Intent, fallback: (() -> Unit)? = null) {
    if (!canStart(context, intent)) {
        fallback?.invoke()
        return
    }
    val launch = Intent(intent)
    if (context.findActivity() == null) {
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(launch) }.onFailure { fallback?.invoke() }
}

private fun canStart(context: Context, intent: Intent): Boolean {
    val probe = Intent(intent)
    if (context.findActivity() == null) {
        probe.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    return probe.resolveActivity(context.packageManager) != null
}

private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
