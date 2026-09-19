package com.kite.zmusic.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NcmListenLogicTest {
    @Test
    fun longSongQualifiesAtTwentySeconds() {
        assertFalse(NcmListenLogic.qualifies(19_000L, 180_000L))
        assertTrue(NcmListenLogic.qualifies(20_000L, 180_000L))
    }

    @Test
    fun shortSongQualifiesAtHalfDuration() {
        assertFalse(NcmListenLogic.qualifies(4_000L, 10_000L))
        assertTrue(NcmListenLogic.qualifies(5_000L, 10_000L))
    }

    @Test
    fun unknownDurationUsesTwentySeconds() {
        assertFalse(NcmListenLogic.qualifies(19_000L, 0L))
        assertTrue(NcmListenLogic.qualifies(20_000L, 0L))
    }

    @Test
    fun accumulateCreditsRealGapsUpToTwoSeconds() {
        assertEquals(1_500L, NcmListenLogic.accumulate(0L, 1_000L, 2_500L))
        assertEquals(2_000L, NcmListenLogic.accumulate(0L, 1_000L, 4_000L))
    }

    @Test
    fun accumulateIgnoresMissingAnchor() {
        assertEquals(8_000L, NcmListenLogic.accumulate(8_000L, 0L, 9_000L))
    }

    @Test
    fun wrapDetectsLoopBackNearEnd() {
        assertTrue(
            NcmListenLogic.wrapped(
                sameTrack = true,
                lastPos = 170_000L,
                durationMs = 180_000L,
                positionMs = 800L,
                listenedMs = 5_000L,
            ),
        )
        assertFalse(
            NcmListenLogic.wrapped(
                sameTrack = true,
                lastPos = 10_000L,
                durationMs = 180_000L,
                positionMs = 800L,
                listenedMs = 5_000L,
            ),
        )
    }

    @Test
    fun submitOnceUntilReset() {
        assertTrue(NcmListenLogic.shouldSubmit(false, 20_000L, 180_000L))
        assertFalse(NcmListenLogic.shouldSubmit(true, 180_000L, 180_000L))
        assertFalse(NcmListenLogic.shouldSubmit(false, 1_000L, 180_000L))
    }
}
