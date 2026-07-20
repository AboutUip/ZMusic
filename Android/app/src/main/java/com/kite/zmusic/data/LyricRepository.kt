package com.kite.zmusic.data

import android.content.Context
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 歌词磁盘/内存缓存：不依赖 PlaybackService，冷启动即可读出。
 */
class LyricRepository(
    context: Context,
    private val userClient: NcmUserClient = NcmUserClient(),
) {
    private val appContext = context.applicationContext
    private val memory = object : LruCache<Long, List<LrcLine>>(48) {}
    private val dir = File(appContext.filesDir, "zmusic_lyrics").apply { mkdirs() }
    private val legacyDir = File(appContext.cacheDir, "zmusic_lyrics_cache")

    fun peekMemory(songId: Long): List<LrcLine>? = memory.get(songId)

    suspend fun loadBestEffort(songId: Long, cookie: String): List<LrcLine> {
        memory.get(songId)?.let { return it }
        val fromDisk = withContext(Dispatchers.IO) { readFromDisk(songId) }
        if (fromDisk != null) {
            memory.put(songId, fromDisk)
            return fromDisk
        }
        return try {
            val json = withContext(Dispatchers.IO) { userClient.lyric(songId, cookie) }
            val raw = NcmPlaybackParse.lrcText(json)
            val lines = raw?.let(LrcParser::parse).orEmpty()
            if (raw != null && lines.isNotEmpty()) {
                memory.put(songId, lines)
                withContext(Dispatchers.IO) {
                    runCatching { diskFile(songId).writeText(raw, Charsets.UTF_8) }
                    trimDiskLocked()
                }
            } else if (lines.isNotEmpty()) {
                memory.put(songId, lines)
            }
            lines
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** 预热邻曲歌词（已命中则立刻返回）。 */
    suspend fun prefetch(songId: Long, cookie: String) {
        if (memory.get(songId) != null) return
        loadBestEffort(songId, cookie)
    }

    private fun diskFile(songId: Long) = File(dir, "$songId.lrc")

    private fun readFromDisk(songId: Long): List<LrcLine>? {
        val primary = diskFile(songId)
        if (primary.exists()) {
            return runCatching {
                LrcParser.parse(primary.readText(Charsets.UTF_8))
            }.getOrNull()?.takeIf { it.isNotEmpty() }
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
                        lines
                    }.getOrNull()?.takeIf { it.isNotEmpty() }
                }
        }
        return null
    }

    /** 磁盘歌词上限：按最旧修改时间淘汰，避免 filesDir 无限涨。 */
    private fun trimDiskLocked() {
        val files = dir.listFiles()?.filter { it.isFile && it.name.endsWith(".lrc") }
            ?.sortedBy { it.lastModified() }
            ?: return
        var excess = files.size - DISK_MAX_FILES
        var i = 0
        while (excess > 0 && i < files.size) {
            if (files[i++].delete()) excess--
        }
    }

    companion object {
        private const val DISK_MAX_FILES = 80
    }
}
