package com.kite.zmusic.ui.home

import kotlin.math.hypot
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeLoftTest {
    private val refresh = 72f
    private val loft = 156f
    private val max = 210f

    @Test
    fun deeperThresholdOpensTheLoftInsteadOfRefreshing() {
        assertEquals(HomePullPhase.Pull, homePullPhase(40f, refresh, loft))
        assertEquals(HomePullPhase.Refresh, homePullPhase(72f, refresh, loft))
        assertEquals(HomePullPhase.Loft, homePullPhase(156f, refresh, loft))
        assertEquals(HomePullRelease.Cancel, homePullRelease(71f, refresh, loft))
        assertEquals(HomePullRelease.Refresh, homePullRelease(155f, refresh, loft))
        assertEquals(HomePullRelease.OpenLoft, homePullRelease(156f, refresh, loft))
    }

    @Test
    fun pullResistsAfterEachThresholdAndComesBackOneToOne() {
        assertEquals(72f, homePullOffsetAfterDrag(0f, 72f, refresh, loft, max), 0.01f)
        assertEquals(150f + 20f * 0.72f, homePullOffsetAfterDrag(150f, 20f, refresh, loft, max), 0.01f)
        assertEquals(170f + 20f * 0.38f, homePullOffsetAfterDrag(170f, 20f, refresh, loft, max), 0.01f)
        assertEquals(max, homePullOffsetAfterDrag(200f, 80f, refresh, loft, max), 0.01f)
        assertEquals(40f, homePullOffsetAfterDrag(100f, -60f, refresh, loft, max), 0.01f)
        assertEquals(0f, homePullOffsetAfterDrag(30f, -80f, refresh, loft, max), 0.01f)
    }

    @Test
    fun upwardDragOnlyConsumesTheDistanceThatActuallyReturns() {
        assertEquals(-40f, homePullConsumed(40f, 0f, -80f, max), 0.01f)
        assertEquals(24f, homePullConsumed(10f, 34f, 24f, max), 0.01f)
        assertEquals(30f, homePullConsumed(max, max, 30f, max), 0.01f)
        assertEquals(0f, homePullConsumed(0f, 0f, -12f, max), 0.01f)
    }

    @Test
    fun returnSwipeClosesWithoutRefreshing() {
        assertEquals(HomePullRelease.Cancel, homePullReturnRelease(400f, 800f, -1200f))
        assertEquals(HomePullRelease.Cancel, homePullReturnRelease(600f, 800f, 0f))
        assertEquals(HomePullRelease.OpenLoft, homePullReturnRelease(700f, 800f, -200f))
        assertEquals(HomePullRelease.Cancel, homePullReturnRelease(0f, 0f, 0f))
    }

    @Test
    fun crossingTheLoftLineDoesNotDragTheFloorTheCircleDoes() {
        val refresh = 48f
        val loft = 80f
        assertEquals(0f, homeSheetOffset(200f, loft), 0.01f)
        assertEquals(20f, homeLoftCoverRadius(0f, 20f, 900f), 0.01f)
        assertEquals(900f, homeLoftCoverRadius(1f, 20f, 900f), 0.01f)
        assertEquals(460f, homeLoftCoverRadius(0.5f, 20f, 900f), 0.01f)
        assertEquals(0f, homeLoftContentAlpha(0.1f), 0.01f)
        assertEquals(1f, homeLoftContentAlpha(0.7f), 0.01f)
        assertEquals(HomePullRelease.Refresh, homePullRelease(79f, refresh, loft))
        assertEquals(HomePullRelease.OpenLoft, homePullRelease(80f, refresh, loft))
        val center = androidx.compose.ui.geometry.Offset(100f, 40f)
        assertEquals(hypot(300f, 760f), homeLoftFullRadius(center, 400f, 800f), 0.5f)
    }

    @Test
    fun floorInterceptStopsOnceTheLoftIsOpenOrGone() {
        assertEquals(false, homeLoftInterceptsFloor(open = false, reveal = 0f))
        assertEquals(false, homeLoftInterceptsFloor(open = true, reveal = 1f))
        assertEquals(true, homeLoftInterceptsFloor(open = true, reveal = 0.2f))
        assertEquals(true, homeLoftInterceptsFloor(open = false, reveal = 0.4f))
        assertEquals(false, homeLoftInterceptsFloor(open = false, reveal = 0.01f))
    }

    @Test
    fun openLoftStillBlocksTheFloorAfterTheTransitionInterceptorLeaves() {
        assertEquals(true, homeLoftBlocksUnderlyingPointers(open = true, reveal = 1f))
        assertEquals(false, homeLoftInterceptsFloor(open = true, reveal = 1f))
        assertEquals(true, homeLoftBlocksUnderlyingPointers(open = true, reveal = 0.2f))
        assertEquals(true, homeLoftBlocksUnderlyingPointers(open = false, reveal = 0.4f))
        assertEquals(false, homeLoftBlocksUnderlyingPointers(open = false, reveal = 0f))
        assertEquals(false, homeLoftBlocksUnderlyingPointers(open = false, reveal = 0.01f))
    }

    @Test
    fun navigationUnlocksAsSoonAsTheLoftStartsClosing() {
        assertEquals(true, homeLoftLocksNavigation(open = true))
        assertEquals(false, homeLoftLocksNavigation(open = false))
    }

    @Test
    fun floorChromeFollowsTheCircleInsteadOfPopping() {
        assertEquals(1f, homeLoftChromeReveal(open = false, reveal = 0f), 0.001f)
        assertEquals(0f, homeLoftChromeReveal(open = true, reveal = 1f), 0.001f)
        assertEquals(0.4f, homeLoftChromeReveal(open = false, reveal = 0.6f), 0.001f)
        assertEquals(0.4f, homeLoftChromeReveal(open = true, reveal = 0.6f), 0.001f)
        assertEquals(0f, homeLoftChromeReveal(open = false, reveal = 1f), 0.001f)
    }

    @Test
    fun leavingTheLoftReturnsTheLoadingCircleAlongItsEntryPath() {
        val hidden = homeLoftIndicatorHiddenCenterY(indicatorTopPx = 80f, indicatorRadiusPx = 20f)
        assertEquals(60f, hidden, 0.01f)
        assertEquals(180f, homeLoftIndicatorExitCenterY(180f, hidden, 0f), 0.01f)
        assertEquals(120f, homeLoftIndicatorExitCenterY(180f, hidden, 0.5f), 0.01f)
        assertEquals(hidden, homeLoftIndicatorExitCenterY(180f, hidden, 1f), 0.01f)
        assertEquals(1f, homeLoftIndicatorExitAlpha(0f), 0.01f)
        assertEquals(1f, homeLoftIndicatorExitAlpha(0.4f), 0.01f)
        assertEquals(0f, homeLoftIndicatorExitAlpha(1f), 0.01f)
        assertEquals(1f, homeLoftIndicatorExitScale(0f), 0.01f)
        assertEquals(0.84f, homeLoftIndicatorExitScale(1f), 0.01f)
    }
}
