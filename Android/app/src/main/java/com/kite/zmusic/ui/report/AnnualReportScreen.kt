package com.kite.zmusic.ui.report

import android.content.res.Configuration
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.runtime.key
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kite.zmusic.ZMusicApplication
import com.kite.zmusic.data.AnnualArtist
import com.kite.zmusic.data.AnnualChapter
import com.kite.zmusic.data.AnnualHourSlot
import com.kite.zmusic.data.AnnualReport
import com.kite.zmusic.data.AnnualReportLogic
import com.kite.zmusic.data.AnnualSong
import com.kite.zmusic.data.AnnualStyle
import com.kite.zmusic.data.SessionRepository
import com.kite.zmusic.data.TrackRow
import com.kite.zmusic.ui.common.UrlImage
import com.kite.zmusic.ui.icons.ZIcons
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private object AnnualTone {
    val Void = Color(0xFF07040C)
    val Wine = Color(0xFF3B1226)
    val Copper = Color(0xFFE2B56A)
    val Paper = Color(0xFFF4E6C8)
    val Rose = Color(0xFFD46A84)
    val Mist = Color(0xFFA090B4)
}

@Composable
fun AnnualReportScreen(
    sessionRepository: SessionRepository,
    contentBottomInset: Dp,
    onBack: () -> Unit,
    onPlayTracks: (List<TrackRow>, Int, Long?, String?) -> Unit,
    onOpenArtist: (Long, String, String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val app = LocalContext.current.applicationContext as ZMusicApplication
    val vm: AnnualReportViewModel = viewModel(
        factory = AnnualReportViewModelFactory(sessionRepository, app.ncmUserClient),
    )
    val ui by vm.ui.collectAsStateWithLifecycle()
    val reduceMotion = rememberAnimatorOff()
    val chapters = remember(ui.report) { AnnualReportLogic.chapters(ui.report) }

    Box(
        modifier
            .fillMaxSize()
            .background(AnnualTone.Void),
    ) {
        VinylAtmosphere(
            spinning = !reduceMotion,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to AnnualTone.Void.copy(alpha = 0.28f),
                        0.55f to Color.Transparent,
                        1f to AnnualTone.Void.copy(alpha = 0.72f),
                    ),
                ),
        )
        when {
            ui.loading -> LoadingPane(Modifier.fillMaxSize())
            ui.error != null && ui.report.isBlank() -> ErrorPane(
                message = ui.error!!,
                fallbackYear = ui.years.firstOrNull { AnnualReportLogic.hasClassicYearbook(it) }
                    ?.takeIf { it != ui.year },
                onRetry = { vm.load(ui.year) },
                onFallback = { vm.load(it) },
                modifier = Modifier.fillMaxSize(),
            )
            else -> {
                KeyedReportPager(
                    year = ui.year,
                    chapters = chapters,
                    ui = ui,
                    contentBottomInset = contentBottomInset,
                    reduceMotion = reduceMotion,
                    onPlayTracks = onPlayTracks,
                    onOpenArtist = onOpenArtist,
                )
            }
        }
        TopChrome(
            years = ui.years,
            year = ui.year,
            onBack = onBack,
            onYear = vm::load,
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding(),
        )
    }
}

@Composable
private fun KeyedReportPager(
    year: Int,
    chapters: List<AnnualChapter>,
    ui: AnnualReportUi,
    contentBottomInset: Dp,
    reduceMotion: Boolean,
    onPlayTracks: (List<TrackRow>, Int, Long?, String?) -> Unit,
    onOpenArtist: (Long, String, String?) -> Unit,
) {
    key(year) {
        val pager = rememberPagerState { chapters.size }
        ReportPager(
            pager = pager,
            chapters = chapters,
            ui = ui,
            contentBottomInset = contentBottomInset,
            reduceMotion = reduceMotion,
            onPlayTracks = onPlayTracks,
            onOpenArtist = onOpenArtist,
        )
    }
}

@Composable
private fun ReportPager(
    pager: PagerState,
    chapters: List<AnnualChapter>,
    ui: AnnualReportUi,
    contentBottomInset: Dp,
    reduceMotion: Boolean,
    onPlayTracks: (List<TrackRow>, Int, Long?, String?) -> Unit,
    onOpenArtist: (Long, String, String?) -> Unit,
) {
    val landscape =
        LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    Box(Modifier.fillMaxSize()) {
        VerticalPager(
            state = pager,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 1,
        ) { index ->
            val chapter = chapters[index]
            val pageOffset = pager.currentPageOffsetFraction + (pager.currentPage - index)
            val current = pager.currentPage == index
            ChapterPage(
                chapter = chapter,
                ui = ui,
                current = current,
                pageOffset = pageOffset,
                reduceMotion = reduceMotion,
                landscape = landscape,
                contentBottomInset = contentBottomInset,
                onPlayTracks = onPlayTracks,
                onOpenArtist = onOpenArtist,
            )
        }
        ChapterRail(
            count = chapters.size,
            current = pager.currentPage,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 10.dp),
        )
    }
}

@Composable
private fun ChapterPage(
    chapter: AnnualChapter,
    ui: AnnualReportUi,
    current: Boolean,
    pageOffset: Float,
    reduceMotion: Boolean,
    landscape: Boolean,
    contentBottomInset: Dp,
    onPlayTracks: (List<TrackRow>, Int, Long?, String?) -> Unit,
    onOpenArtist: (Long, String, String?) -> Unit,
) {
    val lift = if (reduceMotion) 0f else pageOffset.coerceIn(-1f, 1f)
    val pad = Modifier
        .fillMaxSize()
        .graphicsLayer {
            translationY = lift * 36f
            alpha = (1f - kotlin.math.abs(lift) * 0.18f).coerceIn(0.4f, 1f)
        }
        .padding(
            start = if (landscape) 28.dp else 22.dp,
            end = if (landscape) 36.dp else 28.dp,
            top = 92.dp,
            bottom = contentBottomInset + 18.dp,
        )
    val report = ui.report
    when (chapter) {
        AnnualChapter.Cover -> CoverPage(ui.year, ui.nickname, report, current, reduceMotion, pad)
        AnnualChapter.Time -> TimePage(report, current, reduceMotion, pad)
        AnnualChapter.Volume -> VolumePage(report, current, reduceMotion, pad)
        AnnualChapter.Crown -> CrownPage(report, current, reduceMotion, onPlayTracks, pad)
        AnnualChapter.Rank -> RankPage(report, current, reduceMotion, landscape, onPlayTracks, pad)
        AnnualChapter.Artists -> ArtistsPage(report, current, reduceMotion, onOpenArtist, pad)
        AnnualChapter.Styles -> StylesPage(report, current, reduceMotion, pad)
        AnnualChapter.Hours -> HoursPage(report, current, reduceMotion, pad)
        AnnualChapter.Close -> ClosePage(ui.year, ui.nickname, report, current, reduceMotion, pad)
    }
}

@Composable
private fun CoverPage(
    year: Int,
    nickname: String?,
    report: AnnualReport,
    current: Boolean,
    reduceMotion: Boolean,
    modifier: Modifier,
) {
    val spin = rememberInfiniteTransition(label = "label")
    val rot by spin.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(64000, easing = LinearEasing)),
        label = "labelRot",
    )
    Column(modifier, verticalArrangement = Arrangement.SpaceBetween) {
        Appear(current, reduceMotion, 0) {
            Column {
                Text("ANNUAL PRESSING", style = Kicker)
                Spacer(Modifier.height(6.dp))
                Text(
                    text = nickname?.takeIf { it.isNotBlank() } ?: "未署名",
                    color = AnnualTone.Paper,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "33⅓ RPM  ·  STEREO  ·  SIDE A  ·  ${year % 1000}",
                    color = AnnualTone.Mist,
                    fontSize = 11.sp,
                    letterSpacing = 1.6.sp,
                )
            }
        }
        Appear(current, reduceMotion, 80) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Canvas(
                    Modifier
                        .size(248.dp)
                        .graphicsLayer { rotationZ = if (reduceMotion) 0f else rot * 0.15f },
                ) {
                    val c = center
                    var r = size.minDimension / 2f
                    var i = 0
                    while (r > 58.dp.toPx()) {
                        drawCircle(
                            color = AnnualTone.Copper.copy(alpha = if (i % 4 == 0) 0.28f else 0.08f),
                            radius = r,
                            center = c,
                            style = Stroke(1.1f),
                        )
                        r -= 6.5.dp.toPx()
                        i++
                    }
                    drawCircle(AnnualTone.Wine, radius = 54.dp.toPx(), center = c)
                    drawCircle(
                        AnnualTone.Copper.copy(alpha = 0.95f),
                        radius = 54.dp.toPx(),
                        center = c,
                        style = Stroke(2.6.dp.toPx()),
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = year.toString(),
                        color = AnnualTone.Paper,
                        fontSize = 44.sp,
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold,
                    )
                    Text("MASTER", color = AnnualTone.Copper, fontSize = 10.sp, letterSpacing = 4.sp)
                }
            }
        }
        Appear(current, reduceMotion, 160) {
            Column {
                if (report.songs.isNotEmpty()) {
                    CoverStrip(report.songs.take(8).map { it.coverUrl })
                    Spacer(Modifier.height(12.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    report.listenDurationMs?.let {
                        MetaChip("${AnnualReportLogic.durationParts(it).hours} 小时")
                    }
                    report.playCount?.let { MetaChip("${AnnualReportLogic.formatCount(it)} 次") }
                    report.songCount?.let { MetaChip("${AnnualReportLogic.formatCount(it)} 首") }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = if (report.keyword.isNullOrBlank()) {
                        "这一年的声音压进这张片子里。"
                    } else {
                        "关键词 · ${report.keyword}"
                    },
                    color = AnnualTone.Paper,
                    fontSize = 18.sp,
                    fontFamily = FontFamily.Serif,
                    lineHeight = 26.sp,
                )
                Spacer(Modifier.height(8.dp))
                Text("上滑翻开 · 共 ${AnnualReportLogic.chapters(report).size} 面", color = AnnualTone.Mist, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun TimePage(
    report: AnnualReport,
    current: Boolean,
    reduceMotion: Boolean,
    modifier: Modifier,
) {
    val parts = AnnualReportLogic.durationParts(report.listenDurationMs ?: 0L)
    val shown = rememberCounting(parts.hours, current, reduceMotion)
    val yearHours = 24L * 365L
    val share = (parts.hours.toFloat() / yearHours.toFloat()).coerceIn(0.02f, 1f)
    Column(modifier, verticalArrangement = Arrangement.SpaceBetween) {
        Appear(current, reduceMotion, 0) {
            Column {
                Text("LISTENING TIME", style = Kicker)
                Spacer(Modifier.height(4.dp))
                Text("把一年听成连续的时间", color = AnnualTone.Mist, fontSize = 13.sp)
            }
        }
        Appear(current, reduceMotion, 70) {
            Column {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = shown.toString(),
                        color = AnnualTone.Paper,
                        fontSize = 78.sp,
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 78.sp,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "小时",
                        color = AnnualTone.Copper,
                        fontSize = 20.sp,
                        modifier = Modifier.padding(bottom = 14.dp),
                    )
                }
                Spacer(Modifier.height(10.dp))
                YearShareBar(share)
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "占一整年 ${yearHours} 小时的 ${trimPct(share)}",
                    color = AnnualTone.Mist,
                    fontSize = 12.sp,
                )
            }
        }
        Appear(current, reduceMotion, 140) {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetaChip("另有 ${parts.minutes} 分钟")
                    MetaChip("约 ${parts.days} 个整天")
                    if (parts.hours > 0L) MetaChip("日均 ${maxOf(1L, parts.hours / 365L)} 小时")
                }
                Spacer(Modifier.height(18.dp))
                MonthTicks()
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "时间没有声音。是你把它听成了年。",
                    color = AnnualTone.Paper,
                    fontSize = 16.sp,
                    fontFamily = FontFamily.Serif,
                    lineHeight = 24.sp,
                )
            }
        }
    }
}

@Composable
private fun VolumePage(
    report: AnnualReport,
    current: Boolean,
    reduceMotion: Boolean,
    modifier: Modifier,
) {
    val plays = rememberCounting(report.playCount ?: 0L, current, reduceMotion)
    val songs = rememberCounting(report.songCount ?: 0L, current, reduceMotion)
    val artists = rememberCounting(report.artistCount ?: 0L, current, reduceMotion)
    val maxPlay = (report.songs.maxOfOrNull { it.playCount } ?: 1L).coerceAtLeast(1L)
    val perSong = if ((report.songCount ?: 0L) > 0L) {
        (report.playCount ?: 0L).toFloat() / report.songCount!!.toFloat()
    } else {
        0f
    }
    val pulse = rememberInfiniteTransition(label = "volEq")
    val eq by pulse.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "volPulse",
    )
    Box(modifier) {
        Text(
            text = AnnualReportLogic.formatCount(plays),
            color = AnnualTone.Copper.copy(alpha = 0.07f),
            fontSize = 124.sp,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 18.dp, y = (-12).dp),
        )
        Column(Modifier.fillMaxSize()) {
            Appear(current, reduceMotion, 0) {
                Column {
                    Text("VOLUME", style = Kicker)
                    Spacer(Modifier.height(4.dp))
                    Text("针压下去的次数，和压过的槽", color = AnnualTone.Mist, fontSize = 13.sp)
                }
            }
            Spacer(Modifier.height(14.dp))
            Appear(current, reduceMotion, 50) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    VolumeTile("播放", plays, "次", Modifier.weight(1f))
                    VolumeTile("不同的歌", songs, "首", Modifier.weight(1f))
                    VolumeTile("歌手", artists, "位", Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(10.dp))
            Appear(current, reduceMotion, 90) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (perSong > 0f) {
                        MetaChip("每首约听 ${trimDecimalOne(perSong)} 次")
                    }
                    report.songs.firstOrNull()?.let {
                        MetaChip("最勤 ${it.name}")
                    }
                    if ((report.artistCount ?: 0L) > 0L && (report.songCount ?: 0L) > 0L) {
                        MetaChip("人均 ${maxOf(1L, (report.songCount ?: 1L) / (report.artistCount ?: 1L))} 首")
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            if (report.songs.isNotEmpty()) {
                Appear(current, reduceMotion, 120) {
                    CoverWall(
                        urls = report.songs.take(9).map { it.coverUrl },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(168.dp),
                    )
                }
                Spacer(Modifier.height(12.dp))
                Appear(current, reduceMotion, 150) {
                    VolumeSpectrum(
                        songs = report.songs.take(16),
                        maxPlay = maxPlay,
                        pulse = if (reduceMotion) 0.8f else eq,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp),
                    )
                }
                Spacer(Modifier.height(12.dp))
            }
            Appear(current, reduceMotion, 180) {
                Column {
                    Text("TOP CUTS 波形", color = AnnualTone.Copper, fontSize = 11.sp, letterSpacing = 2.sp)
                    Spacer(Modifier.height(8.dp))
                    report.songs.take(7).forEach { song ->
                        MiniWaveRow(song, maxPlay)
                        Spacer(Modifier.height(7.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun VolumeTile(label: String, value: Long, unit: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(AnnualTone.Wine.copy(alpha = 0.72f))
            .border(1.dp, AnnualTone.Copper.copy(alpha = 0.28f), RoundedCornerShape(14.dp))
            .padding(horizontal = 10.dp, vertical = 12.dp),
    ) {
        Text(label, color = AnnualTone.Mist, fontSize = 11.sp, letterSpacing = 1.2.sp)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = AnnualReportLogic.formatCount(value),
                color = AnnualTone.Paper,
                fontSize = 26.sp,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            Spacer(Modifier.width(4.dp))
            Text(unit, color = AnnualTone.Copper, fontSize = 11.sp, modifier = Modifier.padding(bottom = 3.dp))
        }
    }
}

@Composable
private fun CrownPage(
    report: AnnualReport,
    current: Boolean,
    reduceMotion: Boolean,
    onPlayTracks: (List<TrackRow>, Int, Long?, String?) -> Unit,
    modifier: Modifier,
) {
    val song = report.songs.firstOrNull() ?: return
    val queue = remember(report.songs) { report.toTrackRows() }
    val pulse = rememberInfiniteTransition(label = "eq")
    val eq by pulse.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(420, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "eqPulse",
    )
    Box(Modifier.fillMaxSize()) {
        UrlImage(
            url = song.coverUrl,
            contentDescription = song.name,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { scaleX = 1.08f; scaleY = 1.08f },
            contentScale = ContentScale.Crop,
            showPlaceholder = false,
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to AnnualTone.Void.copy(alpha = 0.62f),
                        0.38f to AnnualTone.Void.copy(alpha = 0.18f),
                        1f to AnnualTone.Void.copy(alpha = 0.94f),
                    ),
                ),
        )
        report.songs.drop(1).take(3).forEachIndexed { i, extra ->
            UrlImage(
                url = extra.coverUrl,
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 108.dp, end = 18.dp)
                    .offset(x = ((i - 1) * 18).dp, y = (i * 14).dp)
                    .size(54.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .border(1.dp, AnnualTone.Copper.copy(alpha = 0.45f), RoundedCornerShape(6.dp)),
                contentScale = ContentScale.Crop,
            )
        }
        Column(modifier, verticalArrangement = Arrangement.SpaceBetween) {
            Appear(current, reduceMotion, 0) {
                Column {
                    Text("SIDE A · 01", style = Kicker)
                    Spacer(Modifier.height(8.dp))
                    GrooveEq(eq = if (reduceMotion) 0.6f else eq)
                }
            }
            Appear(current, reduceMotion, 90) {
                Column {
                    Text("这一年听得最多", color = AnnualTone.Copper, fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = song.name,
                        color = AnnualTone.Paper,
                        fontSize = 32.sp,
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 40.sp,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(song.artists.ifBlank { "未知歌手" }, color = AnnualTone.Mist, fontSize = 15.sp)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (song.playCount > 0L) {
                            MetaChip("${AnnualReportLogic.formatCount(song.playCount)} 次")
                        }
                        report.songs.getOrNull(1)?.let { MetaChip("其次 ${it.name}") }
                    }
                    Spacer(Modifier.height(18.dp))
                    if (queue.isNotEmpty()) {
                        PlayChip("从这首开始") {
                            val idx = queue.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
                            onPlayTracks(queue, idx, null, "${report.year} 年度报告")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RankPage(
    report: AnnualReport,
    current: Boolean,
    reduceMotion: Boolean,
    landscape: Boolean,
    onPlayTracks: (List<TrackRow>, Int, Long?, String?) -> Unit,
    modifier: Modifier,
) {
    val queue = remember(report.songs) { report.toTrackRows() }
    val rows = report.songs.take(if (landscape) 10 else 8)
    val maxPlay = (rows.maxOfOrNull { it.playCount } ?: 1L).coerceAtLeast(1L)
    Column(modifier) {
        Appear(current, reduceMotion, 0) {
            Column {
                Text("TOP CUTS", style = Kicker)
                Spacer(Modifier.height(4.dp))
                Text("按针落下的次数排", color = AnnualTone.Mist, fontSize = 13.sp)
            }
        }
        Spacer(Modifier.height(14.dp))
        rows.forEachIndexed { index, song ->
            Appear(current, reduceMotion, 40 + index * 45) {
                RankRow(
                    index = index + 1,
                    song = song,
                    maxPlay = maxPlay,
                    onPlay = {
                        val idx = queue.indexOfFirst { it.id == song.id }
                        if (idx >= 0) onPlayTracks(queue, idx, null, "${report.year} 年度报告")
                    },
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun RankRow(index: Int, song: AnnualSong, maxPlay: Long, onPlay: () -> Unit) {
    val t = (song.playCount.toFloat() / maxPlay.toFloat()).coerceIn(0.08f, 1f)
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onPlay,
            ),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = index.toString().padStart(2, '0'),
                color = AnnualTone.Copper,
                fontFamily = FontFamily.Serif,
                fontSize = 20.sp,
                modifier = Modifier.width(36.dp),
            )
            UrlImage(
                url = song.coverUrl,
                contentDescription = song.name,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(4.dp)),
                contentScale = ContentScale.Crop,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = song.name,
                    color = AnnualTone.Paper,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = song.artists.ifBlank { "未知歌手" },
                    color = AnnualTone.Mist,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (song.playCount > 0L) {
                Text(
                    text = AnnualReportLogic.formatCount(song.playCount),
                    color = AnnualTone.Copper,
                    fontSize = 12.sp,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .padding(start = 36.dp)
                .fillMaxWidth(t)
                .height(2.dp)
                .background(AnnualTone.Copper.copy(alpha = 0.7f), RoundedCornerShape(99.dp)),
        )
    }
}

@Composable
private fun ArtistsPage(
    report: AnnualReport,
    current: Boolean,
    reduceMotion: Boolean,
    onOpenArtist: (Long, String, String?) -> Unit,
    modifier: Modifier,
) {
    val artists = report.artists.take(8)
    val spin = rememberInfiniteTransition(label = "orbit")
    val rot by spin.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(42000, easing = LinearEasing)),
        label = "orbitRot",
    )
    Column(modifier) {
        Appear(current, reduceMotion, 0) {
            Column {
                Text("PLAYERS", style = Kicker)
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "围着转盘的人",
                    color = AnnualTone.Paper,
                    fontSize = 22.sp,
                    fontFamily = FontFamily.Serif,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "共 ${artists.size} 位 · 点头像可打开",
                    color = AnnualTone.Mist,
                    fontSize = 12.sp,
                )
            }
        }
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            val sizePx = with(LocalDensity.current) { minOf(maxWidth, maxHeight).toPx() }
            val radius = sizePx * 0.34f
            val density = LocalDensity.current
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(
                    color = AnnualTone.Copper.copy(alpha = 0.18f),
                    radius = radius,
                    center = center,
                    style = Stroke(1.2f),
                )
            }
            artists.forEachIndexed { i, artist ->
                val base = -PI / 2.0 + 2.0 * PI * i / artists.size
                val extra = if (reduceMotion) 0.0 else rot * PI / 180.0
                val angle = base + extra
                val dx = (cos(angle) * radius)
                val dy = (sin(angle) * radius)
                ArtistDot(
                    artist = artist,
                    featured = i == 0,
                    onClick = {
                        if (artist.id > 0L) onOpenArtist(artist.id, artist.name, artist.coverUrl)
                    },
                    modifier = Modifier.offset(
                        x = with(density) { dx.toFloat().toDp() },
                        y = with(density) { dy.toFloat().toDp() },
                    ),
                )
            }
        }
        Appear(current, reduceMotion, 120) {
            Text(
                text = artists.take(4).joinToString("  ·  ") { it.name },
                color = AnnualTone.Mist,
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ArtistDot(
    artist: AnnualArtist,
    featured: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val size = if (featured) 76.dp else 58.dp
    Column(
        modifier
            .width(84.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        UrlImage(
            url = artist.coverUrl,
            contentDescription = artist.name,
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .border(1.dp, AnnualTone.Copper.copy(alpha = 0.7f), CircleShape),
            contentScale = ContentScale.Crop,
        )
        Spacer(Modifier.height(5.dp))
        Text(
            text = artist.name,
            color = AnnualTone.Paper,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (artist.playCount > 0L) {
            Text(
                text = AnnualReportLogic.formatCount(artist.playCount),
                color = AnnualTone.Copper,
                fontSize = 10.sp,
            )
        }
    }
}

@Composable
private fun StylesPage(
    report: AnnualReport,
    current: Boolean,
    reduceMotion: Boolean,
    modifier: Modifier,
) {
    val max = (report.styles.maxOfOrNull { it.playCount } ?: 1L).coerceAtLeast(1L)
    Column(modifier, verticalArrangement = Arrangement.SpaceBetween) {
        Appear(current, reduceMotion, 0) {
            Column {
                Text("GROOVE", style = Kicker)
                Spacer(Modifier.height(8.dp))
                if (!report.keyword.isNullOrBlank()) {
                    Text(
                        text = report.keyword!!,
                        color = AnnualTone.Paper,
                        fontSize = 40.sp,
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold,
                    )
                    Text("这一年的关键词", color = AnnualTone.Mist, fontSize = 13.sp)
                } else {
                    Text(
                        text = "针走过的槽",
                        color = AnnualTone.Paper,
                        fontSize = 22.sp,
                        fontFamily = FontFamily.Serif,
                    )
                }
            }
        }
        Appear(current, reduceMotion, 80) {
            Column {
                report.styles.take(8).forEachIndexed { i, style ->
                    Appear(current, reduceMotion, 80 + i * 40) {
                        StyleBar(style, max)
                    }
                    Spacer(Modifier.height(10.dp))
                }
            }
        }
        if (report.facts.isNotEmpty()) {
            Appear(current, reduceMotion, 200) {
                Column {
                    report.facts.forEach { fact ->
                        Text("${fact.label}  ·  ${fact.value}", color = AnnualTone.Mist, fontSize = 13.sp)
                        Spacer(Modifier.height(6.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun StyleBar(style: AnnualStyle, max: Long) {
    val t = (style.playCount.toFloat() / max.toFloat()).coerceIn(0.12f, 1f)
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(style.name, color = AnnualTone.Paper, fontSize = 14.sp)
            if (style.playCount > 0L) {
                Text(
                    AnnualReportLogic.formatCount(style.playCount),
                    color = AnnualTone.Copper,
                    fontSize = 12.sp,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier
                .fillMaxWidth(t)
                .height(4.dp)
                .background(AnnualTone.Copper, RoundedCornerShape(99.dp)),
        )
    }
}

@Composable
private fun HoursPage(
    report: AnnualReport,
    current: Boolean,
    reduceMotion: Boolean,
    modifier: Modifier,
) {
    val peak = AnnualReportLogic.peakHourLabel(report.hours)
    val parts = dayParts(report.hours)
    Column(
        modifier,
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Appear(current, reduceMotion, 0) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("CLOCK", style = Kicker)
                Spacer(Modifier.height(4.dp))
                Text("针最常在哪个钟点落下", color = AnnualTone.Mist, fontSize = 13.sp)
            }
        }
        Appear(current, reduceMotion, 70) {
            HourClock(report.hours, Modifier.size(228.dp))
        }
        Appear(current, reduceMotion, 140) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = peak?.let { "最常落在$it" } ?: "听歌的钟点散落在一天里",
                    color = AnnualTone.Paper,
                    fontSize = 18.sp,
                    fontFamily = FontFamily.Serif,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    parts.forEach { MetaChip("${it.label} ${it.pct}") }
                }
            }
        }
    }
}

@Composable
private fun HourClock(hours: List<AnnualHourSlot>, modifier: Modifier) {
    val max = (hours.maxOfOrNull { it.playCount } ?: 1L).coerceAtLeast(1L).toFloat()
    Canvas(modifier) {
        val c = center
        val outer = size.minDimension / 2f
        val inner = outer * 0.42f
        drawCircle(AnnualTone.Wine.copy(alpha = 0.55f), radius = outer)
        drawCircle(AnnualTone.Void, radius = inner)
        hours.forEach { slot ->
            if (slot.playCount <= 0L) return@forEach
            val start = -90f + slot.hour * 15f
            val t = (slot.playCount / max).coerceIn(0.08f, 1f)
            drawArc(
                color = AnnualTone.Copper.copy(alpha = 0.25f + 0.65f * t),
                startAngle = start,
                sweepAngle = 13.5f,
                useCenter = false,
                topLeft = Offset(c.x - outer, c.y - outer),
                size = Size(outer * 2, outer * 2),
                style = Stroke(width = (outer - inner) * t, cap = StrokeCap.Butt),
            )
        }
        drawCircle(
            color = AnnualTone.Copper.copy(alpha = 0.4f),
            radius = inner,
            style = Stroke(width = 1.5.dp.toPx()),
        )
    }
}

@Composable
private fun ClosePage(
    year: Int,
    nickname: String?,
    report: AnnualReport,
    current: Boolean,
    reduceMotion: Boolean,
    modifier: Modifier,
) {
    val mosaic = report.songs.take(6)
    Column(modifier, verticalArrangement = Arrangement.SpaceBetween) {
        Appear(current, reduceMotion, 0) {
            Text("ENDLESS SIDE", style = Kicker)
        }
        Appear(current, reduceMotion, 70) {
            Column {
                Text(
                    text = "$year",
                    color = AnnualTone.Paper,
                    fontSize = 58.sp,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = (nickname?.takeIf { it.isNotBlank() } ?: "你") + " 的听歌年",
                    color = AnnualTone.Copper,
                    fontSize = 18.sp,
                )
                Spacer(Modifier.height(14.dp))
                if (mosaic.isNotEmpty()) {
                    CoverStrip(mosaic.map { it.coverUrl })
                    Spacer(Modifier.height(12.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    report.listenDurationMs?.let {
                        MetaChip("${AnnualReportLogic.durationParts(it).hours} 小时")
                    }
                    report.playCount?.let { MetaChip("${AnnualReportLogic.formatCount(it)} 次") }
                    report.songs.firstOrNull()?.let { MetaChip(it.name) }
                }
            }
        }
        Appear(current, reduceMotion, 160) {
            Text(
                text = "片子可以翻面。年份不会。",
                color = AnnualTone.Paper,
                fontSize = 16.sp,
                fontFamily = FontFamily.Serif,
            )
        }
    }
}

@Composable
private fun TopChrome(
    years: List<Int>,
    year: Int,
    onBack: () -> Unit,
    onYear: (Int) -> Unit,
    modifier: Modifier,
) {
    Row(
        modifier.padding(horizontal = 6.dp, vertical = 4.dp),
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
                contentDescription = "返回",
                tint = AnnualTone.Paper,
                modifier = Modifier.size(22.dp),
            )
        }
        LazyRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(end = 12.dp),
        ) {
            items(years, key = { it }) { y ->
                val selected = y == year
                Text(
                    text = y.toString(),
                    color = if (selected) AnnualTone.Void else AnnualTone.Paper.copy(alpha = 0.8f),
                    fontSize = 12.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier
                        .clip(RoundedCornerShape(99.dp))
                        .background(if (selected) AnnualTone.Copper else Color.White.copy(alpha = 0.08f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onYear(y) },
                        )
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun ChapterRail(count: Int, current: Int, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(count) { i ->
            Box(
                Modifier
                    .width(3.dp)
                    .height(if (i == current) 18.dp else 7.dp)
                    .background(
                        if (i == current) AnnualTone.Copper else AnnualTone.Mist.copy(alpha = 0.45f),
                        RoundedCornerShape(99.dp),
                    ),
            )
        }
    }
}

@Composable
private fun PlayChip(label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(99.dp))
            .background(AnnualTone.Copper)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = ZIcons.Play,
            contentDescription = null,
            tint = AnnualTone.Void,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(label, color = AnnualTone.Void, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun LoadingPane(modifier: Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Text(
            text = "压片中",
            color = AnnualTone.Copper,
            fontFamily = FontFamily.Serif,
            fontSize = 18.sp,
        )
    }
}

@Composable
private fun ErrorPane(
    message: String,
    fallbackYear: Int?,
    onRetry: () -> Unit,
    onFallback: (Int) -> Unit,
    modifier: Modifier,
) {
    Column(
        modifier.padding(horizontal = 28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = message,
            color = AnnualTone.Paper,
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
            lineHeight = 24.sp,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "再试一次",
            color = AnnualTone.Void,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .clip(RoundedCornerShape(99.dp))
                .background(AnnualTone.Copper)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onRetry,
                )
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )
        if (fallbackYear != null) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = "查看 $fallbackYear",
                color = AnnualTone.Copper,
                fontSize = 13.sp,
                modifier = Modifier
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onFallback(fallbackYear) },
                    )
                    .padding(8.dp),
            )
        }
    }
}

@Composable
private fun VinylAtmosphere(spinning: Boolean, modifier: Modifier) {
    val infinite = rememberInfiniteTransition(label = "vinyl")
    val rot by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(52000, easing = LinearEasing)),
        label = "groove",
    )
    val sweep by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(18000, easing = LinearEasing)),
        label = "sweep",
    )
    val dust by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(14000, easing = LinearEasing)),
        label = "dust",
    )
    Canvas(modifier.background(AnnualTone.Void)) {
        val c = Offset(size.width * 0.72f, size.height * 0.38f)
        val maxR = size.minDimension * 0.78f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(AnnualTone.Wine.copy(alpha = 0.85f), AnnualTone.Void),
                center = c,
                radius = maxR,
            ),
            radius = maxR,
            center = c,
        )
        val left = Offset(size.width * 0.18f, size.height * 0.78f)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(AnnualTone.Wine.copy(alpha = 0.4f), Color.Transparent),
                center = left,
                radius = maxR * 0.45f,
            ),
            radius = maxR * 0.45f,
            center = left,
        )
        rotate(if (spinning) rot else 12f, c) {
            var r = maxR
            var i = 0
            while (r > 36f) {
                drawCircle(
                    color = AnnualTone.Copper.copy(alpha = if (i % 6 == 0) 0.16f else 0.05f),
                    radius = r,
                    center = c,
                    style = Stroke(width = 1.2f),
                )
                r -= 8.4f
                i++
            }
        }
        rotate(if (spinning) sweep else 48f, c) {
            drawCircle(
                brush = Brush.sweepGradient(
                    colors = listOf(
                        Color.Transparent,
                        AnnualTone.Copper.copy(alpha = 0.18f),
                        Color.Transparent,
                        Color.Transparent,
                    ),
                    center = c,
                ),
                radius = maxR * 0.92f,
                center = c,
            )
        }
        val t = if (spinning) dust else 0.3f
        for (i in 0 until 42) {
            val ang = (i * 17f + t * 220f) * (PI.toFloat() / 180f)
            val rad = size.minDimension * (0.08f + (i % 9) * 0.07f)
            val x = size.width * 0.5f + cos(ang) * rad * (0.7f + (i % 5) * 0.12f)
            val y = size.height * 0.45f + sin(ang * 1.15f) * rad
            drawCircle(
                color = AnnualTone.Copper.copy(alpha = 0.06f + (i % 6) * 0.03f),
                radius = 1.4f + (i % 4),
                center = Offset(x, y),
            )
        }
    }
}

@Composable
private fun Appear(
    active: Boolean,
    reduceMotion: Boolean,
    delayMs: Int,
    content: @Composable () -> Unit,
) {
    val t = remember { Animatable(if (reduceMotion) 1f else 0f) }
    LaunchedEffect(active, reduceMotion, delayMs) {
        if (reduceMotion) {
            t.snapTo(1f)
            return@LaunchedEffect
        }
        if (active) {
            t.snapTo(0f)
            t.animateTo(1f, tween(720, delayMs, FastOutSlowInEasing))
        }
    }
    Box(
        Modifier.graphicsLayer {
            alpha = t.value
            translationY = (1f - t.value) * 26f
        },
    ) { content() }
}

@Composable
private fun CoverStrip(urls: List<String?>) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        urls.take(8).forEach { url ->
            UrlImage(
                url = url,
                contentDescription = null,
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .border(1.dp, AnnualTone.Copper.copy(alpha = 0.25f), RoundedCornerShape(5.dp)),
                contentScale = ContentScale.Crop,
            )
        }
    }
}

@Composable
private fun CoverWall(urls: List<String?>, modifier: Modifier = Modifier) {
    val rows = urls.take(9).chunked(3)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        rows.forEach { row ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                row.forEach { url ->
                    UrlImage(
                        url = url,
                        contentDescription = null,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.dp, AnnualTone.Copper.copy(alpha = 0.22f), RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop,
                    )
                }
                repeat(3 - row.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun VolumeSpectrum(
    songs: List<AnnualSong>,
    maxPlay: Long,
    pulse: Float,
    modifier: Modifier = Modifier,
) {
    val max = maxPlay.toFloat().coerceAtLeast(1f)
    Canvas(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(AnnualTone.Wine.copy(alpha = 0.55f)),
    ) {
        if (songs.isEmpty()) return@Canvas
        val gap = 3.dp.toPx()
        val barW = ((size.width - gap * (songs.size + 1)) / songs.size).coerceAtLeast(2.dp.toPx())
        songs.forEachIndexed { i, song ->
            val t = (song.playCount / max).coerceIn(0.08f, 1f)
            val wobble = 0.82f + 0.18f * pulse * if (i % 2 == 0) 1f else 0.7f
            val h = size.height * t * wobble
            val x = gap + i * (barW + gap)
            drawRoundRect(
                color = if (i == 0) AnnualTone.Copper else AnnualTone.Rose.copy(alpha = 0.55f + 0.35f * t),
                topLeft = Offset(x, size.height - h),
                size = Size(barW, h),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx(), 3.dp.toPx()),
            )
        }
    }
}

private fun trimDecimalOne(value: Float): String {
    val tenths = kotlin.math.round(value * 10f).toInt()
    return if (tenths % 10 == 0) (tenths / 10).toString() else "${tenths / 10}.${tenths % 10}"
}

@Composable
private fun MetaChip(text: String) {
    Text(
        text = text,
        color = AnnualTone.Paper,
        fontSize = 11.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .clip(RoundedCornerShape(99.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .border(1.dp, AnnualTone.Copper.copy(alpha = 0.28f), RoundedCornerShape(99.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

@Composable
private fun YearShareBar(share: Float) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(99.dp))
            .background(Color.White.copy(alpha = 0.08f)),
    ) {
        Box(
            Modifier
                .fillMaxWidth(share)
                .height(6.dp)
                .background(AnnualTone.Copper, RoundedCornerShape(99.dp)),
        )
    }
}

@Composable
private fun MonthTicks() {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        listOf("1", "3", "5", "7", "9", "11").forEach { m ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .width(2.dp)
                        .height(10.dp)
                        .background(AnnualTone.Copper.copy(alpha = 0.7f)),
                )
                Spacer(Modifier.height(4.dp))
                Text(m, color = AnnualTone.Mist, fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun MiniWaveRow(song: AnnualSong, maxPlay: Long) {
    val t = (song.playCount.toFloat() / maxPlay.toFloat()).coerceIn(0.1f, 1f)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            song.name,
            color = AnnualTone.Paper,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(88.dp),
        )
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier
                .weight(1f)
                .height(3.dp)
                .clip(RoundedCornerShape(99.dp))
                .background(Color.White.copy(alpha = 0.08f)),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(t)
                    .height(3.dp)
                    .background(AnnualTone.Rose.copy(alpha = 0.9f)),
            )
        }
    }
}

@Composable
private fun GrooveEq(eq: Float) {
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Bottom) {
        val hs = listOf(0.45f, 0.9f, 0.6f, 1f, 0.35f, 0.75f, 0.5f)
        hs.forEachIndexed { i, h ->
            val phase = if (i % 2 == 0) eq else 1f - eq * 0.45f
            val amp = 10.dp + 18.dp * h * phase
            Box(
                Modifier
                    .width(3.dp)
                    .height(amp)
                    .background(AnnualTone.Copper, RoundedCornerShape(99.dp)),
            )
        }
    }
}

private data class DayPart(val label: String, val pct: String)

private fun dayParts(hours: List<AnnualHourSlot>): List<DayPart> {
    val buckets = longArrayOf(0, 0, 0, 0)
    hours.forEach { slot ->
        val i = when (slot.hour) {
            in 0..5 -> 0
            in 6..11 -> 1
            in 12..17 -> 2
            else -> 3
        }
        buckets[i] += slot.playCount
    }
    val total = buckets.sum().coerceAtLeast(1L)
    val names = listOf("凌晨", "上午", "下午", "夜里")
    return names.mapIndexed { i, name ->
        DayPart(name, "${(buckets[i] * 100L / total)}%")
    }
}

private fun trimPct(share: Float): String {
    val n = kotlin.math.round(share * 1000f) / 10f
    return if (n < 0.1f) "<0.1%" else "${n}%"
}

@Composable
private fun rememberCounting(target: Long, active: Boolean, reduceMotion: Boolean): Long {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(target, active, reduceMotion) {
        if (!active) return@LaunchedEffect
        if (reduceMotion) {
            anim.snapTo(target.toFloat())
            return@LaunchedEffect
        }
        anim.snapTo(0f)
        anim.animateTo(
            target.toFloat(),
            tween(durationMillis = 1100, easing = FastOutSlowInEasing),
        )
    }
    return anim.value.toLong()
}

@Composable
private fun rememberAnimatorOff(): Boolean {
    val context = LocalContext.current
    return remember {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f
    }
}

private val Kicker = TextStyle(
    color = AnnualTone.Copper,
    fontSize = 11.sp,
    fontWeight = FontWeight.SemiBold,
    letterSpacing = 3.sp,
)
