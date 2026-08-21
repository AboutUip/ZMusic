package com.kite.zmusic.ui.chrome

/**
 * 有构图的页始终挂着壁纸层。当前页透明度为 0，透出壳层给 Dock / 横屏栏采样。
 * 切走时只把透明度拉到 1，不要卸层再解码：解码前会铺主题黑底，上一页整页闪黑。
 */
internal fun pagerPageKeepsOwnWallpaperLayer(): Boolean = true

internal fun pagerPageShowsOwnWallpaper(
    pageIndex: Int,
    currentPage: Int,
): Boolean = pageIndex != currentPage

/** 路径切换时继续画上一张，直到新图就绪，避免壳层先铺一层黑底。 */
internal fun <T> wallpaperHoldUntilReady(ready: T?, previous: T?): T? = ready ?: previous
