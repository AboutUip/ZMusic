package com.kite.zmusic.ui.podcast

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kite.zmusic.R
import com.kite.zmusic.data.PodcastCategory
import com.kite.zmusic.data.PodcastEpisode
import com.kite.zmusic.data.PodcastFeed
import com.kite.zmusic.data.PodcastShow
import com.kite.zmusic.i18n.t
import com.kite.zmusic.ui.theme.MainPalette

@Composable
internal fun PodcastCategoryBoard(
    categories: List<PodcastCategory>,
    onOpen: (PodcastCategory) -> Unit,
    modifier: Modifier = Modifier,
) {
    val columns = when {
        LocalConfiguration.current.screenWidthDp >= 840 -> 4
        LocalConfiguration.current.screenWidthDp >= 600 -> 3
        else -> 2
    }
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            PodcastSectionTitle(title = t("全部分类"))
            Spacer(Modifier.height(12.dp))
        }
        if (categories.isEmpty()) {
            item { Text(t("分类还在路上"), color = MainPalette.Secondary, fontSize = 13.sp) }
        }
        val rows = categories.chunked(columns)
        items(rows.size) { index ->
            val row = rows[index]
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { category ->
                    CategoryCell(category, Modifier.weight(1f), onOpen)
                }
                repeat(columns - row.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

private const val CategoryImageRect = 0.45f
private const val CategoryImageTriangle = 0.10f

/** 右梯形：右侧 45% 矩形，再在左下补 10% 直角边。上短下长。 */
private object CategoryRightTrapezoid : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val topLeft = size.width * (1f - CategoryImageRect)
        val bottomLeft = size.width * (1f - CategoryImageRect - CategoryImageTriangle)
        return Outline.Generic(
            Path().apply {
                moveTo(topLeft, 0f)
                lineTo(size.width, 0f)
                lineTo(size.width, size.height)
                lineTo(bottomLeft, size.height)
                close()
            },
        )
    }
}

@Composable
private fun CategoryCell(
    category: PodcastCategory,
    modifier: Modifier,
    onOpen: (PodcastCategory) -> Unit,
) {
    Box(
        modifier
            .height(88.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MainPalette.Surface)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { onOpen(category) },
            ),
    ) {
        Box(
            Modifier
                .matchParentSize()
                .clip(CategoryRightTrapezoid),
        ) {
            Image(
                painter = painterResource(podcastCategoryArt(category.name)),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .fillMaxWidth(CategoryImageRect + CategoryImageTriangle),
            )
        }
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight()
                .fillMaxWidth(0.42f)
                .padding(start = 14.dp, end = 4.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = category.name,
                color = MainPalette.Ink,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 20.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun podcastCategoryArt(name: String): Int {
    val key = name.trim().replace("|", "").replace("｜", "").replace(" ", "")
    return when (key) {
        "情感", "情感调频" -> R.drawable.podcast_cat_emotion
        "音乐播客", "音乐推荐", "音乐" -> R.drawable.podcast_cat_music
        "有声书" -> R.drawable.podcast_cat_audiobook
        "脱口秀" -> R.drawable.podcast_cat_talk
        "创作翻唱" -> R.drawable.podcast_cat_cover
        "电音", "3D电子" -> R.drawable.podcast_cat_edm
        "知识" -> R.drawable.podcast_cat_knowledge
        "二次元" -> R.drawable.podcast_cat_anime
        "明星专区" -> R.drawable.podcast_cat_star
        "生活" -> R.drawable.podcast_cat_life
        "亲子", "亲子宝贝" -> R.drawable.podcast_cat_family
        "新闻资讯" -> R.drawable.podcast_cat_news
        "广播剧" -> R.drawable.podcast_cat_drama
        "故事" -> R.drawable.podcast_cat_story
        "人文历史" -> R.drawable.podcast_cat_history
        "娱乐" -> R.drawable.podcast_cat_ent
        "相声曲艺" -> R.drawable.podcast_cat_quyi
        "文学出版", "美文读物" -> R.drawable.podcast_cat_lit
        else -> R.drawable.podcast_cat_other
    }
}

@Composable
internal fun PodcastLibraryBoard(
    feed: PodcastFeed,
    onOpenShow: (PodcastShow) -> Unit,
    onPlay: (List<PodcastEpisode>, Int) -> Unit,
    modifier: Modifier = Modifier,
    liked: List<PodcastEpisode> = emptyList(),
) {
    val empty = liked.isEmpty() && feed.subscribed.isEmpty() && feed.recentShows.isEmpty() &&
        feed.recentEpisodes.isEmpty() && feed.created.isEmpty()
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
    ) {
        item {
            if (empty) {
                Text(
                    t("喜欢的声音、订阅和最近收听会出现在这里"),
                    color = MainPalette.Secondary,
                    fontSize = 13.sp,
                )
            }
            EpisodeBlock(t("我喜欢的声音"), liked, onPlay)
            ShowBlock(t("我的订阅"), feed.subscribed, onOpenShow)
            EpisodeBlock(t("最近声音"), feed.recentEpisodes, onPlay)
            ShowBlock(t("最近播客"), feed.recentShows, onOpenShow)
            ShowBlock(t("我创建的"), feed.created, onOpenShow)
        }
    }
}

@Composable
private fun ShowBlock(
    title: String,
    shows: List<PodcastShow>,
    onOpen: (PodcastShow) -> Unit,
) {
    if (shows.isEmpty()) return
    val columns = if (LocalConfiguration.current.screenWidthDp >= 600) 2 else 1
    PodcastSectionTitle(title = title)
    Spacer(Modifier.height(4.dp))
    if (columns == 1) {
        shows.forEachIndexed { index, show ->
            PodcastShowLine(show, onClick = { onOpen(show) }, index = index + 1)
        }
    } else {
        shows.chunked(2).forEachIndexed { rowIndex, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                row.forEachIndexed { offset, show ->
                    val index = rowIndex * 2 + offset
                    PodcastShowLine(
                        show,
                        onClick = { onOpen(show) },
                        index = index + 1,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
    Spacer(Modifier.height(18.dp))
}

@Composable
private fun EpisodeBlock(
    title: String,
    episodes: List<PodcastEpisode>,
    onPlay: (List<PodcastEpisode>, Int) -> Unit,
) {
    if (episodes.isEmpty()) return
    val columns = if (LocalConfiguration.current.screenWidthDp >= 600) 2 else 1
    PodcastSectionTitle(title = title)
    Spacer(Modifier.height(4.dp))
    if (columns == 1) {
        episodes.forEachIndexed { index, episode ->
            PodcastEpisodeLine(episode, onClick = { onPlay(episodes, index) })
        }
    } else {
        episodes.chunked(2).forEachIndexed { rowIndex, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                row.forEachIndexed { offset, episode ->
                    val index = rowIndex * 2 + offset
                    PodcastEpisodeLine(
                        episode,
                        onClick = { onPlay(episodes, index) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
    Spacer(Modifier.height(18.dp))
}
