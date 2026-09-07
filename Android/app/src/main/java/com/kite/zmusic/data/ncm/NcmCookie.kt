package com.kite.zmusic.data.ncm

import java.net.URLEncoder

internal object NcmCookie {
    fun encodeURIComponent(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8.name())
            .replace("+", "%20")
            .replace("%21", "!")
            .replace("%27", "'")
            .replace("%28", "(")
            .replace("%29", ")")
            .replace("%7E", "~")

    fun cookieHeader(map: Map<String, String>): String =
        map.entries.joinToString("; ") { (k, v) ->
            "${encodeURIComponent(k)}=${encodeURIComponent(v)}"
        }

    fun headerCookie(header: Map<String, String>): String = cookieHeader(header)

    fun firstPairs(setCookies: List<String>): String =
        setCookies.mapNotNull { raw ->
            raw.substringBefore(';').trim().takeIf { it.contains('=') }
        }.joinToString("; ")

    fun value(cookie: String, name: String): String? {
        cookie.split(';').forEach { part ->
            val item = part.trim()
            val eq = item.indexOf('=')
            if (eq <= 0) return@forEach
            if (item.substring(0, eq).trim() == name) {
                return item.substring(eq + 1).trim().takeIf { it.isNotEmpty() }
            }
        }
        return null
    }
}
