package com.kite.zmusic.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackLoadGateTest {
    @Test
    fun readyOfOldMediaDoesNotClearLoadPending() {
        assertFalse(PlaybackLoadGate.canCommitReady(true, "100", 200L))
        assertFalse(PlaybackLoadGate.mediaMatchesTrack("100", 200L))
    }

    @Test
    fun readyOfTargetMediaClearsLoadPending() {
        assertTrue(PlaybackLoadGate.canCommitReady(true, "200", 200L))
        assertTrue(PlaybackLoadGate.mediaMatchesTrack("200", 200L))
    }

    @Test
    fun readyWhileNotLoadingAlwaysCommits() {
        assertTrue(PlaybackLoadGate.canCommitReady(false, "100", 200L))
        assertTrue(PlaybackLoadGate.canCommitReady(false, null, 200L))
    }

    @Test
    fun emptyTrackMatchesBlankMedia() {
        assertTrue(PlaybackLoadGate.mediaMatchesTrack(null, null))
        assertTrue(PlaybackLoadGate.mediaMatchesTrack("", 0L))
        assertFalse(PlaybackLoadGate.mediaMatchesTrack("1", 0L))
    }

    @Test
    fun skipKeepsLoadPendingWhileOldExoItemReportsReady() {
        assertFalse(PlaybackLoadGate.canCommitReady(loadPending = true, mediaId = "100", trackId = 200L))
        assertTrue(PlaybackLoadGate.canCommitReady(loadPending = true, mediaId = "200", trackId = 200L))
    }

    @Test
    fun committingOldReadyWouldExposeLeftoverMsAsNewTrackProgress() {
        val oldPos = 179_000L
        val newDuration = 120_000L
        val gated = PlaybackLoadGate.canCommitReady(true, "1", 2L)
        assertFalse(gated)
        if (!gated) {
            assertTrue(oldPos > newDuration)
        }
    }
}
