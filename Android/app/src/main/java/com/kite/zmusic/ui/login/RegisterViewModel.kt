package com.kite.zmusic.ui.login

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kite.zmusic.data.NcmAuthClient
import com.kite.zmusic.data.NcmEndpointMissingException
import com.kite.zmusic.data.NcmJson
import com.kite.zmusic.data.SessionRepository
import com.kite.zmusic.util.Md5Util
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class RegisterViewModel(
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
    var nickname by mutableStateOf("")

    var captchaSending by mutableStateOf(false)
        private set

    var captchaCooldownSec by mutableStateOf(0)
        private set

    private var captchaCooldownJob: Job? = null

    fun dismissError() {
        bannerError = null
    }

    fun reset() {
        captchaCooldownJob?.cancel()
        captchaCooldownJob = null
        busy = false
        bannerError = null
        phone = ""
        captcha = ""
        password = ""
        nickname = ""
        captchaSending = false
        captchaCooldownSec = 0
    }

    fun onPhoneChanged() {
        captchaCooldownJob?.cancel()
        captchaCooldownJob = null
        captchaCooldownSec = 0
        captcha = ""
    }

    fun checkPhoneAndSendCaptcha(onSent: () -> Unit) {
        viewModelScope.launch {
            if (captchaCooldownSec > 0 || captchaSending || busy) return@launch
            val p = phone.trim()
            if (!PHONE_REGEX.matches(p)) {
                bannerError = "请输入正确的手机号"
                return@launch
            }
            busy = true
            bannerError = null
            try {
                val existJson = api.cellphoneExistenceCheck(p)
                when (NcmJson.phoneAlreadyRegistered(existJson)) {
                    true -> {
                        bannerError = "该手机号已注册，请返回登录"
                        return@launch
                    }
                    null -> {
                        bannerError = NcmJson.userFacingMessage(existJson, "无法检测该手机号是否已注册")
                        return@launch
                    }
                    false -> Unit
                }
            } catch (e: Exception) {
                bannerError = NcmJson.userFacingThrowable(e, "检测失败")
                return@launch
            } finally {
                busy = false
            }
            sendCaptchaInternal(p, onSent)
        }
    }

    fun resendCaptcha() {
        viewModelScope.launch {
            sendCaptchaInternal(phone.trim(), onSent = {})
        }
    }

    fun goProfileAfterCaptcha(onOk: () -> Unit) {
        viewModelScope.launch {
            if (busy) return@launch
            val p = phone.trim()
            val code = captcha.trim()
            if (code.isBlank()) {
                bannerError = "请输入验证码"
                return@launch
            }
            busy = true
            bannerError = null
            try {
                val j = api.captchaVerify(p, code)
                val codeNum = NcmJson.apiCode(j)
                if (codeNum == 200) {
                    onOk()
                } else {
                    bannerError = NcmJson.userFacingMessage(j, "验证码不正确")
                }
            } catch (_: NcmEndpointMissingException) {
                onOk()
            } catch (e: Exception) {
                bannerError = NcmJson.userFacingThrowable(e, "校验失败")
            } finally {
                busy = false
            }
        }
    }

    /**
     * `/register/cellphone` 文档只约定注册/改密，不保证返回 cookie。
     * 有凭证则直接登录；否则走 [onNeedSmsLogin]。
     */
    fun register(onLoggedIn: () -> Unit, onNeedSmsLogin: (phone: String) -> Unit) {
        viewModelScope.launch {
            if (busy) return@launch
            val p = phone.trim()
            val code = captcha.trim()
            val nick = nickname.trim()
            val pwd = password
            if (!PHONE_REGEX.matches(p) || code.isBlank()) {
                bannerError = "请先完成手机号与验证码"
                return@launch
            }
            passwordError(pwd)?.let {
                bannerError = it
                return@launch
            }
            if (nick.isBlank()) {
                bannerError = "请填写昵称"
                return@launch
            }
            if (nick.length > 30) {
                bannerError = "昵称请控制在 30 个字符以内"
                return@launch
            }
            busy = true
            bannerError = null
            try {
                val md5 = Md5Util.md5Hex(pwd)
                val j = api.registerCellphone(
                    phone = p,
                    captcha = code,
                    password = md5,
                    nickname = nick,
                )
                if (NcmJson.apiCode(j) != 200) {
                    bannerError = NcmJson.userFacingMessage(j, "注册失败")
                    return@launch
                }
                val cookie = NcmJson.extractCookie(j)
                if (!cookie.isNullOrEmpty()) {
                    sessionRepository.persist(cookie, NcmJson.displayLabelFromLogin(j) ?: nick)
                    onLoggedIn()
                    return@launch
                }
                onNeedSmsLogin(p)
            } catch (e: Exception) {
                bannerError = NcmJson.userFacingThrowable(e, "注册失败")
            } finally {
                busy = false
            }
        }
    }

    private suspend fun sendCaptchaInternal(phone: String, onSent: () -> Unit) {
        if (captchaCooldownSec > 0 || captchaSending) return
        captchaSending = true
        bannerError = null
        var sentOk = false
        try {
            val j = api.captchaSent(phone)
            if (NcmJson.apiCode(j) != 200) {
                bannerError = NcmJson.userFacingMessage(j, "发送失败")
            } else {
                sentOk = true
            }
        } catch (e: Exception) {
            bannerError = NcmJson.userFacingThrowable(e, "发送失败")
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
            onSent()
        }
    }

    override fun onCleared() {
        super.onCleared()
        captchaCooldownJob?.cancel()
    }

    companion object {
        private const val CAPTCHA_RESEND_INTERVAL_SEC = 60
        private val PHONE_REGEX = Regex("^1[3-9]\\d{9}$")

        fun passwordError(password: String): String? {
            if (password.contains(' ')) return "密码不能包含空格"
            if (password.length !in 8..20) return "密码长度为 8–20 位"
            val kinds = listOf(
                password.any { it.isLetter() },
                password.any { it.isDigit() },
                password.any { !it.isLetterOrDigit() },
            ).count { it }
            if (kinds < 2) return "密码须包含字母、数字、符号中至少两种"
            return null
        }
    }
}

class RegisterViewModelFactory(
    private val sessionRepository: SessionRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(RegisterViewModel::class.java)) {
            return RegisterViewModel(sessionRepository) as T
        }
        error("Unknown ViewModel: ${modelClass.name}")
    }
}
