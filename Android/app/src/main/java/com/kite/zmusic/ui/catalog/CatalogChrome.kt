package com.kite.zmusic.ui.catalog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kite.zmusic.ui.icons.ZIcons
import com.kite.zmusic.ui.theme.TextTheme
import com.kite.zmusic.i18n.t

@Composable
internal fun CatalogTopBar(
    title: String,
    onBack: () -> Unit,
    extraLabel: String? = null,
    extraIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onExtra: (() -> Unit)? = null,
    onSearch: (() -> Unit)? = null,
    searchContentDescription: String = t("搜索歌单内歌曲"),
    onManage: (() -> Unit)? = null,
    onSelectAll: (() -> Unit)? = null,
    allSelected: Boolean = false,
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
                tint = TextTheme.CatalogTitle,
                modifier = Modifier.size(22.dp),
            )
        }
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(
                color = TextTheme.CatalogTitle,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
        if (onSelectAll != null) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onSelectAll,
                    )
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (allSelected) t("取消全选") else t("全选"),
                    color = TextTheme.CatalogAction,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        if (onManage != null) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onManage,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = ZIcons.Manage,
                    contentDescription = t("管理"),
                    tint = TextTheme.CatalogTitle,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        if (onSearch != null) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onSearch,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = ZIcons.Search,
                    contentDescription = searchContentDescription,
                    tint = TextTheme.CatalogTitle,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        if (extraLabel != null && onExtra != null) {
            Row(
                Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onExtra,
                    )
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (extraIcon != null) {
                    Icon(
                        imageVector = extraIcon,
                        contentDescription = extraLabel,
                        tint = TextTheme.CatalogAction,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                }
                Text(extraLabel, color = TextTheme.CatalogAction, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

internal fun isPlaybackCurrent(
    trackId: Long,
    contextId: Long,
    playingTrackId: Long,
    playingSourceId: Long,
): Boolean {
    if (trackId <= 0L || playingTrackId <= 0L || trackId != playingTrackId) return false
    return if (contextId > 0L) {
        playingSourceId == contextId
    } else {
        playingSourceId <= 0L
    }
}
