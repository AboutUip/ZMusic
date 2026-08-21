package com.kite.zmusic.ui.catalog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kite.zmusic.ui.main.MainPalette
import com.kite.zmusic.ui.main.MiniPlayerStackHeight
import com.kite.zmusic.ui.main.mainLiquidGlass
import com.kyant.backdrop.Backdrop

class PlaylistManageBridge {
    var active by mutableStateOf(false)
        private set
    var selectedCount by mutableIntStateOf(0)
    var totalCount by mutableIntStateOf(0)
    var canRemove by mutableStateOf(false)
    var canDownload by mutableStateOf(true)
    var busy by mutableStateOf(false)

    var onSelectAll: () -> Unit = {}
    var onCancel: () -> Unit = {}
    var onRemove: () -> Unit = {}
    var onDownload: () -> Unit = {}

    fun enter() {
        active = true
        busy = false
    }

    fun exit() {
        active = false
        selectedCount = 0
        busy = false
        onSelectAll = {}
        onCancel = {}
        onRemove = {}
        onDownload = {}
        canDownload = true
    }
}

@Composable
internal fun PlaylistManageBar(
    selectedCount: Int,
    canRemove: Boolean,
    busy: Boolean,
    onRemove: () -> Unit,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    canDownload: Boolean = true,
    removeLabel: String = "全部移出歌单",
) {
    val shape = RoundedCornerShape(24.dp)
    val enabled = !busy
    Row(
        modifier
            .mainLiquidGlass(backdrop, shape)
            .height(MiniPlayerStackHeight)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        ManageBarAction(
            label = removeLabel,
            color = if (canRemove) MainPalette.Accent else MainPalette.Hint,
            enabled = enabled && canRemove && selectedCount > 0,
            onClick = onRemove,
            modifier = Modifier.weight(1f),
        )
        if (canDownload) {
            ManageBarAction(
                label = "全部下载",
                color = MainPalette.Ink,
                enabled = enabled && selectedCount > 0,
                onClick = onDownload,
                modifier = Modifier.weight(1f),
            )
        }
        ManageBarAction(
            label = "取消",
            color = MainPalette.Secondary,
            enabled = enabled,
            onClick = onCancel,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ManageBarAction(
    label: String,
    color: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .fillMaxHeight()
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = color.copy(alpha = if (enabled) 1f else 0.38f),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
        )
    }
}
