#pragma once

#include "pch.h"

#include <cstdint>
#include <vector>

namespace winrt::ZMusic
{
    struct LibraryPlaylistRow
    {
        int64_t Id{};
        hstring Name;
        int32_t TrackCount{};
        hstring CoverUrl;
        bool IsHeart{};
        bool IsOwned{};
        bool IsSubscribed{};
    };

    struct LibraryTrackRow
    {
        int64_t Id{};
        hstring Title;
        hstring Artists;
        hstring Album;
        hstring CoverUrl;
        int64_t DurationMs{};
    };

    struct NcmLibrary
    {
        static void PlaylistsFromUserPlaylist(
            Windows::Data::Json::JsonObject const& userPlaylistJson,
            int64_t selfUserId,
            std::vector<LibraryPlaylistRow>& outSorted);

        static uint32_t LikeIdsCount(Windows::Data::Json::JsonObject const& likelistJson);
        static std::vector<int64_t> LikeListTrackIds(Windows::Data::Json::JsonObject const& likelistJson);

        static std::vector<LibraryTrackRow> TracksFromSongDetail(Windows::Data::Json::JsonObject const& json);
        static std::vector<LibraryTrackRow> TracksFromPlaylistDetail(Windows::Data::Json::JsonObject const& json);
        static std::vector<int64_t> TrackIdsFromPlaylistDetail(Windows::Data::Json::JsonObject const& json);
    };
}
