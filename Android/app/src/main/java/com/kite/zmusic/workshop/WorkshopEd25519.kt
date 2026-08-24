package com.kite.zmusic.workshop

import android.util.Base64
import java.security.MessageDigest
import net.i2p.crypto.eddsa.EdDSAEngine
import net.i2p.crypto.eddsa.EdDSAPublicKey
import net.i2p.crypto.eddsa.spec.EdDSANamedCurveTable
import net.i2p.crypto.eddsa.spec.EdDSAPublicKeySpec

object WorkshopEd25519 {
    fun sha256Hex(bytes: ByteArray): String {
        val dig = MessageDigest.getInstance("SHA-256").digest(bytes)
        return dig.joinToString("") { b -> "%02x".format(b) }
    }

    fun sha256HexFile(file: java.io.File): String =
        file.inputStream().use { input ->
            val md = MessageDigest.getInstance("SHA-256")
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                md.update(buf, 0, n)
            }
            md.digest().joinToString("") { b -> "%02x".format(b) }
        }

    /**
     * 对 32 字节 SHA-256 摘要做 Ed25519 校验。
     * [sigBase64] / 公钥均为标准 Base64。
     */
    fun verifySha256Digest(
        digest32: ByteArray,
        sigBase64: String,
        publicKey32: ByteArray,
    ): Boolean {
        if (digest32.size != 32 || publicKey32.size != 32) return false
        val sig = runCatching {
            Base64.decode(sigBase64.trim(), Base64.DEFAULT)
        }.getOrNull() ?: return false
        if (sig.isEmpty()) return false
        return runCatching {
            val spec = EdDSANamedCurveTable.getByName(EdDSANamedCurveTable.ED_25519)
            val pub = EdDSAPublicKey(EdDSAPublicKeySpec(publicKey32, spec))
            val eng = EdDSAEngine(MessageDigest.getInstance(spec.hashAlgorithm))
            eng.initVerify(pub)
            eng.update(digest32)
            eng.verify(sig)
        }.getOrDefault(false)
    }

    fun verifyFileSha256(
        file: java.io.File,
        expectedSha256Hex: String,
        sigBase64: String,
        publicKey32: ByteArray,
    ): Boolean {
        val hex = sha256HexFile(file).lowercase()
        if (hex != expectedSha256Hex.trim().lowercase()) return false
        val digest = hexToBytes(hex) ?: return false
        return verifySha256Digest(digest, sigBase64, publicKey32)
    }

    fun hexToBytes(hex: String): ByteArray? {
        val s = hex.trim().lowercase()
        if (s.length != 64 || !s.all { it in "0123456789abcdef" }) return null
        return ByteArray(32) { i ->
            s.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }
}
