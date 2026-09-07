package com.kite.zmusic.data.ncm

import android.util.Base64
import org.json.JSONObject
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * 网易云 weapi / eapi 加密，对齐 `api-enhanced/util/crypto.js`。
 */
internal object NcmCrypto {
    private const val WeapiIv = "0102030405060708"
    private const val WeapiPresetKey = "0CoJUm6Qyw8W8jud"
    private const val WeapiBase62 =
        "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
    private const val WeapiPublicKeyPem =
        "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDgtQn2JZ34ZC28NWYpAUd98iZ37BUrX/aKzmFbt7clFSs6sXqHauqKWqdtLkF2KexO40H1YTX8z2lSgBBOAxLsvaklV8k4cBFK9snQXE9/DDaFt6Rr7iVZMldczhC0JNgTz+SHXT6CBHuX3e9SdB1Ua44oncaTWz7OBGLbCiK45wIDAQAB"
    const val EapiKey = "e82ckenh8dichen8"
    private val weapiRsaKey by lazy {
        val der = Base64.decode(WeapiPublicKeyPem, Base64.DEFAULT)
        KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(der))
    }

    fun md5Hex(text: String): String = md5Hex(text.toByteArray(Charsets.UTF_8))

    fun md5Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("MD5").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun md5Base64(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("MD5").digest(bytes)
        return b64(digest)
    }

    fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }

    fun weapi(data: JSONObject): Pair<String, String> {
        val text = data.toString()
        val secret = buildString {
            repeat(16) {
                append(WeapiBase62[(Math.random() * 62).toInt().coerceAtMost(61)])
            }
        }
        val params = aesCbcToB64(
            aesCbcToB64(text, WeapiPresetKey, WeapiIv),
            secret,
            WeapiIv,
        )
        val encSecKey = rsaNoPadding(secret.reversed().toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return params to encSecKey
    }

    fun eapi(uri: String, data: JSONObject): String {
        val text = data.toString()
        val digest = md5Hex("nobody${uri}use${text}md5forencrypt")
        val payload = "$uri-36cd479b6b5-$text-36cd479b6b5-$digest"
        return aesEcbToHex(payload.toByteArray(Charsets.UTF_8), EapiKey.toByteArray(Charsets.UTF_8))
    }

    fun aesCbcToB64(text: String, key: String, iv: String): String {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(key.toByteArray(Charsets.UTF_8), "AES"),
            IvParameterSpec(iv.toByteArray(Charsets.UTF_8)),
        )
        return b64(cipher.doFinal(text.toByteArray(Charsets.UTF_8)))
    }

    fun aesEcbEncrypt(key: ByteArray, plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"))
        return cipher.doFinal(plaintext)
    }

    fun aesEcbDecrypt(key: ByteArray, ciphertext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"))
        return cipher.doFinal(ciphertext)
    }

    fun aesGcmEncrypt(key: ByteArray, iv: ByteArray, plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        return cipher.doFinal(plaintext)
    }

    private fun aesEcbToHex(plain: ByteArray, key: ByteArray): String =
        aesEcbEncrypt(key, plain).joinToString("") { "%02X".format(it) }

    private fun rsaNoPadding(data: ByteArray): ByteArray {
        val padded = ByteArray(128)
        data.copyInto(padded, 128 - data.size)
        val cipher = Cipher.getInstance("RSA/ECB/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, weapiRsaKey)
        return cipher.doFinal(padded)
    }

    fun b64(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.NO_WRAP)

    fun b64Decode(text: String): ByteArray =
        Base64.decode(text, Base64.DEFAULT)
}
