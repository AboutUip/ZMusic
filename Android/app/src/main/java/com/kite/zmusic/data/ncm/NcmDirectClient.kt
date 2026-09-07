package com.kite.zmusic.data.ncm

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * 直连网易云 weapi / eapi / xeapi，覆盖短信、密码、注册与游客 token。
 * 二维码登录仍走公益服代理。
 */
internal class NcmDirectClient(
    private val http: OkHttpClient,
    private val profileStore: NcmDeviceProfileStore,
) {
    private val readyMutex = Mutex()
    private val yidunClient = http.newBuilder().readTimeout(10, TimeUnit.SECONDS).build()
    private var yidunToken = ""
    private var yidunAtMs = 0L
    private var xeapiSessionId = ""
    private var xeapiSessionKey = ""
    private var nmtidRetriesLeft = 3
    private val extraCookies = LinkedHashMap<String, String>()

    suspend fun captchaSent(phone: String, ctcode: String, secureCaptcha: String = ""): JSONObject = weapi(
        "/api/sms/captcha/sent",
        JSONObject()
            .put("ctcode", ctcode)
            .put("secrete", "music_middleuser_pclogin")
            .put("cellphone", phone)
            .also { if (secureCaptcha.isNotEmpty()) it.put("secureCaptcha", secureCaptcha) },
    )

    suspend fun captchaVerify(phone: String, captcha: String, ctcode: String): JSONObject = weapi(
        "/api/sms/captcha/verify",
        JSONObject()
            .put("ctcode", ctcode)
            .put("cellphone", phone)
            .put("captcha", captcha),
    )

    suspend fun cellphoneExistenceCheck(phone: String, countrycode: String): JSONObject = eapi(
        "/api/cellphone/existence/check",
        JSONObject()
            .put("cellphone", phone)
            .put("countrycode", countrycode),
        attachCookie = false,
    )

    suspend fun loginCellphone(
        phone: String,
        passwordMd5: String?,
        captcha: String?,
        countrycode: String,
        secureCaptcha: String = "",
    ): JSONObject {
        val data = JSONObject()
            .put("type", "1")
            .put("https", "true")
            .put("phone", phone)
            .put("countrycode", countrycode)
            .put("remember", "true")
        attachRiskProof(data, secureCaptcha)
        if (!captcha.isNullOrEmpty()) {
            data.put("captcha", captcha)
        } else {
            data.put("password", passwordMd5.orEmpty())
        }
        return weapi("/api/w/login/cellphone", data, attachCookie = true)
    }

    suspend fun loginEmail(email: String, passwordMd5: String, secureCaptcha: String = ""): JSONObject {
        val data = JSONObject()
            .put("type", "0")
            .put("https", "true")
            .put("username", email)
            .put("password", passwordMd5)
            .put("rememberLogin", "true")
        attachRiskProof(data, secureCaptcha)
        val json = eapi("/api/w/login", data, attachCookie = true)
        if (json.optInt("code") == 502) {
            json.put("msg", "账号或密码错误")
            json.put("message", "账号或密码错误")
        }
        return json
    }

    suspend fun registerCellphone(
        phone: String,
        captcha: String,
        passwordMd5: String,
        nickname: String,
        countrycode: String,
        secureCaptcha: String = "",
    ): JSONObject {
        val data = JSONObject()
            .put("captcha", captcha)
            .put("phone", phone)
            .put("password", passwordMd5)
            .put("nickname", nickname)
            .put("countrycode", countrycode)
            .put("force", "false")
        attachRiskProof(data, secureCaptcha)
        return eapi("/api/w/register/cellphone", data, attachCookie = true)
    }

    fun invalidateYidun() {
        yidunToken = ""
        yidunAtMs = 0L
    }

    fun webCookies(): Map<String, String> {
        val snap = profileStore.snapshot()
        val map = LinkedHashMap<String, String>()
        map["__remember_me"] = "true"
        map["ntes_kaola_ad"] = "1"
        map["_ntes_nuid"] = snap.nuid
        map["_ntes_nnid"] = snap.nnid
        map["WNMCID"] = snap.wnmcid
        map["WEVNSM"] = "1.0.0"
        map["deviceId"] = snap.deviceId
        if (snap.nmtid.isNotEmpty()) map["NMTID"] = snap.nmtid
        if (snap.csrf.isNotEmpty()) map["__csrf"] = snap.csrf
        if (snap.musicA.isNotEmpty()) map["MUSIC_A"] = snap.musicA
        return map
    }

    fun ingestWebCookies(header: String) {
        ingestCookieHeader(header, harvested = true)
    }

    private fun attachRiskProof(data: JSONObject, secureCaptcha: String) {
        val proof = secureCaptcha.ifBlank { extraCookies["checkToken"].orEmpty() }
        if (proof.isBlank() || proof.startsWith("00.")) return
        data.put("secureCaptcha", proof)
        data.put("checkToken", proof)
    }

    private fun ingestCookieHeader(header: String, harvested: Boolean) {
        if (header.isBlank()) {
            if (harvested) NcmLog.i("harvest cookies empty")
            return
        }
        val musicA = NcmCookie.value(header, "MUSIC_A")
        val nmtid = NcmCookie.value(header, "NMTID")
        val csrf = NcmCookie.value(header, "__csrf")
        val checkToken = NcmCookie.value(header, "checkToken")
        val secureCaptcha = NcmCookie.value(header, "secureCaptcha")
        if (harvested) {
            NcmLog.i(
                "harvest cookies names=${NcmLog.cookieNames(header)} " +
                    "musicA=${!musicA.isNullOrBlank()} nmtid=${!nmtid.isNullOrBlank()} " +
                    "csrf=${!csrf.isNullOrBlank()} checkToken=${!checkToken.isNullOrBlank()} " +
                    "secureCaptcha=${!secureCaptcha.isNullOrBlank()}",
            )
        }
        for (name in HARVEST_COOKIE_KEYS) {
            val v = NcmCookie.value(header, name)?.takeIf { it.isNotBlank() && !it.startsWith("00.") }
            if (!v.isNullOrBlank()) extraCookies[name] = v
        }
        if (musicA.isNullOrBlank() && nmtid.isNullOrBlank() && csrf.isNullOrBlank()) return
        profileStore.update {
            copy(
                musicA = musicA?.takeIf { it.isNotBlank() } ?: this.musicA,
                nmtid = nmtid?.takeIf { it.isNotBlank() } ?: this.nmtid,
                csrf = csrf?.takeIf { it.isNotBlank() } ?: this.csrf,
            )
        }
    }

    private suspend fun weapi(
        uri: String,
        data: JSONObject,
        attachCookie: Boolean = false,
    ): JSONObject = withContext(Dispatchers.IO) {
        ensureReady()
        val cookie = processCookie(CryptoKind.Weapi)
        val payload = JSONObject(data.toString())
            .put("csrf_token", cookie["__csrf"].orEmpty())
            .put("e_r", false)
        val (params, encSecKey) = NcmCrypto.weapi(payload)
        val body = FormBody.Builder()
            .add("params", params)
            .add("encSecKey", encSecKey)
            .build()
        val req = Request.Builder()
            .url("$DOMAIN/weapi/${uri.removePrefix("/api/")}")
            .post(body)
            .header("Referer", DOMAIN)
            .header("User-Agent", UA_WEAPI_PC)
            .header("Cookie", NcmCookie.cookieHeader(cookie))
            .apply {
                if (yidunToken.isNotEmpty()) header("X-antiCheatToken", yidunToken)
                cookie["checkToken"]?.takeIf { it.isNotEmpty() }?.let { header("checkToken", it) }
            }
            .build()
        NcmLog.i(
            "weapi $uri yidun=${yidunToken.isNotEmpty()} cookies=${cookie.keys.joinToString(",")} " +
                "payload=${NcmLog.json(payload)}",
        )
        finish("weapi", uri, executeJson(req), attachCookie)
    }

    private suspend fun eapi(
        uri: String,
        data: JSONObject,
        attachCookie: Boolean,
    ): JSONObject = withContext(Dispatchers.IO) {
        ensureReady()
        val cookie = processCookie(CryptoKind.Eapi)
        val csrf = cookie["__csrf"].orEmpty()
        val header = linkedMapOf(
            "osver" to cookie.getValue("osver"),
            "deviceId" to cookie.getValue("deviceId"),
            "os" to cookie.getValue("os"),
            "appver" to cookie.getValue("appver"),
            "versioncode" to cookie.getOrElse("versioncode") { "140" },
            "mobilename" to cookie.getOrElse("mobilename") { "" },
            "buildver" to cookie.getOrElse("buildver") { now().take(10) },
            "resolution" to cookie.getOrElse("resolution") { "1920x1080" },
            "__csrf" to csrf,
            "channel" to cookie.getValue("channel"),
            "requestId" to requestId(),
        )
        cookie["MUSIC_U"]?.let { header["MUSIC_U"] = it }
        cookie["MUSIC_A"]?.let { header["MUSIC_A"] = it }
        cookie["checkToken"]?.let { header["checkToken"] = it }
        if (yidunToken.isNotEmpty()) header["X-antiCheatToken"] = yidunToken
        val nmtid = cookie["NMTID"]
        if (!nmtid.isNullOrEmpty()) header["NMTID"] = nmtid
        val payload = JSONObject(data.toString())
        payload.put("header", header.toJson())
        payload.put("e_r", false)
        val params = NcmCrypto.eapi(uri, payload)
        val body = FormBody.Builder().add("params", params).build()
        val ua = if (cookie["os"] == "osx") UA_OSX else UA_API_IPHONE
        val req = Request.Builder()
            .url("$EAPI_DOMAIN/eapi/${uri.removePrefix("/api/")}")
            .post(body)
            .header("Cookie", NcmCookie.headerCookie(header))
            .header("User-Agent", ua)
            .build()
        NcmLog.i("eapi $uri yidun=${yidunToken.isNotEmpty()} cookies=${cookie.keys.joinToString(",")}")
        val result = executeJson(req)
        collectNmtid(cookie["NMTID"].isNullOrEmpty(), result.setCookie)
        finish("eapi", uri, result, attachCookie)
    }

    private suspend fun xeapi(uri: String, data: JSONObject): DirectResult {
        val snap = profileStore.snapshot()
        val publicKey = JSONObject(snap.xeapiPublicKeyJson.ifBlank { "{}" })
        if (publicKey.optString("sk").isEmpty() || publicKey.optString("publicKey").isEmpty()) {
            throw IOException("xeapi public key is missing")
        }
        val cookie = processCookie(CryptoKind.Xeapi)
        val os = "android"
        val appver = "9.1.65"
        val osver = "16"
        val buildver = now().take(10)
        val deviceId = cookie.getValue("deviceId")
        val (b, s, r) = NcmXeapi.encrypt(
            uri = uri,
            data = JSONObject(data.toString()).put("e_r", true),
            publicKey = publicKey,
            sessionId = xeapiSessionId,
            sessionKey = xeapiSessionKey,
            os = os,
        )
        val form = FormBody.Builder()
            .add("B", b)
            .add("S", s)
            .add("R", r)
            .build()
        val xeapiCookie = LinkedHashMap(cookie).apply {
            put("os", os)
            put("osver", osver)
            put("appver", appver)
            put("buildver", buildver)
            put("sDeviceId", deviceId)
        }
        NcmLog.i("xeapi $uri yidun=${yidunToken.isNotEmpty()} cookies=${xeapiCookie.keys.joinToString(",")}")
        val req = Request.Builder()
            .url("$XEAPI_DOMAIN/xeapi/${uri.removePrefix("/api/")}")
            .post(form)
            .header("User-Agent", UA_API_ANDROID)
            .header("X-Client-Enc-State", "ENCRYPTED")
            .header("x-aeapi", "true")
            .header("content-type", "application/x-www-form-urlencoded;charset=utf-8")
            .header("x-deviceid", deviceId)
            .header("x-os", os)
            .header("x-osver", osver)
            .header("x-appver", appver)
            .header("x-sdeviceid", deviceId)
            .header("x-buildver", buildver)
            .header("Cookie", NcmCookie.cookieHeader(xeapiCookie))
            .apply {
                if (yidunToken.isNotEmpty()) header("X-antiCheatToken", yidunToken)
            }
            .build()
        return executeXeapi(req)
    }

    private suspend fun ensureReady() {
        readyMutex.withLock {
            refreshYidunLocked()
            ensureXeapiKeyLocked()
            ensureMusicALocked()
        }
    }

    private fun refreshYidunLocked() {
        val age = System.currentTimeMillis() - yidunAtMs
        if (yidunToken.isNotEmpty() && age in 0 until YIDUN_TTL_MS) {
            NcmLog.i("yidun cache hit ageMs=$age")
            return
        }
        yidunToken = fetchYidunToken()
        yidunAtMs = System.currentTimeMillis()
        if (yidunToken.isEmpty()) {
            NcmLog.w("yidun empty, continue without X-antiCheatToken")
        }
    }

    private fun fetchYidunToken(): String {
        val req = Request.Builder()
            .url(YIDUN_URL)
            .get()
            .header("User-Agent", UA_WEAPI_PC)
            .build()
        return try {
            yidunClient.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                val m = YIDUN_REGEX.find(body) ?: run {
                    NcmLog.w("yidun no-token http=${resp.code} preview=${body.take(180)}")
                    return ""
                }
                if (m.groupValues[1] != "200") {
                    NcmLog.w("yidun code=${m.groupValues[1]} http=${resp.code}")
                    return ""
                }
                NcmLog.i("yidun token len=${m.groupValues[2].length}")
                m.groupValues[2]
            }
        } catch (e: Exception) {
            NcmLog.w("yidun fetch failed", e)
            ""
        }
    }

    private fun ensureXeapiKeyLocked() {
        val snap = profileStore.snapshot()
        val cached = snap.xeapiPublicKeyJson.takeIf { it.isNotBlank() }?.let {
            runCatching { JSONObject(it) }.getOrNull()
        }
        if (cached != null && cached.optString("sk").isNotEmpty() && cached.optString("publicKey").isNotEmpty()) {
            NcmLog.i("gorilla key cache hit version=${cached.optString("version")}")
            return
        }
        val fetched = fetchXeapiPublicKey(snap.deviceId, cached?.optString("version").orEmpty()) ?: run {
            NcmLog.w("gorilla key missing, anonymous MUSIC_A skipped")
            return
        }
        if (fetched.optString("sk").isEmpty() && cached != null && cached.optString("sk").isNotEmpty()) {
            fetched.put("sk", cached.optString("sk"))
        }
        if (fetched.optString("sk").isEmpty()) {
            NcmLog.w("gorilla key no sk")
            return
        }
        profileStore.update { copy(xeapiPublicKeyJson = fetched.toString()) }
    }

    private fun fetchXeapiPublicKey(deviceId: String, currentKeyVersion: String): JSONObject? {
        val nonce = buildString { repeat(16) { append(Random.nextInt(10)) } }
        val timestamp = now()
        val form = FormBody.Builder()
            .add("appVersion", "9.5.61")
            .add("currentKeyVersion", currentKeyVersion)
            .add("deviceId", deviceId)
            .add("nonce", nonce)
            .add("os", "android")
            .add("requestType", "active")
            .add("signature", NcmXeapi.sign(timestamp, nonce))
            .add("t1", "")
            .add("t2", "")
            .add("timestamp", timestamp)
            .add("uid", "")
            .build()
        val req = Request.Builder()
            .url("$API_DOMAIN/api/gorilla/anti/crawler/security/key/get")
            .post(form)
            .header("User-Agent", UA_API_ANDROID)
            .header(
                "Cookie",
                if (deviceId.isEmpty()) "" else "deviceId=${NcmCookie.encodeURIComponent(deviceId)}",
            )
            .build()
        return try {
            http.newCall(req).execute().use { resp ->
                val json = JSONObject(resp.body?.string().orEmpty()).coerceCode()
                if (json.optInt("code") != 200) {
                    NcmLog.w("gorilla http=${resp.code} ${NcmLog.summarize(json)}")
                    return null
                }
                val data = json.optJSONObject("data") ?: run {
                    NcmLog.w("gorilla no data ${NcmLog.summarize(json)}")
                    return null
                }
                val encrypted = data.optString("encryptedData")
                val signature = data.optString("signature")
                val resTs = data.opt("timestamp")?.toString().orEmpty()
                if (encrypted.isEmpty() || signature.isEmpty()) {
                    NcmLog.w("gorilla missing encrypted/signature")
                    return null
                }
                if (NcmXeapi.sign(resTs, nonce) != signature) {
                    NcmLog.w("gorilla signature mismatch")
                    return null
                }
                NcmXeapi.decryptPublicKey(encrypted).also {
                    NcmLog.i("gorilla key ok version=${it.optString("version")} hasSk=${it.optString("sk").isNotEmpty()}")
                }
            }
        } catch (e: Exception) {
            NcmLog.w("gorilla key failed", e)
            null
        }
    }

    private suspend fun ensureMusicALocked() {
        if (profileStore.snapshot().musicA.isNotBlank()) {
            NcmLog.i("anonymous MUSIC_A cache hit")
            return
        }
        val username = NcmXeapi.anonymousUsername(profileStore.snapshot().deviceId)
        val result = try {
            xeapi("/api/register/anonimous", JSONObject().put("username", username))
        } catch (e: Exception) {
            NcmLog.w("anonymous xeapi failed", e)
            profileStore.update { copy(xeapiPublicKeyJson = "") }
            return
        }
        if (result.json.optInt("code") != 200) {
            NcmLog.w("anonymous code=${result.json.opt("code")} ${NcmLog.summarize(result.json)}")
            return
        }
        val cookie = result.setCookie.ifEmpty { result.json.optString("cookie") }
        val musicA = NcmCookie.value(cookie, "MUSIC_A")
            ?: result.json.optString("token").takeIf { it.isNotBlank() }
            ?: result.json.optJSONObject("data")?.optString("token")?.takeIf { it.isNotBlank() }
        if (!musicA.isNullOrBlank()) {
            NcmLog.i("anonymous MUSIC_A len=${musicA.length}")
            profileStore.update { copy(musicA = musicA) }
        } else {
            NcmLog.w("anonymous 200 but no MUSIC_A setCookie=${NcmLog.cookieNames(cookie)}")
        }
    }

    private fun processCookie(kind: CryptoKind): LinkedHashMap<String, String> {
        val snap = profileStore.snapshot()
        val os = if (kind == CryptoKind.Xeapi) OS_ANDROID else OS_PC
        val map = LinkedHashMap<String, String>()
        map["__remember_me"] = "true"
        map["ntes_kaola_ad"] = "1"
        map["_ntes_nuid"] = snap.nuid
        map["_ntes_nnid"] = snap.nnid
        map["WNMCID"] = snap.wnmcid
        map["WEVNSM"] = "1.0.0"
        map["osver"] = os.osver
        map["deviceId"] = snap.deviceId
        map["os"] = os.os
        map["channel"] = os.channel
        map["appver"] = os.appver
        when {
            snap.nmtid.isNotEmpty() -> map["NMTID"] = snap.nmtid
            kind != CryptoKind.Eapi || nmtidRetriesLeft <= 0 -> {
                val generated = profileStore.randomNmtid()
                map["NMTID"] = generated
                profileStore.update { copy(nmtid = generated) }
            }
        }
        if (snap.musicA.isNotEmpty()) map["MUSIC_A"] = snap.musicA
        if (snap.csrf.isNotEmpty()) map["__csrf"] = snap.csrf
        extraCookies.forEach { (k, v) ->
            if (v.isNotEmpty()) map[k] = v
        }
        return map
    }

    private fun collectNmtid(probeWithoutNmtid: Boolean, setCookie: String) {
        if (!probeWithoutNmtid || nmtidRetriesLeft <= 0) return
        nmtidRetriesLeft--
        val nmtid = NcmCookie.value(setCookie, "NMTID") ?: return
        profileStore.update { copy(nmtid = nmtid) }
    }

    private fun finish(kind: String, uri: String, result: DirectResult, attachCookie: Boolean): JSONObject {
        val json = JSONObject(result.json.toString().replace("avatarImgId_str", "avatarImgIdStr"))
        if (attachCookie && result.setCookie.isNotEmpty()) {
            json.put("cookie", result.setCookie)
        }
        NcmLog.i(
            "$kind $uri http=${result.httpCode} attachCookie=$attachCookie " +
                "setCookie=${NcmLog.cookieNames(result.setCookie)} ${NcmLog.summarize(json)} body=${NcmLog.json(json)}",
        )
        return json
    }

    private fun executeJson(req: Request): DirectResult {
        NcmLog.i("http POST ${req.url}")
        http.newCall(req).execute().use { resp ->
            val setCookie = NcmCookie.firstPairs(resp.headers("Set-Cookie"))
            ingestCookieHeader(setCookie, harvested = false)
            val text = resp.body?.string().orEmpty()
            val json = try {
                if (text.isBlank()) null else JSONObject(text)
            } catch (e: JSONException) {
                NcmLog.w(
                    "http ${resp.code} not-json len=${text.length} preview=${text.take(400)}",
                    e,
                )
                null
            } ?: run {
                NcmLog.w("http ${resp.code} empty-or-invalid body len=${text.length} preview=${text.take(400)}")
                throw IOException("请求失败，请稍后重试")
            }
            NcmLog.i(
                "http ${resp.code} setCookie=${NcmLog.cookieNames(setCookie)} " +
                    "len=${text.length} ${NcmLog.summarize(json.coerceCode())}",
            )
            return DirectResult(json.coerceCode(), setCookie, resp.code)
        }
    }

    private fun executeXeapi(req: Request): DirectResult {
        NcmLog.i("xeapi POST ${req.url}")
        http.newCall(req).execute().use { resp ->
            resp.header("x-encr-ssid")?.let { xeapiSessionId = it }
            resp.header("x-encr-sskey")?.let { xeapiSessionKey = it }
            val setCookie = NcmCookie.firstPairs(resp.headers("Set-Cookie"))
            ingestCookieHeader(setCookie, harvested = false)
            val bytes = resp.body?.bytes() ?: ByteArray(0)
            val json = try {
                NcmXeapi.decryptResponse(bytes)
            } catch (e: Exception) {
                NcmLog.w("xeapi decrypt failed http=${resp.code} bytes=${bytes.size}", e)
                throw IOException("请求失败，请稍后重试")
            }
            NcmLog.i(
                "xeapi http=${resp.code} setCookie=${NcmLog.cookieNames(setCookie)} ${NcmLog.summarize(json)}",
            )
            return DirectResult(json.coerceCode(), setCookie, resp.code)
        }
    }

    private fun JSONObject.coerceCode(): JSONObject {
        val raw = opt("code") ?: return this
        val n = when (raw) {
            is Number -> raw.toInt()
            is String -> raw.trim().toIntOrNull()
            else -> null
        } ?: return this
        put("code", n)
        return this
    }

    private fun Map<String, String>.toJson(): JSONObject {
        val json = JSONObject()
        forEach { (k, v) -> json.put(k, v) }
        return json
    }

    private fun now() = System.currentTimeMillis().toString()

    private fun requestId(): String {
        val n = Random.nextInt(1000).toString().padStart(4, '0')
        return "${now()}_$n"
    }

    private data class DirectResult(val json: JSONObject, val setCookie: String, val httpCode: Int)

    private enum class CryptoKind { Weapi, Eapi, Xeapi }

    private data class OsPreset(
        val os: String,
        val appver: String,
        val osver: String,
        val channel: String,
    )

    companion object {
        private const val DOMAIN = "https://music.163.com"
        private const val API_DOMAIN = "https://interface.music.163.com"
        private const val EAPI_DOMAIN = "https://interfacepc.music.163.com"
        private const val XEAPI_DOMAIN = "https://interface3.music.163.com"
        private const val YIDUN_URL = "https://ac.dun.163yun.com/v3/b?pn=YD00000558929251"
        private const val YIDUN_TTL_MS = 45_000L
        private val YIDUN_REGEX = Regex("""null\(\[(\d+),\d+,"([^"]+)"\]\)""")
        private const val UA_WEAPI_PC =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36 Edg/124.0.0.0"
        private const val UA_API_IPHONE = "NeteaseMusic 9.0.90/5038 (iPhone; iOS 16.2; zh_CN)"
        private const val UA_API_ANDROID =
            "NeteaseMusic/9.5.61.260802021928(9005061);Dalvik/2.1.0 (Linux; U; Android 12; HBN-AL00 Build/cd737a2.0)"
        private const val UA_OSX =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
        private val OS_PC = OsPreset(
            os = "pc",
            appver = "3.1.17.204416",
            osver = "Microsoft-Windows-10-Professional-build-19045-64bit",
            channel = "netease",
        )
        private val OS_ANDROID = OsPreset(
            os = "android",
            appver = "8.20.20.231215173437",
            osver = "14",
            channel = "xiaomi",
        )
        private val HARVEST_COOKIE_KEYS = arrayOf("checkToken", "secureCaptcha", "sca")
    }
}
