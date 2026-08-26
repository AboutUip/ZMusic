package com.kite.zmusic.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.kite.zmusic.plugin.CollectionCoverKind
import com.kite.zmusic.plugin.CollectionPresentSpec
import com.kite.zmusic.ui.common.CoverPlaceholderVinyl
import com.kite.zmusic.ui.common.UrlImage
import com.kite.zmusic.ui.common.UrlImageCache
import com.kite.zmusic.ui.player.VinylDiscPlate
import com.kite.zmusic.ui.theme.MainPalette

@Composable
internal fun CollectionCover(
    url: String?,
    spec: CollectionPresentSpec,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    when (spec.cover) {
        CollectionCoverKind.Round -> RoundOrCircleCover(
            url = url,
            circle = false,
            modifier = modifier,
            contentDescription = contentDescription,
        )
        CollectionCoverKind.Circle -> RoundOrCircleCover(
            url = url,
            circle = true,
            modifier = modifier,
            contentDescription = contentDescription,
        )
        CollectionCoverKind.Vinyl -> VinylCover(
            url = url,
            spec = spec,
            modifier = modifier,
            contentDescription = contentDescription,
        )
    }
}

@Composable
private fun RoundOrCircleCover(
    url: String?,
    circle: Boolean,
    modifier: Modifier,
    contentDescription: String?,
) {
    val shape = if (circle) CircleShape else RoundedCornerShape(8.dp)
    Box(modifier.clip(shape)) {
        if (url.isNullOrBlank()) {
            CoverPlaceholderVinyl(Modifier.fillMaxSize())
        } else {
            UrlImage(
                url = url,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                maxPx = UrlImageCache.THUMB_MAX_PX,
            )
        }
    }
}

@Composable
private fun VinylCover(
    url: String?,
    spec: CollectionPresentSpec,
    modifier: Modifier,
    contentDescription: String?,
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        VinylDiscPlate(
            modifier = Modifier.fillMaxSize(),
            spindleHoleFrac = 0f,
            colors = spec.plateColors(),
        )
        val inner = Modifier.fillMaxSize(0.62f).clip(CircleShape)
        if (url.isNullOrBlank()) {
            Box(inner.background(MainPalette.Placeholder))
        } else {
            UrlImage(
                url = url,
                contentDescription = contentDescription,
                modifier = inner,
                contentScale = ContentScale.Crop,
                maxPx = UrlImageCache.THUMB_MAX_PX,
            )
        }
    }
}
