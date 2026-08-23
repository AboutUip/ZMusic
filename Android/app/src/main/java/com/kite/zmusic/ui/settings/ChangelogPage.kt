package com.kite.zmusic.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kite.zmusic.ZMusicApplication
import com.kite.zmusic.data.ChangelogEntry
import com.kite.zmusic.data.ChangelogRoster
import com.kite.zmusic.ui.icons.ZIcons
import com.kite.zmusic.ui.main.MainPalette
import com.kite.zmusic.ui.main.wallpaperItemChrome

private val ExpandEnter = fadeIn(tween(180)) + expandVertically(
    animationSpec = tween(260, easing = FastOutSlowInEasing),
)
private val ExpandExit = fadeOut(tween(140)) + shrinkVertically(
    animationSpec = tween(220, easing = FastOutSlowInEasing),
)

private const val ChangelogQueryLimit = 24

@Composable
fun ChangelogPage(
    contentBottomInset: Dp,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val app = remember { context.applicationContext as ZMusicApplication }
    val vm: CommunityCatalogViewModel<ChangelogEntry> = viewModel(
        key = "settings-changelog",
        factory = CommunityCatalogViewModelFactory(app.container.changelogRepository, ChangelogQueryLimit),
    )
    val ui by vm.state.collectAsStateWithLifecycle()
    var expandedKey by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(ui.entries, expandedKey) {
        if (expandedKey != null && ui.entries.none { it.listKey == expandedKey }) {
            expandedKey = null
        }
    }
    CommunityCatalogScaffold(
        ui = ui,
        onQueryChange = vm::onQueryChange,
        onLoadMore = vm::loadMore,
        onRefresh = vm::refresh,
        intro = "点开版本查看结构化预览。可用版本号搜索。",
        searchPlaceholder = "搜索版本，例如 1.2.2",
        queryLimit = ChangelogQueryLimit,
        emptyQueryMessage = "暂时还没有记录。",
        emptySearchMessage = "没有匹配的版本。",
        contentBottomInset = contentBottomInset,
        modifier = modifier,
        itemKey = { it.listKey },
    ) { entry ->
        ChangelogVersionCard(
            title = ChangelogRoster.DefaultTitle,
            entry = entry,
            expanded = expandedKey == entry.listKey,
            onToggle = {
                expandedKey = if (expandedKey == entry.listKey) null else entry.listKey
            },
        )
    }
}

@Composable
private fun ChangelogVersionCard(
    title: String,
    entry: ChangelogEntry,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val chevron by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(220, easing = FastOutSlowInEasing),
        label = "changelog-chevron",
    )
    val kindColor = if (entry.kind.equals("Test", ignoreCase = true)) {
        ChangelogKindTestColor
    } else {
        ChangelogKindReleaseColor
    }
    Column(
        Modifier
            .fillMaxWidth()
            .wallpaperItemChrome(RoundedCornerShape(16.dp)),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onToggle,
                )
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = entry.versionLabel,
                    style = TextStyle(
                        color = MainPalette.Ink,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.2.sp,
                    ),
                )
                Spacer(Modifier.height(8.dp))
                ChangelogChip(
                    label = entry.kind,
                    color = kindColor,
                )
            }
            Icon(
                imageVector = ZIcons.ExpandMore,
                contentDescription = if (expanded) "收起" else "展开",
                tint = MainPalette.Hint,
                modifier = Modifier
                    .size(22.dp)
                    .graphicsLayer { rotationZ = chevron },
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = ExpandEnter,
            exit = ExpandExit,
        ) {
            ChangelogPreviewBody(
                title = title,
                entry = entry,
            )
        }
    }
}

