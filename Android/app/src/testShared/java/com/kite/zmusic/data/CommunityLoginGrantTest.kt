package com.kite.zmusic.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class CommunityLoginGrantTest {
    @Test
    fun grantSidAndPathStayDistinctFromWebsiteLogin() {
        assertEquals(16, CommunityLoginConfig.APP_GRANT_SID.length)
        assertTrue(CommunityLoginConfig.APP_GRANT_SID.matches(Regex("^[A-Za-z0-9_-]+$")))
        assertEquals("app-direct-login", CommunityLoginConfig.APP_GRANT_SID)
        assertEquals("/api/v1/communities/zmusic/login/app", CommunityLoginConfig.APP_PATH)
        assertFalse(CommunityLoginConfig.APP_PATH == CommunityLoginConfig.SUBMIT_PATH)
        assertEquals(90L, CommunityLoginConfig.ASSERTION_TTL_SEC)
    }

    @Test
    fun clientAssertionUsesStringUidAndFixedFieldOrder() {
        val raw = CommunityJwt.hs256(
            iss = "zmusic",
            aud = CommunityLoginConfig.CLIENT_ID,
            sid = CommunityLoginConfig.APP_GRANT_SID,
            uid = "42",
            nickname = "曲",
            avatarUrl = "https://a.example/u.png",
            iat = 1_700_000_000L,
            exp = 1_700_000_000L + CommunityLoginConfig.ASSERTION_TTL_SEC,
            secretHex = CommunityLoginConfig.HMAC_SECRET_HEX,
        )
        val payload = String(
            Base64.getUrlDecoder().decode(raw.split('.')[1]),
            Charsets.UTF_8,
        )
        assertEquals(
            """{"iss":"zmusic","aud":"qp-zmusic","sid":"app-direct-login","uid":"42","nickname":"曲","avatar_url":"https://a.example/u.png","iat":1700000000,"exp":1700000090}""",
            payload,
        )
        assertFalse(payload.contains(""""uid":42"""))
    }

    @Test
    fun clientAssertionEscapesNickname() {
        val raw = CommunityJwt.hs256(
            iss = "zmusic",
            aud = CommunityLoginConfig.CLIENT_ID,
            sid = CommunityLoginConfig.APP_GRANT_SID,
            uid = "7",
            nickname = "a\"b\nc",
            avatarUrl = "",
            iat = 1_700_000_000L,
            exp = 1_700_000_090L,
            secretHex = CommunityLoginConfig.HMAC_SECRET_HEX,
        )
        val payload = String(Base64.getUrlDecoder().decode(raw.split('.')[1]), Charsets.UTF_8)
        assertTrue(payload.contains("\"nickname\":\"a\\\"b\\nc\""))
    }

    @Test
    fun scannerStillReadsWebsiteLoginCode() {
        val sid = "A".repeat(32)
        assertEquals(sid, CommunityLoginConfig.parseQr("ZMLOGIN1:$sid"))
        assertEquals(sid, CommunityLoginConfig.parseQr("  zmlogin1:$sid  "))
        assertNull(CommunityLoginConfig.parseQr(CommunityLoginConfig.APP_PATH))
        assertNull(CommunityLoginConfig.parseQr(""))
    }

    @Test
    fun durableTokenShapeMatchesServerHexToken() {
        val token = "a".repeat(64)
        assertTrue(CommunityLoginClient.TOKEN_RE.matches(token))
        assertTrue(CommunityLoginClient.TOKEN_RE.matches("A_token-16chars"))
        assertFalse(CommunityLoginClient.TOKEN_RE.matches("short"))
        assertFalse(CommunityLoginClient.TOKEN_RE.matches("a".repeat(129)))
        assertFalse(CommunityLoginClient.TOKEN_RE.matches("$token.extra"))
    }

    @Test
    fun grantResponseKeepsTokenOnlyWhenAccepted() {
        val token = "b".repeat(64)
        val direct = CommunityLoginClient.parseCommunitySubmit(
            """{"ok":true,"status":"consumed","app_token":"$token","uid":"42"}""",
        )
        assertTrue(direct.ok)
        assertEquals(token, direct.appToken)
        assertEquals("42", direct.uid)

        val wrapped = CommunityLoginClient.parseCommunitySubmit(
            """{"data":{"ok":true,"status":"consumed","app_token":"$token","uid":"5118104994"}}""",
        )
        assertEquals("5118104994", wrapped.uid)
        assertEquals(token, wrapped.appToken)

        val rejected = CommunityLoginClient.parseCommunitySubmit(
            """{"ok":false,"status":"forbidden","app_token":"$token","uid":"42"}""",
        )
        assertFalse(rejected.ok)
        assertNull(rejected.appToken)
        assertEquals("forbidden", rejected.status)

        val odd = CommunityLoginClient.parseCommunitySubmit(
            """{"ok":"true","status":"consumed","app_token":"not a token"}""",
        )
        assertTrue(odd.ok)
        assertNull(odd.appToken)
    }

    @Test
    fun blankTokenDoesNotCountAsLogin() {
        val token = "c".repeat(32)
        val saved = settleCommunityGrant(
            CommunitySubmitAck(ok = true, status = "consumed", appToken = token, uid = "42"),
        )
        assertTrue(saved.ok)
        assertEquals(token, saved.appToken)

        val blank = settleCommunityGrant(
            CommunitySubmitAck(ok = true, status = "consumed", appToken = "  ", uid = "42"),
        )
        assertFalse(blank.ok)
        assertEquals("forbidden", blank.status)

        val forbidden = settleCommunityGrant(
            CommunitySubmitAck(ok = false, status = "forbidden"),
        )
        assertFalse(forbidden.ok)
        assertEquals("forbidden", forbidden.status)
    }

    @Test
    fun unreadableGrantBodyFailsClosed() {
        try {
            CommunityLoginClient.parseCommunitySubmit("")
            throw AssertionError("blank body")
        } catch (e: IllegalStateException) {
            assertEquals("empty", e.message)
        }
        try {
            CommunityLoginClient.parseCommunitySubmit("not-json")
            throw AssertionError("bad json")
        } catch (e: IllegalStateException) {
            assertEquals("提交失败", e.message)
        }
    }
}
