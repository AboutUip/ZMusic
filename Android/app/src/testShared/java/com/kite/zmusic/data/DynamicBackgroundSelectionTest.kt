package com.kite.zmusic.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DynamicBackgroundSelectionTest {

    @Test
    fun focusAndDynamicKeepSeparateChoicesOverSharedPresets() {
        val prefs = PlayerDisplayPrefs(
            customBackgroundEnabled = true,
            dynamicCustomBackgroundEnabled = false,
            backgroundPresets = listOf(
                PlayerBackgroundPreset(imagePath = "/focus.jpg", locked = true),
                PlayerBackgroundPreset(imagePath = "/dynamic.jpg", locked = true),
            ),
            backgroundPresetIndex = 0,
            dynamicBackgroundPresetIndex = 1,
        )
        assertEquals("/focus.jpg", prefs.resolvedCustomBackground()?.imagePath)
        assertNull(prefs.resolvedCustomBackground(dynamic = true))

        val dynamicOn = prefs.copy(dynamicCustomBackgroundEnabled = true)
        assertEquals("/dynamic.jpg", dynamicOn.resolvedCustomBackground(dynamic = true)?.imagePath)

        val edited = dynamicOn.withBackgroundPresetAt(
            1,
            PlayerBackgroundPreset(imagePath = "/shared.jpg", locked = true, scale = 1.4f),
            dynamic = true,
        )
        assertEquals(0, edited.backgroundPresetIndex)
        assertEquals(1, edited.dynamicBackgroundPresetIndex)
        assertEquals(1.4f, edited.backgroundPresets[1].scale, 0.01f)
        assertEquals("/shared.jpg", edited.resolvedCustomBackground()?.imagePath)
        assertEquals("/shared.jpg", edited.resolvedCustomBackground(dynamic = true)?.imagePath)
    }
}
