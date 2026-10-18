package com.kite.zmusic.playback

import com.kite.zmusic.data.LrcLine
import com.kite.zmusic.data.LyricPack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricPlaybackAlignTest {
    @Test
    fun idlePlayerDoesNotClearLyrics() {
        assertEquals(0L, LyricPlaybackAlign.playingTrackId(null))
        assertEquals(0L, LyricPlaybackAlign.playingTrackId(""))
        assertEquals(0L, LyricPlaybackAlign.playingTrackId("not-a-song"))
        assertFalse(LyricPlaybackAlign.needsRepair(playingTrackId = 0L, lyricTrackId = 5L))
        assertFalse(
            LyricPlaybackAlign.shouldReplaceLyrics(
                playingTrackId = 0L,
                lyricTrackId = 5L,
                playingTrackKnown = true,
            ),
        )
    }

    @Test
    fun matchingSongKeepsItsLyrics() {
        assertEquals(42L, LyricPlaybackAlign.playingTrackId("42"))
        assertFalse(LyricPlaybackAlign.needsRepair(playingTrackId = 42L, lyricTrackId = 42L))
        assertFalse(
            LyricPlaybackAlign.shouldReplaceLyrics(
                playingTrackId = 42L,
                lyricTrackId = 42L,
                playingTrackKnown = true,
            ),
        )
    }

    @Test
    fun queueJumpAheadDoesNotReplaceUntilThePlayerCatchesUp() {
        assertTrue(LyricPlaybackAlign.needsRepair(playingTrackId = 5L, lyricTrackId = 9L))
        assertFalse(LyricPlaybackAlign.mayBindLyrics(playingTrackId = 5L, queueTrackId = 9L, lyricTargetId = 9L))
        assertTrue(LyricPlaybackAlign.mayBindLyrics(playingTrackId = 5L, queueTrackId = 9L, lyricTargetId = 5L))
    }

    @Test
    fun committedSwitchReplacesLyricsLeftOnThePreviousSong() {
        assertTrue(LyricPlaybackAlign.needsRepair(playingTrackId = 9L, lyricTrackId = 5L))
        assertTrue(
            LyricPlaybackAlign.shouldReplaceLyrics(
                playingTrackId = 9L,
                lyricTrackId = 5L,
                playingTrackKnown = true,
            ),
        )
        assertTrue(LyricPlaybackAlign.mayBindLyrics(playingTrackId = 9L, queueTrackId = 9L, lyricTargetId = 9L))
        assertFalse(LyricPlaybackAlign.mayBindLyrics(playingTrackId = 9L, queueTrackId = 9L, lyricTargetId = 5L))
    }

    @Test
    fun unknownPlayingSongDoesNotWipeLyrics() {
        assertTrue(LyricPlaybackAlign.needsRepair(playingTrackId = 9L, lyricTrackId = 5L))
        assertFalse(
            LyricPlaybackAlign.shouldReplaceLyrics(
                playingTrackId = 9L,
                lyricTrackId = 5L,
                playingTrackKnown = false,
            ),
        )
    }

    @Test
    fun coldStartMayBindTheQueuedSongBeforeThePlayerStarts() {
        assertTrue(LyricPlaybackAlign.mayBindLyrics(playingTrackId = 0L, queueTrackId = 5L, lyricTargetId = 5L))
        assertFalse(LyricPlaybackAlign.mayBindLyrics(playingTrackId = 0L, queueTrackId = 5L, lyricTargetId = 9L))
        assertFalse(LyricPlaybackAlign.mayBindLyrics(playingTrackId = 0L, queueTrackId = 5L, lyricTargetId = 0L))
    }

    @Test
    fun sameSongUsesTheProgressClock() {
        assertEquals(
            12_400L,
            LyricPlaybackAlign.lyricClockMs(
                positionMs = 12_400L,
                lyricPositionMs = 3_000L,
                lyricTrackId = 5L,
                queueTrackId = 5L,
            ),
        )
        assertEquals(
            12_400L,
            LyricPlaybackAlign.lyricClockMs(
                positionMs = 12_400L,
                lyricPositionMs = 3_000L,
                lyricTrackId = 0L,
                queueTrackId = 5L,
            ),
        )
    }

    @Test
    fun lyricHeldOnTheAudibleSongIgnoresTheNextSongsBar() {
        assertEquals(
            48_000L,
            LyricPlaybackAlign.lyricClockMs(
                positionMs = 0L,
                lyricPositionMs = 48_000L,
                lyricTrackId = 5L,
                queueTrackId = 9L,
            ),
        )
    }

    @Test
    fun packStampsItsSongWithoutMovingEitherClock() {
        val pack = LyricPack(original = listOf(LrcLine(timeMs = 0L, text = "一句")))
        val stamped = PlaybackUiState(positionMs = 12_000L, lyricPositionMs = 48_000L)
            .withLyricPack(pack, 9L)
        assertEquals(9L, stamped.lyricTrackId)
        assertEquals("一句", stamped.lyricLines.single().text)
        assertEquals(12_000L, stamped.positionMs)
        assertEquals(48_000L, stamped.lyricPositionMs)
        assertEquals(12_000L, stamped.lyricClockMs())
    }

    @Test
    fun clearingRemembersThePlayingSongAndDropsThePreviousLines() {
        val cleared = PlaybackUiState(
            lyricTrackId = 5L,
            lyricLines = listOf(LrcLine(timeMs = 1_000L, text = "上一首")),
            lyricPositionMs = 48_000L,
        ).clearLyricsFor(9L)
        assertTrue(cleared.lyricLines.isEmpty())
        assertEquals(9L, cleared.lyricTrackId)
        assertEquals(48_000L, cleared.lyricPositionMs)
    }
}
