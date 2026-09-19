package com.kite.zmusic.listen

import org.junit.Assert.assertEquals
import org.junit.Test

class ListenChatLogicTest {
    private fun msg(id: Long, uid: String, text: String) = ListenChatMsg(
        id = id,
        uid = uid,
        nickname = uid,
        avatarUrl = "",
        text = text,
        at = id,
    )

    @Test
    fun unreadIgnoresSelfAndAlreadyRead() {
        val chat = listOf(
            msg(1, "7", "hi"),
            msg(2, "42", "mine"),
            msg(3, "7", "again"),
        )
        assertEquals(2, listenUnreadChatCount(chat, "42", 0))
        assertEquals(1, listenUnreadChatCount(chat, "42", 1))
        assertEquals(0, listenUnreadChatCount(chat, "42", 3))
        assertEquals(1, listenUnreadChatCount(chat, "7", 0))
        assertEquals(0, listenUnreadChatCount(chat, "042", 3))
        assertEquals(2, listenUnreadChatCount(chat, "042", 0))
    }

    @Test
    fun selfUidMatchesNumericString() {
        assertEquals(true, listenChatUidEquals("42", "42"))
        assertEquals(true, listenChatUidEquals("42", "042"))
        assertEquals(false, listenChatUidEquals("42", "7"))
        assertEquals(false, listenChatUidEquals("", "42"))
    }

    @Test
    fun mergeKeepsOwnPendingWhenSnapshotOmitsChat() {
        val mine = msg(-1, "42", "hello")
        val other = msg(2, "7", "hi")
        val prev = listOf(other, mine)
        val kept = mergeListenRoomChat(prev, emptyList(), incomingIncluded = false, selfUid = "42")
        assertEquals(prev, kept)
    }

    @Test
    fun mergeKeepsOwnBubbleIfServerEchoIsMissing() {
        val mine = msg(-1, "42", "hello")
        val other = msg(2, "7", "hi")
        val incoming = listOf(other, msg(3, "7", "again"))
        val merged = mergeListenRoomChat(
            previous = listOf(other, mine),
            incoming = incoming,
            incomingIncluded = true,
            selfUid = "42",
        )
        assertEquals(3, merged.size)
        assertEquals(true, merged.any { it.text == "hello" && listenChatUidEquals(it.uid, "42") })
    }

    @Test
    fun mergeDropsPendingOnceServerEchoes() {
        val mine = msg(-1, "42", "hello")
        val echoed = msg(9, "42", "hello")
        val merged = mergeListenRoomChat(
            previous = listOf(mine),
            incoming = listOf(echoed),
            incomingIncluded = true,
            selfUid = "42",
        )
        assertEquals(listOf(echoed), merged)
    }

    @Test
    fun mergeKeepsDuplicatePendingUntilEachEchoArrives() {
        val first = msg(-1, "42", "哈哈")
        val second = msg(-2, "42", "哈哈")
        val echoed = msg(9, "42", "哈哈")
        val merged = mergeListenRoomChat(
            previous = listOf(first, second),
            incoming = listOf(echoed),
            incomingIncluded = true,
            selfUid = "42",
        )
        assertEquals(2, merged.size)
        assertEquals(true, merged.any { it.id == 9L })
        assertEquals(true, merged.any { it.id < 0L && it.text == "哈哈" })
    }

    @Test
    fun mergeUnionsDeltaIncomingWithExistingHistory() {
        val older = msg(2, "7", "hi")
        val mine = msg(3, "42", "hello")
        val newer = msg(4, "7", "again")
        val merged = mergeListenRoomChat(
            previous = listOf(older, mine),
            incoming = listOf(newer),
            incomingIncluded = true,
            selfUid = "42",
        )
        assertEquals(listOf(older, mine, newer), merged)
    }

    @Test
    fun pendingCountsAsSelfAndRetargetsToEcho() {
        val pending = msg(-1, "42", "hello")
        val echoed = msg(9, "42", "hello")
        assertEquals(true, listenChatIsSelf(pending, "42"))
        assertEquals(true, listenChatIsSelf(echoed, "042"))
        assertEquals(false, listenChatIsSelf(msg(3, "7", "hi"), "42"))
        assertEquals(echoed, retargetListenChatToast(pending, listOf(echoed)))
        assertEquals(pending, listenChatKeepToastWhileReading(pending, "42"))
        assertEquals(null, listenChatKeepToastWhileReading(msg(3, "7", "hi"), "42"))
    }

    @Test
    fun parseSnapshotOmittingChatKeepsChatExcluded() {
        val snap = ListenTogetherClient.parseSnapshot(
            """{"ok":true,"id":"r1","host_uid":42,"chat":null}""",
        )
        assertEquals(false, snap.chatIncluded)
        val omitted = ListenTogetherClient.parseSnapshot(
            """{"ok":true,"id":"r1"}""",
        )
        assertEquals(false, omitted.chatIncluded)
        val empty = ListenTogetherClient.parseSnapshot(
            """{"ok":true,"id":"r1","chat":[]}""",
        )
        assertEquals(true, empty.chatIncluded)
        assertEquals(true, empty.chat.isEmpty())
    }

    @Test
    fun parseChatReadsNumericUid() {
        val snap = ListenTogetherClient.parseSnapshot(
            """{"ok":true,"id":"r1","host_uid":7,"chat":[{"id":1,"uid":42,"nickname":"我","text":"hi","at":1}]}""",
        )
        assertEquals("42", snap.chat.single().uid)
        assertEquals("7", snap.hostUid)
        assertEquals(true, snap.chatIncluded)
    }

    @Test
    fun bubbleKeepsShortAndEllipsizesLong() {
        assertEquals("你好", listenChatBubbleText("你好"))
        val twenty = "一二三四五六七八九十一二三四五六七八九十"
        assertEquals(twenty, listenChatBubbleText(twenty))
        assertEquals("$twenty...", listenChatBubbleText(twenty + "超"))
        assertEquals("hello...", listenChatBubbleText("hello world", 5))
    }

    @Test
    fun playerNeedsClockWhenTrackOrPlayingDiffers() {
        val clock = ListenPlaybackClock(trackId = 9L, playing = true)
        assertEquals(false, ListenTogetherClock.playerNeedsClock(clock, 9L, true))
        assertEquals(true, ListenTogetherClock.playerNeedsClock(clock, 8L, true))
        assertEquals(true, ListenTogetherClock.playerNeedsClock(clock, 9L, false))
        assertEquals(false, ListenTogetherClock.playerNeedsClock(ListenPlaybackClock(), 1L, true))
    }

    @Test
    fun shouldApplyOnlyNewerHlc() {
        assertEquals(true, ListenTogetherClock.shouldApply(2L, 1L))
        assertEquals(false, ListenTogetherClock.shouldApply(1L, 1L))
        assertEquals(false, ListenTogetherClock.shouldApply(1L, 2L))
    }

    @Test
    fun takeRemoteClockFollowsWhoeverChanged() {
        assertEquals(
            false,
            ListenTogetherClock.takeRemoteClock(
                remoteHlc = 10L,
                appliedHlc = 9L,
                isMine = true,
                mismatch = true,
                applyingRemote = false,
            ),
        )
        assertEquals(
            false,
            ListenTogetherClock.takeRemoteClock(
                remoteHlc = 8L,
                appliedHlc = 9L,
                isMine = false,
                mismatch = true,
                applyingRemote = false,
            ),
        )
        assertEquals(
            true,
            ListenTogetherClock.takeRemoteClock(
                remoteHlc = 10L,
                appliedHlc = 9L,
                isMine = false,
                mismatch = false,
                applyingRemote = true,
            ),
        )
        assertEquals(
            true,
            ListenTogetherClock.takeRemoteClock(
                remoteHlc = 9L,
                appliedHlc = 9L,
                isMine = false,
                mismatch = true,
                applyingRemote = false,
            ),
        )
        assertEquals(
            false,
            ListenTogetherClock.takeRemoteClock(
                remoteHlc = 9L,
                appliedHlc = 9L,
                isMine = false,
                mismatch = true,
                applyingRemote = true,
            ),
        )
    }

    @Test
    fun ownClockNeverAppliesEvenWhenHlcIsNewerThanLastPost() {
        // postOp 尚未写回 lastPostedHlc 时，poll 已带回自己的 pause/track。
        assertEquals(true, ListenTogetherClock.isOwnClock("42", "42"))
        assertEquals(false, ListenTogetherClock.isOwnClock("42", ""))
        assertEquals(false, ListenTogetherClock.isOwnClock("7", "42"))
        assertEquals(
            false,
            ListenTogetherClock.takeRemoteClock(
                remoteHlc = 50L,
                appliedHlc = 10L,
                isMine = true,
                mismatch = true,
                applyingRemote = false,
            ),
        )
    }

    @Test
    fun guestsFollowRemoteAdvanceHostsDoNot() {
        assertEquals(true, ListenTogetherClock.followRemoteAdvance(inRoom = true, hosting = false))
        assertEquals(false, ListenTogetherClock.followRemoteAdvance(inRoom = true, hosting = true))
        assertEquals(false, ListenTogetherClock.followRemoteAdvance(inRoom = false, hosting = false))
    }

    @Test
    fun newTrackOriginDropsLeftoverProgressFromPreviousSong() {
        assertEquals(
            0L,
            ListenTogetherClock.originMsForNewTrack(
                previousTrackId = 1L,
                previousPositionMs = 179_000L,
                newTrackId = 2L,
                positionMs = 179_000L,
                durationMs = 200_000L,
            ),
        )
        assertEquals(
            0L,
            ListenTogetherClock.originMsForNewTrack(
                previousTrackId = 1L,
                previousPositionMs = 180_000L,
                newTrackId = 2L,
                positionMs = 148_000L,
                durationMs = 150_000L,
            ),
        )
        assertEquals(
            800L,
            ListenTogetherClock.originMsForNewTrack(
                previousTrackId = 1L,
                previousPositionMs = 179_000L,
                newTrackId = 2L,
                positionMs = 800L,
                durationMs = 200_000L,
            ),
        )
        assertEquals(
            90_000L,
            ListenTogetherClock.originMsForNewTrack(
                previousTrackId = 1L,
                previousPositionMs = 90_000L,
                newTrackId = 1L,
                positionMs = 90_000L,
                durationMs = 200_000L,
            ),
        )
        assertEquals(
            0L,
            ListenTogetherClock.originMsForNewTrack(
                previousTrackId = 1L,
                previousPositionMs = 0L,
                newTrackId = 2L,
                positionMs = 179_000L,
                durationMs = 200_000L,
            ),
        )
    }

    @Test
    fun inviteRejectedIncludesDeclineAndTimeout() {
        assertEquals(true, listenInviteIsRejected("declined"))
        assertEquals(true, listenInviteIsRejected("timeout"))
        assertEquals(false, listenInviteIsRejected("pending"))
        assertEquals(false, listenInviteIsRejected("accepted"))
    }

    @Test
    fun parseInviteBoxReadsPeerAndStatus() {
        val box = ListenTogetherClient.parseInviteBox(
            """
            {"ok":true,"incoming":{"id":"abc","room_id":"room1","status":"pending",
            "from":{"uid":"42","nickname":"曲","avatar_url":"https://a.example/u.png"},
            "to":{"uid":"7","nickname":"客","avatar_url":""},
            "expires_at":1700000060000,"expires_in":60}}
            """.trimIndent(),
        )
        val incoming = box.incoming!!
        assertEquals("abc", incoming.id)
        assertEquals("42", incoming.from.uid)
        assertEquals("曲", incoming.from.nickname)
        assertEquals("https://a.example/u.png", incoming.from.avatarUrl)
        assertEquals("pending", incoming.status)
        assertEquals(60L, incoming.expiresIn)
        assertEquals(null, box.outgoing)
    }
}
