package com.kite.zmusic.listen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

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
        assertEquals(null, listenChatKeepToastWhileReading(pending, "42"))
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
    fun toastStackCapsAtTwoAndKeepsNewest() {
        val a = ListenChatToast("r1", msg(1, "7", "hi"))
        val b = ListenChatToast("r2", msg(2, "7", "again"))
        val c = msg(3, "42", "third")
        val stacked = pushListenChatToasts(listOf(a, b), listOf(c))
        assertEquals(2, stacked.size)
        assertEquals("r2", stacked.first().key)
        assertEquals("r3", stacked.last().key)
        assertEquals("third", stacked.last().msg.text)
    }

    @Test
    fun toastRetargetKeepsKeyWhenPendingEchoes() {
        val pending = ListenChatToast("l-1", msg(-1, "42", "hello"))
        val echoed = msg(9, "42", "hello")
        val next = pushListenChatToasts(listOf(pending), listOf(echoed))
        assertEquals(1, next.size)
        assertEquals("l-1", next.single().key)
        assertEquals(9L, next.single().msg.id)
    }

    @Test
    fun toastReadingClearsSelfAndOthers() {
        val mine = ListenChatToast("r1", msg(1, "42", "me"))
        val other = ListenChatToast("r2", msg(2, "7", "them"))
        val kept = listenChatKeepToastsWhileReading(listOf(mine, other), "42")
        assertEquals(emptyList<ListenChatToast>(), kept)
        assertEquals(listOf(other), dismissListenChatToast(listOf(mine, other), "r1"))
        val pending = ListenChatToast("l-1", msg(-1, "42", "hello"))
        val echoed = pending.copy(msg = msg(9, "42", "hello"))
        assertEquals(emptyList<ListenChatToast>(), dismissListenChatToast(listOf(echoed), "l-1"))
    }

    @Test
    fun closingSheetDoesNotReplaySelfBubble() {
        val mine = msg(-1, "42", "hello")
        val echoed = msg(9, "42", "hello")
        assertEquals(
            null,
            listenChatSelfToastForClosedSheet(
                lastSelf = mine,
                current = emptyList(),
                overlayed = emptySet(),
                nowMs = mine.at + 500L,
            ),
        )
        val overlayed = listenChatOverlayedTokens(listOf(mine))
        assertEquals(true, listenChatIsOverlayed(mine, overlayed))
        assertEquals(true, listenChatIsOverlayed(echoed, overlayed))
        assertEquals(
            emptyList<ListenChatMsg>(),
            listenChatIncomingOverlay(listOf(echoed), overlayed),
        )
        val other = msg(10, "7", "hi")
        assertEquals(listOf(other), listenChatIncomingOverlay(listOf(echoed, other), overlayed))
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

    @Test
    fun endingEmptyRoomKeepsPlayingOccupiedRoomPauses() {
        assertEquals(false, listenShouldPauseOnRoomEnd(0))
        assertEquals(false, listenShouldPauseOnRoomEnd(1))
        assertEquals(true, listenShouldPauseOnRoomEnd(2))
        assertEquals(true, listenShouldPauseOnRoomEnd(3))
    }

    @Test
    fun translateCacheHideKeepsTextForReuse() {
        val m = msg(9, "7", "hello")
        val shown = putListenChatTranslate(
            emptyMap(),
            m,
            ListenChatTranslateEntry(text = "你好", visible = true),
        )
        val hidden = hideListenChatTranslate(shown, m)
        val entry = listenChatTranslateLookup(hidden, m)!!
        assertEquals("你好", entry.text)
        assertEquals(false, entry.visible)
        val again = putListenChatTranslate(
            hidden,
            m,
            entry.copy(visible = true),
        )
        assertEquals(true, listenChatTranslateLookup(again, m)!!.visible)
    }

    @Test
    fun translateCacheRetargetsLocalIdToServerId() {
        val local = msg(-1, "42", "hello")
        val remote = msg(88, "42", "hello")
        val cached = putListenChatTranslate(
            emptyMap(),
            local,
            ListenChatTranslateEntry(text = "こんにちは", visible = true),
        )
        val next = retargetListenChatTranslations(cached, listOf(local), listOf(remote))
        assertEquals(null, next[listenChatToastKey(local)])
        assertEquals("こんにちは", next[listenChatToastKey(remote)]!!.text)
        assertEquals("こんにちは", listenChatTranslateLookup(next, remote)!!.text)
    }

    @Test
    fun replyEncodeDecodeRoundTrip() {
        val quote = ListenChatReplyQuote(
            msgId = 9,
            uid = "7",
            nickname = "曲",
            snippet = "hello world",
        )
        val wire = encodeListenChatText(quote, "好的")
        val parsed = parseListenChatText(wire)
        assertEquals(9L, parsed.reply!!.msgId)
        assertEquals("7", parsed.reply!!.uid)
        assertEquals("曲", parsed.reply!!.nickname)
        assertEquals("hello world", parsed.reply!!.snippet)
        assertEquals("好的", parsed.body)
        assertEquals("好的", listenChatVisibleBody(wire))
    }

    @Test
    fun bubbleTextUsesVisibleBodyWithoutReplyHeader() {
        val wire = encodeListenChatText(
            ListenChatReplyQuote(1, "7", "A", "long original text here"),
            "short reply",
        )
        assertEquals("short reply", listenChatBubbleText(wire))
    }

    @Test
    fun clockLabelUsesHourForTodayAndDateOtherwise() {
        val zone = ZoneOffset.UTC
        val now = 1_760_400_000_000L
        assertEquals("00:00", listenChatClockLabel(now, now, zone))
        assertEquals("10/13 00:00", listenChatClockLabel(now - 86_400_000L, now, zone))
        assertEquals("", listenChatClockLabel(0L, now, zone))
    }

    @Test
    fun packRoundTripAndVisibleLabel() {
        val dice = encodeListenChatPack(ListenChatPackKind.Dice, "4")
        val parsed = parseListenChatPack(dice)
        assertEquals(ListenChatPackKind.Dice, parsed!!.kind)
        assertEquals("4", parsed.value)
        assertEquals("🎲 4", listenChatVisibleBody(dice))
        assertEquals("🎲 4", listenChatBubbleText(dice))
        val quoted = encodeListenChatText(
            ListenChatReplyQuote(3, "7", "曲", "在吗"),
            encodeListenChatPack(ListenChatPackKind.Rps, "scissors"),
        )
        val body = parseListenChatText(quoted)
        assertEquals(3L, body.reply!!.msgId)
        assertEquals(ListenChatPackKind.Rps, parseListenChatPack(body.body)!!.kind)
        assertEquals("✌️ 剪刀", listenChatVisibleBody(quoted))
        assertNull(parseListenChatPack("hello"))
    }

    @Test
    fun packMotionLandsOnTheRolledFace() {
        assertEquals("4", listenChatPackMotionValue(ListenChatPackKind.Dice, 1f, "4"))
        assertEquals("paper", listenChatPackMotionValue(ListenChatPackKind.Rps, 0.9f, "paper"))
        val tumbling = listenChatPackMotionValue(ListenChatPackKind.Dice, 0.2f, "4")
        assertTrue(tumbling.toInt() in 1..6)
        val pose = listenChatPackPose(0.3f)
        assertTrue(pose.scaleX in 0.07f..1f)
        assertEquals(1f, listenChatPackPose(1f).scaleX, 0.01f)
        assertFalse(listenChatPackPose(1f).edgeOn)
    }

    @Test
    fun readCheckNeedsEveryOtherMember() {
        val members = listOf(
            ListenMember("42", "曲", "", true),
            ListenMember("7", "客", "", false),
        )
        assertFalse(listenChatReadByPeers(1, "42", members, emptyMap()))
        assertFalse(listenChatReadByPeers(1, "42", members, mapOf("7" to 0L)))
        assertTrue(listenChatReadByPeers(1, "42", members, mapOf("7" to 1L)))
        assertTrue(listenChatReadByPeers(1, "42", members, mapOf("007" to 2L)))
        assertFalse(listenChatReadByPeers(-1, "42", members, mapOf("7" to 9L)))
        assertFalse(
            listenChatReadByPeers(1, "42", listOf(members.first()), mapOf("42" to 1L)),
        )
        val three = members + ListenMember("9", "乙", "", false)
        assertFalse(listenChatReadByPeers(2, "42", three, mapOf("7" to 2L)))
        assertTrue(listenChatReadByPeers(2, "42", three, mapOf("7" to 2L, "9" to 2L)))
    }

    @Test
    fun mergeReadsKeepsHigherCursorAndDropsMissingPeers() {
        val previous = mapOf("7" to 4L, "9" to 2L)
        val incoming = mapOf("7" to 3L, "8" to 1L)
        val merged = mergeListenChatReads(previous, incoming, incomingIncluded = true)
        assertEquals(4L, merged["7"])
        assertEquals(1L, merged["8"])
        assertFalse(merged.containsKey("9"))
        assertEquals(previous, mergeListenChatReads(previous, emptyMap(), incomingIncluded = false))
    }
}
