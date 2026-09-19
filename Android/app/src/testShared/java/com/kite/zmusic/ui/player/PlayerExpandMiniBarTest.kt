package com.kite.zmusic.ui.player

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
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
}
