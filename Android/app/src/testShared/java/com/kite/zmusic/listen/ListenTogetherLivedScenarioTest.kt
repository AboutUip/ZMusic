package com.kite.zmusic.listen

import com.kite.zmusic.playback.PlaybackLoadGate
import com.kite.zmusic.ui.player.seekProgressFraction
import com.kite.zmusic.ui.player.seekRewindDisplayDurationMs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 一起听「整房」情景：本机切歌决策 + 房间 LWW + poll 回声 + 进度条分母。
 * 对应空房间闪切、80% 暂停、歌词错位、客人曲末连跳。
 */
class ListenTogetherLivedScenarioTest {

    @Test
    fun emptyRoomNaturalEnd_roomAndHostStayOnNextSongAtZeroPlaying() {
        val room = ListenRoomSim()
        val host = ListenPeerSim(uid = "42", hosting = true)
        host.seedPlaying(trackId = 1L, pos = 178_500L, duration = 180_000L, cover = "a")

        host.ingest(20L, host.loading(2L, duration = 200_000L, cover = "b"))
        host.ingest(1_200L, host.ready(2L, pos = 0L, duration = 200_000L, cover = "b", playing = true))
        host.flush(room, nowMs = 1_200L)
        host.pollOwnEcho(room, nowMs = 1_400L)

        assertEquals(2L, room.clock.trackId)
        assertEquals(0L, room.clock.originMs)
        assertEquals(true, room.clock.playing)
        assertEquals("b", room.clock.coverUrl)
        assertEquals(2L, host.playerTrackId)
        assertEquals(true, host.playerPlaying)
        assertEquals(0L, host.playerPos)
        assertTrue(host.progress() < 0.02f)
        assertEquals("b", host.playerCover)
    }

    @Test
    fun emptyRoomOldPauseRace_coalescedTrackWinsAndOwnEchoDoesNotPauseAtEighty() {
        val room = ListenRoomSim()
        val host = ListenPeerSim(uid = "42", hosting = true)
        host.seedPlaying(trackId = 1L, pos = 178_500L, duration = 180_000L, cover = "a")

        host.ingest(5L, host.song(1L, 179_000L, playing = false, duration = 180_000L, cover = "a"))
        host.ingest(10L, host.loading(2L, duration = 200_000L, cover = "b"))
        host.ingest(800L, host.ready(2L, pos = 0L, duration = 200_000L, cover = "b", playing = true))
        host.flush(room, nowMs = 900L)
        host.pollOwnEcho(room, nowMs = 1_100L)

        assertEquals(2L, room.clock.trackId)
        assertEquals(true, room.clock.playing)
        assertEquals(0L, room.clock.originMs)
        assertFalse(room.clock.originMs >= 160_000L)
        assertEquals(true, host.playerPlaying)
        assertEquals(2L, host.playerTrackId)
    }

    @Test
    fun leftoverReadyOnShorterSong_doesNotPublishEightyOrPause() {
        val room = ListenRoomSim()
        val host = ListenPeerSim(uid = "42", hosting = true)
        host.seedPlaying(trackId = 1L, pos = 178_500L, duration = 240_000L, cover = "a")

        host.ingest(15L, host.loading(2L, duration = 120_000L, cover = "b"))
        host.ingest(40L, host.ready(2L, pos = 179_000L, duration = 120_000L, cover = "b", playing = false))
        host.ingest(90L, host.ready(2L, pos = 179_000L, duration = 120_000L, cover = "b", playing = true))
        host.flush(room, nowMs = 120L)

        assertEquals(2L, room.clock.trackId)
        assertEquals(0L, room.clock.originMs)
        assertEquals(true, room.clock.playing)
        assertTrue(room.clock.originMs < 2_000L)
        val lyricAt = lyricIndex(longArrayOf(0L, 10_000L, 90_000L), room.clock.originMs)
        assertEquals(0, lyricAt)
    }

    @Test
    fun guestFollowsHostSkip_coverProgressAndLyricsMatchNewSong() {
        val room = ListenRoomSim()
        val host = ListenPeerSim(uid = "42", hosting = true)
        val guest = ListenPeerSim(uid = "7", hosting = false)
        host.seedPlaying(trackId = 1L, pos = 40_000L, duration = 180_000L, cover = "a")
        guest.seedPlaying(trackId = 1L, pos = 40_000L, duration = 180_000L, cover = "a")
        host.flushSeed(room, nowMs = 1_000L)
        guest.poll(room, nowMs = 1_050L)

        host.ingest(2_000L, host.loading(2L, duration = 150_000L, cover = "b"))
        host.ingest(2_400L, host.ready(2L, pos = 0L, duration = 150_000L, cover = "b", playing = true))
        host.flush(room, nowMs = 2_500L)
        guest.poll(room, nowMs = 2_700L)

        assertEquals(2L, guest.playerTrackId)
        assertEquals("b", guest.playerCover)
        assertEquals(true, guest.playerPlaying)
        assertTrue(guest.playerPos < 3_000L)
        assertEquals(0, lyricIndex(longArrayOf(0L, 20_000L, 80_000L), guest.playerPos))
        assertEquals(2L, room.clock.trackId)
    }

    @Test
    fun guestNaturalEndDoesNotPostNextSong_hostAdvanceWins() {
        val room = ListenRoomSim()
        val host = ListenPeerSim(uid = "42", hosting = true)
        val guest = ListenPeerSim(uid = "7", hosting = false)
        host.seedPlaying(trackId = 1L, pos = 179_000L, duration = 180_000L, cover = "a")
        guest.seedPlaying(trackId = 1L, pos = 179_000L, duration = 180_000L, cover = "a")
        host.flushSeed(room, nowMs = 10L)

        assertEquals(true, ListenTogetherClock.followRemoteAdvance(inRoom = true, hosting = false))
        guest.memory = guest.memory.copy(lastPosMs = 179_000L, lastPosAt = 179_900L)
        guest.ingest(180_000L, guest.song(1L, 180_000L, playing = true, duration = 180_000L, cover = "a"))
        guest.flush(room, nowMs = 180_000L)
        assertEquals(1L, room.clock.trackId)

        host.ingest(180_200L, host.loading(2L, duration = 200_000L, cover = "b"))
        host.ingest(180_800L, host.ready(2L, pos = 0L, duration = 200_000L, cover = "b", playing = true))
        host.flush(room, nowMs = 180_900L)
        guest.poll(room, nowMs = 181_100L)

        assertEquals(2L, room.clock.trackId)
        assertEquals(2L, guest.playerTrackId)
        assertEquals("b", guest.playerCover)
    }

    @Test
    fun ifGuestAlsoAutoAdvances_lastWriteCanSkipTwoSongs() {
        val room = ListenRoomSim()
        val host = ListenPeerSim(uid = "42", hosting = true)
        val guest = ListenPeerSim(uid = "7", hosting = false)
        host.seedPlaying(1L, 179_000L, 180_000L, "a")
        guest.seedPlaying(1L, 179_000L, 180_000L, "a")
        host.flushSeed(room, nowMs = 10L)

        host.ingest(200L, host.ready(2L, 0L, 200_000L, "b", playing = true))
        guest.ingest(200L, guest.ready(3L, 0L, 190_000L, "c", playing = true))
        host.flush(room, nowMs = 1_000_000L)
        guest.flush(room, nowMs = 1_000_000L)

        assertEquals(3L, room.clock.trackId)
        assertEquals("c", room.clock.coverUrl)
    }

    @Test
    fun hostOwnEchoOfEndedPreviousSongIsDropped_noDoubleSkip() {
        val host = ListenPeerSim(uid = "42", hosting = true)
        host.playerTrackId = 2L
        host.playerPlaying = true
        host.playerPos = 800L
        host.appliedHlc = 10L
        val stale = ListenPlaybackClock(
            hlc = 40L,
            actor = "42",
            trackId = 1L,
            playing = true,
            originMs = 179_000L,
            originAt = 10L,
            durationMs = 180_000L,
            coverUrl = "a",
        )
        val take = ListenTogetherClock.takeRemoteClock(
            remoteHlc = stale.hlc,
            appliedHlc = host.appliedHlc,
            isMine = ListenTogetherClock.isOwnClock(stale.actor, host.uid),
            mismatch = ListenTogetherClock.playerNeedsClock(stale, host.playerTrackId, host.playerPlaying),
            applyingRemote = false,
        )
        assertEquals(false, take)
        assertEquals(2L, host.playerTrackId)
    }

    @Test
    fun skipToShorterSong_progressBarDoesNotHitOneHundredDuringRewind() {
        val animFrom = 180_000L
        val previousDur = 240_000L
        val nextDur = 120_000L
        val raw = seekProgressFraction(animFrom, nextDur)
        assertEquals(1f, raw, 0.001f)
        val held = seekRewindDisplayDurationMs(
            incomingDurationMs = nextDur,
            animPositionMs = animFrom.toFloat(),
            previousDurationMs = previousDur,
            holdDurationMs = 0L,
            trackChanged = true,
        )
        val shown = seekProgressFraction(animFrom, held)
        assertEquals(0.75f, shown, 0.01f)
        assertTrue(shown < 1f)
    }

    @Test
    fun emptyRoomMidSongSkip_postsPlayingNearZero() {
        val room = ListenRoomSim()
        val host = ListenPeerSim(uid = "42", hosting = true)
        host.seedPlaying(1L, 40_000L, 180_000L, "a")
        host.ingest(10L, host.loading(2L, 210_000L, "b"))
        host.ingest(400L, host.ready(2L, 0L, 210_000L, "b", playing = true))
        host.flush(room, 500L)
        assertEquals(2L, room.clock.trackId)
        assertEquals(0L, room.clock.originMs)
        assertEquals(true, room.clock.playing)
    }

    @Test
    fun userPauseEmptyRoom_keepsTrackAndOrigin() {
        val room = ListenRoomSim()
        val host = ListenPeerSim(uid = "42", hosting = true)
        host.seedPlaying(9L, 40_000L, 180_000L, "a")
        host.flushSeed(room, 100L)
        host.ingest(800L, host.song(9L, 41_200L, playing = false, duration = 180_000L, cover = "a"))
        host.flush(room, 900L)
        assertEquals(9L, room.clock.trackId)
        assertEquals(false, room.clock.playing)
        assertEquals(41_200L, room.clock.originMs)
    }

    @Test
    fun guestPauseThenHostTrack_trackWinsPlayingAtZero() {
        val room = ListenRoomSim()
        val host = ListenPeerSim(uid = "42", hosting = true)
        val guest = ListenPeerSim(uid = "7", hosting = false)
        host.seedPlaying(1L, 20_000L, 180_000L, "a")
        guest.seedPlaying(1L, 20_000L, 180_000L, "a")
        host.flushSeed(room, 50L)
        guest.poll(room, 60L)

        guest.ingest(80L, guest.song(1L, 21_000L, playing = false, duration = 180_000L, cover = "a"))
        guest.flush(room, 90L)
        host.ingest(100L, host.ready(2L, 0L, 160_000L, "b", playing = true))
        host.flush(room, 120L)
        guest.poll(room, 140L)

        assertEquals(2L, room.clock.trackId)
        assertEquals(true, room.clock.playing)
        assertEquals(0L, room.clock.originMs)
        assertEquals(2L, guest.playerTrackId)
        assertEquals("b", guest.playerCover)
    }

    @Test
    fun stalePauseWithoutTrackIdStillPoisons_compatDocumentsWhyClientSendsId() {
        val room = ListenRoomSim()
        room.apply(
            actor = "42",
            op = ListenPostedOp.track(2L, "B", "a", "b", 200_000L, 0L, true),
            nowMs = 10L,
        )
        room.apply(
            actor = "42",
            op = ListenPostedOp.pause(0L, 160_000L),
            nowMs = 20L,
        )
        assertEquals(2L, room.clock.trackId)
        assertEquals(false, room.clock.playing)
        assertEquals(160_000L, room.clock.originMs)
    }

    @Test
    fun stalePauseWithOldTrackIdIsIgnored() {
        val room = ListenRoomSim()
        room.apply(
            actor = "42",
            op = ListenPostedOp.track(2L, "B", "a", "b", 200_000L, 0L, true),
            nowMs = 10L,
        )
        room.apply(
            actor = "42",
            op = ListenPostedOp.pause(1L, 179_000L),
            nowMs = 20L,
        )
        assertEquals(2L, room.clock.trackId)
        assertEquals(true, room.clock.playing)
        assertEquals(0L, room.clock.originMs)
    }

    @Test
    fun oldMediaReadyIsGatedSoListenDoesNotSeeSettledLeftover() {
        assertEquals(false, PlaybackLoadGate.canCommitReady(true, "1", 2L))
        assertEquals(true, PlaybackLoadGate.canCommitReady(true, "2", 2L))
    }

    @Test
    fun interpolationDoesNotNeedStreamedSecondsAfterSkip() {
        val clock = ListenPlaybackClock(
            playing = true,
            originMs = 0L,
            originAt = 1_000L,
            durationMs = 180_000L,
            trackId = 2L,
        )
        assertEquals(1_500L, ListenTogetherClock.interpolate(clock, 2_500L))
        assertEquals(0L, ListenTogetherClock.interpolate(clock.copy(playing = false), 9_000L))
    }

    @Test
    fun rapidDoubleSkip_lastTrackCoverWins() {
        val room = ListenRoomSim()
        val host = ListenPeerSim(uid = "42", hosting = true)
        host.seedPlaying(1L, 10_000L, 180_000L, "a")
        host.ingest(10L, host.ready(2L, 0L, 200_000L, "b", playing = true))
        host.flush(room, 20L)
        host.ingest(40L, host.ready(3L, 0L, 190_000L, "c", playing = true))
        host.flush(room, 50L)
        assertEquals(3L, room.clock.trackId)
        assertEquals("c", room.clock.coverUrl)
        assertEquals(0L, room.clock.originMs)
        assertEquals(true, room.clock.playing)
    }

    @Test
    fun repeatOneWrapPostsSeekZeroNotNewTrack() {
        val room = ListenRoomSim()
        val host = ListenPeerSim(uid = "42", hosting = true)
        host.seedPlaying(4L, 179_000L, 180_000L, "a")
        host.flushSeed(room, 10L)
        host.ingest(50L, host.song(4L, 0L, playing = true, duration = 180_000L, cover = "a"))
        host.flush(room, 60L)
        assertEquals(4L, room.clock.trackId)
        assertEquals("seek", host.lastFlushedKind)
        assertEquals(0L, room.clock.originMs)
        assertEquals(true, room.clock.playing)
    }

    @Test
    fun suppressAfterApplyingGuestDoesNotEchoLocalSkip() {
        val guest = ListenPeerSim(uid = "7", hosting = false)
        guest.seedPlaying(1L, 0L, 180_000L, "a")
        guest.suppressUntil = 15_000L
        guest.ingest(100L, guest.ready(9L, 0L, 120_000L, "x", playing = true))
        assertEquals(null, guest.pending)
        assertEquals(9L, guest.memory.lastTrackId)
    }

    @Test
    fun leftoverOriginWouldPickWrongLyricLineUntilSanitized() {
        val lines = longArrayOf(0L, 12_000L, 150_000L)
        assertEquals(2, lyricIndex(lines, 179_000L))
        val origin = ListenTogetherClock.originMsForNewTrack(1L, 0L, 2L, 179_000L, 200_000L)
        assertEquals(0L, origin)
        assertEquals(0, lyricIndex(lines, origin))
    }

    @Test
    fun emptyRoomNaturalEndThenPoll_interpolatedPositionStaysNearStart() {
        val room = ListenRoomSim()
        val host = ListenPeerSim(uid = "42", hosting = true)
        host.seedPlaying(1L, 179_000L, 180_000L, "a")
        host.ingest(20L, host.loading(2L, 200_000L, "b"))
        host.ingest(80L, host.ready(2L, 0L, 200_000L, "b", playing = true))
        host.flush(room, nowMs = 1_000L)
        val later = ListenTogetherClock.interpolate(room.clock, 2_200L)
        assertTrue(later < 2_000L)
        assertEquals(2L, room.clock.trackId)
    }
}

private class ListenRoomSim {
    var clock = ListenPlaybackClock()

    fun apply(actor: String, op: ListenPostedOp, nowMs: Long) {
        if (listenOpStaleMirror(clock, op)) return
        val hlc = tickHlcMirror(clock.hlc, nowMs)
        val next = when (op.kind) {
            "play" -> clock.copy(
                hlc = hlc,
                actor = actor,
                originAt = nowMs,
                playing = true,
                originMs = ListenTogetherClock.clampOrigin(op.originMs, clock.durationMs),
            )
            "pause" -> clock.copy(
                hlc = hlc,
                actor = actor,
                originAt = nowMs,
                playing = false,
                originMs = ListenTogetherClock.clampOrigin(op.originMs, clock.durationMs),
            )
            "seek" -> clock.copy(
                hlc = hlc,
                actor = actor,
                originAt = nowMs,
                originMs = ListenTogetherClock.clampOrigin(op.originMs, clock.durationMs),
                playing = op.playing,
            )
            "track" -> clock.copy(
                hlc = hlc,
                actor = actor,
                originAt = nowMs,
                trackId = op.trackId,
                title = op.title,
                artists = op.artists,
                coverUrl = op.coverUrl,
                durationMs = op.durationMs.coerceAtLeast(0L),
                originMs = ListenTogetherClock.clampOrigin(op.originMs, op.durationMs),
                playing = op.playing,
            )
            else -> return
        }
        if (next.hlc != clock.hlc) {
            if (next.hlc < clock.hlc) return
        } else if (next.actor <= clock.actor) {
            return
        }
        clock = next
    }
}

private class ListenPeerSim(
    val uid: String,
    val hosting: Boolean,
) {
    var memory = ListenLocalMemory()
    var pending: ListenPostedOp? = null
    var appliedHlc = 0L
    var lastPostedHlc = 0L
    var suppressUntil = 0L
    var playerTrackId = 0L
    var playerPos = 0L
    var playerDur = 0L
    var playerPlaying = false
    var playerCover = ""
    var lastFlushedKind: String = ""

    fun seedPlaying(trackId: Long, pos: Long, duration: Long, cover: String) {
        memory = ListenLocalMemory(
            lastTrackId = trackId,
            lastPlayWhenReady = true,
            lastPosMs = pos,
            lastPosAt = 0L,
            lastHasQueue = true,
        )
        playerTrackId = trackId
        playerPos = pos
        playerDur = duration
        playerPlaying = true
        playerCover = cover
    }

    fun song(
        trackId: Long,
        pos: Long,
        playing: Boolean,
        duration: Long,
        cover: String,
        loading: Boolean = false,
    ) = ListenLocalSnap(
        trackId = trackId,
        title = "t$trackId",
        artists = "a",
        coverUrl = cover,
        durationMs = duration,
        positionMs = pos,
        playWhenReady = playing,
        loadPending = loading,
        hasQueue = true,
    )

    fun loading(trackId: Long, duration: Long, cover: String) =
        song(trackId, 0L, playing = false, duration = duration, cover = cover, loading = true)

    fun ready(trackId: Long, pos: Long, duration: Long, cover: String, playing: Boolean) =
        song(trackId, pos, playing, duration, cover, loading = false)

    fun ingest(now: Long, snap: ListenLocalSnap) {
        val (next, effect) = decideLocalPlayback(
            inRoom = true,
            applyingRemote = false,
            nowElapsed = now,
            suppressUntil = suppressUntil,
            hosting = hosting,
            memory = memory,
            snap = snap,
        )
        memory = next
        playerTrackId = snap.trackId
        playerPos = snap.positionMs
        playerDur = snap.durationMs
        playerPlaying = snap.playWhenReady
        playerCover = snap.coverUrl
        when (effect) {
            ListenLocalEffect.Hold, ListenLocalEffect.EndHostRoom -> Unit
            is ListenLocalEffect.Post -> pending = mergeListenOp(pending, effect.op)
        }
    }

    fun flush(room: ListenRoomSim, nowMs: Long) {
        val op = pending ?: return
        pending = null
        lastFlushedKind = op.kind
        room.apply(uid, op, nowMs)
        lastPostedHlc = room.clock.hlc
        appliedHlc = maxOf(appliedHlc, room.clock.hlc)
        playerTrackId = room.clock.trackId
        playerCover = room.clock.coverUrl
        playerPlaying = room.clock.playing
        playerDur = room.clock.durationMs
        playerPos = room.clock.originMs
    }

    fun flushSeed(room: ListenRoomSim, nowMs: Long) {
        pending = ListenPostedOp.track(
            trackId = playerTrackId,
            title = "t$playerTrackId",
            artists = "a",
            coverUrl = playerCover,
            durationMs = playerDur,
            originMs = playerPos,
            playing = playerPlaying,
        )
        flush(room, nowMs)
    }

    fun poll(room: ListenRoomSim, nowMs: Long) {
        val clock = room.clock
        val mismatch = ListenTogetherClock.playerNeedsClock(clock, playerTrackId, playerPlaying)
        val isMine = ListenTogetherClock.isOwnClock(clock.actor, uid)
        if (!ListenTogetherClock.takeRemoteClock(clock.hlc, appliedHlc, isMine, mismatch, applyingRemote = false)) {
            appliedHlc = maxOf(appliedHlc, clock.hlc)
            return
        }
        appliedHlc = maxOf(appliedHlc, clock.hlc)
        val pos = ListenTogetherClock.interpolate(clock, nowMs)
        playerTrackId = clock.trackId
        playerCover = clock.coverUrl
        playerPlaying = clock.playing
        playerDur = clock.durationMs
        playerPos = pos
        suppressUntil = nowMs + 500L
        memory = memory.copy(
            lastTrackId = clock.trackId,
            lastPlayWhenReady = clock.playing,
            lastPosMs = pos,
            lastPosAt = nowMs,
            carryOverMs = -1L,
        )
    }

    fun pollOwnEcho(room: ListenRoomSim, nowMs: Long) = poll(room, nowMs)

    fun progress(): Float = seekProgressFraction(playerPos, playerDur.coerceAtLeast(1L))
}

private fun listenOpStaleMirror(cur: ListenPlaybackClock, op: ListenPostedOp): Boolean {
    if (op.kind == "track") return false
    if (op.trackId <= 0L || cur.trackId <= 0L) return false
    return op.trackId != cur.trackId
}

private fun tickHlcMirror(last: Long, nowMs: Long): Long {
    val t = nowMs.coerceAtLeast(0L)
    return if (t > last) t else last + 1
}

private fun lyricIndex(lineAt: LongArray, positionMs: Long): Int {
    var ans = -1
    for (i in lineAt.indices) {
        if (lineAt[i] <= positionMs) ans = i
    }
    return ans
}
