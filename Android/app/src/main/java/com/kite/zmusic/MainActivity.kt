package com.kite.zmusic

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.kite.zmusic.config.NcmApiConfig
import com.kite.zmusic.data.ServerConfigRepository
import com.kite.zmusic.navigation.ZMusicNavHost
import com.kite.zmusic.ui.orientation.SessionRotationLockStore
import com.kite.zmusic.ui.orientation.ZMusicOrientationHost
import com.kite.zmusic.ui.theme.MainPalette
import com.kite.zmusic.ui.theme.ZMusicTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 尽早重套旋转锁：通知冷启动时系统可能已变成竖屏，不能等 Compose 再锁
        SessionRotationLockStore.applyTo(this)
        // 尽早应用已持久化的服务器地址，供后续 OkHttp 请求读取
        ServerConfigRepository(applicationContext)
        if (BuildConfig.DEBUG) {
            Log.d("ZMusic", "NCM API base URL: ${NcmApiConfig.baseUrl}")
        }
        enableEdgeToEdge()
        consumeOpenPlayerIntent(intent)
        setContent {
            ZMusicTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MainPalette.Page,
                ) {
                    ZMusicOrientationHost(modifier = Modifier.fillMaxSize()) {
                        ZMusicNavHost(modifier = Modifier.fillMaxSize())
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        SessionRotationLockStore.applyTo(this)
    }

    override fun onResume() {
        super.onResume()
        SessionRotationLockStore.applyTo(this)
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // 通知 / singleTop 回前台
        SessionRotationLockStore.applyTo(this)
        consumeOpenPlayerIntent(intent)
    }

    private fun consumeOpenPlayerIntent(intent: android.content.Intent?) {
        if (intent == null) return
        val open = intent.getBooleanExtra(EXTRA_OPEN_PLAYER, false) ||
            intent.action == ACTION_OPEN_PLAYER
        if (!open) return
        (application as ZMusicApplication).playbackBridge.requestOpenPlayer()
        intent.removeExtra(EXTRA_OPEN_PLAYER)
        if (intent.action == ACTION_OPEN_PLAYER) {
            intent.action = android.content.Intent.ACTION_MAIN
        }
    }

    companion object {
        const val ACTION_OPEN_PLAYER = "com.kite.zmusic.OPEN_PLAYER"
        const val EXTRA_OPEN_PLAYER = "open_player"
    }
}
