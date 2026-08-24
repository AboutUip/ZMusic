package com.kite.zmusic.ui.workshop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kite.zmusic.ui.main.MainPalette

/**
 * 插件 README 子集渲染（对齐 PACKAGE.md：标题 / 段落 / 列表 / 链接样式 / 行内与围栏代码）。
 * 不执行 HTML。
 */
@Composable
internal fun WorkshopReadmeMarkdown(
    source: String,
    modifier: Modifier = Modifier,
    maxBlocks: Int? = null,
) {
    val blocks = remember(source, maxBlocks) {
        val all = parseReadmeBlocks(normalizeReadmeSource(source))
        if (maxBlocks == null || all.size <= maxBlocks) all else all.take(maxBlocks)
    }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        blocks.forEach { block ->
            when (block) {
                is ReadmeBlock.Heading -> Text(
                    text = block.text,
                    style = TextStyle(
                        color = MainPalette.Ink,
                        fontSize = when (block.level) {
                            1 -> 20.sp
                            2 -> 17.sp
                            else -> 15.sp
                        },
                        fontWeight = FontWeight.Bold,
                        lineHeight = when (block.level) {
                            1 -> 28.sp
                            2 -> 24.sp
                            else -> 22.sp
                        },
                    ),
                )
                is ReadmeBlock.Paragraph -> {
                    val ann = remember(block.text) {
                        inlineMarkdown(block.text, MainPalette.Ink, MainPalette.Accent)
                    }
                    Text(
                        text = ann,
                        style = TextStyle(
                            color = MainPalette.Ink,
                            fontSize = 14.sp,
                            lineHeight = 22.sp,
                        ),
                    )
                }
                is ReadmeBlock.Bullet -> {
                    val ann = remember(block.text) {
                        inlineMarkdown(block.text, MainPalette.Ink, MainPalette.Accent)
                    }
                    Row {
                        Text(
                            "•",
                            style = TextStyle(
                                color = MainPalette.Ink,
                                fontSize = 14.sp,
                                lineHeight = 22.sp,
                            ),
                            modifier = Modifier.padding(end = 8.dp),
                        )
                        Text(
                            text = ann,
                            style = TextStyle(
                                color = MainPalette.Ink,
                                fontSize = 14.sp,
                                lineHeight = 22.sp,
                            ),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                is ReadmeBlock.CodeFence -> Text(
                    text = block.text,
                    style = TextStyle(
                        color = MainPalette.Ink,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        fontFamily = FontFamily.Monospace,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                )
            }
        }
    }
}

internal fun normalizeReadmeSource(raw: String): String {
    var s = raw.trim()
    if (s.isEmpty()) return s
    if (s.contains("\\n")) {
        s = s.replace("\\r\\n", "\n")
            .replace("\\n", "\n")
            .replace("\\t", "\t")
            .replace("\\r", "\n")
    }
    return s.replace("\r\n", "\n").replace('\r', '\n')
}

internal fun readmeBlockCount(source: String): Int =
    parseReadmeBlocks(normalizeReadmeSource(source)).size

private sealed class ReadmeBlock {
    data class Heading(val level: Int, val text: String) : ReadmeBlock()
    data class Paragraph(val text: String) : ReadmeBlock()
    data class Bullet(val text: String) : ReadmeBlock()
    data class CodeFence(val text: String) : ReadmeBlock()
}

private fun parseReadmeBlocks(src: String): List<ReadmeBlock> {
    if (src.isBlank()) return emptyList()
    val out = ArrayList<ReadmeBlock>()
    val lines = src.lines()
    var i = 0
    val para = StringBuilder()
    fun flushPara() {
        val t = para.toString().trim()
        if (t.isNotEmpty()) out += ReadmeBlock.Paragraph(t)
        para.setLength(0)
    }
    while (i < lines.size) {
        val line = lines[i]
        val trimmed = line.trim()
        when {
            trimmed.startsWith("```") -> {
                flushPara()
                i++
                val code = StringBuilder()
                while (i < lines.size && !lines[i].trim().startsWith("```")) {
                    if (code.isNotEmpty()) code.append('\n')
                    code.append(lines[i])
                    i++
                }
                out += ReadmeBlock.CodeFence(code.toString())
                if (i < lines.size) i++
            }
            trimmed.isEmpty() -> {
                flushPara()
                i++
            }
            headingOf(trimmed) != null -> {
                flushPara()
                val (level, text) = headingOf(trimmed)!!
                out += ReadmeBlock.Heading(level, text)
                i++
            }
            trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                flushPara()
                out += ReadmeBlock.Bullet(trimmed.drop(2).trim())
                i++
            }
            else -> {
                if (para.isNotEmpty()) para.append(' ')
                para.append(trimmed)
                i++
            }
        }
    }
    flushPara()
    return out
}

private fun headingOf(line: String): Pair<Int, String>? {
    val m = Regex("^(#{1,3})\\s+(.+)$").matchEntire(line) ?: return null
    return m.groupValues[1].length to m.groupValues[2].trim()
}

private fun inlineMarkdown(text: String, ink: Color, link: Color): AnnotatedString {
    return buildAnnotatedString {
        var i = 0
        while (i < text.length) {
            when {
                text.startsWith("`", i) -> {
                    val end = text.indexOf('`', i + 1)
                    if (end > i) {
                        withStyle(
                            SpanStyle(
                                fontFamily = FontFamily.Monospace,
                                background = ink.copy(alpha = 0.08f),
                            ),
                        ) {
                            append(text.substring(i + 1, end))
                        }
                        i = end + 1
                    } else {
                        append('`')
                        i++
                    }
                }
                text.startsWith("**", i) -> {
                    val end = text.indexOf("**", i + 2)
                    if (end > i) {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            append(text.substring(i + 2, end))
                        }
                        i = end + 2
                    } else {
                        append("**")
                        i += 2
                    }
                }
                text.startsWith("[", i) -> {
                    val mid = text.indexOf("](", i)
                    val end = if (mid > i) text.indexOf(')', mid + 2) else -1
                    if (mid > i && end > mid) {
                        val label = text.substring(i + 1, mid)
                        withStyle(
                            SpanStyle(color = link, textDecoration = TextDecoration.Underline),
                        ) {
                            append(label)
                        }
                        i = end + 1
                    } else {
                        append('[')
                        i++
                    }
                }
                else -> {
                    append(text[i])
                    i++
                }
            }
        }
    }
}
