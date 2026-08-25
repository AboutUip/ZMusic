package com.kite.zmusic.plugin

/**
 * 宿主具名表面。手势钩子与 [Xuan.ui.action] 共用这些名字。
 * 未知名在 `action.set` 时失败；钩子仍可自定义其它 `name`。
 */
object PluginSurfaces {
    const val PLAYER_COVER = "player.cover"
    const val MINIPLAYER_COVER = "miniplayer.cover"
    const val ALBUM_COVER = "album.cover"
    const val PLAYLIST_COVER = "playlist.cover"
    const val ARTIST_COVER = "artist.cover"
    const val TRACK_COVER = "track.cover"
    const val TRACK_OVERFLOW = "track.overflow"
    const val MV_COVER = "mv.cover"
    const val CHART_COVER = "chart.cover"
    const val USER_AVATAR = "user.avatar"
    const val HOME_BANNER = "home.banner"

    val KNOWN: Set<String> = setOf(
        PLAYER_COVER,
        MINIPLAYER_COVER,
        ALBUM_COVER,
        PLAYLIST_COVER,
        ARTIST_COVER,
        TRACK_COVER,
        TRACK_OVERFLOW,
        MV_COVER,
        CHART_COVER,
        USER_AVATAR,
        HOME_BANNER,
    )
}
