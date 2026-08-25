package com.kite.zmusic.ui.plugin

import androidx.compose.ui.graphics.vector.ImageVector
import com.kite.zmusic.ui.icons.ZIcons

internal fun pluginUiIcon(name: String?): ImageVector = when (name?.trim()?.lowercase()) {
    "home" -> ZIcons.Home
    "features" -> ZIcons.Features
    "profile" -> ZIcons.Profile
    "search" -> ZIcons.Search
    "settings" -> ZIcons.Settings
    "favorite" -> ZIcons.Favorite
    "timer", "moon" -> ZIcons.Timer
    "radio" -> ZIcons.Radio
    "daily" -> ZIcons.Daily
    "charts" -> ZIcons.Charts
    "folder" -> ZIcons.CachedSongs
    "workshop" -> ZIcons.Workshop
    "extension" -> ZIcons.Extension
    "music" -> ZIcons.MusicNote
    "playlist" -> ZIcons.Playlist
    "info" -> ZIcons.Info
    "add" -> ZIcons.Add
    "check" -> ZIcons.Check
    "history" -> ZIcons.History
    "headset" -> ZIcons.Headset
    else -> ZIcons.Extension
}
