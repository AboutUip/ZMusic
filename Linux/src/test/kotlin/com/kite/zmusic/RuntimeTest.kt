package com.kite.zmusic

import com.kite.zmusic.config.NcmApiConfig
import com.kite.zmusic.data.AudioQuality
import com.kite.zmusic.data.LocalLibrary
import com.kite.zmusic.data.NcmUserClient
import com.kite.zmusic.data.PlayUrlResolver
import com.kite.zmusic.data.PrefsStore
import com.kite.zmusic.data.SessionStore
import com.kite.zmusic.data.TrackRow
import com.kite.zmusic.playback.FakePlaybackEngine
import com.kite.zmusic.playback.NoopMprisExporter
import com.kite.zmusic.playback.PlaybackMode
import com.kite.zmusic.playback.PlaylistCoordinator
import com.kite.zmusic.ui.notice.IslandNoticeCenter
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest

class SessionStoreTest {
    private lateinit var home: java.nio.file.Path

    @BeforeTest
    fun setup() {
        home = Files.createTempDirectory("zmusic-test-")
        System.setProperty("zmusic.home", home.toString())
    }

    @AfterTest
    fun tearDown() {
        System.clearProperty("zmusic.home")
        home.toFile().deleteRecursively()
    }

    @Test
    fun encryptRoundTripAndCorruptFile() {
        val store = SessionStore()
        store.persist("MUSIC_U=tok", "萱")
        val again = SessionStore()
        assertEquals("MUSIC_U=tok", again.session.value?.cookie)
        assertEquals("萱", again.session.value?.displayLabel)
        val enc = home.resolve("xdg_data_home").resolve("session.enc")
            .takeIf { Files.exists(it) }
            ?: home.toFile().walkTopDown().first { it.name == "session.enc" }.toPath()
        Files.write(enc, byteArrayOf(1, 2, 3, 4, 5))
        val broken = SessionStore()
        assertNull(broken.session.value)
    }
}

class PrefsStoreTest {
    @BeforeTest
    fun setup() {
        System.setProperty("zmusic.home", Files.createTempDirectory("zmusic-prefs-").toString())
    }

    @AfterTest
    fun tearDown() {
        System.clearProperty("zmusic.home")
    }

    @Test
    fun qualityAndGlassWriteBack() {
        val store = PrefsStore()
        store.update { it.copy(audioQuality = AudioQuality.HIRES, lyricWordByWord = false) }
        store.update { it.copy(glass = it.glass.copy(refraction = 0.8f, blur = 0.2f)) }
        store.update {
            it.copy(
                downloadAccel = true,
                realtimeCache = com.kite.zmusic.data.RealtimeCachePrefs(
                    enabled = true,
                    mode = com.kite.zmusic.data.RealtimeCacheMode.Aggressive,
                    maxMb = 1024,
                ),
            )
        }
        val again = PrefsStore()
        assertEquals(AudioQuality.HIRES, again.current().audioQuality)
        assertEquals(false, again.current().lyricWordByWord)
        assertEquals(0.8f, again.current().glass.refraction, 0.01f)
        assertEquals(true, again.current().downloadAccel)
        assertEquals(com.kite.zmusic.data.RealtimeCacheMode.Aggressive, again.current().realtimeCache.mode)
        assertEquals(1024, again.current().realtimeCache.maxMb)
    }
}

class PlayUrlResolverTest {
    @Test
    fun v1ThenLegacy() = runBlocking {
        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    path.contains("/song/url/v1") -> MockResponse().setBody("""{"code":200,"data":[{"id":9,"url":""}]}""")
                    path.contains("/song/url") -> MockResponse().setBody(
                        """{"code":200,"data":[{"id":9,"url":"https://cdn/x.mp3"}]}""",
                    )
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
        try {
            NcmApiConfig.setRuntimeBaseUrl(server.url("/").toString().trimEnd('/'))
            val url = PlayUrlResolver.resolve(NcmUserClient(), 9L, "c", AudioQuality.EXHIGH)
            assertEquals("https://cdn/x.mp3", url)
        } finally {
            NcmApiConfig.clearRuntimeBaseUrl()
            server.shutdown()
        }
    }

    @Test
    fun v1WinsWhenPresent() = runBlocking {
        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    path.contains("/song/url/v1") -> MockResponse().setBody(
                        """{"code":200,"data":[{"id":3,"url":"https://v1/a.mp3"}]}""",
                    )
                    else -> MockResponse().setBody("""{"code":200,"data":[{"id":3,"url":"https://legacy/a.mp3"}]}""")
                }
            }
        }
        server.start()
        try {
            NcmApiConfig.setRuntimeBaseUrl(server.url("/").toString().trimEnd('/'))
            val url = PlayUrlResolver.resolve(NcmUserClient(), 3L, "c", AudioQuality.EXHIGH)
            assertEquals("https://v1/a.mp3", url)
        } finally {
            NcmApiConfig.clearRuntimeBaseUrl()
            server.shutdown()
        }
    }
}

class CoordinatorTest {
    @Test
    fun playPauseSeekAndMpris() = runBlocking {
        System.setProperty("zmusic.home", Files.createTempDirectory("zmusic-co-").toString())
        val engine = FakePlaybackEngine()
        engine.setDuration(10_000L)
        val mpris = NoopMprisExporter()
        val coord = PlaylistCoordinator(
            userClient = NcmUserClient(),
            engine = engine,
            mpris = mpris,
            cookie = { "x" },
            quality = { AudioQuality.EXHIGH },
            persistentPlayback = { true },
        )
        val tracks = listOf(
            TrackRow(1, "A", "art", null, 10_000, localAudioUri = "file:///tmp/fixture.mp3"),
            TrackRow(2, "B", "art", null, 10_000, localAudioUri = "file:///tmp/fixture.mp3"),
        )
        coord.playQueue(tracks, 0)
        delay(250)
        assertEquals("file:///tmp/fixture.mp3", engine.lastUrl)
        assertTrue(engine.isPlaying())
        mpris.simulatePlayPause()
        delay(20)
        assertTrue(!engine.isPlaying())
        coord.skipNext()
        delay(250)
        assertEquals(1, coord.ui.value.index)
        assertEquals(1, coord.ui.value.skipDir)
        assertTrue(coord.ui.value.skipSeq > 0)
        coord.cycleMode()
        assertEquals(PlaybackMode.REPEAT_ONE, coord.ui.value.playbackMode)
        engine.emitEnded()
        delay(250)
        assertNotNull(engine.lastUrl)
        coord.duckForOthers(true)
        assertEquals(0.35f, engine.volume, 0.01f)
        coord.close()
        System.clearProperty("zmusic.home")
    }
}

class CacheMechanismTest {
    private lateinit var home: java.nio.file.Path

    @BeforeTest
    fun setup() {
        home = Files.createTempDirectory("zmusic-cache-")
        System.setProperty("zmusic.home", home.toString())
    }

    @AfterTest
    fun tearDown() {
        System.clearProperty("zmusic.home")
        home.toFile().deleteRecursively()
    }

    @Test
    fun occupancyPlayUriAndListenModes() {
        val id = 42L
        val f = LocalLibrary.cacheFile(id)
        Files.write(f, ByteArray(2048) { 1 })
        val occ = LocalLibrary.occupancy(512)
        assertEquals(1, occ.fileCount)
        assertTrue(occ.usedBytes >= 2048)
        assertNotNull(LocalLibrary.playUri(id, null, realtime = true, accel = false))
        assertNull(LocalLibrary.playUri(id, null, realtime = false, accel = false))
        val listed = LocalLibrary.listCachedTracks()
        assertTrue(listed.any { it.id == id })
        LocalLibrary.finishListen(id, 1_000L, 10_000L, com.kite.zmusic.data.RealtimeCacheMode.Realtime, null)
        assertEquals(0, LocalLibrary.occupancy(512).fileCount)
        val cautious = com.kite.zmusic.data.RealtimeCachePrefs(enabled = true)
        val realtime = com.kite.zmusic.data.RealtimeCachePrefs(
            enabled = true,
            mode = com.kite.zmusic.data.RealtimeCacheMode.Realtime,
        )
        assertEquals("谨慎", com.kite.zmusic.data.RealtimeCacheMode.Cautious.title)
        assertTrue(!cautious.liveDownload)
        assertTrue(realtime.liveDownload)
        assertTrue(cautious.settingsSubtitle.contains("谨慎"))
        assertEquals("0 B", LocalLibrary.formatBytes(0L))
    }

    @Test
    fun accelFilePlaysWithoutNetwork() = runBlocking {
        val id = 7L
        val dest = LocalLibrary.exportsDir().resolve("$id.mp3")
        Files.write(dest, ByteArray(2048) { 2 })
        val engine = FakePlaybackEngine()
        engine.setDuration(10_000L)
        val coord = PlaylistCoordinator(
            userClient = NcmUserClient(),
            engine = engine,
            mpris = NoopMprisExporter(),
            cookie = { "x" },
            quality = { AudioQuality.EXHIGH },
            persistentPlayback = { true },
            downloadAccel = { true },
        )
        coord.playQueue(listOf(TrackRow(id, "A", "art", null, 10_000)), 0)
        delay(300)
        assertTrue(engine.lastUrl.orEmpty().startsWith("file:"))
        assertEquals("此歌曲已进行缓存加速", coord.ui.value.notice?.message)
        coord.close()
    }

    @Test
    fun realtimeCachePlaysFromCacheDir() = runBlocking {
        val id = 8L
        Files.write(LocalLibrary.cacheFile(id), ByteArray(2048) { 3 })
        val engine = FakePlaybackEngine()
        engine.setDuration(10_000L)
        val coord = PlaylistCoordinator(
            userClient = NcmUserClient(),
            engine = engine,
            mpris = NoopMprisExporter(),
            cookie = { "x" },
            quality = { AudioQuality.EXHIGH },
            persistentPlayback = { true },
            downloadAccel = { false },
            cachePrefs = {
                com.kite.zmusic.data.RealtimeCachePrefs(enabled = true)
            },
        )
        coord.playQueue(listOf(TrackRow(id, "A", "art", null, 10_000)), 0)
        delay(300)
        assertTrue(engine.lastUrl.orEmpty().startsWith("file:"))
        assertTrue(coord.ui.value.notice?.message != "此歌曲已进行缓存加速")
        coord.close()
    }
}

class IslandNoticeTest {
    @Test
    fun dropsBlank() {
        val c = IslandNoticeCenter()
        c.show("  ")
        assertNull(c.notice.value)
        c.show("已收藏")
        assertEquals("已收藏", c.notice.value?.message)
    }
}

class AppContainerSmokeTest {
    @Test
    fun constructsAndClosesWithoutThrowing() {
        val home = Files.createTempDirectory("zmusic-app-")
        System.setProperty("zmusic.home", home.toString())
        try {
            val app = com.kite.zmusic.AppContainer(engineOverride = FakePlaybackEngine())
            assertNotNull(app.bridge)
            app.coordinator.close()
        } finally {
            System.clearProperty("zmusic.home")
            home.toFile().deleteRecursively()
        }
    }
}
