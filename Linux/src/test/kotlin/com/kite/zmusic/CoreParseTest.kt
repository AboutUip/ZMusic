package com.kite.zmusic

import com.kite.zmusic.data.AudioQuality
import com.kite.zmusic.data.LrcParser
import com.kite.zmusic.data.NcmHomeParse
import com.kite.zmusic.data.NcmJson
import com.kite.zmusic.data.NcmLibraryParse
import com.kite.zmusic.data.NcmPlaybackParse
import com.kite.zmusic.data.YrcParser
import com.kite.zmusic.data.isSelfHeartPlaylist
import com.kite.zmusic.playback.PlaybackMode
import com.kite.zmusic.ui.main.MainOverlay
import com.kite.zmusic.ui.main.OverlayStack
import org.json.JSONObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AudioQualityTest {
    @Test
    fun defaultIsExhigh() {
        assertEquals(AudioQuality.EXHIGH, AudioQuality.Default)
    }

    @Test
    fun fallbacksMatchAndroid() {
        assertEquals(listOf(AudioQuality.HIGHER, AudioQuality.STANDARD), AudioQuality.EXHIGH.fallbacks())
        assertEquals(listOf(AudioQuality.LOSSLESS, AudioQuality.EXHIGH), AudioQuality.HIRES.fallbacks())
        assertTrue(AudioQuality.STANDARD.fallbacks().isEmpty())
        assertEquals(AudioQuality.LOSSLESS, AudioQuality.fromLevel("lossless"))
        assertEquals(AudioQuality.EXHIGH, AudioQuality.fromLevel("nope"))
    }
}

class PlaybackModeTest {
    @Test
    fun cyclesOrderRepeatShuffle() {
        assertEquals(PlaybackMode.REPEAT_ONE, PlaybackMode.ORDER.next())
        assertEquals(PlaybackMode.SHUFFLE, PlaybackMode.REPEAT_ONE.next())
        assertEquals(PlaybackMode.ORDER, PlaybackMode.SHUFFLE.next())
    }
}

class LrcParserTest {
    @Test
    fun parsesTimedLines() {
        val lines = LrcParser.parse("[00:12.50]第一行\n[01:03]第二行\n[99:00]···")
        assertEquals(2, lines.size)
        assertEquals(12_500L, lines[0].timeMs)
        assertEquals("第一行", lines[0].text)
        assertEquals(63_000L, lines[1].timeMs)
    }
}

class YrcParserTest {
    @Test
    fun parsesWordTimedLine() {
        val lines = YrcParser.parse("[1200,800](1200,200,0)你(1400,200,0)好")
        assertEquals(1, lines.size)
        assertEquals(1200L, lines[0].timeMs)
        assertEquals("你好", lines[0].text)
        assertEquals(2, lines[0].words.size)
        assertEquals("你", lines[0].words[0].text)
    }
}

class NcmJsonTest {
    @Test
    fun loginStatusAndCookie() {
        val json = JSONObject(
            """{"code":200,"cookie":"MUSIC_U=abc","data":{"account":{"id":42},"profile":{"nickname":"萱"}}}""",
        )
        assertTrue(NcmJson.isLoggedInStatus(json))
        assertEquals("MUSIC_U=abc", NcmJson.extractCookie(json))
        assertEquals("萱", NcmJson.displayLabelFromLogin(json))
        assertEquals(42L, NcmJson.userIdFromLoginStatus(json))
        assertEquals(800, NcmJson.qrCheckCode(JSONObject("""{"code":800}""")))
    }
}

class NcmPlaybackParseTest {
    @Test
    fun songUrlAndLyrics() {
        val urlJson = JSONObject("""{"code":200,"data":[{"id":1,"url":"https://m/a.mp3"},{"id":2,"url":""}]}""")
        assertEquals("https://m/a.mp3", NcmPlaybackParse.songUrlForId(urlJson, 1))
        assertNull(NcmPlaybackParse.songUrlForId(urlJson, 2))
        val lrc = JSONObject("""{"code":200,"lrc":{"lyric":"[00:01]hi"},"tlyric":{"lyric":"[00:01]嗨"}}""")
        assertEquals("[00:01]hi", NcmPlaybackParse.lrcText(lrc))
        assertEquals("[00:01]嗨", NcmPlaybackParse.translatedLrcText(lrc))
    }
}

class LibraryParseTest {
    @Test
    fun heartPlaylistAndTracks() {
        val json = JSONObject(
            """
            {"code":200,"playlist":[
              {"id":9,"name":"我喜欢的音乐","specialType":5,"trackCount":2,
               "creator":{"userId":7},"subscribed":false,"coverImgUrl":"https://p/a.jpg"}
            ]}
            """.trimIndent(),
        )
        val list = NcmLibraryParse.playlistsFromUserPlaylist(json, 7)
        assertEquals(1, list.size)
        assertTrue(list[0].isHeartPlaylist)
        assertTrue(list[0].isOwned)
        val detail = JSONObject(
            """
            {"code":200,"playlist":{"tracks":[
              {"id":11,"name":"歌","ar":[{"id":1,"name":"A"}],"al":{"name":"专","picUrl":"https://p/c.jpg"},"dt":180000}
            ]}}
            """.trimIndent(),
        )
        val tracks = NcmLibraryParse.tracksFromPlaylistDetail(detail)
        assertEquals(1, tracks.size)
        assertEquals("歌", tracks[0].name)
        assertEquals("A", tracks[0].artists)
    }

    @Test
    fun othersLikedMusicIsNotSelfHeart() {
        assertFalse(
            isSelfHeartPlaylist(selfUid = 1, creatorId = 2, subscribed = true, specialType = 5, name = "我喜欢的音乐"),
        )
    }
}

class HomeParseTest {
    @Test
    fun bannersAndCharts() {
        val banners = NcmHomeParse.banners(
            JSONObject("""{"banners":[{"pic":"https://p/b.jpg","targetId":3,"targetType":1000,"typeTitle":"歌单"}]}"""),
        )
        assertEquals(1, banners.size)
        assertEquals(3L, banners[0].targetId)
        val charts = NcmHomeParse.charts(
            JSONObject("""{"list":[{"id":8,"name":"热歌榜","coverImgUrl":"https://p/c.jpg","updateFrequency":"每天"}]}"""),
        )
        assertEquals("热歌榜", charts[0].name)
    }
}

class OverlayStackTest {
    @Test
    fun pushPopReplace() {
        val stack = OverlayStack()
        assertNull(stack.top())
        stack.push(MainOverlay.Settings)
        stack.push(MainOverlay.Search)
        assertEquals(MainOverlay.Search, stack.top())
        assertEquals(MainOverlay.Search, stack.pop())
        assertEquals(MainOverlay.Settings, stack.top())
        stack.replaceTop(MainOverlay.Charts)
        assertEquals(MainOverlay.Charts, stack.top())
        stack.clear()
        assertTrue(stack.snapshot().isEmpty())
    }
}

class DebControlTest {
    @Test
    fun controlFileIsAmd64Zmusic() {
        val roots = listOf(
            java.nio.file.Path.of("..", "Distribution", "Linux", "debian", "control"),
            java.nio.file.Path.of("Distribution", "Linux", "debian", "control"),
        )
        val text = roots.firstOrNull { java.nio.file.Files.exists(it) }?.let {
            java.nio.file.Files.readString(it)
        }
        assertNotNull(text, "debian/control missing")
        assertTrue(text.contains("Package: zmusic"))
        assertTrue(text.contains("Architecture: amd64"))
        assertTrue(text.contains("Version:"))
        assertTrue(text.contains("libasound2t64 | libasound2"))
        assertTrue(text.contains("libgl1 | libgl1-mesa-glx"))
    }

    @Test
    fun desktopFileIsMenuVisible() {
        val text = readDist("zmusic.desktop")
        assertNotNull(text)
        assertTrue(text.contains("Name=ZMusic"))
        assertTrue(text.contains("Categories=AudioVideo;Audio;Player;GTK;"))
        assertTrue(text.contains("Icon=zmusic"))
        assertTrue(text.contains("Exec=/usr/bin/zmusic"))
        assertTrue(text.contains("Name[zh_CN]="))
    }

    @Test
    fun postinstRefreshesApplicationMenu() {
        val text = readDist("debian/postinst")
        assertNotNull(text)
        assertTrue(text.contains("update-desktop-database"))
        assertTrue(text.contains("gtk-update-icon-cache"))
        assertTrue(text.contains("update-menus"))
    }

    @Test
    fun buildDebScriptExists() {
        val text = readDist("build-deb.sh")
        assertNotNull(text, "Distribution/Linux/build-deb.sh missing")
        assertTrue(text.contains("#!/usr/bin/env bash"))
        assertTrue(text.contains("pack.py"))
        assertTrue(text.contains("--install-deps"))
        assertTrue(text.contains("jdk-21"))
        assertTrue(text.contains("XAIOP_URL"))
        assertTrue(text.contains("ensure_xaiop_jar"))
        assertTrue(text.contains("pack.py --self-test"))
        assertTrue(text.contains("--skip-gradle"))
        assertTrue(!text.contains("\r"), "build-deb.sh must be LF (CRLF breaks shebang on Linux)")
    }

    @Test
    fun packPyVendorsXaiop() {
        val text = readDist("pack.py")
        assertNotNull(text)
        assertTrue(text.contains("XAIOP_URL"))
        assertTrue(text.contains("ensure_xaiop_jar"))
    }

    @Test
    fun packPyLauncherResolvesInstallRoot() {
        val text = readDist("pack.py")
        assertNotNull(text)
        val launcher = text.substringAfter("INNER_LAUNCHER = \"\"\"").substringBefore("\"\"\"")
        assertTrue(launcher.contains("dirname --"))
        assertTrue(launcher.contains("\$ROOT/zmusic.jar"))
        assertTrue(launcher.contains("--smoke"))
        assertTrue(!launcher.contains("-jar /opt/zmusic/zmusic.jar"))
        assertTrue(text.contains("--compress=zip"))
    }

    @Test
    fun smokeReturnsBeforeAppContainer() {
        val roots = listOf(
            java.nio.file.Path.of("src", "main", "kotlin", "com", "kite", "zmusic", "Main.kt"),
            java.nio.file.Path.of("Linux", "src", "main", "kotlin", "com", "kite", "zmusic", "Main.kt"),
        )
        val text = roots.firstOrNull { java.nio.file.Files.exists(it) }?.let {
            java.nio.file.Files.readString(it)
        }
        assertNotNull(text)
        val smoke = text.indexOf("args.contains(\"--smoke\")")
        val container = text.indexOf("AppContainer()")
        assertTrue(smoke >= 0 && container >= 0 && smoke < container)
    }

    @Test
    fun directionalIconsUseAutoMirrored() {
        val files = listOf(
            "ui/catalog/CatalogOverlays.kt",
            "ui/player/LandscapePlayerBody.kt",
            "ui/settings/SettingsScreen.kt",
        ).flatMap { rel ->
            listOf(
                java.nio.file.Path.of("src", "main", "kotlin", "com", "kite", "zmusic").resolve(rel),
                java.nio.file.Path.of("Linux", "src", "main", "kotlin", "com", "kite", "zmusic").resolve(rel),
            )
        }
        val texts = files.filter { java.nio.file.Files.exists(it) }.map { java.nio.file.Files.readString(it) }
        assertTrue(texts.isNotEmpty())
        texts.forEach { text ->
            assertTrue(!text.contains("Icons.Outlined.ArrowBack"))
            assertTrue(!text.contains("Icons.Outlined.Logout"))
            assertTrue(!text.contains("Icons.Outlined.KeyboardArrowRight"))
        }
    }
}

class XaiopJarTest {
    @Test
    fun officialSdkJarIsVendored() {
        val roots = listOf(
            java.nio.file.Path.of("libs", "xaiop-0.15.1.jar"),
            java.nio.file.Path.of("Linux", "libs", "xaiop-0.15.1.jar"),
        )
        val jar = roots.firstOrNull { java.nio.file.Files.isRegularFile(it) }
        assertNotNull(jar, "Linux/libs/xaiop-0.15.1.jar missing")
        assertTrue(java.nio.file.Files.size(jar) > 100_000L)
    }
}

private fun readDist(rel: String): String? {
    val roots = listOf(
        java.nio.file.Path.of("..", "Distribution", "Linux").resolve(rel),
        java.nio.file.Path.of("Distribution", "Linux").resolve(rel),
    )
    return roots.firstOrNull { java.nio.file.Files.exists(it) }?.let {
        java.nio.file.Files.readString(it)
    }
}
