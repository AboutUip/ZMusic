package com.kite.zmusic.plugin

/**
 * 宿主具名陈列区。插件用 [Xuan.ui.collection] 改流式布置与封面壳。
 * 未知名使 `set` 失败。
 */
object PluginCollections {
    const val LIBRARY_LIKED = "library.liked"
    const val LIBRARY_CREATED = "library.created"
    const val LIBRARY_COLLECTED_PLAYLISTS = "library.collected.playlists"
    const val LIBRARY_COLLECTED_ALBUMS = "library.collected.albums"

    val KNOWN: Set<String> = setOf(
        LIBRARY_LIKED,
        LIBRARY_CREATED,
        LIBRARY_COLLECTED_PLAYLISTS,
        LIBRARY_COLLECTED_ALBUMS,
    )

    val LIST_REGIONS: Set<String> = setOf(
        LIBRARY_LIKED,
        LIBRARY_CREATED,
        LIBRARY_COLLECTED_PLAYLISTS,
    )
}
