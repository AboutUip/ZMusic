package com.kite.zmusic.ui.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kite.zmusic.data.AnnualReport
import com.kite.zmusic.data.AnnualReportLogic
import com.kite.zmusic.data.AnnualReportParse
import com.kite.zmusic.data.NcmJson
import com.kite.zmusic.data.NcmUserClient
import com.kite.zmusic.data.SessionRepository
import java.util.Calendar
import java.util.TimeZone
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AnnualReportUi(
    val year: Int,
    val years: List<Int>,
    val nickname: String?,
    val loading: Boolean = true,
    val error: String? = null,
    val report: AnnualReport = AnnualReport(year = year),
)

class AnnualReportViewModel(
    private val sessionRepository: SessionRepository,
    private val userClient: NcmUserClient,
) : ViewModel() {

    private val shanghai = TimeZone.getTimeZone("Asia/Shanghai")

    private val _ui = MutableStateFlow(initialUi())
    val ui: StateFlow<AnnualReportUi> = _ui.asStateFlow()

    init {
        load(_ui.value.year)
    }

    fun load(year: Int) {
        val years = _ui.value.years.ifEmpty { yearsNow() }
        val picked = year.takeIf { it in years } ?: years.firstOrNull() ?: year
        viewModelScope.launch {
            val session = sessionRepository.session.value
            if (session == null || session.isGuest || session.cookie.isBlank()) {
                _ui.update {
                    it.copy(
                        year = picked,
                        years = years,
                        nickname = session?.displayLabel,
                        loading = false,
                        error = "登录网易云账号后才能看年度报告",
                        report = AnnualReport(year = picked),
                    )
                }
                return@launch
            }
            _ui.update {
                it.copy(
                    year = picked,
                    years = years,
                    nickname = session.displayLabel?.trim()?.takeIf { name -> name.isNotEmpty() },
                    loading = true,
                    error = null,
                )
            }
            val latest = years.firstOrNull() ?: picked
            val cookie = session.cookie
            val fetched = runCatching { fetch(cookie, picked, latest) }
            val report = fetched.getOrNull() ?: AnnualReport(year = picked)
            val error = when {
                fetched.isFailure ->
                    NcmJson.userFacingThrowable(fetched.exceptionOrNull()!!, "年度报告暂时无法打开")
                report.isBlank() -> AnnualReportLogic.emptyHint(picked)
                else -> null
            }
            _ui.update {
                it.copy(
                    year = picked,
                    years = years,
                    loading = false,
                    error = error,
                    report = report,
                )
            }
        }
    }

    private suspend fun fetch(cookie: String, year: Int, latestYear: Int): AnnualReport =
        coroutineScope {
            val latest = AnnualReportLogic.shouldAttachCurrentFootprint(year, latestYear)
            val jobs = buildList {
                add(async { runCatching { userClient.summaryAnnual(cookie, year) }.getOrNull() })
                AnnualReportLogic.endTimesForYear(year).forEach { end ->
                    add(
                        async {
                            runCatching {
                                userClient.listenDataReport(cookie, "year", end)
                            }.getOrNull()
                        },
                    )
                    add(
                        async {
                            runCatching {
                                userClient.listenDataSongPlayRank(cookie, "month", end)
                            }.getOrNull()
                        },
                    )
                }
                if (latest) {
                    add(
                        async {
                            runCatching { userClient.listenDataReport(cookie, "year") }.getOrNull()
                        },
                    )
                    add(
                        async {
                            runCatching { userClient.listenDataYearReport(cookie) }.getOrNull()
                        },
                    )
                }
            }
            var merged = AnnualReport(year = year)
            jobs.forEach { job ->
                val json = job.await() ?: return@forEach
                merged = AnnualReportLogic.merge(merged, AnnualReportParse.fromJson(year, json))
            }
            merged
        }

    private fun initialUi(): AnnualReportUi {
        val years = yearsNow()
        val year = years.firstOrNull() ?: AnnualReportLogic.MIN_YEAR
        return AnnualReportUi(year = year, years = years, nickname = null)
    }

    private fun yearsNow(): List<Int> {
        val cal = Calendar.getInstance(shanghai)
        return AnnualReportLogic.availableYears(
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
        )
    }
}

class AnnualReportViewModelFactory(
    private val sessionRepository: SessionRepository,
    private val userClient: NcmUserClient,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return AnnualReportViewModel(sessionRepository, userClient) as T
    }
}
