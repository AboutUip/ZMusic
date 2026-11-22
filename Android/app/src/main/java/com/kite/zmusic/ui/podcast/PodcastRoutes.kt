package com.kite.zmusic.ui.podcast

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kite.zmusic.data.NcmCommentParse
import com.kite.zmusic.data.NcmJson
import com.kite.zmusic.data.NcmPodcastClient
import com.kite.zmusic.data.NcmPodcastParse
import com.kite.zmusic.data.PodcastEpisode
import com.kite.zmusic.data.PodcastSessionCache
import com.kite.zmusic.data.PodcastShow
import com.kite.zmusic.data.SongComment
import com.kite.zmusic.i18n.t
import com.kite.zmusic.ui.common.UrlImage
import com.kite.zmusic.ui.icons.ZIcons
import com.kite.zmusic.ui.theme.MainPalette
import kotlinx.coroutines.launch

@Composable
internal fun PodcastShowPage(
    client: NcmPodcastClient,
    cookie: String,
    showId: Long,
    title: String,
    cache: PodcastSessionCache,
    onBack: () -> Unit,
    onPlay: (List<PodcastEpisode>, Int) -> Unit,
) {
    val seeded = cache.episodes(showId, false)
    var show by remember(showId) { mutableStateOf(cache.applySubscription(seeded?.show)) }
    var episodes by remember(showId) { mutableStateOf(seeded?.episodes ?: emptyList()) }
    var asc by remember(showId) { mutableStateOf(false) }
    var more by remember(showId) { mutableStateOf(seeded?.hasMore == true) }
    var loading by remember(showId) { mutableStateOf(seeded == null) }
    var loadingMore by remember(showId) { mutableStateOf(false) }
    var error by remember(showId) { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    suspend fun load(reset: Boolean) {
        val offset = if (reset) 0 else episodes.size
        val detail = if (reset) client.djDetail(cookie, showId) else null
        val programs = client.djProgram(cookie, showId, limit = 30, offset = offset, asc = asc)
        if (detail != null) show = cache.applySubscription(NcmPodcastParse.show(detail) ?: show)
        val page = NcmPodcastParse.episodes(programs)
        val fresh = if (reset) page else page.filter { next -> episodes.none { it.programId == next.programId } }
        episodes = if (reset) page else episodes + fresh
        more = fresh.isNotEmpty() && (NcmPodcastParse.hasMore(programs) || page.size >= 30)
        cache.saveEpisodes(showId, asc, show, episodes, more)
        if (reset && show == null && episodes.isEmpty() && !NcmPodcastParse.ok(programs)) {
            error = t("播客加载失败")
        } else {
            error = null
        }
    }

    LaunchedEffect(showId, asc, cookie) {
        val hit = cache.episodes(showId, asc)
        if (hit != null) {
            show = cache.applySubscription(hit.show ?: show)
            episodes = hit.episodes
            more = hit.hasMore
            loading = false
            return@LaunchedEffect
        }
        if (episodes.isEmpty()) loading = true
        runCatching { load(reset = true) }
            .onFailure { error = NcmJson.userFacingThrowable(it, "播客加载失败") }
        loading = false
    }
    val nearEnd = rememberPodcastNearEnd(listState)
    LaunchedEffect(nearEnd, more, episodes.size, loading, loadingMore) {
        if (!nearEnd || !more || loading || loadingMore || episodes.isEmpty()) return@LaunchedEffect
        loadingMore = true
        scope.launch {
            runCatching { load(reset = false) }
                .onFailure { error = NcmJson.userFacingThrowable(it, "播客加载失败") }
            loadingMore = false
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
    ) {
        PodcastBackTitle(show?.name ?: title, onBack)
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item(key = "head") {
                val current = show
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PodcastCover(current?.coverUrl, 108.dp, shape = RoundedCornerShape(22.dp))
                    Spacer(Modifier.size(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = current?.name ?: title,
                            color = MainPalette.Ink,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                        current?.djName?.let {
                            Spacer(Modifier.height(4.dp))
                            Text(it, color = MainPalette.Secondary, fontSize = 13.sp, maxLines = 1)
                        }
                        Spacer(Modifier.height(10.dp))
                        Row {
                            Text(
                                text = if (current?.subscribed == true) t("已订阅") else t("订阅"),
                                color = if (current?.subscribed == true) MainPalette.Ink else MainPalette.Page,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(if (current?.subscribed == true) MainPalette.Surface else MainPalette.Accent)
                                    .clickable(enabled = current != null && !busy) {
                                        val target = current ?: return@clickable
                                        scope.launch {
                                            busy = true
                                            val next = !target.subscribed
                                            val json = client.djSub(cookie, target.id, next)
                                            if (NcmPodcastParse.ok(json)) {
                                                val updated = target.copy(subscribed = next)
                                                show = updated
                                                cache.rememberSubscription(updated)
                                            }
                                            busy = false
                                        }
                                    }
                                    .padding(horizontal = 14.dp, vertical = 7.dp),
                            )
                            Spacer(Modifier.size(8.dp))
                            Text(
                                text = t("播放"),
                                color = MainPalette.Ink,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(MainPalette.Surface)
                                    .clickable(enabled = episodes.isNotEmpty()) { onPlay(episodes, 0) }
                                    .padding(horizontal = 14.dp, vertical = 7.dp),
                            )
                        }
                    }
                }
                current?.desc?.let { desc ->
                    Spacer(Modifier.height(14.dp))
                    Text(desc, color = MainPalette.Secondary, fontSize = 13.sp, lineHeight = 19.sp, maxLines = 6, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = t("声音"),
                        color = MainPalette.Ink,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = if (asc) t("正序") else t("倒序"),
                        color = MainPalette.Accent,
                        fontSize = 13.sp,
                        modifier = Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { asc = !asc },
                    )
                }
            }
            if (loading && episodes.isEmpty()) {
                item(key = "loading") {
                    Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MainPalette.Accent, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                    }
                }
            }
            error?.let { message ->
                item(key = "error") {
                    Text(message, color = MainPalette.Secondary, fontSize = 13.sp, modifier = Modifier.padding(vertical = 8.dp))
                }
            }
            items(episodes, key = { it.programId }) { episode ->
                PodcastEpisodeLine(episode, onClick = {
                    val index = episodes.indexOfFirst { it.programId == episode.programId }
                    onPlay(episodes, index.coerceAtLeast(0))
                })
            }
            if (loadingMore) {
                item(key = "more") {
                    Box(Modifier.fillMaxWidth().height(48.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            color = MainPalette.Accent,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun PodcastSearchPage(
    client: NcmPodcastClient,
    cookie: String,
    cache: PodcastSessionCache,
    onBack: () -> Unit,
    onOpenShow: (PodcastShow) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var shows by remember { mutableStateOf<List<PodcastShow>>(emptyList()) }
    var more by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var loadingMore by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    val listState = rememberLazyListState()
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    suspend fun fetch(reset: Boolean) {
        val text = query.trim()
        if (text.isEmpty()) {
            shows = emptyList()
            more = false
            return
        }
        val key = "s:$text"
        if (reset) {
            cache.search(key)?.let { hit ->
                shows = hit.shows
                more = hit.hasMore
                return
            }
        }
        val offset = if (reset) 0 else shows.size
        val json = client.search(cookie, text, 1009, offset = offset)
        if (query.trim() != text) return
        val page = NcmPodcastParse.shows(json)
        val fresh = if (reset) page else page.filter { next -> shows.none { it.id == next.id } }
        shows = if (reset) page else shows + fresh
        more = fresh.isNotEmpty() && page.size >= 30
        cache.saveSearch(key, shows, emptyList(), more)
    }
    LaunchedEffect(query, cookie) {
        val text = query.trim()
        if (text.isEmpty()) {
            shows = emptyList()
            more = false
            loading = false
            return@LaunchedEffect
        }
        if (shows.isEmpty()) loading = true
        runCatching { fetch(reset = true) }
        loading = false
    }
    val nearEnd = rememberPodcastNearEnd(listState)
    LaunchedEffect(nearEnd, more, shows.size, loading, loadingMore) {
        if (!nearEnd || !more || loading || loadingMore) return@LaunchedEffect
        if (shows.isEmpty()) return@LaunchedEffect
        loadingMore = true
        scope.launch {
            runCatching { fetch(reset = false) }
            loadingMore = false
        }
    }
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
    ) {
        PodcastBackTitle(
            t("搜索"),
            onBack,
            modifier = Modifier.offset(x = (-12).dp),
        )
        val fieldStyle = TextStyle(
            color = MainPalette.Ink,
            fontSize = 16.sp,
            lineHeight = 20.sp,
            platformStyle = PlatformTextStyle(includeFontPadding = false),
            lineHeightStyle = LineHeightStyle(
                alignment = LineHeightStyle.Alignment.Center,
                trim = LineHeightStyle.Trim.None,
            ),
        )
        BasicTextField(
            value = query,
            onValueChange = { query = it },
            textStyle = fieldStyle,
            cursorBrush = SolidColor(MainPalette.Accent),
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focus)
                .clip(CircleShape)
                .background(MainPalette.Surface)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) {
                        Text(
                            t("输入电台名"),
                            style = fieldStyle.copy(color = MainPalette.Hint),
                        )
                    }
                    inner()
                }
            },
        )
        Spacer(Modifier.height(12.dp))
        if (query.isBlank()) {
            Text(t("输入关键词后再搜索"), color = MainPalette.Secondary, fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp))
        } else if (loading) {
            Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MainPalette.Accent, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                if (shows.isEmpty()) {
                    item { Text(t("没有找到电台"), color = MainPalette.Secondary, fontSize = 13.sp, modifier = Modifier.padding(vertical = 12.dp)) }
                }
                items(shows, key = { "s${it.id}" }) { show ->
                    PodcastShowLine(show, onClick = { onOpenShow(show) })
                }
                if (loadingMore) {
                    item {
                        Box(Modifier.fillMaxWidth().height(48.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = MainPalette.Accent, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun PodcastCatalogPage(
    title: String,
    shows: List<PodcastShow>,
    episodes: List<PodcastEpisode>,
    loading: Boolean,
    error: String?,
    onBack: () -> Unit,
    onOpenShow: (PodcastShow) -> Unit,
    onPlay: (List<PodcastEpisode>, Int) -> Unit,
    onLoadMore: (() -> Unit)?,
) {
    val listState = rememberLazyListState()
    val nearEnd = rememberPodcastNearEnd(listState)
    val itemCount = shows.size + episodes.size
    val loadMore = onLoadMore
    LaunchedEffect(nearEnd, itemCount, loadMore != null) {
        if (nearEnd && itemCount > 0 && loadMore != null) loadMore()
    }
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
    ) {
        PodcastBackTitle(title, onBack)
        if (loading && shows.isEmpty() && episodes.isEmpty()) {
            Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MainPalette.Accent, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
            }
        }
        error?.let {
            Text(it, color = MainPalette.Secondary, fontSize = 13.sp)
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            items(shows, key = { it.id }) { show ->
                PodcastShowLine(show, onClick = { onOpenShow(show) })
            }
            items(episodes, key = { it.programId }) { episode ->
                PodcastEpisodeLine(episode, onClick = {
                    onPlay(episodes, episodes.indexOfFirst { it.programId == episode.programId }.coerceAtLeast(0))
                })
            }
            if (shows.isEmpty() && episodes.isEmpty() && !loading && error == null) {
                item { Text(t("这里还没有内容"), color = MainPalette.Secondary, fontSize = 13.sp, modifier = Modifier.padding(vertical = 12.dp)) }
            }
        }
    }
}

@Composable
internal fun PodcastCommentsPage(
    client: NcmPodcastClient,
    cookie: String,
    programId: Long,
    title: String,
    cache: PodcastSessionCache,
    onBack: () -> Unit,
) {
    val seeded = cache.comments(programId)
    var comments by remember(programId) { mutableStateOf(seeded?.comments ?: emptyList()) }
    var draft by remember(programId) { mutableStateOf("") }
    var loading by remember(programId) { mutableStateOf(seeded == null) }
    var loadingMore by remember(programId) { mutableStateOf(false) }
    var more by remember(programId) { mutableStateOf(seeded?.hasMore == true) }
    var error by remember(programId) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    suspend fun load(reset: Boolean) {
        val offset = if (reset) 0 else comments.size
        val json = client.commentDj(cookie, programId, offset = offset)
        val page = NcmCommentParse.pageFromCommentMusic(json, includeHotFirst = reset)
        val fresh = if (reset) page.comments else page.comments.filter { next -> comments.none { it.commentId == next.commentId } }
        comments = if (reset) page.comments else comments + fresh
        more = fresh.isNotEmpty() && page.hasMore
        cache.saveComments(programId, comments, more)
    }

    LaunchedEffect(programId, cookie) {
        if (cache.comments(programId) != null) {
            loading = false
            return@LaunchedEffect
        }
        loading = comments.isEmpty()
        runCatching { load(true) }.onFailure { error = NcmJson.userFacingThrowable(it, "评论加载失败") }
        loading = false
    }
    val nearEnd = rememberPodcastNearEnd(listState)
    LaunchedEffect(nearEnd, more, comments.size, loading, loadingMore) {
        if (!nearEnd || !more || loading || loadingMore || comments.isEmpty()) return@LaunchedEffect
        loadingMore = true
        scope.launch {
            runCatching { load(false) }.onFailure { error = NcmJson.userFacingThrowable(it, "评论加载失败") }
            loadingMore = false
        }
    }

    val canSend = draft.isNotBlank()
    Column(
        Modifier
            .fillMaxSize()
            .imePadding()
            .padding(horizontal = 16.dp),
    ) {
        PodcastBackTitle(title, onBack)
        Text(
            text = when {
                loading && comments.isEmpty() -> t("评论")
                comments.isEmpty() -> t("还没有评论")
                more -> t("评论")
                else -> t("%d 条评论", comments.size)
            },
            color = MainPalette.Secondary,
            fontSize = 13.sp,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (loading && comments.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MainPalette.Accent, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
                    }
                }
            }
            error?.let { message ->
                item {
                    Text(
                        message,
                        color = MainPalette.Secondary,
                        fontSize = 13.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(MainPalette.Surface)
                            .padding(14.dp),
                    )
                }
            }
            if (!loading && comments.isEmpty() && error == null) {
                item {
                    Text(
                        t("还没有评论，来写下第一条"),
                        color = MainPalette.Secondary,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(vertical = 28.dp),
                    )
                }
            }
            items(comments, key = { it.commentId }) { comment ->
                CommentCard(comment)
            }
            if (loadingMore) {
                item {
                    Box(Modifier.fillMaxWidth().height(48.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            color = MainPalette.Accent,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(MainPalette.Surface)
                .padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = draft,
                onValueChange = { draft = it },
                textStyle = TextStyle(color = MainPalette.Ink, fontSize = 15.sp),
                cursorBrush = SolidColor(MainPalette.Accent),
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 8.dp),
                decorationBox = { inner ->
                    Box {
                        if (draft.isEmpty()) Text(t("写下评论"), color = MainPalette.Hint, fontSize = 15.sp)
                        inner()
                    }
                },
            )
            Box(
                Modifier
                    .padding(start = 8.dp)
                    .height(36.dp)
                    .clip(CircleShape)
                    .background(if (canSend) MainPalette.Accent else MainPalette.Page)
                    .clickable(enabled = canSend) {
                        val text = draft.trim()
                        if (text.isEmpty()) return@clickable
                        scope.launch {
                            val json = client.commentPost(cookie, programId, text)
                            if (NcmPodcastParse.ok(json)) {
                                draft = ""
                                runCatching { load(true) }
                            }
                        }
                    }
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = t("发送"),
                    color = if (canSend) Color.White else MainPalette.Hint,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun CommentCard(comment: SongComment) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MainPalette.Surface)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MainPalette.Page),
            ) {
                UrlImage(
                    url = comment.avatarUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Column(
                Modifier
                    .weight(1f)
                    .padding(start = 10.dp),
            ) {
                Text(
                    text = comment.nickname,
                    color = MainPalette.Ink,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (comment.timeLabel.isNotBlank()) {
                    Text(
                        text = comment.timeLabel,
                        color = MainPalette.Secondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                    )
                }
            }
            if (comment.liked || comment.likedCount > 0) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (comment.liked) ZIcons.Favorite else ZIcons.FavoriteBorder,
                        contentDescription = null,
                        tint = if (comment.liked) MainPalette.Accent else MainPalette.Hint,
                        modifier = Modifier.size(14.dp),
                    )
                    if (comment.likedCount > 0) {
                        Text(
                            text = comment.likedCount.toString(),
                            color = if (comment.liked) MainPalette.Accent else MainPalette.Secondary,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                }
            }
        }
        val quote = comment.repliedContent?.takeIf { it.isNotBlank() }
        if (quote != null) {
            val who = comment.repliedNickname?.takeIf { it.isNotBlank() }
            Text(
                text = if (who != null) "$who：$quote" else quote,
                color = MainPalette.Secondary,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(top = 10.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MainPalette.Page)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            )
        }
        if (comment.content.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = comment.content,
                color = MainPalette.Ink,
                fontSize = 15.sp,
                lineHeight = 22.sp,
            )
        }
    }
}

@Composable
internal fun rememberPodcastNearEnd(state: LazyListState): Boolean {
    val near by remember(state) {
        derivedStateOf {
            val info = state.layoutInfo
            val total = info.totalItemsCount
            if (total <= 0) return@derivedStateOf false
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: return@derivedStateOf false
            last >= total - 3
        }
    }
    return near
}
