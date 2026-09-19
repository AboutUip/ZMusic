package com.kite.zmusic.data

import com.kite.zmusic.i18n.t

/**
 * 歌单写入：新建 / 重命名 / 删除 / 收藏，以及把歌曲加进歌单（去重、幂等）。
 */
class PlaylistEditor(
    private val sessionRepository: SessionRepository,
    private val userClient: NcmUserClient,
    private val collection: PlaylistCollectionRepository,
    private val tracksCache: PlaylistTracksCache,
    private val liked: LikedPlaylistRepository,
    private val libraryHome: LibraryHomeRepository,
) {
    fun ownedPlaylists(): List<PlaylistSummary> =
        collection.playlists.value.filter { it.isOwned }

    fun hasCreatedName(name: String, exceptId: Long = 0L): Boolean {
        val n = name.trim()
        if (n.isEmpty()) return false
        return collection.playlists.value.any { pl ->
            pl.isOwned && pl.id != exceptId && pl.name.trim().equals(n, ignoreCase = true)
        }
    }

    suspend fun create(name: String): String {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return t("请输入歌单名称")
        if (hasCreatedName(trimmed)) return t("已有同名歌单")
        val cookie = cookieOrNull() ?: return t("请先登录")
        val json = userClient.playlistCreate(trimmed, cookie)
        if (NcmJson.apiCode(json) != 200) {
            return NcmJson.userFacingMessage(json, "创建失败")
        }
        val created = NcmLibraryParse.playlistFromCreate(json)
            ?: PlaylistSummary(
                id = json.optLong("id", 0L),
                name = trimmed,
                coverUrl = null,
                trackCount = 0,
                isHeartPlaylist = false,
                isOwned = true,
                isSubscribed = false,
                playCount = 0L,
            )
        if (created.id <= 0L) return NcmJson.userFacingMessage(json, "创建失败")
        val named = created.copy(
            name = created.name.ifBlank { trimmed },
            coverUrl = created.coverUrl.takeUnless { isDefaultPlaylistCover(it) || created.trackCount <= 0 },
        )
        collection.upsertCreated(named)
        runCatching { libraryHome.refresh(force = true) }
        return t("已创建「%s」", created.name.ifBlank { trimmed })
    }

    suspend fun rename(playlist: PlaylistSummary, name: String): String {
        if (playlist.isHeartPlaylist) return t("喜欢的音乐不能改名")
        if (!playlist.isOwned) return t("只能重命名自己创建的歌单")
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return t("请输入歌单名称")
        if (trimmed == playlist.name) return t("名称没有变化")
        if (hasCreatedName(trimmed, exceptId = playlist.id)) return t("已有同名歌单")
        val cookie = cookieOrNull() ?: return t("请先登录")
        val json = userClient.playlistNameUpdate(playlist.id, trimmed, cookie)
        if (NcmJson.apiCode(json) != 200) {
            return NcmJson.userFacingMessage(json, "重命名失败")
        }
        collection.rename(playlist.id, trimmed)
        tracksCache.renameTitle(playlist.id, trimmed)
        return t("已重命名为「%s」", trimmed)
    }

    suspend fun deleteOwned(playlist: PlaylistSummary): String {
        if (playlist.isHeartPlaylist) return t("喜欢的音乐不能删除")
        if (!playlist.isOwned) return t("只能删除自己创建的歌单")
        val cookie = cookieOrNull() ?: return t("请先登录")
        val json = userClient.playlistDelete(playlist.id, cookie)
        if (NcmJson.apiCode(json) != 200) {
            return NcmJson.userFacingMessage(json, "删除失败")
        }
        collection.remove(playlist.id)
        runCatching { libraryHome.refresh(force = true) }
        return t("已删除「%s」", playlist.name)
    }

    suspend fun unsubscribe(playlist: PlaylistSummary): String {
        if (playlist.isOwned) return t("自己的歌单不用取消收藏")
        val cookie = cookieOrNull() ?: return t("请先登录")
        val json = userClient.playlistSubscribe(playlist.id, false, cookie)
        if (NcmJson.apiCode(json) != 200) {
            return NcmJson.userFacingMessage(json, "取消收藏失败")
        }
        collection.setSubscribed(playlist.id, false)
        runCatching { libraryHome.refresh(force = true) }
        return t("已取消收藏")
    }

    suspend fun subscribe(playlist: PlaylistSummary): String {
        if (playlist.isOwned) return t("自己的歌单不用收藏")
        val cookie = cookieOrNull() ?: return t("请先登录")
        val json = userClient.playlistSubscribe(playlist.id, true, cookie)
        if (NcmJson.apiCode(json) != 200) {
            return NcmJson.userFacingMessage(json, "收藏失败")
        }
        collection.setSubscribed(playlist.id, true, insert = playlist)
        runCatching { libraryHome.refresh(force = true) }
        return t("已收藏歌单")
    }

    /**
     * 把歌曲加入歌单。已在歌单内则直接成功（幂等）。
     * @return 给灵动岛的短句
     */
    suspend fun addTrack(playlist: PlaylistSummary, track: TrackRow): String {
        if (track.id <= 0L) return t("无法添加这首歌")
        if (playlist.isHeartPlaylist) return addToLiked(track)
        if (!playlist.isOwned) return t("只能加到自己创建的歌单")
        when (tracksCache.containsTrack(playlist.id, track.id)) {
            true -> return t("已在「%s」中", playlist.name)
            false, null -> Unit
        }
        val cookie = cookieOrNull() ?: return t("请先登录")
        val json = userClient.playlistTracks("add", playlist.id, listOf(track.id), cookie)
        val code = NcmJson.apiCode(json)
        val already = code != 200 && NcmLibraryParse.isPlaylistTrackDuplicate(json)
        if (code != 200 && !already) {
            return NcmJson.userFacingMessage(json, "添加失败")
        }
        if (!already) {
            tracksCache.addTrackIfAbsent(playlist.id, track)
            collection.applyAddedTrack(playlist.id, track.coverUrl)
        }
        return if (already) t("已在「%s」中", playlist.name) else t("已添加到「%s」", playlist.name)
    }

    private suspend fun addToLiked(track: TrackRow): String {
        if (liked.isLiked(track.id) == true) return t("已经在喜欢的音乐里")
        val cookie = cookieOrNull() ?: return t("请先登录")
        liked.applyLocalLike(track, liked = true)
        if (!liked.pushLike(track, liked = true, cookie)) {
            liked.applyLocalLike(track, liked = false, scheduleSync = false)
            return t("添加失败")
        }
        return t("已添加到喜欢的音乐")
    }

    private fun cookieOrNull(): String? =
        sessionRepository.session.value?.cookie?.takeIf { it.isNotBlank() }
}
