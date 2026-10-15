package com.kite.zmusic.data.platform

import android.util.Log
import com.kite.zmusic.data.AudioQuality
import com.kite.zmusic.data.TrackRow
import com.whl.quickjs.wrapper.JSCallFunction
import com.whl.quickjs.wrapper.QuickJSContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.security.MessageDigest
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * 没有官方播放地址的平台，用用户填的脚本地址换 http(s) 直链。
 * 脚本通过 lx.on("request") 接收 musicUrl，再用 lx.request 访问网络。
 */
class CustomPlaySourceClient(
    private val http: OkHttpClient,
    private val store: CustomPlaySourceStore,
) {
    private val worker = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "custom-play-source").apply { isDaemon = true }
    }
    private val fetch = http.newBuilder()
        .dns(PlaybackDns())
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .callTimeout(45, TimeUnit.SECONDS)
        .build()
    private val scriptLock = Any()
    private var context: QuickJSContext? = null
    private var loadedUrl: String? = null
    private var loadedText: String? = null
    private val httpQueue = ConcurrentLinkedQueue<PendingHttp>()

    suspend fun musicUrl(platform: MusicPlatform, track: TrackRow, quality: AudioQuality): String? {
        val scriptUrl = store.url()
        if (!scriptUrl.startsWith("http://") && !scriptUrl.startsWith("https://")) {
            Log.w(TAG, "stop empty-or-bad-script platform=${platform.id}")
            return null
        }
        val source = sourceCode(platform)
        if (source.isBlank()) {
            Log.w(TAG, "stop no-source-code platform=${platform.id}")
            return null
        }
        return withContext(Dispatchers.IO) {
            runCatching { resolveOnWorker(scriptUrl, source, track, quality) }
                .onFailure { Log.e(TAG, "resolve threw id=${track.id}", it) }
                .getOrNull()
        }
    }

    private fun resolveOnWorker(
        scriptUrl: String,
        source: String,
        track: TrackRow,
        quality: AudioQuality,
    ): String? {
        val text = scriptText(scriptUrl)
        val labels = listOf(qualityLabel(quality), "128k").distinct()
        val api = directApi(text)
        if (api != null) {
            for (label in labels) {
                val url = directUrl(text, source, track, label)
                if (!url.isNullOrBlank()) return url
            }
            Log.w(TAG, "direct api returned no url source=$source id=${track.id}")
            return null
        }
        ensureScript(scriptUrl, text)
        for (label in labels) {
            val url = ask(source, track, label)
            if (!url.isNullOrBlank()) return url
        }
        return null
    }

    private fun scriptText(url: String): String {
        synchronized(scriptLock) {
            if (loadedUrl == url && !loadedText.isNullOrBlank()) {
                return loadedText!!
            }
            var text: String? = null
            for (candidate in scriptCandidates(url)) {
                text = download(candidate)
                if (text != null) break
            }
            val body = text ?: error("脚本下载失败")
            if (body.startsWith("<") || body.length > 4_000_000) error("脚本内容无效")
            loadedUrl = url
            loadedText = body
            context?.destroy()
            context = null
            return body
        }
    }

    private fun scriptCandidates(url: String): List<String> {
        val mirrors = mutableListOf(url)
        if (url.contains("cdn.jsdelivr.net/gh/pdone/lx-music-source@main/")) {
            val path = url.substringAfter("cdn.jsdelivr.net/gh/pdone/lx-music-source@main/")
            mirrors += "https://raw.githubusercontent.com/pdone/lx-music-source/main/$path"
            mirrors += "https://ghproxy.net/https://raw.githubusercontent.com/pdone/lx-music-source/main/$path"
        }
        return mirrors.distinct()
    }

    private fun directApi(script: String): Pair<String, String>? {
        val api = Regex("""API_URL\s*=\s*['"](https?://[^'"]+)['"]""")
            .find(script)?.groupValues?.getOrNull(1)?.trimEnd('/')
            ?: return null
        val key = Regex("""API_KEY\s*=\s*['"]([^'"]+)['"]""")
            .find(script)?.groupValues?.getOrNull(1)
            ?: return null
        if (!script.contains("/url/")) return null
        return api to key
    }

    private fun directUrl(script: String, source: String, track: TrackRow, quality: String): String? {
        val (api, key) = directApi(script) ?: return null
        val songId = when (source) {
            "kg" -> track.sourceHash?.takeIf { it.isNotBlank() } ?: track.sourceId
            else -> track.sourceId?.takeIf { it.isNotBlank() }
        } ?: track.id.toString()
        val endpoint = "$api/url/$source/$songId/$quality"
        val request = Request.Builder()
            .url(endpoint)
            .header("User-Agent", "lx-music-mobile/2.0.0")
            .header("X-Request-Key", key)
            .get()
            .build()
        fetch.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) return null
            val json = runCatching { JSONObject(body) }.getOrNull()
            if (json == null) {
                Log.w(TAG, "direct body is not json")
                return null
            }
            if (json.optInt("code", -1) != 0) {
                Log.w(TAG, "direct code=${json.optInt("code", -1)} msg=${json.optString("msg")}")
                return null
            }
            val play = json.optString("url").trim()
            return play.takeIf { it.startsWith("http://") || it.startsWith("https://") }
        }
    }

    private fun ask(source: String, track: TrackRow, quality: String): String? {
        val ctx = context ?: return null
        val global = ctx.getGlobalObject()
        global.setProperty("__lxInfo", musicInfo(source, track).toString())
        global.setProperty("__lxSource", source)
        global.setProperty("__lxQuality", quality)
        ctx.evaluate(START_REQUEST, "custom-source-call.js")
        val deadline = System.currentTimeMillis() + 20_000
        var done: Any? = "0"
        while (System.currentTimeMillis() < deadline) {
            drainHttp(ctx)
            ctx.evaluate("void 0", "custom-source-jobs.js")
            ctx.evaluate(PUMP, "custom-source-pump.js")
            done = ctx.evaluate("globalThis.__lxResult.done ? '1' : '0'", "custom-source-done.js")
            if (done == "1") break
            if (httpQueue.isEmpty()) Thread.sleep(40)
        }
        val value = (ctx.evaluate("String(globalThis.__lxResult.value || '')", "custom-source-value.js") as? String)
            ?.trim()
            ?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
        if (value == null) {
            val error = ctx.evaluate("String(globalThis.__lxResult.error || '')", "custom-source-error.js") as? String
            Log.w(TAG, "script no url source=$source quality=$quality error=${error.orEmpty()} done=$done")
        }
        return value
    }

    private fun ensureScript(url: String, text: String) {
        if (context != null && loadedUrl == url) return
        context?.destroy()
        context = null
        httpQueue.clear()
        loadedUrl = url
        loadedText = text
        val ctx = QuickJSContext.create()
        context = ctx
        val global = ctx.getGlobalObject()
        global.setProperty("__lxScriptMeta", scriptMeta(text))
        global.setProperty("__lxMd5", JSCallFunction { args ->
            val raw = args.getOrNull(0)?.toString().orEmpty()
            val digest = MessageDigest.getInstance("MD5").digest(raw.toByteArray(Charsets.UTF_8))
            digest.joinToString("") { "%02x".format(it) }
        })
        global.setProperty("__lxEnqueue", JSCallFunction { args ->
            val id = (args.getOrNull(0) as? Number)?.toInt() ?: return@JSCallFunction null
            val target = args.getOrNull(1)?.toString().orEmpty()
            val options = args.getOrNull(2)?.toString().orEmpty()
            httpQueue.add(PendingHttp(id, target, options))
            null
        })
        ctx.evaluate(SHIM, "custom-source-shim.js")
        ctx.evaluate(text, "custom-source.js")
        ctx.evaluate(PUMP, "custom-source-pump.js")
        loadedUrl = url
    }

    private fun drainHttp(ctx: QuickJSContext) {
        while (true) {
            val pending = httpQueue.poll() ?: return
            val outcome = runCatching { perform(pending) }.getOrElse {
                Log.e(TAG, "script http failed ${describeUrl(pending.url)}", it)
                HttpOutcome(0, it.message ?: "request failed")
            }
            val global = ctx.getGlobalObject()
            global.setProperty("__lxRawBody", outcome.body)
            global.setProperty("__lxStatus", outcome.status)
            global.setProperty("__lxHttpId", pending.id)
            ctx.evaluate(FINISH_HTTP, "custom-source-http.js")
        }
    }

    private fun perform(pending: PendingHttp): HttpOutcome {
        if (!pending.url.startsWith("http://") && !pending.url.startsWith("https://")) {
            return HttpOutcome(0, "unsupported url")
        }
        val options = runCatching { JSONObject(pending.options) }.getOrDefault(JSONObject())
        val method = options.optString("method", "get").uppercase()
        val timeout = options.optLong("timeout", 13_000).coerceIn(1_000, 60_000)
        val call = fetch.newBuilder()
            .callTimeout(timeout, TimeUnit.MILLISECONDS)
            .build()
        val builder = Request.Builder().url(pending.url).header("User-Agent", WEB_UA)
        val headers = options.optJSONObject("headers")
        headers?.keys()?.forEach { key ->
            val value = headers.optString(key)
            if (key.isNotBlank() && value.isNotBlank()) builder.header(key, value)
        }
        if (method != "GET" && method != "HEAD") {
            val bodyText = when {
                options.has("form") -> options.opt("form")?.toString().orEmpty()
                options.has("body") -> options.opt("body")?.let { raw ->
                    if (raw is JSONObject || raw is org.json.JSONArray) raw.toString() else raw.toString()
                }.orEmpty()
                else -> ""
            }
            val type = headers?.optString("Content-Type").orEmpty().ifBlank {
                if (options.has("form")) "application/x-www-form-urlencoded" else "application/json"
            }
            builder.method(method, bodyText.toRequestBody(type.toMediaType()))
        }
        call.newCall(builder.build()).execute().use { response ->
            return HttpOutcome(response.code, response.body?.string().orEmpty().take(1_000_000))
        }
    }

    private fun download(url: String): String? {
        return runCatching {
            val request = Request.Builder().url(url).header("User-Agent", WEB_UA).get().build()
            fetch.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) null else body
            }
        }.onFailure { Log.e(TAG, "download failed ${describeUrl(url)}", it) }
            .getOrNull()
    }

    private fun describeUrl(raw: String?): String {
        if (raw.isNullOrBlank()) return "empty"
        return runCatching {
            val uri = java.net.URI(raw)
            "${uri.scheme}://${uri.host}${uri.path} len=${raw.length}"
        }.getOrElse { "len=${raw.length}" }
    }

    private data class PendingHttp(val id: Int, val url: String, val options: String)
    private data class HttpOutcome(val status: Int, val body: String)

    companion object {
        private const val TAG = "ZMusicSource"
        private const val WEB_UA =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120.0.0.0 Mobile Safari/537.36"

        private const val SHIM = """
            globalThis.__lxQueue = [];
            globalThis.__lxWaiters = {};
            globalThis.__lxResult = {done:false, value:'', error:''};
            globalThis.__timers = [];
            globalThis.setTimeout = function(fn, ms) {
              __timers.push({fn: fn, at: Date.now() + (Number(ms) || 0)});
              return __timers.length;
            };
            globalThis.clearTimeout = function() {};
            globalThis.console = {
              log: function() {},
              info: function() {},
              warn: function() {},
              error: function() {},
              group: function() {},
              groupEnd: function() {}
            };
            function __lxBytes(text) {
              var bytes = [];
              for (var i = 0; i < text.length; i++) {
                var code = text.charCodeAt(i);
                if (code < 128) bytes.push(code);
                else if (code < 2048) {
                  bytes.push((code >> 6) | 192);
                  bytes.push((code & 63) | 128);
                } else {
                  bytes.push((code >> 12) | 224);
                  bytes.push(((code >> 6) & 63) | 128);
                  bytes.push((code & 63) | 128);
                }
              }
              return new Uint8Array(bytes);
            }
            var meta = {};
            try { meta = JSON.parse(globalThis.__lxScriptMeta || '{}'); } catch (e) {}
            var lx = {
              version: '2.0.0',
              env: 'mobile',
              EVENT_NAMES: {inited: 'inited', request: 'request', updateAlert: 'updateAlert'},
              currentScriptInfo: {
                name: meta.name || '',
                description: meta.description || '',
                version: meta.version || '',
                author: meta.author || '',
                homepage: meta.homepage || '',
                rawScript: ''
              },
              utils: {
                crypto: {
                  md5: function(str) {
                    if (typeof str !== 'string') throw new Error('param required a string');
                    return __lxMd5(encodeURIComponent(str));
                  },
                  randomBytes: function(size) { return new Uint8Array(size); }
                },
                buffer: {
                  from: function(input, encoding) {
                    if (typeof input === 'string') {
                      if (encoding === 'hex') {
                        var pairs = input.match(/.{1,2}/g) || [];
                        var out = new Uint8Array(pairs.length);
                        for (var i = 0; i < pairs.length; i++) out[i] = parseInt(pairs[i], 16);
                        return out;
                      }
                      return __lxBytes(input);
                    }
                    return new Uint8Array(input);
                  },
                  bufToString: function(buf, format) {
                    var bytes = buf;
                    if (format === 'hex') {
                      var hex = '';
                      for (var i = 0; i < bytes.length; i++) hex += ('0' + bytes[i].toString(16)).slice(-2);
                      return hex;
                    }
                    var text = '';
                    for (var j = 0; j < bytes.length; j++) text += String.fromCharCode(bytes[j]);
                    return text;
                  }
                }
              },
              send: function() { return Promise.resolve(); },
              on: function(name, fn) {
                if (name === 'request') globalThis.__lxOnRequest = fn;
                return Promise.resolve();
              },
              request: function(url, options, callback) {
                var id = (globalThis.__lxSeq = (globalThis.__lxSeq || 1) + 1);
                __lxWaiters[id] = callback;
                __lxEnqueue(id, String(url), JSON.stringify(options || {}));
                return function() { delete __lxWaiters[id]; };
              }
            };
            globalThis.lx = lx;
        """

        private const val START_REQUEST = """
            globalThis.__lxResult = {done:false, value:'', error:''};
            if (typeof globalThis.__lxOnRequest !== 'function') {
              __lxResult.done = true;
              __lxResult.error = 'request handler missing';
            } else {
              try {
                var ret = globalThis.__lxOnRequest({
                  source: __lxSource,
                  action: 'musicUrl',
                  info: {type: __lxQuality, musicInfo: JSON.parse(__lxInfo)}
                });
                if (ret && typeof ret.then === 'function') {
                  ret.then(function(value) {
                    __lxResult.done = true;
                    __lxResult.value = value == null ? '' : String(value);
                  }, function(error) {
                    __lxResult.done = true;
                    __lxResult.error = error && error.message ? String(error.message) : String(error);
                  });
                } else {
                  __lxResult.done = true;
                  __lxResult.value = ret == null ? '' : String(ret);
                }
              } catch (error) {
                __lxResult.done = true;
                __lxResult.error = error && error.message ? String(error.message) : String(error);
              }
            }
        """

        private const val FINISH_HTTP = """
            (function() {
              var callback = __lxWaiters[__lxHttpId];
              delete __lxWaiters[__lxHttpId];
              if (!callback) return;
              var body = __lxRawBody;
              try { body = JSON.parse(__lxRawBody); } catch (e) {}
              var resp = {statusCode: __lxStatus, statusMessage: '', headers: {}, body: body};
              callback(__lxStatus >= 200 && __lxStatus < 400 ? null : new Error('http ' + __lxStatus), resp, body);
            })();
        """

        private const val PUMP = """
            (function() {
              var now = Date.now();
              var left = [];
              var timers = globalThis.__timers || [];
              for (var i = 0; i < timers.length; i++) {
                var timer = timers[i];
                if (timer.at <= now) {
                  try { timer.fn(); } catch (e) {}
                } else {
                  left.push(timer);
                }
              }
              globalThis.__timers = left;
            })();
        """

        private fun scriptMeta(text: String): String {
            fun tag(name: String): String =
                Regex("@$name\\s+([^\\n\\r*]*)").find(text)?.groupValues?.getOrNull(1)?.trim().orEmpty()
            return JSONObject()
                .put("name", tag("name"))
                .put("description", tag("description"))
                .put("version", tag("version"))
                .put("author", tag("author"))
                .put("homepage", tag("homepage"))
                .toString()
        }

        fun sourceCode(platform: MusicPlatform): String = when (platform) {
            MusicPlatform.KUWO -> "kw"
            MusicPlatform.KUGOU -> "kg"
            MusicPlatform.QQ -> "tx"
            MusicPlatform.NETEASE -> "wy"
            MusicPlatform.QISHUI -> ""
        }

        fun qualityLabel(quality: AudioQuality): String = when (quality) {
            AudioQuality.STANDARD, AudioQuality.HIGHER -> "128k"
            AudioQuality.EXHIGH -> "320k"
            else -> "flac"
        }

        fun musicInfo(source: String, track: TrackRow): JSONObject {
            val seconds = (track.durationMs / 1000).toInt().coerceAtLeast(0)
            val songmid = track.sourceId?.ifBlank { null } ?: track.id.toString()
            return JSONObject()
                .put("name", track.name)
                .put("singer", track.artists)
                .put("source", source)
                .put("songmid", songmid)
                .put("interval", "%02d:%02d".format(seconds / 60, seconds % 60))
                .put("albumName", track.album.orEmpty())
                .put("img", track.coverUrl.orEmpty())
                .put("typeUrl", JSONObject())
                .put("albumId", "")
                .put("types", org.json.JSONArray())
                .put("_types", JSONObject())
                .apply {
                    if (source == "kg") put("hash", track.sourceHash.orEmpty())
                    if (source == "tx") {
                        put("strMediaMid", track.sourceHash?.takeIf { it.isNotBlank() } ?: songmid)
                        put("albumMid", track.albumMid.orEmpty())
                        put("songId", track.sourceSongId)
                    }
                }
        }
    }
}
