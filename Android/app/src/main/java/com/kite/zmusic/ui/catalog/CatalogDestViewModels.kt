package com.kite.zmusic.ui.catalog

import com.kite.zmusic.data.AlbumCollectionRepository
import com.kite.zmusic.data.AlbumTracksCache
import com.kite.zmusic.data.CatalogRepository
import com.kite.zmusic.data.HomeFeedRepository
import com.kite.zmusic.data.LikedPlaylistRepository
import com.kite.zmusic.data.PlaylistCollectionRepository
import com.kite.zmusic.data.PlaylistTracksCache
import com.kite.zmusic.data.SessionRepository
import com.kite.zmusic.ui.notice.IslandNoticeCenter

class DailyFmViewModel(
    sessionRepository: SessionRepository,
    playlistTracksCache: PlaylistTracksCache,
    albumTracksCache: AlbumTracksCache,
    homeFeed: HomeFeedRepository,
    likedPlaylistRepository: LikedPlaylistRepository,
    playlistCollection: PlaylistCollectionRepository,
    albumCollection: AlbumCollectionRepository,
    islandNotices: IslandNoticeCenter,
    catalog: CatalogRepository,
) : CatalogViewModel(
    sessionRepository,
    playlistTracksCache,
    albumTracksCache,
    homeFeed,
    likedPlaylistRepository,
    playlistCollection,
    albumCollection,
    islandNotices,
    catalog,
)

class PlaylistDetailViewModel(
    sessionRepository: SessionRepository,
    playlistTracksCache: PlaylistTracksCache,
    albumTracksCache: AlbumTracksCache,
    homeFeed: HomeFeedRepository,
    likedPlaylistRepository: LikedPlaylistRepository,
    playlistCollection: PlaylistCollectionRepository,
    albumCollection: AlbumCollectionRepository,
    islandNotices: IslandNoticeCenter,
    catalog: CatalogRepository,
) : CatalogViewModel(
    sessionRepository,
    playlistTracksCache,
    albumTracksCache,
    homeFeed,
    likedPlaylistRepository,
    playlistCollection,
    albumCollection,
    islandNotices,
    catalog,
)

class AlbumDetailViewModel(
    sessionRepository: SessionRepository,
    playlistTracksCache: PlaylistTracksCache,
    albumTracksCache: AlbumTracksCache,
    homeFeed: HomeFeedRepository,
    likedPlaylistRepository: LikedPlaylistRepository,
    playlistCollection: PlaylistCollectionRepository,
    albumCollection: AlbumCollectionRepository,
    islandNotices: IslandNoticeCenter,
    catalog: CatalogRepository,
) : CatalogViewModel(
    sessionRepository,
    playlistTracksCache,
    albumTracksCache,
    homeFeed,
    likedPlaylistRepository,
    playlistCollection,
    albumCollection,
    islandNotices,
    catalog,
)

class ChartsCatalogViewModel(
    sessionRepository: SessionRepository,
    playlistTracksCache: PlaylistTracksCache,
    albumTracksCache: AlbumTracksCache,
    homeFeed: HomeFeedRepository,
    likedPlaylistRepository: LikedPlaylistRepository,
    playlistCollection: PlaylistCollectionRepository,
    albumCollection: AlbumCollectionRepository,
    islandNotices: IslandNoticeCenter,
    catalog: CatalogRepository,
) : CatalogViewModel(
    sessionRepository,
    playlistTracksCache,
    albumTracksCache,
    homeFeed,
    likedPlaylistRepository,
    playlistCollection,
    albumCollection,
    islandNotices,
    catalog,
)
