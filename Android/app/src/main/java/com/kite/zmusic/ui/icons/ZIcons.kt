package com.kite.zmusic.ui.icons

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.LibraryAdd
import androidx.compose.material.icons.rounded.LibraryAddCheck
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.HideImage
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Forward5
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Replay5
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.ui.graphics.vector.ImageVector
import com.kite.zmusic.ui.main.MainDestination

/**
 * 矢量图标（Material Icons Rounded，源路径为 SVG）。
 * 主界面按钮不再用文字首字或手绘占位。
 */
object ZIcons {
    val Home: ImageVector get() = Icons.Rounded.Home
    val Features: ImageVector get() = Icons.Rounded.GridView
    val Profile: ImageVector get() = Icons.Rounded.Person
    val Search: ImageVector get() = Icons.Rounded.Search
    val Settings: ImageVector get() = Icons.Rounded.Settings
    val GraphicEq: ImageVector get() = Icons.Rounded.GraphicEq
    val Security: ImageVector get() = Icons.Rounded.Security
    val Notifications: ImageVector get() = Icons.Rounded.Notifications
    val Camera: ImageVector get() = Icons.Rounded.PhotoCamera
    val Battery: ImageVector get() = Icons.Rounded.BatteryChargingFull
    val BlurOn: ImageVector get() = Icons.Rounded.BlurOn
    val Info: ImageVector get() = Icons.Rounded.Info
    val Favorite: ImageVector get() = Icons.Rounded.Favorite
    val More: ImageVector get() = Icons.Rounded.MoreVert
    val Check: ImageVector get() = Icons.Rounded.Check
    val Manage: ImageVector get() = Icons.Rounded.Checklist
    val Close: ImageVector get() = Icons.Rounded.Close
    val History: ImageVector get() = Icons.Rounded.History
    val Play: ImageVector get() = Icons.Rounded.PlayArrow
    val Pause: ImageVector get() = Icons.Rounded.Pause
    val Replay5: ImageVector get() = Icons.Rounded.Replay5
    val Forward5: ImageVector get() = Icons.Rounded.Forward5
    val Back: ImageVector get() = Icons.AutoMirrored.Rounded.ArrowBack
    val Daily: ImageVector get() = Icons.Rounded.CalendarMonth
    val Radio: ImageVector get() = Icons.Rounded.Radio
    val Charts: ImageVector get() = Icons.Rounded.EmojiEvents
    val Playlist: ImageVector get() = Icons.AutoMirrored.Rounded.QueueMusic
    val Add: ImageVector get() = Icons.Rounded.Add
    val Server: ImageVector get() = Icons.Rounded.Dns
    val Logout: ImageVector get() = Icons.AutoMirrored.Rounded.Logout
    val SkipNext: ImageVector get() = Icons.Rounded.SkipNext
    val CollectPlaylist: ImageVector get() = Icons.Rounded.LibraryAdd
    val CollectedPlaylist: ImageVector get() = Icons.Rounded.LibraryAddCheck
    val Vip: ImageVector get() = Icons.Rounded.WorkspacePremium
    val MusicNote: ImageVector get() = Icons.Rounded.MusicNote
    val Artist: ImageVector get() = Icons.Rounded.Mic
    val ChevronLeft: ImageVector get() = Icons.Rounded.ChevronLeft
    val ChevronRight: ImageVector get() = Icons.Rounded.ChevronRight
    val Wallpaper: ImageVector get() = Icons.Rounded.Wallpaper
    val HideImage: ImageVector get() = Icons.Rounded.HideImage
    val RelatedMv: ImageVector get() = Icons.Rounded.VideoLibrary
    val Fullscreen: ImageVector get() = Icons.Rounded.Fullscreen
    val FullscreenExit: ImageVector get() = Icons.Rounded.FullscreenExit

    fun dock(destination: MainDestination): ImageVector = when (destination) {
        MainDestination.Home -> Home
        MainDestination.Features -> Features
        MainDestination.Profile -> Profile
    }
}
