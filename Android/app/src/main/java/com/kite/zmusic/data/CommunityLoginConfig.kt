package com.kite.zmusic.data

import android.net.Uri
import java.net.URLDecoder

/**
 * 方案 C：官方包预置的社区登录常量。密钥只用于本机签发断言，不放请求头。
 */
object CommunityLoginConfig {
    const val CLIENT_ID = "qp-zmusic"
    const val HMAC_SECRET_HEX = "a5f6efd78093e026a2bcc8993f61cf2e78c50ca3ab480dae47fb810511eb4dcc"
    const val DISPLAY_NAME = "量子像素 · ZMusic"
    const val QR_PREFIX = "ZMLOGIN1:"
    const val SUBMIT_PATH = "/api/v1/communities/zmusic/login/submit"
    /** 社区网页登录页（扫码确认用）。 */
    const val SITE_PATH = "/zmusic/login"
    const val ASSERTION_TTL_SEC = 90L
    const val SID_MIN = 16
    const val SID_MAX = 64

    private val SidChars = Regex("^[A-Za-z0-9_-]+$")

    fun parseQr(raw: String): String? {
        val text = raw.trim().trim('\uFEFF')
        if (text.isEmpty()) return null
        val decoded = runCatching {
            URLDecoder.decode(text.replace("+", "%2B"), Charsets.UTF_8.name())
        }.getOrDefault(text)

        sidAfterPrefix(text)?.let { return it }
        sidAfterPrefix(decoded)?.let { return it }

        val uri = runCatching { Uri.parse(decoded) }.getOrNull()
        uri?.getQueryParameter("sid")?.trim()?.let { if (validSid(it)) return it }
        uri?.getQueryParameter("qr_text")?.let { sidAfterPrefix(it)?.let { sid -> return sid } }
        uri?.getQueryParameter("qr")?.let { sidAfterPrefix(it) ?: it.trim().takeIf(::validSid) }
            ?.let { return it }

        if (validSid(text)) return text
        return null
    }

    private fun validSid(sid: String): Boolean =
        sid.length in SID_MIN..SID_MAX && SidChars.matches(sid)

    private fun sidAfterPrefix(source: String): String? {
        val idx = source.indexOf(QR_PREFIX, ignoreCase = true)
        if (idx < 0) return null
        val sid = source.substring(idx + QR_PREFIX.length)
            .trim()
            .substringBefore('&')
            .substringBefore('?')
            .substringBefore('#')
            .substringBefore('/')
            .trim()
        return sid.takeIf(::validSid)
    }
}
