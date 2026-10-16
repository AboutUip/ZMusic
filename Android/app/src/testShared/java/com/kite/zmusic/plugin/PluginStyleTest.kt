package com.kite.zmusic.plugin

import com.kite.zmusic.data.LyricColorSlot
import com.kite.zmusic.data.LyricOverlayPrefs
import com.kite.zmusic.data.LyricRoleStyle
import com.kite.zmusic.data.PlayerDisplayPrefs
import com.kite.zmusic.data.TitleColorSlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PluginStyleTest {
    @Before
    fun reset() {
        PluginStylePresent.resetForTests()
    }

    @Test
    fun playingColorLeavesBoldItalicAndScale() {
        assertTrue(setFocus(role("playing", mapOf("color" to "#112233"))))
        val painted = PluginStylePresent.paint(PlayerDisplayPrefs(), PluginStyleSurface.Focus)
        val role = painted.lyricPlayingStyle
        assertEquals(0xFF112233.toInt(), role.preset0Argb)
        assertEquals(LyricColorSlot.PRESET_0, role.colorSlot)
        assertTrue(role.bold)
        assertFalse(role.italic)
        assertEquals(1f, role.sanitizedFontScale(), 0.001f)
        assertEquals(LyricColorSlot.DEFAULT, painted.lyricPlayedStyle.colorSlot)
        assertEquals(
            LyricRoleStyle.DynamicPlayingDefault.preset0Argb,
            painted.dynamicLyricPlayingStyle.preset0Argb,
        )
    }

    @Test
    fun playedItalicFalseKeepsDefaultColorSlot() {
        assertTrue(setFocus(role("played", mapOf("italic" to false, "bold" to true))))
        val role = PluginStylePresent.paint(PlayerDisplayPrefs(), PluginStyleSurface.Focus).lyricPlayedStyle
        assertFalse(role.italic)
        assertTrue(role.bold)
        assertEquals(LyricColorSlot.DEFAULT, role.colorSlot)
    }

    @Test
    fun focusAndDynamicStayOnSeparateFields() {
        assertTrue(setFocus(role("playing", mapOf("color" to "#112233"))))
        assertTrue(
            PluginStylePresent.set(
                "p",
                "dynamic",
                mapOf("lyric" to mapOf("playing" to mapOf("color" to "#445566", "fontScale" to 1.2))),
            ),
        )
        val both = PluginStylePresent.paint(
            PluginStylePresent.paint(PlayerDisplayPrefs(), PluginStyleSurface.Focus),
            PluginStyleSurface.Dynamic,
        )
        assertEquals(0xFF112233.toInt(), both.lyricPlayingStyle.preset0Argb)
        assertEquals(0xFF445566.toInt(), both.dynamicLyricPlayingStyle.preset0Argb)
        assertEquals(1.2f, both.dynamicLyricPlayingStyle.sanitizedFontScale(), 0.001f)
        assertEquals(1f, both.lyricPlayingStyle.sanitizedFontScale(), 0.001f)
        assertEquals(LyricColorSlot.DEFAULT, PlayerDisplayPrefs().lyricPlayingStyle.colorSlot)
    }

    @Test
    fun portraitDoesNotPaintFocusLyric() {
        assertTrue(setFocus(role("playing", mapOf("color" to "#112233"))))
        val portrait = PluginStylePresent.paint(PlayerDisplayPrefs(), PluginStyleSurface.Portrait)
        assertEquals(LyricColorSlot.DEFAULT, portrait.lyricPlayingStyle.colorSlot)
        assertEquals(0f, portrait.lyricOffsetYDp, 0.001f)
    }

    @Test
    fun portraitOffsetDoesNotMoveFocusLyric() {
        assertTrue(
            PluginStylePresent.set(
                "p",
                "portrait",
                mapOf(
                    "lyric" to mapOf("offsetY" to -20, "playedCount" to 10, "backgroundTransparency" to 0.4),
                    "preview" to mapOf("playingColor" to "#ABCDEF", "playingFont" to 18, "count" to 3),
                    "transport" to mapOf("offsetY" to 12, "includeControls" to true),
                ),
            ),
        )
        val painted = PluginStylePresent.paint(PlayerDisplayPrefs(), PluginStyleSurface.Portrait)
        assertEquals(-20f, painted.lyricOffsetYDp, 0.001f)
        assertEquals(0f, painted.lyricOffsetXDp, 0.001f)
        assertEquals(10, painted.lyricPlayedCount)
        assertEquals(0.4f, painted.lyricBackgroundTransparency, 0.001f)
        assertEquals(0xFFABCDEF.toInt(), painted.portraitPreviewLyricPlayingArgb)
        assertEquals(18f, painted.portraitPreviewLyricPlayingFontSp, 0.001f)
        assertEquals(3, painted.portraitPreviewLyricCount)
        assertEquals(12f, painted.portraitTransportOffsetYDp, 0.001f)
        assertTrue(painted.portraitTransportContainerInclude)
        val focus = PluginStylePresent.paint(PlayerDisplayPrefs(), PluginStyleSurface.Focus)
        assertEquals(0f, focus.lyricOffsetYDp, 0.001f)
        assertEquals(2, focus.lyricPlayedCount)
    }

    @Test
    fun titleLinesAreIndependent() {
        assertTrue(
            PluginStylePresent.set(
                "p",
                "focus",
                mapOf(
                    "title" to mapOf(
                        "align" to "lyrics",
                        "name" to mapOf("color" to "#010203", "fontScale" to 1.25),
                        "source" to mapOf("fontScale" to 0.8),
                    ),
                ),
            ),
        )
        val painted = PluginStylePresent.paint(PlayerDisplayPrefs(), PluginStyleSurface.Focus)
        assertEquals(0xFF010203.toInt(), painted.titleNameStyle.preset0Argb)
        assertEquals(TitleColorSlot.PRESET_0, painted.titleNameStyle.colorSlot)
        assertEquals(1.25f, painted.titleNameStyle.sanitizedFontScale(), 0.001f)
        assertEquals(TitleColorSlot.DEFAULT, painted.titleSourceStyle.colorSlot)
        assertEquals(0.8f, painted.titleSourceStyle.sanitizedFontScale(), 0.001f)
        assertEquals(TitleColorSlot.DEFAULT, painted.titleArtistStyle.colorSlot)
    }

    @Test
    fun vinylGeometryAndCustomColorDoNotRequireLook() {
        assertTrue(
            PluginStylePresent.set(
                "p",
                "focus",
                mapOf("vinyl" to mapOf("size" to 1.2, "base" to "#101010", "groove" to "#EEEEEE")),
            ),
        )
        val painted = PluginStylePresent.paint(PlayerDisplayPrefs(), PluginStyleSurface.Focus)
        assertEquals(1.2f, painted.vinylSizeScale, 0.001f)
        assertEquals(0xFF101010.toInt(), painted.vinylCustomBaseArgb)
        assertEquals(0xFFEEEEEE.toInt(), painted.vinylCustomGrooveArgb)
    }

    @Test
    fun presetVinylColorConflictsWithCustomChannel() {
        assertFalse(
            PluginStylePresent.set(
                "p",
                "focus",
                mapOf("vinyl" to mapOf("color" to "black", "base" to "#101010")),
            ),
        )
        assertTrue(PluginStylePresent.overlayMap().isEmpty())
    }

    @Test
    fun rangesClampPerSurface() {
        assertTrue(setFocus(mapOf("lyric" to mapOf("playedCount" to 99, "offsetX" to 150))))
        assertTrue(
            PluginStylePresent.set(
                "p",
                "focus",
                mapOf("lyric" to mapOf("playing" to mapOf("fontScale" to 9))),
            ),
        )
        assertTrue(
            PluginStylePresent.set(
                "p",
                "portrait",
                mapOf("lyric" to mapOf("playedCount" to 99)),
            ),
        )
        assertTrue(
            PluginStylePresent.set(
                "p",
                "dynamic",
                mapOf("lyric" to mapOf("offsetX" to 150)),
            ),
        )
        val focus = focusMap()
        val lyric = focus["lyric"] as Map<*, *>
        val playing = lyric["playing"] as Map<*, *>
        assertEquals(3, lyric["playedCount"])
        assertEquals(72f, lyric["offsetX"] as Float, 0.001f)
        assertEquals(1.5f, playing["fontScale"] as Float, 0.001f)
        val portrait = PluginStylePresent.overlayMap()["portrait"] as Map<*, *>
        assertEquals(10, (portrait["lyric"] as Map<*, *>)["playedCount"])
        val dynamic = PluginStylePresent.overlayMap()["dynamic"] as Map<*, *>
        assertEquals(150f, (dynamic["lyric"] as Map<*, *>)["offsetX"] as Float, 0.001f)
    }

    @Test
    fun illegalValueDoesNotClobberPreviousField() {
        assertTrue(setFocus(role("playing", mapOf("color" to "#112233"))))
        assertFalse(setFocus(role("playing", mapOf("color" to "#XYZ"))))
        assertFalse(setFocus(role("playing", mapOf("bold" to 1))))
        assertFalse(setFocus(mapOf("lyric" to mapOf("playedCount" to 1.5))))
        assertFalse(PluginStylePresent.set("p", "focus", mapOf("preview" to mapOf("enabled" to true))))
        assertFalse(PluginStylePresent.set("p", "dynamic", mapOf("vinyl" to mapOf("size" to 1.1))))
        assertFalse(PluginStylePresent.set("p", "nope", mapOf("uiScale" to 1)))
        assertFalse(PluginStylePresent.set("p", "focus", emptyMap<String, Any?>()))
        val playing = ((focusMap()["lyric"] as Map<*, *>)["playing"] as Map<*, *>)
        assertEquals("#112233", playing["color"])
        assertNull(playing["bold"])
    }

    @Test
    fun alphaColorRoundTrips() {
        assertTrue(setFocus(role("unplayed", mapOf("color" to "#80112233"))))
        val playing = ((focusMap()["lyric"] as Map<*, *>)["unplayed"] as Map<*, *>)
        assertEquals("#80112233", playing["color"])
        val painted = PluginStylePresent.paint(PlayerDisplayPrefs(), PluginStyleSurface.Focus)
        assertEquals(0x80112233.toInt(), painted.lyricUnplayedStyle.preset0Argb)
    }

    @Test
    fun mergeAndClearOneProperty() {
        assertTrue(setFocus(role("playing", mapOf("color" to "#112233", "bold" to false))))
        assertTrue(setFocus(role("playing", mapOf("italic" to true))))
        assertTrue(PluginStylePresent.clear("p", "focus", "lyric.playing.color"))
        val playing = ((focusMap()["lyric"] as Map<*, *>)["playing"] as Map<*, *>)
        assertNull(playing["color"])
        assertEquals(false, playing["bold"])
        assertEquals(true, playing["italic"])
        assertFalse(PluginStylePresent.clear("other", "focus", "lyric.playing.bold"))
        assertTrue(PluginStylePresent.clear("p", "focus", "lyric.playing"))
        assertFalse(PluginStylePresent.overlayMap().containsKey("focus"))
        assertFalse(PluginStylePresent.clear("p", "focus", "lyric.playing.bold"))
        assertFalse(PluginStylePresent.clear("p", "focus"))
    }

    @Test
    fun clearLastFieldRemovesSurface() {
        assertTrue(PluginStylePresent.set("p", "focus", mapOf("uiScale" to 1.1)))
        assertTrue(PluginStylePresent.clear("p", "focus", "uiScale"))
        assertFalse(PluginStylePresent.overlayMap().containsKey("focus"))
    }

    @Test
    fun laterSetTakesOwnership() {
        assertTrue(PluginStylePresent.set("a", "overlay", mapOf("currentColor" to "#FF0000")))
        assertTrue(PluginStylePresent.set("b", "overlay", mapOf("fontSize" to 20)))
        assertFalse(PluginStylePresent.clear("a", "overlay", "currentColor"))
        PluginStylePresent.clearIfOwner("a")
        assertTrue(PluginStylePresent.overlayMap().containsKey("overlay"))
        PluginStylePresent.clearIfOwner("b")
        assertTrue(PluginStylePresent.overlayMap().isEmpty())
    }

    @Test
    fun overlayPaintKeepsPlacementAndOtherColors() {
        val raw = LyricOverlayPrefs(posX = 12, locked = true, enabled = true)
        assertTrue(
            PluginStylePresent.set(
                "p",
                "overlay",
                mapOf("currentColor" to "#FF0000", "fontSize" to 20, "align" to "right"),
            ),
        )
        val shown = PluginStylePresent.paintOverlay(raw)
        assertEquals(12, shown.posX)
        assertTrue(shown.locked)
        assertTrue(shown.enabled)
        assertEquals(0xFFFF0000.toInt(), shown.currentColorArgb)
        assertEquals(20f, shown.fontSizeSp, 0.001f)
        assertEquals(LyricOverlayPrefs.ALIGN_RIGHT, shown.textAlign)
        assertEquals(raw.playedColorArgb, shown.playedColorArgb)
        assertEquals(raw.posY, shown.posY)
    }

    private fun setFocus(patch: Map<String, Any?>): Boolean =
        PluginStylePresent.set("p", "focus", patch)

    private fun role(name: String, fields: Map<String, Any?>): Map<String, Any?> =
        mapOf("lyric" to mapOf(name to fields))

    private fun focusMap(): Map<*, *> =
        PluginStylePresent.overlayMap()["focus"] as Map<*, *>
}
