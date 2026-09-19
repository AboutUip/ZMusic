package com.kite.zmusic.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SeekDisplayPositionTest {
    @Test
    fun shorterNextTrackWouldHitOneHundredWithoutHold() {
        val from = 180_000L
        val incoming = 120_000L
        assertEquals(1f, seekProgressFraction(from, incoming), 0.001f)
    }

    @Test
    fun trackChangeHoldsPreviousDurationSoBarDoesNotJumpToEnd() {
        val from = 180_000f
        val held = seekRewindDisplayDurationMs(
            incomingDurationMs = 120_000L,
            animPositionMs = from,
            previousDurationMs = 240_000L,
            holdDurationMs = 0L,
            trackChanged = true,
        )
        assertEquals(240_000L, held)
        assertEquals(0.75f, seekProgressFraction(from.toLong(), held), 0.01f)
        assertTrue(seekProgressFraction(from.toLong(), held) < 1f)
    }

    @Test
    fun capturedHoldKeepsRewindOffTheEndAfterTrackIdAlreadyUpdated() {
        val from = 160_000f
        val held = seekRewindDisplayDurationMs(
            incomingDurationMs = 90_000L,
            animPositionMs = from,
            previousDurationMs = 90_000L,
            holdDurationMs = 210_000L,
            trackChanged = false,
        )
        assertEquals(210_000L, held)
        assertEquals(160_000f / 210_000f, seekProgressFraction(from.toLong(), held), 0.01f)
    }

    @Test
    fun midSongSkipOntoShorterTrackKeepsOldPercentNotAJumpUp() {
        val from = 60_000f
        val held = seekRewindDisplayDurationMs(
            incomingDurationMs = 80_000L,
            animPositionMs = from,
            previousDurationMs = 200_000L,
            holdDurationMs = 0L,
            trackChanged = true,
        )
        assertEquals(200_000L, held)
        assertEquals(0.30f, seekProgressFraction(from.toLong(), held), 0.01f)
        assertTrue(seekProgressFraction(from.toLong(), 80_000L) > 0.70f)
    }

    @Test
    fun rewindCompleteUsesIncomingDuration() {
        val held = seekRewindDisplayDurationMs(
            incomingDurationMs = 120_000L,
            animPositionMs = 0f,
            previousDurationMs = 240_000L,
            holdDurationMs = 240_000L,
            trackChanged = false,
        )
        assertEquals(120_000L, held)
        assertEquals(0f, seekProgressFraction(0L, held), 0.001f)
    }

    @Test
    fun sameLengthSkipDoesNotNeedHold() {
        val held = seekRewindDisplayDurationMs(
            incomingDurationMs = 180_000L,
            animPositionMs = 40_000f,
            previousDurationMs = 180_000L,
            holdDurationMs = 0L,
            trackChanged = true,
        )
        assertEquals(180_000L, held)
    }

    @Test
    fun captureHoldIsAtLeastPreviousDurationAndCurrentMs() {
        assertEquals(240_000L, seekRewindCaptureHoldMs(240_000L, 180_000f))
        assertEquals(180_000L, seekRewindCaptureHoldMs(120_000L, 180_000f))
        assertEquals(1L, seekRewindCaptureHoldMs(0L, 0f))
    }

    @Test
    fun longerNextTrackCanUseIncomingDuration() {
        val held = seekRewindDisplayDurationMs(
            incomingDurationMs = 300_000L,
            animPositionMs = 90_000f,
            previousDurationMs = 180_000L,
            holdDurationMs = 0L,
            trackChanged = true,
        )
        assertEquals(300_000L, held)
        assertEquals(0.30f, seekProgressFraction(90_000L, held), 0.01f)
    }

    @Test
    fun durationArrivingBeforeTrackIdStillHoldsSoAnimationCanCover() {
        val from = 180_000f
        val held = seekRewindDisplayDurationMs(
            incomingDurationMs = 120_000L,
            animPositionMs = from,
            previousDurationMs = 240_000L,
            holdDurationMs = 0L,
            trackChanged = false,
        )
        assertEquals(240_000L, held)
        assertEquals(0.75f, seekProgressFraction(from.toLong(), held), 0.01f)
        assertTrue(seekShouldKeepPreviousDuration(from, 120_000L, 0L, false))
    }

    @Test
    fun skipRewindAnimatesEvenWhenLoadTookLongerThanStaleGap() {
        assertTrue(
            seekShouldAnimateToTarget(
                from = 180_000f,
                target = 0f,
                trackChanged = true,
                stale = true,
                durationMs = 120_000L,
            ),
        )
        assertTrue(
            seekShouldAnimateToTarget(
                from = 180_000f,
                target = 200f,
                trackChanged = true,
                stale = true,
                durationMs = 120_000L,
            ),
        )
    }

    @Test
    fun loopFromNearEndAnimatesBackToZero() {
        assertTrue(
            seekShouldAnimateToTarget(
                from = 178_000f,
                target = 0f,
                trackChanged = false,
                stale = false,
                durationMs = 180_000L,
            ),
        )
    }

    @Test
    fun bufferingFakeZeroDoesNotStealTheBar() {
        assertTrue(
            seekShouldPreservePositionOnFakeZero(
                from = 90_000f,
                target = 0f,
                loadPending = true,
                trackChanged = false,
            ),
        )
        assertTrue(
            !seekShouldPreservePositionOnFakeZero(
                from = 90_000f,
                target = 0f,
                loadPending = true,
                trackChanged = true,
            ),
        )
    }

    @Test
    fun staleFollowSnapsWithoutACoveringAnimation() {
        assertTrue(
            !seekShouldAnimateToTarget(
                from = 1_000f,
                target = 1_200f,
                trackChanged = false,
                stale = true,
                durationMs = 240_000L,
            ),
        )
    }
}
