package com.kite.zmusic.plugin

/**
 * 插件组件树。解析失败返回 null（整树作废，不部分生效）。
 */
data class PluginUiNode(
    val type: String,
    val id: String?,
    val props: Map<String, Any?>,
    val children: List<PluginUiNode>,
) {
    fun str(key: String): String? = props[key] as? String

    fun bool(key: String, default: Boolean = false): Boolean =
        when (val v = props[key]) {
            is Boolean -> v
            else -> default
        }

    fun num(key: String): Double? = when (val v = props[key]) {
        is Number -> v.toDouble()
        else -> null
    }

    fun toJson(): Map<String, Any?> {
        val out = LinkedHashMap<String, Any?>()
        out["type"] = type
        if (id != null) out["id"] = id
        props.forEach { (k, v) -> out[k] = v }
        if (children.isNotEmpty()) out["children"] = children.map { it.toJson() }
        return out
    }
}

data class PluginPageDef(
    val pluginId: String,
    val pluginName: String,
    val name: String,
    val title: String,
    val root: PluginUiNode,
) {
    fun toJson(): Map<String, Any?> = mapOf(
        "name" to name,
        "title" to title,
        "root" to root.toJson(),
    )
}

internal object PluginUiTree {
    const val DEFAULT_PAGE = "default"
    const val SLOT_FEATURES = "features.grid"
    const val SLOT_SETTINGS = "settings.rows"
    const val MAX_PAGES = 16
    const val MAX_SLOTS = 16
    const val MAX_ACTIONS = 16
    const val MAX_STACK = 8
    const val MAX_NODES = 200
    const val MAX_DEPTH = 12
    const val MAX_CHILDREN = 48
    const val MAX_TEXT = 8_192
    const val MAX_LEGACY_BODY = 32_768
    const val MAX_TITLE = 80
    const val MAX_NAME = 64
    const val MAX_LABEL = 80

    val SLOTS: Set<String> = setOf(SLOT_FEATURES, SLOT_SETTINGS)

    private val TYPES = setOf(
        "column", "row", "scroll", "spacer", "section",
        "tabs", "tab",
        "text", "image", "empty", "loading",
        "button", "toggle", "slider", "field", "segmented", "option",
        "list", "item", "track",
    )

    fun pageName(raw: Any?): String? {
        val s = raw as? String ?: return null
        val t = s.trim()
        return t.takeIf { it.isNotEmpty() && it.length <= MAX_NAME }
    }

    fun parseDefine(raw: Any?): Pair<String, PluginUiNode>? {
        val map = raw as? Map<*, *> ?: return null
        val title = (map["title"] as? String)?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (title.length > MAX_TITLE) return null
        val counter = intArrayOf(0)
        val root = parseNode(map["root"], 0, counter) ?: return null
        if (counter[0] > MAX_NODES) return null
        return title to root
    }

    fun parseLegacySet(raw: Any?): Pair<String, PluginUiNode>? {
        val map = raw as? Map<*, *> ?: return null
        if (map["root"] != null) return parseDefine(raw)
        val title = (map["title"] as? String)?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (title.length > MAX_TITLE) return null
        val body = when (val b = map["body"]) {
            null -> ""
            is String -> b
            else -> return null
        }
        if (body.length > MAX_LEGACY_BODY) return null
        val text = PluginUiNode(
            type = "text",
            id = null,
            props = mapOf("text" to body, "style" to "body"),
            children = emptyList(),
        )
        val root = PluginUiNode("scroll", null, emptyMap(), listOf(text))
        return title to root
    }

    fun parseNode(raw: Any?, depth: Int, counter: IntArray): PluginUiNode? {
        if (depth > MAX_DEPTH) return null
        val map = raw as? Map<*, *> ?: return null
        val type = (map["type"] as? String)?.trim()?.lowercase() ?: return null
        if (type !in TYPES) return null
        counter[0] += 1
        if (counter[0] > MAX_NODES) return null
        val id = when (val i = map["id"]) {
            null -> null
            is String -> i.trim().takeIf { it.isNotEmpty() && it.length <= MAX_NAME }
            else -> return null
        }
        val childrenRaw = map["children"]
        val children = when (childrenRaw) {
            null -> emptyList()
            is List<*> -> {
                if (childrenRaw.size > MAX_CHILDREN) return null
                childrenRaw.map { parseNode(it, depth + 1, counter) ?: return null }
            }
            else -> return null
        }
        if (!childrenOk(type, children)) return null
        val props = LinkedHashMap<String, Any?>()
        for ((k, v) in map) {
            val key = k as? String ?: return null
            if (key == "type" || key == "id" || key == "children") continue
            when (val copied = PluginJsonable.copy(v)) {
                PluginJsonCopy.Fail -> return null
                is PluginJsonCopy.Ok -> {
                    if (!propOk(type, key, copied.value)) return null
                    props[key] = copied.value
                }
            }
        }
        if (!requiredOk(type, props, children, id)) return null
        return PluginUiNode(type, id, props, children)
    }

    private fun patchInner(
        node: PluginUiNode,
        id: String,
        props: Map<String, Any?>,
        children: List<PluginUiNode>?,
    ): Pair<PluginUiNode, Boolean>? {
        if (node.id == id) {
            val merged = LinkedHashMap(node.props)
            for ((k, v) in props) {
                if (k == "type" || k == "id" || k == "children") continue
                if (!propOk(node.type, k, v)) return null
                merged[k] = v
            }
            val nextChildren = children ?: node.children
            if (!childrenOk(node.type, nextChildren)) return null
            if (!requiredOk(node.type, merged, nextChildren, node.id)) return null
            return node.copy(props = merged, children = nextChildren) to true
        }
        var hit = false
        val next = ArrayList<PluginUiNode>(node.children.size)
        for (child in node.children) {
            val inner = patchInner(child, id, props, children) ?: return null
            if (inner.second) hit = true
            next.add(inner.first)
        }
        return if (hit) node.copy(children = next) to true else node to false
    }

    fun instanceKey(params: Map<String, Any?>): String {
        if (params.isEmpty()) return "_"
        when (val id = params["id"]) {
            is String -> {
                val t = id.trim()
                if (t.isNotEmpty()) return t.take(MAX_NAME)
            }
            is Number -> return id.toString().take(MAX_NAME)
        }
        return PluginJson.stringify(params).hashCode().toUInt().toString(16)
    }

    fun stackKey(pluginId: String, pageName: String, instance: String): String =
        "plugin-page-$pluginId-$pageName-$instance"

    fun validLimits(root: PluginUiNode): Boolean {
        var count = 0
        fun walk(node: PluginUiNode, depth: Int): Boolean {
            if (depth > MAX_DEPTH) return false
            if (node.children.size > MAX_CHILDREN) return false
            count++
            if (count > MAX_NODES) return false
            return node.children.all { walk(it, depth + 1) }
        }
        return walk(root, 0)
    }

    fun patch(
        root: PluginUiNode,
        id: String,
        props: Map<String, Any?>,
        children: List<PluginUiNode>?,
    ): PluginUiNode? {
        val result = patchInner(root, id, props, children) ?: return null
        if (!result.second) return null
        return result.first.takeIf { validLimits(it) }
    }

    fun parseParams(raw: Any?): Map<String, Any?>? {
        if (raw == null) return emptyMap()
        return when (val copied = PluginJsonable.copy(raw)) {
            PluginJsonCopy.Fail -> null
            is PluginJsonCopy.Ok -> {
                val map = copied.value as? Map<*, *> ?: return null
                val out = LinkedHashMap<String, Any?>()
                for ((k, v) in map) {
                    val key = k as? String ?: return null
                    out[key] = v
                }
                out
            }
        }
    }

    fun parsePatch(raw: Any?): Triple<String, Map<String, Any?>, List<PluginUiNode>?>? {
        val map = raw as? Map<*, *> ?: return null
        val id = (map["id"] as? String)?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val props = LinkedHashMap<String, Any?>()
        var children: List<PluginUiNode>? = null
        if (map.containsKey("children")) {
            val counter = intArrayOf(0)
            val rawChildren = map["children"] as? List<*> ?: return null
            if (rawChildren.size > MAX_CHILDREN) return null
            children = rawChildren.map { parseNode(it, 1, counter) ?: return null }
        }
        for ((k, v) in map) {
            val key = k as? String ?: return null
            if (key == "id" || key == "children" || key == "type") continue
            when (val copied = PluginJsonable.copy(v)) {
                PluginJsonCopy.Fail -> return null
                is PluginJsonCopy.Ok -> props[key] = copied.value
            }
        }
        return Triple(id, props, children)
    }

    data class SlotSpec(
        val id: String,
        val title: String,
        val subtitle: String?,
        val icon: String?,
        val accent: String?,
        val page: String?,
    )

    fun parseSlot(raw: Any?): SlotSpec? {
        val map = raw as? Map<*, *> ?: return null
        val id = (map["id"] as? String)?.trim()?.takeIf { it.isNotEmpty() && it.length <= MAX_NAME }
            ?: return null
        val title = (map["title"] as? String)?.trim()?.takeIf { it.isNotEmpty() && it.length <= MAX_TITLE }
            ?: return null
        val subtitle = when (val s = map["subtitle"]) {
            null -> null
            is String -> s.takeIf { it.length <= MAX_LABEL } ?: return null
            else -> return null
        }
        val icon = when (val s = map["icon"]) {
            null -> null
            is String -> s.trim().takeIf { it.isNotEmpty() && it.length <= 32 }
            else -> return null
        }
        val accent = when (val s = map["accent"]) {
            null -> null
            is String -> s.trim().takeIf { it.isNotEmpty() && it.length <= 16 }
            else -> return null
        }
        val page = when (val s = map["page"]) {
            null -> null
            is String -> pageName(s) ?: return null
            else -> return null
        }
        return SlotSpec(id, title, subtitle, icon, accent, page)
    }

    data class ActionSpec(
        val id: String,
        val title: String,
        val icon: String?,
        val destructive: Boolean,
    )

    fun parseAction(raw: Any?): ActionSpec? {
        val map = raw as? Map<*, *> ?: return null
        val id = (map["id"] as? String)?.trim()?.takeIf { it.isNotEmpty() && it.length <= MAX_NAME }
            ?: return null
        val title = (map["title"] as? String)?.trim()?.takeIf { it.isNotEmpty() && it.length <= MAX_TITLE }
            ?: return null
        val icon = when (val s = map["icon"]) {
            null -> null
            is String -> s.trim().takeIf { it.isNotEmpty() && it.length <= 32 }
            else -> return null
        }
        val destructive = when (val d = map["destructive"]) {
            null -> false
            is Boolean -> d
            else -> return null
        }
        return ActionSpec(id, title, icon, destructive)
    }

    private fun childrenOk(type: String, children: List<PluginUiNode>): Boolean = when (type) {
        "tabs" -> children.isNotEmpty() && children.all { it.type == "tab" && it.id != null }
        "tab" -> true
        "segmented" -> children.isNotEmpty() && children.all { it.type == "option" && it.id != null }
        "list" -> children.all { it.type == "item" || it.type == "track" }
        "option", "spacer", "text", "image", "empty", "loading",
        "button", "toggle", "slider", "field",
        -> children.isEmpty()
        "item", "track" -> children.isEmpty()
        else -> true
    }

    private fun requiredOk(
        type: String,
        props: Map<String, Any?>,
        children: List<PluginUiNode>,
        id: String?,
    ): Boolean = when (type) {
        "text" -> (props["text"] as? String)?.let { it.length <= MAX_TEXT } == true
        "button" -> !((props["label"] as? String).isNullOrBlank())
        "tab" -> id != null && !(props["label"] as? String).isNullOrBlank()
        "option" -> id != null && !(props["label"] as? String).isNullOrBlank()
        "item" -> !(props["title"] as? String).isNullOrBlank()
        "track" -> !(props["title"] as? String).isNullOrBlank()
        "image" -> !(props["src"] as? String).isNullOrBlank()
        "slider" -> true
        "tabs", "segmented" -> {
            val v = props["value"]
            v == null || (v is String && children.any { it.id == v })
        }
        else -> true
    }

    private fun propOk(type: String, key: String, value: Any?): Boolean {
        if (key.length > 32) return false
        return when (key) {
            "text", "label", "title", "subtitle", "trailing", "placeholder",
            "style", "role", "src", "coverUrl", "icon", "accent", "state",
            -> value is String && value.length <= when (key) {
                "text" -> MAX_TEXT
                "src", "coverUrl" -> 2048
                else -> MAX_LABEL
            }
            "color", "weight", "align", "fit", "width" -> value is String && value.length <= 32
            "enabled", "multiline" -> value is Boolean
            "value" -> when (value) {
                null, is Boolean, is Number -> true
                is String -> value.length <= MAX_TEXT
                else -> false
            }
            "min", "max", "durationMs", "step" -> value is Number
            "height" -> value is Number && value.toDouble() in 1.0..400.0
            "gap", "pad", "padH", "padV" -> value is Number && value.toDouble() in 0.0..48.0
            "size" -> value is Number && value.toDouble() in 10.0..32.0
            "radius" -> value is Number && value.toDouble() in 0.0..48.0
            "flex" -> value is Number && value.toDouble() in 0.0..8.0
            else -> value == null || value is Boolean || value is String || value is Number
        }
    }
}
