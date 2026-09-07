package com.kite.zmusic.ui.player

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kite.zmusic.R
import com.kite.zmusic.ui.icons.ZIcons
import com.kite.zmusic.ui.main.MainPalette
import com.kite.zmusic.ui.main.pageSheetHazeStyle
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect

private val SharePanelShape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)

@Composable
internal fun PortraitShareSheet(
    onPick: (NcmShareTarget) -> Unit,
    hazeState: HazeState? = null,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .fillMaxHeight()
            .fillMaxWidth()
            .clip(SharePanelShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            ),
    ) {
        if (hazeState != null) {
            Box(
                Modifier
                    .matchParentSize()
                    .hazeEffect(state = hazeState, style = pageSheetHazeStyle()),
            )
        } else {
            Box(
                Modifier
                    .matchParentSize()
                    .background(MainPalette.Page.copy(alpha = 0.96f)),
            )
        }
        Box(
            Modifier
                .matchParentSize()
                .background(MainPalette.SheetWash),
        )
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .width(36.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MainPalette.Hint),
                )
            }
            Text(
                text = "分享",
                style = TextStyle(
                    color = MainPalette.Ink,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    letterSpacing = (-0.2).sp,
                ),
            )
            Spacer(Modifier.height(18.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top,
            ) {
                ShareBrandAction(
                    drawableRes = R.drawable.ic_share_wechat_moments,
                    label = "微信朋友圈",
                    onClick = { onPick(NcmShareTarget.WeChatMoments) },
                )
                ShareBrandAction(
                    drawableRes = R.drawable.ic_share_wechat,
                    label = "微信好友",
                    onClick = { onPick(NcmShareTarget.WeChatFriend) },
                )
                ShareBrandAction(
                    drawableRes = R.drawable.ic_share_qzone,
                    label = "QQ空间",
                    onClick = { onPick(NcmShareTarget.Qzone) },
                )
                ShareBrandAction(
                    drawableRes = R.drawable.ic_share_qq,
                    label = "QQ好友",
                    onClick = { onPick(NcmShareTarget.QqFriend) },
                )
                ShareVectorAction(
                    icon = ZIcons.Link,
                    label = "仅复制链接",
                    onClick = { onPick(NcmShareTarget.CopyLink) },
                )
            }
        }
    }
}

@Composable
private fun ShareBrandAction(
    drawableRes: Int,
    label: String,
    onClick: () -> Unit,
) {
    Column(
        Modifier
            .width(72.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(drawableRes),
            contentDescription = label,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = label,
            style = TextStyle(
                color = MainPalette.Ink,
                fontSize = 11.sp,
                lineHeight = 14.sp,
            ),
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ShareVectorAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Column(
        Modifier
            .width(72.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(MainPalette.Hairline.copy(alpha = 0.55f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = MainPalette.Ink,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = label,
            style = TextStyle(
                color = MainPalette.Ink,
                fontSize = 11.sp,
                lineHeight = 14.sp,
            ),
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
