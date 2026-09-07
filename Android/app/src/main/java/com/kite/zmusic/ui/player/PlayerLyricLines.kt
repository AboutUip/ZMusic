package com.kite.zmusic.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kite.zmusic.ZMusicApplication
import com.kite.zmusic.data.DisplayLyricBundle
import com.kite.zmusic.data.PlayerDisplayPrefs
import com.kite.zmusic.data.pickDisplayLyricBundle
import com.kite.zmusic.data.sanitizedForDisplay
import com.kite.zmusic.playback.PlaybackUiState

@Composable
internal fun rememberDisplayLyrics(
    state: PlaybackUiState,
    isLandscape: Boolean,
    portraitPrefs: PlayerDisplayPrefs,
): DisplayLyricBundle {
    val app = LocalContext.current.applicationContext as ZMusicApplication
    val wordByWord by app.lyricRenderStore.wordByWord.collectAsStateWithLifecycle()
    val prefer = !isLandscape && portraitPrefs.portraitLyricPreferTranslation
    return pickDisplayLyricBundle(
        original = state.lyricLines.mapNotNull { it.sanitizedForDisplay() },
        translated = state.translatedLyricLines.mapNotNull { it.sanitizedForDisplay() },
        wordOriginal = state.wordLyricLines.mapNotNull { it.sanitizedForDisplay() },
        wordTranslated = state.translatedWordLyricLines.mapNotNull { it.sanitizedForDisplay() },
        preferTranslation = prefer,
        coexist = prefer && portraitPrefs.portraitLyricTranslationCoexist,
        wordByWord = wordByWord,
    )
}
