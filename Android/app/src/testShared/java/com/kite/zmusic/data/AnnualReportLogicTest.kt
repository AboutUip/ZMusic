package com.kite.zmusic.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class AnnualReportLogicTest {
    @Test
    fun septemberUsesPreviousYear() {
        assertEquals(2025, AnnualReportLogic.defaultYear(2026, 9))
        assertEquals(
            listOf(2025, 2024, 2023, 2022, 2021, 2020, 2019, 2018, 2017),
            AnnualReportLogic.availableYears(2026, 9),
        )
    }

    @Test
    fun decemberAllowsCurrentYear() {
        assertEquals(2026, AnnualReportLogic.defaultYear(2026, 12))
        assertTrue(AnnualReportLogic.availableYears(2026, 12).first() == 2026)
    }

    @Test
    fun yearEndIsShanghaiNewYearsEveMidnight() {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Shanghai"))
        cal.timeInMillis = AnnualReportLogic.yearEndTimeMs(2024)
        assertEquals(2024, cal.get(Calendar.YEAR))
        assertEquals(Calendar.DECEMBER, cal.get(Calendar.MONTH))
        assertEquals(31, cal.get(Calendar.DAY_OF_MONTH))
        assertEquals(0, cal.get(Calendar.HOUR_OF_DAY))
    }

    @Test
    fun footprintOnlyJoinsLatestYear() {
        assertTrue(AnnualReportLogic.shouldAttachCurrentFootprint(2025, 2025))
        assertFalse(AnnualReportLogic.shouldAttachCurrentFootprint(2023, 2025))
    }

    @Test
    fun classicYearbookStopsAt2024() {
        assertTrue(AnnualReportLogic.hasClassicYearbook(2024))
        assertFalse(AnnualReportLogic.hasClassicYearbook(2025))
        assertTrue(AnnualReportLogic.emptyHint(2025).contains("2024"))
    }

    @Test
    fun yearEndTimesCoverMidnightAndLastMs() {
        val start = AnnualReportLogic.yearEndTimeMs(2025)
        val last = AnnualReportLogic.yearLastMomentMs(2025)
        assertTrue(last > start)
        assertEquals(2, AnnualReportLogic.endTimesForYear(2025).size)
        val cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Shanghai"))
        cal.timeInMillis = last
        assertEquals(2025, cal.get(Calendar.YEAR))
        assertEquals(23, cal.get(Calendar.HOUR_OF_DAY))
    }

    @Test
    fun chaptersSkipEmptySlices() {
        val blank = AnnualReport(year = 2024)
        assertEquals(
            listOf(AnnualChapter.Cover, AnnualChapter.Close),
            AnnualReportLogic.chapters(blank),
        )
        val full = AnnualReport(
            year = 2024,
            listenDurationMs = 3_600_000L,
            playCount = 10L,
            songCount = 4L,
            songs = listOf(
                AnnualSong(1, "A", "X", null, 9),
                AnnualSong(2, "B", "Y", null, 3),
            ),
            artists = listOf(AnnualArtist(3, "X", null, 9)),
            styles = listOf(AnnualStyle("流行", 2)),
            hours = listOf(AnnualHourSlot(23, 4)),
        )
        assertEquals(
            listOf(
                AnnualChapter.Cover,
                AnnualChapter.Time,
                AnnualChapter.Volume,
                AnnualChapter.Crown,
                AnnualChapter.Rank,
                AnnualChapter.Artists,
                AnnualChapter.Styles,
                AnnualChapter.Hours,
                AnnualChapter.Close,
            ),
            AnnualReportLogic.chapters(full),
        )
    }

    @Test
    fun mergeKeepsHigherCountsAndCovers() {
        val a = AnnualReport(
            year = 2024,
            playCount = 10,
            songs = listOf(AnnualSong(1, "A", "", null, 2)),
        )
        val b = AnnualReport(
            year = 2024,
            listenDurationMs = 60_000L,
            playCount = 8,
            songs = listOf(AnnualSong(1, "A", "X", "https://c", 9)),
            keyword = "夜航",
        )
        val m = AnnualReportLogic.merge(a, b)
        assertEquals(10L, m.playCount)
        assertEquals(60_000L, m.listenDurationMs)
        assertEquals("夜航", m.keyword)
        assertEquals("X", m.songs.single().artists)
        assertEquals("https://c", m.songs.single().coverUrl)
        assertEquals(9L, m.songs.single().playCount)
    }

    @Test
    fun formatCountUsesWan() {
        assertEquals("9999", AnnualReportLogic.formatCount(9999))
        assertEquals("1.2万", AnnualReportLogic.formatCount(12_000))
        assertEquals("12小时", "${AnnualReportLogic.durationParts(12 * 3_600_000L).hours}小时")
    }
}

class AnnualReportParseTest {
    @Test
    fun listenFootprintReport() {
        val json = JSONObject(
            """
            {
              "code": 200,
              "data": {
                "playCount": 3521,
                "songCount": 486,
                "artistCount": 120,
                "playDuration": 12840,
                "keyword": "夜航",
                "songPlayRank": [
                  {
                    "songId": 11,
                    "songName": "第一首",
                    "artistName": "甲",
                    "coverUrl": "https://pic/a.jpg",
                    "playCount": 99
                  }
                ],
                "artistPlayRank": [
                  {
                    "artistId": 22,
                    "artistName": "甲",
                    "picUrl": "https://pic/b.jpg",
                    "playCount": 80
                  }
                ],
                "genreList": [{ "name": "流行", "playCount": 10 }],
                "hourPlayRank": [{ "hour": 23, "playCount": 400 }]
              }
            }
            """.trimIndent(),
        )
        val report = AnnualReportParse.fromJson(2024, json)
        assertEquals(3521L, report.playCount)
        assertEquals(486L, report.songCount)
        assertEquals(120L, report.artistCount)
        assertEquals(12840L * 60_000L, report.listenDurationMs)
        assertEquals("夜航", report.keyword)
        assertEquals(11L, report.songs.single().id)
        assertEquals("第一首", report.songs.single().name)
        assertEquals("甲", report.songs.single().artists)
        assertEquals(22L, report.artists.single().id)
        assertEquals("流行", report.styles.single().name)
        assertEquals(23, report.hours.single { it.playCount > 0L }.hour)
    }

    @Test
    fun classicSummarySongObject() {
        val json = JSONObject(
            """
            {
              "code": 200,
              "data": {
                "listenSongs": 1000,
                "keyword": "治愈",
                "song": {
                  "id": 3,
                  "name": "曲",
                  "ar": [{ "name": "乙" }],
                  "al": { "picUrl": "https://z" }
                },
                "artist": { "id": 4, "name": "乙", "picUrl": "https://w" },
                "style": "民谣",
                "period": "凌晨"
              }
            }
            """.trimIndent(),
        )
        val report = AnnualReportParse.fromJson(2019, json)
        assertEquals("治愈", report.keyword)
        assertEquals("曲", report.songs.single().name)
        assertEquals("乙", report.songs.single().artists)
        assertEquals("https://z", report.songs.single().coverUrl)
        assertEquals(4L, report.artists.single().id)
        assertTrue(report.styles.any { it.name == "民谣" })
        assertTrue(report.facts.any { it.value == "凌晨" })
    }

    @Test
    fun rejectedCodeYieldsBlank() {
        val json = JSONObject("""{"code":301,"msg":"未登录"}""")
        assertTrue(AnnualReportParse.fromJson(2024, json).isBlank())
    }
}
