package com.kite.zmusic.listen

import com.kite.zmusic.data.TrackRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ListenQueueOrderTest {
    @Test
    fun sharedOrderKeepsCurrentSongAndPrefersLocalRows() {
        val local = listOf(row(1, "A"), row(2, "B-local"), row(3, "C"))
        val shared = listOf(row(3, "C-remote"), row(1, "A-remote"), row(3, "dup"))
        val merged = mergeSharedListenQueue(local, shared, currentId = 2)
        assertEquals(listOf(3L, 1L, 2L), merged.map { it.id })
        assertEquals("C", merged[0].name)
        assertEquals("A", merged[1].name)
        assertEquals("B-local", merged[2].name)
    }

    @Test
    fun incomingTrackStaysAtItsSharedSlot() {
        val ids = listOf(1L, 2L, 3L, 4L)
        assertEquals(3, listenSharedTrackIndex(ids, 4L))
        assertEquals(0, listenSharedTrackIndex(ids, 1L))
        assertEquals(-1, listenSharedTrackIndex(ids, 9L))
    }

    @Test
    fun postDropsDuplicatesAndCaps() {
        val tracks = (1..5).map { row(it.toLong(), "t$it") } + row(2, "again")
        val posted = listenQueueTracksForPost(tracks, cap = 3)
        assertEquals(listOf(1L, 2L, 3L), posted.map { it.id })
        val json = listenQueueOpJson(listOf(row(8, "夜曲")))
        assertEquals("queue", json?.getString("kind"))
        assertEquals(8L, json?.getJSONArray("tracks")?.getJSONObject(0)?.getLong("id"))
        assertNull(listenQueueOpJson(emptyList()))
    }

    private fun row(id: Long, name: String) = TrackRow(
        id = id,
        name = name,
        artists = "artist",
        album = null,
        durationMs = 1_000L,
        coverUrl = "cover-$id",
    )
}
