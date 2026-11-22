package com.kite.zmusic.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecentPlayTest {
    @Test
    fun newestSongStaysInFrontAndReplaysReplaceTheOldCopy() {
        val first = recentPush(emptyList(), song(1, "A"), RecentSongCap) { a, b -> a.id == b.id }
        val second = recentPush(first, song(2, "B"), RecentSongCap) { a, b -> a.id == b.id }
        assertEquals(listOf(2L, 1L), second.map { it.id })
        val again = recentPush(second, song(1, "A2"), RecentSongCap) { a, b -> a.id == b.id }
        assertEquals(listOf(1L, 2L), again.map { it.id })
        assertEquals("A2", again.first().name)
    }

    @Test
    fun songsStopAtFiveHundredAndMvsStopAtOneHundred() {
        var songs = emptyList<RecentSong>()
        for (i in 1..RecentSongCap + 1) {
            songs = recentPush(songs, song(i.toLong(), "歌$i"), RecentSongCap) { a, b -> a.id == b.id }
        }
        assertEquals(RecentSongCap, songs.size)
        assertEquals((RecentSongCap + 1).toLong(), songs.first().id)
        assertFalse(songs.any { it.id == 1L })

        var mvs = emptyList<RecentMv>()
        for (i in 1..RecentMvCap + 1) {
            mvs = recentPush(mvs, mv(i.toLong(), "片$i"), RecentMvCap) { a, b -> a.id == b.id }
        }
        assertEquals(RecentMvCap, mvs.size)
        assertEquals((RecentMvCap + 1).toLong(), mvs.first().id)
    }

    @Test
    fun diskMergeKeepsSongsRecordedBeforeTheFileReturns() {
        val merged = recentMergeFront(
            fresh = listOf(song(2, "新")),
            stored = listOf(song(1, "旧"), song(2, "过期"), song(3, "更旧")),
            cap = RecentSongCap,
            id = RecentSong::id,
        )
        assertEquals(listOf(2L, 1L, 3L), merged.map { it.id })
        assertEquals("新", merged.first().name)
    }

    @Test
    fun browsingLoadsOnePageWhileSearchScansTheWholeFile() {
        val all = (1..500).map { index ->
            song(index.toLong(), name = if (index == 500) "稀有曲子" else "日常$index")
        }
        val browsing = recentSlice(all, query = "", loaded = RecentPageSize, matches = ::recentSongMatches)
        assertEquals(RecentPageSize, browsing.shown.size)
        assertEquals(500, browsing.playable.size)
        assertTrue(browsing.hasMore)
        assertEquals(1L, browsing.shown.first().id)

        val searching = recentSlice(all, query = "稀有", loaded = RecentPageSize, matches = ::recentSongMatches)
        assertEquals(listOf(500L), searching.shown.map { it.id })
        assertEquals(1, searching.playable.size)
        assertFalse(searching.hasMore)
    }

    @Test
    fun searchHitsStillPageAfterTheFullScan() {
        val all = (1..80).map { song(it.toLong(), name = "同名") }
        val first = recentSlice(all, query = "同名", loaded = RecentPageSize, matches = ::recentSongMatches)
        assertEquals(RecentPageSize, first.shown.size)
        assertEquals(80, first.playable.size)
        assertTrue(first.hasMore)
        val rest = recentSlice(all, query = "同名", loaded = RecentPageSize * 2, matches = ::recentSongMatches)
        assertEquals(80, rest.shown.size)
        assertFalse(rest.hasMore)
    }

    private fun song(id: Long, name: String) = RecentSong(
        id = id,
        name = name,
        artists = "",
        album = null,
        durationMs = 0L,
        coverUrl = null,
        sourceId = null,
        sourceHash = null,
        albumMid = null,
        sourceSongId = 0L,
        fee = 0,
        localAudioUri = null,
        localFolder = null,
        localLyricUri = null,
        localTransLyricUri = null,
        artistRefs = emptyList(),
        playedAt = id,
    )

    private fun mv(id: Long, name: String) = RecentMv(
        id = id,
        name = name,
        coverUrl = null,
        artist = null,
        playedAt = id,
    )
}
