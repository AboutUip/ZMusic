package com.kite.zmusic.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kite.zmusic.ZMusicApplication
import com.kite.zmusic.data.LrcLine
import com.kite.zmusic.data.PlayerDisplayPrefs
import com.kite.zmusic.data.pickDisplayLyricLines
import com.kite.zmusic.data.sanitizedForDisplay
import com.kite.zmusic.playback.PlaybackUiState

@Composable
internal fun rememberDisplayLyricLines(
    state: PlaybackUiState,
    isLandscape: Boolean,
    portraitPrefs: PlayerDisplayPrefs,
): List<LrcLine> {
    val app = LocalContext.current.applicationContext as ZMusicApplication
    val wordByWord by app.lyricRenderStore.wordByWord.collectAsStateWithLifecycle()
    return pickDisplayLyricLines(
        original = state.lyricLines.mapNotNull { it.sanitizedForDisplay() },
        translated = state.translatedLyricLines.mapNotNull { it.sanitizedForDisplay() },
        wordOriginal = state.wordLyricLines.mapNotNull { it.sanitizedForDisplay() },
        wordTranslated = state.translatedWordLyricLines.mapNotNull { it.sanitizedForDisplay() },
        preferTranslation = !isLandscape && portraitPrefs.portraitLyricPreferTranslation,
        wordByWord = wordByWord,
    )
}
