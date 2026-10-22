package com.kite.zmusic.i18n

import java.util.Locale

enum class AppLanguage(
    val tag: String,
    val locale: Locale,
    val nativeName: String,
    private val subtitleKey: String,
    val asset: String?,
) {
    Chinese(
        tag = "zh",
        locale = Locale.SIMPLIFIED_CHINESE,
        nativeName = "简体中文",
        subtitleKey = "界面显示为简体中文",
        asset = null,
    ),
    Traditional(
        tag = "zh-Hant",
        locale = Locale.forLanguageTag("zh-Hant"),
        nativeName = "繁體中文",
        subtitleKey = "界面显示为繁体中文",
        asset = "i18n/zh-Hant.json",
    ),
    English(
        tag = "en",
        locale = Locale.ENGLISH,
        nativeName = "English",
        subtitleKey = "界面显示为英文",
        asset = "i18n/en.json",
    ),
    Japanese(
        tag = "ja",
        locale = Locale.JAPANESE,
        nativeName = "日本語",
        subtitleKey = "界面显示为日语",
        asset = "i18n/ja.json",
    ),
    German(
        tag = "de",
        locale = Locale.GERMAN,
        nativeName = "Deutsch",
        subtitleKey = "界面显示为德语",
        asset = "i18n/de.json",
    ),
    French(
        tag = "fr",
        locale = Locale.FRENCH,
        nativeName = "Français",
        subtitleKey = "界面显示为法语",
        asset = "i18n/fr.json",
    ),
    Spanish(
        tag = "es",
        locale = Locale.forLanguageTag("es"),
        nativeName = "Español",
        subtitleKey = "界面显示为西班牙语",
        asset = "i18n/es.json",
    ),
    Russian(
        tag = "ru",
        locale = Locale.forLanguageTag("ru"),
        nativeName = "Русский",
        subtitleKey = "界面显示为俄语",
        asset = "i18n/ru.json",
    ),
    Korean(
        tag = "ko",
        locale = Locale.KOREAN,
        nativeName = "한국어",
        subtitleKey = "界面显示为韩语",
        asset = "i18n/ko.json",
    ),
    Portuguese(
        tag = "pt",
        locale = Locale.forLanguageTag("pt"),
        nativeName = "Português",
        subtitleKey = "界面显示为葡萄牙语",
        asset = "i18n/pt.json",
    ),
    Italian(
        tag = "it",
        locale = Locale.ITALIAN,
        nativeName = "Italiano",
        subtitleKey = "界面显示为意大利语",
        asset = "i18n/it.json",
    ),
    Dutch(
        tag = "nl",
        locale = Locale.forLanguageTag("nl"),
        nativeName = "Nederlands",
        subtitleKey = "界面显示为荷兰语",
        asset = "i18n/nl.json",
    ),
    Polish(
        tag = "pl",
        locale = Locale.forLanguageTag("pl"),
        nativeName = "Polski",
        subtitleKey = "界面显示为波兰语",
        asset = "i18n/pl.json",
    ),
    Czech(
        tag = "cs",
        locale = Locale.forLanguageTag("cs"),
        nativeName = "Čeština",
        subtitleKey = "界面显示为捷克语",
        asset = "i18n/cs.json",
    ),
    Turkish(
        tag = "tr",
        locale = Locale.forLanguageTag("tr"),
        nativeName = "Türkçe",
        subtitleKey = "界面显示为土耳其语",
        asset = "i18n/tr.json",
    ),
    ;

    /** 语言名称保持各语言自己的写法，说明句跟随当前界面语言。 */
    val subtitle: String
        get() = t(subtitleKey)

    companion object {
        val Default: AppLanguage = Chinese

        fun fromStored(raw: String?): AppLanguage =
            entries.find { it.name.equals(raw?.trim(), ignoreCase = true) } ?: Default

        fun fromTag(tag: String?): AppLanguage {
            val norm = tag?.trim().orEmpty().replace('_', '-').lowercase(Locale.US)
            if (norm.isEmpty()) return Default
            entries.find { it.tag.equals(norm, ignoreCase = true) }?.let { return it }
            if (norm.startsWith("zh")) {
                val traditional = norm.contains("hant") ||
                    norm.endsWith("-tw") ||
                    norm.endsWith("-hk") ||
                    norm.endsWith("-mo")
                return if (traditional) Traditional else Chinese
            }
            val primary = norm.substringBefore('-')
            return entries.find { it.tag.equals(primary, ignoreCase = true) } ?: Default
        }

        /** 按国家或地区代码选界面语言。没有对应语言时用英语。 */
        fun fromCountry(countryIso: String?): AppLanguage {
            return when (countryIso?.trim()?.uppercase(Locale.US)) {
                "CN" -> Chinese
                "TW", "HK", "MO" -> Traditional
                "JP" -> Japanese
                "DE", "AT", "LI" -> German
                "FR", "MC" -> French
                "ES", "MX", "AR", "CO", "CL", "PE", "VE", "EC", "GT", "CU", "BO", "DO",
                "HN", "PY", "SV", "NI", "CR", "PA", "UY", "PR", "GQ",
                -> Spanish
                "RU" -> Russian
                "KR" -> Korean
                "PT", "BR" -> Portuguese
                "IT", "SM" -> Italian
                "NL" -> Dutch
                "PL" -> Polish
                "CZ" -> Czech
                "TR" -> Turkish
                else -> English
            }
        }
    }
}

/** 设置里的语言项。自动不写入具体语言，下次启动再按国家决定。 */
enum class LanguageChoice(val language: AppLanguage?) {
    Auto(null),
    Chinese(AppLanguage.Chinese),
    Traditional(AppLanguage.Traditional),
    English(AppLanguage.English),
    Japanese(AppLanguage.Japanese),
    German(AppLanguage.German),
    French(AppLanguage.French),
    Spanish(AppLanguage.Spanish),
    Russian(AppLanguage.Russian),
    Korean(AppLanguage.Korean),
    Portuguese(AppLanguage.Portuguese),
    Italian(AppLanguage.Italian),
    Dutch(AppLanguage.Dutch),
    Polish(AppLanguage.Polish),
    Czech(AppLanguage.Czech),
    Turkish(AppLanguage.Turkish),
    ;

    fun resolve(countryIso: String): AppLanguage = language ?: AppLanguage.fromCountry(countryIso)

    companion object {
        fun fromStored(raw: String?): LanguageChoice {
            val text = raw?.trim().orEmpty()
            if (text.isEmpty()) return Auto
            return entries.find { it.name.equals(text, ignoreCase = true) } ?: Auto
        }
    }
}
