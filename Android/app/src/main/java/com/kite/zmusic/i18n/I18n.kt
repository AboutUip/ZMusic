package com.kite.zmusic.i18n

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import org.json.JSONObject
import java.util.Locale
import kotlin.math.abs
import kotlin.math.round

/**
 * 以中文原文为 key 的运行时翻译。目录缺失时回退中文。
 */
object I18n {
    @Volatile
    var language: AppLanguage = AppLanguage.Default
        private set

    @Volatile
    private var en: Map<String, String> = emptyMap()

    @Volatile
    private var ja: Map<String, String> = emptyMap()

    @Volatile
    private var reverse: Map<String, String> = emptyMap()

    fun install(context: Context, language: AppLanguage) {
        if (en.isEmpty() || ja.isEmpty()) {
            val app = context.applicationContext
            en = loadAsset(app, "i18n/en.json")
            ja = loadAsset(app, "i18n/ja.json")
            reverse = buildReverse(en) + buildReverse(ja)
        }
        setLanguage(language)
    }

    fun setLanguage(language: AppLanguage) {
        this.language = language
    }

    fun applyToApp(language: AppLanguage) {
        setLanguage(language)
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language.tag))
    }

    fun relaunchApp(context: Context) {
        val app = context.applicationContext
        val launch = app.packageManager.getLaunchIntentForPackage(app.packageName) ?: return
        val component = launch.component ?: return
        app.startActivity(Intent.makeRestartActivityTask(component))
        Runtime.getRuntime().exit(0)
    }

    fun wrapContext(base: Context, language: AppLanguage = this.language): Context {
        val config = Configuration(base.resources.configuration)
        config.setLocale(language.locale)
        return base.createConfigurationContext(config)
    }

    fun translate(zh: String, vararg args: Any?): String {
        val template = templateOf(zh)
        if (args.isEmpty()) return template
        return try {
            String.format(Locale.US, template, *args.map { it ?: "" }.toTypedArray())
        } catch (_: Exception) {
            template
        }
    }

    /**
     * 把已展示文案还原成中文原文，供 `startsWith("已")` 这类内部判断使用。
     */
    fun sourceOf(displayed: String): String {
        if (language == AppLanguage.Chinese) return displayed
        return reverse[displayed] ?: displayed
    }

    fun formatCompactCount(n: Long): String {
        val abs = abs(n)
        return when (language) {
            AppLanguage.English -> when {
                abs >= 1_000_000_000L -> compact(n / 1_000_000_000.0, "B")
                abs >= 1_000_000L -> compact(n / 1_000_000.0, "M")
                abs >= 1_000L -> compact(n / 1_000.0, "k")
                else -> n.toString()
            }
            AppLanguage.Japanese -> wanYi(n, "万", "億")
            AppLanguage.Chinese -> wanYi(n, "万", "亿")
        }
    }

    private fun templateOf(zh: String): String = when (language) {
        AppLanguage.Chinese -> zh
        AppLanguage.English -> en[zh] ?: zh
        AppLanguage.Japanese -> ja[zh] ?: zh
    }

    private fun loadAsset(context: Context, path: String): Map<String, String> {
        return runCatching {
            context.assets.open(path).bufferedReader(Charsets.UTF_8).use { it.readText() }
                .let(::parseMap)
        }.getOrDefault(emptyMap())
    }

    private fun parseMap(raw: String): Map<String, String> {
        val obj = JSONObject(raw)
        val out = HashMap<String, String>(obj.length())
        val keys = obj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            out[key] = obj.getString(key)
        }
        return out
    }

    private fun buildReverse(map: Map<String, String>): Map<String, String> {
        val out = HashMap<String, String>(map.size)
        for ((zh, translated) in map) {
            if (translated.isNotEmpty() && translated != zh) {
                out.putIfAbsent(translated, zh)
            }
        }
        return out
    }

    private fun wanYi(n: Long, wan: String, yi: String): String {
        val abs = abs(n)
        return when {
            abs >= 100_000_000L -> {
                val v = n / 100_000_000.0
                if (abs(v) >= 10) "${v.toInt()}$yi" else compact(v, yi)
            }
            abs >= 10_000L -> {
                val v = n / 10_000.0
                if (abs(v) >= 10) "${v.toInt()}$wan" else compact(v, wan)
            }
            else -> n.toString()
        }
    }

    private fun compact(value: Double, suffix: String): String {
        val scaled = round(value * 10.0) / 10.0
        val text = if (scaled == scaled.toLong().toDouble()) {
            scaled.toLong().toString()
        } else {
            String.format(Locale.US, "%.1f", scaled)
        }
        return text + suffix
    }
}

fun t(zh: String, vararg args: Any?): String = I18n.translate(zh, *args)
