package com.kite.zmusic.ui.plugin

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kite.zmusic.ZMusicApplication
import com.kite.zmusic.ui.chrome.chromePage
import com.kite.zmusic.ui.icons.ZIcons
import com.kite.zmusic.ui.main.MainContentPadTop
import com.kite.zmusic.ui.main.MainPageHeader
import com.kite.zmusic.ui.main.mainContentPadH
import com.kite.zmusic.ui.theme.TextTheme

enum class PluginPageChrome {
    Overlay,
    Destination,
}

@Composable
fun PluginPageScreen(
    pluginId: String,
    pageName: String,
    instance: String,
    contentBottomInset: Dp,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    chrome: PluginPageChrome = PluginPageChrome.Overlay,
    selected: Boolean = true,
    landscape: Boolean = false,
) {
    val app = LocalContext.current.applicationContext as ZMusicApplication
    val ui = app.pluginEngine.ui
    val pages by ui.pages.collectAsStateWithLifecycle()
    val page = pages[pluginId]?.get(pageName)
    val params = remember(pluginId, pageName, instance) {
        ui.paramsOf(pluginId, pageName, instance)
    }
    LaunchedEffect(page == null, chrome) {
        if (page == null && chrome == PluginPageChrome.Overlay) onBack()
    }
    DisposableEffect(pluginId, pageName, instance, selected) {
        if (!selected) {
            return@DisposableEffect onDispose { }
        }
        ui.emit(
            pluginId,
            mapOf(
                "type" to "open",
                "page" to pageName,
                "params" to params,
            ),
        )
        onDispose {
            ui.emit(
                pluginId,
                mapOf(
                    "type" to "leave",
                    "page" to pageName,
                    "params" to params,
                ),
            )
        }
    }
    if (page == null) return
    val padH = mainContentPadH(landscape)
    Column(
        modifier
            .fillMaxSize()
            .chromePage()
            .statusBarsPadding(),
    ) {
        when (chrome) {
            PluginPageChrome.Overlay -> {
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
                            tint = TextTheme.CatalogTitle,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Text(
                        text = page.title,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = TextStyle(
                            color = TextTheme.CatalogTitle,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                }
                Text(
                    text = page.pluginName,
                    modifier = Modifier.padding(horizontal = 20.dp),
                    style = TextStyle(color = TextTheme.Hint, fontSize = 12.sp),
                )
                Spacer(Modifier.height(8.dp))
            }
            PluginPageChrome.Destination -> {
                if (!landscape) {
                    MainPageHeader(
                        title = page.title,
                        landscape = false,
                        modifier = Modifier
                            .padding(horizontal = padH)
                            .padding(top = MainContentPadTop),
                    )
                }
            }
        }
        PluginUiTreeView(
            pluginId = pluginId,
            node = page.root,
            onPress = { id ->
                ui.emit(
                    pluginId,
                    mapOf(
                        "type" to "press",
                        "page" to pageName,
                        "id" to id,
                    ),
                )
            },
            onChange = { id, value ->
                ui.applyControl(pluginId, pageName, id, value)
            },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = padH)
                .padding(bottom = contentBottomInset + 24.dp),
        )
    }
}
