package com.kite.zmusic.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricOverlayLogicTest {
    @Test
    fun awakeAlwaysShowsWindowBackground() {
        assertTrue(overlayShowsWindowBackground(locked = false, idleChrome = false, windowBackgroundEnabled = false))
        assertTrue(overlayShowsWindowBackground(locked = false, idleChrome = false, windowBackgroundEnabled = true))
    }

    @Test
    fun lockedFollowsWindowBackgroundSwitch() {
        assertFalse(overlayShowsWindowBackground(locked = true, idleChrome = false, windowBackgroundEnabled = false))
        assertTrue(overlayShowsWindowBackground(locked = true, idleChrome = false, windowBackgroundEnabled = true))
        assertFalse(overlayShowsWindowBackground(locked = true, idleChrome = true, windowBackgroundEnabled = false))
        assertTrue(overlayShowsWindowBackground(locked = true, idleChrome = true, windowBackgroundEnabled = true))
    }

    @Test
    fun idleUnlockedFollowsWindowBackgroundSwitch() {
        assertFalse(overlayShowsWindowBackground(locked = false, idleChrome = true, windowBackgroundEnabled = false))
        assertTrue(overlayShowsWindowBackground(locked = false, idleChrome = true, windowBackgroundEnabled = true))
    }

    @Test
    fun windowBackgroundDefaultsOff() {
        assertEquals(false, LyricOverlayPrefs().windowBackground)
    }

    @Test
    fun idleUnlockedClaimsTouchesSoDragIsNotStolen() {
        assertTrue(overlayClaimsWindowTouches(locked = false, idleChrome = true, windowDragging = false))
        assertTrue(overlayClaimsWindowTouches(locked = false, idleChrome = true, windowDragging = true))
        assertFalse(overlayClaimsWindowTouches(locked = true, idleChrome = true, windowDragging = false))
        assertFalse(overlayClaimsWindowTouches(locked = false, idleChrome = false, windowDragging = false))
        assertTrue(overlayClaimsWindowTouches(locked = false, idleChrome = false, windowDragging = true))
    }

    @Test
    fun idleTapWakesButDragStaysIdle() {
        assertTrue(
            overlayWakesFromIdle(
                idleChrome = true,
                locked = false,
                dragged = false,
                pointerOnOverlay = true,
            ),
        )
        assertFalse(
            overlayWakesFromIdle(
                idleChrome = true,
                locked = false,
                dragged = true,
                pointerOnOverlay = true,
            ),
        )
        assertFalse(
            overlayWakesFromIdle(
                idleChrome = true,
                locked = true,
                dragged = false,
                pointerOnOverlay = true,
            ),
        )
    }

    @Test
    fun othersKeepOriginalWhenCompanionHidden() {
        val rows = overlayLyricRows(
            lineText = "hello",
            companionText = "你好",
            originalOnTop = true,
            showCompanion = false,
        )
        assertEquals(listOf(OverlayLyricRow("hello", false)), rows)
    }

    @Test
    fun coexistPutsOriginalThenTranslation() {
        val rows = overlayLyricRows(
            lineText = "hello",
            companionText = "你好",
            originalOnTop = true,
            showCompanion = true,
        )
        assertEquals(
            listOf(
                OverlayLyricRow("hello", false),
                OverlayLyricRow("你好", true),
            ),
            rows,
        )
    }

    @Test
    fun coexistCanPutTranslationOnTop() {
        val rows = overlayLyricRows(
            lineText = "hello",
            companionText = "你好",
            originalOnTop = false,
            showCompanion = true,
        )
        assertEquals(
            listOf(
                OverlayLyricRow("你好", true),
                OverlayLyricRow("hello", false),
            ),
            rows,
        )
    }

    @Test
    fun blankCompanionFallsBackToSingleLine() {
        val rows = overlayLyricRows(
            lineText = "",
            companionText = "  ",
            originalOnTop = true,
            showCompanion = true,
            fallback = "♪",
        )
        assertEquals(listOf(OverlayLyricRow("♪", false)), rows)
    }

    @Test
    fun translationDefaultsOnWithCoexist() {
        val prefs = LyricOverlayPrefs()
        assertEquals(true, prefs.preferTranslation)
        assertEquals(true, prefs.translationCoexist)
        assertEquals(false, prefs.othersShowTranslation)
    }

    @Test
    fun overlayBundleCoexistKeepsOriginalWithCompanion() {
        val bundle = pickDisplayLyricBundle(
            original = listOf(LrcLine(0L, "hello")),
            translated = listOf(LrcLine(0L, "你好")),
            wordOriginal = emptyList(),
            wordTranslated = emptyList(),
            preferTranslation = true,
            coexist = true,
            wordByWord = false,
        )
        assertEquals("hello", bundle.lines.single().text)
        assertEquals("你好", bundle.companions.single()?.text)
    }

    @Test
    fun overlayBundleCoverUsesTranslationOnly() {
        val bundle = pickDisplayLyricBundle(
            original = listOf(LrcLine(0L, "hello")),
            translated = listOf(LrcLine(0L, "你好")),
            wordOriginal = emptyList(),
            wordTranslated = emptyList(),
            preferTranslation = true,
            coexist = false,
            wordByWord = false,
        )
        assertEquals("你好", bundle.lines.single().text)
        assertTrue(bundle.companions.isEmpty())
    }
}
