package com.kite.zmusic.ui.main

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kite.zmusic.AppContainer
import com.kite.zmusic.data.NcmJson
import com.kite.zmusic.ui.theme.MainPalette
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(app: AppContainer, onDone: (loggedIn: Boolean) -> Unit) {
    LaunchedEffect(Unit) {
        delay(600)
        val cookie = app.sessions.session.value?.cookie
        val ok = if (cookie.isNullOrBlank()) {
            false
        } else {
            runCatching {
                val json = app.auth.loginStatus(cookie)
                NcmJson.isLoggedInStatus(json) || NcmJson.userIdFromLoginStatus(json) != null
            }.getOrDefault(false)
        }
        if (!ok && cookie != null) app.sessions.clear()
        onDone(ok)
    }
    Box(Modifier.fillMaxSize().background(MainPalette.Page), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                bitmap = rememberLogoBitmap(),
                contentDescription = null,
                modifier = Modifier.size(88.dp).clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "ZMusic",
                style = TextStyle(
                    color = MainPalette.Ink,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
        }
    }
}
