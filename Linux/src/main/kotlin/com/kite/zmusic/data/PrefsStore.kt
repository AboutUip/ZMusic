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
    Light("浅色", "始终使用浅色界面"),
    Dark("深色", "始终使用深色界面"),
    System("跟随系统", "与系统外观保持一致"),
    ;

    fun resolveDark(systemDark: Boolean): Boolean = when (this) {
        Light -> false
        Dark -> true
        System -> systemDark
    }

    companion object {
        fun fromStored(raw: String?): AppAppearance =
            entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: Light
    }
}

enum class ChromeGlassMode {
    Liquid,
    Frosted,
    Solid,
    ;

    val title: String
        get() = when (this) {
            Liquid -> "液态"
            Frosted -> "磨砂"
            Solid -> "纯色"
        }

    val caption: String
        get() = when (this) {
            Liquid -> "折射背后的画面"
            Frosted -> "只做模糊，不折射"
            Solid -> "不透明底，不再透出背景"
        }

    companion object {
        fun fromStored(raw: String?): ChromeGlassMode =
            entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: Liquid
    }
}

data class GlassStyle(
    val mode: ChromeGlassMode = ChromeGlassMode.Liquid,
    val refraction: Float = 1f,
    val blur: Float = 0.4f,
) {
    val settingsSubtitle: String
        get() = when (mode) {
            ChromeGlassMode.Liquid ->
                "液态 · 折射率 ${formatRefraction(refraction)} · 模糊 ${formatBlurPercent(blur)}"
            ChromeGlassMode.Frosted -> "磨砂 · 模糊 ${formatBlurPercent(blur)}"
            ChromeGlassMode.Solid -> "纯色，不透明"
        }

    companion object {
        fun formatRefraction(value: Float): String =
            String.format(java.util.Locale.US, "%.2f", value.coerceIn(0f, 2f))

        fun formatBlurPercent(value: Float): String =
            "${(value.coerceIn(0f, 1f) * 100f).toInt()}%"
    }
}

enum class RealtimeCacheMode(val title: String, val caption: String) {
    Cautious("谨慎", "听满六成后再写入"),
    Realtime("实时", "边听边下，听不满就删"),
    Aggressive("激进", "边听边下，占满按旧文件淘汰"),
    ;

    companion object {
        fun fromStored(raw: String?): RealtimeCacheMode =
            entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: Cautious
    }
}

data class RealtimeCachePrefs(
    val enabled: Boolean = false,
    val mode: RealtimeCacheMode = RealtimeCacheMode.Cautious,
    val maxMb: Int = 512,
) {
    val liveDownload: Boolean
        get() = enabled && (mode == RealtimeCacheMode.Realtime || mode == RealtimeCacheMode.Aggressive)

    val settingsSubtitle: String
        get() = if (enabled) "已开启 · ${mode.title}模式" else "已关闭 · 不采样不走本地缓存"
}

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
    val playerDisplay: PlayerDisplayPrefs = PlayerDisplayPrefs(),
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
                    mode = RealtimeCacheMode.fromStored(o.optString("realtimeCacheMode")),
                    maxMb = o.optInt("realtimeCacheMb", 512).coerceIn(64, 4096),
                ),
                appearance = AppAppearance.fromStored(o.optString("appearance")),
                glass = GlassStyle(
                    mode = ChromeGlassMode.fromStored(o.optString("glassMode")),
                    refraction = o.optDouble("glassRefraction", 1.0).toFloat(),
                    blur = o.optDouble("glassBlur", 0.4).toFloat(),
                ),
                wallpaperPath = o.optString("wallpaperPath", ""),
                playerDisplay = playerDisplayPrefsFromJson(
                    o.optJSONObject("playerDisplay"),
                    haloFallback = o.optBoolean("playerHalo", false),
                ),
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
            .put("realtimeCacheMode", p.realtimeCache.mode.name)
            .put("realtimeCacheMb", p.realtimeCache.maxMb)
            .put("appearance", p.appearance.name)
            .put("glassMode", p.glass.mode.name)
            .put("glassRefraction", p.glass.refraction.toDouble())
            .put("glassBlur", p.glass.blur.toDouble())
            .put("wallpaperPath", p.wallpaperPath)
            .put("playerDisplay", p.playerDisplay.toJson())
        file().writeText(o.toString())
    }
}
