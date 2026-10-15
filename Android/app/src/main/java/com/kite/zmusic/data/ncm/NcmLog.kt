package com.kite.zmusic.data.ncm

import android.util.Log
import org.json.JSONObject

/** 登录直连失败时的 logcat 记录。 */
internal object NcmLog {
    const val TAG = "ZMusicNcm"
    private const val MAX = 3500

    fun w(msg: String, err: Throwable? = null) {
        if (err != null) Log.w(TAG, msg.take(MAX), err) else Log.w(TAG, msg.take(MAX))
    }

    fun e(msg: String, err: Throwable? = null) {
        if (err != null) Log.e(TAG, msg.take(MAX), err) else Log.e(TAG, msg.take(MAX))
    }

    fun cookieNames(header: String): String {
        if (header.isBlank()) return "(none)"
        val names = header.split(';').mapNotNull { part ->
            part.substringBefore('=').trim().takeIf { it.isNotEmpty() }
        }
        return names.joinToString(",")
    }

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

}
