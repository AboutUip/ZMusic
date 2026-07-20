package com.kite.zmusic

import android.app.Application
import com.kite.zmusic.data.SessionRepository
import com.kite.zmusic.playback.PlaybackBridge

class ZMusicApplication : Application() {
    lateinit var sessionRepository: SessionRepository
        private set
    lateinit var playbackBridge: PlaybackBridge
        private set

    override fun onCreate() {
        super.onCreate()
        sessionRepository = SessionRepository(this)
        playbackBridge = PlaybackBridge(this, sessionRepository)
    }
}
