package com.kite.zmusic.ui.player

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerExpandVinylSpinTest {
    @Test
    fun shortestUprightKeepsSmallAngles() {
        assertEquals(40f, vinylShortestUprightDeg(40f), 0.01f)
        assertEquals(40f, vinylShortestUprightDeg(400f), 0.01f)
        assertEquals(0f, vinylShortestUprightDeg(720f), 0.01f)
    }

    @Test
    fun shortestUprightTakesTheShortWayPastHalfTurn() {
        assertEquals(-160f, vinylShortestUprightDeg(200f), 0.01f)
        assertEquals(-1f, vinylShortestUprightDeg(359f), 0.01f)
    }

    @Test
    fun closeUnwindsCapturedSpinWithProgress() {
        val from = vinylShortestUprightDeg(400f)
        assertEquals(40f, flightVinylRotationDeg(1f, from), 0.01f)
        assertEquals(20f, flightVinylRotationDeg(0.5f, from), 0.01f)
        assertEquals(0f, flightVinylRotationDeg(0f, from), 0.01f)
    }

    @Test
    fun openFromMiniStaysUpright() {
        assertEquals(0f, flightVinylRotationDeg(0f, 0f), 0.01f)
        assertEquals(0f, flightVinylRotationDeg(0.4f, 0f), 0.01f)
        assertEquals(0f, flightVinylRotationDeg(1f, 0f), 0.01f)
    }

    @Test
    fun landscapeAndPortraitShareTheSameUnwind() {
        val from = vinylShortestUprightDeg(90f)
        val mid = flightVinylRotationDeg(0.5f, from)
        assertEquals(45f, mid, 0.01f)
    }

    @Test
    fun snappingToFlightAtHandoffDoesNotJumpWhenProgressIsNearOne() {
        val live = vinylShortestUprightDeg(137f)
        val flight = flightVinylRotationDeg(0.995f, live)
        assertEquals(live, flight, 1f)
    }
}
