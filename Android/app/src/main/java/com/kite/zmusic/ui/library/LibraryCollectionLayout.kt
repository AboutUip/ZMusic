package com.kite.zmusic.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kite.zmusic.plugin.CollectionFlow
import com.kite.zmusic.plugin.CollectionItemKind
import com.kite.zmusic.plugin.CollectionPresentSpec
import com.kite.zmusic.plugin.PluginCollectionPresent
import com.kite.zmusic.ui.icons.ZIcons
import com.kite.zmusic.ui.main.MainPalette
import com.kite.zmusic.ui.main.wallpaperItemChrome

internal data class LibraryCollectionEntry(
    val title: String,
    val subtitle: String,
    val coverUrl: String?,
    val onOpen: () -> Unit,
    val onMore: (() -> Unit)? = null,
)

@Composable
internal fun LibraryCollectionItems(
    region: String,
    entries: List<LibraryCollectionEntry>,
    modifier: Modifier = Modifier,
) {
    val spec = PluginCollectionPresent.of(region)
    val gap = spec.gap.dp
    if (spec.flow == CollectionFlow.Grid) {
        val cols = spec.columns.coerceIn(1, 4)
        val rows = (entries.size + cols - 1) / cols
        Column(
            modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(gap),
        ) {
            repeat(rows) { row ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(gap),
                ) {
                    repeat(cols) { col ->
                        val i = row * cols + col
                        if (i < entries.size) {
                            CollectionItem(
                                entry = entries[i],
                                spec = spec,
                                modifier = Modifier.weight(1f),
                            )
                        } else {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    } else {
        Column(
            modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(gap),
        ) {
            entries.forEach { entry ->
                CollectionItem(
                    entry = entry,
                    spec = spec,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun CollectionItem(
    entry: LibraryCollectionEntry,
    spec: CollectionPresentSpec,
    modifier: Modifier = Modifier,
) {
    if (spec.item == CollectionItemKind.Tile) {
        CollectionTile(entry, spec, modifier)
    } else {
        CollectionRow(entry, spec, modifier)
    }
}

@Composable
private fun CollectionRow(
    entry: LibraryCollectionEntry,
    spec: CollectionPresentSpec,
    modifier: Modifier,
) {
    val cover = if (spec.flow == CollectionFlow.Grid) 40.dp else 54.dp
    Row(
        modifier
            .then(
                if (spec.flow == CollectionFlow.List) {
                    Modifier.wallpaperItemChrome(RoundedCornerShape(14.dp))
                } else {
                    Modifier
                },
            )
            .padding(horizontal = if (spec.flow == CollectionFlow.List) 12.dp else 0.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .weight(1f)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = entry.onOpen,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CollectionCover(
                url = entry.coverUrl,
                spec = spec,
                modifier = Modifier.size(cover),
                contentDescription = entry.title,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = entry.title,
                    style = TextStyle(
                        color = MainPalette.Ink,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = if (spec.flow == CollectionFlow.Grid) 12.sp else 14.sp,
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (entry.subtitle.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = entry.subtitle,
                        style = TextStyle(
                            color = MainPalette.Secondary,
                            fontSize = if (spec.flow == CollectionFlow.Grid) 10.sp else 12.sp,
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        MoreButton(entry.onMore)
    }
}

@Composable
private fun CollectionTile(
    entry: LibraryCollectionEntry,
    spec: CollectionPresentSpec,
    modifier: Modifier,
) {
    val coverMod = if (spec.flow == CollectionFlow.Grid) {
        Modifier.fillMaxWidth().aspectRatio(1f)
    } else {
        Modifier.size(96.dp)
    }
    Column(modifier) {
        Box {
            CollectionCover(
                url = entry.coverUrl,
                spec = spec,
                modifier = coverMod.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = entry.onOpen,
                ),
                contentDescription = entry.title,
            )
            if (entry.onMore != null) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(2.dp),
                ) {
                    MoreButton(entry.onMore)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Column(
            Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = entry.onOpen,
            ),
        ) {
            Text(
                text = entry.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(
                    color = MainPalette.Ink,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                ),
            )
            if (entry.subtitle.isNotEmpty()) {
                Text(
                    text = entry.subtitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(color = MainPalette.Secondary, fontSize = 10.sp),
                )
            }
        }
    }
}

@Composable
private fun MoreButton(onMore: (() -> Unit)?) {
    if (onMore == null) return
    Box(
        Modifier
            .size(36.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onMore,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = ZIcons.More,
            contentDescription = "更多",
            tint = MainPalette.Hint,
            modifier = Modifier.size(20.dp),
        )
    }
}
