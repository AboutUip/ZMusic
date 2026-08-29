package com.kite.zmusic.ui.notice

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.kite.zmusic.data.GlassStyle
import com.kite.zmusic.ui.chrome.chromeGlassSurface
import com.kite.zmusic.ui.theme.MainPalette
import kotlinx.coroutines.delay

private val IslandOvershoot = CubicBezierEasing(0.2f, 1.1f, 0.32f, 1f)

@Composable
fun IslandNoticeHost(
    center: IslandNoticeCenter,
    glass: GlassStyle,
    modifier: Modifier = Modifier,
) {
    val notice by center.notice.collectAsState()
    LaunchedEffect(notice?.token) {
        if (notice != null) {
            delay(2800)
            center.clear()
        }
    }
    Box(modifier.fillMaxWidth().zIndex(20f), contentAlignment = Alignment.TopCenter) {
        AnimatedVisibility(
            visible = notice != null,
            enter = fadeIn(tween(200)) + scaleIn(
                initialScale = 0.72f,
                animationSpec = tween(320, easing = IslandOvershoot),
            ),
            exit = fadeOut(tween(160)),
        ) {
            val msg = notice?.message.orEmpty()
            Row(
                Modifier
                    .padding(top = 16.dp)
                    .widthIn(max = 320.dp)
                    .chromeGlassSurface(RoundedCornerShape(50), glass)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .width(8.dp)
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MainPalette.Accent),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    msg,
                    style = TextStyle(
                        color = MainPalette.Ink,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
