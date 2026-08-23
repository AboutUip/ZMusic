package com.kite.zmusic.ui.user

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kite.zmusic.ZMusicApplication
import com.kite.zmusic.data.FollowedUser
import com.kite.zmusic.data.SessionRepository
import com.kite.zmusic.ui.catalog.CatalogTopBar
import com.kite.zmusic.ui.chrome.chromePage
import com.kite.zmusic.ui.common.UrlImage
import com.kite.zmusic.ui.common.UrlImageCache
import com.kite.zmusic.ui.common.ZPullRefresh
import com.kite.zmusic.ui.main.MainOverlay
import com.kite.zmusic.ui.main.MainPalette

@Composable
fun UserRelationsScreen(
    overlay: MainOverlay.UserRelations,
    sessionRepository: SessionRepository,
    contentBottomInset: Dp,
    onBack: () -> Unit,
    onOpenUser: (FollowedUser) -> Unit,
    modifier: Modifier = Modifier,
) {
    val app = LocalContext.current.applicationContext as ZMusicApplication
    val vm: UserRelationsViewModel = viewModel(
        key = "user-rel-${overlay.userId}-${overlay.fans}",
        factory = UserRelationsViewModelFactory(
            userId = overlay.userId,
            seedName = overlay.name,
            fans = overlay.fans,
            sessionRepository = sessionRepository,
            users = app.userRepository,
        ),
    )
    LaunchedEffect(overlay.userId, overlay.fans) { vm.load() }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val nearEnd by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = listState.layoutInfo.totalItemsCount
            total > 1 && last >= total - 3
        }
    }
    LaunchedEffect(nearEnd, ui.people.size, ui.hasMore, ui.loadingMore) {
        if (nearEnd && ui.hasMore && !ui.loadingMore && ui.people.isNotEmpty()) {
            vm.loadMore()
        }
    }
    Column(
        modifier
            .fillMaxSize()
            .chromePage()
            .statusBarsPadding(),
    ) {
        CatalogTopBar(title = ui.title, onBack = onBack)
        ZPullRefresh(
            refreshing = ui.refreshing,
            onRefresh = vm::refresh,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            when {
                ui.loading && ui.people.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            color = MainPalette.Accent,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }
                ui.error != null && ui.people.isEmpty() -> {
                    Text(
                        text = ui.error ?: "加载失败",
                        color = MainPalette.Secondary,
                        fontSize = 14.sp,
                        modifier = Modifier
                            .padding(24.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { vm.load(force = true) },
                            ),
                    )
                }
                ui.people.isEmpty() -> {
                    Text(
                        text = if (ui.fans) "还没有粉丝" else "还没有关注的人",
                        color = MainPalette.Secondary,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(24.dp),
                    )
                }
                else -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 20.dp,
                            end = 20.dp,
                            bottom = contentBottomInset + 16.dp,
                        ),
                    ) {
                        items(ui.people, key = { it.id }) { person ->
                            UserPersonRow(
                                person = person,
                                onClick = { onOpenUser(person) },
                            )
                        }
                        if (ui.loadingMore) {
                            item(key = "rel-more") {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 12.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CircularProgressIndicator(
                                        color = MainPalette.Accent.copy(alpha = 0.7f),
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(18.dp),
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

@Composable
private fun UserPersonRow(
    person: FollowedUser,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UrlImage(
            url = person.avatarUrl,
            contentDescription = null,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape),
            contentScale = ContentScale.Crop,
            maxPx = UrlImageCache.THUMB_MAX_PX,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = person.name,
                style = TextStyle(
                    color = MainPalette.Ink,
                    fontWeight = FontWeight.Medium,
                    fontSize = 15.sp,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            person.signature?.let { sig ->
                Text(
                    text = sig,
                    style = TextStyle(color = MainPalette.Secondary, fontSize = 12.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
