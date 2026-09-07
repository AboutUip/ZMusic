package com.kite.zmusic.data

import java.util.concurrent.CancellationException
import org.json.JSONObject

internal object NcmJson {

    fun needsQrFallback(json: JSONObject): Boolean {
        val code = apiCode(json)
        if (code == -462 || code == 415) return true
        val data = json.optJSONObject("data")
        val url = listOf(
            data?.optString("url").orEmpty(),
            data?.optString("verifyUrl").orEmpty(),
        ).joinToString()
        if (url.contains("encrypt-pages")) return true
        if (code == 200 || code == 502 || code == 501) return false
        val msg = listOf(
            json.optString("message"),
            json.optString("msg"),
            data?.optString("message").orEmpty(),
            data?.optString("msg").orEmpty(),
        ).joinToString(" ")
        return msg.contains("安全验证") ||
            msg.contains("滑块") ||
            msg.contains("行为验证") ||
            msg.contains("人机验证")
    }

    fun apiCode(json: JSONObject): Int {
        val raw = when {
            json.has("code") && !json.isNull("code") -> json.opt("code")
            json.has("status") && !json.isNull("status") -> json.opt("status")
            else -> return -1
        }
        return when (raw) {
            is Number -> raw.toInt()
            is String -> raw.trim().toIntOrNull() ?: -1
            else -> -1
        }
    }

    fun extractCookie(json: JSONObject): String? {
        textField(json, "cookie")?.let { if (looksLikeCookie(it)) return it }
        json.optJSONObject("data")?.let { data ->
            textField(data, "cookie")?.let { if (looksLikeCookie(it)) return it }
        }
        if (apiCode(json) != 200) return null
        val token = textField(json, "token")
            ?: json.optJSONObject("data")?.let { textField(it, "token") }
        if (token.isNullOrEmpty()) return null
        val csrf = textField(json, "csrf")
            ?: json.optJSONObject("data")?.let { textField(it, "csrf") }
        return buildString {
            append("MUSIC_U=").append(token)
            if (!csrf.isNullOrEmpty()) append("; __csrf=").append(csrf)
        }
    }

    /** `/login/status`：data.account 非空视为已登录。 */
    fun isLoggedInStatus(json: JSONObject): Boolean {
        if (apiCode(json) != 200) return false
        val data = json.optJSONObject("data") ?: return false
        val account = data.opt("account")
        return account != null && account !== JSONObject.NULL
    }

    /**
     * `/cellphone/existence/check`：exist == 1 视为已注册。
     * @return null 表示接口未成功，调用方应中止。
     */
    fun phoneAlreadyRegistered(json: JSONObject): Boolean? {
        if (apiCode(json) != 200) return null
        val payload = json.optJSONObject("data") ?: json
        val exist = if (payload.has("exist")) payload.opt("exist") else json.opt("exist")
        return when (exist) {
            null, JSONObject.NULL -> false
            is Boolean -> exist
            is Number -> exist.toInt() == 1
            is String -> exist.trim() == "1" || exist.equals("true", ignoreCase = true)
            else -> false
        }
    }

    fun displayLabelFromLogin(json: JSONObject): String? {
        json.optJSONObject("profile")?.let { textField(it, "nickname") }?.let { return it }
        json.optJSONObject("account")?.let { textField(it, "userName") }?.let { return it }
        json.optJSONObject("data")?.optJSONObject("profile")?.let { textField(it, "nickname") }
            ?.let { return it }
        return null
    }

    fun qrImgBase64(json: JSONObject): String? {
        val data = json.optJSONObject("data") ?: return null
        val raw = data.optString("qrimg", "").trim()
        if (raw.isEmpty()) return null
        val idx = raw.indexOf(',')
        return if (idx >= 0) raw.substring(idx + 1) else raw
    }

    fun qrKey(json: JSONObject): String? {
        val data = json.optJSONObject("data") ?: return null
        val k = data.optString("unikey", data.optString("key", "")).trim()
        return k.takeIf { it.isNotEmpty() }
    }

    fun qrUrl(json: JSONObject, key: String): String {
        val data = json.optJSONObject("data")
        val fromApi = data?.optString("qrurl").orEmpty().trim()
            .ifEmpty { data?.optString("qrUrl").orEmpty().trim() }
        if (fromApi.startsWith("http")) return fromApi
        return "https://music.163.com/login?codekey=$key"
    }

    /** 二维码轮询：801 等待，802 待确认，803 成功，800 过期。 */
    fun qrCheckCode(json: JSONObject): Int = json.optInt("code", 0)

    /**
     * 从 `/login/status` 解析用户 id。兼容：双层 data、profile/account 在根级、id 为字符串等。
     */
    fun userIdFromLoginStatus(json: JSONObject): Long? {
        val code = apiCode(json)
        if (code != 200 && code != 301) {
            // 部分代理仍返回 body，尝试继续解析
            if (!json.has("data") && !json.has("profile") && !json.has("account")) return null
        }
        val payload = effectiveLoginStatusPayload(json)
        if (payload != null) {
            val profile = payload.optJSONObject("profile")
            val account = payload.optJSONObject("account")
            longFromJson(profile, "userId")?.takeIf { it > 0L }?.let { return it }
            longFromJson(profile, "userid")?.takeIf { it > 0L }?.let { return it }
            longFromJson(account, "id")?.takeIf { it > 0L }?.let { return it }
            longFromJson(account, "userId")?.takeIf { it > 0L }?.let { return it }
            longFromJson(account, "userid")?.takeIf { it > 0L }?.let { return it }
            longFromJson(payload, "userId")?.takeIf { it > 0L }?.let { return it }
            longFromJson(payload, "userid")?.takeIf { it > 0L }?.let { return it }
            longFromJson(payload, "uid")?.takeIf { it > 0L }?.let { return it }
        }
        // 无 data 包装或字段在根级
        longFromJson(json.optJSONObject("profile"), "userId")?.takeIf { it > 0L }?.let { return it }
        longFromJson(json.optJSONObject("profile"), "userid")?.takeIf { it > 0L }?.let { return it }
        longFromJson(json.optJSONObject("account"), "id")?.takeIf { it > 0L }?.let { return it }
        longFromJson(json.optJSONObject("account"), "userId")?.takeIf { it > 0L }?.let { return it }
        longFromJson(json, "userId")?.takeIf { it > 0L }?.let { return it }
        longFromJson(json, "uid")?.takeIf { it > 0L }?.let { return it }
        return null
    }

    private fun effectiveLoginStatusPayload(json: JSONObject): JSONObject? {
        val d1 = json.optJSONObject("data") ?: return null
        val d2 = d1.optJSONObject("data")
        return when {
            d2 != null && (d2.has("profile") || d2.has("account")) -> d2
            d1.has("profile") || d1.has("account") -> d1
            d2 != null -> d2
            else -> d1
        }
    }

    private fun longFromJson(obj: JSONObject?, key: String): Long? {
        if (obj == null || !obj.has(key) || obj.isNull(key)) return null
        return when (val v = obj.get(key)) {
            is Number -> v.toLong().takeIf { it > 0L }
            is String -> v.trim().toLongOrNull()?.takeIf { it > 0L }
            else -> obj.optString(key, "").trim().toLongOrNull()?.takeIf { it > 0L }
        }
    }

    /**
     * 从接口 JSON 取出可展示给用户的短句。
     * 丢弃含 URL、IP、userId、原始 HTTP 堆栈的字段。
     */
    fun userFacingMessage(json: JSONObject, fallback: String): String {
        val data = json.optJSONObject("data")
        val candidates = listOf(
            textField(json, "message"),
            textField(json, "msg"),
            textField(data, "message"),
            textField(data, "msg"),
            textField(data, "blockText"),
            textField(json, "blockText"),
        )
        for (raw in candidates) {
            val sanitized = sanitizeUserMessage(raw)
            if (sanitized != null) return sanitized
        }
        return messageForCode(apiCode(json), fallback)
    }

    fun userFacingThrowable(error: Throwable, fallback: String): String {
        if (error is CancellationException) return fallback
        val raw = error.message.orEmpty()
        if (raw.contains("coroutine", ignoreCase = true) &&
            raw.contains("cancelled", ignoreCase = true)
        ) {
            return fallback
        }
        return sanitizeUserMessage(error.message) ?: fallback
    }

    private fun sanitizeUserMessage(raw: String?): String? {
        val t = raw?.trim().orEmpty()
        if (t.isEmpty()) return null
        val lower = t.lowercase()
        if (lower == "null" || lower == "undefined" || lower == "nan") return null
        if (lower == "un login" || lower == "unlogin" || lower == "cheating") return null
        if (lower.startsWith("http ") || lower.contains("http://") || lower.contains("https://")) {
            return null
        }
        if (t.contains('{') || t.contains('[') || t.contains("@ ")) return null
        if (lower.contains("userid=") || lower.contains("cookie=") || lower.contains("timestamp=")) {
            return null
        }
        if (
            t.contains("/login") ||
            t.contains("/register") ||
            t.contains("/captcha") ||
            t.contains("/inner/")
        ) {
            return null
        }
        if (IPV4.containsMatchIn(t) || HTTP_STATUS.containsMatchIn(t)) return null
        return if (t.length > 80) t.take(80).trimEnd() + "…" else t
    }

    /**
     * Android [JSONObject.optString] 会把 JSON `null` 变成字面量 "null"。
     */
    private fun textField(obj: JSONObject?, key: String): String? {
        if (obj == null || !obj.has(key) || obj.isNull(key)) return null
        val v = obj.opt(key) ?: return null
        if (v === JSONObject.NULL) return null
        val s = v.toString().trim()
        if (s.isEmpty() || s.equals("null", true) || s.equals("undefined", true)) return null
        return s
    }

    private fun looksLikeCookie(value: String): Boolean = value.contains('=')

    private fun messageForCode(code: Int, fallback: String): String = when (code) {
        400, 503, 505 -> "验证码不正确"
        501 -> "账号不存在"
        502 -> "账号或密码错误"
        509 -> "密码错误次数过多，请稍后再试"
        250 -> "登录失败，请稍后重试"
        301 -> "登录状态无效，请改用二维码登录"
        415 -> "需要安全验证"
        -462 -> "需要安全验证"
        else -> fallback
    }

    private val IPV4 = Regex("""\b\d{1,3}(?:\.\d{1,3}){3}\b""")
    private val HTTP_STATUS = Regex("""\bHTTP\s+\d{3}\b""", RegexOption.IGNORE_CASE)
}
