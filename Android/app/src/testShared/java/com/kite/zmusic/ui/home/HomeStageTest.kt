package com.kite.zmusic.ui.home

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeStageTest {
    @Test
    fun closedFrameCoversThePageAndOpenFrameRecedes() {
        val closed = homeStageFrame(0f, 400f, 28f, 22f, 22f, 56f)
        assertEquals(0f, closed.pageTranslationX, 0.01f)
        assertEquals(1f, closed.pageScale, 0.01f)
        assertEquals(0f, closed.pageRotationY, 0.01f)
        assertEquals(0f, closed.wingRotationY, 0.01f)
        assertEquals(Rect(0f, 0f, 400f, 800f), homeStageCardRect(closed, 400f, 800f))

        val open = homeStageFrame(1f, 400f, 28f, 22f, 22f, 56f)
        assertEquals(400f * HomeStageTravelFraction, open.pageTranslationX, 0.01f)
        assertEquals(0.86f, open.pageScale, 0.01f)
        assertEquals(-18f, open.pageRotationY, 0.01f)
        assertEquals(18f, open.wingRotationY, 0.01f)
        assertEquals(0f, open.wingTranslationX, 0.01f)
        assertTrue(open.cavityTranslationX < open.pageTranslationX)
        val card = homeStageCardRect(open, 400f, 800f)
        assertEquals(open.pageTranslationX, card.left, 0.01f)
        assertTrue(card.top > 0f)
        assertTrue(card.bottom < 800f)
        val side = homeStageSidebarRect(1f, 400f, 800f)
        assertEquals(0f, side.left, 0.01f)
        assertEquals(800f * HomeStageSidebarInsetYFraction, side.top, 0.01f)
        assertEquals(800f * (1f - HomeStageSidebarInsetYFraction), side.bottom, 0.01f)
        assertEquals(400f * (HomeStageTravelFraction - HomeStageGapFraction), side.right, 0.01f)
        assertTrue(open.pageTranslationX - side.right > 400f * 0.10f)
        assertEquals(0f, homeStageSidebarRect(0f, 400f, 800f).width, 0.01f)
    }

    @Test
    fun dragFollowsTheFingerAndClamps() {
        assertEquals(0.5f, homeStageProgressForDelta(0f, 200f * 0.74f, 400f), 0.01f)
        assertEquals(0f, homeStageProgressForDelta(0.1f, -400f, 400f), 0.01f)
        assertEquals(1f, homeStageProgressForDelta(0.9f, 400f, 400f), 0.01f)
    }

    @Test
    fun rightwardDragOpensAndAnOpenPageTracksEitherWay() {
        assertNull(homeStageClaimsDrag(0f, 4f, 2f, 12f))
        assertEquals(false, homeStageClaimsDrag(0f, 4f, 40f, 12f))
        assertEquals(false, homeStageClaimsDrag(0f, -40f, 4f, 12f))
        assertEquals(true, homeStageClaimsDrag(0f, 40f, 6f, 12f))
        assertEquals(true, homeStageClaimsDrag(0.4f, -30f, 4f, 12f))
    }

    @Test
    fun settleUsesDistanceUnlessTheFlingIsClear() {
        assertEquals(true, homeStageSettlesOpen(0.5f, 0f))
        assertEquals(false, homeStageSettlesOpen(0.2f, 0f))
        assertEquals(true, homeStageSettlesOpen(0.1f, 1600f))
        assertEquals(false, homeStageSettlesOpen(0.9f, -1600f))
    }
}
