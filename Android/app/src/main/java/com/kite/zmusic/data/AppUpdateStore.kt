package com.kite.zmusic.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface AppUpdatePrefs {
    var testPlan: Boolean
    var ignoredVersion: String?
}

/**
 * 测试计划默认关闭；忽略的版本号持久化到下次更高版本。
 */
class AppUpdateStore(context: Context) : AppUpdatePrefs {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _testPlan = MutableStateFlow(prefs.getBoolean(KEY_TEST_PLAN, DEFAULT_TEST_PLAN))
    val testPlanFlow: StateFlow<Boolean> = _testPlan.asStateFlow()

    override var testPlan: Boolean
        get() = _testPlan.value
        set(value) {
            if (value == _testPlan.value) return
            prefs.edit().putBoolean(KEY_TEST_PLAN, value).apply()
            _testPlan.value = value
        }

    override var ignoredVersion: String?
        get() = prefs.getString(KEY_IGNORED, null)?.let { ChangelogRoster.normalizeVersion(it) }
            ?.takeIf { it.isNotEmpty() }
        set(value) {
            val v = value?.let { ChangelogRoster.normalizeVersion(it) }.orEmpty()
            if (v.isEmpty()) {
                prefs.edit().remove(KEY_IGNORED).apply()
            } else {
                prefs.edit().putString(KEY_IGNORED, v).apply()
            }
        }

    companion object {
        const val PREFS = "zmusic_app_update"
        const val KEY_TEST_PLAN = "test_plan"
        const val KEY_IGNORED = "ignored_version"
        const val DEFAULT_TEST_PLAN = false
    }
}
