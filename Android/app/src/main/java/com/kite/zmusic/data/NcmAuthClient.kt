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

    suspend fun registerAnonymous(): JSONObject = withContext(Dispatchers.IO) {
        get("/register/anonimous", mapOf("timestamp" to ts()))
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
            if (!resp.isSuccessful) {
                throw IOException(
                    "HTTP ${resp.code} ${resp.message} @ ${req.url} · ${text.take(200)}",
                )
            }
            if (text.isBlank()) {
                throw IOException("空响应 @ ${req.url}")
            }
            return try {
                JSONObject(text)
            } catch (e: JSONException) {
                throw IOException("非 JSON 响应 @ ${req.url}: ${text.take(300)}", e)
            }
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
