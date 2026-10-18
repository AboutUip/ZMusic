package com.kite.zmusic.listen

import com.kite.zmusic.data.uapipro.UApiProClient
import com.kite.zmusic.i18n.I18n
import com.kite.zmusic.i18n.t
import com.kite.zmusic.ui.notice.IslandNoticeCenter
import com.kite.zmusic.workshop.WorkshopApiError
import com.kite.zmusic.workshop.WorkshopAuthStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * 二楼公共聊天。打开时才拉最近一页并长轮询，更早的记录等滑到顶再要。
 */
class LoftChatController(
    private val client: LoftChatClient,
    private val auth: WorkshopAuthStore,
    private val notices: IslandNoticeCenter,
    private val uapiPro: UApiProClient,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _messages = MutableStateFlow<List<ListenChatMsg>>(emptyList())
    val messages: StateFlow<List<ListenChatMsg>> = _messages.asStateFlow()
    private val _hasOlder = MutableStateFlow(false)
    val hasOlder: StateFlow<Boolean> = _hasOlder.asStateFlow()
    private val _translations = MutableStateFlow<Map<String, ListenChatTranslateEntry>>(emptyMap())
    val translations: StateFlow<Map<String, ListenChatTranslateEntry>> = _translations.asStateFlow()

    private val localSeq = AtomicLong(0)
    private val translateJobs = ConcurrentHashMap<String, Job>()
    private var pollJob: Job? = null
    private var olderJob: Job? = null
    private var seenUid = ""

    init {
        scope.launch {
            auth.session.collect { session ->
                val uid = session?.uid.orEmpty()
                if (uid == seenUid) return@collect
                seenUid = uid
                olderJob?.cancel()
                _messages.value = emptyList()
                _hasOlder.value = false
                _translations.value = emptyMap()
            }
        }
    }

    fun setActive(active: Boolean) {
        pollJob?.cancel()
        pollJob = null
        if (!active || !auth.hasToken()) return
        pollJob = scope.launch { runPoll() }
    }

    fun loadOlder() {
        if (!_hasOlder.value || olderJob?.isActive == true) return
        val before = _messages.value.asSequence().map { it.id }.filter { it > 0L }.minOrNull() ?: return
        olderJob = scope.launch {
            val page = runCatching { client.before(before) }.getOrNull() ?: return@launch
            updateMessages { mergeLoft(it, page.messages) }
            _hasOlder.value = page.hasMore
        }
    }

    fun send(text: String) {
        val body = text.trim()
        if (body.isEmpty() || !auth.hasToken()) return
        val self = auth.current()?.uid.orEmpty()
        val prior = _messages.value.lastOrNull {
            listenChatUidEquals(it.uid, self) && (it.nickname.isNotBlank() || it.avatarUrl.isNotBlank())
        }
        val localId = -localSeq.incrementAndGet()
        val local = ListenChatMsg(
            id = localId,
            uid = self,
            nickname = prior?.nickname.orEmpty(),
            avatarUrl = prior?.avatarUrl.orEmpty(),
            text = body,
            at = System.currentTimeMillis(),
        )
        updateMessages { ordered(it + local) }
        scope.launch {
            try {
                val saved = client.post(body)
                updateMessages { mergeLoft(it, listOf(saved)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                updateMessages { cur -> cur.filter { it.id != localId } }
                notices.show(loftChatError(e))
            }
        }
    }

    fun translate(msg: ListenChatMsg) {
        val key = listenChatToastKey(msg)
        val existing = listenChatTranslateLookup(_translations.value, msg)
        if (existing != null && existing.text.isNotBlank() && !existing.loading) {
            _translations.update {
                putListenChatTranslate(it, msg, existing.copy(visible = true, loading = false, error = null))
            }
            return
        }
        if (existing?.loading == true) return
        _translations.update {
            putListenChatTranslate(it, msg, ListenChatTranslateEntry(loading = true, visible = true))
        }
        translateJobs[key]?.cancel()
        translateJobs[key] = scope.launch {
            val result = uapiPro.translate(
                text = listenChatVisibleBody(msg.text),
                targetLang = I18n.language.tag,
                sourceLang = null,
                style = "academic",
                context = "general",
                preserveFormat = true,
            )
            result.fold(
                onSuccess = { ok ->
                    _translations.update {
                        commitListenChatTranslate(
                            it,
                            msg,
                            ListenChatTranslateEntry(text = ok.translatedText, visible = true, loading = false),
                        )
                    }
                },
                onFailure = { err ->
                    _translations.update {
                        commitListenChatTranslate(
                            it,
                            msg,
                            ListenChatTranslateEntry(visible = false, loading = false, error = err.message),
                        )
                    }
                    notices.show(err.message?.takeIf { it.isNotBlank() } ?: t("翻译失败"))
                },
            )
        }.also { job ->
            job.invokeOnCompletion { translateJobs.remove(key, job) }
        }
    }

    fun hideTranslation(msg: ListenChatMsg) {
        _translations.update { hideListenChatTranslate(it, msg) }
    }

    private suspend fun runPoll() {
        var primed = _messages.value.any { it.id > 0L }
        while (currentCoroutineContext().isActive) {
            if (!primed) {
                val page = runCatching { client.latest() }.getOrElse {
                    kotlinx.coroutines.delay(2_000)
                    continue
                }
                updateMessages { mergeLoft(it.filter { msg -> msg.id < 0L }, page.messages) }
                _hasOlder.value = page.hasMore
                primed = true
                continue
            }
            val after = _messages.value.asSequence().map { it.id }.filter { it > 0L }.maxOrNull() ?: 0L
            val page = runCatching { client.after(after, wait = true) }.getOrElse {
                kotlinx.coroutines.delay(2_000)
                continue
            }
            if (page.messages.isNotEmpty()) {
                updateMessages { mergeLoft(it, page.messages) }
            }
            if (page.hasMore) continue
        }
    }

    private fun updateMessages(block: (List<ListenChatMsg>) -> List<ListenChatMsg>) {
        val previous = _messages.value
        val merged = block(previous)
        _messages.value = merged
        _translations.update { retargetListenChatTranslations(it, previous, merged) }
    }

    private fun mergeLoft(previous: List<ListenChatMsg>, incoming: List<ListenChatMsg>): List<ListenChatMsg> {
        if (incoming.isEmpty()) return ordered(previous)
        val self = auth.current()?.uid.orEmpty()
        val pending = previous.filter { it.id < 0L }
        val consumed = HashSet<Long>()
        val mapped = ArrayList<ListenChatMsg>(incoming.size)
        for (remote in incoming) {
            if (remote.id <= 0L) continue
            val match = pending.firstOrNull { local ->
                local.id !in consumed &&
                    local.text == remote.text &&
                    (listenChatUidEquals(local.uid, remote.uid) || listenChatUidEquals(remote.uid, self))
            }
            val kept = previous.firstOrNull { it.id == remote.id }
            val rowKey = when {
                match != null -> match.rowKey
                kept != null && kept.rowKey != kept.id -> kept.rowKey
                else -> remote.rowKey
            }
            if (match != null) consumed += match.id
            mapped += if (rowKey == remote.rowKey) remote else remote.copy(rowKey = rowKey)
        }
        val rest = previous.filter { msg -> msg.id !in consumed && mapped.none { it.id == msg.id } }
        return ordered(rest + mapped)
    }

    private fun ordered(messages: List<ListenChatMsg>): List<ListenChatMsg> {
        val pending = messages.filter { it.id < 0L }.distinctBy { it.id }
        val saved = messages.filter { it.id > 0L }.distinctBy { it.id }.sortedBy { it.id }
        return saved + pending
    }

    private fun loftChatError(err: Throwable): String {
        val code = (err as? WorkshopApiError.Message)?.message.orEmpty()
        return when {
            err is WorkshopApiError.RateLimited || code == "rate_limited" -> t("请求太频繁，稍后再试")
            err is WorkshopApiError.Unauthorized -> t("请先授权登录社区")
            else -> t("发送失败")
        }
    }
}
