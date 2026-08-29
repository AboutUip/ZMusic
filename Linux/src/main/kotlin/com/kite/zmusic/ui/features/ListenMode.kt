package com.kite.zmusic.ui.features

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Radio

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
        icon = Icons.Outlined.Radio,
    ),
    ListenMode(
        id = ListenModeId.Heart,
        title = "心动模式",
        caption = "从我喜欢的音乐出发，智能接着放",
        accent = Color(0xFFEC4141),
        icon = Icons.Outlined.Favorite,
    ),
)
