@file:OptIn(ExperimentalMaterial3Api::class)

package com.kite.zmusic.ui.plugin

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kite.zmusic.ZMusicApplication
import com.kite.zmusic.plugin.PluginUiNode
import com.kite.zmusic.ui.common.GlassPromptField
import com.kite.zmusic.ui.common.UrlImage
import com.kite.zmusic.ui.common.UrlImageCache
import com.kite.zmusic.ui.main.wallpaperItemChrome
import com.kite.zmusic.ui.theme.MainControls
import com.kite.zmusic.ui.theme.MainPalette
import com.kite.zmusic.ui.theme.MainSlider
import com.kite.zmusic.ui.theme.TextTheme
import com.kite.zmusic.ui.theme.parseThemeColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun PluginUiTreeView(
    pluginId: String,
    node: PluginUiNode,
    onPress: (String) -> Unit,
    onChange: (String, Any?) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (node.type) {
        "column" -> {
            Column(
                modifier.then(node.padModifier()),
                verticalArrangement = Arrangement.spacedBy(node.gapDp()),
                horizontalAlignment = node.horizontalAlign(),
            ) {
                node.children.forEach { child ->
                    PluginUiTreeView(pluginId, child, onPress, onChange)
                }
            }
        }
        "row" -> {
            Row(
                modifier
                    .fillMaxWidth()
                    .then(node.padModifier()),
                horizontalArrangement = Arrangement.spacedBy(node.gapDp()),
                verticalAlignment = node.verticalAlign(),
            ) {
                node.children.forEach { child ->
                    val flex = child.num("flex")?.toInt()
                    val childMod = when {
                        flex == null -> Modifier.weight(1f, fill = false)
                        flex <= 0 -> Modifier
                        else -> Modifier.weight(flex.coerceIn(1, 8).toFloat())
                    }
                    PluginUiTreeView(
                        pluginId,
                        child,
                        onPress,
                        onChange,
                        childMod,
                    )
                }
            }
        }
        "scroll" -> {
            Column(
                modifier
                    .then(node.padModifier())
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(node.gapDp()),
                horizontalAlignment = node.horizontalAlign(),
            ) {
                node.children.forEach { child ->
                    PluginUiTreeView(pluginId, child, onPress, onChange)
                }
            }
        }
        "spacer" -> {
            val h = node.num("height")?.toInt()?.coerceIn(1, 200) ?: 8
            Spacer(Modifier.height(h.dp))
        }
        "section" -> {
            Column(
                modifier.then(node.padModifier()),
                verticalArrangement = Arrangement.spacedBy(node.gapDp(default = 8)),
                horizontalAlignment = node.horizontalAlign(),
            ) {
                node.str("title")?.takeIf { it.isNotBlank() }?.let { title ->
                    Text(
                        text = title,
                        style = TextStyle(
                            color = node.resolveColor(TextTheme.Subtitle),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.4.sp,
                        ),
                    )
                }
                node.children.forEach { child ->
                    PluginUiTreeView(pluginId, child, onPress, onChange)
                }
            }
        }
        "tabs" -> PluginTabs(pluginId, node, onPress, onChange, modifier)
        "tab" -> {
            Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                node.children.forEach { child ->
                    PluginUiTreeView(pluginId, child, onPress, onChange)
                }
            }
        }
        "text" -> {
            val base = textStyle(node.str("style"))
            Text(
                text = node.str("text").orEmpty(),
                modifier = modifier.then(node.padModifier()),
                style = base.merge(
                    TextStyle(
                        color = node.resolveColor(base.color),
                        fontSize = node.fontSizeSp() ?: base.fontSize,
                        fontWeight = node.fontWeight() ?: base.fontWeight,
                        textAlign = node.textAlign(),
                    ),
                ),
                textAlign = node.textAlign(),
            )
        }
        "image" -> {
            val h = node.num("height")?.toInt()?.coerceIn(40, 400) ?: 160
            val radius = node.num("radius")?.toInt()?.coerceIn(0, 48) ?: 12
            val scale = if (node.str("fit") == "contain") ContentScale.Fit else ContentScale.Crop
            PluginTreeImage(
                pluginId = pluginId,
                src = node.str("src").orEmpty(),
                modifier = modifier
                    .fillMaxWidth()
                    .height(h.dp)
                    .then(node.padModifier())
                    .clip(RoundedCornerShape(radius.dp)),
                contentScale = scale,
            )
        }
        "empty" -> {
            Box(
                modifier
                    .fillMaxWidth()
                    .padding(vertical = 28.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = node.str("text") ?: "暂无内容",
                    style = TextStyle(color = TextTheme.Hint, fontSize = 14.sp),
                )
            }
        }
        "loading" -> {
            Box(
                modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    color = MainPalette.Accent,
                    strokeWidth = 2.5.dp,
                )
            }
        }
        "button" -> PluginTreeButton(node, onPress, modifier)
        "toggle" -> PluginTreeToggle(node, onChange, modifier)
        "slider" -> PluginTreeSlider(node, onChange, modifier)
        "field" -> PluginTreeField(node, onChange, modifier)
        "segmented" -> PluginTreeSegmented(node, onChange, modifier)
        "list" -> {
            Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                node.children.forEach { child ->
                    PluginUiTreeView(pluginId, child, onPress, onChange)
                }
            }
        }
        "item" -> PluginTreeItem(node, onPress, modifier)
        "track" -> PluginTreeTrack(pluginId, node, onPress, modifier)
        else -> Unit
    }
}

@Composable
private fun PluginTabs(
    pluginId: String,
    node: PluginUiNode,
    onPress: (String) -> Unit,
    onChange: (String, Any?) -> Unit,
    modifier: Modifier,
) {
    val tabs = node.children.filter { it.type == "tab" && it.id != null }
    if (tabs.isEmpty()) return
    val selected = (node.str("value") ?: tabs.first().id).orEmpty()
    val current = tabs.find { it.id == selected } ?: tabs.first()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            tabs.forEach { tab ->
                val id = tab.id ?: return@forEach
                val on = id == current.id
                Text(
                    text = tab.str("label").orEmpty(),
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {
                            node.id?.let { onChange(it, id) }
                        },
                    ),
                    style = TextStyle(
                        color = if (on) TextTheme.CatalogTitle else TextTheme.Hint,
                        fontSize = 15.sp,
                        fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                    ),
                )
            }
        }
        PluginUiTreeView(pluginId, current, onPress, onChange)
    }
}

@Composable
private fun PluginTreeButton(
    node: PluginUiNode,
    onPress: (String) -> Unit,
    modifier: Modifier,
) {
    val enabled = node.bool("enabled", true)
    val role = node.str("role")
    val color = when (role) {
        "primary" -> TextTheme.Accent
        "destructive" -> TextTheme.Destructive
        else -> TextTheme.Body
    }.let { node.resolveColor(it) }
    val radius = node.num("radius")?.toInt()?.coerceIn(0, 48) ?: 14
    val hug = node.str("width") == "hug"
    val iconName = node.str("icon")
    Box(
        modifier
            .then(if (hug) Modifier else Modifier.fillMaxWidth())
            .then(node.padModifier())
            .then(if (enabled) Modifier.wallpaperItemChrome(RoundedCornerShape(radius.dp)) else Modifier)
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { node.id?.let(onPress) },
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (!iconName.isNullOrBlank()) {
                Icon(
                    imageVector = pluginUiIcon(iconName),
                    contentDescription = null,
                    tint = if (enabled) color else TextTheme.Hint,
                    modifier = Modifier.size(18.dp),
                )
            }
            Text(
                text = node.str("label").orEmpty(),
                style = TextStyle(
                    color = if (enabled) color else TextTheme.Hint,
                    fontSize = node.fontSizeSp() ?: 15.sp,
                    fontWeight = node.fontWeight() ?: FontWeight.SemiBold,
                ),
            )
        }
    }
}

@Composable
private fun PluginTreeToggle(
    node: PluginUiNode,
    onChange: (String, Any?) -> Unit,
    modifier: Modifier,
) {
    val on = node.bool("value")
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        node.str("label")?.takeIf { it.isNotBlank() }?.let { label ->
            Text(
                text = label,
                style = TextStyle(color = TextTheme.Body, fontSize = 15.sp),
                modifier = Modifier.weight(1f),
            )
        }
        Switch(
            checked = on,
            onCheckedChange = { next ->
                node.id?.let { onChange(it, next) }
            },
            colors = MainControls.switchColors(),
        )
    }
}

@Composable
private fun PluginTreeSlider(
    node: PluginUiNode,
    onChange: (String, Any?) -> Unit,
    modifier: Modifier,
) {
    val min = node.num("min")?.toFloat() ?: 0f
    val maxRaw = node.num("max")?.toFloat() ?: 1f
    val max = if (maxRaw > min) maxRaw else min + 1f
    val value = (node.num("value")?.toFloat() ?: min).coerceIn(min, max)
    val label = node.str("label")
    Column(modifier.fillMaxWidth().then(node.padModifier())) {
        if (!label.isNullOrBlank()) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = label,
                    style = TextStyle(color = TextTheme.Body, fontSize = 15.sp),
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = sliderValueLabel(value),
                    style = TextStyle(color = TextTheme.Meta, fontSize = 13.sp),
                )
            }
        }
        MainSlider(
            value = value,
            onValueChange = { next ->
                val stepped = node.num("step")?.toFloat()?.takeIf { it > 0f }?.let { step ->
                    val n = ((next - min) / step).toInt()
                    (min + n * step).coerceIn(min, max)
                } ?: next
                node.id?.let { onChange(it, jsonNumber(stepped)) }
            },
            modifier = Modifier.fillMaxWidth(),
            valueRange = min..max,
        )
    }
}

@Composable
private fun PluginTreeField(
    node: PluginUiNode,
    onChange: (String, Any?) -> Unit,
    modifier: Modifier,
) {
    val multiline = node.bool("multiline")
    Column(
        modifier.fillMaxWidth().then(node.padModifier()),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        node.str("label")?.takeIf { it.isNotBlank() }?.let { label ->
            Text(
                text = label,
                style = TextStyle(color = TextTheme.Body, fontSize = 15.sp),
            )
        }
        GlassPromptField(
            value = node.str("value").orEmpty(),
            onValueChange = { next ->
                node.id?.let { onChange(it, next) }
            },
            placeholder = node.str("placeholder").orEmpty(),
            modifier = if (multiline) Modifier.heightIn(min = 88.dp) else Modifier,
            maxLength = 2048,
            singleLine = !multiline,
            minLines = if (multiline) 3 else 1,
        )
    }
}

@Composable
private fun PluginTreeSegmented(
    node: PluginUiNode,
    onChange: (String, Any?) -> Unit,
    modifier: Modifier,
) {
    val options = node.children.filter { it.type == "option" && it.id != null }
    if (options.isEmpty()) return
    val selected = node.str("value") ?: options.first().id
    Row(
        modifier
            .fillMaxWidth()
            .wallpaperItemChrome(RoundedCornerShape(14.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { option ->
            val id = option.id ?: return@forEach
            val on = id == selected
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (on) MainPalette.Accent.copy(alpha = 0.16f) else MainPalette.Card.copy(alpha = 0f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { node.id?.let { onChange(it, id) } },
                    )
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = option.str("label").orEmpty(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(
                        color = if (on) TextTheme.Accent else TextTheme.Body,
                        fontSize = 13.sp,
                        fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium,
                    ),
                )
            }
        }
    }
}

@Composable
private fun PluginTreeItem(
    node: PluginUiNode,
    onPress: (String) -> Unit,
    modifier: Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .wallpaperItemChrome(RoundedCornerShape(14.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { node.id?.let(onPress) },
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = node.str("title").orEmpty(),
                style = TextStyle(
                    color = TextTheme.Body,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            node.str("subtitle")?.takeIf { it.isNotBlank() }?.let { sub ->
                Text(
                    text = sub,
                    style = TextStyle(color = TextTheme.Hint, fontSize = 12.sp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        node.str("trailing")?.takeIf { it.isNotBlank() }?.let { trailing ->
            Spacer(Modifier.width(8.dp))
            Text(
                text = trailing,
                style = TextStyle(color = TextTheme.Meta, fontSize = 12.sp),
            )
        }
    }
}

@Composable
private fun PluginTreeTrack(
    pluginId: String,
    node: PluginUiNode,
    onPress: (String) -> Unit,
    modifier: Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .wallpaperItemChrome(RoundedCornerShape(14.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { node.id?.let(onPress) },
            )
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PluginTreeImage(
            pluginId = pluginId,
            src = node.str("coverUrl").orEmpty(),
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp)),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = node.str("title").orEmpty(),
                style = TextStyle(
                    color = TextTheme.Body,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            node.str("subtitle")?.takeIf { it.isNotBlank() }?.let { sub ->
                Text(
                    text = sub,
                    style = TextStyle(color = TextTheme.Hint, fontSize = 12.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        node.num("durationMs")?.let { ms ->
            Spacer(Modifier.width(8.dp))
            Text(
                text = formatDurationMs(ms),
                style = TextStyle(color = TextTheme.Meta, fontSize = 12.sp),
            )
        }
    }
}

@Composable
private fun PluginTreeImage(
    pluginId: String,
    src: String,
    modifier: Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val trimmed = src.trim()
    if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
        UrlImage(
            url = trimmed,
            contentDescription = null,
            modifier = modifier,
            contentScale = contentScale,
        )
        return
    }
    val app = LocalContext.current.applicationContext as ZMusicApplication
    val file = remember(pluginId, trimmed) {
        trimmed.takeIf { it.isNotEmpty() }?.let { app.pluginEngine.resolvePackFile(pluginId, it) }
    }
    var bitmap by remember(file?.absolutePath) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(file?.absolutePath) {
        val path = file ?: run {
            bitmap = null
            return@LaunchedEffect
        }
        bitmap = withContext(Dispatchers.IO) {
            runCatching { UrlImageCache.decodeSampledBitmap(path.readBytes()) }.getOrNull()
        }
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        val shown = bitmap
        if (shown != null) {
            Image(
                bitmap = shown,
                contentDescription = null,
                modifier = Modifier.matchParentSize(),
                contentScale = contentScale,
            )
        } else {
            Box(
                Modifier
                    .matchParentSize()
                    .background(MainPalette.Placeholder),
            )
        }
    }
}

private fun textStyle(style: String?): TextStyle = when (style) {
    "title" -> TextStyle(
        color = TextTheme.Title,
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
    )
    "subtitle" -> TextStyle(
        color = TextTheme.Subtitle,
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
    )
    "meta" -> TextStyle(
        color = TextTheme.Meta,
        fontSize = 12.sp,
    )
    "hint" -> TextStyle(
        color = TextTheme.Hint,
        fontSize = 13.sp,
    )
    else -> TextStyle(
        color = TextTheme.Body,
        fontSize = 15.sp,
        lineHeight = 22.sp,
    )
}

private fun PluginUiNode.gapDp(default: Int = 10): Dp =
    (num("gap")?.toInt()?.coerceIn(0, 48) ?: default).dp

private fun PluginUiNode.padModifier(): Modifier {
    val all = num("pad")?.toInt()?.coerceIn(0, 48)
    val h = num("padH")?.toInt()?.coerceIn(0, 48) ?: all
    val v = num("padV")?.toInt()?.coerceIn(0, 48) ?: all
    if (h == null && v == null) return Modifier
    return Modifier.padding(horizontal = (h ?: 0).dp, vertical = (v ?: 0).dp)
}

private fun PluginUiNode.horizontalAlign(): Alignment.Horizontal = when (str("align")) {
    "center" -> Alignment.CenterHorizontally
    "end" -> Alignment.End
    else -> Alignment.Start
}

private fun PluginUiNode.verticalAlign(): Alignment.Vertical = when (str("align")) {
    "start" -> Alignment.Top
    "end" -> Alignment.Bottom
    else -> Alignment.CenterVertically
}

private fun PluginUiNode.textAlign(): TextAlign = when (str("align")) {
    "center" -> TextAlign.Center
    "end" -> TextAlign.End
    else -> TextAlign.Start
}

private fun PluginUiNode.fontSizeSp() = num("size")?.toInt()?.coerceIn(10, 32)?.sp

private fun PluginUiNode.fontWeight(): FontWeight? = when (str("weight")) {
    "regular" -> FontWeight.Normal
    "medium" -> FontWeight.Medium
    "semibold" -> FontWeight.SemiBold
    "bold" -> FontWeight.Bold
    else -> null
}

private fun PluginUiNode.resolveColor(fallback: Color): Color {
    val raw = str("color")?.trim() ?: return fallback
    if (raw.startsWith("#")) return parseThemeColor(raw) ?: fallback
    return TextTheme.namedColor(raw) ?: fallback
}

private fun sliderValueLabel(value: Float): String {
    val asInt = value.toInt()
    return if (asInt.toFloat() == value) asInt.toString() else "%.1f".format(value)
}

private fun jsonNumber(value: Float): Any {
    val asInt = value.toInt()
    return if (asInt.toFloat() == value) asInt else value.toDouble()
}

private fun formatDurationMs(ms: Double): String {
    val total = (ms / 1000.0).toInt().coerceAtLeast(0)
    val m = total / 60
    val s = total % 60
    return "%d:%02d".format(m, s)
}
