package com.kite.zmusic.ui.login

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kite.zmusic.data.NcmAuthClient
import com.kite.zmusic.data.NcmJson
import com.kite.zmusic.data.SessionRepository
import com.kite.zmusic.util.Md5Util
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext
import org.json.JSONObject

class LoginViewModel(
    private val sessionRepository: SessionRepository,
    private val api: NcmAuthClient = NcmAuthClient(),
) : ViewModel() {

    var busy by mutableStateOf(false)
        private set

    var bannerError by mutableStateOf<String?>(null)
        private set

    var phone by mutableStateOf("")
    var captcha by mutableStateOf("")
    var password by mutableStateOf("")
    var email by mutableStateOf("")
    var emailPassword by mutableStateOf("")

    var qrImageBase64 by mutableStateOf<String?>(null)
    var qrHint by mutableStateOf("")
    private var qrUnikey: String? = null

    /** 短信验证码：发送请求中（不用全局 [busy]，避免整页遮罩）。 */
    var captchaSending by mutableStateOf(false)
        private set

    /** 发送成功后的冷却秒数，0 表示可再次发送。 */
    var captchaCooldownSec by mutableStateOf(0)
        private set

    /** 发送成功后的简短提示，仅短信面板使用。 */
    var smsCaptchaHint by mutableStateOf("")
        private set

    private var captchaCooldownJob: Job? = null

    fun dismissError() {
        bannerError = null
    }

    /** 修改手机号时清空冷却与提示，避免串号。 */
    fun onSmsPhoneChanged() {
        captchaCooldownJob?.cancel()
        captchaCooldownJob = null
        captchaCooldownSec = 0
        smsCaptchaHint = ""
    }

    fun loadQrSession() {
        viewModelScope.launch {
            if (busy) return@launch
            busy = true
            bannerError = null
            try {
                val keyJson = api.loginQrKey()
                if (NcmJson.apiCode(keyJson) != 200) {
                    bannerError = keyJson.optString("msg", "无法获取二维码")
                    return@launch
                }
                val key = NcmJson.qrKey(keyJson) ?: run {
                    bannerError = "二维码 key 解析失败"
                    return@launch
                }
                qrUnikey = key
                val create = api.loginQrCreate(key)
                if (NcmJson.apiCode(create) != 200) {
                    bannerError = create.optString("msg", create.optString("message", "二维码生成失败"))
                    return@launch
                }
                val img = NcmJson.qrImgBase64(create)
                if (img.isNullOrBlank()) {
                    bannerError = "二维码数据为空（请确认服务端 /login/qr/create 返回 data.qrimg）"
                    return@launch
                }
                qrImageBase64 = img
                qrHint = "使用网易云音乐 App 扫描"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                bannerError = e.message ?: "网络错误"
            } finally {
                busy = false
            }
        }
    }

    /**
     * 在 [LaunchedEffect] 中调用；Composition 取消时会中断轮询。
     */
    suspend fun runQrPolling(onLoggedIn: () -> Unit) {
        val key = qrUnikey ?: return
        while (coroutineContext.isActive) {
            delay(2_000)
            try {
                var json = api.loginQrCheck(key, noCookie = false)
                var code = NcmJson.qrCheckCode(json)
                if (code == 502) {
                    json = api.loginQrCheck(key, noCookie = true)
                    code = NcmJson.qrCheckCode(json)
                }
                when (code) {
                    800 -> {
                        qrHint = "二维码已过期，请刷新"
                        return
                    }
                    801 -> qrHint = "等待扫描…"
                    802 -> qrHint = "请在手机上确认登录"
                    803 -> {
                        val cookie = NcmJson.extractCookie(json)
                        if (cookie.isNullOrEmpty()) {
                            bannerError = "登录成功但未返回 cookie"
                            return
                        }
                        sessionRepository.persist(cookie, NcmJson.displayLabelFromLogin(json))
                        onLoggedIn()
                        return
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                bannerError = e.message
                return
            }
        }
    }

    fun sendCaptcha() {
        viewModelScope.launch {
            if (captchaCooldownSec > 0 || captchaSending) return@launch
            if (phone.isBlank()) {
                bannerError = "请输入手机号"
                return@launch
            }
            captchaSending = true
            bannerError = null
            var sentOk = false
            try {
                val j = api.captchaSent(phone.trim())
                if (NcmJson.apiCode(j) != 200) {
                    bannerError = j.optString("message", j.optString("msg", "发送失败"))
                } else {
                    smsCaptchaHint = "验证码已下发至手机，请查收短信"
                    sentOk = true
                }
            } catch (e: Exception) {
                bannerError = e.message ?: "发送失败"
            } finally {
                captchaSending = false
            }
            if (sentOk) {
                captchaCooldownJob?.cancel()
                captchaCooldownSec = CAPTCHA_RESEND_INTERVAL_SEC
                captchaCooldownJob = viewModelScope.launch {
                    while (isActive && captchaCooldownSec > 0) {
                        delay(1_000)
                        captchaCooldownSec--
                    }
                }
            }
        }
    }

    fun loginSms(onDone: () -> Unit) {
        viewModelScope.launch {
            if (busy) return@launch
            if (phone.isBlank() || captcha.isBlank()) {
                bannerError = "请输入手机号与验证码"
                return@launch
            }
            busy = true
            bannerError = null
            try {
                val j = api.loginCellphone(phone.trim(), captcha = captcha.trim())
                if (consumeLoginSuccess(j)) onDone()
            } catch (e: Exception) {
                bannerError = e.message ?: "登录失败"
            } finally {
                busy = false
            }
        }
    }

    fun loginPhonePassword(onDone: () -> Unit) {
        viewModelScope.launch {
            if (busy) return@launch
            if (phone.isBlank() || password.isBlank()) {
                bannerError = "请输入手机号与密码"
                return@launch
            }
            busy = true
            bannerError = null
            try {
                val md5 = Md5Util.md5Hex(password)
                val j = api.loginCellphone(phone.trim(), md5Password = md5)
                if (consumeLoginSuccess(j)) onDone()
            } catch (e: Exception) {
                bannerError = e.message ?: "登录失败"
            } finally {
                busy = false
            }
        }
    }

    fun loginEmail(onDone: () -> Unit) {
        viewModelScope.launch {
            if (busy) return@launch
            if (email.isBlank() || emailPassword.isBlank()) {
                bannerError = "请输入邮箱与密码"
                return@launch
            }
            busy = true
            bannerError = null
            try {
                val md5 = Md5Util.md5Hex(emailPassword)
                val j = api.loginEmail(email.trim(), md5Password = md5)
                if (consumeLoginSuccess(j)) onDone()
            } catch (e: Exception) {
                bannerError = e.message ?: "登录失败"
            } finally {
                busy = false
            }
        }
    }

    fun loginGuest(onDone: () -> Unit) {
        viewModelScope.launch {
            if (busy) return@launch
            busy = true
            bannerError = null
            try {
                val j = api.registerAnonymous()
                if (NcmJson.apiCode(j) != 200) {
                    bannerError = j.optString("msg", j.optString("message", "游客登录失败"))
                    return@launch
                }
                val cookie = NcmJson.extractCookie(j)
                if (cookie.isNullOrEmpty()) {
                    bannerError = "未返回游客 cookie"
                    return@launch
                }
                sessionRepository.persist(cookie, "游客", isGuest = true)
                onDone()
            } catch (e: Exception) {
                bannerError = e.message ?: "游客登录失败"
            } finally {
                busy = false
            }
        }
    }

    /** @return true if login succeeded and session saved */
    private fun consumeLoginSuccess(j: JSONObject): Boolean {
        if (NcmJson.apiCode(j) != 200) {
            bannerError = j.optString("msg", j.optString("message", "登录失败"))
            return false
        }
        val cookie = NcmJson.extractCookie(j)
        if (cookie.isNullOrEmpty()) {
            bannerError = "登录成功但未返回 cookie，请重试或使用二维码"
            return false
        }
        sessionRepository.persist(cookie, NcmJson.displayLabelFromLogin(j))
        return true
    }

    override fun onCleared() {
        super.onCleared()
        captchaCooldownJob?.cancel()
    }

    companion object {
        private const val CAPTCHA_RESEND_INTERVAL_SEC = 60
    }
}
