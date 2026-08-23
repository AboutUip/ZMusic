package com.kite.zmusic.ui.settings

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kite.zmusic.ui.common.ZPullRefresh
import com.kite.zmusic.ui.icons.ZIcons
import com.kite.zmusic.ui.main.MainPalette
import com.kite.zmusic.ui.main.wallpaperItemChrome

@Composable
fun <T> CommunityCatalogScaffold(
    ui: CommunityCatalogUiState<T>,
    onQueryChange: (String) -> Unit,
    onLoadMore: () -> Unit,
    onRefresh: () -> Unit,
    intro: String,
    searchPlaceholder: String,
    queryLimit: Int,
    emptyQueryMessage: String,
    emptySearchMessage: String,
    contentBottomInset: Dp,
    modifier: Modifier = Modifier,
    itemKey: (T) -> String,
    itemContent: @Composable (T) -> Unit,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val listState = rememberLazyListState()
    val filtered = ui.entries
    DisposableEffect(Unit) {
        onDispose { onQueryChange("") }
    }
    LaunchedEffect(listState, ui.query, ui.more, ui.ready, filtered.size) {
        snapshotFlow {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: return@snapshotFlow false
            val total = info.totalItemsCount
            total > 0 && last >= total - 3
        }.collect { nearEnd ->
            if (nearEnd) onLoadMore()
        }
    }
    Column(
        modifier
            .fillMaxSize()
            .imePadding()
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        Text(
            text = intro,
            style = TextStyle(
                color = MainPalette.Secondary,
                fontSize = 13.sp,
                lineHeight = 18.sp,
            ),
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Spacer(Modifier.height(14.dp))
        CatalogSearchField(
            value = ui.query,
            onValueChange = onQueryChange,
            placeholder = searchPlaceholder,
            maxLength = queryLimit,
            onDone = { keyboard?.hide() },
        )
        Spacer(Modifier.height(16.dp))
        ZPullRefresh(
            refreshing = ui.refreshing,
            onRefresh = onRefresh,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = contentBottomInset + 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                when {
                    !ui.ready && !ui.failed -> {
                        item(key = "catalog-wait") {
                            Box(Modifier.fillParentMaxSize())
                        }
                    }
                    ui.failed && filtered.isEmpty() -> {
                        item(key = "catalog-busy") {
                            CatalogStatusText("社区服务器繁忙")
                        }
                    }
                    filtered.isEmpty() -> {
                        item(key = "catalog-empty") {
                            CatalogStatusText(
                                if (ui.query.isBlank()) emptyQueryMessage else emptySearchMessage,
                            )
                        }
                    }
                    else -> {
                        items(filtered, key = itemKey) { entry ->
                            itemContent(entry)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CatalogStatusText(text: String) {
    Text(
        text = text,
        style = TextStyle(
            color = MainPalette.Hint,
            fontSize = 14.sp,
        ),
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
    )
}

@Composable
private fun CatalogSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    maxLength: Int,
    onDone: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .wallpaperItemChrome(RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = ZIcons.Search,
            contentDescription = null,
            tint = MainPalette.Hint,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(10.dp))
        BasicTextField(
            value = value,
            onValueChange = { onValueChange(it.take(maxLength)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onDone() }),
            textStyle = TextStyle(
                color = MainPalette.Ink,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
            ),
            cursorBrush = SolidColor(MainPalette.Accent),
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            color = MainPalette.Hint,
                            fontSize = 15.sp,
                        )
                    }
                    inner()
                }
            },
        )
        if (value.isNotEmpty()) {
            Icon(
                imageVector = ZIcons.Close,
                contentDescription = "清除",
                tint = MainPalette.Hint,
                modifier = Modifier
                    .size(18.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onValueChange("") },
                    ),
            )
        }
    }
}
