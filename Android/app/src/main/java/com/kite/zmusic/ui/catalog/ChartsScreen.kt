package com.kite.zmusic.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kite.zmusic.data.ChartSummary
import com.kite.zmusic.ui.common.UrlImage
import com.kite.zmusic.ui.chrome.chromePage
import com.kite.zmusic.ui.main.MainPalette
import com.kite.zmusic.plugin.PluginSurfaces
import com.kite.zmusic.plugin.PluginUiTarget
import com.kite.zmusic.ui.plugin.pluginSurface
import com.kite.zmusic.i18n.t

@Composable
internal fun ChartsScreen(
    state: ChartsUiState,
    contentBottomInset: Dp,
    onBack: () -> Unit,
    onOpen: (ChartSummary) -> Unit,
    onRetry: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .chromePage()
            .statusBarsPadding(),
    ) {
        CatalogTopBar(title = t("排行榜"), onBack = onBack)
        when {
            state.loading && state.charts.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        color = MainPalette.Accent,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
            state.error != null && state.charts.isEmpty() -> {
                Text(
                    text = state.error,
                    color = MainPalette.Secondary,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .padding(24.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onRetry,
                        ),
                )
            }
            else -> {
                val cols = 3
                val rows = (state.charts.size + cols - 1) / cols
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 20.dp,
                        end = 20.dp,
                        bottom = contentBottomInset + 16.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    items(rows) { row ->
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            repeat(cols) { col ->
                                val i = row * cols + col
                                if (i < state.charts.size) {
                                    ChartTile(
                                        chart = state.charts[i],
                                        onOpen = { onOpen(state.charts[i]) },
                                        modifier = Modifier.weight(1f),
                                    )
                                } else {
                                    Spacer(Modifier.weight(1f))
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
private fun ChartTile(
    chart: ChartSummary,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onOpen,
        ),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(MainPalette.Placeholder)
                .pluginSurface(PluginSurfaces.CHART_COVER, PluginUiTarget.chart(chart)),
        ) {
            UrlImage(
                url = chart.coverUrl,
                contentDescription = chart.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = chart.name,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(color = MainPalette.Ink, fontSize = 12.sp, fontWeight = FontWeight.Medium),
        )
        chart.updateFrequency?.let {
            Text(it, color = MainPalette.Secondary, fontSize = 10.sp, maxLines = 1)
        }
    }
}

