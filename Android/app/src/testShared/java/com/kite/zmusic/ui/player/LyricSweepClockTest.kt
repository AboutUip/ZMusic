package com.kite.zmusic.ui.player

import com.kite.zmusic.data.LrcLine
import com.kite.zmusic.data.LyricWord
import com.kite.zmusic.data.lyricWordProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricSweepClockTest {

    @Test
    fun lineTransitionFinishesWhenTheNextLineStarts() {
        val lines = sampleLines()
        val timing = lyricAnimTiming(lines, 200L, 0L)
        assertTrue(timing.leadMs > 0L)
        assertEquals(timing.leadMs, timing.durationMs.toLong())
    }

    @Test
    fun sweepLeadMatchesTheLineSwitchLeadWhileThatLineIsCurrent() {
        val lines = sampleLines()
        val at = 200L
        val real = lyricActiveIndex(lines, at)
        assertEquals(0, real)
        assertEquals(
            lyricAnimTiming(lines, at, 0L).leadMs,
            lyricSweepLeadMs(lines, real, 0L),
        )
    }

    @Test
    fun lastWordIsFilledWhenTheLineSwitches() {
        val lines = sampleLines()
        val lead = lyricSweepLeadMs(lines, 0, 0L)
        val lineEnd = lyricLineEndMs(lines, 0, 0L)
        val switchAt = lineEnd - lead
        assertTrue(lead > 0L)
        assertEquals(1, lyricAnimActiveIndex(lines, switchAt, 0L))
        assertEquals(0, lyricAnimActiveIndex(lines, switchAt - 1, 0L))

        val last = lines[0].words.last()
        val raw = lyricWordProgress(last, switchAt)
        assertTrue("raw progress $raw", raw < 0.85f)
        assertEquals(1f, lyricSweepProgress(last, switchAt, lead), 0.001f)
        val earlier = lines[0].words.first()
        assertEquals(
            lyricWordProgress(earlier, switchAt),
            lyricSweepProgress(earlier, switchAt, 0L),
            0.001f,
        )
    }

    @Test
    fun lastWordRenderLengthDropsTheSwitchLead() {
        val word = LyricWord(timeMs = 400L, durationMs = 2_000L, text = "好")
        val lead = 300L
        assertEquals(1_700L, lyricLastWordRenderDurationMs(word.durationMs, lead))
        val doneAt = word.timeMs + word.durationMs - lead
        assertEquals(1f, lyricSweepProgress(word, doneAt, lead), 0.001f)
        assertTrue(lyricSweepProgress(word, doneAt - 1, lead) < 1f)
        assertEquals(1L, lyricLastWordRenderDurationMs(80L, 300L))
    }

    @Test
    fun zeroLeadLeavesWordProgressAlone() {
        val word = LyricWord(timeMs = 1_000L, durationMs = 400L, text = "宽")
        assertEquals(400L, lyricLastWordRenderDurationMs(word.durationMs, 0L))
        assertEquals(0.5f, lyricSweepProgress(word, 1_200L, 0L), 0.001f)
    }

    private fun sampleLines(): List<LrcLine> = listOf(
        LrcLine(
            timeMs = 0L,
            text = "你好",
            words = listOf(
                LyricWord(0L, 500L, "你"),
                LyricWord(500L, 500L, "好"),
            ),
        ),
        LrcLine(timeMs = 1_000L, text = "世界"),
        LrcLine(timeMs = 3_000L, text = "再一句"),
    )
}
