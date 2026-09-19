package com.kite.zmusic.data

import com.kite.zmusic.i18n.t

data class PersonalFmModeChoice(
    val mode: String,
    val submode: String? = null,
) {
    val isDefault: Boolean
        get() = mode == MODE_DEFAULT && submode.isNullOrBlank()

    fun label(): String = personalFmModeEntries()
        .firstOrNull { it.choice == this }
        ?.title
        ?: t("默认")

    companion object {
        const val MODE_DEFAULT = "DEFAULT"
        const val MODE_FAMILIAR = "FAMILIAR"
        const val MODE_EXPLORE = "EXPLORE"
        const val MODE_SCENE = "SCENE_RCMD"
        const val MODE_PUZZLE = "PUZZLE_MODE_RCMD"
        val Default = PersonalFmModeChoice(MODE_DEFAULT)
    }
}

data class PersonalFmModeEntry(
    val choice: PersonalFmModeChoice,
    val title: String,
)

fun personalFmModeEntries(): List<PersonalFmModeEntry> = buildList {
    add(PersonalFmModeEntry(PersonalFmModeChoice.Default, t("默认")))
    add(PersonalFmModeEntry(PersonalFmModeChoice(PersonalFmModeChoice.MODE_FAMILIAR), t("熟悉")))
    add(PersonalFmModeEntry(PersonalFmModeChoice(PersonalFmModeChoice.MODE_EXPLORE), t("探索")))
    add(PersonalFmModeEntry(PersonalFmModeChoice(PersonalFmModeChoice.MODE_PUZZLE), t("拼图")))
    scene("EXERCISE", t("运动"))
    scene("FOCUS", t("专注"))
    scene("NIGHT_EMO", t("伤感"))
    scene("SLEEP_HELP", t("助眠"))
    scene("RELAX", t("放松"))
    scene("CHEERFUL", t("欢快"))
    scene("LYRICAL", t("抒情"))
    scene("CURE", t("治愈"))
    scene("SWEET", t("情歌"))
    scene("RHYTHM_BLUES", "R&B")
    scene("RAINY", t("雨天"))
    scene("GAMES", t("游戏"))
    scene("RAP", t("说唱"))
    scene("K_POP", "K-Pop")
    scene("ORIGINAL_MUSICIAL", t("宝藏原创"))
    scene("ELECTRONIC", t("电音"))
    scene("COMMUTE", t("出行"))
    scene("TAKE_SHOWER", t("洗澡"))
    scene("COFFEE_SHOP", t("咖啡馆"))
    scene("ROCK", t("摇滚"))
    scene("INSPIRATIONAL", t("励志"))
    scene("CHINESE", t("华语"))
    scene("ENGLISH", t("欧美"))
    scene("YUEYU", t("粤语"))
    scene("MANYAO", t("慢摇DJ"))
    scene("JINGDIAN", t("经典"))
    scene("LIGHT", t("轻音乐"))
    scene("GUOFENG", t("国风"))
    scene("FOLK", t("民谣"))
    scene("ACG", t("二次元"))
    scene("GUDIAN", t("古典"))
    scene("JAZZ", t("爵士"))
    scene("JAPANESE", t("日语"))
    scene("GLOBAL", t("全球"))
    scene("FRANCH", t("法语"))
    scene("BLUE", t("蓝调"))
    scene("DANCE", t("舞蹈"))
    scene("LATIN", t("拉丁"))
    scene("PUNK", t("放克"))
    scene("COUNTRY", t("乡村乐"))
    scene("MUSICAL", t("音乐剧"))
    scene("YINGSHI", t("影视"))
}

private fun MutableList<PersonalFmModeEntry>.scene(submode: String, title: String) {
    add(
        PersonalFmModeEntry(
            PersonalFmModeChoice(PersonalFmModeChoice.MODE_SCENE, submode),
            title,
        ),
    )
}
