package com.kite.zmusic.ui.player

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kite.zmusic.ZMusicApplication
import com.kite.zmusic.i18n.t
import com.kite.zmusic.listen.ListenChatMsg
import com.kite.zmusic.listen.ListenChatPackKind
import com.kite.zmusic.listen.ListenChatReplyQuote
import com.kite.zmusic.listen.ListenChatTranslateEntry
import com.kite.zmusic.listen.encodeListenChatPack
import com.kite.zmusic.listen.encodeListenChatText
import com.kite.zmusic.listen.listenChatClockLabel
import com.kite.zmusic.listen.listenChatPackCaption
import com.kite.zmusic.listen.listenChatQuoteFromMsg
import com.kite.zmusic.listen.listenChatReadByPeers
import com.kite.zmusic.listen.listenChatTranslateLookup
import com.kite.zmusic.listen.listenChatUidEquals
import com.kite.zmusic.listen.listenChatVisibleBody
import com.kite.zmusic.listen.parseListenChatPack
import com.kite.zmusic.listen.parseListenChatText
import com.kite.zmusic.ui.common.UrlImage
import com.kite.zmusic.ui.easter.MjEasterEgg
import com.kite.zmusic.ui.icons.ZIcons
import com.kite.zmusic.ui.main.MainPalette
import com.kite.zmusic.ui.main.pageSheetHazeStyle
import com.kite.zmusic.ui.notice.showIslandNotice
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import kotlin.math.abs
import kotlin.math.max
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val ChatBubbleShape = RoundedCornerShape(14.dp)
private val ChatComposerShape = RoundedCornerShape(18.dp)
private val ChatActionPillShape = RoundedCornerShape(14.dp)
private val ChatReplyBarShape = RoundedCornerShape(12.dp)
private val ChatActionInSpec = tween<Float>(durationMillis = 200, easing = FastOutSlowInEasing)
private val ChatActionOutSpec = tween<Float>(durationMillis = 140, easing = FastOutSlowInEasing)

@Composable
internal fun PortraitListenChatSheet(
    openProgress: Float,
    sheetFrac: Float,
    onExpandFullscreen: () -> Unit,
    onCollapseToTwoThirds: () -> Unit,
    hazeState: HazeState? = null,
    onOpenUser: (Long, String, String?) -> Unit = { _, _, _ -> },
    modifier: Modifier = Modifier,
) {
    val t = openProgress.coerceIn(0f, 1f)
    val expandT = ((sheetFrac - 2f / 3f) / (1f / 3f)).coerceIn(0f, 1f)
    val fullscreen = expandT >= 0.97f
    val corner = lerp(22.dp, 0.dp, expandT)
    val context = LocalContext.current
    val app = context.applicationContext as ZMusicApplication
    val listen = app.listenTogether
    val ui by listen.ui.collectAsStateWithLifecycle()
    val translations by listen.chatTranslations.collectAsStateWithLifecycle()
    val chat = ui.room?.chat.orEmpty()
    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val density = LocalDensity.current
    var draft by remember { mutableStateOf(TextFieldValue("")) }
    var composerFocused by remember { mutableStateOf(false) }
    var emojiOpen by remember { mutableStateOf(false) }
    var keyboardHandoff by remember { mutableStateOf(false) }
    var recentEmoji by remember { mutableStateOf(emptyList<String>()) }
    var actionMsgId by remember { mutableStateOf<Long?>(null) }
    var replyTo by remember { mutableStateOf<ListenChatReplyQuote?>(null) }
    var spunPacks by remember { mutableStateOf(emptySet<String>()) }
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val imeBottom = WindowInsets.ime.getBottom(density)
    val navBottom = WindowInsets.navigationBars.getBottom(density)
    var emojiPanelH by remember { mutableStateOf(248.dp) }
    var panelPx by remember { mutableFloatStateOf(0f) }
    var lastImeSlot by remember { mutableFloatStateOf(0f) }
    val imeSlot = (imeBottom - navBottom).coerceAtLeast(0).toFloat()
    val imeSlotRef = rememberUpdatedState(imeSlot)
    val emojiTargetRef = rememberUpdatedState(with(density) { emojiPanelH.toPx() })
    val handoffRef = rememberUpdatedState(keyboardHandoff)
    SideEffect {
        if (!emojiOpen && !keyboardHandoff && imeSlot > 1f) lastImeSlot = imeSlot
        val slotDp = with(density) { imeSlot.toDp() }
        if (!emojiOpen && !keyboardHandoff && slotDp > emojiPanelH) emojiPanelH = slotDp
    }
    LaunchedEffect(emojiOpen) {
        val anim = Animatable(panelPx)
        if (emojiOpen) {
            val from = max(panelPx, imeSlotRef.value)
            anim.snapTo(from)
            panelPx = from
            val target = max(emojiTargetRef.value, from)
            anim.animateTo(target, tween(260, easing = FastOutSlowInEasing)) {
                panelPx = value
            }
            panelPx = target
        } else if (handoffRef.value) {
            val dest = lastImeSlot
            if (dest > 1f && abs(panelPx - dest) > 1f) {
                anim.animateTo(dest, tween(260, easing = FastOutSlowInEasing)) {
                    panelPx = value
                }
            }
            val started = System.currentTimeMillis()
            while (handoffRef.value && System.currentTimeMillis() - started < 520L) {
                val ime = imeSlotRef.value
                val caughtUp = if (dest > 1f) {
                    ime >= dest - 6f
                } else {
                    ime > 1f && ime >= panelPx - 6f
                }
                if (caughtUp) break
                delay(16)
            }
            anim.snapTo(0f)
            panelPx = 0f
            keyboardHandoff = false
        } else if (panelPx > 1f) {
            anim.animateTo(0f, tween(220, easing = FastOutSlowInEasing)) {
                panelPx = value
            }
            panelPx = 0f
        }
    }
    val slotPx = max(imeSlot, panelPx)
    BackHandler(enabled = emojiOpen) {
        keyboardHandoff = false
        emojiOpen = false
    }
    val newestId = chat.lastOrNull()?.id
    val expandFullscreenUpdated by rememberUpdatedState(onExpandFullscreen)
    val fullscreenUpdated by rememberUpdatedState(fullscreen)
    LaunchedEffect(ui.inRoom, ui.room?.id) {
        spunPacks = emptySet()
        if (ui.inRoom) listen.markChatRead()
        if (!ui.inRoom) replyTo = null
    }
    // 输入/表情只升全屏，避免 2/3 被 IME 压扁；点面板收键盘不打回半屏（箭头/返回才收）
    LaunchedEffect(composerFocused, emojiOpen) {
        if ((composerFocused || emojiOpen) && !fullscreenUpdated) {
            expandFullscreenUpdated()
            delay(320)
        }
    }
    LaunchedEffect(newestId, chat.size, imeBottom) {
        if (chat.isEmpty()) return@LaunchedEffect
        kotlinx.coroutines.yield()
        listState.scrollToItem(0)
    }

    fun send() {
        val text = draft.text.trim()
        if (text.isEmpty()) return
        MjEasterEgg.consider(text)
        val payload = encodeListenChatText(replyTo, text)
        draft = TextFieldValue("")
        replyTo = null
        listen.sendChat(payload)
    }

    fun sendPack(kind: ListenChatPackKind) {
        val payload = encodeListenChatText(replyTo, encodeListenChatPack(kind))
        replyTo = null
        listen.sendChat(payload)
    }

    fun insertEmoji(emoji: String) {
        val text = draft.text
        val start = draft.selection.start.coerceIn(0, text.length)
        val end = draft.selection.end.coerceIn(0, text.length)
        val a = minOf(start, end)
        val b = maxOf(start, end)
        val next = text.replaceRange(a, b, emoji)
        draft = TextFieldValue(next, TextRange(a + emoji.length))
        recentEmoji = (listOf(emoji) + recentEmoji.filter { it != emoji }).take(16)
    }

    fun beginReply(msg: ListenChatMsg) {
        actionMsgId = null
        replyTo = listenChatQuoteFromMsg(msg)
        if (emojiOpen) keyboardHandoff = true
        emojiOpen = false
        if (!fullscreen) onExpandFullscreen()
        focusRequester.requestFocus()
        keyboard?.show()
    }

    fun copyMsg(msg: ListenChatMsg) {
        actionMsgId = null
        val body = listenChatVisibleBody(msg.text)
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        cm?.setPrimaryClip(ClipData.newPlainText(t("聊天消息"), body))
        context.showIslandNotice(t("已复制"))
    }

    Box(
        modifier
            .fillMaxHeight()
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = corner, topEnd = corner))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {
                    actionMsgId = null
                    keyboardHandoff = false
                    if (emojiOpen) emojiOpen = false
                    keyboard?.hide()
                    focusManager.clearFocus(force = true)
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
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(top = statusTop * expandT)
                .padding(horizontal = 16.dp)
                .padding(top = 14.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = t("聊天室"),
                        style = TextStyle(
                            color = MainPalette.Ink,
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            letterSpacing = (-0.2).sp,
                        ),
                    )
                    Text(
                        text = if (chat.isEmpty()) t("结束一起听后记录会清空") else t("共 %s 条", chat.size),
                        style = TextStyle(
                            color = MainPalette.Secondary,
                            fontFamily = FontFamily.SansSerif,
                            fontSize = 13.sp,
                        ),
                    )
                }
                ChatHeaderArrowButton(
                    expandT = expandT,
                    onClick = {
                        if (expandT >= 0.97f) onCollapseToTwoThirds() else onExpandFullscreen()
                    },
                )
            }
            Spacer(Modifier.height(12.dp))
            if (chat.isEmpty()) {
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = t("还没有人发言"),
                        color = MainPalette.Secondary.copy(alpha = 0.7f),
                        fontSize = 14.sp,
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    reverseLayout = true,
                    contentPadding = PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(chat.asReversed(), key = { it.id }) { msg ->
                        ChatRow(
                            msg = msg,
                            self = msg.id < 0L || listenChatUidEquals(msg.uid, ui.selfUid),
                            read = listenChatReadByPeers(
                                msgId = msg.id,
                                senderUid = msg.uid,
                                members = ui.room?.members.orEmpty(),
                                reads = ui.room?.chatReads.orEmpty(),
                            ),
                            spunPacks = spunPacks,
                            onPackSpun = { key -> spunPacks = spunPacks + key },
                            translation = listenChatTranslateLookup(translations, msg),
                            menuOpen = actionMsgId == msg.id,
                            onOpenUser = onOpenUser,
                            onLongPress = {
                                keyboard?.hide()
                                focusManager.clearFocus(force = true)
                                actionMsgId = msg.id
                            },
                            onDismissMenu = { actionMsgId = null },
                            onCopy = { copyMsg(msg) },
                            onReply = { beginReply(msg) },
                            onTranslate = {
                                actionMsgId = null
                                listen.translateChat(msg)
                            },
                            onHideTranslation = { listen.hideChatTranslation(msg) },
                        )
                    }
                }
            }
            AnimatedVisibility(
                visible = replyTo != null,
                enter = fadeIn(tween(140)) + expandVertically(expandFrom = Alignment.Bottom),
                exit = fadeOut(tween(100)) + shrinkVertically(shrinkTowards = Alignment.Bottom),
                label = "listenChatReplyBar",
            ) {
                val quote = replyTo
                if (quote != null) {
                    ChatComposerReplyBar(
                        quote = quote,
                        onClear = { replyTo = null },
                    )
                }
            }
            ChatComposerBar(
                draft = draft,
                emojiOpen = emojiOpen,
                focusRequester = focusRequester,
                onDraftChange = { draft = it },
                onToggleEmoji = {
                    if (emojiOpen) {
                        keyboardHandoff = true
                        emojiOpen = false
                        focusRequester.requestFocus()
                        keyboard?.show()
                    } else {
                        if (!fullscreen) onExpandFullscreen()
                        keyboardHandoff = false
                        keyboard?.hide()
                        focusManager.clearFocus(force = true)
                        emojiOpen = true
                    }
                },
                onSend = { send() },
                onFocusChange = { focused ->
                    composerFocused = focused
                    if (focused && emojiOpen) {
                        keyboardHandoff = true
                        emojiOpen = false
                    }
                },
            )
            if (slotPx > 1f) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(with(density) { slotPx.toDp() })
                        .clipToBounds(),
                ) {
                    if (emojiOpen || panelPx > 1f) {
                        ChatEmojiPanel(
                            recent = recentEmoji,
                            onPick = { insertEmoji(it) },
                            onSendPack = { sendPack(it) },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
        if (t < 0.02f) {
            Box(Modifier.matchParentSize())
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatRow(
    msg: ListenChatMsg,
    self: Boolean,
    read: Boolean,
    spunPacks: Set<String>,
    onPackSpun: (String) -> Unit,
    translation: ListenChatTranslateEntry?,
    menuOpen: Boolean,
    onOpenUser: (Long, String, String?) -> Unit,
    onLongPress: () -> Unit,
    onDismissMenu: () -> Unit,
    onCopy: () -> Unit,
    onReply: () -> Unit,
    onTranslate: () -> Unit,
    onHideTranslation: () -> Unit,
) {
    val parsed = remember(msg.text) { parseListenChatText(msg.text) }
    val body = parsed.body.ifBlank { msg.text }
    val pack = remember(body) { parseListenChatPack(body) }
    val clock = remember(msg.at) { listenChatClockLabel(msg.at) }
    val name = if (self) t("我") else msg.nickname.ifBlank { msg.uid }
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (self) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top,
    ) {
        if (!self) {
            ChatAvatar(msg, onOpenUser)
            Spacer(Modifier.width(8.dp))
        }
        Column(
            horizontalAlignment = if (self) Alignment.End else Alignment.Start,
            modifier = Modifier.widthIn(max = 280.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (self) Arrangement.End else Arrangement.Start,
            ) {
                if (self && clock.isNotEmpty()) {
                    ChatTimeLabel(clock)
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    text = name,
                    color = MainPalette.Secondary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 180.dp),
                )
                if (!self && clock.isNotEmpty()) {
                    Spacer(Modifier.width(4.dp))
                    ChatTimeLabel(clock)
                }
            }
            Spacer(Modifier.height(4.dp))
            Box {
                Box(
                    Modifier
                        .clip(ChatBubbleShape)
                        .background(if (self) MainPalette.Accent.copy(alpha = 0.18f) else MainPalette.Card)
                        .combinedClickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onDismissMenu() },
                            onLongClick = onLongPress,
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Column {
                        parsed.reply?.let { quote ->
                            ChatBubbleReplyQuote(quote = quote, self = self)
                            Spacer(Modifier.height(6.dp))
                        }
                        if (pack != null) {
                            ChatPackFace(
                                pack = pack,
                                spin = msg.id > 0L &&
                                    listenChatPackFresh(msg.at) &&
                                    !spunPacks.contains(listenChatPackClaim(msg, body)),
                                onSpinStart = { onPackSpun(listenChatPackClaim(msg, body)) },
                            )
                        } else {
                            Text(
                                text = body,
                                color = MainPalette.Ink,
                                fontSize = 14.sp,
                            )
                        }
                        if (read) {
                            Spacer(Modifier.height(2.dp))
                            Icon(
                                imageVector = ZIcons.Check,
                                contentDescription = t("已读"),
                                tint = MainPalette.Accent,
                                modifier = Modifier
                                    .align(Alignment.End)
                                    .size(14.dp),
                            )
                        }
                    }
                }
                ChatMessageActionPopup(
                    visible = menuOpen,
                    self = self,
                    onDismissRequest = onDismissMenu,
                    onCopy = onCopy,
                    onReply = onReply,
                    onTranslate = onTranslate,
                )
            }
            val showTranslation = translation != null &&
                translation.visible &&
                (translation.loading || translation.text.isNotBlank())
            AnimatedVisibility(
                visible = showTranslation,
                enter = fadeIn(tween(160)) + expandVertically(expandFrom = Alignment.Top),
                exit = fadeOut(tween(120)) + shrinkVertically(shrinkTowards = Alignment.Top),
                label = "listenChatTranslation",
            ) {
                ChatTranslationBlock(
                    translation = translation,
                    onHide = onHideTranslation,
                )
            }
        }
        if (self) {
            Spacer(Modifier.width(8.dp))
            ChatAvatar(msg, onOpenUser)
        }
    }
}

@Composable
private fun ChatMessageActionPopup(
    visible: Boolean,
    self: Boolean,
    onDismissRequest: () -> Unit,
    onCopy: () -> Unit,
    onReply: () -> Unit,
    onTranslate: () -> Unit,
) {
    val density = LocalDensity.current
    val reveal = remember { Animatable(0f) }
    var hosted by remember { mutableStateOf(false) }
    LaunchedEffect(visible) {
        if (visible) {
            hosted = true
            reveal.animateTo(1f, ChatActionInSpec)
        } else if (hosted) {
            reveal.animateTo(0f, ChatActionOutSpec)
            hosted = false
        }
    }
    if (!visible && !hosted) return
    val t = reveal.value.coerceIn(0f, 1f)
    val risePx = with(density) { 8.dp.toPx() }
    Popup(
        alignment = if (self) Alignment.TopEnd else Alignment.TopStart,
        offset = IntOffset(0, with(density) { (-52).dp.roundToPx() }),
        onDismissRequest = onDismissRequest,
        properties = PopupProperties(focusable = true),
    ) {
        Box(
            Modifier.graphicsLayer {
                alpha = t
                val s = 0.88f + 0.12f * t
                scaleX = s
                scaleY = s
                translationY = (1f - t) * risePx
                transformOrigin = TransformOrigin(if (self) 1f else 0f, 1f)
            },
        ) {
            ChatMessageActionBar(
                onCopy = onCopy,
                onReply = onReply,
                onTranslate = onTranslate,
            )
        }
    }
}

@Composable
private fun ChatMessageActionBar(
    onCopy: () -> Unit,
    onReply: () -> Unit,
    onTranslate: () -> Unit,
) {
    val bg = if (MainPalette.isDark) Color(0xFF2C2C2E) else Color(0xFFF2F2F7)
    val fg = if (MainPalette.isDark) Color(0xFFF2F2F7) else Color(0xFF2C2C2E)
    val divider = fg.copy(alpha = 0.18f)
    Row(
        Modifier
            .clip(ChatActionPillShape)
            .background(bg)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChatActionItem(
            icon = ZIcons.Copy,
            label = t("复制"),
            fg = fg,
            onClick = onCopy,
        )
        Box(
            Modifier
                .padding(vertical = 8.dp)
                .width(1.dp)
                .height(22.dp)
                .background(divider),
        )
        ChatActionItem(
            icon = ZIcons.Reply,
            label = t("回复"),
            fg = fg,
            onClick = onReply,
        )
        Box(
            Modifier
                .padding(vertical = 8.dp)
                .width(1.dp)
                .height(22.dp)
                .background(divider),
        )
        ChatActionItem(
            icon = ZIcons.Translate,
            label = t("翻译"),
            fg = fg,
            onClick = onTranslate,
        )
    }
}

@Composable
private fun ChatActionItem(
    icon: ImageVector,
    label: String,
    fg: Color,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = fg,
            modifier = Modifier.size(17.dp),
        )
        Text(
            text = label,
            color = fg,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun ChatBubbleReplyQuote(
    quote: ListenChatReplyQuote,
    self: Boolean,
) {
    val bar = if (self) MainPalette.Accent else MainPalette.Ink.copy(alpha = 0.45f)
    val wash = if (self) {
        MainPalette.Accent.copy(alpha = 0.12f)
    } else {
        MainPalette.Ink.copy(alpha = if (MainPalette.isDark) 0.10f else 0.06f)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(wash)
            .padding(start = 0.dp),
    ) {
        Box(
            Modifier
                .width(3.dp)
                .heightIn(min = 34.dp)
                .background(bar),
        )
        Column(
            Modifier
                .weight(1f)
                .padding(horizontal = 8.dp, vertical = 5.dp),
        ) {
            Text(
                text = quote.nickname.ifBlank { quote.uid },
                color = bar,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = quote.snippet.ifBlank { "…" },
                color = MainPalette.Secondary,
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ChatComposerReplyBar(
    quote: ListenChatReplyQuote,
    onClear: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clip(ChatReplyBarShape)
            .background(
                if (MainPalette.isDark) Color(0xFF2C2C2E) else Color(0xFFF2F2F7),
            )
            .padding(start = 0.dp, end = 6.dp, top = 0.dp, bottom = 0.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(3.dp)
                .height(44.dp)
                .background(MainPalette.Accent),
        )
        Column(
            Modifier
                .weight(1f)
                .padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            Text(
                text = t("回复 %s", quote.nickname.ifBlank { quote.uid }),
                color = MainPalette.Accent,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = quote.snippet.ifBlank { "…" },
                color = MainPalette.Secondary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            Modifier
                .size(28.dp)
                .clip(CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClear,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = ZIcons.Close,
                contentDescription = t("取消回复"),
                tint = MainPalette.Secondary,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun ChatTranslationBlock(
    translation: ListenChatTranslateEntry?,
    onHide: () -> Unit,
) {
    if (translation == null) return
    Row(
        Modifier
            .padding(top = 4.dp)
            .widthIn(max = 280.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (translation.loading) {
            ChatTranslatingDots(
                Modifier
                    .weight(1f, fill = false)
                    .padding(horizontal = 4.dp, vertical = 2.dp),
            )
        } else {
            Text(
                text = translation.text,
                color = MainPalette.Secondary,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .padding(horizontal = 4.dp, vertical = 2.dp),
            )
            Box(
                Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onHide,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = ZIcons.Close,
                    contentDescription = t("取消翻译"),
                    tint = MainPalette.Secondary.copy(alpha = 0.75f),
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

@Composable
private fun ChatTranslatingDots(modifier: Modifier = Modifier) {
    val infinite = rememberInfiniteTransition(label = "chatTranslateDots")
    val phase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "chatTranslateDotsPhase",
    )
    val dots = ".".repeat((phase.toInt() % 3) + 1)
    Text(
        text = dots,
        color = MainPalette.Secondary.copy(alpha = 0.85f),
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        modifier = modifier,
    )
}

@Composable
private fun ChatAvatar(
    msg: ListenChatMsg,
    onOpenUser: (Long, String, String?) -> Unit,
) {
    val uid = msg.ncmUserId()
    Box(
        Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(MainPalette.Placeholder)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {
                    val id = uid ?: return@clickable
                    onOpenUser(id, msg.nickname, msg.avatarUrl.ifBlank { null })
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (msg.avatarUrl.isNotBlank()) {
            UrlImage(
                url = msg.avatarUrl,
                contentDescription = msg.nickname,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Text(
                text = msg.nickname.trim().take(1).ifBlank { "?" },
                color = MainPalette.Ink,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

private fun ListenChatMsg.ncmUserId(): Long? =
    uid.trim().toLongOrNull()?.takeIf { it > 0L }

@Composable
private fun ChatComposerBar(
    draft: TextFieldValue,
    emojiOpen: Boolean,
    focusRequester: FocusRequester,
    onDraftChange: (TextFieldValue) -> Unit,
    onToggleEmoji: () -> Unit,
    onSend: () -> Unit,
    onFocusChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .weight(1f)
                .heightIn(min = 40.dp)
                .clip(ChatComposerShape)
                .background(MainPalette.Placeholder)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            val composerStyle = TextStyle(
                color = MainPalette.Ink,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                platformStyle = PlatformTextStyle(includeFontPadding = false),
                lineHeightStyle = LineHeightStyle(
                    alignment = LineHeightStyle.Alignment.Center,
                    trim = LineHeightStyle.Trim.None,
                ),
            )
            BasicTextField(
                value = draft,
                onValueChange = onDraftChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .onFocusChanged { onFocusChange(it.isFocused) },
                textStyle = composerStyle,
                cursorBrush = SolidColor(MainPalette.Accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend() }),
                maxLines = 4,
                decorationBox = { inner ->
                    Box(
                        Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (draft.text.isEmpty()) {
                            Text(
                                text = t("发条消息…"),
                                style = composerStyle.copy(color = MainPalette.Hint),
                            )
                        }
                        inner()
                    }
                },
            )
        }
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(if (emojiOpen) MainPalette.Accent.copy(alpha = 0.16f) else MainPalette.Placeholder)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onToggleEmoji,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = ZIcons.Emoji,
                contentDescription = t("表情"),
                tint = if (emojiOpen) MainPalette.Accent else MainPalette.Secondary,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(MainPalette.Accent)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onSend,
                )
                .padding(horizontal = 14.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = t("发送"),
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
            )
        }
    }
}

@Composable
private fun ChatTimeLabel(text: String) {
    Text(
        text = text,
        color = MainPalette.Secondary.copy(alpha = 0.72f),
        fontSize = 10.sp,
        maxLines = 1,
    )
}

private fun listenChatPackClaim(msg: ListenChatMsg, body: String): String =
    "${msg.uid.trim()}|${msg.at}|$body"

private fun listenChatPackFresh(atMs: Long, nowMs: Long = System.currentTimeMillis()): Boolean {
    if (atMs <= 0L) return false
    val age = nowMs - atMs
    return age in -3_000L..2_000L
}

@Composable
private fun ChatPackFace(
    pack: com.kite.zmusic.listen.ListenChatPack,
    spin: Boolean,
    onSpinStart: () -> Unit,
) {
    var spinning by remember(pack) { mutableStateOf(spin) }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        ListenChatPackArt(
            pack = pack,
            play = spinning,
            onPlayStart = onSpinStart,
            onFinished = { spinning = false },
        )
        if (spinning) {
            Spacer(Modifier.height(2.dp))
        } else {
            Text(
                text = listenChatPackCaption(pack),
                color = MainPalette.Secondary,
                fontSize = 11.sp,
            )
        }
    }
}

private val ChatQuickEmojis = listOf(
    "😀", "😁", "😂", "🤣", "😊", "😍", "🥰", "😘", "😜", "🤪",
    "🤗", "🤔", "🙄", "😴", "🥺", "😢", "😭", "😤", "😡", "🤯",
    "😳", "😇", "😎", "🤩", "🥳", "🤤", "😷", "🤒", "🤡", "👻",
    "👍", "👎", "👌", "✌️", "🤞", "🤟", "🤘", "👏", "🙌", "🤝",
    "❤️", "🧡", "💛", "💚", "💙", "💜", "🖤", "💔", "💕", "💯",
    "🔥", "⭐", "✨", "🎉", "🎵", "🎶", "🎧", "🎤", "🎸", "💃",
    "🫶", "👀", "💪", "🙏", "🌸", "🍀", "🌙", "☀️", "🌈", "☕",
)

@Composable
private fun ChatEmojiPanel(
    recent: List<String>,
    onPick: (String) -> Unit,
    onSendPack: (ListenChatPackKind) -> Unit,
    modifier: Modifier = Modifier,
) {
    val glyphs = remember(recent) {
        (recent + ChatQuickEmojis).distinct()
    }
    val pagerState = rememberPagerState { 2 }
    val scope = rememberCoroutineScope()
    Column(
        modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            ),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ChatEmojiTabChip(
                label = t("表情"),
                selected = pagerState.currentPage == 0,
                onClick = { scope.launch { pagerState.animateScrollToPage(0) } },
            )
            ChatEmojiTabChip(
                label = t("表情包"),
                selected = pagerState.currentPage == 1,
                onClick = { scope.launch { pagerState.animateScrollToPage(1) } },
            )
        }
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = false,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .pointerInput(pagerState) {
                    val slop = viewConfiguration.touchSlop
                    awaitEachGesture {
                        val down = awaitFirstDown(
                            requireUnconsumed = false,
                            pass = PointerEventPass.Initial,
                        )
                        var total = 0f
                        var horizontal = false
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            val dx = change.position.x - change.previousPosition.x
                            val dy = change.position.y - change.previousPosition.y
                            if (!horizontal) {
                                val moved = abs(change.position.x - down.position.x)
                                val movedY = abs(change.position.y - down.position.y)
                                if (moved > slop && moved > movedY) {
                                    horizontal = true
                                } else if (movedY > slop) {
                                    break
                                }
                            }
                            if (horizontal) {
                                change.consume()
                                total += dx
                                pagerState.dispatchRawDelta(-dx)
                            }
                            if (!change.pressed) {
                                if (horizontal) {
                                    val target = when {
                                        pagerState.currentPageOffsetFraction > 0.18f ->
                                            pagerState.currentPage + 1
                                        pagerState.currentPageOffsetFraction < -0.18f ->
                                            pagerState.currentPage - 1
                                        abs(total) > 72f && pagerState.currentPageOffsetFraction == 0f ->
                                            pagerState.currentPage + if (total < 0f) 1 else -1
                                        else -> pagerState.currentPage
                                    }.coerceIn(0, 1)
                                    scope.launch { pagerState.animateScrollToPage(target) }
                                }
                                break
                            }
                        }
                    }
                },
        ) { page ->
            when (page) {
                0 -> LazyVerticalGrid(
                    columns = GridCells.Fixed(8),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(glyphs, key = { it }) { emoji ->
                        Box(
                            Modifier
                                .height(44.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable(
                                    interactionSource = remember(emoji) { MutableInteractionSource() },
                                    indication = null,
                                    onClick = { onPick(emoji) },
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(text = emoji, fontSize = 22.sp)
                        }
                    }
                }
                else -> Row(
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ChatPackTile(
                        modifier = Modifier.weight(1f),
                        kind = ListenChatPackKind.Dice,
                        title = t("骰子"),
                        subtitle = t("随机 1–6"),
                        onClick = { onSendPack(ListenChatPackKind.Dice) },
                    )
                    ChatPackTile(
                        modifier = Modifier.weight(1f),
                        kind = ListenChatPackKind.Rps,
                        title = t("猜拳"),
                        subtitle = t("石头剪刀布"),
                        onClick = { onSendPack(ListenChatPackKind.Rps) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatEmojiTabChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) MainPalette.Accent.copy(alpha = 0.16f) else MainPalette.Card)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (selected) MainPalette.Accent else MainPalette.Secondary,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun ChatPackTile(
    kind: ListenChatPackKind,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(16.dp))
            .background(MainPalette.Card)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 12.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        ListenChatPackArt(
            pack = com.kite.zmusic.listen.ListenChatPack(kind, if (kind == ListenChatPackKind.Dice) "6" else "rock"),
            play = false,
            preview = true,
            size = 72.dp,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = title,
            color = MainPalette.Ink,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = subtitle,
            color = MainPalette.Secondary,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun ChatHeaderArrowButton(
    expandT: Float,
    onClick: () -> Unit,
) {
    NowPlayingDismissIconButton(
        onClick = onClick,
        modifier = Modifier.graphicsLayer { rotationZ = expandT * 180f },
        chromeBackground = false,
        tint = MainPalette.Ink,
        pointingUp = true,
    )
}
