package com.kite.zmusic.ui.server

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kite.zmusic.R
import com.kite.zmusic.data.ServerConfigRepository
import com.kite.zmusic.ui.main.MainLightSystemBars
import com.kite.zmusic.ui.main.MainPalette

/**
 * 启动时服务不可达的全屏配置页。应用内改地址走设置里的液体玻璃弹窗。
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

    MainLightSystemBars()
    Box(
        modifier
            .fillMaxSize()
            .background(MainPalette.Page),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(36.dp))
            Image(
                painter = painterResource(R.drawable.ic_logo_vinyl_z),
                contentDescription = null,
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
            Spacer(Modifier.height(22.dp))
            Text(
                text = "无法连接服务",
                style = TextStyle(
                    color = MainPalette.Ink,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.3).sp,
                ),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "填写主机和端口，测试通过后再进入",
                style = TextStyle(
                    color = MainPalette.Secondary,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    textAlign = TextAlign.Center,
                ),
            )
            Spacer(Modifier.height(28.dp))
            Column(
                Modifier
                    .widthIn(max = 400.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.White)
                    .padding(horizontal = 18.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                GateField(
                    label = "主机 / IP",
                    value = vm.host,
                    onChange = vm::onHostChange,
                    keyboardType = KeyboardType.Uri,
                )
                GateField(
                    label = "端口",
                    value = vm.portText,
                    onChange = vm::onPortChange,
                    keyboardType = KeyboardType.Number,
                )
                vm.statusHint?.let { hint ->
                    Text(
                        text = hint,
                        style = TextStyle(
                            color = MainPalette.Secondary,
                            fontSize = 13.sp,
                        ),
                    )
                }
                vm.bannerError?.let { err ->
                    Text(
                        text = err,
                        style = TextStyle(
                            color = MainPalette.Accent,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                        ),
                    )
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(
                            if (vm.busy) MainPalette.Accent.copy(alpha = 0.45f) else MainPalette.Accent,
                        )
                        .clickable(
                            enabled = !vm.busy,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { vm.saveAndConnect(onConfigured) },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (vm.busy) "连接中" else "测试并保存",
                        style = TextStyle(
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
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
                    modifier = Modifier.size(28.dp),
                    color = MainPalette.Accent,
                    strokeWidth = 2.dp,
                )
            }
        }
    }
}

@Composable
private fun GateField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    keyboardType: KeyboardType,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = label,
            style = TextStyle(
                color = MainPalette.Secondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            ),
        )
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            textStyle = TextStyle(
                color = MainPalette.Ink,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
            ),
            cursorBrush = SolidColor(MainPalette.Accent),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(MainPalette.Page)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) {
                        Text(
                            text = label,
                            color = MainPalette.Hint,
                            fontSize = 16.sp,
                        )
                    }
                    inner()
                }
            },
        )
    }
}
