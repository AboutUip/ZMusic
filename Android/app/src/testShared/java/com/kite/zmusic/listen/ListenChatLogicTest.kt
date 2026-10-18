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
    fun songSyncKeepsFacesWhenMembersOrAvatarsAreMissing() {
        val previous = listOf(ListenMember("42", "曲", "https://a.example/u.png", true))
        val omitted = ListenTogetherClient.parseSnapshot(
            """{"ok":true,"id":"r1","host_uid":42,"clock":{"track_id":9}}""",
        )
        assertFalse(omitted.membersIncluded)
        assertEquals(
            previous,
            resolveListenMembers(previous, omitted.members, omitted.membersIncluded),
        )
        val blank = ListenTogetherClient.parseSnapshot(
            """{"ok":true,"id":"r1","members":[{"uid":42,"nickname":"曲","avatar_url":null,"host":true}]}""",
        )
        assertTrue(blank.membersIncluded)
        assertEquals("", blank.members.single().avatarUrl)
        assertEquals(
            "https://a.example/u.png",
            resolveListenMembers(previous, blank.members, blank.membersIncluded).single().avatarUrl,
        )
        val relative = ListenTogetherClient.parseSnapshot(
            """{"ok":true,"id":"r1","members":[{"uid":7,"nickname":"客","avatarUrl":"//p1.music.126.net/a.jpg","host":false}]}""",
        )
        assertEquals("https://p1.music.126.net/a.jpg", relative.members.single().avatarUrl)
        val chat = preserveListenChatFaces(
            chat = listOf(msg(3, "42", "这首歌")),
            previous = emptyList(),
            members = previous,
        )
        assertEquals("https://a.example/u.png", chat.single().avatarUrl)
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
        assertEquals(true, ListenTogetherClock.isOwnClock("042", "42"))
        assertEquals(true, ListenTogetherClock.isOwnClock("42", "042"))
        assertEquals(false, ListenTogetherClock.isOwnClock("42", ""))
        assertEquals(false, ListenTogetherClock.isOwnClock("7", "42"))
        assertTrue(ListenTogetherClock.DRIFT_MS <= 1_000L)
        assertTrue(ListenTogetherClock.DRIFT_CHECK_MS <= 2_500L)
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
    fun reportedPositionKeepsUiDuringBufferingSeek() {
        assertEquals(null, listenReportedPositionMs(buffering = true, playerPositionMs = 10_000L, uiPositionMs = 40_000L))
        assertEquals(null, listenReportedPositionMs(buffering = true, playerPositionMs = 0L, uiPositionMs = 40_000L))
        assertEquals(40_000L, listenReportedPositionMs(buffering = false, playerPositionMs = 10_000L, uiPositionMs = 40_000L))
        assertEquals(120L, listenReportedPositionMs(buffering = true, playerPositionMs = 120L, uiPositionMs = 80L))
    }

    @Test
    fun bufferingRewindDoesNotPublishSeek() {
        val memory = ListenLocalMemory(
            lastTrackId = 1L,
            lastPlayWhenReady = true,
            lastPosMs = 40_000L,
            lastPosAt = 1_000L,
            lastHasQueue = true,
        )
        val snap = ListenLocalSnap(
            trackId = 1L,
            positionMs = 10_000L,
            playWhenReady = true,
            hasQueue = true,
            buffering = true,
        )
        val (held, effect) = decideLocalPlayback(
            inRoom = true,
            applyingRemote = false,
            nowElapsed = 1_200L,
            suppressUntil = 0L,
            hosting = false,
            memory = memory,
            snap = snap,
        )
        assertTrue(effect is ListenLocalEffect.Hold)
        assertEquals(40_000L, held.lastPosMs)
        val (echo, echoEffect) = decideLocalPlayback(
            inRoom = true,
            applyingRemote = true,
            nowElapsed = 1_200L,
            suppressUntil = 0L,
            hosting = false,
            memory = memory,
            snap = snap,
        )
        assertTrue(echoEffect is ListenLocalEffect.Hold)
        assertEquals(40_000L, echo.lastPosMs)
    }

    @Test
    fun translateDoesNotStickToOlderDuplicate() {
        val older = msg(10, "42", "hello")
        val local = msg(-2, "42", "hello").copy(at = 20L)
        val echo = msg(11, "42", "hello").copy(at = 21L)
        val cached = putListenChatTranslate(
            emptyMap(),
            local,
            ListenChatTranslateEntry(text = "你好", visible = true),
        )
        val next = retargetListenChatTranslations(cached, listOf(older, local), listOf(older, echo))
        assertEquals(null, listenChatTranslateLookup(next, older))
        assertEquals("你好", listenChatTranslateLookup(next, echo)!!.text)
        assertEquals(null, next[listenChatToastKey(local)])
    }

    @Test
    fun translateCommitFollowsRetargetAndDoesNotReopen() {
        val local = msg(-1, "42", "hello")
        val remote = msg(88, "42", "hello")
        val cached = putListenChatTranslate(
            emptyMap(),
            local,
            ListenChatTranslateEntry(loading = true, visible = true),
        )
        val moved = retargetListenChatTranslations(cached, listOf(local), listOf(remote))
        val done = commitListenChatTranslate(
            moved,
            local,
            ListenChatTranslateEntry(text = "你好", visible = true),
        )
        assertEquals(null, done[listenChatToastKey(local)])
        assertEquals("你好", listenChatTranslateLookup(done, remote)!!.text)
        val hidden = hideListenChatTranslate(done, remote)
        val late = commitListenChatTranslate(
            hidden,
            local,
            ListenChatTranslateEntry(text = "你好", visible = true),
        )
        assertEquals(false, listenChatTranslateLookup(late, remote)!!.visible)
        assertEquals("你好", listenChatTranslateLookup(late, remote)!!.text)
        assertEquals(null, listenChatTranslateLookup(late, msg(89, "42", "hello")))
    }

    @Test
    fun twoPendingTranslationsClaimDistinctEchoes() {
        val first = msg(-1, "42", "hello").copy(at = 1L)
        val second = msg(-2, "42", "hello").copy(at = 2L)
        val echoA = msg(10, "42", "hello").copy(at = 3L)
        val echoB = msg(11, "42", "hello").copy(at = 4L)
        var map = putListenChatTranslate(emptyMap(), first, ListenChatTranslateEntry(text = "A", visible = true))
        map = putListenChatTranslate(map, second, ListenChatTranslateEntry(text = "B", visible = true))
        val next = retargetListenChatTranslations(map, listOf(first, second), listOf(echoA, echoB))
        assertEquals("A", listenChatTranslateLookup(next, echoA)!!.text)
        assertEquals("B", listenChatTranslateLookup(next, echoB)!!.text)
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
        val stickerId = "AbcdEfghijklmnopqr12"
        val sticker = encodeListenChatSticker(stickerId)
        val stickerPack = parseListenChatPack(sticker)
        assertEquals(ListenChatPackKind.Sticker, stickerPack!!.kind)
        assertEquals(stickerId, stickerPack.value)
        assertEquals("表情包", listenChatVisibleBody(sticker))
        assertNull(parseListenChatPack("\u200B\u200BZMpack|sticker|../etc/passwd"))
        assertNull(parseListenChatPack("\u200B\u200BZMpack|sticker|short"))
        val stickerMsg = msg(4, "7", sticker)
        val stickerQuote = listenChatQuoteFromMsg(stickerMsg.copy(nickname = "曲"))
        assertEquals(stickerId, listenChatQuoteStickerId(stickerQuote.snippet))
        val quotedSticker = encodeListenChatText(stickerQuote, "好看")
        val quotedParsed = parseListenChatText(quotedSticker)
        assertEquals(stickerId, listenChatQuoteStickerId(quotedParsed.reply!!.snippet))
        assertEquals(stickerId, listenChatActionStickerId(sticker))
        assertEquals(stickerId, listenChatActionStickerId(quotedSticker))
        assertEquals("好看", listenChatVisibleBody(quotedSticker))
        assertNull(listenChatQuoteStickerId("表情包"))
        assertEquals("🎲 4", listenChatQuoteSnippet(dice))
        val png = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0,
        )
        assertEquals("image/png", listenChatStickerMime(png))
        assertEquals("image/gif", listenChatStickerMime("GIF89a".encodeToByteArray() + ByteArray(8)))
        assertEquals(
            "image/jpeg",
            listenChatStickerMime(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()) + ByteArray(9)),
        )
        assertEquals("image/webp", listenChatStickerMime("RIFF".encodeToByteArray() + ByteArray(4) + "WEBP".encodeToByteArray()))
        assertNull(listenChatStickerMime("not-an-image!!".encodeToByteArray()))
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
    fun groupReadUsesSendTimePeersAndShowsPartial() {
        val members = listOf(
            ListenMember("42", "曲", "", true),
            ListenMember("7", "客", "", false),
            ListenMember("9", "乙", "", false),
            ListenMember("11", "丙", "", false),
        )
        val sentWith = listOf("7", "9")
        val partial = listenChatReadReceipt(2, "42", sentWith, members, mapOf("7" to 2L))
        assertEquals(1, partial.count)
        assertEquals(2, partial.total)
        assertFalse(partial.complete)
        val both = listenChatReadReceipt(2, "42", sentWith, members, mapOf("7" to 2L, "9" to 3L))
        assertTrue(both.complete)
        val left = listenChatReadReceipt(
            2,
            "42",
            sentWith,
            members.filter { it.uid != "9" },
            mapOf("7" to 2L),
        )
        assertEquals(1, left.total)
        assertTrue(left.complete)
        val solo = listenChatReadReceipt(1, "42", emptyList(), members, emptyMap())
        assertEquals(0, solo.total)
        assertFalse(solo.complete)
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
