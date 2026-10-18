package com.kite.zmusic.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackQueueOrderTest {
    @Test
    fun moveEarlierSongLeavesCurrentOnTheSameTrack() {
        val queue = mutable("A", "B", "C", "D", "E")
        val index = apply(queue, index = 2, from = 1, to = 3)
        assertEquals(listOf("A", "C", "D", "B", "E"), queue)
        assertEquals(1, index)
        assertEquals("C", queue[index])
    }

    @Test
    fun moveLaterSongLeavesCurrentOnTheSameTrack() {
        val queue = mutable("A", "B", "C", "D", "E")
        val index = apply(queue, index = 2, from = 3, to = 1)
        assertEquals(listOf("A", "D", "B", "C", "E"), queue)
        assertEquals(3, index)
        assertEquals("C", queue[index])
    }

    @Test
    fun movingCurrentSongKeepsPlaybackOnIt() {
        val down = mutable("A", "B", "C", "D", "E")
        val downIndex = apply(down, index = 2, from = 2, to = 4)
        assertEquals(listOf("A", "B", "D", "E", "C"), down)
        assertEquals(4, downIndex)
        assertEquals("C", down[downIndex])

        val up = mutable("A", "B", "C", "D", "E")
        val upIndex = apply(up, index = 2, from = 2, to = 0)
        assertEquals(listOf("C", "A", "B", "D", "E"), up)
        assertEquals(0, upIndex)
        assertEquals("C", up[upIndex])
    }

    @Test
    fun everyItemFollowsTheSameMap() {
        val original = listOf("A", "B", "C", "D", "E")
        val from = 1
        val to = 3
        val moved = reorderQueue(original, from, to)!!
        original.indices.forEach { index ->
            assertEquals(original[index], moved[queueIndexAfterMove(index, from, to)])
        }
    }

    @Test
    fun sameSpotAndOutOfRangeDoNotChangeTheQueue() {
        val queue = listOf("A", "B")
        assertNull(reorderQueue(queue, 0, 0))
        assertNull(reorderQueue(queue, -1, 0))
        assertNull(reorderQueue(queue, 0, 2))
        assertEquals(2, queueIndexAfterMove(2, 2, 2))
    }

    @Test
    fun dragPreviewShiftsOnlyTheRowsBetween() {
        assertEquals(0, queueDragRowShift(1, from = 1, to = 3))
        assertEquals(-1, queueDragRowShift(2, from = 1, to = 3))
        assertEquals(-1, queueDragRowShift(3, from = 1, to = 3))
        assertEquals(0, queueDragRowShift(0, from = 1, to = 3))
        assertEquals(1, queueDragRowShift(1, from = 3, to = 1))
        assertEquals(1, queueDragRowShift(2, from = 3, to = 1))
        assertEquals(0, queueDragRowShift(3, from = 3, to = 1))
        assertEquals(0, queueDragRowShift(4, from = 1, to = 1))
    }

    @Test
    fun playNextInsertsAfterTheCurrentSong() {
        val queued = queuePlayNext(listOf("A", "B", "C"), 0, "D") { it } as QueuePlayNext.Queued
        assertEquals(listOf("A", "D", "B", "C"), queued.queue)
        assertEquals(0, queued.index)
        assertEquals(1, queued.insertedAt)
        assertEquals("A", queued.queue[queued.index])

        val atEnd = queuePlayNext(listOf("A", "B", "C"), 2, "D") { it } as QueuePlayNext.Queued
        assertEquals(listOf("A", "B", "C", "D"), atEnd.queue)
        assertEquals(2, atEnd.index)
    }

    @Test
    fun playNextMovesAnExistingSongToTheSlotAfterCurrent() {
        val before = queuePlayNext(listOf("A", "B", "C"), 1, "A") { it } as QueuePlayNext.Queued
        assertEquals(listOf("B", "A", "C"), before.queue)
        assertEquals(0, before.index)
        assertEquals("B", before.queue[before.index])

        val after = queuePlayNext(listOf("A", "B", "C"), 0, "C") { it } as QueuePlayNext.Queued
        assertEquals(listOf("A", "C", "B"), after.queue)
        assertEquals(0, after.index)
        assertEquals(1, after.insertedAt)
    }

    @Test
    fun playNextLeavesCurrentAndImmediateNextAlone() {
        assertTrue(queuePlayNext(listOf("A", "B"), 0, "A") { it } is QueuePlayNext.AlreadyPlaying)
        assertTrue(queuePlayNext(listOf("A", "B", "C"), 1, "C") { it } is QueuePlayNext.AlreadyNext)
        assertNull(queuePlayNext(emptyList<String>(), 0, "A") { it })
    }

    private fun mutable(vararg ids: String) = ids.toMutableList()

    private fun apply(queue: MutableList<String>, index: Int, from: Int, to: Int): Int {
        val next = reorderQueue(queue, from, to)!!
        queue.clear()
        queue.addAll(next)
        return queueIndexAfterMove(index, from, to)
    }
}
