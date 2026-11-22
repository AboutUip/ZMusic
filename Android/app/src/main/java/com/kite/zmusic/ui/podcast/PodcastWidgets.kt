package com.kite.zmusic.ui.podcast

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kite.zmusic.ZMusicApplication
import com.kite.zmusic.data.PodcastEpisode
import com.kite.zmusic.data.PodcastShow
import com.kite.zmusic.i18n.t
import com.kite.zmusic.ui.common.PlayingEqualizer
import com.kite.zmusic.ui.common.UrlImage
import com.kite.zmusic.ui.icons.ZIcons
import com.kite.zmusic.ui.theme.MainPalette
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Composable
internal fun PodcastCover(
    url: String?,
    size: Dp,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
) {
    if (url.isNullOrBlank()) {
        Spacer(
            modifier
                .size(size)
                .clip(shape)
                .background(MainPalette.Placeholder),
        )
    } else {
        UrlImage(
            url = url,
            contentDescription = null,
            modifier = modifier
                .size(size)
                .clip(shape),
            contentScale = ContentScale.Crop,
        )
    }
}

@Composable
internal fun PodcastSectionTitle(
    title: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            color = MainPalette.Ink,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        if (action != null && onAction != null) {
            Text(
                text = action,
                color = MainPalette.Accent,
                fontSize = 13.sp,
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onAction,
                ),
            )
        }
    }
}

@Composable
internal fun PodcastShowCard(
    show: PodcastShow,
    width: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .width(width)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
    ) {
        PodcastCover(show.coverUrl, width, shape = RoundedCornerShape(18.dp))
        Spacer(Modifier.height(8.dp))
        Text(
            text = show.name,
            color = MainPalette.Ink,
            style = TextStyle(
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 18.sp,
            ),
            maxLines = 2,
            minLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = show.djName.orEmpty(),
            color = MainPalette.Secondary,
            style = TextStyle(
                fontSize = 12.sp,
                lineHeight = 16.sp,
            ),
            maxLines = 1,
            minLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun PodcastShowLine(
    show: PodcastShow,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    index: Int? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (index != null) {
            Text(
                text = "%02d".format(index),
                color = MainPalette.Accent,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(32.dp),
            )
        }
        PodcastCover(show.coverUrl, 52.dp, shape = RoundedCornerShape(12.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = show.name,
                color = MainPalette.Ink,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val meta = listOfNotNull(
                show.djName,
                show.programCount.takeIf { it > 0 }?.let { t("%s期", it) },
            ).joinToString("  ·  ")
            if (meta.isNotBlank()) {
                Text(
                    text = meta,
                    color = MainPalette.Secondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
internal fun PodcastEpisodeLine(
    episode: PodcastEpisode,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val now = rememberPodcastNowPlaying()
    val current = episode.trackId > 0L && episode.trackId == now.trackId
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (current) MainPalette.Accent.copy(alpha = 0.08f) else Color.Transparent)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PodcastEpisodeCover(
            episode = episode,
            size = 52.dp,
            current = current,
            playing = current && now.playing,
            shape = RoundedCornerShape(12.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = episode.name,
                color = if (current) MainPalette.Accent else MainPalette.Ink,
                fontSize = 15.sp,
                fontWeight = if (current) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 20.sp,
            )
            val meta = listOfNotNull(
                episode.podcastName.takeIf { it.isNotBlank() },
                podcastDay(episode.createTime).takeIf { it.isNotBlank() },
                podcastClock(episode.durationMs).takeIf { episode.durationMs > 0L },
            ).joinToString("  ·  ")
            if (meta.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = meta,
                    color = if (current) MainPalette.Accent.copy(alpha = 0.72f) else MainPalette.Secondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
internal fun PodcastEpisodeCover(
    episode: PodcastEpisode,
    size: Dp,
    current: Boolean,
    playing: Boolean,
    shape: Shape = RoundedCornerShape(16.dp),
) {
    Box(Modifier.size(size)) {
        PodcastCover(episode.coverUrl, size, shape = shape)
        if (current) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(3.dp)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.46f)),
                contentAlignment = Alignment.Center,
            ) {
                PlayingEqualizer(
                    playing = playing,
                    color = Color.White,
                    modifier = Modifier.size(11.dp, 9.dp),
                )
            }
        }
    }
}

@Composable
internal fun rememberPodcastNowPlaying(): PodcastNowPlaying {
    val app = LocalContext.current.applicationContext as ZMusicApplication
    val ui by app.playbackBridge.ui.collectAsStateWithLifecycle()
    val trackId = ui.currentTrack?.id ?: 0L
    return remember(trackId, ui.playWhenReady) {
        PodcastNowPlaying(trackId = trackId, playing = ui.playWhenReady)
    }
}

internal data class PodcastNowPlaying(
    val trackId: Long,
    val playing: Boolean,
)

@Composable
internal fun PodcastBackTitle(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .height(48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = ZIcons.Back,
            contentDescription = t("返回"),
            tint = MainPalette.Ink,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onBack,
                )
                .padding(8.dp),
        )
        Text(
            text = title,
            style = TextStyle(
                color = MainPalette.Ink,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(start = 4.dp),
        )
    }
}

internal fun podcastClock(ms: Long): String {
    if (ms <= 0L) return ""
    val total = ms / 1000L
    val h = total / 3600L
    val m = (total % 3600L) / 60L
    val s = total % 60L
    return if (h > 0L) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

internal fun podcastDay(ms: Long): String {
    if (ms <= 0L) return ""
    val date = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()
    val today = LocalDate.now()
    return if (date.year == today.year) {
        "%02d-%02d".format(date.monthValue, date.dayOfMonth)
    } else {
        "%d-%02d-%02d".format(date.year, date.monthValue, date.dayOfMonth)
    }
}
