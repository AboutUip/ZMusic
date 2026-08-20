package com.kite.zmusic.ui.features

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.kite.zmusic.ui.icons.ZIcons

/**
 * 听歌模式：点进去按自己的规则连续听，不是打开一个列表页。
 * 新模式加到 [builtInListenModes] 即可出现在功能页。
 */
enum class ListenModeId {
    Fm,
    Heart,
}

data class ListenMode(
    val id: ListenModeId,
    val title: String,
    val caption: String,
    val accent: Color,
    val icon: ImageVector,
)

fun builtInListenModes(): List<ListenMode> = listOf(
    ListenMode(
        id = ListenModeId.Fm,
        title = "私人漫游",
        caption = "按口味连续听，播完自动补歌",
        accent = Color(0xFF5B8DEF),
        icon = ZIcons.Radio,
    ),
    ListenMode(
        id = ListenModeId.Heart,
        title = "心动模式",
        caption = "从我喜欢的音乐出发，智能接着放",
        accent = Color(0xFFEC4141),
        icon = ZIcons.Favorite,
    ),
)
