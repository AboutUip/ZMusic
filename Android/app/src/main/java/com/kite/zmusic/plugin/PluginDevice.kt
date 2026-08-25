package com.kite.zmusic.plugin

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

data class PluginSaveImageRequest(
    val url: String?,
    val pack: String?,
    val filename: String?,
)

data class PluginShareRequest(
    val title: String?,
    val text: String?,
    val url: String?,
    val imageUrl: String?,
    val pack: String?,
)

internal object PluginDeviceParams {
    const val MAX_BYTES = 8_388_608
    const val MAX_FILENAME = 80
    const val MAX_SHARE_TEXT = 8_000
    const val MAX_CLIPBOARD = 32_768
    const val DOWNLOAD_TIMEOUT_MS = 30_000L

    val IMAGE_PACK_EXT: Set<String> = setOf("png", "jpg", "jpeg", "webp", "gif", "bmp")

    fun parseSave(raw: Any?): PluginSaveImageRequest? {
        val map = raw as? Map<*, *> ?: return null
        val url = when (val u = optHttpUrl(map["url"])) {
            Opt.Missing -> null
            Opt.Bad -> return null
            is Opt.Some -> u.value
        }
        val pack = when (val p = optPack(map["pack"])) {
            Opt.Missing -> null
            Opt.Bad -> return null
            is Opt.Some -> p.value
        }
        if ((url == null) == (pack == null)) return null
        val filename = when (val f = optFilename(map["filename"])) {
            Opt.Missing -> null
            Opt.Bad -> return null
            is Opt.Some -> f.value
        }
        return PluginSaveImageRequest(url, pack, filename)
    }

    fun parseShare(raw: Any?): PluginShareRequest? {
        val map = raw as? Map<*, *> ?: return null
        val title = when (val t = optBounded(map["title"], PluginUiTree.MAX_TITLE, trim = true)) {
            Opt.Missing -> null
            Opt.Bad -> return null
            is Opt.Some -> t.value
        }
        val text = when (val t = optBounded(map["text"], MAX_SHARE_TEXT, trim = false)) {
            Opt.Missing -> null
            Opt.Bad -> return null
            is Opt.Some -> t.value
        }
        val url = when (val u = optHttpUrl(map["url"])) {
            Opt.Missing -> null
            Opt.Bad -> return null
            is Opt.Some -> u.value
        }
        val imageUrl = when (val u = optHttpUrl(map["imageUrl"])) {
            Opt.Missing -> null
            Opt.Bad -> return null
            is Opt.Some -> u.value
        }
        val pack = when (val p = optPack(map["pack"])) {
            Opt.Missing -> null
            Opt.Bad -> return null
            is Opt.Some -> p.value
        }
        if (text == null && url == null && imageUrl == null && pack == null) return null
        if (imageUrl != null && pack != null) return null
        return PluginShareRequest(title, text, url, imageUrl, pack)
    }

    fun parseClipboardSet(raw: Any?): String? {
        val s = raw as? String ?: return null
        if (s.length > MAX_CLIPBOARD) return null
        return s
    }

    fun result(ok: Boolean, error: String?): Map<String, Any?> = mapOf(
        "ok" to ok,
        "error" to error,
    )

    fun packIsImage(rel: String): Boolean {
        val ext = rel.substringAfterLast('.', "").lowercase()
        return ext in IMAGE_PACK_EXT
    }

    private sealed class Opt<out T> {
        data object Missing : Opt<Nothing>()
        data object Bad : Opt<Nothing>()
        data class Some<T>(val value: T) : Opt<T>()
    }

    private fun optHttpUrl(raw: Any?): Opt<String> {
        if (raw == null) return Opt.Missing
        val s = (raw as? String)?.trim()?.takeIf { it.isNotEmpty() } ?: return Opt.Bad
        val parsed = s.toHttpUrlOrNull() ?: return Opt.Bad
        if (parsed.scheme != "http" && parsed.scheme != "https") return Opt.Bad
        return Opt.Some(parsed.toString())
    }

    private fun optPack(raw: Any?): Opt<String> {
        if (raw == null) return Opt.Missing
        val s = raw as? String ?: return Opt.Bad
        val rel = PluginPackageRules.normalizeRel(s) ?: return Opt.Bad
        if (!packIsImage(rel)) return Opt.Bad
        return Opt.Some(rel)
    }

    private fun optFilename(raw: Any?): Opt<String> {
        if (raw == null) return Opt.Missing
        val s = raw as? String ?: return Opt.Bad
        val t = s.trim()
        if (t.isEmpty() || t.length > MAX_FILENAME) return Opt.Bad
        if (t.any { it == '/' || it == '\\' || it == '\u0000' }) return Opt.Bad
        return Opt.Some(t)
    }

    private fun optBounded(raw: Any?, max: Int, trim: Boolean): Opt<String> {
        if (raw == null) return Opt.Missing
        val s = raw as? String ?: return Opt.Bad
        val t = if (trim) s.trim() else s
        if (t.isEmpty() || t.length > max) return Opt.Bad
        return Opt.Some(t)
    }
}

/**
 * 设备侧能力。实现切到主线程 / IO，禁止在插件线程做磁盘或 Intent。
 */
interface PluginDeviceHost {
    fun saveImage(
        pluginId: String,
        url: String?,
        bytes: ByteArray?,
        filename: String?,
        onDone: (Map<String, Any?>) -> Unit,
    ): Boolean

    fun share(
        pluginId: String,
        req: PluginShareRequest,
        bytes: ByteArray?,
        onDone: (Map<String, Any?>) -> Unit,
    ): Boolean

    fun clipboardSet(text: String): Boolean

    fun clipboardGet(): String?

    fun cancel(pluginId: String)

    companion object {
        val Noop: PluginDeviceHost = object : PluginDeviceHost {
            override fun saveImage(
                pluginId: String,
                url: String?,
                bytes: ByteArray?,
                filename: String?,
                onDone: (Map<String, Any?>) -> Unit,
            ): Boolean = false

            override fun share(
                pluginId: String,
                req: PluginShareRequest,
                bytes: ByteArray?,
                onDone: (Map<String, Any?>) -> Unit,
            ): Boolean = false

            override fun clipboardSet(text: String): Boolean = false

            override fun clipboardGet(): String? = null

            override fun cancel(pluginId: String) = Unit
        }
    }
}
