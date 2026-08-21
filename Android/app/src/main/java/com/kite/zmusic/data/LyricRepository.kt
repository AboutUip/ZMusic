package com.kite.zmusic.data

import android.content.Context
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class LyricPack(
    val original: List<LrcLine>,
    val translated: List<LrcLine> = emptyList(),
    val wordOriginal: List<LrcLine> = emptyList(),
    val wordTranslated: List<LrcLine> = emptyList(),
    /** 已向接口确认过是否存在 `tlyric` / `yrc`（空也算已确认）。 */
    val translationResolved: Boolean = false,
) {
    companion object {
        val Empty = LyricPack(
            original = emptyList(),
            translated = emptyList(),
            wordOriginal = emptyList(),
            wordTranslated = emptyList(),
            translationResolved = true,
        )
    }
}

/**
 * 歌词磁盘/内存缓存：不依赖 PlaybackService，冷启动即可读出。
 */
class LyricRepository(
    context: Context,
    private val userClient: NcmUserClient,
) {
    private val appContext = context.applicationContext
    private val memory = object : LruCache<Long, LyricPack>(48) {}
    private val dir = File(appContext.filesDir, "zmusic_lyrics").apply { mkdirs() }
    private val legacyDir = File(appContext.cacheDir, "zmusic_lyrics_cache")

    fun peekPack(songId: Long): LyricPack? = memory.get(songId)

    fun peekMemory(songId: Long): List<LrcLine>? =
        memory.get(songId)?.original?.takeIf { it.isNotEmpty() }

    suspend fun loadBestEffort(songId: Long, cookie: String): LyricPack {
        memory.get(songId)?.takeIf { it.translationResolved && it.wordOriginal.isNotEmpty() }?.let {
            return it
        }
        val fromDisk = withContext(Dispatchers.IO) { readFromDisk(songId) }
        if (fromDisk != null && fromDisk.wordOriginal.isNotEmpty()) {
            memory.put(songId, fromDisk)
            return fromDisk
        }
        if (fromDisk != null && fromDisk.translationResolved) {
            memory.put(songId, fromDisk)
            return fromDisk
        }
        return try {
            val json = withContext(Dispatchers.IO) { userClient.lyric(songId, cookie) }
            val originalRaw = NcmPlaybackParse.lrcText(json)
            val translatedRaw = NcmPlaybackParse.translatedLrcText(json)
            val yrcRaw = NcmPlaybackParse.yrcText(json)
            val ytlrcRaw = NcmPlaybackParse.ytlrcText(json)
            val original = originalRaw?.let(LrcParser::parse)?.takeIf { it.isNotEmpty() }
                ?: fromDisk?.original.orEmpty()
            val translated = translatedRaw?.let(LrcParser::parse).orEmpty()
            val wordOriginal = yrcRaw?.let(YrcParser::parse).orEmpty()
            val wordTranslated = ytlrcRaw?.let(YrcParser::parse).orEmpty()
            if (original.isEmpty() && wordOriginal.isEmpty()) {
                return fromDisk ?: LyricPack.Empty
            }
            val pack = LyricPack(
                original = original.ifEmpty { wordOriginal.map { it.copy(words = emptyList()) } },
                translated = translated,
                wordOriginal = wordOriginal,
                wordTranslated = wordTranslated,
                translationResolved = true,
            )
            memory.put(songId, pack)
            withContext(Dispatchers.IO) {
                writePackLocked(
                    songId = songId,
                    originalRaw = originalRaw,
                    translatedRaw = translatedRaw,
                    yrcRaw = yrcRaw,
                    ytlrcRaw = ytlrcRaw,
                )
            }
            pack
        } catch (_: Exception) {
            val fallback = fromDisk ?: memory.get(songId)
            if (fallback != null) {
                memory.put(songId, fallback)
                fallback
            } else {
                LyricPack.Empty
            }
        }
    }

    suspend fun loadFromLocalUris(lrcUri: String?, transUri: String?): LyricPack {
        return withContext(Dispatchers.IO) {
            val original = readUriText(lrcUri)?.let(LrcParser::parse).orEmpty()
            val translated = readUriText(transUri)?.let(LrcParser::parse).orEmpty()
            if (original.isEmpty() && translated.isEmpty()) LyricPack.Empty
            else LyricPack(
                original = original,
                translated = translated,
                translationResolved = true,
            )
        }
    }

    private fun readUriText(uri: String?): String? {
        val raw = uri?.trim().orEmpty()
        if (raw.isEmpty()) return null
        return runCatching {
            appContext.contentResolver.openInputStream(android.net.Uri.parse(raw))?.use {
                it.readBytes().toString(Charsets.UTF_8)
            }
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    /** 预热邻曲歌词（已命中则立刻返回）。 */
    suspend fun prefetch(songId: Long, cookie: String) {
        if (memory.get(songId)?.translationResolved == true) return
        loadBestEffort(songId, cookie)
    }

    private fun diskFile(songId: Long) = File(dir, "$songId.lrc")

    private fun transFile(songId: Long) = File(dir, "$songId.trans.lrc")

    private fun transDoneFile(songId: Long) = File(dir, "$songId.trans.done")

    private fun yrcFile(songId: Long) = File(dir, "$songId.yrc")

    private fun ytlrcFile(songId: Long) = File(dir, "$songId.ytlrc")

    private fun yrcDoneFile(songId: Long) = File(dir, "$songId.yrc.v2.done")

    private fun writePackLocked(
        songId: Long,
        originalRaw: String?,
        translatedRaw: String?,
        yrcRaw: String?,
        ytlrcRaw: String?,
    ) {
        if (originalRaw != null) {
            runCatching { diskFile(songId).writeText(originalRaw, Charsets.UTF_8) }
        }
        val trans = transFile(songId)
        if (!translatedRaw.isNullOrBlank()) {
            runCatching { trans.writeText(translatedRaw, Charsets.UTF_8) }
        } else {
            runCatching { trans.delete() }
        }
        val yrc = yrcFile(songId)
        if (!yrcRaw.isNullOrBlank()) {
            runCatching { yrc.writeText(yrcRaw, Charsets.UTF_8) }
        } else {
            runCatching { yrc.delete() }
        }
        val ytlrc = ytlrcFile(songId)
        if (!ytlrcRaw.isNullOrBlank()) {
            runCatching { ytlrc.writeText(ytlrcRaw, Charsets.UTF_8) }
        } else {
            runCatching { ytlrc.delete() }
        }
        runCatching { transDoneFile(songId).writeText("", Charsets.UTF_8) }
        runCatching { yrcDoneFile(songId).writeText("", Charsets.UTF_8) }
        trimDiskLocked()
    }

    private fun readFromDisk(songId: Long): LyricPack? {
        val originalRaw = readOriginalText(songId)
        val original = originalRaw?.let {
            runCatching { LrcParser.parse(it) }.getOrNull()
        }.orEmpty()
        val yrcLines = if (yrcFile(songId).exists()) {
            runCatching {
                YrcParser.parse(yrcFile(songId).readText(Charsets.UTF_8))
            }.getOrNull().orEmpty()
        } else {
            emptyList()
        }
        if (original.isEmpty() && yrcLines.isEmpty()) return null
        val resolved = transDoneFile(songId).exists() && yrcDoneFile(songId).exists()
        val translated = if (transFile(songId).exists()) {
            runCatching {
                LrcParser.parse(transFile(songId).readText(Charsets.UTF_8))
            }.getOrNull().orEmpty()
        } else {
            emptyList()
        }
        val ytlrcLines = if (ytlrcFile(songId).exists()) {
            runCatching {
                YrcParser.parse(ytlrcFile(songId).readText(Charsets.UTF_8))
            }.getOrNull().orEmpty()
        } else {
            emptyList()
        }
        return LyricPack(
            original = original.ifEmpty { yrcLines.map { it.copy(words = emptyList()) } },
            translated = translated,
            wordOriginal = yrcLines,
            wordTranslated = ytlrcLines,
            translationResolved = resolved,
        )
    }

    private fun readOriginalText(songId: Long): String? {
        val primary = diskFile(songId)
        if (primary.exists()) {
            return runCatching { primary.readText(Charsets.UTF_8) }.getOrNull()
        }
        if (legacyDir.isDirectory) {
            legacyDir.listFiles()
                ?.firstOrNull { it.name.startsWith("${songId}_") && it.name.endsWith(".lrc") }
                ?.let { legacy ->
                    return runCatching {
                        val text = legacy.readText(Charsets.UTF_8)
                        val lines = LrcParser.parse(text)
                        if (lines.isNotEmpty()) {
                            runCatching { primary.writeText(text, Charsets.UTF_8) }
                        }
                        text
                    }.getOrNull()
                }
        }
        return null
    }

    /** 磁盘歌词上限：按最旧修改时间淘汰，避免 filesDir 无限涨。 */
    private fun trimDiskLocked() {
        val files = dir.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".lrc") && !it.name.endsWith(".trans.lrc") }
            ?.sortedBy { it.lastModified() }
            ?: return
        var excess = files.size - DISK_MAX_FILES
        var i = 0
        while (excess > 0 && i < files.size) {
            val f = files[i++]
            val idPart = f.name.removeSuffix(".lrc")
            val deleted = f.delete()
            runCatching { File(dir, "$idPart.trans.lrc").delete() }
            runCatching { File(dir, "$idPart.trans.done").delete() }
            runCatching { File(dir, "$idPart.yrc").delete() }
            runCatching { File(dir, "$idPart.ytlrc").delete() }
            runCatching { File(dir, "$idPart.yrc.done").delete() }
            runCatching { File(dir, "$idPart.yrc.v2.done").delete() }
            if (deleted) excess--
        }
    }

    companion object {
        private const val DISK_MAX_FILES = 80
    }
}
