#include "pch.h"
#include "NcmLibrary.h"
#include "NcmJson.h"

#include <algorithm>
#include <cwctype>

using namespace winrt;
using namespace winrt::Windows::Data::Json;

namespace winrt::ZMusic
{
    namespace
    {
        static hstring JsonStr(JsonObject const& o, hstring const& key)
        {
            return o.HasKey(key) ? o.GetNamedString(key) : hstring{ L"" };
        }

        static hstring Trim(hstring const& s)
        {
            std::wstring_view w{ s };
            while (!w.empty() && iswspace(static_cast<wint_t>(w.front())))
            {
                w.remove_prefix(1);
            }
            while (!w.empty() && iswspace(static_cast<wint_t>(w.back())))
            {
                w.remove_suffix(1);
            }
            return hstring{ w };
        }

        static int PlaylistSortRank(LibraryPlaylistRow const& p)
        {
            if (p.IsHeart)
            {
                return 0;
            }
            if (p.IsOwned && !p.IsHeart)
            {
                return 1;
            }
            if (p.IsSubscribed)
            {
                return 2;
            }
            return 3;
        }

        static std::optional<LibraryTrackRow> ParseTrackObject(JsonObject const& t)
        {
            if (!t.HasKey(L"id"))
            {
                return std::nullopt;
            }
            int64_t const id = static_cast<int64_t>(t.Lookup(L"id").GetNumber());
            if (id <= 0)
            {
                return std::nullopt;
            }
            hstring const name = JsonStr(t, L"name");
            if (name.empty())
            {
                return std::nullopt;
            }
            std::wstring artists;
            if (t.HasKey(L"ar"))
            {
                auto const ar = t.GetNamedArray(L"ar");
                for (uint32_t i = 0; i < ar.Size(); ++i)
                {
                    auto v = ar.GetAt(i);
                    if (v.ValueType() != JsonValueType::Object)
                    {
                        continue;
                    }
                    hstring const n = JsonStr(v.GetObject(), L"name");
                    if (n.empty())
                    {
                        continue;
                    }
                    if (!artists.empty())
                    {
                        artists += L" / ";
                    }
                    artists += std::wstring{ n.c_str() };
                }
            }
            if (artists.empty())
            {
                artists = L"—";
            }
            hstring album;
            hstring coverUrl;
            if (t.HasKey(L"al") && t.Lookup(L"al").ValueType() == JsonValueType::Object)
            {
                auto const al = t.GetNamedObject(L"al");
                album = JsonStr(al, L"name");
                coverUrl = Trim(JsonStr(al, L"picUrl"));
            }
            int64_t durationMs{};
            if (t.HasKey(L"dt") && t.Lookup(L"dt").ValueType() == JsonValueType::Number)
            {
                durationMs = static_cast<int64_t>(t.Lookup(L"dt").GetNumber());
            }
            LibraryTrackRow row{};
            row.Id = id;
            row.Title = name;
            row.Artists = hstring{ artists };
            row.Album = album;
            row.CoverUrl = coverUrl;
            row.DurationMs = durationMs;
            return row;
        }
    } // namespace

    void NcmLibrary::PlaylistsFromUserPlaylist(JsonObject const& userPlaylistJson, int64_t selfUserId, std::vector<LibraryPlaylistRow>& outSorted)
    {
        outSorted.clear();
        if (NcmJson::ApiCode(userPlaylistJson) != 200 || !userPlaylistJson.HasKey(L"playlist"))
        {
            return;
        }
        JsonArray const arr = userPlaylistJson.GetNamedArray(L"playlist");
        for (uint32_t i = 0; i < arr.Size(); ++i)
        {
            if (arr.GetAt(i).ValueType() != JsonValueType::Object)
            {
                continue;
            }
            JsonObject const o = arr.GetAt(i).GetObject();
            int64_t const id = o.HasKey(L"id") ? static_cast<int64_t>(o.Lookup(L"id").GetNumber()) : 0LL;
            if (id <= 0)
            {
                continue;
            }
            hstring const name = o.HasKey(L"name") ? o.GetNamedString(L"name") : hstring{ L"(未命名)" };
            int32_t const trackCount = o.HasKey(L"trackCount") ? static_cast<int32_t>(o.Lookup(L"trackCount").GetNumber()) : 0;
            hstring coverUrl;
            if (o.HasKey(L"coverImgUrl"))
            {
                coverUrl = Trim(JsonStr(o, L"coverImgUrl"));
            }
            if (coverUrl.empty() && o.HasKey(L"coverUrl"))
            {
                coverUrl = Trim(JsonStr(o, L"coverUrl"));
            }
            int32_t const specialType = o.HasKey(L"specialType") ? static_cast<int32_t>(o.Lookup(L"specialType").GetNumber()) : 0;
            bool const nameIsHeart = (name == L"我喜欢的音乐");
            bool const isHeart = (specialType == 5) || nameIsHeart;
            int64_t creatorId = -1;
            if (o.HasKey(L"creator") && o.Lookup(L"creator").ValueType() == JsonValueType::Object)
            {
                JsonObject const c = o.GetNamedObject(L"creator");
                if (c.HasKey(L"userId"))
                {
                    creatorId = static_cast<int64_t>(c.Lookup(L"userId").GetNumber());
                }
            }
            bool const subscribed = o.HasKey(L"subscribed") && o.GetNamedBoolean(L"subscribed");
            bool const isOwned = (creatorId == selfUserId);
            LibraryPlaylistRow row{};
            row.Id = id;
            row.Name = name;
            row.TrackCount = trackCount;
            row.CoverUrl = coverUrl;
            row.IsHeart = isHeart;
            row.IsOwned = isOwned;
            row.IsSubscribed = subscribed;
            outSorted.push_back(row);
        }
        std::sort(
            outSorted.begin(),
            outSorted.end(),
            [](LibraryPlaylistRow const& a, LibraryPlaylistRow const& b)
            {
                int const ra = PlaylistSortRank(a);
                int const rb = PlaylistSortRank(b);
                if (ra != rb)
                {
                    return ra < rb;
                }
                return std::wstring_view{ a.Name.c_str() } < std::wstring_view{ b.Name.c_str() };
            });
    }

    uint32_t NcmLibrary::LikeIdsCount(JsonObject const& likelistJson)
    {
        return static_cast<uint32_t>(LikeListTrackIds(likelistJson).size());
    }

    std::vector<int64_t> NcmLibrary::LikeListTrackIds(JsonObject const& likelistJson)
    {
        std::vector<int64_t> out;
        if (NcmJson::ApiCode(likelistJson) != 200 || !likelistJson.HasKey(L"ids"))
        {
            return out;
        }
        JsonArray const ids = likelistJson.GetNamedArray(L"ids");
        for (uint32_t i = 0; i < ids.Size(); ++i)
        {
            IJsonValue const v = ids.GetAt(i);
            if (v.ValueType() == JsonValueType::Number)
            {
                int64_t const n = static_cast<int64_t>(v.GetNumber());
                if (n > 0)
                {
                    out.push_back(n);
                }
            }
            else if (v.ValueType() == JsonValueType::Object)
            {
                JsonObject const o = v.GetObject();
                if (o.HasKey(L"id") && o.Lookup(L"id").ValueType() == JsonValueType::Number)
                {
                    int64_t const n = static_cast<int64_t>(o.Lookup(L"id").GetNumber());
                    if (n > 0)
                    {
                        out.push_back(n);
                    }
                }
            }
        }
        return out;
    }

    std::vector<LibraryTrackRow> NcmLibrary::TracksFromSongDetail(JsonObject const& json)
    {
        std::vector<LibraryTrackRow> rows;
        if (NcmJson::ApiCode(json) != 200 || !json.HasKey(L"songs"))
        {
            return rows;
        }
        JsonArray const songs = json.GetNamedArray(L"songs");
        for (uint32_t i = 0; i < songs.Size(); ++i)
        {
            if (songs.GetAt(i).ValueType() != JsonValueType::Object)
            {
                continue;
            }
            if (auto t = ParseTrackObject(songs.GetAt(i).GetObject()))
            {
                rows.push_back(*t);
            }
        }
        return rows;
    }

    std::vector<LibraryTrackRow> NcmLibrary::TracksFromPlaylistDetail(JsonObject const& json)
    {
        std::vector<LibraryTrackRow> rows;
        if (NcmJson::ApiCode(json) != 200 || !json.HasKey(L"playlist"))
        {
            return rows;
        }
        JsonObject const pl = json.GetNamedObject(L"playlist");
        if (!pl.HasKey(L"tracks"))
        {
            return rows;
        }
        JsonArray const tracks = pl.GetNamedArray(L"tracks");
        for (uint32_t i = 0; i < tracks.Size(); ++i)
        {
            if (tracks.GetAt(i).ValueType() != JsonValueType::Object)
            {
                continue;
            }
            if (auto t = ParseTrackObject(tracks.GetAt(i).GetObject()))
            {
                rows.push_back(*t);
            }
        }
        return rows;
    }

    std::vector<int64_t> NcmLibrary::TrackIdsFromPlaylistDetail(JsonObject const& json)
    {
        std::vector<int64_t> out;
        if (!json.HasKey(L"playlist"))
        {
            return out;
        }
        JsonObject const pl = json.GetNamedObject(L"playlist");
        if (!pl.HasKey(L"trackIds"))
        {
            return out;
        }
        JsonArray const ids = pl.GetNamedArray(L"trackIds");
        for (uint32_t i = 0; i < ids.Size(); ++i)
        {
            IJsonValue const v = ids.GetAt(i);
            if (v.ValueType() == JsonValueType::Number)
            {
                int64_t const n = static_cast<int64_t>(v.GetNumber());
                if (n > 0)
                {
                    out.push_back(n);
                }
            }
            else if (v.ValueType() == JsonValueType::Object)
            {
                JsonObject const o = v.GetObject();
                if (o.HasKey(L"id") && o.Lookup(L"id").ValueType() == JsonValueType::Number)
                {
                    int64_t const n = static_cast<int64_t>(o.Lookup(L"id").GetNumber());
                    if (n > 0)
                    {
                        out.push_back(n);
                    }
                }
            }
        }
        return out;
    }
}
