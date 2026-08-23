package com.kite.zmusic.data

import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

internal object CommunityJwt {
    fun hs256(
        iss: String,
        aud: String,
        sid: String,
        uid: String,
        nickname: String,
        avatarUrl: String,
        iat: Long,
        exp: Long,
        secretHex: String,
    ): String {
        val header = """{"alg":"HS256","typ":"JWT"}"""
        val payload = buildString {
            append('{')
            append("\"iss\":").append(jsonString(iss)).append(',')
            append("\"aud\":").append(jsonString(aud)).append(',')
            append("\"sid\":").append(jsonString(sid)).append(',')
            append("\"uid\":").append(jsonString(uid)).append(',')
            append("\"nickname\":").append(jsonString(nickname)).append(',')
            append("\"avatar_url\":").append(jsonString(avatarUrl)).append(',')
            append("\"iat\":").append(iat).append(',')
            append("\"exp\":").append(exp)
            append('}')
        }
        val h = b64(header.toByteArray(Charsets.UTF_8))
        val p = b64(payload.toByteArray(Charsets.UTF_8))
        val signing = "$h.$p"
        val sig = b64(hmacSha256(signing.toByteArray(Charsets.UTF_8), hexToBytes(secretHex)))
        return "$signing.$sig"
    }

    private fun jsonString(value: String): String = buildString(value.length + 2) {
        append('"')
        for (ch in value) {
            when (ch) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (ch.code < 0x20) {
                    append("\\u").append(ch.code.toString(16).padStart(4, '0'))
                } else {
                    append(ch)
                }
            }
        }
        append('"')
    }

    private fun hmacSha256(data: ByteArray, key: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }

    private fun b64(bytes: ByteArray): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    private fun hexToBytes(hex: String): ByteArray {
        val h = hex.trim()
        require(h.length % 2 == 0) { "bad hmac hex" }
        return ByteArray(h.length / 2) { i ->
            h.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }
}
