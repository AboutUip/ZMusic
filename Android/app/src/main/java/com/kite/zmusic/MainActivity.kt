package com.kite.zmusic

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.kite.zmusic.navigation.ZMusicNavHost
import com.kite.zmusic.ui.orientation.ZMusicOrientationHost
import com.kite.zmusic.ui.theme.ZMusicTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ZMusicTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Black,
                ) {
                    ZMusicOrientationHost(modifier = Modifier.fillMaxSize()) {
                        ZMusicNavHost(modifier = Modifier.fillMaxSize())
                    }
                }
            }
        }
    }
}
