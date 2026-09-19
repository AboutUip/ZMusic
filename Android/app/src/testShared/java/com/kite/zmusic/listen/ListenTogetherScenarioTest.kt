package com.kite.zmusic.listen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 一起听切歌竞态的情景回放。不依赖 ExoPlayer / 网络，把本机 UI 快照喂给决策函数。
 */
class ListenTogetherScenarioTest {
    private fun song(
        id: Long,
        pos: Long,
        playing: Boolean,
        loading: Boolean = false,
        duration: Long = 180_000L,
        title: String = "t$id",
        hasQueue: Boolean = true,
    ) = ListenLocalSnap(
        trackId = id,
        title = title,
        artists = "a",
        coverUrl = "https://cover/$id",
        durationMs = duration,
        positionMs = pos,
        playWhenReady = playing,
        loadPending = loading,
        hasQueue = hasQueue,
    )

    private fun playingANearEnd() = ListenLocalMemory(
        lastTrackId = 1L,
        lastPlayWhenReady = true,
        lastPosMs = 178_500L,
        lastPosAt = 0L,
        lastHasQueue = true,
    )

    @Test
    fun emptyRoomNaturalEndPostsSingleTrackAtZeroWhilePlaying() {
        val ops = drain(
            playingANearEnd(),
            listOf(
                10L to song(1, 179_000, playing = true, loading = false),
                20L to song(2, 0, playing = false, loading = true, duration = 200_000L),
                80L to song(2, 0, playing = false, loading = true, duration = 200_000L),
                1_200L to song(2, 0, playing = true, loading = false, duration = 200_000L),
            ),
        )
        assertEquals(listOf("track"), ops.map { it.kind })
        val track = ops.single()
        assertEquals(2L, track.trackId)
        assertEquals(0L, track.originMs)
        assertEquals(true, track.playing)
        assertEquals("https://cover/2", track.coverUrl)
    }

    @Test
    fun skipDoesNotPostPauseOnOldSongWhenLoadPendingIsSetFirst() {
        val ops = drain(
            playingANearEnd(),
            listOf(
                5L to song(2, 0, playing = false, loading = true, duration = 240_000L),
                900L to song(2, 400, playing = true, loading = false, duration = 240_000L),
            ),
        )
        assertEquals(1, ops.size)
        assertEquals("track", ops[0].kind)
        assertEquals(2L, ops[0].trackId)
        assertEquals(400L, ops[0].originMs)
        assertEquals(true, ops[0].playing)
    }

    @Test
    fun oldBugPauseBeforeLoadPendingIsCoalescedIntoTrack() {
        val ops = drain(
            playingANearEnd(),
            listOf(
                5L to song(1, 179_000, playing = false, loading = false),
                10L to song(2, 0, playing = false, loading = true, duration = 200_000L),
                800L to song(2, 0, playing = true, loading = false, duration = 200_000L),
            ),
        )
        assertEquals(listOf("track"), ops.map { it.kind })
        assertEquals(2L, ops[0].trackId)
        assertEquals(0L, ops[0].originMs)
        assertEquals(true, ops[0].playing)
    }

    @Test
    fun leftoverReadyOnNewSongDoesNotPublishEightyPercentOrigin() {
        val ops = drain(
            playingANearEnd(),
            listOf(
                15L to song(2, 0, playing = false, loading = true, duration = 200_000L),
                40L to song(2, 179_000, playing = false, loading = false, duration = 200_000L),
                90L to song(2, 179_000, playing = true, loading = false, duration = 200_000L),
            ),
        )
        assertEquals(listOf("track"), ops.map { it.kind })
        assertEquals(0L, ops[0].originMs)
        assertEquals(true, ops[0].playing)
        assertTrue(ops[0].originMs < 2_000L)
    }

    @Test
    fun leftoverOriginWouldDesyncLyricsAndProgressUntilSanitized() {
        val lineAt = longArrayOf(0L, 12_000L, 150_000L)
        val badPos = 179_000L
        assertEquals(2, lyricIndex(lineAt, badPos))
        assertEquals(0, lyricIndex(lineAt, 0L))
        assertNotEquals(lyricIndex(lineAt, badPos), lyricIndex(lineAt, 0L))
        val origin = ListenTogetherClock.originMsForNewTrack(1L, 179_000L, 2L, badPos, 200_000L)
        assertEquals(0L, origin)
        assertEquals(0, lyricIndex(lineAt, origin))
        assertEquals(0.895, 179_000.0 / 200_000.0, 0.01)
    }

    @Test
    fun playAfterCarryOverDoesNotSeekNewSongToOldPosition() {
        var mem = playingANearEnd()
        val loading = decide(mem, 10L, song(2, 0, playing = false, loading = true, duration = 200_000L))
        mem = loading.first
        val settled = decide(mem, 40L, song(2, 179_000, playing = false, loading = false, duration = 200_000L))
        mem = settled.first
        val track = (settled.second as ListenLocalEffect.Post).op
        assertEquals(0L, track.originMs)
        val resumed = decide(mem, 90L, song(2, 179_000, playing = true, loading = false, duration = 200_000L))
        val play = (resumed.second as ListenLocalEffect.Post).op
        assertEquals("play", play.kind)
        assertEquals(0L, play.originMs)
    }

    @Test
    fun sequentialPauseThenTrackWithoutMergeKeepsPauseOnOldSong() {
        val posted = sequential(
            playingANearEnd(),
            listOf(
                5L to song(1, 179_000, playing = false, loading = false),
                800L to song(2, 0, playing = true, loading = false, duration = 200_000L),
            ),
        )
        assertEquals(listOf("pause", "track"), posted.map { it.kind })
        assertEquals(1L, posted[0].trackId)
        assertEquals(179_000L, posted[0].originMs)
        assertEquals(2L, posted[1].trackId)
        assertEquals(0L, posted[1].originMs)
        assertEquals(true, posted[1].playing)
    }

    @Test
    fun mergeDropsStalePauseForPreviousTrackAfterQueuedTrack() {
        val track = ListenPostedOp.track(2L, "n", "a", "c", 200_000L, 0L, true)
        val pauseOld = ListenPostedOp.pause(1L, 179_000L)
        val merged = mergeListenOp(track, pauseOld)
        assertEquals("track", merged.kind)
        assertEquals(2L, merged.trackId)
        assertEquals(true, merged.playing)
        assertEquals(0L, merged.originMs)
    }

    @Test
    fun mergeLetsSeekUpdateQueuedTrackOrigin() {
        val track = ListenPostedOp.track(2L, "n", "a", "c", 200_000L, 0L, true)
        val seek = ListenPostedOp.seek(2L, 12_000L, true)
        val merged = mergeListenOp(track, seek)
        assertEquals(12_000L, merged.originMs)
        assertEquals(true, merged.playing)
    }

    @Test
    fun userPauseOnSameSongStillPostsPause() {
        val ops = drain(
            ListenLocalMemory(
                lastTrackId = 9L,
                lastPlayWhenReady = true,
                lastPosMs = 40_000L,
                lastPosAt = 0L,
                lastHasQueue = true,
            ),
            listOf(10L to song(9, 41_200, playing = false)),
        )
        assertEquals(listOf("pause"), ops.map { it.kind })
        assertEquals(9L, ops[0].trackId)
        assertEquals(41_200L, ops[0].originMs)
    }

    @Test
    fun userSeekPostsJump() {
        val ops = drain(
            ListenLocalMemory(
                lastTrackId = 9L,
                lastPlayWhenReady = true,
                lastPosMs = 1_000L,
                lastPosAt = 0L,
                lastHasQueue = true,
            ),
            listOf(200L to song(9, 80_000, playing = true)),
        )
        assertEquals(listOf("seek"), ops.map { it.kind })
        assertEquals(80_000L, ops[0].originMs)
    }

    @Test
    fun loadPendingSuppressesTransportUntilReady() {
        val first = decide(
            ListenLocalMemory(lastTrackId = 1L, lastPlayWhenReady = true, lastPosMs = 10_000L, lastHasQueue = true),
            5L,
            song(1, 10_000, playing = false, loading = true),
        )
        assertEquals(ListenLocalEffect.Hold, first.second)
        assertEquals(true, first.first.lastPlayWhenReady)
        assertEquals(1L, first.first.lastTrackId)
    }

    @Test
    fun remoteApplySwallowsLocalEchoOfSameTrackChange() {
        val mem = playingANearEnd()
        val (next, effect) = decideLocalPlayback(
            inRoom = true,
            applyingRemote = true,
            nowElapsed = 50L,
            suppressUntil = 10_000L,
            hosting = true,
            memory = mem,
            snap = song(2, 0, playing = true),
        )
        assertEquals(ListenLocalEffect.Hold, effect)
        assertEquals(2L, next.lastTrackId)
        assertEquals(true, next.lastPlayWhenReady)
    }

    @Test
    fun hostEmptyQueueEndsRoomOnce() {
        val mem = ListenLocalMemory(lastTrackId = 1L, lastPlayWhenReady = true, lastHasQueue = true)
        val (next, effect) = decideLocalPlayback(
            inRoom = true,
            applyingRemote = false,
            nowElapsed = 20L,
            suppressUntil = 0L,
            hosting = true,
            memory = mem,
            snap = song(1, 0, playing = false, hasQueue = false),
        )
        assertEquals(ListenLocalEffect.EndHostRoom, effect)
        assertEquals(false, next.lastHasQueue)
    }

    @Test
    fun guestEmptyQueueDoesNotEndRoom() {
        val mem = ListenLocalMemory(lastTrackId = 1L, lastHasQueue = true)
        val (_, effect) = decideLocalPlayback(
            inRoom = true,
            applyingRemote = false,
            nowElapsed = 20L,
            suppressUntil = 0L,
            hosting = false,
            memory = mem,
            snap = song(1, 0, playing = false, hasQueue = false),
        )
        assertEquals(ListenLocalEffect.Hold, effect)
    }

    @Test
    fun interpolatingOwnClockAtDurationWouldRetriggerEnded() {
        val clock = ListenPlaybackClock(
            trackId = 1L,
            playing = true,
            originMs = 0L,
            originAt = 0L,
            durationMs = 180_000L,
        )
        assertEquals(180_000L, ListenTogetherClock.interpolate(clock, 190_000L))
        assertEquals(
            true,
            ListenTogetherClock.playerNeedsClock(clock.copy(trackId = 1L, playing = false), 2L, true),
        )
    }

    @Test
    fun applyingOwnPauseEchoWouldPauseNewSongAtEightyPercent() {
        val remote = ListenPlaybackClock(
            hlc = 99L,
            actor = "42",
            trackId = 2L,
            playing = false,
            originMs = 160_000L,
            originAt = 1_000L,
            durationMs = 200_000L,
        )
        assertEquals(false, ListenTogetherClock.takeRemoteClock(99L, 10L, isMine = true, mismatch = true, applyingRemote = false))
        assertEquals(160_000L, ListenTogetherClock.interpolate(remote, 1_000L))
        assertEquals(0.8, 160_000.0 / 200_000.0, 0.001)
    }

    @Test
    fun applyingStaleEndedClockOfPreviousSongWouldDoubleSkip() {
        val staleA = ListenPlaybackClock(
            hlc = 40L,
            actor = "7",
            trackId = 1L,
            playing = true,
            originMs = 179_000L,
            originAt = 10L,
            durationMs = 180_000L,
        )
        assertEquals(true, ListenTogetherClock.playerNeedsClock(staleA, 2L, true))
        assertEquals(180_000L, ListenTogetherClock.interpolate(staleA, 5_000L))
        assertEquals(true, ListenTogetherClock.takeRemoteClock(40L, 10L, isMine = false, mismatch = true, applyingRemote = false))
        assertEquals(false, ListenTogetherClock.takeRemoteClock(40L, 10L, isMine = true, mismatch = true, applyingRemote = false))
    }

    @Test
    fun midSongJoinKeepsOriginWhenSameTrack() {
        val origin = ListenTogetherClock.originMsForNewTrack(
            previousTrackId = 9L,
            previousPositionMs = 55_000L,
            newTrackId = 9L,
            positionMs = 55_400L,
            durationMs = 200_000L,
        )
        assertEquals(55_400L, origin)
    }

    @Test
    fun coverUrlFollowsTrackOpNotPreviousSong() {
        val ops = drain(
            playingANearEnd(),
            listOf(800L to song(2, 0, playing = true, duration = 200_000L, title = "next")),
        )
        assertEquals("https://cover/2", ops.single().coverUrl)
        assertEquals("next", ops.single().title)
        assertNotEquals("https://cover/1", ops.single().coverUrl)
    }

    @Test
    fun coalescedQueueFlushesLatestIntentOnly() {
        var pending: ListenPostedOp? = null
        pending = mergeListenOp(pending, ListenPostedOp.pause(1L, 179_000L))
        pending = mergeListenOp(pending, ListenPostedOp.track(2L, "n", "a", "c", 200_000L, 0L, true))
        pending = mergeListenOp(pending, ListenPostedOp.play(2L, 179_000L))
        val op = pending!!
        assertEquals("track", op.kind)
        assertEquals(2L, op.trackId)
        assertEquals(true, op.playing)
        assertEquals(0L, op.originMs)
    }

    @Test
    fun suppressWindowIgnoresLocalSkipAfterRemoteApply() {
        val (_, effect) = decideLocalPlayback(
            inRoom = true,
            applyingRemote = false,
            nowElapsed = 100L,
            suppressUntil = 15_000L,
            hosting = true,
            memory = playingANearEnd(),
            snap = song(3, 0, playing = true),
        )
        assertEquals(ListenLocalEffect.Hold, effect)
    }

    @Test
    fun notInRoomNeverPosts() {
        val (_, effect) = decideLocalPlayback(
            inRoom = false,
            applyingRemote = false,
            nowElapsed = 10L,
            suppressUntil = 0L,
            hosting = true,
            memory = playingANearEnd(),
            snap = song(2, 0, playing = true),
        )
        assertEquals(ListenLocalEffect.Hold, effect)
    }

    @Test
    fun repeatOneSeekToZeroIsSeekNotTrack() {
        val ops = drain(
            ListenLocalMemory(
                lastTrackId = 4L,
                lastPlayWhenReady = true,
                lastPosMs = 179_000L,
                lastPosAt = 0L,
                lastHasQueue = true,
            ),
            listOf(50L to song(4, 0, playing = true, duration = 180_000L)),
        )
        assertEquals(listOf("seek"), ops.map { it.kind })
        assertEquals(0L, ops[0].originMs)
        assertEquals(4L, ops[0].trackId)
    }

    @Test
    fun emptyRoomMidSongSkipPostsSinglePlayingTrack() {
        val ops = drain(
            ListenLocalMemory(
                lastTrackId = 1L,
                lastPlayWhenReady = true,
                lastPosMs = 40_000L,
                lastPosAt = 0L,
                lastHasQueue = true,
            ),
            listOf(
                5L to song(2, 0, playing = false, loading = true, duration = 210_000L),
                400L to song(2, 0, playing = true, loading = false, duration = 210_000L),
            ),
        )
        assertEquals(listOf("track"), ops.map { it.kind })
        assertEquals(0L, ops[0].originMs)
        assertEquals(true, ops[0].playing)
        assertEquals(2L, ops[0].trackId)
    }

    @Test
    fun guestHostSameTickWouldNeedFollowModeToAvoidDoubleTrack() {
        assertEquals(true, ListenTogetherClock.followRemoteAdvance(true, false))
        assertEquals(false, ListenTogetherClock.followRemoteAdvance(true, true))
    }

    private fun decide(
        memory: ListenLocalMemory,
        now: Long,
        snap: ListenLocalSnap,
        hosting: Boolean = true,
    ) = decideLocalPlayback(
        inRoom = true,
        applyingRemote = false,
        nowElapsed = now,
        suppressUntil = 0L,
        hosting = hosting,
        memory = memory,
        snap = snap,
    )

    /** 同一排水窗口内合成一拍，模拟 postOp 串行前的 pending 队列。 */
    private fun drain(
        start: ListenLocalMemory,
        frames: List<Pair<Long, ListenLocalSnap>>,
        hosting: Boolean = true,
    ): List<ListenPostedOp> {
        var mem = start
        var pending: ListenPostedOp? = null
        for ((now, snap) in frames) {
            val (next, effect) = decide(mem, now, snap, hosting)
            mem = next
            when (effect) {
                ListenLocalEffect.Hold, ListenLocalEffect.EndHostRoom -> Unit
                is ListenLocalEffect.Post -> pending = mergeListenOp(pending, effect.op)
            }
        }
        return listOfNotNull(pending)
    }

    /** 每拍立刻发出，模拟旧逻辑并发 postOp。 */
    private fun sequential(
        start: ListenLocalMemory,
        frames: List<Pair<Long, ListenLocalSnap>>,
        hosting: Boolean = true,
    ): List<ListenPostedOp> {
        var mem = start
        val out = ArrayList<ListenPostedOp>()
        for ((now, snap) in frames) {
            val (next, effect) = decide(mem, now, snap, hosting)
            mem = next
            if (effect is ListenLocalEffect.Post) out += effect.op
        }
        return out
    }

    private fun lyricIndex(lineAt: LongArray, positionMs: Long): Int {
        var ans = -1
        for (i in lineAt.indices) {
            if (lineAt[i] <= positionMs) ans = i
        }
        return ans
    }
}
