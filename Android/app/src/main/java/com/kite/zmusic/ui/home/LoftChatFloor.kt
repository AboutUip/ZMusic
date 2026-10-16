package com.kite.zmusic.ui.home

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kite.zmusic.ZMusicApplication
import com.kite.zmusic.i18n.t
import com.kite.zmusic.listen.ListenChatMsg
import com.kite.zmusic.listen.ListenChatPackKind
import com.kite.zmusic.listen.ListenChatReplyQuote
import com.kite.zmusic.listen.ListenSticker
import com.kite.zmusic.listen.ListenStickerMaxBytes
import com.kite.zmusic.listen.encodeListenChatPack
import com.kite.zmusic.listen.encodeListenChatSticker
import com.kite.zmusic.listen.encodeListenChatText
import com.kite.zmusic.listen.listenChatQuoteFromMsg
import com.kite.zmusic.listen.listenChatStickerMime
import com.kite.zmusic.listen.listenChatTranslateLookup
import com.kite.zmusic.listen.listenChatUidEquals
import com.kite.zmusic.listen.listenChatVisibleBody
import com.kite.zmusic.ui.community.rememberCommunityLoginOpener
import com.kite.zmusic.ui.easter.MjEasterEgg
import com.kite.zmusic.ui.main.MainPalette
import com.kite.zmusic.ui.notice.showIslandNotice
import com.kite.zmusic.ui.player.ChatComposerBar
import com.kite.zmusic.ui.player.ChatComposerReplyBar
import com.kite.zmusic.ui.player.ChatEmojiPanel
import com.kite.zmusic.ui.player.ChatRow
import com.kite.zmusic.workshop.WorkshopApiError
import java.io.ByteArrayOutputStream
import kotlin.math.abs
import kotlin.math.max
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun LoftChatFloor(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val app = context.applicationContext as ZMusicApplication
    val session by app.workshopAuthStore.session.collectAsStateWithLifecycle()
    val login = rememberCommunityLoginOpener()
    if (session == null) {
        LoftLoginGate(
            onLogin = { login.openLogin() },
            onClose = onClose,
            modifier = modifier,
        )
        return
    }
    LoftChatPane(onClose = onClose, modifier = modifier)
}

@Composable
private fun LoftLoginGate(
    onLogin: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .swipeUpToClose(onClose)
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = t("二楼"),
            color = MainPalette.Ink,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = t("登录后即可在二楼发言"),
            color = MainPalette.Secondary,
            fontSize = 14.sp,
        )
        Spacer(Modifier.height(22.dp))
        Box(
            Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(MainPalette.Accent)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onLogin,
                )
                .padding(horizontal = 22.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = t("登录社区"), color = androidx.compose.ui.graphics.Color.White, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(18.dp))
        Text(text = t("上滑回到一楼"), color = MainPalette.Hint, fontSize = 13.sp)
    }
}

@Composable
private fun LoftChatPane(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val app = context.applicationContext as ZMusicApplication
    val loft = app.loftChat
    val listen = app.listenTogether
    val messages by loft.messages.collectAsStateWithLifecycle()
    val translations by loft.translations.collectAsStateWithLifecycle()
    val selfUid = app.workshopAuthStore.session.collectAsStateWithLifecycle().value?.uid.orEmpty()
    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    var draft by remember { mutableStateOf(TextFieldValue("")) }
    var replyTo by remember { mutableStateOf<ListenChatReplyQuote?>(null) }
    var emojiOpen by remember { mutableStateOf(false) }
    var keyboardHandoff by remember { mutableStateOf(false) }
    var actionMsgId by remember { mutableStateOf<Long?>(null) }
    var spunPacks by remember { mutableStateOf(emptySet<String>()) }
    var recentEmoji by remember { mutableStateOf(emptyList<String>()) }
    var stickers by remember { mutableStateOf<List<ListenSticker>>(emptyList()) }
    var stickerUploading by remember { mutableStateOf(false) }
    val stickersRef = rememberUpdatedState(stickers)
    val closeRef = rememberUpdatedState(onClose)
    val density = LocalDensity.current
    val backClaim = LocalLoftBackClaim.current
    var emojiPanelH by remember { mutableStateOf(260.dp) }
    var panelPx by remember { mutableFloatStateOf(0f) }
    var lastImeSlot by remember { mutableFloatStateOf(0f) }
    var followBottom by remember { mutableStateOf(true) }
    val imeBottom = WindowInsets.ime.getBottom(density)
    val navBottom = WindowInsets.navigationBars.getBottom(density)
    val imeSlot = (imeBottom - navBottom).coerceAtLeast(0).toFloat()
    val imeSlotRef = rememberUpdatedState(imeSlot)
    val emojiTargetRef = rememberUpdatedState(with(density) { emojiPanelH.toPx() })
    val handoffRef = rememberUpdatedState(keyboardHandoff)

    DisposableEffect(Unit) {
        loft.setActive(true)
        onDispose {
            loft.setActive(false)
            backClaim.held = false
        }
    }
    SideEffect {
        backClaim.held = emojiOpen
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
    LaunchedEffect(emojiOpen) {
        if (!emojiOpen) return@LaunchedEffect
        val loaded = runCatching { listen.listStickers() }.getOrNull() ?: return@LaunchedEffect
        stickers = loaded.filter { it.id !in stickersRef.value.map { s -> s.id }.toSet() }.let { remote ->
            stickersRef.value.filter { it.id !in remote.map { s -> s.id }.toSet() } + remote
        }
    }
    val tailKey = messages.lastOrNull()?.rowKey
    LaunchedEffect(listState) {
        snapshotFlow {
            listState.isScrollInProgress to
                (listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset <= 48)
        }.collect { (scrolling, atBottom) ->
            if (atBottom) followBottom = true
            else if (scrolling) followBottom = false
        }
    }
    LaunchedEffect(tailKey) {
        val tail = messages.lastOrNull() ?: return@LaunchedEffect
        if (followBottom || tail.rowKey < 0L) listState.animateScrollToItem(0)
    }
    LaunchedEffect(listState, messages.size) {
        snapshotFlow {
            val info = listState.layoutInfo
            val visible = info.visibleItemsInfo
            visible.isNotEmpty() && visible.last().index >= info.totalItemsCount - 2
        }
            .distinctUntilChanged()
            .collect { nearOldest -> if (nearOldest) loft.loadOlder() }
    }
    val pickSticker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        stickerUploading = true
        scope.launch {
            try {
                val bytes = withContext(Dispatchers.IO) { readLoftStickerBytes(context, uri) }
                if (listenChatStickerMime(bytes) == null) {
                    context.showIslandNotice(t("不支持的图片"))
                    return@launch
                }
                val saved = listen.uploadSticker(bytes)
                stickers = listOf(saved) + stickersRef.value.filter { it.id != saved.id }
            } catch (e: CancellationException) {
                throw e
            } catch (e: LoftStickerTooLarge) {
                context.showIslandNotice(t("图片不能超过 4MB"))
            } catch (e: Exception) {
                context.showIslandNotice(loftStickerNotice(e))
            } finally {
                stickerUploading = false
            }
        }
    }

    fun send(body: String) {
        MjEasterEgg.consider(body)
        val payload = encodeListenChatText(replyTo, body)
        draft = TextFieldValue("")
        replyTo = null
        loft.send(payload)
    }

    Column(
        modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 16.dp),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .swipeUpToClose { closeRef.value() }
                .padding(top = 12.dp, bottom = 10.dp),
        ) {
            Text(
                text = t("二楼"),
                color = MainPalette.Ink,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = t("上滑回到一楼"),
                color = MainPalette.Secondary,
                fontSize = 13.sp,
            )
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .loftListDismiss(listState) { closeRef.value() },
                reverseLayout = true,
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(messages.asReversed(), key = { it.rowKey }) { msg ->
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .animateItem(
                                fadeInSpec = tween(180),
                                placementSpec = null,
                                fadeOutSpec = tween(120),
                            ),
                    ) {
                    ChatRow(
                        msg = msg,
                        self = msg.id < 0L || listenChatUidEquals(msg.uid, selfUid),
                        read = false,
                        showRead = false,
                        spunPacks = spunPacks,
                        onPackSpun = { spunPacks = spunPacks + it },
                        translation = listenChatTranslateLookup(translations, msg),
                        menuOpen = actionMsgId == msg.rowKey,
                        onOpenUser = { _, _, _ -> },
                        onLongPress = {
                            keyboard?.hide()
                            focusManager.clearFocus(force = true)
                            actionMsgId = msg.rowKey
                        },
                        onDismissMenu = { actionMsgId = null },
                        onCopy = {
                            actionMsgId = null
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            cm?.setPrimaryClip(ClipData.newPlainText(t("聊天消息"), listenChatVisibleBody(msg.text)))
                            context.showIslandNotice(t("已复制"))
                        },
                        onReply = {
                            actionMsgId = null
                            replyTo = listenChatQuoteFromMsg(msg)
                            if (emojiOpen) keyboardHandoff = true
                            emojiOpen = false
                            focusRequester.requestFocus()
                            keyboard?.show()
                        },
                        onTranslate = {
                            actionMsgId = null
                            loft.translate(msg)
                        },
                        onHideTranslation = { loft.hideTranslation(msg) },
                    )
                    }
                }
            }
            if (messages.isEmpty()) {
                Box(
                    Modifier.fillMaxSize().swipeUpToClose { closeRef.value() },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = t("还没有人发言"), color = MainPalette.Secondary, fontSize = 14.sp)
                }
            }
        }
        AnimatedVisibility(
            visible = replyTo != null,
            enter = fadeIn(tween(140)) + expandVertically(expandFrom = Alignment.Bottom),
            exit = fadeOut(tween(100)) + shrinkVertically(shrinkTowards = Alignment.Bottom),
        ) {
            val quote = replyTo
            if (quote != null) {
                ChatComposerReplyBar(quote = quote, onClear = { replyTo = null })
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
                    keyboardHandoff = false
                    keyboard?.hide()
                    focusManager.clearFocus(force = true)
                    emojiOpen = true
                }
            },
            onSend = {
                val text = draft.text.trim()
                if (text.isNotEmpty()) send(text)
            },
            onFocusChange = { focused ->
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
                        stickers = stickers,
                        stickerUploading = stickerUploading,
                        onPick = { emoji ->
                            val text = draft.text
                            val start = draft.selection.start.coerceIn(0, text.length)
                            val end = draft.selection.end.coerceIn(0, text.length)
                            val a = minOf(start, end)
                            val b = maxOf(start, end)
                            val next = text.replaceRange(a, b, emoji)
                            draft = TextFieldValue(next, TextRange(a + emoji.length))
                            recentEmoji = (listOf(emoji) + recentEmoji.filter { it != emoji }).take(16)
                        },
                        onSendPack = { kind -> send(encodeListenChatPack(kind)) },
                        onAddSticker = {
                            if (!stickerUploading) {
                                pickSticker.launch(arrayOf("image/*", "image/gif", "image/png", "image/webp", "image/jpeg"))
                            }
                        },
                        onSendSticker = { id -> send(encodeListenChatSticker(id)) },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

@Composable
private fun Modifier.swipeUpToClose(onClose: () -> Unit): Modifier {
    val current = rememberUpdatedState(onClose)
    return pointerInput(Unit) {
        val threshold = 32.dp.toPx()
        val slop = viewConfiguration.touchSlop
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            var up = 0f
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                val dy = change.positionChange().y
                if (dy < 0f) up += -dy else if (dy > 0f) up = 0f
                if (up > slop && dy <= 0f) change.consume()
                if (up >= threshold) current.value()
                if (!change.pressed) break
            }
        }
    }
}

@Composable
private fun Modifier.loftListDismiss(
    listState: LazyListState,
    onClose: () -> Unit,
): Modifier {
    val current = rememberUpdatedState(onClose)
    return pointerInput(listState) {
        val threshold = 32.dp.toPx()
        val slop = viewConfiguration.touchSlop
        awaitEachGesture {
            val down = awaitFirstDown(
                requireUnconsumed = false,
                pass = PointerEventPass.Initial,
            )
            var up = 0f
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                val dy = change.positionChange().y
                val atNewest = !listState.canScrollBackward
                if (atNewest && dy < 0f) {
                    up += -dy
                } else if (dy > 0f) {
                    up = 0f
                }
                if (atNewest && up > slop && dy <= 0f) change.consume()
                if (up >= threshold) current.value()
                if (!change.pressed) break
            }
        }
    }
}

private class LoftStickerTooLarge : RuntimeException()

private fun readLoftStickerBytes(context: Context, uri: Uri): ByteArray {
    val input = context.contentResolver.openInputStream(uri) ?: throw java.io.IOException("sticker")
    input.use { stream ->
        val out = ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        var total = 0
        while (true) {
            val n = stream.read(buf)
            if (n < 0) break
            total += n
            if (total > ListenStickerMaxBytes) throw LoftStickerTooLarge()
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }
}

private fun loftStickerNotice(err: Throwable): String {
    val code = (err as? WorkshopApiError.Message)?.message.orEmpty()
    return when {
        err is WorkshopApiError.RateLimited || code == "rate_limited" -> t("请求太频繁，稍后再试")
        code == "too_large" -> t("图片不能超过 4MB")
        code == "bad_image" -> t("不支持的图片")
        code == "quota" -> t("表情包已满")
        else -> t("表情包上传失败")
    }
}
