package com.kite.zmusic.plugin

import com.kite.zmusic.data.ArtistAlbumCard
import com.kite.zmusic.data.ChartSummary
import com.kite.zmusic.data.CollectedAlbum
import com.kite.zmusic.data.HomeBanner
import com.kite.zmusic.data.RecommendMvCard
import com.kite.zmusic.data.RecommendPlaylistCard
import com.kite.zmusic.data.SearchArtistHit
import com.kite.zmusic.data.SearchPlaylistHit
import com.kite.zmusic.data.SearchUserHit
import com.kite.zmusic.data.TrackRow

/**
 * 宿主手势 / 操作槽附带的对象。字段均可空；插件按 [kind] 与有值字段使用。
 */
data class PluginUiTarget(
    val kind: String,
    val id: Long? = null,
    val name: String? = null,
    val subtitle: String? = null,
    val imageUrl: String? = null,
    val extra: Map<String, Any?> = emptyMap(),
) {
    fun toMap(): Map<String, Any?> {
        val out = linkedMapOf<String, Any?>(
            "kind" to kind,
            "id" to id,
            "name" to name,
            "subtitle" to subtitle,
            "imageUrl" to imageUrl,
        )
        extra.forEach { (k, v) ->
            if (k !in out) out[k] = v
        }
        return out
    }

    companion object {
        const val KIND_TRACK = "track"
        const val KIND_ALBUM = "album"
        const val KIND_PLAYLIST = "playlist"
        const val KIND_ARTIST = "artist"
        const val KIND_MV = "mv"
        const val KIND_CHART = "chart"
        const val KIND_USER = "user"
        const val KIND_IMAGE = "image"

        fun track(row: TrackRow): PluginUiTarget = PluginUiTarget(
            kind = KIND_TRACK,
            id = row.id,
            name = row.name,
            subtitle = row.artists,
            imageUrl = row.coverUrl,
            extra = buildMap {
                row.album?.let { put("album", it) }
                put("durationMs", row.durationMs)
            },
        )

        fun album(id: Long, name: String?, coverUrl: String?, subtitle: String? = null): PluginUiTarget =
            PluginUiTarget(KIND_ALBUM, id.takeIf { it > 0L }, name, subtitle, coverUrl)

        fun album(card: ArtistAlbumCard): PluginUiTarget = album(
            id = card.id,
            name = card.name,
            coverUrl = card.coverUrl,
            subtitle = card.year,
        )

        fun album(row: CollectedAlbum): PluginUiTarget = album(
            id = row.id,
            name = row.name,
            coverUrl = row.coverUrl,
            subtitle = row.artist,
        )

        fun playlist(id: Long, name: String?, coverUrl: String?, subtitle: String? = null): PluginUiTarget =
            PluginUiTarget(KIND_PLAYLIST, id.takeIf { it > 0L }, name, subtitle, coverUrl)

        fun playlist(card: RecommendPlaylistCard): PluginUiTarget = playlist(
            id = card.id,
            name = card.name,
            coverUrl = card.coverUrl,
        )

        fun playlist(hit: SearchPlaylistHit): PluginUiTarget = playlist(
            id = hit.id,
            name = hit.name,
            coverUrl = hit.coverUrl,
            subtitle = hit.creator,
        )

        fun artist(id: Long, name: String?, coverUrl: String?, subtitle: String? = null): PluginUiTarget =
            PluginUiTarget(KIND_ARTIST, id.takeIf { it > 0L }, name, subtitle, coverUrl)

        fun artist(hit: SearchArtistHit): PluginUiTarget = artist(hit.id, hit.name, hit.coverUrl)

        fun mv(id: Long, name: String?, coverUrl: String?, artist: String? = null): PluginUiTarget =
            PluginUiTarget(KIND_MV, id.takeIf { it > 0L }, name, artist, coverUrl)

        fun mv(card: RecommendMvCard): PluginUiTarget = mv(card.id, card.name, card.coverUrl, card.artist)

        fun chart(summary: ChartSummary): PluginUiTarget = PluginUiTarget(
            kind = KIND_CHART,
            id = summary.id.takeIf { it > 0L },
            name = summary.name,
            subtitle = summary.updateFrequency,
            imageUrl = summary.coverUrl,
        )

        fun user(id: Long, name: String?, avatarUrl: String?): PluginUiTarget =
            PluginUiTarget(KIND_USER, id.takeIf { it > 0L }, name, null, avatarUrl)

        fun user(hit: SearchUserHit): PluginUiTarget = user(hit.id, hit.name, hit.avatarUrl)

        fun banner(item: HomeBanner): PluginUiTarget = PluginUiTarget(
            kind = KIND_IMAGE,
            id = item.targetId.takeIf { it > 0L },
            name = item.title,
            imageUrl = item.picUrl,
            extra = buildMap {
                put("targetType", item.targetType)
                item.url?.let { put("url", it) }
            },
        )
    }
}
