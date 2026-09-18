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
