package com.kite.zmusic.data

import org.junit.Assert.assertEquals
import org.junit.Test

class LongLyricScrollTest {

    @Test
    fun storedValueRoundTripsAndUnknownFallsBackToWrap() {
        assertEquals(LongLyricRender.Wrap, LongLyricRender.fromStored(null))
        assertEquals(LongLyricRender.Wrap, LongLyricRender.fromStored("wrap"))
        assertEquals(LongLyricRender.Wrap, LongLyricRender.fromStored("nope"))
        assertEquals(LongLyricRender.Horizontal, LongLyricRender.fromStored("horizontal"))
        assertEquals("horizontal", LongLyricRender.stored(LongLyricRender.Horizontal))
    }

    @Test
    fun shortLineStaysPutAccordingToAlignment() {
        assertEquals(10f, offset(80f, 100f, LongLyricMotion.Timed(0.4f), LongLyricAlign.Center), 0.01f)
        assertEquals(0f, offset(80f, 100f, LongLyricMotion.Timed(0.4f), LongLyricAlign.Start), 0.01f)
        assertEquals(20f, offset(80f, 100f, LongLyricMotion.Timed(0.4f), LongLyricAlign.End), 0.01f)
    }

    @Test
    fun timedScrollHoldsTheEndsThenTravels() {
        assertEquals(0f, offset(200f, 100f, LongLyricMotion.Timed(0f)), 0.01f)
        assertEquals(0f, offset(200f, 100f, LongLyricMotion.Timed(0.12f)), 0.01f)
        assertEquals(-50f, offset(200f, 100f, LongLyricMotion.Timed(0.5f)), 0.01f)
        assertEquals(-100f, offset(200f, 100f, LongLyricMotion.Timed(0.88f)), 0.01f)
        assertEquals(-100f, offset(200f, 100f, LongLyricMotion.Timed(1f)), 0.01f)
    }

    @Test
    fun karaokeScrollFollowsTheSungPoint() {
        assertEquals(0f, offset(200f, 100f, LongLyricMotion.Karaoke(0f)), 0.01f)
        assertEquals(-68f, offset(200f, 100f, LongLyricMotion.Karaoke(0.5f)), 0.01f)
        assertEquals(-100f, offset(200f, 100f, LongLyricMotion.Karaoke(1f)), 0.01f)
    }

    @Test
    fun progressAndSungFractionStayInsideTheLine() {
        assertEquals(0.25f, longLyricProgress(1_500L, 1_000L, 2_000L), 0.001f)
        assertEquals(0f, longLyricProgress(100L, 1_000L, 2_000L), 0.001f)
        assertEquals(1f, longLyricProgress(9_000L, 1_000L, 2_000L), 0.001f)
        val words = listOf(
            LyricWord(0L, 100L, "ab"),
            LyricWord(100L, 100L, "cd"),
        )
        assertEquals(0.75f, longLyricSungFraction(words, 150L), 0.001f)
        assertEquals(0f, longLyricSungFraction(words, 0L), 0.001f)
        assertEquals(1f, longLyricSungFraction(words, 200L), 0.001f)
        val held = listOf(LyricWord(0L, 800L, "啊"))
        assertEquals(1f, longLyricSungFraction(held, 200L, lineEndMs = 200L), 0.001f)
    }

    private fun offset(
        text: Float,
        viewport: Float,
        motion: LongLyricMotion,
        align: LongLyricAlign = LongLyricAlign.Center,
    ) = longLyricOffsetPx(text, viewport, align, motion)
}
