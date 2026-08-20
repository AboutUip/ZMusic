package com.kite.zmusic.ui.catalog

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kite.zmusic.R
import com.kite.zmusic.ZMusicApplication
import com.kite.zmusic.data.ChartSummary
import com.kite.zmusic.data.NcmHomeParse
import com.kite.zmusic.data.SessionRepository
import com.kite.zmusic.data.TrackRow
import com.kite.zmusic.data.UserProfileBrief
import com.kite.zmusic.data.formatAlbumType
import com.kite.zmusic.data.formatAlbumYear
import com.kite.zmusic.ui.artist.ArtistAlbumsScreen
import com.kite.zmusic.ui.artist.ArtistMvsScreen
import com.kite.zmusic.ui.artist.ArtistScreen
import com.kite.zmusic.ui.common.GlassAlertDialog
import com.kite.zmusic.ui.common.PlayingEqualizer
import com.kite.zmusic.ui.common.UrlImage
import com.kite.zmusic.ui.common.ZPullRefresh
import com.kite.zmusic.ui.icons.ZIcons
import com.kite.zmusic.ui.library.LikedArtistsScreen
import com.kite.zmusic.ui.library.LikedArtistsSearchScreen
import com.kite.zmusic.ui.main.LandscapeCoverEnter
import com.kite.zmusic.ui.main.LandscapeCoverExit
import com.kite.zmusic.ui.main.MainPalette
import com.kite.zmusic.ui.mv.MvPlayerScreen
import com.kite.zmusic.ui.search.SearchScreen
import com.kite.zmusic.ui.search.SearchViewModel
import com.kite.zmusic.ui.search.SearchViewModelFactory
import com.kite.zmusic.ui.settings.SettingsScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

import com.kite.zmusic.ui.main.MainOverlay
@Composable
internal fun CatalogTopBar(
    title: String,
    onBack: () -> Unit,
    extraLabel: String? = null,
    extraIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onExtra: (() -> Unit)? = null,
    onSearch: (() -> Unit)? = null,
    onManage: (() -> Unit)? = null,
    onSelectAll: (() -> Unit)? = null,
    allSelected: Boolean = false,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
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
                tint = MainPalette.Ink,
                modifier = Modifier.size(22.dp),
            )
        }
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(
                color = MainPalette.Ink,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
        if (onSelectAll != null) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onSelectAll,
                    )
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (allSelected) "取消全选" else "全选",
                    color = MainPalette.Accent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        if (onManage != null) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onManage,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = ZIcons.Manage,
                    contentDescription = "管理",
                    tint = MainPalette.Ink,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        if (onSearch != null) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onSearch,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = ZIcons.Search,
                    contentDescription = "搜索歌单内歌曲",
                    tint = MainPalette.Ink,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        if (extraLabel != null && onExtra != null) {
            Row(
                Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onExtra,
                    )
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (extraIcon != null) {
                    Icon(
                        imageVector = extraIcon,
                        contentDescription = extraLabel,
                        tint = MainPalette.Accent,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                }
                Text(extraLabel, color = MainPalette.Accent, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

internal fun isPlaybackCurrent(
    trackId: Long,
    contextId: Long,
    playingTrackId: Long,
    playingSourceId: Long,
): Boolean {
    if (trackId <= 0L || playingTrackId <= 0L || trackId != playingTrackId) return false
    return if (contextId > 0L) {
        playingSourceId == contextId
    } else {
        playingSourceId <= 0L
    }
}
