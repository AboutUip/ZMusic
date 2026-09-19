package com.kite.zmusic.data

import java.util.Calendar
import java.util.TimeZone
import com.kite.zmusic.i18n.t

data class AnnualSong(
    val id: Long,
    val name: String,
    val artists: String,
    val coverUrl: String?,
    val playCount: Long,
)

data class AnnualArtist(
    val id: Long,
    val name: String,
    val coverUrl: String?,
    val playCount: Long,
)

data class AnnualStyle(
    val name: String,
    val playCount: Long,
)

data class AnnualHourSlot(
    val hour: Int,
    val playCount: Long,
)

data class AnnualFact(
    val label: String,
    val value: String,
)

data class AnnualReport(
    val year: Int,
    val listenDurationMs: Long? = null,
    val playCount: Long? = null,
    val songCount: Long? = null,
    val artistCount: Long? = null,
    val keyword: String? = null,
    val songs: List<AnnualSong> = emptyList(),
    val artists: List<AnnualArtist> = emptyList(),
    val styles: List<AnnualStyle> = emptyList(),
    val hours: List<AnnualHourSlot> = emptyList(),
    val facts: List<AnnualFact> = emptyList(),
) {
    fun isBlank(): Boolean =
        listenDurationMs == null &&
            playCount == null &&
            songCount == null &&
            artistCount == null &&
            keyword.isNullOrBlank() &&
            songs.isEmpty() &&
            artists.isEmpty() &&
            styles.isEmpty() &&
            hours.isEmpty() &&
            facts.isEmpty()

    fun toTrackRows(): List<TrackRow> = songs.filter { it.id > 0L }.map { song ->
        TrackRow(
            id = song.id,
            name = song.name,
            artists = song.artists,
            album = null,
            durationMs = 0L,
            coverUrl = song.coverUrl,
        )
    }
}

enum class AnnualChapter {
    Cover,
    Time,
    Volume,
    Crown,
    Rank,
    Artists,
    Styles,
    Hours,
    Close,
}

data class AnnualDurationParts(
    val hours: Long,
    val minutes: Long,
    val days: Long,
)

internal object AnnualReportLogic {
    const val MIN_YEAR = 2017
    /** `/summary/annual` 文档写到 2024；之后的年份只能走听歌足迹。 */
    const val CLASSIC_MAX_YEAR = 2024
    private val Shanghai: TimeZone = TimeZone.getTimeZone("Asia/Shanghai")

    fun maxSelectableYear(calendarYear: Int, month1to12: Int): Int {
        val lastComplete = if (month1to12 >= 12) calendarYear else calendarYear - 1
        return lastComplete.coerceAtLeast(MIN_YEAR)
    }

    fun hasClassicYearbook(year: Int): Boolean = year in MIN_YEAR..CLASSIC_MAX_YEAR

    fun emptyHint(year: Int): String =
        if (hasClassicYearbook(year)) {
            t("这一年还没有可展示的听歌记录")
        } else {
            t("网易官方年报目前做到 %s。%s 只能靠听歌足迹，这次没有拿到记录。", CLASSIC_MAX_YEAR, year)
        }

    fun availableYears(calendarYear: Int, month1to12: Int): List<Int> {
        val max = maxSelectableYear(calendarYear, month1to12)
        return (MIN_YEAR..max).toList().asReversed()
    }

    fun defaultYear(calendarYear: Int, month1to12: Int): Int =
        maxSelectableYear(calendarYear, month1to12)

    fun yearEndTimeMs(year: Int): Long = shanghaiTime(year, Calendar.DECEMBER, 31, 0, 0, 0, 0)

    fun yearLastMomentMs(year: Int): Long =
        shanghaiTime(year, Calendar.DECEMBER, 31, 23, 59, 59, 999)

    fun endTimesForYear(year: Int): List<Long> =
        listOf(yearEndTimeMs(year), yearLastMomentMs(year)).distinct()

    private fun shanghaiTime(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
        second: Int,
        millis: Int,
    ): Long {
        val cal = Calendar.getInstance(Shanghai)
        cal.clear()
        cal.timeZone = Shanghai
        cal.set(year, month, day, hour, minute, second)
        cal.set(Calendar.MILLISECOND, millis)
        return cal.timeInMillis
    }

    fun shouldAttachCurrentFootprint(selectedYear: Int, latestYear: Int): Boolean =
        selectedYear == latestYear

    fun chapters(report: AnnualReport): List<AnnualChapter> = buildList {
        add(AnnualChapter.Cover)
        if ((report.listenDurationMs ?: 0L) > 0L) add(AnnualChapter.Time)
        if ((report.playCount ?: 0L) > 0L || (report.songCount ?: 0L) > 0L) {
            add(AnnualChapter.Volume)
        }
        if (report.songs.isNotEmpty()) {
            add(AnnualChapter.Crown)
            if (report.songs.size > 1) add(AnnualChapter.Rank)
        }
        if (report.artists.isNotEmpty()) add(AnnualChapter.Artists)
        if (report.styles.isNotEmpty() || !report.keyword.isNullOrBlank()) {
            add(AnnualChapter.Styles)
        }
        if (report.hours.any { it.playCount > 0L }) add(AnnualChapter.Hours)
        add(AnnualChapter.Close)
    }

    fun durationParts(ms: Long): AnnualDurationParts {
        val safe = ms.coerceAtLeast(0L)
        val totalMin = safe / 60_000L
        return AnnualDurationParts(
            hours = totalMin / 60L,
            minutes = totalMin % 60L,
            days = totalMin / (60L * 24L),
        )
    }

    fun formatCount(n: Long): String = com.kite.zmusic.i18n.I18n.formatCompactCount(n)

    private fun trimDecimal(value: Double): String {
        val scaled = kotlin.math.round(value * 10.0) / 10.0
        return if (scaled == scaled.toLong().toDouble()) {
            scaled.toLong().toString()
        } else {
            val tenths = kotlin.math.round(value * 10.0).toLong()
            "${tenths / 10}.${tenths % 10}"
        }
    }

    fun peakHourLabel(hours: List<AnnualHourSlot>): String? {
        val peak = hours.maxByOrNull { it.playCount } ?: return null
        if (peak.playCount <= 0L) return null
        return when (peak.hour) {
            in 0..4 -> t("凌晨 %s 点", peak.hour)
            in 5..10 -> t("早晨 %s 点", peak.hour)
            in 11..13 -> t("正午 %s 点", peak.hour)
            in 14..17 -> t("午后 %s 点", peak.hour)
            in 18..21 -> t("傍晚 %s 点", peak.hour)
            else -> t("夜里 %s 点", peak.hour)
        }
    }

    fun merge(primary: AnnualReport, extra: AnnualReport): AnnualReport {
        val year = if (primary.year > 0) primary.year else extra.year
        return AnnualReport(
            year = year,
            listenDurationMs = maxOrNull(primary.listenDurationMs, extra.listenDurationMs),
            playCount = maxOrNull(primary.playCount, extra.playCount),
            songCount = maxOrNull(primary.songCount, extra.songCount),
            artistCount = maxOrNull(primary.artistCount, extra.artistCount),
            keyword = primary.keyword?.takeIf { it.isNotBlank() }
                ?: extra.keyword?.takeIf { it.isNotBlank() },
            songs = mergeSongs(primary.songs, extra.songs),
            artists = mergeArtists(primary.artists, extra.artists),
            styles = mergeStyles(primary.styles, extra.styles),
            hours = mergeHours(primary.hours, extra.hours),
            facts = mergeFacts(primary.facts, extra.facts),
        )
    }

    private fun maxOrNull(a: Long?, b: Long?): Long? = when {
        a == null -> b
        b == null -> a
        else -> maxOf(a, b)
    }

    private fun mergeSongs(a: List<AnnualSong>, b: List<AnnualSong>): List<AnnualSong> {
        val byKey = LinkedHashMap<String, AnnualSong>()
        (a + b).forEach { song ->
            val key = if (song.id > 0L) "id:${song.id}" else "n:${song.name}|${song.artists}"
            val old = byKey[key]
            byKey[key] = if (old == null) song else preferSong(old, song)
        }
        return byKey.values.sortedByDescending { it.playCount }.take(30)
    }

    private fun preferSong(old: AnnualSong, next: AnnualSong): AnnualSong = AnnualSong(
        id = old.id.takeIf { it > 0L } ?: next.id,
        name = old.name.ifBlank { next.name },
        artists = old.artists.ifBlank { next.artists },
        coverUrl = old.coverUrl ?: next.coverUrl,
        playCount = maxOf(old.playCount, next.playCount),
    )

    private fun mergeArtists(a: List<AnnualArtist>, b: List<AnnualArtist>): List<AnnualArtist> {
        val byKey = LinkedHashMap<String, AnnualArtist>()
        (a + b).forEach { artist ->
            val key = if (artist.id > 0L) "id:${artist.id}" else "n:${artist.name}"
            val old = byKey[key]
            byKey[key] = if (old == null) {
                artist
            } else {
                AnnualArtist(
                    id = old.id.takeIf { it > 0L } ?: artist.id,
                    name = old.name.ifBlank { artist.name },
                    coverUrl = old.coverUrl ?: artist.coverUrl,
                    playCount = maxOf(old.playCount, artist.playCount),
                )
            }
        }
        return byKey.values.sortedByDescending { it.playCount }.take(20)
    }

    private fun mergeStyles(a: List<AnnualStyle>, b: List<AnnualStyle>): List<AnnualStyle> {
        val byName = LinkedHashMap<String, AnnualStyle>()
        (a + b).forEach { style ->
            val key = style.name
            val old = byName[key]
            byName[key] = if (old == null) {
                style
            } else {
                AnnualStyle(key, maxOf(old.playCount, style.playCount))
            }
        }
        return byName.values.sortedByDescending { it.playCount }.take(16)
    }

    private fun mergeHours(a: List<AnnualHourSlot>, b: List<AnnualHourSlot>): List<AnnualHourSlot> {
        val counts = LongArray(24)
        (a + b).forEach { slot ->
            if (slot.hour in 0..23) {
                counts[slot.hour] = maxOf(counts[slot.hour], slot.playCount)
            }
        }
        if (counts.all { it == 0L }) return emptyList()
        return counts.mapIndexed { hour, count -> AnnualHourSlot(hour, count) }
    }

    private fun mergeFacts(a: List<AnnualFact>, b: List<AnnualFact>): List<AnnualFact> {
        val byLabel = LinkedHashMap<String, AnnualFact>()
        (a + b).forEach { fact ->
            if (!byLabel.containsKey(fact.label)) byLabel[fact.label] = fact
        }
        return byLabel.values.take(8)
    }
}
