package com.kite.zmusic.plugin

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class PluginSlotEntry(
    val pluginId: String,
    val pluginName: String,
    val slot: String,
    val id: String,
    val title: String,
    val subtitle: String?,
    val icon: String?,
    val accent: String?,
    val page: String?,
)

data class PluginOpenFrame(
    val pluginId: String,
    val pageName: String,
    val instance: String,
) {
    fun stackKey(): String = PluginUiTree.stackKey(pluginId, pageName, instance)
}

data class PluginAlertState(
    val pluginId: String,
    val pluginName: String,
    val title: String,
    val message: String?,
    val confirm: String,
    val cancel: String?,
    val destructive: Boolean,
    val onResult: (String) -> Unit,
)

data class PluginSheetActionSpec(
    val id: String,
    val label: String,
    val destructive: Boolean,
)

data class PluginSheetSpec(
    val title: String,
    val message: String?,
    val actions: List<PluginSheetActionSpec>,
)

data class PluginSheetState(
    val pluginId: String,
    val pluginName: String,
    val title: String,
    val message: String?,
    val actions: List<PluginSheetActionSpec>,
    val onResult: (String) -> Unit,
)

data class PluginActionEntry(
    val pluginId: String,
    val pluginName: String,
    val surface: String,
    val id: String,
    val title: String,
    val icon: String?,
    val destructive: Boolean,
)

data class PluginSurfaceMenuState(
    val surface: String,
    val title: String,
    val message: String?,
    val coverUrl: String?,
    val target: Map<String, Any?>,
    val pluginActions: List<PluginActionEntry>,
    val hostDefaultLabel: String?,
    val hostDefault: (() -> Unit)?,
)

sealed class PluginUiCommand {
    data class OpenPage(
        val pluginId: String,
        val pageName: String,
        val instance: String,
    ) : PluginUiCommand()

    data class ClosePlugin(val pluginId: String) : PluginUiCommand()

    data class ClosePage(val pluginId: String, val pageName: String) : PluginUiCommand()

    data class Back(val pluginId: String) : PluginUiCommand()
}

data class PluginAlertSpec(
    val title: String,
    val message: String?,
    val confirm: String,
    val cancel: String?,
    val destructive: Boolean,
)

/**
 * 插件 UI 状态。不持有 QuickJS 句柄；事件与结果回调由会话 post 回插件线程。
 */
class PluginUiBridge {
    private val _pages = MutableStateFlow<Map<String, Map<String, PluginPageDef>>>(emptyMap())
    val pages: StateFlow<Map<String, Map<String, PluginPageDef>>> = _pages.asStateFlow()

    private val _slots = MutableStateFlow<List<PluginSlotEntry>>(emptyList())
    val slots: StateFlow<List<PluginSlotEntry>> = _slots.asStateFlow()

    private val _alert = MutableStateFlow<PluginAlertState?>(null)
    val alert: StateFlow<PluginAlertState?> = _alert.asStateFlow()

    private val _sheet = MutableStateFlow<PluginSheetState?>(null)
    val sheet: StateFlow<PluginSheetState?> = _sheet.asStateFlow()

    private val _actions = MutableStateFlow<List<PluginActionEntry>>(emptyList())
    val actions: StateFlow<List<PluginActionEntry>> = _actions.asStateFlow()

    private val _contextMenu = MutableStateFlow<PluginSurfaceMenuState?>(null)
    val contextMenu: StateFlow<PluginSurfaceMenuState?> = _contextMenu.asStateFlow()

    private val _commands = MutableSharedFlow<PluginUiCommand>(extraBufferCapacity = 32)
    val commands: SharedFlow<PluginUiCommand> = _commands.asSharedFlow()

    private val lock = Any()
    private val pendingOpen = AtomicReference<PluginOpenFrame?>(null)
    private val emitters = ConcurrentHashMap<String, (Map<String, Any?>) -> Unit>()
    private val paramsByKey = ConcurrentHashMap<String, Map<String, Any?>>()
    private val unsynced = CopyOnWriteArrayList<PluginOpenFrame>()
    @Volatile
    private var openStack: List<PluginOpenFrame> = emptyList()

    fun bind(pluginId: String, emit: (Map<String, Any?>) -> Unit) {
        emitters[pluginId] = emit
    }

    fun unbind(pluginId: String) {
        emitters.remove(pluginId)
    }

    fun emit(pluginId: String, event: Map<String, Any?>) {
        emitters[pluginId]?.invoke(event)
    }

    fun pageOf(pluginId: String, name: String = PluginUiTree.DEFAULT_PAGE): PluginPageDef? =
        _pages.value[pluginId]?.get(name)

    fun hasPages(pluginId: String): Boolean = !_pages.value[pluginId].isNullOrEmpty()

    fun paramsOf(pluginId: String, pageName: String, instance: String): Map<String, Any?> =
        paramsByKey[frameKey(pluginId, pageName, instance)] ?: emptyMap()

    fun setPage(pluginId: String, pluginName: String, title: String, body: String): Boolean {
        val t = title.trim()
        if (t.isEmpty() || t.length > PluginUiTree.MAX_TITLE) return false
        if (body.length > PluginUiTree.MAX_LEGACY_BODY) return false
        val text = PluginUiNode(
            type = "text",
            id = null,
            props = mapOf("text" to body, "style" to "body"),
            children = emptyList(),
        )
        val root = PluginUiNode("scroll", null, emptyMap(), listOf(text))
        return definePage(pluginId, pluginName, PluginUiTree.DEFAULT_PAGE, t, root)
    }

    fun definePage(
        pluginId: String,
        pluginName: String,
        name: String,
        title: String,
        root: PluginUiNode,
    ): Boolean {
        if (!PluginUiTree.validLimits(root)) return false
        var ok = false
        _pages.update { cur ->
            val existing = cur[pluginId] ?: emptyMap()
            if (name !in existing && existing.size >= PluginUiTree.MAX_PAGES) return@update cur
            ok = true
            val nextInner = LinkedHashMap(existing)
            nextInner[name] = PluginPageDef(pluginId, pluginName, name, title, root)
            cur + (pluginId to nextInner)
        }
        return ok
    }

    fun patchPage(
        pluginId: String,
        name: String,
        id: String,
        props: Map<String, Any?>,
        children: List<PluginUiNode>?,
    ): Boolean {
        var ok = false
        _pages.update { cur ->
            val existing = cur[pluginId] ?: return@update cur
            val def = existing[name] ?: return@update cur
            val patched = PluginUiTree.patch(def.root, id, props, children) ?: return@update cur
            ok = true
            val nextInner = LinkedHashMap(existing)
            nextInner[name] = def.copy(root = patched)
            cur + (pluginId to nextInner)
        }
        return ok
    }

    fun applyControl(pluginId: String, pageName: String, id: String, value: Any?): Boolean {
        if (!patchPage(pluginId, pageName, id, mapOf("value" to value), null)) return false
        emit(
            pluginId,
            mapOf(
                "type" to "change",
                "page" to pageName,
                "id" to id,
                "value" to value,
            ),
        )
        return true
    }

    fun clearPage(pluginId: String, name: String): Boolean {
        var removed = false
        _pages.update { cur ->
            val existing = cur[pluginId] ?: return@update cur
            if (name !in existing) return@update cur
            removed = true
            val nextInner = LinkedHashMap(existing)
            nextInner.remove(name)
            if (nextInner.isEmpty()) cur - pluginId else cur + (pluginId to nextInner)
        }
        if (removed) {
            _commands.tryEmit(PluginUiCommand.ClosePage(pluginId, name))
        }
        return true
    }

    fun clearPages(pluginId: String): Boolean {
        var removed = false
        _pages.update { cur ->
            if (pluginId in cur) {
                removed = true
                cur - pluginId
            } else {
                cur
            }
        }
        if (removed) {
            _commands.tryEmit(PluginUiCommand.ClosePlugin(pluginId))
        }
        return true
    }

    fun openPage(
        pluginId: String,
        name: String = PluginUiTree.DEFAULT_PAGE,
        params: Map<String, Any?> = emptyMap(),
    ): Boolean {
        if (_pages.value[pluginId]?.get(name) == null) return false
        val instance = PluginUiTree.instanceKey(params)
        val frame = PluginOpenFrame(pluginId, name, instance)
        val live = openStack
        if (live.any { it == frame } || unsynced.any { it == frame }) {
            paramsByKey[frameKey(pluginId, name, instance)] = params
            return true
        }
        val depth = live.count { it.pluginId == pluginId } + unsynced.count { it.pluginId == pluginId }
        if (depth >= PluginUiTree.MAX_STACK) return false
        paramsByKey[frameKey(pluginId, name, instance)] = params
        unsynced.add(frame)
        pendingOpen.set(frame)
        _commands.tryEmit(PluginUiCommand.OpenPage(pluginId, name, instance))
        return true
    }

    fun openPreferred(pluginId: String): Boolean {
        val names = _pages.value[pluginId] ?: return false
        val name = when {
            PluginUiTree.DEFAULT_PAGE in names -> PluginUiTree.DEFAULT_PAGE
            else -> names.keys.firstOrNull() ?: return false
        }
        return openPage(pluginId, name, emptyMap())
    }

    fun back(pluginId: String): Boolean {
        val topLive = openStack.lastOrNull()
        val top = when {
            topLive != null -> topLive
            else -> unsynced.lastOrNull() ?: return false
        }
        if (top.pluginId != pluginId) return false
        _commands.tryEmit(PluginUiCommand.Back(pluginId))
        return true
    }

    fun consumePendingOpen(): PluginOpenFrame? = pendingOpen.getAndSet(null)

    fun syncOpenStack(frames: List<PluginOpenFrame>) {
        openStack = frames
        unsynced.removeAll { promised -> frames.any { it == promised } }
        val liveKeys = frames.map { frameKey(it.pluginId, it.pageName, it.instance) }.toSet()
        val pendingKeys = unsynced.map { frameKey(it.pluginId, it.pageName, it.instance) }.toSet()
        paramsByKey.keys.removeAll { it !in liveKeys && it !in pendingKeys }
    }

    internal fun setSlot(
        pluginId: String,
        pluginName: String,
        slot: String,
        spec: PluginUiTree.SlotSpec,
    ): Boolean {
        if (slot !in PluginUiTree.SLOTS) return false
        var ok = false
        _slots.update { cur ->
            val mine = cur.filter { it.pluginId == pluginId }
            val idx = cur.indexOfFirst {
                it.pluginId == pluginId && it.slot == slot && it.id == spec.id
            }
            if (idx < 0 && mine.size >= PluginUiTree.MAX_SLOTS) return@update cur
            ok = true
            val entry = PluginSlotEntry(
                pluginId = pluginId,
                pluginName = pluginName,
                slot = slot,
                id = spec.id,
                title = spec.title,
                subtitle = spec.subtitle,
                icon = spec.icon,
                accent = spec.accent,
                page = spec.page,
            )
            if (idx >= 0) {
                cur.toMutableList().also { it[idx] = entry }
            } else {
                cur + entry
            }
        }
        return ok
    }

    fun removeSlot(pluginId: String, slot: String, id: String): Boolean {
        _slots.update { cur ->
            cur.filterNot { it.pluginId == pluginId && it.slot == slot && it.id == id }
        }
        return true
    }

    fun clearSlots(pluginId: String): Boolean {
        _slots.update { cur -> cur.filterNot { it.pluginId == pluginId } }
        return true
    }

    fun activateSlot(pluginId: String, slot: String, id: String): Boolean {
        val entry = _slots.value.find {
            it.pluginId == pluginId && it.slot == slot && it.id == id
        } ?: return false
        val page = entry.page
        if (page != null) {
            openPage(pluginId, page, emptyMap())
        }
        emit(
            pluginId,
            mapOf(
                "type" to "press",
                "slot" to slot,
                "id" to id,
            ),
        )
        return true
    }

    internal fun setAction(
        pluginId: String,
        pluginName: String,
        surface: String,
        spec: PluginUiTree.ActionSpec,
    ): Boolean {
        if (surface !in PluginSurfaces.KNOWN) return false
        var ok = false
        _actions.update { cur ->
            val mine = cur.filter { it.pluginId == pluginId }
            val idx = cur.indexOfFirst {
                it.pluginId == pluginId && it.surface == surface && it.id == spec.id
            }
            if (idx < 0 && mine.size >= PluginUiTree.MAX_ACTIONS) return@update cur
            ok = true
            val entry = PluginActionEntry(
                pluginId = pluginId,
                pluginName = pluginName,
                surface = surface,
                id = spec.id,
                title = spec.title,
                icon = spec.icon,
                destructive = spec.destructive,
            )
            if (idx >= 0) {
                cur.toMutableList().also { it[idx] = entry }
            } else {
                cur + entry
            }
        }
        return ok
    }

    fun removeAction(pluginId: String, surface: String, id: String): Boolean {
        _actions.update { cur ->
            cur.filterNot { it.pluginId == pluginId && it.surface == surface && it.id == id }
        }
        return true
    }

    fun clearActions(pluginId: String): Boolean {
        _actions.update { cur -> cur.filterNot { it.pluginId == pluginId } }
        val menu = _contextMenu.value
        if (menu != null && menu.pluginActions.any { it.pluginId == pluginId }) {
            dismissSurfaceMenu(null)
        }
        return true
    }

    fun actionsOf(surface: String): List<PluginActionEntry> =
        _actions.value.filter { it.surface == surface }

    fun activateAction(
        pluginId: String,
        surface: String,
        id: String,
        target: Map<String, Any?>,
    ): Boolean {
        val entry = _actions.value.find {
            it.pluginId == pluginId && it.surface == surface && it.id == id
        } ?: return false
        emit(
            pluginId,
            mapOf(
                "type" to "press",
                "surface" to surface,
                "id" to entry.id,
                "target" to target,
            ),
        )
        return true
    }

    fun presentSurfaceMenu(
        surface: String,
        target: PluginUiTarget,
        hostDefaultLabel: String?,
        onHostDefault: (() -> Unit)?,
    ): Boolean {
        val pluginActions = actionsOf(surface)
        if (pluginActions.isEmpty()) return false
        synchronized(lock) {
            if (_alert.value != null || _sheet.value != null || _contextMenu.value != null) {
                return false
            }
            val map = target.toMap()
            _contextMenu.value = PluginSurfaceMenuState(
                surface = surface,
                title = target.name?.takeIf { it.isNotBlank() } ?: surface,
                message = target.subtitle,
                coverUrl = target.imageUrl,
                target = map,
                pluginActions = pluginActions,
                hostDefaultLabel = hostDefaultLabel?.trim()?.takeIf { it.isNotEmpty() },
                hostDefault = onHostDefault,
            )
            return true
        }
    }

    fun dismissSurfaceMenu(picked: PluginActionEntry?) {
        val shown = synchronized(lock) {
            val cur = _contextMenu.value ?: return
            _contextMenu.value = null
            cur
        }
        if (picked != null) {
            activateAction(picked.pluginId, picked.surface, picked.id, shown.target)
        }
    }

    fun runSurfaceHostDefault() {
        val shown = synchronized(lock) {
            val cur = _contextMenu.value ?: return
            _contextMenu.value = null
            cur
        }
        shown.hostDefault?.invoke()
    }

    fun presentAlert(
        pluginId: String,
        pluginName: String,
        spec: PluginAlertSpec,
        blocked: Boolean,
        onResult: (String) -> Unit,
    ): Boolean {
        if (blocked) return false
        synchronized(lock) {
            if (_alert.value != null || _sheet.value != null || _contextMenu.value != null) return false
            _alert.value = PluginAlertState(
                pluginId = pluginId,
                pluginName = pluginName,
                title = spec.title,
                message = spec.message,
                confirm = spec.confirm,
                cancel = spec.cancel,
                destructive = spec.destructive,
                onResult = onResult,
            )
            return true
        }
    }

    fun dismissAlert(action: String) {
        val shown = synchronized(lock) {
            val cur = _alert.value ?: return
            _alert.value = null
            cur
        }
        shown.onResult(action)
    }

    fun presentSheet(
        pluginId: String,
        pluginName: String,
        spec: PluginSheetSpec,
        blocked: Boolean,
        onResult: (String) -> Unit,
    ): Boolean {
        if (blocked) return false
        synchronized(lock) {
            if (_sheet.value != null || _alert.value != null || _contextMenu.value != null) return false
            _sheet.value = PluginSheetState(
                pluginId = pluginId,
                pluginName = pluginName,
                title = spec.title,
                message = spec.message,
                actions = spec.actions,
                onResult = onResult,
            )
            return true
        }
    }

    fun dismissSheet(action: String) {
        val shown = synchronized(lock) {
            val cur = _sheet.value ?: return
            _sheet.value = null
            cur
        }
        shown.onResult(action)
    }

    fun dropPlugin(pluginId: String) {
        val frames = (openStack + unsynced).filter { it.pluginId == pluginId }.distinct()
        for (frame in frames) {
            emit(
                pluginId,
                mapOf(
                    "type" to "leave",
                    "page" to frame.pageName,
                    "params" to paramsOf(pluginId, frame.pageName, frame.instance),
                ),
            )
        }
        unbind(pluginId)
        clearSlots(pluginId)
        clearActions(pluginId)
        var hadPages = false
        _pages.update { cur ->
            if (pluginId in cur) {
                hadPages = true
                cur - pluginId
            } else {
                cur
            }
        }
        unsynced.removeAll { it.pluginId == pluginId }
        paramsByKey.keys.removeAll { it.startsWith("$pluginId\u0000") }
        if (hadPages || frames.isNotEmpty()) {
            _commands.tryEmit(PluginUiCommand.ClosePlugin(pluginId))
        }
        val alert = synchronized(lock) {
            val cur = _alert.value
            if (cur != null && cur.pluginId == pluginId) {
                _alert.value = null
                cur
            } else {
                null
            }
        }
        alert?.onResult("dismiss")
        val sheet = synchronized(lock) {
            val cur = _sheet.value
            if (cur != null && cur.pluginId == pluginId) {
                _sheet.value = null
                cur
            } else {
                null
            }
        }
        sheet?.onResult("dismiss")
        synchronized(lock) {
            if (_contextMenu.value?.pluginActions?.any { it.pluginId == pluginId } == true) {
                _contextMenu.value = null
            }
        }
    }

    companion object {
        const val MAX_TITLE = 80
        const val MAX_BODY = 32_768
        const val MAX_MESSAGE = 2_000
        const val MAX_BUTTON = 16
        const val MAX_SHEET_ACTIONS = 8
        const val MAX_STACK = 8

        fun parseAlert(raw: Any?): PluginAlertSpec? {
            val map = raw as? Map<*, *> ?: return null
            val title = (map["title"] as? String)?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            if (title.length > MAX_TITLE) return null
            val message = when (val m = map["message"]) {
                null -> null
                is String -> m.takeIf { it.length <= MAX_MESSAGE } ?: return null
                else -> return null
            }
            val confirm = when (val c = map["confirm"]) {
                null -> "确定"
                is String -> c.trim().takeIf { it.isNotEmpty() && it.length <= MAX_BUTTON } ?: return null
                else -> return null
            }
            val cancel = when (val c = map["cancel"]) {
                null -> "取消"
                false -> null
                is String -> c.trim().takeIf { it.isNotEmpty() && it.length <= MAX_BUTTON } ?: return null
                else -> return null
            }
            val destructive = when (val d = map["destructive"]) {
                null -> false
                is Boolean -> d
                else -> return null
            }
            return PluginAlertSpec(
                title = title,
                message = message,
                confirm = confirm,
                cancel = cancel,
                destructive = destructive,
            )
        }

        fun parseSheet(raw: Any?): PluginSheetSpec? {
            val map = raw as? Map<*, *> ?: return null
            val title = (map["title"] as? String)?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            if (title.length > MAX_TITLE) return null
            val message = when (val m = map["message"]) {
                null -> null
                is String -> m.takeIf { it.length <= MAX_MESSAGE } ?: return null
                else -> return null
            }
            val rawActions = map["actions"] as? List<*> ?: return null
            if (rawActions.isEmpty() || rawActions.size > MAX_SHEET_ACTIONS) return null
            val seen = HashSet<String>()
            val actions = ArrayList<PluginSheetActionSpec>(rawActions.size)
            for (item in rawActions) {
                val row = item as? Map<*, *> ?: return null
                val id = (row["id"] as? String)?.trim()?.takeIf {
                    it.isNotEmpty() && it.length <= PluginUiTree.MAX_NAME
                } ?: return null
                if (!seen.add(id)) return null
                val label = (row["label"] as? String)?.trim()?.takeIf {
                    it.isNotEmpty() && it.length <= MAX_BUTTON
                } ?: return null
                val destructive = when (val d = row["destructive"]) {
                    null -> false
                    is Boolean -> d
                    else -> return null
                }
                actions.add(PluginSheetActionSpec(id, label, destructive))
            }
            return PluginSheetSpec(title, message, actions)
        }

        private fun frameKey(pluginId: String, pageName: String, instance: String): String =
            "$pluginId\u0000$pageName\u0000$instance"
    }

    private fun frameKey(pluginId: String, pageName: String, instance: String): String =
        Companion.frameKey(pluginId, pageName, instance)
}
