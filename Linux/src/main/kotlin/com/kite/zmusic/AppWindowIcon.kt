package com.kite.zmusic

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toComposeImageBitmap
import java.awt.Image
import java.awt.Taskbar
import java.awt.Window
import javax.imageio.ImageIO
import org.jetbrains.skia.Image as SkiaImage

internal const val AppLogoClasspath = "/drawable/ic_logo_vinyl_z.png"

fun loadAppAwtIcons(): List<Image> {
    val stream = object {}.javaClass.getResourceAsStream(AppLogoClasspath) ?: return emptyList()
    val image = stream.use { ImageIO.read(it) } ?: return emptyList()
    return listOf(image)
}

fun applyAppWindowIcon(window: Window) {
    val icons = loadAppAwtIcons()
    if (icons.isEmpty()) return
    window.iconImages = icons
    runCatching {
        if (!Taskbar.isTaskbarSupported()) return@runCatching
        val bar = Taskbar.getTaskbar()
        if (bar.isSupported(Taskbar.Feature.ICON_IMAGE)) {
            bar.iconImage = icons.first()
        }
    }
}

@Composable
fun rememberAppLogoPainter(): Painter {
    val bitmap = remember {
        val stream = object {}.javaClass.getResourceAsStream(AppLogoClasspath)
        if (stream == null) {
            ImageBitmap(1, 1)
        } else {
            stream.use { SkiaImage.makeFromEncoded(it.readBytes()).toComposeImageBitmap() }
        }
    }
    return remember(bitmap) { BitmapPainter(bitmap) }
}
