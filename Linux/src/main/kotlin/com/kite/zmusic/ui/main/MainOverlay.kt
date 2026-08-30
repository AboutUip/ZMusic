package com.kite.zmusic.ui.main

sealed class MainOverlay {
    data object Daily : MainOverlay()
    data object Fm : MainOverlay()
    data object Charts : MainOverlay()
    data object CachedSongs : MainOverlay()
    data object Search : MainOverlay()
    data object Settings : MainOverlay()
    data class Playlist(val id: Long, val title: String, val coverUrl: String? = null) : MainOverlay()
    data class Album(val id: Long, val title: String) : MainOverlay()
    data class Mv(val id: Long, val title: String, val coverUrl: String? = null) : MainOverlay()
    data class Artist(val id: Long, val title: String, val coverUrl: String? = null) : MainOverlay()
    data class User(val id: Long, val title: String, val coverUrl: String? = null) : MainOverlay()
    data object LikedArtists : MainOverlay()
}

class OverlayStack {
    private val items = mutableListOf<MainOverlay>()

    fun snapshot(): List<MainOverlay> = items.toList()

    fun isEmpty(): Boolean = items.isEmpty()

    fun isNotEmpty(): Boolean = items.isNotEmpty()

    fun top(): MainOverlay? = items.lastOrNull()

    fun push(overlay: MainOverlay) {
        if (items.lastOrNull() == overlay) return
        items.add(overlay)
    }

    fun pop(): MainOverlay? {
        if (items.isEmpty()) return null
        return items.removeAt(items.lastIndex)
    }

    fun clear() {
        items.clear()
    }

    fun replaceTop(overlay: MainOverlay) {
        if (items.isEmpty()) {
            items.add(overlay)
        } else {
            items[items.lastIndex] = overlay
        }
    }
}

enum class MainDestination(val titleZh: String) {
    Home("主页"),
    Features("功能"),
    Profile("个人"),
}
