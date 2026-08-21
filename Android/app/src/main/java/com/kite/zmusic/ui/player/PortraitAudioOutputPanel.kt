package com.kite.zmusic.ui.player

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kite.zmusic.ZMusicApplication
import com.kite.zmusic.playback.AudioOutputDevice
import com.kite.zmusic.playback.AudioOutputTypes
import com.kite.zmusic.ui.icons.ZIcons
import com.kite.zmusic.ui.main.MainPalette
import com.kite.zmusic.ui.notice.showIslandNotice
import com.kite.zmusic.ui.settings.nearbyDevicesGranted

private val OutputRowShape = RoundedCornerShape(14.dp)

@Composable
internal fun PortraitAudioOutputPanel(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val app = context.applicationContext as ZMusicApplication
    val controller = app.audioOutputController
    val state by controller.state.collectAsStateWithLifecycle()
    val nearbyGranted = nearbyDevicesGranted(context)
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        controller.refresh()
        if (granted) {
            context.showIslandNotice("已开启附近的设备")
        } else {
            context.showIslandNotice("未开启时，蓝牙设备可能只显示通用名称")
        }
    }

    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "列出当前可发声的输出口。智能模式把选择交给系统。",
            style = TextStyle(
                color = MainPalette.Secondary,
                fontSize = 12.sp,
                lineHeight = 16.sp,
            ),
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
        )
        if (Build.VERSION.SDK_INT >= 31 && !nearbyGranted) {
            OutputHintRow(
                title = "开启附近的设备权限",
                subtitle = "才能显示蓝牙耳机、音箱的真实名称",
                onClick = {
                    permissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
                },
            )
        }
        OutputChoiceRow(
            icon = ZIcons.Speaker,
            title = "智能模式",
            subtitle = "由操作系统决定当前输出",
            selected = state.usingSmart,
            onClick = { controller.selectSmart() },
        )
        state.devices.forEach { device ->
            OutputChoiceRow(
                icon = audioOutputIcon(device.type),
                title = device.name,
                subtitle = audioOutputKindLabel(device.type),
                selected = !state.usingSmart && state.active?.id == device.id,
                onClick = { controller.selectDevice(device) },
            )
        }
        if (state.devices.isEmpty()) {
            Text(
                text = "暂时没有检测到输出设备",
                style = TextStyle(
                    color = MainPalette.Secondary,
                    fontSize = 13.sp,
                ),
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun OutputHintRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(OutputRowShape)
            .background(MainPalette.Accent.copy(alpha = 0.12f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(
            text = title,
            style = TextStyle(
                color = MainPalette.Accent,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
            ),
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = subtitle,
            style = TextStyle(
                color = MainPalette.Secondary,
                fontSize = 12.sp,
                lineHeight = 16.sp,
            ),
        )
    }
}

@Composable
private fun OutputChoiceRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(OutputRowShape)
            .background(MainPalette.Card)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MainPalette.Ink,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = TextStyle(
                    color = MainPalette.Ink,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                ),
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = TextStyle(
                    color = MainPalette.Secondary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                ),
            )
        }
        if (selected) {
            Icon(
                imageVector = ZIcons.Check,
                contentDescription = null,
                tint = MainPalette.Accent,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

internal fun audioOutputIcon(type: Int): ImageVector = when (type) {
    AudioOutputTypes.BUILTIN_SPEAKER,
    AudioOutputTypes.BUILTIN_SPEAKER_SAFE,
    -> ZIcons.Speaker
    AudioOutputTypes.WIRED_HEADSET,
    AudioOutputTypes.WIRED_HEADPHONES,
    -> ZIcons.Headset
    AudioOutputTypes.BLUETOOTH_A2DP,
    AudioOutputTypes.BLUETOOTH_SCO,
    AudioOutputTypes.BLE_HEADSET,
    AudioOutputTypes.BLE_SPEAKER,
    AudioOutputTypes.BLE_BROADCAST,
    -> ZIcons.Bluetooth
    AudioOutputTypes.USB_HEADSET,
    AudioOutputTypes.USB_DEVICE,
    AudioOutputTypes.USB_ACCESSORY,
    -> ZIcons.Usb
    AudioOutputTypes.HDMI,
    AudioOutputTypes.HDMI_ARC,
    AudioOutputTypes.HDMI_EARC,
    -> ZIcons.Tv
    AudioOutputTypes.HEARING_AID -> ZIcons.Hearing
    else -> ZIcons.Speaker
}

internal fun audioOutputKindLabel(type: Int): String = when (type) {
    AudioOutputTypes.BUILTIN_SPEAKER,
    AudioOutputTypes.BUILTIN_SPEAKER_SAFE,
    -> "本机"
    AudioOutputTypes.BUILTIN_EARPIECE -> "听筒"
    AudioOutputTypes.WIRED_HEADSET,
    AudioOutputTypes.WIRED_HEADPHONES,
    -> "有线"
    AudioOutputTypes.USB_HEADSET,
    AudioOutputTypes.USB_DEVICE,
    AudioOutputTypes.USB_ACCESSORY,
    -> "USB"
    AudioOutputTypes.BLUETOOTH_A2DP,
    AudioOutputTypes.BLE_HEADSET,
    AudioOutputTypes.BLE_SPEAKER,
    AudioOutputTypes.BLE_BROADCAST,
    -> "蓝牙"
    AudioOutputTypes.BLUETOOTH_SCO -> "蓝牙通话"
    AudioOutputTypes.HDMI,
    AudioOutputTypes.HDMI_ARC,
    AudioOutputTypes.HDMI_EARC,
    -> "HDMI"
    AudioOutputTypes.HEARING_AID -> "听力设备"
    else -> "输出"
}
