package com.kite.zmusic.ui.server

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kite.zmusic.data.ServerConfigRepository
import com.kite.zmusic.ui.scifi.SciFiBackdrop
import com.kite.zmusic.ui.scifi.SciFiHudPrimaryButton
import com.kite.zmusic.ui.scifi.SciFiHudTextAction
import com.kite.zmusic.ui.scifi.SciFiPanelFrame

private val Cyan = Color(0xFF00FFD1)
private val Dim = Color(0xFF8FA8B8)
private val Warn = Color(0xFFFFB86C)
private val TermGreen = Color(0xFF39FF9C)

/**
 * 服务器 IP / 端口配置；探测成功后持久化并进入应用。
 */
@Composable
fun ServerConfigScreen(
    serverConfigRepository: ServerConfigRepository,
    onConfigured: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val vm: ServerConfigViewModel = viewModel(
        factory = ServerConfigViewModelFactory(serverConfigRepository),
    )

    Box(modifier.fillMaxSize()) {
        SciFiBackdrop(Modifier.fillMaxSize())
        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "LINK // 服务器",
                style = TextStyle(
                    color = Cyan,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    letterSpacing = 3.sp,
                ),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "无法连接 API 服务时需配置主机与端口",
                style = TextStyle(
                    color = Dim.copy(alpha = 0.9f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    letterSpacing = 0.5.sp,
                    lineHeight = 16.sp,
                ),
            )
            Spacer(Modifier.height(22.dp))
            SciFiPanelFrame(
                modifier = Modifier
                    .widthIn(max = 420.dp)
                    .fillMaxWidth(),
            ) {
                Column(
                    Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    ConfigField(
                        label = "主机 / IP",
                        value = vm.host,
                        onChange = vm::onHostChange,
                        keyboardType = KeyboardType.Uri,
                    )
                    ConfigField(
                        label = "端口",
                        value = vm.portText,
                        onChange = vm::onPortChange,
                        keyboardType = KeyboardType.Number,
                    )
                    vm.statusHint?.let { hint ->
                        Text(
                            text = hint,
                            style = TextStyle(
                                color = TermGreen.copy(alpha = 0.85f),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                            ),
                        )
                    }
                    vm.bannerError?.let { err ->
                        Column {
                            Text(
                                text = err,
                                style = TextStyle(
                                    color = Warn,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp,
                                ),
                            )
                            SciFiHudTextAction(text = "关闭", onClick = vm::dismissError)
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    SciFiHudPrimaryButton(
                        text = "测试并保存",
                        enabled = !vm.busy,
                        onClick = { vm.saveAndConnect(onConfigured) },
                    )
                }
            }
        }

        if (vm.busy) {
            Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(40.dp),
                    color = Cyan.copy(alpha = 0.85f),
                    strokeWidth = 2.dp,
                )
            }
        }
    }
}

@Composable
private fun ConfigField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    keyboardType: KeyboardType,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text(label, fontFamily = FontFamily.Monospace, fontSize = 11.sp) },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        textStyle = TextStyle(
            color = Color.White.copy(alpha = 0.92f),
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
        ),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Color.White.copy(alpha = 0.95f),
            unfocusedTextColor = Color.White.copy(alpha = 0.88f),
            focusedBorderColor = Cyan.copy(alpha = 0.75f),
            unfocusedBorderColor = Cyan.copy(alpha = 0.28f),
            cursorColor = Cyan,
            focusedLabelColor = Cyan.copy(alpha = 0.85f),
            unfocusedLabelColor = Dim,
        ),
    )
}
