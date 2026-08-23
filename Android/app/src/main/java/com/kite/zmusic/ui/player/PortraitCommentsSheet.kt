package com.kite.zmusic.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.SnapshotStateSet
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastForEach
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kite.zmusic.ZMusicApplication
import com.kite.zmusic.data.CommentHugUser
import com.kite.zmusic.data.NcmJson
import com.kite.zmusic.data.SongComment
import com.kite.zmusic.data.SongCommentsCache
import com.kite.zmusic.data.SongCommentsSnapshot
import com.kite.zmusic.ui.common.UrlImage
import com.kite.zmusic.ui.main.MainPalette
import com.kite.zmusic.ui.main.pageSheetHazeStyle
import com.kite.zmusic.ui.notice.showIslandNotice
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import kotlin.math.hypot
import kotlin.math.min
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

private val CommentLabel get() = MainPalette.Ink
private val CommentHint get() = MainPalette.Secondary
private val CommentAccent get() = MainPalette.Accent
private val CommentIconTint get() = MainPalette.Ink
private val CommentQuoteBg get() = MainPalette.Placeholder
private val CommentPanelShape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)
private val CommentOpenEasing = CubicBezierEasing(0.16f, 1.02f, 0.3f, 1f)
private val CommentCloseEasing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)
private val CommentAvatarBg get() = MainPalette.Placeholder
private val CommentComposerFill get() = MainPalette.Placeholder
private val CommentComposerDock get() = MainPalette.Surface

private const val CommentPageSize = 20

private data class CachedFloor(
    val replies: List<SongComment>,
    val hasMore: Boolean,
)

/**
 * `/comment/new`：与 NeteaseCloudMusicApi 一致。
 * 2=热度（服务端用 pageNo 生成 `normalHot#offset`，不要传 time cursor）；
 * 3=时间（第二页起传上一条 `time`）。
 */
private enum class CommentSortMode(val sortType: Int, val label: String) {
    Hot(2, "热度"),
    Time(3, "时间"),
}

private data class CommentReplyTarget(
    val commentId: Long,
    val nickname: String,
)

/**
 * 竖屏播放条：总时长上方的评论入口（仅非「容器包含」时使用）。
 */
@Composable
fun NowPlayingCommentsIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .size(width = 32.dp, height = 22.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        TransportCommentsIcon(size = 16.dp, tint = Color(0xFFE8EEF5).copy(alpha = 0.92f))
    }
}

/** 对话气泡：与传输条线描图标同族。 */
@Composable
fun TransportCommentsIcon(
    modifier: Modifier = Modifier,
    size: Dp = 16.dp,
    tint: Color = CommentIconTint,
) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = Stroke(
            width = min(w, h) * 0.11f,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        )
        val left = w * 0.12f
        val right = w * 0.88f
        val top = h * 0.14f
        val bottom = h * 0.62f
        drawRoundRect(
            color = tint,
            topLeft = Offset(left, top),
            size = Size(right - left, bottom - top),
            cornerRadius = CornerRadius(w * 0.12f, w * 0.12f),
            style = stroke,
        )
        val tail = Path().apply {
            moveTo(w * 0.28f, bottom)
            lineTo(w * 0.22f, h * 0.84f)
            lineTo(w * 0.42f, bottom)
        }
        drawPath(tail, tint, style = stroke)
        val cy = (top + bottom) / 2f
        val r = min(w, h) * 0.045f
        drawCircle(tint, r, Offset(w * 0.34f, cy))
        drawCircle(tint, r, Offset(w * 0.50f, cy))
        drawCircle(tint, r, Offset(w * 0.66f, cy))
    }
}

/**
 * 竖屏评论底栏面板：默认 2/3；上箭头扩至全屏；无拖拽改高。
 * 外壳与「更多 / 曲谱 / 音源」同一套：磨砂 + SheetWash，无外侧描边。
 */
@Composable
fun PortraitCommentsSheet(
    songId: Long,
    cookie: String,
    openProgress: Float,
    sheetFrac: Float,
    onExpandFullscreen: () -> Unit,
    onCollapseToTwoThirds: () -> Unit,
    modifier: Modifier = Modifier,
    coverUrl: String? = null,
    hazeState: HazeState? = null,
    onOpenUser: (Long, String, String?) -> Unit = { _, _, _ -> },
) {
    val t = openProgress.coerceIn(0f, 1f)
    val fullscreen = sheetFrac >= 0.97f
    var listGeneration by remember { mutableIntStateOf(0) }
    val listState = remember(listGeneration) { LazyListState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val app = context.applicationContext as ZMusicApplication
    val commentsVm: CommentsViewModel = viewModel(
        key = "comments-$songId",
        factory = CommentsViewModelFactory(app.commentsRepository),
    )
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val comments = remember { mutableStateListOf<SongComment>() }
    val expandedTextIds: SnapshotStateSet<Long> = remember { mutableStateSetOf() }
    val openFloorIds: SnapshotStateSet<Long> = remember { mutableStateSetOf() }
    val floorCache = remember { mutableStateMapOf<Long, CachedFloor>() }
    var pageNo by remember { mutableIntStateOf(0) }
    var cursor by remember { mutableStateOf<String?>(null) }
    var hasMore by remember { mutableStateOf(true) }
    var loading by remember { mutableStateOf(false) }
    var bootstrapped by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var total by remember { mutableLongStateOf(0L) }
    var useLegacy by remember { mutableStateOf(false) }
    var selfUid by remember { mutableLongStateOf(0L) }
    var selfNickname by remember { mutableStateOf("我") }
    var selfAvatar by remember { mutableStateOf<String?>(null) }
    var sortMode by remember { mutableStateOf(CommentSortMode.Hot) }
    var replyTarget by remember { mutableStateOf<CommentReplyTarget?>(null) }
    var replyDraft by remember { mutableStateOf("") }
    var replySending by remember { mutableStateOf(false) }
    var composerFocused by remember { mutableStateOf(false) }
    // 回复时若从 2/3 升全屏，关闭输入框后还原
    var restoreSheetAfterReply by remember { mutableStateOf(false) }
    var composerHeightPx by remember { mutableIntStateOf(0) }
    var floorRefreshTick by remember { mutableStateOf<Map<Long, Int>>(emptyMap()) }
    // 刚发送的回复置顶展示；用户再次展开/手动刷新楼层后恢复真实顺序
    var pendingFloorTop by remember { mutableStateOf<Map<Long, SongComment>>(emptyMap()) }
    val replyFocus = remember { FocusRequester() }
    val density = LocalDensity.current
    val songIdUpdated by rememberUpdatedState(songId)
    val cookieUpdated by rememberUpdatedState(cookie)
    val expandFullscreenUpdated by rememberUpdatedState(onExpandFullscreen)
    var fetchSeq by remember { mutableIntStateOf(0) }
    val collapseToTwoThirdsUpdated by rememberUpdatedState(onCollapseToTwoThirds)
    val coverUrlUpdated by rememberUpdatedState(coverUrl)

    fun hint(msg: String) {
        context.showIslandNotice(msg, coverUrlUpdated)
    }

    fun persistCommentsCache(
        forSongId: Long = songIdUpdated,
        forSort: Int = sortMode.sortType,
    ) {
        if (forSongId <= 0L || comments.isEmpty()) return
        SongCommentsCache.put(
            SongCommentsSnapshot(
                songId = forSongId,
                sortType = forSort,
                comments = comments.toList(),
                pageNo = pageNo,
                cursor = cursor,
                hasMore = hasMore,
                total = total,
                useLegacy = useLegacy,
                expandedTextIds = expandedTextIds.toSet(),
                openFloorIds = openFloorIds.toSet(),
            ),
        )
    }

    fun applyCommentsCache(snap: SongCommentsSnapshot) {
        comments.clear()
        comments.addAll(snap.comments)
        pageNo = snap.pageNo
        cursor = snap.cursor
        hasMore = snap.hasMore
        total = snap.total
        useLegacy = snap.useLegacy
        bootstrapped = true
        error = null
        loading = false
        expandedTextIds.clear()
        expandedTextIds.addAll(snap.expandedTextIds)
        openFloorIds.clear()
        openFloorIds.addAll(snap.openFloorIds)
        floorCache.clear()
    }

    fun patchComment(id: Long, transform: (SongComment) -> SongComment) {
        val i = comments.indexOfFirst { it.commentId == id }
        if (i >= 0) comments[i] = transform(comments[i])
        persistCommentsCache()
    }

    fun dismissReplyComposer(restoreSheet: Boolean = true) {
        val shouldRestore = restoreSheet && restoreSheetAfterReply
        restoreSheetAfterReply = false
        replyTarget = null
        replyDraft = ""
        replySending = false
        composerFocused = false
        keyboard?.hide()
        focusManager.clearFocus(force = true)
        if (shouldRestore) {
            collapseToTwoThirdsUpdated()
        }
    }

    LaunchedEffect(cookie, openProgress > 0.5f) {
        if (cookie.isBlank() || openProgress <= 0.5f) return@LaunchedEffect
        try {
            val profile = commentsVm.selfProfile(cookie)
            selfUid = profile.uid
            selfNickname = profile.nickname
            selfAvatar = profile.avatarUrl
        } catch (_: Exception) {
            selfUid = 0L
        }
    }

    // 回复或输入主评：先升全屏再弹键盘，避免 2/3 面板被 imePadding 压扁
    LaunchedEffect(replyTarget?.commentId, composerFocused) {
        val typing = replyTarget != null || composerFocused
        if (!typing) return@LaunchedEffect
        if (sheetFrac < 0.97f) {
            restoreSheetAfterReply = true
            expandFullscreenUpdated()
            delay(360)
        } else {
            delay(60)
        }
        val target = replyTarget
        if (target != null) {
            val index = comments.indexOfFirst { it.commentId == target.commentId }
            if (index >= 0) {
                runCatching { listState.animateScrollToItem(index) }
            }
        }
        runCatching { replyFocus.requestFocus() }
        keyboard?.show()
    }

    suspend fun loadMore(reset: Boolean, requested: CommentSortMode = sortMode) {
        if (!reset && (loading || !hasMore)) return
        val seq = fetchSeq + 1
        fetchSeq = seq
        loading = true
        if (reset) error = null
        try {
            val nextPage = if (reset) 1 else pageNo + 1
            val mode = requested
            val page = if (!useLegacy) {
                try {
                    commentsVm.pageNew(
                        songId = songIdUpdated,
                        cookie = cookieUpdated,
                        pageNo = nextPage,
                        pageSize = CommentPageSize,
                        sortType = mode.sortType,
                        cursor = when {
                            // 接口按 sortType=3 要 cursor，与 UI 文案无关
                            mode.sortType == 3 && !reset -> cursor
                            else -> null
                        },
                    )
                } catch (e: Exception) {
                    // 翻页失败不切旧接口，避免热度/时间列表被混进另一套排序
                    if (!reset) throw e
                    if (mode != CommentSortMode.Time) throw e
                    useLegacy = true
                    commentsVm.pageLegacy(
                        songId = songIdUpdated,
                        cookie = cookieUpdated,
                        limit = CommentPageSize,
                        offset = 0,
                        before = null,
                        includeHotFirst = false,
                    )
                }
            } else {
                commentsVm.pageLegacy(
                    songId = songIdUpdated,
                    cookie = cookieUpdated,
                    limit = CommentPageSize,
                    offset = if (reset) 0 else comments.size,
                    before = if (!reset) {
                        comments.lastOrNull()?.timeMs?.takeIf { it > 0L }
                    } else {
                        null
                    },
                    includeHotFirst = reset,
                )
            }
            if (seq != fetchSeq) return
            if (reset) comments.clear()
            val existing = comments.mapTo(HashSet()) { it.commentId }
            var added = 0
            page.comments.forEach { c ->
                if (existing.add(c.commentId)) {
                    comments.add(c)
                    added++
                }
            }
            pageNo = nextPage
            cursor = when {
                mode.sortType == 3 ->
                    page.comments.lastOrNull()?.timeMs?.takeIf { it > 0L }?.toString()
                        ?: page.cursor
                else -> null
            }
            hasMore = when {
                !page.hasMore -> false
                page.comments.isEmpty() -> false
                added == 0 && nextPage >= 3 -> false
                else -> true
            }
            if (page.total > 0L) total = page.total
            bootstrapped = true
            error = null
            persistCommentsCache()
            if (reset) {
                runCatching { listState.scrollToItem(0) }
            }
        } catch (e: Exception) {
            if (seq != fetchSeq) return
            error = NcmJson.userFacingThrowable(e, "加载失败")
            bootstrapped = true
            if (!reset) {
                hasMore = true
            }
        } finally {
            if (seq == fetchSeq) loading = false
        }
    }

    suspend fun switchSort(mode: CommentSortMode) {
        if (mode == sortMode && !bootstrapped) return
        persistCommentsCache()
        if (mode != sortMode) {
            sortMode = mode
            dismissReplyComposer()
            total = 0L
        }
        val cached = SongCommentsCache.get(songIdUpdated, mode.sortType)
        if (cached != null) {
            applyCommentsCache(cached)
            listGeneration += 1
            return
        }
        pageNo = 0
        cursor = null
        hasMore = true
        bootstrapped = false
        error = null
        useLegacy = false
        pendingFloorTop = emptyMap()
        comments.clear()
        expandedTextIds.clear()
        openFloorIds.clear()
        floorCache.clear()
        listGeneration += 1
        loadMore(reset = true, requested = mode)
    }

    fun optimisticPosted(
        posted: SongComment?,
        text: String,
        repliedNickname: String?,
    ): SongComment = posted?.copy(
        nickname = posted.nickname.ifBlank { selfNickname },
        avatarUrl = posted.avatarUrl ?: selfAvatar,
        timeLabel = posted.timeLabel.ifBlank { "刚刚" },
        repliedNickname = posted.repliedNickname ?: repliedNickname,
    ) ?: SongComment(
        commentId = -System.currentTimeMillis(),
        content = text,
        timeMs = System.currentTimeMillis(),
        timeLabel = "刚刚",
        likedCount = 0,
        liked = false,
        replyCount = 0,
        userId = selfUid,
        nickname = selfNickname,
        avatarUrl = selfAvatar,
        repliedContent = null,
        repliedNickname = repliedNickname,
    )

    suspend fun sendComposer() {
        val target = replyTarget
        val text = replyDraft.trim()
        if (text.isEmpty()) {
            hint(if (target != null) "请输入回复内容" else "请输入评论")
            return
        }
        if (cookieUpdated.isBlank() || selfUid <= 0L) {
            hint(if (target != null) "请先登录后再回复" else "请先登录后再评论")
            return
        }
        if (replySending) return
        replySending = true
        try {
            val failHint = if (target != null) "回复失败，请稍后重试" else "评论失败，请稍后重试"
            val postedResult = commentsVm.post(
                songId = songIdUpdated,
                content = text,
                cookie = cookieUpdated,
                replyCommentId = target?.commentId,
            )
            if (!postedResult.ok) {
                throw IllegalStateException(postedResult.message.ifBlank { failHint })
            }
            val posted = postedResult.comment
            val optimistic = optimisticPosted(posted, text, target?.nickname)
            if (target != null) {
                pendingFloorTop = pendingFloorTop + (target.commentId to optimistic)
                patchComment(target.commentId) { it.copy(replyCount = it.replyCount + 1) }
                floorRefreshTick = floorRefreshTick + (
                    target.commentId to ((floorRefreshTick[target.commentId] ?: 0) + 1)
                    )
                if (total > 0L) total += 1L
                hint("回复成功")
                dismissReplyComposer(restoreSheet = false)
                val parentIndex = comments.indexOfFirst { it.commentId == target.commentId }
                if (parentIndex >= 0) {
                    runCatching { listState.animateScrollToItem(parentIndex) }
                }
            } else {
                comments.add(0, optimistic)
                if (total > 0L) total += 1L else total = comments.size.toLong()
                hint("评论成功")
                dismissReplyComposer(restoreSheet = true)
                persistCommentsCache()
                runCatching { listState.animateScrollToItem(0) }
            }
        } catch (e: Exception) {
            val failHint = if (target != null) "回复失败，请稍后重试" else "评论失败，请稍后重试"
            hint(NcmJson.userFacingThrowable(e, failHint))
        } finally {
            replySending = false
        }
    }

    LaunchedEffect(songId) {
        dismissReplyComposer()
        floorRefreshTick = emptyMap()
        pendingFloorTop = emptyMap()
        listGeneration += 1
        val cached = SongCommentsCache.get(songId, sortMode.sortType)
        if (cached != null) {
            applyCommentsCache(cached)
        } else {
            comments.clear()
            pageNo = 0
            cursor = null
            hasMore = true
            bootstrapped = false
            error = null
            total = 0L
            useLegacy = false
            expandedTextIds.clear()
            openFloorIds.clear()
            floorCache.clear()
            loadMore(reset = true, requested = sortMode)
        }
    }

    DisposableEffect(songId, sortMode) {
        onDispose {
            persistCommentsCache(forSongId = songId, forSort = sortMode.sortType)
        }
    }

    LaunchedEffect(listGeneration, bootstrapped) {
        if (!bootstrapped || comments.isEmpty()) return@LaunchedEffect
        runCatching { listState.scrollToItem(0) }
    }

    LaunchedEffect(listState, listGeneration, songId) {
        snapshotFlow {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            val totalItems = info.totalItemsCount
            val can = bootstrapped && hasMore && !loading && comments.size >= CommentPageSize
            Triple(last, totalItems, can)
        }
            .distinctUntilChanged()
            .collect { (last, totalItems, can) ->
                if (can && totalItems > 0 && last >= (totalItems - 3).coerceAtLeast(0)) {
                    loadMore(reset = false)
                }
            }
    }

    val statusTop = WindowInsets.statusBars
        .asPaddingValues()
        .calculateTopPadding()
    val typingComposer = replyTarget != null || composerFocused
    val listBottomPad = run {
        val measured = with(density) { composerHeightPx.toDp() }
        if (measured > 0.dp) measured else 56.dp
    }

    Box(
        modifier
            .fillMaxHeight()
            .fillMaxWidth()
            .clip(if (fullscreen) RoundedCornerShape(0.dp) else CommentPanelShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {
                    if (typingComposer) {
                        if (replyTarget != null) dismissReplyComposer()
                        else {
                            composerFocused = false
                            keyboard?.hide()
                            focusManager.clearFocus(force = true)
                        }
                    }
                },
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
                .padding(top = if (fullscreen) statusTop else 0.dp)
                .padding(horizontal = 16.dp)
                .padding(top = 14.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "评论",
                        style = TextStyle(
                            color = CommentLabel,
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            letterSpacing = (-0.2).sp,
                        ),
                    )
                    Text(
                        text = if (total > 0L) "共 $total 条" else "说说你的想法",
                        style = TextStyle(
                            color = CommentHint,
                            fontFamily = FontFamily.SansSerif,
                            fontSize = 13.sp,
                        ),
                    )
                }
                CommentSortSegment(
                    selected = sortMode,
                    onSelect = { mode -> scope.launch { switchSort(mode) } },
                )
                Spacer(Modifier.width(10.dp))
                CommentHeaderArrowButton(
                    expanded = fullscreen,
                    onClick = {
                        if (fullscreen) onCollapseToTwoThirds()
                        else onExpandFullscreen()
                    },
                )
            }

            Spacer(Modifier.height(12.dp))

            when {
                !bootstrapped && loading -> {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(bottom = listBottomPad),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            color = CommentAccent.copy(alpha = 0.75f),
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }
                error != null && comments.isEmpty() -> {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(bottom = listBottomPad),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = error ?: "加载失败",
                                color = CommentHint,
                                fontSize = 13.sp,
                            )
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = "重试",
                                color = CommentAccent,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = { scope.launch { loadMore(reset = true) } },
                                ),
                            )
                        }
                    }
                }
                comments.isEmpty() -> {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(bottom = listBottomPad),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "还没有评论",
                            color = CommentHint.copy(alpha = 0.8f),
                            fontSize = 13.sp,
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentPadding = PaddingValues(
                            bottom = listBottomPad,
                        ),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(
                            items = comments,
                            key = { it.commentId },
                        ) { item ->
                            CommentRow(
                                comment = item,
                                songId = songIdUpdated,
                                cookie = cookieUpdated,
                                selfUid = selfUid,
                                commentsVm = commentsVm,
                                floorRefreshTick = floorRefreshTick[item.commentId] ?: 0,
                                pendingTopReply = pendingFloorTop[item.commentId],
                                replyActive = replyTarget?.commentId == item.commentId,
                                textExpanded = item.commentId in expandedTextIds,
                                onTextExpandedChange = { expanded ->
                                    if (expanded) {
                                        expandedTextIds.add(item.commentId)
                                    } else {
                                        expandedTextIds.remove(item.commentId)
                                    }
                                    persistCommentsCache()
                                },
                                repliesOpen = item.commentId in openFloorIds,
                                onRepliesOpenChange = { open ->
                                    if (open) {
                                        openFloorIds.add(item.commentId)
                                    } else {
                                        openFloorIds.remove(item.commentId)
                                    }
                                    persistCommentsCache()
                                },
                                initialReplies = floorCache[item.commentId]?.replies.orEmpty(),
                                initialRepliesHasMore = floorCache[item.commentId]?.hasMore == true,
                                onFloorCache = { replies, hasMore ->
                                    floorCache[item.commentId] = CachedFloor(replies, hasMore)
                                },
                                onPatchComment = ::patchComment,
                                onHint = { hint(it) },
                                onOpenUser = onOpenUser,
                                onConsumePendingTopReply = {
                                    pendingFloorTop = pendingFloorTop - item.commentId
                                },
                                onReplyClick = {
                                    replyTarget = CommentReplyTarget(
                                        commentId = item.commentId,
                                        nickname = item.nickname,
                                    )
                                    replyDraft = ""
                                },
                            )
                        }
                        item(key = "footer") {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                when {
                                    loading -> CircularProgressIndicator(
                                        color = CommentAccent.copy(alpha = 0.7f),
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(22.dp),
                                    )
                                    !hasMore -> Text(
                                        text = "已经到底了",
                                        color = CommentHint.copy(alpha = 0.55f),
                                        fontSize = 12.sp,
                                    )
                                    error != null -> Text(
                                        text = "加载失败，上滑重试",
                                        color = CommentHint.copy(alpha = 0.75f),
                                        fontSize = 12.sp,
                                        modifier = Modifier.clickable(
                                            interactionSource = remember {
                                                MutableInteractionSource()
                                            },
                                            indication = null,
                                            onClick = {
                                                scope.launch { loadMore(reset = false) }
                                            },
                                        ),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 入场时轻微压暗感由宿主 alpha 负责；此处 t 仅作内部可用标记
        if (t < 0.02f) {
            Box(Modifier.matchParentSize())
        }

        CommentComposerBar(
            targetNickname = replyTarget?.nickname,
            draft = replyDraft,
            sending = replySending,
            focusRequester = replyFocus,
            onDraftChange = { replyDraft = it },
            onSend = { scope.launch { sendComposer() } },
            onDismissReply = { dismissReplyComposer() },
            onFocusChange = { focused -> composerFocused = focused },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .onSizeChanged { composerHeightPx = it.height },
        )
    }
}

@Composable
private fun CommentSortSegment(
    selected: CommentSortMode,
    onSelect: (CommentSortMode) -> Unit,
) {
    Row(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color(0x14000000))
            .border(1.dp, MainPalette.Hairline, RoundedCornerShape(999.dp))
            .padding(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CommentSortMode.entries.forEach { mode ->
            val on = mode == selected
            Text(
                text = mode.label,
                style = TextStyle(
                    color = if (on) CommentAccent else CommentHint,
                    fontSize = 12.sp,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                ),
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (on) MainPalette.Card else Color.Transparent)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onSelect(mode) },
                    )
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            )
        }
    }
}

@Composable
private fun CommentComposerBar(
    targetNickname: String?,
    draft: String,
    sending: Boolean,
    focusRequester: FocusRequester,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onDismissReply: () -> Unit,
    onFocusChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val canSend = draft.trim().isNotEmpty() && !sending
    val replying = !targetNickname.isNullOrBlank()
    Column(
        modifier
            .fillMaxWidth()
            .background(CommentComposerDock)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            ),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .background(MainPalette.Hairline),
        )
        Column(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
                .padding(horizontal = 14.dp)
                .padding(top = 8.dp, bottom = 8.dp),
        ) {
            if (replying) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "回复 @$targetNickname",
                        style = TextStyle(
                            color = CommentAccent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "取消",
                        style = TextStyle(
                            color = CommentHint,
                            fontSize = 12.sp,
                        ),
                        modifier = Modifier
                            .clickable(
                                enabled = !sending,
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onDismissReply,
                            )
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                    )
                }
                Spacer(Modifier.height(8.dp))
            }
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    Modifier
                        .weight(1f)
                        .heightIn(min = 40.dp, max = 120.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(CommentComposerFill)
                        .border(1.dp, MainPalette.Hairline, RoundedCornerShape(14.dp))
                        .padding(start = 12.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
                ) {
                    if (draft.isEmpty()) {
                        Text(
                            text = if (replying) "写下你的回复…" else "说说你的想法…",
                            color = CommentHint,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(end = 44.dp),
                        )
                    }
                    BasicTextField(
                        value = draft,
                        onValueChange = { if (it.length <= 140) onDraftChange(it) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(end = 44.dp)
                            .focusRequester(focusRequester)
                            .onFocusChanged { onFocusChange(it.isFocused) },
                        textStyle = TextStyle(
                            color = CommentLabel,
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                        ),
                        cursorBrush = SolidColor(CommentAccent),
                        maxLines = 5,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(
                            onSend = { if (canSend) onSend() },
                        ),
                        enabled = !sending,
                    )
                    Text(
                        text = "${draft.length}/140",
                        color = CommentHint,
                        fontSize = 10.sp,
                        modifier = Modifier.align(Alignment.CenterEnd),
                    )
                }
                Box(
                    Modifier
                        .widthIn(min = 56.dp)
                        .height(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (canSend) CommentAccent else MainPalette.TrackOff,
                        )
                        .clickable(
                            enabled = canSend,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onSend,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (sending) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color.White,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text(
                            text = "发送",
                            color = if (canSend) Color.White else CommentHint,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CommentHeaderArrowButton(
    expanded: Boolean,
    onClick: () -> Unit,
) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "commentArrow",
    )
    Box(
        Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(MainPalette.Card)
            .border(1.dp, MainPalette.Hairline, CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .graphicsLayer { rotationZ = rotation },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(16.dp)) {
            val w = this.size.width
            val h = this.size.height
            val stroke = Stroke(
                width = min(w, h) * 0.14f,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            )
            // 上箭头（展开后旋转 180° 变下箭头）
            drawLine(
                CommentIconTint,
                Offset(w * 0.22f, h * 0.58f),
                Offset(w * 0.50f, h * 0.32f),
                stroke.width,
                StrokeCap.Round,
            )
            drawLine(
                CommentIconTint,
                Offset(w * 0.50f, h * 0.32f),
                Offset(w * 0.78f, h * 0.58f),
                stroke.width,
                StrokeCap.Round,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CommentRow(
    comment: SongComment,
    songId: Long,
    cookie: String,
    selfUid: Long,
    commentsVm: CommentsViewModel,
    floorRefreshTick: Int,
    pendingTopReply: SongComment?,
    replyActive: Boolean,
    textExpanded: Boolean,
    onTextExpandedChange: (Boolean) -> Unit,
    repliesOpen: Boolean,
    onRepliesOpenChange: (Boolean) -> Unit,
    initialReplies: List<SongComment>,
    initialRepliesHasMore: Boolean,
    onFloorCache: (List<SongComment>, Boolean) -> Unit,
    onPatchComment: (Long, (SongComment) -> SongComment) -> Unit,
    onHint: (String) -> Unit,
    onOpenUser: (Long, String, String?) -> Unit = { _, _, _ -> },
    onConsumePendingTopReply: () -> Unit,
    onReplyClick: () -> Unit,
) {
    var replies by remember(comment.commentId) { mutableStateOf(initialReplies) }
    var repliesLoading by remember(comment.commentId) { mutableStateOf(false) }
    var repliesError by remember(comment.commentId) { mutableStateOf<String?>(null) }
    var repliesHasMore by remember(comment.commentId) { mutableStateOf(initialRepliesHasMore) }
    var likeBusy by remember(comment.commentId) { mutableStateOf(false) }
    var hugBusy by remember(comment.commentId) { mutableStateOf(false) }
    var hugged by remember(comment.commentId) { mutableStateOf(false) }
    var hugListOpen by remember(comment.commentId) { mutableStateOf(false) }
    var hugUsers by remember(comment.commentId) { mutableStateOf<List<CommentHugUser>>(emptyList()) }
    var hugListLoading by remember(comment.commentId) { mutableStateOf(false) }
    var pinchProgress by remember(comment.commentId) { mutableFloatStateOf(0f) }
    var hugAnimPlaying by remember(comment.commentId) { mutableStateOf(false) }
    val hugAnimProgress = remember(comment.commentId) { Animatable(0f) }
    val scope = rememberCoroutineScope()

    val collapseLines = 4
    // 粗估：短文直接展示；略长才给「展开」
    val maybeLong = comment.content.length > 72 || comment.content.count { it == '\n' } >= 2

    /** 网易云：捏合过程预览靠近；触发后播完拥抱+微笑并淡出（不松手分开）。 */
    suspend fun playHugPeopleAnim(fromPinch: Float = 0f) {
        hugAnimPlaying = true
        val start = (fromPinch * 0.42f).coerceIn(0f, 0.42f)
        hugAnimProgress.snapTo(start)
        // 先到环抱+微笑定格，再淡出；进度 0.88→1 只降透明度，姿态仍抱紧
        hugAnimProgress.animateTo(
            targetValue = 0.86f,
            animationSpec = tween(durationMillis = 720, easing = FastOutSlowInEasing),
        )
        delay(420)
        hugAnimProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
        )
        // 切勿再 1→0：那会演成「抱完又松手」
        hugAnimProgress.snapTo(0f)
        hugAnimPlaying = false
    }

    suspend fun loadReplies(reset: Boolean, consumePending: Boolean = false) {
        if (repliesLoading) return
        if (consumePending) onConsumePendingTopReply()
        repliesLoading = true
        repliesError = null
        try {
            val page = commentsVm.floor(
                songId = songId,
                parentCommentId = comment.commentId,
                cookie = cookie,
                limit = 20,
                time = if (reset) null else replies.lastOrNull()?.timeMs,
            )
            replies = if (reset) page.comments else {
                val seen = replies.mapTo(HashSet()) { it.commentId }
                replies + page.comments.filter { seen.add(it.commentId) }
            }
            repliesHasMore = page.hasMore && page.comments.isNotEmpty()
            onFloorCache(replies, repliesHasMore)
        } catch (e: Exception) {
            repliesError = NcmJson.userFacingThrowable(e, "回复加载失败")
        } finally {
            repliesLoading = false
        }
    }

    LaunchedEffect(floorRefreshTick) {
        if (floorRefreshTick <= 0) return@LaunchedEffect
        onRepliesOpenChange(true)
        // 发送后的对齐刷新：保留置顶乐观回复
        loadReplies(reset = true, consumePending = false)
    }

    LaunchedEffect(pendingTopReply?.commentId) {
        if (pendingTopReply != null) {
            onRepliesOpenChange(true)
        }
    }

    LaunchedEffect(repliesOpen, comment.commentId) {
        if (!repliesOpen) return@LaunchedEffect
        if (replies.isNotEmpty() || repliesLoading) return@LaunchedEffect
        loadReplies(reset = true, consumePending = false)
    }

    val displayReplies = remember(replies, pendingTopReply) {
        val pending = pendingTopReply
        if (pending == null) {
            replies
        } else {
            listOf(pending) + replies.filter { it.commentId != pending.commentId }
        }
    }

    suspend fun toggleLike() {
        if (likeBusy) return
        if (cookie.isBlank() || selfUid <= 0L) {
            onHint("请先登录后再点赞")
            return
        }
        likeBusy = true
        val nextLiked = !comment.liked
        val prevCount = comment.likedCount
        onPatchComment(comment.commentId) {
            it.copy(
                liked = nextLiked,
                likedCount = (prevCount + if (nextLiked) 1 else -1).coerceAtLeast(0),
            )
        }
        try {
            if (!commentsVm.like(
                    songId = songId,
                    commentId = comment.commentId,
                    like = nextLiked,
                    cookie = cookie,
                )
            ) {
                throw IllegalStateException("点赞失败")
            }
        } catch (_: Exception) {
            onPatchComment(comment.commentId) {
                it.copy(liked = !nextLiked, likedCount = prevCount)
            }
            onHint("点赞失败，请稍后重试")
        } finally {
            likeBusy = false
        }
    }

    suspend fun doHug(playAnim: Boolean = true) {
        if (hugBusy) return
        if (cookie.isBlank() || selfUid <= 0L) {
            onHint("请先登录后再抱抱")
            return
        }
        if (comment.userId <= 0L) {
            onHint("无法抱抱该评论")
            return
        }
        if (comment.userId == selfUid) {
            onHint("不能抱抱自己")
            return
        }
        hugBusy = true
        val animJob = if (playAnim && !hugAnimPlaying) {
            scope.launch { playHugPeopleAnim(fromPinch = pinchProgress) }
        } else {
            null
        }
        try {
            // 严格对齐文档：uid=评论作者，cid=评论，sid=歌曲
            val ack = commentsVm.hug(
                targetUid = comment.userId,
                commentId = comment.commentId,
                songId = songId,
                cookie = cookie,
            )
            if (!ack.ok) {
                throw IllegalStateException(ack.message.ifBlank { "抱抱失败" })
            }
            hugged = true
            onHint("已抱抱 ${comment.nickname}")
        } catch (_: Exception) {
            onHint("抱抱失败，请稍后重试")
        } finally {
            animJob?.join()
            hugBusy = false
        }
    }

    suspend fun loadHugList() {
        if (hugListLoading) return
        if (cookie.isBlank() || selfUid <= 0L) {
            onHint("请先登录后查看抱抱")
            return
        }
        if (comment.userId <= 0L) return
        hugListLoading = true
        try {
            hugUsers = commentsVm.hugUsers(
                targetUid = comment.userId,
                commentId = comment.commentId,
                songId = songId,
                cookie = cookie,
            )
            hugListOpen = true
        } catch (_: Exception) {
            onHint("抱抱列表加载失败")
        } finally {
            hugListLoading = false
        }
    }

    val onPinchHug = rememberUpdatedState(newValue = {
        scope.launch { doHug(playAnim = true) }
    })
    val onPinchProgress = rememberUpdatedState(newValue = { p: Float ->
        pinchProgress = p
    })

    // 捏合过程小人靠近；触发后播完拥抱+微笑（对齐网易云彩蛋）
    val overlayProgress = when {
        hugAnimPlaying -> hugAnimProgress.value
        pinchProgress > 0.02f -> pinchProgress * 0.42f
        else -> 0f
    }

    // 外层 Column：抱抱叠层只锚评论本体；回复/抱抱列表在锚点外，避免展开后小人落到回复底部
    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .commentHugPinch(
                        enabled = !hugBusy,
                        onPinchProgress = { onPinchProgress.value(it) },
                        onPinchHug = { onPinchHug.value() },
                    ),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .then(
                                if (hugged) {
                                    Modifier.border(1.5.dp, Color(0xFFFF5A5F), CircleShape)
                                } else {
                                    Modifier
                                },
                            )
                            .background(CommentAvatarBg)
                            .then(
                                if (comment.userId > 0L) {
                                    Modifier.clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        onClick = {
                                            onOpenUser(
                                                comment.userId,
                                                comment.nickname,
                                                comment.avatarUrl,
                                            )
                                        },
                                    )
                                } else {
                                    Modifier
                                },
                            ),
                    ) {
                        UrlImage(
                            url = comment.avatarUrl,
                            contentDescription = comment.nickname,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    }
                    if (hugged) {
                        Text(
                            text = "收到了抱抱",
                            style = TextStyle(
                                color = Color(0xFFFF5A5F),
                                fontFamily = FontFamily.SansSerif,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Medium,
                            ),
                            maxLines = 1,
                            modifier = Modifier.padding(top = 3.dp),
                        )
                    }
                }
                Column(Modifier.weight(1f)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = comment.nickname,
                            style = TextStyle(
                                color = CommentLabel.copy(alpha = 0.92f),
                                fontFamily = FontFamily.SansSerif,
                                fontWeight = FontWeight.Medium,
                                fontSize = 13.sp,
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .then(
                                    if (comment.userId > 0L) {
                                        Modifier.clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null,
                                            onClick = {
                                                onOpenUser(
                                                    comment.userId,
                                                    comment.nickname,
                                                    comment.avatarUrl,
                                                )
                                            },
                                        )
                                    } else {
                                        Modifier
                                    },
                                ),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = comment.timeLabel,
                            style = TextStyle(
                                color = CommentHint.copy(alpha = 0.75f),
                                fontFamily = FontFamily.SansSerif,
                                fontSize = 11.sp,
                            ),
                            maxLines = 1,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    if (!comment.repliedContent.isNullOrBlank()) {
                        Text(
                            text = "回复 ${comment.repliedNickname.orEmpty()}：${comment.repliedContent}",
                            style = TextStyle(
                                color = CommentHint.copy(alpha = 0.8f),
                                fontFamily = FontFamily.SansSerif,
                                fontSize = 12.sp,
                                lineHeight = 17.sp,
                            ),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(CommentQuoteBg.copy(alpha = 0.85f))
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                    Text(
                        text = comment.content,
                        style = TextStyle(
                            color = CommentLabel.copy(alpha = 0.88f),
                            fontFamily = FontFamily.SansSerif,
                            fontSize = 14.sp,
                            lineHeight = 21.sp,
                            letterSpacing = 0.15.sp,
                        ),
                        maxLines = if (textExpanded || !maybeLong) Int.MAX_VALUE else collapseLines,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (maybeLong) {
                                    Modifier.clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        onClick = { onTextExpandedChange(!textExpanded) },
                                    )
                                } else {
                                    Modifier
                                },
                            ),
                    )
                    if (maybeLong) {
                        Text(
                            text = if (textExpanded) "收起" else "展开",
                            style = TextStyle(
                                color = CommentAccent.copy(alpha = 0.9f),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                            ),
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = { onTextExpandedChange(!textExpanded) },
                                ),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.clickable(
                                enabled = !likeBusy,
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { scope.launch { toggleLike() } },
                            ),
                        ) {
                            CommentLikeIcon(
                                size = 14.dp,
                                tint = if (comment.liked) {
                                    Color(0xFFFF6B81)
                                } else {
                                    CommentHint.copy(alpha = 0.85f)
                                },
                                filled = comment.liked,
                            )
                            Text(
                                text = if (comment.likedCount > 0) {
                                    formatCount(comment.likedCount)
                                } else {
                                    "赞"
                                },
                                style = TextStyle(
                                    color = if (comment.liked) {
                                        Color(0xFFFF6B81).copy(alpha = 0.92f)
                                    } else {
                                        CommentHint.copy(alpha = 0.8f)
                                    },
                                    fontSize = 12.sp,
                                ),
                            )
                        }
                        // 抱抱：双指捏合评论触发（对齐网易云）；图标点按也可，长按看列表
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier
                                .combinedClickable(
                                    enabled = !hugBusy && !hugListLoading,
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = { scope.launch { doHug(playAnim = true) } },
                                    onLongClick = { scope.launch { loadHugList() } },
                                ),
                        ) {
                            CommentHugIcon(
                                size = 15.dp,
                                tint = if (hugged || overlayProgress > 0.12f) {
                                    Color(0xFFFF6B81).copy(alpha = 0.95f)
                                } else {
                                    CommentHint.copy(alpha = 0.88f)
                                },
                            )
                            Text(
                                text = if (hugged) "已抱抱" else "抱抱",
                                style = TextStyle(
                                    color = if (hugged || overlayProgress > 0.12f) {
                                        Color(0xFFFF6B81).copy(alpha = 0.92f)
                                    } else {
                                        CommentHint.copy(alpha = 0.8f)
                                    },
                                    fontSize = 12.sp,
                                ),
                            )
                        }
                        Text(
                            text = if (replyActive) "回复中" else "回复",
                            style = TextStyle(
                                color = if (replyActive) {
                                    CommentAccent.copy(alpha = 0.95f)
                                } else {
                                    CommentHint.copy(alpha = 0.85f)
                                },
                                fontSize = 12.sp,
                                fontWeight = if (replyActive) FontWeight.Medium else FontWeight.Normal,
                            ),
                            modifier = Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onReplyClick,
                            ),
                        )
                        if (comment.replyCount > 0) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = {
                                        val next = !repliesOpen
                                        onRepliesOpenChange(next)
                                        if (next) {
                                            // 用户再次展开：取消置顶，拉真实顺序
                                            scope.launch {
                                                loadReplies(reset = true, consumePending = true)
                                            }
                                        }
                                    },
                                ),
                            ) {
                                Text(
                                    text = if (repliesOpen) {
                                        "收起回复"
                                    } else {
                                        "回复 ${formatCount(comment.replyCount)}"
                                    },
                                    style = TextStyle(
                                        color = CommentAccent.copy(alpha = 0.88f),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                    ),
                                )
                                CommentChevronIcon(
                                    expanded = repliesOpen,
                                    size = 11.dp,
                                    tint = CommentAccent.copy(alpha = 0.88f),
                                )
                            }
                        }
                    }
                }
            }

            // matchParentSize：叠在评论本体上，不参与测量；不随回复/抱抱列表增高
            if (overlayProgress > 0.01f) {
                Box(
                    Modifier.matchParentSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CommentHugPeopleOverlay(
                        progress = overlayProgress,
                        modifier = Modifier.size(132.dp),
                    )
                }
            }
        }

        if (hugListOpen || repliesOpen) {
            // 与正文列对齐：头像 40 + spacedBy 12
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Spacer(Modifier.width(40.dp))
                Column(Modifier.weight(1f)) {
                    if (hugListOpen) {
                        Spacer(Modifier.height(10.dp))
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(CommentQuoteBg.copy(alpha = 0.8f))
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "抱抱了这些人",
                                    color = CommentLabel.copy(alpha = 0.9f),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    text = "收起",
                                    color = CommentAccent,
                                    fontSize = 12.sp,
                                    modifier = Modifier.clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        onClick = { hugListOpen = false },
                                    ),
                                )
                            }
                            if (hugUsers.isEmpty()) {
                                Text(
                                    text = "暂时还没有人抱抱",
                                    color = CommentHint.copy(alpha = 0.75f),
                                    fontSize = 12.sp,
                                )
                            } else {
                                hugUsers.take(30).forEach { user ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Box(
                                            Modifier
                                                .size(24.dp)
                                                .clip(CircleShape)
                                                .background(CommentAvatarBg),
                                        ) {
                                            UrlImage(
                                                url = user.avatarUrl,
                                                contentDescription = user.nickname,
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop,
                                            )
                                        }
                                        Text(
                                            text = user.nickname,
                                            color = CommentLabel.copy(alpha = 0.88f),
                                            fontSize = 12.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (repliesOpen) {
                        Spacer(Modifier.height(10.dp))
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(CommentQuoteBg.copy(alpha = 0.72f))
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            when {
                                repliesLoading && displayReplies.isEmpty() -> {
                                    Box(
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        CircularProgressIndicator(
                                            color = CommentAccent.copy(alpha = 0.7f),
                                            strokeWidth = 2.dp,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                }
                                repliesError != null && displayReplies.isEmpty() -> {
                                    Text(
                                        text = repliesError ?: "加载失败",
                                        color = CommentHint,
                                        fontSize = 12.sp,
                                        modifier = Modifier.clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null,
                                            onClick = {
                                                scope.launch {
                                                    loadReplies(reset = true, consumePending = true)
                                                }
                                            },
                                        ),
                                    )
                                }
                                displayReplies.isEmpty() -> {
                                    Text(
                                        text = "暂无回复",
                                        color = CommentHint.copy(alpha = 0.7f),
                                        fontSize = 12.sp,
                                    )
                                }
                                else -> {
                                    displayReplies.forEach { reply ->
                                        CommentReplyRow(
                                            comment = reply,
                                            justSent = pendingTopReply?.commentId == reply.commentId,
                                        )
                                    }
                                    when {
                                        repliesLoading -> Box(
                                            Modifier.fillMaxWidth(),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            CircularProgressIndicator(
                                                color = CommentAccent.copy(alpha = 0.65f),
                                                strokeWidth = 2.dp,
                                                modifier = Modifier.size(16.dp),
                                            )
                                        }
                                        repliesHasMore -> Text(
                                            text = "加载更多回复",
                                            color = CommentAccent.copy(alpha = 0.9f),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium,
                                            modifier = Modifier.clickable(
                                                interactionSource = remember {
                                                    MutableInteractionSource()
                                                },
                                                indication = null,
                                                onClick = {
                                                    scope.launch { loadReplies(reset = false) }
                                                },
                                            ),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CommentReplyRow(
    comment: SongComment,
    justSent: Boolean = false,
) {
    var textExpanded by remember(comment.commentId) { mutableStateOf(false) }
    val maybeLong = comment.content.length > 56
    Row(
        Modifier
            .fillMaxWidth()
            .then(
                if (justSent) {
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(CommentAccent.copy(alpha = 0.10f))
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                } else {
                    Modifier
                },
            ),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(CommentAvatarBg),
        ) {
            UrlImage(
                url = comment.avatarUrl,
                contentDescription = comment.nickname,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = comment.nickname,
                    color = CommentLabel.copy(alpha = 0.88f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (justSent) "刚刚发送" else comment.timeLabel,
                    color = if (justSent) {
                        CommentAccent.copy(alpha = 0.9f)
                    } else {
                        CommentHint.copy(alpha = 0.7f)
                    },
                    fontSize = 10.sp,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = comment.content,
                color = CommentLabel.copy(alpha = 0.82f),
                fontSize = 13.sp,
                lineHeight = 18.sp,
                maxLines = if (textExpanded || !maybeLong) Int.MAX_VALUE else 3,
                overflow = TextOverflow.Ellipsis,
                modifier = if (maybeLong) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { textExpanded = !textExpanded },
                    )
                } else {
                    Modifier
                },
            )
            if (maybeLong) {
                Text(
                    text = if (textExpanded) "收起" else "展开",
                    color = CommentAccent.copy(alpha = 0.85f),
                    fontSize = 11.sp,
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { textExpanded = !textExpanded },
                        ),
                )
            }
            Spacer(Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                CommentLikeIcon(
                    size = 11.dp,
                    tint = CommentHint.copy(alpha = 0.75f),
                    filled = false,
                )
                Text(
                    text = if (comment.likedCount > 0) formatCount(comment.likedCount) else "赞",
                    color = CommentHint.copy(alpha = 0.75f),
                    fontSize = 11.sp,
                )
            }
        }
    }
}

/**
 * 网易云「抱一抱」彩蛋视觉：白 / 红两个小人从两侧靠近 → 拥抱 → 露出笑容。
 * progress 0→1：出现与靠近(0–0.4) → 环抱(0.4–0.65) → 微笑定格(0.65–0.88) → 淡出(0.88–1)。
 */
@Composable
private fun CommentHugPeopleOverlay(
    progress: Float,
    modifier: Modifier = Modifier,
) {
    val p = progress.coerceIn(0f, 1f)
    val appear = (p / 0.10f).coerceIn(0f, 1f)
    val approach = ((p - 0.04f) / 0.36f).coerceIn(0f, 1f)
    val hug = ((p - 0.38f) / 0.28f).coerceIn(0f, 1f)
    val smile = ((p - 0.58f) / 0.22f).coerceIn(0f, 1f)
    val fade = if (p > 0.88f) ((1f - p) / 0.12f).coerceIn(0f, 1f) else 1f
    val alpha = appear * fade

    Canvas(
        modifier.graphicsLayer {
            this.alpha = alpha
            val pop = 0.92f + hug * 0.08f
            scaleX = pop
            scaleY = pop
        },
    ) {
        val cx = size.width * 0.5f
        val cy = size.height * 0.52f
        val unit = min(size.width, size.height)

        // 暖色柔光
        if (hug > 0.05f) {
            drawCircle(
                color = Color(0x55FF8A90),
                radius = unit * (0.28f + smile * 0.08f),
                center = Offset(cx, cy - unit * 0.04f),
            )
        }

        val spread = unit * (0.42f - approach * 0.28f - hug * 0.06f)
        val leftX = cx - spread
        val rightX = cx + spread

        drawHugPerson(
            cx = leftX,
            cy = cy,
            unit = unit,
            fill = Color(0xFFFFF4F0),
            stroke = Color(0xFFE8D4CE),
            faceRight = true,
            armWrap = hug,
            smile = smile,
        )
        drawHugPerson(
            cx = rightX,
            cy = cy,
            unit = unit,
            fill = Color(0xFFFF5A5F),
            stroke = Color(0xFFE04850),
            faceRight = false,
            armWrap = hug,
            smile = smile,
        )

        // 拥抱中心小心心
        if (smile > 0.2f) {
            val hr = unit * 0.045f * smile
            val hx = cx
            val hy = cy - unit * 0.42f
            val heart = Path().apply {
                moveTo(hx, hy + hr * 0.35f)
                cubicTo(hx - hr * 1.1f, hy - hr * 0.2f, hx - hr * 0.95f, hy - hr * 1.1f, hx, hy - hr * 0.45f)
                cubicTo(hx + hr * 0.95f, hy - hr * 1.1f, hx + hr * 1.1f, hy - hr * 0.2f, hx, hy + hr * 0.35f)
                close()
            }
            drawPath(heart, Color(0xFFFF6B81).copy(alpha = 0.55f + smile * 0.45f))
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawHugPerson(
    cx: Float,
    cy: Float,
    unit: Float,
    fill: Color,
    stroke: Color,
    faceRight: Boolean,
    armWrap: Float,
    smile: Float,
) {
    val headR = unit * 0.145f
    val headCy = cy - unit * 0.22f
    val bodyW = unit * 0.20f
    val bodyH = unit * 0.30f
    val bodyTop = cy - unit * 0.08f
    val dir = if (faceRight) 1f else -1f

    // 身体
    drawRoundRect(
        color = fill,
        topLeft = Offset(cx - bodyW / 2f, bodyTop),
        size = Size(bodyW, bodyH),
        cornerRadius = CornerRadius(bodyW * 0.45f, bodyW * 0.45f),
    )
    drawRoundRect(
        color = stroke,
        topLeft = Offset(cx - bodyW / 2f, bodyTop),
        size = Size(bodyW, bodyH),
        cornerRadius = CornerRadius(bodyW * 0.45f, bodyW * 0.45f),
        style = Stroke(width = unit * 0.012f),
    )

    // 头
    drawCircle(fill, headR, Offset(cx, headCy))
    drawCircle(stroke, headR, Offset(cx, headCy), style = Stroke(width = unit * 0.012f))

    // 脸
    val face = Color(0xFF3A2A28)
    val eyeY = headCy - headR * 0.08f
    val eyeDx = headR * 0.36f
    if (smile < 0.35f) {
        drawCircle(face, headR * 0.09f, Offset(cx - eyeDx, eyeY))
        drawCircle(face, headR * 0.09f, Offset(cx + eyeDx, eyeY))
        drawLine(
            face,
            Offset(cx - headR * 0.18f, headCy + headR * 0.38f),
            Offset(cx + headR * 0.18f, headCy + headR * 0.38f),
            strokeWidth = unit * 0.012f,
            cap = StrokeCap.Round,
        )
    } else {
        // 弯弯笑眼
        val eyeStroke = Stroke(width = unit * 0.018f, cap = StrokeCap.Round)
        val eyeArc = headR * 0.22f
        drawArc(
            color = face,
            startAngle = 200f,
            sweepAngle = 140f,
            useCenter = false,
            topLeft = Offset(cx - eyeDx - eyeArc / 2f, eyeY - eyeArc * 0.15f),
            size = Size(eyeArc, eyeArc * 0.7f),
            style = eyeStroke,
        )
        drawArc(
            color = face,
            startAngle = 200f,
            sweepAngle = 140f,
            useCenter = false,
            topLeft = Offset(cx + eyeDx - eyeArc / 2f, eyeY - eyeArc * 0.15f),
            size = Size(eyeArc, eyeArc * 0.7f),
            style = eyeStroke,
        )
        // 微笑嘴
        drawArc(
            color = face,
            startAngle = 20f,
            sweepAngle = 140f,
            useCenter = false,
            topLeft = Offset(cx - headR * 0.28f, headCy + headR * 0.12f),
            size = Size(headR * 0.56f, headR * 0.42f),
            style = Stroke(width = unit * 0.016f, cap = StrokeCap.Round),
        )
        // 腮红
        drawCircle(
            Color(0x55FF8A90),
            headR * 0.16f,
            Offset(cx - headR * 0.55f, headCy + headR * 0.22f),
        )
        drawCircle(
            Color(0x55FF8A90),
            headR * 0.16f,
            Offset(cx + headR * 0.55f, headCy + headR * 0.22f),
        )
    }

    // 手臂：张开 → 环抱对方
    val armStroke = Stroke(
        width = unit * 0.055f,
        cap = StrokeCap.Round,
        join = StrokeJoin.Round,
    )
    val shoulder = Offset(cx + dir * bodyW * 0.35f, bodyTop + bodyH * 0.22f)
    val openHand = Offset(cx + dir * unit * 0.38f, bodyTop + bodyH * 0.08f)
    val hugHand = Offset(cx + dir * unit * 0.02f, bodyTop + bodyH * 0.42f)
    val hand = Offset(
        openHand.x + (hugHand.x - openHand.x) * armWrap,
        openHand.y + (hugHand.y - openHand.y) * armWrap,
    )
    val ctrl = Offset(
        cx + dir * unit * (0.28f - armWrap * 0.18f),
        bodyTop - unit * 0.02f + armWrap * unit * 0.12f,
    )
    val arm = Path().apply {
        moveTo(shoulder.x, shoulder.y)
        quadraticTo(ctrl.x, ctrl.y, hand.x, hand.y)
    }
    drawPath(arm, fill, style = armStroke)

    // 腿
    val legY = bodyTop + bodyH - unit * 0.01f
    drawLine(
        fill,
        Offset(cx - bodyW * 0.22f, legY),
        Offset(cx - bodyW * 0.28f, legY + unit * 0.12f),
        strokeWidth = unit * 0.05f,
        cap = StrokeCap.Round,
    )
    drawLine(
        fill,
        Offset(cx + bodyW * 0.22f, legY),
        Offset(cx + bodyW * 0.28f, legY + unit * 0.12f),
        strokeWidth = unit * 0.05f,
        cap = StrokeCap.Round,
    )
}

@Composable
private fun CommentLikeIcon(
    size: Dp,
    tint: Color,
    filled: Boolean = false,
) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val path = Path().apply {
            moveTo(w * 0.50f, h * 0.88f)
            cubicTo(
                w * 0.18f, h * 0.68f,
                w * 0.05f, h * 0.42f,
                w * 0.28f, h * 0.24f,
            )
            cubicTo(
                w * 0.40f, h * 0.14f,
                w * 0.50f, h * 0.22f,
                w * 0.50f, h * 0.34f,
            )
            cubicTo(
                w * 0.50f, h * 0.22f,
                w * 0.60f, h * 0.14f,
                w * 0.72f, h * 0.24f,
            )
            cubicTo(
                w * 0.95f, h * 0.42f,
                w * 0.82f, h * 0.68f,
                w * 0.50f, h * 0.88f,
            )
            close()
        }
        if (filled) {
            drawPath(path, tint)
        } else {
            drawPath(
                path,
                tint,
                style = Stroke(width = min(w, h) * 0.12f, join = StrokeJoin.Round),
            )
        }
    }
}

/**
 * 网易云抱抱：左右两只手掌水平对捏（非上下合十）。
 */
@Composable
private fun CommentHugIcon(
    size: Dp,
    tint: Color,
) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = Stroke(
            width = min(w, h) * 0.11f,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        )
        // 左手：从左向右捏
        val left = Path().apply {
            moveTo(w * 0.10f, h * 0.38f)
            quadraticTo(w * 0.22f, h * 0.22f, w * 0.36f, h * 0.30f)
            quadraticTo(w * 0.42f, h * 0.42f, w * 0.38f, h * 0.56f)
            quadraticTo(w * 0.28f, h * 0.70f, w * 0.14f, h * 0.62f)
            quadraticTo(w * 0.08f, h * 0.52f, w * 0.10f, h * 0.38f)
            close()
        }
        // 右手：从右向左捏
        val right = Path().apply {
            moveTo(w * 0.90f, h * 0.38f)
            quadraticTo(w * 0.78f, h * 0.22f, w * 0.64f, h * 0.30f)
            quadraticTo(w * 0.58f, h * 0.42f, w * 0.62f, h * 0.56f)
            quadraticTo(w * 0.72f, h * 0.70f, w * 0.86f, h * 0.62f)
            quadraticTo(w * 0.92f, h * 0.52f, w * 0.90f, h * 0.38f)
            close()
        }
        drawPath(left, tint, style = stroke)
        drawPath(right, tint, style = stroke)
        // 中间捏合触点
        drawCircle(tint, min(w, h) * 0.055f, Offset(w * 0.50f, h * 0.46f))
    }
}

@Composable
private fun CommentChevronIcon(
    expanded: Boolean,
    size: Dp,
    tint: Color,
) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(220),
        label = "replyChevron",
    )
    Canvas(
        Modifier
            .size(size)
            .graphicsLayer { rotationZ = rotation },
    ) {
        val w = this.size.width
        val h = this.size.height
        val stroke = Stroke(
            width = min(w, h) * 0.16f,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        )
        // 默认下箭头，展开后旋转为上
        drawLine(tint, Offset(w * 0.18f, h * 0.38f), Offset(w * 0.50f, h * 0.68f), stroke.width, StrokeCap.Round)
        drawLine(tint, Offset(w * 0.50f, h * 0.68f), Offset(w * 0.82f, h * 0.38f), stroke.width, StrokeCap.Round)
    }
}

/**
 * 网易云抱抱：在评论上双指捏合（距离明显缩小）触发。
 * 两指落在同一行后开始跟踪；明显内收时消费位移，避免 LazyColumn 抢走手势。
 */
private fun Modifier.commentHugPinch(
    enabled: Boolean,
    onPinchProgress: (Float) -> Unit,
    onPinchHug: () -> Unit,
): Modifier = pointerInput(enabled) {
    if (!enabled) return@pointerInput
    val minStartDist = 36.dp.toPx()
    val fireRatio = 0.78f
    val consumeSlop = 8.dp.toPx()

    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        var startDist = -1f
        var fired = false
        var consuming = false

        try {
            while (true) {
                val event = awaitPointerEvent(
                    if (consuming) PointerEventPass.Initial else PointerEventPass.Main,
                )
                val pressed = event.changes.filter { it.pressed }
                if (pressed.size < 2) {
                    if (startDist > 0f) onPinchProgress(0f)
                    startDist = -1f
                    consuming = false
                    if (pressed.isEmpty()) break
                    continue
                }

                val a = pressed[0].position
                val b = pressed[1].position
                val dist = hypot(a.x - b.x, a.y - b.y)

                if (startDist < 0f) {
                    if (dist >= minStartDist) startDist = dist
                    continue
                }

                val ratio = (dist / startDist).coerceIn(0f, 1.4f)
                if (!fired) {
                    onPinchProgress(((1f - ratio) / (1f - fireRatio)).coerceIn(0f, 1f))
                }

                if (!consuming && dist < startDist - consumeSlop) {
                    consuming = true
                }
                if (consuming) {
                    event.changes.fastForEach { change ->
                        if (change.positionChanged()) change.consume()
                    }
                }

                if (!fired && ratio <= fireRatio) {
                    fired = true
                    // 交给 playHugPeopleAnim 接管；此处清 0 会造成「刚抱上就松」闪断
                    onPinchHug()
                }
            }
        } finally {
            onPinchProgress(0f)
        }
    }
}

private fun formatCount(n: Int): String = when {
    n >= 10_000 -> String.format("%.1f 万", n / 10_000f)
    else -> n.toString()
}

/** 宿主侧：打开时 2/3、点箭头到全屏的高度动画辅助（与设置同套曲线）。 */
suspend fun Animatable<Float, *>.animateCommentSheetFrac(
    target: Float,
) {
    animateTo(
        targetValue = target.coerceIn(2f / 3f, 1f),
        animationSpec = spring(
            dampingRatio = 0.86f,
            stiffness = 340f,
        ),
    )
}

val CommentSheetOpenSpec = tween<Float>(
    durationMillis = 420,
    easing = CommentOpenEasing,
)
val CommentSheetCloseSpec = tween<Float>(
    durationMillis = 360,
    easing = CommentCloseEasing,
)
