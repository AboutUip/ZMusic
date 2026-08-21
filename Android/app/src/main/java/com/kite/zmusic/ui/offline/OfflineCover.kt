package com.kite.zmusic.ui.offline

import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kite.zmusic.ui.icons.ZIcons
import com.kite.zmusic.ui.main.MainPalette
import com.kite.zmusic.ui.main.mainChromePlate

@Composable
fun OfflineModeLayer(
    visible: Boolean,
    modifier: Modifier = Modifier,
    title: String = "离线模式",
    caption: String = "网络恢复后会自动继续",
    actionLabel: String? = "查看缓存的歌曲",
    onAction: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null,
    contentBottomInset: Dp = 0.dp,
) {
    if (!visible) return
    OfflineEmptyPage(
        title = title,
        caption = caption,
        actionLabel = actionLabel,
        onAction = onAction,
        onBack = onBack,
        contentBottomInset = contentBottomInset,
        modifier = modifier,
    )
}

@Composable
fun OfflineEmptyPage(
    modifier: Modifier = Modifier,
    title: String = "离线模式",
    caption: String = "网络恢复后会自动继续",
    actionLabel: String? = "查看缓存的歌曲",
    onAction: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null,
    contentBottomInset: Dp = 0.dp,
) {
    val context = LocalContext.current
    val reduceMotion = remember {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f
    }
    val inf = rememberInfiniteTransition(label = "offline-404")
    val caretAnim = inf.animateFloat(
        initialValue = 0.12f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 860, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "offline-caret",
    )
    val caret = if (reduceMotion) 0.55f else caretAnim.value
    val plateShape = RoundedCornerShape(32.dp)
    Box(modifier.fillMaxSize()) {
        if (onBack != null) {
            Box(
                Modifier
                    .padding(start = 8.dp, top = 6.dp)
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
                    contentDescription = "返回",
                    tint = MainPalette.Ink,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 28.dp)
                .padding(bottom = contentBottomInset)
                .widthIn(min = 248.dp, max = 320.dp)
                .fillMaxWidth()
                .mainChromePlate(plateShape)
                .padding(horizontal = 28.dp, vertical = 32.dp),
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "404",
                    style = TextStyle(
                        color = MainPalette.Hint,
                        fontSize = 52.sp,
                        fontWeight = FontWeight.Light,
                        letterSpacing = 6.sp,
                    ),
                )
                Box(
                    Modifier
                        .padding(start = 4.dp, bottom = 12.dp)
                        .width(2.dp)
                        .height(24.dp)
                        .background(MainPalette.Hint.copy(alpha = caret)),
                )
            }
            Spacer(Modifier.height(4.dp))
            Box(
                Modifier
                    .width(28.dp)
                    .height(1.dp)
                    .background(MainPalette.Hairline),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = title,
                style = TextStyle(
                    color = MainPalette.Ink,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.4.sp,
                ),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = caption,
                style = TextStyle(
                    color = MainPalette.Secondary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    textAlign = TextAlign.Center,
                ),
            )
            if (!actionLabel.isNullOrBlank() && onAction != null) {
                Spacer(Modifier.height(20.dp))
                Text(
                    text = actionLabel,
                    style = TextStyle(
                        color = MainPalette.Ink,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onAction,
                    ),
                )
            }
        }
    }
}
