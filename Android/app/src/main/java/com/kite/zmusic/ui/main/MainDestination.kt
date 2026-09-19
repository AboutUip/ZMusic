package com.kite.zmusic.ui.main

import com.kite.zmusic.i18n.t

/**
 * 主界面三个模块：主页 / 功能 / 个人。
 */
enum class MainDestination {
    Home,
    Features,
    Profile,
    ;

    val titleZh: String
        get() = when (this) {
            Home -> t("主页")
            Features -> t("功能")
            Profile -> t("个人")
        }
}
