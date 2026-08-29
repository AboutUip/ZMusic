package com.kite.zmusic

import com.kite.zmusic.data.ChangelogRoster
import com.kite.zmusic.data.Md5Util
import com.kite.zmusic.data.NcmAuthClient
import com.kite.zmusic.data.PartnerRoster
import com.kite.zmusic.data.SponsorRoster
import com.kite.zmusic.data.jsonToCatalogTree
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import com.kite.zmusic.ui.catalog.parseDailySongs
import org.json.JSONObject

class CatalogParseTest {
    @Test
    fun changelogSponsorsPartnersFromTree() {
        val changelog = mapOf(
            "ok" to true,
            "more" to false,
            "releases" to listOf(
                mapOf(
                    "id" to "r1",
                    "notes" to mapOf(
                        "version" to "V0.1.0",
                        "kind" to "Release",
                        "notice" to "横屏听歌",
                        "items" to listOf(
                            mapOf("type" to "add", "text" to "Linux 客户端"),
                            mapOf("type" to "fix", "text" to "歌词滚动"),
                        ),
                    ),
                ),
            ),
        )
        val releases = ChangelogRoster.parseRemote(changelog)
        assertTrue(releases.ok)
        assertEquals("0.1.0", releases.entries[0].version)
        assertEquals(2, releases.entries[0].items.size)

        val sponsors = SponsorRoster.parseRemote(
            mapOf(
                "ok" to true,
                "sponsors" to listOf(mapOf("name" to "萱", "amount" to 12.3, "time" to "2026-01-01")),
            ),
        )
        assertEquals("萱", sponsors.entries[0].name)
        assertTrue(sponsors.entries[0].amount.contains("12.30"))

        val partners = PartnerRoster.parseRemote(
            mapOf(
                "ok" to true,
                "vendors" to listOf(mapOf("name" to "AboutUip", "url" to "https://example.com")),
            ),
        )
        assertEquals("AboutUip", partners.entries[0].name)
    }

    @Test
    fun jsonObjectBecomesMapTree() {
        val json = JSONObject("""{"ok":true,"releases":[{"id":"1","version":"1.0","items":[{"type":"add","text":"x"}]}]}""")
        val tree = jsonToCatalogTree(json) as Map<*, *>
        val page = ChangelogRoster.parseRemote(tree)
        assertEquals(1, page.entries.size)
    }
}

class DailyParseTest {
    @Test
    fun dailySongsFromWrappedData() {
        val json = JSONObject(
            """{"code":200,"data":{"dailySongs":[{"id":1,"name":"日推","ar":[{"id":2,"name":"艺"}],"al":{"name":"a","picUrl":"https://x/y.jpg"},"dt":1000}]}}""",
        )
        val tracks = parseDailySongs(json)
        assertEquals(1, tracks.size)
        assertEquals("日推", tracks[0].name)
    }
}

class Md5UtilTest {
    @Test
    fun knownVector() {
        assertEquals("098f6bcd4621d373cade4e832627b4f6", Md5Util.md5Hex("test"))
    }
}

class MockLoginTest {
    @Test
    fun cellphoneCaptchaReturnsCookie() = runBlocking {
        val server = MockWebServer()
        server.enqueue(
            MockResponse().setBody("""{"code":200,"cookie":"MUSIC_U=from-sms","profile":{"nickname":"测"}}"""),
        )
        server.start()
        try {
            com.kite.zmusic.config.NcmApiConfig.setRuntimeBaseUrl(server.url("/").toString().trimEnd('/'))
            val json = NcmAuthClient().loginCellphone("13800138000", captcha = "123456")
            assertEquals("MUSIC_U=from-sms", com.kite.zmusic.data.NcmJson.extractCookie(json))
        } finally {
            com.kite.zmusic.config.NcmApiConfig.clearRuntimeBaseUrl()
            server.shutdown()
        }
    }

    @Test
    fun cellphonePasswordUsesMd5PasswordField() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody("""{"code":200,"cookie":"MUSIC_U=from-pwd"}"""))
        server.start()
        try {
            com.kite.zmusic.config.NcmApiConfig.setRuntimeBaseUrl(server.url("/").toString().trimEnd('/'))
            NcmAuthClient().loginCellphone("13800138000", md5Password = Md5Util.md5Hex("test"))
            val body = server.takeRequest().body.readUtf8()
            assertTrue(body.contains("md5_password="))
            assertTrue(!Regex("(^|&)password=").containsMatchIn(body))
        } finally {
            com.kite.zmusic.config.NcmApiConfig.clearRuntimeBaseUrl()
            server.shutdown()
        }
    }

    @Test
    fun emailLoginHitsLoginPath() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody("""{"code":200,"cookie":"MUSIC_U=from-mail"}"""))
        server.start()
        try {
            com.kite.zmusic.config.NcmApiConfig.setRuntimeBaseUrl(server.url("/").toString().trimEnd('/'))
            val json = NcmAuthClient().loginEmail("a@b.c", md5Password = Md5Util.md5Hex("test"))
            assertEquals("MUSIC_U=from-mail", com.kite.zmusic.data.NcmJson.extractCookie(json))
            val rec = server.takeRequest()
            assertTrue(rec.path.orEmpty().contains("/login"))
            assertTrue(rec.body.readUtf8().contains("md5_password="))
        } finally {
            com.kite.zmusic.config.NcmApiConfig.clearRuntimeBaseUrl()
            server.shutdown()
        }
    }
}
