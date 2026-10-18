package com.kite.zmusic.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LiquidUnderlineTabsTest {
    private val slots = listOf(
        LiquidTabSlot(0f, 40f),
        LiquidTabSlot(80f, 110f),
        LiquidTabSlot(150f, 190f),
    )

    @Test
    fun restingLineMatchesTheLabel() {
        assertEquals(slots[0], liquidTabSpan(slots, 0f))
        assertEquals(slots[1], liquidTabSpan(slots, 1f))
        assertEquals(slots[2], liquidTabSpan(slots, 2f))
        assertEquals(slots[2], liquidTabSpan(slots, 6f))
        assertNull(liquidTabSpan(emptyList(), 0f))
    }

    @Test
    fun stretchStaysCenteredOnThePathBetweenLabels() {
        listOf(0.2f, 0.5f, 0.8f).forEach { t ->
            val span = liquidTabSpan(slots, t)!!
            val from = slots[0]
            val to = slots[1]
            val mid = from.center + (to.center - from.center) * t
            val center = (span.left + span.right) / 2f
            assertEquals(mid, center, 0.01f)
            assertEquals(center - span.left, span.right - center, 0.01f)
        }
    }

    @Test
    fun longerJumpsTakeLongerSoMiddleTabsAreCrossed() {
        assertEquals(340, liquidTabScrollMillis(1f))
        assertEquals(340, liquidTabScrollMillis(-1f))
        assertEquals(660, liquidTabScrollMillis(3f))
        assertEquals(920, liquidTabScrollMillis(8f))
        assertEquals(0, liquidTabScrollMillis(0f))
    }

    @Test
    fun midpointIsWiderThanEitherLabel() {
        val span = liquidTabSpan(slots, 0.5f)!!
        val width = span.right - span.left
        assertTrue(width > slots[0].right - slots[0].left)
        assertTrue(width > slots[1].right - slots[1].left)
    }

    @Test
    fun mvIsNotTreatedAsAnIdeograph() {
        assertTrue(labelHasIdeograph("歌曲"))
        assertFalse(labelHasIdeograph("MV"))
    }

    @Test
    fun latinLabelDropsToTheIdeographInkCenter() {
        val shifts = labelOpticalShiftPx(
            baselineFromTop = floatArrayOf(30f, 26f, 30f),
            inkCenterFromBaseline = floatArrayOf(-6f, -8f, -6f),
            ideograph = booleanArrayOf(true, false, true),
            maxShift = 20,
        )
        assertEquals(0, shifts[0])
        assertEquals(6, shifts[1])
        assertEquals(0, shifts[2])
    }

    @Test
    fun ideographLabelsStayOnTheirOwnLine() {
        val shifts = labelOpticalShiftPx(
            baselineFromTop = floatArrayOf(30f, 28f),
            inkCenterFromBaseline = floatArrayOf(-6f, -8f),
            ideograph = booleanArrayOf(true, true),
            maxShift = 20,
        )
        assertEquals(0, shifts[0])
        assertEquals(0, shifts[1])
    }

    @Test
    fun missingBaselineDoesNotMoveAnything() {
        val shifts = labelOpticalShiftPx(
            baselineFromTop = floatArrayOf(30f, 0f),
            inkCenterFromBaseline = floatArrayOf(-6f, -8f),
            ideograph = booleanArrayOf(true, false),
            maxShift = 20,
        )
        assertEquals(0, shifts[0])
        assertEquals(0, shifts[1])
    }

    @Test
    fun shiftStopsBeforeTheUnderline() {
        val shifts = labelOpticalShiftPx(
            baselineFromTop = floatArrayOf(40f, 10f),
            inkCenterFromBaseline = floatArrayOf(0f, 0f),
            ideograph = booleanArrayOf(true, false),
            maxShift = 4,
        )
        assertEquals(0, shifts[0])
        assertEquals(4, shifts[1])
    }
}
