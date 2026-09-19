package com.kite.zmusic.listen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ListenMatchLogicTest {
    private fun invite(from: String) = ListenInvite(
        id = "i-$from",
        roomId = "r",
        from = ListenPeer(from, from, ""),
        to = ListenPeer("7", "客", ""),
        status = "pending",
        expiresAt = 0L,
        expiresIn = 60L,
    )

    @Test
    fun skipUntilIsFiveMinutes() {
        assertEquals(300_000L, ListenDeclineCoolMs)
        assertEquals(400_000L, listenSkipUntil(100_000L))
    }

    @Test
    fun declinedPeerIsSkippedForFiveMinutes() {
        val skip = mapOf("42" to 300_000L)
        assertTrue(listenUidSkipped("42", skip, 299_999L))
        assertFalse(listenUidSkipped("42", skip, 300_000L))
        assertFalse(listenUidSkipped("99", skip, 0L))
    }

    @Test
    fun incomingHiddenWhileQuietEvenIfDifferentHost() {
        val next = invite("99")
        assertNull(listenIncomingVisible(next, nowMs = 10L, quietUntilMs = 20L, skipUntil = emptyMap()))
        assertEquals(next, listenIncomingVisible(next, nowMs = 20L, quietUntilMs = 20L, skipUntil = emptyMap()))
    }

    @Test
    fun incomingHiddenWhenThatHostWasSkipped() {
        val fromHost = invite("42")
        val skip = mapOf("42" to 50_000L)
        assertNull(listenIncomingVisible(fromHost, nowMs = 10L, quietUntilMs = 0L, skipUntil = skip))
        assertEquals(fromHost, listenIncomingVisible(fromHost, nowMs = 50_000L, quietUntilMs = 0L, skipUntil = skip))
    }

    @Test
    fun activeSkipUidsDropsExpired() {
        val skip = mapOf("42" to 10L, "99" to 30L, "" to 100L)
        val active = listenActiveSkipUids(skip, 20L)
        assertEquals(setOf("99"), active.toSet())
    }

    @Test
    fun threePeopleHostCancelDoesNotOfferNextUntilMatchClickedAgain() {
        val now = 1_000L
        val after = listenHostCancel("7", now)
        assertEquals(false, after.matching)
        assertTrue(listenUidSkipped("7", after.skipUntil, now))
        val next = listenOfferedPeer(
            matching = after.matching,
            candidates = listOf("7", "99"),
            skipUntil = after.skipUntil,
            nowMs = now,
        )
        assertNull(next)
        val ifClickedMatchAgain = listenOfferedPeer(
            matching = true,
            candidates = listOf("7", "99"),
            skipUntil = after.skipUntil,
            nowMs = now,
        )
        assertEquals("99", ifClickedMatchAgain)
    }

    @Test
    fun threePeopleGuestRejectHidesNextHostForFiveMinutes() {
        val now = 10L
        val after = listenGuestReject("42", now)
        assertEquals(listenSkipUntil(now), after.quietUntilMs)
        val nextHost = invite("99")
        assertNull(
            listenIncomingVisible(
                nextHost,
                nowMs = now,
                quietUntilMs = after.quietUntilMs,
                skipUntil = after.skipUntil,
            ),
        )
        assertEquals(
            nextHost,
            listenIncomingVisible(
                nextHost,
                nowMs = after.quietUntilMs,
                quietUntilMs = after.quietUntilMs,
                skipUntil = after.skipUntil,
            ),
        )
        assertNull(
            listenIncomingVisible(
                invite("42"),
                nowMs = after.quietUntilMs - 1,
                quietUntilMs = after.quietUntilMs,
                skipUntil = after.skipUntil,
            ),
        )
    }

    @Test
    fun oldMatchingLoopWouldKeepOfferingTheOtherPerson() {
        val now = 0L
        val skip = listenHostCancel("7", now).skipUntil
        val oldLoop = listenOfferedPeer(true, listOf("7", "99"), skip, now)
        assertEquals("99", oldLoop)
    }
}
