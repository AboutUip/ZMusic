package com.kite.zmusic.data

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 启动页并行校验会话，主界面进门时复用结果，避免 splash 后再卡一层「正在进入」。
 */
class SessionWarmup(
    private val sessionRepository: SessionRepository,
    private val authClient: NcmAuthClient,
    private val onUserId: (Long) -> Unit = {},
) {
    private val mutex = Mutex()

    @Volatile
    private var checkedCookie: String? = null

    @Volatile
    private var valid: Boolean? = null

    @Volatile
    var selfUserId: Long = 0L
        private set

    fun isValidFor(cookie: String?): Boolean {
        val c = cookie?.trim().orEmpty()
        return c.isNotEmpty() && checkedCookie == c && valid == true
    }

    fun invalidate() {
        checkedCookie = null
        valid = null
        selfUserId = 0L
    }

    suspend fun prefetch() {
        validate()
    }

    suspend fun validate(): Boolean = mutex.withLock {
        val session = sessionRepository.session.value
        if (session == null) {
            checkedCookie = null
            valid = false
            selfUserId = 0L
            return@withLock false
        }
        if (session.isGuest) {
            checkedCookie = session.cookie
            valid = true
            return@withLock true
        }
        if (checkedCookie == session.cookie && valid != null) {
            return@withLock valid!!
        }
        val json = runCatching { authClient.loginStatus(session.cookie) }.getOrNull()
        val stillValid = when {
            json == null -> true
            NcmJson.isLoggedInStatus(json) -> {
                NcmJson.userIdFromLoginStatus(json)?.let { rememberUserId(it) }
                true
            }
            NcmJson.apiCode(json) == 200 -> false
            else -> true
        }
        checkedCookie = session.cookie
        valid = stillValid
        stillValid
    }

    private fun rememberUserId(uid: Long) {
        if (uid <= 0L) return
        selfUserId = uid
        onUserId(uid)
    }
}
