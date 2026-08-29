package com.kite.zmusic.data

import com.kite.zmusic.ZMusicPaths
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.fileSize
import okhttp3.OkHttpClient
import okhttp3.Request

data class RealtimeCacheOccupancy(
    val usedBytes: Long = 0L,
    val limitBytes: Long = 32L * 1024L * 1024L,
    val fileCount: Int = 0,
    val downloading: Boolean = false,
) {
    val ratio: Float
        get() = if (limitBytes <= 0L) 0f else (usedBytes.toDouble() / limitBytes.toDouble()).toFloat().coerceIn(0f, 1f)
}

object LocalLibrary {
    private val http = OkHttpClient()
    private val downloading = ConcurrentHashMap.newKeySet<Long>()
    private val cancelled = ConcurrentHashMap.newKeySet<Long>()
    private val inflight = AtomicInteger(0)
    private val io = Executors.newFixedThreadPool(2) { r ->
        Thread(r, "zmusic-cache").apply { isDaemon = true }
    }

    fun cacheDir(): Path = ZMusicPaths.dataDir().resolve("cache").createDirectories()

    fun exportsDir(): Path = ZMusicPaths.dataDir().resolve("exports").createDirectories()

    fun imageCacheDir(): Path = ZMusicPaths.dataDir().resolve("image-cache").createDirectories()

    fun cacheFile(id: Long): Path = cacheDir().resolve("$id.bin")

    fun playUri(
        id: Long,
        localHint: String?,
        realtime: Boolean,
        accel: Boolean,
    ): String? {
        localHint?.takeIf { it.isNotBlank() }?.let { return it }
        if (realtime) {
            findInDirs(id, listOf(cacheDir()))?.let { return it }
        }
        if (accel) {
            findInDirs(id, accelDirs())?.let { return it }
        }
        return null
    }

    fun findTrackFile(id: Long): String? = findInDirs(id, searchDirs())

    fun occupancy(limitMb: Int): RealtimeCacheOccupancy {
        val files = cacheDir().toFile().listFiles()?.filter { it.isFile && !it.name.endsWith(".part") }.orEmpty()
        return RealtimeCacheOccupancy(
            usedBytes = files.sumOf { it.length() },
            limitBytes = limitMb.coerceAtLeast(32).toLong() * 1024L * 1024L,
            fileCount = files.size,
            downloading = inflight.get() > 0,
        )
    }

    fun formatBytes(bytes: Long): String {
        val b = bytes.coerceAtLeast(0L)
        return when {
            b >= 1024L * 1024L * 1024L -> String.format(Locale.US, "%.1f GB", b / (1024.0 * 1024.0 * 1024.0))
            b >= 1024L * 1024L -> String.format(Locale.US, "%.0f MB", b / (1024.0 * 1024.0))
            b >= 1024L -> String.format(Locale.US, "%.0f KB", b / 1024.0)
            else -> "$b B"
        }
    }

    fun startDownload(id: Long, url: String, maxMb: Int = 512) {
        if (id <= 0L) return
        if (!url.startsWith("http://") && !url.startsWith("https://")) return
        val dest = cacheFile(id)
        if (dest.exists() && dest.fileSize() > 1024L) return
        if (!downloading.add(id)) return
        cancelled.remove(id)
        inflight.incrementAndGet()
        val cap = maxMb.coerceIn(64, 4096)
        io.execute {
            try {
                if (cancelled.contains(id)) return@execute
                val req = Request.Builder()
                    .url(url)
                    .header("Referer", "https://music.163.com")
                    .get()
                    .build()
                http.newCall(req).execute().use { resp ->
                    val body = resp.body ?: return@use
                    val tmp = dest.resolveSibling("${dest.fileName}.part")
                    Files.copy(body.byteStream(), tmp, StandardCopyOption.REPLACE_EXISTING)
                    if (cancelled.contains(id)) {
                        Files.deleteIfExists(tmp)
                        return@use
                    }
                    Files.move(tmp, dest, StandardCopyOption.REPLACE_EXISTING)
                }
            } catch (_: Exception) {
                runCatching { Files.deleteIfExists(dest.resolveSibling("${dest.fileName}.part")) }
            } finally {
                downloading.remove(id)
                inflight.decrementAndGet()
                trimToMaxMb(cap)
            }
        }
    }

    fun finishListen(id: Long, listenedMs: Long, durationMs: Long, mode: RealtimeCacheMode, url: String?, maxMb: Int = 512) {
        if (id <= 0L) return
        val dur = durationMs.coerceAtLeast(1L)
        val ratio = listenedMs.toDouble() / dur.toDouble()
        when (mode) {
            RealtimeCacheMode.Cautious -> {
                if (ratio >= 0.60 && !url.isNullOrBlank() && playUri(id, null, realtime = true, accel = false) == null) {
                    startDownload(id, url, maxMb)
                }
            }
            RealtimeCacheMode.Realtime -> {
                if (ratio < 0.60) deleteTrack(id)
            }
            RealtimeCacheMode.Aggressive -> Unit
        }
    }

    fun deleteTrack(id: Long) {
        cancelled.add(id)
        downloading.remove(id)
        runCatching { Files.deleteIfExists(cacheFile(id)) }
        runCatching { Files.deleteIfExists(cacheFile(id).resolveSibling("$id.bin.part")) }
    }

    fun clearCache() {
        cacheDir().toFile().listFiles()?.forEach { f ->
            val id = f.name.substringBefore('.').toLongOrNull()
            if (id != null) cancelled.add(id)
            f.delete()
        }
        downloading.clear()
    }

    fun trimToMaxMb(maxMb: Int) {
        val cap = maxMb.coerceAtLeast(32).toLong() * 1024L * 1024L
        val files = cacheDir().toFile().listFiles()?.filter { it.isFile && !it.name.endsWith(".part") }
            ?.sortedBy { it.lastModified() }
            ?: return
        var total = files.sumOf { it.length() }
        for (f in files) {
            if (total <= cap) break
            total -= f.length()
            f.delete()
        }
    }

    fun listCachedTracks(): List<TrackRow> {
        val byId = linkedMapOf<Long, TrackRow>()
        for (dir in searchDirs()) {
            val files = dir.toFile().listFiles()?.filter { it.isFile && !it.name.endsWith(".part") } ?: continue
            for (f in files) {
                val id = f.name.substringBefore('.').toLongOrNull() ?: continue
                if (id <= 0L || f.length() <= 1024L) continue
                if (byId.containsKey(id)) continue
                byId[id] = TrackRow(
                    id = id,
                    name = "缓存 $id",
                    artists = "本机",
                    album = null,
                    durationMs = 0L,
                    coverUrl = null,
                    localAudioUri = f.toURI().toString(),
                )
            }
        }
        return byId.values.toList()
    }

    fun readImageCache(url: String): ByteArray? {
        val p = imageFile(url)
        if (!p.exists() || p.fileSize() < 32L) return null
        return runCatching { Files.readAllBytes(p) }.getOrNull()
    }

    fun writeImageCache(url: String, bytes: ByteArray) {
        if (bytes.size < 32) return
        runCatching {
            val p = imageFile(url)
            Files.write(p, bytes)
            trimImageCache()
        }
    }

    fun uriIfExists(id: Long): String? = findTrackFile(id)

    fun parseFileUri(uri: String): Path? = runCatching {
        Path.of(URI(uri))
    }.getOrNull()

    private fun searchDirs(): List<Path> = accelDirs() + listOf(cacheDir())

    private fun accelDirs(): List<Path> {
        val override = System.getProperty("zmusic.home")?.trim().orEmpty()
        if (override.isNotEmpty()) return listOf(exportsDir())
        val home = System.getProperty("user.home").orEmpty()
        return listOf(
            exportsDir(),
            Path.of(home, "Music", "ZMusic"),
            Path.of(home, "Downloads", "ZMusic"),
            Path.of(home, "Download", "ZMusic"),
        )
    }

    private fun findInDirs(id: Long, dirs: List<Path>): String? {
        val names = listOf("$id.mp3", "$id.flac", "$id.m4a", "$id.ogg", "$id.bin")
        for (dir in dirs) {
            if (!dir.exists()) continue
            for (name in names) {
                val p = dir.resolve(name)
                if (p.exists() && p.fileSize() > 1024L) return p.toUri().toString()
            }
        }
        return null
    }

    private fun imageFile(url: String): Path {
        val digest = MessageDigest.getInstance("SHA-1").digest(url.toByteArray())
        val name = digest.joinToString("") { b -> "%02x".format(b) }
        return imageCacheDir().resolve("$name.img")
    }

    private fun trimImageCache() {
        val files = imageCacheDir().toFile().listFiles()?.filter { it.isFile }?.sortedBy { it.lastModified() }
            ?: return
        var total = files.sumOf { it.length() }
        val cap = 96L * 1024L * 1024L
        for (f in files) {
            if (total <= cap) break
            total -= f.length()
            f.delete()
        }
    }
}
