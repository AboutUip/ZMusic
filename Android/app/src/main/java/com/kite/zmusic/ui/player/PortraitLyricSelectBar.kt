package com.kite.zmusic.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val SelectBarLabel = Color(0xFFF4F0E8)
private val SelectBarAccent = Color(0xFF9AF0F0)

/**
 * 竖屏选句底栏：取消 / 复制（页内，非弹窗）。
 */
@Composable
fun PortraitLyricSelectBar(
    selectedCount: Int,
    progress: Float,
    onCancel: () -> Unit,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = progress.coerceIn(0f, 1f)
    if (t <= 0.001f) return
    val navBottom = WindowInsets.navigationBars
        .asPaddingValues()
        .calculateBottomPadding()
    val copyEnabled = selectedCount > 0
    Row(
        modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = t
                translationY = (1f - t) * 36f
            }
            .padding(horizontal = 20.dp)
            .padding(bottom = navBottom.coerceAtLeast(12.dp) + 8.dp, top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PortraitSelectBarButton(
            label = "取消",
            enabled = true,
            accent = false,
            onClick = onCancel,
            modifier = Modifier.weight(1f),
        )
        PortraitSelectBarButton(
            label = if (selectedCount > 0) "复制 ($selectedCount)" else "复制",
            enabled = copyEnabled,
            accent = true,
            onClick = onCopy,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun PortraitSelectBarButton(
    label: String,
    enabled: Boolean,
    accent: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(12.dp)
    val alpha = if (enabled) 1f else 0.38f
    Box(
        modifier
            .height(48.dp)
            .clip(shape)
            .background(
                if (accent) SelectBarAccent.copy(alpha = 0.18f * alpha)
                else Color.White.copy(alpha = 0.08f * alpha),
            )
            .border(
                width = 1.dp,
                color = if (accent) SelectBarAccent.copy(alpha = 0.55f * alpha)
                else Color.White.copy(alpha = 0.12f * alpha),
                shape = shape,
            )
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
            style = TextStyle(
                color = if (accent) SelectBarAccent.copy(alpha = alpha)
                else SelectBarLabel.copy(alpha = 0.92f * alpha),
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                letterSpacing = 0.2.sp,
            ),
        )
    }
}
