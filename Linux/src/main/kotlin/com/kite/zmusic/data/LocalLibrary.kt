package com.kite.zmusic.data

import com.kite.zmusic.ZMusicPaths
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.fileSize
import okhttp3.OkHttpClient
import okhttp3.Request

object LocalLibrary {
    private val http = OkHttpClient()

    fun cacheDir(): Path = ZMusicPaths.dataDir().resolve("cache").createDirectories()

    fun exportsDir(): Path = ZMusicPaths.dataDir().resolve("exports").createDirectories()

    fun findTrackFile(id: Long): String? {
        val names = listOf("$id.mp3", "$id.flac", "$id.m4a", "$id.ogg", "$id.bin")
        for (dir in listOf(exportsDir(), cacheDir())) {
            for (name in names) {
                val p = dir.resolve(name)
                if (p.exists() && p.fileSize() > 1024L) return p.toUri().toString()
            }
        }
        return null
    }

    fun cacheFile(id: Long): Path = cacheDir().resolve("$id.bin")

    fun trimToMaxMb(maxMb: Int) {
        val cap = maxMb.coerceAtLeast(32).toLong() * 1024L * 1024L
        val files = cacheDir().toFile().listFiles()?.filter { it.isFile }?.sortedBy { it.lastModified() }
            ?: return
        var total = files.sumOf { it.length() }
        for (f in files) {
            if (total <= cap) break
            total -= f.length()
            f.delete()
        }
    }

    fun startDownload(id: Long, url: String) {
        if (!url.startsWith("http://") && !url.startsWith("https://")) return
        val dest = cacheFile(id)
        if (dest.exists() && dest.fileSize() > 1024L) return
        Thread({
            runCatching {
                val req = Request.Builder().url(url).get().build()
                http.newCall(req).execute().use { resp ->
                    val body = resp.body ?: return@use
                    val tmp = dest.resolveSibling("${dest.fileName}.part")
                    Files.copy(body.byteStream(), tmp, StandardCopyOption.REPLACE_EXISTING)
                    Files.move(tmp, dest, StandardCopyOption.REPLACE_EXISTING)
                }
            }
        }, "zmusic-cache-$id").apply { isDaemon = true }.start()
    }

    fun uriIfExists(id: Long): String? = findTrackFile(id)

    fun parseFileUri(uri: String): Path? = runCatching {
        Path.of(URI(uri))
    }.getOrNull()
}
