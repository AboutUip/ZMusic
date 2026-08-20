package com.kite.zmusic.ui.player

import androidx.compose.ui.graphics.Color
import com.kite.zmusic.data.LyricRoleStyle
import com.kite.zmusic.data.PlayerDisplayPrefs
import com.kite.zmusic.data.TitleLineStyle

fun LyricRoleStyle.resolvedColor(defaultArgb: Int): Color = Color(resolvedArgb(defaultArgb))

fun TitleLineStyle.resolvedColor(defaultArgb: Int): Color = Color(resolvedArgb(defaultArgb))

fun PlayerDisplayPrefs.lyricPlayingColor(): Color =
    lyricPlayingStyle.resolvedColor(LyricRoleStyle.DEFAULT_PLAYING_ARGB)

fun PlayerDisplayPrefs.lyricPlayedColor(): Color =
    lyricPlayedStyle.resolvedColor(LyricRoleStyle.DEFAULT_PLAYED_ARGB)

fun PlayerDisplayPrefs.lyricUnplayedColor(): Color =
    lyricUnplayedStyle.resolvedColor(LyricRoleStyle.DEFAULT_UNPLAYED_ARGB)

fun PlayerDisplayPrefs.titleNameColor(): Color =
    titleNameStyle.resolvedColor(TitleLineStyle.DEFAULT_NAME_ARGB)

fun PlayerDisplayPrefs.titleArtistColor(): Color =
    titleArtistStyle.resolvedColor(TitleLineStyle.DEFAULT_ARTIST_ARGB)

fun PlayerDisplayPrefs.titleSourceColor(): Color =
    titleSourceStyle.resolvedColor(TitleLineStyle.DEFAULT_SOURCE_ARGB)
