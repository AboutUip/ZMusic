package com.kite.zmusic.util

import java.security.MessageDigest

internal object Md5Util {
    fun md5Hex(text: String): String {
        val md = MessageDigest.getInstance("MD5")
        val bytes = md.digest(text.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { b -> "%02x".format(b) }
    }
}
