package com.kite.zmusic.plugin

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import java.io.ByteArrayInputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * 打开链接、系统选文件，以及每个插件一个隔离的 WebView。
 * 页面不能导航，也不能读沙盒以外的文件。宿主只把文档变更推进去。
 */
class BetterNcmAndroidBridge(
    private val appContext: android.content.Context,
) {
    private val views = ConcurrentHashMap<String, WebView>()
    private val fileResult = AtomicReference<((String) -> Unit)?>(null)

    @Volatile private var activity: ComponentActivity? = null
    @Volatile private var fileLauncher: ActivityResultLauncher<Array<String>>? = null

    fun attach(activity: ComponentActivity, fileLauncher: ActivityResultLauncher<Array<String>>) {
        this.activity = activity
        this.fileLauncher = fileLauncher
    }

    fun detach() {
        val host = activity
        activity = null
        fileLauncher = null
        views.keys.toList().forEach { release(it) }
        host?.let { dropViews(it) }
    }

    fun onFilePicked(uri: Uri?) {
        val callback = fileResult.getAndSet(null) ?: return
        if (uri != null) {
            runCatching {
                activity?.contentResolver?.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
        }
        callback(uri?.toString().orEmpty())
    }

    fun openUrl(url: String): Boolean {
        val safe = BetterNcmLinks.allow(url) ?: return false
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(safe))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            appContext.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun openFile(
        filter: String,
        @Suppress("UNUSED_PARAMETER") initialDir: String,
    ): String {
        val launcher = fileLauncher ?: return ""
        val host = activity ?: return ""
        if (host.isDestroyed) return ""
        val done = CountDownLatch(1)
        val picked = AtomicReference("")
        val previous = fileResult.getAndSet { value ->
            picked.set(value)
            done.countDown()
        }
        if (previous != null) {
            previous("")
        }
        host.runOnUiThread {
            runCatching {
                launcher.launch(BetterNcmLinks.mimeTypes(filter).toTypedArray())
            }.onFailure {
                fileResult.getAndSet(null)?.invoke("")
            }
        }
        done.await(FILE_WAIT_SECONDS, TimeUnit.SECONDS)
        return picked.get().orEmpty()
    }

    fun mirror(pluginId: String, script: String) {
        if (script.isEmpty()) return
        val host = activity ?: return
        host.runOnUiThread {
            val view = views.getOrPut(pluginId) { createView(host) }
            view.evaluateJavascript(script, null)
        }
    }

    fun release(pluginId: String) {
        val view = views.remove(pluginId) ?: return
        val host = activity ?: return
        host.runOnUiThread {
            (view.parent as? ViewGroup)?.removeView(view)
            view.destroy()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createView(host: Activity): WebView {
        val view = WebView(host)
        val settings = view.settings
        settings.javaScriptEnabled = true
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.domStorageEnabled = false
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        settings.setSupportMultipleWindows(false)
        settings.javaScriptCanOpenWindowsAutomatically = false
        @Suppress("DEPRECATION")
        run {
            settings.allowFileAccessFromFileURLs = false
            settings.allowUniversalAccessFromFileURLs = false
        }
        view.isClickable = false
        view.isFocusable = false
        view.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        view.translationX = -10000f
        view.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = true

            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                val url = request.url?.toString().orEmpty()
                if (url == "about:blank" || url.startsWith("data:text/html")) return null
                return WebResourceResponse(
                    "text/plain",
                    "utf-8",
                    403,
                    "blocked",
                    emptyMap(),
                    ByteArrayInputStream(ByteArray(0)),
                )
            }
        }
        view.loadDataWithBaseURL(
            "about:blank",
            PAGE,
            "text/html",
            "utf-8",
            null,
        )
        val decor = host.window?.decorView as? ViewGroup
        decor?.addView(
            view,
            ViewGroup.LayoutParams(1, 1),
        )
        return view
    }

    private fun dropViews(host: Activity) {
        host.runOnUiThread {
            views.values.forEach { view ->
                (view.parent as? ViewGroup)?.removeView(view)
                view.destroy()
            }
            views.clear()
        }
    }

    companion object {
        private const val FILE_WAIT_SECONDS = 120L

        private val PAGE = """
            <!DOCTYPE html><html><head><meta charset="utf-8"></head><body>
            <script>
            window.__bncmNodes = {};
            window.__bncmUpsert = function(id, tag, classes, style, text, attrs, children) {
              var el = document.createElement(tag);
              el.setAttribute("data-bncm-id", String(id));
              for (var i = 0; i < classes.length; i++) el.classList.add(classes[i]);
              for (var key in style) { try { el.style[key] = style[key]; } catch (e) {} }
              for (var name in attrs) el.setAttribute(name, attrs[name]);
              el.textContent = text || "";
              window.__bncmNodes[id] = el;
              var parent = document.body;
              if (children && children.length) {
                for (var c = 0; c < children.length; c++) {
                  var child = window.__bncmNodes[children[c]];
                  if (child) el.appendChild(child);
                }
              }
              parent.appendChild(el);
            };
            window.__bncmAppend = function(parentId, childId) {
              var parent = window.__bncmNodes[parentId];
              var child = window.__bncmNodes[childId];
              if (parent && child) parent.appendChild(child);
            };
            </script>
            </body></html>
        """.trimIndent()
    }
}
