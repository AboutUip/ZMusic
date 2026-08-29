package com.kite.zmusic.ui.main

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kite.zmusic.ui.theme.MainPalette
import org.jetbrains.skia.Image

internal val LandscapeRailWidth = 208.dp

@Composable
fun rememberLogoBitmap(): ImageBitmap {
    return remember {
        val stream = object {}.javaClass.getResourceAsStream("/drawable/ic_logo_vinyl_z.png")
        if (stream == null) {
            ImageBitmap(1, 1)
        } else {
            stream.use { Image.makeFromEncoded(it.readBytes()).toComposeImageBitmap() }
        }
    }
}

@Composable
fun LandscapeNavRail(
    selected: MainDestination,
    settingsSelected: Boolean,
    onDestination: (MainDestination) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val logo = rememberLogoBitmap()
    Row(
        modifier
            .width(LandscapeRailWidth)
            .fillMaxHeight()
            .background(MainPalette.Surface),
    ) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(top = 18.dp, bottom = 12.dp),
        ) {
            Row(
                Modifier.padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    bitmap = logo,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp).clip(CircleShape),
                    contentScale = ContentScale.Crop,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    "ZMusic",
                    style = TextStyle(
                        color = MainPalette.Ink,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.3).sp,
                    ),
                )
            }
            Spacer(Modifier.height(28.dp))
            Column(
                Modifier.padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                RailItem("主页", selected == MainDestination.Home && !settingsSelected, Icons.Outlined.Home) {
                    onDestination(MainDestination.Home)
                }
                RailItem("功能", selected == MainDestination.Features && !settingsSelected, Icons.Outlined.Widgets) {
                    onDestination(MainDestination.Features)
                }
                RailItem("个人", selected == MainDestination.Profile && !settingsSelected, Icons.Outlined.Person) {
                    onDestination(MainDestination.Profile)
                }
            }
            Spacer(Modifier.weight(1f))
            Column(Modifier.padding(horizontal = 12.dp)) {
                RailItem("设置", settingsSelected, Icons.Outlined.Settings, onOpenSettings)
            }
        }
        Box(
            Modifier
                .width(0.5.dp)
                .fillMaxHeight()
                .background(MainPalette.Hairline),
        )
    }
}

@Composable
private fun RailItem(
    label: String,
    selected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    val bg = if (selected) MainPalette.Accent.copy(alpha = 0.10f) else Color.Transparent
    Row(
        Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (selected) MainPalette.DockActive else MainPalette.DockInactive,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            label,
            style = TextStyle(
                color = if (selected) MainPalette.DockActive else MainPalette.DockInactive,
                fontSize = 14.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            ),
        )
    }
}
