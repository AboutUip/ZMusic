package com.kite.zmusic.ui.settings

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kite.zmusic.ZMusicApplication
import com.kite.zmusic.data.SponsorEntry
import com.kite.zmusic.data.SponsorPodiumPlace
import com.kite.zmusic.data.sponsorPodium
import com.kite.zmusic.ui.main.MainPalette
import com.kite.zmusic.ui.main.wallpaperItemChrome
import com.kite.zmusic.i18n.t

private const val SponsorQueryLimit = 64

@Composable
fun SponsorListPage(
    contentBottomInset: Dp,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val app = remember { context.applicationContext as ZMusicApplication }
    val vm: CommunityCatalogViewModel<SponsorEntry> = viewModel(
        key = "settings-sponsors",
        factory = CommunityCatalogViewModelFactory(app.container.sponsorRepository, SponsorQueryLimit),
    )
    val ui by vm.state.collectAsStateWithLifecycle()
    // null：还在合计全部打赏。空列表是已经算完，没有可上榜的人。
    var podium by remember { mutableStateOf<List<SponsorPodiumPlace>?>(null) }
    LaunchedEffect(ui.refreshing) {
        if (ui.refreshing) return@LaunchedEffect
        val all = app.container.sponsorRepository.loadEntire()
        podium = if (all == null) emptyList() else sponsorPodium(all)
    }
    CommunityCatalogScaffold(
        ui = ui,
        onQueryChange = vm::onQueryChange,
        onLoadMore = vm::loadMore,
        onRefresh = vm::refresh,
        intro = t("谢谢每一位投喂。"),
        searchPlaceholder = t("搜索名字、金额或日期"),
        queryLimit = SponsorQueryLimit,
        emptyQueryMessage = t("暂时还没有记录。"),
        emptySearchMessage = t("没有匹配的记录。"),
        contentBottomInset = contentBottomInset,
        modifier = modifier,
        header = when (val places = podium) {
            null -> {
                { SponsorPodiumPlaceholder() }
            }
            else -> if (places.isEmpty()) {
                null
            } else {
                { SponsorPodium(places) }
            }
        },
        itemKey = { it.listKey },
    ) { entry ->
        SponsorCard(entry)
    }
}

@Composable
private fun SponsorPodium(places: List<SponsorPodiumPlace>) {
    val byRank = remember(places) { places.associateBy { it.rank } }
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = t("打赏榜"),
            style = TextStyle(
                color = MainPalette.Secondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            ),
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PodiumSlot(byRank[2], step = 72.dp, Modifier.weight(1f))
            PodiumSlot(byRank[1], step = 104.dp, Modifier.weight(1f))
            PodiumSlot(byRank[3], step = 56.dp, Modifier.weight(1f))
        }
        Spacer(Modifier.height(2.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(2.dp)
                .wallpaperItemChrome(RoundedCornerShape(1.dp), solid = MainPalette.Hairline),
        )
    }
}

@Composable
private fun SponsorPodiumPlaceholder() {
    val transition = rememberInfiniteTransition(label = "sponsorPodiumShimmer")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1300, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "sponsorPodiumShimmerShift",
    )
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = t("打赏榜"),
            style = TextStyle(
                color = MainPalette.Secondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            ),
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .sponsorDiagonalShimmer(progress),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PodiumPlaceholderSlot(step = 72.dp, nameFrac = 0.62f, Modifier.weight(1f))
            PodiumPlaceholderSlot(step = 104.dp, nameFrac = 0.78f, Modifier.weight(1f))
            PodiumPlaceholderSlot(step = 56.dp, nameFrac = 0.5f, Modifier.weight(1f))
        }
        Spacer(Modifier.height(2.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(2.dp)
                .wallpaperItemChrome(RoundedCornerShape(1.dp), solid = MainPalette.Hairline),
        )
    }
}

@Composable
private fun PodiumPlaceholderSlot(
    step: Dp,
    nameFrac: Float,
    modifier: Modifier = Modifier,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .fillMaxWidth(nameFrac)
                .height(12.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MainPalette.Placeholder),
        )
        Spacer(Modifier.height(8.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .height(step)
                .wallpaperItemChrome(
                    RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp, bottomStart = 4.dp, bottomEnd = 4.dp),
                )
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                Modifier
                    .width(22.dp)
                    .height(16.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MainPalette.Placeholder),
            )
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier
                    .fillMaxWidth(0.72f)
                    .height(10.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MainPalette.Placeholder),
            )
        }
    }
}

private fun Modifier.sponsorDiagonalShimmer(progress: Float): Modifier =
    graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val span = size.width + size.height
            if (span <= 0f) return@drawWithContent
            val head = -span + span * 2f * progress
            drawRect(
                brush = Brush.linearGradient(
                    colorStops = arrayOf(
                        0.38f to Color.Transparent,
                        0.5f to Color.White.copy(alpha = 0.5f),
                        0.62f to Color.Transparent,
                    ),
                    start = Offset(head, 0f),
                    end = Offset(head + size.height, size.height),
                ),
                blendMode = BlendMode.SrcAtop,
            )
        }

@Composable
private fun PodiumSlot(
    place: SponsorPodiumPlace?,
    step: Dp,
    modifier: Modifier = Modifier,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        if (place == null) return@Column
        Text(
            text = place.name,
            style = TextStyle(
                color = MainPalette.Ink,
                fontSize = if (place.rank == 1) 14.sp else 13.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(8.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .height(step)
                .wallpaperItemChrome(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp, bottomStart = 4.dp, bottomEnd = 4.dp))
                .padding(horizontal = 6.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = place.rank.toString(),
                style = TextStyle(
                    color = if (place.rank == 1) MainPalette.Accent else MainPalette.Ink,
                    fontSize = if (place.rank == 1) 22.sp else 18.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = place.amountLabel,
                style = TextStyle(
                    color = MainPalette.Accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SponsorCard(entry: SponsorEntry) {
    Row(
        Modifier
            .fillMaxWidth()
            .wallpaperItemChrome(RoundedCornerShape(16.dp))
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = entry.name,
                style = TextStyle(
                    color = MainPalette.Ink,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (entry.time.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = entry.time,
                    style = TextStyle(
                        color = MainPalette.Secondary,
                        fontSize = 12.sp,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (entry.amount.isNotBlank()) {
            Text(
                text = entry.amount,
                style = TextStyle(
                    color = MainPalette.Accent,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
            )
        }
    }
}
