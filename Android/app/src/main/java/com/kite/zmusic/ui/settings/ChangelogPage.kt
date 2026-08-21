package com.kite.zmusic.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kite.zmusic.data.ChangelogDocument
import com.kite.zmusic.data.ChangelogEntry
import com.kite.zmusic.data.ChangelogItem
import com.kite.zmusic.data.ChangelogItemType
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

private val KindTestColor = Color(0xFFC9A227)
private val KindReleaseColor = Color(0xFF3D9B6E)
private val TypeAddColor = Color(0xFF8B5CF6)
private val TypeSupportColor = Color(0xFF3478F6)
private val TypeImproveColor = Color(0xFF2A9D8F)
private val TypeFixColor = Color(0xFFE85D75)

@Composable
fun ChangelogPage(
    contentBottomInset: Dp,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var document by remember { mutableStateOf<ChangelogDocument?>(null) }
    var query by remember { mutableStateOf("") }
    var expandedVersion by remember { mutableStateOf<String?>(null) }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        document = ChangelogRoster.load(context)
    }
    val doc = document
    val filtered = remember(doc, query) {
        ChangelogRoster.filter(doc?.entries.orEmpty(), query)
    }
    LaunchedEffect(filtered, expandedVersion) {
        if (expandedVersion != null && filtered.none { it.version == expandedVersion }) {
            expandedVersion = null
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
            text = "点开版本查看结构化预览。可用版本号搜索。",
            style = TextStyle(
                color = MainPalette.Secondary,
                fontSize = 13.sp,
                lineHeight = 18.sp,
            ),
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Spacer(Modifier.height(14.dp))
        ChangelogSearchField(
            value = query,
            onValueChange = { query = it },
            onDone = { keyboard?.hide() },
        )
        Spacer(Modifier.height(16.dp))
        when {
            doc == null -> { }
            filtered.isEmpty() -> {
                Text(
                    text = if (query.isBlank()) "暂时还没有记录。" else "没有匹配的版本。",
                    style = TextStyle(
                        color = MainPalette.Hint,
                        fontSize = 14.sp,
                    ),
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                )
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = contentBottomInset + 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(filtered, key = { it.version }) { entry ->
                        ChangelogVersionCard(
                            title = doc.title,
                            entry = entry,
                            expanded = expandedVersion == entry.version,
                            onToggle = {
                                expandedVersion =
                                    if (expandedVersion == entry.version) null else entry.version
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChangelogSearchField(
    value: String,
    onValueChange: (String) -> Unit,
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
            onValueChange = { onValueChange(it.take(24)) },
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
                            text = "搜索版本，例如 1.2.2",
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
        KindTestColor
    } else {
        KindReleaseColor
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

@Composable
private fun ChangelogPreviewBody(
    title: String,
    entry: ChangelogEntry,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
    ) {
        ChangelogHairline()
        Spacer(Modifier.height(12.dp))
        Text(
            text = title,
            style = TextStyle(
                color = MainPalette.Ink,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.4.sp,
            ),
        )
        Spacer(Modifier.height(10.dp))
        ChangelogHairline()
        Spacer(Modifier.height(10.dp))
        ChangelogMetaRow(label = "版本", value = entry.versionLabel)
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "性质",
                style = TextStyle(
                    color = MainPalette.Secondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                ),
                modifier = Modifier.width(40.dp),
            )
            ChangelogChip(
                label = entry.kind,
                color = if (entry.kind.equals("Test", ignoreCase = true)) {
                    KindTestColor
                } else {
                    KindReleaseColor
                },
            )
        }
        if (entry.notice.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Column(Modifier.fillMaxWidth()) {
                Text(
                    text = "声明",
                    style = TextStyle(
                        color = MainPalette.Secondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = entry.notice,
                    style = TextStyle(
                        color = MainPalette.Ink.copy(alpha = 0.78f),
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                    ),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        ChangelogHairline()
        Spacer(Modifier.height(10.dp))
        entry.items.forEachIndexed { index, item ->
            if (index > 0) Spacer(Modifier.height(8.dp))
            ChangelogChangeRow(item)
        }
    }
}

@Composable
private fun ChangelogMetaRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = TextStyle(
                color = MainPalette.Secondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            ),
            modifier = Modifier.width(40.dp),
        )
        Text(
            text = value,
            style = TextStyle(
                color = MainPalette.Ink,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            ),
        )
    }
}

@Composable
private fun ChangelogChangeRow(item: ChangelogItem) {
    val (label, color) = when (item.type) {
        ChangelogItemType.Add -> "新增" to TypeAddColor
        ChangelogItemType.Support -> "支持" to TypeSupportColor
        ChangelogItemType.Improve -> "优化" to TypeImproveColor
        ChangelogItemType.Fix -> "修复" to TypeFixColor
    }
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        ChangelogChip(label = label, color = color)
        Spacer(Modifier.width(8.dp))
        Text(
            text = item.text,
            style = TextStyle(
                color = MainPalette.Ink,
                fontSize = 13.sp,
                lineHeight = 20.sp,
            ),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ChangelogChip(
    label: String,
    color: Color,
) {
    Text(
        text = label,
        style = TextStyle(
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.3.sp,
        ),
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
private fun ChangelogHairline() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(0.5.dp)
            .background(MainPalette.Hairline),
    )
}
