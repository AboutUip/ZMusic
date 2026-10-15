package com.kite.zmusic.ui.player

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerExpandMiniBarTest {
    private val fallback = Rect(20f, 900f, 1000f, 964f)
    private val liveFlash = Rect(20f, 820f, 1000f, 1020f)

    @Test
    fun closeUsesFallbackEvenWhenLiveHeightFlashes() {
        val got = resolveMiniBarInShell(
            targetOpen = false,
            live = liveFlash,
            fallback = fallback,
        )
        assertEquals(fallback, got)
    }

    @Test
    fun closeUsesFallbackWhenLiveIsInvalid() {
        val got = resolveMiniBarInShell(
            targetOpen = false,
            live = Rect.Zero,
            fallback = fallback,
        )
        assertEquals(fallback, got)
    }

    @Test
    fun openKeepsMatchingLive() {
        val live = Rect(20f, 900f, 1000f, 964f)
        val got = resolveMiniBarInShell(
            targetOpen = true,
            live = live,
            fallback = fallback,
        )
        assertEquals(live, got)
    }

    @Test
    fun openDropsLiveWhenHeightMismatchesFallback() {
        val got = resolveMiniBarInShell(
            targetOpen = true,
            live = liveFlash,
            fallback = fallback,
        )
        assertEquals(fallback, got)
    }

    @Test
    fun closeUsesFallbackWhenLiveWidthIgnoresRail() {
        val liveWide = Rect(20f, 900f, 2000f, 964f)
        val got = resolveMiniBarInShell(
            targetOpen = false,
            live = liveWide,
            fallback = fallback,
        )
        assertEquals(fallback, got)
    }

    @Test
    fun openDropsLiveWhenWidthMismatchesFallback() {
        val liveWide = Rect(20f, 900f, 2000f, 964f)
        val got = resolveMiniBarInShell(
            targetOpen = true,
            live = liveWide,
            fallback = fallback,
        )
        assertEquals(fallback, got)
    }

    @Test
    fun openDropsLiveWhenTopFlashesButSizeMatches() {
        val liveShifted = Rect(20f, 820f, 1000f, 884f)
        val got = resolveMiniBarInShell(
            targetOpen = true,
            live = liveShifted,
            fallback = fallback,
        )
        assertEquals(fallback, got)
    }

    @Test
    fun closeKeepsHeldBarWhenRecomputeOnlyDropsNav() {
        val recomputed = Rect(20f, 948f, 1000f, 1012f)
        val got = preferCloseMiniBar(fallback, recomputed)
        assertEquals(fallback, got)
    }

    @Test
    fun closeReplacesHeldBarWhenWidthChanges() {
        val landscape = Rect(228f, 780f, 1980f, 844f)
        val got = preferCloseMiniBar(fallback, landscape)
        assertEquals(landscape, got)
    }

    @Test
    fun closeUsesRecomputedWhenHeldIsInvalid() {
        val recomputed = Rect(20f, 900f, 1000f, 964f)
        val got = preferCloseMiniBar(Rect.Zero, recomputed)
        assertEquals(recomputed, got)
    }

    @Test
    fun landscapeFormulaLeavesRail() {
        val shell = Rect(0f, 0f, 2000f, 900f)
        val rail = 208f
        val side = 20f
        val barH = 64f
        val got = formulaMiniBarRect(
            shell = shell,
            sidePx = side,
            railPx = rail,
            barH = barH,
            homeFromBottom = 120f,
        )
        assertEquals(rail + side, got.left, 0.01f)
        assertEquals(shell.width - side, got.right, 0.01f)
        assertEquals(barH, got.height, 0.01f)
        assertEquals(shell.height - 120f, got.top, 0.01f)
    }

    @Test
    fun portraitFormulaHasNoRail() {
        val shell = Rect(0f, 0f, 1080f, 1920f)
        val got = formulaMiniBarRect(
            shell = shell,
            sidePx = 18f,
            railPx = 0f,
            barH = 64f,
            homeFromBottom = 200f,
        )
        assertEquals(18f, got.left, 0.01f)
        assertEquals(1080f - 18f, got.right, 0.01f)
    }

    @Test
    fun portraitFormulaCentersMaxWidth() {
        val shell = Rect(0f, 0f, 2000f, 1920f)
        val maxW = 520f
        val got = formulaMiniBarRect(
            shell = shell,
            sidePx = 18f,
            railPx = 0f,
            barH = 64f,
            homeFromBottom = 200f,
            maxBarWidthPx = maxW,
        )
        val contentW = 2000f - 36f
        val expectedLeft = 18f + (contentW - maxW) / 2f
        assertEquals(expectedLeft, got.left, 0.01f)
        assertEquals(expectedLeft + maxW, got.right, 0.01f)
        assertEquals(64f, got.height, 0.01f)
        assertEquals(1920f - 200f, got.top, 0.01f)
    }

    @Test
    fun coldOpenWaitsUntilVinylAndMiniAreMeasured() {
        val shell = Rect(0f, 0f, 1080f, 1920f)
        val mini = Rect(40f, 1700f, 1040f, 1764f)
        val cover = Rect(48f, 1708f, 96f, 1756f)
        val title = Rect(110f, 1712f, 700f, 1740f)
        val play = Rect(960f, 1716f, 1020f, 1756f)
        val progress = Rect(48f, 1760f, 1032f, 1768f)
        val vinyl = Rect(200f, 280f, 880f, 960f)
        val fullPlay = Rect(480f, 1400f, 600f, 1520f)
        val fullTitle = Rect(80f, 1000f, 1000f, 1080f)
        val fullProgress = Rect(48f, 1600f, 1032f, 1620f)
        assertFalse(
            playerExpandFlightAnchorsReady(
                shell = shell,
                lookReady = true,
                squareCover = false,
                fullVinyl = Rect.Zero,
                fullCover = Rect.Zero,
                fullPlay = fullPlay,
                fullTitle = fullTitle,
                fullProgress = fullProgress,
                miniCover = cover,
                miniTitle = title,
                miniPlay = play,
                miniBar = mini,
            ),
        )
        assertTrue(
            playerExpandFlightAnchorsReady(
                shell = shell,
                lookReady = true,
                squareCover = false,
                fullVinyl = vinyl,
                fullCover = Rect.Zero,
                fullPlay = fullPlay,
                fullTitle = fullTitle,
                fullProgress = fullProgress,
                miniCover = cover,
                miniTitle = title,
                miniPlay = play,
                miniBar = mini,
            ),
        )
    }

    @Test
    fun squareCoverDoesNotTreatVinylAsReady() {
        val box = Rect(10f, 10f, 100f, 100f)
        assertFalse(
            playerExpandFlightAnchorsReady(
                shell = box,
                lookReady = true,
                squareCover = true,
                fullVinyl = box,
                fullCover = Rect.Zero,
                fullPlay = box,
                fullTitle = box,
                fullProgress = Rect(10f, 10f, 100f, 16f),
                miniCover = box,
                miniTitle = box,
                miniPlay = box,
                miniBar = box,
            ),
        )
    }

    @Test
    fun openStableCountResetsWhenColdLayoutMoves() {
        assertEquals(0, playerExpandOpenStableCount(ready = false, sameAsPrevious = true, previousStable = 1))
        assertEquals(0, playerExpandOpenStableCount(ready = true, sameAsPrevious = false, previousStable = 1))
        assertEquals(2, playerExpandOpenStableCount(ready = true, sameAsPrevious = true, previousStable = 1))
    }

    @Test
    fun fullAnchorStaysPutWhileFlyingAndClosing() {
        assertFalse(
            playerExpandHoldFullAnchor(
                mounted = true,
                targetOpen = true,
                visualProgress = 0f,
                anchorValid = true,
            ),
        )
        assertTrue(
            playerExpandHoldFullAnchor(
                mounted = true,
                targetOpen = true,
                visualProgress = 0.4f,
                anchorValid = true,
            ),
        )
        assertTrue(
            playerExpandHoldFullAnchor(
                mounted = true,
                targetOpen = false,
                visualProgress = 0.9f,
                anchorValid = true,
            ),
        )
        assertFalse(
            playerExpandHoldFullAnchor(
                mounted = true,
                targetOpen = false,
                visualProgress = 0.9f,
                anchorValid = false,
            ),
        )
        assertFalse(
            playerExpandHoldFullAnchor(
                mounted = true,
                targetOpen = true,
                visualProgress = 1f,
                anchorValid = true,
            ),
        )
    }
}
