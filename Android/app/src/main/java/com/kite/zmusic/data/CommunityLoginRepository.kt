package com.kite.zmusic.data

import com.kite.zmusic.workshop.WorkshopAuthStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class CommunityLoginPreview(
    val sid: String,
    val uid: Long,
    val nickname: String,
    val avatarUrl: String?,
)

class CommunityLoginRepository(
    private val sessionRepository: SessionRepository,
    private val authClient: NcmAuthClient,
    private val userClient: NcmUserClient,
    private val communityServerStore: CommunityServerStore,
    private val workshopAuthStore: WorkshopAuthStore,
    private val client: CommunityLoginClient = CommunityLoginClient(),
) {
    suspend fun preview(sid: String): CommunityLoginPreview {
        val session = sessionRepository.session.value
            ?: error("请先登录网易云账号")
        if (session.isGuest) error("游客无法授权，请先登录")
        val status = authClient.loginStatus(session.cookie)
        val uid = NcmJson.userIdFromLoginStatus(status) ?: 0L
        if (uid <= 0L) error("当前账号无法授权")
        var nickname = NcmJson.displayLabelFromLogin(status).orEmpty()
        var avatar = avatarFromStatus(status)
        if (nickname.isBlank() || avatar.isNullOrBlank()) {
            val detail = runCatching { userClient.userDetail(uid, session.cookie) }.getOrNull()
            val brief = detail?.let { NcmLibraryParse.userProfileFromDetail(it) }
            if (nickname.isBlank()) nickname = brief?.nickname.orEmpty()
            if (avatar.isNullOrBlank()) avatar = brief?.avatarUrl
        }
        return CommunityLoginPreview(
            sid = sid,
            uid = uid,
            nickname = nickname,
            avatarUrl = avatar,
        )
    }

    suspend fun allow(preview: CommunityLoginPreview): CommunitySubmitAck {
        val now = System.currentTimeMillis() / 1000L
        val assertion = CommunityJwt.hs256(
            iss = "zmusic",
            aud = CommunityLoginConfig.CLIENT_ID,
            sid = preview.sid,
            uid = preview.uid.toString(),
            nickname = preview.nickname,
            avatarUrl = preview.avatarUrl.orEmpty(),
            iat = now,
            exp = now + CommunityLoginConfig.ASSERTION_TTL_SEC,
            secretHex = CommunityLoginConfig.HMAC_SECRET_HEX,
        )
        val url = communityServerStore.submitUrl()
        var firstForbidden: CommunitySubmitAck? = null
        for (attempt in 0..3) {
            val ack = client.submitAllow(url, assertion)
            if (ack.ok) {
                val token = ack.appToken
                if (!token.isNullOrBlank()) {
                    val uidText = ack.uid?.takeIf { it.isNotBlank() } ?: preview.uid.toString()
                    workshopAuthStore.save(token, uidText)
                }
                return ack
            }
            if (ack.status == "forbidden") {
                firstForbidden = ack
                if (attempt < 3) delay(400L * (attempt + 1))
            } else if (firstForbidden != null && ack.status in setOf("missing", "expired")) {
                return firstForbidden
            } else {
                return ack
            }
        }
        return firstForbidden ?: error("提交失败")
    }

    suspend fun deny(sid: String): CommunitySubmitAck = withContext(Dispatchers.IO) {
        client.submitDeny(communityServerStore.submitUrl(), sid)
    }

    private fun avatarFromStatus(json: JSONObject): String? {
        val profile = json.optJSONObject("profile")
            ?: json.optJSONObject("data")?.optJSONObject("profile")
            ?: json.optJSONObject("data")?.optJSONObject("data")?.optJSONObject("profile")
        val raw = profile?.optString("avatarUrl", "").orEmpty().trim()
        return raw.takeIf { it.isNotEmpty() && it != "null" }
    }
}
