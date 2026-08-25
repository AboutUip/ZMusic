package com.kite.zmusic.plugin

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.kite.zmusic.BuildConfig
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/**
 * 相册写入、系统分享、剪贴板。I/O 在 OkHttp 线程；Intent / 剪贴板切主线程。
 */
internal class PluginAndroidDevice(
    private val app: Context,
    private val http: OkHttpClient,
) : PluginDeviceHost {
    private val main = Handler(Looper.getMainLooper())
    private val inflight = ConcurrentHashMap<String, CopyOnWriteArrayList<Pending>>()
    private val shareDir: File = File(app.cacheDir, "plugin-share")

    override fun saveImage(
        pluginId: String,
        url: String?,
        bytes: ByteArray?,
        filename: String?,
        onDone: (Map<String, Any?>) -> Unit,
    ): Boolean {
        if (bytes != null) {
            if (bytes.isEmpty() || bytes.size > PluginDeviceParams.MAX_BYTES) {
                onDone(PluginDeviceParams.result(false, "too_large"))
                return true
            }
            finishSave(bytes, filename, sniffMime(bytes), onDone)
            return true
        }
        val src = url ?: return false
        return enqueueDownload(pluginId, src) { data, error ->
            if (data == null) {
                onDone(PluginDeviceParams.result(false, error ?: "network"))
            } else {
                finishSave(data, filename, sniffMime(data), onDone)
            }
        }
    }

    override fun share(
        pluginId: String,
        req: PluginShareRequest,
        bytes: ByteArray?,
        onDone: (Map<String, Any?>) -> Unit,
    ): Boolean {
        if (bytes != null) {
            if (bytes.isEmpty() || bytes.size > PluginDeviceParams.MAX_BYTES) {
                onDone(PluginDeviceParams.result(false, "too_large"))
                return true
            }
            sendShare(req, bytes, sniffMime(bytes), onDone)
            return true
        }
        val imageUrl = req.imageUrl
        if (imageUrl != null) {
            return enqueueDownload(pluginId, imageUrl) { data, error ->
                if (data == null) {
                    onDone(PluginDeviceParams.result(false, error ?: "network"))
                } else {
                    sendShare(req, data, sniffMime(data), onDone)
                }
            }
        }
        sendShare(req, imageBytes = null, mime = null, onDone = onDone)
        return true
    }

    override fun clipboardSet(text: String): Boolean {
        main.post {
            val cm = app.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return@post
            cm.setPrimaryClip(ClipData.newPlainText("ZMusic", text))
        }
        return true
    }

    override fun clipboardGet(): String? {
        var out: String? = null
        val done = CountDownLatch(1)
        main.post {
            try {
                val cm = app.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                val clip = cm?.primaryClip
                if (clip != null && clip.itemCount > 0) {
                    out = clip.getItemAt(0).coerceToText(app)?.toString()
                }
            } finally {
                done.countDown()
            }
        }
        runCatching { done.await(1, TimeUnit.SECONDS) }
        val text = out ?: return null
        return if (text.length > PluginDeviceParams.MAX_CLIPBOARD) {
            text.take(PluginDeviceParams.MAX_CLIPBOARD)
        } else {
            text
        }
    }

    override fun cancel(pluginId: String) {
        val list = inflight.remove(pluginId) ?: return
        list.forEach { pending ->
            pending.call.cancel()
            pending.complete(null, "cancelled")
        }
    }

    private fun finishSave(
        bytes: ByteArray,
        filename: String?,
        mime: String,
        onDone: (Map<String, Any?>) -> Unit,
    ) {
        val name = displayName(filename, mime)
        val ok = runCatching { writeGallery(bytes, name, mime) }.isSuccess
        onDone(PluginDeviceParams.result(ok, if (ok) null else "io"))
    }

    private fun writeGallery(bytes: ByteArray, displayName: String, mime: String) {
        val resolver = app.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, mime)
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/ZMusic")
            if (Build.VERSION.SDK_INT >= 29) {
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val collection = if (Build.VERSION.SDK_INT >= 29) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        val uri = resolver.insert(collection, values) ?: error("insert")
        resolver.openOutputStream(uri)?.use { it.write(bytes) } ?: error("stream")
        if (Build.VERSION.SDK_INT >= 29) {
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }
    }

    private fun sendShare(
        req: PluginShareRequest,
        imageBytes: ByteArray?,
        mime: String?,
        onDone: (Map<String, Any?>) -> Unit,
    ) {
        main.post {
            val ok = runCatching {
                val send = Intent(Intent.ACTION_SEND)
                val body = buildString {
                    req.text?.let { append(it) }
                    req.url?.let {
                        if (isNotEmpty()) append('\n')
                        append(it)
                    }
                }.takeIf { it.isNotEmpty() }
                if (imageBytes != null) {
                    val ext = extensionOf(mime ?: "image/jpeg")
                    shareDir.mkdirs()
                    val file = File(shareDir, "share-${System.currentTimeMillis()}.$ext")
                    file.writeBytes(imageBytes)
                    val uri = FileProvider.getUriForFile(
                        app,
                        BuildConfig.APPLICATION_ID + ".fileprovider",
                        file,
                    )
                    send.type = mime ?: "image/*"
                    send.putExtra(Intent.EXTRA_STREAM, uri)
                    send.clipData = ClipData.newRawUri("image", uri)
                    send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    body?.let { send.putExtra(Intent.EXTRA_TEXT, it) }
                } else {
                    send.type = "text/plain"
                    send.putExtra(Intent.EXTRA_TEXT, body ?: req.title.orEmpty())
                }
                req.title?.let { send.putExtra(Intent.EXTRA_SUBJECT, it) }
                send.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                val chooser = Intent.createChooser(send, req.title).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                app.startActivity(chooser)
            }.isSuccess
            onDone(PluginDeviceParams.result(ok, if (ok) null else "share"))
        }
    }

    private fun enqueueDownload(
        pluginId: String,
        url: String,
        onDone: (ByteArray?, String?) -> Unit,
    ): Boolean {
        val client = http.newBuilder()
            .callTimeout(PluginDeviceParams.DOWNLOAD_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .connectTimeout(PluginDeviceParams.DOWNLOAD_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .readTimeout(PluginDeviceParams.DOWNLOAD_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
        val request = try {
            Request.Builder().url(url).get().build()
        } catch (_: IllegalArgumentException) {
            return false
        }
        val call = client.newCall(request)
        val pending = Pending(call, onDone)
        inflight.getOrPut(pluginId) { CopyOnWriteArrayList() }.add(pending)
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                val err = when {
                    call.isCanceled() -> "cancelled"
                    e is java.net.SocketTimeoutException -> "timeout"
                    else -> "network"
                }
                completeDownload(pluginId, pending, null, err)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use { resp ->
                    val finalUrl = resp.request.url
                    if (finalUrl.scheme != "http" && finalUrl.scheme != "https") {
                        completeDownload(pluginId, pending, null, "network")
                        return
                    }
                    if (!resp.isSuccessful) {
                        completeDownload(pluginId, pending, null, "network")
                        return
                    }
                    val body = resp.body ?: run {
                        completeDownload(pluginId, pending, null, "network")
                        return
                    }
                    val cap = PluginDeviceParams.MAX_BYTES.toLong()
                    val source = body.source()
                    val tooLarge = try {
                        source.request(cap + 1L)
                    } catch (_: IOException) {
                        completeDownload(pluginId, pending, null, "network")
                        return
                    }
                    if (tooLarge) {
                        completeDownload(pluginId, pending, null, "too_large")
                        return
                    }
                    val bytes = try {
                        source.readByteArray()
                    } catch (_: IOException) {
                        completeDownload(pluginId, pending, null, "network")
                        return
                    }
                    if (bytes.isEmpty()) {
                        completeDownload(pluginId, pending, null, "network")
                        return
                    }
                    completeDownload(pluginId, pending, bytes, null)
                }
            }
        })
        return true
    }

    private fun completeDownload(
        pluginId: String,
        pending: Pending,
        bytes: ByteArray?,
        error: String?,
    ) {
        inflight[pluginId]?.remove(pending)
        pending.complete(bytes, error)
    }

    private class Pending(
        val call: Call,
        val onDone: (ByteArray?, String?) -> Unit,
    ) {
        private val done = AtomicBoolean(false)

        fun complete(bytes: ByteArray?, error: String?) {
            if (done.compareAndSet(false, true)) onDone(bytes, error)
        }
    }

    companion object {
        fun sniffMime(bytes: ByteArray): String {
            if (bytes.size >= 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()) {
                return "image/jpeg"
            }
            if (bytes.size >= 8 &&
                bytes[0] == 0x89.toByte() &&
                bytes[1] == 0x50.toByte() &&
                bytes[2] == 0x4E.toByte() &&
                bytes[3] == 0x47.toByte()
            ) {
                return "image/png"
            }
            if (bytes.size >= 12 &&
                bytes[0] == 0x52.toByte() &&
                bytes[8] == 0x57.toByte() &&
                bytes[9] == 0x45.toByte() &&
                bytes[10] == 0x42.toByte() &&
                bytes[11] == 0x50.toByte()
            ) {
                return "image/webp"
            }
            if (bytes.size >= 6 &&
                bytes[0] == 0x47.toByte() &&
                bytes[1] == 0x49.toByte() &&
                bytes[2] == 0x46.toByte()
            ) {
                return "image/gif"
            }
            return "image/jpeg"
        }

        fun displayName(filename: String?, mime: String): String {
            val ext = extensionOf(mime)
            val raw = filename?.trim().orEmpty()
            if (raw.isEmpty()) return "zmusic-$ext-${System.currentTimeMillis()}.$ext"
            val base = raw.substringAfterLast('/').substringAfterLast('\\')
            return if (base.contains('.')) base else "$base.$ext"
        }

        fun extensionOf(mime: String): String = when (mime.lowercase()) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            "image/gif" -> "gif"
            "image/bmp" -> "bmp"
            else -> "jpg"
        }
    }
}
