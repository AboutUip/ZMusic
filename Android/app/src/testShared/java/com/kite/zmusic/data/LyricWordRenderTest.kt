package com.kite.zmusic.data

import org.junit.Assert.assertEquals
import org.junit.Test

class LyricWordRenderTest {

    @Test
    fun storedValueRoundTripsAndUnknownFallsBackToLine() {
        assertEquals(LyricWordRender.Line, LyricWordRender.fromStored(null))
        assertEquals(LyricWordRender.Line, LyricWordRender.fromStored("line"))
        assertEquals(LyricWordRender.Line, LyricWordRender.fromStored("nope"))
        assertEquals(LyricWordRender.Word, LyricWordRender.fromStored("word"))
        assertEquals(LyricWordRender.Width, LyricWordRender.fromStored("width"))
        assertEquals("width", LyricWordRender.stored(LyricWordRender.Width))
        assertEquals("word", LyricWordRender.stored(LyricWordRender.Word))
    }

    @Test
    fun legacyBooleanBecomesWordModeUntilAModeIsStored() {
        assertEquals(LyricWordRender.Line, resolveLyricWordRender(null, legacyWordByWord = false))
        assertEquals(LyricWordRender.Word, resolveLyricWordRender(null, legacyWordByWord = true))
        assertEquals(LyricWordRender.Word, resolveLyricWordRender("", legacyWordByWord = true))
        assertEquals(LyricWordRender.Width, resolveLyricWordRender("width", legacyWordByWord = false))
        assertEquals(LyricWordRender.Line, resolveLyricWordRender("nope", legacyWordByWord = true))
    }

    @Test
    fun progressFollowsEachWordsOwnTiming() {
        val word = LyricWord(timeMs = 1_000L, durationMs = 400L, text = "宽")
        assertEquals(0f, lyricWordProgress(word, 1_000L), 0.001f)
        assertEquals(0.5f, lyricWordProgress(word, 1_200L), 0.001f)
        assertEquals(1f, lyricWordProgress(word, 1_400L), 0.001f)
        assertEquals(1f, lyricWordProgress(word, 2_000L), 0.001f)
        assertEquals(0f, lyricWordProgress(word, 0L), 0.001f)
    }
}
