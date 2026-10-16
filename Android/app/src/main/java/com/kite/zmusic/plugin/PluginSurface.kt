package com.kite.zmusic.plugin

import com.whl.quickjs.wrapper.JSCallFunction
import com.whl.quickjs.wrapper.JSFunction
import com.whl.quickjs.wrapper.JSObject
import com.whl.quickjs.wrapper.QuickJSContext

internal interface PluginSurfaceHost {
    fun allowed(): Boolean
    fun player(): PluginPlayerController
    fun local(): PluginLocalHost
    fun catalog(op: String, args: Any?, fn: JSFunction): Boolean
    fun toast(message: String)
    fun toJs(ctx: QuickJSContext, value: Any?): Any?
}

internal object PluginSurfaceSync {
    val ALL: List<String> = listOf(
        "player.volume",
        "player.setVolume",
        "player.rate",
        "player.setRate",
        "player.mode",
        "player.setMode",
        "player.cycleMode",
        "player.playAt",
        "player.clear",
        "player.open",
        "player.sleep",
        "player.sleepStart",
        "player.sleepCancel",
        "lyric.get",
        "lyric.line",
        "app.locale",
        "app.dark",
        "app.online",
        "app.foreground",
        "app.platform",
        "app.openUrl",
        "app.toast",
        "app.haptic",
        "app.screen",
        "app.keepAwake",
        "app.network",
        "app.battery",
        "app.orientation",
        "nav.player",
        "nav.search",
        "nav.settings",
        "nav.charts",
        "nav.fm",
        "nav.daily",
        "nav.cloud",
        "nav.workshop",
        "nav.playlist",
        "nav.album",
        "nav.artist",
        "nav.mv",
        "nav.user",
        "nav.back",
        "nav.home",
        "user.get",
    )

    init {
        check(ALL.distinct().size == ALL.size)
        val added = ALL.size + PluginCatalogOps.ALL.size
        check(added >= 118) { "plugin api growth $added < 118" }
    }
}

internal object PluginSurface {
    private val FALSE = java.lang.Boolean.FALSE
    private val TRUE = java.lang.Boolean.TRUE

    fun attach(
        ctx: QuickJSContext,
        xuan: JSObject,
        playerObj: JSObject,
        host: PluginSurfaceHost,
    ) {
        fun ok(): Boolean = host.allowed()
        fun async(op: String) = JSCallFunction { args ->
            if (!ok()) return@JSCallFunction FALSE
            val fn = args.getOrNull(1) as? JSFunction ?: return@JSCallFunction FALSE
            val first = args.getOrNull(0)
            if (first is JSFunction) return@JSCallFunction FALSE
            if (host.catalog(op, first, fn)) TRUE else FALSE
        }
        fun fillAsync(obj: JSObject, prefix: String) {
            for (op in PluginCatalogOps.ALL) {
                if (!op.startsWith("$prefix.")) continue
                obj.setProperty(op.substringAfter('.'), async(op))
            }
        }

        playerObj.setProperty("volume", JSCallFunction {
            if (!ok()) null else host.player().volume().toDouble()
        })
        playerObj.setProperty("setVolume", JSCallFunction { args ->
            if (!ok()) return@JSCallFunction FALSE
            val level = finite(args.firstOrNull()) ?: return@JSCallFunction FALSE
            box(host.player().setVolume(level.toFloat()))
        })
        playerObj.setProperty("rate", JSCallFunction {
            if (!ok()) null else host.player().playbackRate().toDouble()
        })
        playerObj.setProperty("setRate", JSCallFunction { args ->
            if (!ok()) return@JSCallFunction FALSE
            val rate = finite(args.firstOrNull()) ?: return@JSCallFunction FALSE
            box(host.player().setPlaybackRate(rate.toFloat()))
        })
        playerObj.setProperty("mode", JSCallFunction {
            if (!ok()) null else host.player().mode()
        })
        playerObj.setProperty("setMode", JSCallFunction { args ->
            if (!ok()) return@JSCallFunction FALSE
            val name = args.firstOrNull() as? String ?: return@JSCallFunction FALSE
            box(host.player().setMode(name))
        })
        playerObj.setProperty("cycleMode", JSCallFunction {
            if (!ok()) FALSE else box(host.player().cycleMode())
        })
        playerObj.setProperty("playAt", JSCallFunction { args ->
            if (!ok()) return@JSCallFunction FALSE
            val index = exactInt(args.firstOrNull()) ?: return@JSCallFunction FALSE
            box(host.player().playAt(index))
        })
        playerObj.setProperty("clear", JSCallFunction {
            if (!ok()) FALSE else box(host.player().clearQueue())
        })
        playerObj.setProperty("open", JSCallFunction {
            if (!ok()) FALSE else box(host.local().openNav(PluginNavRequest("player")) || host.player().openPlayer())
        })
        playerObj.setProperty("sleep", JSCallFunction {
            if (!ok()) null else host.toJs(ctx, host.player().sleep())
        })
        playerObj.setProperty("sleepStart", JSCallFunction { args ->
            if (!ok()) return@JSCallFunction FALSE
            val minutes = exactInt(args.firstOrNull()) ?: return@JSCallFunction FALSE
            val wait = args.getOrNull(1) as? Boolean ?: false
            box(host.player().sleepStart(minutes, wait))
        })
        playerObj.setProperty("sleepCancel", JSCallFunction {
            if (!ok()) FALSE else box(host.player().sleepCancel())
        })

        val lyric = ctx.createNewJSObject()
        lyric.setProperty("get", JSCallFunction {
            if (!ok()) null else host.toJs(ctx, host.player().lyric())
        })
        lyric.setProperty("line", JSCallFunction {
            if (!ok()) null else host.toJs(ctx, host.player().lyricLine())
        })
        fillAsync(lyric, "lyric")

        val app = ctx.createNewJSObject()
        app.setProperty("locale", JSCallFunction { if (!ok()) null else host.local().locale() })
        app.setProperty("dark", JSCallFunction { if (!ok()) null else host.local().facts().dark })
        app.setProperty("online", JSCallFunction { if (!ok()) null else host.local().facts().online })
        app.setProperty("foreground", JSCallFunction { if (!ok()) null else host.local().facts().foreground })
        app.setProperty("platform", JSCallFunction { if (!ok()) null else host.local().platform() })
        app.setProperty("openUrl", JSCallFunction { args ->
            if (!ok()) return@JSCallFunction FALSE
            val url = args.firstOrNull() as? String ?: return@JSCallFunction FALSE
            box(host.local().openUrl(url))
        })
        app.setProperty("toast", JSCallFunction { args ->
            if (!ok()) return@JSCallFunction FALSE
            val text = (args.firstOrNull() as? String)?.trim().orEmpty()
            if (text.isEmpty()) return@JSCallFunction FALSE
            host.toast(text.take(120))
            TRUE
        })
        app.setProperty("haptic", JSCallFunction { args ->
            if (!ok()) return@JSCallFunction FALSE
            val ms = exactInt(args.firstOrNull()) ?: return@JSCallFunction FALSE
            box(host.local().haptic(ms))
        })
        app.setProperty("screen", JSCallFunction {
            if (!ok()) null else host.toJs(ctx, host.local().screen())
        })
        app.setProperty("keepAwake", JSCallFunction { args ->
            if (!ok()) return@JSCallFunction FALSE
            val on = args.firstOrNull() as? Boolean ?: return@JSCallFunction FALSE
            box(host.local().keepAwake(on))
        })
        app.setProperty("network", JSCallFunction { if (!ok()) null else host.local().network() })
        app.setProperty("battery", JSCallFunction {
            if (!ok()) null else host.toJs(ctx, host.local().battery())
        })
        app.setProperty("orientation", JSCallFunction { if (!ok()) null else host.local().orientation() })

        val nav = ctx.createNewJSObject()
        fun go(name: String) = JSCallFunction {
            if (!ok()) FALSE else box(host.local().openNav(PluginNavRequest(name)))
        }
        fun goId(name: String) = JSCallFunction { args ->
            if (!ok()) return@JSCallFunction FALSE
            val id = exactLong(args.firstOrNull()) ?: return@JSCallFunction FALSE
            if (id <= 0L) return@JSCallFunction FALSE
            val title = (args.getOrNull(1) as? String)?.trim().orEmpty().take(80)
            box(host.local().openNav(PluginNavRequest(name, id, title)))
        }
        nav.setProperty("player", go("player"))
        nav.setProperty("search", go("search"))
        nav.setProperty("settings", go("settings"))
        nav.setProperty("charts", go("charts"))
        nav.setProperty("fm", go("fm"))
        nav.setProperty("daily", go("daily"))
        nav.setProperty("cloud", go("cloud"))
        nav.setProperty("workshop", go("workshop"))
        nav.setProperty("back", go("back"))
        nav.setProperty("home", go("home"))
        nav.setProperty("playlist", goId("playlist"))
        nav.setProperty("album", goId("album"))
        nav.setProperty("artist", goId("artist"))
        nav.setProperty("mv", goId("mv"))
        nav.setProperty("user", goId("user"))

        val user = ctx.createNewJSObject()
        user.setProperty("get", JSCallFunction {
            if (!ok()) null else host.toJs(ctx, host.local().user())
        })
        fillAsync(user, "user")

        xuan.setProperty("lyric", lyric)
        xuan.setProperty("app", app)
        xuan.setProperty("nav", nav)
        xuan.setProperty("user", user)
        for (prefix in listOf(
            "song", "search", "album", "artist", "playlist", "comment", "mv",
            "recommend", "home", "cloud", "play", "fm",
        )) {
            val obj = ctx.createNewJSObject()
            fillAsync(obj, prefix)
            xuan.setProperty(prefix, obj)
            obj.release()
        }
        user.release()
        nav.release()
        app.release()
        lyric.release()
    }

    private fun box(ok: Boolean): Any = if (ok) TRUE else FALSE

    private fun finite(raw: Any?): Double? {
        val n = when (raw) {
            is Double -> raw
            is Float -> raw.toDouble()
            is Int -> raw.toDouble()
            is Long -> raw.toDouble()
            is Number -> raw.toDouble()
            else -> return null
        }
        return n.takeIf { it.isFinite() }
    }

    private fun exactInt(raw: Any?): Int? {
        val n = finite(raw) ?: return null
        if (n % 1.0 != 0.0 || n < Int.MIN_VALUE.toDouble() || n > Int.MAX_VALUE.toDouble()) return null
        return n.toInt()
    }

    private fun exactLong(raw: Any?): Long? {
        val n = finite(raw) ?: return null
        if (n % 1.0 != 0.0 || n < Long.MIN_VALUE.toDouble() || n > Long.MAX_VALUE.toDouble()) return null
        return n.toLong()
    }
}
