package com.kite.zmusic.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kite.zmusic.ZMusicApplication
import com.kite.zmusic.listen.ListenChatMsg
import com.kite.zmusic.ui.common.UrlImage
import com.kite.zmusic.ui.easter.MjEasterEgg
import com.kite.zmusic.ui.main.MainPalette
import com.kite.zmusic.ui.main.pageSheetHazeStyle
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import kotlinx.coroutines.delay
import com.kite.zmusic.i18n.t

private val ChatBubbleShape = RoundedCornerShape(14.dp)
private val ChatComposerShape = RoundedCornerShape(18.dp)

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
    val chat = ui.room?.chat.orEmpty()
    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val density = LocalDensity.current
    var draft by remember { mutableStateOf("") }
    var composerFocused by remember { mutableStateOf(false) }
    var restoreAfterIme by remember { mutableStateOf(false) }
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val imeBottom = WindowInsets.ime.getBottom(density)
    val newestId = chat.lastOrNull()?.id
    LaunchedEffect(ui.inRoom, ui.room?.id) {
        if (ui.inRoom) listen.markChatRead()
    }
    LaunchedEffect(composerFocused) {
        if (composerFocused) {
            if (!fullscreen) {
                restoreAfterIme = true
                onExpandFullscreen()
                delay(320)
            }
        } else if (restoreAfterIme) {
            restoreAfterIme = false
            onCollapseToTwoThirds()
        }
    }
    LaunchedEffect(newestId, chat.size, imeBottom) {
        if (chat.isEmpty()) return@LaunchedEffect
        kotlinx.coroutines.yield()
        listState.scrollToItem(0)
    }

    fun send() {
        val text = draft.trim()
        if (text.isEmpty()) return
        MjEasterEgg.consider(text)
        draft = ""
        listen.sendChat(text)
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
                .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
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
                            self = msg.uid == ui.selfUid,
                            onOpenUser = onOpenUser,
                        )
                    }
                }
            }
            ChatComposerBar(
                draft = draft,
                focusRequester = focusRequester,
                onDraftChange = { draft = it },
                onSend = { send() },
                onFocusChange = { composerFocused = it },
            )
        }
        if (t < 0.02f) {
            Box(Modifier.matchParentSize())
        }
    }
}

@Composable
private fun ChatRow(
    msg: ListenChatMsg,
    self: Boolean,
    onOpenUser: (Long, String, String?) -> Unit,
) {
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
            Text(
                text = if (self) t("我") else msg.nickname.ifBlank { msg.uid },
                color = MainPalette.Secondary,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Box(
                Modifier
                    .clip(ChatBubbleShape)
                    .background(if (self) MainPalette.Accent.copy(alpha = 0.18f) else MainPalette.Card)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(
                    text = msg.text,
                    color = MainPalette.Ink,
                    fontSize = 14.sp,
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
    draft: String,
    focusRequester: FocusRequester,
    onDraftChange: (String) -> Unit,
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
                        if (draft.isEmpty()) {
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
        Spacer(Modifier.width(10.dp))
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
