package com.kite.zmusic.data.ncm

import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.json.JSONObject
import java.net.URLEncoder
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.spec.X509EncodedKeySpec
import java.util.zip.GZIPInputStream
import javax.crypto.KeyAgreement

/**
 * xeapi（X25519 + AES-GCM/ECB），对齐 `api-enhanced/util/crypto.js`。
 * 用于游客 `/register/anonimous` 拿 `MUSIC_A`。
 */
internal object NcmXeapi {
    private val StaticKey = hex(
        "ab1d5a430f6bb04a3f01e81ddd72bd916d5ce591248ac128714806d7f8fb1b84",
    )
    private val SignKey =
        "mUHCwVNWJbunMqAHf5MImuirT6plvs6VSFW62MGHstFQxhBGdEoIhLItH3djc4+FB/OKty3+lL2rGeoFBpVe5g=="
            .toByteArray(Charsets.UTF_8)
    private val X25519SpkiPrefix = hex("302a300506032b656e032100")
    private val random = SecureRandom()
    private val bc = BouncyCastleProvider()

    fun sign(timestamp: String, nonce: String): String =
        NcmCrypto.b64(NcmCrypto.hmacSha256(SignKey, (timestamp + nonce).toByteArray(Charsets.UTF_8)))

    fun decryptPublicKey(encryptedData: String): JSONObject {
        val plain = NcmCrypto.aesEcbDecrypt(StaticKey, NcmCrypto.b64Decode(encryptedData))
        return JSONObject(String(plain, Charsets.UTF_8))
    }

    fun encrypt(
        uri: String,
        data: JSONObject,
        publicKey: JSONObject,
        sessionId: String,
        sessionKey: String,
        os: String,
    ): Triple<String, String, String> {
        val activeSessionKey = sessionKey.takeIf { it.isNotEmpty() }?.toByteArray(Charsets.UTF_8)
        val dynamicKey = activeSessionKey ?: ByteArray(16).also { random.nextBytes(it) }
        val plaintext = buildPlaintext(uri, data).toByteArray(Charsets.UTF_8)
        val b = NcmCrypto.aesEcbEncrypt(
            dynamicKey,
            midTransform(NcmCrypto.aesEcbEncrypt(StaticKey, plaintext)),
        )
        val s = encryptS(dynamicKey, publicKey, os)
        val version = publicKey.optString("version")
        val rPayload = "$version|${if (activeSessionKey != null) sessionId else ""}"
        val r = NcmCrypto.aesEcbEncrypt(StaticKey, rPayload.toByteArray(Charsets.UTF_8))
        return Triple(NcmCrypto.b64(b), NcmCrypto.b64(s), NcmCrypto.b64(r))
    }

    fun decryptResponse(body: ByteArray): JSONObject {
        val decrypted = NcmCrypto.aesEcbDecrypt(
            NcmCrypto.EapiKey.toByteArray(Charsets.UTF_8),
            body,
        )
        val plain = if (decrypted.size >= 2 && decrypted[0] == 0x1f.toByte() && decrypted[1] == 0x8b.toByte()) {
            GZIPInputStream(decrypted.inputStream()).use { it.readBytes() }
        } else {
            decrypted
        }
        return JSONObject(String(plain, Charsets.UTF_8))
    }

    private fun encryptS(dynamicKey: ByteArray, publicKeyState: JSONObject, os: String): ByteArray {
        val peerRaw = NcmCrypto.b64Decode(publicKeyState.optString("publicKey"))
        val peerKey = x25519Public(peerRaw)
        val kpg = KeyPairGenerator.getInstance("X25519", bc)
        val kp = kpg.generateKeyPair()
        val ephemeralRaw = kp.public.encoded.let { it.copyOfRange(it.size - 32, it.size) }
        val agreement = KeyAgreement.getInstance("X25519", bc)
        agreement.init(kp.private)
        agreement.doPhase(peerKey, true)
        val shared = agreement.generateSecret()
        val aesKey = deriveAesKey(shared, ephemeralRaw)
        val iv = ByteArray(12).also { random.nextBytes(it) }
        val sk = publicKeyState.optString("sk")
        val plain = "${NcmCrypto.b64(dynamicKey)}|$os|$sk".toByteArray(Charsets.UTF_8)
        val cipherAndTag = NcmCrypto.aesGcmEncrypt(aesKey, iv, plain)
        return ephemeralRaw + iv + cipherAndTag
    }

    private fun deriveAesKey(sharedSecret: ByteArray, ephemeralPublicKey: ByteArray): ByteArray {
        val secret = if (sharedSecret.isEmpty()) ByteArray(32) else sharedSecret
        val prk = NcmCrypto.hmacSha256(ByteArray(32), secret)
        val okm = NcmCrypto.hmacSha256(prk, ephemeralPublicKey + byteArrayOf(1))
        return okm.copyOf(16)
    }

    private fun x25519Public(raw: ByteArray): java.security.PublicKey {
        val spki = X25519SpkiPrefix + raw
        return KeyFactory.getInstance("X25519", bc).generatePublic(X509EncodedKeySpec(spki))
    }

    private fun midTransform(ciphertext: ByteArray): ByteArray {
        val rand = ByteArray(16).also { random.nextBytes(it) }
        val xored = ByteArray(ciphertext.size) { i ->
            (ciphertext[i].toInt() xor rand[i and 0x0f].toInt()).toByte()
        }
        val b64 = NcmCrypto.b64(xored).toByteArray(Charsets.US_ASCII)
        val rot = if (b64.isEmpty()) 0 else (rand[0].toInt() and 0x0f) % b64.size
        val rotated = if (b64.isEmpty()) b64 else b64.copyOfRange(rot, b64.size) + b64.copyOfRange(0, rot)
        return rand + rotated
    }

    private fun buildPlaintext(uri: String, data: JSONObject): String {
        val bodyData = JSONObject(data.toString()).apply { remove("e_r") }
        val fields = JSONObject()
        if (bodyData.length() > 0) {
            val form = formUrlEncode(bodyData)
            fields.put("body", NcmCrypto.b64(form.toByteArray(Charsets.UTF_8)))
        }
        val parsed = java.net.URI("https://interface.music.163.com$uri")
        val search = parsed.rawQuery.orEmpty()
        fields.put(
            "queryString",
            if (search.isEmpty()) "e_r=true" else "$search&e_r=true",
        )
        return fields.toString()
    }

    private fun formUrlEncode(data: JSONObject): String {
        val keys = data.keys().asSequence().toList()
        return keys.joinToString("&") { key ->
            val raw = data.opt(key)
            val value = when (raw) {
                null, JSONObject.NULL -> ""
                is JSONObject -> raw.toString()
                else -> raw.toString()
            }
            URLEncoder.encode(key, Charsets.UTF_8.name()) + "=" +
                URLEncoder.encode(value, Charsets.UTF_8.name())
        }
    }

    fun anonymousUsername(deviceId: String): String {
        val encodedId = cloudmusicEncodeId(deviceId)
        val joined = "$deviceId $encodedId"
        return NcmCrypto.b64(joined.toByteArray(Charsets.UTF_8))
    }

    private fun cloudmusicEncodeId(id: String): String {
        val xorKey = "3go8&\$8*3*3h0k(2)2"
        val xored = CharArray(id.length) { i ->
            (id[i].code xor xorKey[i % xorKey.length].code).toChar()
        }.concatToString()
        return NcmCrypto.md5Base64(xored.toByteArray(Charsets.UTF_8))
    }

    private fun hex(s: String): ByteArray {
        val clean = s.replace(" ", "")
        return ByteArray(clean.length / 2) { i ->
            clean.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }
}
