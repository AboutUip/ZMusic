package com.kite.zmusic.data.ncm

import android.app.Application
import org.json.JSONObject
import java.io.File
import java.security.SecureRandom
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * 持久化网易直连用的设备指纹、游客 `MUSIC_A`、xeapi 公钥。
 * 发送验证码与登录必须共用同一套 cookie，否则短信对不上。
 */
class NcmDeviceProfileStore(app: Application) {
    private val file = File(app.filesDir, FILE_NAME)
    private val lock = ReentrantLock()
    private val random = SecureRandom()
    private var snapshot: Snapshot = lock.withLock { readLocked() }

    fun snapshot(): Snapshot = lock.withLock { snapshot }

    fun update(block: Snapshot.() -> Snapshot) {
        lock.withLock {
            snapshot = block(snapshot)
            writeLocked(snapshot)
        }
    }

    private fun readLocked(): Snapshot {
        if (!file.isFile) return fresh()
        return try {
            val json = JSONObject(file.readText())
            Snapshot(
                deviceId = json.optString("deviceId").ifBlank { randomDeviceId() },
                nuid = json.optString("nuid").ifBlank { randomHex(32) },
                nnid = json.optString("nnid").ifBlank { "" },
                wnmcid = json.optString("wnmcid").ifBlank { randomWnmcid() },
                nmtid = json.optString("nmtid"),
                csrf = json.optString("csrf"),
                musicA = json.optString("musicA"),
                xeapiPublicKeyJson = json.optString("xeapiPublicKeyJson"),
            ).let { s ->
                if (s.nnid.isBlank()) s.copy(nnid = "${s.nuid},${System.currentTimeMillis()}") else s
            }
        } catch (_: Exception) {
            fresh()
        }
    }

    private fun writeLocked(s: Snapshot) {
        val json = JSONObject()
            .put("deviceId", s.deviceId)
            .put("nuid", s.nuid)
            .put("nnid", s.nnid)
            .put("wnmcid", s.wnmcid)
            .put("nmtid", s.nmtid)
            .put("csrf", s.csrf)
            .put("musicA", s.musicA)
            .put("xeapiPublicKeyJson", s.xeapiPublicKeyJson)
        file.writeText(json.toString())
    }

    private fun fresh(): Snapshot {
        val nuid = randomHex(32)
        val s = Snapshot(
            deviceId = randomDeviceId(),
            nuid = nuid,
            nnid = "$nuid,${System.currentTimeMillis()}",
            wnmcid = randomWnmcid(),
            nmtid = "",
            csrf = "",
            musicA = "",
            xeapiPublicKeyJson = "",
        )
        writeLocked(s)
        return s
    }

    private fun randomDeviceId(): String {
        val alphabet = "0123456789ABCDEF"
        return CharArray(52) { alphabet[random.nextInt(alphabet.length)] }.concatToString()
    }

    private fun randomHex(byteCount: Int): String {
        val bytes = ByteArray(byteCount)
        random.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun randomWnmcid(): String {
        val letters = "abcdefghijklmnopqrstuvwxyz"
        val prefix = CharArray(6) { letters[random.nextInt(letters.length)] }.concatToString()
        return "$prefix.${System.currentTimeMillis()}.01.0"
    }

    fun randomNmtid(): String = "00O" + randomHex(19)

    data class Snapshot(
        val deviceId: String,
        val nuid: String,
        val nnid: String,
        val wnmcid: String,
        val nmtid: String,
        val csrf: String,
        val musicA: String,
        val xeapiPublicKeyJson: String,
    )

    companion object {
        private const val FILE_NAME = "ncm_device_profile.json"
    }
}
