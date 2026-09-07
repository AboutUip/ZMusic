package com.kite.zmusic.data

import com.kite.zmusic.config.NcmApiConfig
import com.kite.zmusic.data.ncm.NcmDeviceProfileStore
import com.kite.zmusic.data.ncm.NcmDirectClient
import com.kite.zmusic.data.ncm.NcmLog
import com.kite.zmusic.util.Md5Util
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * 登录相关：短信 / 密码 / 注册 / 邮箱直连网易；二维码与登录态仍走公益服代理。
 */
class NcmAuthClient(
    private val client: OkHttpClient = defaultClient(),
    profileStore: NcmDeviceProfileStore? = null,
) {
    private val direct = profileStore?.let { NcmDirectClient(client, it) }

    suspend fun loginStatus(cookie: String): JSONObject = withContext(Dispatchers.IO) {
        get(
            "/login/status",
            mapOf("cookie" to cookie, "timestamp" to ts()),
        )
    }

    suspend fun loginQrKey(): JSONObject = withContext(Dispatchers.IO) {
        get("/login/qr/key", mapOf("timestamp" to ts()))
    }

    suspend fun loginQrCreate(key: String): JSONObject = withContext(Dispatchers.IO) {
        get(
            "/login/qr/create",
            mapOf("key" to key, "qrimg" to "true", "timestamp" to ts()),
        )
    }

    suspend fun loginQrCheck(key: String, noCookie: Boolean): JSONObject = withContext(Dispatchers.IO) {
        val q = mutableMapOf("key" to key, "timestamp" to ts())
        if (noCookie) q["noCookie"] = "true"
        get("/login/qr/check", q)
    }

    suspend fun captchaSent(phone: String, ctcode: String = "86", secureCaptcha: String = ""): JSONObject {
        val gateway = direct ?: return withContext(Dispatchers.IO) {
            val q = mutableMapOf("phone" to phone, "ctcode" to ctcode, "timestamp" to ts())
            if (secureCaptcha.isNotEmpty()) q["sca"] = secureCaptcha
            get("/captcha/sent", q)
        }
        return gateway.captchaSent(phone, ctcode, secureCaptcha)
    }

    suspend fun loginCellphone(
        phone: String,
        password: String? = null,
        md5Password: String? = null,
        captcha: String? = null,
        countrycode: String = "86",
        secureCaptcha: String = "",
    ): JSONObject {
        val gateway = direct ?: return withContext(Dispatchers.IO) {
            val form = mutableMapOf("phone" to phone, "countrycode" to countrycode)
            when {
                captcha != null -> form["captcha"] = captcha
                md5Password != null -> form["md5_password"] = md5Password
                password != null -> form["password"] = password
                else -> error("password or captcha required")
            }
            if (secureCaptcha.isNotEmpty()) form["sca"] = secureCaptcha
            postForm("/login/cellphone", form)
        }
        val passwordMd5 = when {
            !captcha.isNullOrEmpty() -> null
            md5Password != null -> md5Password
            password != null -> Md5Util.md5Hex(password)
            else -> error("password or captcha required")
        }
        return gateway.loginCellphone(phone, passwordMd5, captcha, countrycode, secureCaptcha)
    }

    suspend fun loginEmail(
        email: String,
        password: String? = null,
        md5Password: String? = null,
        secureCaptcha: String = "",
    ): JSONObject {
        val gateway = direct ?: return withContext(Dispatchers.IO) {
            val form = mutableMapOf("email" to email)
            when {
                md5Password != null -> form["md5_password"] = md5Password
                password != null -> form["password"] = password
                else -> error("password or md5_password required")
            }
            if (secureCaptcha.isNotEmpty()) form["sca"] = secureCaptcha
            postForm("/login", form)
        }
        val passwordMd5 = md5Password ?: password?.let { Md5Util.md5Hex(it) }
            ?: error("password or md5_password required")
        return gateway.loginEmail(email, passwordMd5, secureCaptcha)
    }

    suspend fun cellphoneExistenceCheck(phone: String, countrycode: String = "86"): JSONObject {
        val gateway = direct ?: return withContext(Dispatchers.IO) {
            get(
                "/cellphone/existence/check",
                mapOf("phone" to phone, "countrycode" to countrycode, "timestamp" to ts()),
            )
        }
        return gateway.cellphoneExistenceCheck(phone, countrycode)
    }

    suspend fun captchaVerify(phone: String, captcha: String, ctcode: String = "86"): JSONObject {
        val gateway = direct ?: return withContext(Dispatchers.IO) {
            get(
                "/captcha/verify",
                mapOf(
                    "phone" to phone,
                    "captcha" to captcha,
                    "ctcode" to ctcode,
                    "timestamp" to ts(),
                ),
            )
        }
        return gateway.captchaVerify(phone, captcha, ctcode)
    }

    suspend fun registerCellphone(
        phone: String,
        captcha: String,
        password: String,
        nickname: String,
        countrycode: String = "86",
        secureCaptcha: String = "",
    ): JSONObject {
        val gateway = direct ?: return withContext(Dispatchers.IO) {
            val form = mutableMapOf(
                "phone" to phone,
                "captcha" to captcha,
                "password" to password,
                "nickname" to nickname,
                "countrycode" to countrycode,
            )
            if (secureCaptcha.isNotEmpty()) form["sca"] = secureCaptcha
            postForm("/register/cellphone", form)
        }
        return gateway.registerCellphone(phone, captcha, password, nickname, countrycode, secureCaptcha)
    }

    fun invalidateYidun() {
        direct?.invalidateYidun()
    }

    fun webCookies(): Map<String, String> = direct?.webCookies().orEmpty()

    fun harvestWebCookies(cookieHeader: String) {
        direct?.ingestWebCookies(cookieHeader)
    }

    private fun get(path: String, query: Map<String, String>): JSONObject {
        val url = buildUrl(path, query)
        val req = Request.Builder().url(url).get().build()
        return executeJson(req)
    }

    private fun postForm(path: String, fields: Map<String, String>): JSONObject {
        val body = FormBody.Builder().apply {
            fields.forEach { (k, v) -> add(k, v) }
        }.build()
        val url = buildUrl(path, mapOf("timestamp" to ts()))
        val req = Request.Builder().url(url).post(body).build()
        return executeJson(req)
    }

    private fun buildUrl(path: String, query: Map<String, String>): String {
        val base = NcmApiConfig.baseUrl.trimEnd('/')
        val full = (base + if (path.startsWith("/")) path else "/$path").toHttpUrl()
            .newBuilder()
        query.forEach { (k, v) -> full.addQueryParameter(k, v) }
        return full.build().toString()
    }

    private fun executeJson(req: Request): JSONObject {
        NcmLog.i("proxy ${req.method} ${req.url.encodedPath}")
        client.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            val json = try {
                if (text.isBlank()) null else JSONObject(text)
            } catch (e: JSONException) {
                NcmLog.w("proxy http=${resp.code} not-json len=${text.length} preview=${text.take(400)}", e)
                null
            }
            if (json != null) {
                NcmLog.i("proxy http=${resp.code} ${NcmLog.summarize(json)} body=${NcmLog.json(json)}")
                return json
            }
            if (resp.code == 404 || resp.code == 502) {
                NcmLog.w("proxy endpoint missing http=${resp.code} path=${req.url.encodedPath}")
                throw NcmEndpointMissingException()
            }
            NcmLog.w("proxy fail http=${resp.code} len=${text.length} preview=${text.take(400)}")
            throw IOException("请求失败，请稍后重试")
        }
    }

    private fun ts() = System.currentTimeMillis().toString()

    companion object {
        private fun defaultClient() = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }
}

/** 公益服未部署该路由（如部分环境没有 `/captcha/verify`）。 */
internal class NcmEndpointMissingException : IOException("接口不可用")
