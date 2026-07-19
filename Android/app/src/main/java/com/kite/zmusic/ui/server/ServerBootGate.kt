package com.kite.zmusic.ui.server

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kite.zmusic.data.NcmConnectivityClient
import com.kite.zmusic.data.ServerConfigRepository
import com.kite.zmusic.ui.scifi.SciFiBackdrop

private val Cyan = Color(0xFF00FFD1)
private val Dim = Color(0xFF8FA8B8)

/**
 * 启动后连通性门闸：成功进入主流程，失败进入服务器配置页。
 */
@Composable
fun ServerBootGate(
    serverConfigRepository: ServerConfigRepository,
    onReady: () -> Unit,
    onNeedConfig: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(Unit) {
        serverConfigRepository.applyToRuntime()
        val result = NcmConnectivityClient().checkReachable()
        if (result.isSuccess) {
            onReady()
        } else {
            onNeedConfig()
        }
    }

    Box(modifier.fillMaxSize()) {
        SciFiBackdrop(Modifier.fillMaxSize())
        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "PROBE // SERVER",
                style = TextStyle(
                    color = Cyan,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    letterSpacing = 3.sp,
                ),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "正在检测 API 连通性…",
                style = TextStyle(
                    color = Dim.copy(alpha = 0.85f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                ),
            )
            Spacer(Modifier.height(20.dp))
            CircularProgressIndicator(
                modifier = Modifier.size(36.dp),
                color = Cyan.copy(alpha = 0.85f),
                strokeWidth = 2.dp,
            )
        }
    }
}
