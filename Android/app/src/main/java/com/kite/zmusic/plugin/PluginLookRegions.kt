package com.kite.zmusic.plugin

/**
 * 宿主具名外观区。插件用 [Xuan.look] 覆盖非危险个性化（壁纸、玻璃、浅深色、播放页氛围）。
 * 未知名使 `set` 失败。
 */
object PluginLookRegions {
    const val APPEARANCE = "appearance"
    const val CHROME_GLASS = "chrome.glass"
    const val CHROME_WALLPAPER = "chrome.wallpaper"
    const val PLAYER_VINYL = "player.vinyl"
    const val PLAYER_ATMOSPHERE = "player.atmosphere"
    const val PLAYER_BACKGROUND = "player.background"
    const val LIBRARY_PROFILE = "library.profile"

    val KNOWN: Set<String> = setOf(
        APPEARANCE,
        CHROME_GLASS,
        CHROME_WALLPAPER,
        PLAYER_VINYL,
        PLAYER_ATMOSPHERE,
        PLAYER_BACKGROUND,
        LIBRARY_PROFILE,
    )
}
