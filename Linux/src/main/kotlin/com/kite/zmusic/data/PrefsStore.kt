package com.kite.zmusic.data

import com.kite.zmusic.ZMusicPaths
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

enum class AppAppearance(val title: String, val subtitle: String) {
    Light("浅色", "浅色界面"),
    Dark("深色", "深色界面"),
}

data class GlassStyle(
    val refraction: Float = 0.42f,
    val blur: Float = 0.55f,
) {
    val settingsSubtitle: String
        get() = "折射率 ${(refraction * 100).toInt()} · 模糊 ${(blur * 100).toInt()}"
}

data class RealtimeCachePrefs(
    val enabled: Boolean = false,
    val maxMb: Int = 512,
)

data class AppPrefs(
    val musicServer: String = "",
    val communityServer: String = "114.215.189.208:80",
    val audioQuality: AudioQuality = AudioQuality.Default,
    val persistentPlayback: Boolean = false,
    val lyricWordByWord: Boolean = true,
    val downloadAccel: Boolean = false,
    val realtimeCache: RealtimeCachePrefs = RealtimeCachePrefs(),
    val appearance: AppAppearance = AppAppearance.Light,
    val glass: GlassStyle = GlassStyle(),
    val wallpaperPath: String = "",
)

class PrefsStore {
    private val _prefs = MutableStateFlow(read())
    val prefs: StateFlow<AppPrefs> = _prefs.asStateFlow()

    fun current(): AppPrefs = _prefs.value

    fun update(transform: (AppPrefs) -> AppPrefs) {
        val next = transform(_prefs.value)
        write(next)
        _prefs.value = next
    }

    private fun file() = ZMusicPaths.configDir().resolve("prefs.json")

    private fun read(): AppPrefs {
        val f = file()
        if (!f.exists()) return AppPrefs()
        return runCatching {
            val o = JSONObject(f.readText())
            AppPrefs(
                musicServer = o.optString("musicServer", ""),
                communityServer = o.optString("communityServer", "114.215.189.208:80"),
                audioQuality = AudioQuality.fromLevel(o.optString("audioQuality")),
                persistentPlayback = o.optBoolean("persistentPlayback", false),
                lyricWordByWord = o.optBoolean("lyricWordByWord", true),
                downloadAccel = o.optBoolean("downloadAccel", false),
                realtimeCache = RealtimeCachePrefs(
                    enabled = o.optBoolean("realtimeCache", false),
                    maxMb = o.optInt("realtimeCacheMb", 512),
                ),
                appearance = if (o.optString("appearance") == "Dark") {
                    AppAppearance.Dark
                } else {
                    AppAppearance.Light
                },
                glass = GlassStyle(
                    refraction = o.optDouble("glassRefraction", 0.42).toFloat(),
                    blur = o.optDouble("glassBlur", 0.55).toFloat(),
                ),
                wallpaperPath = o.optString("wallpaperPath", ""),
            )
        }.getOrDefault(AppPrefs())
    }

    private fun write(p: AppPrefs) {
        val o = JSONObject()
            .put("musicServer", p.musicServer)
            .put("communityServer", p.communityServer)
            .put("audioQuality", p.audioQuality.level)
            .put("persistentPlayback", p.persistentPlayback)
            .put("lyricWordByWord", p.lyricWordByWord)
            .put("downloadAccel", p.downloadAccel)
            .put("realtimeCache", p.realtimeCache.enabled)
            .put("realtimeCacheMb", p.realtimeCache.maxMb)
            .put("appearance", p.appearance.name)
            .put("glassRefraction", p.glass.refraction.toDouble())
            .put("glassBlur", p.glass.blur.toDouble())
            .put("wallpaperPath", p.wallpaperPath)
        file().writeText(o.toString())
    }
}
