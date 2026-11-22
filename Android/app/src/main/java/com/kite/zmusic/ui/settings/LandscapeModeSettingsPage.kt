package com.kite.zmusic.ui.settings

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kite.zmusic.data.OrientationLock
import com.kite.zmusic.i18n.t
import com.kite.zmusic.ui.main.MainControls
import com.kite.zmusic.ui.main.MainPalette
import com.kite.zmusic.ui.main.wallpaperItemChrome

@Composable
fun LandscapeModeSettingsPage(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    orientationLock: OrientationLock,
    onOrientationLockChange: (OrientationLock) -> Unit,
    showOrientationLock: Boolean,
    contentBottomInset: Dp,
    modifier: Modifier = Modifier,
) {
    val switchColors = MainControls.switchColors()
    val directionLocked = orientationLock != OrientationLock.Auto
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = contentBottomInset + 24.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        Text(
            text = t("关闭后，首页、播放页和 MV 上的旋转按钮（含锁定旋转）都会收起。不会把应用锁成只能竖屏。"),
            style = TextStyle(
                color = MainPalette.Secondary,
                fontSize = 13.sp,
                lineHeight = 20.sp,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = t("系统自动旋转仍可进入横屏，横屏界面照常。系统也锁了旋转时，没有应用内按钮可强制转横。关闭时若已钉住方向，会自动解开。"),
            style = TextStyle(
                color = MainPalette.Secondary,
                fontSize = 13.sp,
                lineHeight = 20.sp,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
        )
        if (!showOrientationLock && directionLocked) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = t("方向已在音乐模式里锁定，这个开关保持关闭。"),
                style = TextStyle(
                    color = MainPalette.Secondary,
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
            )
        }
        Spacer(Modifier.height(20.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .alpha(if (directionLocked) 0.45f else 1f)
                .wallpaperItemChrome(RoundedCornerShape(16.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = !directionLocked,
                    onClick = { onEnabledChange(!enabled) },
                )
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = t("横屏模式"),
                    style = TextStyle(
                        color = MainPalette.Ink,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                )
                Text(
                    text = if (directionLocked) {
                        t("已关闭 · 方向锁定时保持关闭")
                    } else {
                        landscapeModeSubtitle(enabled)
                    },
                    style = TextStyle(
                        color = MainPalette.Secondary,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                    ),
                )
            }
            Switch(
                checked = enabled && !directionLocked,
                onCheckedChange = { if (!directionLocked) onEnabledChange(it) },
                enabled = !directionLocked,
                colors = switchColors,
            )
        }
        if (showOrientationLock) {
            Spacer(Modifier.height(20.dp))
            Text(
                text = t("锁定方向"),
                style = TextStyle(
                    color = MainPalette.Ink,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            Spacer(Modifier.height(12.dp))
            OrientationLockSegment(
                selected = orientationLock,
                onSelect = onOrientationLockChange,
            )
        }
    }
}

@Composable
private fun OrientationLockSegment(
    selected: OrientationLock,
    onSelect: (OrientationLock) -> Unit,
) {
    val options = listOf(
        OrientationLock.Auto to t("自动"),
        OrientationLock.Portrait to t("锁定竖屏"),
        OrientationLock.Landscape to t("锁定横屏"),
    )
    Row(
        Modifier
            .fillMaxWidth()
            .wallpaperItemChrome(RoundedCornerShape(16.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEach { (lock, label) ->
            val on = lock == selected
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (on) MainPalette.Accent.copy(alpha = 0.16f) else Color.Transparent)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onSelect(lock) },
                    )
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(
                        color = if (on) MainPalette.Accent else MainPalette.Ink,
                        fontSize = 14.sp,
                        fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium,
                    ),
                )
            }
        }
    }
}

internal fun landscapeModeSubtitle(
    enabled: Boolean,
    lock: OrientationLock = OrientationLock.Auto,
): String = when (lock) {
    OrientationLock.Portrait -> t("已锁定竖屏")
    OrientationLock.Landscape -> t("已锁定横屏")
    OrientationLock.Auto -> if (enabled) {
        t("已开启 · 显示旋转按钮（默认）")
    } else {
        t("已关闭 · 仅系统自动旋转可横屏")
    }
}
