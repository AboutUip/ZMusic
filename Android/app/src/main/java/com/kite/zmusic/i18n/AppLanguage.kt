package com.kite.zmusic.i18n

import java.util.Locale

enum class AppLanguage {
    Chinese,
    English,
    Japanese,
    ;

    val tag: String
        get() = when (this) {
            Chinese -> "zh"
            English -> "en"
            Japanese -> "ja"
        }

    val locale: Locale
        get() = when (this) {
            Chinese -> Locale.SIMPLIFIED_CHINESE
            English -> Locale.ENGLISH
            Japanese -> Locale.JAPANESE
        }

    /** 语言名称保持各语言自己的写法，不随界面语言翻译。 */
    val nativeName: String
        get() = when (this) {
            Chinese -> "简体中文"
            English -> "English"
            Japanese -> "日本語"
        }

    val subtitle: String
        get() = when (this) {
            Chinese -> t("界面显示为简体中文")
            English -> t("界面显示为英文")
            Japanese -> t("界面显示为日语")
        }

    companion object {
        val Default: AppLanguage = Chinese

        fun fromStored(raw: String?): AppLanguage =
            entries.find { it.name.equals(raw?.trim(), ignoreCase = true) } ?: Default

        fun fromTag(tag: String?): AppLanguage {
            val t = tag?.trim().orEmpty().lowercase()
            return when {
                t.startsWith("zh") -> Chinese
                t.startsWith("en") -> English
                t.startsWith("ja") -> Japanese
                else -> Default
            }
        }

        /** 按国家或地区代码选界面语言。没有对应语言时用英语。 */
        fun fromCountry(countryIso: String?): AppLanguage {
            return when (countryIso?.trim()?.uppercase(Locale.US)) {
                "CN", "TW", "HK", "MO" -> Chinese
                "JP" -> Japanese
                else -> English
            }
        }
    }
}

/** 设置里的语言项。自动不写入具体语言，下次启动再按国家决定。 */
enum class LanguageChoice {
    Auto,
    Chinese,
    English,
    Japanese,
    ;

    fun resolve(countryIso: String): AppLanguage = when (this) {
        Auto -> AppLanguage.fromCountry(countryIso)
        Chinese -> AppLanguage.Chinese
        English -> AppLanguage.English
        Japanese -> AppLanguage.Japanese
    }

    companion object {
        fun fromStored(raw: String?): LanguageChoice {
            val text = raw?.trim().orEmpty()
            if (text.isEmpty()) return Auto
            return entries.find { it.name.equals(text, ignoreCase = true) } ?: Auto
        }
    }
}
