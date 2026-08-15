package com.kite.zmusic.data

import com.kite.zmusic.config.NcmApiConfig
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
 * 网易云兼容 API 登录相关请求（GET/表单 POST）。
 */
class NcmAuthClient(
    private val client: OkHttpClient = defaultClient(),
) {

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

    suspend fun captchaSent(phone: String, ctcode: String = "86"): JSONObject = withContext(Dispatchers.IO) {
        get("/captcha/sent", mapOf("phone" to phone, "ctcode" to ctcode, "timestamp" to ts()))
    }

    suspend fun loginCellphone(
        phone: String,
        password: String? = null,
        md5Password: String? = null,
        captcha: String? = null,
        countrycode: String = "86",
    ): JSONObject = withContext(Dispatchers.IO) {
        val form = mutableMapOf("phone" to phone, "countrycode" to countrycode)
        when {
            captcha != null -> form["captcha"] = captcha
            md5Password != null -> form["md5_password"] = md5Password
            password != null -> form["password"] = password
            else -> error("password or captcha required")
        }
        postForm("/login/cellphone", form)
    }

    suspend fun loginEmail(email: String, password: String? = null, md5Password: String? = null): JSONObject =
        withContext(Dispatchers.IO) {
            val form = mutableMapOf("email" to email)
            when {
                md5Password != null -> form["md5_password"] = md5Password
                password != null -> form["password"] = password
                else -> error("password or md5_password required")
            }
            postForm("/login", form)
        }

    suspend fun cellphoneExistenceCheck(phone: String, countrycode: String = "86"): JSONObject =
        withContext(Dispatchers.IO) {
            get(
                "/cellphone/existence/check",
                mapOf("phone" to phone, "countrycode" to countrycode, "timestamp" to ts()),
            )
        }

    suspend fun captchaVerify(phone: String, captcha: String, ctcode: String = "86"): JSONObject =
        withContext(Dispatchers.IO) {
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

    suspend fun registerCellphone(
        phone: String,
        captcha: String,
        password: String,
        nickname: String,
        countrycode: String = "86",
    ): JSONObject = withContext(Dispatchers.IO) {
        postForm(
            "/register/cellphone",
            mapOf(
                "phone" to phone,
                "captcha" to captcha,
                "password" to password,
                "nickname" to nickname,
                "countrycode" to countrycode,
            ),
        )
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
        client.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            val json = try {
                if (text.isBlank()) null else JSONObject(text)
            } catch (_: JSONException) {
                null
            }
            // 业务失败（如 HTTP 400 + code/message）仍返回 JSON，由调用方展示短句。
            if (json != null) return json
            if (resp.code == 404 || resp.code == 502) {
                throw NcmEndpointMissingException()
            }
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
