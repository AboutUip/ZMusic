package com.kite.zmusic

import com.kite.zmusic.data.AudioQuality
import com.kite.zmusic.data.AppAppearance
import com.kite.zmusic.data.AppPrefs
import com.kite.zmusic.data.ChromeGlassMode
import com.kite.zmusic.data.GlassStyle
import com.kite.zmusic.data.LrcParser
import com.kite.zmusic.data.NcmHomeParse
import com.kite.zmusic.data.NcmJson
import com.kite.zmusic.data.NcmLibraryParse
import com.kite.zmusic.data.NcmPlaybackParse
import com.kite.zmusic.data.YrcParser
import com.kite.zmusic.playback.pulseSpectrum
import com.kite.zmusic.data.isSelfHeartPlaylist
import com.kite.zmusic.playback.PlaybackMode
import com.kite.zmusic.ui.catalog.parseSearchHits
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
        val liked = NcmLibraryParse.likedIdsFromLikeCheck(
            JSONObject("""{"code":200,"data":[11,22]}"""),
        )
        assertTrue(liked.contains(11L))
        assertTrue(NcmLibraryParse.isTrackLiked(JSONObject("""{"code":200,"ids":[11]}"""), 11L))
        assertEquals(setOf(3L, 4L), NcmLibraryParse.likeIdsFromLikeList(JSONObject("""{"ids":[3,4]}""")))
        assertTrue(NcmLibraryParse.isSubscribed(JSONObject("""{"subscribed":true}""")))
        assertEquals(listOf("热歌"), NcmLibraryParse.searchHotTerms(JSONObject("""{"data":[{"searchWord":"热歌"}]}""")))
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

    @Test
    fun personalizedFeedAndCollectedAlbums() {
        val playlists = NcmHomeParse.personalizedPlaylists(
            JSONObject("""{"code":200,"result":[{"id":1,"name":"推荐歌单","picUrl":"https://p/a.jpg","playCount":9}]}"""),
        )
        assertEquals(1, playlists.size)
        assertEquals("推荐歌单", playlists[0].name)
        val news = NcmHomeParse.personalizedNewSongs(
            JSONObject(
                """{"code":200,"result":[{"song":{"id":2,"name":"新歌","ar":[{"name":"A"}],"al":{"picUrl":"https://p/n.jpg"},"dt":1000}}]}""",
            ),
        )
        assertEquals("新歌", news[0].name)
        val albums = NcmHomeParse.collectedAlbums(
            JSONObject("""{"code":200,"data":[{"id":5,"name":"专","picUrl":"https://p/c.jpg","artist":{"name":"B"}}]}"""),
        )
        assertEquals(1, albums.size)
        assertEquals("专", albums[0].name)
        assertEquals("B", albums[0].artist)
        val fm = NcmHomeParse.personalFmTracks(
            JSONObject("""{"code":200,"data":[{"id":9,"name":"漫游","ar":[{"name":"C"}],"al":{"picUrl":"https://p/f.jpg"},"dt":1000}]}"""),
        )
        assertEquals("漫游", fm[0].name)
        val intel = NcmHomeParse.intelligenceTracks(
            JSONObject("""{"code":200,"data":[{"songInfo":{"id":8,"name":"心动","ar":[{"name":"D"}],"al":{},"dt":1}}]}"""),
        )
        assertEquals("心动", intel[0].name)
    }
}

class SearchHitParseTest {
    @Test
    fun parsesUserAndMvHits() {
        val users = parseSearchHits(
            JSONObject("""{"result":{"userprofiles":[{"userId":5,"nickname":"萱","avatarUrl":"https://p/a.jpg"}]}}"""),
            1002,
        )
        assertEquals(5L, users[0].id)
        assertEquals("萱", users[0].name)
        val mvs = parseSearchHits(
            JSONObject("""{"result":{"mvs":[{"id":7,"name":"PV","artistName":"A","cover":"https://p/m.jpg"}]}}"""),
            1004,
        )
        assertEquals("PV", mvs[0].name)
    }
}

class AppearanceGlassTest {
    @Test
    fun appearanceResolvesSystemFromOs() {
        assertEquals(false, AppAppearance.Light.resolveDark(true))
        assertEquals(true, AppAppearance.Dark.resolveDark(false))
        assertEquals(true, AppAppearance.System.resolveDark(true))
        assertEquals(false, AppAppearance.System.resolveDark(false))
        assertEquals("跟随系统", AppAppearance.System.title)
    }

    @Test
    fun chromeGlassHasLiquidFrostedSolid() {
        assertEquals(listOf("液态", "磨砂", "纯色"), ChromeGlassMode.entries.map { it.title })
        assertTrue(GlassStyle(mode = ChromeGlassMode.Frosted, blur = 0.5f).settingsSubtitle.contains("磨砂"))
        assertEquals("纯色，不透明", GlassStyle(mode = ChromeGlassMode.Solid).settingsSubtitle)
        assertEquals(true, AppPrefs().playerHalo)
    }

    @Test
    fun pulseSpectrumRisesWhenPlaying() {
        val idle = pulseSpectrum(false, 0L)
        val live = pulseSpectrum(true, 12_000L)
        assertTrue(idle.low < 0.2f)
        assertTrue(live.low > idle.low)
        assertTrue(live.high > 0f)
    }
}

class LandscapeSourceTest {
    @Test
    fun homeDoesNotLoadLikedPlaylists() {
        val text = readLinuxSrc("ui/home/HomeScreen.kt")
        assertNotNull(text)
        assertTrue(!text.contains("userPlaylist"))
        assertTrue(text.contains("推荐歌单"))
        assertTrue(text.contains("isLikedMusicPlaylistName"))
        assertTrue(text.contains("LandscapeDailyPanel"))
    }

    @Test
    fun profileHasLandscapeLibrarySections() {
        val text = readLinuxSrc("ui/library/ProfileScreen.kt")
        assertNotNull(text)
        assertTrue(text.contains("我喜欢的音乐"))
        assertTrue(text.contains("创建的歌单"))
        assertTrue(text.contains("进入用户空间"))
        assertTrue(text.contains("albumSublist"))
        assertTrue(text.contains("playlistCreate"))
        assertTrue(text.contains("新建"))
    }

    @Test
    fun settingsUsesGroupedCardsAndGlassPages() {
        val settings = readLinuxSrc("ui/settings/SettingsScreen.kt")
        val pages = readLinuxSrc("ui/settings/SettingsPages.kt")
        assertNotNull(settings)
        assertNotNull(pages)
        assertTrue(settings.contains("SettingsGroup"))
        assertTrue(settings.contains("AppearanceSettingsPage"))
        assertTrue(settings.contains("LiquidGlassStylePage"))
        assertTrue(settings.contains("DownloadAccelSettingsPage"))
        assertTrue(settings.contains("RealtimeCacheSettingsPage"))
        assertTrue(settings.contains("AnimatedContent"))
        assertTrue(settings.contains("chromeGlassSurface"))
        assertTrue(settings.contains("动态光晕"))
        assertTrue(pages.contains("磨砂"))
        assertTrue(pages.contains("AppAppearance.System"))
        assertTrue(!settings.contains("if (it.appearance == AppAppearance.Light)"))
    }

    @Test
    fun playerCopiesLandscapeHaloAndVinyl() {
        val body = readLinuxSrc("ui/player/LandscapePlayerBody.kt")
        val orbs = readLinuxSrc("ui/player/PlayerAtmosphere.kt")
        val vinyl = readLinuxSrc("ui/player/PlayerVinyl.kt")
        assertNotNull(body)
        assertNotNull(orbs)
        assertNotNull(vinyl)
        assertTrue(body.contains("GeminiOrbsBackdrop"))
        assertTrue(body.contains("VinylWithCoverArt"))
        assertTrue(orbs.contains("Color(0xFFE8A0C8)"))
        assertTrue(vinyl.contains("VinylDiscBase"))
        assertTrue(body.contains("onToggleLike"))
        assertTrue(body.contains("QueuePickOverlay"))
        assertTrue(body.contains("投影歌词"))
        assertTrue(body.contains("播放队列"))
        assertTrue(body.contains("skipDir"))
        assertTrue(body.contains("slideInHorizontally"))
        assertTrue(vinyl.contains("skipSeq"))
        assertTrue(!body.contains("fun VinylDisc(playing"))
    }

    @Test
    fun catalogWiresSearchSubscribeAndCache() {
        val search = readLinuxSrc("ui/catalog/CatalogOverlays.kt")
        val detail = readLinuxSrc("ui/catalog/DetailOverlays.kt")
        val home = readLinuxSrc("ui/home/HomeScreen.kt")
        val settings = readLinuxSrc("ui/settings/SettingsScreen.kt")
        val pages = readLinuxSrc("ui/settings/SettingsPages.kt")
        val shell = readLinuxSrc("ui/main/MainShell.kt")
        assertNotNull(search)
        assertNotNull(detail)
        assertNotNull(home)
        assertNotNull(settings)
        assertNotNull(pages)
        assertNotNull(shell)
        assertTrue(search.contains("1004 to \"MV\""))
        assertTrue(search.contains("1002 to \"用户\""))
        assertTrue(search.contains("searchHotDetail"))
        assertTrue(search.contains("CachedSongsOverlay"))
        assertTrue(search.contains("playlistSubscribe"))
        assertTrue(detail.contains("ArtistTab"))
        assertTrue(detail.contains("artistAlbums"))
        assertTrue(detail.contains("userFollow"))
        assertTrue(home.contains("targetType == 10"))
        assertTrue(home.contains("targetType == 100"))
        assertTrue(home.contains("targetType == 1"))
        assertTrue(settings.contains("SettingsPage.Appreciate"))
        assertTrue(pages.contains("img_wechat_appreciate"))
        assertTrue(shell.contains("onToggleLike"))
        assertTrue(shell.contains("insertNext"))
        assertTrue(shell.contains("personalFmTracks"))
        assertTrue(search.contains("occupancy"))
        val cachePage = readLinuxSrc("ui/settings/SettingsCachePages.kt")
        val lib = readLinuxSrc("data/LocalLibrary.kt")
        val coord = readLinuxSrc("playback/PlaylistCoordinator.kt")
        val mini = readLinuxSrc("ui/main/MiniPlayerBar.kt")
        val rail = readLinuxSrc("ui/main/LandscapeNavRail.kt")
        val urlImage = readLinuxSrc("ui/common/UrlImage.kt")
        assertNotNull(cachePage)
        assertNotNull(lib)
        assertNotNull(coord)
        assertNotNull(mini)
        assertNotNull(rail)
        assertNotNull(urlImage)
        assertTrue(cachePage.contains("OccupancyCard"))
        assertTrue(lib.contains("fun playUri"))
        assertTrue(lib.contains("fun occupancy"))
        assertTrue(lib.contains("fun finishListen"))
        assertTrue(lib.contains("readImageCache"))
        assertTrue(lib.contains("listOf(cacheDir())"))
        assertTrue(!lib.contains("accelDirs() + cacheDir()"))
        assertTrue(coord.contains("cachePrefs"))
        assertTrue(coord.contains("此歌曲已进行缓存加速"))
        assertTrue(mini.contains("AnimatedContent"))
        assertTrue(rail.contains("animateColorAsState"))
        assertTrue(urlImage.contains("Crossfade"))
        assertTrue(urlImage.contains("readImageCache"))
        assertTrue(home.contains("animateScrollToPage"))
        assertTrue(shell.contains("LandscapeCoverEnter"))
        assertTrue(shell.contains("slideInVertically"))
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
        assertTrue(text.contains("GNU_FORMAT"))
        assertTrue(text.contains("assert_dpkg_tar"))
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

private fun readLinuxSrc(rel: String): String? {
    val roots = listOf(
        java.nio.file.Path.of("src", "main", "kotlin", "com", "kite", "zmusic").resolve(rel),
        java.nio.file.Path.of("Linux", "src", "main", "kotlin", "com", "kite", "zmusic").resolve(rel),
    )
    return roots.firstOrNull { java.nio.file.Files.exists(it) }?.let {
        java.nio.file.Files.readString(it)
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
