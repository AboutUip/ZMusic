package com.kite.zmusic.ui.player

import com.kite.zmusic.data.DanmakuRegion
import com.kite.zmusic.data.SongComment
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DanmakuPlaylistTest {
    private fun comment(id: Long, text: String) = SongComment(
        commentId = id,
        content = text,
        timeMs = id,
        timeLabel = "",
        likedCount = 1,
        replyCount = 0,
        userId = id,
        nickname = "u$id",
        avatarUrl = null,
        repliedContent = null,
        repliedNickname = null,
    )

    @Test
    fun dropsBlankAndMultiline() {
        assertNull(DanmakuPlaylist.singleLineContent(""))
        assertNull(DanmakuPlaylist.singleLineContent("  \n"))
        assertNull(DanmakuPlaylist.singleLineContent("第一行\n第二行"))
        assertEquals("一句话", DanmakuPlaylist.singleLineContent("  一句话  "))
    }

    @Test
    fun shuffleKeepsGroupMembership() {
        val src = (1..45).toList()
        val out = DanmakuPlaylist.shuffleGroups(src, 15)
        assertEquals(src.toSet(), out.toSet())
        assertEquals(45, out.size)
        val first = out.take(15).toSet()
        assertEquals((1..15).toSet(), first)
        val second = out.drop(15).take(15).toSet()
        assertEquals((16..30).toSet(), second)
    }

    @Test
    fun feedSkipsDuplicatesAndRefills() {
        val feed = DanmakuFeed()
        val page = (1L..15L).map { comment(it, "评$it") }
        assertEquals(15, feed.ingest(page, pageHasMore = false, fetchedPageNo = 1))
        assertEquals(0, feed.ingest(page, pageHasMore = false, fetchedPageNo = 1))
        val firstPass = buildList {
            repeat(15) { add(feed.next()?.commentId) }
        }
        assertEquals(15, firstPass.filterNotNull().size)
        assertTrue(feed.next() != null)
    }

    @Test
    fun bandYKeepsRowInsideRegion() {
        val h = 1000f
        val row = 32f
        val pad = 10f
        DanmakuRegion.entries.forEach { region ->
            val band = DanmakuPlaylist.bandY(region, h, row, pad)
            assertTrue(band.start >= pad - 0.01f)
            assertTrue(band.endInclusive + row <= h - pad + 0.01f)
            assertTrue(band.endInclusive >= band.start)
        }
        val top = DanmakuPlaylist.bandY(DanmakuRegion.TOP, h, row, pad)
        assertTrue(top.endInclusive <= h * 0.25f)
        val lower = DanmakuPlaylist.bandY(DanmakuRegion.LOWER, h, row, pad)
        assertTrue(lower.start >= h * 0.45f)
    }

    @Test
    fun pickYRespectsVerticalGap() {
        val band = 0f..400f
        val others = listOf(
            DanmakuFlightProbe(x = 900f, y = 100f, widthPx = 200f, vGap = 20f, hGap = 60f),
        )
        val y = DanmakuPlaylist.pickY(
            band = band,
            rowH = 32f,
            spawnX = 1000f,
            spawnWidth = 180f,
            others = others,
            random = Random(1),
            attempts = 40,
        )
        assertTrue(y != null)
        assertTrue(kotlin.math.abs(y!! - 100f) >= 32f)
    }
}
