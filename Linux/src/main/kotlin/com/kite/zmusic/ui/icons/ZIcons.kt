package com.kite.zmusic.ui.icons

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.Business
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Handshake
import androidx.compose.material.icons.rounded.Headset
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Opacity
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.VolunteerActivism
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.ui.graphics.vector.ImageVector
import com.kite.zmusic.ui.main.MainDestination

/**
 * 矢量图标（Material Icons Rounded）。主界面按钮不再用文字首字或 Outlined 占位。
 */
object ZIcons {
    val Home: ImageVector get() = Icons.Rounded.Home
    val Features: ImageVector get() = Icons.Rounded.GridView
    val Profile: ImageVector get() = Icons.Rounded.Person
    val Search: ImageVector get() = Icons.Rounded.Search
    val Settings: ImageVector get() = Icons.Rounded.Settings
    val GraphicEq: ImageVector get() = Icons.Rounded.GraphicEq
    val BlurOn: ImageVector get() = Icons.Rounded.BlurOn
    val DarkMode: ImageVector get() = Icons.Rounded.DarkMode
    val Info: ImageVector get() = Icons.Rounded.Info
    val Lyrics: ImageVector get() = Icons.Rounded.Lyrics
    val Favorite: ImageVector get() = Icons.Rounded.Favorite
    val History: ImageVector get() = Icons.Rounded.History
    val Handshake: ImageVector get() = Icons.Rounded.Handshake
    val Play: ImageVector get() = Icons.Rounded.PlayArrow
    val Pause: ImageVector get() = Icons.Rounded.Pause
    val Back: ImageVector get() = Icons.AutoMirrored.Rounded.ArrowBack
    val Daily: ImageVector get() = Icons.Rounded.CalendarMonth
    val Radio: ImageVector get() = Icons.Rounded.Radio
    val Charts: ImageVector get() = Icons.Rounded.EmojiEvents
    val CachedSongs: ImageVector get() = Icons.Rounded.Folder
    val Speed: ImageVector get() = Icons.Rounded.Speed
    val Storage: ImageVector get() = Icons.Rounded.Storage
    val Playlist: ImageVector get() = Icons.AutoMirrored.Rounded.QueueMusic
    val Server: ImageVector get() = Icons.Rounded.Dns
    val Cloud: ImageVector get() = Icons.Rounded.Cloud
    val Logout: ImageVector get() = Icons.AutoMirrored.Rounded.Logout
    val SkipNext: ImageVector get() = Icons.Rounded.SkipNext
    val SkipPrevious: ImageVector get() = Icons.Rounded.SkipPrevious
    val Translate: ImageVector get() = Icons.Rounded.Translate
    val Wallpaper: ImageVector get() = Icons.Rounded.Wallpaper
    val Sponsor: ImageVector get() = Icons.Rounded.VolunteerActivism
    val Partners: ImageVector get() = Icons.Rounded.Business
    val Legal: ImageVector get() = Icons.Rounded.Description
    val MusicNote: ImageVector get() = Icons.Rounded.MusicNote
    val Glass: ImageVector get() = Icons.Rounded.Opacity
    val Headset: ImageVector get() = Icons.Rounded.Headset
    val Fullscreen: ImageVector get() = Icons.Rounded.Fullscreen
    val FullscreenExit: ImageVector get() = Icons.Rounded.FullscreenExit
    val Repeat: ImageVector get() = Icons.Rounded.Repeat
    val RepeatOne: ImageVector get() = Icons.Rounded.RepeatOne
    val Shuffle: ImageVector get() = Icons.Rounded.Shuffle
    val Comments: ImageVector get() = Icons.Outlined.ChatBubbleOutline
    val FavoriteBorder: ImageVector get() = Icons.Outlined.FavoriteBorder
    val Chevron: ImageVector get() = Icons.AutoMirrored.Rounded.KeyboardArrowRight

    fun dock(destination: MainDestination): ImageVector = when (destination) {
        MainDestination.Home -> Home
        MainDestination.Features -> Features
        MainDestination.Profile -> Profile
    }
}
