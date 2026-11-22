package com.kite.zmusic.ui.podcast

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kite.zmusic.data.PodcastBanner
import com.kite.zmusic.data.PodcastEpisode
import com.kite.zmusic.data.PodcastFeed
import com.kite.zmusic.data.PodcastShow
import com.kite.zmusic.i18n.t
import com.kite.zmusic.ui.common.UrlImage
import com.kite.zmusic.ui.common.ZPullRefresh
import com.kite.zmusic.ui.home.homeStageHorizontalContent
import com.kite.zmusic.ui.icons.ZIcons
import com.kite.zmusic.ui.theme.MainPalette

enum class PodcastChart {
    Recommend,
    Hot,
    New,
    Popular,
    Newcomers,
    AnchorHours,
    Pay,
    PayGift,
    Programs,
    ProgramHours,
}

@Composable
internal fun PodcastHome(
    feed: PodcastFeed,
    loading: Boolean,
    error: String?,
    onRefresh: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenShow: (PodcastShow) -> Unit,
    onOpenChart: (PodcastChart) -> Unit,
    onPlayShow: (PodcastShow) -> Unit,
    modifier: Modifier = Modifier,
    onPlayEpisodes: (List<PodcastEpisode>, Int) -> Unit = { _, _ -> },
) {
    ZPullRefresh(
        refreshing = loading && (
            feed.banners.isNotEmpty() || feed.recommends.isNotEmpty() ||
                feed.hot.isNotEmpty() || feed.today != null
            ),
        onRefresh = onRefresh,
        modifier = modifier.fillMaxSize(),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 28.dp),
        ) {
            item(key = "search") {
                PodcastSearchEntry(onClick = onOpenSearch, modifier = Modifier.padding(horizontal = 20.dp))
                Spacer(Modifier.height(18.dp))
            }
            if (loading && feed.hot.isEmpty() && feed.recommends.isEmpty() && feed.banners.isEmpty()) {
                item(key = "loading") {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(160.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            color = MainPalette.Accent,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }
            }
            error?.let { message ->
                item(key = "error") {
                    Text(
                        text = message,
                        color = MainPalette.Secondary,
                        fontSize = 13.sp,
                        modifier = Modifier
                            .padding(horizontal = 20.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onRefresh,
                            ),
                    )
                    Spacer(Modifier.height(12.dp))
                }
            }
            feed.today?.let { today ->
                item(key = "today") {
                    PodcastSectionTitle(
                        title = t("今日优选"),
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                    Spacer(Modifier.height(12.dp))
                    TodayCard(
                        show = today,
                        onOpen = { onOpenShow(today) },
                        onPlay = { onPlayShow(today) },
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                    Spacer(Modifier.height(22.dp))
                }
            }
            if (feed.banners.isNotEmpty()) {
                item(key = "banners") {
                    BannerRow(
                        banners = feed.banners,
                        onOpen = { banner ->
                            if (banner.targetId > 0L) {
                                onOpenShow(
                                    PodcastShow(
                                        id = banner.targetId,
                                        name = banner.title.ifBlank { t("播客") },
                                        coverUrl = banner.picUrl,
                                        djName = null,
                                        desc = null,
                                        programCount = 0,
                                        subCount = 0L,
                                        subscribed = false,
                                        category = null,
                                    ),
                                )
                            }
                        },
                    )
                    Spacer(Modifier.height(22.dp))
                }
            }
            if (feed.recommends.isNotEmpty()) {
                item(key = "rec") {
                    ShowMosaic(t("推荐"), feed.recommends, onOpenShow) {
                        onOpenChart(PodcastChart.Recommend)
                    }
                }
            }
            if (feed.hot.isNotEmpty()) {
                item(key = "hot") {
                    ShowMosaic(t("热门电台"), feed.hot, onOpenShow) {
                        onOpenChart(PodcastChart.Hot)
                    }
                }
            }
            if (feed.newest.isNotEmpty()) {
                item(key = "newest") {
                    ShowMosaic(t("新晋电台"), feed.newest, onOpenShow) {
                        onOpenChart(PodcastChart.New)
                    }
                }
            }
            if (feed.popular.isNotEmpty()) {
                item(key = "popular") {
                    ShowMosaic(t("最热主播"), feed.popular, onOpenShow) {
                        onOpenChart(PodcastChart.Popular)
                    }
                }
            }
            if (feed.newcomers.isNotEmpty()) {
                item(key = "newcomers") {
                    ShowMosaic(t("主播新人榜"), feed.newcomers, onOpenShow) {
                        onOpenChart(PodcastChart.Newcomers)
                    }
                }
            }
            if (feed.anchorHours.isNotEmpty()) {
                item(key = "anchor-hours") {
                    ShowMosaic(t("24 小时主播"), feed.anchorHours, onOpenShow) {
                        onOpenChart(PodcastChart.AnchorHours)
                    }
                }
            }
            if (feed.pay.isNotEmpty()) {
                item(key = "pay") {
                    ShowMosaic(t("付费精品"), feed.pay, onOpenShow) {
                        onOpenChart(PodcastChart.Pay)
                    }
                }
            }
            if (feed.payGift.isNotEmpty()) {
                item(key = "pay-gift") {
                    ShowMosaic(t("付费精选"), feed.payGift, onOpenShow) {
                        onOpenChart(PodcastChart.PayGift)
                    }
                }
            }
            if (feed.programChart.isNotEmpty()) {
                item(key = "program-chart") {
                    EpisodeMosaic(t("节目榜"), feed.programChart, onPlayEpisodes) {
                        onOpenChart(PodcastChart.Programs)
                    }
                }
            }
            if (feed.programHours.isNotEmpty()) {
                item(key = "program-hours") {
                    EpisodeMosaic(t("24 小时节目"), feed.programHours, onPlayEpisodes) {
                        onOpenChart(PodcastChart.ProgramHours)
                    }
                }
            }
        }
    }
}

@Composable
private fun PodcastSearchEntry(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(CircleShape)
            .background(MainPalette.Surface)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = ZIcons.Search,
            contentDescription = null,
            tint = MainPalette.Hint,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = t("搜索电台或声音"),
            color = MainPalette.Hint,
            fontSize = 14.sp,
        )
    }
}

@Composable
private fun TodayCard(
    show: PodcastShow,
    onOpen: () -> Unit,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(MainPalette.Surface)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onOpen,
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PodcastCover(show.coverUrl, 96.dp, shape = RoundedCornerShape(18.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = show.category ?: t("今日"),
                color = MainPalette.Accent,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = show.name,
                color = MainPalette.Ink,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 22.sp,
            )
            show.djName?.let { dj ->
                Spacer(Modifier.height(4.dp))
                Text(text = dj, color = MainPalette.Secondary, fontSize = 13.sp, maxLines = 1)
            }
        }
        Box(
            Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(MainPalette.Accent)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onPlay,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = ZIcons.Play,
                contentDescription = t("播放"),
                tint = MainPalette.Page,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun BannerRow(
    banners: List<PodcastBanner>,
    onOpen: (PodcastBanner) -> Unit,
) {
    val pager = rememberPagerState { banners.size }
    HorizontalPager(
        state = pager,
        modifier = Modifier
            .fillMaxWidth()
            .homeStageHorizontalContent(),
        contentPadding = PaddingValues(horizontal = 20.dp),
        pageSpacing = 10.dp,
    ) { page ->
        val banner = banners[page]
        Box(
            Modifier
                .fillMaxWidth()
                .height(132.dp)
                .clip(RoundedCornerShape(20.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { onOpen(banner) },
                ),
        ) {
            UrlImage(
                url = banner.picUrl,
                contentDescription = banner.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
    }
}

@Composable
private fun mosaicColumns(): Int {
    return if (LocalConfiguration.current.screenWidthDp >= 600) 4 else 3
}

@Composable
private fun ShowMosaic(
    title: String,
    shows: List<PodcastShow>,
    onOpen: (PodcastShow) -> Unit,
    onViewAll: () -> Unit,
) {
    val columns = mosaicColumns()
    val preview = shows.take(columns * 3)
    Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 22.dp)) {
        PodcastSectionTitle(title = title, action = t("查看完整"), onAction = onViewAll)
        Spacer(Modifier.height(12.dp))
        CoverGrid(preview.size, columns) { index, cell ->
            PodcastShowCard(preview[index], cell, onClick = { onOpen(preview[index]) })
        }
    }
}

@Composable
private fun EpisodeMosaic(
    title: String,
    episodes: List<PodcastEpisode>,
    onPlay: (List<PodcastEpisode>, Int) -> Unit,
    onViewAll: () -> Unit,
) {
    val columns = mosaicColumns()
    val preview = episodes.take(columns * 3)
    Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 22.dp)) {
        PodcastSectionTitle(title = title, action = t("查看完整"), onAction = onViewAll)
        Spacer(Modifier.height(12.dp))
        CoverGrid(preview.size, columns) { index, cell ->
            EpisodeGridCard(preview[index], cell) { onPlay(episodes, index) }
        }
    }
}

@Composable
private fun CoverGrid(
    count: Int,
    columns: Int,
    cell: @Composable (index: Int, width: Dp) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val gap = 12.dp
        val width = (maxWidth - gap * (columns - 1)) / columns
        val rows = (count + columns - 1) / columns
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            for (row in 0 until rows) {
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    val start = row * columns
                    val end = minOf(start + columns, count)
                    for (index in start until end) {
                        cell(index, width)
                    }
                }
            }
        }
    }
}

@Composable
private fun EpisodeGridCard(
    episode: PodcastEpisode,
    width: Dp,
    onClick: () -> Unit,
) {
    val now = rememberPodcastNowPlaying()
    val current = episode.trackId > 0L && episode.trackId == now.trackId
    Column(
        Modifier
            .width(width)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
    ) {
        PodcastEpisodeCover(
            episode = episode,
            size = width,
            current = current,
            playing = current && now.playing,
            shape = RoundedCornerShape(18.dp),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = episode.name,
            color = if (current) MainPalette.Accent else MainPalette.Ink,
            style = TextStyle(
                fontSize = 14.sp,
                fontWeight = if (current) FontWeight.SemiBold else FontWeight.Medium,
                lineHeight = 18.sp,
            ),
            maxLines = 2,
            minLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = episode.podcastName.ifBlank { episode.djName.orEmpty() },
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
