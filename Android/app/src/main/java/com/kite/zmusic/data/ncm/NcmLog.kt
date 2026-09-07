package com.kite.zmusic.data.ncm

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/**
 * 登录直连排查：`adb logcat -s ZMusicNcm:D`
 * 会打请求 URL、HTTP、业务 code、脱敏后的 JSON。不要把完整日志发到公开场合。
 */
internal object NcmLog {
    const val TAG = "ZMusicNcm"
    private const val MAX = 3500

    fun i(msg: String) {
        Log.i(TAG, msg.take(MAX))
    }

    fun w(msg: String, err: Throwable? = null) {
        if (err != null) Log.w(TAG, msg.take(MAX), err) else Log.w(TAG, msg.take(MAX))
    }

    fun e(msg: String, err: Throwable? = null) {
        if (err != null) Log.e(TAG, msg.take(MAX), err) else Log.e(TAG, msg.take(MAX))
    }

    fun maskPhone(phone: String): String {
        val d = phone.filter { it.isDigit() }
        return if (d.length >= 7) d.take(3) + "****" + d.takeLast(4) else "***"
    }

    fun cookieNames(header: String): String {
        if (header.isBlank()) return "(none)"
        val names = header.split(';').mapNotNull { part ->
            part.substringBefore('=').trim().takeIf { it.isNotEmpty() }
        }
        return names.joinToString(",")
    }

    fun json(obj: JSONObject): String = redact(obj).toString()

    fun summarize(obj: JSONObject): String {
        val code = obj.opt("code")
        val msg = obj.opt("message")
        val msg2 = obj.opt("msg")
        val cookie = obj.opt("cookie")
        val token = obj.opt("token")
        val hasProfile = obj.optJSONObject("profile") != null
        val hasAccount = obj.optJSONObject("account") != null
        val data = obj.optJSONObject("data")
        return "code=$code message=$msg msg=$msg2 cookie=${describe(cookie)} " +
            "token=${describe(token)} profile=$hasProfile account=$hasAccount " +
            "dataKeys=${data?.keys()?.asSequence()?.joinToString(",") ?: "-"}"
    }

    private fun describe(value: Any?): String = when (value) {
        null, JSONObject.NULL -> "null"
        is String -> if (value.isEmpty()) "empty" else "str(len=${value.length},hasEq=${value.contains('=')})"
        else -> value.javaClass.simpleName
    }

    private fun redact(value: Any?): Any? = when (value) {
        null, JSONObject.NULL -> JSONObject.NULL
        is JSONObject -> {
            val out = JSONObject()
            val keys = value.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                out.put(key, redactKey(key, value.opt(key)))
            }
            out
        }
        is JSONArray -> {
            val out = JSONArray()
            for (i in 0 until value.length()) out.put(redact(value.opt(i)))
            out
        }
        else -> value
    }

    private fun redactKey(key: String, value: Any?): Any? {
        val k = key.lowercase()
        val sensitive = k.contains("cookie") || k.contains("token") || k.contains("password") ||
            k.contains("secret") || k == "sk" || k.contains("captcha")
        return if (sensitive) {
            when (value) {
                null, JSONObject.NULL -> JSONObject.NULL
                is String -> if (value.isEmpty()) "" else "<redacted len=${value.length}>"
                else -> "<redacted>"
            }
        } else if (k == "phone" || k == "cellphone" || k == "username") {
            when (value) {
                is String -> maskPhone(value)
                else -> redact(value)
            }
        } else {
            redact(value)
        }
    }
}
