package com.kite.zmusic

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.kite.zmusic.data.AppPrefs
import com.kite.zmusic.data.AudioQuality
import com.kite.zmusic.data.GlassStyle
import com.kite.zmusic.data.NcmAuthClient
import com.kite.zmusic.data.SessionStore
import com.kite.zmusic.data.TrackRow
import com.kite.zmusic.playback.PlaybackUiState
import com.kite.zmusic.ui.features.FeaturesScreen
import com.kite.zmusic.ui.home.HomeSearchEntry
import com.kite.zmusic.ui.login.LoginScreen
import com.kite.zmusic.ui.main.LandscapeNavRail
import com.kite.zmusic.ui.main.LandscapeRailWidth
import com.kite.zmusic.ui.main.LandscapeVinylWeight
import com.kite.zmusic.ui.main.MainDestination
import com.kite.zmusic.ui.main.MiniPlayerBar
import com.kite.zmusic.ui.main.landscapeVinylDiscDp
import com.kite.zmusic.ui.notice.IslandNoticeCenter
import com.kite.zmusic.ui.notice.IslandNoticeHost
import com.kite.zmusic.ui.player.LandscapePlayerBody
import com.kite.zmusic.ui.theme.MainColors
import com.kite.zmusic.ui.theme.MainPalette
import androidx.compose.ui.unit.dp
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class ComposeSmokeTest {
    @Test
    fun railSwitchesDestinations() = runComposeUiTest {
        MainPalette.bind(MainColors.Light)
        var dest by mutableStateOf(MainDestination.Home)
        var settings = false
        setContent {
            LandscapeNavRail(
                selected = dest,
                settingsSelected = settings,
                onDestination = { dest = it },
                onOpenSettings = { settings = !settings },
            )
        }
        onNodeWithText("功能").performClick()
        waitUntil { dest == MainDestination.Features }
        onNodeWithText("设置").performClick()
        waitUntil { settings }
        onNodeWithText("个人").assertIsDisplayed()
    }

    @Test
    fun islandShowsMessage() = runComposeUiTest {
        MainPalette.bind(MainColors.Light)
        val center = IslandNoticeCenter()
        setContent {
            IslandNoticeHost(center, GlassStyle())
        }
        center.show("已收藏")
        waitUntil(timeoutMillis = 2_000) {
            runCatching {
                onNodeWithText("已收藏").assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
    }

    @Test
    fun qualityCycleMatchesStore() {
        var prefs = AppPrefs()
        val next = AudioQuality.entries.let { e -> e[(e.indexOf(prefs.audioQuality) + 1) % e.size] }
        prefs = prefs.copy(audioQuality = next)
        assertEquals(AudioQuality.LOSSLESS, prefs.audioQuality)
        prefs = prefs.copy(lyricWordByWord = false, appearance = com.kite.zmusic.data.AppAppearance.Dark)
        assertTrue(!prefs.lyricWordByWord)
        assertEquals(com.kite.zmusic.data.AppAppearance.Dark, prefs.appearance)
    }

    @Test
    fun landscapeGeometryMatchesAndroid() {
        assertEquals(208f, LandscapeRailWidth.value)
        assertEquals(0.36f, LandscapeVinylWeight)
        val disc = landscapeVinylDiscDp(200.dp)
        assertTrue(disc.value <= 286f)
        assertTrue(disc.value >= 132f)
    }

    @Test
    fun featuresHasListenModesNotHomeSearch() = runComposeUiTest {
        MainPalette.bind(MainColors.Light)
        setContent {
            FeaturesScreen(onOpenOverlay = {}, onStartFm = {}, onStartIntelligence = {})
        }
        onNodeWithText("听歌模式").assertIsDisplayed()
        onNodeWithText("私人漫游").assertIsDisplayed()
        onNodeWithText("心动模式").assertIsDisplayed()
        onNodeWithText("每日推荐").assertIsDisplayed()
        onNodeWithText("功能").assertIsDisplayed()
    }

    @Test
    fun homeSearchPlaceholderMatchesAndroid() = runComposeUiTest {
        MainPalette.bind(MainColors.Light)
        setContent { HomeSearchEntry(onClick = {}) }
        onNodeWithText("搜索歌曲、歌单、MV、歌手").assertIsDisplayed()
    }

    @Test
    fun loginLandingMatchesLandscape() = runComposeUiTest {
        MainPalette.bind(MainColors.Light)
        val home = Files.createTempDirectory("zmusic-login-")
        System.setProperty("zmusic.home", home.toString())
        try {
            setContent {
                LoginScreen(
                    auth = NcmAuthClient(),
                    sessions = SessionStore(),
                    notices = IslandNoticeCenter(),
                    onLoggedIn = {},
                )
            }
            onNodeWithText("登录").assertIsDisplayed()
            onNodeWithText("手机号登录").assertIsDisplayed()
            onNodeWithText("扫码登录").assertIsDisplayed()
            onNodeWithText("把世界调小一点  ·  把歌开大一点").assertIsDisplayed()
        } finally {
            System.clearProperty("zmusic.home")
            home.toFile().deleteRecursively()
        }
    }

    @Test
    fun miniBarPlayOnlyNoSkip() = runComposeUiTest {
        MainPalette.bind(MainColors.Light)
        val state = PlaybackUiState(
            queue = listOf(TrackRow(1, "曲目A", "歌手", null, 1000, coverUrl = null)),
            index = 0,
            playWhenReady = false,
            hasQueue = true,
            durationMs = 1000,
        )
        setContent {
            MiniPlayerBar(state = state, glass = GlassStyle(), onToggle = {}, onExpand = {})
        }
        onNodeWithText("曲目A").assertIsDisplayed()
        onNodeWithContentDescription("播放").assertIsDisplayed()
        onAllNodesWithContentDescription("上一首").assertCountEquals(0)
    }

    @Test
    fun playerShowsBackAndTransport() = runComposeUiTest {
        MainPalette.bind(MainColors.Light)
        val state = PlaybackUiState(
            queue = listOf(TrackRow(1, "曲目B", "歌手B", null, 90_000)),
            index = 0,
            playWhenReady = true,
            hasQueue = true,
            durationMs = 90_000,
            sourcePlaylistTitle = "每日推荐",
        )
        setContent {
            LandscapePlayerBody(
                state = state,
                wordByWord = false,
                onBack = {},
                onToggle = {},
                onSeek = {},
                onMode = {},
                onNext = {},
                onPrev = {},
            )
        }
        onNodeWithText("曲目B").assertIsDisplayed()
        onNodeWithContentDescription("返回").assertIsDisplayed()
        onNodeWithContentDescription("暂停").assertIsDisplayed()
        onNodeWithContentDescription("喜欢").assertIsDisplayed()
        onNodeWithContentDescription("播放队列").assertIsDisplayed()
        onNodeWithContentDescription("投影歌词").assertIsDisplayed()
    }
}
